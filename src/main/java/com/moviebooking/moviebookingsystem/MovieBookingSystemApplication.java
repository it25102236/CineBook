package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Promotion;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.SupportTicket;
import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.service.CustomerSupportService;
import com.moviebooking.moviebookingsystem.service.MarketingService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import com.moviebooking.moviebookingsystem.service.TheatreService;
import com.moviebooking.moviebookingsystem.service.UserService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.List;

@SpringBootApplication
@EnableScheduling
public class MovieBookingSystemApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieBookingSystemApplication.class, args);
    }

    // ==========================================
    // DATABASE SEEDING (RUNS ONCE, IDEMPOTENT)
    // ==========================================
    // Movies, the default theatre, a sample showtime, a demo staff
    // account per role, a sample support ticket and a sample
    // promotion all live in SQL Server now, so this only seeds them
    // the first time the app connects to an empty database (guarded
    // by an isEmpty() check per table) instead of re-inserting
    // duplicates on every restart. IDs are never hard-coded - the
    // database (IDENTITY column) assigns them.

    @Bean
    CommandLineRunner seedDatabase(
            MovieService movieService,
            TheatreService theatreService,
            ShowtimeService showtimeService,
            CustomerSupportService supportService,
            MarketingService marketingService,
            UserService userService) {

        return args -> {

            // ================= MOVIES =================
            // Fetch existing movies once to avoid repeated DB calls
            List<Movie> existingMovies = movieService.getAllMovies();

            if (existingMovies.isEmpty()) {

                Movie movie1 = new Movie();
                movie1.setTitle("The Last Journey");
                movie1.setGenre("Action • Adventure");
                movie1.setDescription("An exciting adventure begins.");
                movie1.setPosterUrl("/images/The Last Journey.jpg");
                movie1.setRating(8.7);
                movieService.addMovie(movie1);

                Movie movie2 = new Movie();
                movie2.setTitle("Midnight Story");
                movie2.setGenre("Drama • Thriller");
                movie2.setDescription("A mysterious story unfolds at midnight.");
                movie2.setPosterUrl("/images/Midnight Story.jpg");
                movie2.setRating(8.4);
                movieService.addMovie(movie2);

                Movie movie3 = new Movie();
                movie3.setTitle("Galaxy War");
                movie3.setGenre("Sci-Fi • Fantasy");
                movie3.setDescription("A battle for the future of the galaxy.");
                movie3.setPosterUrl("/images/Galaxy War.jpg");
                movie3.setRating(9.1);
                movieService.addMovie(movie3);

                Movie movie4 = new Movie();
                movie4.setTitle("Love Again");
                movie4.setGenre("Romance • Comedy");
                movie4.setDescription("A beautiful story about love and friendship.");
                movie4.setPosterUrl("/images/Love Again.jpg");
                movie4.setRating(8.2);
                movieService.addMovie(movie4);
            }

            // ================= ADDITIONAL MOVIES (ADDED IF NOT EXIST) =================
            // These are added regardless of whether the initial 4 were seeded
            // Check each movie by title to avoid duplicates

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Oppenheimer"))) {
                Movie movie5 = new Movie();
                movie5.setTitle("Oppenheimer");
                movie5.setGenre("Biography • Drama • History");
                movie5.setDescription("The story of American scientist J. Robert Oppenheimer and his role in the development of the atomic bomb.");
                movie5.setPosterUrl("https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg");
                movie5.setRating(8.6);
                movieService.addMovie(movie5);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Barbie"))) {
                Movie movie6 = new Movie();
                movie6.setTitle("Barbie");
                movie6.setGenre("Adventure • Comedy • Fantasy");
                movie6.setDescription("Barbie suffers a crisis that leads her to question her world and her existence.");
                movie6.setPosterUrl("https://image.tmdb.org/t/p/w500/iuFNMS8U5cb6xfzi51Dbkovj7vM.jpg");
                movie6.setRating(7.2);
                movieService.addMovie(movie6);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Mission: Impossible - Dead Reckoning Part One"))) {
                Movie movie7 = new Movie();
                movie7.setTitle("Mission: Impossible - Dead Reckoning Part One");
                movie7.setGenre("Action • Adventure • Thriller");
                movie7.setDescription("Ethan Hunt and his IMF team must track down a dangerous weapon before it falls into the wrong hands.");
                movie7.setPosterUrl("https://image.tmdb.org/t/p/w500/NNxYkU70HPurnNCSiCjYAmacwm.jpg");
                movie7.setRating(8.0);
                movieService.addMovie(movie7);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Spider-Man: Across the Spider-Verse"))) {
                Movie movie8 = new Movie();
                movie8.setTitle("Spider-Man: Across the Spider-Verse");
                movie8.setGenre("Animation • Action • Adventure");
                movie8.setDescription("Miles Morales catapults across the multiverse, where he encounters a team of Spider-People charged with protecting its very existence.");
                movie8.setPosterUrl("https://image.tmdb.org/t/p/w500/8Vt6mWEReuy4Of61Lnj5Xj704m8.jpg");
                movie8.setRating(8.9);
                movieService.addMovie(movie8);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Guardians of the Galaxy Vol. 3"))) {
                Movie movie9 = new Movie();
                movie9.setTitle("Guardians of the Galaxy Vol. 3");
                movie9.setGenre("Action • Adventure • Comedy");
                movie9.setDescription("Peter Quill, still reeling from the loss of Gamora, must rally his team to defend the universe.");
                movie9.setPosterUrl("https://image.tmdb.org/t/p/w500/r2J02Z2OpNTctfOSN1Ydgii51I3.jpg");
                movie9.setRating(8.3);
                movieService.addMovie(movie9);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("The Super Mario Bros. Movie"))) {
                Movie movie10 = new Movie();
                movie10.setTitle("The Super Mario Bros. Movie");
                movie10.setGenre("Animation • Adventure • Comedy");
                movie10.setDescription("Mario teams up with Princess Peach and Toad to find his brother Luigi and save the Mushroom Kingdom from Bowser.");
                movie10.setPosterUrl("https://image.tmdb.org/t/p/w500/qNBAXBIQlnOThrVvA6mA2B5ggV6.jpg");
                movie10.setRating(7.7);
                movieService.addMovie(movie10);
            }

            // ================= RECENT TAMIL MOVIES =================

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Leo"))) {
                Movie movie11 = new Movie();
                movie11.setTitle("Leo");
                movie11.setGenre("Action • Thriller");
                movie11.setDescription("A mild-mannered cafe owner becomes a local hero through an act of violence, which sets off a chain of events that connects to his past.");
                movie11.setPosterUrl("https://image.tmdb.org/t/p/w500/p3J2vZDDvv1M53d3Q7ZJzGcpzFe.jpg");
                movie11.setRating(7.4);
                movieService.addMovie(movie11);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Jailer"))) {
                Movie movie12 = new Movie();
                movie12.setTitle("Jailer");
                movie12.setGenre("Action • Comedy");
                movie12.setDescription("A retired jailer sets out to help his son, a police officer, who gets into trouble with a ruthless gangster.");
                movie12.setPosterUrl("https://image.tmdb.org/t/p/w500/rCzpDGYd1sQEL7HlAfmOCDKBd5J.jpg");
                movie12.setRating(7.1);
                movieService.addMovie(movie12);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Vikram"))) {
                Movie movie13 = new Movie();
                movie13.setTitle("Vikram");
                movie13.setGenre("Action • Thriller");
                movie13.setDescription("A special investigator pursues a group of masked vigilantes while uncovering a larger conspiracy.");
                movie13.setPosterUrl("https://image.tmdb.org/t/p/w500/ok2JhF0AhuN5H2mMgLrufnVQ5tK.jpg");
                movie13.setRating(8.3);
                movieService.addMovie(movie13);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Ponniyin Selvan: II"))) {
                Movie movie14 = new Movie();
                movie14.setTitle("Ponniyin Selvan: II");
                movie14.setGenre("Action • Drama • History");
                movie14.setDescription("The Chola empire faces threats from within and without as the succession battle intensifies.");
                movie14.setPosterUrl("https://image.tmdb.org/t/p/w500/vZloFAK7NmvMGHx08GzLgOyJmHF.jpg");
                movie14.setRating(8.5);
                movieService.addMovie(movie14);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Jawan"))) {
                Movie movie15 = new Movie();
                movie15.setTitle("Jawan");
                movie15.setGenre("Action • Thriller");
                movie15.setDescription("A man driven by a personal vendetta takes on a corrupt system while confronting his past.");
                movie15.setPosterUrl("https://image.tmdb.org/t/p/w500/vjzSn4itLWgO2bZ4HkHfVwJzJ8Y.jpg");
                movie15.setRating(7.0);
                movieService.addMovie(movie15);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Mandaadi"))) {
                Movie movie16 = new Movie();
                movie16.setTitle("Mandaadi");
                movie16.setGenre("Drama • Thriller");
                movie16.setDescription("A gripping tale of survival and betrayal in the hills of Tamil Nadu.");
                movie16.setPosterUrl("/images/Mandaadi.jpg");
                movie16.setRating(7.8);
                movieService.addMovie(movie16);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("Sarpatta Parambarai"))) {
                Movie movie17 = new Movie();
                movie17.setTitle("Sarpatta Parambarai");
                movie17.setGenre("Sports • Drama");
                movie17.setDescription("A boxing drama set in 1970s North Chennai, following the clash between two clans.");
                movie17.setPosterUrl("https://image.tmdb.org/t/p/w500/xRfzJ9K8vGzR2yV8W3KzJ9Q8mHF.jpg");
                movie17.setRating(8.5);
                movieService.addMovie(movie17);
            }

            if (existingMovies.stream().noneMatch(m -> m.getTitle().equals("DC"))) {
                Movie movie18 = new Movie();
                movie18.setTitle("DC");
                movie18.setGenre("Action • Thriller");
                movie18.setDescription("A high-octane action thriller set in the criminal underworld.");
                movie18.setPosterUrl("/images/DC.jpg");
                movie18.setRating(7.5);
                movieService.addMovie(movie18);
            }

            // ================= ENSURE POSTER URLs FOR TAMIL MOVIES =================
            java.util.Map<String, String> tamilPosterUrls = new java.util.HashMap<>();
            tamilPosterUrls.put("Leo", "/images/posters/leo.jpg");
            tamilPosterUrls.put("Jailer", "/images/posters/jailer.jpg");
            tamilPosterUrls.put("Vikram", "/images/posters/vikram.jpg");
            tamilPosterUrls.put("Ponniyin Selvan: II", "/images/posters/ponniyin_selvan_ii.jpg");
            tamilPosterUrls.put("Jawan", "/images/posters/jawan.jpg");
            tamilPosterUrls.put("Sarpatta Parambarai", "/images/posters/sarpatta_parambarai.jpg");

            for (java.util.Map.Entry<String, String> entry : tamilPosterUrls.entrySet()) {
                Movie tamilMovie = movieService.findMovieByTitle(entry.getKey());
                if (tamilMovie != null && !entry.getValue().equals(tamilMovie.getPosterUrl())) {
                    tamilMovie.setPosterUrl(entry.getValue());
                    movieService.updateMovie(tamilMovie);
                }
            }

            // ================= THEATRE =================

            if (theatreService.getAllTheatres().isEmpty()) {

                Theatre theatre = new Theatre();
                theatre.setName("CineBook Main Hall");
                theatre.setLocation("Jaffna");
                theatre.setTotalSeats(150);
                theatreService.addTheatre(theatre);
            }

            // ================= SHOWTIMES FOR ALL MOVIES =================

            List<Movie> movies = movieService.getAllMovies();
            List<Theatre> theatres = theatreService.getAllTheatres();

            java.time.LocalDate baseDate = java.time.LocalDate.now().plusDays(1);

            String[] timeSlots = {
                "10:00 AM", "01:00 PM", "04:00 PM", "07:00 PM", "10:00 PM"
            };

            double[] prices = {1200.0, 1300.0, 1400.0, 1500.0, 1600.0};

            int movieIndex = 0;

            for (Movie movie : movies) {

                List<Showtime> existing =
                        showtimeService.findShowtimesByMovie(movie.getId());

                if (!existing.isEmpty()) {
                    movieIndex++;
                    continue;
                }

                if (theatres.isEmpty()) {
                    Theatre defaultTheatre = new Theatre();
                    defaultTheatre.setName("CineBook Main Hall");
                    defaultTheatre.setLocation("Jaffna");
                    defaultTheatre.setTotalSeats(150);
                    theatreService.addTheatre(defaultTheatre);
                    theatres = theatreService.getAllTheatres();
                }

                String theatreName =
                        theatres.isEmpty()
                                ? "CineBook Main Hall"
                                : theatres.get(movieIndex % theatres.size()).getName();

                Theatre theatre = theatreService.findTheatreByName(theatreName);

                int capacity =
                        theatre != null
                                ? Math.max(0, theatre.getTotalSeats())
                                : 150;

                String date =
                        baseDate.plusDays(
                                movieIndex / Math.max(1, theatres.size())
                        ).toString();

                String showTime = timeSlots[movieIndex % timeSlots.length];

                String endTime =
                        ShowtimeService.formatMinutes(
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
                showtime.setTicketPrice(prices[movieIndex % prices.length]);

                showtimeService.addShowtime(showtime);

                movieIndex++;
            }

            // ================= SAMPLE SUPPORT TICKET =================

            if (supportService.getAllTickets().isEmpty()) {

                SupportTicket ticket = new SupportTicket();
                ticket.setCustomerName("John");
                ticket.setEmail("john@gmail.com");
                ticket.setSubject("Booking Issue");
                ticket.setMessage("I cannot select my preferred seat.");
                ticket.setStatus("OPEN");
                ticket.setResponse("");

                supportService.createTicket(ticket);
            }

            // ================= SAMPLE PROMOTION =================

            if (marketingService.getAllPromotions().isEmpty()) {

                Promotion promotion = new Promotion();
                promotion.setTitle("Weekend Movie Offer");
                promotion.setDescription("Get special discounts on weekend movie bookings.");
                promotion.setStartDate("2026-09-20");
                promotion.setEndDate("2026-09-30");
                promotion.setStatus("ACTIVE");

                marketingService.addPromotion(promotion);
            }

            // ================= DEMO STAFF ACCOUNTS (ONE PER ROLE) =================
            // Lets each of the 7 group members log straight into their
            // own dashboard for the viva/demo without going through
            // the Admin "add user" screen first.

            if (userService.findUserByEmail("admin@cinebook.com") == null) {
                seedStaff(userService, "System Admin", "admin@cinebook.com",
                        "admin", "Admin@123", "ADMIN");
            }

            if (userService.findUserByEmail("cashier@cinebook.com") == null) {
                seedStaff(userService, "Cashier Desk", "cashier@cinebook.com",
                        "cashier", "Cashier@123", "CASHIER");
            }

            if (userService.findUserByEmail("theatremanager@cinebook.com") == null) {
                seedStaff(userService, "Theatre Manager", "theatremanager@cinebook.com",
                        "theatremanager", "Theatre@123", "THEATRE_MANAGER");
            }

            if (userService.findUserByEmail("owner@cinebook.com") == null) {
                seedStaff(userService, "Cinema Owner", "owner@cinebook.com",
                        "owner", "Owner@123", "OWNER");
            }

            if (userService.findUserByEmail("support@cinebook.com") == null) {
                seedStaff(userService, "Support Staff", "support@cinebook.com",
                        "support", "Support@123", "SUPPORT");
            }

            if (userService.findUserByEmail("marketing@cinebook.com") == null) {
                seedStaff(userService, "Marketing Manager", "marketing@cinebook.com",
                        "marketing", "Marketing@123", "MARKETING");
            }
        };
    }

    private static void seedStaff(
            UserService userService,
            String name,
            String email,
            String username,
            String password,
            String role) {

        User staff = new User();
        staff.setName(name);
        staff.setEmail(email);
        staff.setUsername(username);
        staff.setPassword(password);
        staff.setTelephone("0770000000");
        staff.setDateOfBirth("1995-01-01");
        staff.setRole(role);
        staff.setStatus("ACTIVE");

        userService.saveUser(staff);
    }
}
