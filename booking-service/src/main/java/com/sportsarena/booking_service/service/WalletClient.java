package com.sportsarena.booking_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@Service
public class WalletClient {
    private final RestTemplate rest;
    private final String baseUrl;
    private final String serviceKey;

    public WalletClient(RestTemplate rest,
            @Value("${auth.service.wallet.internal.url:http://AUTH-SERVICE/internal/wallet}") String baseUrl,
            @Value("${wallet.service.key:${WALLET_SERVICE_KEY:}}") String serviceKey) {
        this.rest = rest; this.baseUrl = baseUrl; this.serviceKey = serviceKey;
    }

    public record DebitRequest(UUID operationId, String email, BigDecimal amount, boolean allowDebit) {}
    public record DebitDecision(UUID operationId, String status) {}
    public record RefundRequest(UUID operationId, UUID debitReference, BigDecimal amount) {}

    public String settleDebit(RecoveryWork work) {
        DebitDecision decision = rest.exchange(uri("/settle-debit"), HttpMethod.POST,
                entity(new DebitRequest(work.operationId(), work.email(), work.amount(), work.allowDebit())),
                DebitDecision.class).getBody();
        if (decision == null || !work.operationId().equals(decision.operationId())
                || !List.of("APPLIED", "DECLINED", "VOIDED").contains(decision.status())) {
            throw new IllegalStateException("Invalid wallet decision.");
        }
        return decision.status();
    }

    public void refund(RecoveryWork work) {
        rest.exchange(uri("/refund"), HttpMethod.POST,
                entity(new RefundRequest(work.operationId(), work.bookingId(), work.amount())), Void.class);
    }

    private java.net.URI uri(String path) {
        return UriComponentsBuilder.fromUriString(baseUrl).path(path).build().encode().toUri();
    }

    private HttpEntity<Object> entity(Object body) {
        if (serviceKey.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("Configure WALLET_SERVICE_KEY in auth and booking services.");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Wallet-Service-Key", serviceKey);
        return new HttpEntity<>(body, headers);
    }
}
