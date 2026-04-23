# Salem Custom Tile Baking Pipeline

Bakes the "Witchy" raster basemap tiles that ship in
`app-salem/src/main/assets/salem_tiles.sqlite` under provider name
`Salem-Custom`. Built on top of MassGIS data + OSM (via OpenMapTiles) +
MHC Inventory historic designations.

## Pipeline overview

```
┌──────────────────────────────┐   planetiler
│ Geofabrik MA OSM extract     │ ────────────► salem-vector.mbtiles
│ (downloaded into data/)      │                (OSM roads, labels, water,
└──────────────────────────────┘                 landuse, parks — z0-z16)

┌──────────────────────────────┐   ogr2ogr + tippecanoe
│ massgis.l3_parcels_essex     │ ────────────► parcels.mbtiles
│ (PostGIS, Salem bbox clip)   │                (parcel outlines — z14-z16)
└──────────────────────────────┘

┌──────────────────────────────┐   psql JSON export + tippecanoe
│ massgis.structures           │ ────────────► buildings.mbtiles
│  JOIN mhc_inventory          │                (building footprints w/
│  (MHC designation → tier)    │                 NHL/NRIND/NRDIS tier tag)
└──────────────────────────────┘

         ┌──────────────────────┐
         │ style-salem.json     │  curated MapLibre style
         │ (parchment palette,  │  (no commercial POI layer,
         │  purple historic     │   witchy typography)
         │  tier overlays)      │
         └──────────┬───────────┘
                    │
                    ▼
         maplibre-gl-native
         (node headless render)
                    │
                    ▼
         salem-custom.mbtiles   WebP lossless, z14-z18 full Salem + z19 downtown
                    │
                    ▼
         merge-into-bundle.js
                    │
                    ▼
         salem_tiles.sqlite     provider='Salem-Custom' (osmdroid-compatible)
```

## Outputs by zoom

| Zoom | Bbox | Tile count | Use |
|---|---|---:|---|
| z14 | full Salem | 35 | city-overview zoom-out |
| z15 | full Salem | 120 | neighborhoods |
| z16 | full Salem | 437 | downtown context |
| z17 | full Salem | 1,656 | street-level |
| z18 | full Salem | 6,461 | building-level |
| z19 | downtown only | 1,935 | tight walking-tour zoom |
| **Total** | | **10,644** | |

## Scripts

- `render-tiles.js` — main render. Reads `style-salem.json` + vector sources, produces `salem-custom.mbtiles` (WebP lossless). Buffered-block rendering: 6×6 tile blocks rendered as one 1536×1536 viewport, center 4×4 tiles sliced — eliminates tile seams and label fragmentation.
- `render-preview-wide.js` — 4 eyeball previews at different zooms (uncropped single-viewport renders).
- `render-preview.js` / `render-cemetery-preview.js` — smaller targeted previews.
- `extract-osm-buildings.js` — enumerates OSM building polygons from the vector tiles (used before we pivoted to MassGIS structures; kept for reference).
- `merge-into-bundle.js` — merges `salem-custom.mbtiles` into `app-salem/src/main/assets/salem_tiles.sqlite` under provider name `Salem-Custom`. Preserves Esri-WorldImagery + Mapnik rows. Uses osmdroid's `MapTileIndex` key encoding + TMS-to-XYZ y-flip.
- `stitch-big.js` / `stitch-test.js` / `sample-extract.js` — debugging tools for inspecting bundled tile quality.

## Rebuild from scratch

