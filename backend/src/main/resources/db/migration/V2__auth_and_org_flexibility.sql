-- Supports the real onboarding flow: a user can exist before joining/creating
-- an organization, and before completing their profile (role, vehicle type).
ALTER TABLE users
    ALTER COLUMN org_id DROP NOT NULL,
    ALTER COLUMN role DROP NOT NULL,
    ALTER COLUMN default_vehicle_type DROP NOT NULL;

ALTER TABLE users
    ADD COLUMN is_verified BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN verification_code VARCHAR(10),
    ADD COLUMN verification_code_expires_at TIMESTAMP;
