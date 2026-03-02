# LocationMapApp — Active Plan: Geofence Alert System

## Origin
Planned in session `54ff08bd` (2026-03-01 23:14). Research covered 40+ data sources, JTS spatial indexing, alert severity levels, database distribution formats, and UI concepts. Full research report is in that session's JSONL.

## 5-Phase Roadmap

### Phase 1 — Core Engine + TFRs (DONE v1.5.35)
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

### Phase 2 — Additional Zone Types (DONE v1.5.36)
- Original plan called this "Real-Time Alert Sources" (earthquakes, wildfires, AQI)
- User chose "Additional Zone Types" scope instead
- 4 new zone types all feeding into same GeofenceEngine spatial index:
  - **Speed Cameras**: Overpass `highway=speed_camera`, 200m alert circle, orange overlays, 24h cache
  - **School Zones**: Overpass `amenity=school`, polygon or 300m circle, amber overlays, 24h cache, weekday 7-9AM/2-4PM filter
  - **Flood Zones**: FEMA NFHL ArcGIS Layer 28, high-risk SFHA, blue overlays, 30-day cache
  - **Railroad Crossings**: Overpass `railway=level_crossing`, 100m circle, dark+yellow overlays, 7-day cache
- `ZoneType` enum, `GeofenceRepository.kt` (NEW), per-type LiveData, `rebuildGeofenceIndex()`
- 4 proxy endpoints: `/cameras`, `/schools`, `/flood-zones`, `/crossings`
- Zone-type-aware UI: detail dialog adapts color bar + metadata; alert banner color per type
- Zoom guards: cameras ≥10, schools/flood/crossings ≥12
- Alerts menu: 4 new toggles (all default OFF)

### Phase 3A — Downloadable Databases: Foundation + Military Bases (DONE v1.5.37)
- **SQLite database format**: `zones` table (geometry JSON, bbox columns, severity, metadata), `db_meta` table (version, source, license)
- **`GeofenceDatabaseRepository.kt`** (NEW): catalog fetch, streaming download with progress, SQLite bbox queries, installed database management
- **Proxy catalog + download endpoints**:
  - `GET /geofences/catalog` — available database catalog with actual file sizes
  - `GET /geofences/database/:id/download` — streams `.db` file with Content-Disposition
- **Military Bases database**: 824 MIRTA/NTAD features → 1944 polygon zones (multi-polygons split), 27MB SQLite
  - Source: ArcGIS NTAD Military Bases feature service (HIFLD/data.gov, Public Domain)
  - `build-military.js` — downloads GeoJSON from ArcGIS, builds SQLite with bbox index
- **Database Manager dialog**: Alerts menu → "Zone Databases…" → dark 90%×85% dialog
  - Installed section (name, stats, UP TO DATE/UPDATE/DELETE buttons)
  - Available section (name, description, stats, DOWNLOAD button with progress)
- **New ZoneTypes**: `MILITARY_BASE`, `NO_FLY_ZONE`, `CUSTOM` added to enum
- **GeofenceDatabaseInfo** data class for catalog entries
- **Green polygon overlays** for military bases, purple for no-fly, gray for custom
- **All `when` blocks** updated: severity mapping, detail dialog colors, alert banner labels/colors
- **ViewModel**: `_databaseZones` LiveData, `loadDatabaseZonesForVisibleArea()`, `fetchGeofenceCatalog()`, `downloadGeofenceDatabase()`, `deleteGeofenceDatabase()`, `hasInstalledDatabases()`
- **Viewport reload**: database zones reload on scroll/zoom via `scheduleGeofenceReload()`
- **Debug state**: `databaseCount` + `database` overlay count in `/geofences` endpoint
- Storage: `filesDir/geofence_databases/{id}.db`

### Phase 3B — Additional Database Builders (NEXT)

#### Key Data Sources for Phase 3
| Source | Data | Format | Access |
|--------|------|--------|--------|
| WzSabre ExCam | 100k+ speed/red-light cameras | JSON-lines/XZ | `wzsabre.rocks/cameras` (free) |
| NCES School Locations | ~130k US public schools | SHP/GeoJSON | Bulk download |
| FRA Railroad Crossings | ~250k at-grade crossings | DBF/SHP/CSV | Bulk download |
| Military Installations (MIRTA) | DoD base boundaries | SHP/GeoJSON | data.gov download |
| DJI No-Fly Zones | Drone restriction zones | SQLite | GitHub static file (free) |

### Phase 4 — User-Created Databases + Distribution
- Create/edit zones on map (long-press to add polygon vertices)
- Export as SQLite file
- Import from file picker
- Delta update system
- Community sharing via proxy

### Phase 5 — Advanced Sources
- Bridge weight limits (National Bridge Inventory — 624k+ bridges), HOV lanes, construction zones (WZDx)
- EPA facilities (TRI, Superfund, RCRA — 4M+ facilities), NPS/BLM boundaries
- FEMA flood zones combined with real-time USGS gauge data
- Commercial API evaluation: TomTom (freemium), HERE (250k tx/mo free), Geoapify speed limits (3k/day free)
- Real-time sources deferred from Phase 2: USGS earthquakes, NIFC wildfires, AirNow AQI

## Alert Severity Levels (from research)
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
- **Delta updates**: JSON patches when version gap <5; full snapshot for larger gaps
