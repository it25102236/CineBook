package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.CashierService;
import com.moviebooking.moviebookingsystem.service.RefundService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@Controller
public class CashierController {

    private final CashierService cashierService;
    private final BookingService bookingService;
        private final RefundService refundService;

    public CashierController(
            CashierService cashierService,
            BookingService bookingService,
            RefundService refundService) {

        this.cashierService = cashierService;
        this.bookingService = bookingService;
        this.refundService = refundService;
    }


    // ==============================
    // CASHIER DASHBOARD
    // ==============================

    @GetMapping("/cashier")
    public String cashierDashboard(Model model) {

        model.addAttribute(
                "payments",
                cashierService.getAllPayments()
        );

        return "cashier/dashboard";
    }


    // ==============================
    // COUNTER BOOKING PAGE
    // ==============================

    @GetMapping("/cashier/counter-booking")
        public String counterBookingPage(Model model) {

                model.addAttribute(
                                "minBookingDate",
                                LocalDate.now().plusDays(1).toString()
                );

        return "cashier/counter-booking";
    }


    // ==============================
    // CREATE COUNTER BOOKING
    // ==============================

    @PostMapping("/cashier/counter-booking")
    public String createCounterBooking(

            @RequestParam Long movieId,

            @RequestParam String movieTitle,

            @RequestParam String theatre,

            @RequestParam String date,

            @RequestParam String showTime,

            @RequestParam String seatNumber,

            @RequestParam double price,

            Model model) {

                model.addAttribute(
                                "minBookingDate",
                                LocalDate.now().plusDays(1).toString()
                );

                try {
                        if (!LocalDate.parse(date).isAfter(LocalDate.now())) {
                                model.addAttribute(
                                                "error",
                                                "Please select a booking date starting tomorrow."
                                );
                                return "cashier/counter-booking";
                        }
                } catch (DateTimeParseException exception) {
                        model.addAttribute(
                                        "error",
                                        "Please select a valid booking date starting tomorrow."
                        );
                        return "cashier/counter-booking";
                }

        Booking booking = new Booking();

        booking.setMovieId(movieId);

        booking.setMovieTitle(movieTitle);

        booking.setTheatre(theatre);

        booking.setDate(date);

        booking.setShowTime(showTime);

        booking.setSeatNumber(seatNumber);

        booking.setPrice(price);


        boolean created =
                bookingService.createBooking(booking);


        if (!created) {

            model.addAttribute(
                    "error",
                    "Sorry! This seat is already booked."
            );

            return "cashier/counter-booking";
        }


        model.addAttribute(
                "booking",
                booking
        );


        return "cashier/counter-booking";
    }


    // ==============================
    // PAYMENT PAGE
    // ==============================

    @GetMapping("/cashier/payment")
    public String paymentPage(

            @RequestParam(required = false)
            Long bookingId,

            Model model) {

        addPaymentModel(model, bookingId);

        return "cashier/payment";
    }

    /**
     * Loads the payable bookings and, when a booking is selected, the amount
     * owed. The amount always comes from the stored booking price.
     */
    private void addPaymentModel(Model model, Long bookingId) {

        model.addAttribute("payableBookings", cashierService.getPayableBookings());

        if (bookingId == null) {
            return;
        }

        Booking booking = bookingService.getBookingById(bookingId);

        if (booking == null) {

            model.addAttribute("error", "Booking #" + bookingId + " was not found.");
            return;
        }

        if (!"LOCKED".equals(booking.getStatus())) {

            model.addAttribute(
                    "error",
                    "Booking #" + bookingId + " is not awaiting payment (status: "
                            + booking.getStatus() + ")."
            );
            return;
        }

        Payment existingPayment = cashierService.getPaymentByBookingId(bookingId);

        if (existingPayment != null && "SUCCESS".equals(existingPayment.getStatus())) {

            model.addAttribute(
                    "error",
                    "Booking #" + bookingId + " has already been paid."
            );
            return;
        }

        model.addAttribute("booking", booking);
        model.addAttribute("bookingId", booking.getId());
        model.addAttribute("amount", booking.getPrice());
    }


    // ==============================
    // PROCESS PAYMENT
    // ==============================

    @PostMapping("/cashier/payment")
    public String processPayment(

            @RequestParam Long bookingId,

            @RequestParam String paymentMethod,

            Model model) {

        Booking booking = bookingService.getBookingById(bookingId);

        if (booking == null) {

            addPaymentModel(model, null);
            model.addAttribute("error", "Booking #" + bookingId + " was not found.");
            return "cashier/payment";
        }

        Payment payment = cashierService.processPayment(bookingId, 0, paymentMethod);

        if (payment == null || !"SUCCESS".equals(payment.getStatus())) {

            addPaymentModel(model, null);
            model.addAttribute(
                    "error",
                    "Payment could not be completed for booking #" + bookingId + "."
            );
            return "cashier/payment";
        }

        addPaymentModel(model, null);

        model.addAttribute("payment", payment);
        model.addAttribute("booking", booking);

        return "cashier/ticket";
    }


