#!/usr/bin/env bash
# Copy completed triptychs into the APK assets dir.
#
# Source : tools/hero-triptych/output-full/triptychs/*.webp
# Target : app-salem/src/main/assets/heroes/*.webp
#
# Filenames are salem_pois.id slugs (e.g. scarlet_letter_house.webp),
# matching what HeroAssetLoader.loadTriptych* / PoiHeroResolver Tier 0 expect.
# Idempotent: re-running only copies newer files (rsync -u).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
SRC="$SCRIPT_DIR/output-full/triptychs"
DST="$REPO_ROOT/app-salem/src/main/assets/heroes"

if [[ ! -d "$SRC" ]]; then
    echo "ERROR: source dir not found: $SRC" >&2
    exit 1
fi

mkdir -p "$DST"

src_count=$(find "$SRC" -maxdepth 1 -name '*.webp' | wc -l)
dst_before=$(find "$DST" -maxdepth 1 -name '*.webp' | wc -l)
src_bytes=$(du -sb "$SRC" | cut -f1)

echo "sync-to-apk: $src_count triptychs in source, $dst_before already in APK assets"
echo "sync-to-apk: copying ($(numfmt --to=iec $src_bytes) source)..."

rsync -a --update --include='*.webp' --exclude='*' "$SRC/" "$DST/"

dst_after=$(find "$DST" -maxdepth 1 -name '*.webp' | wc -l)
dst_bytes=$(du -sb "$DST" | cut -f1)

echo "sync-to-apk: done. APK heroes dir now has $dst_after files, $(numfmt --to=iec $dst_bytes)"
