package com.moviebooking.moviebookingsystem;

import com.moviebooking.moviebookingsystem.config.SupportAuthorizationInterceptor;
import com.moviebooking.moviebookingsystem.model.SupportTicket;
import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.model.TicketAttachment;
import com.moviebooking.moviebookingsystem.model.SupportTicketActivity;
import com.moviebooking.moviebookingsystem.model.TicketReply;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.repository.BookingRepository;
import com.moviebooking.moviebookingsystem.repository.PaymentRepository;
import com.moviebooking.moviebookingsystem.repository.SupportTicketRepository;
import com.moviebooking.moviebookingsystem.repository.TicketAttachmentRepository;
import com.moviebooking.moviebookingsystem.repository.TicketReplyRepository;
import com.moviebooking.moviebookingsystem.repository.UserRepository;
import com.moviebooking.moviebookingsystem.service.CustomerSupportService;
import com.moviebooking.moviebookingsystem.service.SupportAnalyticsService;
import com.moviebooking.moviebookingsystem.service.TicketAttachmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the 15 required Customer Support Ticket Management areas:
 * priority/category, reply history, reopen, escalation, attachments,
 * feedback, analytics, Tamil/English messages, message length,
 * duplicate detection, empty replies, unauthorized access,
 * attachment validation, repeated submissions and database persistence.
 */
@SpringBootTest(properties = "app.support.email-notifications-enabled=false")
@AutoConfigureMockMvc
class CustomerSupportTicketManagementTests {

