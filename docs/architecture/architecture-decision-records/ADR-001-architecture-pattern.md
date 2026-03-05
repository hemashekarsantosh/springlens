# ADR-001: Microservices Architecture Pattern

**Status:** Accepted
**Date:** 2026-03-05

## Context

SpringLens ingests JVM telemetry, runs compute-intensive analysis, serves a web dashboard, and handles billing — four distinct domains with different scaling characteristics. The ingestion endpoint must handle burst traffic (CI/CD runs triggering simultaneously), while the analysis engine is CPU-bound. A monolith would require scaling all components together.

## Decision

Adopt **microservices architecture** with 5 domain services:

1. **ingestion-service** — high-throughput telemetry receiver (Kafka producer)
2. **analysis-service** — CPU-bound startup timeline analysis (Kafka consumer)
3. **recommendation-service** — rule-based + heuristic recommendation engine
4. **auth-service** — JWT issuance, OAuth2, API key management, billing/plan enforcement
5. **notification-service** — webhook fan-out (Slack, PagerDuty, GitHub PR comments)

Plus: **API Gateway** (AWS ALB + Kong) for routing, rate limiting, and auth header injection.

Frontend: **Next.js 14** SPA/SSR served via CloudFront CDN.

## Consequences

- Independent scaling per service (ingestion scales on traffic, analysis scales on CPU)
- Higher operational complexity vs monolith — mitigated by EKS + Helm
- Services communicate async (Kafka) where latency tolerance allows; sync (REST) for user-facing reads
- Each service owns its database schema (no shared DB)

## Alternatives Considered

- **Modular monolith** — rejected: ingestion burst traffic and analysis CPU isolation are genuine needs, not premature optimization given 1,000 events/min target
- **Serverless (Lambda)** — rejected: JVM cold starts on Lambda are ironic given the product's purpose; EKS gives consistent latency
