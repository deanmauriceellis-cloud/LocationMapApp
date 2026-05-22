#!/bin/bash
# points_to_gdal_translate.sh — convert a QGIS Georeferencer .points file into
# a gdal_translate -gcp invocation that embeds the GCPs into a GeoTIFF.
#
# Usage:
#   points_to_gdal_translate.sh <input_image> <points_file> <output_tif>
#
# Reads .points CSV header: mapX,mapY,pixelX,pixelY,enable,dX,dY,residual
# Emits one -gcp pixelX pixelY mapX mapY per enabled (enable=1) row.
# Target CRS is EPSG:4326 (lon/lat) — matches QGIS Step 3 setting in the guide.
set -euo pipefail

if [ "$#" -ne 3 ]; then
  echo "usage: $0 <input_image.png> <points_file> <output.tif>" >&2
  exit 2
fi

IN_IMG=$1
POINTS=$2
OUT_TIF=$3

[ -f "$IN_IMG" ] || { echo "missing input image: $IN_IMG" >&2; exit 1; }
[ -f "$POINTS" ] || { echo "missing points file: $POINTS" >&2; exit 1; }

# Build -gcp args from the .points file. Skip header line; skip enable=0 rows.
# QGIS columns: mapX, mapY, pixelX, pixelY, enable, dX, dY, residual
# Pixel Y in QGIS Georeferencer is NEGATIVE for image-space rows (rows go down
# from top, but Georeferencer represents image as bottom-up internally). We
# need to flip to positive pixel-Y for gdal_translate, which counts rows from
# top-left.
GCPS=$(awk -F',' '
  NR == 1 { next }                            # header
  NF < 5 { next }                              # malformed
  $5 != "1" { next }                           # disabled
  {
    pixel_x = $3
    pixel_y = $4
    if (pixel_y < 0) pixel_y = -pixel_y       # flip QGIS negative pixel-Y
    printf " -gcp %s %s %s %s", pixel_x, pixel_y, $1, $2
  }
' "$POINTS")

GCP_COUNT=$(echo "$GCPS" | grep -o "\-gcp" | wc -l)
echo "==== $GCP_COUNT enabled GCPs parsed from $POINTS"
[ "$GCP_COUNT" -lt 4 ] && { echo "FATAL: need at least 4 GCPs" >&2; exit 1; }

echo "==== Running gdal_translate → $OUT_TIF"
# shellcheck disable=SC2086
gdal_translate -of GTiff -a_srs EPSG:4326 $GCPS "$IN_IMG" "$OUT_TIF" 2>&1 | tail -5

echo "==== done. GCPs embedded; next step: gdalwarp -tps -t_srs EPSG:3857"
