package com.sportsarena.venue_service.controller;

import com.sportsarena.venue_service.entity.Review;
import com.sportsarena.venue_service.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @PostMapping
    public ResponseEntity<String> submitReview(@RequestBody Review review) {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        review.setUserEmail(email);
        reviewRepository.save(review);
        return ResponseEntity.ok("Review submitted successfully");
    }

    @GetMapping("/venue/{venueId}")
    public ResponseEntity<List<Review>> getVenueReviews(@PathVariable Long venueId) {
        return ResponseEntity.ok(reviewRepository.findByVenueIdOrderByCreatedAtDesc(venueId));
    }
}