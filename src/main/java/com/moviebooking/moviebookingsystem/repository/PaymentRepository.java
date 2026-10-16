package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.Payment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByBookingId(Long bookingId);

    /**
     * Newest payment row for a booking. A booking can have several rows when a
     * payment was attempted more than once, and findByBookingId throws when it
     * matches more than one.
     */
    Optional<Payment> findFirstByBookingIdOrderByIdDesc(Long bookingId);

    /**
     * Newest successful payment row for a booking. Used to decide whether a
     * booking is paid and which row a refund must act on, so a later failed
     * attempt cannot hide the real payment.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payment> findFirstByBookingIdAndStatusOrderByIdDesc(Long bookingId, String status);

    boolean existsByBookingIdAndStatus(Long bookingId, String status);

    List<Payment> findByBookingIdOrderByIdDesc(Long bookingId);
}
