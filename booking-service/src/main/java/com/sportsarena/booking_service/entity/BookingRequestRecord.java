package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Persistable;
import java.util.UUID;

@Entity
@Table(name = "booking_requests", uniqueConstraints = @UniqueConstraint(name = "booking_request_owner_key", columnNames = {"user_email", "request_key"}))
@Getter @NoArgsConstructor
public class BookingRequestRecord implements Persistable<String> {
    @Id @Column(length = 64) private String id;
    @Column(nullable = false) private String userEmail;
    @Column(nullable = false) private UUID requestKey;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(nullable = false) private UUID bookingId;
    @Column(nullable = false) private long createdAt;
    public BookingRequestRecord(String id, String email, UUID key, String fingerprint, UUID bookingId, long now) {
        this.id = id; this.userEmail = email; this.requestKey = key; this.fingerprint = fingerprint;
        this.bookingId = bookingId; this.createdAt = now;
    }
    @Override @Transient public boolean isNew() { return true; }
}
