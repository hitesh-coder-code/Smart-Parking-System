-- Adds TOTP Multi-Factor Authentication (MFA) fields (DA3 Q2.B)
ALTER TABLE users
    ADD COLUMN mfa_enabled BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN totp_secret VARCHAR(64);
