package com.sportsarena.auth_service.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/protected")
    public ResponseEntity<String> etProtectedData() {
        return ResponseEntity.ok("Congratulations! You have unlocked this endpoint using a valid JWT Token.");
    }

    @GetMapping("/owner-dashboard")
    @PreAuthorize("hasRole('ACADEMY_OWNER')")
    public ResponseEntity<String> getOwnerData() {
        return ResponseEntity.ok("Welcome to the Academy Owner Dashboard. You have admin privileges!");
    }
}