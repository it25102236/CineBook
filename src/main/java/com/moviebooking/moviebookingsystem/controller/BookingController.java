package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.RefundService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import com.moviebooking.moviebookingsystem.service.TheatreService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;
import jakarta.servlet.http.HttpSession;

import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Controller
public class BookingController {

    private final BookingService bookingService;
    private final MovieService movieService;
    private final ShowtimeService showtimeService;
        private final RefundService refundService;
        private final TheatreService theatreService;
        private final PaymentRepository paymentRepository;


    // ================= CONSTRUCTOR =================

    public BookingController(
            BookingService bookingService,
            MovieService movieService,
            ShowtimeService showtimeService,
            RefundService refundService,
            TheatreService theatreService,
            PaymentRepository paymentRepository) {

        this.bookingService = bookingService;
        this.movieService = movieService;
        this.showtimeService = showtimeService;
        this.refundService = refundService;
        this.theatreService = theatreService;
        this.paymentRepository = paymentRepository;
    }


    // ================= BOOKING PAGE =================

    @GetMapping("/booking")
    public String bookingPage(
            @RequestParam(required = false) Long movieId,
            @RequestParam(required = false) Long showtimeId,
            @RequestParam(required = false) String movieTitle,
            @RequestParam(required = false) String theatre,
            @RequestParam(required = false) String date,
            @RequestParam(required = false) String showTime,
            @RequestParam(required = false) Double ticketPrice,
            Model model,
            HttpSession session) {

                String accessRedirect = requireCustomer(session, "/booking");
                if (accessRedirect != null) {
                        return accessRedirect;
                }

        Booking booking = new Booking();

        Showtime selectedShowtime = null;

        if (showtimeId != null) {
            selectedShowtime = showtimeService.findShowtimeById(showtimeId);
        }

        if (selectedShowtime != null) {
            booking.setMovieId(selectedShowtime.getMovieId());
            booking.setMovieTitle(selectedShowtime.getMovieTitle());
            booking.setTheatre(selectedShowtime.getTheatre());
            booking.setDate(selectedShowtime.getDate());
            booking.setShowTime(selectedShowtime.getShowTime());
            model.addAttribute("totalSeats", selectedShowtime.getTotalSeats());
            model.addAttribute("availableSeats", selectedShowtime.getAvailableSeats());
        } else {
            booking.setMovieId(movieId);
            Showtime fromDetails = showtimeService.findShowtimeByDetails(
                    movieId, theatre, date, showTime);
            if (fromDetails != null
                    && showtimeService.getFutureShowtimes().stream()
                    .anyMatch(showtime -> Objects.equals(showtime.getId(), fromDetails.getId()))) {
                selectedShowtime = fromDetails;
                booking.setMovieId(fromDetails.getMovieId());
                booking.setMovieTitle(fromDetails.getMovieTitle());
                booking.setTheatre(fromDetails.getTheatre());
                booking.setDate(fromDetails.getDate());
                booking.setShowTime(fromDetails.getShowTime());
                model.addAttribute("totalSeats", fromDetails.getTotalSeats());
                model.addAttribute("availableSeats", fromDetails.getAvailableSeats());
            } else if (movieService.findMovieById(movieId) != null) {
                booking.setMovieTitle(movieService.findMovieById(movieId).getTitle());
            }
        }

        double trustedTicketPrice = selectedShowtime == null
                ? 0 : selectedShowtime.getTicketPrice();
        booking.setPrice(trustedTicketPrice);

        model.addAttribute("booking", booking);
        model.addAttribute("ticketPrice", trustedTicketPrice);


        // ================= MOVIES =================

        List<Movie> movies = movieService.getAllMovies();

        model.addAttribute(
                "movies",
                movies
        );


        // ================= SHOWTIMES =================

        addBookingShowtimeOptions(model);


        // ================= BOOKED SEATS =================

        addBookedSeats(model);
        addCustomerEmail(booking, session);


        return "booking";
    }


