package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.controller.BookingController;
import com.moviebooking.moviebookingsystem.controller.MovieController;
import com.moviebooking.moviebookingsystem.controller.PaymentController;
import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.NotificationService;
import com.moviebooking.moviebookingsystem.service.RefundService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import com.moviebooking.moviebookingsystem.service.TheatreService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.ui.ExtendedModelMap;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerBookingSecurityAndSearchTests {

    @Test
    void movieSearchFiltersByTitleGenreAndShowtimeAvailability() {
        MovieService movieService = mock(MovieService.class);
        ShowtimeService showtimeService = mock(ShowtimeService.class);
        Movie drama = movie(1L, "Alpha", "Drama");
        Movie comedy = movie(2L, "Beta", "Comedy");
        when(movieService.getAllMovies()).thenReturn(List.of(drama, comedy));
        when(showtimeService.getFutureShowtimes()).thenReturn(List.of(showtime(1L, 1L, 3)));
        MovieController controller = new MovieController(movieService, showtimeService);

        ExtendedModelMap availableModel = new ExtendedModelMap();
        controller.getMovies("alp", "Drama", "available", availableModel);
        assertEquals(List.of(drama), availableModel.get("movies"));

        ExtendedModelMap unavailableMoviesModel = new ExtendedModelMap();
        controller.getMovies("", "", "unavailable", unavailableMoviesModel);
        assertEquals(List.of(comedy), unavailableMoviesModel.get("movies"));

        ExtendedModelMap unavailableModel = new ExtendedModelMap();
        controller.getMovies("does-not-exist", "", "unavailable", unavailableModel);
        assertTrue(((List<?>) unavailableModel.get("movies")).isEmpty());
    }

    @Test
    void customerPaymentIsOwnerCheckedAndRecordedAsSimulatedExactlyOnce() {
        BookingService bookingService = mock(BookingService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentController controller = new PaymentController(bookingService, paymentRepository);
        Booking booking = booking(71L, "customer@example.com", "LOCKED");
        when(bookingService.getBookingByIdForUpdate(71L)).thenReturn(booking);
        MockHttpSession session = customerSession("customer@example.com");

        String result = controller.processPayment(
                71L, "CARD", new ExtendedModelMap(), session);

        assertEquals("payment-success", result);
        assertEquals("PAID", booking.getStatus());
        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());
        assertEquals("SIMULATED", paymentCaptor.getValue().getStatus());
        assertEquals(2400.0, paymentCaptor.getValue().getAmount());

        when(paymentRepository.existsByBookingIdAndStatus(71L, "SIMULATED"))
                .thenReturn(true);
        assertEquals("redirect:/my-bookings?error=payment",
                controller.processPayment(71L, "CARD", new ExtendedModelMap(), session));
    }

    @Test
    void customerPaymentRejectsUnsupportedMethodsAndOtherCustomers() {
        BookingService bookingService = mock(BookingService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentController controller = new PaymentController(bookingService, paymentRepository);
        Booking booking = booking(71L, "owner@example.com", "LOCKED");
        when(bookingService.getBookingByIdForUpdate(71L)).thenReturn(booking);

        assertEquals("redirect:/my-bookings?error=payment",
                controller.processPayment(71L, "WIRE", new ExtendedModelMap(),
                        customerSession("owner@example.com")));
        assertEquals("redirect:/my-bookings?error=payment",
                controller.processPayment(71L, "CARD", new ExtendedModelMap(),
                        customerSession("other@example.com")));
        verify(paymentRepository, never()).save(any(Payment.class));
    }

    @Test
    void paymentDoesNotTreatExpiredSeatLocksAsPayable() {
        BookingService bookingService = mock(BookingService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        PaymentController controller = new PaymentController(bookingService, paymentRepository);
        Booking booking = booking(71L, "customer@example.com", "LOCKED");
        booking.setLockExpiryTime(System.currentTimeMillis() - 1);
        when(bookingService.getBookingById(71L)).thenReturn(booking);
        when(bookingService.getBookingByIdForUpdate(71L)).thenReturn(booking);

        assertEquals("redirect:/my-bookings?error=booking",
                controller.paymentPage(71L, new ExtendedModelMap(),
                        customerSession("customer@example.com")));
        assertEquals("redirect:/my-bookings?error=payment",
                controller.processPayment(71L, "CARD", new ExtendedModelMap(),
                        customerSession("customer@example.com")));
    }

    @Test
    void bookingDetailsNeverRevealAnotherCustomersBooking() {
        BookingService bookingService = mock(BookingService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingController controller = new BookingController(
                bookingService,
                mock(MovieService.class),
                mock(ShowtimeService.class),
                mock(RefundService.class),
                mock(TheatreService.class),
                paymentRepository);
        Booking booking = booking(71L, "owner@example.com", "PAID");
        when(bookingService.getBookingById(71L)).thenReturn(booking);
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("redirect:/my-bookings?error=booking",
                controller.bookingDetails(71L, customerSession("other@example.com"), model));
        verify(paymentRepository, never()).findFirstByBookingIdOrderByIdDesc(71L);
    }

    @Test
    void bookingDetailsShowsPersistedPaymentAndBookingValuesForItsOwner() {
        BookingService bookingService = mock(BookingService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        BookingController controller = new BookingController(
                bookingService,
                mock(MovieService.class),
                mock(ShowtimeService.class),
                mock(RefundService.class),
                mock(TheatreService.class),
                paymentRepository);
        Booking booking = booking(71L, "customer@example.com", "PAID");
        Payment payment = new Payment();
        payment.setBookingId(71L);
        payment.setStatus("SIMULATED");
        when(bookingService.getBookingById(71L)).thenReturn(booking);
        when(paymentRepository.findFirstByBookingIdOrderByIdDesc(71L))
                .thenReturn(Optional.of(payment));
        ExtendedModelMap model = new ExtendedModelMap();

        assertEquals("booking-details",
                controller.bookingDetails(71L, customerSession("customer@example.com"), model));
        assertEquals("SIMULATED", model.get("paymentStatus"));
        assertEquals(booking, model.get("booking"));
        assertFalse(((Booking) model.get("booking")).getSeatNumber().isBlank());
    }

    @Test
    void createBookingRejectsDuplicateSeatsWithoutThrowingOrCreatingReservation() {
        BookingService bookingService = mock(BookingService.class);
        MovieService movieService = mock(MovieService.class);
        ShowtimeService showtimeService = mock(ShowtimeService.class);
        TheatreService theatreService = mock(TheatreService.class);
        BookingController controller = new BookingController(
                bookingService,
                movieService,
                showtimeService,
                mock(RefundService.class),
                theatreService,
                mock(PaymentRepository.class));

        Movie movie = movie(1L, "Alpha", "Drama");
        Showtime showtime = new Showtime();
        showtime.setId(3L);
        showtime.setMovieId(1L);
        showtime.setMovieTitle("Alpha");
        showtime.setTheatre("Hall 1");
        showtime.setDate("2099-01-01");
        showtime.setShowTime("7:00 PM");
        showtime.setTicketPrice(1200);
        showtime.setTotalSeats(20);
        showtime.setAvailableSeats(20);
        when(movieService.findMovieById(1L)).thenReturn(movie);
        when(movieService.getAllMovies()).thenReturn(List.of(movie));
        when(showtimeService.findShowtimeByDetails(1L, "Hall 1", "2099-01-01", "7:00 PM"))
                .thenReturn(showtime);
        when(showtimeService.getFutureShowtimes()).thenReturn(List.of(showtime));
        when(bookingService.getAllBookings()).thenReturn(List.of());
        when(theatreService.getAllTheatres()).thenReturn(List.<Theatre>of());

        Booking submitted = new Booking();
        submitted.setMovieId(1L);
        submitted.setTheatre("Hall 1");
        submitted.setDate("2099-01-01");
        submitted.setShowTime("7:00 PM");
        submitted.setSeatNumber("A1, A1");

        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("booking", controller.createBooking(
                submitted, model, customerSession("customer@example.com")));
        assertEquals("The same seat was selected more than once.", model.get("error"));
        verify(bookingService, never()).createBooking(any(Booking.class));
    }

    @Test
    void cancellationRejectsAnotherCustomerAndPastShowtimes() {
        BookingService bookingService = mock(BookingService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        RefundService refundService = new RefundService(
                bookingService, paymentRepository, mock(NotificationService.class));
        User customer = customer("customer@example.com");

        Booking otherCustomerBooking = booking(72L, "owner@example.com", "PAID");
        when(bookingService.getBookingByIdForUpdate(72L))
                .thenReturn(otherCustomerBooking);
        assertFalse(refundService.cancelByCustomer(72L, customer));

        Booking completedBooking = booking(73L, "customer@example.com", "PAID");
        completedBooking.setDate("2000-01-01");
        when(bookingService.getBookingByIdForUpdate(73L)).thenReturn(completedBooking);
        assertFalse(refundService.cancelByCustomer(73L, customer));
        verify(bookingService, never()).incrementAvailableSeatsForBooking(any());
    }

    @Test
    void eligibleCustomerCancellationReleasesSeatsAndMarksPaymentRefunded() {
        BookingService bookingService = mock(BookingService.class);
        PaymentRepository paymentRepository = mock(PaymentRepository.class);
        RefundService refundService = new RefundService(
                bookingService, paymentRepository, mock(NotificationService.class));
        Booking booking = booking(74L, "customer@example.com", "PAID");
        Payment payment = new Payment();
        payment.setBookingId(74L);
        payment.setAmount(2400.0);
        payment.setStatus("SUCCESS");
        when(bookingService.getBookingByIdForUpdate(74L)).thenReturn(booking);
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(74L, "SUCCESS"))
                .thenReturn(Optional.of(payment));

        assertTrue(refundService.cancelByCustomer(74L, customer("customer@example.com")));
        assertEquals("CANCELLED", booking.getStatus());
        assertEquals("REFUND_SIMULATED", payment.getStatus());
        assertTrue(booking.getCancelledAt() > 0);
        verify(bookingService).incrementAvailableSeatsForBooking(booking);
    }

    private static User customer(String email) {
        User customer = new User();
        customer.setEmail(email);
        customer.setRole("CUSTOMER");
        return customer;
    }

    private static MockHttpSession customerSession(String email) {
        User customer = new User();
        customer.setId(1L);
        customer.setEmail(email);
        customer.setRole("CUSTOMER");
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedInUser", customer);
        return session;
    }

    private static Booking booking(Long id, String email, String status) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setCustomerEmail(email);
        booking.setStatus(status);
        booking.setMovieTitle("Alpha");
        booking.setTheatre("Hall 1");
        booking.setDate("2099-01-01");
        booking.setShowTime("7:00 PM");
        booking.setSeatNumber("A1, A2");
        booking.setPrice(2400.0);
        booking.setLockExpiryTime(System.currentTimeMillis() + 60_000);
        return booking;
    }

    private static Movie movie(Long id, String title, String genre) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        movie.setGenre(genre);
        return movie;
    }

    private static Showtime showtime(Long id, Long movieId, int availableSeats) {
        Showtime showtime = new Showtime();
        showtime.setId(id);
        showtime.setMovieId(movieId);
        showtime.setDate("2099-01-01");
        showtime.setShowTime("7:00 PM");
        showtime.setAvailableSeats(availableSeats);
        return showtime;
    }
}
