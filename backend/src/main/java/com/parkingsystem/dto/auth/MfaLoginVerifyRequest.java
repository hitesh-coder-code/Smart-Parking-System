package com.parkingsystem.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaLoginVerifyRequest {
    @NotBlank
    private String tempToken;

    @NotBlank
    private String totpCode;
}
