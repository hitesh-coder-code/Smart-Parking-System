# API Documentation

Base URL (local): `http://localhost:8080`
All endpoints except `/api/auth/**` and `/api/orgs/public` require:
`Authorization: Bearer <token>`

There is no seed data — every organization, user, zone, and slot is created
through the flow below, starting from an empty database.

---

## Auth

### POST /api/auth/signup
```json
{ "name": "New User", "email": "newuser@example.com", "password": "a-strong-password" }
```
**201** → `{ "message": "Account created. A verification code has been sent to newuser@example.com." }`

The account is inactive until verified. In local dev (`EMAIL_MODE=LOG`, the
default), the code is printed to the backend console instead of emailed.

### POST /api/auth/verify
```json
{ "email": "newuser@example.com", "code": "123456" }
```
**200** → `{ "message": "Email verified. You can now log in." }`
Codes expire after 15 minutes — use resend below if needed.

### POST /api/auth/resend-verification
```json
{ "email": "newuser@example.com" }
```
**200** → `{ "message": "A new verification code has been sent to newuser@example.com." }`

### POST /api/auth/login
```json
{ "email": "newuser@example.com", "password": "a-strong-password" }
```
**200** →
```json
{ "token": "...", "userId": "...", "name": "New User", "email": "...", "role": null, "orgId": null }
```
`role` and `orgId` are `null` until the user joins or creates an organization.
Returns **403** if the account hasn't been verified yet.

---

## Current user

### GET /api/users/me
Works even before joining an organization — used right after login to decide
whether to route the user to organization selection or their dashboard.
```json
{
  "id": "...", "name": "...", "email": "...",
  "role": null, "defaultVehicleType": null,
  "orgId": null, "orgType": null, "verified": true
}
```

---

## Organizations

### GET /api/orgs/public *(no auth required)*
Returns `[{ "id", "name", "type" }]` only — no sensitive organization data.

### POST /api/orgs *(any authenticated user without an org yet)*
```json
{ "name": "Greenfield College", "type": "COLLEGE", "defaultVehicleType": "FOUR_WHEELER" }
```
Creates the organization and makes the caller its `ADMIN`. **400** if the
caller already belongs to an organization.

### POST /api/orgs/{id}/join *(any authenticated user without an org yet)*
```json
{ "role": "FACULTY", "defaultVehicleType": "FOUR_WHEELER" }
```
`role` must be valid for that organization's type:

| Organization type   | Joinable roles              |
|----------------------|------------------------------|
| `COLLEGE`            | `FACULTY`, `STAFF`            |
| `SHOPPING_COMPLEX`   | `STAFF`, `CUSTOMER`           |
| `APARTMENT`          | `RESIDENT`                    |

`ADMIN` is never joinable — it's only granted to whoever creates the org.
Both endpoints return the caller's updated `UserResponse` (see above).

---

## Users

- `GET /api/users` — list users in your organization
- `GET /api/users/{id}`
- `PUT /api/users/{id}` *(admin only)* — body: `{ "name", "role", "defaultVehicleType" }`
- `DELETE /api/users/{id}` *(admin only)*

All of these require the caller to already belong to an organization
(**400** with a clear message otherwise), and the organization is always
taken from the caller's JWT-backed session — never from a client-supplied ID.

---

## Zones

- `GET /api/zones` — list zones in your organization
- `POST /api/zones` *(admin only)* — `{ "name": "Block A" }`

No `orgId` in the request body — the zone is created in the admin's own
organization automatically.

---

## Parking Slots

- `GET /api/slots` — all slots in your organization
- `GET /api/slots/available` — slots currently open (temporarily released, or never claimed)
- `POST /api/slots` *(admin only)*:
  ```json
  { "zoneId": "...", "slotCode": "A-05", "vehicleType": "FOUR_WHEELER", "assignedToUserId": null }
  ```
- `DELETE /api/slots/{id}` *(admin only)*

