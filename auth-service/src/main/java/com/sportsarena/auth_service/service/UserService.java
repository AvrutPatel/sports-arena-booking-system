package com.sportsarena.auth_service.service;

import com.sportsarena.auth_service.dto.UserRegistrationDto;
import com.sportsarena.auth_service.entity.User;
import com.sportsarena.auth_service.repository.UserRepository;
import org.modelmapper.ModelMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ModelMapper modelMapper;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public String registerUser(UserRegistrationDto dto) {
        // 1. Map DTO to Entity automatically
        User user = modelMapper.map(dto, User.class);

        // 2. Hash the plain text password from the DTO!
        String hashedPassword = passwordEncoder.encode(dto.getPasswordHash());
        user.setPasswordHash(hashedPassword);

        // 3. Set default values
        if (dto.getRole() != null && dto.getRole().equals("ACADEMY_OWNER")) {
            user.setRole("ACADEMY_OWNER");
        } else {
            user.setRole("PLAYER");
        }

        // 4. Save to database
        userRepository.save(user);

        return "User registered successfully with encrypted password!";
    }
}