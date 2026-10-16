package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.TicketReply;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketReplyRepository extends JpaRepository<TicketReply, Long> {

    List<TicketReply> findByTicketIdOrderByCreatedAtAsc(Long ticketId);

    List<TicketReply> findByTicketIdOrderByCreatedAtDesc(Long ticketId);

    void deleteByTicketId(Long ticketId);

    List<TicketReply> findByTicketIdAndSenderIdOrderByCreatedAtDesc(
            Long ticketId, Long senderId);
}
