package com.parkingsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot_claims")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotClaim {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private SlotEvent event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "claimed_by", nullable = false)
    private User claimedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "destination_zone_id")
    private Zone destinationZone;

    @Column(name = "proximity_rank")
    private Integer proximityRank;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum ClaimStatus {
        PENDING, APPROVED, REJECTED
    }
}
