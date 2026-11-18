package com.sportsarena.booking_service.repository;

import com.sportsarena.booking_service.entity.BookingRequestRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface BookingRequestRecordRepository extends JpaRepository<BookingRequestRecord, String> {
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    Optional<BookingRequestRecord> findByUserEmailAndRequestKey(String email, UUID key);
}
