# DA3 Q2.B: Multi-Factor Authentication (MFA) & Role-Based Access Control (RBAC) Verification Report

**Project:** Smart Parking Slot Reallocation System  
**Repository Branch:** `feature/scaling-security`  
**Execution Environment:** Isolated DA3 Docker Stack (Nginx load balancer `:8088`, 3 Spring Boot instances `:8080`, PostgreSQL 16 `:5433`)  
**Evaluation Date:** October 27, 2026  
**Assignment Reference:** BACSE344 Cloud Infrastructure and Architecture — Digital Assignment 3 (DA3), Question 2.B

---

## 1. Executive Summary

This report documents the implementation, hardening, and live verification of **Two-Factor Authentication (TOTP MFA)** and **Role-Based Access Control (RBAC)** in the Smart Parking Slot Reallocation System, satisfying all criteria for DA3 Q2.B and Stage 8A:

1. **401 Unauthorized vs. 403 Forbidden Differentiation:**
   - Unauthenticated requests lacking a JWT or presenting an invalid/expired JWT strictly return **HTTP 401 Unauthorized** with structured JSON: `{"error":"Full authentication is required to access this resource"}`.
   - Authenticated requests where the user identity is valid but lacks the required authority (e.g. `CUSTOMER` attempting administrative deletion) strictly return **HTTP 403 Forbidden** with structured JSON: `{"error":"Access Denied"}`.
   - Preserves all existing security protections, filter-chain ordering, and public endpoints (`/api/auth/**` login/signup/verify).

2. **MFA Enrollment Security & Account Binding:**
   - Standard RFC 6238 Time-Based One-Time Password algorithm (HMAC-SHA1, 30-second window, 6 decimal digits, RFC 4648 Base32 keys).
   - `/api/auth/mfa/setup` requires a fully authenticated session (unauthenticated calls return **HTTP 401 Unauthorized**).
   - MFA enrollment is strictly bound to the authenticated caller's persistent database record (`userRepository.findById(requester.getId())`), preventing parameter tampering or enrollment hijacking.
   - **Secret Protection:** If MFA is already active on an account, `/api/auth/mfa/setup` returns **HTTP 400 Bad Request** (`{"error":"MFA is already enabled on this account. To reconfigure, disable it first."}`), strictly guaranteeing that an existing TOTP secret is never re-exposed.
   - **Two-Step Login Protocol:** Primary password verification issues an ephemeral pre-auth token (`tempToken` with `"pre_auth": true`, 5-minute TTL) with `token: null`. Pre-auth tokens are intercepted and blocked by `JwtAuthFilter` from accessing any protected business endpoints. Full JWT session tokens are issued **only after cryptographically validating the live 6-digit TOTP code**.

3. **Authorized Operation & Multi-Tenant Isolation Verification:**
   - Tested using a disposable test record (`SLOT-DISPOSABLE-01`, UUID: `22222222-2222-2222-2222-222222222222`) created solely within the isolated DA3 database (`da3-db-1`).
   - Unauthorized access is blocked at multiple layers:
     - Unauthenticated callers receive **HTTP 401 Unauthorized**.
     - Non-admin authenticated users (`CUSTOMER`) receive **HTTP 403 Forbidden** (`{"error":"Access Denied"}`).
   - Authorized `ADMIN` within the same organization successfully executes the permitted operation, returning **HTTP 200 OK** (Content-Length: 0).
   - Deletion verified in the database: slot record count reduced to 0. A repeat deletion attempt correctly returns **HTTP 404 Not Found** (`{"error":"Slot not found: ..."}`).
   - **Tenant Isolation:** When an authorized `ADMIN` attempts to access/delete a slot belonging to a different organization, service-level guard `findAndCheckSameOrg` intercepts the call and returns **HTTP 403 Forbidden** (`{"error":"Cannot access a slot from a different organization"}`).

---

## 2. Architecture & Implementation Details

### A. Database Migration: `V3__add_totp_mfa.sql`
A dedicated Flyway migration was added at [`backend/src/main/resources/db/migration/V3__add_totp_mfa.sql`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/src/main/resources/db/migration/V3__add_totp_mfa.sql):
```sql
ALTER TABLE users ADD COLUMN IF NOT EXISTS mfa_enabled BOOLEAN DEFAULT FALSE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS totp_secret VARCHAR(64);
```
Applied automatically on DA3 cluster launch and verified against `da3-db-1`.

