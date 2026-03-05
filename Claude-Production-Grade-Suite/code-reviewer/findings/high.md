# High Findings
**Severity:** Must fix before production | Architectural violation | Reliability risk

---

## HIGH-001: Missing Explicit PostgreSQL RLS Policy Enforcement

**Severity:** HIGH
**Category:** Architecture | Security
**Location:** Migration files (not visible in code review)

**Description:**
ADR-005 specifies that PostgreSQL Row-Level Security (RLS) policies should be enabled and enforced at the database level as a defense layer. The application layer correctly filters queries by `workspace_id`, but the database-level enforcement is not visible in the reviewed code.

**Impact:**
If a service layer bug accidentally queries without workspace_id filtering, or if an attacker gains database access, RLS would prevent data leakage. Without RLS, multi-tenant isolation is 100% dependent on application-layer correctness. This violates defense-in-depth principle.

**Evidence:**
- `IngestionService.getStatus(UUID snapshotId, UUID workspaceId)` uses `.findByIdAndWorkspaceId()` for filtering
- No SQL migration files were reviewed to verify RLS policies exist
- ADR-005 explicitly requires: `ALTER TABLE startup_snapshots ENABLE ROW LEVEL SECURITY; CREATE POLICY tenant_isolation ...`

**Recommendation:**
Verify that all tenant-scoped tables have RLS policies enabled. If missing, add migrations:

```sql
-- For all tenant-scoped tables:
ALTER TABLE startup_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_snapshots ON startup_snapshots
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

ALTER TABLE startup_timelines ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation_timelines ON startup_timelines
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);

-- Repeat for all tenant-scoped tables
```

**Action:** Review and verify all migration files contain RLS policy creation. Add missing policies if needed.

---

## HIGH-002: Missing Circuit Breaker Visibility for Inter-Service REST Calls

**Severity:** HIGH
**Category:** Reliability | Resilience
**Location:** Service configurations (not visible in sampled code)

**Description:**
ADR-002 specifies "circuit breaker (Resilience4j), 3s timeout, 3 retries with exponential backoff" for inter-service REST calls. The ingestion, analysis, and recommendation services appear to communicate primarily via Kafka (which is correct), but any service-to-service REST calls (e.g., analysis calling auth-service to validate a user) need circuit breaker protection.

The code review found no explicit circuit breaker configuration in the sampled SecurityConfig or service classes. This may be configured in `application.yml` (not reviewed), but should be explicitly verified.

**Impact:**
Without circuit breakers, a slow or failing downstream service (auth, for example) can cause cascading failures. Requests will pile up, timeouts will trigger, and the entire system can become unresponsive. This violates reliability and scalability goals.

**Evidence:**
- ADR-002 specifies circuit breaker requirements
- No `@CircuitBreaker` or `resilience4j` config visible in sampled code
- HTTP client configuration not reviewed (may be in application.yml)

**Recommendation:**
1. Add Resilience4j configuration to `application.yml`:
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 3s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        waitDurationInOpenState: 10s
    instances:
      auth-service:
        baseConfig: default
      analysis-service:
        baseConfig: default
```

2. Apply to REST client calls:
```java
@CircuitBreaker(name = "auth-service", fallbackMethod = "authServiceFallback")
@Retry(name = "auth-service", fallbackMethod = "authServiceFallback")
@Timeout(duration = "3s")
public User getUser(UUID userId) {
    return restTemplate.getForObject("http://auth:8084/v1/users/" + userId, User.class);
}

