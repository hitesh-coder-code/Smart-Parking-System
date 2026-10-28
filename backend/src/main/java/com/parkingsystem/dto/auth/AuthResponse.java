package com.parkingsystem.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UUID userId;
    private String name;
    private String email;
    private String role;
    private UUID orgId;

    private boolean mfaRequired;
    private String tempToken;

    public AuthResponse(String token, UUID userId, String name, String email, String role, UUID orgId) {
        this.token = token;
        this.userId = userId;
        this.name = name;
        this.email = email;
        this.role = role;
        this.orgId = orgId;
        this.mfaRequired = false;
        this.tempToken = null;
    }
}
