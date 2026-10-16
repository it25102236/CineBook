package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.repository.MovieRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }


    // =========================
    // GET ALL MOVIES
    // =========================

    public List<Movie> getAllMovies() {

        return movieRepository.findAll();
    }


    // =========================
    // FIND MOVIE BY ID
    // =========================

    public Movie findMovieById(Long id) {

        if (id == null) {
            return null;
        }

        return movieRepository.findById(id).orElse(null);
    }


    // =========================
    // SAVE MOVIE
    // =========================

    public void saveMovie(Movie movie) {

        movieRepository.save(movie);
    }


    // =========================
    // ADD MOVIE
    // =========================

    public void addMovie(Movie movie) {

        movieRepository.save(movie);
    }


    // =========================
    // UPDATE MOVIE (fetch existing, merge, save)
    // =========================

    public boolean updateMovie(Movie updatedMovie) {

        if (updatedMovie == null || updatedMovie.getId() == null) {
            return false;
        }

        Optional<Movie> existingOpt = movieRepository.findById(updatedMovie.getId());

        if (existingOpt.isEmpty()) {
            return false;
        }

        Movie existing = existingOpt.get();

        existing.setTitle(updatedMovie.getTitle());
        existing.setGenre(updatedMovie.getGenre());
        existing.setDescription(updatedMovie.getDescription());
        existing.setPosterUrl(updatedMovie.getPosterUrl());
        existing.setRating(updatedMovie.getRating());

        movieRepository.save(existing);

        return true;
    }


    // =========================
    // DELETE MOVIE
    // =========================

    public boolean deleteMovie(Long id) {

        if (id == null || !movieRepository.existsById(id)) {
            return false;
        }

        movieRepository.deleteById(id);

        return true;
    }

    public Movie findMovieByTitle(String title) {
        return movieRepository.findByTitle(title).orElse(null);
    }
}
