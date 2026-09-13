package com.parkingsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "zone_proximity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ZoneProximity {

    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_a_id", nullable = false)
    private Zone zoneA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "zone_b_id", nullable = false)
    private Zone zoneB;

    @Column(name = "distance_rank", nullable = false)
    private Integer distanceRank;
}
