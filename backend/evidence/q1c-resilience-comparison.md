# DA3 Q1.C Before vs. After Resilience Comparison Report

**Experiment Date:** 2026-10-27T20:03:40 to 2026-10-27T20:07:52+05:30  
**Target API:** `GET http://localhost:8088/api/orgs/public` (Public Organization Query)  
**Load Testing Tool:** `k6` v2.3.0 (`grafana/k6:latest`) on Docker network `da3_default`  
**Test Workload:** 800 iterations shared across 10 VUs (`shared-iterations` executor)  
**Failure Injection:** `da3-backend-2-1` stopped via `docker stop` at T+3.1s during active traffic flow  

---

## 1. Measured Comparative Results Table

| Metric / Parameter | BEFORE: Retries Disabled (`proxy_next_upstream off`) | AFTER: Retries Enabled (`proxy_next_upstream error timeout http_502 http_503; proxy_next_upstream_tries 3;`) | Impact / Variance |
| :--- | :---: | :---: | :--- |
| **Total Requests Sent** | **800** | **800** | Identical test volume (800 requests) |
| **Concurrency Level** | **10 VUs** | **10 VUs** | Identical load pressure |
| **Traffic Start Time** | `2026-10-27T20:03:40.798+05:30` | `2026-10-27T20:07:36.934+05:30` | Validated test intervals |
| **Node Interruption Time** | `2026-10-27T20:03:44.185+05:30` | `2026-10-27T20:07:40.112+05:30` | Stopped at T+3.3s and T+3.1s during active flow |
| **Node Stop Confirmed** | `2026-10-27T20:03:49.539+05:30` | `2026-10-27T20:07:45.548+05:30` | `Exited (137)` verified |
| **Successful Requests (200 OK)** | **797 (99.62%)** | **800 (100.00%)** | **+3 requests recovered (+0.38% improvement)** |
| **Failed Requests (5xx Errors)** | **3 (0.37% failure rate)** | **0 (0.00% failure rate)** | **100% elimination of client errors** |
| **HTTP Status Code Breakdown** | • HTTP 200 OK: **797**<br>• HTTP 502 Bad Gateway: **3** | • HTTP 200 OK: **800**<br>• HTTP 502 Bad Gateway: **0** | **Zero client-visible 502 errors** |
| **Total Test Duration** | **8.8s** | **11.3s** | Retries introduce modest retry wait time |
| **Throughput (req/s)** | **91.34 req/s** | **70.88 req/s** | Retry negotiation bounds throughput slightly |
| **Average Latency (avg)** | **107.30 ms** | **139.21 ms** | +31.91 ms (safe retry handshake) |
| **Median Latency (p50)** | **50.76 ms** | **59.81 ms** | +9.05 ms |
| **90th Percentile (p90)** | **180.84 ms** | **191.62 ms** | +10.78 ms |
| **Maximum Tail Latency (max)** | **2.41s** (fast failure return) | **4.53s** (retry failover latency) | Upstream connection timeout & retry |
| **Raw Evidence Artifact** | [`q1c-before-retries-disabled-raw.txt`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/evidence/q1c-before-retries-disabled-raw.txt) | [`q1c-after-retries-enabled-raw.txt`](file:///d:/Smart%20Parking%20Allocation%20System/parking-system%20%282%29/parking-system/backend/evidence/q1c-after-retries-enabled-raw.txt) | Verified raw k6 outputs |

---

## 2. Configuration Differences

### Before: Unprotected Reverse Proxy
In `backend/nginx/nginx.conf`:
```nginx
upstream backend_cluster {
    server backend-1:8080;
    server backend-2:8080;
    server backend-3:8080;
}

location / {
    proxy_pass http://backend_cluster;
    ...
    proxy_next_upstream off;
    proxy_connect_timeout 2s;
    proxy_read_timeout 10s;
}
```

### After: Resilient Cluster Configuration
In `backend/nginx/nginx.conf`:
```nginx
upstream backend_cluster {
    server backend-1:8080 max_fails=3 fail_timeout=10s;
    server backend-2:8080 max_fails=3 fail_timeout=10s;
    server backend-3:8080 max_fails=3 fail_timeout=10s;
}

location / {
    proxy_pass http://backend_cluster;
    ...
    proxy_next_upstream error timeout http_502 http_503;
    proxy_next_upstream_tries 3;
    proxy_connect_timeout 2s;
    proxy_read_timeout 10s;
}
```

---

## 3. Nginx Upstream Log Comparison

### Before: Unhandled Socket Drop (HTTP 502 returned to Client)
When `da3-backend-2-1` stopped under `proxy_next_upstream off`:
```text
2026/10/27 14:33:47 [error] 75#75: *174 connect() failed (111: Connection refused) while connecting to upstream, client: 172.19.0.7, server: , request: "GET /api/orgs/public HTTP/1.1", upstream: "http://172.19.0.4:8080/api/orgs/public", host: "nginx"
2026/10/27 14:33:47 [error] 75#75: *172 connect() failed (111: Connection refused) while connecting to upstream, client: 172.19.0.7, server: , request: "GET /api/orgs/public HTTP/1.1", upstream: "http://172.19.0.4:8080/api/orgs/public", host: "nginx"
2026/10/27 14:33:47 [error] 75#75: *175 connect() failed (111: Connection refused) while connecting to upstream, client: 172.19.0.7, server: , request: "GET /api/orgs/public HTTP/1.1", upstream: "http://172.19.0.4:8080/api/orgs/public", host: "nginx"
172.19.0.7 - - [27/Oct/2026:14:33:47 +0000] "GET /api/orgs/public HTTP/1.1" 502 157 "-" "Grafana k6/2.3.0"
172.19.0.7 - - [27/Oct/2026:14:33:47 +0000] "GET /api/orgs/public HTTP/1.1" 502 157 "-" "Grafana k6/2.3.0"
172.19.0.7 - - [27/Oct/2026:14:33:47 +0000] "GET /api/orgs/public HTTP/1.1" 502 157 "-" "Grafana k6/2.3.0"
```
Because failover retries were turned off, Nginx immediately surfaced `502 Bad Gateway` to the caller for all in-flight connections.

### After: Transparent Failover (HTTP 200 returned to Client)
When `da3-backend-2-1` stopped under `proxy_next_upstream error timeout http_502 http_503`:
```text
172.19.0.7 - - [27/Oct/2026:14:37:49 +0000] "GET /api/orgs/public HTTP/1.1" 200 12 "-" "Grafana k6/2.3.0"
172.19.0.7 - - [27/Oct/2026:14:37:49 +0000] "GET /api/orgs/public HTTP/1.1" 200 12 "-" "Grafana k6/2.3.0"
172.19.0.7 - - [27/Oct/2026:14:37:49 +0000] "GET /api/orgs/public HTTP/1.1" 200 12 "-" "Grafana k6/2.3.0"
```
Nginx detected the connection close, automatically transferred the requests to `backend-1` and `backend-3`, and temporarily ejected `backend-2` from the active rotation for 10 seconds. Every request returned HTTP 200 with 0 errors.

---

## 4. Post-Test Cluster Restoration & Health Verification

1. **Backend Restart:** `docker start da3-backend-2-1` was executed.
2. **Spring Boot Startup:** Started cleanly on port 8080 in 19.416 seconds (`Started ParkingSystemApplication in 19.416 seconds`).
3. **Cluster Health Check:**
   - `da3-nginx-1`: Up (port 8088 -> 80)
   - `da3-backend-1-1`: Up
   - `da3-backend-2-1`: Up
   - `da3-backend-3-1`: Up
   - `da3-db-1`: Up (healthy)
4. **Load Balancing Distribution Probe:**
   Six sequential requests confirmed full 3-node round-robin recovery:
   ```text
   Request 1: HTTP/1.1 200 | X-Served-By: 172.19.0.5:8080 (backend-3)
   Request 2: HTTP/1.1 200 | X-Served-By: 172.19.0.4:8080 (backend-2)  <-- RECOVERED
   Request 3: HTTP/1.1 200 | X-Served-By: 172.19.0.3:8080 (backend-1)
   Request 4: HTTP/1.1 200 | X-Served-By: 172.19.0.5:8080 (backend-3)
   Request 5: HTTP/1.1 200 | X-Served-By: 172.19.0.4:8080 (backend-2)  <-- RECOVERED
   Request 6: HTTP/1.1 200 | X-Served-By: 172.19.0.3:8080 (backend-1)
   ```
