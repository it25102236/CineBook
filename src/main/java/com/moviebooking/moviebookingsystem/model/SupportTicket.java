package com.moviebooking.moviebookingsystem.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Entity
@Table(name = "support_tickets")
public class SupportTicket {

    public static final String PRIORITY_LOW = "LOW";
    public static final String PRIORITY_MEDIUM = "MEDIUM";
    public static final String PRIORITY_HIGH = "HIGH";
    public static final String PRIORITY_URGENT = "URGENT";

    public static final String CATEGORY_BOOKING = "BOOKING_ISSUE";
    public static final String CATEGORY_PAYMENT = "PAYMENT_ISSUE";
    public static final String CATEGORY_REFUND = "REFUND_REQUEST";
    public static final String CATEGORY_CANCELLATION = "CANCELLATION";
    public static final String CATEGORY_TECHNICAL = "TECHNICAL_ISSUE";
    public static final String CATEGORY_OTHER = "OTHER";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;
    private String email;
    private Long customerId;
    private String subject;
    @Column(length = 2000)
    private String message;
    private String status;
    @Column(length = 2000)
    private String response;
    private long createdAt;
    private long updatedAt;

    private String priority = PRIORITY_MEDIUM;
    private String category = CATEGORY_OTHER;
    private Long bookingId;
    private Long assignedStaffId;
    private String assignedStaffName;
    private long responseDeadlineAt;
    private long resolutionDeadlineAt;
    private String refundTrackingStatus = "NOT_REQUESTED";

    private boolean escalated;
    @Column(length = 2000)
    private String escalationReason;
    private Long escalatedById;
    private String escalatedBy;
    private long escalatedAt;

    private long firstResponseAt;
    private long resolvedAt;

    private Integer feedbackRating;
    @Column(length = 2000)
    private String feedbackComment;
    private long feedbackAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getBookingId() {
        return bookingId;
    }

    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    public Long getAssignedStaffId() {
        return assignedStaffId;
    }

    public void setAssignedStaffId(Long assignedStaffId) {
        this.assignedStaffId = assignedStaffId;
    }

    public String getAssignedStaffName() {
        return assignedStaffName;
    }

    public void setAssignedStaffName(String assignedStaffName) {
        this.assignedStaffName = assignedStaffName;
    }

    public long getResponseDeadlineAt() {
        return responseDeadlineAt;
    }

    public void setResponseDeadlineAt(long responseDeadlineAt) {
        this.responseDeadlineAt = responseDeadlineAt;
    }

    public long getResolutionDeadlineAt() {
        return resolutionDeadlineAt;
    }

    public void setResolutionDeadlineAt(long resolutionDeadlineAt) {
        this.resolutionDeadlineAt = resolutionDeadlineAt;
    }

    public String getRefundTrackingStatus() {
        return refundTrackingStatus;
    }

    public void setRefundTrackingStatus(String refundTrackingStatus) {
        this.refundTrackingStatus = refundTrackingStatus;
    }

    public boolean isEscalated() {
        return escalated;
    }

    public void setEscalated(boolean escalated) {
        this.escalated = escalated;
    }

    public String getEscalationReason() {
        return escalationReason;
    }

    public void setEscalationReason(String escalationReason) {
        this.escalationReason = escalationReason;
    }

    public Long getEscalatedById() {
        return escalatedById;
    }

    public void setEscalatedById(Long escalatedById) {
        this.escalatedById = escalatedById;
    }

    public String getEscalatedBy() {
        return escalatedBy;
    }

    public void setEscalatedBy(String escalatedBy) {
        this.escalatedBy = escalatedBy;
    }

    public long getEscalatedAt() {
        return escalatedAt;
    }

    public void setEscalatedAt(long escalatedAt) {
        this.escalatedAt = escalatedAt;
    }

    public long getFirstResponseAt() {
        return firstResponseAt;
    }

    public void setFirstResponseAt(long firstResponseAt) {
        this.firstResponseAt = firstResponseAt;
    }

    public long getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(long resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Integer getFeedbackRating() {
        return feedbackRating;
    }

    public void setFeedbackRating(Integer feedbackRating) {
        this.feedbackRating = feedbackRating;
    }

    public String getFeedbackComment() {
        return feedbackComment;
    }

    public void setFeedbackComment(String feedbackComment) {
        this.feedbackComment = feedbackComment;
    }

    public long getFeedbackAt() {
        return feedbackAt;
    }

    public void setFeedbackAt(long feedbackAt) {
        this.feedbackAt = feedbackAt;
    }

    public String getCreatedAtDisplay() {
        return formatTimestamp(createdAt);
    }

    public String getUpdatedAtDisplay() {
        return formatTimestamp(updatedAt);
    }

    public String getEscalatedAtDisplay() {
        return formatTimestamp(escalatedAt);
    }

    public String getFeedbackAtDisplay() {
        return formatTimestamp(feedbackAt);
    }

    public String getFirstResponseAtDisplay() {
        return formatTimestamp(firstResponseAt);
    }

    public String getResolvedAtDisplay() {
        return formatTimestamp(resolvedAt);
    }

    public String getResponseDeadlineDisplay() {
        return formatTimestamp(responseDeadlineAt);
    }

    public String getResolutionDeadlineDisplay() {
        return formatTimestamp(resolutionDeadlineAt);
    }

    public boolean isResponseSlaOverdue() {
        return firstResponseAt == 0L
                && responseDeadlineAt > 0L
                && System.currentTimeMillis() > responseDeadlineAt;
    }

    public boolean isResolutionSlaOverdue() {
        return !"RESOLVED".equalsIgnoreCase(status)
                && resolutionDeadlineAt > 0L
                && System.currentTimeMillis() > resolutionDeadlineAt;
    }

    public String getPriorityDisplay() {
        return titleCase(priority);
    }

    public String getCategoryDisplay() {
        if (category == null || category.isBlank()) {
            return "Other";
        }
        return titleCase(category.replace("_", " "));
    }

    private static String titleCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String lower = value.trim().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String formatTimestamp(long epochMillis) {
        if (epochMillis <= 0L) {
            return "-";
        }
        return Instant.ofEpochMilli(epochMillis)
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }
}
