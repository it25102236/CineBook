package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.repository.BookingRepository;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.repository.ShowtimeRepository;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.NotificationService;
import com.moviebooking.moviebookingsystem.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefundAndGroupBookingTests {

    private BookingRepository bookingRepository;
    private ShowtimeRepository showtimeRepository;
    private BookingService bookingService;
    private RefundService refundService;

    @BeforeEach
    void setUp() {
        bookingRepository = mock(BookingRepository.class);
        showtimeRepository = mock(ShowtimeRepository.class);
        bookingService = new BookingService(bookingRepository, showtimeRepository);
        refundService = new RefundService(
                bookingService,
                mock(PaymentRepository.class),
                mock(NotificationService.class)
        );
    }

    @Test
    void refundPolicyUsesTheThreeTimeBandsAndRoundsToCents() {
        assertEquals(75, refundService.refundPercent(bookingStartingIn(25)));
        assertEquals(50, refundService.refundPercent(bookingStartingIn(18)));
        assertEquals(25, refundService.refundPercent(bookingStartingIn(6)));
        assertEquals(14.99, refundService.refundAmount(19.99, 75));
    }

    @Test
    void groupBookingRejectsAnyAlreadyReservedSeat() {
        Showtime showtime = showtime();
        when(showtimeRepository.findAll()).thenReturn(List.of(showtime));
        when(showtimeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(showtime));

        Booking existing = booking("A1", "LOCKED");
        when(bookingRepository.findAll()).thenReturn(List.of(existing));
        when(bookingRepository.findByMovieIdAndTheatreAndDateAndShowTime(
                1L, "Hall 1", "2099-01-01", "7:00 PM"))
                .thenReturn(List.of(existing));

        Booking group = booking("A1, A2", null);

        assertFalse(bookingService.createBooking(group));
    }

    @Test
    void groupBookingStoresOneFiveMinuteLockForAllSeats() {
        Showtime showtime = showtime();
        when(bookingRepository.findAll()).thenReturn(List.of());
        when(showtimeRepository.findAll()).thenReturn(List.of(showtime));
        when(showtimeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(showtime));
        when(bookingRepository.save(any(Booking.class))).thenAnswer(call -> call.getArgument(0));
        when(showtimeRepository.save(any(Showtime.class))).thenAnswer(call -> call.getArgument(0));

        Booking group = booking("B1, B2", null);

        assertTrue(bookingService.createBooking(group));
        assertEquals("B1, B2", group.getSeatNumber());
        assertEquals("LOCKED", group.getStatus());
        assertEquals(2000.0, group.getPrice());
        assertTrue(group.getLockExpiryTime() - System.currentTimeMillis() <= 5 * 60 * 1000);
        assertTrue(group.getCreatedAt() > 0);
    }

    @Test
    void groupBookingRejectsDuplicateAndOutOfCapacitySeatLabels() {
        Showtime showtime = showtime();
        when(bookingRepository.findAll()).thenReturn(List.of());
        when(showtimeRepository.findAll()).thenReturn(List.of(showtime));
        when(showtimeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(showtime));

        assertFalse(bookingService.createBooking(booking("A1, A1", null)));
        assertFalse(bookingService.createBooking(booking("F1", null)));
    }

    @Test
    void groupBookingRejectsMoreSeatsThanRemainAvailable() {
        Showtime showtime = showtime();
        showtime.setAvailableSeats(1);
        when(bookingRepository.findAll()).thenReturn(List.of());
        when(showtimeRepository.findAll()).thenReturn(List.of(showtime));
        when(showtimeRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(showtime));

        assertFalse(bookingService.createBooking(booking("A1, A2", null)));
    }

    private Booking bookingStartingIn(int hours) {
        LocalDateTime show = LocalDateTime.now().plusHours(hours).plusMinutes(2);
        Booking booking = new Booking();
        booking.setDate(show.toLocalDate().toString());
        booking.setShowTime(show.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)));
        booking.setPrice(1000);
        return booking;
    }

    private Booking booking(String seats, String status) {
        Booking booking = new Booking();
        booking.setMovieId(1L);
        booking.setMovieTitle("Movie");
        booking.setTheatre("Hall 1");
        booking.setDate("2099-01-01");
        booking.setShowTime("7:00 PM");
        booking.setSeatNumber(seats);
        booking.setStatus(status);
        booking.setCustomerEmail("customer@example.com");
        return booking;
    }

    private Showtime showtime() {
        Showtime showtime = new Showtime();
        showtime.setId(1L);
        showtime.setMovieId(1L);
        showtime.setMovieTitle("Movie");
        showtime.setTheatre("Hall 1");
        showtime.setDate("2099-01-01");
        showtime.setShowTime("7:00 PM");
        showtime.setTotalSeats(20);
        showtime.setAvailableSeats(20);
        showtime.setTicketPrice(1000.0);
        return showtime;
    }
}
