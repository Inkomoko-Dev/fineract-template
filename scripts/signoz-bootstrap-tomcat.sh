#!/usr/bin/env bash
# Install OpenTelemetry Java agent for Tomcat (bare-metal CBS).
# Default install path matches prod setenv: /opt/tomcat/lib/opentelemetry-javaagent.jar
#
# Usage:
#   sudo bash scripts/signoz-bootstrap-tomcat.sh
#   sudo TOMCAT_LIB=/opt/tomcat/lib bash scripts/signoz-bootstrap-tomcat.sh
#   bash scripts/signoz-bootstrap-tomcat.sh   # installs under ./signoz/agent if not root
set -euo pipefail

OTEL_AGENT_VERSION="${OTEL_AGENT_VERSION:-2.10.0}"
TOMCAT_LIB="${TOMCAT_LIB:-/opt/tomcat/lib}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REPO_AGENT="${ROOT}/signoz/agent/opentelemetry-javaagent.jar"

if [[ -w "${TOMCAT_LIB}" ]] 2>/dev/null || [[ -d "${TOMCAT_LIB}" && -w "$(dirname "${TOMCAT_LIB}")" ]]; then
  TARGET_DIR="${TOMCAT_LIB}"
elif [[ "$(id -u)" -eq 0 ]]; then
  mkdir -p "${TOMCAT_LIB}"
  TARGET_DIR="${TOMCAT_LIB}"
else
  echo "WARN: ${TOMCAT_LIB} not writable; installing to ${ROOT}/signoz/agent instead"
  echo "      Copy later: sudo cp ${REPO_AGENT} ${TOMCAT_LIB}/"
  TARGET_DIR="${ROOT}/signoz/agent"
  mkdir -p "${TARGET_DIR}"
fi

TARGET_JAR="${TARGET_DIR}/opentelemetry-javaagent.jar"

echo "==> OpenTelemetry Java agent ${OTEL_AGENT_VERSION} → ${TARGET_JAR}"
if [[ -f "${TARGET_JAR}" ]]; then
  echo "    exists ${TARGET_JAR}"
else
  curl -fsSL \
    "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar" \
    -o "${TARGET_JAR}"
  chmod 0644 "${TARGET_JAR}"
  echo "    downloaded ${TARGET_JAR}"
fi

# Keep a copy under the repo agent dir for Docker overlays / local tests
mkdir -p "${ROOT}/signoz/agent"
if [[ "${TARGET_JAR}" != "${REPO_AGENT}" ]]; then
  cp -f "${TARGET_JAR}" "${REPO_AGENT}"
  echo "    mirrored ${REPO_AGENT}"
fi

echo "==> Done. Tomcat setenv should include:"
echo "    CATALINA_OPTS=\"\$CATALINA_OPTS -javaagent:${TOMCAT_LIB}/opentelemetry-javaagent.jar\""
echo "    export OTEL_EXPORTER_OTLP_ENDPOINT=http://127.0.0.1:4317"
