# DA3 Q2.D: Threat Agents & Regulatory Analysis Report

**Project:** Smart Parking Slot Reallocation System
**Repository Branch:** `assignment-3`
**Execution Environment:** Isolated DA3 Docker Stack (Nginx load balancer `:8088`, 3 Spring Boot instances `:8080`, PostgreSQL 16 `:5433`)
**Evaluation Date:** October 28, 2026
**Assignment Reference:** BACSE344 Cloud Infrastructure and Architecture — Digital Assignment 3 (DA3), Question 2.D

---

## 1. Executive Summary & Asset Classification

The **Smart Parking Slot Reallocation System** manages shared and reserved parking spaces across campuses and commercial complexes. While its primary function is operational efficiency, the application collects, stores, and processes sensitive personal and institutional data across its database tables.

### Target Assets & Data Elements:
1. **Authentication Credentials & Cryptographic Secrets:**
   - Password hashes (`users.password_hash`): BCrypt cost factor 10.
   - Multi-Factor Authentication Secrets (`users.totp_secret`): RFC 4648 Base32 encoded HMAC-SHA1 keys.
   - Verification Tokens (`users.verification_code`): Short-lived OTP codes for email verification.
2. **Personally Identifiable Information (PII):**
   - User identity: Full names (`users.name`), personal/institutional emails (`users.email`), default vehicle types (`users.default_vehicle_type`).
   - Organizational affiliation: Tenant mapping (`users.org_id`, `organizations.name`, `organizations.type`).
3. **Absence & Behavioral Movement Patterns:**
   - Absence events (`slot_events.start_at`, `slot_events.end_at`, `slot_events.event_type`): Discloses when a specific faculty member, student, or tenant is physically away from campus or their residence.
   - Temporary vehicle reassignment (`slot_events.temp_vehicle_slot_id`).
4. **Allocation & Reallocation Business Records:**
   - Permanent slot ownership (`parking_slots.assigned_to`, `parking_slots.original_owner_id`).
   - Dynamic reallocation claims (`slot_claims.claimed_by`, `slot_claims.status`, `slot_claims.destination_zone_id`).
   - Inter-user swapping agreements (`swap_agreements.user_a_id`, `swap_agreements.user_b_id`).
5. **System & Host Infrastructure:**
   - Spring Boot runtime environment, JVM process, Nginx proxy configuration, and PostgreSQL database instance.

---

## 2. Threat Agent 1: Malicious Insider / Rogue Student or Tenant (Authorized Low-Privilege User)

### A. Threat Actor Profile & Motivation
- **Actor:** A legitimately enrolled student, campus resident, or low-privilege customer with valid credentials and an authenticated session.
- **Motivation:** Resource monopolization (e.g., squatting on coveted parking slots near academic blocks, reserving premium four-wheeler spaces, bypassing the daily fairness lottery), curiosity, or academic friction against faculty/administration.

### B. Target Assets & Attack Path
- **Target Assets:** `parking_slots` (slot availability and assignments), `slot_events` (faculty absence calendars), `slot_claims`, and `swap_agreements`.
- **Attack Path:**
  1. **Privilege Escalation / IDOR:** The attacker attempts to invoke administrative endpoints (such as `DELETE /api/slots/{id}` or `PUT /api/users/{id}`) by guessing or enumerating UUIDs.
  2. **Absence Reconnaissance & Slot Monopolization:** The insider inspects `slot_events` to monitor when senior faculty members are traveling, immediately generating automated or scripted claim requests (`POST /api/slots/claims`) before legitimate commuters can request them.
  3. **Cross-Tenant Tampering:** An attacker in Organization A attempts to query or reassign slots belonging to Organization B by supplying foreign `slot_id` parameters.

### C. Likely Impact (CIA Triad)
- **Integrity (High):** Unauthorized reassignment or deletion of slots disrupts the automated reallocation schedule, corrupting database mapping records.
- **Availability (High):** Legitimate faculty, staff, and disabled commuters are denied access to parking spaces due to slot squatting or griefing.
- **Confidentiality (Medium):** Exposure of faculty absence schedules creates a physical and digital privacy risk, enabling tracking of faculty travel routines.

