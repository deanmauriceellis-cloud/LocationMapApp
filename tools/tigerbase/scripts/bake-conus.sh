#!/usr/bin/env bash
# bake-conus.sh — end-to-end CONUS Z6-Z14 bake.
#
# Stages:
#   1. Pull all CONUS state shapefiles + national boundaries + coastline + gazetteer.
#   2. Render all 5 layers (water / boundaries / roads / places / labels) Z6-Z14 to out/conus/.
#   3. Pack each layer to a single PMTiles archive at out/conus.<layer>.pmtiles.
#
# Logs all output to out/conus-bake.log. Designed to be launched in background.

set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG="$HERE/out/conus-bake.log"
PY="$HERE/.venv/bin/python"
BBOX="-125.0,24.4,-66.9,49.4"
ZMIN=6
ZMAX=14
SCOPE="conus"

mkdir -p "$HERE/out"
exec > >(tee -a "$LOG") 2>&1

echo "===================================================================="
echo "[bake-conus] start: $(date -u +%FT%TZ)"
echo "[bake-conus] bbox=$BBOX  zmin=$ZMIN  zmax=$ZMAX  scope=$SCOPE"
echo "===================================================================="

echo
echo "[bake-conus] STAGE 1/3 — downloading TIGER shapefiles ..."
bash "$HERE/scripts/download-tiger.sh" --all

echo
echo "[bake-conus] STAGE 2/3 — rendering all layers (Z$ZMIN-Z$ZMAX) ..."
"$PY" "$HERE/scripts/bake-tigerbase.py" \
    --bbox="$BBOX" \
    --zmin "$ZMIN" --zmax "$ZMAX" \
    --sources "$HERE/sources/" \
    --out "$HERE/out/$SCOPE/" \
    --layer all

echo
echo "[bake-conus] STAGE 3/3 — packing PMTiles archives ..."
for layer in water boundaries roads places labels; do
    in_dir="$HERE/out/$SCOPE/$layer"
    out_file="$HERE/out/${SCOPE}.${layer}.pmtiles"
    if [ ! -d "$in_dir" ]; then
        echo "[bake-conus] WARN: layer '$layer' has no output dir, skip"
        continue
    fi
    "$PY" "$HERE/scripts/pack-pmtiles.py" \
        --in "$in_dir" \
        --out "$out_file" \
        --name "tigerbase-${SCOPE}-${layer}" \
        --bbox="$BBOX" \
        --minzoom "$ZMIN" --maxzoom "$ZMAX"
done

echo
echo "[bake-conus] DONE: $(date -u +%FT%TZ)"
ls -lh "$HERE/out/"${SCOPE}.*.pmtiles 2>/dev/null || true
