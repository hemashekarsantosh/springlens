# ADR-004: Authentication Architecture

**Status:** Accepted
**Date:** 2026-03-05

## Context

SpringLens needs three auth surfaces:
1. **Human users** — dashboard login (GitHub OAuth, email/password, SAML/SSO for Enterprise)
2. **JVM agents** — API key auth for telemetry upload (high throughput, stateless)
3. **CI/CD tooling** — API key auth for pipeline integrations

Requirements: SAML for enterprise, JWT for session, API keys for machine-to-machine, RBAC (Admin/Developer/Viewer).

## Decision

**Self-hosted auth-service** (Spring Security + Spring Authorization Server) rather than managed Auth0/Cognito.

Rationale: Enterprise customers require data residency guarantees and SAML configuration control. Auth0 at Enterprise scale ($23k+/year) is cost-prohibitive. Spring Authorization Server is production-mature and keeps all identity data within our infrastructure.

**Token strategy:**
- **User sessions:** Short-lived JWT access tokens (15min) + long-lived refresh tokens (30 days) stored in HttpOnly cookies
- **API keys:** `sl_proj_<base64url(random 32 bytes)>` — hashed (bcrypt) before storage, shown once on creation
- **Inter-service:** Service account JWT signed by auth-service, validated by shared JWKS endpoint

**RBAC:** Three roles per workspace — Admin (full control), Developer (read/write projects), Viewer (read-only). Enforced at API Gateway (coarse) and service layer (fine-grained).

**SAML:** Integrated via Spring Security SAML2 extension. One IdP configuration per Enterprise workspace.

## Consequences

- Auth-service is a critical path dependency — must be HA (3 replicas, PodDisruptionBudget)
- JWKS endpoint cached by all services (5min TTL) — reduces auth-service load
- API key rotation invalidates old key immediately (no grace period) — by design per BR-002

## Alternatives Considered

- **Auth0** — rejected: $23k+/year at Enterprise scale, data residency concerns, SAML config opacity
- **Keycloak** — rejected: heavy operational footprint, XML-based SAML config complexity; Spring Authorization Server is sufficient
- **AWS Cognito** — rejected: SAML + custom RBAC requires heavy Lambda customization; cloud-lock-in
