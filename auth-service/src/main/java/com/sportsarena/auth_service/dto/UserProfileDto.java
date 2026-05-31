package com.sportsarena.auth_service.dto;

import lombok.Data;
import java.util.List;

@Data
public class UserProfileDto {
    private String fullName;
    private String avatar;
    private List<String> interestedSports;
    private List<Long> favoriteVenueIds;
}