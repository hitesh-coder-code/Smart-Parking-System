-- Enables gen_random_uuid() for UUID primary keys
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE organizations (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    type        VARCHAR(30)  NOT NULL CHECK (type IN ('COLLEGE', 'SHOPPING_COMPLEX', 'APARTMENT'))
);

CREATE TABLE users (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id                UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name                  VARCHAR(150) NOT NULL,
    email                 VARCHAR(150) NOT NULL UNIQUE,
    password_hash         VARCHAR(255) NOT NULL,
    role                  VARCHAR(30)  NOT NULL CHECK (role IN ('FACULTY', 'STAFF', 'RESIDENT', 'CUSTOMER', 'ADMIN')),
    default_vehicle_type  VARCHAR(20)  NOT NULL CHECK (default_vehicle_type IN ('TWO_WHEELER', 'FOUR_WHEELER')),
    created_at            TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE zones (
    id      UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id  UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    name    VARCHAR(150) NOT NULL
);

-- Precomputed "closeness" between two zones, used to rank slot claims.
-- Only one direction needs to be stored per pair; the service layer checks both orders.
CREATE TABLE zone_proximity (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    zone_a_id      UUID NOT NULL REFERENCES zones (id) ON DELETE CASCADE,
    zone_b_id      UUID NOT NULL REFERENCES zones (id) ON DELETE CASCADE,
    distance_rank  INT  NOT NULL,
    UNIQUE (zone_a_id, zone_b_id)
);

CREATE TABLE parking_slots (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id              UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    zone_id             UUID NOT NULL REFERENCES zones (id) ON DELETE CASCADE,
    -- who currently holds the slot right now (may be a temporary claimant)
    assigned_to         UUID REFERENCES users (id) ON DELETE SET NULL,
    -- the permanent owner, used to restore the slot once a temporary event expires
    original_owner_id   UUID REFERENCES users (id) ON DELETE SET NULL,
    slot_code           VARCHAR(30) NOT NULL,
    vehicle_type        VARCHAR(20) NOT NULL CHECK (vehicle_type IN ('TWO_WHEELER', 'FOUR_WHEELER')),
    status              VARCHAR(20) NOT NULL DEFAULT 'RESERVED' CHECK (status IN ('RESERVED', 'AVAILABLE', 'CLAIMED')),
    UNIQUE (org_id, slot_code)
);

-- An event that temporarily frees up a slot: the owner is absent, or is arriving
-- on a different vehicle type than their assigned slot supports.
CREATE TABLE slot_events (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    slot_id               UUID NOT NULL REFERENCES parking_slots (id) ON DELETE CASCADE,
    triggered_by          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    event_type            VARCHAR(20) NOT NULL CHECK (event_type IN ('ABSENCE', 'VEHICLE_CHANGE')),
    start_at              TIMESTAMP   NOT NULL,
    end_at                TIMESTAMP   NOT NULL,
    recurrence_rule       VARCHAR(50),
    status                VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'EXPIRED', 'CANCELLED')),
    -- for VEHICLE_CHANGE events only: the temporary slot auto-assigned to the reporting user
    temp_vehicle_slot_id  UUID REFERENCES parking_slots (id) ON DELETE SET NULL,
    created_at            TIMESTAMP   NOT NULL DEFAULT now(),
    CHECK (end_at > start_at)
);

CREATE TABLE slot_claims (
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id              UUID NOT NULL REFERENCES slot_events (id) ON DELETE CASCADE,
    claimed_by            UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    destination_zone_id   UUID REFERENCES zones (id) ON DELETE SET NULL,
    proximity_rank        INT,
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    created_at            TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE TABLE swap_agreements (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id           UUID NOT NULL REFERENCES organizations (id) ON DELETE CASCADE,
    user_a_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    slot_a_id        UUID NOT NULL REFERENCES parking_slots (id) ON DELETE CASCADE,
    user_b_id        UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    slot_b_id        UUID NOT NULL REFERENCES parking_slots (id) ON DELETE CASCADE,
    recurrence_type  VARCHAR(20) NOT NULL CHECK (recurrence_type IN ('ONE_TIME', 'WEEKLY', 'DATE_RANGE')),
    day_of_week      VARCHAR(10),
    start_date       DATE,
    end_date         DATE,
    status           VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACTIVE', 'CANCELLED', 'EXPIRED')),
    created_at       TIMESTAMP   NOT NULL DEFAULT now(),
    CHECK (user_a_id <> user_b_id)
);

CREATE INDEX idx_users_org ON users (org_id);
CREATE INDEX idx_zones_org ON zones (org_id);
CREATE INDEX idx_slots_org ON parking_slots (org_id);
CREATE INDEX idx_slots_zone ON parking_slots (zone_id);
CREATE INDEX idx_slots_status ON parking_slots (status);
CREATE INDEX idx_events_slot ON slot_events (slot_id);
CREATE INDEX idx_events_status ON slot_events (status);
CREATE INDEX idx_claims_event ON slot_claims (event_id);
CREATE INDEX idx_swaps_org ON swap_agreements (org_id);
