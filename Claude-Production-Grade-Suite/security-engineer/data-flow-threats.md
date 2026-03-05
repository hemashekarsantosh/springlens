# Data Flow Threats — SpringLens

Sensitive data flows through SpringLens with various encryption, caching, and logging implications.

---

## Data Classification

| Data Type | Sensitivity | Volume | Retention | Examples |
|-----------|-------------|--------|-----------|----------|
| **PII (Personally Identifiable)** | HIGH | Low | 7 years (GDPR) | Email, GitHub username, display name, avatar URL |
| **Credentials** | CRITICAL | Very low | Until revoked | API keys, JWT tokens, GitHub access tokens, Stripe webhook secret |
| **Startup Telemetry** | MEDIUM | High | 30 days | Bean names, class names, durations, dependencies (may be proprietary) |
| **Billing Data** | HIGH | Low | 7 years (tax) | Stripe subscription ID, plan name, project limits, payment status |
| **Logs** | MEDIUM | High | 30 days | Request/response, errors, Kafka events (may contain sensitive data) |

---

## Data Flow 1: User Profile (PII)

```
GitHub ──code──> [Auth Service] ──GitHub API──> GitHub
                        │
                        ├─ Fetch user profile (email, avatar)
                        │
                        ├─ Create User entity (email, githubId, displayName, avatarUrl)
                        │
                        └──write──> PostgreSQL
                                      │
                                      ├─ [user.email] — plaintext PII
                                      ├─ [user.github_id] — semi-public
                                      ├─ [user.avatar_url] — URL (public)
                                      └─ [user.created_at] — metadata

Browser <──────────────> [Frontend] <──────────> [Auth Service]
                                │
                                ├─ session.user.email ← visible to user
                                └─ session.user.id ← used for workspace membership
```

### Threats

| Threat | Likelihood | Impact | Mitigation | Status |
|--------|-----------|--------|-----------|--------|
| Email leakage in logs | Medium | High | No email in logs; only user_id | ✓ OK |
| Avatar URL HijackInterception | Low | Low | Avatar URL is public (GitHub); no credentials | ✓ OK |
| User enumeration via email | Low | Medium | Email not visible in API (only to workspace members) | ✓ OK |
| Database breach exposes emails | Medium | High | No column-level encryption | ⚠️ GAP: Enable RDS encryption at rest |
| Cross-workspace email matching | Low | Medium | Email matching only during OAuth (user lookup) | ✓ ACCEPTABLE |

### Data Handling

| Stage | Encryption | Integrity | Logging |
|-------|-----------|-----------|---------|
| Transit (GitHub API) | ✓ HTTPS | ✓ GitHub cert | ⚠️ Token logged in debug logs (sanitize) |
| At Rest (PostgreSQL) | ✗ PLAINTEXT | ✓ DB integrity | ✓ No email in app logs |
| Session (Browser) | ✓ HTTPS | ✓ next-auth session | ⚠️ Email visible in browser session |
| Logs | ✗ PLAINTEXT | N/A | ✓ No email in app logs (good) |

---

## Data Flow 2: API Key (Credential)

```
[Workspace Member] ──POST─────────> [Auth Service]
                    /v1/api-keys/create
                          │
                          ├─ Generate random 32 bytes
                          │
                          ├─ Format: sl_proj_<base64>
                          │
                          ├─ Hash: BCrypt(rawKey)
                          │
                          ├─ Store [api_key.key_hash] in PostgreSQL
                          │
                          └─ Return { raw_key: "sl_proj_..." } in JSON
                                      │
                                      └─> Browser / Agent (ONE-TIME ONLY)

[JVM Agent] ────────────────> [Ingestion Service]
   │                           │
   ├─ Store key in env var     ├─ ApiKeyAuthFilter validates key
   │  (INSECURE)               │  ├─ Call /internal/validate-key
   │                           │
   │                           └─> [Auth Service]
   └───Bearer <key>────────>       │
                                   ├─ Find ApiKey by prefix + BCrypt match
                                   │
                                   └─ Return { workspace_id, project_id }
```

### Threats

