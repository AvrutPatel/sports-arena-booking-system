package com.sportsarena.auth_service.service;

import com.sportsarena.auth_service.repository.UserProfileRepository;
import com.sportsarena.auth_service.repository.WalletTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "debug=false", "logging.level.org.springframework=WARN"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserProfileService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Testcontainers(disabledWithoutDocker = true)
class WalletPostgresTests {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired UserProfileService wallet;
    @Autowired UserProfileRepository profiles;
    @Autowired WalletTransactionRepository ledger;
    @Autowired JdbcTemplate jdbc;
    private final String email = "concurrent@example.com";

    @BeforeEach
    void clearDatabase() {
        jdbc.update("DELETE FROM wallet_payment_attempts");
        jdbc.update("DELETE FROM wallet_transactions");
        jdbc.update("DELETE FROM user_profiles");
    }

    @Test
    void simultaneousDebitsCannotOverspend() throws Exception {
        wallet.addFunds(email, new BigDecimal("100.00"), UUID.randomUUID());
        List<Boolean> results = race(20, i -> wallet.deductFunds(email, BigDecimal.TEN, UUID.randomUUID()));
        assertEquals(10L, results.stream().filter(Boolean::booleanValue).count());
        assertBalance("0.00");
        assertEquals(11, ledger.count());
    }

    @Test
    void simultaneousDuplicateDebitsChargeOnce() throws Exception {
        wallet.addFunds(email, new BigDecimal("100.00"), UUID.randomUUID());
        UUID operationId = UUID.randomUUID();
        assertTrue(race(20, i -> wallet.deductFunds(email, BigDecimal.TEN, operationId)).stream().allMatch(Boolean::booleanValue));
        assertBalance("90.00");
        assertEquals(2, ledger.count());
    }

    @Test
    void simultaneousFirstTopUpsCreateOneProfileAndOneCredit() throws Exception {
        UUID operationId = UUID.randomUUID();
        assertTrue(race(20, i -> wallet.addFunds(email, BigDecimal.TEN, operationId)).stream().allMatch(Boolean::booleanValue));
        assertBalance("10.00");
        assertEquals(1, profiles.count());
        assertEquals(1, ledger.count());
    }

    @Test
    void simultaneousRefundsCannotExceedDebit() throws Exception {
        wallet.addFunds(email, new BigDecimal("100.00"), UUID.randomUUID());
        UUID debitId = UUID.randomUUID();
        wallet.deductFunds(email, new BigDecimal("100.00"), debitId);
        List<Boolean> results = race(20, i -> wallet.refundFunds(debitId, BigDecimal.TEN, UUID.randomUUID()));
        assertEquals(10L, results.stream().filter(Boolean::booleanValue).count());
        assertBalance("100.00");
        assertEquals(new BigDecimal("100.00"), ledger.totalRefunded(debitId));
    }

    @Test
    void sameOperationIdAcrossDifferentWalletsCannotOverwriteLedger() throws Exception {
        String other = "other@example.com";
        wallet.addFunds(email, new BigDecimal("100.00"), UUID.randomUUID());
        wallet.addFunds(other, new BigDecimal("100.00"), UUID.randomUUID());
        UUID operationId = UUID.randomUUID();
        List<Boolean> results = race(2, i -> wallet.deductFunds(i == 0 ? email : other, BigDecimal.TEN, operationId));
        assertEquals(1L, results.stream().filter(Boolean::booleanValue).count());
        assertEquals(3, ledger.count());
        assertEquals(new BigDecimal("190.00"), profiles.findByEmail(email).orElseThrow().getWalletBalance()
                .add(profiles.findByEmail(other).orElseThrow().getWalletBalance()));
    }

    @Test
    void conflictingOperationLeavesBalanceUntouched() {
        wallet.addFunds("other@example.com", BigDecimal.TEN, UUID.fromString("00000000-0000-0000-0000-000000000001"));
        wallet.addFunds(email, new BigDecimal("100.00"), UUID.randomUUID());
        assertThrows(ResponseStatusException.class, () -> wallet.deductFunds(email, BigDecimal.TEN,
                UUID.fromString("00000000-0000-0000-0000-000000000001")));
        assertBalance("100.00");
    }

    @Test
    void failedLedgerInsertRollsBackTheBalance() {
        wallet.addFunds(email, new BigDecimal("100.00"), UUID.randomUUID());
        jdbc.execute("ALTER TABLE wallet_transactions ADD CONSTRAINT test_reject_debit CHECK (type <> 'DEBIT')");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> wallet.deductFunds(email, BigDecimal.TEN, UUID.randomUUID()));
            assertBalance("100.00");
            assertEquals(1, ledger.count());
        } finally {
            jdbc.execute("ALTER TABLE wallet_transactions DROP CONSTRAINT test_reject_debit");
        }
    }

    private void assertBalance(String balance) {
        assertEquals(new BigDecimal(balance), profiles.findByEmail(email).orElseThrow().getWalletBalance());
    }

    private List<Boolean> race(int count, IntConsumer operation) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(count)) {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    start.await();
                    try {
                        operation.accept(index);
                        return true;
                    } catch (ResponseStatusException ex) {
                        if (ex.getStatusCode().value() != 409) throw ex;
                        return false;
                    } catch (org.springframework.dao.DataIntegrityViolationException ex) {
                        // Concurrent global operation-ID collision is rejected by the primary key.
                        return false;
                    }
                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        }
    }
}
