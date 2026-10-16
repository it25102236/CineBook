package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.dto.ShowtimeAssignmentDto;
import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import com.moviebooking.moviebookingsystem.service.TheatreService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Objects;

@Controller
public class TheatreManagerController {

    private final TheatreService theatreService;
    private final ShowtimeService showtimeService;
    private final BookingService bookingService;
    private final MovieService movieService;


    // ================= CONSTRUCTOR =================

    public TheatreManagerController(
            TheatreService theatreService,
            ShowtimeService showtimeService,
            BookingService bookingService,
            MovieService movieService) {

        this.theatreService = theatreService;
        this.showtimeService = showtimeService;
        this.bookingService = bookingService;
        this.movieService = movieService;
    }


    // ================= DASHBOARD =================

    @GetMapping("/theatre-manager")
    public String dashboard(Model model) {

        model.addAttribute(
                "theatreCount",
                theatreService.getAllTheatres().size()
        );

        model.addAttribute(
                "theatres",
                theatreService.getAllTheatres()
        );

        model.addAttribute(
                "showtimes",
                showtimeService.getAllShowtimes()
        );

        model.addAttribute(
                "showtimeCount",
                showtimeService.getAllShowtimes().size()
        );

        model.addAttribute(
                "movieCount",
                movieService.getAllMovies().size()
        );

        model.addAttribute(
                "bookingCount",
                bookingService.getAllBookings().size()
        );

        return "theatre/dashboard";
    }


    // ================= SHOWTIME LIST =================

    @GetMapping("/theatre-manager/showtimes")
    public String showtimes(Model model) {

        model.addAttribute(
                "showtimes",
                showtimeService.getAllShowtimes()
        );

        return "theatre/showtimes";
    }


    // ================= ADD SHOWTIME PAGE =================

    @GetMapping("/theatre-manager/showtimes/add")
    public String addShowtimePage(Model model) {

        model.addAttribute(
                "showtimeDto",
                new ShowtimeAssignmentDto()
        );

        model.addAttribute(
                "theatres",
                theatreService.getAllTheatres()
        );

        model.addAttribute(
                "movies",
                movieService.getAllMovies()
        );

        model.addAttribute(
                "selectedTheatreCapacity",
                null
        );

        model.addAttribute("formAction", "/theatre-manager/showtimes/add");

        // The picker starts at today so no past date can be chosen
        addDatePickerMin(model, null);

        // Blank inputs so the manager picks the times
        model.addAttribute("startHour", "");
        model.addAttribute("startMinute", "");
        model.addAttribute("startPeriod", "AM");
        model.addAttribute("endHour", "");
        model.addAttribute("endMinute", "");
        model.addAttribute("endPeriod", "AM");

        return "theatre/showtime-form";
    }


    // ================= ADD SHOWTIME =================

