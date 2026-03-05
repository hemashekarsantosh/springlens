# SpringLens — Tech Stack

**Last Updated:** 2026-03-05

## Backend Services

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Language | Java | 21 (LTS) | Virtual threads (Project Loom), record types, pattern matching; LTS until 2031 |
| Framework | Spring Boot | 3.3.x | AOT support, native compilation readiness, Spring Security 6, Spring Authorization Server |
| Build | Gradle | 8.x | Multi-project build, faster than Maven for monorepo |
| API Framework | Spring Web MVC | (Spring Boot 3.x) | Standard REST; WebFlux considered but MVC sufficient at launch scale |
| Data Access | Spring Data JPA + Hibernate | 6.x | Full ORM for relational; JSONB support for bean graphs |
| DB Migrations | Flyway | 10.x | SQL-first, per-service migration directories |
| Async | Spring Kafka | 3.x | Kafka consumer/producer abstractions |
| Auth | Spring Security 6 + Spring Authorization Server | 1.x | JWT, OAuth2, SAML2, API key filter |
| Resilience | Resilience4j | 2.x | Circuit breaker, retry, rate limiter |
| Observability | Micrometer + OpenTelemetry | Latest | Metrics to Prometheus, traces to AWS X-Ray |
| Testing | JUnit 5 + Mockito + Testcontainers | Latest | Testcontainers for PostgreSQL/Kafka/Redis in integration tests |

## JVM Agent

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| Agent type | Java Instrumentation API (`-javaagent`) | Zero code change integration |
| Spring integration | `ApplicationStartupMetrics` + `SmartApplicationListener` | Hooks into Spring Boot startup lifecycle natively |
| HTTP client | Java 11+ `HttpClient` (async) | No extra dependency; async upload after startup |
| Build | Maven (standalone jar) | Fat jar with shade plugin; single dependency in Maven Central |

## Frontend

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Framework | Next.js | 14 (App Router) | SSR for dashboard SEO, RSC for performance, file-based routing |
| Language | TypeScript | 5.x | Type safety across API contract boundaries |
| UI Library | shadcn/ui + Radix UI | Latest | Accessible, headless, composable; no vendor lock-in |
| Styling | Tailwind CSS | 3.x | Utility-first, fast iteration |
| Data viz | D3.js + Recharts | Latest | Bean DAG (D3 force graph), Gantt timeline (D3), trend charts (Recharts) |
| State | TanStack Query (React Query) | v5 | Server state, caching, background refresh |
| Auth | next-auth | v5 | GitHub OAuth, session management |
| Testing | Playwright (E2E) + Vitest (unit) | Latest | Full browser testing for critical flows |

## Data

| Component | Technology | Notes |
|-----------|-----------|-------|
| Primary DB | PostgreSQL | 16, AWS RDS Multi-AZ |
| Time-series extension | TimescaleDB | Hypertables for startup_snapshots, bean_events |
| Cache / Session | Redis | AWS ElastiCache, Cluster mode, 3-node |
| Message broker | Apache Kafka | AWS MSK, 3 brokers, 3 partitions/topic |
| Schema registry | AWS Glue Schema Registry | Avro schemas for Kafka events |

## Infrastructure

| Component | Technology | Notes |
|-----------|-----------|-------|
| Container orchestration | AWS EKS | Kubernetes 1.29, managed node groups |
| Container registry | AWS ECR | Per-service image repos |
| Load balancer | AWS ALB | HTTPS termination, WAF integration |
| API gateway | Kong (on EKS) | Rate limiting, auth plugin, request ID |
| CDN | AWS CloudFront | Next.js static assets + API cache |
| Object storage | AWS S3 | Agent jar distribution, export files |
| Secrets | AWS Secrets Manager | DB passwords, Stripe keys, OAuth secrets |
| IaC | Terraform | 1.8+, modular, per-environment workspaces |
| CI/CD | GitHub Actions | Build, test, security scan, deploy |
| Monitoring | Prometheus + Grafana | On-cluster, dashboards per service |
| Alerting | PagerDuty + Alertmanager | SLO breach alerts |
| Log aggregation | AWS CloudWatch Logs | Structured JSON logs, 90-day retention |
| Distributed tracing | AWS X-Ray | OpenTelemetry instrumentation |

## Security

| Concern | Solution |
|---------|---------|
| Secrets at rest | AWS Secrets Manager + KMS AES-256 |
| TLS | ACM certificates, TLS 1.3 minimum |
| Container security | Non-root user, read-only filesystem, Distroless base |
| SAST | SpotBugs + FindSecBugs in CI |
| Dependency scanning | Dependabot + OWASP Dependency-Check |
| WAF | AWS WAF on ALB (OWASP managed rule group) |
