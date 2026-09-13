package com.parkingsystem.dto.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class AuthResponse {
    private String token;
    private UUID userId;
    private String name;
    private String email;
    private String role;
    private UUID orgId;
}
