package com.moviebooking.moviebookingsystem.dto;

public class ShowtimeAssignmentDto {

    private Long id;
    private Long movieId;
    private String movieTitle;
    private String theatre;
    private String date;
    private String showTime;
    private String endTime;
    private int totalSeats;
    private double ticketPrice;

    private String newTheatreName;
    private String newTheatreLocation;
    private Integer newTheatreCapacity;

    public static final String NEW_THEATRE_SENTINEL = "NEW_THEATRE";

    public ShowtimeAssignmentDto() {
    }

    public ShowtimeAssignmentDto(Long id, Long movieId, String movieTitle, String theatre,
                                 String date, String showTime, int totalSeats, double ticketPrice) {
        this.id = id;
        this.movieId = movieId;
        this.movieTitle = movieTitle;
        this.theatre = theatre;
        this.date = date;
        this.showTime = showTime;
        this.totalSeats = totalSeats;
        this.ticketPrice = ticketPrice;
    }

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

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public int getTotalSeats() {
        return totalSeats;
    }

    public void setTotalSeats(int totalSeats) {
        this.totalSeats = totalSeats;
    }

    public double getTicketPrice() {
        return ticketPrice;
    }

    public void setTicketPrice(double ticketPrice) {
        this.ticketPrice = ticketPrice;
    }

    public String getNewTheatreName() {
        return newTheatreName;
    }

    public void setNewTheatreName(String newTheatreName) {
        this.newTheatreName = newTheatreName;
    }

    public String getNewTheatreLocation() {
        return newTheatreLocation;
    }

    public void setNewTheatreLocation(String newTheatreLocation) {
        this.newTheatreLocation = newTheatreLocation;
    }

    public Integer getNewTheatreCapacity() {
        return newTheatreCapacity;
    }

    public void setNewTheatreCapacity(Integer newTheatreCapacity) {
        this.newTheatreCapacity = newTheatreCapacity;
    }

    public boolean isNewTheatreSelected() {
        return NEW_THEATRE_SENTINEL.equals(this.theatre);
    }
}
