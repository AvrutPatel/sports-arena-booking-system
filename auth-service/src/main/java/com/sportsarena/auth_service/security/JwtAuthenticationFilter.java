package com.sportsarena.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. Get the Authorization header from the request
        String authHeader = request.getHeader("Authorization");
        String token = null;
        String email = null;

        // 2. The JWT token always starts with "Bearer "
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7); // Remove "Bearer " to get just the token
            try {
                email = jwtUtil.extractEmail(token);
            } catch (Exception e) {
                System.out.println("Error extracting email from token: " + e.getMessage());
            }
        }

        // 3. If we found an email and the user isn't authenticated yet, validate it
        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(token, email)) {

                // EXTRACT THE ROLE HERE
                String role = jwtUtil.extractRole(token);

                // Spring Security expects roles to have a "ROLE_" prefix by default
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority("ROLE_" + role);

                // Pass the authority into the authentication token
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(
                                email,
                                null,
                                java.util.Collections.singletonList(authority) // <-- Added authority here!
                        );

                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 4. Move on to the next filter in the chain
        filterChain.doFilter(request, response);
    }
}