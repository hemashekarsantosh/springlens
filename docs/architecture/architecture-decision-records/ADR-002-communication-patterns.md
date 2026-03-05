# ADR-002: Service Communication Patterns

**Status:** Accepted
**Date:** 2026-03-05

## Context

SpringLens has two distinct communication needs:
1. **Telemetry ingestion** — fire-and-forget from JVM agent, high throughput, tolerates async
2. **Dashboard queries** — user-facing reads, require low latency synchronous responses

## Decision

**Async (Apache Kafka):** Agent → ingestion-service → `startup.events` topic → analysis-service → `analysis.complete` topic → recommendation-service → `recommendations.ready` topic → notification-service

**Sync (REST/HTTPS):** API Gateway → auth-service, analysis-service, recommendation-service for all dashboard read endpoints and CI/CD gate checks.

**Event Schema:** Apache Avro with Schema Registry for type safety and backward compatibility.

**Inter-service calls (sync):** REST with OpenFeign clients, circuit breaker (Resilience4j), 3s timeout, 3 retries with exponential backoff.

## Consequences

- Ingestion is fully decoupled from analysis — burst traffic is absorbed by Kafka partition buffer
- Dashboard reads are synchronous REST with SLA enforcement
- Kafka adds operational overhead — managed via AWS MSK

## Alternatives Considered

- **gRPC inter-service** — rejected for v1: REST is simpler to debug, OpenAPI tooling is mature, team velocity trumps marginal performance gain at this scale
- **RabbitMQ** — rejected: Kafka's log retention enables event replay for analysis re-runs; RabbitMQ deletes on ack