| Threat | Likelihood | Impact | Mitigation | Status |
|--------|-----------|--------|-----------|--------|
| Key leakage in logs | Medium | Critical | BCrypt hash only; raw key never logged | ✓ OK |
| Key leak in browser | Low | Medium | Raw key returned once; cannot retrieve | ✓ OK |
| Key leak in agent (env var) | Medium | Critical | Agent stores key in plaintext environment | ⚠️ GAP: Recommend agent use secure storage (keychain) |
| Key brute force | Medium | Medium | No rate limiting on /internal/validate-key | 🔴 GAP: Add rate limiting |
| Key reuse after revocation | Low | Medium | Revoked key has revoked_at timestamp | ✓ OK |
| API key prefix leakage | Low | Low | First 12 chars visible; cannot reconstruct key | ✓ OK |

### Data Handling

| Stage | Encryption | Integrity | Logging |
|-------|-----------|-----------|---------|
| Generation | N/A | ✓ SecureRandom | ✓ Not logged |
| Transit (API) | ✓ HTTPS | ✓ TLS | ✓ Prefix only logged |
| At Rest (DB) | ✗ BCRYPT HASH | ✓ Hash immutable | ✓ Never logged plaintext |
| Agent Storage | ✗ PLAINTEXT (env var) | N/A | ✗ Possibly in agent logs |
| Validation Cache | ✗ Redis plaintext | N/A | ✗ Could leak if Redis is compromised |

---

## Data Flow 3: JWT Token (Credential)

```
[Auth Service (JwtService)] ──issue──> { access_token: "eyJh..." }
   │                                         │
   ├─ Secret: HS256 key from @Value        ├─ Browser session (next-auth)
   │  (application.yml)                    │
   │                                        └─> Axios interceptor
   ├─ Subject: user_id                         │
   ├─ Claims: workspace_id, email, role        ├─ Attach: Authorization: Bearer <token>
   │                                           │
   ├─ Expiry: 15 minutes (access)              └─> [All Services]
   │           30 days (refresh)                  │
   │                                             ├─ JwtService.validateToken()
   └─ Signature: HMAC-SHA256                   ├─ Extract claims (workspace_id, role)
                                               │
                                               └─ SecurityContext updated
```

### Threats

| Threat | Likelihood | Impact | Mitigation | Status |
|--------|-----------|--------|-----------|--------|
| **Secret compromise** | Low | **Critical** | Secret in application.yml (not Secrets Manager) | 🔴 **CRITICAL GAP** |
| Token forgery | Low | Critical | If secret is known, all tokens forged | DEPENDS ON ABOVE |
| Token theft (XSS) | Medium | Critical | Token in browser memory (vulnerable to XSS) | 🔴 **GAP**: Use httpOnly cookies |
| Token replay | Low | High | 15-minute window; no revocation list | ⚠️ ACCEPTABLE (short TTL) |
| Refresh token compromise | Low | Critical | 30-day TTL; no rotation | ⚠️ **GAP**: Implement refresh token rotation |
| Token in logs | Low | Low | Tokens not logged (good) | ✓ OK |
| Signature algorithm downgrade | Low | Medium | HS256 (symmetric); cannot downgrade | ✓ OK (if secret safe) |

### Data Handling

| Stage | Encryption | Integrity | Logging |
|-------|-----------|-----------|---------|
| Generation (Auth) | N/A | ✓ HMAC-SHA256 signature | ✗ Secret in plaintext config |
| Transit (API) | ✓ HTTPS | ✓ TLS | ✓ Not logged |
| Storage (Browser) | ⚠️ SESSION MEMORY | ✓ Signed JWT | ⚠️ Vulnerable to XSS |
| Validation | N/A | ✓ Signature verified | ✓ Not logged |
| Cache | ✗ PLAINTEXT (if cached) | N/A | ✗ Could leak |

---

## Data Flow 4: StartupSnapshot (Telemetry)

