package com.sportsarena.auth_service.repository;

import com.sportsarena.auth_service.entity.UserProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

public interface UserProfileRepository extends JpaRepository<UserProfile, Long> {

    // Finds a profile by the user's email
    Optional<UserProfile> findByEmail(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from UserProfile p where p.email = :email")
    Optional<UserProfile> findByEmailForUpdate(@Param("email") String email);

    @Modifying
    @Query(value = "INSERT INTO user_profiles (email, wallet_balance) VALUES (:email, 0.00) "
            + "ON CONFLICT (email) DO NOTHING", nativeQuery = true)
    void ensureProfile(@Param("email") String email);
}
