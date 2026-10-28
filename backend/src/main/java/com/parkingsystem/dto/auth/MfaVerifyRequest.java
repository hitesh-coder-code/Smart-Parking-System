package com.parkingsystem.dto.auth;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class MfaVerifyRequest {
    @NotBlank
    private String totpCode;
}