```
[JVM Agent]
   │
   ├─ Collect beans[], phases[], durations
   │  (may include proprietary class names)
   │
   └─────── POST /v1/ingest ────────────────> [Ingestion Service]
                (HTTPS)                               │
                                                      ├─ Deduplication check (Redis)
                                                      │
                                                      ├─ Save StartupSnapshot:
                                                      │  ├─ beans[]: JSON (plaintext)
                                                      │  ├─ phases[]: JSON (plaintext)
                                                      │  ├─ created_at: timestamp
                                                      │  └─ workspace_id, project_id
                                                      │
                                                      ├─────> PostgreSQL (TimescaleDB)
                                                      │         │
                                                      │         └─ [startup_snapshot.beans_json]
                                                      │            [startup_snapshot.phases_json]
                                                      │
                                                      ├─ Publish StartupEvent (Kafka)
                                                      │  └─ start_up.events topic (plaintext)
                                                      │
                                                      └─> [Analysis Service]
                                                            │
                                                            ├─ Consume event
                                                            ├─ Run analysis (BeanGraphAnalyzer)
                                                            ├─ Save StartupTimeline
                                                            │
                                                            └─> PostgreSQL
                                                                  │
                                                                  └─ [startup_timeline.timeline_json]
                                                                     (beans + durations)

[Frontend: User Dashboard]
   │
   └─────── GET /v1/projects/{id}/snapshots/{id}/timeline ────>
                                                         │
                                                         └─> Return TimelineResponse
                                                             (all beans + durations visible)
```

### Threats

| Threat | Likelihood | Impact | Mitigation | Status |
|--------|-----------|--------|-----------|--------|
| **Cross-project visibility** | **Low** | **Critical** | Repository queries missing project_id filter | 🔴 **CRITICAL GAP** |
| Proprietary info leak | Medium | High | Bean names, class names, dependency names visible | ⚠️ ACCEPTED (user consents) |
| Telemetry in logs | Medium | Medium | Bean JSON could be logged (debug level) | ⚠️ **GAP**: Redact in logs |
| Kafka interception | Low | Medium | Topics unencrypted; plaintext beans visible | ⚠️ **GAP**: Enable TLS on Kafka |
| Database breach | Medium | Medium | All telemetry exposed (no column encryption) | ⚠️ **GAP**: Enable RDS encryption |
| Snapshot enumeration | Low | Low | User can guess snapshot IDs (UUIDs) | ACCEPTABLE (high entropy) |
| Long retention | Low | Medium | Snapshots retained indefinitely | ⚠️ **GAP**: Implement retention policy |

### Data Handling

| Stage | Encryption | Integrity | Logging |
|-------|-----------|-----------|---------|
| Collection (Agent) | N/A | N/A | ✗ Possibly in agent logs |
| Transit (HTTP) | ✓ HTTPS | ✓ TLS | ✓ Not logged (large payload) |
| Redis (Dedup) | ✗ PLAINTEXT | N/A | ✗ If debug, visible |
| PostgreSQL | ✗ PLAINTEXT JSON | ✓ DB integrity | ✗ Possibly in slow query logs |
| Kafka | ✗ PLAINTEXT | N/A | ✗ Visible on network sniff |
| Frontend | ✓ HTTPS | ✓ TLS | ⚠️ Visible in browser console |

---

## Data Flow 5: Webhook URL (Sensitive Config)

```
[Workspace User]
   │
   └─ POST /v1/workspaces/{id}/webhooks
       { type: "slack", url: "https://hooks.slack.com/..." }
               │
               └─────────────> [Notification Service]
                                    │
                                    ├─ EncryptionService.encrypt(url)
                                    │  ├─ Generate random 12-byte IV
                                    │  ├─ AES-256-GCM encrypt (key from @Value)
                                    │  └─ Return Base64(IV + ciphertext)
                                    │
                                    └─────> PostgreSQL
                                             │
                                             └─ [webhook_config.encrypted_url]
                                                (IV + ciphertext in base64)

[Kafka Consumer: recommendations.ready]
   │
   └─> [Notification Service] processes event
           │
           ├─ Query WebhookConfig by workspace_id
           │
           ├─ EncryptionService.decrypt(encrypted_url)
           │  ├─ Extract IV from base64
           │  ├─ Load encryption key from @Value
           │  └─ AES-256-GCM decrypt
           │
           ├─ GET decrypted URL from memory
           │
           └─────── HTTP POST ───────────> Slack / Custom URL
                     (plaintext URL in memory)
```

