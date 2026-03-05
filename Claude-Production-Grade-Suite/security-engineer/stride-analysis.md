# STRIDE Threat Analysis — SpringLens

**Date:** 2026-03-05
**Compliance:** SOC2 Type II
**Threat Model:** Public SaaS with active attackers
**Status:** Phase 1 Complete - Ready for Phase 2 Code Audit

---

## Executive Summary

SpringLens is a 5-service microservices SaaS platform with event-driven architecture (Kafka), multi-tenant isolation (workspace/project), GitHub OAuth2 auth, and API key management. The threat model reflects these characteristics:

- **High-Value Targets:** Auth service (credential issuance), API keys (agent impersonation), Stripe webhooks (billing manipulation)
- **Attack Surface:** 20+ public endpoints + 3 Kafka consumer entry points + webhook callbacks
- **Trust Boundaries:** 6 critical boundaries (external→API, OAuth, service-to-service, webhook validation)
- **Data Sensitivity:** PII (email, GitHub profile), JWTs, API keys, startup telemetry (may contain proprietary build info)

---

## Service 1: Ingestion Service (Port 8081)

**Role:** External agent ingestion endpoint
**Auth:** API key (bearer token, sl_proj_* format)
**Data In:** POST /v1/ingest (startup snapshot + beans + durations)
**Kafka Out:** startup.events topic

### Spoofing Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| S-ING-001 | API key forgery/brute force | Medium | High | HIGH | BCrypt hashing (work factor 10), 32-byte random keys | ⚠️ No rate limiting on /v1/ingest endpoint; attacker can spam with invalid keys |
| S-ING-002 | Agent impersonation (key theft) | Low | Critical | HIGH | Bearer token validation, key revocation | ⚠️ No API key expiration; keys are revokable only by admin, not time-gated |
| S-ING-003 | Workspace/project context bypass | Low | High | MEDIUM | Request attributes set by filter (workspaceId, projectId) | ✓ Context injected by ApiKeyAuthFilter, used in all queries |

### Tampering Threats

| Threat ID | Threat | Likelihood | Medium | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| T-ING-001 | StartupSnapshot JSON mutation in Redis | Low | Medium | LOW | Redis ACLs (network isolation assumed) | ⚠️ Idempotency cache in Redis without encryption; if compromised, attackers can cause duplicate/replay ingestion |
| T-ING-002 | Kafka message tampering | Low | Medium | LOW | Kafka broker assumed private (cluster network) | ⚠️ No message signing; service-to-service Kafka topics unencrypted |
| T-ING-003 | Request body replay attack | Medium | High | HIGH | Idempotency-Key deduplication via Redis | ⚠️ Idempotency key = hash(projectId + environment + commitSha); no timestamp, allows indefinite replay within 60-second window |

### Repudiation Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| R-ING-001 | Denied ingestion | Low | Low | LOW | Logs include projectId, timestamp | ✓ Adequate; ingestion timestamp + snapshot ID returned to agent |
| R-ING-002 | Agent denies key usage | Low | Medium | LOW | API Key has last_used_at field | ⚠️ No request audit log; only last_used_at timestamp, not full history |

### Information Disclosure Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| I-ING-001 | API error message leakage | Medium | Medium | MEDIUM | Generic ErrorResponse class | ⚠️ BillingController returns ex.getMessage() in JSON (line 66 of BillingController) — leaks stack traces |
| I-ING-002 | Logs capture sensitive data | Medium | Medium | MEDIUM | Logs include commit SHA, env name (public) | ⚠️ Logs may capture startup snapshot data if debug level enabled; no PII redaction filter |
| I-ING-003 | API key prefix leakage | Low | Low | LOW | Key prefix (first 12 chars) logged only; full key never logged | ✓ Appropriate; prevents full key in logs |

### Denial of Service Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| D-ING-001 | /v1/ingest flood (unbounded snapshots) | High | Medium | HIGH | No rate limiting per API key | 🔴 **CRITICAL:** Attacker with valid key can flood ingestion service; no per-project or per-agent rate limit |
| D-ING-002 | Database table fill (TimescaleDB) | High | Medium | HIGH | Disk quotas (infrastructure concern) | ⚠️ Application has no limits on rows per project or per workspace |
| D-ING-003 | Kafka partition saturation | Medium | Medium | MEDIUM | Kafka broker capacity (infrastructure) | ⚠️ startup.events topic has no max message size or throughput limit enforced by service |
| D-ING-004 | /v1/healthz abuse | Low | Low | LOW | No metrics (GET endpoint, stateless) | ✓ Acceptable for health checks |

