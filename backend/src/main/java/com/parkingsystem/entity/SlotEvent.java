package com.parkingsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "slot_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SlotEvent {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "slot_id", nullable = false)
    private ParkingSlot slot;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggered_by", nullable = false)
    private User triggeredBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;

    @Column(name = "start_at", nullable = false)
    private Instant startAt;

    @Column(name = "end_at", nullable = false)
    private Instant endAt;

    @Column(name = "recurrence_rule")
    private String recurrenceRule;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    /** For VEHICLE_CHANGE events only: the 2W slot temporarily assigned to the reporting user. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "temp_vehicle_slot_id")
    private ParkingSlot tempVehicleSlot;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum EventType {
        ABSENCE, VEHICLE_CHANGE
    }

    public enum EventStatus {
        ACTIVE, EXPIRED, CANCELLED
    }
}
