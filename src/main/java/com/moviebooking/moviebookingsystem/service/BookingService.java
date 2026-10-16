package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.repository.BookingRepository;
import com.moviebooking.moviebookingsystem.repository.ShowtimeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final ShowtimeRepository showtimeRepository;

    // Seat lock time = 5 minutes
    private static final long LOCK_DURATION =
            5 * 60 * 1000;

    public BookingService(
            BookingRepository bookingRepository,
            ShowtimeRepository showtimeRepository) {

        this.bookingRepository = bookingRepository;
        this.showtimeRepository = showtimeRepository;
    }

    // ================= GET ALL BOOKINGS =================

    @Transactional
    public List<Booking> getAllBookings() {

        releaseExpiredSeats();

        return bookingRepository.findAll();
    }

    @Transactional
    public List<Booking> getBookingsForCustomer(String customerEmail) {
        if (customerEmail == null || customerEmail.isBlank()) {
            return List.of();
        }
        releaseExpiredSeats();
        return bookingRepository.findByCustomerEmailIgnoreCaseOrderByIdDesc(
                customerEmail.trim());
    }

    // ================= RELEASE EXPIRED SEAT LOCKS =================
    // Each expired booking was fetched from the database, so the
    // status change has to be saved explicitly or it is silently
    // lost (no active persistence context / dirty-checking here).

    @Scheduled(fixedDelay = 5000, initialDelay = 10000)
    @Transactional
    public void releaseExpiredSeats() {

        long currentTime = System.currentTimeMillis();

        for (Booking booking : bookingRepository.findAll()) {

            if ("LOCKED".equals(booking.getStatus())
                    && booking.getLockExpiryTime() > 0
                    && booking.getLockExpiryTime() <= currentTime) {

                Booking current = bookingRepository.findByIdForUpdate(booking.getId())
                        .orElse(null);
                if (current == null
                        || !"LOCKED".equals(current.getStatus())
                        || current.getLockExpiryTime() <= 0
                        || current.getLockExpiryTime() > currentTime) {
                    continue;
                }

                current.setStatus("AVAILABLE");
                current.setLockExpiryTime(0);
                bookingRepository.save(current);

                incrementAvailableSeatsForBooking(current);

                System.out.println("Seat released automatically: " + current.getSeatNumber());
            }
        }
    }

    // ================= CHECK SEAT =================

    public boolean isSeatBooked(
            String seatNumber,
            Long movieId,
            String theatre,
            String date,
            String showTime) {

        releaseExpiredSeats();

        if (seatNumber == null || movieId == null) {
            return false;
        }
        String requestedSeat = seatNumber.trim().toUpperCase(Locale.ROOT);
        return bookingRepository.findByMovieIdAndTheatreAndDateAndShowTime(
                        movieId, theatre, date, showTime)
                .stream()
                .filter(this::isActiveReservation)
                .filter(booking -> booking.getSeatNumber() != null)
                .flatMap(booking -> Arrays.stream(booking.getSeatNumber().split(",")))
                .map(seat -> seat.trim().toUpperCase(Locale.ROOT))
                .anyMatch(requestedSeat::equals);
    }

    // ================= CREATE / LOCK BOOKING =================

    @Transactional
    public boolean createBooking(Booking booking) {

        releaseExpiredSeats();
        if (booking == null
                || booking.getMovieId() == null
                || booking.getTheatre() == null
                || booking.getTheatre().isBlank()
                || booking.getDate() == null
                || booking.getShowTime() == null
                || booking.getCustomerEmail() == null
                || booking.getCustomerEmail().isBlank()
                || booking.getSeatNumber() == null) {
            return false;
        }

        List<String> submittedSeats = Arrays.stream(booking.getSeatNumber().split(",", -1))
                .map(seat -> seat.trim().toUpperCase(Locale.ROOT))
                .toList();
        Set<String> uniqueSeats = new HashSet<>(submittedSeats);
        if (submittedSeats.isEmpty()
                || submittedSeats.stream().anyMatch(String::isEmpty)
                || uniqueSeats.size() != submittedSeats.size()) {
            return false;
        }

        Showtime candidate = findShowtimeForBooking(booking);
        if (candidate == null || candidate.getId() == null) {
            return false;
        }

        Showtime showtime = showtimeRepository.findByIdForUpdate(candidate.getId())
                .orElse(null);
        if (showtime == null
                || !matchesBooking(showtime, booking)
                || !isFutureShowtime(showtime)
                || !Double.isFinite(showtime.getTicketPrice())
                || showtime.getTicketPrice() <= 0
                || showtime.getTotalSeats() <= 0
                || showtime.getAvailableSeats() < submittedSeats.size()
                || showtime.getAvailableSeats() > showtime.getTotalSeats()
                || submittedSeats.stream().anyMatch(seat ->
                        !isValidSeatForCapacity(seat, showtime.getTotalSeats()))) {
            return false;
        }
        double bookingTotal = showtime.getTicketPrice() * submittedSeats.size();
        if (!Double.isFinite(bookingTotal) || bookingTotal <= 0) {
            return false;
        }

        List<Booking> sameShowtimeBookings =
                bookingRepository.findByMovieIdAndTheatreAndDateAndShowTime(
                        showtime.getMovieId(),
                        showtime.getTheatre(),
                        showtime.getDate(),
                        showtime.getShowTime());
        boolean seatConflict = sameShowtimeBookings.stream()
                .filter(this::isActiveReservation)
                .filter(existing -> existing.getSeatNumber() != null)
                .flatMap(existing -> Arrays.stream(existing.getSeatNumber().split(",")))
                .map(seat -> seat.trim().toUpperCase(Locale.ROOT))
                .anyMatch(uniqueSeats::contains);
        if (seatConflict) {
            return false;
        }

        booking.setMovieTitle(showtime.getMovieTitle());
        booking.setTheatre(showtime.getTheatre());
        booking.setDate(showtime.getDate());
        booking.setShowTime(showtime.getShowTime());
        booking.setSeatNumber(String.join(", ", submittedSeats));
        booking.setPrice(bookingTotal);

        // Lock the seat
        booking.setStatus("LOCKED");

        // Set expiry time = current time + 5 minutes
        booking.setLockExpiryTime(
                System.currentTimeMillis() + LOCK_DURATION
        );
        if (booking.getCreatedAt() == 0) {
            booking.setCreatedAt(System.currentTimeMillis());
        }

        bookingRepository.save(booking);

        showtime.setAvailableSeats(showtime.getAvailableSeats() - submittedSeats.size());
        showtimeRepository.save(showtime);

        System.out.println(
                "Seat LOCKED: " + booking.getSeatNumber() + " for 5 minutes."
        );

        return true;
    }

    // ================= SAVE BOOKING (persist any in-place change) =================

    public void saveBooking(Booking booking) {

        bookingRepository.save(booking);
    }

    @Transactional
    public void updateCustomerEmail(String oldEmail, String newEmail) {
        if (oldEmail == null || newEmail == null
                || oldEmail.isBlank() || newEmail.isBlank()
                || oldEmail.equalsIgnoreCase(newEmail)) {
            return;
        }
        bookingRepository.updateCustomerEmail(oldEmail.trim(), newEmail.trim());
    }

    public Booking getBookingByIdForUpdate(Long bookingId) {
        if (bookingId == null) {
            return null;
        }
        return bookingRepository.findByIdForUpdate(bookingId).orElse(null);
    }

    public Booking getBookingById(Long bookingId) {
        if (bookingId == null) {
            return null;
        }
        return bookingRepository.findById(bookingId).orElse(null);
    }

    // ================= CANCEL BOOKING =================

    public boolean cancelBooking(Long bookingId) {

        if (bookingId == null) {
            return false;
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);

        if (bookingOpt.isEmpty()) {
            return false;
        }

        Booking booking = bookingOpt.get();

        booking.setStatus("CANCELLED");
        booking.setLockExpiryTime(0);

        bookingRepository.save(booking);

        incrementAvailableSeatsForBooking(booking);

        System.out.println("Booking cancelled: " + booking.getId());

        return true;
    }

    // ================= RELEASE SEAT AFTER REFUND =================

    public boolean releaseSeat(Long bookingId) {

        if (bookingId == null) {
            return false;
        }

        Optional<Booking> bookingOpt = bookingRepository.findById(bookingId);

        if (bookingOpt.isEmpty()) {
            return false;
        }

        Booking booking = bookingOpt.get();

        booking.setStatus("REFUNDED");
        booking.setLockExpiryTime(0);

        bookingRepository.save(booking);

        incrementAvailableSeatsForBooking(booking);

        System.out.println("Seat released after refund: " + booking.getSeatNumber());

        return true;
    }

    // ================= AVAILABLE SEATS MANAGEMENT =================

    public void decrementAvailableSeatsForBooking(Booking booking) {

        if (booking == null || booking.getSeatNumber() == null) {
            return;
        }

        Showtime showtime = findShowtimeForBooking(booking);

        if (showtime == null) {
            return;
        }

        List<String> seats = Arrays.stream(booking.getSeatNumber().split(","))
                .map(String::trim)
                .filter(seat -> !seat.isEmpty())
                .toList();

        int newAvailable = Math.max(
                0,
                showtime.getAvailableSeats() - seats.size()
        );

        showtime.setAvailableSeats(newAvailable);
        showtimeRepository.save(showtime);

        System.out.println(
                "Available seats updated for showtime "
                + showtime.getId()
                + ": " + newAvailable
        );
    }

    @Transactional
    public void incrementAvailableSeatsForBooking(Booking booking) {

        if (booking == null || booking.getSeatNumber() == null) {
            return;
        }

        Showtime candidate = findShowtimeForBooking(booking);
        Showtime showtime = candidate == null || candidate.getId() == null
                ? null
                : showtimeRepository.findByIdForUpdate(candidate.getId()).orElse(null);

        if (showtime == null) {
            return;
        }

        List<String> seats = Arrays.stream(booking.getSeatNumber().split(","))
                .map(String::trim)
                .filter(seat -> !seat.isEmpty())
                .toList();

        int newAvailable = Math.min(
                showtime.getTotalSeats(),
                showtime.getAvailableSeats() + seats.size()
        );

        showtime.setAvailableSeats(newAvailable);
        showtimeRepository.save(showtime);

        System.out.println(
                "Available seats updated for showtime "
                + showtime.getId()
                + ": " + newAvailable
        );
    }

    private Showtime findShowtimeForBooking(Booking booking) {

        if (booking == null || booking.getMovieId() == null) {
            return null;
        }

        for (Showtime showtime : showtimeRepository.findAll()) {

            if (showtime.getMovieId() != null
                    && showtime.getMovieId().equals(booking.getMovieId())
                    && Objects.equals(showtime.getTheatre(), booking.getTheatre())
                    && Objects.equals(showtime.getDate(), booking.getDate())
                    && Objects.equals(showtime.getShowTime(), booking.getShowTime())) {

                return showtime;
            }
        }

        return null;
    }

    private boolean isActiveReservation(Booking booking) {
        return "PAID".equals(booking.getStatus())
                || ("LOCKED".equals(booking.getStatus())
                && (booking.getLockExpiryTime() <= 0
                || booking.getLockExpiryTime() > System.currentTimeMillis()));
    }

    private boolean matchesBooking(Showtime showtime, Booking booking) {
        return Objects.equals(showtime.getMovieId(), booking.getMovieId())
                && Objects.equals(showtime.getTheatre(), booking.getTheatre())
                && Objects.equals(showtime.getDate(), booking.getDate())
                && Objects.equals(showtime.getShowTime(), booking.getShowTime());
    }

    private boolean isFutureShowtime(Showtime showtime) {
        try {
            LocalDate date = LocalDate.parse(showtime.getDate().trim());
            Integer startMinute = ShowtimeService.parseTimeToMinutes(showtime.getShowTime());
            if (startMinute == null || date.isBefore(LocalDate.now())) {
                return false;
            }
            LocalTime now = LocalTime.now();
            return date.isAfter(LocalDate.now())
                    || startMinute > now.getHour() * 60 + now.getMinute();
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    private boolean isValidSeatForCapacity(String seat, int totalSeats) {
        if (!seat.matches("[A-Z]+[1-9][0-9]*")) {
            return false;
        }
        int seatsPerRow = totalSeats <= 24 ? 4
                : totalSeats <= 80 ? 8
                : totalSeats <= 200 ? 10 : 12;
        int separator = 0;
        while (separator < seat.length()
                && Character.isLetter(seat.charAt(separator))) {
            separator++;
        }

        long rowIndex = 0;
        for (int index = 0; index < separator; index++) {
            rowIndex = rowIndex * 26 + (seat.charAt(index) - 'A' + 1);
            if (rowIndex > totalSeats) {
                return false;
            }
        }
        rowIndex--;

        try {
            int column = Integer.parseInt(seat.substring(separator));
            return column <= seatsPerRow
                    && rowIndex * seatsPerRow + column <= totalSeats;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}
