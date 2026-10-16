package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.dto.ShowtimeAssignmentDto;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.repository.ShowtimeRepository;
import com.moviebooking.moviebookingsystem.repository.TheatreRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class ShowtimeService {

    // Default running time used when an older showtime (saved before end
    // times existed) has to be shown in the edit form.
    public static final int DEFAULT_DURATION_MINUTES = 120;

    private final ShowtimeRepository showtimeRepository;
    private final TheatreRepository theatreRepository;

    public ShowtimeService(
            ShowtimeRepository showtimeRepository,
            TheatreRepository theatreRepository) {

        this.showtimeRepository = showtimeRepository;
        this.theatreRepository = theatreRepository;
    }


    // ================= GET ALL SHOWTIMES =================

    public List<Showtime> getAllShowtimes() {

        return showtimeRepository.findAll();
    }

    public List<Showtime> getFutureShowtimes() {
        LocalDate today = LocalDate.now();
        return getAllShowtimes().stream()
                .filter(showtime -> {
                    try {
                        return showtime.getDate() != null
                                && LocalDate.parse(showtime.getDate().trim()).isAfter(today);
                    } catch (DateTimeParseException exception) {
                        return false;
                    }
                })
                .toList();
    }


    // ================= FIND SHOWTIME =================

    public Showtime findShowtimeById(Long id) {

        if (id == null) {
            return null;
        }

        return showtimeRepository.findById(id).orElse(null);
    }


    // ================= ADD SHOWTIME =================

    public void addShowtime(Showtime showtime) {

        showtimeRepository.save(showtime);
    }


    // ================= UPDATE SHOWTIME (fetch existing, merge, save) =================

    public boolean updateShowtime(Showtime updatedShowtime) {

        if (updatedShowtime == null || updatedShowtime.getId() == null) {
            return false;
        }

        Optional<Showtime> existingOpt = showtimeRepository.findById(updatedShowtime.getId());

        if (existingOpt.isEmpty()) {
            return false;
        }

        Showtime existing = existingOpt.get();

        existing.setMovieId(updatedShowtime.getMovieId());
        existing.setMovieTitle(updatedShowtime.getMovieTitle());
        existing.setTheatre(updatedShowtime.getTheatre());
        existing.setDate(updatedShowtime.getDate());
        existing.setShowTime(updatedShowtime.getShowTime());
        existing.setTotalSeats(updatedShowtime.getTotalSeats());
        existing.setAvailableSeats(updatedShowtime.getAvailableSeats());

        showtimeRepository.save(existing);

        return true;
    }


    // ================= DELETE SHOWTIME =================

    public boolean deleteShowtime(Long id) {

        if (id == null || !showtimeRepository.existsById(id)) {
            return false;
        }

        showtimeRepository.deleteById(id);

        return true;
    }

    // ================= TIME PARSING =================

    /**
     * Converts a show time into minutes from midnight so start/end times can
     * be compared. Accepts the formats already stored in the database, for
     * example "06:30 PM", "4.00PM", "1.30AM" and "09:15".
     *
     * @return minutes from midnight, or null when the text is not a time
     */
    public static Integer parseTimeToMinutes(String time) {

        if (time == null) {
            return null;
        }

        String normalised =
                time.trim().toLowerCase().replace(".", ":").replace(" ", "");

        if (normalised.isEmpty()) {
            return null;
        }

        boolean hasMeridiem =
                normalised.endsWith("am") || normalised.endsWith("pm");

        boolean isPm =
                hasMeridiem && normalised.substring(normalised.length() - 2).equals("pm");

        if (hasMeridiem) {
            normalised = normalised.substring(0, normalised.length() - 2);
        }

        String[] parts = normalised.split(":");

        if (parts.length < 2) {
            return null;
        }

        int hours;
        int minutes;

        try {
            hours = Integer.parseInt(parts[0]);
            minutes = Integer.parseInt(parts[1]);
        } catch (NumberFormatException exception) {
            return null;
        }

        if (minutes < 0 || minutes > 59) {
            return null;
        }

        if (!hasMeridiem) {
            if (hours < 0 || hours > 23) {
                return null;
            }
            return hours * 60 + minutes;
        }

        if (hours < 1 || hours > 12) {
            return null;
        }

        int hours24 = hours % 12;

        if (isPm) {
            hours24 += 12;
        }

        return hours24 * 60 + minutes;
    }


    /**
     * Renders minutes from midnight back into a "hh:mm AM/PM" string.
     */
    public static String formatMinutes(int minutesFromMidnight) {

        int total = ((minutesFromMidnight % 1440) + 1440) % 1440;

        int hours24 = total / 60;
        int minutes = total % 60;

        int hours12 = hours24 % 12;

        if (hours12 == 0) {
            hours12 = 12;
        }

        String meridiem = hours24 < 12 ? "AM" : "PM";

        return String.format("%02d:%02d %s", hours12, minutes, meridiem);
    }


    /**
     * End time used when editing a showtime that was saved before the end
     * time column existed.
     */
    public String resolveEndTime(String startTime, String endTime) {

        if (endTime != null && !endTime.isBlank()) {
            return endTime.trim();
        }

        Integer start = parseTimeToMinutes(startTime);

        if (start == null) {
            return null;
        }

        return formatMinutes(start + DEFAULT_DURATION_MINUTES);
    }


    // ================= SEAT SAFETY =================

    /**
     * Available seats can never be negative or greater than total seats.
     */
    public static int clampAvailableSeats(int totalSeats, int availableSeats) {

        int total = Math.max(0, totalSeats);

        return Math.max(0, Math.min(total, availableSeats));
    }


    // ================= CHECK SHOWTIME CONFLICT =================
    // A conflict is any other showtime in the same theatre on the same date
    // whose start/end window overlaps this one. The same check runs on edit.

    public boolean checkShowtimeConflict(ShowtimeAssignmentDto dto) {

        if (dto == null || isBlank(dto.getTheatre()) || isBlank(dto.getDate())
                || isBlank(dto.getShowTime())) {
            return true;
        }

        Integer start = parseTimeToMinutes(dto.getShowTime());
        Integer end = parseTimeToMinutes(dto.getEndTime());

        if (start == null) {
            return true;
        }

        String normalizedTheatre = dto.getTheatre().trim();

        for (Showtime existing : showtimeRepository.findAll()) {

            if (existing.getId() != null && existing.getId().equals(dto.getId())) {
                continue;
            }

            if (!Objects.equals(
                    existing.getTheatre() != null ? existing.getTheatre().trim().toLowerCase() : null,
                    normalizedTheatre.toLowerCase())) {
                continue;
            }

            if (!Objects.equals(existing.getDate(), dto.getDate().trim())) {
                continue;
            }

            Integer existingStart = parseTimeToMinutes(existing.getShowTime());

            if (existingStart == null) {
                continue;
            }

            if (start.equals(existingStart)) {
                return true;
            }

            Integer existingEnd = parseTimeToMinutes(existing.getEndTime());

            // Older rows have no end time, so they only clash on an exact
            // start time match, which is already handled above.
            if (end == null || existingEnd == null) {
                continue;
            }

            boolean overlaps = start < existingEnd && existingStart < end;

            if (overlaps) {
                return true;
            }
        }

        return false;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }


    // ================= ASSIGN SHOWTIME =================

    public Showtime assignShowtime(ShowtimeAssignmentDto dto) {

        if (dto == null) {
            return null;
        }

        String theatreName = dto.getTheatre().trim();
        Theatre theatre = theatreRepository.findByName(theatreName);
        if (theatre == null) {
            for (Theatre t : theatreRepository.findAll()) {
                if (t.getName() != null && t.getName().trim().equalsIgnoreCase(theatreName)) {
                    theatre = t;
                    break;
                }
            }
        }
        if (theatre == null) {
            return null;
        }

        int capacity = Math.max(0, theatre.getTotalSeats());

        Showtime showtime = new Showtime();

        showtime.setMovieId(dto.getMovieId());
        showtime.setMovieTitle(dto.getMovieTitle());
        showtime.setTheatre(theatre.getName().trim());
        showtime.setDate(dto.getDate());
        showtime.setShowTime(dto.getShowTime());
        showtime.setEndTime(dto.getEndTime());
        showtime.setTotalSeats(capacity);

        // A brand new showtime starts with every seat free
        showtime.setAvailableSeats(capacity);
        showtime.setTicketPrice(dto.getTicketPrice());

        showtimeRepository.save(showtime);

        return showtime;
    }


    // ================= UPDATE SHOWTIME FROM DTO =================

    @Transactional
    public boolean updateShowtimeFromDto(ShowtimeAssignmentDto dto) {

        if (dto == null || dto.getId() == null) {
            return false;
        }

        Optional<Showtime> existingOpt = showtimeRepository.findById(dto.getId());

        if (existingOpt.isEmpty()) {
            return false;
        }

        Showtime existing = existingOpt.get();

        existing.setMovieId(dto.getMovieId());
        existing.setMovieTitle(dto.getMovieTitle());

        String theatreName = dto.getTheatre().trim();
        Theatre theatre = theatreRepository.findByName(theatreName);
        if (theatre == null) {
            for (Theatre t : theatreRepository.findAll()) {
                if (t.getName() != null && t.getName().trim().equalsIgnoreCase(theatreName)) {
                    theatre = t;
                    break;
                }
            }
        }
        if (theatre != null) {
            existing.setTheatre(theatre.getName().trim());
        } else {
            existing.setTheatre(theatreName);
        }

        existing.setDate(dto.getDate());
        existing.setShowTime(dto.getShowTime());
        existing.setEndTime(dto.getEndTime());
        existing.setTicketPrice(dto.getTicketPrice());

        if (theatre != null) {
            int newCapacity = Math.max(0, theatre.getTotalSeats());
            int bookedSeats = Math.max(0, existing.getTotalSeats() - existing.getAvailableSeats());
            existing.setTotalSeats(newCapacity);

            existing.setAvailableSeats(
                    clampAvailableSeats(newCapacity, newCapacity - bookedSeats)
            );
        } else {
            existing.setAvailableSeats(
                    clampAvailableSeats(existing.getTotalSeats(), existing.getAvailableSeats())
            );
        }

        showtimeRepository.save(existing);

        return true;
    }


    // ================= FIND SHOWTIMES BY MOVIE =================

    public List<Showtime> findShowtimesByMovie(Long movieId) {

        if (movieId == null) {
            return List.of();
        }

        return getAllShowtimes().stream()
                .filter(showtime -> showtime.getMovieId() != null
                        && showtime.getMovieId().equals(movieId))
                .toList();
    }


    // ================= FIND SHOWTIME BY DETAILS =================

    public Showtime findShowtimeByDetails(Long movieId, String theatre, String date, String showTime) {

        if (movieId == null || theatre == null || date == null || showTime == null) {
            return null;
        }

        for (Showtime showtime : getAllShowtimes()) {

            if (showtime.getMovieId() != null && showtime.getMovieId().equals(movieId)
                    && theatre.equals(showtime.getTheatre())
                    && date.equals(showtime.getDate())
                    && showTime.equals(showtime.getShowTime())) {

                return showtime;
            }
        }

        return null;
    }
}