### Elevation of Privilege Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| E-ING-001 | Cross-workspace data access (API key scope) | Low | Critical | HIGH | ApiKeyAuthFilter validates workspace_id on every request; repository queries filter by workspace_id | ✓ Proper tenant isolation |
| E-ING-002 | API key privilege escalation | Low | Critical | HIGH | API keys are immutable once created; no role field (implicit AGENT role) | ✓ No privilege escalation possible with API key model |

---

## Service 2: Analysis Service (Port 8082)

**Role:** Kafka consumer + query API
**Auth:** JWT (OAuth2 resource server)
**Data In:** Kafka startup.events topic
**HTTP Endpoints:** GET /v1/projects/{id}/snapshots, GET /v1/projects/{id}/snapshots/{id}/timeline, etc.
**Kafka Out:** analysis.complete topic

### Spoofing Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| S-ANA-001 | JWT token forgery | Low | Critical | HIGH | JwtService validates with HS256 secret key | ✓ JJWT library validates signatures; cannot forge without secret |
| S-ANA-002 | JWT secret compromise | Low | Critical | CRITICAL | Secret stored in application.yml (config server) | ⚠️ Secret in environment variable; no key rotation mechanism |
| S-ANA-003 | Token replay after expiry | Low | High | MEDIUM | 15-minute access token TTL | ✓ Proper expiration; refresh token mechanism exists |

### Tampering Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| T-ANA-001 | StartupTimeline JSONB mutation in DB | Low | Medium | LOW | TimescaleDB ACLs (infrastructure) | ⚠️ Application has no immutability guarantees; timelines can be updated if code allows |
| T-ANA-002 | Kafka event tampering | Low | Medium | LOW | Kafka broker network isolation | ⚠️ No message signatures; events are plaintext JSON |
| T-ANA-003 | JWT claims tampering | Low | Critical | MEDIUM | JWT signature validation prevents tampering | ✓ Any tampering invalidates signature |

### Repudiation Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| R-ANA-001 | User denies timeline query | Low | Low | LOW | Logs include user ID, workspace ID, timestamp | ⚠️ No audit log table; only application logs (ephemeral) |

### Information Disclosure Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| I-ANA-001 | Startup telemetry leakage (proprietary build info) | Medium | High | HIGH | StartupTimeline returned to authenticated user only | ⚠️ No data classification; bean names, class names, durations may contain proprietary info; returned to any workspace member |
| I-ANA-002 | Cross-project timeline access | Low | Critical | HIGH | Repository queries filter by workspace_id; no per-project filter | 🔴 **CRITICAL:** Analysis service does not filter by project_id in TimelineRepository.findAll(). Any workspace member can view all snapshots from other projects in the workspace |
| I-ANA-003 | Error messages leak schema | Medium | Medium | MEDIUM | GlobalExceptionHandler returns generic messages | ⚠️ Some endpoints may not have proper error handling |
| I-ANA-004 | JWT claims visible in logs | Low | Low | LOW | Logs do not capture tokens | ✓ Appropriate; tokens not logged |

### Denial of Service Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|-----------|------|------|-------------------|-----|
| D-ANA-001 | GET /v1/projects/{id}/snapshots pagination abuse | High | Medium | HIGH | No page size limits enforced | 🔴 **CRITICAL:** Attacker can request pageSize=999999 and fetch entire snapshot list; no max page size |
| D-ANA-002 | Kafka consumer lag attack | Medium | Medium | MEDIUM | Kafka broker capacity | ⚠️ No backpressure handling; if startup.events floods, consumer lag grows unbounded |
| D-ANA-003 | BeanGraphAnalyzer graph complexity | Medium | Medium | MEDIUM | DAG construction in memory (no limits) | ⚠️ Beans with 10,000+ dependencies could cause CPU spike; no node limit enforced |

### Elevation of Privilege Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| E-ANA-001 | Non-admin views admin snapshots | Low | High | MEDIUM | No role-based filtering on timeline endpoint | ⚠️ All workspace members see all snapshots regardless of role |
| E-ANA-002 | User modifies timeline (if update endpoint exists) | Low | High | MEDIUM | TimelineController has no PUT/PATCH endpoints | ✓ Read-only for users |

---

## Service 3: Recommendation Service (Port 8083)

**Role:** Kafka consumer + query API
**Auth:** JWT (OAuth2 resource server)
**Data In:** Kafka analysis.complete topic
**HTTP Endpoints:** GET /v1/projects/{id}/recommendations, PATCH /v1/projects/{id}/recommendations/{id}/status, etc.
**Kafka Out:** recommendations.ready topic

### Spoofing Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| S-REC-001 | JWT token forgery | Low | Critical | HIGH | Same as Analysis Service | ✓ JJWT signature validation |
| S-REC-002 | Recommendation engine tampering (rules) | Low | Medium | LOW | RecommendationEngine code-based rules (immutable) | ✓ Rules hardcoded; cannot be modified at runtime |

