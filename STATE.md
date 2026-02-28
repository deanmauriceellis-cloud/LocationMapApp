# LocationMapApp v1.5 — Project State

## Last Updated: 2026-02-28 UTC

## Architecture
- **Android app** (Kotlin, Hilt DI, OkHttp, osmdroid) targeting API 34
- **Cache proxy** (Node.js/Express on port 3000) — transparent caching layer between app and external APIs
- **Split**: App → Cache Proxy → External APIs (Overpass, USGS, NWS, Aviation Weather, MBTA)

## What's Working
- Map display with osmdroid (OpenStreetMap tiles)
- GPS location tracking with manual override (long-press)
- Custom zoom slider (right edge)
- POI search via Overpass (restaurants, gas, parks, civic, transit)
- POI markers as small colored category dots (5dp)
- Earthquake overlay (USGS 2.5+ weekly feed)
- Weather alerts (NWS active alerts)
- METAR stations (aviation weather)
- NWS NEXRAD radar tiles (Iowa State Mesonet — replaced RainViewer)
- MBTA live vehicles: buses, commuter rail, subway (with auto-refresh)
- Vehicle follow mode: tap a bus/train → map tracks it, banner shows status
- POI prefetch along followed vehicle routes
- Cache-only POI display on map scroll (no upstream API calls)
- Adaptive POI search radius — proxy stores per-grid-cell hints, app fetches/reports
- Individual POI cache (poi-cache.json) — deduped by OSM type+id, with first/last seen timestamps
- POI database (PostgreSQL) — permanent storage with JSONB tags, category indexing, upsert import
- Cache proxy with disk persistence (cache-data.json, radius-hints.json, poi-cache.json, 365-day Overpass TTL)
- Debug logging + TCP log streamer

## External API Routing
| API | App Endpoint | Proxy Route | TTL |
|-----|-------------|-------------|-----|
| Overpass POI | `http://10.0.0.4:3000/overpass` | POST /overpass | 365 days |
| USGS Earthquakes | `http://10.0.0.4:3000/earthquakes` | GET /earthquakes | 2 hours |
| NWS Alerts | `http://10.0.0.4:3000/nws-alerts` | GET /nws-alerts | 1 hour |
| METAR | `http://10.0.0.4:3000/metar` | GET /metar | 1 hour |
| Radius Hints | `http://10.0.0.4:3000/radius-hint` | GET+POST /radius-hint | persistent |
| POI Cache | `http://10.0.0.4:3000/pois/...` | GET /pois/stats, /pois/export, /poi/:type/:id | persistent |
| MBTA | direct (api-v3.mbta.com) | not proxied | — |
| Radar tiles | direct (mesonet.agron.iastate.edu) | not proxied | — |

## Map Interaction Model
- **Single tap**: no action
- **Long press (~2s)**: enter manual mode, center map, search POIs at location
- **Scroll/pan**: displays cached POIs for visible area (cache-only, no upstream)
- **Tap vehicle marker**: follow mode (map tracks vehicle, banner shows status/speed)
- **Tap follow banner**: stop following

## Key Files
- `app/src/main/java/.../ui/MainActivity.kt` — main map activity, all overlays
- `app/src/main/java/.../ui/MainViewModel.kt` — LiveData, data fetching
- `app/src/main/java/.../ui/MarkerIconHelper.kt` — icon/dot rendering with cache
- `app/src/main/java/.../data/repository/PlacesRepository.kt` — Overpass + earthquakes
- `app/src/main/java/.../data/repository/WeatherRepository.kt` — NWS + METAR
- `app/src/main/java/.../data/repository/MbtaRepository.kt` — MBTA vehicles
- `cache-proxy/server.js` — Express caching proxy
- `cache-proxy/cache-data.json` — persistent cache (gitignored)
- `cache-proxy/radius-hints.json` — adaptive radius hints per grid cell (gitignored)
- `cache-proxy/poi-cache.json` — individual POI cache, deduped by type+id (gitignored)
- `cache-proxy/schema.sql` — PostgreSQL schema for permanent POI storage
- `cache-proxy/import-pois.js` — standalone script to import POIs from proxy into PostgreSQL

## PostgreSQL POI Database
- Database: `locationmapapp`, table: `pois`
- Composite PK: `(osm_type, osm_id)` — globally unique OSM identifiers
- Promoted columns: `name`, `category` (e.g. `amenity=restaurant`) for fast filtering
- `tags` JSONB with GIN index — preserves all OSM tag keys for flexible queries
- `first_seen`/`last_seen` timestamps track cache discovery history
- Import: `DATABASE_URL=postgres://... node import-pois.js` (fetches from proxy `/pois/export`, upserts)
- 1334 POIs imported as of 2026-02-28

## Known Issues
- MBTA API key hardcoded in MbtaRepository.kt (should be in BuildConfig/secrets)
- 10.0.0.4 proxy IP hardcoded (works on local network only)

## Next Steps
- Test vehicle follow mode across full bus routes
- Monitor cache growth and hit rates over time
- Consider adding more POI categories to prefetch
- Evaluate proxy → remote deployment for non-local testing
- Build query API on top of PostgreSQL POI database
- Automate periodic POI imports (cron or proxy hook)
