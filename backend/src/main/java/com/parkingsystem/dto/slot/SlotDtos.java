package com.parkingsystem.dto.slot;

import com.parkingsystem.entity.ParkingSlot;
import com.parkingsystem.entity.User;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

public class SlotDtos {

    @Data
    public static class SlotRequest {
        @NotNull
        private UUID zoneId;
        @NotNull
        private String slotCode;
        @NotNull
        private User.VehicleType vehicleType;
        private UUID assignedToUserId; // optional: null means the slot starts in the unassigned pool
    }

    @Data
    @AllArgsConstructor
    public static class SlotResponse {
        private UUID id;
        private String slotCode;
        private String vehicleType;
        private String status;
        private UUID zoneId;
        private String zoneName;
        private UUID assignedToUserId;
        private String assignedToName;
        private UUID originalOwnerId;

        public static SlotResponse from(ParkingSlot s) {
            return new SlotResponse(
                    s.getId(), s.getSlotCode(), s.getVehicleType().name(), s.getStatus().name(),
                    s.getZone().getId(), s.getZone().getName(),
                    s.getAssignedTo() != null ? s.getAssignedTo().getId() : null,
                    s.getAssignedTo() != null ? s.getAssignedTo().getName() : null,
                    s.getOriginalOwner() != null ? s.getOriginalOwner().getId() : null
            );
        }
    }
}
