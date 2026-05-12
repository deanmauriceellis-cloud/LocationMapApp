#!/usr/bin/env bash
# resume-conus.sh — single coordinated resume of the CONUS bake.
#
# Stage 1: recompress already-baked Z3-Z10 tiles to new per-layer encoding
#          (lossless WebP for water/boundaries/places; tuned-lossy for roads/labels).
# Stage 2: bake Z11 -> Z12 -> Z13 -> Z14 in one bake-tigerbase.py invocation
#          (one shapefile load, one process, --workers 4 = polite to the box).
#
# Refuses to start if any bake-tigerbase.py / recompress-existing.py is already running.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PY="$HERE/.venv/bin/python"
LOG="$HERE/out/resume-conus.log"
BBOX="-125.0,24.4,-66.9,49.4"
WORKERS=4

# Refuse to start if anything is already churning — prevents the doubled-up overload that hit S252.
if pgrep -f "bake-tigerbase\.py" > /dev/null || pgrep -f "recompress-existing\.py" > /dev/null; then
    echo "[resume] ABORT — bake or recompress already running. PIDs:"
    pgrep -af "bake-tigerbase\.py" || true
    pgrep -af "recompress-existing\.py" || true
    exit 1
fi

exec > >(tee -a "$LOG") 2>&1

echo "===================================================================="
echo "[resume] start: $(date -u +%FT%TZ)"
echo "[resume] workers=$WORKERS  bbox=$BBOX"
echo "===================================================================="

echo
echo "[resume] STAGE 1/2 — recompress Z3-Z10 to new per-layer encoding ..."
"$PY" "$HERE/scripts/recompress-existing.py" \
    --root "$HERE/out/conus" \
    --workers $WORKERS

echo
echo "[resume] STAGE 2/2 — bake Z11-Z14 ..."
"$PY" "$HERE/scripts/bake-tigerbase.py" \
    --bbox="$BBOX" \
    --zmin 11 --zmax 14 \
    --sources "$HERE/sources/" \
    --out "$HERE/out/conus/" \
    --layer all \
    --workers $WORKERS

echo "[resume] DONE: $(date -u +%FT%TZ)"
