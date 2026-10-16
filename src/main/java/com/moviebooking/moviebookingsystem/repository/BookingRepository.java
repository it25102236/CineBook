package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    List<Booking> findByCustomerEmailIgnoreCaseOrderByIdDesc(String customerEmail);

    List<Booking> findByMovieIdAndTheatreAndDateAndShowTime(
            Long movieId,
            String theatre,
            String date,
            String showTime);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from Booking b where b.id = :id")
    Optional<Booking> findByIdForUpdate(@Param("id") Long id);

    @Query("update Booking b set b.customerEmail = :newEmail where lower(b.customerEmail) = lower(:oldEmail)")
    @org.springframework.data.jpa.repository.Modifying
    int updateCustomerEmail(
            @Param("oldEmail") String oldEmail,
            @Param("newEmail") String newEmail);
}
