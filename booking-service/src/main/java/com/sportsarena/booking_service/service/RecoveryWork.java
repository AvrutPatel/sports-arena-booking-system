package com.sportsarena.booking_service.service;

import java.math.BigDecimal;
import java.util.UUID;

public record RecoveryWork(UUID bookingId, UUID leaseToken, String kind, UUID operationId,
                           String email, BigDecimal amount, boolean allowDebit) {}
