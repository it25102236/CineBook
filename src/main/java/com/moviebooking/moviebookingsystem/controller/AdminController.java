package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.DatabaseBackupService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import com.moviebooking.moviebookingsystem.service.TheatreService;
import com.moviebooking.moviebookingsystem.service.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Controller
public class AdminController {

    private final UserService userService;
    private final MovieService movieService;
    private final TheatreService theatreService;
    private final ShowtimeService showtimeService;
    private final BookingService bookingService;
    private final PaymentRepository paymentRepository;
    private final DatabaseBackupService databaseBackupService;

    public AdminController(
            UserService userService,
            MovieService movieService,
            TheatreService theatreService,
            ShowtimeService showtimeService,
            BookingService bookingService,
            PaymentRepository paymentRepository,
            DatabaseBackupService databaseBackupService) {

        this.userService = userService;
        this.movieService = movieService;
        this.theatreService = theatreService;
        this.showtimeService = showtimeService;
        this.bookingService = bookingService;
        this.paymentRepository = paymentRepository;
        this.databaseBackupService = databaseBackupService;
    }


    // ==================================================
    // ADMIN DASHBOARD
    // ==================================================

    @GetMapping("/admin")
    public String adminDashboard(Model model) {

        model.addAttribute(
                "title",
                "Admin Dashboard"
        );

        // Total Users
        model.addAttribute(
                "userCount",
                userService.getAllUsers().size()
        );

        // Total Movies
        model.addAttribute(
                "movieCount",
                movieService.getAllMovies().size()
        );

        // Total Theatres
        model.addAttribute(
                "theatreCount",
                theatreService.getAllTheatres().size()
        );

        // Total Bookings
        model.addAttribute(
                "bookingCount",
                bookingService.getAllBookings().size()
        );

        // Total Payments
        model.addAttribute(
                "paymentCount",
                paymentRepository.findAll().size()
        );

        return "admin/dashboard";
    }


    // ==================================================
    // DATABASE BACKUP
    // ==================================================

    @GetMapping("/admin/backup")
    public void backupDatabase(jakarta.servlet.http.HttpServletResponse response) throws IOException {

        String timestamp = LocalDateTime.now().format(
                DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        );

        response.setContentType("application/sql");
        response.setHeader(
                "Content-Disposition",
                "attachment; filename=\"cinebook_backup_" + timestamp + ".sql\""
        );

        try {
            String sql = databaseBackupService.exportDatabase();

            try (PrintWriter writer = response.getWriter()) {
                writer.write(sql);
                writer.flush();
            }

        } catch (SQLException exception) {
            response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("text/plain");
            response.getWriter().write("Backup failed: " + exception.getMessage());
        }
    }


    // ==================================================
    // USER MANAGEMENT
    // ==================================================

    @GetMapping("/admin/users")
    public String userManagement(Model model) {

        model.addAttribute(
                "users",
                userService.getAllUsers()
        );

        return "admin/users";
    }


    @GetMapping("/admin/users/add")
    public String addUserPage(Model model) {

        model.addAttribute(
                "user",
                new User()
        );

        return "admin/add-user";
    }


    @PostMapping("/admin/users/add")
    public String addUser(
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String role,
            Model model) {

        User user = new User();

        user.setName(name);
        user.setEmail(email);
        user.setPassword(password);
        user.setRole(role);

        userService.saveUser(user);

        model.addAttribute(
                "success",
                "User added successfully."
        );

        model.addAttribute(
                "users",
                userService.getAllUsers()
        );

        return "admin/users";
    }


    @GetMapping("/admin/users/edit")
    public String editUserPage(
            @RequestParam Long id,
            Model model) {

        User user = userService.findUserById(id);

        if (user == null) {

            model.addAttribute(
                    "error",
                    "User not found."
            );

            model.addAttribute(
                    "users",
                    userService.getAllUsers()
            );

            return "admin/users";
        }

        model.addAttribute(
                "user",
                user
        );

        return "admin/edit-user";
    }


