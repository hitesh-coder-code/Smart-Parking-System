package com.parkingsystem.service;

import com.parkingsystem.dto.auth.*;
import com.parkingsystem.entity.User;
import com.parkingsystem.exception.BadRequestException;
import com.parkingsystem.exception.ForbiddenException;
import com.parkingsystem.exception.ResourceNotFoundException;
import com.parkingsystem.repository.UserRepository;
import com.parkingsystem.security.JwtUtil;
import com.parkingsystem.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final long CODE_VALIDITY_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final EmailService emailService;
    private final com.parkingsystem.security.TotpUtil totpUtil;

    @Transactional
    public MessageResponse signup(SignupRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .verified(false)
                .build();

        issueNewCode(user);
        userRepository.save(user);
        emailService.sendVerificationCode(user.getEmail(), user.getVerificationCode());

        return new MessageResponse("Account created. A verification code has been sent to " + user.getEmail() + ".");
    }

    @Transactional
    public MessageResponse verify(VerifyRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found for this email"));

        if (user.isVerified()) {
            throw new BadRequestException("This account is already verified");
        }
        if (user.getVerificationCode() == null || !user.getVerificationCode().equals(request.getCode())) {
            throw new BadRequestException("Invalid verification code");
        }
        if (user.getVerificationCodeExpiresAt() == null || user.getVerificationCodeExpiresAt().isBefore(Instant.now())) {
            throw new BadRequestException("This code has expired — request a new one");
        }

        user.setVerified(true);
        user.setVerificationCode(null);
        user.setVerificationCodeExpiresAt(null);
        userRepository.save(user);

        return new MessageResponse("Email verified. You can now log in.");
    }

    @Transactional
    public MessageResponse resendVerification(ResendVerificationRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("No account found for this email"));

        if (user.isVerified()) {
            throw new BadRequestException("This account is already verified");
        }

        issueNewCode(user);
        userRepository.save(user);
        emailService.sendVerificationCode(user.getEmail(), user.getVerificationCode());

        return new MessageResponse("A new verification code has been sent to " + user.getEmail() + ".");
    }

    public AuthResponse login(LoginRequest request) {
        // Authenticate first so a wrong password fails the same way regardless
        // of verification status — this avoids leaking account state to guessers.
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isVerified()) {
            throw new ForbiddenException("Please verify your email before logging in");
        }

        if (user.isMfaEnabled()) {
            // If the client submitted the TOTP code in the primary login payload:
            if (request.getTotpCode() != null && !request.getTotpCode().isBlank()) {
                if (!totpUtil.verifyCode(user.getTotpSecret(), request.getTotpCode())) {
                    throw new BadRequestException("Invalid MFA TOTP code");
                }
                return buildFullAuthResponse(user);
            }
            // Two-step flow: issue short-lived pre-auth token that only permits MFA verification
            String tempToken = jwtUtil.generatePreAuthToken(user.getEmail());
            return AuthResponse.builder()
                    .mfaRequired(true)
                    .tempToken(tempToken)
                    .userId(user.getId())
                    .email(user.getEmail())
                    .build();
        }

        return buildFullAuthResponse(user);
    }

    public AuthResponse verifyLoginMfa(MfaLoginVerifyRequest request) {
        if (!jwtUtil.isPreAuthToken(request.getTempToken())) {
            throw new BadRequestException("Invalid or expired MFA session token");
        }
        String email = jwtUtil.extractEmail(request.getTempToken());
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isMfaEnabled() || user.getTotpSecret() == null) {
            throw new BadRequestException("MFA is not enabled on this account");
        }

        if (!totpUtil.verifyCode(user.getTotpSecret(), request.getTotpCode())) {
            throw new BadRequestException("Invalid MFA TOTP code");
        }

        return buildFullAuthResponse(user);
    }

    @Transactional
    public MfaSetupResponse setupMfa(User requester) {
        User user = userRepository.findById(requester.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isMfaEnabled()) {
            throw new BadRequestException("MFA is already enabled on this account. To reconfigure, disable it first.");
        }

        String secret = totpUtil.generateSecret();
        String qrUri = totpUtil.getQrUri(secret, user.getEmail(), "SmartParking");

        user.setTotpSecret(secret);
        // Note: mfaEnabled remains false until user proves possession by verifying a code
        userRepository.save(user);

        return MfaSetupResponse.builder()
                .secret(secret)
                .qrUri(qrUri)
                .message("Scan QR code or enter secret into Google or Microsoft Authenticator, then call /api/auth/mfa/enable with the 6-digit code.")
                .build();
    }

    @Transactional
    public MessageResponse enableMfa(User requester, MfaVerifyRequest request) {
        User user = userRepository.findById(requester.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (user.isMfaEnabled()) {
            throw new BadRequestException("MFA is already active on this account");
        }
        if (user.getTotpSecret() == null) {
            throw new BadRequestException("MFA setup has not been initiated. Call /setup first.");
        }
        if (!totpUtil.verifyCode(user.getTotpSecret(), request.getTotpCode())) {
            throw new BadRequestException("Invalid verification code. Ensure your device clock is synchronized.");
        }

        user.setMfaEnabled(true);
        userRepository.save(user);

        return new MessageResponse("MFA successfully enabled. TOTP will now be required at login.");
    }

    @Transactional
    public MessageResponse disableMfa(User requester, MfaVerifyRequest request) {
        User user = userRepository.findById(requester.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        if (!user.isMfaEnabled()) {
            throw new BadRequestException("MFA is not enabled on this account");
        }
        if (!totpUtil.verifyCode(user.getTotpSecret(), request.getTotpCode())) {
            throw new BadRequestException("Invalid verification code");
        }

        user.setMfaEnabled(false);
        user.setTotpSecret(null);
        userRepository.save(user);

        return new MessageResponse("MFA has been disabled.");
    }

    private AuthResponse buildFullAuthResponse(User user) {
        String token = jwtUtil.generateToken(user.getEmail());
        return new AuthResponse(
                token, user.getId(), user.getName(), user.getEmail(),
                user.getRole() != null ? user.getRole().name() : null,
                user.getOrganization() != null ? user.getOrganization().getId() : null);
    }

    private void issueNewCode(User user) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        user.setVerificationCode(code);
        user.setVerificationCodeExpiresAt(Instant.now().plus(CODE_VALIDITY_MINUTES, ChronoUnit.MINUTES));
    }
}
