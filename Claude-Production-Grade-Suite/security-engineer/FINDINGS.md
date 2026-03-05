# Security Audit: Critical Findings Summary

**Date:** 2026-03-05
**Scope:** SpringLens SaaS (Java 21 services + Next.js 14 frontend)
**Compliance:** SOC2 Type II
**Threat Model:** Public SaaS with active attackers

---

## 🔴 CRITICAL (6 Findings — Fix Immediately)

### C1: Stripe Webhook Signature NOT Verified
**Location:** `services/auth-service/src/main/java/io/springlens/auth/controller/BillingController.java:48`
**Risk:** Attacker forges billing events (invoice.paid, subscription.deleted)
**Impact:** Free plan → Pro upgrade; disabling billing enforcement
**Fix Required:** Use `com.stripe.net.Webhook.constructEvent(payload, signature, secret)` to validate before parsing

---

### C2: OAuth State Parameter NOT Validated
**Location:** `services/auth-service/src/main/java/io/springlens/auth/controller/GitHubOAuthController.java:62`
**Risk:** CSRF attack on GitHub OAuth callback
**Attack:** Attacker crafts malicious link `springlens.io/oauth?code=...&state=ATTACKER_STATE`
**Impact:** Login as different user; account takeover
**Fix Required:** Store state in session; validate on callback (or use next-auth state validation)

---

### C3: Cross-Project Snapshot/Recommendation Access
**Location:** `services/analysis-service/src/main/java/io/springlens/analysis/repository/StartupTimelineRepository.java`
**Location:** `services/recommendation-service/src/main/java/io/springlens/recommendation/repository/RecommendationRepository.java`
**Risk:** Any workspace member views **all projects' data** (not just their own)
**Attack:** User A in workspace X creates project X1 and X2. Adds colleague B to workspace. B can see X1 + X2 snapshots/recommendations, even if not explicitly granted access to X1.
**Impact:** Data exposure; confidentiality breach
**Fix Required:** Add `project_id` filter to all queries; validate in controllers before returning data

---

### C4: Non-Admin Users Modify Production CI Budgets
**Location:** `services/recommendation-service/src/main/java/io/springlens/recommendation/controller/RecommendationController.java`
**Risk:** Attacker disables budget enforcement for production
**Attack:** Non-admin user calls `PUT /v1/projects/{id}/ci-budgets` with environment="production", budget_ms=999999
**Impact:** Budget enforcement bypassed; slow builds not caught
**Fix Required:** Enforce admin role check on production environment updates:
```java
if ("production".equals(request.environment()) && !isAdmin()) {
    throw new ForbiddenException("Only admins can modify production budgets");
}
```

---

### C5: JWT Secret in Configuration File
**Location:** `services/auth-service/src/main/java/io/springlens/auth/service/JwtService.java:34`
**Risk:** Secret in plaintext in application.yml (source control, container images)
**Attack:** Developer commits secret; attacker forks repo or accesses ECR image; can forge all JWTs
**Impact:** Complete auth bypass; impersonate any user
**Fix Required:** Load from AWS Secrets Manager (or similar):
```java
@Value("${spring.cloud.aws.secretsmanager.jwt.secret}")  // from AWS Secrets Manager
private String secret;
```

---

### C6: Single JWT Secret Across All Services
**Risk:** If one service is compromised, all JWTs are forged (no service isolation)
**Impact:** Attacker in one service can generate tokens for other services
**Fix Required:** Option A (Recommended): Use RS256 (asymmetric signing)
- Auth service: private key (sign JWTs)
- Other services: public key (verify JWTs)

Option B: Separate secrets per service (higher operational overhead)

---

## 🟠 HIGH (12 Findings — Fix Within 1 Week)

### H1: No Rate Limiting on Critical Endpoints
**Endpoints:** `/v1/ingest`, `/v1/api-keys`, `/v1/webhooks`, `/v1/snapshots?page=1&size=999999`
**Risk:** DoS attack; attacker floods service
**Attack:** POST 10,000 requests/sec to `/v1/ingest`; service overwhelmed
**Impact:** Availability; resource exhaustion
**Fix Required:** Implement per-IP, per-API-key, per-user rate limiting (e.g., 100 requests/min):
- Spring Cloud CircuitBreaker + Resilience4j
- Redis-based rate limiter (allow 1000 requests/hour per API key)
- API gateway rate limiting (AWS ALB rate limiting)

