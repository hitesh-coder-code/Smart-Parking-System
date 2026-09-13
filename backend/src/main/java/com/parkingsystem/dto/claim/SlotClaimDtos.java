package com.parkingsystem.dto.claim;

import com.parkingsystem.entity.SlotClaim;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

public class SlotClaimDtos {

    @Data
    public static class ClaimRequest {
        @NotNull
        private UUID eventId;
        private UUID destinationZoneId; // optional: used for proximity ranking
    }

    @Data
    @AllArgsConstructor
    public static class ClaimResponse {
        private UUID id;
        private UUID eventId;
        private UUID claimedByUserId;
        private String claimedByName;
        private Integer proximityRank;
        private String status;

        public static ClaimResponse from(SlotClaim c) {
            return new ClaimResponse(
                    c.getId(), c.getEvent().getId(),
                    c.getClaimedBy().getId(), c.getClaimedBy().getName(),
                    c.getProximityRank(), c.getStatus().name());
        }
    }
}
