# SpringLens QA Test Plan
**Date:** 2026-03-05
**Status:** Test Planning Phase
**Scope:** Critical Path Testing (Agent Ingestion → Analysis → Recommendations)

---

## 1. Scope & Strategy

### What We're Testing
The critical data flow that delivers core product value:
1. **Ingestion Service** — JVM agent uploads startup telemetry
2. **Analysis Service** — Computes bean dependency graphs and timelines
3. **Recommendation Service** — Generates optimization recommendations
4. **Kafka Event Pipeline** — Async processing between services

### What's Out of Scope (Phase 2)
- Frontend E2E (UI component testing deferred)
- Auth Service (OAuth, API keys — tested separately)
- Notification Service (Slack/GitHub webhooks)
- Performance tests (load testing deferred)
- Full multi-tenant isolation testing

### Test Pyramid
```
    ┌─────────────┐
    │   E2E       │  Full flow: Agent → API → Kafka → Analysis → Rec
    ├─────────────┤
    │  Contract   │  API schema validation, event contracts
    ├─────────────┤
    │Integration  │  Real DB, real Kafka, real Redis
    ├─────────────┤
    │   Unit      │  Business logic, analyzers, engines in isolation
    └─────────────┘
```

---

## 2. Acceptance Criteria → Test Case Mapping

| AC ID | Requirement | Test Type | Test Case ID |
|-------|-------------|-----------|--------------|
| AC-001 | Agent overhead ≤ 5ms | Performance | PERF-001 |
| AC-002 | Timeline accuracy ≥ 99% vs Actuator | Integration | INT-ANALYSIS-001 |
| AC-003 | ≥1 recommendation per bottleneck, ±20% accuracy | Unit | UNIT-REC-001 |
| AC-004 | CI/CD budget gate: return 422 if > threshold | Integration | INT-INGEST-006 |
| AC-010 | Agent config via env vars, upload within 10s | Integration | INT-INGEST-001 |

---

## 3. Test Cases by Service

### A. Ingestion Service (Port 8081)

**Endpoints:**
- `POST /v1/ingest` — Upload startup snapshot
- `GET /v1/snapshots/{id}/status` — Poll analysis status
- `GET /v1/snapshots/{id}/budget-check?budget_ms=X` — CI/CD budget validation

#### Unit Tests (UNIT-INGEST-*)
| ID | Test | Input | Expected |
|-----|------|-------|----------|
| UNIT-INGEST-001 | Valid payload parsing | StartupSnapshotRequest (beans, phases, autoconfigures) | ParsedSnapshot with correct bean count |
| UNIT-INGEST-002 | Payload size validation | Payload > 10MB | 413 Payload Too Large |
| UNIT-INGEST-003 | Required field validation | Missing `project_id` | 400 Bad Request |
| UNIT-INGEST-004 | Git SHA format validation | Invalid SHA (not 40 hex chars) | 400 Bad Request |
| UNIT-INGEST-005 | Deduplication logic | Same (project, env, commit) in 60s window | Return existing snapshot_id |
| UNIT-INGEST-006 | Deduplication expiry | Same snapshot after 60s window | Create new snapshot |
| UNIT-INGEST-007 | Redis idempotency key generation | Project A, env dev, commit X | Key format verified |
| UNIT-INGEST-008 | Kafka event payload creation | Valid snapshot | StartupEvent published with correct schema |

#### Integration Tests (INT-INGEST-*)
| ID | Test | Setup | Validates |
|-----|------|-------|-----------|
| INT-INGEST-001 | Happy path: ingest → Kafka publish | Real Redis, Kafka, Postgres | 202 Accepted, event in topic, snapshot persisted |
| INT-INGEST-002 | Deduplication: 2 identical requests in 60s | Redis cache | First: 202, Second: 200 deduplicated |
| INT-INGEST-003 | Deduplication expiry after 60s | Redis TTL | Third request (after 60s) creates new snapshot |
| INT-INGEST-004 | Payload size limit enforcement | Real Servlet | Payload 10MB+1 → 413 |
| INT-INGEST-005 | API key validation | Missing Authorization header | 401 Unauthorized |
| INT-INGEST-006 | Budget check: within budget | Budget 5000ms, actual 3500ms | 200 OK, within_budget: true |
| INT-INGEST-007 | Budget check: exceeds budget | Budget 2000ms, actual 3500ms | 422 Unprocessable, excess_ms: 1500 |
| INT-INGEST-008 | Budget check: identifies top bottlenecks | 5 beans, top 3 > 200ms | Returns top 3 bottleneck details |
| INT-INGEST-009 | Status polling: queued → processing → complete | Snapshot created, Kafka → Analysis → Redis update | Status transitions correctly |

