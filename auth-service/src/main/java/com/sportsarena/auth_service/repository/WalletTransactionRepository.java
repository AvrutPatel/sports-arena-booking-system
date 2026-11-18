package com.sportsarena.auth_service.repository;

import com.sportsarena.auth_service.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, UUID> {
    @Query("select coalesce(sum(t.amount), 0) from WalletTransaction t "
            + "where t.type = 'REFUND' and t.debitReference = :debitReference")
    BigDecimal totalRefunded(@Param("debitReference") UUID debitReference);
}
