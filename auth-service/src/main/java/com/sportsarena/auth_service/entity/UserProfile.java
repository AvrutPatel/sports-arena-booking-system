package com.sportsarena.auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "user_profiles")
@Data
@NoArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // We link the profile to the user via their unique email
    @Column(unique = true, nullable = false)
    private String email;

    private String fullName;

    // Will store a simple string like "avatar1.png" or an emoji
    private String avatar;

    // The Player's Digital Wallet
    @Column(nullable = false)
    private Double walletBalance = 0.0;

    // A list of strings (e.g., ["Badminton", "Football"])
    @ElementCollection
    @CollectionTable(name = "user_sports", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "sport_type")
    private List<String> interestedSports = new ArrayList<>();

    // A list of IDs linking to venues they like in the venue-service
    @ElementCollection
    @CollectionTable(name = "user_favorite_venues", joinColumns = @JoinColumn(name = "profile_id"))
    @Column(name = "venue_id")
    private List<Long> favoriteVenueIds = new ArrayList<>();
}