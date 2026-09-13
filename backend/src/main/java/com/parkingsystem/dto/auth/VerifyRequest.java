package com.parkingsystem.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class VerifyRequest {
    @Email
    @NotBlank
    private String email;

    @NotBlank
    private String code;
}