    // ==============================
    // REFUND PAGE
    // ==============================

    @GetMapping("/cashier/refund")
        public String refundPage(
                        @RequestParam(required = false) Long bookingId,
                        Model model) {

                addRefundableBookings(model);

        if (bookingId != null) {
            Booking booking = bookingService.getBookingById(bookingId);
            if (booking == null) {
                model.addAttribute(
                        "error",
                        "Booking #" + bookingId + " was not found."
                );
            } else if (!"PAID".equals(booking.getStatus())) {
                model.addAttribute(
                        "error",
                        "Booking #" + bookingId + " is not paid."
                );
            } else {
                Payment payment = cashierService.getPaymentByBookingId(bookingId);
                if (payment == null || !"SUCCESS".equals(payment.getStatus())) {
                    model.addAttribute(
                            "error",
                            "No successful payment was found for that booking."
                    );
                } else {
                    model.addAttribute("payment", payment);
                    addRefundQuote(model, booking, payment.getAmount());
                }
            }
        }

        return "cashier/refund";
    }


    // ==============================
    // PROCESS REFUND
    // ==============================

    @PostMapping("/cashier/refund")
    public String processRefund(

            @RequestParam Long bookingId,

            Model model) {


        addRefundableBookings(model);


        boolean refunded =
                cashierService.processRefund(
                        bookingId
                );


        // The booking has to be put back into the model on every path.
        // Without it the refund page dereferences a null booking while
        // rendering the confirmation, which returns HTTP 500 even though
        // the refund itself was already committed.
        Booking booking = bookingService.getBookingById(bookingId);

        if (booking != null) {
                model.addAttribute("booking", booking);
        }

        if (refunded) {

                model.addAttribute(
                        "success",
                        "Refund processed successfully."
                );

                if (booking != null) {
                        model.addAttribute("refundPercent", booking.getRefundPercent());
                        model.addAttribute("refundAmount", booking.getRefundAmount());
                }

        } else {

                model.addAttribute(
                        "error",
                        "Refund failed. No successful payment was found for that booking, "
                                + "or it has already been refunded."
                );

                if (bookingId != null) {
                        Payment existingPayment =
                                cashierService.getPaymentByBookingId(bookingId);

                        if (existingPayment != null) {
                                model.addAttribute("payment", existingPayment);
                        }
                }
        }


        return "cashier/refund";
    }

    private void addRefundableBookings(Model model) {
        model.addAttribute(
                "refundableBookings",
                bookingService.getAllBookings().stream()
                        .filter(booking -> "PAID".equals(booking.getStatus()))
                        .filter(booking -> {
                            Payment payment = cashierService.getPaymentByBookingId(booking.getId());
                            return payment != null && "SUCCESS".equals(payment.getStatus());
                        })
                        .toList()
        );
    }

        private void addRefundQuote(Model model, Booking booking, double paidAmount) {
                int percent = refundService.refundPercent(booking);
                model.addAttribute("booking", booking);
                model.addAttribute("refundPercent", percent);
                model.addAttribute(
                                "refundAmount",
                                refundService.refundAmount(paidAmount, percent)
                );
        }


    // ==============================
    // TRANSACTIONS
    // ==============================

    @GetMapping("/cashier/transactions")
    public String transactions(Model model) {

        model.addAttribute(
                "payments",
                cashierService.getAllPayments()
        );

        return "Cashier/transactions";
    }


    // ==============================
    // SEARCH BOOKING
    // ==============================

    @GetMapping("/cashier/search-booking")
    public String searchBooking(

            @RequestParam(required = false)
            Long bookingId,

            Model model) {


        if (bookingId != null) {

            Booking foundBooking = null;


            for (Booking booking :
                    bookingService.getAllBookings()) {

                if (booking.getId() != null
                        && booking.getId()
                        .equals(bookingId)) {

                    foundBooking = booking;

                    break;
                }
            }


            if (foundBooking != null) {

                model.addAttribute(
                        "booking",
                        foundBooking
                );


                Payment payment =
                        cashierService.getPaymentByBookingId(
                                bookingId
                        );


                model.addAttribute(
                        "payment",
                        payment
                );

            } else {

                model.addAttribute(
                        "error",
                        "Booking not found."
                );
            }
        }


        return "cashier/search-booking";
    }
}