### D. Existing Controls Verified in Code & Live Tests
1. **Method-Level Role-Based Access Control (RBAC):**
   - Configured via `@PreAuthorize("hasRole('ADMIN')")` on critical modification and deletion endpoints (`SlotController.deleteSlot`, `UserController.updateUser`, `UserController.deleteUser`).
   - Verified live in Stage 8A: Authenticated users with role `CUSTOMER` or `STUDENT` attempting administrative operations receive **HTTP 403 Forbidden** (`{"error":"Access Denied"}`).
2. **Multi-Tenant Isolation Enforced at Service Layer:**
   - Enforced by `OrgGuard.require(requester)` and `SlotService.findAndCheckSameOrg(requester, slotId)`.
   - Verified live in Stage 8A: Even an authenticated `ADMIN` cannot access or delete slots belonging to another organization, strictly receiving **HTTP 403 Forbidden** (`{"error":"Cannot access a slot from a different organization"}`).
3. **Session Token Constraints:**
   - JWT tokens include strict role claims (`role`) and 24-hour expiration (`exp`), preventing elevation without re-authentication.

### E. Additional Recommended Controls
> [!IMPORTANT]
> The following controls are **RECOMMENDED** enhancements for future hardening and are not currently implemented:
> - **[RECOMMENDATION] Anti-Hoarding Rate Limiting:** Enforce strict per-user rate limits on `/api/slots/claims` (e.g., maximum 5 claims per hour via Bucket4j or Nginx `limit_req_zone`) to prevent script-driven slot reservation.
> - **[RECOMMENDATION] Privacy-Masked Absence Views:** Restrict visibility of `slot_events` so students only see aggregate slot availability flags (`AVAILABLE`), while redacting the identity (`triggered_by`), license details, and full travel itinerary of the absent faculty member.
> - **[RECOMMENDATION] Comprehensive Audit Logging:** Maintain immutable audit trails recording all slot claims, overrides, and swaps with user ID, client IP, and timestamp.

### F. Relevant Regulation & Legal Requirement
- **Digital Personal Data Protection Act, 2023 (India) — Section 8(5) & Section 8(7):**
  - **Statutory Status & Applicability:** Section 8(5) of the DPDPA 2023 establishes a statutory obligation for a Data Fiduciary to maintain "reasonable security safeguards" to prevent personal data breaches, while Section 8(7) mandates data erasure once the purpose is served. However, under the Ministry of Electronics and Information Technology (MeitY) phased commencement notification (S.O. 5218(E) dated November 13, 2025), Section 8(5) is scheduled to come into force on **May 13, 2027**. Consequently, as of October 2026, Section 8(5) is an enacted statutory standard rather than an immediately operative, legally enforceable duty. Implementing RBAC, tenant isolation, and absence visibility restrictions aligns the system proactively with the enacted statutory architecture prior to its formal enforcement date.

---

## 3. Threat Agent 2: External Opportunistic Attacker / Credential Stuffer (Unauthenticated Remote Threat)

### A. Threat Actor Profile & Motivation
- **Actor:** An unauthenticated external cybercriminal or automated botnet operator scanning Internet-facing IP ranges and web applications.
- **Motivation:** Financial extortion (credential harvesting for resale, ransomware deployment), server compute hijacking (cryptomining), or lateral pivot into the campus internal network.

### B. Target Assets & Attack Path
- **Target Assets:** User credential repository (`users.email`, `users.password_hash`), MFA secrets (`users.totp_secret`), host OS, and container runtime environment.
- **Attack Path:**
  1. **Automated Credential Stuffing:** The attacker launches automated dictionary or stuffing attacks against `/api/auth/login` using breached username/password pairs harvested from third-party leaks.
  2. **MFA Bypass Attempt:** If a valid password is found, the attacker attempts to access protected business endpoints directly or intercept/forge pre-authentication tokens.
  3. **Container Breakout via Unpatched Vulnerabilities:** The attacker scans the public reverse proxy or backend runtime for known OS library vulnerabilities (e.g. `CVE-2026-111497` in `brotli-libs`) to achieve remote code execution and escape as `root`.

