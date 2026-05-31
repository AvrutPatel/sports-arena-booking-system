package com.sportsarena.auth_service.service;

import com.sportsarena.auth_service.dto.UserProfileDto;
import com.sportsarena.auth_service.entity.UserProfile;
import com.sportsarena.auth_service.repository.UserProfileRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    @Autowired
    private UserProfileRepository profileRepository;

    // Fetch the profile. If it doesn't exist, create an empty one instantly!
    public UserProfile getProfile(String email) {
        return profileRepository.findByEmail(email).orElseGet(() -> {
            UserProfile newProfile = new UserProfile();
            newProfile.setEmail(email);
            newProfile.setWalletBalance(0.0); // Start with an empty wallet
            return profileRepository.save(newProfile);
        });
    }

    // Update the profile fields that the user chose to fill out
    public UserProfile updateProfile(String email, UserProfileDto dto) {
        UserProfile profile = getProfile(email);

        if (dto.getFullName() != null) profile.setFullName(dto.getFullName());
        if (dto.getAvatar() != null) profile.setAvatar(dto.getAvatar());
        if (dto.getInterestedSports() != null) profile.setInterestedSports(dto.getInterestedSports());
        if (dto.getFavoriteVenueIds() != null) profile.setFavoriteVenueIds(dto.getFavoriteVenueIds());

        return profileRepository.save(profile);
    }

    // Securely add funds to the wallet
    public UserProfile addFunds(String email, Double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }

        UserProfile profile = getProfile(email);
        profile.setWalletBalance(profile.getWalletBalance() + amount);

        return profileRepository.save(profile);
    }

    // Deduct funds and throw an error if the balance is too low
    public UserProfile deductFunds(String email, Double amount) {
        UserProfile profile = getProfile(email);

        if (profile.getWalletBalance() < amount) {
            throw new RuntimeException("Insufficient wallet balance. Please add funds.");
        }

        profile.setWalletBalance(profile.getWalletBalance() - amount);
        return profileRepository.save(profile);
    }
}