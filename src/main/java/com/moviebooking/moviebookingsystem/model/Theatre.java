package com.moviebooking.moviebookingsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "theatres")
public class Theatre {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_MAINTENANCE = "MAINTENANCE";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String location;
    private int totalSeats;
    private String status;


    // =========================
    // GET ID
    // =========================

    public Long getId() {
        return id;
    }


    // =========================
    // SET ID
    // =========================

    public void setId(Long id) {
        this.id = id;
    }


    // =========================
    // GET NAME
    // =========================

    public String getName() {
        return name;
    }


    // =========================
    // SET NAME
    // =========================

    public void setName(String name) {
        this.name = name;
    }


    // =========================
    // GET LOCATION
    // =========================

    public String getLocation() {
        return location;
    }


    // =========================
    // SET LOCATION
    // =========================

    public void setLocation(String location) {
        this.location = location;
    }


    // =========================
    // GET TOTAL SEATS
    // =========================

    public int getTotalSeats() {
        return totalSeats;
    }


    // =========================
    // SET TOTAL SEATS
    // =========================

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }


    // =========================
    // GET STATUS
    // =========================

    /**
     * Theatres saved before the status column existed have no value stored,
     * so anything unknown is treated as AVAILABLE.
     */
    public String getStatus() {
        if (status == null || status.isBlank()) {
            return STATUS_AVAILABLE;
        }
        return status.trim().toUpperCase();
    }


    // =========================
    // SET STATUS
    // =========================

    public void setStatus(String status) {
        this.status = status;
    }


    // =========================
    // IS UNDER MAINTENANCE
    // =========================

    public boolean isUnderMaintenance() {
        return STATUS_MAINTENANCE.equals(getStatus());
    }
}
