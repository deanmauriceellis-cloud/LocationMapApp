#!/usr/bin/env bash
# chain-z12-z14.sh — wait for any in-flight Z11 bake, then bake Z12-Z14 sequentially.
# Estimated wall clock: ~22 hours at current 12-worker throughput.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PY="$HERE/.venv/bin/python"
LOG="$HERE/out/chain-z12-z14.log"
BBOX="-125.0,24.4,-66.9,49.4"

exec > >(tee -a "$LOG") 2>&1

echo "==================================================================="
echo "[chain] start: $(date -u +%FT%TZ)"

# Wait for any active bake-tigerbase.py (any zoom). 30s poll is fine; we're not waking up much.
while pgrep -f "bake-tigerbase\.py" > /dev/null 2>&1; do
    cnt=$(pgrep -f "bake-tigerbase\.py" | wc -l)
    echo "[chain] $(date -u +%FT%TZ) waiting on $cnt in-flight bake process(es)..."
    sleep 30
done
echo "[chain] no prior bake running; launching Z12-Z14"

"$PY" "$HERE/scripts/bake-tigerbase.py" \
    --bbox="$BBOX" \
    --zmin 12 --zmax 14 \
    --sources "$HERE/sources/" \
    --out "$HERE/out/conus/" \
    --layer all \
    --workers 12

echo "[chain] DONE: $(date -u +%FT%TZ)"
