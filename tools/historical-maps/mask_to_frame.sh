#!/bin/bash
# mask_to_frame.sh — paint everything outside a drawn-frame rect to alpha=0.
# Reusable across historical maps (1692, 1700, 1911 ...) to strip archive labels,
# handwritten marks, legends, and captions in one shot.
#
# Usage:
#   mask_to_frame.sh <input> <output.png> <x0> <y0> <x1> <y1>
#
# Notes:
#   - Coordinates are the drawn frame's outer rectangle in pixel space.
#   - Output is RGBA PNG; outside-rect pixels have alpha=0.
#   - Does NOT touch content inside the frame; insets or callouts overlapping
#     the main content need a separate hand-edit pass (GIMP).
set -euo pipefail

if [ "$#" -ne 6 ]; then
  echo "usage: $0 <input> <output.png> <x0> <y0> <x1> <y1>" >&2
  exit 2
fi

IN=$1
OUT=$2
X0=$3
Y0=$4
X1=$5
Y1=$6

if [ ! -f "$IN" ]; then
  echo "input not found: $IN" >&2
  exit 1
fi

# Build a black/white mask where the frame rect is white, everything else black,
# then copy that mask into the source image's alpha channel.
convert "$IN" \
  \( +clone -fill black -colorize 100 \
     -fill white -draw "rectangle ${X0},${Y0} ${X1},${Y1}" \
  \) \
  -alpha off -compose CopyOpacity -composite \
  "$OUT"

echo "wrote $OUT"
identify "$OUT" | head -1
