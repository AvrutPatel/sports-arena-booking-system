package com.sportsarena.auth_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data // Generates Getters, Setters, toString, equals, and hashCode
@NoArgsConstructor // Generates an empty constructor required by JPA
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String fullName;
    private String email;
    private String passwordHash;
    private String role;
}