# LocationMapApp v1.5 — Project State

## Last Updated: 2026-02-27 16:30 UTC

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
- Cache proxy with disk persistence (cache-data.json, 365-day Overpass TTL)
- Debug logging + TCP log streamer

## External API Routing
| API | App Endpoint | Proxy Route | TTL |
|-----|-------------|-------------|-----|
| Overpass POI | `http://10.0.0.4:3000/overpass` | POST /overpass | 365 days |
| USGS Earthquakes | `http://10.0.0.4:3000/earthquakes` | GET /earthquakes | 2 hours |
| NWS Alerts | `http://10.0.0.4:3000/nws-alerts` | GET /nws-alerts | 1 hour |
| METAR | `http://10.0.0.4:3000/metar` | GET /metar | 1 hour |
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

## Known Issues
- MBTA API key hardcoded in MbtaRepository.kt (should be in BuildConfig/secrets)
- 10.0.0.4 proxy IP hardcoded (works on local network only)
- No error retry/backoff on Overpass 504s (relies on cache to reduce load)

## Next Steps
- Test vehicle follow mode across full bus routes
- Monitor cache growth and hit rates over time
- Consider adding more POI categories to prefetch
- Evaluate proxy → remote deployment for non-local testing
