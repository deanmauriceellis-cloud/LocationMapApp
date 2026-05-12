# TigerBase — CONUS basemap bake

In-house pipeline: TIGER/Line shapefiles → Pillow render → WebP tiles → PMTiles.

**Untouched neighbors:** `tools/tile-bake/` (Salem Witchy) is not read or written by anything here.

See `docs/plans/tigerbase-conus-bake.md` for scope.

## Quick start (pilot — Massachusetts only)

```bash
cd tools/tigerbase
.venv/bin/python --version              # 3.12

# 1. Pull Massachusetts shapefiles (FIPS 25)
scripts/download-tiger.sh 25

# 2. Bake Z6-Z12 for MA bbox
.venv/bin/python scripts/bake-tigerbase.py \
    --bbox '-73.5,41.2,-69.9,42.9' \
    --zmin 6 --zmax 12 \
    --sources sources/ \
    --out out/ma-pilot/

# 3. Eyeball a few tiles
xdg-open out/ma-pilot/10/318/385.webp     # Boston metro at Z10
```

## Layout

```
tools/tigerbase/
├── .venv/              # Python 3.12 venv (geopandas, shapely, pillow, mercantile, pmtiles)
├── scripts/
│   ├── download-tiger.sh
│   ├── bake-tigerbase.py
│   └── pack-pmtiles.py    (post-pilot)
├── style/
│   └── tigerbase.json     # render style config
├── sources/            # TIGER shapefiles (gitignored)
└── out/                # baked tiles + PMTiles archives (gitignored)
```
