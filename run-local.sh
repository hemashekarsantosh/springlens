#!/usr/bin/env bash
# SpringLens — Run all services locally WITHOUT Docker
# Prerequisites: PostgreSQL 16 + TimescaleDB, Redis 7, Kafka (local), Java 21, Node 20
#
# Install dependencies:
#   Windows (Chocolatey):
#     choco install postgresql16 redis kafka
#   macOS (Homebrew):
#     brew install postgresql@16 redis kafka
#     brew install timescaledb/tap/timescaledb  # then run timescaledb-tune
#   Linux:
#     Follow https://docs.timescale.com/install/latest/self-hosted/installation-linux/

set -euo pipefail

echo "━━━ SpringLens Local Dev (no Docker) ━━━━━━━━━━━━━━━━━━"

# ── 1. Create databases ──────────────────────────────────────────────────────
echo "⧖ Creating databases..."
psql -U postgres -c "CREATE USER springlens WITH PASSWORD 'springlens_dev';" 2>/dev/null || true
for db in springlens_auth springlens_ingestion springlens_analysis springlens_recommendation springlens_notification; do
  psql -U postgres -c "CREATE DATABASE $db OWNER springlens;" 2>/dev/null || true
done

# Enable TimescaleDB in ingestion and analysis DBs
psql -U springlens -d springlens_ingestion -c "CREATE EXTENSION IF NOT EXISTS timescaledb;" 2>/dev/null || true
psql -U springlens -d springlens_analysis -c "CREATE EXTENSION IF NOT EXISTS timescaledb;" 2>/dev/null || true
echo "✓ Databases ready"

# ── 2. Check Redis ───────────────────────────────────────────────────────────
if ! redis-cli ping > /dev/null 2>&1; then
  echo "⧖ Starting Redis..."
  redis-server --daemonize yes --logfile /tmp/redis-springlens.log
fi
echo "✓ Redis running"

# ── 3. Check Kafka ───────────────────────────────────────────────────────────
if ! kafka-topics.sh --bootstrap-server localhost:9092 --list > /dev/null 2>&1; then
  echo "⧖ Starting Kafka (Kraft mode)..."
  KAFKA_HOME="${KAFKA_HOME:-/usr/local/kafka}"
  # Generate cluster ID if needed
  if [ ! -f /tmp/kafka-springlens-kraft.id ]; then
    $KAFKA_HOME/bin/kafka-storage.sh random-uuid > /tmp/kafka-springlens-kraft.id
  fi
  CLUSTER_ID=$(cat /tmp/kafka-springlens-kraft.id)
  $KAFKA_HOME/bin/kafka-storage.sh format -t "$CLUSTER_ID" \
    -c $KAFKA_HOME/config/kraft/server.properties --ignore-formatted 2>/dev/null || true
  $KAFKA_HOME/bin/kafka-server-start.sh \
    -daemon $KAFKA_HOME/config/kraft/server.properties
  sleep 3
fi
echo "✓ Kafka running"

# ── 4. Export environment for local Spring Boot ──────────────────────────────
export DB_PASSWORD=springlens_dev
export KAFKA_BROKERS=localhost:9092
export REDIS_HOST=localhost
export SPRING_PROFILES_ACTIVE=local
export JWT_SECRET=dev-secret-change-in-production-minimum-256-bits
export GITHUB_CLIENT_ID=${GITHUB_CLIENT_ID:-your-github-oauth-app-client-id}
export GITHUB_CLIENT_SECRET=${GITHUB_CLIENT_SECRET:-your-github-oauth-app-client-secret}
export STRIPE_SECRET_KEY=${STRIPE_SECRET_KEY:-sk_test_placeholder}
export STRIPE_WEBHOOK_SECRET=${STRIPE_WEBHOOK_SECRET:-whsec_placeholder}

echo ""
echo "⧖ Starting Spring Boot services in background..."

# ── 5. Start each service ────────────────────────────────────────────────────
start_service() {
  local name=$1
  local port=$2
  local db=$3
  local db_port=${4:-5432}
  echo "  Starting $name on :$port..."
  DB_URL="jdbc:postgresql://localhost:$db_port/$db" \
    ./gradlew ":services:$name:bootRun" \
    --args="--server.port=$port" \
    > /tmp/springlens-$name.log 2>&1 &
  echo $! > /tmp/springlens-$name.pid
}

start_service "auth-service"           8084 springlens_auth 5432
start_service "ingestion-service"      8081 springlens_ingestion 5432
start_service "analysis-service"       8082 springlens_analysis 5432
start_service "recommendation-service" 8083 springlens_recommendation 5432
start_service "notification-service"   8085 springlens_notification 5432

# ── 6. Start frontend ────────────────────────────────────────────────────────
echo "  Starting frontend on :3000..."
cd frontend
NEXT_PUBLIC_API_URL=http://localhost:8080/v1 \
  NEXTAUTH_URL=http://localhost:3000 \
  NEXTAUTH_SECRET=dev-nextauth-secret \
  npm run dev > /tmp/springlens-frontend.log 2>&1 &
echo $! > /tmp/springlens-frontend.pid
cd ..

echo ""
echo "━━━ All services started ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  Dashboard:         http://localhost:3000"
echo "  Ingestion API:     http://localhost:8081/healthz"
echo "  Analysis API:      http://localhost:8082/healthz"
echo "  Recommendation:    http://localhost:8083/healthz"
echo "  Auth API:          http://localhost:8084/healthz"
echo ""
echo "  Logs: /tmp/springlens-<service>.log"
echo "  Stop: ./stop-local.sh"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
