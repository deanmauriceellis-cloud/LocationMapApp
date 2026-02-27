# LocationMapApp — Session Log

## Session: 2026-02-27 (Initial Commit Session)

### Context
First documented session. App was already functional with map, GPS, POI search, MBTA transit, weather overlays. This session added caching infrastructure, radar fix, vehicle tracking, and UI improvements.

### Changes Made

#### Cache Proxy (NEW)
- Created `cache-proxy/` — Node.js Express server on port 3000
- Routes: POST /overpass, GET /earthquakes, GET /nws-alerts, GET /metar
- Admin: GET /cache/stats, POST /cache/clear
- In-memory cache with TTL eviction + disk persistence (cache-data.json)
- Overpass cache key: lat/lon rounded to 3 decimals (~111m) + sorted tag filters
- X-Cache-Only header support for cache-only requests (no upstream on miss)
- Overpass TTL set to 365 days; earthquakes 2h; NWS alerts 1h; METAR 1h
- Tested: first request 400ms (MISS) → cached request 3.6ms (HIT)

#### Radar Fix
- RainViewer tiles returning 403 Forbidden on all requests
- Switched to NWS NEXRAD composite via Iowa State Mesonet
- URL: `mesonet.agron.iastate.edu/cache/tile.py/1.0.0/nexrad-n0q-900913/`
- No API key, no timestamp fetch needed, standard XYZ tiles

#### Vehicle Follow Mode (NEW)
- Tap any bus/train/subway marker → map zooms to it (zoom 16), dark banner appears
- Banner shows: vehicle type, label, route, status, speed
- On each refresh cycle (30-60s): map re-centers on vehicle, banner updates
- Tap banner to stop following
- Toggling layer off stops following
- POI prefetch fires at vehicle position on each update (fills cache along route)

#### MBTA JsonNull Fix
- Vehicles with null stop/trip relationships crashed parser (~30 warnings per refresh)
- Fixed: `getAsJsonObject("data")` → `get("data")?.takeIf { !it.isJsonNull }?.asJsonObject`
- All vehicles now parse cleanly

#### POI Marker Redesign
- Changed from 26dp vector icons to 5dp colored dots
- Semi-transparent circle with opaque center point
- Category colors preserved (orange=gas, red=restaurant, green=park, etc.)
- Much cleaner at any zoom level

#### Map Interaction Changes
- Single tap: disabled (was setting manual location)
- Long press: enters manual mode, centers map, triggers POI search
- Scroll/pan: displays cached POIs only (X-Cache-Only header, no upstream calls)

#### Android URL Routing
- PlacesRepository: Overpass + earthquakes → proxy at 10.0.0.4:3000
- WeatherRepository: NWS alerts + METAR → proxy at 10.0.0.4:3000
- AndroidManifest: usesCleartextTraffic was already true
