package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.SupportTicketActivity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketActivityRepository extends JpaRepository<SupportTicketActivity, Long> {

    List<SupportTicketActivity> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    void deleteByTicketId(Long ticketId);
}
