package com.sportsarena.auth_service.security;

import com.sportsarena.auth_service.controller.AuthController;
import com.sportsarena.auth_service.controller.TestController;
import com.sportsarena.auth_service.controller.UserProfileController;
import com.sportsarena.auth_service.controller.InternalWalletController;
import com.sportsarena.auth_service.entity.UserProfile;
import com.sportsarena.auth_service.repository.UserRepository;
import com.sportsarena.auth_service.service.UserProfileService;
import com.sportsarena.auth_service.service.UserService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.math.BigDecimal;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({AuthController.class, UserProfileController.class, TestController.class, InternalWalletController.class})
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtUtil.class})
@TestPropertySource(properties = {
        "jwt.secret=authentication-boundary-test-key-at-least-32-bytes",
        "jwt.expiration=60000",
        "wallet.service.key=test-wallet-service-key-at-least-32-bytes",
        "debug=false",
        "logging.level.org.springframework=WARN"
})
class AuthenticationBoundaryTests {

    @Test
    void internalWalletCannotBeInvokedWithCustomerJwtAlone() throws Exception {
        mvc.perform(post("/internal/wallet/settle-debit").header(HttpHeaders.AUTHORIZATION, bearer("PLAYER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profileService);
    }

    @Test
    void workerCanAuthenticateWithoutCustomerJwt() throws Exception {
        UUID id = UUID.randomUUID();
        when(profileService.settleDebit(EMAIL, new BigDecimal("10.00"), id, false))
                .thenReturn(new UserProfileService.DebitDecision(id, "VOIDED"));
        mvc.perform(post("/internal/wallet/settle-debit")
                        .header("X-Wallet-Service-Key", "test-wallet-service-key-at-least-32-bytes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"operationId\":\"" + id + "\",\"email\":\"" + EMAIL + "\",\"amount\":10.00,\"allowDebit\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("VOIDED"));
    }

    private static final String EMAIL = "player@example.com";
    private static final String SECRET = "authentication-boundary-test-key-at-least-32-bytes";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private UserProfileService profileService;

    @ParameterizedTest
    @CsvSource({
            "GET,/api/auth/profile",
            "PUT,/api/auth/profile",
            "POST,/api/auth/profile/wallet/add",
            "POST,/api/auth/profile/wallet/deduct",
            "POST,/api/auth/profile/wallet/refund",
            "GET,/api/test/protected",
            "GET,/api/auth/future-endpoint",
            "GET,/api/auth/login",
            "GET,/api/auth/register"
    })
    void anonymousRequestsCannotReachProtectedRoutes(String method, String path) throws Exception {
        mvc.perform(request(HttpMethod.valueOf(method), path)
                        .contentType(MediaType.APPLICATION_JSON).content("{}")
                        .param("amount", "10").param("targetEmail", EMAIL))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(profileService, userService, userRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/login", "/api/auth/register"})
    void publicPostRoutesReachRequestValidationWithoutAuthentication(String path) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void validTokenAccessesOnlyItsOwnProfileWithoutCreatingSession() throws Exception {
        UserProfile profile = new UserProfile();
        profile.setEmail(EMAIL);
        when(profileService.getProfile(EMAIL)).thenReturn(profile);

        mvc.perform(get("/api/auth/profile").header(HttpHeaders.AUTHORIZATION, bearer("PLAYER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(result -> org.junit.jupiter.api.Assertions.assertNull(
                        result.getRequest().getSession(false)));
        verify(profileService).getProfile(EMAIL);

        // A preceding authenticated request must not authenticate the next request.
        mvc.perform(get("/api/auth/profile")).andExpect(status().isUnauthorized());
    }

    @Test
    void trustedBookingWalletCallRequiresServiceCredentialAndCustomerToken() throws Exception {
        UUID operationId = UUID.randomUUID();
        when(profileService.deductFunds(EMAIL, new BigDecimal("10"), operationId)).thenReturn(new UserProfile());

        mvc.perform(post("/api/auth/profile/wallet/deduct").param("amount", "10")
                        .header("Idempotency-Key", operationId)
                        .header("X-Wallet-Service-Key", "test-wallet-service-key-at-least-32-bytes")
                        .header(HttpHeaders.AUTHORIZATION, bearer("PLAYER")))
                .andExpect(status().isOk());
        verify(profileService).deductFunds(EMAIL, new BigDecimal("10"), operationId);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/auth/profile/wallet/deduct", "/api/auth/profile/wallet/refund"})
    void customerTokenAloneCannotInvokeInternalWalletRoutes(String path) throws Exception {
        mvc.perform(post(path).param("amount", "10").header(HttpHeaders.AUTHORIZATION, bearer("PLAYER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(profileService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"NaN", "Infinity", "-Infinity", "not-money"})
    void nonDecimalTopUpAmountsReturnBadRequest(String amount) throws Exception {
        mvc.perform(post("/api/auth/profile/wallet/add").param("amount", amount)
                        .header("Idempotency-Key", UUID.randomUUID())
                        .header(HttpHeaders.AUTHORIZATION, bearer("PLAYER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profileService);
    }

    @Test
    void topUpRequiresOperationReference() throws Exception {
        mvc.perform(post("/api/auth/profile/wallet/add").param("amount", "10")
                        .header(HttpHeaders.AUTHORIZATION, bearer("PLAYER")))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(profileService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Bearer invalid-token", "Bearer ", "Basic invalid"})
    void invalidCredentialsCannotAccessProfile(String authorization) throws Exception {
        mvc.perform(get("/api/auth/profile").header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profileService);
    }

    @Test
    void expiredTokenCannotAccessProfile() throws Exception {
        assertRejectedToken(signedToken(SECRET, EMAIL, "PLAYER", -60000));
    }

    @Test
    void tokenWithWrongSignatureCannotAccessProfile() throws Exception {
        assertRejectedToken(signedToken("different-test-signing-key-at-least-32-bytes", EMAIL, "PLAYER", 60000));
    }

    @Test
    void tokenWithoutRoleCannotAccessProfile() throws Exception {
        assertRejectedToken(signedToken(SECRET, EMAIL, null, 60000));
    }

    @Test
    void tokenWithoutSubjectCannotAccessProfile() throws Exception {
        assertRejectedToken(signedToken(SECRET, null, "PLAYER", 60000));
    }

    @Test
    void authenticatedPlayerCannotAccessOwnerRoute() throws Exception {
        mvc.perform(get("/api/test/owner-dashboard").header(HttpHeaders.AUTHORIZATION, bearer("PLAYER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void authenticatedOwnerCanAccessOwnerRoute() throws Exception {
        mvc.perform(get("/api/test/owner-dashboard").header(HttpHeaders.AUTHORIZATION, bearer("ACADEMY_OWNER")))
                .andExpect(status().isOk());
    }

    @Test
    void preflightDoesNotRequireAuthentication() throws Exception {
        mvc.perform(options("/api/auth/profile"))
                .andExpect(status().isOk());
        verifyNoInteractions(profileService);
    }

    private String bearer(String role) {
        return "Bearer " + jwtUtil.generateToken(EMAIL, role);
    }

    private void assertRejectedToken(String token) throws Exception {
        mvc.perform(get("/api/auth/profile").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(profileService);
    }

    private String signedToken(String secret, String subject, String role, long lifetimeMillis) {
        return Jwts.builder().setSubject(subject).claim("role", role)
                .setExpiration(new Date(System.currentTimeMillis() + lifetimeMillis))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256)
                .compact();
    }
}
