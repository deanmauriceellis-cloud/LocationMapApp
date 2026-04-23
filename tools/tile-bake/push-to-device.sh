#!/usr/bin/env bash
# Push the tile archive to a connected device's osmdroid base path.
# Defaults to Lenovo TB305FU (HNY0CY0W); override with DEVICE=...
#
# Usage:
#   ./push-to-device.sh                 # push to default device
#   DEVICE=emulator-5570 ./push-to-device.sh
set -euo pipefail

DEVICE="${DEVICE:-HNY0CY0W}"
PKG="com.destructiveaigurus.katrinasmysticvisitorsguide"
SRC="$(dirname "$0")/dist/salem_tiles.sqlite"
DEST_DIR="/sdcard/Android/data/${PKG}/files"
DEST="${DEST_DIR}/salem_tiles.sqlite"

if [ ! -f "$SRC" ]; then
  echo "ERROR: $SRC does not exist. Build it first:" >&2
  echo "  node render-tiles.js && node merge-into-bundle.js" >&2
  exit 1
fi

size_mb=$(du -m "$SRC" | cut -f1)
echo "Pushing ${size_mb} MB → ${DEVICE}:${DEST}"
adb -s "$DEVICE" shell "mkdir -p ${DEST_DIR}"
adb -s "$DEVICE" push "$SRC" "$DEST"
adb -s "$DEVICE" shell "ls -la ${DEST}"
echo "Done."
