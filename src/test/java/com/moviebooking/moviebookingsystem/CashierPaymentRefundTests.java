package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.repository.BookingRepository;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.repository.ShowtimeRepository;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.CashierService;
import com.moviebooking.moviebookingsystem.service.NotificationService;
import com.moviebooking.moviebookingsystem.service.RefundService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CashierPaymentRefundTests {

    private PaymentRepository paymentRepository;
    private BookingService bookingService;
    private CashierService cashierService;
    private RefundService refundService;

    private final List<Booking> bookings = new ArrayList<>();
    private final List<Payment> payments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        BookingRepository bookingRepository = mock(BookingRepository.class);
        NotificationService notificationService = mock(NotificationService.class);

        when(bookingRepository.findAll()).thenAnswer(call -> bookings);
        when(bookingRepository.findById(any())).thenAnswer(call -> bookings.stream()
                .filter(b -> b.getId() != null && b.getId().equals(call.getArgument(0)))
                .findFirst());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(call -> {
            Booking saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId((long) bookings.size() + 100);
            }
            return saved;
        });

        // Mirrors the real lookups: the successful row is the meaningful one
        when(paymentRepository.findFirstByBookingIdAndStatusOrderByIdDesc(any(), any()))
                .thenAnswer(call -> {
                    Long bookingId = call.getArgument(0);
                    String status = call.getArgument(1);
                    return payments.stream()
                            .filter(p -> bookingId.equals(p.getBookingId())
                                    && status.equals(p.getStatus()))
                            .reduce((first, second) -> second);
                });
        when(paymentRepository.existsByBookingIdAndStatus(any(), any()))
                .thenAnswer(call -> {
                    Long bookingId = call.getArgument(0);
                    String status = call.getArgument(1);
                    return payments.stream()
                            .anyMatch(p -> bookingId.equals(p.getBookingId())
                                    && status.equals(p.getStatus()));
                });
        when(paymentRepository.findFirstByBookingIdOrderByIdDesc(any()))
                .thenAnswer(call -> {
                    Long bookingId = call.getArgument(0);
                    return payments.stream()
                            .filter(p -> bookingId.equals(p.getBookingId()))
                            .reduce((first, second) -> second);
                });
        when(paymentRepository.save(any(Payment.class))).thenAnswer(call -> {
            Payment saved = call.getArgument(0);
            if (saved.getId() == null) {
                saved.setId((long) payments.size() + 1);
            }
            payments.add(saved);
            return saved;
        });

        bookingService = new BookingService(bookingRepository, mock(ShowtimeRepository.class));
        refundService = new RefundService(bookingService, paymentRepository, notificationService);
        cashierService = new CashierService(paymentRepository, bookingService, refundService);
    }


    // ================= PAYMENT: AMOUNT COMES FROM THE BOOKING =================

    @Test
    void paymentAmountAlwaysComesFromTheBookingPrice() {
        booking(1L, "LOCKED", 3000);

        Payment payment = cashierService.processPayment(1L, 0, "CASH");

        assertNotNull(payment);
        assertEquals(3000.0, payment.getAmount());
        assertEquals("SUCCESS", payment.getStatus());
    }

    @Test
    void aManuallyTypedAmountCannotOverrideTheBookingPrice() {
        booking(1L, "LOCKED", 3000);

        // A cashier tampering with the amount, in both directions
        Payment cheap = cashierService.processPayment(1L, 1, "CASH");
        assertEquals(3000.0, cheap.getAmount());
        assertEquals("SUCCESS", cheap.getStatus());

        booking(2L, "LOCKED", 2500);
        Payment expensive = cashierService.processPayment(2L, 999999, "CARD");
        assertEquals(2500.0, expensive.getAmount());
        assertEquals("SUCCESS", expensive.getStatus());
    }

    @Test
    void aZeroAmountSubmissionStillPaysTheFullBookingPrice() {
        booking(1L, "LOCKED", 3000);

        // The old code treated a submitted amount of 0 as a failure
        Payment payment = cashierService.processPayment(1L, 0, "CASH");

        assertEquals("SUCCESS", payment.getStatus());
        assertEquals(3000.0, payment.getAmount());
    }


    // ================= PAYMENT: VALIDATION =================

    @Test
    void aNonExistingBookingIsRejectedAndWritesNoPaymentRow() {
        Payment payment = cashierService.processPayment(9999L, 500, "CASH");

        assertNull(payment);
        assertTrue(payments.isEmpty(), "no payment row may be written for a made up booking");
    }

    @Test
    void aBookingThatIsNotLockedCannotBePaid() {
        Booking booking = booking(1L, "PAID", 3000);

        Payment payment = cashierService.processPayment(1L, 3000, "CASH");

        assertNotNull(payment);
        assertEquals("FAILED", payment.getStatus());
        assertEquals(3000.0, payment.getAmount());
        assertEquals("PAID", booking.getStatus());
    }

    @Test
    void anAlreadyPaidBookingCannotBePaidTwice() {
        booking(1L, "LOCKED", 3000);
        payment(50L, 1L, 3000, "SUCCESS");

        Payment second = cashierService.processPayment(1L, 3000, "CASH");

        assertEquals("FAILED", second.getStatus());
    }

    @Test
    void successfulPaymentMovesTheBookingToPaidAndClearsTheLock() {
        Booking booking = booking(1L, "LOCKED", 3000);
        booking.setLockExpiryTime(System.currentTimeMillis() + 300000);

        cashierService.processPayment(1L, 3000, "CASH");

        assertEquals("PAID", booking.getStatus());
        assertEquals(0, booking.getLockExpiryTime());
    }

    @Test
    void onlyLockedBookingsWithoutASuccessfulPaymentAreOfferedForPayment() {
        booking(1L, "LOCKED", 1000);
        booking(2L, "PAID", 2000);
        booking(3L, "AVAILABLE", 3000);
        booking(4L, "REFUNDED", 4000);
        booking(5L, "LOCKED", 5000);
        payment(90L, 5L, 5000, "SUCCESS");

        List<Booking> payable = cashierService.getPayableBookings();

        assertEquals(List.of(1L), payable.stream().map(Booking::getId).toList());
    }


    // ================= REFUND =================

    @Test
    void refundMarksBothThePaymentAndTheBookingAsRefunded() {
        Booking booking = booking(1L, "PAID", 3000);
        Payment paid = payment(10L, 1L, 3000, "SUCCESS");

        assertTrue(cashierService.processRefund(1L));

        assertEquals("REFUNDED", paid.getStatus());
        assertEquals("REFUNDED", booking.getStatus());
        assertTrue(booking.getRefundPercent() > 0);
        assertTrue(booking.getRefundAmount() > 0);
        assertEquals(0, booking.getLockExpiryTime());
    }

    @Test
    void refundWorksWhenTheBookingHasSeveralPaymentRows() {
        // A failed attempt followed by a successful one is normal once the
        // amount could be typed by hand. The newest row is the real payment.
        Booking booking = booking(1L, "PAID", 3000);
        payment(10L, 1L, 1, "FAILED");
        Payment success = payment(11L, 1L, 3000, "SUCCESS");

        assertTrue(cashierService.processRefund(1L));
        assertEquals("REFUNDED", success.getStatus());
        assertEquals("REFUNDED", booking.getStatus());
    }

    @Test
    void refundIsRejectedForANonExistingBooking() {
        assertFalse(cashierService.processRefund(9999L));
    }

    @Test
    void refundIsRejectedWhenThereIsNoPayment() {
        booking(1L, "PAID", 3000);

        assertFalse(cashierService.processRefund(1L));
    }

    @Test
    void refundIsRejectedWhenThePaymentDidNotSucceed() {
        Booking booking = booking(1L, "PAID", 3000);
        payment(10L, 1L, 3000, "FAILED");

        assertFalse(cashierService.processRefund(1L));
        assertEquals("PAID", booking.getStatus());
    }

    @Test
    void aBookingCannotBeRefundedTwice() {
        booking(1L, "PAID", 3000);
        payment(10L, 1L, 3000, "SUCCESS");

        assertTrue(cashierService.processRefund(1L));
        assertFalse(cashierService.processRefund(1L));
    }

    @Test
    void refundUsesTheAmountActuallyPaidNotTheBookingPrice() {
        Booking booking = booking(1L, "PAID", 3000);
        Payment paid = payment(10L, 1L, 2000, "SUCCESS");

        assertTrue(cashierService.processRefund(1L));

        assertEquals(2000, paid.getAmount());
        assertTrue(booking.getRefundAmount() <= 2000);
    }

    @Test
    void theSuccessfulPaymentIsUsedForTheLookup() {
        payment(10L, 1L, 3000, "SUCCESS");
        // A later failed attempt must not hide the real payment
        payment(11L, 1L, 3000, "FAILED");

        // The old findByBookingId would have thrown here because two rows match
        Payment found = cashierService.getPaymentByBookingId(1L);
        assertEquals(3000, found.getAmount());
        assertEquals("SUCCESS", found.getStatus());
    }

    @Test
    void aFailedAttemptSavedAfterPaymentDoesNotBlockTheRefund() {
        Booking booking = booking(1L, "PAID", 3000);
        Payment success = payment(10L, 1L, 3000, "SUCCESS");
        // The cashier tried to pay again after the booking was already PAID,
        // which stores a FAILED row on top of the successful one.
        payment(11L, 1L, 3000, "FAILED");

        assertTrue(cashierService.processRefund(1L));
        assertEquals("REFUNDED", success.getStatus());
        assertEquals("REFUNDED", booking.getStatus());
    }

    @Test
    void aLockedBookingWithAnEarlierFailedAttemptCanStillBePaid() {
        booking(1L, "LOCKED", 3000);
        payment(10L, 1L, 1, "FAILED");

        Payment result = cashierService.processPayment(1L, 0, "CASH");

        assertEquals("SUCCESS", result.getStatus());
        assertEquals(3000.0, result.getAmount());
    }


    // ================= HELPERS =================

    /**
     * Builds a booking and registers it in the fake repository, so
     * getAllBookings() finds it.
     */
    private Booking booking(Long id, String status, double price) {
        Booking booking = new Booking();
        booking.setId(id);
        booking.setMovieId(1L);
        booking.setMovieTitle("The Last Journey");
        booking.setTheatre("CineBook Main Hall");
        booking.setDate("2099-01-01");
        booking.setShowTime("7:00 PM");
        booking.setSeatNumber("A1");
        booking.setPrice(price);
        booking.setStatus(status);
        bookings.add(booking);
        return booking;
    }

    /**
     * Builds a payment row and registers it in the fake repository.
     */
    private Payment payment(Long id, Long bookingId, double amount, String status) {
        Payment payment = new Payment();
        payment.setId(id);
        payment.setBookingId(bookingId);
        payment.setAmount(amount);
        payment.setPaymentMethod("CASH");
        payment.setStatus(status);
        payments.add(payment);
        return payment;
    }
}
