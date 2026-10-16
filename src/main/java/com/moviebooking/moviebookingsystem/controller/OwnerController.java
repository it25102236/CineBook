package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.CashierService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.TheatreService;
import com.moviebooking.moviebookingsystem.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// ==========================================
// CINEMA OWNER - BUSINESS MANAGEMENT
// ==========================================
// Read-only reporting for the Cinema Owner role:
// revenue, sales and booking summaries, built from
// the same data the rest of the system already uses
// (no separate database needed for this phase).

@Controller
public class OwnerController {

    private final BookingService bookingService;
    private final CashierService cashierService;
    private final MovieService movieService;
    private final TheatreService theatreService;
    private final UserService userService;

    public OwnerController(
            BookingService bookingService,
            CashierService cashierService,
            MovieService movieService,
            TheatreService theatreService,
            UserService userService) {

        this.bookingService = bookingService;
        this.cashierService = cashierService;
        this.movieService = movieService;
        this.theatreService = theatreService;
        this.userService = userService;
    }


    // ==========================================
    // OWNER DASHBOARD
    // ==========================================

    @GetMapping("/owner")
    public String dashboard(Model model) {

        List<Booking> bookings = bookingService.getAllBookings();
        List<Payment> payments = cashierService.getAllPayments();

        double totalRevenue = totalRevenue(payments);

        long paidBookings = bookings.stream()
                .filter(booking -> "PAID".equals(booking.getStatus()))
                .count();

        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("totalBookings", bookings.size());
        model.addAttribute("paidBookings", paidBookings);
        model.addAttribute("activeMovies", movieService.getAllMovies().size());
        model.addAttribute("totalTheatres", theatreService.getAllTheatres().size());
        model.addAttribute("totalUsers", userService.getAllUsers().size());

        List<MovieRevenue> topMovies = topMoviesByRevenue(bookings, 5);

        double maxTopRevenue = topMovies.stream()
                .mapToDouble(MovieRevenue::getRevenue)
                .max()
                .orElse(0);

        model.addAttribute("topMovies", topMovies);
        model.addAttribute("maxTopRevenue", maxTopRevenue == 0 ? 1 : maxTopRevenue);

        return "owner/dashboard";
    }


    // ==========================================
    // REVENUE OVERVIEW
    // ==========================================

    @GetMapping("/owner/revenue")
    public String revenue(Model model) {

        List<Booking> bookings = bookingService.getAllBookings();
        List<Payment> payments = cashierService.getAllPayments();

        double totalRevenue = totalRevenue(payments);

        List<MovieRevenue> movieRevenues =
                topMoviesByRevenue(bookings, Integer.MAX_VALUE);

        double maxRevenue = movieRevenues.stream()
                .mapToDouble(MovieRevenue::getRevenue)
                .max()
                .orElse(0);

        model.addAttribute("totalRevenue", totalRevenue);
        model.addAttribute("movieRevenues", movieRevenues);
        model.addAttribute("maxRevenue", maxRevenue == 0 ? 1 : maxRevenue);

        return "owner/revenue";
    }


    // ==========================================
    // SALES REPORT
    // ==========================================

    @GetMapping("/owner/sales-report")
    public String salesReport(Model model) {

        List<Booking> bookings = bookingService.getAllBookings();

        model.addAttribute("bookings", bookings);
        model.addAttribute("totalSales", bookings.size());

        double totalValue = bookings.stream()
                .mapToDouble(Booking::getPrice)
                .sum();

        model.addAttribute("totalValue", totalValue);

        return "owner/sales-report";
    }


    // ==========================================
    // BOOKING SUMMARY
    // ==========================================

    @GetMapping("/owner/booking-summary")
    public String bookingSummary(Model model) {

        List<Booking> bookings = bookingService.getAllBookings();

        long paid = bookings.stream()
                .filter(booking -> "PAID".equals(booking.getStatus()))
                .count();

        long locked = bookings.stream()
                .filter(booking -> "LOCKED".equals(booking.getStatus()))
                .count();

        long cancelled = bookings.stream()
                .filter(booking -> "CANCELLED".equals(booking.getStatus()))
                .count();

        long refunded = bookings.stream()
                .filter(booking -> "REFUNDED".equals(booking.getStatus()))
                .count();

        Map<String, Long> byTheatre = new LinkedHashMap<>();

        for (Booking booking : bookings) {

            String theatre =
                    (booking.getTheatre() == null || booking.getTheatre().isEmpty())
                            ? "Unassigned"
                            : booking.getTheatre();

            byTheatre.merge(theatre, 1L, Long::sum);
        }

        model.addAttribute("totalBookings", bookings.size());
        model.addAttribute("paidCount", paid);
        model.addAttribute("lockedCount", locked);
        model.addAttribute("cancelledCount", cancelled);
        model.addAttribute("refundedCount", refunded);
        model.addAttribute("byTheatre", byTheatre);

        return "owner/booking-summary";
    }


    // ==========================================
    // HELPERS
    // ==========================================

    private double totalRevenue(List<Payment> payments) {

        return payments.stream()
                .filter(payment -> "SUCCESS".equals(payment.getStatus()))
                .mapToDouble(Payment::getAmount)
                .sum();
    }

    private List<MovieRevenue> topMoviesByRevenue(
            List<Booking> bookings,
            int limit) {

        Map<String, MovieRevenue> grouped = new LinkedHashMap<>();

        for (Booking booking : bookings) {

            String title =
                    (booking.getMovieTitle() == null || booking.getMovieTitle().isEmpty())
                            ? "Unknown Movie"
                            : booking.getMovieTitle();

            MovieRevenue entry =
                    grouped.computeIfAbsent(title, MovieRevenue::new);

            entry.addBooking(booking.getPrice());
        }

        List<MovieRevenue> result = new ArrayList<>(grouped.values());

        result.sort(
                Comparator.comparingDouble(MovieRevenue::getRevenue).reversed());

        if (result.size() > limit) {
            return result.subList(0, limit);
        }

        return result;
    }


    // ==========================================
    // SMALL VIEW MODEL FOR MOVIE REVENUE ROWS
    // ==========================================

    public static class MovieRevenue {

        private final String title;
        private long bookings;
        private double revenue;

        public MovieRevenue(String title) {
            this.title = title;
        }

        public void addBooking(double price) {
            this.bookings++;
            this.revenue += price;
        }

        public String getTitle() {
            return title;
        }

        public long getBookings() {
            return bookings;
        }

        public double getRevenue() {
            return revenue;
        }
    }
}