    @PostMapping("/theatre-manager/showtimes/add")
    public String addShowtime(
            @ModelAttribute("showtimeDto") ShowtimeAssignmentDto dto,
            @RequestParam(required = false) String startHour,
            @RequestParam(required = false) String startMinute,
            @RequestParam(required = false) String startPeriod,
            @RequestParam(required = false) String endHour,
            @RequestParam(required = false) String endMinute,
            @RequestParam(required = false) String endPeriod,
            Model model) {

        String timeError = applyTimeParts(
                dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);

        if (timeError != null) {
            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
            model.addAttribute("error", timeError);
            return "theatre/showtime-form";
        }

        populateMovieTitle(dto);

        if (dto.isNewTheatreSelected()) {
            if (dto.getNewTheatreName() == null || dto.getNewTheatreName().trim().isEmpty()) {
                addShowtimeFormModel(
                        model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
                model.addAttribute(
                        "error",
                        "Please enter a theatre name."
                );
                return "theatre/showtime-form";
            }

            String theatreName = dto.getNewTheatreName().trim();

            if (theatreService.getAllTheatres().stream()
                    .anyMatch(existing -> existing.getName() != null
                            && existing.getName().equalsIgnoreCase(theatreName))) {
                addShowtimeFormModel(
                        model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
                model.addAttribute(
                        "error",
                        "A theatre with this name already exists. Please select it from the list."
                );
                return "theatre/showtime-form";
            }

            Integer newCapacity = dto.getNewTheatreCapacity();

            if (newCapacity == null || newCapacity < 1 || newCapacity > 500) {
                addShowtimeFormModel(
                        model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
                model.addAttribute(
                        "error",
                        "Theatre capacity is required and must be between 1 and 500."
                );
                return "theatre/showtime-form";
            }

            Theatre newTheatre = new Theatre();
            newTheatre.setName(theatreName);
            newTheatre.setLocation(dto.getNewTheatreLocation() != null ? dto.getNewTheatreLocation().trim() : "");
            newTheatre.setTotalSeats(newCapacity);
            newTheatre.setStatus(Theatre.STATUS_AVAILABLE);

            theatreService.saveTheatre(newTheatre);

            dto.setTheatre(newTheatre.getName().trim());
            dto.setTotalSeats(newTheatre.getTotalSeats());
        }

        String validationError = validateShowtimeInput(dto, null);

        if (validationError != null) {
            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
            model.addAttribute("error", validationError);
            return "theatre/showtime-form";
        }

        // A theatre under maintenance cannot be given new showtimes
        if (theatreService.isUnderMaintenance(dto.getTheatre())) {
            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
            model.addAttribute(
                    "error",
                    dto.getTheatre() + " is under maintenance and cannot be assigned to a new showtime."
            );
            return "theatre/showtime-form";
        }

        if (showtimeService.checkShowtimeConflict(dto)) {

            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);

            model.addAttribute(
                    "selectedTheatreCapacity",
                    lookupTheatreCapacity(dto.getTheatre())
            );

            model.addAttribute(
                    "error",
                    "This showtime overlaps an existing showtime in the same theatre on the selected date."
            );

            return "theatre/showtime-form";
        }

        Showtime assigned = showtimeService.assignShowtime(dto);

        if (assigned == null) {
            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
            model.addAttribute(
                    "selectedTheatreCapacity",
                    lookupTheatreCapacity(dto.getTheatre())
            );
            model.addAttribute(
                    "error",
                    "The selected theatre no longer exists."
            );
            return "theatre/showtime-form";
        }

        return "redirect:/theatre-manager/showtimes";
    }


    // ================= TIME INPUT =================
    // Start and end times are typed as an hour, minutes and an AM/PM choice,
    // then combined into the "hh:mm AM/PM" value the showtime stores.

    /**
     * Builds the stored start/end times from the submitted hour, minutes and
     * AM/PM selector.
     *
     * @return an error message, or null when both times were valid
     */
    private String applyTimeParts(
            ShowtimeAssignmentDto dto,
            String startHour,
            String startMinute,
            String startPeriod,
            String endHour,
            String endMinute,
            String endPeriod) {

        String startTime = combineTime(startHour, startMinute, startPeriod);

        if (startTime == null) {
            return "Start time must be an hour between 1 and 12, minutes between 0 and 59, and AM or PM.";
        }

        String endTime = combineTime(endHour, endMinute, endPeriod);

        if (endTime == null) {
            return "End time must be an hour between 1 and 12, minutes between 0 and 59, and AM or PM.";
        }

        dto.setShowTime(startTime);
        dto.setEndTime(endTime);

        return null;
    }


    /**
     * @return "hh:mm AM/PM", or null when the parts are not a valid time
     */
    private String combineTime(String hour, String minute, String period) {

        if (hour == null || minute == null) {
            return null;
        }

        String trimmedHour = hour.trim();
        String trimmedMinute = minute.trim();

        if (!trimmedHour.matches("^[0-9]{1,2}$") || !trimmedMinute.matches("^[0-9]{1,2}$")) {
            return null;
        }

        int hourValue = Integer.parseInt(trimmedHour);
        int minuteValue = Integer.parseInt(trimmedMinute);

        if (hourValue < 1 || hourValue > 12 || minuteValue < 0 || minuteValue > 59) {
            return null;
        }

        String meridiem = (period == null || period.trim().isEmpty())
                ? "AM"
                : period.trim().toUpperCase();

        if (!"AM".equals(meridiem) && !"PM".equals(meridiem)) {
            return null;
        }

        return String.format("%02d:%02d %s", hourValue, minuteValue, meridiem);
    }


    /**
     * Splits a stored time into hour, minutes and AM/PM so the form can show
     * the current values. Showtimes saved with an older format still load.
     */
    private String[] timePartsForForm(String time) {

        Integer minutes = ShowtimeService.parseTimeToMinutes(time);

        if (minutes == null) {
            return new String[]{"", "", "AM"};
        }

        String[] split = ShowtimeService.formatMinutes(minutes).split("\\s+");

        if (split.length < 2) {
            return new String[]{"", "", "AM"};
        }

        String[] hourAndMinute = split[0].split(":");

        if (hourAndMinute.length < 2) {
            return new String[]{"", "", split[1]};
        }

        return new String[]{hourAndMinute[0], hourAndMinute[1], split[1]};
    }


    // ================= SHOWTIME VALIDATION =================

    private String validateShowtimeInput(ShowtimeAssignmentDto dto, String existingDate) {

        if (dto.getMovieId() == null) {
            return "Please select a movie.";
        }

        if (movieService.findMovieById(dto.getMovieId()) == null) {
            return "The selected movie no longer exists.";
        }

        if (dto.getTheatre() == null || dto.getTheatre().trim().isEmpty()) {
            return "Please select a theatre.";
        }

        if (lookupTheatreCapacity(dto.getTheatre().trim()) == null) {
            return "The selected theatre no longer exists.";
        }

        if (dto.getDate() == null || dto.getDate().trim().isEmpty()) {
            return "Please select a date.";
        }

        LocalDate selectedDate;

        try {
            selectedDate = LocalDate.parse(dto.getDate().trim());
        } catch (DateTimeParseException exception) {
            return "Invalid date";
        }

        // A date in the past can never be scheduled. A showtime that is
        // already stored keeps its own date so past rows stay editable.
        if (selectedDate.isBefore(LocalDate.now()) && !isSameDate(selectedDate, existingDate)) {
            return "Invalid date. Please select today or a future date.";
        }

        if (dto.getShowTime() == null || dto.getShowTime().trim().isEmpty()) {
            return "Please enter a start time.";
        }

        Integer start = ShowtimeService.parseTimeToMinutes(dto.getShowTime());

        if (start == null) {
            return "Start time is not valid. Use a format like 06:30 PM.";
        }

        if (dto.getEndTime() == null || dto.getEndTime().trim().isEmpty()) {
            return "Please enter an end time.";
        }

        Integer end = ShowtimeService.parseTimeToMinutes(dto.getEndTime());

        if (end == null) {
            return "End time is not valid. Use a format like 09:00 PM.";
        }

        if (start >= end) {
            return "Start time must be earlier than end time.";
        }

        if (dto.getTicketPrice() <= 0) {
            return "Ticket price must be greater than 0.";
        }

        return null;
    }


    // ================= SHOWTIME DATE =================

    /**
     * @return true when the submitted date is the date already stored on the
     *         showtime being edited
     */
    private boolean isSameDate(LocalDate selectedDate, String existingDate) {

        if (existingDate == null || existingDate.trim().isEmpty()) {
            return false;
        }

        try {
            return LocalDate.parse(existingDate.trim()).equals(selectedDate);
        } catch (DateTimeParseException exception) {
            return false;
        }
    }


    /**
     * The date picker must not offer days that have already passed. A
     * showtime stored in the past can still be opened and edited, so its own
     * date stays selectable.
     */
    private void addDatePickerMin(Model model, Long showtimeId) {

        LocalDate min = LocalDate.now();

        if (showtimeId != null) {
            Showtime existing = showtimeService.findShowtimeById(showtimeId);

            if (existing != null && existing.getDate() != null) {
                try {
                    LocalDate stored = LocalDate.parse(existing.getDate().trim());
                    if (stored.isBefore(min)) {
                        min = stored;
                    }
                } catch (DateTimeParseException ignored) {
                    // A date that cannot be read simply leaves the picker at today
                }
            }
        }

        model.addAttribute("datePickerMin", min.toString());
    }


    // ================= HELPER METHODS =================

    private void populateMovieTitle(ShowtimeAssignmentDto dto) {
        if (dto.getMovieId() != null) {
            Movie movie = movieService.findMovieById(dto.getMovieId());
            if (movie != null) {
                dto.setMovieTitle(movie.getTitle());
            }
        }
    }

    private void addShowtimeFormModel(
            Model model,
            ShowtimeAssignmentDto dto,
            String startHour,
            String startMinute,
            String startPeriod,
            String endHour,
            String endMinute,
            String endPeriod) {
        model.addAttribute("showtimeDto", dto);
        model.addAttribute("theatres", theatreService.getAllTheatres());
        model.addAttribute("movies", movieService.getAllMovies());
        model.addAttribute(
                "selectedTheatreCapacity",
                dto == null ? null : lookupTheatreCapacity(dto.getTheatre())
        );
        model.addAttribute("startHour", startHour);
        model.addAttribute("startMinute", startMinute);
        model.addAttribute("startPeriod", normalisePeriod(startPeriod));
        model.addAttribute("endHour", endHour);
        model.addAttribute("endMinute", endMinute);
        model.addAttribute("endPeriod", normalisePeriod(endPeriod));

        if (dto != null && dto.getId() != null) {
            model.addAttribute("formAction", "/theatre-manager/showtimes/edit");
        } else {
            model.addAttribute("formAction", "/theatre-manager/showtimes/add");
        }

        // Keeps the picker bound to today, or to the stored date of a
        // showtime that is already in the past
        addDatePickerMin(model, dto == null ? null : dto.getId());
    }

    private String normalisePeriod(String period) {
        if (period == null || period.trim().isEmpty()) {
            return "AM";
        }
        String value = period.trim().toUpperCase();
        return "PM".equals(value) ? "PM" : "AM";
    }

    private Integer lookupTheatreCapacity(String theatreName) {
        if (theatreName == null || theatreName.trim().isEmpty()) {
            return null;
        }
        String normalized = theatreName.trim().toLowerCase();
        for (Theatre theatre : theatreService.getAllTheatres()) {
            if (theatre.getName() != null && theatre.getName().trim().toLowerCase().equals(normalized)) {
                return theatre.getTotalSeats();
            }
        }
        return null;
    }

    private String canonicalTheatreName(String theatreName) {
        if (theatreName == null || theatreName.trim().isEmpty()) {
            return theatreName;
        }
        String normalized = theatreName.trim().toLowerCase();
        for (Theatre theatre : theatreService.getAllTheatres()) {
            if (theatre.getName() != null && theatre.getName().trim().toLowerCase().equals(normalized)) {
                return theatre.getName().trim();
            }
        }
        return theatreName.trim();
    }

    private boolean normalizedEquals(String a, String b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        return a.trim().toLowerCase().equals(b.trim().toLowerCase());
    }


    // ================= EDIT SHOWTIME PAGE =================

    @GetMapping("/theatre-manager/showtimes/edit")
    public String editShowtimePage(
            @RequestParam Long id,
            Model model) {

        Showtime showtime =
                showtimeService.findShowtimeById(id);

        if (showtime == null) {
            return "redirect:/theatre-manager/showtimes";
        }

        ShowtimeAssignmentDto dto = new ShowtimeAssignmentDto();
        dto.setId(showtime.getId());
        dto.setMovieId(showtime.getMovieId());
        dto.setMovieTitle(showtime.getMovieTitle());
        dto.setTheatre(showtime.getTheatre());
        dto.setDate(showtime.getDate());
        dto.setShowTime(showtime.getShowTime());

        // Showtimes saved before end times existed get a sensible default
        dto.setEndTime(showtimeService.resolveEndTime(
                showtime.getShowTime(),
                showtime.getEndTime()
        ));

        dto.setTotalSeats(showtime.getTotalSeats());
        dto.setTicketPrice(showtime.getTicketPrice());

        model.addAttribute("showtimeDto", dto);
        model.addAttribute("theatres", theatreService.getAllTheatres());
        model.addAttribute("movies", movieService.getAllMovies());
        model.addAttribute("selectedAvailableSeats", showtime.getAvailableSeats());

        // The stored start/end times are split back into hour, minutes and
        // AM/PM so the manual inputs show the current values
        String[] startParts = timePartsForForm(dto.getShowTime());
        String[] endParts = timePartsForForm(dto.getEndTime());

        model.addAttribute("startHour", startParts[0]);
        model.addAttribute("startMinute", startParts[1]);
        model.addAttribute("startPeriod", startParts[2]);
        model.addAttribute("endHour", endParts[0]);
        model.addAttribute("endMinute", endParts[1]);
        model.addAttribute("endPeriod", endParts[2]);

        addDatePickerMin(model, showtime.getId());

        Integer capacity = null;
        if (showtime.getTheatre() != null) {
            String normalized = showtime.getTheatre().trim().toLowerCase();
            for (Theatre theatre : theatreService.getAllTheatres()) {
                if (theatre.getName() != null && theatre.getName().trim().toLowerCase().equals(normalized)) {
                    capacity = theatre.getTotalSeats();
                    break;
                }
            }
        }
        model.addAttribute("selectedTheatreCapacity", capacity);

        model.addAttribute("formAction", "/theatre-manager/showtimes/edit");

        return "theatre/showtime-form";
    }


    // ================= UPDATE SHOWTIME =================

    @PostMapping("/theatre-manager/showtimes/edit")
    public String updateShowtime(
            @ModelAttribute("showtimeDto") ShowtimeAssignmentDto dto,
            @RequestParam(required = false) String startHour,
            @RequestParam(required = false) String startMinute,
            @RequestParam(required = false) String startPeriod,
            @RequestParam(required = false) String endHour,
            @RequestParam(required = false) String endMinute,
            @RequestParam(required = false) String endPeriod,
            Model model,
            RedirectAttributes redirectAttributes) {

        if (dto.getId() == null) {
            return "redirect:/theatre-manager/showtimes";
        }

        Showtime existing = showtimeService.findShowtimeById(dto.getId());

        if (existing == null) {
            redirectAttributes.addFlashAttribute("error", "The selected showtime no longer exists.");
            return "redirect:/theatre-manager/showtimes";
        }

        if (dto.getTheatre() != null) {
            dto.setTheatre(canonicalTheatreName(dto.getTheatre()));
        }

        String timeError = applyTimeParts(
                dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);

        if (timeError != null) {
            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
            model.addAttribute(
                    "selectedTheatreCapacity",
                    lookupTheatreCapacity(dto.getTheatre())
            );
            model.addAttribute("error", timeError);
            return "theatre/showtime-form";
        }

        populateMovieTitle(dto);

        String validationError = validateShowtimeInput(dto, existing.getDate());

        if (validationError != null) {
            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);
            model.addAttribute(
                    "selectedTheatreCapacity",
                    lookupTheatreCapacity(dto.getTheatre())
            );
            model.addAttribute("error", validationError);
            return "theatre/showtime-form";
        }

        boolean theatreChanged =
                !normalizedEquals(existing.getTheatre(), dto.getTheatre());

        if (theatreChanged && theatreService.isUnderMaintenance(dto.getTheatre())) {

            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);

            model.addAttribute(
                    "selectedTheatreCapacity",
                    lookupTheatreCapacity(dto.getTheatre())
            );

            model.addAttribute(
                    "error",
                    dto.getTheatre() + " is under maintenance and cannot be assigned to a showtime."
            );

            return "theatre/showtime-form";
        }

        if (showtimeService.checkShowtimeConflict(dto)) {

            addShowtimeFormModel(
                    model, dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);

            model.addAttribute(
                    "selectedTheatreCapacity",
                    lookupTheatreCapacity(dto.getTheatre())
            );

            model.addAttribute(
                    "error",
                    "This showtime overlaps an existing showtime in the same theatre on the selected date."
            );

            return "theatre/showtime-form";
        }

        boolean updated = showtimeService.updateShowtimeFromDto(dto);

        if (!updated) {
            redirectAttributes.addFlashAttribute("error", "The selected showtime no longer exists.");
            return "redirect:/theatre-manager/showtimes";
        }

        redirectAttributes.addFlashAttribute("success", "Showtime updated successfully.");
        return "redirect:/theatre-manager/showtimes";
    }