#### Contract Tests (CONTRACT-INGEST-*)
| ID | Test | Validates |
|-----|------|-----------|
| CONTRACT-INGEST-001 | POST /ingest response schema | Response matches OpenAPI 202/200/400/401/413/422 |
| CONTRACT-INGEST-002 | StartupSnapshotRequest schema | All required fields present, types correct |
| CONTRACT-INGEST-003 | Status response fields | snapshot_id (UUID), status (enum), timestamps |
| CONTRACT-INGEST-004 | Error response contract | code, message, trace_id, details present |

---

### B. Analysis Service (Port 8082)

**Endpoints:**
- `GET /v1/projects/{id}/snapshots` — List snapshots (paginated)
- `GET /v1/projects/{id}/snapshots/{id}/timeline` — Get startup timeline
- `GET /v1/projects/{id}/snapshots/{id}/bean-graph` — Get bean dependency graph
- `GET /v1/projects/{id}/compare?baseline={id}&target={id}` — Compare snapshots

**Key Logic:** BeanGraphAnalyzer, PhaseAnalyzer

#### Unit Tests (UNIT-ANALYSIS-*)
| ID | Test | Input | Expected |
|-----|------|-------|----------|
| UNIT-ANALYSIS-001 | Bean DAG construction | Beans with dependencies | Correct graph topology |
| UNIT-ANALYSIS-002 | Bottleneck detection (>200ms) | 10 beans, 3 > 200ms | 3 bottlenecks identified |
| UNIT-ANALYSIS-003 | Phase timeline assembly | 5 phase events in sequence | Timeline with correct phase durations |
| UNIT-ANALYSIS-004 | Percentage breakdown | phase1: 2000ms, total: 5000ms | phase1: 40% |
| UNIT-ANALYSIS-005 | Autoconfiguration cost analysis | 5 autoconfigs, 2 > 100ms | Top costly ones identified |
| UNIT-ANALYSIS-006 | Missing dependency handling | Bean B depends on missing A | Error logged, bean marked as orphan |
| UNIT-ANALYSIS-007 | Circular dependency detection | A → B → C → A | Detected and logged |
| UNIT-ANALYSIS-008 | Empty bean list handling | StartupEvent with 0 beans | Graceful handling, empty timeline |

#### Integration Tests (INT-ANALYSIS-*)
| ID | Test | Setup | Validates |
|-----|------|-------|-----------|
| INT-ANALYSIS-001 | Kafka consumer: consume StartupEvent → persist StartupTimeline | Real Kafka topic, Postgres | Timeline entity created with correct analysis |
| INT-ANALYSIS-002 | Timeline accuracy vs Spring Actuator | Real Spring Boot startup data | ≥99% bean count match, ±5% timing variance |
| INT-ANALYSIS-003 | Bean graph correctness | Real dependency data | DAG correctly represents relationships |
| INT-ANALYSIS-004 | Phase breakdown sum | All phases should sum to total startup | phase1 + phase2 + … = total |
| INT-ANALYSIS-005 | Pagination: list snapshots | 50 snapshots, page_size=10 | Returns 5 pages correctly |
| INT-ANALYSIS-006 | Filtering: snapshots by environment | Create snapshots in dev/staging/prod | Filter returns only matching env |
| INT-ANALYSIS-007 | Snapshot comparison | Baseline snapshot A, Target snapshot B | Diff shows added/removed beans, duration deltas |
| INT-ANALYSIS-008 | Ordering: newest snapshots first | Create 3 snapshots with known timestamps | Latest timestamp first in list |

#### Contract Tests (CONTRACT-ANALYSIS-*)
| ID | Test | Validates |
|-----|------|-----------|
| CONTRACT-ANALYSIS-001 | Timeline response schema | BeanEvent[], PhaseEvent[], bottlenecks[] |
| CONTRACT-ANALYSIS-002 | Bean graph schema | nodes[], edges[] with bean_name, duration_ms |
| CONTRACT-ANALYSIS-003 | Comparison schema | baseline_timeline, target_timeline, deltas |
| CONTRACT-ANALYSIS-004 | Error handling schema | Consistent error response format |

---

### C. Recommendation Service (Port 8083)

