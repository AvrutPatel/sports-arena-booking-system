package com.sportsarena.venue_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "courts")
@Data
@NoArgsConstructor
public class Court {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Many courts belong to one venue
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    private String name;

    private String sportType; // e.g., BADMINTON, TENNIS, FOOTBALL

    private double hourlyRate;

    private boolean isActive = true;
}