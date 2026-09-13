package com.parkingsystem.dto.zone;

import com.parkingsystem.entity.Zone;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

public class ZoneDtos {

    @Data
    public static class ZoneRequest {
        @NotBlank
        private String name;
    }

    @Data
    @AllArgsConstructor
    public static class ZoneResponse {
        private UUID id;
        private String name;
        private UUID orgId;

        public static ZoneResponse from(Zone z) {
            return new ZoneResponse(z.getId(), z.getName(), z.getOrganization().getId());
        }
    }
}
