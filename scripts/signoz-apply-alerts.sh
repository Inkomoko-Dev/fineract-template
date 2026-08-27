#!/usr/bin/env bash
# Apply alert rules from signoz/alerts/*.json to a running SigNoz instance.
#
# Usage:
#   export SIGNOZ_URL=http://localhost:3301
#   export SIGNOZ_API_EMAIL=admin@example.com
#   export SIGNOZ_API_PASSWORD=your_password
#   export SIGNOZ_ALERT_CHANNEL_IDS=<uuid>   # optional, comma-separated
#   bash scripts/signoz-apply-alerts.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ALERTS_DIR="${ROOT}/signoz/alerts"

SIGNOZ_URL="${SIGNOZ_URL:-http://localhost:3301}"
SIGNOZ_API_EMAIL="${SIGNOZ_API_EMAIL:-}"
SIGNOZ_API_PASSWORD="${SIGNOZ_API_PASSWORD:-}"
SIGNOZ_API_KEY="${SIGNOZ_API_KEY:-}"
SIGNOZ_ALERT_CHANNEL_IDS="${SIGNOZ_ALERT_CHANNEL_IDS:-}"

if [[ ! -d "$ALERTS_DIR" ]]; then
  echo "ERROR: alerts directory not found: ${ALERTS_DIR}" >&2
  exit 1
fi

auth_header() {
  if [[ -n "$SIGNOZ_API_KEY" ]]; then
    echo "SIGNOZ-API-KEY: ${SIGNOZ_API_KEY}"
    return
  fi
  if [[ -z "$SIGNOZ_API_EMAIL" || -z "$SIGNOZ_API_PASSWORD" ]]; then
    echo "ERROR: set SIGNOZ_API_KEY or SIGNOZ_API_EMAIL + SIGNOZ_API_PASSWORD" >&2
    exit 1
  fi
  local token
  token="$(curl -fsS -X POST "${SIGNOZ_URL}/api/v1/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${SIGNOZ_API_EMAIL}\",\"password\":\"${SIGNOZ_API_PASSWORD}\"}" \
    | python3 -c 'import json,sys; print(json.load(sys.stdin).get("accessJwt",""))')"
  if [[ -z "$token" ]]; then
    echo "ERROR: login failed — check SIGNOZ_URL and credentials" >&2
    exit 1
  fi
  echo "Authorization: Bearer ${token}"
}

AUTH="$(auth_header)"
CHANNEL_JSON="[]"
if [[ -n "$SIGNOZ_ALERT_CHANNEL_IDS" ]]; then
  CHANNEL_JSON="$(python3 - <<'PY' "$SIGNOZ_ALERT_CHANNEL_IDS"
import json, sys
ids = [x.strip() for x in sys.argv[1].split(",") if x.strip()]
print(json.dumps(ids))
PY
)"
fi

ok=0
fail=0
for file in "$ALERTS_DIR"/*.json; do
  [[ -f "$file" ]] || continue
  name="$(basename "$file" .json)"
  payload="$(python3 - <<'PY' "$file" "$CHANNEL_JSON"
import json, sys
path, channels = sys.argv[1], json.loads(sys.argv[2])
with open(path) as f:
    data = json.load(f)
if channels:
    data["preferredChannels"] = channels
print(json.dumps(data))
PY
)"
  if curl -fsS -X POST "${SIGNOZ_URL}/api/v1/rules" \
    -H "Content-Type: application/json" \
    -H "$AUTH" \
    -d "$payload" >/dev/null 2>&1; then
    echo "OK:  ${name}"
    ok=$((ok + 1))
  else
    echo "FAIL: ${name} (create manually in SigNoz UI if API shape drifted)" >&2
    fail=$((fail + 1))
  fi
done

echo ""
echo "Applied ${ok} rule(s), ${fail} failed."
[[ "$fail" -eq 0 ]] || exit 1
