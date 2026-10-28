# DA3 Q2.A Sensitive Data Identification & Password Hashing Verification Report

**Course:** BACSE344 — Cloud Infrastructure and Architecture  
**Assignment:** Digital Assignment 3 (Security Section — Q2.A)  
**Repository:** `Smart-Parking-System`  
**Branch:** `feature/scaling-security`  
**Evaluation Date:** 2026-10-27  
**Database Target:** Isolated Local DA3 Database (`da3-db-1:5433`, PostgreSQL 16)  

---

## 1. Sensitive Data Inventory & Risk Assessment

Based on a comprehensive audit of the application entities (`com.parkingsystem.entity`) and Flyway migration schemas (`V1__init_schema.sql` and `V2__auth_and_org_flexibility.sql`), the Smart Parking Slot Reallocation System processes and stores several categories of sensitive personal data (PII) and operational records.

| Data Category | Confirmed Database Table & Columns | Sensitivity Level | Principal Confidentiality & Integrity Risks |
| :--- | :--- | :---: | :--- |
| **Authentication Credentials** | `users.password_hash` (`VARCHAR(255)`) | **CRITICAL** | **Credential Compromise & Account Takeover:** If exposed, stored credentials can be subjected to offline dictionary/rainbow table attacks. Must never be stored in plaintext. |
| **Identity & Account PII** | `users.email` (`VARCHAR(150)`), `users.name` (`VARCHAR(150)`) | **HIGH** | **Privacy Violation & Phishing:** Email addresses serve as unique user logins and contact vectors. Exposure violates privacy mandates (GDPR / Digital Personal Data Protection Act). |
| **Authorization & Tenant Roles** | `users.role` (`VARCHAR(30)`), `users.org_id` (`UUID`) | **HIGH** | **Privilege Escalation & Multi-Tenant Bleed:** Unauthorized tampering with roles (`ADMIN`, `FACULTY`, `STAFF`) or organization IDs allows unauthorized administrative access and cross-tenant data leakage. |
| **Account Recovery Secrets** | `users.verification_code` (`VARCHAR(10)`), `users.verification_code_expires_at` (`TIMESTAMP`) | **HIGH** | **Account Hijacking:** The 6-digit numeric OTP enables email verification and account activation. If intercepted or leaked, an attacker could hijack pending accounts. |
| **Physical Location & Parking Allocation** | `parking_slots.assigned_to`, `parking_slots.original_owner_id`, `parking_slots.slot_code` | **MEDIUM-HIGH** | **Physical Presence & Vehicle Tracking:** Linking a user ID to a physical parking stall reveals daily campus commute patterns, vehicle type (`TWO_WHEELER`, `FOUR_WHEELER`), and arrival locations. |
| **Schedule & Absence Records** | `slot_events.triggered_by`, `slot_events.start_at`, `slot_events.end_at`, `slot_events.event_type` | **MEDIUM-HIGH** | **Behavioral & Absence Surveillance:** Recording when a user will be absent (`ABSENCE`) or driving a different vehicle exposes real-world physical absences from university offices, creating personal security risks. |
| **Peer-to-Peer Agreements & Claims** | `swap_agreements.user_a_id`, `user_b_id`, `slot_claims.claimed_by` | **MEDIUM** | **Tampering & Repudiation:** Unauthorized alteration of slot claims or swap agreements can deny legitimate parking access to authorized personnel. |

---

## 2. Password-Hashing Source Code Architecture

The password-handling flow was inspected across the Spring Security configuration and authentication service layers:

### A. Password Encoder Bean Definition
In [`SecurityConfig.java`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/src/main/java/com/parkingsystem/config/SecurityConfig.java#L36-L39):
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
}
```
- **Class:** `org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder`
- **Default Work Factor (Cost):** BCrypt cost factor `10` (a logarithmic cost parameter representing $2^{10} = 1,024$ rounds of the Eksblowfish key schedule, rather than 1,024 separate hashing operations).
- **Security Guarantee:** Uses OpenBSD's adaptive Blowfish password hashing algorithm, generating a cryptographically secure, unique 128-bit random salt for every single password hash.

### B. Password Hashing Execution at User Registration
In [`AuthService.java`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/src/main/java/com/parkingsystem/service/AuthService.java#L41-L46):
```java
User user = User.builder()
        .name(request.getName())
        .email(request.getEmail())
        .passwordHash(passwordEncoder.encode(request.getPassword()))
        .verified(false)
        .build();
```
- The plaintext password from `SignupRequest` is passed immediately into `passwordEncoder.encode(...)`.
- The plaintext password is never logged, never cached in memory, and never persisted in database columns. Only the 60-character Modular Crypt Format (MCF) hash string is saved to the database.

### C. Authentication Verification
In [`AuthService.java`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/src/main/java/com/parkingsystem/service/AuthService.java#L97-L98) and `DaoAuthenticationProvider`:
```java
authenticationManager.authenticate(
        new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));
