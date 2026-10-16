package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.dto.ShowtimeAssignmentDto;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.repository.ShowtimeRepository;
import com.moviebooking.moviebookingsystem.repository.TheatreRepository;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import com.moviebooking.moviebookingsystem.service.TheatreService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TheatreManagerShowtimeRulesTests {

    private static final String DATE = "2099-05-20";

    // ================= TIME PARSING =================

    @Test
    void parsesTheTimeFormatsAlreadyStoredInTheDatabase() {
        assertEquals(18 * 60 + 30, ShowtimeService.parseTimeToMinutes("06:30 PM"));
        assertEquals(16 * 60, ShowtimeService.parseTimeToMinutes("4.00PM"));
        assertEquals(1 * 60 + 30, ShowtimeService.parseTimeToMinutes("1.30AM"));
        assertEquals(6 * 60, ShowtimeService.parseTimeToMinutes("6.00AM"));
        assertEquals(9 * 60 + 15, ShowtimeService.parseTimeToMinutes("09:15"));
        assertEquals(0, ShowtimeService.parseTimeToMinutes("12:00 AM"));
        assertEquals(12 * 60, ShowtimeService.parseTimeToMinutes("12:00 PM"));
    }

    @Test
    void rejectsValuesThatAreNotTimes() {
        assertNull(ShowtimeService.parseTimeToMinutes(null));
        assertNull(ShowtimeService.parseTimeToMinutes(""));
        assertNull(ShowtimeService.parseTimeToMinutes("not a time"));
        assertNull(ShowtimeService.parseTimeToMinutes("25:00"));
        assertNull(ShowtimeService.parseTimeToMinutes("13:00 PM"));
        assertNull(ShowtimeService.parseTimeToMinutes("06:75 PM"));
    }

    @Test
    void endTimeFallsBackToADefaultDurationForOlderShowtimes() {
        ShowtimeService service = new ShowtimeService(
                mock(ShowtimeRepository.class),
                mock(TheatreRepository.class));

        assertEquals("08:30 PM", service.resolveEndTime("06:30 PM", null));
        assertEquals("10:00 PM", service.resolveEndTime("06:30 PM", "10:00 PM"));
        assertNull(service.resolveEndTime("not a time", "  "));
    }


    // ================= SEAT SAFETY =================

    @Test
    void availableSeatsNeverGoesNegativeOrAboveTotal() {
        assertEquals(0, ShowtimeService.clampAvailableSeats(100, -5));
        assertEquals(100, ShowtimeService.clampAvailableSeats(100, 150));
        assertEquals(60, ShowtimeService.clampAvailableSeats(100, 60));
        assertEquals(0, ShowtimeService.clampAvailableSeats(0, 20));
    }


    // ================= OVERLAP VALIDATION =================

    @Test
    void rejectsShowtimesThatOverlapInTheSameTheatre() {
        ShowtimeService service = serviceWithExisting(showtime(1L, "Prizee", DATE, "06:00 PM", "09:00 PM"));

        assertTrue(service.checkShowtimeConflict(dto("Prizee", DATE, "08:00 PM", "10:00 PM")),
                "partially overlapping window must be rejected");
        assertTrue(service.checkShowtimeConflict(dto("Prizee", DATE, "05:00 PM", "06:30 PM")),
                "window containing the existing showtime must be rejected");
        assertTrue(service.checkShowtimeConflict(dto("Prizee", DATE, "06:00 PM", "09:00 PM")),
                "identical window must be rejected");
    }

    @Test
    void acceptsShowtimesThatDoNotOverlap() {
        ShowtimeService service = serviceWithExisting(showtime(1L, "Prizee", DATE, "06:00 PM", "09:00 PM"));

        assertFalse(service.checkShowtimeConflict(dto("Prizee", DATE, "09:00 PM", "11:00 PM")),
                "back to back showtimes are allowed");
        assertFalse(service.checkShowtimeConflict(dto("Prizee", DATE, "01:00 PM", "02:00 PM")),
                "an earlier window is allowed");
    }

    @Test
    void allowsTheSameWindowInADifferentTheatreOrOnADifferentDate() {
        ShowtimeService service = serviceWithExisting(showtime(1L, "Prizee", DATE, "06:00 PM", "09:00 PM"));

        assertFalse(service.checkShowtimeConflict(dto("Sivas", DATE, "06:00 PM", "09:00 PM")),
                "a different theatre can run the same window");
        assertFalse(service.checkShowtimeConflict(dto("Prizee", "2099-05-21", "06:00 PM", "09:00 PM")),
                "a different date can run the same window");
    }

    @Test
    void theSameConflictCheckAppliesWhenEditing() {
        ShowtimeRepository repository = mock(ShowtimeRepository.class);
        when(repository.findAll()).thenReturn(List.of(
                showtime(1L, "Prizee", DATE, "06:00 PM", "09:00 PM"),
                showtime(2L, "Prizee", DATE, "10:00 PM", "11:30 PM")));

        ShowtimeService service = new ShowtimeService(repository, mock(TheatreRepository.class));

        // Moving showtime 2 onto showtime 1's window must be rejected
        ShowtimeAssignmentDto edit = dto("Prizee", DATE, "07:00 PM", "08:00 PM");
        edit.setId(2L);
        assertTrue(service.checkShowtimeConflict(edit));

        // Moving showtime 2 somewhere free must be accepted
        ShowtimeAssignmentDto free = dto("Prizee", DATE, "01:00 PM", "02:00 PM");
        free.setId(2L);
        assertFalse(service.checkShowtimeConflict(free));

        // Saving a showtime without changing it must not clash with itself
        ShowtimeAssignmentDto same = dto("Prizee", DATE, "06:00 PM", "09:00 PM");
        same.setId(1L);
        assertFalse(service.checkShowtimeConflict(same));
    }

    @Test
    void olderRowsWithoutAnEndTimeOnlyClashOnAnIdenticalStartTime() {
        ShowtimeService service = serviceWithExisting(showtime(1L, "Prizee", DATE, "06:00 PM", null));

        assertTrue(service.checkShowtimeConflict(dto("Prizee", DATE, "06:00 PM", "09:00 PM")));
        assertFalse(service.checkShowtimeConflict(dto("Prizee", DATE, "07:00 PM", "09:00 PM")),
                "an older row without an end time cannot be compared as a window");
    }

    @Test
    void incompleteInputIsAlwaysRejected() {
        ShowtimeService service = serviceWithExisting(List.of());

        assertTrue(service.checkShowtimeConflict(dto(null, DATE, "06:00 PM", "09:00 PM")));
        assertTrue(service.checkShowtimeConflict(dto("Prizee", null, "06:00 PM", "09:00 PM")));
        assertTrue(service.checkShowtimeConflict(dto("Prizee", DATE, "  ", "09:00 PM")));
        assertTrue(service.checkShowtimeConflict(dto("Prizee", DATE, "nonsense", "09:00 PM")));
    }


    // ================= SEAT CAPACITY ON CREATE =================

    @Test
    void creatingAShowtimeTakesTotalAndAvailableSeatsFromTheatreCapacity() {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        TheatreRepository theatreRepository = mock(TheatreRepository.class);

        Theatre theatre = new Theatre();
        theatre.setId(1L);
        theatre.setName("Prizee");
        theatre.setTotalSeats(250);

        when(theatreRepository.findByName("Prizee")).thenReturn(theatre);
        when(showtimeRepository.save(any(Showtime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShowtimeService service = new ShowtimeService(showtimeRepository, theatreRepository);

        Showtime created = service.assignShowtime(dto("Prizee", DATE, "06:00 PM", "09:00 PM"));

        assertEquals(250, created.getTotalSeats());
        assertEquals(250, created.getAvailableSeats());
        assertEquals("09:00 PM", created.getEndTime());
    }

    @Test
    void aShowtimeCannotBeCreatedForAnUnknownTheatre() {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        TheatreRepository theatreRepository = mock(TheatreRepository.class);

        when(theatreRepository.findByName("Ghost")).thenReturn(null);

        ShowtimeService service = new ShowtimeService(showtimeRepository, theatreRepository);

        assertNull(service.assignShowtime(dto("Ghost", DATE, "06:00 PM", "09:00 PM")));
    }

    @Test
    void editingAShowtimeKeepsAlreadySoldSeatsWhenCapacityChanges() {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        TheatreRepository theatreRepository = mock(TheatreRepository.class);

        Showtime existing = showtime(1L, "Prizee", DATE, "06:00 PM", "09:00 PM");
        existing.setTotalSeats(100);
        existing.setAvailableSeats(90);

        Theatre bigger = new Theatre();
        bigger.setName("Prizee");
        bigger.setTotalSeats(200);

        when(showtimeRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(theatreRepository.findByName("Prizee")).thenReturn(bigger);
        when(showtimeRepository.save(any(Showtime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShowtimeService service = new ShowtimeService(showtimeRepository, theatreRepository);

        ShowtimeAssignmentDto edit = dto("Prizee", DATE, "06:00 PM", "09:00 PM");
        edit.setId(1L);

        assertTrue(service.updateShowtimeFromDto(edit));
        assertEquals(200, existing.getTotalSeats());
        assertEquals(190, existing.getAvailableSeats());
    }

    @Test
    void availableSeatsStayWithinRangeWhenCapacityShrinksBelowBookedSeats() {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        TheatreRepository theatreRepository = mock(TheatreRepository.class);

        Showtime existing = showtime(1L, "Prizee", DATE, "06:00 PM", "09:00 PM");
        existing.setTotalSeats(100);
        existing.setAvailableSeats(20);

        Theatre smaller = new Theatre();
        smaller.setName("Prizee");
        smaller.setTotalSeats(50);

        when(showtimeRepository.findById(1L)).thenReturn(java.util.Optional.of(existing));
        when(theatreRepository.findByName("Prizee")).thenReturn(smaller);
        when(showtimeRepository.save(any(Showtime.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ShowtimeService service = new ShowtimeService(showtimeRepository, theatreRepository);

        ShowtimeAssignmentDto edit = dto("Prizee", DATE, "06:00 PM", "09:00 PM");
        edit.setId(1L);

        service.updateShowtimeFromDto(edit);

        assertEquals(50, existing.getTotalSeats());
        assertEquals(0, existing.getAvailableSeats());
        assertTrue(existing.getAvailableSeats() >= 0);
        assertTrue(existing.getAvailableSeats() <= existing.getTotalSeats());
    }


    // ================= EXISTING PAST SHOWTIME RULE =================

    @Test
    void pastShowtimesStayOutOfTheFutureList() {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        TheatreRepository theatreRepository = mock(TheatreRepository.class);

        Showtime past = showtime(1L, "Prizee", "2020-01-01", "06:00 PM", "09:00 PM");
        Showtime future = showtime(2L, "Prizee", "2099-01-01", "06:00 PM", "09:00 PM");

        when(showtimeRepository.findAll()).thenReturn(List.of(past, future));

        ShowtimeService service = new ShowtimeService(showtimeRepository, theatreRepository);

        assertEquals(List.of(future), service.getFutureShowtimes());
    }


    // ================= THEATRE STATUS =================

    @Test
    void aTheatreSavedBeforeTheStatusColumnExistedReadsAsAvailable() {
        Theatre legacy = new Theatre();
        legacy.setName("Prizee");

        // Nothing was ever written to the new column for these rows
        assertEquals(Theatre.STATUS_AVAILABLE, legacy.getStatus());
        assertFalse(legacy.isUnderMaintenance());
    }

    @Test
    void aBlankStatusAlsoReadsAsAvailable() {
        Theatre theatre = new Theatre();
        theatre.setStatus("   ");

        assertEquals(Theatre.STATUS_AVAILABLE, theatre.getStatus());
        assertFalse(theatre.isUnderMaintenance());
    }

    @Test
    void aTheatreUnderMaintenanceIsRecognised() {
        Theatre theatre = new Theatre();
        theatre.setStatus(Theatre.STATUS_MAINTENANCE);

        assertEquals(Theatre.STATUS_MAINTENANCE, theatre.getStatus());
        assertTrue(theatre.isUnderMaintenance());
    }

    @Test
    void statusIsStoredRegardlessOfCasingOrPadding() {
        Theatre theatre = new Theatre();
        theatre.setStatus("  maintenance ");

        assertEquals(Theatre.STATUS_MAINTENANCE, theatre.getStatus());
        assertTrue(theatre.isUnderMaintenance());
    }

    @Test
    void theServiceReportsWhetherATheatreIsUnderMaintenance() {
        TheatreRepository repository = mock(TheatreRepository.class);

        Theatre available = new Theatre();
        available.setId(1L);
        available.setName("Prizee");
        available.setStatus(Theatre.STATUS_AVAILABLE);

        Theatre closed = new Theatre();
        closed.setId(2L);
        closed.setName("Sivas");
        closed.setStatus(Theatre.STATUS_MAINTENANCE);

        when(repository.findAll()).thenReturn(List.of(available, closed));

        TheatreService service = new TheatreService(repository);

        assertFalse(service.isUnderMaintenance("Prizee"));
        assertTrue(service.isUnderMaintenance("Sivas"));
        assertFalse(service.isUnderMaintenance("Unknown Theatre"));
        assertFalse(service.isUnderMaintenance(null));
        assertFalse(service.isUnderMaintenance("  "));
    }

    @Test
    void updatingATheatreKeepsTheSubmittedStatus() {
        TheatreRepository repository = mock(TheatreRepository.class);

        Theatre stored = new Theatre();
        stored.setId(1L);
        stored.setName("Prizee");
        stored.setLocation("Colombo");
        stored.setTotalSeats(200);
        stored.setStatus(Theatre.STATUS_AVAILABLE);

        when(repository.findById(1L)).thenReturn(java.util.Optional.of(stored));

        TheatreService service = new TheatreService(repository);

        Theatre submitted = new Theatre();
        submitted.setId(1L);
        submitted.setName("Prizee");
        submitted.setLocation("Colombo");
        submitted.setTotalSeats(200);
        submitted.setStatus(Theatre.STATUS_MAINTENANCE);

        assertTrue(service.updateTheatre(submitted));
        assertEquals(Theatre.STATUS_MAINTENANCE, stored.getStatus());
        assertTrue(stored.isUnderMaintenance());

        // Putting it back to AVAILABLE is a normal edit too
        submitted.setStatus(Theatre.STATUS_AVAILABLE);
        assertTrue(service.updateTheatre(submitted));
        assertEquals(Theatre.STATUS_AVAILABLE, stored.getStatus());
        assertFalse(stored.isUnderMaintenance());
    }


    // ================= HELPERS =================

    private ShowtimeService serviceWithExisting(Showtime... showtimes) {
        return serviceWithExisting(List.of(showtimes));
    }

    private ShowtimeService serviceWithExisting(List<Showtime> showtimes) {
        ShowtimeRepository repository = mock(ShowtimeRepository.class);
        when(repository.findAll()).thenReturn(showtimes);
        return new ShowtimeService(repository, mock(TheatreRepository.class));
    }

    private Showtime showtime(Long id, String theatre, String date, String start, String end) {
        Showtime showtime = new Showtime();
        showtime.setId(id);
        showtime.setMovieId(1L);
        showtime.setMovieTitle("The Last Journey");
        showtime.setTheatre(theatre);
        showtime.setDate(date);
        showtime.setShowTime(start);
        showtime.setEndTime(end);
        showtime.setTotalSeats(100);
        showtime.setAvailableSeats(100);
        showtime.setTicketPrice(1000);
        return showtime;
    }

    private ShowtimeAssignmentDto dto(String theatre, String date, String start, String end) {
        ShowtimeAssignmentDto dto = new ShowtimeAssignmentDto();
        dto.setMovieId(1L);
        dto.setMovieTitle("The Last Journey");
        dto.setTheatre(theatre);
        dto.setDate(date);
        dto.setShowTime(start);
        dto.setEndTime(end);
        dto.setTicketPrice(1000);
        return dto;
    }
}