    @PostMapping("/admin/users/edit")
    public String updateUser(
            @RequestParam Long id,
            @RequestParam String name,
            @RequestParam String email,
            @RequestParam String role,
            Model model) {

        User user = new User();

        user.setId(id);
        user.setName(name);
        user.setEmail(email);
        user.setRole(role);

        boolean updated =
                userService.updateUser(user);

        if (updated) {

            model.addAttribute(
                    "success",
                    "User updated successfully."
            );

        } else {

            model.addAttribute(
                    "error",
                    "User not found."
            );
        }

        model.addAttribute(
                "users",
                userService.getAllUsers()
        );

        return "admin/users";
    }


    @PostMapping("/admin/users/delete")
    public String deleteUser(
            @RequestParam Long id,
            Model model) {

        boolean deleted =
                userService.deleteUser(id);

        if (deleted) {

            model.addAttribute(
                    "success",
                    "User deleted successfully."
            );

        } else {

            model.addAttribute(
                    "error",
                    "User not found."
            );
        }

        model.addAttribute(
                "users",
                userService.getAllUsers()
        );

        return "admin/users";
    }


    // ==================================================
    // MOVIE MANAGEMENT
    // ==================================================

    @GetMapping("/admin/movies")
    public String movieManagement(Model model) {

        model.addAttribute(
                "movies",
                movieService.getAllMovies()
        );

        return "admin/movies";
    }


    @GetMapping("/admin/movies/add")
    public String addMoviePage(Model model) {

        model.addAttribute(
                "movie",
                new Movie()
        );

        return "admin/add-movie";
    }


    @PostMapping("/admin/movies/add")
    public String addMovie(
            @RequestParam String title,
            @RequestParam String genre,
            @RequestParam String description,
            @RequestParam String posterUrl,
            @RequestParam double rating,
            Model model) {

        Movie movie = new Movie();

        movie.setTitle(title);
        movie.setGenre(genre);
        movie.setDescription(description);
        movie.setPosterUrl(posterUrl);
        movie.setRating(rating);

        movieService.addMovie(movie);

        assignDefaultShowtime(movie);

        model.addAttribute(
                "success",
                "Movie added successfully."
        );

        model.addAttribute(
                "movies",
                movieService.getAllMovies()
        );

        return "admin/movies";
    }


    @GetMapping("/admin/movies/edit")
    public String editMoviePage(
            @RequestParam Long id,
            Model model) {

        Movie movie =
                movieService.findMovieById(id);

        if (movie == null) {

            model.addAttribute(
                    "error",
                    "Movie not found."
            );

            model.addAttribute(
                    "movies",
                    movieService.getAllMovies()
            );

            return "admin/movies";
        }

        model.addAttribute(
                "movie",
                movie
        );

        return "admin/edit-movie";
    }


    @PostMapping("/admin/movies/edit")
    public String updateMovie(
            @RequestParam Long id,
            @RequestParam String title,
            @RequestParam String genre,
            @RequestParam String description,
            @RequestParam String posterUrl,
            @RequestParam double rating,
            Model model) {

        Movie movie = new Movie();

        movie.setId(id);
        movie.setTitle(title);
        movie.setGenre(genre);
        movie.setDescription(description);
        movie.setPosterUrl(posterUrl);
        movie.setRating(rating);

        boolean updated =
                movieService.updateMovie(movie);

        if (updated) {

            model.addAttribute(
                    "success",
                    "Movie updated successfully."
            );

        } else {

            model.addAttribute(
                    "error",
                    "Movie not found."
            );
        }

        model.addAttribute(
                "movies",
                movieService.getAllMovies()
        );

        return "admin/movies";
    }


    @PostMapping("/admin/movies/delete")
    public String deleteMovie(
            @RequestParam Long id,
            Model model) {

        boolean deleted =
                movieService.deleteMovie(id);

        if (deleted) {

            model.addAttribute(
                    "success",
                    "Movie deleted successfully."
            );

        } else {

            model.addAttribute(
                    "error",
                    "Movie not found."
            );
        }

        model.addAttribute(
                "movies",
                movieService.getAllMovies()
        );

        return "admin/movies";
    }


