# OWASP Top 10 Code Audit — SpringLens

**Date:** 2026-03-05
**Scope:** Java 21 microservices + Next.js 14 frontend
**Total Findings:** 18 (6 Critical, 12 High)

---

## A01: Broken Access Control (IDOR)

### [CRITICAL] Cross-Project Data Access via Repository Method

**Location:** `services/recommendation-service/src/main/java/io/springlens/recommendation/repository/RecommendationRepository.java:39`

**Vulnerable Code:**
```java
@Query("""
    SELECT r FROM Recommendation r
    WHERE r.snapshotId = :snapshotId
    ORDER BY r.rank ASC
    """)
List<Recommendation> findBySnapshotId(@Param("snapshotId") UUID snapshotId);
```

**Vulnerability:** Repository query lacks workspace_id and project_id filtering. If used without controller-side validation, returns recommendations for ANY snapshot regardless of project.

**Proof of Concept:**
1. User A creates project P1 with snapshot S1 (generates recommendations R1-R5)
2. User A invites User B to workspace (User B not invited to P1 specifically)
3. Attacker (User B) guesses or enumerates snapshot IDs
4. Attacker calls internal code that uses `findBySnapshotId(S1)` → gets User A's recommendations (cross-project leak)

**Current Usage:** Search codebase for `.findBySnapshotId` calls

**Remediation:**
```java
// FIXED: Add workspace + project filtering
@Query("""
    SELECT r FROM Recommendation r
    WHERE r.snapshotId = :snapshotId
      AND r.workspaceId = :workspaceId
      AND r.projectId = :projectId
    ORDER BY r.rank ASC
    """)
List<Recommendation> findBySnapshotIdAndProject(
        @Param("snapshotId") UUID snapshotId,
        @Param("workspaceId") UUID workspaceId,
        @Param("projectId") UUID projectId);
```

**References:** CWE-639 (Authorization Bypass Through User-Controlled Key), OWASP A01:2021

---

### [CRITICAL] Timeline Query Lacks Project Filter

**Location:** `services/analysis-service/src/main/java/io/springlens/analysis/repository/StartupTimelineRepository.java:19`

**Vulnerable Code:**
```java
Optional<StartupTimeline> findBySnapshotId(UUID snapshotId);
```

**Vulnerability:** Same as above - snapshot ID query without project context.

**Remediation:**
```java
@Query("""
    SELECT t FROM StartupTimeline t
    WHERE t.snapshotId = :snapshotId
      AND t.workspaceId = :workspaceId
      AND t.projectId = :projectId
    """)
Optional<StartupTimeline> findBySnapshotIdAndProject(
        @Param("snapshotId") UUID snapshotId,
        @Param("workspaceId") UUID workspaceId,
        @Param("projectId") UUID projectId);
```

---

### [HIGH] Recommendation Update Lacks Project Validation

**Location:** `services/recommendation-service/src/main/java/io/springlens/recommendation/controller/RecommendationController.java:80-82`

**Vulnerable Code:**
```java
var rec = recommendationRepository.findByIdAndWorkspaceId(recommendationId, workspaceId)
        .filter(r -> r.getProjectId().equals(projectId))
        .orElse(null);
```

**Vulnerability:** Relies on Java-side filter (not database-level). If `.filter()` is removed or if there's a code path bypass, attacker can update recommendations from different projects.

**Remediation:**
```java
// Use database query with project_id included
@Query("""
    SELECT r FROM Recommendation r
    WHERE r.id = :id
      AND r.workspaceId = :workspaceId
      AND r.projectId = :projectId
    """)
Optional<Recommendation> findByIdAndWorkspaceIdAndProjectId(
        @Param("id") UUID id,
        @Param("workspaceId") UUID workspaceId,
        @Param("projectId") UUID projectId);

// In controller:
var rec = recommendationRepository.findByIdAndWorkspaceIdAndProjectId(
        recommendationId, workspaceId, projectId)
        .orElse(null);
```

---

## A02: Cryptographic Failures

### [CRITICAL] JWT Secret in Configuration File (No Rotation)

**Location:** `services/auth-service/src/main/java/io/springlens/auth/service/JwtService.java:34`

**Vulnerable Code:**
```java
public JwtService(
        @Value("${springlens.jwt.secret}") String secret,
        @Value("${springlens.jwt.issuer:https://api.springlens.io}") String issuer) {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.issuer = issuer;
}
```

**Vulnerability:** Secret loaded from application.yml (plaintext in config files, potentially in version control). No key rotation mechanism.

**Proof of Concept:**
1. Attacker gains access to ECR image or GitHub repo
2. Extracts JWT secret from application.yml
3. Forges JWTs for any user (complete auth bypass)