### B. RFC 6238 TOTP Utility: `TotpUtil.java`
Implemented pure Java RFC 6238 compliance in [`backend/src/main/java/com/parkingsystem/security/TotpUtil.java`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/src/main/java/com/parkingsystem/security/TotpUtil.java):
- **Secret Generation:** Cryptographically secure 20-byte random keys encoded into 32-character Base32 strings.
- **HMAC Calculation:** `HmacSHA1` dynamic truncation over 8-byte big-endian time step counter (`T = floor(currentTime / 30)`).
- **Clock Drift Tolerance:** Validates current window plus adjacent past and future windows ($\pm 1$ step, $\pm 30$ seconds) to absorb client-server clock drift.
- **URI Formatter:** Generates standard `otpauth://totp/SmartParking:{email}?secret={secret}&issuer=SmartParking` strings for Google Authenticator, Microsoft Authenticator, and 1Password.

### C. Security Exception Handling: `SecurityConfig.java`
Configured explicit `AuthenticationEntryPoint` and `AccessDeniedHandler` in [`backend/src/main/java/com/parkingsystem/config/SecurityConfig.java`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/src/main/java/com/parkingsystem/config/SecurityConfig.java) to strictly separate HTTP 401 from HTTP 403:
```java
.exceptionHandling(exceptions -> exceptions
    .authenticationEntryPoint((request, response, authException) -> {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Full authentication is required to access this resource\"}");
    })
    .accessDeniedHandler((request, response, accessDeniedException) -> {
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"Access Denied\"}");
    })
)
```

### D. Hardened MFA Service: `AuthService.java`
In [`backend/src/main/java/com/parkingsystem/service/AuthService.java`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/src/main/java/com/parkingsystem/service/AuthService.java):
- `setupMfa`, `enableMfa`, and `disableMfa` re-fetch the user entity strictly by persistent ID (`userRepository.findById(requester.getId())`), binding actions to the authenticated identity.
- `setupMfa` verifies `user.isMfaEnabled()` from the persistent database record; if true, it immediately throws `BadRequestException("MFA is already enabled on this account. To reconfigure, disable it first.")`, preventing secret overwrite or exposure.
- `enableMfa` requires valid live TOTP code verification via `TotpUtil.verifyCode` before setting `mfa_enabled = true`.

---

## 3. Live Verification Part A: HTTP 401 vs. 403 Differentiation

All probes were executed against the live DA3 Nginx load balancer at `http://localhost:8088`.

### Probe 1: Unauthenticated Request with Missing Token (HTTP 401)
Protected endpoint `/api/slots/{id}` called without `Authorization` header.

**HTTP Command:**
```bash
curl.exe -i -s -X DELETE http://localhost:8088/api/slots/22222222-2222-2222-2222-222222222222
```
**Live Response:**
```http
HTTP/1.1 401 
Server: nginx/1.31.8
Date: Tue, 27 Oct 2026 18:03:07 GMT
Content-Type: application/json
Transfer-Encoding: chunked
Connection: keep-alive
X-Served-By: 172.19.0.4:8080

{"error":"Full authentication is required to access this resource"}
```
*Result:* Missing authentication header triggers `authenticationEntryPoint`, returning **HTTP 401 Unauthorized**.

---

### Probe 2: Unauthenticated Request with Malformed/Invalid Token (HTTP 401)
Protected endpoint `/api/slots/{id}` called with invalid Bearer token.

**HTTP Command:**
```bash
curl.exe -i -s -X DELETE http://localhost:8088/api/slots/22222222-2222-2222-2222-222222222222 \
  -H "Authorization: Bearer invalid.garbage.jwt"
```
**Live Response:**
```http
HTTP/1.1 401 
Server: nginx/1.31.8
Date: Tue, 27 Oct 2026 18:03:22 GMT
Content-Type: application/json
Transfer-Encoding: chunked
Connection: keep-alive
X-Served-By: 172.19.0.3:8080

{"error":"Full authentication is required to access this resource"}
```
*Result:* Invalid signature/token is rejected and returns **HTTP 401 Unauthorized**.

---

### Probe 3: Authenticated Request with Insufficient Role (HTTP 403)
A valid authenticated user holding role `CUSTOMER` (`customer@campus.edu`) attempts to execute administrative slot deletion.