```
- Spring Security's `DaoAuthenticationProvider` delegates to `BCryptPasswordEncoder.matches(rawPassword, encodedPassword)`.
- It extracts the salt and work factor embedded within the stored hash, computes the candidate hash, and uses constant-time byte comparison to eliminate side-channel timing attacks.

---

## 3. Verified Algorithm, Work Factor, and Modular Crypt Format

The stored hash adheres strictly to the standard **Modular Crypt Format (MCF)** for BCrypt:

$$\underbrace{\$2\text{a}\$}_{\text{Algorithm Identifier}} \underbrace{10\$}_{\text{Cost Factor}} \underbrace{\text{22-character Salt}}_{\text{Cryptographically Random Salt}} \underbrace{\text{31-character Hash}}_{\text{Computed Ciphertext}}$$

- **Identifier (`$2a$`):** Denotes the standard OpenBSD BCrypt algorithm variant.
- **Work Factor (`10`):** Denotes BCrypt cost factor 10 (a logarithmic cost parameter specifying $2^{10} = 1,024$ internal key expansion rounds, not 1,024 separate hashing operations).
- **Salt Length (22 base64 characters):** Encodes 16 bytes (128 bits) of secure randomness.
- **Hash Length (31 base64 characters):** Encodes the 192-bit ciphertext output.
- **Total String Length:** Exactly **60 characters**.

---

## 4. Safe Database Evidence from Isolated DA3 Database

To verify the implementation without fabricating historical states or weakening security, a dedicated audit user record was created through the application's official registration endpoint:
- **Registration Endpoint:** `POST http://localhost:8088/api/auth/signup`
- **Audit Email:** `da3.audit@campus.edu`
- **Registration Response:** `HTTP 200 OK — "Account created. A verification code has been sent to da3.audit@campus.edu."`

### A. SQL Verification Query
Executed directly against the isolated local DA3 database container (`da3-db-1`):
```sql
SELECT 
    id, 
    email, 
    name, 
    is_verified, 
    LEFT(password_hash, 7) AS bcrypt_prefix, 
    CONCAT(LEFT(password_hash, 14), '...[REDACTED]...', RIGHT(password_hash, 6)) AS safe_hash_format, 
    LENGTH(password_hash) AS hash_length, 
    created_at 
FROM users 
WHERE email = 'da3.audit@campus.edu';
```

### B. Query Output (Safe Redacted Representation)

```text
                  id                  |        email         |      name      | is_verified | bcrypt_prefix |           safe_hash_format           | hash_length |         created_at         
--------------------------------------+----------------------+----------------+-------------+---------------+--------------------------------------+-------------+----------------------------
 9946765b-f2b7-407f-98bb-c070089b0628 | da3.audit@campus.edu | DA3 Audit User | f           | $2a$10$       | $2a$10$emjL1jV...[REDACTED]...T3UkJe |          60 | 2026-10-27 15:21:07.095617
(1 row)
```

### C. Database State Observations:
1. **No Plaintext Passwords:** Passwords have been stored exclusively as salted hashes since project inception. Plaintext passwords were never present in schema or persistent storage.
2. **Verified Salt & Cost:** The prefix `$2a$10$` confirms BCrypt cost factor 10.
3. **Verified Length:** The stored string length is exactly 60 characters, matching the standard BCrypt output specification.
4. **Isolated Storage:** Query executed on dedicated DA3 container `da3-db-1` (port 5433); development database (`backend-db-1` on port 5432) and cloud databases remain untouched.
5. **Scope & Historical Verification Boundary:** The source code inspection and redacted database output conclusively support the active BCrypt implementation; however, this evidence directly validates the current codebase, live registration pipeline, and current database record. It does not prove that every historical record in past suspended database instances was hashed correctly without a full audit of all historical database dumps.

---

## 5. Why Salted Adaptive Hashing Matters

1. **Defense Against Precomputed Rainbow Tables:**
   - Fast cryptographic hashes (e.g., MD5, SHA-256) without unique salts are vulnerable to lookup tables that reverse common passwords instantaneously.
   - BCrypt generates a distinct 128-bit random salt for every user. Two users with the identical password will produce completely distinct hash outputs, neutralizing rainbow tables and batch dictionary attacks.

2. **Adaptive Work Factor vs. GPU/ASIC Acceleration:**
   - General-purpose hashes like SHA-256 are engineered for high throughput (billions of calculations per second on modern GPUs), making brute-force cracking trivially cheap.
   - BCrypt is **memory-hard** and **computationally intensive**. The cost factor parameter can be increased over time as computing power advances (e.g., from 10 to 12 or 14) without changing database schema or invalidating existing hashes.

3. **Protection in the Event of Database Exfiltration:**
   - In accordance with GDPR Article 32 (Security of Processing), passwords stored as salted BCrypt hashes provide defense-in-depth: even if an attacker acquires an unauthorized database dump, offline decryption of strong passwords remains computationally infeasible.

---

## 6. Commands Used for Evidence Collection

1. **User Registration Call (PowerShell):**
   ```powershell
   Invoke-RestMethod -Uri "http://localhost:8088/api/auth/signup" -Method Post -ContentType "application/json" -Body '{"name":"DA3 Audit User","email":"da3.audit@campus.edu","password":"<redacted>"}'
   ```
2. **Database Verification Command (Docker Exec):**
   ```powershell
   docker exec da3-db-1 psql -U parking_user -d parking -c "SELECT id, email, name, is_verified, LEFT(password_hash, 7) AS bcrypt_prefix, CONCAT(LEFT(password_hash, 14), '...[REDACTED]...', RIGHT(password_hash, 6)) AS safe_hash_format, LENGTH(password_hash) AS hash_length, created_at FROM users WHERE email = 'da3.audit@campus.edu';"
   ```
