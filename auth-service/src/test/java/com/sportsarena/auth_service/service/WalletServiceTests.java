package com.sportsarena.auth_service.service;

import com.sportsarena.auth_service.entity.UserProfile;
import com.sportsarena.auth_service.entity.WalletTransaction;
import com.sportsarena.auth_service.repository.UserProfileRepository;
import com.sportsarena.auth_service.repository.WalletTransactionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTests {
    @Mock UserProfileRepository profiles;
    @Mock WalletTransactionRepository ledger;
    @Mock com.sportsarena.auth_service.repository.WalletPaymentAttemptRepository paymentAttempts;
    @InjectMocks UserProfileService service;
    private final String email = "player@example.com";

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "100000000000000000.00"})
    void invalidMoneyNeverTouchesTheDatabase(String amount) {
        assertThrows(IllegalArgumentException.class,
                () -> service.deductFunds(email, new BigDecimal(amount), UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class,
                () -> service.addFunds(email, new BigDecimal(amount), UUID.randomUUID()));
        verifyNoInteractions(profiles, ledger);
    }

    @Test
    void nullAmountAndMissingOperationIdAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.addFunds(email, null, UUID.randomUUID()));
        assertThrows(IllegalArgumentException.class, () -> service.addFunds(email, BigDecimal.ONE, null));
        verifyNoInteractions(profiles, ledger);
    }

    @Test
    void insufficientFundsDoNotCreateDebit() {
        UserProfile profile = profile("5.00");
        assertThrows(ResponseStatusException.class,
                () -> service.deductFunds(email, new BigDecimal("10.00"), UUID.randomUUID()));
        assertEquals(new BigDecimal("5.00"), profile.getWalletBalance());
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void debitUsesExactDecimalArithmetic() {
        UserProfile profile = profile("0.30");
        service.deductFunds(email, new BigDecimal("0.10"), UUID.randomUUID());
        assertEquals(new BigDecimal("0.20"), profile.getWalletBalance());
        verify(ledger).saveAndFlush(argThat(t -> t.getBalanceAfter().equals(new BigDecimal("0.20"))));
    }

    @Test
    void repeatedOperationDoesNotChangeBalanceAgain() {
        UserProfile profile = profile("90.00");
        UUID id = UUID.randomUUID();
        when(ledger.findById(id)).thenReturn(Optional.of(
                new WalletTransaction(id, email, "DEBIT", new BigDecimal("10.00"), null, new BigDecimal("90.00"))));
        service.deductFunds(email, new BigDecimal("10"), id);
        assertEquals(new BigDecimal("90.00"), profile.getWalletBalance());
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void reusedReferenceWithDifferentAmountIsConflict() {
        profile("90.00");
        UUID id = UUID.randomUUID();
        when(ledger.findById(id)).thenReturn(Optional.of(
                new WalletTransaction(id, email, "DEBIT", BigDecimal.TEN, null, new BigDecimal("90.00"))));
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.deductFunds(email, new BigDecimal("20"), id));
        assertEquals(409, error.getStatusCode().value());
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void refundsCannotExceedOriginalDebit() {
        UserProfile profile = profile("50.00");
        UUID debitId = UUID.randomUUID();
        when(ledger.findById(debitId)).thenReturn(Optional.of(
                new WalletTransaction(debitId, email, "DEBIT", new BigDecimal("100.00"), null, BigDecimal.ZERO)));
        when(ledger.totalRefunded(debitId)).thenReturn(new BigDecimal("80.00"));
        assertThrows(ResponseStatusException.class,
                () -> service.refundFunds(debitId, new BigDecimal("30.00"), UUID.randomUUID()));
        assertEquals(new BigDecimal("50.00"), profile.getWalletBalance());
        verify(ledger, never()).saveAndFlush(any());
    }

    @Test
    void refundRecipientComesFromOriginalDebit() {
        UserProfile profile = profile("0.00");
        UUID debitId = UUID.randomUUID();
        when(ledger.findById(debitId)).thenReturn(Optional.of(
                new WalletTransaction(debitId, email, "DEBIT", new BigDecimal("100.00"), null, BigDecimal.ZERO)));
        when(ledger.totalRefunded(debitId)).thenReturn(BigDecimal.ZERO);
        service.refundFunds(debitId, new BigDecimal("25.00"), UUID.randomUUID());
        assertEquals(new BigDecimal("25.00"), profile.getWalletBalance());
        verify(ledger).saveAndFlush(argThat(t -> t.getEmail().equals(email) && t.getDebitReference().equals(debitId)));
    }

    @Test
    void missingDebitCannotBeRefunded() {
        assertThrows(ResponseStatusException.class,
                () -> service.refundFunds(UUID.randomUUID(), BigDecimal.TEN, UUID.randomUUID()));
        verifyNoInteractions(profiles);
    }

    private UserProfile profile(String balance) {
        UserProfile profile = new UserProfile();
        profile.setEmail(email);
        profile.setWalletBalance(new BigDecimal(balance));
        when(profiles.findByEmailForUpdate(email)).thenReturn(Optional.of(profile));
        return profile;
    }
}
