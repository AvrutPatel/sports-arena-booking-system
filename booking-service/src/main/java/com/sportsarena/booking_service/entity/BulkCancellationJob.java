package com.sportsarena.booking_service.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "bulk_cancellation_jobs", uniqueConstraints = @UniqueConstraint(name = "bulk_owner_request_key",
        columnNames = {"owner_email", "request_key"}), indexes = @Index(name = "bulk_job_scan_idx", columnList = "state,created_at"))
@Data @NoArgsConstructor
public class BulkCancellationJob {
    @Id private UUID id;
    @Version private Long version;
    @Column(nullable = false) private String ownerEmail;
    @Column(nullable = false) private UUID requestKey;
    @Column(nullable = false, length = 64) private String fingerprint;
    @Column(nullable = false) private Long venueId;
    private Long courtId;
    @Column(nullable = false) private LocalDate startDate;
    private LocalDate endDate;
    @Column(nullable = false) private Long closureId;
    @Column(nullable = false, length = 24) private String state;
    @Column(nullable = false) private long queuedCount;
    @Column(nullable = false) private long createdAt;
}