    private static final byte[] PNG_BYTES = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
    };

    @Autowired
    private CustomerSupportService supportService;

    @Autowired
    private TicketAttachmentService attachmentService;

    @Autowired
    private SupportAnalyticsService analyticsService;

    @Autowired
    private SupportTicketRepository ticketRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketReplyRepository replyRepository;

    @Autowired
    private TicketAttachmentRepository attachmentRepository;

    @Autowired
    private MockMvc mockMvc;

    private String unique() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private SupportTicket newTicket(String email, String subject, String message) {
        SupportTicket ticket = new SupportTicket();
        ticket.setCustomerName("Test Customer");
        ticket.setEmail(email);
        ticket.setSubject(subject);
        ticket.setMessage(message);
        ticket.setStatus("OPEN");
        ticket.setResponse("");
        return ticket;
    }

    private User user(String role, String email) {
        User user = new User();
        user.setId(1L);
        user.setName("Test " + role);
        user.setEmail(email);
        user.setRole(role);
        user.setStatus("ACTIVE");
        return user;
    }

    // ================= 1. PRIORITY AND CATEGORY =================

    @Test
    void priorityAndCategoryAreStoredAndInvalidValuesRejected() {
        String id = unique();
        SupportTicket ticket = newTicket(id + "@example.com", "Priority Test " + id,
                "Testing priority and category storage");
        ticket.setPriority("high");
        ticket.setCategory("refund_request");

        supportService.createTicket(ticket);

        SupportTicket stored = supportService.getTicketById(ticket.getId());
        assertEquals("HIGH", stored.getPriority());
        assertEquals("REFUND_REQUEST", stored.getCategory());

        SupportTicket defaults = newTicket(id + "-b@example.com", "Default Test " + id,
                "Testing default priority and category values");
        supportService.createTicket(defaults);

        SupportTicket storedDefaults = supportService.getTicketById(defaults.getId());
        assertEquals("MEDIUM", storedDefaults.getPriority());
        assertEquals("OTHER", storedDefaults.getCategory());

        SupportTicket invalid = newTicket(id + "-c@example.com", "Invalid Values " + id,
                "Testing invalid priority and category values");
        invalid.setPriority("NONSENSE");
        assertThrows(IllegalArgumentException.class, () -> supportService.createTicket(invalid));
        invalid.setPriority("MEDIUM");
        invalid.setCategory("NONSENSE");
        assertThrows(IllegalArgumentException.class, () -> supportService.createTicket(invalid));
    }

    // ================= 2. REPLY HISTORY =================

    @Test
    void replyHistoryKeepsFullConversation() {
        String id = unique();
        SupportTicket ticket = newTicket(id + "@example.com", "History Test " + id,
                "Testing reply history conversation");
        supportService.createTicket(ticket);

        supportService.addReply(ticket.getId(), "First staff reply for history test",
                2L, "Support Staff", "SUPPORT");
        supportService.addReply(ticket.getId(), "Second staff reply for history test",
                2L, "Support Staff", "SUPPORT");

        List<TicketReply> replies = supportService.getRepliesForTicket(ticket.getId());
        assertEquals(2, replies.size());
        // Newest first
        assertEquals("Second staff reply for history test", replies.get(0).getMessage());
        assertEquals("First staff reply for history test", replies.get(1).getMessage());
        assertEquals("SUPPORT", replies.get(0).getSenderRole());
        assertTrue(replies.get(0).isFromStaff());
        assertNotNull(replies.get(0).getCreatedAtDisplay());
    }

    // ================= 3. REOPEN TICKET =================

    @Test
    void resolvedTicketCanBeReopened() {
        String id = unique();
        SupportTicket ticket = newTicket(id + "@example.com", "Reopen Test " + id,
                "Testing ticket reopening after resolution");
        supportService.createTicket(ticket);

        supportService.resolveTicket(ticket.getId(), "Resolved for reopen test");
        SupportTicket resolved = supportService.getTicketById(ticket.getId());
        assertEquals("RESOLVED", resolved.getStatus());
        assertTrue(resolved.getResolvedAt() > 0L);

        assertTrue(supportService.reopenTicket(ticket.getId()));
        SupportTicket reopened = supportService.getTicketById(ticket.getId());
        assertEquals("IN_PROGRESS", reopened.getStatus());
        assertEquals(0L, reopened.getResolvedAt());

        // Only resolved tickets can be reopened
        assertThrows(IllegalArgumentException.class,
                () -> supportService.reopenTicket(ticket.getId()));
    }

    // ================= 4. ESCALATION =================

    @Test
    void escalationRequiresReasonAndRaisesPriority() {
        String id = unique();
        SupportTicket ticket = newTicket(id + "@example.com", "Escalation Test " + id,
                "Testing ticket escalation with a mandatory reason");
        supportService.createTicket(ticket);

        // Reason is mandatory
        assertThrows(IllegalArgumentException.class,
                () -> supportService.escalateTicket(ticket.getId(), "  ", 2L, "Support Staff"));
        assertThrows(IllegalArgumentException.class,
                () -> supportService.escalateTicket(ticket.getId(), null, 2L, "Support Staff"));

        assertTrue(supportService.escalateTicket(ticket.getId(),
                "Customer requested supervisor review", 2L, "Support Staff"));

        SupportTicket escalated = supportService.getTicketById(ticket.getId());
        assertTrue(escalated.isEscalated());
        assertEquals("Customer requested supervisor review", escalated.getEscalationReason());
        assertEquals("HIGH", escalated.getPriority());
        assertEquals("IN_PROGRESS", escalated.getStatus());
        assertEquals("Support Staff", escalated.getEscalatedBy());
        assertTrue(escalated.getEscalatedAt() > 0L);
        assertNotNull(escalated.getEscalatedAtDisplay());
    }

    // ================= 5. ATTACHMENTS =================

    @Test
    void attachmentIsStoredAndCanBeReadBack() throws Exception {
        String id = unique();
        SupportTicket ticket = newTicket(id + "@example.com", "Attachment Test " + id,
                "Testing secure attachment storage and download");
        supportService.createTicket(ticket);

        MockMultipartFile file = new MockMultipartFile(
                "file", "receipt.png", "image/png", PNG_BYTES);

        TicketAttachment stored = attachmentService.store(
                file, ticket, 1L, "Test Customer");

        assertNotNull(stored.getId());
        assertEquals(ticket.getId(), stored.getTicket().getId());
        assertEquals("receipt.png", stored.getFileName());
        assertEquals("image/png", stored.getContentType());
        assertEquals(PNG_BYTES.length, stored.getFileSize());
        assertNotNull(stored.getFileSizeDisplay());

        TicketAttachment fetched = attachmentService.getAttachment(stored.getId());
        assertNotNull(fetched);
        assertArrayEquals(PNG_BYTES, attachmentService.readStoredFile(fetched));

        List<TicketAttachment> forTicket =
                attachmentService.getAttachmentsForTicket(ticket.getId());
        assertEquals(1, forTicket.size());
    }

    private static void assertArrayEquals(byte[] expected, byte[] actual) {
        assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            assertEquals(expected[i], actual[i]);
        }
    }

    // ================= 6. FEEDBACK =================

    @Test
    void resolvedTicketAcceptsStarFeedback() {
        String id = unique();
        SupportTicket ticket = newTicket(id + "@example.com", "Feedback Test " + id,
                "Testing customer feedback star ratings");
        supportService.createTicket(ticket);
        supportService.resolveTicket(ticket.getId(), "Resolved for feedback test");

        assertTrue(supportService.rateTicket(ticket.getId(), 5, "Excellent support"));
        SupportTicket rated = supportService.getTicketById(ticket.getId());
        assertEquals(5, rated.getFeedbackRating());
        assertEquals("Excellent support", rated.getFeedbackComment());
        assertTrue(rated.getFeedbackAt() > 0L);

        // Only resolved tickets can be rated
        SupportTicket openTicket = newTicket(id + "-b@example.com", "Feedback Open Test " + id,
                "Testing that open tickets cannot be rated");
        supportService.createTicket(openTicket);
        assertThrows(IllegalArgumentException.class,
                () -> supportService.rateTicket(openTicket.getId(), 4, "Too early"));

        // Rating must be 1-5
        assertThrows(IllegalArgumentException.class,
                () -> supportService.rateTicket(ticket.getId(), 6, "Invalid"));
        assertThrows(IllegalArgumentException.class,
                () -> supportService.rateTicket(ticket.getId(), 0, "Invalid"));
    }

    // ================= 7. ANALYTICS =================

    @Test
    void analyticsUsesActualDatabaseRecords() {
        long totalBefore = supportService.getAllTickets().size();

        String id = unique();
        SupportTicket open = newTicket(id + "-a@example.com", "Analytics Open Test " + id,
                "Testing analytics counting for open tickets");
        open.setCategory("TECHNICAL_ISSUE");
        open.setPriority("URGENT");
        supportService.createTicket(open);

        SupportTicket resolved = newTicket(id + "-b@example.com", "Analytics Resolved Test " + id,
                "Testing analytics counting for resolved tickets");
        supportService.createTicket(resolved);
        supportService.resolveTicket(resolved.getId(), "Resolved for analytics test");
        supportService.rateTicket(resolved.getId(), 5, "Great");

        assertEquals(totalBefore + 2, supportService.getAllTickets().size());

        assertEquals(
                supportService.getTicketsByStatus("OPEN").size(),
                analyticsService.ticketsByStatus().get("OPEN"));
        assertEquals(
                supportService.getTicketsByStatus("RESOLVED").size(),
                analyticsService.ticketsByStatus().get("RESOLVED"));

        assertTrue(analyticsService.ticketsByCategory().get("TECHNICAL_ISSUE") >= 1);
        assertTrue(analyticsService.ticketsByPriority().get("URGENT") >= 1);
        assertTrue(analyticsService.countSatisfied() >= 1);
        assertTrue(analyticsService.countEscalated() >= 0);
    }

    // ================= 8. TAMIL AND ENGLISH UNICODE =================

    @Test
    void tamilAndEnglishMessagesArePreserved() {
        String id = unique();
        String tamilMessage = "எனது படிப்பில் சிக்கல் உள்ளது - நிலையில் இருக்கிறேன்";
        String englishMessage = "My booking has a problem - I need help";

        SupportTicket tamilTicket = newTicket(id + "-a@example.com", "Tamil Test " + id,
                tamilMessage);
        supportService.createTicket(tamilTicket);

        SupportTicket englishTicket = newTicket(id + "-b@example.com", "English Test " + id,
                englishMessage);
        supportService.createTicket(englishTicket);

        assertEquals(tamilMessage, supportService.getTicketById(tamilTicket.getId()).getMessage());
        assertEquals(englishMessage, supportService.getTicketById(englishTicket.getId()).getMessage());

        // Tamil staff reply is preserved too
        String tamilReply = "உங்கள் சிக்கல் தீர்க்கப்பட்டது";
        supportService.addReply(tamilTicket.getId(), tamilReply, 2L, "Support Staff", "SUPPORT");
        assertEquals(tamilReply,
                supportService.getRepliesForTicket(tamilTicket.getId()).get(0).getMessage());
    }

    // ================= 9. MESSAGE LENGTH =================

    @Test
    void messageLengthIsLimitedToTwoThousandCharacters() {
        String id = unique();

        // Too short
        SupportTicket shortTicket = newTicket(id + "-a@example.com", "Short Test " + id, "Too short");
        assertThrows(IllegalArgumentException.class, () -> supportService.createTicket(shortTicket));

        // Too long
        String tooLong = "A".repeat(2001);
        SupportTicket longTicket = newTicket(id + "-b@example.com", "Long Test " + id, tooLong);
        assertThrows(IllegalArgumentException.class, () -> supportService.createTicket(longTicket));

        // Exactly 2000 is accepted
        String maxLength = "B".repeat(2000);
        SupportTicket maxTicket = newTicket(id + "-c@example.com", "Max Test " + id, maxLength);
        supportService.createTicket(maxTicket);
        assertEquals(2000, supportService.getTicketById(maxTicket.getId()).getMessage().length());

        // Staff replies obey the same limit
        SupportTicket replyTicket = newTicket(id + "-d@example.com", "Reply Length Test " + id,
                "Testing staff reply length validation");
        supportService.createTicket(replyTicket);
        assertThrows(IllegalArgumentException.class,
                () -> supportService.addReply(replyTicket.getId(), "C".repeat(2001),
                        2L, "Support Staff", "SUPPORT"));
    }

    // ================= 10. DUPLICATE DETECTION =================

    @Test
    void duplicateTicketsWithinWindowAreRejected() {
        String id = unique();
        String email = id + "@example.com";

        SupportTicket first = newTicket(email, "Duplicate Test " + id,
                "Testing duplicate ticket detection");
        supportService.createTicket(first);

        // Identical email + subject + category + message within the window
        SupportTicket duplicate = newTicket(email, "Duplicate Test " + id,
                "Testing duplicate ticket detection");
        assertThrows(IllegalArgumentException.class, () -> supportService.createTicket(duplicate));

        // A different message from the same customer is allowed
        SupportTicket different = newTicket(email, "Duplicate Test " + id,
                "Testing duplicate ticket detection with a different message body");
        supportService.createTicket(different);
    }

    // ================= 11. EMPTY REPLIES =================

    @Test
    void emptyOrWhitespaceRepliesAreRejected() {
        String id = unique();
        SupportTicket ticket = newTicket(id + "@example.com", "Empty Reply Test " + id,
                "Testing that empty staff replies are rejected");
        supportService.createTicket(ticket);

        assertThrows(IllegalArgumentException.class,
                () -> supportService.addReply(ticket.getId(), "", 2L, "Support Staff", "SUPPORT"));
        assertThrows(IllegalArgumentException.class,
                () -> supportService.addReply(ticket.getId(), "   ", 2L, "Support Staff", "SUPPORT"));
        assertThrows(IllegalArgumentException.class,
                () -> supportService.addReply(ticket.getId(), null, 2L, "Support Staff", "SUPPORT"));

        // No reply was persisted
        assertEquals(0, supportService.getRepliesForTicket(ticket.getId()).size());
    }

    // ================= 12. UNAUTHORIZED ACCESS =================

    @Test
    void interceptorRedirectsUnauthorizedAccess() throws Exception {
        SupportAuthorizationInterceptor interceptor = new SupportAuthorizationInterceptor();

        // Anonymous request to staff area -> login page
        MockHttpServletRequest anonymousStaff =
                new MockHttpServletRequest("GET", "/support/tickets");
        MockHttpServletResponse staffResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(anonymousStaff, staffResponse, null));
        assertEquals(302, staffResponse.getStatus());
        assertEquals("/login", staffResponse.getRedirectedUrl());

        // Customer trying to reach the staff dashboard -> friendly error page
        MockHttpServletRequest customerToStaff =
                new MockHttpServletRequest("GET", "/support/tickets");
        customerToStaff.getSession().setAttribute("loggedInUser", user("CUSTOMER", "c@example.com"));
        MockHttpServletResponse customerStaffResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(customerToStaff, customerStaffResponse, null));
        assertEquals(302, customerStaffResponse.getStatus());
        assertEquals("/error/403", customerStaffResponse.getRedirectedUrl());

        // Support staff reaching the customer ticket list -> friendly error page
        MockHttpServletRequest staffToCustomer =
                new MockHttpServletRequest("GET", "/my-support-tickets");
        staffToCustomer.getSession().setAttribute("loggedInUser", user("SUPPORT", "s@cinebook.com"));
        MockHttpServletResponse staffCustomerResponse = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(staffToCustomer, staffCustomerResponse, null));
        assertEquals(302, staffCustomerResponse.getStatus());
        assertEquals("/error/403", staffCustomerResponse.getRedirectedUrl());

        // Support staff is allowed into the staff area
        MockHttpServletRequest supportToStaff =
                new MockHttpServletRequest("GET", "/support/tickets");
        supportToStaff.getSession().setAttribute("loggedInUser", user("SUPPORT", "s@cinebook.com"));
        MockHttpServletResponse supportResponse = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(supportToStaff, supportResponse, null));

        // Administrator is allowed into the staff area
        MockHttpServletRequest adminToStaff =
                new MockHttpServletRequest("GET", "/support/tickets");
        adminToStaff.getSession().setAttribute("loggedInUser", user("ADMIN", "a@cinebook.com"));
        MockHttpServletResponse adminResponse = new MockHttpServletResponse();
        assertTrue(interceptor.preHandle(adminToStaff, adminResponse, null));
    }

    @Test
    void endpointsRedirectUnauthorizedAccess() throws Exception {
        // Customer blocked from staff pages -> friendly error page
        mockMvc.perform(get("/support/tickets")
                        .sessionAttr("loggedInUser", user("CUSTOMER", "c@example.com")))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/error/403"));

        // Anonymous blocked from customer ticket list -> login page
        mockMvc.perform(get("/my-support-tickets"))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl("/login"));

        // Support staff allowed on staff pages
        mockMvc.perform(get("/support/tickets")
                        .sessionAttr("loggedInUser", user("SUPPORT", "s@cinebook.com")))
                .andExpect(status().isOk());

        // Customer allowed on their own ticket list
        mockMvc.perform(get("/my-support-tickets")
                        .sessionAttr("loggedInUser", user("CUSTOMER", "c@example.com")))
                .andExpect(status().isOk());
    }

    @Test
    void customerCannotAccessAnotherCustomersTicket() throws Exception {
        String id = unique();
        SupportTicket ticket = newTicket(id + "-owner@example.com", "Ownership Test " + id,
                "Testing that customers cannot access other customers tickets");
        supportService.createTicket(ticket);

        User stranger = user("CUSTOMER", id + "-stranger@example.com");

        // Reply to someone else's ticket
        mockMvc.perform(post("/my-support-tickets/reply")
                        .param("ticketId", String.valueOf(ticket.getId()))
                        .param("message", "Not my ticket reply")
                        .sessionAttr("loggedInUser", stranger))
                .andExpect(status().isForbidden());

        // Rate someone else's ticket
        mockMvc.perform(post("/my-support-tickets/rate")
                        .param("ticketId", String.valueOf(ticket.getId()))
                        .param("rating", "5")
                        .sessionAttr("loggedInUser", stranger))
                .andExpect(status().isForbidden());

        // Reopen someone else's ticket
        mockMvc.perform(post("/my-support-tickets/reopen")
                        .param("ticketId", String.valueOf(ticket.getId()))
                        .sessionAttr("loggedInUser", stranger))
                .andExpect(status().isForbidden());

        // Nothing was persisted by the stranger
        assertEquals(0, supportService.getRepliesForTicket(ticket.getId()).size());
        assertEquals("OPEN", supportService.getTicketById(ticket.getId()).getStatus());
    }

    // ================= 13. ATTACHMENT VALIDATION =================

    @Test
    void attachmentValidationRejectsUnsafeUploads() {
        String id = unique();
        SupportTicket ticket = newTicket(id + "@example.com", "Attachment Validation Test " + id,
                "Testing attachment validation and malicious upload prevention");
        supportService.createTicket(ticket);

        // Oversized file (over 5 MB)
        MockMultipartFile tooBig = new MockMultipartFile(
                "file", "big.png", "image/png", new byte[5 * 1024 * 1024 + 1]);
        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.store(tooBig, ticket, 1L, "Test Customer"));

        // Disallowed type (GIF)
        byte[] gif = new byte[]{(byte) 0x47, 0x49, 0x46, 0x38, 0x39, 0x61};
        MockMultipartFile gifFile = new MockMultipartFile(
                "file", "anim.gif", "image/gif", gif);
        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.store(gifFile, ticket, 1L, "Test Customer"));

        // Spoofed extension: declares image/jpeg but content is plain text
        MockMultipartFile spoofed = new MockMultipartFile(
                "file", "malware.jpg", "image/jpeg", "not an image".getBytes());
        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.store(spoofed, ticket, 1L, "Test Customer"));

        // Spoofed PDF extension with text content
        MockMultipartFile fakePdf = new MockMultipartFile(
                "file", "fake.pdf", "application/pdf", "plain text".getBytes());
        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.store(fakePdf, ticket, 1L, "Test Customer"));

        // Pre-validation used by the customer form
        assertThrows(IllegalArgumentException.class,
                () -> attachmentService.validateAttachment(spoofed));

        // No attachment was persisted
        assertEquals(0, attachmentService.getAttachmentsForTicket(ticket.getId()).size());
    }

    // ================= 14. REPEATED SUBMISSIONS =================

    @Test
    void repeatedIdenticalSubmissionsArePrevented() {
        String id = unique();
        String email = id + "@example.com";

        SupportTicket first = newTicket(email, "Resubmission Test " + id,
                "Testing prevention of repeated form submissions");
        supportService.createTicket(first);

        // Immediate double submit of the identical form data
        for (int i = 0; i < 3; i++) {
            SupportTicket repeat = newTicket(email, "Resubmission Test " + id,
                    "Testing prevention of repeated form submissions");
            assertThrows(IllegalArgumentException.class,
                    () -> supportService.createTicket(repeat));
        }

        // Only one ticket exists for this subject
        long count = supportService.getAllTickets().stream()
                .filter(t -> t.getSubject().equals("Resubmission Test " + id))
                .count();
        assertEquals(1, count);
    }

    // ================= 15. DATABASE PERSISTENCE =================

    @Test
    void ticketsRepliesAndAttachmentsPersistToDatabase() {
        String id = unique();

        SupportTicket ticket = newTicket(id + "@example.com", "Persistence Test " + id,
                "Testing that tickets persist to the database");
        ticket.setPriority("URGENT");
        ticket.setCategory("PAYMENT_ISSUE");
        supportService.createTicket(ticket);

        supportService.addReply(ticket.getId(), "Persisted reply", 2L, "Support Staff", "SUPPORT");

        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf",
                ("%PDF-1.4 test").getBytes());
        attachmentService.store(file, ticket, 1L, "Test Customer");

        // Reload everything from the database
        SupportTicket fromDb = ticketRepository.findById(ticket.getId()).orElse(null);
        assertNotNull(fromDb);
        assertEquals("Persistence Test " + id, fromDb.getSubject());
        assertEquals("URGENT", fromDb.getPriority());
        assertEquals("PAYMENT_ISSUE", fromDb.getCategory());

        List<TicketReply> repliesFromDb = replyRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId());
        assertEquals(1, repliesFromDb.size());
        assertEquals("Persisted reply", repliesFromDb.get(0).getMessage());

        List<TicketAttachment> attachmentsFromDb =
                attachmentRepository.findByTicketIdOrderByUploadedAtAsc(ticket.getId());
        assertEquals(1, attachmentsFromDb.size());
        assertEquals("doc.pdf", attachmentsFromDb.get(0).getFileName());
    }

    @Test
    void bookingReferenceMustBelongToTicketEmail() {
        String id = unique();
        String ownerEmail = id + "@example.com";
        Booking booking = new Booking();
        booking.setCustomerEmail(ownerEmail);
        booking.setMovieTitle("Test Movie");
        booking.setStatus("PAID");
        booking.setPrice(1200);
        booking = bookingRepository.save(booking);

        SupportTicket valid = newTicket(ownerEmail, "Booking reference " + id,
                "Please help me with my verified booking reference");
        valid.setBookingId(booking.getId());
        supportService.createTicket(valid);
        assertEquals(booking.getId(),
                supportService.getVerifiedBookingForTicket(valid.getId()).getId());

        SupportTicket foreign = newTicket(id + "-other@example.com", "Foreign reference " + id,
                "A different customer must not link this booking");
        foreign.setBookingId(booking.getId());
        assertThrows(IllegalArgumentException.class, () -> supportService.createTicket(foreign));

        SupportTicket malformed = newTicket(ownerEmail, "Malformed reference " + id,
                "Booking references must be positive identifiers");
        malformed.setBookingId(0L);
        assertThrows(IllegalArgumentException.class,
                () -> supportService.createTicket(malformed));
    }

    @Test
    void assignmentSlaTimelineNotesAndFaqAreStaffOnly() throws Exception {
        String id = unique();
        String email = id + "@example.com";
        User staff = new User();
        staff.setName("Support " + id);
        staff.setEmail(id + "-staff@example.com");
        staff.setUsername("support-" + id);
        staff.setRole("SUPPORT");
        staff.setStatus("ACTIVE");
        staff = userRepository.save(staff);

        SupportTicket ticket = newTicket(email, "Assignment Test " + id,
                "Testing assignment timeline and private staff notes");
        supportService.createTicket(ticket);
        assertTrue(ticket.getResponseDeadlineAt() > ticket.getCreatedAt());
        assertTrue(ticket.getResolutionDeadlineAt() > ticket.getResponseDeadlineAt());
        assertFalse(ticket.isResponseSlaOverdue());
        assertFalse(ticket.isResolutionSlaOverdue());

        assertTrue(supportService.assignTicket(
                ticket.getId(), staff.getId(), staff.getId()));
        assertEquals(staff.getId(), supportService.getTicketById(ticket.getId()).getAssignedStaffId());

        supportService.addInternalNote(
                ticket.getId(), "Staff-only confidential note", staff.getId(), staff.getName(), "SUPPORT");
        assertTrue(supportService.getInternalNotesForTicket(ticket.getId()).stream()
                .anyMatch(note -> note.getNote().equals("Staff-only confidential note")));
        List<SupportTicketActivity> activities = supportService.getActivitiesForTicket(ticket.getId());
        assertTrue(activities.stream().anyMatch(activity -> "TICKET_ASSIGNED".equals(activity.getEventType())));
        assertTrue(activities.stream().anyMatch(activity -> "INTERNAL_NOTE_ADDED".equals(activity.getEventType())));

        mockMvc.perform(get("/support/tickets/view")
                        .param("id", ticket.getId().toString())
                        .sessionAttr("loggedInUser", staff))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FAQ &amp; Quick Replies")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Staff-only confidential note")));

        User customer = user("CUSTOMER", email);
        mockMvc.perform(get("/my-support-tickets")
                        .sessionAttr("loggedInUser", customer)
                        .sessionAttr("userEmail", email))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Staff-only confidential note"))));
    }

    @Test
    void refundTrackingRequiresVerifiedPaymentAndValidTransitions() {
        String id = unique();
        String email = id + "@example.com";
        Booking booking = new Booking();
        booking.setCustomerEmail(email);
        booking.setMovieTitle("Refund Test Movie");
        booking.setStatus("PAID");
        booking.setPrice(2000);
        booking = bookingRepository.save(booking);

        Payment payment = new Payment();
        payment.setBookingId(booking.getId());
        payment.setAmount(2000);
        payment.setStatus("SUCCESS");
        paymentRepository.save(payment);

        SupportTicket ticket = newTicket(email, "Refund tracking " + id,
                "Please review the refund request for this booking");
        ticket.setCategory(SupportTicket.CATEGORY_REFUND);
        ticket.setBookingId(booking.getId());
        supportService.createTicket(ticket);
        assertEquals("REQUESTED", ticket.getRefundTrackingStatus());

        assertThrows(IllegalArgumentException.class,
                () -> supportService.updateRefundTracking(ticket.getId(), "REFUNDED", null, "Staff"));
        assertTrue(supportService.updateRefundTracking(ticket.getId(), "UNDER_REVIEW", null, "Staff"));
        assertTrue(supportService.updateRefundTracking(ticket.getId(), "APPROVED", null, "Staff"));
        assertTrue(supportService.updateRefundTracking(
                ticket.getId(), "REFERRED_TO_CASHIER", null, "Staff"));

        booking.setStatus("REFUNDED");
        bookingRepository.save(booking);
        payment.setStatus("REFUNDED");
        paymentRepository.save(payment);

        assertTrue(supportService.updateRefundTracking(ticket.getId(), "REFUNDED", null, "Staff"));
        assertEquals("REFUNDED",
                supportService.getTicketById(ticket.getId()).getRefundTrackingStatus());
        assertThrows(IllegalArgumentException.class,
                () -> supportService.updateRefundTracking(ticket.getId(), "UNKNOWN", null, "Staff"));
    }
}
