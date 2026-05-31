package com.sportsarena.auth_service.repository;

import com.sportsarena.auth_service.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    // Finds a profile by the user's email
    Optional<UserProfile> findByEmail(String email);
}