package com.sportsarena.booking_service.service;

import org.junit.jupiter.api.*;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpServerErrorException;
import java.math.BigDecimal;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class WalletClientTests {
    private MockRestServiceServer server;
    private WalletClient wallet;
    private RecoveryWork work;
    private static final String KEY = "test-service-wallet-key-at-least-32-bytes";
    @BeforeEach void setup() {
        RestTemplate rest = new RestTemplate();
        server = MockRestServiceServer.createServer(rest);
        wallet = new WalletClient(rest, "http://auth/internal/wallet", KEY);
        UUID id = UUID.randomUUID();
        work = new RecoveryWork(id, UUID.randomUUID(), "PAYMENT", id, "player@example.com", new BigDecimal("100.00"), true);
    }
    @AfterEach void verifyServer() { server.verify(); }

    @Test void retryUsesSameReferenceWithoutRequestContextOrCustomerJwt() {
        for (int i = 0; i < 2; i++) {
            server.expect(requestTo("http://auth/internal/wallet/settle-debit"))
                    .andExpect(method(HttpMethod.POST)).andExpect(header("X-Wallet-Service-Key", KEY))
                    .andExpect(headerDoesNotExist("Authorization"))
                    .andExpect(jsonPath("$.operationId").value(work.operationId().toString()))
                    .andRespond(withSuccess("{\"operationId\":\"" + work.operationId() + "\",\"status\":\"APPLIED\"}", MediaType.APPLICATION_JSON));
        }
        assertEquals("APPLIED", wallet.settleDebit(work));
        assertEquals("APPLIED", wallet.settleDebit(work));
    }

    @Test void expiredAttemptRequestsTerminalVoidInsteadOfNewDebit() {
        RecoveryWork expired = new RecoveryWork(work.bookingId(), work.leaseToken(), "PAYMENT", work.operationId(),
                work.email(), work.amount(), false);
        server.expect(requestTo("http://auth/internal/wallet/settle-debit"))
                .andExpect(jsonPath("$.allowDebit").value(false))
                .andRespond(withSuccess("{\"operationId\":\"" + work.operationId() + "\",\"status\":\"VOIDED\"}", MediaType.APPLICATION_JSON));
        assertEquals("VOIDED", wallet.settleDebit(expired));
    }

    @Test void refundFailurePropagatesForDurableRetry() {
        server.expect(requestTo("http://auth/internal/wallet/refund"))
                .andExpect(jsonPath("$.debitReference").value(work.bookingId().toString()))
                .andExpect(header("X-Wallet-Service-Key", KEY)).andRespond(withServerError());
        assertThrows(HttpServerErrorException.class, () -> wallet.refund(work));
    }

    @Test void missingCredentialDoesNotSendPayment() {
        WalletClient unconfigured = new WalletClient(new RestTemplate(), "http://auth/internal/wallet", "");
        assertThrows(IllegalStateException.class, () -> unconfigured.settleDebit(work));
    }
}
