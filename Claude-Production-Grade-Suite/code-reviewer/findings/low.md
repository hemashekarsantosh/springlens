# Low Findings
**Severity:** Advisory | Style | Minor optimizations

---

## LOW-001: Missing Javadoc on Public API Methods

**Severity:** LOW
**Category:** Documentation
**Location:** Multiple controller classes (IngestController, TimelineController, etc.)

**Description:**
Public endpoint methods lack Javadoc documentation:

```java
// Current:
@PostMapping("/ingest")
public ResponseEntity<Object> ingest(
        @Valid @RequestBody StartupSnapshotRequest request,
        @RequestAttribute("projectId") UUID projectId,
        @RequestAttribute("workspaceId") UUID workspaceId) {
    // ...
}

// Better:
/**
 * Ingests a startup snapshot from a JVM agent.
 *
 * <p>Payloads are deduplicated by (project_id, environment, git_commit_sha) within 60 seconds.
 * Returns 202 Accepted immediately; analysis is async.
 *
 * @param request the startup telemetry payload (max 10MB)
 * @param projectId the project ID (from API key)
 * @param workspaceId the workspace ID (from API key)
 * @return 202 if new snapshot, 200 if deduplicated
 * @throws PayloadTooLargeException if payload > 10MB
 */
@PostMapping("/ingest")
public ResponseEntity<Object> ingest(...) { ... }
```

**Impact:**
- IDE autocomplete doesn't show endpoint documentation
- API docs generation (Springdoc) are less complete
- New developers must read code to understand behavior

**Recommendation:**
Add Javadoc to all public controller methods and public services.

---

## LOW-002: Inconsistent Exception Response Format

**Severity:** LOW
**Category:** API Consistency
**Location:** GlobalExceptionHandler vs custom handlers

**Description:**
Error responses may not follow a consistent format. The OpenAPI spec defines:
```json
{
  "code": "BUDGET_EXCEEDED",
  "message": "Startup time 6000ms exceeds budget 2000ms",
  "details": { ... },
  "trace_id": "req-12345"
}
```

But custom exception classes may return different formats.

**Recommendation:**
Ensure all exceptions map through GlobalExceptionHandler and return the consistent format. Test that all error codes match the OpenAPI spec.

---

## LOW-003: Missing Request/Response Size Metrics

**Severity:** LOW
**Category:** Observability
**Location:** All controllers

**Description:**
There are no visible metrics for request/response sizes. Understanding payload sizes helps optimize for network bandwidth and identify bloated responses.

**Recommendation:**
Add Micrometer metrics:
```java
@Bean
public MeterFilter payloadMetrics() {
    return MeterFilter.build()
        .onlyIf((id, config) -> id.getName().startsWith("http.server.requests"))
        .deny()
        .build();
}

@Component
public class HttpMetricsFilter implements Filter {
    private final MeterRegistry meterRegistry;

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain) {
        if (req instanceof HttpServletRequest && resp instanceof HttpServletResponse) {
            HttpServletRequest httpReq = (HttpServletRequest) req;
            var startTime = System.currentTimeMillis();

            chain.doFilter(req, resp);

            int responseSize = resp.getBufferSize();
            long duration = System.currentTimeMillis() - startTime;

            meterRegistry.timer("http.response.size",
                    "method", httpReq.getMethod(),
                    "path", httpReq.getServletPath())
                    .record(Duration.ofMillis(duration));
            meterRegistry.gauge("http.response.bytes",
                    httpReq.getServletPath(), responseSize);
        }
    }
}
```

---

## LOW-004: Missing OpenAPI @Tag Annotations for Grouping

**Severity:** LOW
**Category:** API Documentation
**Location:** Controllers

**Description:**
OpenAPI spec generation could be improved by grouping endpoints with @Tag:

```java
@RestController
@RequestMapping("/v1")
@Tag(name = "Ingestion", description = "Agent telemetry ingestion endpoints")
public class IngestController {
    @PostMapping("/ingest")
    @Operation(summary = "Ingest startup snapshot")
    public ResponseEntity<Object> ingest(...) { ... }
}
```

This improves Swagger UI grouping and API documentation clarity.

---

## LOW-005: Potential N+1 Query in Timeline Retrieval

**Severity:** LOW
**Category:** Performance
**Location:** StartupTimelineRepository (not reviewed, but likely)

**Description:**
The test plan mentions list endpoint pagination (INT-ANALYSIS-005). If the repository loads full JSONB timeline data for each snapshot in a list response, this could be inefficient.

**Recommendation:**
For list endpoints, consider returning a lightweight DTO:

```java
@Query("""
    SELECT new io.springlens.analysis.dto.TimelineListItemDto(
        t.snapshotId, t.totalStartupMs, t.bottleneckCount, t.createdAt)
    FROM StartupTimeline t
    WHERE t.projectId = :projectId AND t.workspaceId = :workspaceId
    ORDER BY t.createdAt DESC
""")
Page<TimelineListItemDto> listTimelines(UUID projectId, UUID workspaceId, Pageable pageable);
```

Full JSONB can be loaded on-demand (GET /snapshots/{id}/timeline).

---

## LOW-006: Missing Spring Boot Actuator Endpoints Configuration

**Severity:** LOW
**Category:** Observability
**Location:** application.yml (not reviewed)

**Description:**
Spring Boot Actuator endpoints (/actuator/health, /actuator/metrics) are typically exposed. Ensure they're configured for production safety:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus  # Expose for monitoring
      base-path: /actuator
  metrics:
    export:
      prometheus:
        enabled: true
  endpoint:
    health:
      show-details: when-authorized  # Only show details to authenticated requests
```

---

## LOW-007: Missing Request Logging Middleware

**Severity:** LOW
**Category:** Observability
**Location:** Global configuration

**Description:**
No visible request/response logging middleware for debugging. Adding request logging helps with incident investigation:

```java
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) {
        MDC.put("requestId", UUID.randomUUID().toString());
        MDC.put("path", request.getServletPath());
        MDC.put("method", request.getMethod());

        try {
            filterChain.doFilter(request, response);
            MDC.put("status", String.valueOf(response.getStatus()));
        } finally {
            MDC.clear();
        }
    }
}
```

---

## Summary

| Finding ID | Title | Impact | Fix Effort |
|------------|-------|--------|-----------|
| LOW-001 | Missing Javadoc | Poor IDE experience | Low (add docs) |
| LOW-002 | Inconsistent error format | API confusion | Low (standardize) |
| LOW-003 | Missing size metrics | No bandwidth insights | Low (add metrics) |
| LOW-004 | Missing @Tag annotations | Poor API docs | Low (add annotations) |
| LOW-005 | Potential N+1 queries | Slow list endpoints | Low (use DTOs) |
| LOW-006 | Actuator config missing | Reduced observability | Low (add config) |
| LOW-007 | Missing request logging | Hard to debug | Low (add filter) |

**Recommendation:** Address these in documentation and next sprint. Not urgent.

---

**Total Low Findings:** 7
**Estimated Fix Time:** 4–6 hours combined
