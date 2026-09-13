package com.parkingsystem.dto.swap;

import com.parkingsystem.entity.SwapAgreement;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

public class SwapDtos {

    @Data
    public static class SwapRequest {
        @NotNull
        private UUID userBId;
        @NotNull
        private UUID slotAId; // proposer's own slot
        @NotNull
        private UUID slotBId; // the other user's slot
        @NotNull
        private SwapAgreement.RecurrenceType recurrenceType;
        private String dayOfWeek;     // required if WEEKLY
        private LocalDate startDate;  // required for ONE_TIME and DATE_RANGE
        private LocalDate endDate;    // required for DATE_RANGE (and WEEKLY, as the recurrence's last date)
    }

    @Data
    @AllArgsConstructor
    public static class SwapResponse {
        private UUID id;
        private UUID userAId;
        private String userAName;
        private UUID slotAId;
        private UUID userBId;
        private String userBName;
        private UUID slotBId;
        private String recurrenceType;
        private String dayOfWeek;
        private LocalDate startDate;
        private LocalDate endDate;
        private String status;

        public static SwapResponse from(SwapAgreement s) {
            return new SwapResponse(
                    s.getId(),
                    s.getUserA().getId(), s.getUserA().getName(), s.getSlotA().getId(),
                    s.getUserB().getId(), s.getUserB().getName(), s.getSlotB().getId(),
                    s.getRecurrenceType().name(), s.getDayOfWeek(),
                    s.getStartDate(), s.getEndDate(), s.getStatus().name());
        }
    }
}
