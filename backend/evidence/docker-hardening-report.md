# DA3 Q2.C: Docker Image Hardening & Vulnerability Scan Comparison Report

**Project:** Smart Parking Slot Reallocation System
**Repository Branch:** `assignment-3`
**Execution Environment:** Isolated DA3 Docker Stack (Nginx load balancer `:8088`, 3 Spring Boot instances `:8080`, PostgreSQL 16 `:5433`)
**Evaluation Date:** October 28, 2026
**Assignment Reference:** BACSE344 Cloud Infrastructure and Architecture — Digital Assignment 3 (DA3), Question 2.C

---

## 1. Executive Summary

This report documents the security hardening of the backend container image (`backend/Dockerfile`) and provides an empirical before-and-after vulnerability comparison using Aqua Security's **Trivy** vulnerability scanner.

### Key Achievements:
1. **Elimination of All High and Critical OS Vulnerabilities:**
   - **Baseline Scan:** 1 HIGH vulnerability (`CVE-2026-111497` in `brotli-libs 1.2.0-r1`), 0 CRITICAL.
   - **Hardened Scan:** **0 HIGH**, **0 CRITICAL** vulnerabilities detected. Clean scan achieved.
2. **Principle of Least Privilege (Non-Root Execution):**
   - **Baseline Runtime:** Container ran as `root` (UID 0), posing significant privilege escalation risk.
   - **Hardened Runtime:** Application executes strictly as a dedicated, unprivileged system user (`appuser:appgroup`, UID 10001, GID 10001). Verified at container entrypoint and live in the DA3 cluster.
3. **Compatibility & Operational Continuity Preserved:**
   - Maintained Java 21 runtime (`eclipse-temurin:21-jre-alpine`), Spring Boot 3.3.4 startup, embedded Tomcat on port 8080, and file permissions.
   - All 3 backend instances behind the Nginx load balancer (`http://localhost:8088`) were verified functional and healthy.

---

## 2. Baseline Security Profile (Before Hardening)

- **Evidence Source:** [`backend/evidence/trivy-baseline.txt`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/evidence/trivy-baseline.txt)
- **Target Image:** `smart-parking-backend:test`
- **Scan Timestamp:** 2026-10-27T01:36:22+05:30 (UTC: 2026-10-26T20:06:22Z)
- **Base OS:** Alpine Linux 3.24.3 (73 OS packages scanned)
- **Scanner Command:**
  ```bash
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache/ \
    aquasec/trivy:latest image --scanners vuln --pkg-types os --severity HIGH,CRITICAL \
    smart-parking-backend:test
  ```
- **Findings:**
  - **Total Vulnerabilities:** 1 (HIGH: 1, CRITICAL: 0)
  - **Identified Flaw:** `CVE-2026-111497` (Out-of-Bounds Write in Google Brotli Decoder) in `brotli-libs 1.2.0-r1` (fixed in `1.2.1-r0`).
- **Runtime User:** `root` (UID 0).

---

## 3. Hardening Implementation: `backend/Dockerfile`

The following minimal, security-focused modifications were applied to [`backend/Dockerfile`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/Dockerfile):

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Upgrade OS packages to apply security patches (e.g. brotli-libs)
# Create dedicated non-root user and group (appuser:appgroup, UID/GID 10001)
RUN apk update && apk upgrade --no-cache && \
    addgroup -S appgroup -g 10001 && \
    adduser -S appuser -u 10001 -G appgroup && \
    chown -R appuser:appgroup /app

COPY --from=build --chown=appuser:appgroup /app/target/parking-system.jar app.jar

USER appuser:appgroup

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Rationale:
1. **`apk update && apk upgrade --no-cache`**: Immediately upgrades base Alpine OS libraries to their latest patched versions in the Alpine 3.24 branch, upgrading `brotli-libs` from `1.2.0-r1` to `1.2.1-r0` and resolving `CVE-2026-111497`.
2. **`addgroup -S appgroup -g 10001 && adduser -S appuser -u 10001 -G appgroup`**: Establishes a dedicated, predictable non-root system identity with specific UID/GID 10001, avoiding collision with standard system accounts.
3. **`COPY --chown=appuser:appgroup`**: Grants explicit read permissions on the application artifact to the unprivileged runtime user.
4. **`USER appuser:appgroup`**: Instructs the Docker daemon and runtime engine to drop all root privileges prior to launching the JVM.

---

## 4. Verification & Live Evidence

### A. Non-Root Execution Verification
Verified directly against the built image:
```bash
docker run --rm --entrypoint sh smart-parking-backend:hardened -c "whoami && id"
```
**Live Output:**
```
appuser
uid=10001(appuser) gid=10001(appgroup) groups=10001(appgroup)
```