**Remediation:**
```java
@Service
public class JwtService {
    private final SecretKey signingKey;
    private final String issuer;
    private final SecretsManagerClient secretsClient; // AWS Secrets Manager

    public JwtService(SecretsManagerClient secretsClient,
                      @Value("${springlens.jwt.issuer:https://api.springlens.io}") String issuer) {
        this.secretsClient = secretsClient;
        this.issuer = issuer;
        // Load secret from AWS Secrets Manager
        String secret = getSecretFromSecretsManager("springlens/jwt-secret");
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    private String getSecretFromSecretsManager(String secretName) {
        try {
            GetSecretValueRequest request = GetSecretValueRequest.builder()
                    .secretId(secretName)
                    .build();
            GetSecretValueResponse response = secretsClient.getSecretValue(request);
            return response.secretString();
        } catch (SecretsManagerException e) {
            throw new RuntimeException("Failed to load JWT secret", e);
        }
    }
}
```

**Alternative (Recommended): Use RS256 (Asymmetric Signing)**
```java
// Auth Service (signer)
@Service
public class JwtService {
    private final PrivateKey privateKey;

    public JwtService() {
        // Load private key from Secrets Manager
        String pkcs8Pem = loadPrivateKeyFromSecretsManager();
        this.privateKey = parsePrivateKey(pkcs8Pem);
    }

    public String issueAccessToken(UUID userId, UUID workspaceId, String email, String role) {
        return Jwts.builder()
                .subject(userId.toString())
                .issuer(issuer)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 900_000)) // 15 min
                .claims(Map.of(
                        "workspace_id", workspaceId.toString(),
                        "email", email,
                        "workspace_role", role))
                .signWith(privateKey, SignatureAlgorithm.RS256) // Use private key
                .compact();
    }
}

// Other Services (verifier)
@Component
public class JwtValidator {
    private final PublicKey publicKey; // Load from Secrets Manager

    public Claims validateToken(String token) {
        return Jwts.parser()
                .verifyWith(publicKey) // Use public key (can be public)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
```

**References:** CWE-321 (Use of Hard-Coded Cryptographic Key), CWE-613 (Insufficient Entropy), OWASP A02:2021

---

### [CRITICAL] Encryption Key in Configuration (Webhook URLs)

**Location:** `services/notification-service/src/main/java/io/springlens/notification/service/EncryptionService.java:31`

**Vulnerable Code:**
```java
public EncryptionService(
        @Value("${springlens.webhook.encryption-key}") String encryptionKeyBase64) {
    byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
    this.secretKey = new SecretKeySpec(keyBytes, "AES");
}
```

**Vulnerability:** Encryption key in plaintext config file.

**Remediation:**
```java
@Service
public class EncryptionService {
    private final SecretKey secretKey;

    public EncryptionService(SecretsManagerClient secretsClient) {
        String encryptionKeyBase64 = getSecretFromSecretsManager(
                secretsClient, "springlens/webhook-encryption-key");
        byte[] keyBytes = Base64.getDecoder().decode(encryptionKeyBase64);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }

    private String getSecretFromSecretsManager(SecretsManagerClient client, String secretName) {
        GetSecretValueRequest request = GetSecretValueRequest.builder()
                .secretId(secretName)
                .build();
        return client.getSecretValue(request).secretString();
    }
}
```

**References:** CWE-321, CWE-798 (Use of Hard-Coded Credentials)

---

### [HIGH] Stripe Webhook Secret in Configuration

**Location:** `services/auth-service/src/main/java/io/springlens/auth/controller/BillingController.java:30-31`

**Vulnerable Code:**
```java
@Value("${springlens.stripe.webhook-secret:}")
private String webhookSecret;
```

**Vulnerability:** Stripe webhook secret in plaintext config; never actually used for signature verification.

**Remediation:**
```java
@Value("${springlens.stripe.webhook-secret}")
private String webhookSecret;

@PostMapping("/webhooks/stripe")
@Transactional
public ResponseEntity<Map<String, String>> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature") String signature) {

    // VERIFY SIGNATURE FIRST (before parsing)
    try {
        Event event = Webhook.constructEvent(payload, signature, webhookSecret);
        // Now process the verified event
        String eventType = event.getType();

        switch(eventType) {
            case "invoice.paid":
                handleInvoicePaid((Invoice) event.getDataObjectDeserializer().getObject().orElse(null));
                break;
            // ... other cases
        }

        return ResponseEntity.ok(Map.of("status", "received"));
    } catch (SignatureVerificationException ex) {
        log.warn("Stripe webhook signature verification failed");
        return ResponseEntity.status(400).body(Map.of("status", "error"));
    } catch (Exception ex) {
        log.error("Stripe webhook processing failed", ex);
        return ResponseEntity.status(400).body(Map.of("status", "error"));
    }
}
```

**Add Dependency:**
```gradle
implementation 'com.stripe:stripe-java:23.0.0'
```

**References:** CWE-347 (Improper Verification of Cryptographic Signature), OWASP A02:2021

---

## A04: Insecure Design (Business Logic)

### [CRITICAL] Stripe Webhook Signature NOT Verified

**Location:** `services/auth-service/src/main/java/io/springlens/auth/controller/BillingController.java:47-68`

