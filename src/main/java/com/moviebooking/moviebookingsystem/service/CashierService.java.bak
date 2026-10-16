package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CashierService {

    private final PaymentRepository paymentRepository;
    private final BookingService bookingService;
    private final RefundService refundService;

    public CashierService(
            PaymentRepository paymentRepository,
            BookingService bookingService,
            RefundService refundService) {

        this.paymentRepository = paymentRepository;
        this.bookingService = bookingService;
        this.refundService = refundService;
    }

    /**
     * Bookings that can still be paid for: the seat lock is still held and no
     * successful payment has been taken yet.
     */
    public List<Booking> getPayableBookings() {

        return bookingService.getAllBookings().stream()
                .filter(booking -> booking.getId() != null)
                .filter(booking -> "LOCKED".equals(booking.getStatus()))
                .filter(booking -> !paymentRepository
                        .existsByBookingIdAndStatus(booking.getId(), "SUCCESS"))
                .toList();
    }

    // Process Payment
    public Payment processPayment(
            Long bookingId,
            double amount,
            String paymentMethod) {

        Booking selectedBooking = null;

        // Find booking
        for (Booking booking : bookingService.getAllBookings()) {

            if (booking.getId() != null
                    && booking.getId().equals(bookingId)) {

                selectedBooking = booking;
                break;
            }
        }

        // A booking that does not exist is rejected outright. No payment row is
        // written for it, otherwise junk rows accumulate for made up IDs.
        if (selectedBooking == null) {
            return null;
        }

        Payment payment = new Payment();

        payment.setBookingId(bookingId);
        payment.setPaymentMethod(paymentMethod);

        // The amount is always taken from the stored booking price. The value
        // submitted by the form is never trusted, so a cashier cannot pay an
        // arbitrary figure.
        payment.setAmount(selectedBooking.getPrice());

        // Check whether booking is still locked
        if (!"LOCKED".equals(selectedBooking.getStatus())) {

            payment.setStatus("FAILED");

            paymentRepository.save(payment);

            return payment;
        }

        Payment previousPayment = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, "SUCCESS")
                .orElse(null);

        if (previousPayment != null) {

            payment.setStatus("FAILED");

            paymentRepository.save(payment);

            return payment;
        }

        if (selectedBooking.getPrice() <= 0) {

            payment.setStatus("FAILED");

            paymentRepository.save(payment);

            return payment;
        }

        payment.setStatus("SUCCESS");

        // Change booking status to PAID and persist it - this
        // booking was fetched from the database, so the change
        // has to be saved explicitly or it is silently lost.
        selectedBooking.setStatus("PAID");
        selectedBooking.setLockExpiryTime(0);
        bookingService.saveBooking(selectedBooking);

        paymentRepository.save(payment);

        return payment;
    }

    // Get payment by Booking ID
    public Payment getPaymentByBookingId(Long bookingId) {

        if (bookingId == null) {
            return null;
        }

        // A booking can have more than one payment row (a failed attempt
        // followed by a successful one, or the other way round). The
        // successful row is the meaningful one, and findByBookingId throws
        // when several rows match.
        return paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, "SUCCESS")
                .orElseGet(() -> paymentRepository
                        .findFirstByBookingIdOrderByIdDesc(bookingId)
                        .orElse(null));
    }

    // Get all payments
    public List<Payment> getAllPayments() {

        return paymentRepository.findAll();
    }

    // Process Refund
    public boolean processRefund(Long bookingId) {
        return refundService.processCashierRefund(bookingId);
    }
}
