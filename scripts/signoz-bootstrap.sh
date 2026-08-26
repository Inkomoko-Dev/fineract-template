#!/usr/bin/env bash
# Downloads OpenTelemetry Java agent and ClickHouse histogramQuantile UDF for SigNoz.
# Run from repo root: bash scripts/signoz-bootstrap.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

OTEL_AGENT_VERSION="${OTEL_AGENT_VERSION:-2.10.0}"
HISTOGRAM_VERSION="${HISTOGRAM_VERSION:-v0.0.1}"

AGENT_DIR="signoz/agent"
AGENT_JAR="${AGENT_DIR}/opentelemetry-javaagent.jar"
UDF_DIR="signoz/clickhouse/user_scripts"
UDF_BIN="${UDF_DIR}/histogramQuantile"

mkdir -p "$AGENT_DIR" "$UDF_DIR"

echo "==> OpenTelemetry Java agent ${OTEL_AGENT_VERSION}"
if [[ ! -f "$AGENT_JAR" ]]; then
  curl -fsSL \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
    -o "$AGENT_JAR"
  echo "    downloaded ${AGENT_JAR}"
else
  echo "    exists ${AGENT_JAR}"
fi

echo "==> ClickHouse histogramQuantile UDF ${HISTOGRAM_VERSION}"
if [[ ! -f "$UDF_BIN" ]]; then
  node_os="$(uname -s | tr '[:upper:]' '[:lower:]')"
  node_arch="$(uname -m | sed 's/aarch64/arm64/' | sed 's/x86_64/amd64/')"
  tmp="$(mktemp -d)"
  trap 'rm -rf "$tmp"' EXIT
  curl -fsSL \
    "https://github.com/SigNoz/signoz/releases/download/histogram-quantile%2F${HISTOGRAM_VERSION}/histogram-quantile_${node_os}_${node_arch}.tar.gz" \
    -o "${tmp}/histogram-quantile.tar.gz"
  tar -xzf "${tmp}/histogram-quantile.tar.gz" -C "$tmp"
  install -m 0755 "${tmp}/histogram-quantile" "$UDF_BIN"
  echo "    installed ${UDF_BIN}"
else
  echo "    exists ${UDF_BIN}"
fi

# Keep large binaries out of git
for entry in \
  "signoz/agent/opentelemetry-javaagent.jar" \
  "signoz/clickhouse/user_scripts/histogramQuantile"
do
  if ! grep -qxF "$entry" .gitignore 2>/dev/null; then
    echo "$entry" >> .gitignore
    echo "    added ${entry} to .gitignore"
  fi
done

echo "==> Done. Start stack with:"
echo "    docker compose -f docker-compose.yml -f docker-compose.signoz.yml up -d"