    // ================= CREATE BOOKING =================

    @PostMapping("/booking")
    public String createBooking(
            @ModelAttribute Booking booking,
            Model model,
            HttpSession session) {

                String accessRedirect = requireCustomer(session, "/booking");
                if (accessRedirect != null) {
                        return accessRedirect;
                }

                booking.setId(null);
                booking.setStatus(null);
                booking.setLockExpiryTime(0);
                booking.setCreatedAt(0);
                booking.setCancelledAt(0);
                booking.setRefundPercent(0);
                booking.setRefundAmount(0);
        addCustomerEmail(booking, session);
        List<String> selectedSeats = booking.getSeatNumber() == null
                ? List.of()
                : Arrays.stream(booking.getSeatNumber().split(",", -1))
                .map(seat -> seat.trim().toUpperCase(Locale.ROOT))
                .toList();
        boolean duplicateSeats = new HashSet<>(selectedSeats).size() != selectedSeats.size();

        Movie selectedMovie = movieService.findMovieById(booking.getMovieId());
        Showtime matchedShowtime = selectedMovie == null
                ? null
                : showtimeService.findShowtimeByDetails(
                booking.getMovieId(),
                booking.getTheatre(),
                booking.getDate(),
                booking.getShowTime());
        boolean validFutureShowtime = matchedShowtime != null
                && showtimeService.getFutureShowtimes().stream()
                .anyMatch(showtime -> Objects.equals(showtime.getId(), matchedShowtime.getId()));

        double pricePerSeat = validFutureShowtime
                ? matchedShowtime.getTicketPrice() : 0;
        booking.setPrice(validFutureShowtime
                ? pricePerSeat * selectedSeats.size() : 0);

        if (!validFutureShowtime
                || selectedSeats.isEmpty()
                || selectedSeats.stream().anyMatch(String::isEmpty)
                || duplicateSeats) {
            populateBookingModel(
                    model,
                    booking,
                    matchedShowtime,
                    pricePerSeat
            );
            model.addAttribute(
                    "error",
                    !validFutureShowtime
                            ? "Choose a valid future showtime from the available list."
                            : selectedSeats.isEmpty() || selectedSeats.stream().anyMatch(String::isEmpty)
                            ? "Select at least one valid seat."
                            : "The same seat was selected more than once."
            );
            addBookedSeats(model);
            return "booking";
        }

        boolean created =
                bookingService.createBooking(booking);


        // ================= BOOKING FAILED =================

        if (!created) {

            populateBookingModel(
                    model,
                    booking,
                    showtimeService.findShowtimeByDetails(
                            booking.getMovieId(),
                            booking.getTheatre(),
                            booking.getDate(),
                            booking.getShowTime()
                    ),
                    pricePerSeat
            );

            model.addAttribute(
                    "error",
                    "Sorry! This seat is no longer available. Please select another seat."
            );

            addBookedSeats(model);

            return "booking";
        }


        // ================= BOOKING SUCCESS =================

        System.out.println(
                "Booking created successfully."
        );

        System.out.println(
                "Seat LOCKED: "
                        + booking.getSeatNumber()
        );

        System.out.println(
                "Lock expires at: "
                        + booking.getLockExpiryTime()
        );


        model.addAttribute(
                "booking",
                booking
        );


        // Countdown timer
        model.addAttribute(
                "lockSuccess",
                true
        );


        // Expiry time
        model.addAttribute(
                "lockExpiryTime",
                booking.getLockExpiryTime()
        );


        // Movies and showtimes
        populateBookingModel(
                model,
                booking,
                showtimeService.findShowtimeByDetails(
                        booking.getMovieId(),
                        booking.getTheatre(),
                        booking.getDate(),
                        booking.getShowTime()
                ),
                pricePerSeat
        );

        // ================= BOOKED SEATS =================

        addBookedSeats(model);

        return "booking";
    }


