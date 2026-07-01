#!/usr/bin/env bash
#
# build-quick-app.sh — assemble a double-clickable Photomosaic.app that runs on
# the Java already installed on the machine (no bundled JRE). Lighter than
# package-mac.sh; good for quick local use.
#
# Usage:
#   ./packaging/mac/build-quick-app.sh            # build jar if needed, assemble app
#   ./packaging/mac/build-quick-app.sh --rebuild  # force a fresh `mvn package`
#
# Output: build/mac-quick/Photomosaic.app
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

REBUILD=0
[ "${1:-}" = "--rebuild" ] && REBUILD=1

JAR_PATH="$PROJECT_ROOT/target/photomosaic.jar"
if [ "$REBUILD" = "1" ] || [ ! -f "$JAR_PATH" ]; then
  command -v mvn >/dev/null 2>&1 || { echo "!! Maven not found." >&2; exit 1; }
  echo ">> Building the fat jar…"
  ( cd "$PROJECT_ROOT" && mvn -q -DskipTests clean package )
fi
[ -f "$JAR_PATH" ] || { echo "!! Expected $JAR_PATH" >&2; exit 1; }

OUT="$PROJECT_ROOT/build/mac-quick"
APP="$OUT/Photomosaic.app"
rm -rf "$OUT"
mkdir -p "$OUT"
cp -R "$SCRIPT_DIR/app-skeleton/Photomosaic.app" "$APP"

mkdir -p "$APP/Contents/app"
cp "$JAR_PATH" "$APP/Contents/app/photomosaic.jar"
chmod +x "$APP/Contents/MacOS/Photomosaic"

# Clear the quarantine flag so it opens without a right-click dance (best effort).
xattr -dr com.apple.quarantine "$APP" 2>/dev/null || true

echo ">> Built: $APP"
echo "   Double-click it, or drag it to /Applications."
