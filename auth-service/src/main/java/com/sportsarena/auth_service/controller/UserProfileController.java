package com.sportsarena.auth_service.controller;

import com.sportsarena.auth_service.dto.UserProfileDto;
import com.sportsarena.auth_service.entity.UserProfile;
import com.sportsarena.auth_service.service.UserProfileService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

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
    public ResponseEntity<UserProfile> addFundsToWallet(@RequestParam BigDecimal amount,
            @RequestHeader("Idempotency-Key") UUID operationId) {
        return ResponseEntity.ok(profileService.addFunds(getCurrentUserEmail(), amount, operationId));
    }

    @PostMapping("/wallet/deduct")
    public ResponseEntity<UserProfile> deductFundsFromWallet(@RequestParam BigDecimal amount,
            @RequestHeader("Idempotency-Key") UUID operationId) {
        return ResponseEntity.ok(profileService.deductFunds(getCurrentUserEmail(), amount, operationId));
    }

    // Allows the booking-service to target a specific user for a refund
    @PostMapping("/wallet/refund")
    public ResponseEntity<UserProfile> refundSpecificUserWallet(
            @RequestParam UUID debitReference,
            @RequestParam BigDecimal amount,
            @RequestHeader("Idempotency-Key") UUID operationId) {
        return ResponseEntity.ok(profileService.refundFunds(debitReference, amount, operationId));
    }
}
