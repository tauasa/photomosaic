#!/usr/bin/env bash
#
# Start Photomosaic.
#   ./run.sh              build (if needed) and run
#   ./run.sh --rebuild    force a clean rebuild first
#
# Override JVM options (e.g. more heap for very large tile libraries) with:
#   PHOTOMOSAIC_JAVA_OPTS="-Xmx4g" ./run.sh
#
set -euo pipefail

# Work from this script's directory so target/ and pom.xml resolve when double-clicked.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR="target/photomosaic.jar"
JAVA_OPTS="${PHOTOMOSAIC_JAVA_OPTS:-}"

# Prefer JAVA_HOME if it points at a usable JDK, else fall back to PATH.
if [[ -n "${JAVA_HOME:-}" && -x "$JAVA_HOME/bin/java" ]]; then
  JAVA="$JAVA_HOME/bin/java"
else
  JAVA="java"
fi
if ! command -v "$JAVA" >/dev/null 2>&1; then
  echo "Error: Java not found. Install a JDK 17+ (https://adoptium.net) and try again." >&2
  exit 1
fi

# Optional forced rebuild.
if [[ "${1:-}" == "--rebuild" ]]; then
  rm -f "$JAR"
  shift
fi

# Build the fat jar on first run (or after --rebuild).
if [[ ! -f "$JAR" ]]; then
  echo "Building $JAR (first run, this may take a minute)…"
  if [[ -x "./mvnw" ]]; then
    MVN="./mvnw"
  elif command -v mvn >/dev/null 2>&1; then
    MVN="mvn"
  else
    echo "Error: Maven not found and $JAR isn't built." >&2
    echo "Install Maven (https://maven.apache.org) or run 'mvn clean package' once." >&2
    exit 1
  fi
  "$MVN" -q clean package
fi

echo "Starting Photomosaic…"
# JAVA_OPTS is intentionally unquoted so multiple options split into separate args.
exec "$JAVA" $JAVA_OPTS -jar "$JAR" "$@"
