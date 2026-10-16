package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.SupportTicket;
import com.moviebooking.moviebookingsystem.repository.TicketReplyRepository;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SupportAnalyticsService {

    private final CustomerSupportService supportService;
    private final TicketReplyRepository replyRepository;

    public SupportAnalyticsService(
            CustomerSupportService supportService,
            TicketReplyRepository replyRepository) {

        this.supportService = supportService;
        this.replyRepository = replyRepository;
    }

    // Ticket counts per status (OPEN, IN_PROGRESS, RESOLVED)
    public Map<String, Long> ticketsByStatus() {

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("OPEN", 0L);
        counts.put("IN_PROGRESS", 0L);
        counts.put("RESOLVED", 0L);

        for (SupportTicket ticket : supportService.getAllTickets()) {
            String status = ticket.getStatus() == null ? "OPEN" : ticket.getStatus();
            counts.merge(status, 1L, Long::sum);
        }

        return counts;
    }

    // Ticket counts per category
    public Map<String, Long> ticketsByCategory() {

        Map<String, Long> counts = new LinkedHashMap<>();
        for (SupportTicket ticket : supportService.getAllTickets()) {
            String category = ticket.getCategory() == null
                    ? SupportTicket.CATEGORY_OTHER : ticket.getCategory();
            counts.merge(category, 1L, Long::sum);
        }
        return counts;
    }

    // Ticket counts per priority
    public Map<String, Long> ticketsByPriority() {

        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put(SupportTicket.PRIORITY_LOW, 0L);
        counts.put(SupportTicket.PRIORITY_MEDIUM, 0L);
        counts.put(SupportTicket.PRIORITY_HIGH, 0L);
        counts.put(SupportTicket.PRIORITY_URGENT, 0L);

        for (SupportTicket ticket : supportService.getAllTickets()) {
            String priority = ticket.getPriority() == null
                    ? SupportTicket.PRIORITY_MEDIUM : ticket.getPriority();
            counts.merge(priority, 1L, Long::sum);
        }

        return counts;
    }

    // Count of escalated tickets
    public long countEscalated() {

        return supportService.getAllTickets().stream()
                .filter(SupportTicket::isEscalated)
                .count();
    }

    // Count of tickets rated 4 or 5 stars
    public long countSatisfied() {

        return supportService.getAllTickets().stream()
                .filter(ticket -> ticket.getFeedbackRating() != null
                        && ticket.getFeedbackRating() >= 4)
                .count();
    }

    // Count of tickets rated 1 or 2 stars
    public long countDissatisfied() {

        return supportService.getAllTickets().stream()
                .filter(ticket -> ticket.getFeedbackRating() != null
                        && ticket.getFeedbackRating() <= 2)
                .count();
    }

    // Average first-response time in milliseconds (0 when no data)
    public double averageResponseTimeMillis() {

        List<SupportTicket> responded = supportService.getAllTickets().stream()
                .filter(ticket -> ticket.getCreatedAt() > 0L
                        && ticket.getFirstResponseAt() > 0L)
                .toList();

        return averageMillis(responded,
                ticket -> ticket.getFirstResponseAt() - ticket.getCreatedAt());
    }

    // Average resolution time in milliseconds (0 when no data)
    public double averageResolveTimeMillis() {

        List<SupportTicket> resolved = supportService.getAllTickets().stream()
                .filter(ticket -> ticket.getCreatedAt() > 0L
                        && ticket.getResolvedAt() > 0L)
                .toList();

        return averageMillis(resolved,
                ticket -> ticket.getResolvedAt() - ticket.getCreatedAt());
    }

    // Total staff replies across all tickets
    public long totalReplies() {
        return replyRepository.count();
    }

    // Replies submitted in the last N milliseconds
    public long repliesInLast(long millis) {

        long cutoff = System.currentTimeMillis() - millis;

        return replyRepository.findAll().stream()
                .filter(reply -> reply.getCreatedAt() >= cutoff)
                .count();
    }

    private double averageMillis(
            List<SupportTicket> tickets,
            java.util.function.ToLongFunction<SupportTicket> durationOf) {

        if (tickets.isEmpty()) {
            return 0.0;
        }

        return tickets.stream()
                .mapToLong(durationOf)
                .average()
                .orElse(0.0);
    }
}
