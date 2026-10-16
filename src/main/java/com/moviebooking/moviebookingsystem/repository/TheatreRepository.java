package com.moviebooking.moviebookingsystem.repository;

import com.moviebooking.moviebookingsystem.model.Theatre;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TheatreRepository extends JpaRepository<Theatre, Long> {

    Theatre findByName(String name);
}
