package com.parkingsystem.service;

import com.parkingsystem.entity.ZoneProximity;
import com.parkingsystem.repository.ZoneProximityRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** Ranks how "close" a claimed slot's zone is to a claimant's stated destination zone. */
@Service
@RequiredArgsConstructor
public class ProximityService {

    private final ZoneProximityRepository zoneProximityRepository;

    public Integer rank(UUID destinationZoneId, UUID slotZoneId) {
        if (destinationZoneId == null) {
            return null; // claimant didn't specify a destination — treated as lowest priority when sorting
        }
        if (destinationZoneId.equals(slotZoneId)) {
            return 0; // same zone = closest possible
        }
        return zoneProximityRepository.findByZoneAIdAndZoneBId(destinationZoneId, slotZoneId)
                .or(() -> zoneProximityRepository.findByZoneAIdAndZoneBId(slotZoneId, destinationZoneId))
                .map(ZoneProximity::getDistanceRank)
                .orElse(Integer.MAX_VALUE); // no known relationship — treated as farthest
    }
}