**HTTP Command:**
```bash
curl.exe -i -s -X DELETE http://localhost:8088/api/slots/22222222-2222-2222-2222-222222222222 \
  -H "Authorization: Bearer <CUSTOMER_JWT>"
```
**Live Response:**
```http
HTTP/1.1 403 
Server: nginx/1.31.8
Date: Tue, 27 Oct 2026 18:14:15 GMT
Content-Type: application/json
Transfer-Encoding: chunked
Connection: keep-alive
X-Served-By: 172.19.0.5:8080

{"error":"Access Denied"}
```
*Result:* Authentication succeeds, but `@PreAuthorize("hasRole('ADMIN')")` evaluates user role, detects absence of `ROLE_ADMIN`, and triggers `accessDeniedHandler`, returning **HTTP 403 Forbidden**.

---

## 4. Live Verification Part B: MFA Enrollment Security & Secret Protection

### Step 1: Unauthenticated MFA Setup Blocked (HTTP 401)
Unauthenticated call to `/api/auth/mfa/setup`.

**HTTP Command:**
```bash
curl.exe -i -s -X POST http://localhost:8088/api/auth/mfa/setup
```
**Live Response:**
```http
HTTP/1.1 401 
Server: nginx/1.31.8
Date: Tue, 27 Oct 2026 18:03:14 GMT
Content-Type: application/json
Transfer-Encoding: chunked
Connection: keep-alive
X-Served-By: 172.19.0.5:8080

{"error":"Full authentication is required to access this resource"}
```
*Result:* MFA enrollment endpoint requires an active authenticated session; unauthenticated callers are rejected with HTTP 401.

---

### Step 2: MFA Enrollment Bound to Authenticated Caller
When called by an authenticated user (`customer@campus.edu`), `AuthService.setupMfa` resolves the user entity from the persistent database by `requester.getId()`. It generates a fresh Base32 TOTP secret, saves it to `users.totp_secret`, and returns the enrollment payload:

**Live Response:**
```json
{
  "secret": "7L73XMUY6AENZMCKWKTEW3LQSXSE4FC7",
  "qrUri": "otpauth://totp/SmartParking:customer%40campus.edu?secret=7L73XMUY6AENZMCKWKTEW3LQSXSE4FC7&issuer=SmartParking",
  "message": "Scan QR code or enter secret into Google or Microsoft Authenticator, then call /api/auth/mfa/enable with the 6-digit code."
}
```
*Database State in `da3-db-1`:*
```sql
SELECT email, totp_secret, mfa_enabled FROM users WHERE email='customer@campus.edu';
-- customer@campus.edu | 7L73XMUY6AENZMCKWKTEW3LQSXSE4FC7 | f
```

---

### Step 3: MFA Secret Protection on Already-Enrolled Accounts
After activating MFA via valid TOTP code (`mfa_enabled = true`), a subsequent call to `/api/auth/mfa/setup` is attempted using the customer's active session.

**HTTP Command:**
```bash
curl.exe -i -s -X POST http://localhost:8088/api/auth/mfa/setup \
  -H "Authorization: Bearer <CUSTOMER_JWT>"
```
**Live Response:**
```http
HTTP/1.1 400 
Server: nginx/1.31.8
Date: Tue, 27 Oct 2026 18:05:43 GMT
Content-Type: application/json
Transfer-Encoding: chunked
Connection: keep-alive
X-Served-By: 172.19.0.4:8080

{"error":"MFA is already enabled on this account. To reconfigure, disable it first."}
```
*Security Guarantee:* The active TOTP secret is **never re-exposed** over the wire, and cannot be silently overwritten while MFA is enabled.

---

### Step 4: MFA Two-Step Login & Confinement
1. **Step 1 (Password Verification):**
   ```json
   POST /api/auth/login
   {"email":"customer@campus.edu","password":"SecureCustomer123!"}
   ```
   **Response:**
   ```json
   {
     "token": null,
     "userId": "bdd6d30a-0619-4dc7-a0c0-19c70ed59e79",
     "name": null,
     "email": "customer@campus.edu",
     "role": null,
     "orgId": null,
     "mfaRequired": true,
     "tempToken": "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJjdXN0b21lckBjYW1wdXMuZWR1IiwicHJlX2F1dGgiOnRydWUsImlhdCI6MTc5MzExODIxNywiZXhwIjoxNzkzMTE4NTE3fQ.-ij0aJ31lTMchJqJTjktoQmeMXrszY7x14KD5glMqHs"
   }
   ```
   *Session token (`token`) is strictly `null`.*
