package com.moviebooking.moviebookingsystem.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "email"),
                @UniqueConstraint(columnNames = "username")
        }
)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String email;
    private String telephone;
    private String dateOfBirth;
    private String username;
    private String password;
    private String role;
    private String status;


    // ==============================
    // GET ID
    // ==============================

    public Long getId() {
        return id;
    }


    // ==============================
    // SET ID
    // ==============================

    public void setId(Long id) {
        this.id = id;
    }


    // ==============================
    // GET NAME
    // ==============================

    public String getName() {
        return name;
    }


    // ==============================
    // SET NAME
    // ==============================

    public void setName(String name) {
        this.name = name;
    }


    // ==============================
    // GET EMAIL
    // ==============================

    public String getEmail() {
        return email;
    }


    // ==============================
    // SET EMAIL
    // ==============================

    public void setEmail(String email) {
        this.email = email;
    }


    // ==============================
    // GET TELEPHONE
    // ==============================

    public String getTelephone() {
        return telephone;
    }


    // ==============================
    // SET TELEPHONE
    // ==============================

    public void setTelephone(String telephone) {
        this.telephone = telephone;
    }


    // ==============================
    // GET DATE OF BIRTH
    // ==============================

    public String getDateOfBirth() {
        return dateOfBirth;
    }


    // ==============================
    // SET DATE OF BIRTH
    // ==============================

    public void setDateOfBirth(String dateOfBirth) {
        this.dateOfBirth = dateOfBirth;
    }


    // ==============================
    // GET USERNAME
    // ==============================

    public String getUsername() {
        return username;
    }


    // ==============================
    // SET USERNAME
    // ==============================

    public void setUsername(String username) {
        this.username = username;
    }


    // ==============================
    // GET PASSWORD
    // ==============================

    public String getPassword() {
        return password;
    }


    // ==============================
    // SET PASSWORD
    // ==============================

    public void setPassword(String password) {
        this.password = password;
    }


    // ==============================
    // GET ROLE
    // ==============================

    public String getRole() {
        return role;
    }


    // ==============================
    // SET ROLE
    // ==============================

    public void setRole(String role) {
        this.role = role;
    }


    // ==============================
    // GET STATUS
    // ==============================

    public String getStatus() {
        return status;
    }


    // ==============================
    // SET STATUS
    // ==============================

    public void setStatus(String status) {
        this.status = status;
    }
}
