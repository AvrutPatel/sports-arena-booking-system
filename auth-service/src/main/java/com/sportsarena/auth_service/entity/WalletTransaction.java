package com.sportsarena.auth_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wallet_transactions", indexes = @Index(name = "wallet_debit_reference_idx", columnList = "debit_reference"))
@Getter
@NoArgsConstructor
public class WalletTransaction implements Persistable<UUID> {
    @Id
    private UUID operationId;
    @Column(nullable = false)
    private String email;
    @Column(nullable = false, length = 10)
    private String type;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;
    private UUID debitReference;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balanceAfter;
    @Column(nullable = false)
    private Instant createdAt;

    @Override
    @Transient
    public UUID getId() {
        return operationId;
    }

    @Override
    @Transient
    public boolean isNew() {
        // Append-only: assigned operation IDs must INSERT, never merge into another operation.
        return true;
    }

    public WalletTransaction(UUID operationId, String email, String type, BigDecimal amount,
                             UUID debitReference, BigDecimal balanceAfter) {
        this.operationId = operationId;
        this.email = email;
        this.type = type;
        this.amount = amount;
        this.debitReference = debitReference;
        this.balanceAfter = balanceAfter;
        this.createdAt = Instant.now();
    }
}
