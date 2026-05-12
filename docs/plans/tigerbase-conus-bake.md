# TigerBase — CONUS basemap (Z6-Z14)

**Status:** post-V1, lawyer-gated fallback feature. Not on V1 ship path.
**Owner:** operator + Claude.
**Started:** 2026-05-12 (S252).
**Touches Salem bake?** No — entirely separate path under `tools/tigerbase/`. Salem at `tools/tile-bake/` is not read or written by any TigerBase script.

## Goal

When LMA has internet and the user is outside the Salem geofence, serve a simple roads-and-cities basemap of the entire US so the map isn't blank. Source is TIGER/Line (US Census, public-domain) — operator-confirmed in-house, no Mapnik, no OSM toolchain.

## Scope (operator-confirmed)

- **Zoom:** Z6–Z14. TigerLine has no buildings or parcels; above Z14 just fatter lines on empty space.
- **Coverage:** CONUS (lower 48). Alaska, Hawaii, territories deferred.
- **Style:** thin grey roads (primary thicker), city dots + labels scaled by population, faint county lines below Z10.
- **Output:** PMTiles archive (~11 GB WebP target).
- **Serving:** cache-proxy adds `/tigerbase/{z}/{x}/{y}.webp` route reading from PMTiles. Same auto-overzoom pattern as Witchy when z>14.

## Source data

Pulled directly from `https://www2.census.gov/geo/tiger/TIGER2023/`:
- **PRISECROADS** — primary + secondary highways, ~56 state shapefiles
- **PLACE** — incorporated places (cities/towns) as polygons; we use centroids
- **COUNTY** — county boundaries (single national file)
- **STATE** — state boundaries (single national file)

Total raw: ~2-3 GB. Stored at `tools/tigerbase/sources/`, gitignored.

## Pipeline

1. `scripts/download-tiger.sh <state-fips>` — pulls one state's shapefiles. `--all` for full CONUS.
2. `scripts/bake-tigerbase.py --bbox <bbox> --zmin 6 --zmax 14 --out out/<name>/` — renders tile pyramid as WebP files.
3. `scripts/pack-pmtiles.py out/<name>/ out/<name>.pmtiles` — packs WebP tree into PMTiles archive.
4. cache-proxy route (post-pilot, separate work).

## Pilot

Massachusetts only at Z6-Z12 first. Validates renderer style, measures actual tile size + render rate, then operator signs off before CONUS commit.

## Estimated cost (from earlier sizing pass)

| Zoom | CONUS land tiles | WebP ~3 KB |
|---|---:|---:|
| Z6  | ~120 | 0.4 MB |
| Z8  | ~2K | 6 MB |
| Z10 | ~32K | 100 MB |
| Z12 | ~500K | 1.5 GB |
| Z13 | ~1M | 3 GB |
| Z14 | ~2M | 6 GB |
| **Total** | **~3.5M** | **~11 GB** |

8-core CPU bake ~2 hours.

## Standing rules respected

- **`feedback_no_osm_use_local_geo.md`** — TIGER + MassGIS preferred. This bake uses TIGER only. No OSM input.
- **`feedback_v1_no_external_contact.md`** — Tile fetch is post-V1 only. Android does NOT call this route in V1 builds. Gating happens via SuperAdmin or a future opt-in flag.
- **`project_witchy_tile_bake.md`** + **`project_basemap_wickedmapview_only.md`** — Witchy stays the V1 Salem-only basemap. TigerBase is a separate post-V1 fallback for "user outside Salem with internet."
- **`feedback_leverage_existing_assets.md`** — cache-proxy already does auto-overzoom and PMTiles-style range serving for Witchy. Reuse that pattern, do not build a parallel server stack.
