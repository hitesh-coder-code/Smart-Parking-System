package com.parkingsystem.controller;

import com.parkingsystem.dto.auth.*;
import com.parkingsystem.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    public ResponseEntity<MessageResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<MessageResponse> verify(@Valid @RequestBody VerifyRequest request) {
        return ResponseEntity.ok(authService.verify(request));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resend(@Valid @RequestBody ResendVerificationRequest request) {
        return ResponseEntity.ok(authService.resendVerification(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/mfa/verify-login")
    public ResponseEntity<AuthResponse> verifyLoginMfa(@Valid @RequestBody MfaLoginVerifyRequest request) {
        return ResponseEntity.ok(authService.verifyLoginMfa(request));
    }

    @PostMapping("/mfa/setup")
    public ResponseEntity<MfaSetupResponse> setupMfa(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.parkingsystem.security.CustomUserDetails principal) {
        return ResponseEntity.ok(authService.setupMfa(principal.getUser()));
    }

    @PostMapping("/mfa/enable")
    public ResponseEntity<MessageResponse> enableMfa(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.parkingsystem.security.CustomUserDetails principal,
            @Valid @RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(authService.enableMfa(principal.getUser(), request));
    }

    @PostMapping("/mfa/disable")
    public ResponseEntity<MessageResponse> disableMfa(
            @org.springframework.security.core.annotation.AuthenticationPrincipal com.parkingsystem.security.CustomUserDetails principal,
            @Valid @RequestBody MfaVerifyRequest request) {
        return ResponseEntity.ok(authService.disableMfa(principal.getUser(), request));
    }
}
