package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.SupportTicketNote;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketNoteRepository extends JpaRepository<SupportTicketNote, Long> {

    List<SupportTicketNote> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    void deleteByTicketId(Long ticketId);
}