    // ==================================================
    // THEATRE MANAGEMENT
    // ==================================================

    @GetMapping("/admin/theatres")
    public String theatreManagement(Model model) {

        model.addAttribute(
                "theatres",
                theatreService.getAllTheatres()
        );

        return "admin/theatres";
    }


    @GetMapping("/admin/theatres/add")
    public String addTheatrePage(Model model) {

        model.addAttribute(
                "theatre",
                new Theatre()
        );

        return "admin/add-theatre";
    }


    @PostMapping("/admin/theatres/add")
    public String addTheatre(
            @RequestParam String name,
            @RequestParam String location,
            @RequestParam String totalSeats,
            Model model) {

        String validationError = validateTheatreInput(name, location, totalSeats, null);
        if (validationError != null) {
            model.addAttribute("error", validationError);
            model.addAttribute("theatre", new Theatre());
            Theatre t = new Theatre();
            t.setName(name);
            t.setLocation(location);
            t.setTotalSeats(parseIntSafe(totalSeats));
            model.addAttribute("theatre", t);
            return "admin/add-theatre";
        }

        // Check duplicate name
        if (theatreService.getAllTheatres().stream()
                .anyMatch(t -> t.getName().equalsIgnoreCase(name.trim()))) {
            model.addAttribute("error", "A theatre with this name already exists.");
            Theatre t = new Theatre();
            t.setName(name);
            t.setLocation(location);
            t.setTotalSeats(parseIntSafe(totalSeats));
            model.addAttribute("theatre", t);
            return "admin/add-theatre";
        }

        Theatre theatre = new Theatre();
        theatre.setName(name.trim());
        theatre.setLocation(location.trim());
        theatre.setTotalSeats(parseIntSafe(totalSeats));

        theatreService.addTheatre(theatre);

        model.addAttribute(
                "success",
                "Theatre added successfully."
        );

        model.addAttribute(
                "theatres",
                theatreService.getAllTheatres()
        );

        return "admin/theatres";
    }


    private String validateTheatreInput(String name, String location, String totalSeatsStr, Long excludeId) {
        // Name validation
        if (name == null || name.trim().isEmpty()) {
            return "Theatre name is required.";
        }
        String trimmedName = name.trim();
        if (trimmedName.length() < 2 || trimmedName.length() > 100) {
            return "Theatre name must be between 2 and 100 characters.";
        }
        if (!trimmedName.matches("^[a-zA-Z0-9\\s\\-'.&]+$")) {
            return "Theatre name contains invalid characters. Only letters, numbers, spaces, hyphens, apostrophes, periods, and & are allowed.";
        }

        // Location validation
        if (location == null || location.trim().isEmpty()) {
            return "Location is required.";
        }
        String trimmedLocation = location.trim();
        if (trimmedLocation.length() < 2 || trimmedLocation.length() > 200) {
            return "Location must be between 2 and 200 characters.";
        }

        // Total Seats validation
        if (totalSeatsStr == null || totalSeatsStr.trim().isEmpty()) {
            return "Total seats is required.";
        }
        // Check if it's a valid integer (no decimals)
        if (!totalSeatsStr.trim().matches("^\\d+$")) {
            return "Total seats must be a whole number (no decimals).";
        }
        int totalSeats = Integer.parseInt(totalSeatsStr.trim());
        if (totalSeats < 1 || totalSeats > 500) {
            return "Total seats must be between 1 and 500.";
        }

        // Check duplicate name (excluding current theatre for edit)
        if (excludeId != null) {
            Theatre existing = theatreService.getAllTheatres().stream()
                    .filter(t -> t.getName().equalsIgnoreCase(trimmedName))
                    .findFirst()
                    .orElse(null);
            if (existing != null && !existing.getId().equals(excludeId)) {
                return "A theatre with this name already exists.";
            }
        }

        return null;
    }

