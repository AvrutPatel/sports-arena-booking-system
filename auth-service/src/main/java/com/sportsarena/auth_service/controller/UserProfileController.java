package com.sportsarena.auth_service.controller;

import com.sportsarena.auth_service.dto.UserProfileDto;
import com.sportsarena.auth_service.entity.UserProfile;
import com.sportsarena.auth_service.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/profile")
public class UserProfileController {

    @Autowired
    private UserProfileService profileService;

    // Helper method to extract the logged-in user's email from the JWT token
    private String getCurrentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping
    public ResponseEntity<UserProfile> getMyProfile() {
        return ResponseEntity.ok(profileService.getProfile(getCurrentUserEmail()));
    }

    @PutMapping
    public ResponseEntity<UserProfile> updateMyProfile(@RequestBody UserProfileDto dto) {
        return ResponseEntity.ok(profileService.updateProfile(getCurrentUserEmail(), dto));
    }

    @PostMapping("/wallet/add")
    public ResponseEntity<UserProfile> addFundsToWallet(@RequestParam Double amount) {
        return ResponseEntity.ok(profileService.addFunds(getCurrentUserEmail(), amount));
    }

    @PostMapping("/wallet/deduct")
    public ResponseEntity<?> deductFundsFromWallet(@RequestParam Double amount) {
        try {
            return ResponseEntity.ok(profileService.deductFunds(getCurrentUserEmail(), amount));
        } catch (RuntimeException e) {
            // Returns a 400 Bad Request if they don't have enough money
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    // Allows the booking-service to target a specific user for a refund
    @PostMapping("/wallet/refund")
    public ResponseEntity<UserProfile> refundSpecificUserWallet(
            @RequestParam String targetEmail,
            @RequestParam Double amount) {
        return ResponseEntity.ok(profileService.addFunds(targetEmail, amount));
    }
}