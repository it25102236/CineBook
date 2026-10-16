package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Service
public class RefundService {

    private static final DateTimeFormatter SHOW_TIME_FORMAT =
            DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final NotificationService notificationService;

    public RefundService(
            BookingService bookingService,
            PaymentRepository paymentRepository,
            NotificationService notificationService) {
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.notificationService = notificationService;
    }

    public void addEstimate(Booking booking) {
        int percent = refundPercent(booking);
        booking.setRefundEstimatePercent(percent);
        booking.setRefundEstimateAmount(refundAmount(booking.getPrice(), percent));
    }

    public int refundPercent(Booking booking) {
        try {
            LocalDate date = LocalDate.parse(booking.getDate().trim());
            LocalTime time = LocalTime.parse(booking.getShowTime().trim(), SHOW_TIME_FORMAT);
            Duration untilShow = Duration.between(LocalDateTime.now(), LocalDateTime.of(date, time));
            if (untilShow.compareTo(Duration.ofHours(24)) > 0) {
                return 75;
            }
            if (untilShow.compareTo(Duration.ofHours(12)) >= 0) {
                return 50;
            }
            return 25;
        } catch (RuntimeException exception) {
            return 25;
        }
    }

    public double refundAmount(double amount, int percent) {
        return Math.round(amount * percent) / 100.0;
    }

    @Transactional
    public boolean cancelByCustomer(Long bookingId, User customer) {
        Booking booking = bookingService.getBookingByIdForUpdate(bookingId);
        if (booking == null || customer == null
                || !"CUSTOMER".equalsIgnoreCase(customer.getRole())
                || customer.getEmail() == null
                || booking.getCustomerEmail() == null
                || !booking.getCustomerEmail().equalsIgnoreCase(customer.getEmail())
                || !("PAID".equals(booking.getStatus()) || "LOCKED".equals(booking.getStatus()))
                || ("LOCKED".equals(booking.getStatus())
                && booking.getLockExpiryTime() > 0
                && booking.getLockExpiryTime() <= System.currentTimeMillis())
                || !isUpcomingShowtime(booking)) {
            return false;
        }

        Payment payment = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, "SUCCESS")
                .orElse(null);
        int percent = 0;
        double amount = 0;
        if (payment != null && "SUCCESS".equals(payment.getStatus())) {
            percent = refundPercent(booking);
            amount = refundAmount(payment.getAmount(), percent);
            payment.setRefundPercent(percent);
            payment.setRefundAmount(amount);
            payment.setStatus("REFUND_SIMULATED");
            paymentRepository.save(payment);
        }

        booking.setStatus("CANCELLED");
        booking.setLockExpiryTime(0);
        booking.setCancelledAt(System.currentTimeMillis());
        booking.setRefundPercent(percent);
        booking.setRefundAmount(amount);
        bookingService.saveBooking(booking);
        bookingService.incrementAvailableSeatsForBooking(booking);

        String summary = "Booking #" + booking.getId() + " for " + booking.getMovieTitle()
                + " was cancelled."
                + (payment == null
                ? " No real payment-provider charge was made; no refund is due."
                : " Simulated refund estimate: " + percent + "% (LKR " + amount
                + "). No payment provider processed a refund.");
        notificationService.notifySupport(
                "Customer booking cancelled",
                summary,
                "/support"
        );
        notificationService.notifyCustomer(
                customer.getEmail(),
                "Booking cancelled",
                summary,
                "/my-bookings"
        );
        return true;
    }

    private boolean isUpcomingShowtime(Booking booking) {
        try {
            LocalDate showDate = LocalDate.parse(booking.getDate().trim());
            Integer startMinute = ShowtimeService.parseTimeToMinutes(booking.getShowTime());
            if (startMinute == null || showDate.isBefore(LocalDate.now())) {
                return false;
            }
            return showDate.isAfter(LocalDate.now())
                    || startMinute > LocalTime.now().getHour() * 60 + LocalTime.now().getMinute();
        } catch (DateTimeParseException | NullPointerException exception) {
            return false;
        }
    }

    @Transactional
    public boolean processCashierRefund(Long bookingId) {
        if (bookingId == null) {
            return false;
        }
        Booking booking = bookingService.getBookingById(bookingId);
        // The successful row is refunded, not simply the newest one: a booking
        // can have a failed attempt saved after the payment succeeded, and
        // findByBookingId throws outright when several rows match.
        Payment payment = paymentRepository
                .findFirstByBookingIdAndStatusOrderByIdDesc(bookingId, "SUCCESS")
                .orElse(null);
        if (booking == null || payment == null) {
            return false;
        }
        // Double-check that the payment is still SUCCESS (though locked)
        if (!"SUCCESS".equals(payment.getStatus())) {
            return false;
        }

        int percent = refundPercent(booking);
        double amount = refundAmount(payment.getAmount(), percent);
        payment.setRefundPercent(percent);
        payment.setRefundAmount(amount);
        payment.setStatus("REFUNDED");
        paymentRepository.save(payment);

        booking.setStatus("REFUNDED");
        booking.setLockExpiryTime(0);
        booking.setCancelledAt(System.currentTimeMillis());
        booking.setRefundPercent(percent);
        booking.setRefundAmount(amount);
        bookingService.saveBooking(booking);
        bookingService.incrementAvailableSeatsForBooking(booking);

        String summary = "Refund processed for booking #" + booking.getId() + ": "
                + percent + "% (LKR " + amount + ").";
        notificationService.notifySupport(
                "Refund processed",
                summary,
                "/cashier/transactions"
        );
        notificationService.notifyCustomer(
                booking.getCustomerEmail(),
                "Refund processed",
                summary,
                "/my-bookings"
        );
        return true;
    }
}