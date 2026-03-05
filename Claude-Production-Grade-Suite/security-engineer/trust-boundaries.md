# Trust Boundaries Analysis — SpringLens

---

## Boundary 1: Internet → API Gateway (TLS Entry Point)

**Crossing:** HTTP/HTTPS requests from browser, JVM agent, webhooks
**Direction:** Inbound
**Parties:** Untrusted external + Trusted internal services
**Data:** API calls, OAuth codes, webhook payloads

### Validation Checks

| Check | Status | Details |
|-------|--------|---------|
| TLS 1.2+ enforced | ✓ Assumed (infrastructure concern) | HTTPS required for all endpoints |
| Certificate pinning | ✗ NOT IMPLEMENTED | Clients don't pin cert; vulnerable to MITM if CA compromised |
| Request size limits | ✓ Assumed (infrastructure) | WAF/ALB should enforce max 10MB |
| Content-Type validation | ⚠️ PARTIAL | Spring validates application/json; accepts text/plain if body is JSON |
| Host header validation | ⚠️ MISSING | No Host header check in Spring; vulnerable to Host header injection |
| Method validation | ✓ YES | Spring validates HTTP method (POST vs GET, etc.) |
| CORS headers | ⚠️ NOT VISIBLE | No @CrossOrigin; assumed wildcard CORS disabled |
| Rate limiting | ✗ MISSING | No request-level rate limiting at API gateway |

### Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Certificate compromise | MEDIUM | Enforce TLS 1.3; consider certificate pinning in agents |
| Host header injection | MEDIUM | Validate Host header in Spring Security |
| Request flooding | HIGH | Implement WAF + rate limiting |
| Large payload | MEDIUM | Enforce max 1MB payload size |

---

## Boundary 2: Auth Service → GitHub OAuth

**Crossing:** Authorization code + access token exchange
**Direction:** Inbound (GitHub sends code to callback)
**Parties:** GitHub (trusted) ↔ SpringLens (internal)
**Data:** OAuth code, user profile, access token

### Validation Checks

| Check | Status | Details |
|-------|--------|---------|
| State parameter validation | 🔴 MISSING | GitHubOAuthController accepts state but does NOT validate it — CSRF vuln |
| Code single-use | ✓ GITHUB | GitHub invalidates code after 1 use |
| Redirect URI validation | ✓ GITHUB | GitHub only sends code to registered redirect_uri |
| HTTPS on callback | ✓ REQUIRED | Callback MUST be HTTPS |
| Timeout on code exchange | ⚠️ ASSUMED | GitHub code expires in 10 minutes (GitHub-enforced) |
| PKCE (Proof Key for Code Exchange) | ✗ NOT USED | Not needed for server-side OAuth (backend has secret) |
| ID token validation | ✓ GITHUB | GitHub provides JWT in token response; not validated by SpringLens (unnecessary for code flow) |

### Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| CSRF on callback | CRITICAL | Store state in session; validate on callback |
| Code replay | LOW | GitHub prevents (single-use) |
| User profile tampering | LOW | GitHub attests; not relayed without signature |
| Token leak in logs | LOW | Token not logged (code only in debug log) |

---

## Boundary 3: Auth Service → Stripe Webhooks (Inbound)

**Crossing:** Stripe event payload + signature
**Direction:** Inbound (Stripe posts to SpringLens)
**Parties:** Stripe (trusted) ↔ SpringLens (internal)
**Data:** Invoice, subscription, charge events

### Validation Checks

| Check | Status | Details |
|-------|--------|---------|
| Signature verification | 🔴 MISSING | BillingController does NOT verify Stripe-Signature — CRITICAL |
| Timestamp validation | ✗ NOT VISIBLE | Stripe webhook timestamp not checked for replay |
| Idempotency | ✗ PARTIAL | Each event has unique ID; Spring should use idempotency key |
| Payload parsing | ✓ PARTIAL | Jackson ObjectMapper validates JSON structure |
| Allowed event types | ✓ YES | Code switches on eventType; unknown types logged and ignored |
| Webhook secret management | ⚠️ ENV VAR | Secret in @Value; not loaded from Secrets Manager |
| Endpoint validation | ✓ YES | Stripe only posts to registered endpoint |
| Request body size | ✓ ASSUMED | Stripe documents max 25KB payload |

### Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| **Forged billing events** | **CRITICAL** | Implement signature verification (com.stripe.net.Webhook.constructEvent) |
| Replay attack | MEDIUM | Validate timestamp + deduplicate on event ID |
| Webhook secret leak | HIGH | Move to AWS Secrets Manager + rotate quarterly |
| Malicious event injection | CRITICAL | Signature verification prevents this |

