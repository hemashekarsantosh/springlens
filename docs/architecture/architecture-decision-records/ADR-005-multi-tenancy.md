# ADR-005: Multi-Tenancy Strategy — Row-Level Security

**Status:** Accepted
**Date:** 2026-03-05

## Context

SpringLens serves multiple organizations (tenants) from a shared infrastructure. Tenant isolation must be guaranteed at multiple layers. Three strategies exist: separate databases per tenant, separate schemas per tenant, or shared schema with row-level filtering.

## Decision

**Shared schema with PostgreSQL Row-Level Security (RLS).**

Every tenant-scoped table includes a `workspace_id UUID NOT NULL` column. PostgreSQL RLS policies enforce isolation at the database level:

```sql
ALTER TABLE startup_snapshots ENABLE ROW LEVEL SECURITY;
CREATE POLICY tenant_isolation ON startup_snapshots
  USING (workspace_id = current_setting('app.current_workspace_id')::uuid);
```

Application layer: Every authenticated request sets `app.current_workspace_id` via a Spring `HandlerInterceptor` before any DB query. The DB connection pool uses a dedicated role with no superuser privileges — cannot bypass RLS.

**Defense layers:**
1. API Gateway: JWT claims validation (workspace_id in token)
2. Service layer: `@PreAuthorize` on all endpoints checking workspace membership
3. Database layer: PostgreSQL RLS — enforced even if application bug bypasses layers 1+2

## Consequences

- Simpler operations vs schema-per-tenant (no per-tenant migrations, connection pool management)
- RLS has ~5% query overhead — acceptable at launch scale
- Penetration tests must verify RLS isolation (AC-005)
- Workspace ID must be propagated through all Kafka messages for consumer-side enforcement

## Alternatives Considered

- **Database per tenant** — rejected: 500 tenants = 500 databases, unmanageable at launch scale
- **Schema per tenant** — rejected: Flyway schema-per-tenant migrations are operationally complex; connection pool per schema doesn't scale
