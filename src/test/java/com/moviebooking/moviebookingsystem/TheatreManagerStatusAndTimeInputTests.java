package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.dto.ShowtimeAssignmentDto;
import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.Theatre;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import com.moviebooking.moviebookingsystem.service.TheatreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
class TheatreManagerStatusAndTimeInputTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TheatreService theatreService;

    @MockitoBean
    private ShowtimeService showtimeService;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private MovieService movieService;

    private static final String DATE = "2099-05-20";


    // ================= THEATRE CAPACITY =================

    @Test
    void capacityAcceptsOnlyWholeNumbersBetweenOneAndFiveHundred() throws Exception {

        // A value with letters or a decimal point never reaches the service
        assertRejected("abc", "whole number");
        assertRejected("-20", "whole number");
        assertRejected("1.5", "whole number");
        assertRejected("0", "between 1 and 500");
        assertRejected("501", "between 1 and 500");

        verify(theatreService, never()).addTheatre(any());
    }

    @Test
    void theSameCapacityRangeAppliesWhenEditingATheatre() throws Exception {

        when(theatreService.getAllTheatres()).thenReturn(List.of(theatre(1L, "Prizee")));

        // Out of range on the edit form too
        mockMvc.perform(post("/theatre-manager/theatres/edit")
                        .param("id", "1")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", "501")
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/theatre-form"))
                .andExpect(content().string(containsString("between 1 and 500")));

        mockMvc.perform(post("/theatre-manager/theatres/edit")
                        .param("id", "1")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", "0")
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("between 1 and 500")));

        // Digits only, same as the add form
        mockMvc.perform(post("/theatre-manager/theatres/edit")
                        .param("id", "1")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", "12.5")
                        .param("status", "AVAILABLE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("whole number")));

        verify(theatreService, never()).updateTheatre(any());
    }

    @Test
    void theCapacityBoxIsBlankWhenAddingANewTheatre() throws Exception {

        when(theatreService.getAllTheatres()).thenReturn(List.of(theatre(1L, "Prizee")));

        // Even with an existing theatre loaded, the add form must not carry
        // over a previously saved capacity
        String html = mockMvc.perform(get("/theatre-manager/theatres/add"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        String capacityField = null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(?s)<input[^>]*name=\"totalSeats\"[^>]*>")
                .matcher(html);

        if (matcher.find()) {
            capacityField = matcher.group();
        }

        assertNotNull(capacityField, "the capacity field must be rendered");
        assertTrue(capacityField.contains("type=\"number\""),
                "capacity must be a manual numeric input");

        java.util.regex.Matcher valueMatcher = java.util.regex.Pattern
                .compile("value=\"([^\"]*)\"")
                .matcher(capacityField);

        // No previously saved capacity may be carried into the add form
        assertFalse(valueMatcher.find() && !valueMatcher.group(1).isEmpty(),
                "no previously saved capacity may be preselected");
    }

    @Test
    void theLowestAndHighestAllowedCapacitiesAreSaved() throws Exception {

        mockMvc.perform(post("/theatre-manager/theatres/add")
                        .param("name", "Small Hall")
                        .param("location", "Colombo")
                        .param("totalSeats", "1")
                        .param("status", "AVAILABLE"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/theatre-manager/theatre-details"));

        mockMvc.perform(post("/theatre-manager/theatres/add")
                        .param("name", "Big Hall")
                        .param("location", "Colombo")
                        .param("totalSeats", "500")
                        .param("status", "AVAILABLE"))
                .andExpect(status().is3xxRedirection());

        verify(theatreService, times(2)).addTheatre(any());
    }


    // ================= THEATRE STATUS =================

    @Test
    void aNewTheatreIsAvailableWhenNoStatusIsChosen() throws Exception {

        when(theatreService.getAllTheatres()).thenReturn(List.of());

        mockMvc.perform(post("/theatre-manager/theatres/add")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", "250"))
                .andExpect(status().is3xxRedirection());

        verify(theatreService).addTheatre(theatreWithStatus(Theatre.STATUS_AVAILABLE));
    }

    @Test
    void aTheatreCanBeCreatedDirectlyUnderMaintenance() throws Exception {

        when(theatreService.getAllTheatres()).thenReturn(List.of());

        mockMvc.perform(post("/theatre-manager/theatres/add")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", "250")
                        .param("status", "MAINTENANCE"))
                .andExpect(status().is3xxRedirection());

        verify(theatreService).addTheatre(theatreWithStatus(Theatre.STATUS_MAINTENANCE));
    }

    @Test
    void aStatusOutsideTheAllowedTwoIsRejected() throws Exception {

        mockMvc.perform(post("/theatre-manager/theatres/add")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", "250")
                        .param("status", "CLOSED"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/theatre-details"))
                .andExpect(content().string(
                        containsString("AVAILABLE or MAINTENANCE")));

        verify(theatreService, never()).addTheatre(any());
    }

    @Test
    void aTheatreCanBeMovedIntoAndOutOfMaintenance() throws Exception {

        when(theatreService.getAllTheatres()).thenReturn(List.of(theatre(1L, "Prizee")));
        when(theatreService.updateTheatre(any())).thenReturn(true);
        when(theatreService.findTheatreById(1L)).thenReturn(theatre(1L, "Prizee"));

        mockMvc.perform(post("/theatre-manager/theatres/edit")
                        .param("id", "1")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", "250")
                        .param("status", "MAINTENANCE"))
                .andExpect(status().is3xxRedirection());

        verify(theatreService).updateTheatre(theatreWithStatus(Theatre.STATUS_MAINTENANCE));

        mockMvc.perform(post("/theatre-manager/theatres/edit")
                        .param("id", "1")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", "250")
                        .param("status", "AVAILABLE"))
                .andExpect(status().is3xxRedirection());

        verify(theatreService).updateTheatre(theatreWithStatus(Theatre.STATUS_AVAILABLE));
        verify(theatreService, times(2)).updateTheatre(any());
    }


    // ================= MANUAL TIME INPUT =================

    @Test
    void theManualHourMinuteAndPeriodBecomeTheStoredTime() throws Exception {

        givenAvailableTheatre();

        when(showtimeService.checkShowtimeConflict(any())).thenReturn(false);
        when(showtimeService.assignShowtime(any())).thenReturn(new Showtime());

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "05")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/theatre-manager/showtimes"));

        verify(showtimeService).assignShowtime(dtoWithTimes(
                "06:30 PM", "09:05 PM"));
    }

    @Test
    void midnightAndNoonAreHandledByTheAmPmSelector() throws Exception {

        givenAvailableTheatre();

        when(showtimeService.checkShowtimeConflict(any())).thenReturn(false);
        when(showtimeService.assignShowtime(any())).thenReturn(new Showtime());

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "12")
                        .param("startMinute", "00")
                        .param("startPeriod", "AM")
                        .param("endHour", "01")
                        .param("endMinute", "15")
                        .param("endPeriod", "AM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().is3xxRedirection());

        verify(showtimeService).assignShowtime(dtoWithTimes(
                "12:00 AM", "01:15 AM"));
    }

    @Test
    void anHourOrMinuteOutsideItsRangeIsRejected() throws Exception {

        assertTimeRejected("13", "00", "PM", "06", "00", "PM");
        assertTimeRejected("00", "00", "PM", "06", "00", "PM");
        assertTimeRejected("06", "60", "PM", "09", "00", "PM");
        assertTimeRejected("06", "00", "XM", "09", "00", "PM");
        assertTimeRejected("", "00", "PM", "09", "00", "PM");
        assertTimeRejected("six", "00", "PM", "09", "00", "PM");

        // The end time is checked the same way
        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "00")
                        .param("startPeriod", "PM")
                        .param("endHour", "13")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(
                        containsString("End time must be")));

        verify(showtimeService, never()).assignShowtime(any());
    }

    @Test
    void theStartTimeMustBeEarlierThanTheEndTime() throws Exception {

        givenAvailableTheatre();

        // Same clock time, only the period differs
        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "06")
                        .param("endMinute", "30")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(
                        containsString("Start time must be earlier than end time")));

        // Late start, early end
        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "09")
                        .param("startMinute", "00")
                        .param("startPeriod", "PM")
                        .param("endHour", "06")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Start time must be earlier than end time")));

        verify(showtimeService, never()).assignShowtime(any());
    }

    @Test
    void overlappingShowtimesInTheSameTheatreAreStillRejected() throws Exception {

        givenAvailableTheatre();

        when(showtimeService.checkShowtimeConflict(any())).thenReturn(true);

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(
                        containsString("overlaps an existing showtime")));

        verify(showtimeService, never()).assignShowtime(any());
    }


    // ================= MAINTENANCE RESTRICTION =================

    @Test
    void aTheatreUnderMaintenanceCannotReceiveANewShowtime() throws Exception {

        when(theatreService.getAllTheatres())
                .thenReturn(List.of(theatre(1L, "Prizee", Theatre.STATUS_MAINTENANCE)));
        when(theatreService.isUnderMaintenance("Prizee")).thenReturn(true);
        when(movieService.findMovieById(1L)).thenReturn(movie(1L, "The Last Journey"));

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(
                        containsString("under maintenance")));

        verify(showtimeService, never()).assignShowtime(any());
    }

    @Test
    void anExistingShowtimeCanStillBeEditedWhileItsTheatreIsClosed() throws Exception {

        Showtime stored = new Showtime();
        stored.setId(7L);
        stored.setTheatre("Prizee");
        stored.setShowTime("06:30 PM");
        stored.setEndTime("09:00 PM");

        when(theatreService.getAllTheatres())
                .thenReturn(List.of(theatre(1L, "Prizee", Theatre.STATUS_MAINTENANCE)));
        when(theatreService.isUnderMaintenance("Prizee")).thenReturn(true);
        when(movieService.findMovieById(1L)).thenReturn(movie(1L, "The Last Journey"));
        when(showtimeService.findShowtimeById(7L)).thenReturn(stored);
        when(showtimeService.checkShowtimeConflict(any())).thenReturn(false);
        when(showtimeService.updateShowtimeFromDto(any())).thenReturn(true);

        mockMvc.perform(post("/theatre-manager/showtimes/edit")
                        .param("id", "7")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "07")
                        .param("startMinute", "00")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "30")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/theatre-manager/showtimes"));

        verify(showtimeService).updateShowtimeFromDto(dtoWithTimes(
                "07:00 PM", "09:30 PM"));
    }

    @Test
    void aShowtimeCannotBeMovedIntoATheatreUnderMaintenance() throws Exception {

        Showtime stored = new Showtime();
        stored.setId(7L);
        stored.setTheatre("Prizee");
        stored.setShowTime("06:30 PM");
        stored.setEndTime("09:00 PM");

        when(theatreService.getAllTheatres()).thenReturn(List.of(
                theatre(1L, "Prizee"),
                theatre(2L, "Sivas", Theatre.STATUS_MAINTENANCE)));
        when(theatreService.isUnderMaintenance("Sivas")).thenReturn(true);
        when(movieService.findMovieById(1L)).thenReturn(movie(1L, "The Last Journey"));
        when(showtimeService.findShowtimeById(7L)).thenReturn(stored);

        mockMvc.perform(post("/theatre-manager/showtimes/edit")
                        .param("id", "7")
                        .param("movieId", "1")
                        .param("theatre", "Sivas")
                        .param("date", DATE)
                        .param("startHour", "07")
                        .param("startMinute", "00")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "30")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(
                        containsString("under maintenance")));

        verify(showtimeService, never()).updateShowtimeFromDto(any());
    }


    // ================= HELPERS =================

    private void givenAvailableTheatre() {
        when(theatreService.getAllTheatres()).thenReturn(List.of(theatre(1L, "Prizee")));
        when(theatreService.isUnderMaintenance("Prizee")).thenReturn(false);
        when(movieService.findMovieById(1L)).thenReturn(movie(1L, "The Last Journey"));
        when(movieService.getAllMovies()).thenReturn(List.of(movie(1L, "The Last Journey")));
    }

    private void assertRejected(String totalSeats, String expectedMessage) throws Exception {

        mockMvc.perform(post("/theatre-manager/theatres/add")
                        .param("name", "Prizee")
                        .param("location", "Colombo")
                        .param("totalSeats", totalSeats))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString(expectedMessage)));
    }

    private void assertTimeRejected(
            String startHour,
            String startMinute,
            String startPeriod,
            String endHour,
            String endMinute,
            String endPeriod) throws Exception {

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", startHour)
                        .param("startMinute", startMinute)
                        .param("startPeriod", startPeriod)
                        .param("endHour", endHour)
                        .param("endMinute", endMinute)
                        .param("endPeriod", endPeriod)
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(
                        containsString("Start time must be")));
}


    // ================= SHOWTIME DATE =================

    @Test
    void aDateInThePastIsRejected() throws Exception {

        givenAvailableTheatre();

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", LocalDate.now().minusDays(1).toString())
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(containsString("Invalid date")));

        verify(showtimeService, never()).assignShowtime(any());
    }

    @Test
    void aDateThatIsNotARealDateIsRejected() throws Exception {

        givenAvailableTheatre();

        for (String badDate : List.of("not-a-date", "2026-13-45", "01/05/2099", "2099-02-30")) {

            mockMvc.perform(post("/theatre-manager/showtimes/add")
                            .param("movieId", "1")
                            .param("theatre", "Prizee")
                            .param("date", badDate)
                            .param("startHour", "06")
                            .param("startMinute", "30")
                            .param("startPeriod", "PM")
                            .param("endHour", "09")
                            .param("endMinute", "00")
                            .param("endPeriod", "PM")
                            .param("totalSeats", "250")
                            .param("ticketPrice", "1000"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Invalid date")));
        }

        verify(showtimeService, never()).assignShowtime(any());
    }

    @Test
    void todayAndFutureDatesAreAccepted() throws Exception {

        givenAvailableTheatre();

        when(showtimeService.checkShowtimeConflict(any())).thenReturn(false);
        when(showtimeService.assignShowtime(any())).thenReturn(new Showtime());

        for (String goodDate : List.of(LocalDate.now().toString(),
                LocalDate.now().plusDays(1).toString())) {

            mockMvc.perform(post("/theatre-manager/showtimes/add")
                            .param("movieId", "1")
                            .param("theatre", "Prizee")
                            .param("date", goodDate)
                            .param("startHour", "06")
                            .param("startMinute", "30")
                            .param("startPeriod", "PM")
                            .param("endHour", "09")
                            .param("endMinute", "00")
                            .param("endPeriod", "PM")
                            .param("totalSeats", "250")
                            .param("ticketPrice", "1000"))
                    .andExpect(status().is3xxRedirection());
        }

        verify(showtimeService, times(2)).assignShowtime(any());
    }

    @Test
    void aShowtimeAlreadyInThePastCanStillBeEditedOnItsOwnDate() throws Exception {

        String pastDate = LocalDate.now().minusDays(30).toString();

        Showtime stored = new Showtime();
        stored.setId(7L);
        stored.setTheatre("Prizee");
        stored.setDate(pastDate);
        stored.setShowTime("06:30 PM");
        stored.setEndTime("09:00 PM");

        givenAvailableTheatre();

        when(showtimeService.findShowtimeById(7L)).thenReturn(stored);
        when(showtimeService.checkShowtimeConflict(any())).thenReturn(false);
        when(showtimeService.updateShowtimeFromDto(any())).thenReturn(true);

        // Keeping the stored past date must not block the edit
        mockMvc.perform(post("/theatre-manager/showtimes/edit")
                        .param("id", "7")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", pastDate)
                        .param("startHour", "07")
                        .param("startMinute", "00")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "30")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().is3xxRedirection());

        verify(showtimeService).updateShowtimeFromDto(any());
    }

    @Test
    void aShowtimeCannotBeMovedOntoADifferentPastDate() throws Exception {

        Showtime stored = new Showtime();
        stored.setId(7L);
        stored.setTheatre("Prizee");
        stored.setDate(LocalDate.now().minusDays(30).toString());
        stored.setShowTime("06:30 PM");
        stored.setEndTime("09:00 PM");

        givenAvailableTheatre();

        when(showtimeService.findShowtimeById(7L)).thenReturn(stored);

        mockMvc.perform(post("/theatre-manager/showtimes/edit")
                        .param("id", "7")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", LocalDate.now().minusDays(1).toString())
                        .param("startHour", "07")
                        .param("startMinute", "00")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "30")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1000"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(containsString("Invalid date")));

        verify(showtimeService, never()).updateShowtimeFromDto(any());
    }

    @Test
    void theDatePickerStartsAtTodayOnTheAddForm() throws Exception {

        givenAvailableTheatre();

        String html = mockMvc.perform(get("/theatre-manager/showtimes/add"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("type=\"date\""), "the date field must be a date picker");
        assertTrue(html.contains("min=\"" + LocalDate.now() + "\""),
                "the picker must not offer dates before today");
    }

    @Test
    void theDatePickerDoesNotBlankAnAlreadySavedDate() throws Exception {

        givenAvailableTheatre();

        String savedDate = LocalDate.now().plusDays(5).toString();

        Showtime stored = new Showtime();
        stored.setId(7L);
        stored.setTheatre("Prizee");
        stored.setDate(savedDate);
        stored.setShowTime("06:30 PM");
        stored.setEndTime("09:00 PM");

        when(showtimeService.findShowtimeById(7L)).thenReturn(stored);

        String html = mockMvc.perform(get("/theatre-manager/showtimes/edit").param("id", "7"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("value=\"" + savedDate + "\""),
                "editing must show the date already stored on the showtime");
    }


    // ================= MANUAL TICKET PRICE =================

    @Test
    void aTicketPriceOfZeroIsRejectedBecauseThePriceMustBePositive() throws Exception {

        givenAvailableTheatre();

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "0"))
                .andExpect(status().isOk())
                .andExpect(view().name("theatre/showtime-form"))
                .andExpect(content().string(
                        containsString("Ticket price must be greater than 0.")));

        verify(showtimeService, never()).assignShowtime(any());
    }

    @Test
    void aNegativeTicketPriceIsRejected() throws Exception {

        givenAvailableTheatre();

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "-250"))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        containsString("Ticket price must be greater than 0.")));

        verify(showtimeService, never()).assignShowtime(any());
    }

    @Test
    void aFractionalTicketPriceIsSavedAsTyped() throws Exception {

        givenAvailableTheatre();

        when(showtimeService.checkShowtimeConflict(any())).thenReturn(false);
        when(showtimeService.assignShowtime(any())).thenReturn(new Showtime());

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "1250.50"))
                .andExpect(status().is3xxRedirection());

        verify(showtimeService).assignShowtime(
                argThat(dto -> dto != null
                        && Double.compare(dto.getTicketPrice(), 1250.50) == 0));
    }

    @Test
    void theSmallestAllowedTicketPriceIsAccepted() throws Exception {

        givenAvailableTheatre();

        when(showtimeService.checkShowtimeConflict(any())).thenReturn(false);
        when(showtimeService.assignShowtime(any())).thenReturn(new Showtime());

        mockMvc.perform(post("/theatre-manager/showtimes/add")
                        .param("movieId", "1")
                        .param("theatre", "Prizee")
                        .param("date", DATE)
                        .param("startHour", "06")
                        .param("startMinute", "30")
                        .param("startPeriod", "PM")
                        .param("endHour", "09")
                        .param("endMinute", "00")
                        .param("endPeriod", "PM")
                        .param("totalSeats", "250")
                        .param("ticketPrice", "0.01"))
                .andExpect(status().is3xxRedirection());

        verify(showtimeService).assignShowtime(
                argThat(dto -> dto != null
                        && Double.compare(dto.getTicketPrice(), 0.01) == 0));
    }


    /**
     * @return a Theatre carrying the expected status, for use in verify(...)
     */
    private static Theatre theatreWithStatus(String expected) {
        return argThat(theatre -> theatre != null
                && expected.equals(theatre.getStatus())
                && theatre.getTotalSeats() > 0);
    }


    /**
     * @return a ShowtimeAssignmentDto carrying the expected times, for use in verify(...)
     */
    private static ShowtimeAssignmentDto dtoWithTimes(String expectedStart, String expectedEnd) {
        return argThat(dto -> dto != null
                && expectedStart.equals(dto.getShowTime())
                && expectedEnd.equals(dto.getEndTime()));
    }

    private static Theatre theatre(Long id, String name) {
        return theatre(id, name, Theatre.STATUS_AVAILABLE);
    }

    private static Theatre theatre(Long id, String name, String status) {
        Theatre theatre = new Theatre();
        theatre.setId(id);
        theatre.setName(name);
        theatre.setLocation("Colombo");
        theatre.setTotalSeats(250);
        theatre.setStatus(status);
        return theatre;
    }

    private static Movie movie(Long id, String title) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        return movie;
    }
}