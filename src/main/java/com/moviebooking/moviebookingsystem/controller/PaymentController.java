package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Locale;
import java.util.Set;

@Controller
public class PaymentController {

    private static final Set<String> SUPPORTED_METHODS = Set.of("CARD", "CASH", "ONLINE");

    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;

    public PaymentController(
            BookingService bookingService,
            PaymentRepository paymentRepository) {

        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
    }

    // ================= PAYMENT PAGE =================

    @GetMapping("/payment")
    public String paymentPage(
            @RequestParam Long bookingId,
            Model model,
            HttpSession session) {

        String accessRedirect = requireCustomer(session);
        if (accessRedirect != null) {
            return accessRedirect;
        }
        bookingService.releaseExpiredSeats();
        Booking booking = bookingService.getBookingById(bookingId);

        if (!isOwnedByCurrentCustomer(booking, session)
                || !isPayable(booking)) {
            return "redirect:/my-bookings?error=booking";
        }

        model.addAttribute("booking", booking);
        model.addAttribute("simulatedPayment", true);
        return "payment";
    }


    // ================= PROCESS PAYMENT =================

    @PostMapping("/payment/process")
    @Transactional
    public String processPayment(
            @RequestParam Long bookingId,
            @RequestParam String paymentMethod,
            Model model,
            HttpSession session) {

        String accessRedirect = requireCustomer(session);
        if (accessRedirect != null) {
            return accessRedirect;
        }

        Booking booking = bookingService.getBookingByIdForUpdate(bookingId);
        String normalizedMethod = paymentMethod == null
                ? "" : paymentMethod.trim().toUpperCase(Locale.ROOT);
        if (!isOwnedByCurrentCustomer(booking, session)
                || !isPayable(booking)
                || !SUPPORTED_METHODS.contains(normalizedMethod)
                || paymentRepository.existsByBookingIdAndStatus(bookingId, "SIMULATED")
                || paymentRepository.existsByBookingIdAndStatus(bookingId, "SUCCESS")) {
            return "redirect:/my-bookings?error=payment";
        }

        Payment payment = new Payment();
        payment.setBookingId(booking.getId());
        payment.setAmount(booking.getPrice());
        payment.setPaymentMethod(normalizedMethod);
        payment.setStatus("SIMULATED");
        paymentRepository.save(payment);

        booking.setStatus("PAID");
        booking.setLockExpiryTime(0);
        bookingService.saveBooking(booking);

        model.addAttribute("booking", booking);
        model.addAttribute("payment", payment);
        return "payment-success";
    }

    private String requireCustomer(HttpSession session) {
        User customer = (User) session.getAttribute("loggedInUser");
        if (customer == null) {
            return "redirect:/login?redirect=/my-bookings";
        }
        return "CUSTOMER".equalsIgnoreCase(customer.getRole())
                ? null : "redirect:/";
    }

    private boolean isOwnedByCurrentCustomer(Booking booking, HttpSession session) {
        User customer = (User) session.getAttribute("loggedInUser");
        return booking != null
                && customer != null
                && booking.getCustomerEmail() != null
                && booking.getCustomerEmail().equalsIgnoreCase(customer.getEmail());
    }

    private boolean isPayable(Booking booking) {
        return booking != null
                && "LOCKED".equals(booking.getStatus())
                && booking.getPrice() > 0
                && Double.isFinite(booking.getPrice())
                && booking.getLockExpiryTime() > System.currentTimeMillis();
    }
}