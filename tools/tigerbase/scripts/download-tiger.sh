#!/usr/bin/env bash
# download-tiger.sh — pull TIGER/Line shapefiles from Census FTP
#
# Usage:
#   download-tiger.sh <FIPS>       # one state, e.g. 25 for Massachusetts
#   download-tiger.sh --all        # all 50 states (DC + PR optional)
#   download-tiger.sh --nation     # pull single-file national PLACE/COUNTY/STATE
#
# Output:
#   tools/tigerbase/sources/PRISECROADS/<FIPS>/...
#   tools/tigerbase/sources/PLACE/<FIPS>/...
#   tools/tigerbase/sources/national/{county,state}/...

set -euo pipefail

YEAR=2023
BASE="https://www2.census.gov/geo/tiger/TIGER${YEAR}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SRC="$(cd "$SCRIPT_DIR/.." && pwd)/sources"
mkdir -p "$SRC"

# CONUS state FIPS (excludes AK=02, HI=15, territories).
CONUS_FIPS=(01 04 05 06 08 09 10 11 12 13 16 17 18 19 20 21 22 23 24 25 26 27 28 29 30 31 32 33 34 35 36 37 38 39 40 41 42 44 45 46 47 48 49 50 51 53 54 55 56)

fetch_state() {
    local fips="$1"
    local roads_dir="$SRC/PRISECROADS/$fips"
    local place_dir="$SRC/PLACE/$fips"
    mkdir -p "$roads_dir" "$place_dir"

    local roads_zip="tl_${YEAR}_${fips}_prisecroads.zip"
    local place_zip="tl_${YEAR}_${fips}_place.zip"

    if [ ! -f "$roads_dir/$roads_zip" ]; then
        echo "  PRISECROADS state $fips ..."
        curl -fsSL -o "$roads_dir/$roads_zip" "$BASE/PRISECROADS/$roads_zip"
        (cd "$roads_dir" && unzip -oq "$roads_zip")
    else
        echo "  PRISECROADS state $fips already present, skip"
    fi

    if [ ! -f "$place_dir/$place_zip" ]; then
        echo "  PLACE       state $fips ..."
        curl -fsSL -o "$place_dir/$place_zip" "$BASE/PLACE/$place_zip"
        (cd "$place_dir" && unzip -oq "$place_zip")
    else
        echo "  PLACE       state $fips already present, skip"
    fi
}

fetch_nation() {
    local nat="$SRC/national"
    mkdir -p "$nat/county" "$nat/state" "$nat/coastline" "$nat/gazetteer"

    if [ ! -f "$nat/county/tl_${YEAR}_us_county.zip" ]; then
        echo "  national COUNTY ..."
        curl -fsSL -o "$nat/county/tl_${YEAR}_us_county.zip" "$BASE/COUNTY/tl_${YEAR}_us_county.zip"
        (cd "$nat/county" && unzip -oq "tl_${YEAR}_us_county.zip")
    fi

    if [ ! -f "$nat/state/tl_${YEAR}_us_state.zip" ]; then
        echo "  national STATE ..."
        curl -fsSL -o "$nat/state/tl_${YEAR}_us_state.zip" "$BASE/STATE/tl_${YEAR}_us_state.zip"
        (cd "$nat/state" && unzip -oq "tl_${YEAR}_us_state.zip")
    fi

    if [ ! -f "$nat/coastline/tl_${YEAR}_us_coastline.zip" ]; then
        echo "  national COASTLINE ..."
        curl -fsSL -o "$nat/coastline/tl_${YEAR}_us_coastline.zip" "$BASE/COASTLINE/tl_${YEAR}_us_coastline.zip"
        (cd "$nat/coastline" && unzip -oq "tl_${YEAR}_us_coastline.zip")
    fi

    # Census Gazetteer 2023: per-place population (NAME, GEOID, POP, etc.) — used for label filtering.
    if [ ! -f "$nat/gazetteer/2023_Gaz_place_national.txt" ]; then
        echo "  national PLACE gazetteer ..."
        curl -fsSL -o "$nat/gazetteer/2023_Gaz_place_national.zip" \
            "https://www2.census.gov/geo/docs/maps-data/data/gazetteer/${YEAR}_Gazetteer/${YEAR}_Gaz_place_national.zip"
        (cd "$nat/gazetteer" && unzip -oq "${YEAR}_Gaz_place_national.zip")
    fi
}

case "${1:-}" in
    --all)
        fetch_nation
        for f in "${CONUS_FIPS[@]}"; do fetch_state "$f"; done
        ;;
    --nation)
        fetch_nation
        ;;
    "")
        echo "usage: download-tiger.sh <FIPS> | --all | --nation" >&2
        exit 2
        ;;
    *)
        fetch_nation
        fetch_state "$1"
        ;;
esac

echo "done. sources at: $SRC"
