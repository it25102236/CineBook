package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.SupportTicket;
import com.moviebooking.moviebookingsystem.model.SupportTicketActivity;
import com.moviebooking.moviebookingsystem.model.SupportTicketNote;
import com.moviebooking.moviebookingsystem.model.TicketReply;
import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.BookingRepository;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.repository.SupportTicketRepository;
import com.moviebooking.moviebookingsystem.repository.SupportTicketActivityRepository;
import com.moviebooking.moviebookingsystem.repository.SupportTicketNoteRepository;
import com.moviebooking.moviebookingsystem.repository.TicketReplyRepository;
import com.moviebooking.moviebookingsystem.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class CustomerSupportService {

    private final SupportTicketRepository ticketRepository;
    private final TicketReplyRepository replyRepository;
    private final SupportTicketActivityRepository activityRepository;
    private final SupportTicketNoteRepository noteRepository;
    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

    private static final int NAME_MIN_LENGTH = 2;
    private static final int NAME_MAX_LENGTH = 80;
    private static final int SUBJECT_MIN_LENGTH = 5;
    private static final int SUBJECT_MAX_LENGTH = 120;
    private static final int MESSAGE_MIN_LENGTH = 10;
    private static final int MESSAGE_MAX_LENGTH = 2000;
    private static final int NOTE_MAX_LENGTH = 2000;

    @Value("${app.support.duplicate-window-minutes:5}")
    private long duplicateWindowMinutes;

    private static final List<String> STATUSES =
            List.of("OPEN", "IN_PROGRESS", "RESOLVED");
    private static final List<String> PRIORITIES =
            List.of(SupportTicket.PRIORITY_LOW, SupportTicket.PRIORITY_MEDIUM,
                    SupportTicket.PRIORITY_HIGH, SupportTicket.PRIORITY_URGENT);
    private static final List<String> CATEGORIES =
            List.of(SupportTicket.CATEGORY_BOOKING, SupportTicket.CATEGORY_PAYMENT,
                    SupportTicket.CATEGORY_REFUND, SupportTicket.CATEGORY_CANCELLATION,
                    SupportTicket.CATEGORY_TECHNICAL, SupportTicket.CATEGORY_OTHER);

    public CustomerSupportService(
            SupportTicketRepository ticketRepository,
            TicketReplyRepository replyRepository,
            SupportTicketActivityRepository activityRepository,
            SupportTicketNoteRepository noteRepository,
            UserRepository userRepository,
            BookingRepository bookingRepository,
            PaymentRepository paymentRepository) {

        this.ticketRepository = ticketRepository;
        this.replyRepository = replyRepository;
        this.activityRepository = activityRepository;
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.paymentRepository = paymentRepository;
    }

    // Get all support tickets
    public List<SupportTicket> getAllTickets() {
        List<SupportTicket> tickets = ticketRepository.findAll();
        boolean updated = false;
        for (SupportTicket ticket : tickets) {
            updated |= ensureSlaDeadlines(ticket);
        }
        if (updated) {
            ticketRepository.saveAll(tickets);
        }
        return tickets;
    }

    // Get tickets for a specific customer (by email, newest first)
    public List<SupportTicket> getTicketsByCustomerEmail(String email) {
        if (email == null || email.isBlank()) {
            return List.of();
        }
        return ticketRepository.findByEmailIgnoreCaseOrderByCreatedAtDesc(email.trim());
    }

    // Get one ticket by ID
    public SupportTicket getTicketById(Long id) {

        if (id == null) {
            return null;
        }

        SupportTicket ticket = ticketRepository.findById(id).orElse(null);
        if (ticket != null && ensureSlaDeadlines(ticket)) {
            return ticketRepository.save(ticket);
        }
        return ticket;
    }

    // Normalize priority, defaulting to MEDIUM
    public String normalizePriority(String priority) {
        if (priority == null || priority.isBlank()) {
            return SupportTicket.PRIORITY_MEDIUM;
        }
        String value = priority.trim().toUpperCase(Locale.ROOT);
        if (!PRIORITIES.contains(value)) {
            throw new IllegalArgumentException("Invalid priority");
        }
        return value;
    }

    // Normalize category, defaulting to OTHER
    public String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return SupportTicket.CATEGORY_OTHER;
        }
        String value = category.trim().toUpperCase(Locale.ROOT);
        if (!CATEGORIES.contains(value)) {
            throw new IllegalArgumentException("Invalid category");
        }
        return value;
    }

    public List<String> getAllowedPriorities() {
        return PRIORITIES;
    }

    public List<String> getAllowedCategories() {
        return CATEGORIES;
    }

    // Validate ticket fields
    private void validateTicketFields(SupportTicket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket cannot be null");
        }
        String customerName = ticket.getCustomerName() == null
                ? "" : ticket.getCustomerName().trim();
        if (customerName.length() < NAME_MIN_LENGTH || customerName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Customer name must be 2 to 80 characters");
        }
        String email = ticket.getEmail() == null ? "" : ticket.getEmail().trim();
        if (email.isEmpty() || email.length() > 254) {
            throw new IllegalArgumentException("A valid email address is required");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }
        String subject = ticket.getSubject() == null ? "" : ticket.getSubject().trim();
        if (subject.length() < SUBJECT_MIN_LENGTH || subject.length() > SUBJECT_MAX_LENGTH) {
            throw new IllegalArgumentException("Subject must be 5 to 120 characters");
        }
        String message = ticket.getMessage() == null ? "" : ticket.getMessage().trim();
        if (message.length() < MESSAGE_MIN_LENGTH) {
            throw new IllegalArgumentException(
                    "Message must be at least " + MESSAGE_MIN_LENGTH + " characters");
        }
        if (message.length() > MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "Message must be at most " + MESSAGE_MAX_LENGTH + " characters");
        }
        ticket.setCustomerName(customerName);
        ticket.setEmail(email);
        ticket.setSubject(subject);
        ticket.setMessage(message);
        ticket.setPriority(normalizePriority(ticket.getPriority()));
        ticket.setCategory(normalizeCategory(ticket.getCategory()));
        validateBookingReference(ticket);
    }

    // Validate status value
    private void validateStatus(String status) {
        if (status == null || !STATUSES.contains(status.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid status. Allowed: OPEN, IN_PROGRESS, RESOLVED");
        }
    }

    // Check if ticket can be updated (not RESOLVED)
    private void validateTicketCanBeUpdated(SupportTicket ticket) {
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found");
        }
        if ("RESOLVED".equals(ticket.getStatus())) {
            throw new IllegalArgumentException("Cannot update a RESOLVED ticket");
        }
    }

    // Detect a recent duplicate open ticket from the same customer.
    private boolean isDuplicate(SupportTicket ticket) {

        long cutoff = System.currentTimeMillis()
                - duplicateWindowMinutes * 60L * 1000L;

        List<SupportTicket> existing = ticketRepository
                .findByEmailIgnoreCaseAndSubjectIgnoreCaseAndCategoryIgnoreCaseAndMessageIgnoreCaseAndCreatedAtGreaterThanEqual(
                        ticket.getEmail().trim(),
                        ticket.getSubject().trim(),
                        ticket.getCategory(),
                        ticket.getMessage().trim(),
                        cutoff);

        return existing.stream()
                .anyMatch(previous -> !"RESOLVED".equals(previous.getStatus()));
    }

    // Create a new ticket
    @Transactional
    public void createTicket(SupportTicket ticket) {

        validateTicketFields(ticket);

        ticket.setId(null);
        if (ticket.getStatus() == null
                || ticket.getStatus().isEmpty()) {

            ticket.setStatus("OPEN");
        } else {
            ticket.setStatus(ticket.getStatus().trim().toUpperCase(Locale.ROOT));
            validateStatus(ticket.getStatus());
        }

        ticket.setAssignedStaffId(null);
        ticket.setAssignedStaffName(null);
        ticket.setResponse("");
        ticket.setEscalated(false);
        ticket.setEscalationReason(null);
        ticket.setEscalatedById(null);
        ticket.setEscalatedBy(null);
        ticket.setEscalatedAt(0L);
        ticket.setFirstResponseAt(0L);
        ticket.setResolvedAt(0L);
        ticket.setFeedbackRating(null);
        ticket.setFeedbackComment(null);
        ticket.setFeedbackAt(0L);
        ticket.setRefundTrackingStatus(
                SupportTicket.CATEGORY_REFUND.equals(ticket.getCategory())
                        ? "REQUESTED" : "NOT_REQUESTED");

        if (isDuplicate(ticket)) {
            throw new IllegalArgumentException(
                    "A ticket with this subject and message was already submitted from this email within the last "
                            + duplicateWindowMinutes + " minutes.");
        }

        long now = System.currentTimeMillis();
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        setSlaDeadlines(ticket, now);

        SupportTicket saved = ticketRepository.save(ticket);
        recordActivity(saved, "TICKET_CREATED", "Ticket created", null, "CUSTOMER");
    }

    // Update ticket (fetch existing, merge, save)
    public boolean updateTicket(SupportTicket updatedTicket) {

        if (updatedTicket == null || updatedTicket.getId() == null) {
            return false;
        }

        Optional<SupportTicket> existingOpt = ticketRepository.findById(updatedTicket.getId());

        if (existingOpt.isEmpty()) {
            return false;
        }

        SupportTicket existing = existingOpt.get();

        validateTicketCanBeUpdated(existing);
        validateTicketFields(updatedTicket);
        String status = updatedTicket.getStatus() == null
                ? null : updatedTicket.getStatus().trim().toUpperCase();
        validateStatus(status);

        existing.setCustomerName(updatedTicket.getCustomerName());
        existing.setEmail(updatedTicket.getEmail());
        existing.setSubject(updatedTicket.getSubject());
        existing.setMessage(updatedTicket.getMessage());
        existing.setStatus(status);
        existing.setResponse(updatedTicket.getResponse());
        existing.setPriority(normalizePriority(updatedTicket.getPriority()));
        existing.setCategory(normalizeCategory(updatedTicket.getCategory()));
        existing.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.save(existing);

        return true;
    }

    // Get tickets filtered by status ("ALL" or blank returns every ticket)
    public List<SupportTicket> getTicketsByStatus(String status) {

        List<SupportTicket> allTickets = getAllTickets();

        if (status == null || status.isBlank()
                || "ALL".equalsIgnoreCase(status.trim())) {
            return allTickets;
        }

        String value = status.trim().toUpperCase();

        return allTickets.stream()
                .filter(ticket -> value.equals(ticket.getStatus()))
                .toList();
    }

    // Get tickets filtered by priority
    public List<SupportTicket> getTicketsByPriority(String priority) {

        if (priority == null || priority.isBlank()) {
            return getAllTickets();
        }

        String value = priority.trim().toUpperCase();

        return getAllTickets().stream()
                .filter(ticket -> value.equals(ticket.getPriority()))
                .toList();
    }

    // Get tickets filtered by category
    public List<SupportTicket> getTicketsByCategory(String category) {

        if (category == null || category.isBlank()) {
            return getAllTickets();
        }

        String value = category.trim().toUpperCase();

        return getAllTickets().stream()
                .filter(ticket -> value.equals(ticket.getCategory()))
                .toList();
    }

    // Search tickets by keyword in subject, message, customer name or email
    public List<SupportTicket> searchTickets(String keyword) {

        if (keyword == null || keyword.isBlank()) {
            return getAllTickets();
        }

        String needle = keyword.trim().toLowerCase();

        return getAllTickets().stream()
                .filter(ticket -> containsText(ticket.getSubject(), needle)
                        || containsText(ticket.getMessage(), needle)
                        || containsText(ticket.getCustomerName(), needle)
                        || containsText(ticket.getEmail(), needle))
                .toList();
    }

    // Get tickets filtered by status and keyword together
    public List<SupportTicket> findTickets(String status, String keyword) {

        List<SupportTicket> result = getTicketsByStatus(status);

        if (keyword == null || keyword.isBlank()) {
            return result;
        }

        String needle = keyword.trim().toLowerCase();

        return result.stream()
                .filter(ticket -> containsText(ticket.getSubject(), needle)
                        || containsText(ticket.getMessage(), needle)
                        || containsText(ticket.getCustomerName(), needle)
                        || containsText(ticket.getEmail(), needle))
                .toList();
    }

    private static boolean containsText(String value, String needle) {
        return value != null && value.toLowerCase().contains(needle);
    }

    // Update the editable details of an open or in-progress ticket
    @Transactional
    public boolean updateTicketDetails(
            Long id,
            String customerName,
            String subject,
            String message) {

        SupportTicket ticket = getTicketById(id);

        if (ticket == null) {
            return false;
        }

        validateTicketCanBeUpdated(ticket);

        String normalizedName = customerName == null ? "" : customerName.trim();
        if (normalizedName.length() < NAME_MIN_LENGTH || normalizedName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Customer name must be 2 to 80 characters");
        }
        String normalizedSubject = subject == null ? "" : subject.trim();
        if (normalizedSubject.length() < SUBJECT_MIN_LENGTH
                || normalizedSubject.length() > SUBJECT_MAX_LENGTH) {
            throw new IllegalArgumentException("Subject must be 5 to 120 characters");
        }
        validateMessage(message, "Message");

        ticket.setCustomerName(normalizedName);
        ticket.setSubject(normalizedSubject);
        ticket.setMessage(message.trim());
        ticket.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.save(ticket);
        recordActivity(ticket, "TICKET_EDITED", "Ticket details updated", null, "SUPPORT");

        return true;
    }

    // Resolve several tickets at once and return the ones that were updated
    @Transactional
    public List<SupportTicket> resolveTickets(List<Long> ids, String defaultResponse) {

        List<SupportTicket> resolved = new ArrayList<>();

        if (ids == null) {
            return resolved;
        }

        for (Long id : ids) {

            SupportTicket ticket = getTicketById(id);

            if (ticket == null || "RESOLVED".equals(ticket.getStatus())) {
                continue;
            }

            ticket.setResponse(defaultResponse);
            ticket.setStatus("RESOLVED");
            if (ticket.getFirstResponseAt() == 0L) {
                ticket.setFirstResponseAt(System.currentTimeMillis());
            }
            ticket.setResolvedAt(System.currentTimeMillis());
            ticket.setUpdatedAt(System.currentTimeMillis());

            ticketRepository.save(ticket);
            recordActivity(ticket, "TICKET_RESOLVED", "Ticket resolved", null, "SUPPORT");

            resolved.add(ticket);
        }

        return resolved;
    }

    // Delete ticket (cascades to replies)
    @Transactional
    public boolean deleteTicket(Long id) {

        if (id == null || !ticketRepository.existsById(id)) {
            return false;
        }

        replyRepository.deleteByTicketId(id);
        activityRepository.deleteByTicketId(id);
        noteRepository.deleteByTicketId(id);
        ticketRepository.deleteById(id);

        return true;
    }

    // Respond to ticket
    public boolean respondToTicket(Long id, String response) {

        return respondToTicket(id, response, "RESOLVED");
    }

    @Transactional
    public boolean respondToTicket(Long id, String response, String status) {

        SupportTicket ticket = getTicketById(id);

        if (ticket == null) {
            return false;
        }

        validateTicketCanBeUpdated(ticket);

        validateMessage(response, "Response");

        String targetStatus = status == null ? null : status.trim().toUpperCase();
        validateStatus(targetStatus);

        String previousStatus = ticket.getStatus();
        ticket.setResponse(response.trim());
        ticket.setStatus(targetStatus);
        if (ticket.getFirstResponseAt() == 0L) {
            ticket.setFirstResponseAt(System.currentTimeMillis());
        }
        if ("RESOLVED".equals(targetStatus)) {
            ticket.setResolvedAt(System.currentTimeMillis());
        }
        ticket.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.save(ticket);
        recordActivity(ticket, "STATUS_CHANGED",
                "Status changed from " + previousStatus + " to " + targetStatus,
                null, "SUPPORT");
        return true;
    }

    // Reopen a resolved ticket back to in-progress
    @Transactional
    public boolean reopenTicket(Long id) {

        SupportTicket ticket = getTicketById(id);

        if (ticket == null) {
            return false;
        }

        if (!"RESOLVED".equals(ticket.getStatus())) {
            throw new IllegalArgumentException("Only RESOLVED tickets can be reopened");
        }

        ticket.setStatus("IN_PROGRESS");
        ticket.setResolvedAt(0L);
        ticket.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.save(ticket);
        recordActivity(ticket, "TICKET_REOPENED", "Ticket reopened", null, "SUPPORT");
        return true;
    }

    // Escalate a ticket: raise priority, flag it, and move it to in-progress
    @Transactional
    public boolean escalateTicket(
            Long id,
            String reason,
            Long escalatedById,
            String escalatedByName) {

        SupportTicket ticket = getTicketById(id);

        if (ticket == null) {
            return false;
        }

        validateTicketCanBeUpdated(ticket);

        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.length() < 5 || normalizedReason.length() > 500) {
            throw new IllegalArgumentException("Escalation reason must be 5 to 500 characters");
        }

        String current = normalizePriority(ticket.getPriority());
        String escalated = switch (current) {
            case SupportTicket.PRIORITY_LOW -> SupportTicket.PRIORITY_MEDIUM;
            case SupportTicket.PRIORITY_MEDIUM -> SupportTicket.PRIORITY_HIGH;
            default -> SupportTicket.PRIORITY_URGENT;
        };

        ticket.setPriority(escalated);
        setSlaDeadlines(ticket, ticket.getCreatedAt());
        ticket.setEscalated(true);
        ticket.setEscalationReason(normalizedReason);
        ticket.setEscalatedById(escalatedById);
        ticket.setEscalatedBy(escalatedByName);
        ticket.setEscalatedAt(System.currentTimeMillis());
        if ("OPEN".equals(ticket.getStatus())) {
            ticket.setStatus("IN_PROGRESS");
        }
        ticket.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.save(ticket);
        recordActivity(ticket, "TICKET_ESCALATED", "Ticket escalated to " + escalated,
                escalatedById, "SUPPORT");
        return true;
    }

    // Adjust the priority of an open or in-progress ticket
    @Transactional
    public boolean adjustPriority(Long id, String priority) {

        SupportTicket ticket = getTicketById(id);

        if (ticket == null) {
            return false;
        }

        validateTicketCanBeUpdated(ticket);

        String normalizedPriority = normalizePriority(priority);
        String previousPriority = ticket.getPriority();
        ticket.setPriority(normalizedPriority);
        setSlaDeadlines(ticket, ticket.getCreatedAt());
        ticket.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.save(ticket);
        if (!normalizedPriority.equals(previousPriority)) {
            recordActivity(ticket, "PRIORITY_CHANGED",
                    "Priority changed from " + previousPriority + " to " + normalizedPriority,
                    null, "SUPPORT");
        }
        return true;
    }

    // Add a reply to a ticket's conversation history
    @Transactional
    public TicketReply addReply(
            Long ticketId,
            String message,
            Long senderId,
            String senderName,
            String senderRole) {

        SupportTicket ticket = getTicketById(ticketId);

        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found");
        }

        validateTicketCanBeUpdated(ticket);

        validateMessage(message, "Response");
        if (senderRole == null
                || !List.of("CUSTOMER", "SUPPORT", "ADMIN")
                .contains(senderRole.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Invalid reply sender role");
        }
        String normalizedSenderName = senderName == null ? "" : senderName.trim();
        if (normalizedSenderName.isEmpty() || normalizedSenderName.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Reply sender name must be 1 to 80 characters");
        }

        TicketReply reply = new TicketReply();
        reply.setTicket(ticket);
        reply.setSenderId(senderId);
        reply.setSenderName(normalizedSenderName);
        reply.setSenderRole(senderRole.trim().toUpperCase(Locale.ROOT));
        reply.setMessage(message.trim());
        reply.setCreatedAt(System.currentTimeMillis());

        replyRepository.save(reply);

        ticket.setResponse(message.trim());
        if (reply.isFromStaff() && ticket.getFirstResponseAt() == 0L) {
            ticket.setFirstResponseAt(System.currentTimeMillis());
        }
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticketRepository.save(ticket);
        recordActivity(ticket, reply.isFromStaff() ? "STAFF_REPLY" : "CUSTOMER_REPLY",
                reply.isFromStaff() ? "Support staff replied" : "Customer replied",
                senderId, reply.getSenderRole());

        return reply;
    }

    // Reply history for a ticket, newest first
    public List<TicketReply> getRepliesForTicket(Long ticketId) {

        if (ticketId == null) {
            return List.of();
        }

        List<TicketReply> replies =
                replyRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);

        List<TicketReply> newestFirst = new ArrayList<>(replies);
        java.util.Collections.reverse(newestFirst);
        return newestFirst;
    }

    // Resolve a single ticket with a response
    @Transactional
    public boolean resolveTicket(Long id, String response) {

        SupportTicket ticket = getTicketById(id);

        if (ticket == null) {
            return false;
        }

        validateTicketCanBeUpdated(ticket);

        validateMessage(response, "Response");

        ticket.setResponse(response.trim());
        ticket.setStatus("RESOLVED");
        if (ticket.getFirstResponseAt() == 0L) {
            ticket.setFirstResponseAt(System.currentTimeMillis());
        }
        ticket.setResolvedAt(System.currentTimeMillis());
        ticket.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.save(ticket);
        recordActivity(ticket, "TICKET_RESOLVED", "Ticket resolved", null, "SUPPORT");
        return true;
    }

    // Rate a resolved ticket (1-5)
    @Transactional
    public boolean rateTicket(Long id, Integer rating, String comment) {

        SupportTicket ticket = getTicketById(id);

        if (ticket == null) {
            return false;
        }

        if (!"RESOLVED".equals(ticket.getStatus())) {
            throw new IllegalArgumentException("Only RESOLVED tickets can be rated");
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5");
        }

        ticket.setFeedbackRating(rating);
        String feedback = comment == null ? "" : comment.trim();
        if (feedback.length() > 500) {
            throw new IllegalArgumentException("Feedback must be at most 500 characters");
        }
        ticket.setFeedbackComment(feedback);
        ticket.setFeedbackAt(System.currentTimeMillis());
        ticket.setUpdatedAt(System.currentTimeMillis());

        ticketRepository.save(ticket);
        recordActivity(ticket, "CUSTOMER_FEEDBACK", "Customer submitted feedback", null, "CUSTOMER");
        return true;
    }

    public List<User> getActiveSupportStaff() {
        return userRepository.findAll().stream()
                .filter(user -> "SUPPORT".equalsIgnoreCase(user.getRole()))
                .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                .toList();
    }

    public List<SupportTicketActivity> getActivitiesForTicket(Long ticketId) {
        if (ticketId == null || getTicketById(ticketId) == null) {
            return List.of();
        }
        return activityRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    public List<SupportTicketNote> getInternalNotesForTicket(Long ticketId) {
        if (ticketId == null || getTicketById(ticketId) == null) {
            return List.of();
        }
        return noteRepository.findByTicketIdOrderByCreatedAtAsc(ticketId);
    }

    @Transactional
    public boolean assignTicket(Long ticketId, Long staffId, Long actorId) {
        SupportTicket ticket = getTicketById(ticketId);
        if (ticket == null) {
            return false;
        }
        validateTicketCanBeUpdated(ticket);

        User staff = null;
        if (staffId != null) {
            if (staffId <= 0L) {
                throw new IllegalArgumentException("Select a valid support staff member");
            }
            staff = userRepository.findById(staffId)
                    .filter(user -> "SUPPORT".equalsIgnoreCase(user.getRole()))
                    .filter(user -> "ACTIVE".equalsIgnoreCase(user.getStatus()))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Selected user is not active support staff"));
        }

        Long previousId = ticket.getAssignedStaffId();
        ticket.setAssignedStaffId(staff == null ? null : staff.getId());
        ticket.setAssignedStaffName(staff == null ? null : staff.getName());
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticketRepository.save(ticket);

        String description = staff == null
                ? "Ticket unassigned"
                : "Ticket assigned to " + staff.getName();
        if (!java.util.Objects.equals(previousId, ticket.getAssignedStaffId())) {
            recordActivity(ticket, "TICKET_ASSIGNED", description, actorId, "SUPPORT");
        }
        return true;
    }

    @Transactional
    public SupportTicketNote addInternalNote(
            Long ticketId, String text, Long authorId, String authorName, String authorRole) {
        SupportTicket ticket = getTicketById(ticketId);
        if (ticket == null) {
            throw new IllegalArgumentException("Ticket not found");
        }
        if (authorRole == null
                || !List.of("SUPPORT", "ADMIN")
                .contains(authorRole.trim().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("Only support staff may add internal notes");
        }
        String content = text == null ? "" : text.trim();
        if (content.isEmpty() || content.length() > NOTE_MAX_LENGTH) {
            throw new IllegalArgumentException("Internal note must be 1 to 2000 characters");
        }
        String name = authorName == null ? "" : authorName.trim();
        if (name.isEmpty() || name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Author name must be 1 to 80 characters");
        }

        SupportTicketNote note = new SupportTicketNote();
        note.setTicket(ticket);
        note.setAuthorId(authorId);
        note.setAuthorName(name);
        note.setAuthorRole(authorRole.trim().toUpperCase(Locale.ROOT));
        note.setNote(content);
        note.setCreatedAt(System.currentTimeMillis());
        SupportTicketNote saved = noteRepository.save(note);
        recordActivity(ticket, "INTERNAL_NOTE_ADDED", "Internal staff note added",
                authorId, authorRole);
        return saved;
    }

    @Transactional
    public boolean updateRefundTracking(
            Long ticketId, String status, Long actorId, String actorName) {
        SupportTicket ticket = getTicketById(ticketId);
        if (ticket == null) {
            return false;
        }
        if (ticket.getBookingId() == null) {
            throw new IllegalArgumentException(
                    "A verified booking ID is required to track a refund");
        }

        String target = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        List<String> allowed = List.of(
                "REQUESTED", "UNDER_REVIEW", "APPROVED", "REFERRED_TO_CASHIER",
                "REJECTED", "REFUNDED", "REFUND_SIMULATED");
        if (!allowed.contains(target)) {
            throw new IllegalArgumentException("Invalid refund tracking status");
        }

        Booking booking = getVerifiedBooking(ticket);
        if (!isValidRefundTransition(ticket.getRefundTrackingStatus(), target)) {
            throw new IllegalArgumentException(
                    "Refund status cannot move from " + ticket.getRefundTrackingStatus()
                            + " to " + target);
        }
        if (target.equals("REFUNDED")
                && (!"REFUNDED".equals(booking.getStatus())
                || !paymentRepository.existsByBookingIdAndStatus(ticket.getBookingId(), "REFUNDED"))) {
            throw new IllegalArgumentException(
                    "Mark a refund as refunded only after the existing payment record confirms it");
        }
        if (target.equals("REFUND_SIMULATED")
                && !paymentRepository.existsByBookingIdAndStatus(
                        ticket.getBookingId(), "REFUND_SIMULATED")) {
            throw new IllegalArgumentException(
                    "No simulated refund is recorded for the linked booking");
        }

        String previous = ticket.getRefundTrackingStatus();
        ticket.setRefundTrackingStatus(target);
        ticket.setUpdatedAt(System.currentTimeMillis());
        ticketRepository.save(ticket);
        if (!target.equals(previous)) {
            recordActivity(ticket, "REFUND_TRACKING_UPDATED",
                    "Refund tracking updated to " + target, actorId, "SUPPORT");
        }
        return true;
    }

    public Booking getVerifiedBookingForTicket(Long ticketId) {
        SupportTicket ticket = getTicketById(ticketId);
        return ticket == null || ticket.getBookingId() == null
                ? null : getVerifiedBooking(ticket);
    }

    public Payment getLatestPaymentForTicket(Long ticketId) {
        SupportTicket ticket = getTicketById(ticketId);
        if (ticket == null || ticket.getBookingId() == null) {
            return null;
        }
        return paymentRepository.findFirstByBookingIdOrderByIdDesc(ticket.getBookingId())
                .orElse(null);
    }

    public Map<String, String> getFaqs() {
        Map<String, String> faqs = new LinkedHashMap<>();
        faqs.put("Where can I find my booking ID?",
                "Open My Bookings and select the booking. The booking ID appears with its details.");
        faqs.put("How is a refund handled?",
                "Support can track the request; actual refund processing is handled by the Cashier.");
        faqs.put("Can I change seats after booking?",
                "Seat changes depend on availability and booking status. Contact support with your booking ID.");
        faqs.put("How do I check ticket updates?",
                "Open My Support Tickets while signed in to see replies and status changes.");
        return faqs;
    }

    public Map<String, String> getQuickReplies() {
        Map<String, String> replies = new LinkedHashMap<>();
        replies.put("booking-id", "Please share your booking ID so we can verify the booking details.");
        replies.put("refund-review", "Your refund request is under review. We will update you when its status changes.");
        replies.put("more-details", "Could you please provide a little more detail so we can investigate this issue?");
        replies.put("resolved", "We have reviewed your request and resolved the issue. Please contact us if you need further help.");
        return replies;
    }

    public List<String> getAllowedRefundTrackingTransitions(String currentStatus) {
        if (currentStatus == null || currentStatus.isBlank()
                || "NOT_REQUESTED".equals(currentStatus)) {
            return List.of("REQUESTED", "UNDER_REVIEW");
        }
        return switch (currentStatus) {
            case "REQUESTED" -> List.of("UNDER_REVIEW", "REJECTED");
            case "UNDER_REVIEW" -> List.of("APPROVED", "REJECTED");
            case "APPROVED" -> List.of("REFERRED_TO_CASHIER", "REJECTED");
            case "REFERRED_TO_CASHIER" -> List.of("REFUNDED", "REFUND_SIMULATED");
            default -> List.of();
        };
    }

    private void validateBookingReference(SupportTicket ticket) {
        Long bookingId = ticket.getBookingId();
        if (bookingId == null) {
            return;
        }
        if (bookingId <= 0L) {
            throw new IllegalArgumentException("Booking ID must be a positive number");
        }
        Booking booking = bookingRepository.findById(bookingId).orElse(null);
        if (booking == null
                || booking.getCustomerEmail() == null
                || !booking.getCustomerEmail().equalsIgnoreCase(ticket.getEmail())) {
            throw new IllegalArgumentException(
                    "Booking ID could not be verified for the supplied customer email");
        }
    }

    private Booking getVerifiedBooking(SupportTicket ticket) {
        return bookingRepository.findById(ticket.getBookingId())
                .filter(booking -> booking.getCustomerEmail() != null
                        && booking.getCustomerEmail().equalsIgnoreCase(ticket.getEmail()))
                .orElseThrow(() -> new IllegalStateException(
                        "The ticket's linked booking could not be verified"));
    }

    private void setSlaDeadlines(SupportTicket ticket, long createdAt) {
        long baseTime = createdAt > 0L ? createdAt : System.currentTimeMillis();
        long responseHours;
        long resolutionHours;
        switch (normalizePriority(ticket.getPriority())) {
            case SupportTicket.PRIORITY_URGENT -> {
                responseHours = 1L;
                resolutionHours = 8L;
            }
            case SupportTicket.PRIORITY_HIGH -> {
                responseHours = 4L;
                resolutionHours = 24L;
            }
            case SupportTicket.PRIORITY_LOW -> {
                responseHours = 24L;
                resolutionHours = 120L;
            }
            default -> {
                responseHours = 8L;
                resolutionHours = 48L;
            }
        }
        ticket.setResponseDeadlineAt(baseTime + responseHours * 60L * 60L * 1000L);
        ticket.setResolutionDeadlineAt(baseTime + resolutionHours * 60L * 60L * 1000L);
    }

    private boolean ensureSlaDeadlines(SupportTicket ticket) {
        boolean updated = false;
        if (ticket.getResponseDeadlineAt() > 0L && ticket.getResolutionDeadlineAt() > 0L) {
            if (ticket.getRefundTrackingStatus() == null) {
                ticket.setRefundTrackingStatus(
                        SupportTicket.CATEGORY_REFUND.equals(ticket.getCategory())
                                ? "REQUESTED" : "NOT_REQUESTED");
                updated = true;
            }
            return updated;
        }
        setSlaDeadlines(ticket, ticket.getCreatedAt());
        if (ticket.getRefundTrackingStatus() == null) {
            ticket.setRefundTrackingStatus(
                    SupportTicket.CATEGORY_REFUND.equals(ticket.getCategory())
                            ? "REQUESTED" : "NOT_REQUESTED");
        }
        return true;
    }

    private void recordActivity(
            SupportTicket ticket, String eventType, String description,
            Long actorId, String actorRole) {
        SupportTicketActivity activity = new SupportTicketActivity();
        activity.setTicket(ticket);
        activity.setEventType(eventType);
        activity.setDescription(description);
        activity.setActorId(actorId);
        String actorName = actorId == null ? null : userRepository.findById(actorId)
                .map(User::getName)
                .orElse(null);
        if (actorName == null || actorName.isBlank()) {
            actorName = "CUSTOMER".equalsIgnoreCase(actorRole)
                    ? ticket.getCustomerName() : "Support staff";
        }
        activity.setActorName(actorName);
        activity.setActorRole(actorRole == null ? "SYSTEM" : actorRole.toUpperCase(Locale.ROOT));
        activity.setCreatedAt(System.currentTimeMillis());
        activityRepository.save(activity);
    }

    private boolean isValidRefundTransition(String current, String target) {
        return getAllowedRefundTrackingTransitions(current).contains(target);
    }

    private void validateMessage(String message, String fieldName) {
        String content = message == null ? "" : message.trim();
        if (content.length() < MESSAGE_MIN_LENGTH || content.length() > MESSAGE_MAX_LENGTH) {
            throw new IllegalArgumentException(
                    fieldName + " must be " + MESSAGE_MIN_LENGTH + " to "
                            + MESSAGE_MAX_LENGTH + " characters");
        }
    }
}
