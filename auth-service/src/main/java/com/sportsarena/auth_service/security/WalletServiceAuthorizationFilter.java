package com.sportsarena.auth_service.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Internal workers authenticate by service key; legacy wallet routes also require a customer/owner JWT. */
public class WalletServiceAuthorizationFilter extends OncePerRequestFilter {
    private static final Set<String> INTERNAL_PATHS = Set.of(
            "/api/auth/profile/wallet/deduct", "/api/auth/profile/wallet/refund");
    private final byte[] serviceKey;

    public WalletServiceAuthorizationFilter(String serviceKey) {
        this.serviceKey = serviceKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        Authentication caller = SecurityContextHolder.getContext().getAuthentication();
        if (path.equals("/internal/wallet") || path.startsWith("/internal/wallet/")) {
            String supplied = request.getHeader("X-Wallet-Service-Key");
            if (serviceKey.length < 32 || supplied == null || !MessageDigest.isEqual(serviceKey,
                    supplied.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                    "booking-service", null, List.of(new SimpleGrantedAuthority("WALLET_SERVICE"))));
        }
        if (!"OPTIONS".equals(request.getMethod()) && INTERNAL_PATHS.contains(path)
                && caller != null && caller.isAuthenticated()) {
            String supplied = request.getHeader("X-Wallet-Service-Key");
            if (serviceKey.length < 32 || supplied == null || !MessageDigest.isEqual(serviceKey,
                    supplied.getBytes(StandardCharsets.UTF_8))) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                return;
            }
        }
        chain.doFilter(request, response);
    }
}
