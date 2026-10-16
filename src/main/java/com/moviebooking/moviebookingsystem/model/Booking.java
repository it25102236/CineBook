package com.moviebooking.moviebookingsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long movieId;

    private String movieTitle;

    private String theatre;

    private String date;

    private String showTime;

    private String seatNumber;

    private double price;

    // ================= SEAT LOCK =================

    private String status;

    private long lockExpiryTime;

    private String customerEmail;

    private Long createdAt;

    private Long cancelledAt;

    private Integer refundPercent;

    private Double refundAmount;

    @Transient
    private int refundEstimatePercent;

    @Transient
    private double refundEstimateAmount;


    // ================= GETTERS & SETTERS =================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }


    public Long getMovieId() {
        return movieId;
    }

    public void setMovieId(Long movieId) {
        this.movieId = movieId;
    }


    public String getMovieTitle() {
        return movieTitle;
    }

    public void setMovieTitle(String movieTitle) {
        this.movieTitle = movieTitle;
    }


    public String getTheatre() {
        return theatre;
    }

    public void setTheatre(String theatre) {
        this.theatre = theatre;
    }


    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }


    public String getShowTime() {
        return showTime;
    }

    public void setShowTime(String showTime) {
        this.showTime = showTime;
    }


    public String getSeatNumber() {
        return seatNumber;
    }

    public void setSeatNumber(String seatNumber) {
        this.seatNumber = seatNumber;
    }


    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }


    // ================= STATUS =================

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }


    // ================= LOCK EXPIRY =================

    public long getLockExpiryTime() {
        return lockExpiryTime;
    }

    public void setLockExpiryTime(long lockExpiryTime) {
        this.lockExpiryTime = lockExpiryTime;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public long getCreatedAt() {
        return createdAt == null ? 0 : createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    public long getCancelledAt() {
        return cancelledAt == null ? 0 : cancelledAt;
    }

    public void setCancelledAt(long cancelledAt) {
        this.cancelledAt = cancelledAt;
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

    public int getRefundEstimatePercent() {
        return refundEstimatePercent;
    }

    public void setRefundEstimatePercent(int refundEstimatePercent) {
        this.refundEstimatePercent = refundEstimatePercent;
    }

    public double getRefundEstimateAmount() {
        return refundEstimateAmount;
    }

    public void setRefundEstimateAmount(double refundEstimateAmount) {
        this.refundEstimateAmount = refundEstimateAmount;
    }
}