2. **Pre-Auth Confinement:**
   Invoking `/api/users/me` with `tempToken` yields **HTTP 401 Unauthorized** (`{"error":"Full authentication is required to access this resource"}`).
3. **Step 2 (TOTP Verification):**
   Submitting valid live TOTP code to `/api/auth/mfa/verify-login` issues the final session JWT.

---

## 5. Live Verification Part C: Authorized Operation & Multi-Tenant Isolation

### Setup: Disposable Test Record in Isolated DA3 Database
To verify authorized operations without polluting production or shared data, a disposable test slot was inserted directly into the dedicated DA3 PostgreSQL database (`da3-db-1`):
```sql
INSERT INTO parking_slots (id, org_id, zone_id, slot_code, vehicle_type, status)
VALUES (
  '22222222-2222-2222-2222-222222222222',
  '2d475279-2e9f-4491-9af0-f6c4d9cedfda',  -- Organization: DA3 Test Campus
  '11111111-1111-1111-1111-111111111111',  -- Zone A
  'SLOT-DISPOSABLE-01',
  'FOUR_WHEELER',
  'AVAILABLE'
);
```

---

### Step 1: Authorized ADMIN Deletes Disposable Slot (HTTP 200 OK)
An authorized user holding role `ADMIN` (`admin@campus.edu`) belonging to `DA3 Test Campus` calls the delete endpoint on `SLOT-DISPOSABLE-01`.

**HTTP Command:**
```bash
curl.exe -i -s -X DELETE http://localhost:8088/api/slots/22222222-2222-2222-2222-222222222222 \
  -H "Authorization: Bearer <ADMIN_JWT>"
```
**Live Response:**
```http
HTTP/1.1 200 
Server: nginx/1.31.8
Date: Tue, 27 Oct 2026 18:17:55 GMT
Content-Length: 0
Connection: keep-alive
X-Served-By: 172.19.0.4:8080
```
*Result:* Authorized `ADMIN` successfully executes the deletion.

---

### Step 2: Database Deletion Verified
Inspecting `da3-db-1`:
```bash
docker exec da3-db-1 psql -U parking_user -d parking \
  -c "SELECT count(*) FROM parking_slots WHERE id = '22222222-2222-2222-2222-222222222222';"
```
**Live Output:**
```
 count 
-------
     0
(1 row)
```
*Result:* Slot record count is exactly `0`.

---

### Step 3: Repeated Deletion Handled as 404 Not Found
A subsequent deletion request for the same slot by the authorized admin:

**HTTP Command:**
```bash
curl.exe -i -s -X DELETE http://localhost:8088/api/slots/22222222-2222-2222-2222-222222222222 \
  -H "Authorization: Bearer <ADMIN_JWT>"
```
**Live Response:**
```http
HTTP/1.1 404 
Server: nginx/1.31.8
Date: Tue, 27 Oct 2026 18:19:08 GMT
Content-Type: application/json
Transfer-Encoding: chunked
Connection: keep-alive
X-Served-By: 172.19.0.3:8080

{"error":"Slot not found: 22222222-2222-2222-2222-222222222222"}
```
*Result:* Correct HTTP 404 Not Found returned by business logic, confirming the record no longer exists.

---

### Step 4: Multi-Tenant Isolation Verified (Cross-Tenant Access Blocked)
To verify that administrative privileges do not bypass organization boundaries:
1. A separate organization (`Other University`, id: `99999999-9999-9999-9999-999999999999`) and slot (`SLOT-OTHER-ORG`, id: `88888888-8888-8888-8888-888888888888`) were created in `da3-db-1`.
2. The authorized admin (`admin@campus.edu`, belonging to `DA3 Test Campus`) attempted to delete the slot belonging to `Other University`.

**HTTP Command:**
```bash
curl.exe -i -s -X DELETE http://localhost:8088/api/slots/88888888-8888-8888-8888-888888888888 \
  -H "Authorization: Bearer <ADMIN_JWT>"
```
**Live Response:**
```http
HTTP/1.1 403 
Server: nginx/1.31.8
Date: Tue, 27 Oct 2026 18:21:26 GMT
Content-Type: application/json
Transfer-Encoding: chunked
Connection: keep-alive
X-Served-By: 172.19.0.5:8080

{"error":"Cannot access a slot from a different organization"}
```
*Result:* While `@PreAuthorize("hasRole('ADMIN')")` passes the role check, `SlotService.findAndCheckSameOrg` detects that the slot belongs to a different organization and enforces tenant isolation, returning **HTTP 403 Forbidden** (`{"error":"Cannot access a slot from a different organization"}`).

