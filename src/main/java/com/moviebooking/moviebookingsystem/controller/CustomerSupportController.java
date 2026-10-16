package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.SupportTicket;
import com.moviebooking.moviebookingsystem.model.Booking;
import com.moviebooking.moviebookingsystem.model.Payment;
import com.moviebooking.moviebookingsystem.model.TicketAttachment;
import com.moviebooking.moviebookingsystem.model.TicketReply;
import com.moviebooking.moviebookingsystem.model.User;
import com.moviebooking.moviebookingsystem.service.CustomerSupportService;
import com.moviebooking.moviebookingsystem.service.NotificationService;
import com.moviebooking.moviebookingsystem.service.SupportAnalyticsService;
import com.moviebooking.moviebookingsystem.service.SupportEmailNotificationService;
import com.moviebooking.moviebookingsystem.service.TicketAttachmentService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Controller
public class CustomerSupportController {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(CustomerSupportController.class);

    private final CustomerSupportService supportService;
    private final NotificationService notificationService;
    private final SupportAnalyticsService analyticsService;
    private final TicketAttachmentService attachmentService;
    private final SupportEmailNotificationService emailNotificationService;

    public CustomerSupportController(
            CustomerSupportService supportService,
            NotificationService notificationService,
            SupportAnalyticsService analyticsService,
            TicketAttachmentService attachmentService,
            SupportEmailNotificationService emailNotificationService) {

        this.supportService = supportService;
        this.notificationService = notificationService;
        this.analyticsService = analyticsService;
        this.attachmentService = attachmentService;
        this.emailNotificationService = emailNotificationService;
    }


    // =====================================================
    // SUPPORT STAFF DASHBOARD
    // =====================================================

    @GetMapping("/support")
    public String dashboard(Model model) {

        model.addAttribute("statusCounts", analyticsService.ticketsByStatus());
        model.addAttribute("categoryCounts", analyticsService.ticketsByCategory());
        model.addAttribute("priorityCounts", analyticsService.ticketsByPriority());
        model.addAttribute("escalatedCount", analyticsService.countEscalated());
        model.addAttribute("satisfiedCount", analyticsService.countSatisfied());
        model.addAttribute("dissatisfiedCount", analyticsService.countDissatisfied());
        model.addAttribute("averageResponseMs", analyticsService.averageResponseTimeMillis());
        model.addAttribute("averageResolveMs", analyticsService.averageResolveTimeMillis());
        model.addAttribute("totalReplies", analyticsService.totalReplies());
        model.addAttribute("openCount", analyticsService.ticketsByStatus().getOrDefault("OPEN", 0L));
        model.addAttribute("resolvedCount", analyticsService.ticketsByStatus().getOrDefault("RESOLVED", 0L));
        model.addAttribute("inProgressCount", analyticsService.ticketsByStatus().getOrDefault("IN_PROGRESS", 0L));
        model.addAttribute("ticketCount", supportService.getAllTickets().size());

        model.addAttribute(
                "recentTickets",
                supportService.getAllTickets()
                        .stream()
                        .sorted(
                                (first, second) ->
                                        Long.compare(
                                                second.getCreatedAt(),
                                                first.getCreatedAt()))
                        .limit(5)
                        .toList()
        );

        return "support/dashboard";
    }


    // =====================================================
    // SUPPORT STAFF - VIEW ALL TICKETS (WITH FILTERS)
    // =====================================================

    @GetMapping("/support/tickets")
    public String tickets(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String escalated,
            @RequestParam(required = false) String keyword,
            Model model) {

        List<SupportTicket> result = supportService.findTickets(status, keyword);

        if (priority != null && !priority.isBlank()
                && !"ALL".equalsIgnoreCase(priority.trim())) {
            String value = priority.trim().toUpperCase();
            result = result.stream()
                    .filter(ticket -> value.equals(ticket.getPriority()))
                    .toList();
        }

        if (category != null && !category.isBlank()
                && !"ALL".equalsIgnoreCase(category.trim())) {
            String value = category.trim().toUpperCase();
            result = result.stream()
                    .filter(ticket -> value.equals(ticket.getCategory()))
                    .toList();
        }

        if (escalated != null && !escalated.isBlank()
                && !"ALL".equalsIgnoreCase(escalated.trim())) {
            boolean onlyEscalated = Boolean.parseBoolean(escalated.trim());
            result = result.stream()
                    .filter(SupportTicket::isEscalated)
                    .toList();
            if (!onlyEscalated) {
                result = supportService.findTickets(status, keyword);
            }
        }

        model.addAttribute("tickets", result);
        model.addAttribute("status", normaliseStatusFilter(status));
        model.addAttribute("priority", normaliseOptionalFilter(priority));
        model.addAttribute("category", normaliseOptionalFilter(category));
        model.addAttribute("escalated", normaliseOptionalFilter(escalated));
        model.addAttribute("keyword", keyword == null ? "" : keyword);
        model.addAttribute("priorities", supportService.getAllowedPriorities());
        model.addAttribute("categories", supportService.getAllowedCategories());

        return "support/tickets";
    }

