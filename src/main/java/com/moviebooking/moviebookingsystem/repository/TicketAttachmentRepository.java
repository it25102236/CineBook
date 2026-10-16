package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.TicketAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, Long> {

    List<TicketAttachment> findByTicketIdOrderByUploadedAtAsc(Long ticketId);
}