*Cleanup:* The temporary foreign organization record was deleted from `da3-db-1`.

---

## 6. Comprehensive Verification Checklist

| Requirement / Test Case | Security Mechanism | Expected HTTP Status | Measured Live Response | Status |
| :--- | :--- | :--- | :--- | :--- |
| **Missing JWT Authentication** | `authenticationEntryPoint` | `401 Unauthorized` | `HTTP 401 {"error":"Full authentication is required..."}` | **PASSED** |
| **Invalid/Malformed JWT** | `JwtAuthFilter` / entryPoint | `401 Unauthorized` | `HTTP 401 {"error":"Full authentication is required..."}` | **PASSED** |
| **Unauthenticated MFA Setup** | Security filter chain rule | `401 Unauthorized` | `HTTP 401 {"error":"Full authentication is required..."}` | **PASSED** |
| **Pre-Auth Token Confinement** | `JwtAuthFilter.isPreAuthToken` | `401 Unauthorized` | `HTTP 401 {"error":"Full authentication is required..."}` | **PASSED** |
| **Insufficient Role Access** | `@PreAuthorize("hasRole('ADMIN')")` | `403 Forbidden` | `HTTP 403 {"error":"Access Denied"}` | **PASSED** |
| **Cross-Tenant Access by Admin** | `SlotService.findAndCheckSameOrg` | `403 Forbidden` | `HTTP 403 {"error":"Cannot access a slot from a different organization"}` | **PASSED** |
| **MFA Secret Exposure Guard** | `AuthService.setupMfa` check | `400 Bad Request` | `HTTP 400 {"error":"MFA is already enabled..."}` | **PASSED** |
| **Invalid TOTP Activation** | `TotpUtil.verifyCode` | `400 Bad Request` | `HTTP 400 {"error":"Invalid verification code..."}` | **PASSED** |
| **MFA Password-Step Login** | Two-step protocol in `AuthService` | `200 OK` (token: null) | `HTTP 200 OK` (`token: null, tempToken: "..."`) | **PASSED** |
| **Valid TOTP Login Verification** | `verifyMfaLogin` in `AuthService` | `200 OK` with full JWT | `HTTP 200 OK` (`token: "eyJhbGci..."`) | **PASSED** |
| **Authorized Admin Slot Deletion** | RBAC + OrgGuard permitted | `200 OK` / `204` | `HTTP 200 OK` (Content-Length: 0) | **PASSED** |
| **DB Record Deletion Verified** | SQL row count query | Count = 0 | `count: 0` | **PASSED** |
| **Repeated Deletion on Slot** | Business logic not-found | `404 Not Found` | `HTTP 404 {"error":"Slot not found: ..."}` | **PASSED** |

---

## 7. Cluster Health Status After Verification

All five containers in the DA3 stack were verified operational and healthy post-test:
```
NAME              IMAGE                        COMMAND                  SERVICE     STATUS                 PORTS
da3-backend-1-1   smart-parking-backend:test   "java -jar app.jar"      backend-1   Up (healthy)           8080/tcp
da3-backend-2-1   smart-parking-backend:test   "java -jar app.jar"      backend-2   Up (healthy)           8080/tcp
da3-backend-3-1   smart-parking-backend:test   "java -jar app.jar"      backend-3   Up (healthy)           8080/tcp
da3-db-1          postgres:16                  "docker-entrypoint.s…"   db          Up (healthy)           0.0.0.0:5433->5432/tcp
da3-nginx-1       nginx:alpine                 "/docker-entrypoint.…"   nginx       Up                     0.0.0.0:8088->80/tcp
```

---

## 8. Commit and Push Status
- In compliance with strict safety directives, **no Git commit or push has been executed**.
- All modifications were tested exclusively on the isolated DA3 environment (`localhost:8088` and `da3-db-1`).
- The primary development containers (`backend-backend-1`, `backend-db-1`) and cloud environments (Render, Vercel) remain untouched.