### C. Likely Impact (CIA Triad)
- **Confidentiality (High):** Unauthorized exfiltration of the entire `users` table exposes hundreds of campus email addresses, vehicle categories, and password hashes.
- **Availability (High):** Execution of ransomware or resource-exhaustion denial-of-service could render the parking allocation infrastructure completely unreachable.
- **Integrity (High):** Malicious manipulation of database migrations, slot availability, or administrative accounts.

### D. Existing Controls Verified in Code & Live Tests
1. **Strong Password Hashing:**
   - Password hashes generated via Spring Security's `BCryptPasswordEncoder(10)` (verified cost factor 10, per-user 16-byte random salt). Prevents practical precomputation or rainbow-table attacks.
2. **Two-Factor Authentication (TOTP MFA) with Pre-Auth Confinement:**
   - RFC 6238 TOTP implementation verified live in Stage 8/8A.
   - Two-step login protocol: Valid password submission returns `token: null` and an ephemeral `tempToken` (`pre_auth: true`, 5-minute TTL).
   - Verified live in Stage 8A: `JwtAuthFilter` strictly blocks `tempToken` from accessing any protected business endpoints (**HTTP 401 Unauthorized**). Full session JWT is released **only after validating the live 6-digit TOTP code**.
   - Verified live in Stage 8A: `/api/auth/mfa/setup` requires full authentication, binds enrollment to caller's persistent record, and rejects setup on already-enrolled accounts (**HTTP 400 Bad Request**), guaranteeing secrets are never re-exposed.
3. **HTTP 401 vs. 403 Security Differentiation:**
   - Verified live in Stage 8A: Missing or malformed JWTs strictly return **HTTP 401 Unauthorized** via `AuthenticationEntryPoint`, preventing endpoint structure discovery.
4. **Container Hardening & Vulnerability Remediation:**
   - Verified live in Stage 9: Process executes as dedicated non-root user `appuser:appgroup` (UID 10001, GID 10001).
   - Verified live in Stage 9: Minimal Alpine runtime with `apk upgrade --no-cache` achieves **0 HIGH** and **0 CRITICAL** vulnerabilities in comparative Trivy scans, completely eliminating `CVE-2026-111497`.

### E. Additional Recommended Controls
> [!IMPORTANT]
> The following controls are **RECOMMENDED** enhancements for future hardening and are not currently implemented:
> - **[RECOMMENDATION] Account Lockout & Exponential Backoff:** Implement temporary account lockout (e.g., 5 consecutive failed login attempts locks account for 15 minutes) or CAPTCHA verification on `/api/auth/login` to thwart distributed credential stuffing.
> - **[RECOMMENDATION] Production TLS 1.3 Termination:** Enforce HTTPS with strict transport security (HSTS) on the Nginx reverse proxy. (Currently HTTP on port 8088 in the local isolated DA3 evaluation stack).
> - **[RECOMMENDATION] Envelope Encryption for TOTP Secrets:** Encrypt `totp_secret` at rest using an AES-256 Key Encryption Key (KEK) managed via a dedicated secrets vault (e.g., HashiCorp Vault or AWS KMS) rather than plain varchar in PostgreSQL.

### F. Relevant Regulation & Legal Requirement
- **Digital Personal Data Protection Act, 2023 (India) — Section 8(6) & Section 6:**
  - **Statutory Status & Applicability:** Section 8(6) of the DPDPA 2023 requires the Data Fiduciary to notify the **Data Protection Board of India (DPBI)** and all affected Data Principals in the event of a personal data breach. Under the November 13, 2025 commencement notification (S.O. 5218(E)), Section 8(6) is scheduled to take effect on **May 13, 2027**. While breach reporting to the DPBI is not an operative statutory obligation in October 2026, establishing robust technical controls (such as non-root execution and zero-vulnerability container baselines) satisfies baseline security due diligence in anticipation of the 2027 enforcement milestone.

