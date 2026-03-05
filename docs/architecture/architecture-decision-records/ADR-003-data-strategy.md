# ADR-003: Data Strategy — Database per Service

**Status:** Accepted
**Date:** 2026-03-05

## Context

Multiple services need persistent storage with different access patterns:
- Startup telemetry is time-series data with retention TTL
- Auth/billing data is relational with ACID requirements
- Recommendations are derived/computed data, frequently rewritten

## Decision

**Database per service** — no shared database. Each service owns its schema.

| Service | Database | Rationale |
|---------|----------|-----------|
| ingestion-service | PostgreSQL + TimescaleDB extension | Time-series hypertables, compression, retention policies, continuous aggregates |
| analysis-service | PostgreSQL + TimescaleDB | Bean graphs stored as JSONB, timeline data in hypertables |
| recommendation-service | PostgreSQL | Relational recommendation records, status tracking |
| auth-service | PostgreSQL | Users, workspaces, API keys, billing subscriptions |
| notification-service | PostgreSQL | Webhook configs, delivery logs |
| All services | Redis | Distributed cache, rate limiting counters, idempotency keys |

**TimescaleDB** is chosen over InfluxDB/ClickHouse because:
- Full SQL compatibility — standard Spring Data JPA works unchanged
- Native PostgreSQL extensions — single RDS-compatible managed service
- Hypertable partitioning handles 1,000+ events/min with automatic chunk management
- Continuous aggregates for org-wide dashboard rollups

**Multi-tenancy:** Row-level security (RLS) via `tenant_id` column on all tables. PostgreSQL RLS policies enforced at DB level — defense in depth beyond application-layer filtering.

## Consequences

- No cross-service JOINs — services call each other via API for cross-domain data
- Schema migrations per service — each service manages its own Flyway migrations
- TimescaleDB retention policies automate data expiry per plan tier

## Alternatives Considered

- **Shared PostgreSQL** — rejected: violates service autonomy, creates deployment coupling
- **ClickHouse** — rejected: no SQL JPA support, operational complexity, overkill at launch scale
- **MongoDB** — rejected: ACID guarantees needed for billing; JSONB in PostgreSQL handles flexible bean data