Verified inside the running DA3 container (`da3-backend-1-1`):
```bash
docker exec da3-backend-1-1 id
```
**Live Output:**
```
uid=10001(appuser) gid=10001(appgroup) groups=10001(appgroup)
```

Verified in the JVM startup log:
```
c.p.ParkingSystemApplication : Starting ParkingSystemApplication v1.0.0 using Java 21.0.12.1 with PID 1 (/app/app.jar started by appuser in /app)
```

---

### B. Trivy Vulnerability Scan (After Hardening)
- **Evidence Source:** [`backend/evidence/trivy-hardened.txt`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/evidence/trivy-hardened.txt)
- **Target Image:** `smart-parking-backend:hardened`
- **Scan Timestamp:** 2026-10-28T00:07:58+05:30 (UTC: 2026-10-27T18:37:58Z)
- **Base OS:** Alpine Linux 3.24.3 (73 OS packages scanned)
- **Scanner Command:**
  ```bash
  docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -v trivy-cache:/root/.cache/ \
    aquasec/trivy:latest image --skip-db-update --scanners vuln --pkg-types os --severity HIGH,CRITICAL \
    smart-parking-backend:hardened
  ```
- **Vulnerability Database Comparability Note:**
  When attempting an online database refresh during the hardened image scan, Trivy encountered an upstream network connection error (`mirror.gcr.io/aquasec/trivy-db:2: unexpected EOF`). Rather than waiting indefinitely or risking database schema divergence, Trivy was executed using `--skip-db-update` targeting the persistent `trivy-cache` Docker volume populated during the baseline run. This guarantees an exact, controlled comparison against the identical CVE definitions, confirming that the reduction of `CVE-2026-111497` is genuinely attributable to the Alpine package upgrade (`brotli-libs 1.2.0-r1` $\rightarrow$ `1.2.1-r0`) rather than vulnerability database drift.
- **Live Scanner Output:**
  ```text
  Report Summary

  ┌────────────────────────────────────────────────┬────────┬─────────────────┐
  │                     Target                     │  Type  │ Vulnerabilities │
  ├────────────────────────────────────────────────┼────────┼─────────────────┤
  │ smart-parking-backend:hardened (alpine 3.24.3) │ alpine │        0        │
  └────────────────────────────────────────────────┴────────┴─────────────────┘
  Legend:
  - '-': Not scanned
  - '0': Clean (no security findings detected)

  smart-parking-backend:hardened (alpine 3.24.3)
  ==============================================
  Total: 0 (HIGH: 0, CRITICAL: 0)
  ```

---

## 5. Before vs. After Comparison Table

| Metric / Dimension | Baseline Image (`smart-parking-backend:test`) | Hardened Image (`smart-parking-backend:hardened`) | Delta / Improvement |
| :--- | :--- | :--- | :--- |
| **Runtime User** | `root` (UID 0) | `appuser` (UID 10001) | **Privilege dropped to non-root** |
| **Runtime Group** | `root` (GID 0) | `appgroup` (GID 10001) | **Dedicated group** |
| **Base Image** | `eclipse-temurin:21-jre-alpine` | `eclipse-temurin:21-jre-alpine` | Preserved minimal JRE base |
| **Java Version** | 21.0.12.1 (Temurin) | 21.0.12.1 (Temurin) | Preserved runtime compatibility |
| **Critical Vulnerabilities** | 0 | 0 | Unchanged (0) |
| **High Vulnerabilities** | 1 (`CVE-2026-111497`) | **0** | **-1 (100% reduction)** |
| **Total High/Critical Flaws** | 1 | **0** | **Clean scan achieved** |
| **DA3 Cluster Functionality** | Functional behind Nginx `:8088` | **Functional behind Nginx `:8088`** | Fully operational |

---

## 6. DA3 Cluster Operational Verification

All 5 services in the isolated DA3 stack were tested with the hardened image:
- **`da3-nginx-1`**: Listening on `0.0.0.0:8088->80/tcp`, proxying requests across backends.
- **`da3-backend-1-1`**: Up, running as `appuser:appgroup`.
- **`da3-backend-2-1`**: Up, running as `appuser:appgroup`.
- **`da3-backend-3-1`**: Up, running as `appuser:appgroup`.
- **`da3-db-1`**: PostgreSQL 16 container healthy on port 5433.

Sample live endpoint check through Nginx:
```bash
curl.exe -i -s -X POST http://localhost:8088/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@campus.edu","password":"SecureAdmin123!"}'
```
**Response:** `HTTP 200 OK` (Served by backend instance running as `appuser`).

---

## 7. Commit & Push Status
- In compliance with strict project safety rules, **no Git commit or push has been executed**.
- All modifications and scans were conducted exclusively on the local isolated DA3 environment.
- Cloud platforms (Render, Vercel) and development containers remain completely untouched.