    // ================= UPDATE SHOWTIME (AJAX) =================

    @PostMapping("/theatre-manager/showtimes/edit-ajax")
    public ResponseEntity<Map<String, Object>> updateShowtimeAjax(
            @ModelAttribute("showtimeDto") ShowtimeAssignmentDto dto,
            @RequestParam(required = false) String startHour,
            @RequestParam(required = false) String startMinute,
            @RequestParam(required = false) String startPeriod,
            @RequestParam(required = false) String endHour,
            @RequestParam(required = false) String endMinute,
            @RequestParam(required = false) String endPeriod) {

        Showtime existing = showtimeService.findShowtimeById(dto.getId());

        if (existing == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "The selected showtime no longer exists."));
        }

        ShowtimeUpdateResult result =
                updateShowtimeCore(dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod, existing);

        if (result.isSuccess()) {
            Showtime updatedShowtime = showtimeService.findShowtimeById(dto.getId());
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "showtime", (updatedShowtime != null
                            ? Map.of(
                                    "id", updatedShowtime.getId(),
                                    "movieTitle", updatedShowtime.getMovieTitle(),
                                    "theatre", updatedShowtime.getTheatre(),
                                    "date", updatedShowtime.getDate(),
                                    "showTime", updatedShowtime.getShowTime(),
                                    "endTime", updatedShowtime.getEndTime(),
                                    "totalSeats", updatedShowtime.getTotalSeats(),
                                    "availableSeats", updatedShowtime.getAvailableSeats(),
                                    "ticketPrice", updatedShowtime.getTicketPrice())
                            : Map.of())
            ));
        }

        return ResponseEntity.badRequest().body(Map.of("error", result.getError()));
    }

    private ShowtimeUpdateResult updateShowtimeCore(
            ShowtimeAssignmentDto dto,
            String startHour,
            String startMinute,
            String startPeriod,
            String endHour,
            String endMinute,
            String endPeriod,
            Showtime existing) {

        if (dto.getTheatre() != null) {
            dto.setTheatre(canonicalTheatreName(dto.getTheatre()));
        }

        String timeError = applyTimeParts(
                dto, startHour, startMinute, startPeriod, endHour, endMinute, endPeriod);

        if (timeError != null) {
            return ShowtimeUpdateResult.error(timeError);
        }

        populateMovieTitle(dto);

        String validationError = validateShowtimeInput(dto, existing.getDate());

        if (validationError != null) {
            return ShowtimeUpdateResult.error(validationError);
        }

        boolean theatreChanged =
                !normalizedEquals(existing.getTheatre(), dto.getTheatre());

        if (theatreChanged && theatreService.isUnderMaintenance(dto.getTheatre())) {
            return ShowtimeUpdateResult.error(
                    dto.getTheatre() + " is under maintenance and cannot be assigned to a showtime.");
        }

        if (showtimeService.checkShowtimeConflict(dto)) {
            return ShowtimeUpdateResult.error(
                    "This showtime overlaps an existing showtime in the same theatre on the selected date.");
        }

        boolean updated = showtimeService.updateShowtimeFromDto(dto);

        if (!updated) {
            return ShowtimeUpdateResult.error("The selected showtime no longer exists.");
        }

        return ShowtimeUpdateResult.success(existing);
    }

    /** Outcome of a single showtime update attempt. */
    private static final class ShowtimeUpdateResult {
        private final boolean success;
        private final String error;

        private ShowtimeUpdateResult(boolean success, String error) {
            this.success = success;
            this.error = error;
        }

        private static ShowtimeUpdateResult success(Showtime showtime) {
            return new ShowtimeUpdateResult(true, null);
        }

        private static ShowtimeUpdateResult error(String error) {
            return new ShowtimeUpdateResult(false, error);
        }

        public boolean isSuccess() {
            return success;
        }

        public String getError() {
            return error;
        }
    }


    // ================= DELETE SHOWTIME =================

    @PostMapping("/theatre-manager/showtimes/delete")
    public String deleteShowtime(
            @RequestParam Long id) {

        showtimeService.deleteShowtime(id);

        return "redirect:/theatre-manager/showtimes";
    }


    // ================= VIEW BOOKINGS =================

    @GetMapping("/theatre-manager/bookings")
    public String bookings(Model model) {

        model.addAttribute(
                "bookings",
                bookingService.getAllBookings()
        );

        return "theatre/bookings";
    }


    // ================= THEATRE DETAILS =================

    @GetMapping("/theatre-manager/theatre-details")
    public String theatreDetails(
            @RequestParam(required = false) Boolean showAddForm,
            Model model) {

        model.addAttribute(
                "theatres",
                theatreService.getAllTheatres()
        );

        // The "Add New Theatre" form only appears once the button is used
        model.addAttribute("addTheatreFormVisible", Boolean.TRUE.equals(showAddForm));

        if (model.getAttribute("newTheatre") == null) {
            model.addAttribute("newTheatre", new Theatre());
        }

        return "theatre/theatre-details";
    }


    // ================= ADD THEATRE PAGE =================

    @GetMapping("/theatre-manager/theatres/add")
    public String addTheatrePage(Model model) {

        model.addAttribute("theatre", new Theatre());

        return "theatre/theatre-form";
    }


    // ================= ADD THEATRE =================

    @PostMapping("/theatre-manager/theatres/add")
    public String addTheatre(
            @RequestParam String name,
            @RequestParam String location,
            @RequestParam String totalSeats,
            @RequestParam(required = false) String status,
            Model model) {

        String validationError = validateTheatreInput(name, location, totalSeats, status, null);

        if (validationError == null && theatreService.getAllTheatres().stream()
                .anyMatch(existing -> existing.getName() != null
                        && existing.getName().equalsIgnoreCase(name.trim()))) {
            validationError = "A theatre with this name already exists.";
        }

        if (validationError != null) {

            addTheatreListModel(model);

            // The rejected values are put back so the manager can correct
            // them, and the form stays open on top of the list
            Theatre submitted = new Theatre();
            submitted.setName(name);
            submitted.setLocation(location);
            submitted.setTotalSeats(parseIntSafe(totalSeats));
            submitted.setStatus(normaliseStatus(status));

            model.addAttribute("newTheatre", submitted);
            model.addAttribute("addTheatreFormVisible", true);
            model.addAttribute("error", validationError);

            return "theatre/theatre-details";
        }

        Theatre theatre = new Theatre();
        theatre.setName(name.trim());
        theatre.setLocation(location.trim());
        theatre.setTotalSeats(parseIntSafe(totalSeats));
        theatre.setStatus(normaliseStatus(status));

        theatreService.addTheatre(theatre);

        return "redirect:/theatre-manager/theatre-details";
    }


    // ================= EDIT THEATRE PAGE =================

    @GetMapping("/theatre-manager/theatres/edit")
    public String editTheatrePage(
            @RequestParam Long id,
            Model model) {

        Theatre theatre = theatreService.findTheatreById(id);

        if (theatre == null) {
            model.addAttribute("error", "Theatre not found.");
            model.addAttribute("theatre", new Theatre());
            return "theatre/theatre-form";
        }

        model.addAttribute("theatre", theatre);

        return "theatre/theatre-form";
    }


    // ================= UPDATE THEATRE =================

    @PostMapping("/theatre-manager/theatres/edit")
    public String updateTheatre(
            @RequestParam Long id,
            @RequestParam String name,
            @RequestParam String location,
            @RequestParam String totalSeats,
            @RequestParam(required = false) String status,
            Model model) {

        String validationError = validateTheatreInput(name, location, totalSeats, status, id);

        if (validationError == null && theatreService.getAllTheatres().stream()
                .anyMatch(existing -> !Objects.equals(existing.getId(), id)
                        && existing.getName() != null
                        && existing.getName().equalsIgnoreCase(name.trim()))) {
            validationError = "A theatre with this name already exists.";
        }

        if (validationError != null) {
            model.addAttribute("error", validationError);
            Theatre t = new Theatre();
            t.setId(id);
            t.setName(name);
            t.setLocation(location);
            t.setTotalSeats(parseIntSafe(totalSeats));
            t.setStatus(normaliseStatus(status));
            model.addAttribute("theatre", t);
            return "theatre/theatre-form";
        }

        Theatre theatre = new Theatre();
        theatre.setId(id);
        theatre.setName(name.trim());
        theatre.setLocation(location.trim());
        theatre.setTotalSeats(parseIntSafe(totalSeats));
        theatre.setStatus(normaliseStatus(status));

        boolean updated = theatreService.updateTheatre(theatre);

        if (!updated) {
            return "redirect:/theatre-manager/theatre-details";
        }

        // Showtimes and bookings reference the theatre by name, so a rename
        // has to be carried across or the history would be orphaned.
        String previousName = findTheatreName(id);

        if (previousName != null && !previousName.equals(theatre.getName())) {
            renameTheatreReferences(previousName, theatre.getName());
        }

        return "redirect:/theatre-manager/theatre-details";
    }


    // ================= DELETE THEATRE =================

    @PostMapping("/theatre-manager/theatres/delete")
    public String deleteTheatre(
            @RequestParam Long id,
            Model model) {

        Theatre theatre = theatreService.findTheatreById(id);

        if (theatre == null) {
            return "redirect:/theatre-manager/theatre-details";
        }

        String theatreName = theatre.getName();
        String normalizedName = theatreName.trim().toLowerCase();

        long showtimeCount = showtimeService.getAllShowtimes().stream()
                .filter(showtime -> showtime.getTheatre() != null
                        && showtime.getTheatre().trim().toLowerCase().equals(normalizedName))
                .count();

        if (showtimeCount > 0) {
            return refuseTheatreDelete(
                    model,
                    "This theatre still has " + showtimeCount
                            + " showtime(s). Remove them before deleting the theatre."
            );
        }

        long bookingCount = bookingService.getAllBookings().stream()
                .filter(booking -> booking.getTheatre() != null
                        && booking.getTheatre().trim().toLowerCase().equals(normalizedName))
                .count();

        if (bookingCount > 0) {
            return refuseTheatreDelete(
                    model,
                    "This theatre has " + bookingCount
                            + " booking(s) and cannot be deleted."
            );
        }

        theatreService.deleteTheatre(id);

        return "redirect:/theatre-manager/theatre-details";
    }


    // ================= THEATRE HELPERS =================

    private String refuseTheatreDelete(Model model, String message) {

        addTheatreListModel(model);
        model.addAttribute("error", message);

        return "theatre/theatre-details";
    }

    private void addTheatreListModel(Model model) {

        model.addAttribute("theatres", theatreService.getAllTheatres());
        model.addAttribute("newTheatre", new Theatre());
        model.addAttribute("addTheatreFormVisible", false);
    }

    private String findTheatreName(Long id) {

        Theatre theatre = theatreService.findTheatreById(id);

        return theatre == null ? null : theatre.getName();
    }

    private void renameTheatreReferences(String previousName, String newName) {

        if (previousName == null || newName == null || previousName.equals(newName)) {
            return;
        }

        String normalizedPrevious = previousName.trim().toLowerCase();

        for (Showtime showtime : showtimeService.getAllShowtimes()) {

            if (showtime.getTheatre() != null
                    && showtime.getTheatre().trim().toLowerCase().equals(normalizedPrevious)) {
                showtime.setTheatre(newName.trim());
                showtimeService.addShowtime(showtime);
            }
        }

        for (Booking booking : bookingService.getAllBookings()) {

            if (booking.getTheatre() != null
                    && booking.getTheatre().trim().toLowerCase().equals(normalizedPrevious)) {
                booking.setTheatre(newName.trim());
                bookingService.saveBooking(booking);
            }
        }
    }


    // ================= VALIDATION HELPERS =================

    private String validateTheatreInput(
            String name,
            String location,
            String totalSeatsStr,
            String status,
            Long excludeId) {
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

        if (location == null || location.trim().isEmpty()) {
            return "Location is required.";
        }
        String trimmedLocation = location.trim();
        if (trimmedLocation.length() < 2 || trimmedLocation.length() > 200) {
            return "Location must be between 2 and 200 characters.";
        }

        // Capacity accepts digits only: no signs, decimals, spaces or letters
        if (totalSeatsStr == null || totalSeatsStr.trim().isEmpty()) {
            return "Total seats is required.";
        }
        if (!totalSeatsStr.trim().matches("^[0-9]+$")) {
            return "Total seats must be a whole number (digits only).";
        }
        int totalSeats;
        try {
            totalSeats = Integer.parseInt(totalSeatsStr.trim());
        } catch (NumberFormatException exception) {
            return "Total seats must be a whole number (digits only).";
        }
        if (totalSeats < 1 || totalSeats > 500) {
            return "Total seats must be between 1 and 500.";
        }

        if (!isValidTheatreStatus(status)) {
            return "Status must be either AVAILABLE or MAINTENANCE.";
        }

        return null;
    }


    // ================= THEATRE STATUS HELPERS =================

    /**
     * A blank status is treated as AVAILABLE so existing call sites and
     * theatres saved before the column existed keep working.
     */
    private String normaliseStatus(String status) {

        if (status == null || status.trim().isEmpty()) {
            return Theatre.STATUS_AVAILABLE;
        }

        return status.trim().toUpperCase();
    }


    private boolean isValidTheatreStatus(String status) {

        if (status == null || status.trim().isEmpty()) {
            return true;
        }

        String value = status.trim().toUpperCase();

        return Theatre.STATUS_AVAILABLE.equals(value)
                || Theatre.STATUS_MAINTENANCE.equals(value);
    }

    private int parseIntSafe(String s) {
        try {
            return s != null ? Integer.parseInt(s.trim()) : 0;
        } catch (NumberFormatException e) {
            return 0;
        }
    }


}