### POST /api/slots/{id}/claim-permanent *(any org member)*
Self-service: claim an unowned (`originalOwnerId: null`), `AVAILABLE` slot as
your own permanent slot. **400** if the slot already has an owner, isn't
available, or you already own a slot elsewhere.

### POST /api/slots/{id}/release *(the slot's owner, or an admin)*
Returns a permanent slot to the unowned pool. **400** if it currently has an
active absence/vehicle-change event (cancel that first).

**Response shape:**
```json
{
  "id": "...", "slotCode": "A-01", "vehicleType": "FOUR_WHEELER", "status": "AVAILABLE",
  "zoneId": "...", "zoneName": "Block A",
  "assignedToUserId": null, "assignedToName": null,
  "originalOwnerId": null
}
```

---

## Slot Events (absence / vehicle change)

### POST /api/slot-events/absence
```json
{
  "slotId": "...",
  "eventType": "ABSENCE",
  "startAt": "2026-09-08T09:00:00Z",
  "endAt": "2026-09-08T17:00:00Z"
}
```
Releases the slot immediately (`status` → `AVAILABLE`) so other eligible
users can claim it.

### POST /api/slot-events/vehicle-change
Same body shape, `eventType: "VEHICLE_CHANGE"`. Only valid for a
`FOUR_WHEELER` slot you own. Releases your 4W slot and, if a free 2W pool
slot exists in your organization, assigns it to you automatically.

### GET /api/slot-events?slotId={id}
List all events (any status) for a given slot.

### PATCH /api/slot-events/{id}/cancel
Cancels an `ACTIVE` event and restores the slot to its owner. Fails with
`400` if the slot has already been claimed by someone else.

---

## Claims

### POST /api/claims
```json
{ "eventId": "...", "destinationZoneId": "..." }
```
Checks eligibility via the organization's reallocation policy (below), then
**immediately approves the claim if the slot is still available** — strict
first-come-first-served. `destinationZoneId` and the resulting proximity
rank are stored as informational data only and never influence who wins.

### GET /api/claims?eventId={id}
List claims for a given event.

### PATCH /api/claims/{id}/approve
### PATCH /api/claims/{id}/reject
Admin-only manual override (e.g. to reopen a slot after rejecting a claim).

---

## Swap Agreements

### POST /api/swaps
```json
{
  "userBId": "...",
  "slotAId": "...",
  "slotBId": "...",
  "recurrenceType": "WEEKLY",
  "dayOfWeek": "WEDNESDAY"
}
```
`recurrenceType` is one of `ONE_TIME` (needs `startDate`), `WEEKLY` (needs
`dayOfWeek`), or `DATE_RANGE` (needs `startDate` + `endDate`).

### GET /api/swaps
List swaps you're a party to.

### PATCH /api/swaps/{id}/accept
Only the invited user (`userB`) can accept. Exchanges the two slots'
current holder immediately.

### PATCH /api/swaps/{id}/cancel
Either participant or an admin. Reverts slots to their original owners if
the swap was already active.

---

## Reallocation policy summary (Strategy Pattern)

| Organization type   | Who can claim a released slot            |
|----------------------|---------------------------------------------|
| `COLLEGE`            | Other `FACULTY` / `STAFF`                    |
| `SHOPPING_COMPLEX`   | `CUSTOMER` users (not other staff)           |
| `APARTMENT`          | Other `RESIDENT` users                       |

Implemented as a `ReallocationPolicy` interface with one implementation per
organization type (`backend/.../service/reallocation/`), selected at runtime
by `ReallocationPolicyFactory` based on the organization's `type` — no
if/else chains.

---

## Organization isolation

Every endpoint that reads or writes org-scoped data (zones, slots, events,
claims, swaps, other users) derives the organization from the authenticated
user attached to the JWT — never from a client-supplied `orgId` in the
request body or query string. Cross-organization access attempts fail with
`403 Forbidden`.

---

## Error format

All errors return:
```json
{ "error": "human-readable message" }
```
with the appropriate HTTP status (400, 401, 403, 404, 500).
