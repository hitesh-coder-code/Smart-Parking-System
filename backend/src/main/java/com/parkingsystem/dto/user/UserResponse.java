package com.parkingsystem.dto.user;

import com.parkingsystem.entity.User;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class UserResponse {
    private UUID id;
    private String name;
    private String email;
    private String role;              // null until the user joins/creates an org
    private String defaultVehicleType; // null until onboarding is complete
    private UUID orgId;               // null until the user joins/creates an org
    private String orgType;           // null until the user joins/creates an org
    private boolean verified;

    public static UserResponse from(User u) {
        return new UserResponse(
                u.getId(), u.getName(), u.getEmail(),
                u.getRole() != null ? u.getRole().name() : null,
                u.getDefaultVehicleType() != null ? u.getDefaultVehicleType().name() : null,
                u.getOrganization() != null ? u.getOrganization().getId() : null,
                u.getOrganization() != null ? u.getOrganization().getType().name() : null,
                u.isVerified());
    }
}
