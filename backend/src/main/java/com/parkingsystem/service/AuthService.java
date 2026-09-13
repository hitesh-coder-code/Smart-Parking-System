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
