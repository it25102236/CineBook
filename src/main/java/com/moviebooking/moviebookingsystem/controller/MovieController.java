package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
public class MovieController {

    private final MovieService movieService;
    private final ShowtimeService showtimeService;

    public MovieController(
            MovieService movieService,
            ShowtimeService showtimeService) {

        this.movieService = movieService;
        this.showtimeService = showtimeService;
    }

    // ==========================================
    // CUSTOMER - VIEW ALL MOVIES
    // ==========================================

    @GetMapping("/movies")
    public String getMovies(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false, defaultValue = "any") String availability,
            Model model) {

        List<Movie> allMovies = movieService.getAllMovies();
        List<Movie> movies = allMovies;

        String keyword = search == null ? "" : search.trim();
        if (keyword.length() > 100) {
            keyword = keyword.substring(0, 100);
        }
        if (!keyword.isEmpty()) {

            String titleSearch = keyword.toLowerCase(Locale.ROOT);

            movies = movies.stream()
                    .filter(movie ->
                            movie.getTitle() != null
                                    && movie.getTitle()
                                    .toLowerCase(Locale.ROOT)
                                    .contains(titleSearch))
                    .collect(Collectors.toList());
        }

        List<String> genres = allMovies.stream()
                .map(Movie::getGenre)
                .filter(value -> value != null && !value.isBlank())
                .flatMap(value -> Arrays.stream(value.split("[,;|]")))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        String genreCandidate = genre == null ? "" : genre.trim();
        String selectedGenre = !genreCandidate.isEmpty() && genres.stream().anyMatch(
                value -> value.equalsIgnoreCase(genreCandidate))
                ? genreCandidate : "";
        if (!selectedGenre.isEmpty()) {
            String genreFilter = selectedGenre;
            movies = movies.stream()
                    .filter(movie -> movie.getGenre() != null
                            && Arrays.stream(movie.getGenre().split("[,;|]"))
                            .map(String::trim)
                            .anyMatch(value -> value.equalsIgnoreCase(genreFilter)))
                    .toList();
        }

        String selectedAvailability = availability == null
                ? "any" : availability.trim().toLowerCase(Locale.ROOT);
        if (!List.of("any", "available", "unavailable").contains(selectedAvailability)) {
            selectedAvailability = "any";
        }
        if (!"any".equals(selectedAvailability)) {
            List<Long> moviesWithAvailableShows = showtimeService.getFutureShowtimes().stream()
                    .filter(showtime -> showtime.getAvailableSeats() > 0)
                    .map(Showtime::getMovieId)
                    .filter(id -> id != null)
                    .distinct()
                    .toList();
            boolean requireAvailability = "available".equals(selectedAvailability);
            movies = movies.stream()
                    .filter(movie -> moviesWithAvailableShows.contains(movie.getId())
                            == requireAvailability)
                    .toList();
        }

        model.addAttribute("movies", movies);
        model.addAttribute("search", keyword);
        model.addAttribute("genres", genres);
        model.addAttribute("genre", selectedGenre);
        model.addAttribute("availability", selectedAvailability);

        return "movies";
    }


    // ==========================================
    // CUSTOMER - VIEW MOVIE SHOWTIMES
    // ==========================================

    @GetMapping("/movie-showtimes")
    public String movieShowtimes(
            @RequestParam Long movieId,
            Model model) {

        Movie movie =
                movieService.findMovieById(movieId);

        List<Showtime> futureShowtimes =
                showtimeService.getFutureShowtimes();

        List<Showtime> movieShowtimes =
                futureShowtimes.stream()
                        .filter(showtime ->
                                showtime.getMovieId() != null
                                        && showtime.getMovieId()
                                        .equals(movieId))
                        .collect(Collectors.toList());

        model.addAttribute("movie", movie);
        model.addAttribute(
                "showtimes",
                movieShowtimes
        );

        return "movie-showtimes";
    }
}