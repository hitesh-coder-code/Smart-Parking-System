package com.parkingsystem.repository;

import com.parkingsystem.entity.ZoneProximity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ZoneProximityRepository extends JpaRepository<ZoneProximity, UUID> {
    Optional<ZoneProximity> findByZoneAIdAndZoneBId(UUID zoneAId, UUID zoneBId);
}
