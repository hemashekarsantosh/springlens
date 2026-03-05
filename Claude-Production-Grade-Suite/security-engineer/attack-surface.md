# Attack Surface Inventory — SpringLens

**Classification:** Exposed | Protected | Internal | Restricted

---

## Network Topology

```
┌─────────────────────────────────────────────────────────────┐
│ INTERNET (Active Attackers)                                 │
├─────────────────────────────────────────────────────────────┤
│  GitHub OAuth                 Stripe Webhooks  Browser       │
│        ↓                             ↓            ↓            │
├──── HTTPS / TLS ────────────────────────────────────────────┤
│  API Gateway (ALB) — Rate Limiting + WAF (if configured)    │
├──────────────────────────────────────────────────────────────┤
│ ┌──────────────────────────────────────────────────────────┐ │
│ │ VPC / Private Network                                    │ │
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ Service A: Ingestion (8081) — API Key Auth          │ │ │
│ │ │   POST /v1/ingest                                    │ │ │
│ │ │   GET  /v1/snapshots/{id}/status                    │ │ │
│ │ │   GET  /v1/snapshots/{id}/budget-check              │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ Service B: Analysis (8082) — JWT Auth               │ │ │
│ │ │   GET /v1/projects/{id}/snapshots                   │ │ │
│ │ │   GET /v1/projects/{id}/snapshots/{id}/timeline     │ │ │
│ │ │   GET /v1/projects/{id}/snapshots/{id}/bean-graph   │ │ │
│ │ │   GET /v1/projects/{id}/compare                     │ │ │
│ │ │   GET /v1/workspaces/{id}/overview                  │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ Service C: Recommendation (8083) — JWT Auth         │ │ │
│ │ │   GET  /v1/projects/{id}/recommendations            │ │ │
│ │ │   PATCH /v1/projects/{id}/recommendations/{id}/status │ │
│ │ │   GET  /v1/projects/{id}/ci-budgets                 │ │ │
│ │ │   PUT  /v1/projects/{id}/ci-budgets                 │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ Service D: Auth (8084) — GitHub OAuth + JWT         │ │ │
│ │ │   GET  /v1/auth/github/callback ← EXPOSED           │ │ │
│ │ │   POST /v1/billing/webhooks/stripe ← EXPOSED        │ │ │
│ │ │   GET  /v1/workspaces/{id}                          │ │ │
│ │ │   GET  /v1/workspaces/{id}/members                  │ │ │
│ │ │   POST /v1/workspaces/{id}/members                  │ │ │
│ │ │   GET  /v1/workspaces/{id}/projects                 │ │ │
│ │ │   POST /v1/workspaces/{id}/projects                 │ │ │
│ │ │   GET  /v1/workspaces/{id}/api-keys                 │ │ │
│ │ │   POST /v1/workspaces/{id}/api-keys                 │ │ │
│ │ │   DELETE /v1/workspaces/{id}/api-keys/{id}          │ │ │
│ │ │   /internal/validate-key ← INTERNAL ONLY            │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ Service E: Notification (8085) — Internal Consumer  │ │ │
│ │ │   GET  /v1/workspaces/{id}/webhooks                 │ │ │
│ │ │   POST /v1/workspaces/{id}/webhooks                 │ │ │
│ │ │   PUT  /v1/workspaces/{id}/webhooks/{id}            │ │ │
│ │ │   DELETE /v1/workspaces/{id}/webhooks/{id}          │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ │ ┌──────────────────────────────────────────────────────┐ │ │
│ │ │ Data Layer                                           │ │ │
│ │ │  • PostgreSQL (Port 5432) — Encrypted conn, no auth │ │ │
│ │ │  • Redis (Port 6379) — Idempotency cache, no auth   │ │ │
│ │ │  • Kafka (Port 9092) — Event topics, no TLS         │ │ │
│ │ └──────────────────────────────────────────────────────┘ │ │
│ └──────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

---

## Exposed Endpoints (No Auth Required)

### 1. POST /v1/auth/github/callback
**Service:** Auth Service (Port 8084)
**Parameters:** `code`, `state` (optional)
**Returns:** { access_token, refresh_token, token_type, expires_in, user: { id, email, display_name, avatar_url } }
**Threat Model:** OAuth callback
**Threats:**
- CSRF (state parameter not validated) — CRITICAL
- Redirect to attacker-controlled GitHub → GitHub redirects to attacker, bypassing SpringLens entirely — LOW (GitHub validates redirect_uri)
- User enumeration (invalid code returns 400, valid code returns 200) — ACCEPTED

**Risk:** CRITICAL

---

### 2. POST /v1/billing/webhooks/stripe
**Service:** Auth Service (Port 8084)
**Parameters:** JSON payload (Stripe event) + Stripe-Signature header
**Returns:** { status: "received" } or { status: "error", message }
**Threat Model:** Webhook ingestion (Stripe)
**Threats:**
- Signature verification NOT implemented — attacker forges billing events (invoice.paid, subscription.deleted, subscription.updated) — CRITICAL
- Webhook replay attack — same event processed multiple times if no idempotency — MEDIUM
- Information disclosure in error message (line 66) — MEDIUM

**Risk:** CRITICAL

---

### 3. GET /actuator/health
**Service:** All services (Spring Boot Actuator)
**Returns:** { status: "UP" }
**Threat Model:** Health check
**Threats:**
- Service enumeration (attacker learns which services are running) — LOW
- No secrets exposed — SAFE

**Risk:** LOW

---

### 4. GET /v1/healthz
**Service:** All services (custom health check)
**Returns:** "ok"
**Threat Model:** Custom health check
**Threats:**
- Same as /actuator/health — LOW

**Risk:** LOW

---

## Protected Endpoints (Authentication Required)

### Ingestion Service (8081) — API Key Auth

**Endpoints:**
1. **POST /v1/ingest**
   - Auth: Bearer <API_KEY>
   - Input: StartupSnapshot + beans[]
   - Returns: { snapshot_id, deduplicated }
   - **Threats:**
     - Rate limiting missing (DoS) — HIGH
     - Idempotency window replay (60 seconds) — MEDIUM
     - API key brute force (no rate limiting) — MEDIUM

2. **GET /v1/snapshots/{snapshotId}/status**
   - Auth: Bearer <API_KEY>
   - Returns: { status, created_at, processed_at }
   - **Threats:**
     - Information disclosure (status reveals processing progress) — LOW
     - Snapshot ID enumeration (can guess UUIDs) — MEDIUM

3. **GET /v1/snapshots/{snapshotId}/budget-check?budget_ms=2000**
   - Auth: Bearer <API_KEY>
   - Returns: { passed, actual_startup_ms, budget_ms }
   - **Threats:**
     - No input validation on budget_ms (negative, overflow) — LOW

---

### Analysis Service (8082) — JWT Auth

**Endpoints:**
1. **GET /v1/projects/{projectId}/snapshots[?page=1&size=10]**
   - Auth: Bearer <JWT>
   - Returns: Page<StartupSnapshotSummary>
   - **Threats:**
     - **CRITICAL:** No project_id filter — any workspace member sees all projects' snapshots
     - **HIGH:** Page size unbounded — attacker requests pageSize=999999
     - Cross-workspace access — mitigated (workspace_id validated in SecurityContext)

2. **GET /v1/projects/{projectId}/snapshots/{snapshotId}/timeline**
   - Auth: Bearer <JWT>
   - Returns: TimelineResponse { phases, beans[], timing }
   - **Threats:**
     - **CRITICAL:** No project_id filter
     - Proprietary info leak (bean names, class names) — MEDIUM

3. **GET /v1/projects/{projectId}/snapshots/{snapshotId}/bean-graph**
   - Auth: Bearer <JWT>
   - Returns: BeanGraphResponse { nodes, edges, bottlenecks }
   - **Threats:**
     - **CRITICAL:** No project_id filter
     - Reverse engineering risk (build dependencies visible) — MEDIUM

4. **GET /v1/projects/{projectId}/compare?baseline={id}&target={id}**
   - Auth: Bearer <JWT>
   - Returns: SnapshotComparisonResponse { delta, changed_beans, added, removed }
   - **Threats:**
     - **CRITICAL:** No project_id filter
     - Snapshot enumeration (can compare any snapshot pair) — MEDIUM

5. **GET /v1/workspaces/{workspaceId}/overview**
   - Auth: Bearer <JWT>
   - Returns: WorkspaceOverviewResponse { projects, avg_startup_ms, last_7_days_trend }
   - **Threats:**
     - All workspace members see aggregate data (acceptable) — LOW

---

### Recommendation Service (8083) — JWT Auth

**Endpoints:**
1. **GET /v1/projects/{projectId}/recommendations[?status=active]**
   - Auth: Bearer <JWT>
   - Returns: Recommendation[]
   - **Threats:**
     - **CRITICAL:** No project_id filter
     - Status disclosure (which recommendations have been applied) — MEDIUM

2. **PATCH /v1/projects/{projectId}/recommendations/{recommendationId}/status**
   - Auth: Bearer <JWT>
   - Input: { status: "applied" | "wont_fix" | "active" }
   - Returns: { recommendation_id, status }
   - **Threats:**
     - **CRITICAL:** No project_id filter
     - **HIGH:** No workspace member role check (any member can update recommendations)

3. **GET /v1/projects/{projectId}/ci-budgets**
   - Auth: Bearer <JWT>
   - Returns: CiBudget[] { environment, budget_ms, alert_threshold_ms }
   - **Threats:**
     - **CRITICAL:** No project_id filter
     - CI configuration enumeration — MEDIUM

4. **PUT /v1/projects/{projectId}/ci-budgets**
   - Auth: Bearer <JWT>
   - Input: { environment, budget_ms, alert_threshold_ms, enabled }
   - Returns: CiBudget
   - **Threats:**
     - **CRITICAL:** No project_id filter + no role check
     - **CRITICAL:** Non-admin can modify production budgets (disabling enforcement)
     - Budget manipulation attack — HIGH

---

### Auth Service (8084) — Multiple Auth Methods

**Protected Endpoints:**
1. **GET /v1/workspaces/{workspaceId}**
   - Auth: Bearer <JWT> (workspace member)
   - Returns: Workspace { id, name, plan, limits }
   - **Threats:**
     - Workspace ID enumeration (can guess UUIDs) — LOW
     - Plan information leakage (reveals Pro vs Free) — ACCEPTABLE

2. **GET /v1/workspaces/{workspaceId}/members**
   - Auth: Bearer <JWT> (workspace member)
   - Returns: WorkspaceMember[] { user_id, email, role, created_at }
   - **Threats:**
     - Admin enumeration (reveals which users are admins) — MEDIUM

3. **POST /v1/workspaces/{workspaceId}/members**
   - Auth: Bearer <JWT> (admin only, not enforced in code)
   - Input: { email, role }
   - Returns: WorkspaceMember
   - **Threats:**
     - **CRITICAL:** No visible role check (depends on JWT role claim)
     - Email enumeration (can invite non-existent emails) — MEDIUM

4. **GET /v1/workspaceId}/projects**
   - Auth: Bearer <JWT> (workspace member)
   - Returns: Project[]
   - **Threats:**
     - None

5. **POST /v1/workspaceId}/projects**
   - Auth: Bearer <JWT> (workspace member)
   - Input: { name, environment }
   - Returns: Project
   - **Threats:**
     - Plan limit enforcement (planProjectLimit) — MEDIUM (only checked, not enforced in code)

6. **GET /v1/workspaceId}/api-keys**
   - Auth: Bearer <JWT> (workspace member)
   - Returns: ApiKey[] { id, name, key_prefix, last_used_at }
   - **Threats:**
     - Any workspace member can see all API keys (even if created by others) — MEDIUM

7. **POST /v1/workspaceId}/api-keys**
   - Auth: Bearer <JWT> (workspace member)
   - Input: { name }
   - Returns: { api_key_id, raw_key } (raw key only once)
   - **Threats:**
     - **HIGH:** No rate limiting (attacker creates 1000 keys)
     - **MEDIUM:** Raw key transmitted in JSON (ensure HTTPS)

8. **DELETE /v1/workspaceId}/api-keys/{keyId}**
   - Auth: Bearer <JWT> (workspace member)
   - Returns: { deleted }
   - **Threats:**
     - **MEDIUM:** Any workspace member can revoke any key (even if created by others)

---

### Notification Service (8085) — JWT Auth

**Endpoints:**
1. **GET /v1/workspaces/{workspaceId}/webhooks**
   - Auth: Bearer <JWT> (workspace member)
   - Returns: WebhookConfig[] { id, type, url (encrypted), enabled }
   - **Threats:**
     - Workspace member sees all webhooks — ACCEPTABLE

2. **POST /v1/workspaces/{workspaceId}/webhooks**
   - Auth: Bearer <JWT> (workspace member)
   - Input: { type, url, enabled }
   - Returns: WebhookConfig
   - **Threats:**
     - **HIGH:** No rate limiting (create 1000 webhooks)
     - **CRITICAL:** No URL validation (attacker can register http://auth-service:8084/v1/billing/webhooks/stripe)
     - **MEDIUM:** URL must be encrypted before storage (ensure encryption key is used)

3. **PUT /v1/workspaces/{workspaceId}/webhooks/{webhookId}**
   - Auth: Bearer <JWT> (workspace member)
   - Input: { url, enabled }
   - Returns: WebhookConfig
   - **Threats:**
     - Same as POST

4. **DELETE /v1/workspaces/{workspaceId}/webhooks/{webhookId}**
   - Auth: Bearer <JWT> (workspace member)
   - Returns: { deleted }
   - **Threats:**
     - Workspace member can delete any webhook (disabling notifications) — ACCEPTABLE

---

## Internal Endpoints (Service-to-Service)

### 1. GET /internal/validate-key?key={apiKey}
**Service:** Auth Service (Port 8084)
**Called by:** Ingestion Service (ApiKeyAuthFilter)
**Auth:** CIDR-based (10.0.0.0/8, 172.16.0.0/12, 127.0.0.1)
**Returns:** { workspace_id, project_id } or error
**Threats:**
- IP spoofing (if attacker gains network access to VPC) — NETWORK CONCERN (DevOps skill)
- CIDR validation brittle (assumes flat network) — MEDIUM

---

## Kafka Topics (Service-to-Service Events)

### 1. Topic: startup.events
**Producer:** Ingestion Service
**Consumer:** Analysis Service
**Schema:** StartupEvent { snapshot_id, project_id, workspace_id, beans[], phases[] }
**Threats:**
- **MEDIUM:** No message signing (consumer cannot verify producer)
- **MEDIUM:** Unencrypted (network sniffing exposes telemetry)
- **MEDIUM:** No access control (any consumer can read, if connected to Kafka)
- **LOW:** Replay attack (consumer should be idempotent)

---

### 2. Topic: analysis.complete
**Producer:** Analysis Service
**Consumer:** Recommendation Service
**Schema:** AnalysisCompleteEvent { snapshot_id, project_id, workspace_id, timeline, bean_graph }
**Threats:**
- Same as startup.events
- **MEDIUM:** Kafka consumer lag attack (if processing is slow)

---

### 3. Topic: recommendations.ready
**Producer:** Recommendation Service
**Consumer:** Notification Service
**Schema:** RecommendationsReadyEvent { snapshot_id, recommendations[] }
**Threats:**
- Same as above
- **MEDIUM:** Webhook delivery side effects (recomm-notif is one-way; no feedback to recommendation service if webhook fails)

---

## Webhook Callbacks (Outbound)

### Slack Webhook
**Type:** External HTTP POST
**Endpoint:** Slack incoming webhook URL (https://hooks.slack.com/services/...)
**Payload:** { blocks: [...] } (Block Kit JSON)
**Threats:**
- **MEDIUM:** URL stored encrypted; key in env var (not Secrets Manager)
- **MEDIUM:** No delivery confirmation from Slack (fire-and-forget)
- **LOW:** Slack webhook URL enumeration (if leaked)

---

### GitHub PR Comment
**Type:** External HTTP POST
**Endpoint:** GitHub API v2022-11-28 (https://api.github.com/repos/{owner}/{repo}/issues/{pr}/comments)
**Payload:** { body: "# Startup Optimization Recommendations..." }
**Threats:**
- **MEDIUM:** Recommendations posted publicly on PR (may leak proprietary info)
- **MEDIUM:** GitHub API token (if stored) is high-value target
- **LOW:** PR identification risk (attacker can learn project structure from PR numbers)

---

### Custom Webhook
**Type:** User-defined HTTP POST
**Endpoint:** User-provided URL
**Payload:** Same as Slack
**Threats:**
- **CRITICAL:** No URL validation (attacker registers http://auth-service:8084/v1/billing/webhooks/stripe)
- **MEDIUM:** User can register webhook to internal IP (http://10.0.1.5:8084/...)
- **MEDIUM:** Webhook timeout (if endpoint hangs, retries consume resources)

---

## Frontend Attack Surface (Next.js 14)

### Client-Side Exposure
**Technology:** JavaScript/React in browser
**Entry Points:**
1. **Session Storage:** next-auth session object + accessToken
2. **LocalStorage:** Any user data cached (if code stores it)
3. **XSS Vectors:** Recommendation descriptions, bean names, error messages
4. **CSRF:** Form submissions without CSRF token (next-auth middleware handles)

**Threats:**
- **HIGH:** XSS attack steals accessToken from session
- **MEDIUM:** CSRF on form submissions (if CSRF token validation fails)
- **MEDIUM:** Sensitive data in localStorage (browser storage vulnerable to XSS)

---

## Summary: Attack Surface by Classification

| Classification | Count | Examples | Risk Level |
|---|---|---|---|
| **Exposed (0 auth)** | 4 | /v1/auth/github/callback, /v1/billing/webhooks/stripe, /actuator/health, /v1/healthz | CRITICAL |
| **Protected (JWT/API Key)** | 20+ | All /v1/projects, /v1/workspaces, /v1/recommendations endpoints | HIGH |
| **Internal (CIDR)** | 1 | /internal/validate-key | MEDIUM |
| **Kafka Topics** | 3 | startup.events, analysis.complete, recommendations.ready | MEDIUM |
| **Webhooks (Outbound)** | 3 | Slack, GitHub, Custom | HIGH |
| **Frontend (Browser)** | 1 | Dashboard UI + next-auth session | HIGH |

