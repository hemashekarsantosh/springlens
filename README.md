# SpringLens

**Spring Boot Startup Optimization SaaS** — Give your Java/Spring Boot teams continuous visibility into startup performance.

## What is SpringLens?

SpringLens ingests JVM startup telemetry via a lightweight Java agent, analyzes bean initialization timelines and dependency graphs, and delivers ranked optimization recommendations:

- Lazy loading (save 30–50% startup time)
- AOT compilation guidance
- GraalVM native feasibility assessment
- Class Data Sharing (zero-code-change 33% improvement)
- CI/CD budget gates (fail builds on startup regression)

## Quick Start

### Prerequisites
- Docker 24+ and Docker Compose v2
- Java 21+ (for local service development)
- Node.js 20+ (for frontend development)

### Start locally

```bash
# Copy environment template
cp .env.example .env
# Edit .env with your GitHub OAuth and Stripe keys

# Start all services
make dev

# Open dashboard
open http://localhost:3000
```

### Add SpringLens agent to your Spring Boot app

```gradle
// build.gradle
dependencies {
    runtimeOnly 'io.springlens:springlens-agent:1.0.0'
}
```

```bash
# Set environment variables
export SPRINGLENS_API_KEY=sl_proj_your_key_here
export SPRINGLENS_PROJECT=your-project-id
export SPRINGLENS_ENV=staging
```

The agent automatically uploads startup telemetry on every application start. No code changes required.

## Architecture

```
JVM Agent → API Gateway → ingestion-service → Kafka → analysis-service → recommendation-service
                                                                              ↓
Dashboard (Next.js) ← API Gateway ← analysis-service / recommendation-service
```

See [docs/architecture/](./docs/architecture/) for full C4 diagrams, ADRs, and tech stack.

## Services

| Service | Port | Description |
|---------|------|-------------|
| frontend | 3000 | Next.js dashboard |
| ingestion-service | 8081 | JVM telemetry receiver |
| analysis-service | 8082 | Startup timeline analysis |
| recommendation-service | 8083 | Optimization recommendations |
| auth-service | 8084 | Auth, billing, workspaces |
| notification-service | 8085 | Webhooks (Slack, PagerDuty) |
| Kafka UI | 8090 | Dev: inspect Kafka topics |
| Grafana | 3001 | Metrics dashboards |

## Development

```bash
make build    # Build all services
make test     # Run all tests
make lint     # Run linters
make logs     # Tail service logs
make stop     # Stop docker-compose
```

## CI/CD Integration (GitHub Actions)

```yaml
- name: Check startup budget
  uses: springlens/startup-check@v1
  with:
    api-key: ${{ secrets.SPRINGLENS_API_KEY }}
    project: ${{ vars.SPRINGLENS_PROJECT }}
    environment: ci
    budget-ms: 8000  # Fail if startup > 8 seconds
```

## License

Proprietary — SpringLens © 2026
