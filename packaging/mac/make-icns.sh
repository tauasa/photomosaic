#!/usr/bin/env bash
#
# make-icns.sh — convert a 1024x1024 PNG into a macOS .icns, using the built-in
# `sips` and `iconutil` tools (no extra installs). macOS only.
#
# Usage: ./make-icns.sh Photomosaic.png Photomosaic.icns
#
set -euo pipefail

SRC="${1:?usage: make-icns.sh <src.png> <out.icns>}"
OUT="${2:?usage: make-icns.sh <src.png> <out.icns>}"

if [ "$(uname)" != "Darwin" ]; then
  echo "!! make-icns.sh needs macOS tools (sips/iconutil)." >&2
  exit 1
fi

TMP="$(mktemp -d)/Photomosaic.iconset"
mkdir -p "$TMP"

for s in 16 32 128 256 512; do
  sips -z "$s" "$s"           "$SRC" --out "$TMP/icon_${s}x${s}.png"    >/dev/null
  d=$(( s * 2 ))
  sips -z "$d" "$d"           "$SRC" --out "$TMP/icon_${s}x${s}@2x.png" >/dev/null
done

iconutil -c icns "$TMP" -o "$OUT"
echo ">> Wrote $OUT"