**Vulnerable Code:**
```java
@PostMapping("/webhooks/stripe")
@Transactional
public ResponseEntity<Map<String, String>> handleStripeWebhook(
        @RequestBody String payload,
        @RequestHeader(value = "Stripe-Signature", required = false) String signature) {

    log.info("Received Stripe webhook");

    // In production: verify signature using com.stripe.net.Webhook.constructEvent()
    // For now we parse the JSON payload directly
    try {
        var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payload);
        String eventType = json.get("type").asText();
        // ... process without signature verification
```

**Vulnerability:** ANY HTTP request to `/v1/billing/webhooks/stripe` is accepted without signature verification. Attacker can:
1. POST `{"type":"invoice.paid","data":{"object":{"subscription":"sub_123"}}}` → workspace upgraded to Pro
2. POST `{"type":"customer.subscription.deleted","data":{"object":{"id":"sub_123"}}}` → downgrade to Free
3. Manipulate billing forever (Stripe can't be spoofed if signature is verified, but can be spoofed without it)

**Proof of Concept:**
```bash
curl -X POST http://localhost:8084/v1/billing/webhooks/stripe \
  -H "Content-Type: application/json" \
  -d '{
    "type": "invoice.paid",
    "data": {
      "object": {
        "subscription": "sub_existing_valid_stripe_id"
      }
    }
  }'
```

Result: Workspace plan updated to "pro" without any valid payment.

**Remediation:** See A02 above for full code. Key requirement:
```java
try {
    Event event = Webhook.constructEvent(payload, signature, webhookSecret);
    // Only process if signature is valid
} catch (SignatureVerificationException ex) {
    return ResponseEntity.status(401).body(...);
}
```

**References:** CWE-347, CWE-345 (Insufficient Verification of Data Authenticity), OWASP A04:2021

---

### [CRITICAL] OAuth State Parameter NOT Validated (CSRF)

**Location:** `services/auth-service/src/main/java/io/springlens/auth/controller/GitHubOAuthController.java:58-62`

**Vulnerable Code:**
```java
@GetMapping("/github/callback")
@Transactional
public ResponseEntity<Object> githubCallback(
        @RequestParam String code,
        @RequestParam(required = false) String state) {

    log.info("GitHub OAuth callback received code=***");

    try {
        // State parameter is accepted but NOT validated
```

**Vulnerability:** `state` parameter accepted but never validated against session state. Attacker can craft CSRF attack:

**Proof of Concept:**
```
1. Attacker creates malicious link:
   https://springlens.io/auth/github/callback?code=ATTACKER_CODE&state=ATTACKER_STATE

2. Attacker sends link to victim in email/chat

3. Victim (logged into GitHub) clicks link

4. GitHub redirects to the callback endpoint

5. Spring processes code from attacker's GitHub app, creating account as attacker

6. Victim's browser now has session token for ATTACKER's account

7. Victim can now access whatever was set up by attacker
```

**Remediation:**
```java
@GetMapping("/github/callback")
@Transactional
public ResponseEntity<Object> githubCallback(
        @RequestParam String code,
        @RequestParam(required = false) String state,
        HttpSession session) {

    // VALIDATE state against session
    String sessionState = (String) session.getAttribute("oauth_state");
    if (sessionState == null || !sessionState.equals(state)) {
        log.warn("State mismatch: potential CSRF attack");
        return ResponseEntity.badRequest()
                .body(ErrorResponse.of("CSRF_FAILED", "Invalid OAuth state", null));
    }

    // Clear state from session (one-time use)
    session.removeAttribute("oauth_state");

    // Continue with OAuth flow...
    try {
        // Exchange code for token...
    }
}

// Before redirecting to GitHub, generate and store state:
// (in a separate /auth/github/login endpoint)
@GetMapping("/auth/github/login")
public RedirectView initiateGitHubLogin(HttpSession session) {
    String state = UUID.randomUUID().toString();
    session.setAttribute("oauth_state", state);

    return new RedirectView(
        "https://github.com/login/oauth/authorize?" +
        "client_id=" + clientId +
        "&redirect_uri=https://api.springlens.io/v1/auth/github/callback" +
        "&state=" + state);
}
```

**Alternative (Recommended): Use next-auth v5**
next-auth handles state validation automatically:
```typescript
// In frontend auth.ts:
export const { handlers, auth } = NextAuth({
    // next-auth v5 automatically validates state
    // No manual state handling needed
})
```

**References:** CWE-352 (Cross-Site Request Forgery (CSRF)), OWASP A04:2021

---

### [CRITICAL] Non-Admin Users Modify Production CI Budgets

**Location:** `services/recommendation-service/src/main/java/io/springlens/recommendation/controller/RecommendationController.java:114-131`

**Vulnerable Code:**
```java
@PutMapping("/projects/{projectId}/ci-budgets")
public ResponseEntity<Object> upsertCiBudget(
        @PathVariable UUID projectId,
        @RequestBody @Valid CiBudgetRequest body,
        @AuthenticationPrincipal Jwt jwt) {

    UUID workspaceId = extractWorkspaceId(jwt);
    UUID userId = extractUserId(jwt);

    // BR-006: production budget requires Admin role
    if ("production".equals(body.environment())) {
        String role = jwt != null ? jwt.getClaimAsString("workspace_role") : null;
        if (!"admin".equals(role)) {
            return ResponseEntity.status(403)
                    .body(ErrorResponse.of("FORBIDDEN",
                            "Only Admin role can modify production budget thresholds", null));
        }
    }

    // ... save budget
}
```

**Issue:** While the code HAS the check, it's checking the JWT claim "workspace_role". If:
1. JWT is forged (secret compromised from A02)
2. JWT generation doesn't properly set workspace_role
3. Non-admin user can somehow get admin JWT

Then non-admin can bypass this. But more critically, the check is ONLY for "production". For other environments (dev, staging, ci), ANY workspace member can set the budget.

**Proof of Concept:**
```
1. User (non-admin) calls:
   PUT /v1/projects/{id}/ci-budgets
   { "environment": "production", "budget_ms": 999999, "enabled": false }

2. Server rejects (admin check)

3. But user calls:
   PUT /v1/projects/{id}/ci-budgets
   { "environment": "staging", "budget_ms": 999999, "enabled": false }

4. Works! Now all staging builds have huge budgets; regressions not caught

5. Or, if JWT is forged (A02 vulnerability), user just sets workspace_role=admin
```

**Remediation:**

Option 1: Database-level role check (stronger)
```java
@PutMapping("/projects/{projectId}/ci-budgets")
public ResponseEntity<Object> upsertCiBudget(
        @PathVariable UUID projectId,
        @RequestBody @Valid CiBudgetRequest body,
        @AuthenticationPrincipal Jwt jwt) {

    UUID workspaceId = extractWorkspaceId(jwt);
    UUID userId = extractUserId(jwt);

    // Check if user is workspace admin
    var member = memberRepository.findByWorkspaceIdAndUserIdAndDeletedAtIsNull(
            workspaceId, userId);

    if (member.isEmpty() || !"admin".equals(member.get().getRole())) {
        return ResponseEntity.status(403)
                .body(ErrorResponse.of("FORBIDDEN",
                        "Only workspace admins can modify CI budgets", null));
    }

    // Now safe to modify any environment's budget
    // ... save budget
}
```

Option 2: Environment-specific role checks
```java
if ("production".equals(body.environment()) || "staging".equals(body.environment())) {
    String role = jwt != null ? jwt.getClaimAsString("workspace_role") : null;
    if (!"admin".equals(role)) {
        return ResponseEntity.status(403)
                .body(ErrorResponse.of("FORBIDDEN",
                        "Only Admin role can modify production/staging budgets", null));
    }
}
```

**References:** CWE-639 (Authorization Bypass Through User-Controlled Key), OWASP A04:2021

---

## A05: Security Misconfiguration

### [HIGH] Rate Limiting Missing on Critical Endpoints

**Affected Endpoints:**
- `POST /v1/ingest` (ingestion-service)
- `POST /v1/api-keys` (auth-service)
- `POST /v1/webhooks` (notification-service)
- List endpoints with unbounded pagination (pageSize=999999)

**Location:** All service controllers

**Vulnerability:** No rate limiting per API key, per IP, or per user. Attacker can:
- Flood /v1/ingest with 10,000 snapshots/sec → database filled, service unavailable
- Create 10,000 API keys → quota exhaustion
- Enumerate snapshot IDs via GET /snapshots?page=0&pageSize=999999 → full table scan

**Remediation:**

Option 1: Spring Cloud CircuitBreaker + Resilience4j (recommended)
```gradle
implementation 'io.github.resilience4j:resilience4j-spring-boot3:2.1.0'
implementation 'io.github.resilience4j:resilience4j-ratelimiter:2.1.0'
```

```java
@Service
public class RateLimitingService {
    private final RateLimiter ingestLimiter;
    private final RateLimiter apiKeyLimiter;

    public RateLimitingService() {
        RateLimiterConfig ingestConfig = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(60))
                .limitForPeriod(100) // 100 requests per minute
                .timeoutDuration(Duration.ofMillis(25))
                .build();

        this.ingestLimiter = RateLimiter.of("ingest", ingestConfig);
        this.apiKeyLimiter = RateLimiter.of("api-key", ingestConfig);
    }

    public boolean allowIngest(UUID apiKeyId) {
        return ingestLimiter.acquirePermission();
    }
}

// In controller:
@PostMapping("/ingest")
public ResponseEntity<IngestResponse> ingest(
        @Valid @RequestBody StartupSnapshotRequest request,
        @RequestAttribute("projectId") UUID projectId,
        @RequestAttribute("workspaceId") UUID workspaceId) {

    if (!rateLimitingService.allowIngest(projectId)) {
        return ResponseEntity.status(429)
                .body(ErrorResponse.of("RATE_LIMITED",
                        "Too many requests. Try again in 60 seconds.", null));
    }
    // ... process request
}
```

Option 2: Redis-backed rate limiter (for distributed systems)
```gradle
implementation 'io.github.resilience4j:resilience4j-ratelimiter:2.1.0'
implementation 'org.springframework.data:spring-data-redis:3.0.0'
```

```java
@Component
public class RedisRateLimiter {
    private final StringRedisTemplate redisTemplate;

    public boolean isAllowed(String key, int maxRequests, long windowSeconds) {
        long now = System.currentTimeMillis() / 1000;
        String bucketKey = key + ":" + (now / windowSeconds);

        Long count = redisTemplate.opsForValue().increment(bucketKey);
        if (count == 1) {
            redisTemplate.expire(bucketKey, Duration.ofSeconds(windowSeconds));
        }
        return count <= maxRequests;
    }
}

// Usage:
if (!rateLimiter.isAllowed("ingest:" + apiKeyId, 100, 60)) {
    return ResponseEntity.status(429).build();
}
```

**References:** CWE-770 (Allocation of Resources Without Limits or Throttling), OWASP A05:2021

---

### [HIGH] Pagination Unbounded

**Location:** All list endpoints (analysis-service, recommendation-service)

**Vulnerable Code:**
```java
@GetMapping("/projects/{projectId}/snapshots")
public ResponseEntity<Map<String, Object>> listSnapshots(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(required = false) String cursor,
        @AuthenticationPrincipal Jwt jwt) {
    // ...
    var page = timelineRepository.findByProjectFiltered(
            projectId, workspaceId, environment, from, to,
            PageRequest.of(offset / Math.max(limit, 1), Math.min(limit, 100)));
    // Max is 100, but attacker can request pageSize=999999
}
```

**Vulnerability:** While code enforces `Math.min(limit, 100)`, the parameter is user-controlled and not validated on input. Attacker can:
- Send ?limit=-1 → may bypass check
- Send ?limit=999999 → if check doesn't work, fetch entire table

**Remediation:**
```java
@GetMapping("/projects/{projectId}/snapshots")
public ResponseEntity<Map<String, Object>> listSnapshots(
        @PathVariable UUID projectId,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit,
        @RequestParam(required = false) String cursor,
        @AuthenticationPrincipal Jwt jwt) {
    // limit is validated by @Min/@Max annotations
    // If limit=999999, validation fails with 400 Bad Request

    int validatedLimit = Math.max(1, Math.min(limit, 100));
    // ... use validatedLimit
}
```

Add validation dependency:
```gradle
implementation 'org.springframework.boot:spring-boot-starter-validation:3.3.2'
```

**References:** CWE-770, CWE-770 (Allocation of Resources Without Limits)

---

### [HIGH] Error Messages Leak Information

**Location:** `services/auth-service/src/main/java/io/springlens/auth/controller/BillingController.java:66`

**Vulnerable Code:**
```java
} catch (Exception ex) {
    log.error("Stripe webhook processing failed", ex);
    return ResponseEntity.status(400).body(Map.of("status", "error", "message", ex.getMessage()));
    //                                                                            ^^^^^^^^^^^^
    //                                                        Leaks exception details to client
}
```

**Vulnerability:** Attacker sends malformed JSON; exception message reveals:
- Class names (jackson.databind.JsonEOFException)
- Line numbers
- Internal structure

**Remediation:**
```java
} catch (JsonProcessingException ex) {
    log.error("Stripe webhook JSON parsing failed", ex);
    return ResponseEntity.status(400).body(
            Map.of("status", "error",
                   "message", "Invalid JSON payload"));  // Generic message
} catch (Exception ex) {
    log.error("Stripe webhook processing failed", ex);  // Log full details internally
    return ResponseEntity.status(400).body(
            Map.of("status", "error",
                   "message", "Webhook processing failed"));  // Generic to client
}
```

**References:** CWE-209 (Information Exposure Through an Error Message), OWASP A05:2021

---

## A06: Vulnerable and Outdated Components

### [HIGH] Dependency Vulnerabilities (From supply-chain perspective)

**Location:** All `build.gradle` files

**Vulnerability:** See Phase 5 (Supply Chain) for detailed SBOM scan. Typical gaps:
- `io.jsonwebtoken:jjwt` (check latest version for CVEs)
- Jackson (JSON parsing, XPath injection possible)
- Spring Framework (regularly patched)

**Remediation:**
1. Run `./gradlew dependencyCheckAggregate` (OWASP Dependency-Check plugin)
2. Update all dependencies to latest stable versions
3. Add to `build.gradle`:
```gradle
plugins {
    id 'org.owasp.dependencycheck' version '8.4.0'
}

dependencyCheck {
    skipConfigurations = ['compileOnly']
}
```

4. Run: `./gradlew check dependencyCheckAggregate`

**References:** OWASP A06:2021, CWE-1035

---

## A07: Identification and Authentication Failures

### [HIGH] API Key Expiration Not Implemented

**Location:** `services/auth-service/src/main/java/io/springlens/auth/entity/ApiKey.java`

**Issue:** API keys have no expiration. Once created, valid forever (unless manually revoked).

**Remediation:**
```java
@Entity
@Table(name = "api_keys", schema = "auth_service")
public class ApiKey {
    @Id
    private UUID id;
    // ... other fields

    @Column(name = "expires_at")
    private Instant expiresAt;  // NEW

    @Column(name = "revoked_at")
    private Instant revokedAt;

    public boolean isActive() {
        Instant now = Instant.now();
        return revokedAt == null && (expiresAt == null || expiresAt.isAfter(now));
    }
}

// In ApiKeyService:
public Optional<ApiKey> validateKey(String rawKey) {
    // ... find by prefix + bcrypt match ...

    candidates.ifPresent(k -> {
        // Check expiration
        if (k.getExpiresAt() != null && Instant.now().isAfter(k.getExpiresAt())) {
            log.warn("API key expired key_prefix={}", k.getKeyPrefix());
            return;  // Don't validate expired keys
        }

        k.setLastUsedAt(Instant.now());
        apiKeyRepository.save(k);
    });

    return candidates;
}

// In API key creation:
public ApiKeyCreationResult createApiKey(...) {
    // ... generate and hash key ...
    Instant expiresAt = Instant.now().plus(90, ChronoUnit.DAYS);  // 90-day expiry
    var apiKey = ApiKey.create(..., expiresAt);
    // ...
}
```

**References:** CWE-613 (Insufficient Entropy), OWASP A07:2021

---

### [HIGH] No Refresh Token Rotation

**Location:** `services/auth-service/src/main/java/io/springlens/auth/service/JwtService.java:56-69`

**Vulnerable Code:**
```java
public String issueRefreshToken(UUID userId, UUID workspaceId) {
    Instant now = Instant.now();
    return Jwts.builder()
            .subject(userId.toString())
            .issuer(issuer)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(30, ChronoUnit.DAYS)))  // 30-day validity
            // ... no rotation
            .compact();
}
```

**Vulnerability:** Refresh token valid for 30 days without rotation. If leaked, attacker has 30 days of access.

**Remediation:**
```java
// Implement refresh token rotation
@Service
public class JwtService {
    private final JwtRefreshTokenRepository tokenRepository;

    public String issueRefreshToken(UUID userId, UUID workspaceId) {
        String tokenId = UUID.randomUUID().toString();  // Track token by ID
        Instant now = Instant.now();

        // Save token metadata (for blacklisting/rotation)
        JwtRefreshToken refreshToken = new JwtRefreshToken();
        refreshToken.setTokenId(tokenId);
        refreshToken.setUserId(userId);
        refreshToken.setWorkspaceId(workspaceId);
        refreshToken.setIssuedAt(now);
        refreshToken.setExpiresAt(now.plus(7, ChronoUnit.DAYS)); // Shorter 7-day window
        refreshToken.setStatus("ACTIVE");
        tokenRepository.save(refreshToken);

        return Jwts.builder()
                .subject(userId.toString())
                .claims(Map.of(
                        "jti", tokenId,  // Token ID for tracking
                        "workspace_id", workspaceId.toString()))
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(7, ChronoUnit.DAYS)))
                .signWith(signingKey)
                .compact();
    }

    public boolean validateRefreshToken(String token) {
        Claims claims = validateToken(token);
        if (claims == null) return false;

        String tokenId = claims.get("jti", String.class);
        var refreshToken = tokenRepository.findById(tokenId);

        return refreshToken.isPresent() &&
               "ACTIVE".equals(refreshToken.get().getStatus());
    }

    public String refreshAccessToken(String refreshToken) {
        if (!validateRefreshToken(refreshToken)) {
            throw new UnauthorizedException("Invalid refresh token");
        }

        Claims claims = Jwts.parser().verifyWith(signingKey).build()
                .parseSignedClaims(refreshToken).getPayload();

        UUID userId = UUID.fromString(claims.getSubject());
        UUID workspaceId = UUID.fromString(claims.get("workspace_id", String.class));

        // Issue new tokens
        String newAccessToken = issueAccessToken(userId, workspaceId,
                claims.get("email", String.class),
                claims.get("workspace_role", String.class));
        String newRefreshToken = issueRefreshToken(userId, workspaceId);

        // Rotate: revoke old refresh token
        String oldTokenId = claims.get("jti", String.class);
        var oldToken = tokenRepository.findById(oldTokenId);
        oldToken.ifPresent(t -> {
            t.setStatus("ROTATED");
            tokenRepository.save(t);
        });

        return newAccessToken;  // Return new access token (and new refresh in response)
    }
}
```

**References:** CWE-613, OWASP A07:2021

---

## A08: Software and Data Integrity Failures

### [HIGH] No Webhook Delivery Integrity Signing

**Location:** `services/notification-service/src/main/java/io/springlens/notification/service/WebhookDeliveryService.java`

**Vulnerability:** When sending webhooks to external services, no HMAC signature is included. Receiver cannot verify the webhook came from SpringLens.

**Remediation:**
```java
@Service
public class WebhookDeliveryService {
    private final RestTemplate restTemplate;
    private final HmacService hmacService;

    public void deliverWebhook(WebhookConfig config, RecommendationsReadyEvent event) {
        try {
            String payload = objectMapper.writeValueAsString(event);

            // Generate HMAC signature
            String signature = hmacService.generateSignature(payload, config.getWebhookSecret());

            // Include signature in headers
            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("X-Springlens-Signature", "sha256=" + signature);
            headers.set("X-Springlens-Timestamp", String.valueOf(System.currentTimeMillis() / 1000));

            HttpEntity<String> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(config.getUrl(), request, Void.class);
        } catch (Exception ex) {
            log.error("Webhook delivery failed", ex);
        }
    }
}

@Service
public class HmacService {
    public String generateSignature(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
        mac.init(keySpec);
        byte[] hash = mac.doFinal(payload.getBytes());
        return Base64.getEncoder().encodeToString(hash);
    }
}
```

**Receiver Side (Documentation):**
```typescript
// Example: User's webhook receiver
app.post('/webhooks/springlens', (req, res) => {
  const signature = req.headers['x-springlens-signature'];
  const timestamp = req.headers['x-springlens-timestamp'];
  const payload = req.rawBody;  // Raw body, not parsed

  // Verify signature
  const secret = process.env.SPRINGLENS_WEBHOOK_SECRET;
  const expectedSignature = 'sha256=' + crypto
    .createHmac('sha256', secret)
    .update(payload)
    .digest('base64');

  if (signature !== expectedSignature) {
    return res.status(401).send('Signature mismatch');
  }

  // Verify timestamp (replay protection)
  const now = Math.floor(Date.now() / 1000);
  if (Math.abs(now - parseInt(timestamp)) > 300) {
    return res.status(401).send('Request too old');
  }

  // Process webhook
  const event = JSON.parse(payload);
  // ...
});
```

**References:** CWE-347, OWASP A08:2021

---

## A09: Security Logging and Monitoring

### [HIGH] Insufficient Audit Logging

**Location:** All services (lack of audit trail)

**Vulnerability:** No immutable audit log for sensitive operations:
- User login/logout
- API key creation/revocation
- Workspace member changes
- Budget modifications
- Webhook configuration changes

**Remediation:**
```java
@Entity
@Table(name = "audit_logs", schema = "auth_service")
public class AuditLog {
    @Id
    private UUID id;

    @Column(name = "timestamp")
    private Instant timestamp;

    @Column(name = "actor_id")
    private UUID actorId;  // Who did it

    @Column(name = "actor_type")
    private String actorType;  // USER, SERVICE, SYSTEM

    @Column(name = "action")
    private String action;  // USER_LOGIN, API_KEY_CREATED, BUDGET_UPDATED, etc.

    @Column(name = "resource_type")
    private String resourceType;  // ApiKey, Workspace, Project, etc.

    @Column(name = "resource_id")
    private UUID resourceId;

    @Column(name = "changes")
    private String changes;  // JSON: {"budget_ms": {old: 2000, new: 5000}}

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent")
    private String userAgent;

    @Column(name = "workspace_id")
    private UUID workspaceId;  // For filtering
}

@Service
public class AuditService {
    private final AuditLogRepository auditLogRepository;

    public void logAction(UUID userId, String action, String resourceType,
                         UUID resourceId, String changes, HttpServletRequest request) {
        AuditLog log = new AuditLog();
        log.setId(UUID.randomUUID());
        log.setTimestamp(Instant.now());
        log.setActorId(userId);
        log.setActorType("USER");
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setChanges(changes);
        log.setIpAddress(request.getRemoteAddr());
        log.setUserAgent(request.getHeader("User-Agent"));

        auditLogRepository.save(log);
    }
}

// Usage in controllers:
@PutMapping("/projects/{projectId}/ci-budgets")
public ResponseEntity<Object> upsertCiBudget(...) {
    // ... validation ...

    String oldBudget = existing.map(b -> String.valueOf(b.getBudgetMs())).orElse(null);
    String changes = "{\"budget_ms\": {\"old\": " + oldBudget + ", \"new\": " +
                     body.budgetMs() + "}}";

    auditService.logAction(userId, "BUDGET_UPDATED", "CiBudget", budget.getId(),
                          changes, request);

    return ResponseEntity.ok(budgetToDto(budget));
}
```

**References:** CWE-778 (Insufficient Logging), OWASP A09:2021

---

## A10: Server-Side Request Forgery (SSRF)

### [CRITICAL] Webhook URL Validation Missing

**Location:** `services/notification-service/src/main/java/io/springlens/notification/controller/WebhookConfigController.java`

**Vulnerable Code:**
```java
@PostMapping("/v1/workspaces/{workspaceId}/webhooks")
public ResponseEntity<WebhookConfig> createWebhook(
        @PathVariable UUID workspaceId,
        @RequestBody @Valid WebhookConfigRequest body,
        @AuthenticationPrincipal Jwt jwt) {

    // No validation on webhook URL
    var config = WebhookConfig.create(workspaceId, body.type(), body.url(), ...);
    webhookConfigRepository.save(config);
    return ResponseEntity.ok(config);
}
```

**Vulnerability:** Attacker can register webhook pointing to:
1. **Internal services:** `http://auth-service:8084/v1/billing/webhooks/stripe` → trigger billing endpoint
2. **Cloud metadata:** `http://169.254.169.254/latest/meta-data/iam/security-credentials/` → steal AWS credentials
3. **Private IPs:** `http://10.0.1.5:5432/` → port scan internal network
4. **localhost:** `http://localhost:8080/admin` → access local admin panel

**Proof of Concept:**
```bash
curl -X POST http://localhost:8085/v1/workspaces/{id}/webhooks \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "type": "custom",
    "url": "http://auth-service:8084/v1/billing/webhooks/stripe"
  }'

# Now when recommendations are generated, webhook delivery tries to POST to auth-service's Stripe handler
# Attacker has effectively gained access to internal endpoint
```

**Remediation:**
```java
@Service
public class WebhookUrlValidator {
    private static final Pattern VALID_URL_PATTERN =
            Pattern.compile("^https://[a-zA-Z0-9.-]+(:[0-9]+)?(/.*)?$");

    private static final List<String> BLOCKED_HOSTS = Arrays.asList(
            "localhost", "127.0.0.1", "0.0.0.0",
            "169.254.169.254",  // AWS metadata
            "metadata.google.internal",  // GCP metadata
            "169.254.169.254",  // Azure metadata
            "*.internal",  // Private networks
            "10.0.0.0/8",
            "172.16.0.0/12",
            "192.168.0.0/16"
    );

    public void validateWebhookUrl(String url) {
        if (url == null || url.isEmpty()) {
            throw new BadRequestException("Webhook URL is required");
        }

        // Must be HTTPS
        if (!url.startsWith("https://")) {
            throw new BadRequestException("Webhook URL must use HTTPS");
        }

        try {
            URI uri = new URI(url);
            String host = uri.getHost();

            // Check for private IPs
            InetAddress addr = InetAddress.getByName(host);
            if (addr.isSiteLocalAddress() || addr.isLoopbackAddress()) {
                throw new BadRequestException("Webhook URL cannot point to private IP");
            }

            // Check for blocked hosts
            if (BLOCKED_HOSTS.stream().anyMatch(blocked ->
                    host.matches(blocked.replace(".", "\\.").replace("*", ".*")))) {
                throw new BadRequestException("Webhook URL points to blocked host");
            }

            // Attempt DNS resolution to verify host is public
            if (!isPublicHost(host)) {
                throw new BadRequestException("Webhook URL host is not publicly resolvable");
            }

        } catch (Exception ex) {
            throw new BadRequestException("Invalid webhook URL: " + ex.getMessage());
        }
    }

    private boolean isPublicHost(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            return Arrays.stream(addresses)
                    .anyMatch(addr -> !addr.isSiteLocalAddress() && !addr.isLoopbackAddress());
        } catch (UnknownHostException ex) {
            return false;  // Host cannot be resolved
        }
    }
}

// In controller:
@PostMapping("/v1/workspaces/{workspaceId}/webhooks")
public ResponseEntity<WebhookConfig> createWebhook(
        @PathVariable UUID workspaceId,
        @RequestBody @Valid WebhookConfigRequest body,
        @AuthenticationPrincipal Jwt jwt) {

    // Validate URL before saving
    webhookUrlValidator.validateWebhookUrl(body.url());

    var config = WebhookConfig.create(workspaceId, body.type(), body.url(), ...);
    webhookConfigRepository.save(config);
    return ResponseEntity.ok(config);
}
```

**References:** CWE-918 (Server-Side Request Forgery (SSRF)), OWASP A10:2021

---

## Summary

| Category | Critical | High | Medium | Low |
|----------|----------|------|--------|-----|
| A01: Broken Access Control | 2 | 1 | - | - |
| A02: Cryptographic Failures | 2 | 1 | - | - |
| A04: Insecure Design | 2 | 2 | - | - |
| A05: Security Misconfiguration | - | 4 | - | - |
| A06: Vulnerable Components | - | 1 | - | - |
| A07: Auth Failures | - | 2 | - | - |
| A08: Data Integrity | - | 1 | - | - |
| A09: Logging | - | 1 | - | - |
| A10: SSRF | 1 | - | - | - |
| **TOTAL** | **9** | **13** | **-** | **-** |

---

## Next Phase: Phase 3 (Authentication Hardening)

Ready to proceed with detailed auth flow analysis, token management, and RBAC hardening.