    private String normaliseStatusFilter(String status) {
        if (status == null || status.isBlank()) {
            return "ALL";
        }
        String value = status.trim().toUpperCase();
        if (List.of("ALL", "OPEN", "IN_PROGRESS", "RESOLVED").contains(value)) {
            return value;
        }
        return "ALL";
    }

    private String normaliseOptionalFilter(String value) {
        if (value == null || value.isBlank()
                || "ALL".equalsIgnoreCase(value.trim())) {
            return "ALL";
        }
        return value.trim().toUpperCase();
    }


    // =====================================================
    // SUPPORT STAFF - ADD TICKET PAGE
    // =====================================================

    @GetMapping("/support/tickets/add")
    public String addTicketPage(Model model) {

        model.addAttribute(
                "ticket",
                new SupportTicket()
        );
        model.addAttribute("priorities", supportService.getAllowedPriorities());
        model.addAttribute("categories", supportService.getAllowedCategories());

        return "support/add-ticket";
    }


    // =====================================================
    // SUPPORT STAFF - SAVE NEW TICKET
    // =====================================================

    @PostMapping("/support/tickets/add")
    public String addTicket(
            @ModelAttribute SupportTicket ticket,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            supportService.createTicket(ticket);
            notifyTicketByEmail(ticket,
                    "Support ticket #" + ticket.getId() + " created",
                    "Your support ticket #" + ticket.getId()
                            + " has been created. Our team will review it.",
                    redirectAttributes);
            return "redirect:/support/tickets";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("ticket", ticket);
            model.addAttribute("priorities", supportService.getAllowedPriorities());
            model.addAttribute("categories", supportService.getAllowedCategories());
            return "support/add-ticket";
        }
    }


    // =====================================================
    // SUPPORT STAFF - VIEW TICKET DETAILS
    // =====================================================

    @GetMapping("/support/tickets/view")
    public String viewTicket(
            @RequestParam Long id,
            Model model) {

        SupportTicket ticket =
                supportService.getTicketById(id);

        if (ticket == null) {
            return "redirect:/support/tickets";
        }

        populateTicketDetailsModel(model, ticket);

        return "support/ticket-details";
    }


    // =====================================================
    // SUPPORT STAFF - RESPOND AND RESOLVE
    // =====================================================

    @PostMapping("/support/tickets/respond")
    public String respondToTicket(
            @RequestParam Long id,
            @RequestParam String response,
            @RequestParam String status,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            SupportTicket ticket = supportService.getTicketById(id);
            boolean updated = supportService.respondToTicket(
                    id,
                    response,
                    status
            );
            if (updated && ticket != null) {
                notificationService.notifyCustomer(
                        ticket.getEmail(),
                        "Support updated your ticket",
                        "Ticket #" + ticket.getId() + " status: " + status + ". " + response,
                        "/my-support-tickets"
                );
                notifyTicketByEmail(ticket,
                        "Support updated ticket #" + ticket.getId(),
                        "Your ticket status is now " + ticket.getStatus()
                                + ". Support response: " + response,
                        redirectAttributes);
            }
            return "redirect:/support/tickets/view?id=" + id;
        } catch (IllegalArgumentException e) {
            SupportTicket ticket = supportService.getTicketById(id);
            model.addAttribute("error", e.getMessage());
            if (ticket != null) {
                populateTicketDetailsModel(model, ticket);
            }
            return "support/ticket-details";
        }
    }


    // =====================================================
    // SUPPORT STAFF - REPLY TO TICKET (KEEPS CONVERSATION HISTORY)
    // =====================================================

    @PostMapping("/support/tickets/reply")
    public String replyToTicket(
            @RequestParam Long id,
            @RequestParam String message,
            @RequestParam(required = false) String status,
            HttpSession session,
            Model model,
            RedirectAttributes redirectAttributes) {

        SupportTicket ticket = supportService.getTicketById(id);

        try {
            supportService.addReply(
                    id,
                    message,
                    currentUserId(session),
                    currentUserName(session),
                    "SUPPORT"
            );

            if (status != null && !status.isBlank()) {
                supportService.respondToTicket(id, message, status);
            }

            if (ticket != null) {
                notificationService.notifyCustomer(
                        ticket.getEmail(),
                        "New reply on ticket #" + ticket.getId(),
                        "Support replied to ticket #" + ticket.getId() + ": " + message,
                        "/my-support-tickets"
                );
                notifyTicketByEmail(ticket,
                        "New reply on support ticket #" + ticket.getId(),
                        "Support replied to your ticket:\n\n" + message,
                        redirectAttributes);
            }

            return "redirect:/support/tickets/view?id=" + id;
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            if (ticket != null) {
                populateTicketDetailsModel(model, ticket);
            }
            return "support/ticket-details";
        }
    }


    // =====================================================
    // SUPPORT STAFF - ESCALATE TICKET
    // =====================================================

    @PostMapping("/support/tickets/escalate")
    public String escalateTicket(
            @RequestParam Long id,
            @RequestParam String reason,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        SupportTicket ticket = supportService.getTicketById(id);

        try {
            boolean updated = supportService.escalateTicket(
                    id,
                    reason,
                    currentUserId(session),
                    currentUserName(session)
            );

            if (updated && ticket != null) {
                String customerMessage = "Your support ticket #" + ticket.getId()
                        + " has been escalated and assigned priority "
                        + ticket.getPriority() + ".";
                notificationService.notifySupport(
                        "Ticket #" + ticket.getId() + " escalated",
                        "Ticket #" + ticket.getId() + " (" + ticket.getSubject()
                                + ") was escalated. Priority: " + ticket.getPriority(),
                        "/support/tickets/view?id=" + ticket.getId()
                );

                notificationService.notifyAdmin(
                        "Ticket #" + ticket.getId() + " escalated",
                        "Support ticket #" + ticket.getId() + " (" + ticket.getSubject()
                                + ") was escalated by " + currentUserName(session)
                                + ". Reason: " + ticket.getEscalationReason(),
                        "/support/tickets/view?id=" + ticket.getId()
                );
                notificationService.notifyCustomer(
                        ticket.getEmail(), "Support ticket escalated",
                        customerMessage, "/my-support-tickets");
                notifyTicketByEmail(ticket, "Support ticket escalated",
                        customerMessage, redirectAttributes);
            }

            redirectAttributes.addFlashAttribute(
                    "success",
                    updated ? "Ticket escalated successfully." : "Ticket could not be escalated."
            );
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/support/tickets/view?id=" + id;
    }


    // =====================================================
    // SUPPORT STAFF - ADJUST PRIORITY
    // =====================================================

    @PostMapping("/support/tickets/priority")
    public String adjustPriority(
            @RequestParam Long id,
            @RequestParam String priority,
            RedirectAttributes redirectAttributes) {

        boolean updated;
        try {
            updated = supportService.adjustPriority(id, priority);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/support/tickets/view?id=" + id;
        }

        redirectAttributes.addFlashAttribute(
                "success",
                updated ? "Priority updated to " + priority + "." : "Priority could not be updated."
        );

        return "redirect:/support/tickets/view?id=" + id;
    }

    @PostMapping("/support/tickets/assign")
    public String assignTicket(
            @RequestParam Long id,
            @RequestParam(required = false) Long assignedStaffId,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        SupportTicket ticket = supportService.getTicketById(id);
        try {
            boolean updated = supportService.assignTicket(
                    id, assignedStaffId, currentUserId(session));
            if (!updated) {
                redirectAttributes.addFlashAttribute("error", "Ticket not found.");
            } else if (ticket != null) {
                SupportTicket refreshed = supportService.getTicketById(id);
                String assignee = refreshed == null ? null : refreshed.getAssignedStaffName();
                String update = assignee == null
                        ? "Your support ticket is no longer assigned to a staff member."
                        : "Your support ticket has been assigned to " + assignee + ".";
                notificationService.notifyCustomer(
                        ticket.getEmail(), "Support ticket assignment updated", update,
                        "/my-support-tickets");
                notifyTicketByEmail(ticket, "Support ticket assignment updated", update,
                        redirectAttributes);
                redirectAttributes.addFlashAttribute("success", "Ticket assignment updated.");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/support/tickets/view?id=" + id;
    }

    @PostMapping("/support/tickets/refund-tracking")
    public String updateRefundTracking(
            @RequestParam Long id,
            @RequestParam String refundTrackingStatus,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        SupportTicket ticket = supportService.getTicketById(id);
        try {
            boolean updated = supportService.updateRefundTracking(
                    id, refundTrackingStatus, currentUserId(session), currentUserName(session));
            if (!updated) {
                redirectAttributes.addFlashAttribute("error", "Ticket not found.");
            } else if (ticket != null) {
                SupportTicket refreshed = supportService.getTicketById(id);
                String status = refreshed == null
                        ? refundTrackingStatus : refreshed.getRefundTrackingStatus();
                String message = "Refund tracking for support ticket #" + id
                        + " is now " + status + ".";
                notificationService.notifyCustomer(
                        ticket.getEmail(), "Refund request update", message,
                        "/my-support-tickets");
                notifyTicketByEmail(ticket, "Refund request update", message, redirectAttributes);
                redirectAttributes.addFlashAttribute("success", "Refund tracking updated.");
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/support/tickets/view?id=" + id;
    }

    @PostMapping("/support/tickets/note")
    public String addInternalNote(
            @RequestParam Long id,
            @RequestParam String note,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        try {
            supportService.addInternalNote(
                    id, note, currentUserId(session), currentUserName(session), currentUserRole(session));
            redirectAttributes.addFlashAttribute("success", "Internal note added.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/support/tickets/view?id=" + id;
    }


    // =====================================================
    // SUPPORT STAFF - REOPEN RESOLVED TICKET
    // =====================================================

    @PostMapping("/support/tickets/reopen")
    public String reopenTicket(
            @RequestParam Long id,
            RedirectAttributes redirectAttributes) {

        boolean updated;

        try {
            updated = supportService.reopenTicket(id);
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/support/tickets/view?id=" + id;
        }

        redirectAttributes.addFlashAttribute(
                "success",
                updated ? "Ticket reopened as IN_PROGRESS." : "Ticket could not be reopened."
        );

        return "redirect:/support/tickets/view?id=" + id;
    }


    // =====================================================
    // SUPPORT STAFF - UPLOAD ATTACHMENT
    // =====================================================

    @PostMapping("/support/tickets/attach")
    public String attachFile(
            @RequestParam Long id,
            @RequestParam("file") MultipartFile file,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        SupportTicket ticket = supportService.getTicketById(id);

        try {
            attachmentService.store(
                    file,
                    ticket,
                    currentUserId(session),
                    currentUserName(session)
            );
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Attachment uploaded successfully."
            );
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/support/tickets/view?id=" + id;
    }


    // =====================================================
    // SUPPORT STAFF - DELETE ATTACHMENT
    // =====================================================

    @PostMapping("/support/tickets/attachment/delete")
    public String deleteAttachment(
            @RequestParam Long attachmentId,
            @RequestParam Long ticketId,
            RedirectAttributes redirectAttributes) {

        boolean deleted = attachmentService.deleteAttachment(attachmentId);

        redirectAttributes.addFlashAttribute(
                "success",
                deleted ? "Attachment deleted." : "Attachment could not be deleted."
        );

        return "redirect:/support/tickets/view?id=" + ticketId;
    }


    // =====================================================
    // DOWNLOAD ATTACHMENT
    // =====================================================

    @GetMapping("/support/attachments")
    public ResponseEntity<byte[]> downloadAttachment(
            @RequestParam Long id) {

        TicketAttachment attachment = attachmentService.getAttachment(id);

        if (attachment == null) {
            return ResponseEntity.notFound().build();
        }

        byte[] content;
        try {
            content = attachmentService.readStoredFile(attachment);
        } catch (java.io.IOException e) {
            return ResponseEntity.internalServerError().build();
        }

        MediaType contentType;
        try {
            contentType = MediaType.valueOf(attachment.getContentType());
        } catch (IllegalArgumentException e) {
            contentType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + attachment.getFileName() + "\"")
                .body(content);
    }


    // =====================================================
    // SUPPORT STAFF - EDIT TICKET PAGE
    // =====================================================

    @GetMapping("/support/tickets/edit")
    public String editTicketPage(
            @RequestParam Long id,
            Model model) {

        SupportTicket ticket =
                supportService.getTicketById(id);

        if (ticket == null) {
            return "redirect:/support/tickets";
        }

        model.addAttribute(
                "ticket",
                ticket
        );

        return "support/edit-ticket";
    }


    // =====================================================
    // SUPPORT STAFF - SAVE EDITED TICKET
    // =====================================================

    @PostMapping("/support/tickets/edit")
    public String editTicket(
            @ModelAttribute SupportTicket ticket,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            boolean updated = supportService.updateTicketDetails(
                    ticket.getId(),
                    ticket.getCustomerName(),
                    ticket.getSubject(),
                    ticket.getMessage()
            );

            if (!updated) {
                redirectAttributes.addFlashAttribute(
                        "error",
                        "The selected ticket no longer exists."
                );
                return "redirect:/support/tickets";
            }

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Ticket updated successfully."
            );

            return "redirect:/support/tickets/view?id=" + ticket.getId();

        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("ticket", ticket);
            return "support/edit-ticket";
        }
    }


    // =====================================================
    // SUPPORT STAFF - RESOLVE SELECTED TICKETS
    // =====================================================

    @PostMapping("/support/tickets/resolve-selected")
    public String resolveSelectedTickets(
            @RequestParam(required = false) List<Long> ids,
            RedirectAttributes redirectAttributes) {

        if (ids == null || ids.isEmpty()) {
            redirectAttributes.addFlashAttribute(
                    "error",
                    "Select at least one ticket to resolve."
            );
            return "redirect:/support/tickets";
        }

        List<SupportTicket> resolved =
                supportService.resolveTickets(
                        ids,
                        "Resolved by support staff."
                );

        for (SupportTicket ticket : resolved) {
            notificationService.notifyCustomer(
                    ticket.getEmail(),
                    "Support ticket #" + ticket.getId() + " resolved",
                    "Your support ticket has been resolved by our team.",
                    "/my-support-tickets"
            );
            notifyTicketByEmail(ticket,
                    "Support ticket #" + ticket.getId() + " resolved",
                    "Your support ticket has been resolved by our team.",
                    redirectAttributes);
        }

        redirectAttributes.addFlashAttribute(
                "success",
                resolved.size()
                        + " ticket(s) marked as resolved and customers notified."
        );

        return "redirect:/support/tickets";
    }


    // =====================================================
    // SUPPORT STAFF - DELETE TICKET
    // =====================================================

    @PostMapping("/support/tickets/delete")
    public String deleteTicket(@RequestParam Long id) {

        attachmentService.deleteAttachmentsForTicket(id);
        supportService.deleteTicket(id);

        return "redirect:/support/tickets";
    }


    // =====================================================
    // CUSTOMER - CONTACT SUPPORT PAGE
    // =====================================================

    @GetMapping("/contact-support")
    public String contactSupport(Model model, HttpSession session) {

        SupportTicket ticket = new SupportTicket();

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        ticket.setCustomerId(loggedInUser == null ? null : loggedInUser.getId());
        if (loggedInUser != null) {
            ticket.setCustomerName(loggedInUser.getName());
            ticket.setEmail(loggedInUser.getEmail());
        }

        model.addAttribute("ticket", ticket);
        model.addAttribute("priorities", supportService.getAllowedPriorities());
        model.addAttribute("categories", supportService.getAllowedCategories());

        return "contact-support";
    }


    // =====================================================
    // CUSTOMER - SUBMIT SUPPORT TICKET
    // =====================================================

    @PostMapping("/contact-support")
    public String submitCustomerTicket(
            @ModelAttribute SupportTicket ticket,
            @RequestParam(name = "attachment", required = false) MultipartFile attachment,
            Model model,
            HttpSession session,
            RedirectAttributes redirectAttributes) {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        ticket.setCustomerId(loggedInUser == null ? null : loggedInUser.getId());
        if (loggedInUser != null) {
            ticket.setCustomerName(loggedInUser.getName());
            ticket.setEmail(loggedInUser.getEmail());
        }
        ticket.setStatus("OPEN");

        // Validate the attachment first so invalid uploads
        // never create a ticket
        try {
            if (attachment != null && !attachment.isEmpty()) {
                attachmentService.validateAttachment(attachment);
            }
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("ticket", ticket);
            model.addAttribute("priorities", supportService.getAllowedPriorities());
            model.addAttribute("categories", supportService.getAllowedCategories());
            return "contact-support";
        }

        try {
            supportService.createTicket(ticket);

            if (attachment != null && !attachment.isEmpty()) {
                attachmentService.store(
                        attachment,
                        ticket,
                        loggedInUser == null ? null : loggedInUser.getId(),
                        ticket.getCustomerName()
                );
            }

            notificationService.notifySupport(
                    "New support ticket #" + ticket.getId(),
                    ticket.getCustomerName() + " (" + ticket.getEmail() + ") submitted: "
                            + ticket.getSubject(),
                    "/support/tickets"
            );
            notifyTicketByEmail(ticket,
                    "Support ticket #" + ticket.getId() + " received",
                    "We received your support request. Your ticket reference is #"
                            + ticket.getId() + ". You can follow updates under My Support Tickets.",
                    redirectAttributes);

            return "redirect:/contact-support?success=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("ticket", ticket);
            model.addAttribute("priorities", supportService.getAllowedPriorities());
            model.addAttribute("categories", supportService.getAllowedCategories());
            return "contact-support";
        }
    }


    // =====================================================
    // CUSTOMER - MY SUPPORT TICKETS
    // =====================================================

    @GetMapping("/my-support-tickets")
    public String mySupportTickets(Model model, HttpSession session) {

        User customer = (User) session.getAttribute("loggedInUser");
        String email = customer == null ? null : customer.getEmail();
        List<SupportTicket> tickets = email == null
                ? List.of() : supportService.getTicketsByCustomerEmail(email);

        // Conversation history for each of the customer's tickets
        Map<Long, List<TicketReply>> repliesByTicket = new LinkedHashMap<>();
        for (SupportTicket ticket : tickets) {
            repliesByTicket.put(ticket.getId(), supportService.getRepliesForTicket(ticket.getId()));
        }

        model.addAttribute("tickets", tickets);
        model.addAttribute("repliesByTicket", repliesByTicket);

        return "my-support-tickets";
    }


    // =====================================================
    // CUSTOMER - REPLY TO OWN TICKET (OWNERSHIP CHECKED)
    // =====================================================

    @PostMapping("/my-support-tickets/reply")
    public String replyToOwnTicket(
            @RequestParam Long ticketId,
            @RequestParam String message,
            HttpSession session,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) throws IOException {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        SupportTicket ticket = supportService.getTicketById(ticketId);

        if (!isTicketOwner(loggedInUser, ticket)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: this ticket does not belong to you");
            return null;
        }

        try {
            supportService.addReply(
                    ticketId,
                    message,
                    loggedInUser == null ? null : loggedInUser.getId(),
                    loggedInUser == null ? "Customer" : loggedInUser.getName(),
                    "CUSTOMER"
            );

            notificationService.notifySupport(
                    "Customer replied to ticket #" + ticketId,
                    "Customer " + ticket.getCustomerName() + " (" + ticket.getEmail()
                            + ") replied to ticket #" + ticketId + ": " + message,
                    "/support/tickets/view?id=" + ticketId
            );

            redirectAttributes.addFlashAttribute(
                    "success",
                    "Reply added to your ticket."
            );
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/my-support-tickets";
    }


    // =====================================================
    // CUSTOMER - REOPEN OWN RESOLVED TICKET
    // =====================================================

    @PostMapping("/my-support-tickets/reopen")
    public String reopenOwnTicket(
            @RequestParam Long ticketId,
            HttpSession session,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) throws IOException {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        SupportTicket ticket = supportService.getTicketById(ticketId);

        if (!isTicketOwner(loggedInUser, ticket)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: this ticket does not belong to you");
            return null;
        }

        try {
            boolean reopened = supportService.reopenTicket(ticketId);

            if (reopened) {
                notificationService.notifySupport(
                        "Ticket #" + ticketId + " reopened by customer",
                        "Customer " + ticket.getCustomerName() + " (" + ticket.getEmail()
                                + ") reopened ticket #" + ticketId + ": " + ticket.getSubject(),
                        "/support/tickets/view?id=" + ticketId
                );

                redirectAttributes.addFlashAttribute(
                        "success",
                        "Ticket reopened. Support staff have been notified."
                );
            } else {
                redirectAttributes.addFlashAttribute(
                        "error",
                        "Ticket could not be reopened."
                );
            }
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/my-support-tickets";
    }


    // =====================================================
    // CUSTOMER - RATE RESOLVED TICKET (OWNERSHIP CHECKED)
    // =====================================================

    @PostMapping("/my-support-tickets/rate")
    public String rateTicket(
            @RequestParam Long ticketId,
            @RequestParam Integer rating,
            @RequestParam(required = false) String comment,
            HttpSession session,
            HttpServletResponse response,
            RedirectAttributes redirectAttributes) throws IOException {

        User loggedInUser = (User) session.getAttribute("loggedInUser");
        SupportTicket ticket = supportService.getTicketById(ticketId);

        if (!isTicketOwner(loggedInUser, ticket)) {
            response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    "Access denied: this ticket does not belong to you");
            return null;
        }

        try {
            supportService.rateTicket(ticketId, rating, comment);
            redirectAttributes.addFlashAttribute(
                    "success",
                    "Thank you for your feedback."
            );
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/my-support-tickets";
    }


    // =====================================================
    // HELPERS
    // =====================================================

    private boolean isTicketOwner(User user, SupportTicket ticket) {
        return user != null
                && ticket != null
                && user.getEmail() != null
                && ticket.getEmail() != null
                && user.getEmail().equalsIgnoreCase(ticket.getEmail());
    }

    private Long currentUserId(HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        return user == null ? null : user.getId();
    }

    private String currentUserName(HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        if (user != null && user.getName() != null && !user.getName().isBlank()) {
            return user.getName();
        }
        return "Support Staff";
    }

    private String currentUserRole(HttpSession session) {
        User user = (User) session.getAttribute("loggedInUser");
        return user == null || user.getRole() == null ? "SUPPORT" : user.getRole();
    }

    private void populateTicketDetailsModel(Model model, SupportTicket ticket) {
        Long id = ticket.getId();
        model.addAttribute("ticket", ticket);
        model.addAttribute("replies", supportService.getRepliesForTicket(id));
        model.addAttribute("attachments", attachmentService.getAttachmentsForTicket(id));
        model.addAttribute("priorities", supportService.getAllowedPriorities());
        model.addAttribute("categories", supportService.getAllowedCategories());
        model.addAttribute("staffMembers", supportService.getActiveSupportStaff());
        model.addAttribute("activities", supportService.getActivitiesForTicket(id));
        model.addAttribute("internalNotes", supportService.getInternalNotesForTicket(id));
        model.addAttribute("faqs", supportService.getFaqs());
        model.addAttribute("quickReplies", supportService.getQuickReplies());
        Booking booking = supportService.getVerifiedBookingForTicket(id);
        Payment payment = supportService.getLatestPaymentForTicket(id);
        model.addAttribute("linkedBooking", booking);
        model.addAttribute("linkedPayment", payment);
        model.addAttribute(
                "refundStatuses",
                supportService.getAllowedRefundTrackingTransitions(ticket.getRefundTrackingStatus()));
    }

    private void notifyTicketByEmail(
            SupportTicket ticket, String subject, String message,
            RedirectAttributes redirectAttributes) {
        try {
            emailNotificationService.sendTicketUpdate(ticket.getEmail(), subject, message);
        } catch (MailException e) {
            LOGGER.warn("Could not send support email for ticket {}", ticket.getId(), e);
            redirectAttributes.addFlashAttribute(
                    "emailWarning",
                    "The ticket was saved, but its email notification could not be sent.");
        }
    }
}
