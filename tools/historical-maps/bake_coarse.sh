#!/bin/bash
# S286 Phase 3 — coarse 4-corner georef + bake for 3 historical maps.
# Pairs each source image's content-rectangle with a Salem bbox approximation.
# Bbox: N=42.555 S=42.495 W=-70.965 E=-70.845 (~6.7km E-W × 6.6km N-S).
# Run from repo root.
set -euo pipefail

ROOT=/home/witchdoctor/Development/LocationMapApp_v1.5
NORTH=42.555
SOUTH=42.495
WEST=-70.965
EAST=-70.845

bake_one() {
  local year=$1
  local src=$2
  local px_x0=$3 px_y0=$4 px_x1=$5 px_y1=$6
  local dir=$ROOT/tools/historical-maps/$year
  echo "==== $year — $src — content px ($px_x0,$px_y0)→($px_x1,$px_y1)"

  cd "$dir"
  rm -rf translated.tif warped_3857.tif tiles tiles_v1.mbtiles 2>/dev/null || true

  # 4 corner GCPs: top-left → NW, top-right → NE, bottom-right → SE, bottom-left → SW
  gdal_translate -of GTiff -a_srs EPSG:4326 \
    -gcp $px_x0 $px_y0 $WEST  $NORTH \
    -gcp $px_x1 $px_y0 $EAST  $NORTH \
    -gcp $px_x1 $px_y1 $EAST  $SOUTH \
    -gcp $px_x0 $px_y1 $WEST  $SOUTH \
    "$src" translated.tif 2>&1 | tail -2

  # TPS warp into Web Mercator with alpha
  gdalwarp -tps -t_srs EPSG:3857 -r bilinear -dstalpha \
    -overwrite translated.tif warped_3857.tif 2>&1 | tail -3

  # XYZ tiles z14-z19
  gdal2tiles.py -z 14-19 -r bilinear --xyz -q warped_3857.tif tiles 2>&1 | tail -3

  # Pack to MBTiles
  python3 $ROOT/tools/historical-maps/xyz_to_mbtiles.py tiles ${year}.mbtiles 2>&1 | tail -1

  # Merge into salem_tiles.sqlite
  node $ROOT/tools/historical-maps/merge-historical-into-bundle.js \
    --year $year \
    --source-mbtiles "$dir/${year}.mbtiles" \
    --bundle $ROOT/tools/tile-bake/dist/salem_tiles.sqlite 2>&1 | tail -5

  echo "==== $year done"
}

# Hopkins 1874 — 8836×5619 landscape. Map content fills most of image; small
# paper border ~3% inset.
bake_one 1874 "$ROOT/tools/historical-maps/1874/hopkins_1874_plate05.jpg" \
  265 168 8571 5450

# Sanborn 1906 — 6745×7913 portrait. Map content lives in right portion;
# key block + legend takes ~20% on the left. Inset more aggressively.
bake_one 1906 "$ROOT/tools/historical-maps/1906/sanborn_1906_p002.jpg" \
  1350 240 6480 7670

# Walker 1911 — 10492×7030 landscape. Map content fills most of image.
bake_one 1911 "$ROOT/tools/historical-maps/1911/walker_1911_plate05.jpg" \
  314 210 10178 6820

# Sync back into the asset pack
cp "$ROOT/tools/tile-bake/dist/salem_tiles.sqlite" \
   "$ROOT/app-salem-tiles-pack/src/main/assets/salem_tiles.sqlite"

echo "All 3 maps baked + asset pack synced."
ls -lh "$ROOT/tools/tile-bake/dist/salem_tiles.sqlite"
