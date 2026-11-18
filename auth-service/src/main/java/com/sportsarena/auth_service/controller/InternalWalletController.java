package com.sportsarena.auth_service.controller;

import com.sportsarena.auth_service.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;
import java.util.UUID;

/** Service-authenticated operations; background retries never depend on a customer's expired JWT. */
@RestController
@RequestMapping("/internal/wallet")
public class InternalWalletController {
    private final UserProfileService wallets;
    public InternalWalletController(UserProfileService wallets) { this.wallets = wallets; }

    public record DebitRequest(@NotNull UUID operationId, @NotBlank @Email String email,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount, boolean allowDebit) {}
    public record RefundRequest(@NotNull UUID operationId, @NotNull UUID debitReference,
            @NotNull @DecimalMin("0.01") @Digits(integer = 17, fraction = 2) BigDecimal amount) {}

    @PostMapping("/settle-debit")
    public UserProfileService.DebitDecision settle(@Valid @RequestBody DebitRequest request) {
        return wallets.settleDebit(request.email(), request.amount(), request.operationId(), request.allowDebit());
    }

    @PostMapping("/refund")
    public void refund(@Valid @RequestBody RefundRequest request) {
        wallets.refundFunds(request.debitReference(), request.amount(), request.operationId());
    }
}
