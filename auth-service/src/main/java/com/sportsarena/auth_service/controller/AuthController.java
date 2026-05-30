package com.sportsarena.auth_service.controller;

import com.sportsarena.auth_service.dto.UserRegistrationDto;
import com.sportsarena.auth_service.repository.UserRepository;
import com.sportsarena.auth_service.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private com.sportsarena.auth_service.security.JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<String> registerUser(@Valid @RequestBody UserRegistrationDto userDto) {
        String response = userService.registerUser(userDto);
        return ResponseEntity.ok(response);
    }

    // Add this new endpoint
    @PostMapping("/login")
    public ResponseEntity<String> login(@Valid @RequestBody com.sportsarena.auth_service.dto.LoginDto loginDto) {

        com.sportsarena.auth_service.entity.User user = userRepository.findByEmail(loginDto.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found!"));

        if (!passwordEncoder.matches(loginDto.getPassword(), user.getPasswordHash())) {
            return ResponseEntity.status(401).body("Invalid credentials!");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());

        return ResponseEntity.ok(token);
    }
}