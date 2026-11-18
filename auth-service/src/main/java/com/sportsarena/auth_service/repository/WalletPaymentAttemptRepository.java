package com.sportsarena.auth_service.repository;

import com.sportsarena.auth_service.entity.WalletPaymentAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface WalletPaymentAttemptRepository extends JpaRepository<WalletPaymentAttempt, UUID> {}