---

## 4. Privacy & Regulatory Compliance Assessment

### A. Primary Law: India's Digital Personal Data Protection Act (DPDPA), 2023

Since the Smart Parking Slot Reallocation System is designed and operated within an Indian university/college environment, the **Digital Personal Data Protection Act, 2023 (Act No. 22 of 2023)** represents the enacted national data protection framework.

> [!NOTE]
> **Commencement Timeline Clarification (Gazette Notification S.O. 5218(E)):**
> Under the phased commencement notification issued by MeitY on November 13, 2025, foundational definitions and provisions concerning the Board's establishment took initial effect, whereas substantive fiduciary obligations under **Section 8(5) (Security Safeguards)** and **Section 8(6) (Breach Reporting)** are appointed to commence on **May 13, 2027**. Therefore, in October 2026, these provisions serve as enacted compliance benchmarks rather than active, enforceable liability triggers.

| DPDPA 2023 Section | Statutory Requirement | Application to Smart Parking System | Current Implementation & Legal Status |
| :--- | :--- | :--- | :--- |
| **Section 2(i) & 2(j)** | Definitions of Data Fiduciary and Data Principal | The campus institution operating the system is the **Data Fiduciary**; students, faculty, and staff are **Data Principals**. | Governance model established. |
| **Section 5** | Notice of Collection | Must inform users before or at collection of the exact data collected (email, name, vehicle category, absence dates) and purpose. | *Partial:* Data collected at signup; full formal privacy notice is a **recommended** UI addition. |
| **Section 6** | Lawful Consent | Processing must be based on free, specific, informed, unconditional, and unambiguous consent. | *Implemented:* Users register explicitly and verify email via verification codes. |
| **Section 8(5)** | Reasonable Security Safeguards | Legal duty to implement appropriate technical and organizational safeguards to prevent personal data breaches. | **Enacted (Commencement: May 13, 2027):** Proactively verified via BCrypt cost 10, RFC 6238 TOTP MFA, RBAC, tenant isolation, non-root Docker, zero HIGH/CRITICAL Trivy vulnerabilities. |
| **Section 8(6)** | Mandatory Breach Notification | In the event of a breach, must notify the Data Protection Board of India (DPBI) and affected users. | **Enacted (Commencement: May 13, 2027):** Breach notification policy is a **recommended** procedural safeguard. |
| **Section 8(7)** | Purpose Limitation & Erasure | Data must be deleted once the purpose is served (e.g. user graduation/resignation) or consent is withdrawn. | Database foreign key cascades implemented (`ON DELETE CASCADE`); automated account expiration is **recommended**. |

---

### B. Conditional Law: European Union General Data Protection Regulation (GDPR)

The European Union General Data Protection Regulation (Regulation (EU) 2016/679) **does NOT automatically apply** to this system simply because it stores personal data.

#### Scope Analysis (GDPR Article 3 — Territorial Scope):
1. **Article 3(1) (Establishment in the EU):** Does not apply. The data controller (university campus) is established and operates exclusively within India.
2. **Article 3(2) (Extra-Territorial Reach):** Applies to non-EU controllers **only if** the processing activities are related to:
   - *Art. 3(2)(a):* The offering of goods or services (whether paid or free) to data subjects located in the European Union; OR
   - *Art. 3(2)(b):* The monitoring of the behavior of data subjects as far as their behavior takes place within the European Union.

#### Conditional Applicability Conclusion:
- **Baseline Default:** GDPR is **NOT applicable** to ordinary domestic campus parking operations in India. International students and foreign faculty physically residing in India and parking on campus are governed by Indian law (DPDPA 2023) for processing occurring within Indian territory.
- **Conditions Triggering GDPR Applicability:**
  - GDPR would apply **only if** the university specifically markets international exchange programs or pre-booked visitor parking directly to individuals residing in EU member states (e.g. providing EUR payment gateways, EU-targeted advertisements, or localized EU language landing pages), OR if the application tracks behavioral telemetry of students while they are physically located inside the EU prior to traveling to India.

