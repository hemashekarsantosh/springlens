# Solution Architect Working Notes — SpringLens

## Key Decisions

1. **Microservices over modular monolith** — ingestion burst + analysis CPU isolation are real needs
2. **TimescaleDB** chosen for time-series (not InfluxDB) — SQL/JPA compatibility, single managed RDS
3. **Self-hosted auth** (Spring Authorization Server) — enterprise SAML, data residency, cost at scale
4. **Row-Level Security** (shared schema) — 500 tenant target; per-tenant DBs are unmanageable
5. **Kafka over RabbitMQ** — log retention enables analysis re-runs on same event data
6. **AWS EKS** over Lambda — irony of JVM cold starts for a startup optimization tool

## Context Bridge for BUILD phase

- API contracts: `api/openapi/` (3 specs: ingestion, analysis, recommendation)
- Data schemas: `schemas/migrations/` (V001 auth, V002 ingestion TimescaleDB, V003 recommendation)
- Architecture docs: `docs/architecture/` (5 ADRs, 4 diagrams, tech-stack.md)
- Service scaffolds: `services/` (5 services with build.gradle, app class, config)
- Multi-tenant: RLS via `app.current_workspace_id` session variable

## Kafka Topics

| Topic | Producer | Consumer | Schema |
|-------|---------|---------|--------|
| startup.events | ingestion-service | analysis-service | StartupEvent (Avro) |
| analysis.complete | analysis-service | recommendation-service | AnalysisCompleteEvent (Avro) |
| recommendations.ready | recommendation-service | notification-service | RecommendationsReadyEvent (Avro) |

## Port Map (local)

| Service | Port |
|---------|------|
| frontend | 3000 |
| ingestion-service | 8081 |
| analysis-service | 8082 |
| recommendation-service | 8083 |
| auth-service | 8084 |
| notification-service | 8085 |
| PostgreSQL | 5432 |
| TimescaleDB | 5433 |
| Redis | 6379 |
| Kafka | 9092 |
| Kafka UI | 8090 |
| Grafana | 3001 |
| Prometheus | 9090 |