    private int parseIntSafe(String s) {
        try {
            return s != null ? Integer.parseInt(s.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }


    @GetMapping("/admin/theatres/edit")
    public String editTheatrePage(
            @RequestParam Long id,
            Model model) {

        Theatre theatre =
                theatreService.findTheatreById(id);

        if (theatre == null) {

            model.addAttribute(
                    "error",
                    "Theatre not found."
            );

            model.addAttribute(
                    "theatres",
                    theatreService.getAllTheatres()
            );

            return "admin/theatres";
        }

        model.addAttribute(
                "theatre",
                theatre
        );

        return "admin/edit-theatre";
    }


    @PostMapping("/admin/theatres/edit")
    public String updateTheatre(
            @RequestParam Long id,
            @RequestParam String name,
            @RequestParam String location,
            @RequestParam String totalSeats,
            Model model) {

        String validationError = validateTheatreInput(name, location, totalSeats, id);
        if (validationError != null) {
            model.addAttribute("error", validationError);
            Theatre t = new Theatre();
            t.setId(id);
            t.setName(name);
            t.setLocation(location);
            t.setTotalSeats(parseIntSafe(totalSeats));
            model.addAttribute("theatre", t);
            return "admin/edit-theatre";
        }

        Theatre theatre = new Theatre();
        theatre.setId(id);
        theatre.setName(name.trim());
        theatre.setLocation(location.trim());
        theatre.setTotalSeats(parseIntSafe(totalSeats));

        boolean updated =
                theatreService.updateTheatre(theatre);

        if (updated) {

            model.addAttribute(
                    "success",
                    "Theatre updated successfully."
            );

        } else {

            model.addAttribute(
                    "error",
                    "Theatre not found."
            );
        }

        model.addAttribute(
                "theatres",
                theatreService.getAllTheatres()
        );

        return "admin/theatres";
    }


    @PostMapping("/admin/theatres/delete")
    public String deleteTheatre(
            @RequestParam Long id,
            Model model) {

        boolean deleted =
                theatreService.deleteTheatre(id);

        if (deleted) {

            model.addAttribute(
                    "success",
                    "Theatre deleted successfully."
            );

        } else {

            model.addAttribute(
                    "error",
                    "Theatre not found."
            );
        }

        model.addAttribute(
                "theatres",
                theatreService.getAllTheatres()
        );

        return "admin/theatres";
    }

    private void assignDefaultShowtime(Movie movie) {

        List<Showtime> existing = showtimeService.findShowtimesByMovie(movie.getId());

        if (!existing.isEmpty()) {
            return;
        }

        List<Theatre> theatres = theatreService.getAllTheatres();

        String theatreName;
        int capacity;

        if (theatres.isEmpty()) {
            Theatre defaultTheatre = new Theatre();
            defaultTheatre.setName("CineBook Main Hall");
            defaultTheatre.setLocation("Jaffna");
            defaultTheatre.setTotalSeats(150);
            theatreService.addTheatre(defaultTheatre);
            theatreName = defaultTheatre.getName();
            capacity = defaultTheatre.getTotalSeats();
        } else {
            Theatre theatre = theatres.get(0);
            theatreName = theatre.getName();
            capacity = theatre.getTotalSeats();
        }

        java.time.LocalDate baseDate = java.time.LocalDate.now().plusDays(1);
        String date = baseDate.toString();

        String showTime = "06:00 PM";

        String endTime = ShowtimeService.formatMinutes(
                ShowtimeService.parseTimeToMinutes(showTime) + 180
        );

        Showtime showtime = new Showtime();
        showtime.setMovieId(movie.getId());
        showtime.setMovieTitle(movie.getTitle());
        showtime.setTheatre(theatreName);
        showtime.setDate(date);
        showtime.setShowTime(showTime);
        showtime.setEndTime(endTime);
        showtime.setTotalSeats(capacity);
        showtime.setAvailableSeats(capacity);
        showtime.setTicketPrice(1200.0);

        showtimeService.addShowtime(showtime);
    }
}