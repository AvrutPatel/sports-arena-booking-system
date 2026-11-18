package com.sportsarena.auth_service.service;

import com.sportsarena.auth_service.entity.UserProfile;
import com.sportsarena.auth_service.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;

@DataJpaTest(showSql = false, properties = {
        "spring.datasource.url=jdbc:h2:mem:wallet_attempts;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.jpa.hibernate.ddl-auto=create-drop", "debug=false", "logging.level.org.springframework=WARN"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UserProfileService.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class WalletAttemptDatabaseTests {
    @Autowired UserProfileService wallet;
    @MockitoSpyBean UserProfileRepository profiles;
    @Autowired WalletTransactionRepository ledger;
    @Autowired WalletPaymentAttemptRepository attempts;
    @Autowired JdbcTemplate jdbc;
    private final String email = "player@example.com";

    @BeforeEach void setup() {
        attempts.deleteAllInBatch(); ledger.deleteAllInBatch(); profiles.deleteAllInBatch();
        // Profile initialization's existing PostgreSQL upsert is outside this test. Use a pre-existing profile;
        // actual JPA row locks, money writes, ledger writes and attempt decisions are all exercised.
        doNothing().when(profiles).ensureProfile(anyString());
        UserProfile profile = new UserProfile(); profile.setEmail(email); profile.setWalletBalance(new BigDecimal("100.00"));
        profiles.saveAndFlush(profile);
    }

    @Test void terminalVoidPreventsDelayedAndLegacyDebits() {
        UUID id = UUID.randomUUID();
        assertEquals("VOIDED", wallet.settleDebit(email, BigDecimal.TEN, id, false).status());
        assertEquals("VOIDED", wallet.settleDebit(email, BigDecimal.TEN, id, true).status());
        assertThrows(ResponseStatusException.class, () -> wallet.deductFunds(email, BigDecimal.TEN, id));
        assertEquals(0, ledger.count()); assertBalance("100.00");
    }

    @Test void appliedPaymentIsRecognizedByReconciliationWithoutAnotherDebit() {
        UUID id = UUID.randomUUID();
        assertEquals("APPLIED", wallet.settleDebit(email, BigDecimal.TEN, id, true).status());
        assertEquals("APPLIED", wallet.settleDebit(email, BigDecimal.TEN, id, false).status());
        assertEquals("APPLIED", wallet.settleDebit(email, BigDecimal.TEN, id, true).status());
        assertEquals(1, ledger.count()); assertBalance("90.00");
    }

    @Test void insufficientFundsDecisionDoesNotChargeLaterAfterTopUp() {
        UUID id = UUID.randomUUID();
        assertEquals("DECLINED", wallet.settleDebit(email, new BigDecimal("200.00"), id, true).status());
        wallet.addFunds(email, new BigDecimal("200.00"), UUID.randomUUID());
        assertEquals("DECLINED", wallet.settleDebit(email, new BigDecimal("200.00"), id, true).status());
        assertBalance("300.00"); assertEquals(1, ledger.count());
    }

    @Test void concurrentVoidAndDebitAgreeOnOneTerminalOutcome() throws Exception {
        UUID id = UUID.randomUUID();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> debit = executor.submit(() -> { start.await(); return wallet.settleDebit(email, BigDecimal.TEN, id, true).status(); });
            Future<String> close = executor.submit(() -> { start.await(); return wallet.settleDebit(email, BigDecimal.TEN, id, false).status(); });
            start.countDown();
            String decision = debit.get(15, TimeUnit.SECONDS);
            assertEquals(decision, close.get(15, TimeUnit.SECONDS));
            assertBalance("APPLIED".equals(decision) ? "90.00" : "100.00");
            assertEquals("APPLIED".equals(decision) ? 1 : 0, ledger.count());
        }
    }

    @Test void decisionPersistenceFailureRollsBackDebitAndLedger() {
        UUID id = UUID.randomUUID();
        jdbc.execute("ALTER TABLE wallet_payment_attempts ADD CONSTRAINT test_reject_apply CHECK (status <> 'APPLIED')");
        try {
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                    () -> wallet.settleDebit(email, BigDecimal.TEN, id, true));
            assertBalance("100.00"); assertEquals(0, ledger.count()); assertEquals(0, attempts.count());
        } finally { jdbc.execute("ALTER TABLE wallet_payment_attempts DROP CONSTRAINT test_reject_apply"); }
        assertEquals("APPLIED", wallet.settleDebit(email, BigDecimal.TEN, id, true).status());
        assertBalance("90.00");
    }

    @Test void repeatedRefundCreditsOnlyOnce() {
        UUID debit = UUID.randomUUID(), refund = UUID.randomUUID();
        wallet.settleDebit(email, new BigDecimal("100.00"), debit, true);
        wallet.refundFunds(debit, new BigDecimal("50.00"), refund);
        wallet.refundFunds(debit, new BigDecimal("50.00"), refund);
        assertBalance("50.00"); assertEquals(2, ledger.count());
    }

    private void assertBalance(String expected) {
        assertEquals(new BigDecimal(expected), profiles.findByEmail(email).orElseThrow().getWalletBalance());
    }
}
