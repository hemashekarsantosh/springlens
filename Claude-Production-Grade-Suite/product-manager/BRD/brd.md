# Business Requirements Document
# SpringLens — Spring Boot Startup Optimization SaaS

**Status:** Draft
**Date:** 2026-03-05
**Last Updated:** 2026-03-05
**Version:** 1.0

---

## 1. Executive Summary

SpringLens is a multi-tenant SaaS platform that gives Java/Spring Boot teams continuous visibility into application startup performance. It ingests JVM startup telemetry via a lightweight Java agent, analyzes bean initialization timelines and dependency graphs, and delivers actionable optimization recommendations (AOT compilation, lazy loading, GraalVM native image, Class Data Sharing). Teams integrate once via CI/CD and get ongoing startup intelligence without changing application code.

### Market Opportunity
- 60%+ of enterprise Java shops run Spring Boot
- Average Spring Boot microservice startup: 3–15 seconds on JVM; 10–50ms with GraalVM native
- No existing SaaS product owns the startup optimization niche — only generic profilers (JProfiler, YourKit) and local tools (spring-startup-analyzer OSS)
- Cold start latency is a top pain point for Kubernetes rolling deployments and serverless Java workloads

---

## 2. Problem Statement

**Who:** Java developers, DevOps/platform engineers, and SRE teams at companies running 5–500+ Spring Boot microservices.

**Pain today:**
- Startup times are opaque — no easy way to see which beans, autoconfiguration, or dependencies cause delays
- Optimization requires manual profiling with heavyweight tools (JProfiler, VisualVM) on developer machines
- Recommendations are tribal knowledge — teams rediscover the same fixes (lazy init, classpath scanning reduction, AOT) repeatedly
- No historical tracking — teams cannot detect startup regressions across deployments or Spring Boot upgrades
- CI/CD integration is absent — startup performance is never gated or tracked in pipelines

**What we're NOT solving:** General runtime performance (CPU, memory, garbage collection) — that is Digma/Lightrun/DataDog territory.

---

## 3. Proposed Solution

A three-layer platform:

1. **Ingestion Layer** — Lightweight JVM agent (< 5ms overhead on startup measurement) that captures startup telemetry and uploads to SpringLens API
2. **Analysis Engine** — Server-side service that builds bean dependency graphs, startup timelines, autoconfiguration reports, and identifies bottlenecks
3. **Intelligence Layer** — Recommendation engine that maps bottlenecks to specific optimization actions with effort/impact scoring

Delivered via: Web dashboard, REST API, CLI, IDE plugins (Phase 2), CI/CD integrations.

---

## 4. Target Users & Personas

| Persona | Role | Primary Goal |
|---------|------|--------------|
| **Alex the Dev** | Java developer at a startup | Understand why my service takes 8s to start locally and in CI |
| **Jordan the DevOps** | Platform engineer at mid-size company | Track startup regressions across 40 microservices in Kubernetes |
| **Sam the Architect** | Staff engineer at enterprise | Drive AOT/GraalVM adoption, show measurable startup improvements to leadership |
| **Taylor the SRE** | SRE at scale-up | Enforce startup SLOs in CI/CD, alert when a deployment causes startup regression |

---

## 5. User Stories

### Epic 1: JVM Agent Ingestion

- **US-001** As a Java developer, I want to add SpringLens agent to my Spring Boot app with a single dependency so that startup data is collected without writing custom code.
- **US-002** As a developer, I want the agent to have < 5ms overhead on actual application startup so that it does not affect production performance.
- **US-003** As a DevOps engineer, I want to configure the agent via environment variables (`SPRINGLENS_API_KEY`, `SPRINGLENS_PROJECT`, `SPRINGLENS_ENV`) so that no code changes are needed per environment.
- **US-004** As a developer, I want agent data sent asynchronously after startup completes so that it does not block application readiness.
- **US-005** As a security officer, I want agent data transmitted over TLS with API key authentication so that telemetry is secure in transit.