    // ================= SHARED BOOKING MODEL =================

    private void populateBookingModel(
            Model model,
            Booking booking,
            Showtime selectedShowtime,
            double pricePerSeat) {

        model.addAttribute("booking", booking);
        model.addAttribute("movies", movieService.getAllMovies());
        model.addAttribute("ticketPrice", pricePerSeat);

        if (selectedShowtime != null) {
            model.addAttribute("totalSeats", selectedShowtime.getTotalSeats());
            model.addAttribute("availableSeats", selectedShowtime.getAvailableSeats());
        }

        addBookingShowtimeOptions(model);
    }


    // ================= DYNAMIC BOOKING OPTIONS =================
    // Flattens the future showtimes saved by the Theatre Manager so the
    // booking page can build its Theatre / Date / Showtime cascade
    // entirely from live data, with no hard-coded values. Each option also
    // carries the theatre location so the page can show it as soon as the
    // customer picks a theatre.

    private void addBookingShowtimeOptions(Model model) {

        // Theatre name (lower-cased) to location, resolved once per request
        Map<String, String> theatreLocations = theatreService.getAllTheatres()
                .stream()
                .filter(theatre -> theatre.getName() != null
                        && !theatre.getName().isBlank())
                .collect(Collectors.toMap(
                        theatre -> theatre.getName().trim().toLowerCase(),
                        theatre -> theatre.getLocation() == null
                                ? ""
                                : theatre.getLocation().trim(),
                        (first, second) -> first));

        List<Map<String, Object>> bookingShowtimes =
                showtimeService.getFutureShowtimes()
                        .stream()
                        .filter(showtime ->
                                showtime.getTheatre() != null
                                        && !showtime.getTheatre().isBlank()
                                        && showtime.getDate() != null
                                        && !showtime.getDate().isBlank()
                                        && showtime.getShowTime() != null
                                        && !showtime.getShowTime().isBlank())
                        .map(showtime -> {
                            Map<String, Object> option = new LinkedHashMap<>();
                            option.put("theatre", showtime.getTheatre());
                            option.put("theatreLocation", theatreLocations.getOrDefault(
                                    showtime.getTheatre().trim().toLowerCase(), ""));
                            option.put("date", showtime.getDate());
                            option.put("showTime", showtime.getShowTime());
                            option.put("movieId", showtime.getMovieId());
                            option.put("movieTitle", showtime.getMovieTitle());
                            option.put("ticketPrice", showtime.getTicketPrice());
                            option.put("totalSeats", showtime.getTotalSeats());
                            option.put("availableSeats", showtime.getAvailableSeats());
                            return option;
                        })
                        .toList();

        model.addAttribute("bookingShowtimes", bookingShowtimes);
    }

    private void addBookedSeats(Model model) {
        List<Map<String, Object>> seatReservations = bookingService.getAllBookings()
                .stream()
                .filter(existingBooking -> "LOCKED".equals(existingBooking.getStatus())
                        || "PAID".equals(existingBooking.getStatus()))
                .map(existingBooking -> {
                    Map<String, Object> reservation = new LinkedHashMap<>();
                    reservation.put("movieId", existingBooking.getMovieId());
                    reservation.put("theatre", existingBooking.getTheatre());
                    reservation.put("date", existingBooking.getDate());
                    reservation.put("showTime", existingBooking.getShowTime());
                    reservation.put("seats", existingBooking.getSeatNumber() == null
                            ? List.of()
                            : Arrays.stream(existingBooking.getSeatNumber().split(","))
                            .map(String::trim)
                            .toList());
                    return reservation;
                })
                .toList();
        model.addAttribute("bookedSeats", List.of());
        model.addAttribute("seatReservations", seatReservations);
    }

