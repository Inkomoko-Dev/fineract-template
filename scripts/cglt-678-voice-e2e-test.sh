#!/usr/bin/env bash
# CGLT-678 voice IVR and operations API smoke test.
# Usage:
#   ./scripts/cglt-678-voice-e2e-test.sh
# Optional env:
#   BASE_URL=https://localhost:8443
#   TENANT=default
#   USERNAME=mifos
#   PASSWORD=password
#   SKIP_WEBHOOK=1   # skip unsigned inbound webhook probe

set -euo pipefail

BASE_URL="${BASE_URL:-https://localhost:8443}"
TENANT="${TENANT:-default}"
USERNAME="${USERNAME:-mifos}"
PASSWORD="${PASSWORD:-password}"
API="${BASE_URL}/fineract-provider/api/v1"
CURL_OPTS=(-sk)

pass() { echo "[PASS] $*"; }
fail() { echo "[FAIL] $*" >&2; exit 1; }
section() { echo; echo "== $* =="; }

section "Authenticate"
AUTH_RESPONSE="$(curl "${CURL_OPTS[@]}" -X POST "${API}/authentication" \
  -H "Fineract-Platform-TenantId: ${TENANT}" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"${USERNAME}\",\"password\":\"${PASSWORD}\"}")"
TOKEN="$(echo "${AUTH_RESPONSE}" | python3 -c 'import json,sys; print(json.load(sys.stdin).get("base64EncodedAuthenticationKey",""))' 2>/dev/null || true)"
if [[ -z "${TOKEN}" ]]; then
  fail "Authentication failed: ${AUTH_RESPONSE}"
fi
pass "Authenticated as ${USERNAME}"

AUTH_HEADER=(-H "Fineract-Platform-TenantId: ${TENANT}" -H "Authorization: Basic ${TOKEN}")

check_get() {
  local label="$1"
  local path="$2"
  local response
  response="$(curl "${CURL_OPTS[@]}" "${AUTH_HEADER[@]}" "${API}${path}")"
  if echo "${response}" | python3 -c 'import json,sys; json.load(sys.stdin)' >/dev/null 2>&1; then
    pass "${label}"
    echo "${response}" | python3 -m json.tool | head -n 20
  else
    fail "${label} returned non-JSON: ${response}"
  fi
}

section "Voice operations dashboard"
check_get "GET /africastalking/voice/dashboard" "/africastalking/voice/dashboard"

section "Voice callbacks"
check_get "GET /africastalking/voice/callbacks" "/africastalking/voice/callbacks"
FIRST_CALLBACK_ID="$(curl "${CURL_OPTS[@]}" "${AUTH_HEADER[@]}" "${API}/africastalking/voice/callbacks" \
  | python3 -c 'import json,sys; data=json.load(sys.stdin); print(data[0]["id"] if data else "")' 2>/dev/null || true)"
if [[ -n "${FIRST_CALLBACK_ID}" ]]; then
  check_get "GET /africastalking/voice/callbacks/${FIRST_CALLBACK_ID}" "/africastalking/voice/callbacks/${FIRST_CALLBACK_ID}"
else
  echo "[INFO] No callbacks to detail-test"
fi

section "Voice voicemails and queue"
check_get "GET /africastalking/voice/voicemails" "/africastalking/voice/voicemails"
check_get "GET /africastalking/voice/queue" "/africastalking/voice/queue"

section "Voice IVR menus"
check_get "GET /africastalking/voice/menus/definitions" "/africastalking/voice/menus/definitions?menuKey=MAIN"
check_get "GET /africastalking/voice/menus/options" "/africastalking/voice/menus/options?menuKey=MAIN&languageCode=en"

if [[ "${SKIP_WEBHOOK:-}" != "1" ]]; then
  section "Inbound voice webhook probe (unsigned)"
  WEBHOOK_RESPONSE="$(curl "${CURL_OPTS[@]}" -w '\n%{http_code}' -X POST "${API}/africastalking/webhooks/voice/callback" \
    -H "Fineract-Platform-TenantId: ${TENANT}" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    --data 'sessionId=test-session&callerNumber=%2B254700000001&destinationNumber=%2B254711082867&direction=Inbound&callSessionState=Ringing')"
  HTTP_CODE="$(echo "${WEBHOOK_RESPONSE}" | tail -n1)"
  BODY="$(echo "${WEBHOOK_RESPONSE}" | sed '$d')"
  if [[ "${HTTP_CODE}" == "200" || "${HTTP_CODE}" == "401" ]]; then
    pass "Webhook probe returned HTTP ${HTTP_CODE} (200=accepted, 401=signature required)"
    echo "${BODY}" | head -c 300
    echo
  else
    fail "Webhook probe unexpected HTTP ${HTTP_CODE}: ${BODY}"
  fi
fi

section "Done"
echo "CGLT-678 voice E2E smoke checks completed."
