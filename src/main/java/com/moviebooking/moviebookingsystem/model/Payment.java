package com.moviebooking.moviebookingsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long bookingId;
    private double amount;
    private String paymentMethod;
    private String status;

    private Integer refundPercent;

    private Double refundAmount;

    // Constructor
    public Payment() {
    }

    // Get ID
    public Long getId() {
        return id;
    }

    // Set ID
    public void setId(Long id) {
        this.id = id;
    }

    // Get Booking ID
    public Long getBookingId() {
        return bookingId;
    }

    // Set Booking ID
    public void setBookingId(Long bookingId) {
        this.bookingId = bookingId;
    }

    // Get Amount
    public double getAmount() {
        return amount;
    }

    // Set Amount
    public void setAmount(double amount) {
        this.amount = amount;
    }

    // Get Payment Method
    public String getPaymentMethod() {
        return paymentMethod;
    }

    // Set Payment Method
    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    // Get Status
    public String getStatus() {
        return status;
    }

    // Set Status
    public void setStatus(String status) {
        this.status = status;
    }

    public int getRefundPercent() {
        return refundPercent == null ? 0 : refundPercent;
    }

    public void setRefundPercent(int refundPercent) {
        this.refundPercent = refundPercent;
    }

    public double getRefundAmount() {
        return refundAmount == null ? 0 : refundAmount;
    }

    public void setRefundAmount(double refundAmount) {
        this.refundAmount = refundAmount;
    }
}
