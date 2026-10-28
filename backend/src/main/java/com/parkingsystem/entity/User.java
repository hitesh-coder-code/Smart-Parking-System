package com.parkingsystem.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue
    private UUID id;

    /** Null until the user joins or creates an organization. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_id")
    private Organization organization;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    /** Null until the user joins or creates an organization (role is assigned at that point). */
    @Enumerated(EnumType.STRING)
    private Role role;

    /** Null until the user completes onboarding. */
    @Enumerated(EnumType.STRING)
    @Column(name = "default_vehicle_type")
    private VehicleType defaultVehicleType;

    @Column(name = "is_verified", nullable = false)
    private boolean verified;

    @Column(name = "mfa_enabled", nullable = false)
    private boolean mfaEnabled;

    @Column(name = "totp_secret", length = 64)
    private String totpSecret;

    @Column(name = "verification_code")
    private String verificationCode;

    @Column(name = "verification_code_expires_at")
    private Instant verificationCodeExpiresAt;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public enum Role {
        FACULTY, STAFF, RESIDENT, CUSTOMER, ADMIN
    }

    public enum VehicleType {
        TWO_WHEELER, FOUR_WHEELER
    }
}
