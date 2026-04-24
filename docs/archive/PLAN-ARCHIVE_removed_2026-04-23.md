# LocationMapApp — Geofence Alert System Plan (COMPLETED)

## Origin
Planned in session `54ff08bd` (2026-03-01 23:14). Research covered 40+ data sources, JTS spatial indexing, alert severity levels, database distribution formats, and UI concepts. Full research report is in that session's JSONL.

## Status: COMPLETED (v1.5.35–v1.5.39)

All planned phases delivered. The geofence system provides a JTS R-tree spatial engine, 5 live zone types with map overlays, 4 pre-built downloadable databases (220k+ zones), and user import/export of custom databases. Phase 5 (Advanced Sources) was evaluated and deferred — the current source coverage is sufficient and adding more would be complexity without clear user need.

## Final Phase Summary

### Phase 1 — Core Engine + TFRs (v1.5.35)
- JTS `jts-core:1.19.0` R-tree spatial engine (`GeofenceEngine.kt`)
- FAA TFR scraping via proxy `/tfrs?bbox=` (cheerio + fast-xml-parser)
- Semi-transparent red TFR polygon overlays on map
- `checkPosition(lat, lon, altFt?, bearing?)` — entry (CRITICAL), proximity (WARNING, 5nm + bearing ±60°), exit (INFO)
- Cooldowns: 5min proximity, 10min entry
- Severity-colored Alerts toolbar icon (gray → blue → yellow → red → pulsing)
- Two-row toolbar layout (10 icons in 2×5 grid)
- GPS integration: user position + followed aircraft checked against geofences
- Debug endpoints: `/geofences`, `/geofences/alerts`
- Alert Sound toggle (stub — pref exists, no playback), Alert Distance pref

### Phase 2 — Additional Zone Types (v1.5.36)
- 4 new zone types all feeding into same GeofenceEngine spatial index:
  - **Speed Cameras**: Overpass `highway=speed_camera`, 200m alert circle, orange overlays, 24h cache
  - **School Zones**: Overpass `amenity=school`, polygon or 300m circle, amber overlays, 24h cache, weekday 7-9AM/2-4PM filter
  - **Flood Zones**: FEMA NFHL ArcGIS Layer 28, high-risk SFHA, blue overlays, 30-day cache
  - **Railroad Crossings**: Overpass `railway=level_crossing`, 100m circle, dark+yellow overlays, 7-day cache
- `ZoneType` enum, `GeofenceRepository.kt`, per-type LiveData, `rebuildGeofenceIndex()`
- 4 proxy endpoints: `/cameras`, `/schools`, `/flood-zones`, `/crossings`
- Zone-type-aware UI: detail dialog adapts color bar + metadata; alert banner color per type
- Zoom guards: cameras ≥10, schools/flood/crossings ≥12
- Alerts menu: 4 new toggles (all default OFF)

### Phase 3A — Downloadable Databases: Foundation + Military Bases (v1.5.37)
- **SQLite database format**: `zones` table (geometry JSON, bbox columns, severity, metadata), `db_meta` table (version, source, license)
- **`GeofenceDatabaseRepository.kt`**: catalog fetch, streaming download with progress, SQLite bbox queries, installed database management
- **Proxy catalog + download endpoints**: `GET /geofences/catalog`, `GET /geofences/database/:id/download`
- **Military Bases database**: 824 MIRTA/NTAD features → 1,944 polygon zones, 27MB SQLite
- **Database Manager dialog**: Alerts menu → "Zone Databases…" → installed/available sections with download/delete
- **New ZoneTypes**: `MILITARY_BASE`, `NO_FLY_ZONE`, `CUSTOM`
- Green polygon overlays for military bases, purple for no-fly, gray for custom

### Phase 3B — Additional Database Builders (v1.5.38)
- **Speed & Red-Light Cameras** (`excam-cameras.db`): 109,500 worldwide cameras from WzSabre/ExCam
- **US Public Schools** (`nces-schools.db`): 101,390 K-12 schools from NCES EDGE ArcGIS
- **DJI No-Fly Zones** (`dji-nofly.db`): 7,823 drone restriction zones from DJI NFZDB
- Build scripts: `build-excam.js`, `build-nces.js`, `build-dji-nofly.js`
- 4 databases totaling 220,657 zones

### Phase 4 — Database Import & Export (v1.5.39)
- **Import SQLite .db**: SAF file picker → schema validation → install with duplicate detection
- **Import CSV**: SAF file picker → config dialog (name, zone type, radius) → parsed with column aliases → converted to SQLite
- **Export**: installed databases shareable via FileProvider + Android share intent
- **Local-only databases**: catalog merges locally-imported DBs not in remote catalog; works offline
- Database Manager UI: IMPORT .DB / IMPORT CSV buttons, EXPORT button on installed cards

### Phase 5 — Advanced Sources (DEFERRED)
Evaluated and deferred. Candidate sources (bridge weight limits, EPA facilities, NPS/BLM boundaries, real-time earthquakes/wildfires/AQI, commercial APIs) add complexity without clear immediate need. The existing 5 live zone types + 4 downloadable databases + user import/export provide comprehensive coverage. These sources can be revisited as individual features if a specific need arises.

## Alert Severity Levels
| Level | Color | Audio | Example |
|-------|-------|-------|---------|
| INFO (0) | Blue | None | Entering National Park |
| WARNING (1) | Yellow | Short tone | School zone ahead |
| CRITICAL (2) | Red | Loud alert + vibration | Speed camera, TFR violation |
| EMERGENCY (3) | Pulsing red | Continuous alarm + TTS | Wrong-way, active fire zone |

## Key Architecture Decisions
- **JTS R-tree**: 10k polygons = <2ms per GPS update; 100k zones <10ms
- **SQLite for distributable databases** (not SpatiaLite — too heavy): circles via center+radius columns, polygons via JSON/WKT text column, build JTS R-tree in memory at load
- **Proxy serves catalog + downloads**: databases cached/distributed through existing proxy infrastructure