### Epic 2: Startup Timeline Analysis

- **US-006** As a developer, I want to see a Gantt-style startup timeline showing each bean's initialization duration so that I can identify which beans are slow.
- **US-007** As a developer, I want to filter the timeline by bean name, package, autoconfiguration class, or duration threshold so that I can focus on bottlenecks.
- **US-008** As a developer, I want to see the Spring bean dependency graph as an interactive tree/DAG so that I can understand which beans block others.
- **US-009** As a developer, I want to see which Spring Boot autoconfiguration classes are loaded and their individual costs so that I can exclude unnecessary ones.
- **US-010** As a DevOps engineer, I want to compare startup timelines between two deployments (e.g., v1.2 vs v1.3) so that I can detect regressions.
- **US-011** As a developer, I want to see startup time broken down by phase (context refresh, bean post-processors, application listeners, etc.) so that I know which Spring lifecycle phase is the bottleneck.

### Epic 3: Optimization Recommendations

- **US-012** As a developer, I want to receive ranked recommendations (by estimated time savings) such as "Enable lazy initialization for X beans (saves ~1.2s)" so that I know what to fix first.
- **US-013** As a developer, I want each recommendation to include copy-paste code/config snippets so that I can apply fixes immediately.
- **US-014** As a developer, I want recommendations categorized by type: Lazy Loading, AOT Compilation, GraalVM Native, Classpath Optimization, Dependency Removal so that I can choose what fits my constraints.
- **US-015** As a developer, I want each recommendation to show effort level (Low/Medium/High) and estimated startup time savings so that I can prioritize effectively.
- **US-016** As a developer, I want to mark a recommendation as "Applied" or "Won't Fix" so that the dashboard reflects current state.
- **US-017** As a developer, I want AOT compilation recommendations to include compatibility warnings (reflection, dynamic proxies) so that I don't break the app.
- **US-018** As a developer, I want GraalVM native image feasibility assessment per project so that I can evaluate if native compilation is viable.

### Epic 4: CI/CD Integration

- **US-019** As a DevOps engineer, I want a GitHub Actions step that posts startup metrics as a PR comment so that reviewers see startup impact of each change.
- **US-020** As a DevOps engineer, I want to set a startup time budget (e.g., "fail CI if startup > 10s") so that regressions are caught before merge.
- **US-021** As a DevOps engineer, I want Jenkins and GitLab CI plugin support so that the tool works across CI platforms.
- **US-022** As a DevOps engineer, I want a CLI tool (`springlens report`) that can be run in any pipeline to upload analysis results so that integration is pipeline-agnostic.
- **US-023** As a DevOps engineer, I want webhook notifications (Slack, PagerDuty) when a startup regression exceeds a defined threshold so that teams are alerted proactively.

### Epic 5: Multi-Tenant Team Workspaces

- **US-024** As an organization admin, I want to create a workspace with multiple projects (one per microservice or repo) so that all services are managed in one place.
- **US-025** As an admin, I want to invite team members by email with role-based access (Admin, Developer, Viewer) so that I control who can configure vs. view.
- **US-026** As a developer, I want each project to show environment-specific dashboards (dev, staging, prod) so that I can compare startup across environments.
- **US-027** As an admin, I want to see organization-wide startup health: aggregate metrics across all projects so that I have executive visibility.
- **US-028** As a developer, I want startup history retained for 90 days (Free), 1 year (Pro), unlimited (Enterprise) so that long-term trend analysis is possible.

### Epic 6: Authentication & Account Management

- **US-029** As a user, I want to sign up with GitHub OAuth or email/password so that onboarding is frictionless.
- **US-030** As an admin, I want SSO/SAML support for enterprise workspaces so that corporate identity providers are supported.
- **US-031** As a user, I want to manage API keys (create, rotate, revoke) for agent and CI/CD authentication so that I can maintain security hygiene.

### Epic 7: Billing & Plans

