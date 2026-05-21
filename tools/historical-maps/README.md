# tools/historical-maps/

Per-year historical-map bake pipeline. Output: tile rows in `salem_tiles.sqlite`
with `provider='Historical-YYYY'`, consumed at runtime by `HistoricalTileOverlay`.

Plan: `~/.claude/plans/groovy-launching-popcorn.md` (S285).

## Year scope (V1)

| Year | Source | Status |
|------|--------|--------|
| 1851 | McIntyre city map (BPL Leventhal IIIF, PD) | POC complete S285; Phase 1 bake target |
| 1874 | Hopkins atlas plate 02 (BPL Leventhal IIIF, PD) | Phase 3 |
| 1906 | Sanborn fire insurance (80 sheets, LoC, PD) | Phase 3 — mosaic, wildcard effort |
| 1911 | Walker atlas plate 02 (BPL Leventhal IIIF, PD) | Phase 3 |

## Bake pipeline (per year)

1. **Source** — raw JPG/TIF at `/mnt/sdb-images/HistoricalMapsSample/` (S285 downloads).
2. **Paper transparency** — `convert in.jpg -fuzz N% -transparent "rgb(...)" -alpha extract -threshold 50%`
   then merge as alpha channel. Per-map tuning; preserve ink, drop paper.
3. **GCP picking** — QGIS Georeferencer (precise) or browser-based Allmaps editor.
   Target 10-15 GCPs distributed across the bbox, anchored on surviving landmarks
   (Salem Common corners, Charter St Cemetery, Witch House, Custom House, First
   Church, Pickering House, Fort Pickering, Harmony Grove, Gallows Hill, North
   River bridge). Save to `.points` file.
4. **Warp** — `gdal_translate -gcp ... -a_srs EPSG:4326 in.tif translated.tif`
   then `gdalwarp -tps -t_srs EPSG:3857 -r bilinear -dstalpha translated.tif out_3857.tif`.
5. **Tile** — `gdal2tiles.py -z 14-19 -r bilinear --xyz out_3857.tif tiles/`
   produces XYZ tile pyramid (standard Google scheme, matches `MercatorMath.kt`).
   Optionally `gdal_translate -of MBTILES tiles/ → year.mbtiles` for portability.
6. **Merge into bundle** — `node merge-historical-into-bundle.js --year YYYY
   --source-mbtiles /path/to/year.mbtiles` inserts as `provider='Historical-YYYY'`
   into `tools/tile-bake/dist/salem_tiles.sqlite` (the asset-pack source).

## Files in this directory

- `README.md` — this file
- `merge-historical-into-bundle.js` — parametrized clone of
  `tools/tile-bake/merge-into-bundle.js`. Reuses `osmdroidKey(z,x,y)` BigInt
  encoding + TMS→XYZ flip. Inserts under `provider='Historical-YYYY'` (idempotent
  via DELETE-then-INSERT on the same provider).
- `1851/`, `1874/`, `1906/`, `1911/` — per-year sources + intermediates
  (gitignored; staging space only). POC artifacts for 1851 currently live at
  `/mnt/sdb-images/HistoricalMapsSample/poc_1851/` from S285.

## Runtime contract

- `salem_tiles.sqlite` schema: `tiles(key INTEGER, provider TEXT, tile BLOB,
  PRIMARY KEY(key, provider))`. Multi-provider already supported by the existing
  modern basemap workflow.
- `osmdroidKey(z, x, y)` encoding: `(((z) << (z+z)) | (x << z) | y)` as a 64-bit
  signed long. SQLite stores as INTEGER.
- Tile bytes: per-tile PNG/WebP blobs. PNG with alpha channel preferred for
  historical overlays since paper transparency carries through.
- Reader: `TileArchive(file, "Historical-YYYY")` instantiates a new archive with
  its own LruCache and `missing` set; bind to `HistoricalTileOverlay`.
