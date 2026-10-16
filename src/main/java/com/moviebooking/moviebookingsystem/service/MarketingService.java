package com.moviebooking.moviebookingsystem.service;

import com.moviebooking.moviebookingsystem.model.Promotion;
import com.moviebooking.moviebookingsystem.repository.PromotionRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

@Service
public class MarketingService {

    private final PromotionRepository promotionRepository;

    public MarketingService(PromotionRepository promotionRepository) {

        this.promotionRepository = promotionRepository;
    }

    // Helper method to validate dates
    private void validateDates(String startDateStr, String endDateStr) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDate startDate = LocalDate.parse(startDateStr, formatter);
        LocalDate endDate = LocalDate.parse(endDateStr, formatter);
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date.");
        }
    }

    // ==========================================
    // GET ALL PROMOTIONS
    // ==========================================

    public List<Promotion> getAllPromotions() {
        return promotionRepository.findAll();
    }

    // ==========================================
    // FIND PROMOTION BY ID
    // ==========================================

    public Promotion getPromotionById(Long id) {

        if (id == null) {
            return null;
        }

        return promotionRepository.findById(id).orElse(null);
    }

    // ==========================================
    // ADD PROMOTION
    // ==========================================

    public void addPromotion(Promotion promotion) {

        if (promotion.getStatus() == null
                || promotion.getStatus().isEmpty()) {

            promotion.setStatus("ACTIVE");
        }

        // Validate dates
        validateDates(promotion.getStartDate(), promotion.getEndDate());

        promotionRepository.save(promotion);
    }

    // ==========================================
    // UPDATE PROMOTION (fetch existing, merge, save)
    // ==========================================

    public boolean updatePromotion(Promotion updatedPromotion) {

        if (updatedPromotion == null || updatedPromotion.getId() == null) {
            return false;
        }

        Optional<Promotion> existingOpt = promotionRepository.findById(updatedPromotion.getId());
        if (existingOpt.isEmpty()) {
            return false;
        }

        Promotion existing = existingOpt.get();

        // Validate dates
        validateDates(updatedPromotion.getStartDate(), updatedPromotion.getEndDate());

        existing.setTitle(updatedPromotion.getTitle());
        existing.setDescription(updatedPromotion.getDescription());
        existing.setStartDate(updatedPromotion.getStartDate());
        existing.setEndDate(updatedPromotion.getEndDate());
        existing.setStatus(updatedPromotion.getStatus());

        promotionRepository.save(existing);

        return true;
    }

    // ==========================================
    // DELETE PROMOTION
    // ==========================================

    public boolean deletePromotion(Long id) {

        if (id == null || !promotionRepository.existsById(id)) {
            return false;
        }

        promotionRepository.deleteById(id);

        return true;
    }
}
