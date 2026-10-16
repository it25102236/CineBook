package com.moviebooking.moviebookingsystem.controller;

import com.moviebooking.moviebookingsystem.model.Promotion;
import com.moviebooking.moviebookingsystem.service.MarketingService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class MarketingController {

    private final MarketingService marketingService;

    public MarketingController(
            MarketingService marketingService) {

        this.marketingService = marketingService;
    }

    // ==========================================
    // MARKETING DASHBOARD
    // ==========================================

    @GetMapping("/marketing")
    public String dashboard(Model model) {

        model.addAttribute(
                "promotions",
                marketingService.getAllPromotions()
        );

        model.addAttribute(
                "promotionCount",
                marketingService.getAllPromotions().size()
        );

        long activeCount =
                marketingService.getAllPromotions()
                        .stream()
                        .filter(promotion ->
                                "ACTIVE".equals(
                                        promotion.getStatus()
                                ))
                        .count();

        long inactiveCount =
                marketingService.getAllPromotions()
                        .stream()
                        .filter(promotion ->
                                "INACTIVE".equals(
                                        promotion.getStatus()
                                ))
                        .count();

        model.addAttribute(
                "activeCount",
                activeCount
        );

        model.addAttribute(
                "inactiveCount",
                inactiveCount
        );

        return "marketing/dashboard";
    }


    // ==========================================
    // VIEW ALL PROMOTIONS
    // ==========================================

    @GetMapping("/marketing/promotions")
    public String promotions(Model model) {

        model.addAttribute(
                "promotions",
                marketingService.getAllPromotions()
        );

        return "marketing/promotions";
    }

    // ==========================================
    // VIEW ALL CAMPAIGNS
    // ==========================================

    @GetMapping("/marketing/campaigns")
    public String campaigns(Model model) {

        model.addAttribute(
                "promotions",
                marketingService.getAllPromotions()
        );

        return "marketing/promotions";
    }


    // ==========================================
    // ADD PROMOTION PAGE
    // ==========================================

    @GetMapping("/marketing/promotions/add")
    public String addPromotionPage(Model model) {

        model.addAttribute(
                "promotion",
                new Promotion()
        );

        return "marketing/add-promotion";
    }


    // ==========================================
    // SAVE PROMOTION
    // ==========================================

    @PostMapping("/marketing/promotions/add")
    public String addPromotion(
            @ModelAttribute Promotion promotion,
            Model model) {

        try {
            marketingService.addPromotion(promotion);
            return "redirect:/marketing/promotions";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("promotion", promotion);
            return "marketing/add-promotion";
        }
    }


    // ==========================================
    // EDIT PROMOTION PAGE
    // ==========================================

    @GetMapping("/marketing/promotions/edit")
    public String editPromotionPage(
            @RequestParam Long id,
            Model model) {

        Promotion promotion =
                marketingService.getPromotionById(id);

        if (promotion == null) {
            return "redirect:/marketing/promotions";
        }

        model.addAttribute(
                "promotion",
                promotion
        );

        return "marketing/edit-promotion";
    }


    // ==========================================
    // UPDATE PROMOTION
    // ==========================================

    @PostMapping("/marketing/promotions/edit")
    public String updatePromotion(
            @ModelAttribute Promotion promotion,
            Model model) {

        try {
            marketingService.updatePromotion(promotion);
            return "redirect:/marketing/promotions";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("promotion", promotion);
            return "marketing/edit-promotion";
        }
    }


    // ==========================================
    // DELETE PROMOTION
    // ==========================================

    @PostMapping("/marketing/promotions/delete")
    public String deletePromotion(
            @RequestParam Long id) {

        marketingService.deletePromotion(id);

        return "redirect:/marketing/promotions";
    }
}