package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.model.Movie;
import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.service.BookingService;
import com.moviebooking.moviebookingsystem.service.MovieService;
import com.moviebooking.moviebookingsystem.service.RefundService;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BookingPageCascadeTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private BookingService bookingService;

    @MockitoBean
    private MovieService movieService;

    @MockitoBean
    private ShowtimeService showtimeService;

    @MockitoBean
    private RefundService refundService;

    private static final Path RENDERED_PAGE =
            Path.of("target", "booking-page-rendered.html");

    @Test
    void bookingPageRendersLiveShowtimeCascade() throws Exception {

        when(bookingService.getAllBookings()).thenReturn(List.of());
        when(movieService.getAllMovies()).thenReturn(List.of(
                movie(1L, "The Last Journey"),
                movie(2L, "Interstellar")));
        when(showtimeService.getFutureShowtimes()).thenReturn(List.of(
                showtime(1L, "The Last Journey", "CineBook Main Hall", "2099-01-10", "06:30 PM", 300, 298, 1000),
                showtime(1L, "The Last Journey", "CineBook Main Hall", "2099-01-11", "09:45 PM", 300, 300, 1200),
                showtime(2L, "Interstellar", "Prizee", "2099-02-05", "01:15 PM", 500, 500, 1500),
                showtime(2L, "Interstellar", "Prizee", "2099-01-12", "04:00 PM", 500, 499, 1500)));

        String html = mockMvc.perform(get("/booking").session(customerSession()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        Files.createDirectories(RENDERED_PAGE.getParent());
        Files.writeString(RENDERED_PAGE, html, StandardCharsets.UTF_8);

        // ================= DYNAMIC DATA IS PRESENT =================

        assertTrue(html.contains("\"theatre\":\"CineBook Main Hall\""),
                "Theatre must come from saved showtimes");
        assertTrue(html.contains("\"theatre\":\"Prizee\""),
                "Theatre must come from saved showtimes");
        assertTrue(html.contains("\"date\":\"2099-01-10\""));
        assertTrue(html.contains("\"showTime\":\"06:30 PM\""));
        assertTrue(html.contains("\"ticketPrice\":1200.0"),
                "Ticket price must come from the saved showtime");

        // ================= NO HARD-CODED OPTIONS =================

        assertFalse(html.contains("<option th:each="),
                "Theatre/Date/Showtime options must not be hard-coded in the template");
        assertFalse(html.contains("data-movie-id="),
                "Legacy per-movie option attributes must be gone");

// ================= SEAT / BOOKING MARKUP PRESERVED =================

        assertTrue(html.contains("id=\"seatNumber\""));
        assertTrue(html.contains("id=\"seatLayout\""),
                "Seats must be rendered into the seat layout container");
        assertTrue(html.contains("function buildSeatGrid"),
                "The seat grid must be built from the selected showtime capacity");
        assertTrue(html.contains("Confirm Booking"));
    }

    @Test
    void bookingPageFallsBackToAllShowtimesForTheatreFirstFlow() throws Exception {

        when(bookingService.getAllBookings()).thenReturn(List.of());
        when(movieService.getAllMovies()).thenReturn(List.of());
        when(showtimeService.getFutureShowtimes()).thenReturn(List.of());

        String html = mockMvc.perform(get("/booking").session(customerSession()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertTrue(html.contains("const bookingShowtimes ="),
                "Empty showtime data must still produce a usable cascade");
        assertTrue(html.contains("Select Theatre"));
        assertTrue(html.contains("Select Date"));
        assertTrue(html.contains("Select Show Time"));
    }

    private MockHttpSession customerSession() {
        User user = mock(User.class);
        when(user.getRole()).thenReturn("CUSTOMER");
        when(user.getEmail()).thenReturn("cascade-test@example.com");
        when(user.getName()).thenReturn("Cascade Test");

        MockHttpSession session = new MockHttpSession();
        session.setAttribute("loggedInUser", user);
        return session;
    }

    private Movie movie(Long id, String title) {
        Movie movie = new Movie();
        movie.setId(id);
        movie.setTitle(title);
        return movie;
    }

    private long nextShowtimeId = 1L;

    private Showtime showtime(Long movieId, String movieTitle, String theatre, String date,
                              String showTime, int totalSeats, int availableSeats, double ticketPrice) {
        Showtime showtime = new Showtime();
        showtime.setId(nextShowtimeId++);
        showtime.setMovieId(movieId);
        showtime.setMovieTitle(movieTitle);
        showtime.setTheatre(theatre);
        showtime.setDate(date);
        showtime.setShowTime(showTime);
        showtime.setTotalSeats(totalSeats);
        showtime.setAvailableSeats(availableSeats);
        showtime.setTicketPrice(ticketPrice);
        return showtime;
    }
}
