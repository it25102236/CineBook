package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.BookingRepository;
import com.moviebooking.moviebookingsystem.repository.ShowtimeRepository;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.NotificationService;
import com.moviebooking.moviebookingsystem.service.RefundService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class CustomerBookingDatabaseIntegrationTests {

    private static final String CUSTOMER_EMAIL = "booking-db-test@example.com";

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ShowtimeRepository showtimeRepository;

    @Autowired
    private BookingService bookingService;

    @Autowired
    private RefundService refundService;

    @MockitoBean
    private NotificationService notificationService;

    private Showtime testShowtime;

    @AfterEach
    void removeTestRows() {
        bookingRepository.deleteAll(
                bookingRepository.findByCustomerEmailIgnoreCaseOrderByIdDesc(CUSTOMER_EMAIL));
        if (testShowtime != null && testShowtime.getId() != null) {
            showtimeRepository.deleteById(testShowtime.getId());
        }
    }

    @Test
    void bookingIsPersistedSeatCollisionIsRejectedAndCancellationReleasesSeats() {
        testShowtime = new Showtime();
        testShowtime.setMovieId(87654321L);
        testShowtime.setMovieTitle("Customer Database Integration Test");
        testShowtime.setTheatre("Customer DB Integration Hall");
        testShowtime.setDate(LocalDate.now().plusDays(3).toString());
        testShowtime.setShowTime("7:00 PM");
        testShowtime.setTotalSeats(20);
        testShowtime.setAvailableSeats(20);
        testShowtime.setTicketPrice(1250);
        testShowtime = showtimeRepository.saveAndFlush(testShowtime);

        Booking first = booking("A1, A2");
        first.setMovieId(testShowtime.getMovieId());
        first.setTheatre(testShowtime.getTheatre());
        first.setDate(testShowtime.getDate());
        first.setShowTime(testShowtime.getShowTime());

        assertTrue(bookingService.createBooking(first));
        assertEquals(2500.0, first.getPrice());
        assertEquals(18, showtimeRepository.findById(testShowtime.getId())
                .orElseThrow().getAvailableSeats());
        assertEquals(1, bookingService.getBookingsForCustomer(CUSTOMER_EMAIL).size());

        Booking collision = booking("A2");
        collision.setMovieId(testShowtime.getMovieId());
        collision.setTheatre(testShowtime.getTheatre());
        collision.setDate(testShowtime.getDate());
        collision.setShowTime(testShowtime.getShowTime());
        assertFalse(bookingService.createBooking(collision));
        assertEquals(1, bookingRepository.findByCustomerEmailIgnoreCaseOrderByIdDesc(
                CUSTOMER_EMAIL).size());

        User customer = new User();
        customer.setEmail(CUSTOMER_EMAIL);
        customer.setRole("CUSTOMER");
        assertTrue(refundService.cancelByCustomer(first.getId(), customer));
        assertEquals("CANCELLED", bookingRepository.findById(first.getId())
                .orElseThrow().getStatus());
        assertEquals(20, showtimeRepository.findById(testShowtime.getId())
                .orElseThrow().getAvailableSeats());
        assertTrue(bookingRepository.findByMovieIdAndTheatreAndDateAndShowTime(
                testShowtime.getMovieId(),
                testShowtime.getTheatre(),
                testShowtime.getDate(),
                testShowtime.getShowTime())
                .stream()
                .filter(booking -> "LOCKED".equals(booking.getStatus())
                        || "PAID".equals(booking.getStatus()))
                .map(Booking::getSeatNumber)
                .noneMatch("A1, A2"::equals));
    }

    private Booking booking(String seats) {
        Booking booking = new Booking();
        booking.setMovieId(87654321L);
        booking.setMovieTitle("Customer Database Integration Test");
        booking.setTheatre("Customer DB Integration Hall");
        booking.setDate(LocalDate.now().plusDays(3).toString());
        booking.setShowTime("7:00 PM");
        booking.setSeatNumber(seats);
        booking.setCustomerEmail(CUSTOMER_EMAIL);
        return booking;
    }
}