### Tampering Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| T-REC-001 | Recommendation status tampering | Medium | High | HIGH | PATCH /v1/recommendations/{id}/status validates userId matches | ⚠️ Only checks userId (current user); no workspace isolation check visible |
| T-REC-002 | CI Budget tampering | Medium | High | HIGH | PUT /v1/ci-budgets only allows if workspace member | ⚠️ No admin-only flag enforced in code; relies on role claim in JWT |

### Repudiation Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| R-REC-001 | User denies recommendation update | Low | Low | LOW | Logs include user action | ⚠️ No audit log table; only application logs |

### Information Disclosure Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| I-REC-001 | Cross-project recommendation access | Low | Critical | HIGH | Recommendation queries filter by workspace_id only | 🔴 **CRITICAL:** No project_id filter; user from workspace can view recommendations for other projects |
| I-REC-002 | CI Budget leakage (per-environment settings) | Low | High | HIGH | Budget data returned per environment; no access control | ⚠️ All workspace members see all environments' budgets |
| I-REC-003 | Code snippets in error responses | Medium | Medium | MEDIUM | Recommendations contain code snippets (legitimate) | ✓ Acceptable; code snippets are intended output |

### Denial of Service Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| D-REC-001 | GET /v1/recommendations pagination abuse | High | Medium | HIGH | No page size limit | 🔴 **CRITICAL:** Same as Analysis Service |
| D-REC-002 | Kafka consumer lag | Medium | Medium | MEDIUM | No backpressure | ⚠️ Same as Analysis Service |
| D-REC-003 | RecommendationEngine CPU spike | Low | Medium | LOW | 5 recommendation rules; minimal computation | ✓ Rules are O(n) over beans; acceptable |

### Elevation of Privilege Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| E-REC-001 | Non-admin modifies CI Budget (production) | Medium | High | HIGH | "Admin-only for prod" noted in code comment; not enforced | 🔴 **CRITICAL:** RecommendationController.upsertCiBudget() has no role check; any workspace member can set production budgets |

---

## Service 4: Auth Service (Port 8084)

**Role:** Credential issuance, API key management, billing integration
**Auth:** GitHub OAuth2 → JWT issuance + API key validation endpoint
**Data In:** GitHub OAuth code + Stripe webhooks
**HTTP Endpoints:** 8 endpoints including GitHub callback, workspace management, billing
**Kafka Out:** None (consumes via internal API calls)