**Endpoints:**
- `GET /v1/projects/{id}/recommendations` — List recommendations
- `PATCH /v1/projects/{id}/recommendations/{id}/status` — Mark as applied/wont_fix
- `GET /v1/projects/{id}/ci-budgets` — List CI budgets
- `PUT /v1/projects/{id}/ci-budgets` — Set CI budget

**Key Logic:** RecommendationEngine (5 rules: lazy, AOT, CDS, GraalVM, dependency removal)

#### Unit Tests (UNIT-REC-*)
| ID | Test | Input | Expected |
|-----|------|-------|----------|
| UNIT-REC-001 | Lazy initialization rule | Bottleneck beans (>200ms) | Lazy rec generated, savings ±20% accurate |
| UNIT-REC-002 | AOT compilation rule | Spring Boot 3.x, no reflection | AOT recommendation eligible |
| UNIT-REC-003 | GraalVM native rule | No dynamic proxies/reflection | Native-image feasibility assessed |
| UNIT-REC-004 | CDS rule | Beans > 100ms | CDS savings estimate computed |
| UNIT-REC-005 | Dependency removal rule | Unused dependency in classpath | Removal recommendation scored |
| UNIT-REC-006 | Effort/impact ranking | 10 recommendations with varying effort | Ranked by impact/effort ratio |
| UNIT-REC-007 | Effort level assignment | Simple rule | Low effort; Complex rule | High effort |
| UNIT-REC-008 | Empty bottleneck handling | 0 bottlenecks | No recommendations generated |
| UNIT-REC-009 | Quota enforcement (Free tier) | Max 3 recommendations, 4 bottlenecks | Only top 3 by impact returned |

#### Integration Tests (INT-REC-*)
| ID | Test | Setup | Validates |
|-----|------|-------|-----------|
| INT-REC-001 | Kafka consumer: AnalysisComplete → Recommendations | Real Kafka topic, Postgres, Analysis data | Recommendations persisted, count matches |
| INT-REC-002 | Recommendation generation on new analysis | Snapshot analyzed, 3 bottlenecks detected | 3+ recommendations created |
| INT-REC-003 | Status update: mark applied | Recommendation created, PATCH /status | Status changes to APPLIED, timestamp updated |
| INT-REC-004 | Status update: mark wont_fix | Recommendation created, PATCH /status | Status changes to WONT_FIX |
| INT-REC-005 | Staleness warning | Recommendation > 24h old (Pro) | Flagged with staleness warning in response |
| INT-REC-006 | CI budget CRUD | No budget set, PUT /ci-budgets | Budget created; GET returns it |
| INT-REC-007 | CI budget enforcement (read-only in tests) | Budget set, verify API enforces it | Production: 422 if startup > budget |
| INT-REC-008 | Filtering recommendations by status | Mix of PENDING/APPLIED/WONT_FIX | Filter returns only requested status |

#### Contract Tests (CONTRACT-REC-*)
| ID | Test | Validates |
|-----|------|-----------|
| CONTRACT-REC-001 | Recommendation response schema | id, type, title, description, effort, impact_ms, status |
| CONTRACT-REC-002 | CI budget response schema | project_id, environment, budget_ms, created_at |
| CONTRACT-REC-003 | Effort enum values | [LOW, MEDIUM, HIGH] only |
| CONTRACT-REC-004 | Status enum values | [PENDING, APPLIED, WONT_FIX] only |

---

### D. Kafka Event Pipeline

#### Contract Tests (CONTRACT-KAFKA-*)
| ID | Event Topic | Validates |
|-----|-----------|-----------|
| CONTRACT-KAFKA-001 | startup.events | StartupEvent schema: snapshot_id, workspace_id, project_id, beans |
| CONTRACT-KAFKA-002 | analysis.complete | AnalysisCompleteEvent: snapshot_id, bottleneck_ids[], timeline_id |
| CONTRACT-KAFKA-003 | recommendations.ready | RecommendationsReadyEvent: snapshot_id, recommendation_count |

#### Integration Tests (INT-KAFKA-*)
| ID | Test | Setup | Validates |
|-----|------|-------|-----------|
| INT-KAFKA-001 | Full flow: ingest → analysis → recommendations | Real Kafka, all 3 services | All events published/consumed in order |
| INT-KAFKA-002 | Kafka message ordering | Publish 5 events sequentially | Consumed in FIFO order |
| INT-KAFKA-003 | Consumer group: multi-instance | 2 Analysis service instances, 1 topic partition | Events distributed fairly |

