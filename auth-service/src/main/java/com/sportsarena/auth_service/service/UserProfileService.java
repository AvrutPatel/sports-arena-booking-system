package com.sportsarena.auth_service.service;

import com.sportsarena.auth_service.dto.UserProfileDto;
import com.sportsarena.auth_service.entity.UserProfile;
import com.sportsarena.auth_service.repository.UserProfileRepository;
import com.sportsarena.auth_service.entity.WalletTransaction;
import com.sportsarena.auth_service.repository.WalletTransactionRepository;
import com.sportsarena.auth_service.repository.WalletPaymentAttemptRepository;
import com.sportsarena.auth_service.entity.WalletPaymentAttempt;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {

    @Autowired
    private UserProfileRepository profileRepository;

    @Autowired
    private WalletTransactionRepository walletRepository;

    @Autowired
    private WalletPaymentAttemptRepository paymentAttempts;

    // Fetch the profile. If it doesn't exist, create an empty one instantly!
    @Transactional
    public UserProfile getProfile(String email) {
        profileRepository.ensureProfile(email);
        return profileRepository.findByEmail(email).orElseThrow();
    }

    // Update the profile fields that the user chose to fill out
    @Transactional
    public UserProfile updateProfile(String email, UserProfileDto dto) {
        UserProfile profile = lockProfile(email);

        if (dto.getFullName() != null) profile.setFullName(dto.getFullName());
        if (dto.getAvatar() != null) profile.setAvatar(dto.getAvatar());
        if (dto.getInterestedSports() != null) profile.setInterestedSports(dto.getInterestedSports());
        if (dto.getFavoriteVenueIds() != null) profile.setFavoriteVenueIds(dto.getFavoriteVenueIds());

        return profileRepository.save(profile);
    }

    // The caller is responsible for verifying the external top-up payment.
    @Transactional
    public UserProfile addFunds(String email, BigDecimal amount, UUID operationId) {
        return apply(email, amount, operationId, "TOPUP", null);
    }

    @Transactional
    public UserProfile deductFunds(String email, BigDecimal amount, UUID operationId) {
        validateAmount(amount);
        if (operationId == null) throw new IllegalArgumentException("An operation ID is required.");
        lockProfile(email);
        WalletPaymentAttempt terminal = paymentAttempts.findById(operationId).orElse(null);
        if (terminal != null && !"APPLIED".equals(terminal.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment attempt is already closed.");
        }
        return apply(email, amount, operationId, "DEBIT", null);
    }

    public record DebitDecision(UUID operationId, String status) {}

    @Transactional
    public DebitDecision settleDebit(String email, BigDecimal suppliedAmount, UUID operationId, boolean allowDebit) {
        BigDecimal amount = validateAmount(suppliedAmount);
        if (operationId == null || email == null || email.isBlank()) throw new IllegalArgumentException("Invalid payment reference.");
        UserProfile profile = lockProfile(email);
        WalletPaymentAttempt terminal = paymentAttempts.findById(operationId).orElse(null);
        if (terminal != null) {
            if (!terminal.getEmail().equals(email) || terminal.getAmount().compareTo(amount) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment reference does not match the original request.");
            }
            return new DebitDecision(operationId, terminal.getStatus());
        }
        WalletTransaction existing = walletRepository.findById(operationId).orElse(null);
        String result;
        if (existing != null) {
            if (!"DEBIT".equals(existing.getType()) || !existing.getEmail().equals(email)
                    || existing.getAmount().compareTo(amount) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Conflicting payment reference.");
            }
            result = "APPLIED";
        } else if (!allowDebit) {
            result = "VOIDED";
        } else if (profile.getWalletBalance().compareTo(amount) < 0) {
            result = "DECLINED";
        } else {
            apply(email, amount, operationId, "DEBIT", null);
            result = "APPLIED";
        }
        paymentAttempts.saveAndFlush(new WalletPaymentAttempt(operationId, email, amount, result));
        return new DebitDecision(operationId, result);
    }

    @Transactional
    public UserProfile refundFunds(UUID debitReference, BigDecimal amount, UUID operationId) {
        WalletTransaction debit = walletRepository.findById(debitReference)
                .filter(t -> "DEBIT".equals(t.getType()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Original wallet debit not found."));
        // The recipient comes from the immutable debit, never from caller-supplied email.
        return apply(debit.getEmail(), amount, operationId, "REFUND", debit);
    }

    private UserProfile apply(String email, BigDecimal suppliedAmount, UUID operationId,
                              String type, WalletTransaction debit) {
        BigDecimal amount = validateAmount(suppliedAmount);
        if (operationId == null) {
            throw new IllegalArgumentException("An operation ID is required.");
        }
        UserProfile profile = lockProfile(email);
        UUID debitReference = debit == null ? null : debit.getOperationId();
        WalletTransaction existing = walletRepository.findById(operationId).orElse(null);
        if (existing != null) {
            if (!existing.getEmail().equals(email) || !existing.getType().equals(type)
                    || existing.getAmount().compareTo(amount) != 0
                    || !Objects.equals(existing.getDebitReference(), debitReference)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Operation ID was already used for a different request.");
            }
            return profile;
        }
        BigDecimal balance = profile.getWalletBalance();
        if ("DEBIT".equals(type)) {
            if (balance.compareTo(amount) < 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient wallet balance.");
            }
            balance = balance.subtract(amount);
        } else {
            if (debit != null && walletRepository.totalRefunded(debitReference).add(amount)
                    .compareTo(debit.getAmount()) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Refund exceeds the remaining refundable amount.");
            }
            balance = balance.add(amount);
        }
        if (balance.precision() > 19) {
            throw new IllegalArgumentException("Wallet balance exceeds the supported limit.");
        }
        profile.setWalletBalance(balance);
        // Both writes commit or roll back together, while holding the wallet row lock.
        profileRepository.save(profile);
        walletRepository.saveAndFlush(new WalletTransaction(operationId, email, type, amount, debitReference, balance));
        return profile;
    }

    private UserProfile lockProfile(String email) {
        profileRepository.ensureProfile(email);
        return profileRepository.findByEmailForUpdate(email).orElseThrow();
    }

    private BigDecimal validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be greater than zero.");
        }
        if (amount.compareTo(new BigDecimal("99999999999999999.99")) > 0) {
            throw new IllegalArgumentException("Amount exceeds the supported limit.");
        }
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new IllegalArgumentException("Amount must have at most two decimal places.");
        }
        try {
            amount = amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new IllegalArgumentException("Amount must have at most two decimal places.");
        }
        if (amount.precision() > 19) {
            throw new IllegalArgumentException("Amount exceeds the supported limit.");
        }
        return amount;
    }
}
