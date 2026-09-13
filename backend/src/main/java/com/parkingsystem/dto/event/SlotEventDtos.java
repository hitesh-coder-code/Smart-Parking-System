package com.parkingsystem.dto.event;

import com.parkingsystem.entity.SlotEvent;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public class SlotEventDtos {

    @Data
    public static class SlotEventRequest {
        @NotNull
        private UUID slotId;
        @NotNull
        private SlotEvent.EventType eventType;
        @NotNull
        private Instant startAt;
        @NotNull
        private Instant endAt;
        private String recurrenceRule; // optional
    }

    @Data
    @AllArgsConstructor
    public static class SlotEventResponse {
        private UUID id;
        private UUID slotId;
        private String slotCode;
        private String eventType;
        private Instant startAt;
        private Instant endAt;
        private String status;
        private UUID triggeredByUserId;
        private UUID tempVehicleSlotId;

        public static SlotEventResponse from(SlotEvent e) {
            return new SlotEventResponse(
                    e.getId(), e.getSlot().getId(), e.getSlot().getSlotCode(),
                    e.getEventType().name(), e.getStartAt(), e.getEndAt(), e.getStatus().name(),
                    e.getTriggeredBy().getId(),
                    e.getTempVehicleSlot() != null ? e.getTempVehicleSlot().getId() : null
            );
        }
    }
}
