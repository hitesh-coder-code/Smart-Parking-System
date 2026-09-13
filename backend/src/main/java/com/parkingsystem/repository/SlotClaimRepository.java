package com.parkingsystem.repository;

import com.parkingsystem.entity.SlotClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SlotClaimRepository extends JpaRepository<SlotClaim, UUID> {
    List<SlotClaim> findByEventId(UUID eventId);

    List<SlotClaim> findByEventIdAndStatus(UUID eventId, SlotClaim.ClaimStatus status);
}
