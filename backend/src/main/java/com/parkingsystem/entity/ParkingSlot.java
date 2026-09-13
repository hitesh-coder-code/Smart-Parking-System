package com.parkingsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "parking_slots")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ParkingSlot {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_id", nullable = false)
    private Zone zone;

    /** Who currently holds the slot right now — may be a temporary claimant. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    private User assignedTo;

    /** The permanent owner. Used to restore the slot once a temporary event expires. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "original_owner_id")
    private User originalOwner;

    @Column(name = "slot_code", nullable = false)
    private String slotCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false)
    private User.VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SlotStatus status;

    public enum SlotStatus {
        RESERVED, AVAILABLE, CLAIMED
    }
}