public User authServiceFallback(UUID userId, Exception ex) {
    log.warn("Auth service circuit breaker triggered, returning cached user");
    return userCache.getOrElse(userId, () -> new User(userId, "Unknown", Role.VIEWER));
}
```

**Action:** Verify Resilience4j configuration exists in application.yml files and is applied to all inter-service REST calls. Add if missing.

---

## HIGH-003: Missing Explicit Kafka Consumer Error Handling & Dead-Letter Queue

**Severity:** HIGH
**Category:** Reliability | Data Processing
**Location:** `StartupEventConsumer.consume()`, line 102-105 (and similar consumers)

**Description:**
The `StartupEventConsumer` catches all exceptions and re-throws them (`catch (Exception ex) { throw ex; }`). While re-throwing allows Kafka to handle the failure (typically by retrying or moving to a dead-letter queue), there's no explicit configuration visible for:
1. Retry strategy (how many times should Kafka retry?)
2. Dead-letter queue configuration (where do failed messages go?)
3. Error logging context (are trace IDs included for debugging?)

**Impact:**
Without DLQ configuration, failed messages may be lost silently, or Kafka may retry indefinitely, causing processing delays. This violates observability and reliability goals.

**Evidence:**
```java
// StartupEventConsumer.consume() — lines 102-105
catch (Exception ex) {
    log.error("Analysis failed snapshot={}", event.snapshotId(), ex);
    throw ex;  // Re-throw but no DLQ config visible
}
```

**Recommendation:**
1. Configure Kafka consumer with explicit retry and DLQ:
```yaml
spring:
  kafka:
    listener:
      type: batch
      poll-timeout: 3000
      ack-mode: manual_immediate  # Ack after handler completes
    consumer:
      auto-offset-reset: earliest
      max-poll-records: 100
      session-timeout-ms: 60000
    producer:
      retries: 3
      acks: all

error-handling:
  dlq-enabled: true
  dlq-topic-prefix: "dead-letter"
  retry-attempts: 3
  retry-backoff-ms: 1000
```

2. Use Spring's `DeadLetterPublishingRecoverer`:
```java
@Bean
public ConsumerRecordRecoverer recoverer(KafkaTemplate<String, StartupEvent> kafkaTemplate) {
    return new DeadLetterPublishingRecoverer(kafkaTemplate);
}
```

3. Add structured logging:
```java
catch (Exception ex) {
    var traceId = MDC.get("X-Trace-ID");
    log.error("snapshot={} trace_id={} Analysis failed",
            event.snapshotId(), traceId, ex);
    throw ex;
}
```

**Action:** Verify Kafka consumer configuration in application.yml. Add DLQ configuration and DeadLetterPublishingRecoverer if missing.

---

## HIGH-004: Missing Input Validation on Budget Check Endpoint

**Severity:** HIGH
**Category:** API Robustness | Data Validation
**Location:** `IngestController.checkBudget()`, lines 62-69

**Description:**
The budget check endpoint accepts `budget_ms` as a query parameter without explicit validation:
```java
@GetMapping("/snapshots/{snapshotId}/budget-check")
public ResponseEntity<Object> checkBudget(
        @PathVariable UUID snapshotId,
        @RequestParam("budget_ms") int budgetMs,  // No @Min/@Max annotations
        @RequestAttribute("workspaceId") UUID workspaceId) {
```

The OpenAPI spec specifies: `budget_ms` must be between 100 and 300,000 milliseconds (per ingestion-service.yaml). The controller should enforce this with `@Min(100) @Max(300000)`.

**Impact:**
- Negative or zero budget values bypass the check silently
- Extremely large budget values cause integer overflow or slow processing
- API spec is not enforced in code, creating a testing & validation gap

**Evidence:**
- OpenAPI spec: `minimum: 100, maximum: 300000`
- Code: `int budgetMs` with no validation annotations

**Recommendation:**
Add validation:
```java
@GetMapping("/snapshots/{snapshotId}/budget-check")
public ResponseEntity<Object> checkBudget(
        @PathVariable UUID snapshotId,
        @RequestParam("budget_ms") @Min(100) @Max(300000) int budgetMs,
        @RequestAttribute("workspaceId") UUID workspaceId) {
    // validation happens automatically
}
```

**Action:** Add `@Min(100) @Max(300000)` to the budget_ms parameter in IngestController. Add similar validation to all request parameters per OpenAPI spec.

---

## Summary

| Finding ID | Title | Impact | Action Required |
|------------|-------|--------|-----------------|
| HIGH-001 | Missing PostgreSQL RLS Policies | Multi-tenant isolation risk | Verify migrations, add if missing |
| HIGH-002 | Missing Circuit Breaker Config | Cascading failure risk | Add Resilience4j to all inter-service calls |
| HIGH-003 | Missing DLQ Configuration | Message loss risk | Configure Kafka DLQ and error handler |
| HIGH-004 | Missing Input Validation | API abuse risk | Add @Min/@Max annotations |

**Recommendation:** Fix all HIGH findings before production deployment.