---

### E. End-to-End Flow

#### E2E Tests (E2E-*)
| ID | Test | Scenario | Validates |
|-----|------|----------|-----------|
| E2E-001 | Complete ingestion → analysis → recommendations flow | Agent uploads snapshot with 5 beans, 2 bottlenecks | All 3 services complete within 5s; final recommendations exist |
| E2E-002 | Budget gate in E2E | Ingest snapshot, check budget, verify 422 if exceeded | Budget enforcement works end-to-end |
| E2E-003 | Deduplication + separate flow | 2 identical ingests within 60s, both result in same analysis | Deduplication prevents double-processing |
| E2E-004 | Status polling flow | Poll /status every 100ms until complete | Status transitions: queued → processing → complete |

---

## 4. Test Coverage Goals

### Per-Service Coverage Targets
| Service | Line Coverage | Branch Coverage | Notes |
|---------|---------------|-----------------|-------|
| ingestion-service | 85% | 80% | Critical path for agent integration |
| analysis-service | 90% | 85% | Complex logic (DAG, analysis) |
| recommendation-service | 85% | 80% | Scoring engine accuracy critical |
| **Global** | **85%** | **80%** | — |

### Bottleneck Coverage
✅ Unit tests for each rule in RecommendationEngine (5 rules × 2 paths = 10+ tests)
✅ Integration tests for bean graph analysis with real DB queries
✅ Contract tests for API schema and event schema validation
✅ E2E tests for full flow

---

## 5. Test Infrastructure

### Test Containers (docker-compose.test.yml)
```yaml
services:
  postgres:
    image: postgres:16-alpine
    volumes:
      - schemas/migrations/:/docker-entrypoint-initdb.d/
  redis:
    image: redis:7-alpine
  kafka:
    image: confluentinc/cp-kafka:7.6.0
  zookeeper:
    image: confluentinc/cp-zookeeper:7.6.0
```

### Test Dependencies (gradle)
```gradle
testImplementation 'org.springframework.boot:spring-boot-starter-test'
testImplementation 'org.testcontainers:testcontainers:1.19.7'
testImplementation 'org.testcontainers:postgresql:1.19.7'
testImplementation 'org.testcontainers:kafka:1.19.7'
testImplementation 'io.projectreactor:reactor-test:2023.12.1'
testImplementation 'org.mockito:mockito-core:5.7.1'
testImplementation 'org.mockito:mockito-junit-jupiter:5.7.1'
```

### Test Fixtures
- **Factories:** `BeanEventFactory`, `StartupSnapshotFactory`, `RecommendationFactory`
- **Seed Data:** SQL scripts to pre-populate test DB with workspaces, projects
- **Mocks:** Redis mock, Kafka broker mock for unit tests

---

## 6. Execution Order

1. **Phase 2:** Unit Tests (UNIT-*) — ~30 minutes, no containers
2. **Phase 3:** Integration Tests (INT-*) — ~10 minutes with docker-compose
3. **Phase 4:** Contract Tests (CONTRACT-*) — ~5 minutes, no DB needed
4. **Phase 5:** E2E Tests (E2E-*) — ~5 minutes with full stack

**Total:** ~50 minutes for complete test suite

---

## 7. Success Criteria

✅ **Coverage:** ≥85% lines, ≥80% branches per service
✅ **All acceptance criteria traced to test cases** (10 ACs → 40+ tests)
✅ **Zero flaky tests** (retry logic validates ≥3 runs)
✅ **All E2E flows pass** (ingestion → analysis → recommendations)
✅ **Contract validation** (OpenAPI + Kafka event schema)
✅ **Performance baseline** (agent overhead ≤ 5ms measured)

---

## 8. Risk Register

| Risk | Mitigation | Test ID |
|------|-----------|---------|
| Kafka ordering guarantees | Partition-level ordering tests | INT-KAFKA-002 |
| Bean graph complexity | Unit tests for circular deps, orphans | UNIT-ANALYSIS-006/007 |
| Deduplication edge cases | Redis TTL tests, timing tests | INT-INGEST-003 |
| Timeline accuracy drift | Actuator comparison tests | INT-ANALYSIS-002 |
| Recommendation accuracy ±20% | Unit + integration tests for each rule | UNIT-REC-* + INT-REC-* |

---

**Next Step:** Execute Phase 2 (Unit Tests) for all critical-path services.