---

### C. Explicit Inapplicability: Health Insurance Portability and Accountability Act (HIPAA)

- **Finding:** The **Health Insurance Portability and Accountability Act of 1996 (HIPAA)** (45 CFR Parts 160 and 164) is **COMPLETELY INAPPLICABLE** to this project.
- **Legal Rationale:** HIPAA applies strictly to "Covered Entities" (health plans, healthcare clearinghouses, and healthcare providers transmitting electronic health transactions) and their designated "Business Associates" that create, receive, maintain, or transmit Protected Health Information (PHI).
- A smart parking allocation system processes vehicle classifications, slot identifiers, and general absence schedules for campus logistics. It does not process medical diagnoses, clinical treatments, disability medical certificates, or health insurance claims. Claiming HIPAA compliance for an academic parking system is legally invalid.

---

## 5. Comparative Threat Agent Summary

| Threat Dimension | Threat Agent 1: Malicious Insider | Threat Agent 2: External Opportunistic Attacker |
| :--- | :--- | :--- |
| **Actor Identity** | Authenticated Student / Campus Tenant | Unauthenticated Remote Cybercriminal / Botnet |
| **Motivation** | Parking spot monopolization, schedule reconnaissance | Credential theft, extortion, compute hijacking |
| **Target Data** | `parking_slots`, `slot_events` (absence calendars) | `users` table, password hashes, TOTP secrets, host OS |
| **Primary CIA Impact** | Integrity (slot corruption) & Availability (squatting) | Confidentiality (data exfiltration) & Availability (ransom) |
| **Verified Controls** | RBAC (`@PreAuthorize`), `OrgGuard`, 403 Access Denied | BCrypt 10, TOTP MFA, non-root user, 0 Trivy CVEs |
| **Recommended Controls** | Anti-hoarding rate limiting, absence calendar masking | Login lockout/backoff, TLS 1.3/HSTS, encrypted secrets |
| **Key Regulation** | India DPDPA 2023 Section 8(5) (Security Safeguards) | India DPDPA 2023 Section 8(6) (Breach Reporting) |

---

## 6. Sources & Legal References

1. **Digital Personal Data Protection Act, 2023 (DPDPA):**
   - The Gazette of India, Extraordinary, Part II—Section 1, No. 22, August 11, 2023. Ministry of Law and Justice, Government of India.
   - Sections referenced: Sec 2 (Definitions), Sec 5 (Notice), Sec 6 (Consent), Sec 8 (Obligations of Data Fiduciary), Sec 33 (Penalties).
   - **Commencement Notification:** Ministry of Electronics and Information Technology (MeitY), Notification S.O. 5218(E), dated November 13, 2025, published in the Gazette of India, Extraordinary, Part II—Section 3(ii), establishing the phased implementation timeline appointing **May 13, 2027** for Section 8(5) and Section 8(6).
2. **General Data Protection Regulation (GDPR):**
   - Regulation (EU) 2016/679 of the European Parliament and of the Council of 27 April 2016 on the protection of natural persons with regard to the processing of personal data.
   - Articles referenced: Art. 3 (Territorial scope), Art. 4 (Definitions), Art. 32 (Security of processing).
3. **Health Insurance Portability and Accountability Act (HIPAA):**
   - 45 CFR Part 160 and Part 164, Subparts A and E (Privacy Rule), Subpart C (Security Rule). U.S. Department of Health and Human Services (HHS).
4. **OWASP API Security Top 10 (2023 Edition):**
   - API1:2023 Broken Object Level Authorization (BOLA), API2:2023 Broken Authentication, API5:2023 Broken Function Level Authorization (BFLA).

---

## 7. Commit & Push Status
- In compliance with strict project safety rules, **no Git commit or push has been executed**.
- No application code, database structures, or cloud resources were modified during this stage.