---

### H2: Pagination Unbounded (PageSize Injection)
**Location:** All list endpoints (analysis-service, recommendation-service)
**Risk:** Attacker requests `pageSize=999999` → full table scan
**Attack:** `GET /v1/projects/{id}/snapshots?pageSize=999999&page=1`
**Impact:** Memory exhaustion; slow queries; DoS
**Fix Required:** Enforce max page size:
```java
if (pageSize > 100) pageSize = 100;  // max 100 items per page
```

---

### H3: Frontend Token Vulnerable to XSS
**Location:** `frontend/src/lib/auth.ts`, `frontend/src/lib/api/client.ts`
**Risk:** Token stored in next-auth session; vulnerable to JavaScript XSS
**Attack:** Attacker injects `<script>fetch('/steal-session')</script>`; steals accessToken
**Impact:** Account takeover
**Fix Required:** Use httpOnly secure cookies instead of session storage:
```typescript
session: {
  strategy: "jwt",  // currently using jwt in session
  // CHANGE TO: use httpOnly cookies for token storage
}
```

---

### H4: Webhook URL Validation Missing
**Location:** `services/notification-service/src/main/java/io/springlens/notification/controller/WebhookConfigController.java`
**Risk:** Attacker registers webhook to internal endpoint
**Attack:** Register webhook URL = `http://auth-service:8084/v1/billing/webhooks/stripe` → trigger billing logic
**Impact:** Internal endpoint access; privilege escalation; billing manipulation
**Fix Required:** Validate webhook URL:
```java
if (!isValidWebhookUrl(url)) {
    throw new BadRequestException("Invalid webhook URL");
}

private boolean isValidWebhookUrl(String url) {
    // - Must be HTTPS (no http://)
    // - Must not be localhost, 127.0.0.1, or private IPs (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16)
    // - Must be publicly resolvable
    return url.startsWith("https://") && !isPrivateIp(url) && isPublic(url);
}
```

---

### H5-H12: Additional HIGH Findings
- **H5:** Encryption key for webhooks in @Value (not Secrets Manager)
- **H6:** Kafka messages unsigned and unencrypted (no message signing)
- **H7:** No database encryption at rest (RDS encryption disabled)
- **H8:** No IP CIDR-based auth for /internal/* (brittle; service-to-service should use mTLS)
- **H9:** No request audit logging (compliance gap for SOC2)
- **H10:** Stripe webhook error messages leak in response (information disclosure)
- **H11:** No API key expiration (keys valid indefinitely)
- **H12:** No refresh token rotation (30-day refresh token; should auto-rotate)

---

## 📊 Risk Matrix Summary

```
        │ IMPACT
────────┼────────────────────────────────────────────
        │  LOW   │  MEDIUM  │  HIGH  │ CRITICAL
────────┼────────┼──────────┼────────┼──────────
L  I    │        │   M1-M3  │  H5-H9 │
I  K    │        │          │        │
K  E    ├────────┼──────────┼────────┼──────────
E  L    │   L1-L2│   M4-M6  │ H1-H4  │   C1-C6
        │        │          │ H10-H12│
────────┴────────┴──────────┴────────┴──────────
```

**Critical + High:** 18 findings
**Total Risk Score:** 92/100 (HIGH RISK)
**Recommended Action:** Halt production deployment until Critical findings fixed

---

## Phase 2: Code Security Audit (Ready for Review)

Phase 1 (Threat Modeling) outputs:
- ✅ `stride-analysis.md` — Per-service STRIDE tables (risk scoring)
- ✅ `attack-surface.md` — Complete endpoint inventory (26 exposed/protected endpoints)
- ✅ `trust-boundaries.md` — 7 trust boundary crossings analyzed
- ✅ `data-flow-threats.md` — 7 sensitive data flows traced

**Next Steps:**
1. Review findings (this document)
2. Proceed to Phase 2: Code Security Audit (OWASP Top 10 deep dive + code locations)
3. Phase 3: Authentication Hardening (JWT, OAuth, API keys)
4. Phase 4: Data Security (encryption, PII handling, GDPR)
5. Phase 5: Supply Chain (dependency scanning, SBOM)
6. Phase 6: Remediation Plan (before/after code + timeline)

---

## Approval Gate

**Review the findings above. Proceed to Phase 2 (Code Audit)?**

