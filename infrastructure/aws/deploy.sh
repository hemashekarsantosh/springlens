#!/bin/bash
# SpringLens — Deploy & run on EC2
# Run this AFTER ec2-user-data.sh has completed (or SSH in and run manually)
#
# Usage: ./deploy.sh
# Prereqs: Code is at /home/ec2-user/springlens

set -euo pipefail

APP_DIR="/home/ec2-user/springlens"
cd "$APP_DIR"

echo "━━━ SpringLens EC2 Deploy ━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# ── 1. Environment variables ─────────────────────────────────────────────────
export DB_PASSWORD=springlens_dev
export KAFKA_BROKERS=localhost:9092
export REDIS_HOST=localhost
export SPRING_PROFILES_ACTIVE=local
export JWT_SECRET="${JWT_SECRET:-dev-secret-change-in-production-minimum-256-bits}"
export GITHUB_CLIENT_ID="${GITHUB_CLIENT_ID:-}"
export GITHUB_CLIENT_SECRET="${GITHUB_CLIENT_SECRET:-}"
export STRIPE_SECRET_KEY="${STRIPE_SECRET_KEY:-sk_test_placeholder}"
export STRIPE_WEBHOOK_SECRET="${STRIPE_WEBHOOK_SECRET:-whsec_placeholder}"
export KAFKA_HOME=/opt/kafka

# Get public IP for NextAuth
PUBLIC_IP=$(curl -s http://169.254.169.254/latest/meta-data/public-ipv4 2>/dev/null || echo "localhost")
export NEXTAUTH_URL="http://${PUBLIC_IP}:3000"
export NEXTAUTH_SECRET="${NEXTAUTH_SECRET:-dev-nextauth-secret-$(openssl rand -hex 16)}"
export NEXT_PUBLIC_API_URL="http://${PUBLIC_IP}:8084/v1"

echo "Public IP: $PUBLIC_IP"
echo "Frontend URL: $NEXTAUTH_URL"

# ── 2. Build backend ─────────────────────────────────────────────────────────
echo "⧖ Building Spring Boot services..."
chmod +x gradlew 2>/dev/null || true
./gradlew build -x test --parallel --quiet

# ── 3. Start backend services ────────────────────────────────────────────────
echo "⧖ Starting backend services..."

start_service() {
  local name=$1
  local port=$2
  local db=$3
  echo "  Starting $name on :$port..."
  DB_URL="jdbc:postgresql://localhost:5432/$db" \
    nohup ./gradlew ":services:$name:bootRun" \
    --args="--server.port=$port" \
    > "/tmp/springlens-$name.log" 2>&1 &
  echo $! > "/tmp/springlens-$name.pid"
}

start_service "auth-service"           8084 springlens_auth
start_service "ingestion-service"      8081 springlens_ingestion
start_service "analysis-service"       8082 springlens_analysis
start_service "recommendation-service" 8083 springlens_recommendation
start_service "notification-service"   8085 springlens_notification

# ── 4. Start frontend ────────────────────────────────────────────────────────
echo "⧖ Installing frontend dependencies..."
cd frontend
npm install --production=false --quiet
echo "  Starting frontend on :3000..."
AUTH_GITHUB_ID="$GITHUB_CLIENT_ID" \
  AUTH_GITHUB_SECRET="$GITHUB_CLIENT_SECRET" \
  nohup npm run dev -- -H 0.0.0.0 > /tmp/springlens-frontend.log 2>&1 &
echo $! > /tmp/springlens-frontend.pid
cd ..

# ── 5. Wait for services to be healthy ───────────────────────────────────────
echo ""
echo "⧖ Waiting for services to start (this takes 1-2 minutes)..."
sleep 30

echo ""
echo "━━━ SpringLens Running ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "  Dashboard:       http://$PUBLIC_IP:3000"
echo "  Auth API:        http://$PUBLIC_IP:8084/healthz"
echo "  Ingestion API:   http://$PUBLIC_IP:8081/healthz"
echo "  Analysis API:    http://$PUBLIC_IP:8082/healthz"
echo "  Recommendation:  http://$PUBLIC_IP:8083/healthz"
echo "  Notification:    http://$PUBLIC_IP:8085/healthz"
echo ""
echo "  Logs: tail -f /tmp/springlens-*.log"
echo "  Stop: ./stop-local.sh"
echo ""
echo "  GitHub OAuth callback URL:"
echo "  http://$PUBLIC_IP:3000/api/auth/callback/github"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
