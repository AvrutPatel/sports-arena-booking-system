package com.sportsarena.auth_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;
import java.math.BigDecimal;
import java.util.UUID;

/** Terminal decisions prevent a delayed debit from charging after its reservation was released. */
@Entity
@Table(name = "wallet_payment_attempts")
@Getter
@NoArgsConstructor
public class WalletPaymentAttempt implements Persistable<UUID> {
    @Id private UUID operationId;
    @Column(nullable = false) private String email;
    @Column(nullable = false, precision = 19, scale = 2) private BigDecimal amount;
    @Column(nullable = false, length = 16) private String status;

    public WalletPaymentAttempt(UUID id, String email, BigDecimal amount, String status) {
        this.operationId = id; this.email = email; this.amount = amount; this.status = status;
    }
    @Override @Transient public UUID getId() { return operationId; }
    @Override @Transient public boolean isNew() { return true; }
}