- **US-032** As a new user, I want a Free tier (1 project, 1 environment, 90-day history, 1 team member) so that I can evaluate the tool without payment.
- **US-033** As a growing team, I want a Pro tier ($49/month: 10 projects, 3 environments, 1-year history, 10 members, CI/CD integration, Slack notifications) so that I can use it for a small team.
- **US-034** As an enterprise, I want an Enterprise tier (unlimited projects/members, SSO/SAML, SLA, dedicated support, custom retention, on-premise option) so that it meets enterprise requirements.
- **US-035** As a billing admin, I want Stripe-powered subscription management (upgrade, downgrade, cancel, invoice download) so that billing is self-serve.

---

## 6. Acceptance Criteria

### AC-001: Agent Overhead
- Given a Spring Boot app with SpringLens agent attached
- When the application starts
- Then startup time overhead introduced by the agent is ≤ 5ms as measured by comparing 10 runs with and without agent (p95)

### AC-002: Timeline Accuracy
- Given startup telemetry is received from the agent
- When the analysis engine processes it
- Then the timeline displays each bean with name, class, duration (ms), and parent context with ≥ 99% accuracy vs. Spring Boot Actuator `/startup` endpoint data

### AC-003: Recommendation Relevance
- Given a startup timeline with identified bottlenecks
- When the recommendation engine runs
- Then at least 1 ranked recommendation is returned per identified bottleneck costing > 200ms, with estimated savings within ±20% of actual savings when applied

### AC-004: CI/CD Budget Gate
- Given a project with startup budget set to X seconds
- When a CI/CD run uploads startup results showing startup time > X
- Then the API returns HTTP 422 with structured failure reason, and GitHub Actions step exits with code 1

### AC-005: Multi-Tenant Isolation
- Given two organizations (Org A, Org B) each with projects
- When a user authenticated to Org A queries any API endpoint
- Then they receive 0 data belonging to Org B (verified by penetration test of all data-returning endpoints)

### AC-006: Timeline Comparison
- Given two startup snapshots for the same project/environment
- When the user selects "Compare" in the dashboard
- Then a diff view shows beans present in both, beans added/removed, and duration delta (ms and %) for each common bean

### AC-007: History Retention
- Given a Free tier workspace
- When startup data is older than 90 days
- Then it is soft-deleted and inaccessible via API (but retained for 7-day grace period for recovery)

### AC-008: Authentication
- Given a valid GitHub OAuth callback
- When a new user completes OAuth flow
- Then an account is created (or linked if email matches), JWT issued, and user lands on onboarding within 3 seconds

### AC-009: Stripe Billing
- Given a workspace on Free tier
- When admin upgrades to Pro via Stripe Checkout
- Then Pro features are unlocked within 60 seconds of payment confirmation webhook

### AC-010: Agent Configuration
- Given `SPRINGLENS_API_KEY` and `SPRINGLENS_PROJECT` environment variables are set
- When the Spring Boot application starts with the agent on classpath
- Then telemetry is uploaded to the correct project and environment within 10 seconds of startup completion

---

## 7. Business Rules

- **BR-001:** Each project belongs to exactly one workspace. Projects cannot be shared across workspaces.
- **BR-002:** API keys are scoped to a project. A key for Project A cannot upload data to Project B.
- **BR-003:** Free tier workspaces are limited to 1 project, 1 environment, 1 team member (owner). Enforcement is hard — excess resources are rejected at API level, not soft-limited.
- **BR-004:** Startup data is associated with a `(project_id, environment, git_commit_sha, timestamp)` tuple. Duplicate uploads with the same commit SHA in the same environment within 60 seconds are deduplicated (idempotent ingestion).
- **BR-005:** Recommendations are recomputed on every new startup snapshot upload. Stale recommendations (based on data > 24h old for Pro/Enterprise) are flagged with a staleness warning.
- **BR-006:** CI/CD budget thresholds are set per `(project_id, environment)` pair. Production environment thresholds require Admin role to modify.
- **BR-007:** Startup history deletion (at retention limit) is soft-delete first, hard-delete after 7-day grace period. Hard delete is irreversible.
- **BR-008:** SSO/SAML is an Enterprise-only feature. Enabling SSO for a workspace disables email/password login for all members.
- **BR-009:** Agent telemetry payload size limit is 10MB per startup event. Payloads exceeding this are rejected with HTTP 413.
- **BR-010:** All monetary amounts are stored and processed in USD cents (integer). Display conversion to user's locale is frontend-only.

