#!/usr/bin/env bash
#
# package-mac.sh — build a native macOS application bundle (Photomosaic.app)
# for the Photomosaic JavaFX app, using the JDK's jpackage tool.
#
# This bundles a trimmed Java runtime *inside* the .app, so end users don't need
# Java installed. Run this ON A MAC (jpackage emits host-platform bundles only).
#
# Usage:
#   ./packaging/mac/package-mac.sh                 # build Photomosaic.app
#   ./packaging/mac/package-mac.sh --dmg           # also build a Photomosaic-<ver>.dmg
#   ./packaging/mac/package-mac.sh --rebuild       # force a fresh `mvn package`
#   ./packaging/mac/package-mac.sh --sign "Developer ID Application: Name (TEAMID)"
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

APP_NAME="Photomosaic"
APP_VERSION="1.0.0"
VENDOR="Tauasa Timoteo"
MAIN_JAR="photomosaic.jar"
MAIN_CLASS="org.tauasa.apps.photomosaic.Launcher"
IDENTIFIER="org.tauasa.apps.photomosaic"

OUT_DIR="$PROJECT_ROOT/build/mac"
STAGE_DIR="$PROJECT_ROOT/build/stage"
PNG="$SCRIPT_DIR/Photomosaic.png"
ICNS="$SCRIPT_DIR/Photomosaic.icns"

MAKE_DMG=0
REBUILD=0
SIGN_ID=""

while [ $# -gt 0 ]; do
  case "$1" in
    --dmg)     MAKE_DMG=1 ;;
    --rebuild) REBUILD=1 ;;
    --sign)    SIGN_ID="${2:-}"; shift ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $1" >&2; exit 2 ;;
  esac
  shift
done

# --- sanity checks ----------------------------------------------------------
if [ "$(uname)" != "Darwin" ]; then
  echo "!! jpackage produces bundles for the host OS only — run this on macOS." >&2
  exit 1
fi
if ! command -v jpackage >/dev/null 2>&1; then
  echo "!! jpackage not found. Use a JDK 17+ (e.g. Temurin) and ensure its bin is on PATH." >&2
  exit 1
fi
if ! command -v mvn >/dev/null 2>&1; then
  echo "!! Maven (mvn) not found on PATH." >&2
  exit 1
fi

# --- build the fat jar ------------------------------------------------------
JAR_PATH="$PROJECT_ROOT/target/$MAIN_JAR"
if [ "$REBUILD" = "1" ] || [ ! -f "$JAR_PATH" ]; then
  echo ">> Building the fat jar (mvn -DskipTests package)…"
  ( cd "$PROJECT_ROOT" && mvn -q -DskipTests clean package )
fi
[ -f "$JAR_PATH" ] || { echo "!! Expected $JAR_PATH but it isn't there." >&2; exit 1; }

# --- icon -------------------------------------------------------------------
if [ ! -f "$ICNS" ]; then
  if [ -f "$PNG" ]; then
    echo ">> Generating $ICNS from PNG…"
    "$SCRIPT_DIR/make-icns.sh" "$PNG" "$ICNS"
  else
    echo "!! No icon found ($ICNS / $PNG); the app will use a default icon."
  fi
fi

# --- stage just the jar for jpackage's --input -----------------------------
rm -rf "$STAGE_DIR" "$OUT_DIR"
mkdir -p "$STAGE_DIR" "$OUT_DIR"
cp "$JAR_PATH" "$STAGE_DIR/$MAIN_JAR"

ICON_ARG=()
[ -f "$ICNS" ] && ICON_ARG=(--icon "$ICNS")

SIGN_ARGS=()
if [ -n "$SIGN_ID" ]; then
  SIGN_ARGS=(--mac-sign --mac-signing-key-user-name "$SIGN_ID")
fi

# --- build the .app (app-image) --------------------------------------------
echo ">> Running jpackage (app-image)…"
jpackage \
  --type app-image \
  --name "$APP_NAME" \
  --app-version "$APP_VERSION" \
  --vendor "$VENDOR" \
  --input "$STAGE_DIR" \
  --main-jar "$MAIN_JAR" \
  --main-class "$MAIN_CLASS" \
  --mac-package-identifier "$IDENTIFIER" \
  --java-options "-Xmx2g" \
  --java-options "-Dapple.awt.application.appearance=system" \
  ${ICON_ARG[@]+"${ICON_ARG[@]}"} \
  ${SIGN_ARGS[@]+"${SIGN_ARGS[@]}"} \
  --dest "$OUT_DIR"

echo ">> Built: $OUT_DIR/$APP_NAME.app"

# --- optional .dmg ----------------------------------------------------------
if [ "$MAKE_DMG" = "1" ]; then
  echo ">> Wrapping into a .dmg…"
  jpackage \
    --type dmg \
    --name "$APP_NAME" \
    --app-version "$APP_VERSION" \
    --vendor "$VENDOR" \
    --app-image "$OUT_DIR/$APP_NAME.app" \
    ${SIGN_ARGS[@]+"${SIGN_ARGS[@]}"} \
    --dest "$OUT_DIR"
  echo ">> Built: $OUT_DIR/$APP_NAME-$APP_VERSION.dmg"
fi

echo
echo "Done. Drag $APP_NAME.app to /Applications, or open the .dmg."
if [ -z "$SIGN_ID" ]; then
  echo "Note: this build is unsigned. On first launch macOS Gatekeeper may block it —"
  echo "right-click the app → Open, or run:  xattr -dr com.apple.quarantine \"$OUT_DIR/$APP_NAME.app\""
fi
