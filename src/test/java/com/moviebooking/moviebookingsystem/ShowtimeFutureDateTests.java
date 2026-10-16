package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.model.Showtime;
import com.moviebooking.moviebookingsystem.repository.ShowtimeRepository;
import com.moviebooking.moviebookingsystem.repository.TheatreRepository;
import com.moviebooking.moviebookingsystem.service.ShowtimeService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ShowtimeFutureDateTests {

    @Test
    void onlyReturnsShowtimesAfterToday() {
        ShowtimeRepository showtimeRepository = mock(ShowtimeRepository.class);
        TheatreRepository theatreRepository = mock(TheatreRepository.class);
        Showtime today = showtime(LocalDate.now().toString());
        Showtime past = showtime(LocalDate.now().minusDays(1).toString());
        Showtime future = showtime(LocalDate.now().plusDays(1).toString());
        Showtime invalid = showtime("not-a-date");
        when(showtimeRepository.findAll()).thenReturn(List.of(today, past, future, invalid));

        List<Showtime> results = new ShowtimeService(showtimeRepository, theatreRepository).getFutureShowtimes();

        assertEquals(List.of(future), results);
    }

    private Showtime showtime(String date) {
        Showtime showtime = new Showtime();
        showtime.setDate(date);
        return showtime;
    }
}
