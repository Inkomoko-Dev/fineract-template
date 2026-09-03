#!/usr/bin/env bash
# Reset SigNoz ClickHouse/SQLite/Zookeeper volumes (fixes schema mismatch / code 117).
# WARNING: deletes all locally stored traces, metrics, and logs in SigNoz.
#
# Usage (from repo root):
#   bash scripts/signoz-reset-clickhouse.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

COMPOSE=(docker compose -f docker-compose.yml -f docker-compose.signoz.yml)

echo "==> Stopping SigNoz stack..."
"${COMPOSE[@]}" stop signoz signoz-otel-collector signoz-telemetrystore-migrator signoz-clickhouse signoz-zookeeper otel-collector cadvisor signoz-init-clickhouse 2>/dev/null || true

echo "==> Removing SigNoz data volumes..."
for vol in fineract_signoz_clickhouse fineract_signoz_sqlite fineract_signoz_zookeeper; do
  if docker volume inspect "$vol" >/dev/null 2>&1; then
    docker volume rm "$vol"
    echo "    removed ${vol}"
  else
    echo "    skip ${vol} (not found)"
  fi
done

echo "==> Starting SigNoz stack (first boot recreates schema)..."
bash scripts/signoz-bootstrap.sh
"${COMPOSE[@]}" up -d signoz-init-clickhouse signoz-zookeeper signoz-clickhouse signoz-telemetrystore-migrator signoz-otel-collector signoz otel-collector cadvisor

echo "==> Waiting for SigNoz UI health..."
for i in $(seq 1 60); do
  if curl -fsS "http://localhost:3301/api/v1/health" >/dev/null 2>&1; then
    echo "    SigNoz UI is up"
    break
  fi
  sleep 5
done

echo "==> Checking signoz-otel-collector for ClickHouse parse errors (last 5m)..."
errors="$("${COMPOSE[@]}" logs signoz-otel-collector --since 5m 2>&1 | grep -iE 'Cannot parse JSON|Dropping data' | tail -5 || true)"
if [[ -n "$errors" ]]; then
  echo "$errors"
  echo "WARNING: collector still reports ingest errors — investigate logs above." >&2
  exit 1
fi
echo "    no ClickHouse parse / drop errors in recent collector logs"

echo "==> Done. Generate traffic and open http://localhost:3301"