```bash
cd tools/tile-bake
npm install                                  # node deps (gitignored)
curl -L -o planetiler.jar https://github.com/onthegomap/planetiler/releases/latest/download/planetiler.jar

# 1. Vector tiles from OSM
java -Xmx4g -jar planetiler.jar \
  --area=massachusetts \
  --bounds=-70.958,42.475,-70.835,42.545 \
  --minzoom=0 --maxzoom=16 --render_maxzoom=16 \
  --force --output=salem-vector.mbtiles --download

# 2. Parcels from MassGIS
psql -d tiger -A -t -c "SELECT json_build_object('type','Feature', 'geometry',ST_AsGeoJSON(ST_Transform(geom,4326),6)::json, 'properties',json_build_object('loc_id',loc_id))::text FROM massgis.l3_parcels_essex WHERE ST_Intersects(geom,ST_MakeEnvelope(-70.958,42.475,-70.835,42.545,4269));" > salem-parcels.geojsonl
tippecanoe -o parcels.mbtiles -Z 14 -z 16 --layer=parcels --force --drop-densest-as-needed --extend-zooms-if-still-dropping salem-parcels.geojsonl

# 3. Buildings from MassGIS + MHC historic tier
psql -d tiger -A -t -f build-buildings-geojson.sql > salem-buildings.geojsonl   # query in this README
tippecanoe -o buildings.mbtiles -Z 14 -z 16 --layer=buildings --force --drop-densest-as-needed --extend-zooms-if-still-dropping salem-buildings.geojsonl

# 4. Fonts (one-time)
mkdir fonts && cd fonts
curl -L -o v2.0.zip https://github.com/openmaptiles/fonts/releases/download/v2.0/v2.0.zip
unzip -q v2.0.zip && rm v2.0.zip
cd ..

# 5. Render + merge
node render-tiles.js
node merge-into-bundle.js
```

## Buildings query (for step 3 above)

```sql
WITH mhc_tier AS (
  SELECT s.gid AS bld_gid, s.geom,
    CASE
      WHEN string_agg(mhc.designatio,' ') ~ 'NHL'   THEN 'nhl'
      WHEN string_agg(mhc.designatio,' ') ~ 'NRIND' THEN 'nrind'
      WHEN string_agg(mhc.designatio,' ') ~ '(NRDIS|NRMRA|NRDOE|NRTRA|LHD)' THEN 'nrdis'
      ELSE 'none'
    END AS tier
  FROM massgis.structures s
  LEFT JOIN massgis.mhc_inventory mhc
    ON mhc.town_name='Salem'
   AND mhc.designatio ~ '(NHL|NRDIS|NRIND|NRMRA|NRDOE|NRTRA|LHD)'
   AND ST_Contains(s.geom, mhc.geom)
  WHERE ST_Intersects(s.geom, ST_MakeEnvelope(-70.958,42.475,-70.835,42.545,4269))
  GROUP BY s.gid, s.geom
)
SELECT json_build_object(
  'type','Feature',
  'geometry', ST_AsGeoJSON(ST_Transform(geom,4326),6)::json,
  'properties', json_build_object('tier', tier)
)::text
FROM mhc_tier;
```

## Style notes

- **Palette:** parchment (#F3E9D2) background, warm brown road casings, sage park greens, muted slate cemetery fills.
- **No commercial POI labels** — the `poi` vector layer is entirely omitted. Commercial amenities (shops, restaurants, banks, parking) don't appear.
- **Narrow POI carve-out** — cemetery, monument, and place_of_worship classes ARE labeled for 1692-era historical relevance.
- **Historic building tiers** — NHL (deep purple `#4B1D7A`), NRIND (medium purple `#7B4FA0`), NRDIS/LHD (lavender `#B799C7`), undesignated (cream `#D7C9A8`).
- **Parcels** visible at z16+ as thin muted-pink lines (`#B8847A`).
- **Typography** — Metropolis Bold (district/town labels, uppercase tracked), Noto Sans Bold (major streets), Noto Sans Regular (minor streets), Metropolis Medium Italic (cemeteries, churches, monuments, parks).

## Data vintages

- OSM: 2026-04-22 snapshot (Geofabrik MA extract, downloaded at bake time).
- MassGIS structures / L3 parcels / MHC Inventory: local PostGIS tables (see `tiger` DB).
- Attribution required by OpenMapTiles CC-BY: "© OpenMapTiles © OpenStreetMap contributors".
