package com.parkingsystem.dto.org;

import com.parkingsystem.entity.Organization;
import com.parkingsystem.entity.User;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

public class OrgDtos {

    /** Deliberately minimal — no sensitive org data is ever exposed in this listing. */
    @Data
    @AllArgsConstructor
    public static class PublicOrgResponse {
        private UUID id;
        private String name;
        private String type;

        public static PublicOrgResponse from(Organization o) {
            return new PublicOrgResponse(o.getId(), o.getName(), o.getType().name());
        }
    }

    @Data
    public static class CreateOrgRequest {
        @NotBlank
        private String name;
        @NotNull
        private Organization.OrgType type;
        private User.VehicleType defaultVehicleType; // optional, defaults to FOUR_WHEELER
    }

    @Data
    public static class JoinOrgRequest {
        @NotNull
        private User.Role role;
        private User.VehicleType defaultVehicleType; // optional, defaults to FOUR_WHEELER
    }
}