---

## Boundary 4: Service → Service (Kafka Internal)

**Crossing:** Kafka event topics (startup.events, analysis.complete, recommendations.ready)
**Direction:** Async (fire-and-forget)
**Parties:** Internal services (all trusted within VPC, but should not trust Kafka brokers)
**Data:** Startup snapshots, analysis results, recommendations

### Validation Checks

| Check | Status | Details |
|-------|--------|---------|
| Message signing (HMAC) | ✗ MISSING | No HMAC on Kafka events; attacker can inject forged events |
| Message encryption | ✗ MISSING | Kafka broker topics are plaintext; network sniffing exposes data |
| TLS on Kafka brokers | ✗ MISSING | No TLS (infrastructure concern; assume private cluster network) |
| Consumer group authorization | ✗ MISSING | No ACLs; any service can consume any topic |
| Idempotency | ⚠️ PARTIAL | Analysis service assumes idempotency; consumes same event 2x = 2 timelines |
| Schema validation | ✓ YES | Jackson validates event structure (JSON schema not enforced) |
| Timestamp | ✓ YES | Events include created_at; not validated on consumption |
| Partition key | ✓ YES | Events keyed by snapshot_id; ensures ordering per snapshot |

### Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Message forgery | HIGH | Add HMAC signature to all events (JJWT or similar) |
| Network eavesdropping | MEDIUM | Enable TLS on Kafka brokers (infrastructure) |
| Unauthorized consumer | MEDIUM | Enable Kafka ACLs by consumer group |
| Duplicate processing | MEDIUM | Ensure consumers are idempotent (use idempotency key in DB) |

---

## Boundary 5: Service → PostgreSQL Database

**Crossing:** SQL queries (parameterized)
**Direction:** Bidirectional (reads + writes)
**Parties:** Spring Boot services ↔ PostgreSQL
**Data:** Users, workspaces, projects, snapshots, recommendations, API keys

### Validation Checks

| Check | Status | Details |
|-------|--------|---------|
| Parameterized queries | ✓ YES | Spring Data JPA uses parameterized queries (no SQL injection) |
| Connection encryption | ✓ ASSUMED | PostgreSQL connections assume TLS or private network |
| Authentication | ✓ DB USER | Database credentials in connection string; should be secrets |
| Row-level security (RLS) | ✓ PARTIAL | Application enforces workspace_id in WHERE clause; not DB-level RLS |
| Column-level encryption | ✗ MISSING | No column encryption (PII in plaintext: emails, API key hashes, webhook URLs encrypted in app not DB) |
| Database encryption at rest | ✗ MISSING | No RDS encryption (infrastructure concern) |
| Query auditing | ✗ MISSING | No PostgreSQL audit logging (infrastructure concern) |
| Connection pooling | ✓ PARTIAL | HikariCP configured; max pool size should be limited |
| Slow query logging | ✓ ASSUMED | PostgreSQL logs slow queries (infrastructure) |
| Privilege principle | ⚠️ PARTIAL | Likely single DB user for all services (should be separate per service) |

### Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| SQL injection | LOW | Parameterized queries prevent this |
| **Cross-workspace data access** | **CRITICAL** | Missing project_id in queries (analysis, recommendation services) |
| Database credential leak | HIGH | Move DB password to Secrets Manager |
| Plaintext PII in DB | MEDIUM | Enable encryption at rest (RDS encryption) + column encryption |
| Connection exhaustion | MEDIUM | Limit pool size; implement circuit breaker for DB timeouts |

---

## Boundary 6: Service → External APIs (GitHub, Slack, custom)

**Crossing:** HTTP/HTTPS outbound requests
**Direction:** Outbound (service → external)
**Parties:** SpringLens services ↔ GitHub / Slack / Custom webhooks
**Data:** API tokens, recommendation data, webhook signatures

### Validation Checks

| Check | Status | Details |
|-------|--------|---------|
| HTTPS enforced | ✓ YES | All external endpoints use HTTPS |
| Certificate validation | ✓ JAVA | Java/Kotlin validate certificates by default |
| Timeout on requests | ✓ PARTIAL | RestClient has implicit timeout; Notification service retries up to 3x |
| Rate limiting (outbound) | ✗ MISSING | No rate limiting on outbound requests; attacker can flood external services |
| Token injection | ⚠️ RISK | GitHub tokens in Authorization header (RestClient logs may leak) |
| Response validation | ⚠️ PARTIAL | Response is parsed as JSON; no validation of schema |
| Webhook signature | ✗ MISSING | When Notification service sends webhooks, no HMAC signature (receiver cannot verify) |
| Retry logic | ✓ YES | Exponential backoff on webhook delivery (3 retries) |
| Circuit breaker | ✗ MISSING | No circuit breaker; if external service is slow, SpringLens threads hang |
| Content-Type validation | ✓ YES | RestClient enforces application/json |

### Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| Token leak in logs | MEDIUM | Never log Authorization headers |
| Slow external service | MEDIUM | Implement circuit breaker + timeout |
| Webhook forgery (receiver side) | MEDIUM | Add HMAC signature to outbound webhooks |
| Outbound DDoS | HIGH | Rate limit outbound requests (e.g., 100 requests/min to same host) |
| Response injection | MEDIUM | Validate external API responses against schema |

---

## Boundary 7: API Client → Axios Interceptor (Frontend)

**Crossing:** Token attachment + interceptor handling
**Direction:** Request/response transformation
**Parties:** React components ↔ Axios client
**Data:** JWT access token, request/response bodies

### Validation Checks

| Check | Status | Details |
|-------|--------|---------|
| Token retrieval | ✓ getSession() | Calls next-auth; only executes client-side |
| Token attachment | ✓ Authorization header | Bearer token added to every request |
| Request ID generation | ✓ uuid v4 | X-Request-ID added for tracing |
| 401 handling | ✓ signOut({ callbackUrl: "/login" }) | Redirects to login on 401 |
| 422 handling | ✓ PASS-THROUGH | 422 errors returned as response (not thrown) |
| Other error codes | ✓ THROW | 500, 403, etc. are rejected (promise.reject) |
| Token in localStorage | 🔴 RISK | Token stored in next-auth session; if XSS, can be stolen |
| CORS headers | ⚠️ NOT VISIBLE | No explicit CORS handling in client |
| Content-Type | ✓ application/json | Enforced in client config |
| Cookie handling | ⚠️ NOT VISIBLE | Unclear if httpOnly cookies are used vs JWT in storage |

### Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|-----------|
| **XSS token theft** | **HIGH** | Use httpOnly secure cookies instead of session storage |
| CSRF | MEDIUM | Ensure CSRF token in next-auth middleware |
| Error message leakage | MEDIUM | Don't display raw API error messages to users |
| Token refresh | LOW | Refresh token mechanism exists (30-day rotation) |

---

## Cross-Boundary Risks

### Risk 1: JWT Secret Compromise
**Boundary:** Auth Service (generates JWT) → All Services (validate JWT)
**Impact:** All JWTs can be forged; attacker gains access to all services
**Current State:** HS256 symmetric key; secret in @Value from application.yml
**Recommendation:**
- Use RS256 (asymmetric signing): private key in Auth service, public key in other services
- OR rotate HS256 key regularly (quarterly)
- OR load secret from AWS Secrets Manager (not config file)

### Risk 2: Database Credential Sharing
**Boundary:** All services → PostgreSQL
**Impact:** If one service is compromised, attacker can query other services' data
**Current State:** Likely single DB user for all services
**Recommendation:**
- Create separate DB user per service
- Grant least privilege (e.g., analysis-service only reads analysis tables)

### Risk 3: Kafka Event Tampering
**Boundary:** Producer service → Kafka topic → Consumer service
**Impact:** Attacker in cluster can inject forged events; analysis service processes fake snapshots
**Current State:** No message signing; plaintext topics
**Recommendation:**
- Add HMAC signature to all events (include signature as Kafka header)
- Validate signature on consumption

### Risk 4: Webhook Delivery Side Effects
**Boundary:** Notification service → External webhook → (no feedback)
**Impact:** If webhook fails, recommendation service doesn't know; notifications not sent
**Current State:** Fire-and-forget; no acknowledgment
**Recommendation:**
- Implement delivery confirmation (SQS, webhook ACK)
- Store delivery status in database; alert on repeated failures

---

## Summary: Trust Boundary Validation

| Boundary | Trust Level | Validation Strength | Risk Level |
|----------|-------------|-------------------|-----------|
| Internet → API Gateway | UNTRUSTED | Strong (TLS) | MEDIUM (no rate limiting) |
| GitHub OAuth callback | SEMI-TRUSTED | **Weak** (no state validation) | **CRITICAL** |
| Stripe webhook | SEMI-TRUSTED | **None** (no signature verification) | **CRITICAL** |
| Kafka inter-service | INTERNAL | **None** (no signing) | **HIGH** |
| Service → Database | INTERNAL | Strong (parameterized queries) | HIGH (missing project filter) |
| External APIs (outbound) | UNTRUSTED | Medium (no circuit breaker) | MEDIUM |
| Frontend session | UNTRUSTED | **Weak** (XSS vulnerable) | **HIGH** |