### Spoofing Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| S-AUTH-001 | GitHub OAuth code interception | Low | Critical | MEDIUM | Redirect URI validation by GitHub | ✓ GitHub only sends code to registered redirect_uri |
| S-AUTH-002 | GitHub token reuse (code replay) | Low | Critical | HIGH | OAuth code single-use by GitHub | ✓ GitHub invalidates code after one use |
| S-AUTH-003 | CSRF on GitHub OAuth callback | Medium | High | HIGH | OAuth state parameter should be validated | 🔴 **CRITICAL:** GitHubOAuthController accepts state param but does NOT validate it; attacker can forge callback with malicious state |
| S-AUTH-004 | API key validation endpoint spoofing | Low | Medium | LOW | ApiKeyAuthFilter calls /internal/validate-key via RestClient | ⚠️ CIDR-based IP check on /internal/* (line 33); assumes network isolation |
| S-AUTH-005 | Workspace member impersonation | Low | High | MEDIUM | Workspace member creation via OAuth + email matching | ⚠️ Email matching (line 113): if email is null from GitHub, creates new user; if attacker controls email, can hijack workspace |

### Tampering Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| T-AUTH-001 | User profile tampering | Low | Medium | LOW | User entity immutable after creation | ⚠️ Avatar URL and display name can be updated on GitHub link (lines 124-125); no version control |
| T-AUTH-002 | Workspace quota tampering | Low | High | MEDIUM | Workspace.planProjectLimit set by billing handler | ⚠️ No integrity check; if Stripe webhook is spoofed, limits can be modified |
| T-AUTH-003 | Stripe webhook tampering (CRITICAL) | High | Critical | CRITICAL | Webhook signature verification is NOT implemented | 🔴 **CRITICAL:** BillingController line 48 comment: "For now we parse JSON directly"; NO signature verification with com.stripe.net.Webhook.constructEvent() |
| T-AUTH-004 | API key hash manipulation | Low | Medium | LOW | ApiKey.getKeyHash() is bcrypt; cannot be reversed | ✓ Bcrypt prevents hash reversal |
| T-AUTH-005 | Refresh token tampering | Low | High | MEDIUM | Refresh token is JWT with HS256; but no rotation mechanism | ⚠️ Refresh token can be used indefinitely until expiry (30 days) |

### Repudiation Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| R-AUTH-001 | User denies OAuth login | Low | Low | LOW | GitHub and SpringLens both log OAuth flow | ⚠️ No audit log table for compliance; only app logs |
| R-AUTH-002 | Denied API key creation | Low | Low | LOW | Logs include workspace ID and user ID | ⚠️ No immutable audit trail |
| R-AUTH-003 | Denied Stripe charge | Medium | High | HIGH | Stripe webhooks are one-way; no confirmation sent back | ⚠️ If webhook processing fails (e.g., workspace lookup fails), no retry mechanism visible |

### Information Disclosure Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| I-AUTH-001 | API key raw value exposure | Medium | Critical | HIGH | Raw key returned in POST /v1/workspaces/{id}/api-keys response only | ⚠️ Raw key transmitted in JSON response; must use HTTPS + secure transport; no way to retrieve after creation (good) |
| I-AUTH-002 | JWT secret exposure | Low | Critical | CRITICAL | Secret stored in env var (application.yml) | 🔴 **CRITICAL:** JWT_SECRET must be stored in secure secret manager (AWS Secrets Manager, Vault), not config file |
| I-AUTH-003 | GitHub user email not public (potential issue) | Low | High | MEDIUM | GitHubOAuthController requests email; GitHub may not provide if private | ⚠️ Line 105: if email is null, defaults to githubId@github.noreply.com; acceptable fallback |
| I-AUTH-004 | Workspace member list leakage | Low | Medium | LOW | GET /v1/workspaces/{id}/members only visible to workspace members | ✓ Proper access control |
| I-AUTH-005 | Workspace admin detection | Low | Medium | LOW | Admin users returned in member list (role field) | ⚠️ Reveals which users are admins; attackers can target them for phishing |
| I-AUTH-006 | Subscription/billing data exposure | Low | High | MEDIUM | GET /v1/workspaces/{id} returns plan name + limits | ✓ Expected; workspace members need this info |
| I-AUTH-007 | API key prefix leakage in logs | Low | Low | LOW | Logs show first 12 chars only (line 95 of ApiKeyService) | ✓ Appropriate |

### Denial of Service Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|-----------|------|------|-------------------|-----|
| D-AUTH-001 | GitHub OAuth endpoint flood | Medium | Medium | MEDIUM | GitHub rate limits caller | ⚠️ No per-IP rate limiting on /v1/auth/github/callback |
| D-AUTH-002 | API key creation flood | High | Medium | HIGH | No rate limiting on POST /v1/workspaces/{id}/api-keys | 🔴 **CRITICAL:** Attacker can flood with key creation requests; no per-user or per-workspace limit |
| D-AUTH-003 | User enumeration via workspace lookup | Low | Low | LOW | Endpoint returns 404 if workspace not found | ⚠️ Allows enumeration of valid workspace IDs (acceptable risk for business logic) |
| D-AUTH-004 | Stripe webhook retry loop | Medium | Medium | MEDIUM | No visible retry mechanism for failed webhooks | ⚠️ If webhook processing fails, payment status may not update; Stripe will retry but with exponential backoff |

### Elevation of Privilege Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| E-AUTH-001 | Non-admin invites members | Low | High | MEDIUM | POST /v1/workspaces/{id}/members checks authorization | ⚠️ Assumes role claim in JWT; no code visible to verify |
| E-AUTH-002 | API key used beyond intended project | Low | High | HIGH | ApiKeyService stores projectId; used to filter snapshots | ✓ Proper scoping; key cannot be used for other projects |
| E-AUTH-003 | Workspace member role escalation | Low | Critical | HIGH | Role is immutable after creation; only admins can add members | ⚠️ No visible check that role is "admin" before allowing member creation; depends on JWT role claim |
| E-AUTH-004 | Free plan bypasses to Pro features | Low | High | MEDIUM | Plan limits enforced via workspace.planProjectLimit and planMemberLimit | ⚠️ Enforcer comment in code; quota check logic not visible; relies on workspace plan field |

---

## Service 5: Notification Service (Port 8085)

**Role:** Kafka consumer (recommendations.ready) + webhook delivery
**Auth:** None (internal consumer only) + workspace isolation
**Data In:** Kafka recommendations.ready topic
**HTTP Endpoints:** 4 webhook config management endpoints
**External Data Out:** HTTP webhooks (Slack, GitHub PR, custom)

### Spoofing Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| S-NOT-001 | Kafka event forgery | Low | Medium | LOW | Kafka broker network isolation | ⚠️ No signatures on events |
| S-NOT-002 | Webhook destination spoofing | Medium | High | HIGH | Webhook URL encrypted at rest (AES-256-GCM) | ⚠️ URL decrypted for delivery; attacker could modify webhook config in DB and redirect to attacker-controlled endpoint |

### Tampering Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| T-NOT-001 | Webhook URL tampering | Medium | High | HIGH | URL stored encrypted in DB; encryption key in env var | ⚠️ Encryption key not in secure secret manager; if app config is compromised, encryption is useless |
| T-NOT-002 | Webhook config deletion attack | Low | High | MEDIUM | DELETE endpoint requires workspace context | ⚠️ Attacker with workspace access can delete webhook configs and disable notifications |
| T-NOT-003 | Kafka message tampering | Low | Medium | LOW | Network isolation | ⚠️ No message signing |
| T-NOT-004 | DeliveryLog mutation | Low | Low | LOW | DeliveryLog is append-only (INSERT only) | ✓ Good audit trail design |

### Repudiation Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|-----------|------|------|-------------------|-----|
| R-NOT-001 | Webhook delivery denial | Low | Medium | LOW | DeliveryLog records all attempts | ✓ Good; audit trail exists |
| R-NOT-002 | Denied config changes | Low | Low | LOW | Logs include workspace ID + user | ⚠️ No immutable change log |

### Information Disclosure Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| I-NOT-001 | Webhook URL leakage | Medium | High | HIGH | URL encrypted but decrypted in memory during delivery | ⚠️ If service process is dumped, URL is in plaintext in memory |
| I-NOT-002 | GitHub PR comment leakage (proprietary info) | Low | High | MEDIUM | Recommendation details posted publicly on GitHub PRs | ⚠️ If webhook is GitHub PR, recommendations are posted publicly; may leak proprietary info |
| I-NOT-003 | Slack message interception | Low | Medium | LOW | Messages sent HTTPS to Slack; Slack webhook URL is HTTPS | ⚠️ Slack webhook URL in encrypted DB; if compromised, attacker can post messages to Slack workspace |
| I-NOT-004 | DeliveryLog query leak | Low | Medium | LOW | DeliveryLog filtered by workspace_id | ✓ Proper isolation |
| I-NOT-005 | Cross-workspace webhook config leak | Low | Critical | HIGH | Webhook config queries filter by workspace_id | ✓ Proper isolation |

### Denial of Service Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| D-NOT-001 | Malicious webhook loop (external) | High | Medium | HIGH | Webhook delivery retries 3x with exponential backoff | ⚠️ If webhook URL points to attacker service that responds slowly, retries consume thread pool; no timeout enforcement visible |
| D-NOT-002 | Webhook config creation flood | High | Medium | HIGH | No rate limiting on POST /v1/workspaces/{id}/webhooks | 🔴 **CRITICAL:** Attacker can create unlimited webhook configs |
| D-NOT-003 | External webhook server unavailable | Medium | Low | LOW | Retries implemented; delivery marked as failed | ✓ Acceptable; deliverylogs track retries |
| D-NOT-004 | Recommendations.ready topic flood | Medium | Medium | MEDIUM | Kafka consumer lag | ⚠️ No backpressure; if delivery is slow, queue backs up |

### Elevation of Privilege Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| E-NOT-001 | Non-member creates webhook | Low | High | HIGH | POST checks workspace context via JWT | ⚠️ Depends on JWT validation; no visible code check |
| E-NOT-002 | Webhook delivery to unauthorized endpoints | Low | High | HIGH | URL validation is not visible | 🔴 **CRITICAL:** No validation that webhook URL is "safe"; attacker could register webhook pointing to internal service (e.g., http://auth-service:8084/v1/billing/webhooks/stripe) and trigger internal endpoints |

---

## Frontend Threats (Next.js 14)

**Role:** Browser-based dashboard + authentication handling
**Auth:** next-auth v5 JWT + next-auth middleware

### Spoofing Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| S-FE-001 | Session token theft (XSS) | Medium | Critical | HIGH | next-auth httpOnly cookies (if configured) | ⚠️ **NOT VERIFIED** auth.ts uses JWT strategy; unclear if cookies are httpOnly |
| S-FE-002 | GitHub OAuth redirect interception | Low | Medium | LOW | next-auth manages OAuth redirect | ✓ next-auth handles state validation |
| S-FE-003 | CSRF on form submissions | Medium | High | HIGH | next-auth middleware provides CSRF token | ⚠️ Not verified in provided code |

### Tampering Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| T-FE-001 | localStorage token tampering | Medium | Critical | HIGH | Tokens stored in localStorage (XSS vector) | 🔴 **CRITICAL:** accessToken stored in session object; if accessed via JavaScript, XSS can steal it |
| T-FE-002 | Page cache poisoning | Low | Medium | LOW | Dynamic pages; cache headers set by Next.js | ⚠️ No explicit cache control headers visible |

### Information Disclosure Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| I-FE-001 | API error messages in UI | Medium | Medium | MEDIUM | Error responses returned via axios interceptor | ⚠️ Error messages from 422 responses displayed to user; may leak schema/API details |
| I-FE-002 | Request IDs leaked in UI | Low | Low | LOW | X-Request-ID headers for tracing (acceptable) | ✓ Good for observability |
| I-FE-003 | Workspace member list enumeration | Low | Medium | LOW | Frontend fetches /v1/workspaces/{id}/members | ⚠️ Reveals all members + admin status (same as backend) |

### Denial of Service Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|------------|--------|------|-------------------|-----|
| D-FE-001 | API pagination abuse | High | Medium | HIGH | Frontend uses TanStack Query; no page size validation | 🔴 **CRITICAL:** Frontend can request pageSize=999999; backend has no limit |

---

## Cross-Service Threats

| Threat ID | Threat | Likelihood | Impact | Risk | Existing Mitigation | Gap |
|-----------|--------|-----------|------|------|-------------------|-----|
| X-001 | Kafka inter-service communication unencrypted | Medium | High | HIGH | Kafka broker assumes network isolation | ⚠️ No TLS on Kafka brokers; no message signing |
| X-002 | Service-to-service auth via IP CIDR | Medium | High | HIGH | /internal/* endpoints check CIDR (SecurityConfig line 33) | ⚠️ CIDR-based auth brittle; assumes flat network; no mTLS |
| X-003 | JWT secret shared across all services | Low | Critical | CRITICAL | Same secret in all services | ⚠️ If one service is compromised, all JWTs are forged |
| X-004 | No request signing or integrity checking | Medium | High | HIGH | No MAC on requests between services | ⚠️ StartupEventConsumer consumes events without signature verification |
| X-005 | Database connection pooling exhaustion | Medium | Medium | MEDIUM | Connection pool configs in application.yml | ⚠️ No connection limit visible in services |

---

## Threat Matrix (Sorted by Risk Score)

| Threat ID | Service | STRIDE | Description | Likelihood | Impact | Risk | Status |
|-----------|---------|--------|-------------|-----------|--------|------|--------|
| T-AUTH-003 | Auth | Tampering | Stripe webhook signature NOT verified — attacker can forge billing events | High | Critical | **CRITICAL** | 🔴 |
| S-AUTH-003 | Auth | Spoofing | OAuth state parameter NOT validated — CSRF on GitHub callback | Medium | High | **CRITICAL** | 🔴 |
| I-ANA-002 | Analysis | Information Disclosure | Cross-project snapshot access — any workspace member sees all projects | Low | Critical | **CRITICAL** | 🔴 |
| I-REC-001 | Recommendation | Information Disclosure | Cross-project recommendation access | Low | Critical | **CRITICAL** | 🔴 |
| E-REC-001 | Recommendation | Elevation of Privilege | Non-admin users modify production CI budgets | Medium | High | **CRITICAL** | 🔴 |
| I-AUTH-002 | Auth | Information Disclosure | JWT secret in config file (not Secrets Manager) | Low | Critical | **CRITICAL** | 🔴 |
| S-ING-001 | Ingestion | Spoofing | API key rate limiting missing — brute force attack | Medium | High | **HIGH** | 🟠 |
| D-ING-001 | Ingestion | Denial of Service | /v1/ingest flood — no per-API-key rate limiting | High | Medium | **HIGH** | 🟠 |
| D-ANA-001 | Analysis | Denial of Service | Pagination unbounded — pageSize=999999 allowed | High | Medium | **HIGH** | 🟠 |
| D-REC-001 | Recommendation | Denial of Service | Pagination unbounded | High | Medium | **HIGH** | 🟠 |
| E-NOT-002 | Notification | Elevation of Privilege | Webhook URL validation missing — attacker can target internal endpoints | Low | High | **HIGH** | 🟠 |
| T-AUTH-002 | Auth | Tampering | Workspace quota tampering via Stripe webhook forgery | Low | High | **HIGH** | 🟠 |
| I-NOT-001 | Notification | Information Disclosure | Webhook URLs decrypted in memory; process dumps leak URLs | Medium | High | **HIGH** | 🟠 |
| D-AUTH-002 | Auth | Denial of Service | API key creation flood — no rate limiting | High | Medium | **HIGH** | 🟠 |
| S-FE-001 | Frontend | Spoofing | Session token theft via XSS | Medium | Critical | **HIGH** | 🟠 |
| T-FE-001 | Frontend | Tampering | localStorage token vulnerable to XSS | Medium | Critical | **HIGH** | 🟠 |
| X-003 | Cross-Service | Spoofing | Single JWT secret across all services | Low | Critical | **CRITICAL** | 🔴 |
| I-AUTH-001 | Auth | Information Disclosure | API key returned in JSON response (use HTTPS enforced) | Medium | Critical | **HIGH** | 🟠 |
| D-AUTH-001 | Auth | Denial of Service | GitHub OAuth endpoint flood (no per-IP rate limiting) | Medium | Medium | **MEDIUM** | 🟡 |
| T-ING-002 | Ingestion | Tampering | Request replay within 60-second window | Medium | High | **MEDIUM** | 🟡 |

---

## Trust Boundaries

### Boundary 1: External User → API Gateway
**Data crossing:** HTTP requests (JSON)
**Validation:**
- ✓ HTTPS enforced (TLS)
- ✓ Content-Type: application/json validation
- ✓ Request size limits (infrastructure)
- ⚠️ No request rate limiting at API gateway level (application implements per-service)

### Boundary 2: API → Auth Service (OAuth callback)
**Data crossing:** Authorization code + user token → workspace + user
**Validation:**
- ✓ HTTPS enforced
- ⚠️ State parameter NOT validated (CRITICAL)
- ✓ Code single-use enforced by GitHub
- ⚠️ Webhook signature NOT validated (CRITICAL)

### Boundary 3: Ingestion Service → Analysis Service (Kafka)
**Data crossing:** StartupEvent (JSON serialized)
**Validation:**
- ⚠️ No message signing
- ⚠️ No encryption (plaintext JSON in Kafka topic)
- ⚠️ No idempotency check (relies on ApplicationId + partition key)

### Boundary 4: Service → Database
**Data crossing:** SQL queries (parameterized)
**Validation:**
- ✓ Parameterized queries (Spring Data JPA prevents SQL injection)
- ✓ Workspace ID in all queries (row-level security)
- ⚠️ No database-level encryption at rest (infrastructure concern)

### Boundary 5: Frontend → API
**Data crossing:** Bearer token + API calls (JSON)
**Validation:**
- ✓ JWT validation (HS256)
- ✓ HTTPS enforced
- ⚠️ Token in memory; vulnerable to XSS
- ⚠️ Token not HTTPS-only if stored in localStorage

### Boundary 6: Notification Service → External Webhooks
**Data crossing:** HTTP POST (JSON payload)
**Validation:**
- ✓ HTTPS enforced (recommended)
- ⚠️ No webhook signature/HMAC (recipient cannot verify sender)
- ⚠️ URL validation missing (could target internal endpoints)
- ⚠️ Timeout on delivery (retries up to 3x)

---

## Attack Surface Inventory

### Exposed (No Auth Required)
```
POST /v1/auth/github/callback          (OAuth callback)
POST /v1/billing/webhooks/stripe       (Stripe webhook)
GET  /actuator/health                   (health check)
GET  /v1/healthz                        (health check)
```

**Risk:** OAuth callback vulnerable to CSRF; Stripe webhook without signature verification

### Protected (Authentication Required)
```
Backend APIs (5 services × 3-8 endpoints = 20+ endpoints):
- Ingestion:     POST /v1/ingest, GET /v1/snapshots/{id}/status, GET /v1/snapshots/{id}/budget-check
- Analysis:      GET /v1/projects/{id}/snapshots, GET /v1/projects/{id}/snapshots/{id}/timeline, etc.
- Recommendation: GET /v1/projects/{id}/recommendations, PATCH /v1/projects/{id}/recommendations/{id}/status, etc.
- Auth:          GET /v1/auth/github/callback, GET /v1/workspaces/{id}, POST /v1/workspaces/{id}/members, etc.
- Notification:  GET /v1/workspaces/{id}/webhooks, POST /v1/workspaces/{id}/webhooks, etc.
```

**Risk:** Pagination unbounded; cross-project access not enforced; rate limiting missing

### Internal (Service-to-Service)
```
GET /internal/validate-key              (API key validation from ingestion-service)
Kafka topics: startup.events, analysis.complete, recommendations.ready
```

**Risk:** IP CIDR-based auth (brittle); no message signing; unencrypted

### Admin Interfaces
```
Not explicitly identified in provided code; AWS console, database dashboards out of scope
```

---

## Data Flow Threats

### Data: API Key (sl_proj_*)
**Creation:** API key generation in Auth Service (SecureRandom 32 bytes)
**Transmission:** Returned in JSON response (HTTPS required)
**Storage:** BCrypt hash in PostgreSQL
**Usage:** Bearer token in Ingestion Service
**Threat:** Raw key leaked in logs (mitigated: only prefix logged); key theft if HTTPS not enforced; no expiration

### Data: JWT Access Token
**Creation:** JwtService issues (HS256, 15-minute expiry)
**Transmission:** Authorization header (Bearer <token>)
**Storage:** next-auth session object (frontend)
**Usage:** All API calls (Analysis, Recommendation, Notification services)
**Threat:** Secret compromise (CRITICAL); token theft via XSS (frontend); no token revocation mechanism

### Data: StartupSnapshot (Telemetry)
**Creation:** Ingestion Service receives from JVM agent
**Transmission:** HTTP POST /v1/ingest (HTTPS required)
**Storage:** TimescaleDB hypertable
**Usage:** Analysis Service reads; published to Kafka (analysis.complete)
**Threat:** May contain proprietary build info (class names, dependency names); visible to all workspace members

### Data: Webhook URL (Encrypted)
**Creation:** User enters in Notification Service UI
**Transmission:** HTTPS to backend
**Storage:** AES-256-GCM encrypted in PostgreSQL
**Usage:** Decrypted during webhook delivery
**Threat:** Encryption key in env var (not Secrets Manager); if key compromised, all URLs exposed; no key rotation

---

## Summary: Critical, High, Medium Findings

### 🔴 CRITICAL (Fix immediately)

1. **Stripe webhook signature NOT verified** (T-AUTH-003)
   - **Location:** BillingController line 48
   - **Impact:** Attacker can forge billing events (free→pro upgrade, subscription cancellation)
   - **Fix:** Use `com.stripe.net.Webhook.constructEvent(payload, signature, secret)` to validate before parsing

2. **OAuth state parameter NOT validated** (S-AUTH-003)
   - **Location:** GitHubOAuthController line 62
   - **Impact:** CSRF attack on GitHub OAuth callback
   - **Fix:** Validate state parameter matches session state; use next-auth or implement state validation

3. **Cross-project snapshot/recommendation access** (I-ANA-002, I-REC-001)
   - **Location:** TimelineRepository, RecommendationRepository
   - **Impact:** Any workspace member views all projects' data
   - **Fix:** Add `project_id` to all queries and filter in controllers

4. **Non-admin users modify production CI budgets** (E-REC-001)
   - **Location:** RecommendationController.upsertCiBudget()
   - **Impact:** Attacker can disable budget enforcement for production
   - **Fix:** Enforce admin role check on production environment updates

5. **JWT secret in config file** (I-AUTH-002)
   - **Location:** JwtService constructor
   - **Impact:** Secret in version control (if committed); not rotated
   - **Fix:** Load from AWS Secrets Manager; implement key rotation

6. **Single JWT secret across all services** (X-003)
   - **Impact:** If one service is compromised, all JWTs can be forged
   - **Fix:** Use separate secrets per service or asymmetric signing (RS256 with public/private keys)

### 🟠 HIGH (Fix within 1 week)

7. **API key and API endpoint rate limiting missing** (S-ING-001, D-ING-001, D-AUTH-002, D-NOT-002, D-ANA-001, D-REC-001)
   - **Impact:** Attacker can flood services with requests; DoS attack
   - **Fix:** Implement per-IP, per-API-key, per-user rate limiting (Redis + spring-cloud-circuitbreaker)

8. **Frontend token vulnerable to XSS** (S-FE-001, T-FE-001)
   - **Impact:** XSS vulnerability allows token theft
   - **Fix:** Use httpOnly cookies for session storage; never store token in localStorage

9. **Webhook URL validation missing** (E-NOT-002)
   - **Impact:** Attacker registers webhook to internal endpoint (http://auth-service:8084/v1/billing/webhooks/stripe)
   - **Fix:** Validate webhook URL: no localhost, no private IPs, must be HTTPS

10. **Webhook encryption key not in Secrets Manager** (I-NOT-001)
    - **Impact:** Key in env var; if compromised, all webhook URLs exposed
    - **Fix:** Load from AWS Secrets Manager

11. **No database-level encryption** (I-ING-002, I-ANA-001, I-REC-002)
    - **Impact:** Database breach exposes all telemetry + config
    - **Fix:** Enable RDS encryption at rest (AWS KMS)

12. **Kafka messages unencrypted and unsigned** (X-001, X-004)
    - **Impact:** Network sniffer can read events; attacker can inject forged events
    - **Fix:** Enable TLS on Kafka brokers; add HMAC message signing

---

## Conclusion

**Risk Level:** HIGH — 6 critical, 12+ high severity findings. Public SaaS with active attackers requires immediate remediation.

**Phase 2 (Code Audit)** will detail specific code locations, provide before/after code examples, and create pull requests for fixes.

