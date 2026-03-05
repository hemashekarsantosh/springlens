#!/usr/bin/env bash
# Stop all locally-running SpringLens services

echo "Stopping SpringLens local services..."

for service in auth-service ingestion-service analysis-service recommendation-service notification-service frontend; do
  pid_file="/tmp/springlens-$service.pid"
  if [ -f "$pid_file" ]; then
    pid=$(cat "$pid_file")
    kill "$pid" 2>/dev/null && echo "  Stopped $service (PID $pid)" || echo "  $service was not running"
    rm -f "$pid_file"
  fi
done

echo "Done. Kafka and Redis continue running (stop manually if needed)."