---

## 8. Out of Scope (v1.0)

- Runtime performance profiling (CPU hot paths, GC analysis, memory leaks) — this is Digma/DataDog territory
- IDE plugins (IntelliJ, VS Code) — Phase 2
- Quarkus or Micronaut support — Phase 2
- On-premise deployment for Enterprise — Phase 2 (architecture must support it)
- AI-generated code fixes (auto-apply recommendations) — Phase 2
- Mobile app — never
- Spring Boot 2.x support — only Spring Boot 3.x (AOT requires 3.x)

---

## 9. Non-Functional Requirements

| Requirement | Target |
|-------------|--------|
| API p99 latency (analysis endpoints) | ≤ 2 seconds |
| Agent upload endpoint latency | ≤ 500ms p99 |
| Dashboard initial load | ≤ 3 seconds (LCP) |
| System availability | 99.9% monthly uptime |
| Data encryption at rest | AES-256 |
| Data encryption in transit | TLS 1.3 |
| GDPR compliance | Right to erasure within 30 days |
| Startup snapshot ingestion throughput | ≥ 1,000 events/minute at launch |

---

## 10. Research Notes

### Competitive Landscape

| Tool | Type | Spring Boot Startup Focus | Startup-Specific? |
|------|------|--------------------------|-------------------|
| JProfiler | Desktop, paid | General profiling | No |
| YourKit | Desktop, paid | General profiling | No |
| VisualVM | Desktop, free | General profiling | No |
| Digma.ai | SaaS | Runtime code quality | No |
| Lightrun | SaaS | Runtime debugging/observability | No |
| spring-startup-analyzer (OSS) | Local CLI | Yes — startup only | Yes, but no SaaS |
| SpringLens (us) | SaaS | Yes — startup only | **Yes — first SaaS** |

### Key Technical Facts
- Spring Boot Actuator `/startup` endpoint provides bean-level startup data natively (since Spring Boot 2.4)
- GraalVM native reduces startup from ~3–15s to ~10–50ms with 3x memory savings
- AOT processing (Spring Boot 3.x) reduces startup 10–20% while maintaining JVM deployment
- Project Leyden (Java 25+) AOT cache provides 2–5x improvement without AOT code generation
- Class Data Sharing (CDS) reduces startup by ~33% with zero code changes (per ITNEXT 2026 article)
- Lazy initialization (`spring.main.lazy-initialization=true`) reduces startup by 30–50% but requires careful testing

### Pricing Benchmark
- Digma: $450/month starting
- Lightrun Pro: $1,440/year
- Our positioning: Developer-friendly entry ($49/month Pro), enterprise competitive (custom)

---

## 11. Open Questions

- Should the agent support Spring Boot 3.1+ only (AOT stable) or back-port to 3.0?
- Should Free tier require credit card? (Recommended: No, for frictionless adoption)
- What is the on-premise architecture target? (Docker Compose for Phase 2 self-hosted)
- Should we store raw JVM heap dumps or only aggregated startup metrics? (Recommendation: aggregated only for privacy/size)

---

## 12. Success Metrics (KPIs)

| Metric | 6-Month Target |
|--------|---------------|
| Monthly Active Projects | 500 |
| Free → Pro Conversion Rate | ≥ 8% |
| Agent Integration Time (median) | ≤ 15 minutes |
| Recommendation Application Rate | ≥ 40% of delivered recommendations |
| Net Promoter Score | ≥ 45 |
| Startup Time Reduction (median for users who apply recommendations) | ≥ 30% |
