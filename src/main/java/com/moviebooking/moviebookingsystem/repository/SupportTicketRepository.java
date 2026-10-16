package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.SupportTicket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findByEmailIgnoreCaseOrderByCreatedAtDesc(String email);

    List<SupportTicket> findByEmailIgnoreCaseAndSubjectIgnoreCaseAndCategoryIgnoreCaseAndMessageIgnoreCaseAndCreatedAtGreaterThanEqual(
            String email,
            String subject,
            String category,
            String message,
            long createdAtSince);
}