    private void addCustomerEmail(Booking booking, HttpSession session) {
                booking.setCustomerEmail(null);
        User loggedInUser = (User) session.getAttribute("loggedInUser");
        if (loggedInUser != null
                && "CUSTOMER".equalsIgnoreCase(loggedInUser.getRole())) {
            booking.setCustomerEmail(loggedInUser.getEmail());
        }
    }


    // ================= BOOKING SUCCESS =================

    @GetMapping("/booking-success")
    public String bookingSuccess() {

        return "booking-success";
    }


    // ================= MY BOOKINGS =================

    @GetMapping("/my-bookings")
    public String myBookings(Model model, HttpSession session) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
                String accessRedirect = requireCustomer(session, "/my-bookings");
                if (accessRedirect != null) {
                        return accessRedirect;
                }

        List<Booking> bookings = bookingService.getBookingsForCustomer(loggedInUser.getEmail());

        List<Booking> sortedBookings = bookings.stream()
                .sorted(Comparator.comparingLong(this::bookingSortTime).reversed())
                .toList();
        Map<Long, String> paymentStatuses = sortedBookings.stream()
                .filter(booking -> booking.getId() != null)
                .collect(Collectors.toMap(
                        Booking::getId,
                        booking -> paymentRepository
                                .findFirstByBookingIdOrderByIdDesc(booking.getId())
                                .map(Payment::getStatus)
                                .orElse("NOT PAID")));
        sortedBookings.stream()
                .filter(booking -> "PAID".equals(booking.getStatus()))
                .forEach(booking -> {
                    if ("SUCCESS".equals(paymentStatuses.get(booking.getId()))) {
                        refundService.addEstimate(booking);
                    } else {
                        booking.setRefundEstimatePercent(0);
                        booking.setRefundEstimateAmount(0);
                    }
                });
        model.addAttribute("bookings", sortedBookings);
        model.addAttribute("paymentStatuses", paymentStatuses);

        return "my-bookings";
    }

    @GetMapping("/booking/details")
    public String bookingDetails(
            @RequestParam Long bookingId,
            HttpSession session,
            Model model) {
        String accessRedirect = requireCustomer(session, "/my-bookings");
        if (accessRedirect != null) {
            return accessRedirect;
        }

        User customer = (User) session.getAttribute("loggedInUser");
        Booking booking = bookingService.getBookingById(bookingId);
        if (booking == null
                || booking.getCustomerEmail() == null
                || !booking.getCustomerEmail().equalsIgnoreCase(customer.getEmail())) {
            return "redirect:/my-bookings?error=booking";
        }

        Payment payment = booking.getId() == null ? null
                : paymentRepository.findFirstByBookingIdOrderByIdDesc(booking.getId()).orElse(null);
        model.addAttribute("booking", booking);
        model.addAttribute("payment", payment);
        model.addAttribute("paymentStatus", payment == null ? "NOT PAID" : payment.getStatus());
        return "booking-details";
    }

    private long bookingSortTime(Booking booking) {
        return Math.max(
                Math.max(booking.getCreatedAt(), booking.getCancelledAt()),
                booking.getId() == null ? 0 : booking.getId()
        );
    }
    // ================= CANCEL BOOKING =================

    @PostMapping("/booking/cancel")
    public String cancelBooking(
            @RequestParam Long bookingId,
            HttpSession session) {

                User customer = (User) session.getAttribute("loggedInUser");
                if (customer == null
                                || !"CUSTOMER".equalsIgnoreCase(customer.getRole())) {
                        return "redirect:/login?redirect=/my-bookings";
                }
                return refundService.cancelByCustomer(bookingId, customer)
                        ? "redirect:/my-bookings?cancelled=true"
                        : "redirect:/my-bookings?cancelError=true";
    }

        private String requireCustomer(HttpSession session, String returnPath) {
                User user = (User) session.getAttribute("loggedInUser");
                if (user == null) {
                        return "redirect:/login?redirect=" + returnPath;
                }
                return "CUSTOMER".equalsIgnoreCase(user.getRole()) ? null : "redirect:/";
        }

}