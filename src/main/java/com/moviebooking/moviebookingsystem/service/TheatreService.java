package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.repository.TheatreRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class TheatreService {

    private final TheatreRepository theatreRepository;

    public TheatreService(TheatreRepository theatreRepository) {
        this.theatreRepository = theatreRepository;
    }


    // =========================
    // GET ALL THEATRES
    // =========================

    public List<Theatre> getAllTheatres() {

        return theatreRepository.findAll();
    }


    // =========================
    // FIND THEATRE BY ID
    // =========================

    public Theatre findTheatreById(Long id) {

        if (id == null) {
            return null;
        }

        return theatreRepository.findById(id).orElse(null);
    }


    // =========================
    // SAVE THEATRE
    // =========================

    public void saveTheatre(Theatre theatre) {

        theatreRepository.save(theatre);
    }


    // =========================
    // ADD THEATRE
    // =========================

    public void addTheatre(Theatre theatre) {

        theatreRepository.save(theatre);
    }


    // =========================
    // UPDATE THEATRE (fetch existing, merge, save)
    // =========================

    public boolean updateTheatre(Theatre updatedTheatre) {

        if (updatedTheatre == null || updatedTheatre.getId() == null) {
            return false;
        }

        Optional<Theatre> existingOpt = theatreRepository.findById(updatedTheatre.getId());

        if (existingOpt.isEmpty()) {
            return false;
        }

        Theatre existing = existingOpt.get();

        existing.setName(updatedTheatre.getName());
        existing.setLocation(updatedTheatre.getLocation());
        existing.setTotalSeats(updatedTheatre.getTotalSeats());
        existing.setStatus(updatedTheatre.getStatus());

        theatreRepository.save(existing);

        return true;
    }


    // =========================
    // FIND THEATRE BY NAME
    // =========================

    public Theatre findTheatreByName(String name) {

        if (name == null || name.trim().isEmpty()) {
            return null;
        }

        String trimmed = name.trim();

        for (Theatre theatre : theatreRepository.findAll()) {
            if (theatre.getName() != null && theatre.getName().equals(trimmed)) {
                return theatre;
            }
        }

        return null;
    }


    // =========================
    // IS UNDER MAINTENANCE
    // =========================

    /**
     * A theatre under maintenance must not be given new showtimes.
     */
    public boolean isUnderMaintenance(String name) {

        Theatre theatre = findTheatreByName(name);

        return theatre != null && theatre.isUnderMaintenance();
    }


    // =========================
    // DELETE THEATRE
    // =========================

    public boolean deleteTheatre(Long id) {

        if (id == null || !theatreRepository.existsById(id)) {
            return false;
        }

        theatreRepository.deleteById(id);

        return true;
    }
}
