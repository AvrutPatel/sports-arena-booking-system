package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "notifications")
@Data
@NoArgsConstructor
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String userEmail;

    @Column(length = 1000)
    private String message;

    private boolean isRead = false;

    private LocalDateTime createdAt = LocalDateTime.now();

    // Custom constructor for easy saving later
    public Notification(String userEmail, String message) {
        this.userEmail = userEmail;
        this.message = message;
    }
}