### Threats

| Threat | Likelihood | Impact | Mitigation | Status |
|--------|-----------|--------|-----------|--------|
| **Encryption key compromise** | Low | **Critical** | Key in @Value (application.yml) | 🔴 **CRITICAL GAP** |
| URL leak (process dump) | Low | High | URL in plaintext during decryption | ⚠️ **GAP**: Consider key in Secrets Manager + attestation |
| URL mutation in DB | Low | High | If DB is compromised, URL can be changed | ⚠️ **GAP**: Enable RDS encryption |
| Webhook to internal service | **Medium** | **High** | No URL validation (attacker can register http://auth-service:8084/v1/billing/webhooks/stripe) | 🔴 **CRITICAL GAP** |
| Key rotation | Low | High | No key rotation mechanism | ⚠️ **GAP**: Implement quarterly key rotation |
| Slack webhook reuse | Low | Medium | Slack webhook URL can be enumerated by attacker if leaked | ACCEPTABLE (secrets manager protects) |

### Data Handling

| Stage | Encryption | Integrity | Logging |
|-------|-----------|-----------|---------|
| Input (UI) | ✓ HTTPS | ✓ TLS | ⚠️ Possibly logged in request |
| Encryption | ✓ AES-256-GCM | ✓ GCM tag | ✗ Key in plaintext config |
| Storage (DB) | ✓ ENCRYPTED | N/A | ✓ Encrypted in storage |
| Decryption | N/A | ✓ GCM integrity | ✗ Plaintext in memory (briefly) |
| Delivery | ✓ HTTPS | ✓ TLS | ⚠️ URL in plaintext delivery logs (if logging) |

---

## Data Flow 6: Stripe Webhook (Billing Events)

```
[Stripe] ──webhook payload──> POST /v1/billing/webhooks/stripe
                              + Stripe-Signature header
                                     │
                                     └─────> [Auth Service]
                                             │
                                             ├─ BillingController receives
                                             │
                                             ├─ 🔴 NO SIGNATURE VERIFICATION
                                             │
                                             ├─ Parse JSON directly
                                             │  └─ Extract: type, subscription_id, etc.
                                             │
                                             └─ Process event:
                                                ├─ invoice.paid → set workspace.plan = "pro"
                                                ├─ subscription.deleted → set workspace.plan = "free"
                                                └─ subscription.updated → update limits

[Database] <────── UPDATE workspace SET plan = 'pro'
```

### Threats

| Threat | Likelihood | Impact | Mitigation | Status |
|--------|-----------|--------|-----------|--------|
| **Signature NOT verified** | **Medium** | **CRITICAL** | BillingController line 48 comment | 🔴 **CRITICAL GAP** |
| Forged invoice.paid | Medium | Critical | Attacker can upgrade free→pro indefinitely | DEPENDS ON ABOVE |
| Replay attack | Low | Medium | Same event processed 2x = 2 upgrades | ⚠️ **GAP**: Deduplicate on event ID |
| Webhook interception | Low | High | Attacker observes Stripe webhook URL (if leaked) | ACCEPTABLE (HTTPS) |
| Event schema mutation | Medium | High | Attacker sends unknown event fields; parser ignores | ✓ ACCEPTABLE |
| Database compromise | Medium | High | If attacker gains DB access, can manually set plan | ACCEPTABLE (application-level) |

### Data Handling

| Stage | Encryption | Integrity | Logging |
|-------|-----------|-----------|---------|
| Stripe → Endpoint | ✓ HTTPS | 🔴 **NO SIGNATURE** | ✗ Payload logged in error handling (line 66) |
| Payload parsing | N/A | ✗ No validation | ✓ JSON schema implicit |
| Webhook secret | 🔴 ENV VAR | N/A | ✗ Secret in plaintext config |
| Event processing | N/A | N/A | ✓ No sensitive data in logs (usually) |

---

## Data Flow 7: Logs (Aggregated)

```
[All Services] ───────> Application Logs
   │
   ├─ INFO: "Ingested snapshot={} project={} env={}"
   │  └─ safe (UUID, UUID, string)
   │
   ├─ ERROR: "GitHub OAuth callback failed: {exception}"
   │  └─ ⚠️ Exception may contain sensitive data
   │
   ├─ DEBUG: "API key validated workspace={} project={}"
   │  └─ safe (UUIDs, prefix logged)
   │
   ├─ ERROR: "Stripe webhook processing failed: {ex.getMessage()}"
   │  └─ ⚠️ May expose Stripe error (quota exceeded, etc.)
   │
   └─ Kafka consumer lag logs
      └─ safe (no PII)

[Log Aggregation: CloudWatch / ELK]
   │
   ├─ 30-day retention (default)
   │
   ├─ Full-text searchable (exposes PII if logged)
   │
   └─ Queryable by IAM role
```

### Threats

| Threat | Likelihood | Impact | Mitigation | Status |
|--------|-----------|--------|-----------|--------|
| Email in logs | Medium | Medium | Exception messages may contain email | ⚠️ **GAP**: Redact email in error messages |
| Stack traces | Medium | Medium | Exception stack traces expose code structure | ⚠️ **GAP**: Don't log stack traces in production |
| API keys in logs | Low | Critical | If debug logging enabled, could leak keys | ✓ OK (currently safe: prefix only) |
| Webhook URLs in logs | Low | High | If webhook delivery logged, encrypted URLs visible | ⚠️ **GAP**: Don't log webhook URLs |
| Unauthorized log access | Low | High | IAM roles control access; assume proper controls | ⚠️ DEPENDS ON INFRA |
| Log retention too long | Medium | Medium | Logs kept 30+ days; should be 7 days for PII | ⚠️ **GAP**: Implement retention policy |

---

## Cross-Flow Risks

### Risk 1: Insufficient Data Isolation (PII + Telemetry)
**Impact:** User email linked to startup telemetry (proprietary code visible)
**Flows:** User Profile (email) + Startup Snapshot (beans/classes)
**Mitigation:** Implement data segregation; redact PII from telemetry or aggregate anonymously

### Risk 2: Secret Centralization (JWT + Encryption + Webhook)
**Impact:** Single point of failure (all secrets in application.yml)
**Flows:** JWT secret, encryption key, Stripe webhook secret
**Mitigation:** Use AWS Secrets Manager; rotate quarterly; implement secret versioning

### Risk 3: Kafka + Logging Data Exposure
**Impact:** Startup telemetry visible in multiple places (Kafka + logs + DB + Redis)
**Flows:** Startup Snapshot → Kafka → Logs → Cache → DB
**Mitigation:** Implement redaction filter for sensitive fields; enable Kafka TLS; encrypt Redis

### Risk 4: Webhook Configuration + Validation
**Impact:** Attacker registers webhook to internal endpoint; admin credentials stolen
**Flows:** Webhook URL → Storage → Decryption → HTTP POST
**Mitigation:** Validate webhook URL (no localhost, no private IPs, HTTPS only)

---

## Summary: Data Sensitivity vs Protection

| Data | Sensitivity | Encryption | Integrity | Access Control | Status |
|------|-------------|-----------|-----------|-----------------|--------|
| **User Email (PII)** | HIGH | ✗ DB plaintext | ✓ DB | ✓ Workspace members | ⚠️ MEDIUM |
| **API Key (Credential)** | CRITICAL | ✓ BCrypt hash | ✓ Hash | ✓ Workspace members (redacted) | ✓ HIGH |
| **JWT (Credential)** | CRITICAL | ⚠️ Secret in config | ✓ HMAC signature | ✓ Validated per request | 🔴 **CRITICAL** (secret location) |
| **Startup Telemetry** | MEDIUM | ✗ DB plaintext | ✓ DB | 🔴 **MISSING project filter** | 🔴 **CRITICAL** |
| **Webhook URL** | HIGH | ✓ AES-256-GCM | ✓ Encrypted | ✓ Workspace only | ⚠️ HIGH (key location + validation) |
| **Stripe Events** | HIGH | ⚠️ No signing | 🔴 **NOT VERIFIED** | ✗ No idempotency | 🔴 **CRITICAL** |
| **Logs** | MEDIUM | ✗ Plaintext | N/A | ⚠️ IAM-based | ⚠️ MEDIUM |

