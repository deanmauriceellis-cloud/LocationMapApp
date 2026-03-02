# LocationMapApp — Session Log

## Session: 2026-03-02e (Geofence Phase 2 — Additional Zone Types — v1.5.36)

### Context
Phase 2 of the geofence system: extends Phase 1's TFR-only foundation with 4 additional zone types (speed cameras, school zones, flood zones, railroad crossings). All feed into the same GeofenceEngine spatial index and alert pipeline.

### Changes Made

#### Models.kt
- Added `ZoneType` enum: `TFR, SPEED_CAMERA, SCHOOL_ZONE, FLOOD_ZONE, RAILROAD_CROSSING`
- Added `zoneType: ZoneType = ZoneType.TFR` and `metadata: Map<String, String> = emptyMap()` to `TfrZone`
- Added `zoneType: ZoneType = ZoneType.TFR` to `GeofenceAlert`

#### GeofenceEngine.kt (rewrite)
- Added `zoneType: ZoneType` to `IndexedZone` data class
- Renamed `loadTfrs()` → `loadZones()`, kept alias
- Zone type propagated through `checkPosition()` into emitted alerts
- `severityForZoneType()`: TFR entry=CRITICAL, others=WARNING
- `isSchoolHours()`: weekday 7-9 AM, 2-4 PM filter — school zones only alert during school hours
- `getZoneCountByType()`: per-type zone counts for debug

#### Proxy: 4 new endpoints (cache-proxy/server.js)
- `GET /cameras?bbox=s,w,n,e` — Overpass `highway=speed_camera` + `man_made=surveillance[surveillance:type=camera][surveillance=enforcement]`, 24h cache
- `GET /schools?bbox=s,w,n,e` — Overpass `amenity=school` (nodes+ways+relations), `out body; >; out skel qt;` for polygon geometry, 24h cache
- `GET /flood-zones?bbox=s,w,n,e` — FEMA NFHL ArcGIS Layer 28, `SFHA_TF='T'` (high-risk), 2000-record pagination via `resultOffset`, 30-day cache
- `GET /crossings?bbox=s,w,n,e` — Overpass `railway=level_crossing` + `railway=crossing`, warning device extraction from tags, 7-day cache
- Note: Originally planned FRA ArcGIS for crossings, but endpoint was 404/regional-only. Switched to Overpass.
- Pinned `cheerio@1.0.0` for Node.js v18 compatibility (undici@7 requires Node 20+)
- Added `Zones:` line to startup banner

#### GeofenceRepository.kt (NEW)
- `@Singleton` repository with 4 fetch methods
- `fetchCameras()`: point → 200m circle, ZoneType.SPEED_CAMERA, metadata: cameraType, maxspeed, direction
- `fetchSchools()`: polygon schools use points directly, point-only → 300m circle, ZoneType.SCHOOL_ZONE, metadata: grades, operator
- `fetchFloodZones()`: FEMA polygon rings → TfrShape points, ZoneType.FLOOD_ZONE, metadata: zoneCode, zoneSubtype, bfe
- `fetchCrossings()`: point → 100m circle, ZoneType.RAILROAD_CROSSING, metadata: warningDevices, crossingType, railroad, street
- `generateCircleShape()` companion helper: lat/lon + radius → 36-point circle polygon

#### AppModule.kt
- Added `@Provides @Singleton` for `GeofenceRepository`

#### MainViewModel.kt
- Injected `GeofenceRepository`
- 4 per-type LiveData: `_cameraZones`, `_schoolZones`, `_floodZones`, `_crossingZones` (+ public accessors)
- 4 load methods: `loadCameras()`, `loadSchools()`, `loadFloodZones()`, `loadCrossings()`
- `rebuildGeofenceIndex()`: combines all 5 zone type lists → `geofenceEngine.loadZones(all)`
- Updated `loadTfrs()` to call `rebuildGeofenceIndex()` on success
- `clearZoneType()` / `clearAllGeofences()` for toggle-off behavior

#### MenuEventListener.kt
- Added 4 callbacks: `onCameraOverlayToggled()`, `onSchoolOverlayToggled()`, `onFloodOverlayToggled()`, `onCrossingOverlayToggled()`

#### AppBarMenuManager.kt
- 4 new pref constants: `PREF_CAMERA_OVERLAY`, `PREF_SCHOOL_OVERLAY`, `PREF_FLOOD_OVERLAY`, `PREF_CROSSING_OVERLAY`
- All default OFF; wired to `toggleBinary()` in `showAlertsMenu()`
- Added to `syncCheckStates()` for menu state sync

#### menu_alerts.xml
- 4 new checkable items: Speed Camera Alerts, School Zone Alerts, Flood Zone Overlay, Railroad Crossing Alerts

#### MainActivity.kt (extensive)
- 4 new overlay lists: `cameraOverlays`, `schoolOverlays`, `floodOverlays`, `crossingOverlays`
- Renamed `tfrReloadJob` → `geofenceReloadJob`, `pendingTfrRestore` → `pendingGeofenceRestore`
- `loadGeofenceZonesForVisibleArea()`: loads all enabled zone types with zoom guards (cameras≥10, others≥12)
- `scheduleGeofenceReload()`: checks any zone enabled before debouncing
- 4 render methods + shared helpers `renderZoneOverlays()`, `buildZonePolygon()`, `clearOverlayList()`
- Color schemes: cameras=orange, schools=amber, flood=blue (darker A/V), crossings=dark+yellow
- `showZoneDetailDialog()`: zone-type-aware detail dialog (color bar, type icon, metadata rows)
- `showGeofenceAlertBanner()`: zone-type-aware label and banner color
- `findZoneById()`: searches all 5 zone type lists
- 4 new LiveData observers for per-type zone rendering
- 5 toggle callback implementations (TFR updated to use `clearZoneType()`)
- Debug state: `"tfr"` → `"geofences"` with per-type counts, overlay counts, zoneCountByType

#### DebugEndpoints.kt
- `/geofences`: reports all zone types with counts, `zoneCountByType`, serializes zones per type
- `/geofences/alerts`: added `zoneType` field to alert JSON

### Proxy Endpoint Testing
- Cameras: 0 results (valid — no OSM speed cameras in Boston-area test bbox)
- Schools: 157 results ✓
- Flood zones: 1 result ✓
- Crossings: 227 results ✓

### Files Changed
- `app/src/main/java/.../data/model/Models.kt`
- `app/src/main/java/.../util/GeofenceEngine.kt`
- `cache-proxy/server.js`
- `cache-proxy/package.json`
- `app/src/main/java/.../data/repository/GeofenceRepository.kt` (NEW)
- `app/src/main/java/.../di/AppModule.kt`
- `app/src/main/java/.../ui/MainViewModel.kt`
- `app/src/main/java/.../ui/menu/MenuEventListener.kt`
- `app/src/main/java/.../ui/menu/AppBarMenuManager.kt`
- `app/src/main/res/menu/menu_alerts.xml`
- `app/src/main/java/.../ui/MainActivity.kt`
- `app/src/main/java/.../util/DebugEndpoints.kt`

---

## Session: 2026-03-02d (Geofence Alert System — TFR Phase 1 — v1.5.35)

### Context
Phase 1 of geofence alert system: spatial engine + FAA TFR alerting. GeofenceEngine is designed for future zone types (speed cameras, flood zones, etc.) but Phase 1 implements TFR-only detection.

### Changes Made

#### Dependencies
- **app/build.gradle**: Added `org.locationtech.jts:jts-core:1.19.0` (pure Java spatial library — R-tree, point-in-polygon)
- **cache-proxy/package.json**: Added `cheerio@^1.0.0` (HTML parser), `fast-xml-parser@^4.3.0` (XML parser)

#### Proxy: `/tfrs` endpoint (cache-proxy/server.js, ~280 lines)
- `GET /tfrs?bbox=s,w,n,e` — scrapes FAA TFR list from `tfr.faa.gov/tfr2/list.jsp` (5-min cache)
- Fetches individual TFR detail XML pages (10-min cache), parses AIXM shapes with fast-xml-parser
- `parseFaaDms()` — converts FAA DMS coordinates (e.g., `383200N`) to decimal degrees
- `circleToPolygon()` — generates 32-point polygon from center + radius
- `arcToPoints()` — interpolates arc segments every 5°
- Handles circle, polygon, polyarc shape types; filters by requested bbox
- Returns JSON: `[{ id, notam, type, description, effectiveDate, expireDate, facility, state, shapes }]`

#### Data Models (Models.kt)
- `AlertSeverity` enum: INFO(0), WARNING(1), CRITICAL(2), EMERGENCY(3)
- `TfrShape`: type, points [[lon,lat]], floorAltFt, ceilingAltFt, radiusNm?
- `TfrZone`: id, notam, type, description, effectiveDate, expireDate, shapes, facility, state
- `GeofenceAlert`: zoneId, zoneName, alertType, severity, distanceNm?, timestamp, description

#### TfrRepository (NEW: data/repository/TfrRepository.kt)
- `@Singleton class TfrRepository @Inject constructor()`
- OkHttpClient 15s connect / 30s read; `fetchTfrs(s, w, n, e): List<TfrZone>`
- Manual JSON parsing of nested shapes/points arrays

#### GeofenceEngine (NEW: util/GeofenceEngine.kt)
- JTS `STRtree` spatial index + `GeometryFactory` for polygon construction
- `loadTfrs()` — builds JTS polygons, validates geometry, inserts into STRtree
- `checkPosition(lat, lon, altFt?, bearing?)` — queries R-tree, point-in-polygon, proximity + bearing check
- Entry (CRITICAL), proximity (WARNING, within 5nm + bearing ±60°), exit (INFO) detection
- Cooldown maps: 5min proximity, 10min entry; configurable `proximityThresholdNm`
- Helpers: `nmToDeg()`, `degToNm()`, `bearingTo()`, `isWithinBearingWindow()`, `isOnCooldown()`

#### DI (AppModule.kt)
- Added `provideTfrRepository()` Hilt provider

#### ViewModel (MainViewModel.kt)
- Added `TfrRepository` constructor param, `geofenceEngine` field
- New LiveData: `tfrZones`, `geofenceAlerts`
- Methods: `loadTfrs()`, `fetchTfrsDirectly()`, `checkGeofences()`, `clearTfrs()`

#### Menu + Toolbar
- **MenuEventListener.kt**: 4 new callbacks: `onAlertsRequested()`, `onTfrOverlayToggled()`, `onAlertSoundToggled()`, `onAlertDistanceChanged()`
- **menu_alerts.xml** (NEW): TFR Overlay (checkable), Alert Sound (checkable), Alert Distance
- **AppBarMenuManager.kt**: `showAlertsMenu()`, `setupTwoRowToolbar()` (10 programmatic ImageView buttons), `findToolbarIcon()`, 10 ICON_* constants, 3 PREF_* constants
- **toolbar_two_row.xml** (NEW): 2 × LinearLayout rows (36dp each), vertical container
- **activity_main.xml**: Toolbar height → `wrap_content`, `minHeight="0dp"`, content insets zeroed, `<include>` toolbar_two_row

#### Drawables
- **ic_alerts.xml** (NEW): 24dp warning triangle icon (gray fill, dark exclamation)
- **ic_tfr_zone.xml** (NEW): no-fly zone icon (red circle+line with airplane)

#### MainActivity.kt (~452 lines added)
- **Two-row toolbar**: replaced `onCreateOptionsMenu` with `setupTwoRowToolbar()`, stores `alertsIconView` + `weatherIconView`
- **TFR overlays**: `tfrOverlays` list, `renderTfrOverlays()` (semi-transparent red fill, click → detail dialog), `clearTfrOverlays()`
- **TFR viewport loading**: `scheduleTfrReload()` (500ms debounce), `loadTfrsForVisibleArea()`, deferred restore
- **TFR detail dialog**: `showTfrDetailDialog()` — dark dialog, red bar, NOTAM/type/description/altitude/dates
- **Geofence GPS**: checks in `currentLocation` observer (no altitude) + followed aircraft observer (with baroAltitude)
- **Alert icon**: `updateAlertsIcon()` — tint by max severity, pulsing AlphaAnimation for EMERGENCY
- **Alert banner**: `showGeofenceAlertBanner()` — red background, NOTAM + description, tap → detail dialog
- **Menu callbacks**: `onAlertsRequested()`, `onTfrOverlayToggled()`, `onAlertSoundToggled()`, `onAlertDistanceChanged()`
- **Debug state**: `debugTfrState()` → `tfr` field in `/state`

#### Debug Endpoints (DebugEndpoints.kt)
- Added `/geofences` and `/geofences/alerts` to dispatch table
- `handleGeofences()` — loaded zones, active zones, proximity threshold, zone details
- `handleGeofenceAlerts()` — active alerts with severity/type/distance

### Build
- One error caught and fixed: "Only one companion object is allowed per class" in AppBarMenuManager — merged ICON_* constants into existing companion object
- BUILD SUCCESSFUL after fix

### New Files (6)
- `app/.../data/repository/TfrRepository.kt`
- `app/.../util/GeofenceEngine.kt`
- `app/src/main/res/layout/toolbar_two_row.xml`
- `app/src/main/res/drawable/ic_alerts.xml`
- `app/src/main/res/drawable/ic_tfr_zone.xml`
- `app/src/main/res/menu/menu_alerts.xml`

### Files Modified (10)
- `app/build.gradle` — JTS dependency
- `app/.../data/model/Models.kt` — 4 new data classes/enums
- `app/.../ui/MainViewModel.kt` — TfrRepository + geofence LiveData/methods
- `app/.../ui/MainActivity.kt` — TFR overlays, geofence GPS, alert UI, two-row toolbar
- `app/.../ui/menu/AppBarMenuManager.kt` — two-row toolbar, alerts menu, icon constants
- `app/.../ui/menu/MenuEventListener.kt` — 4 new alert callbacks
- `app/.../di/AppModule.kt` — TfrRepository provider
- `app/.../util/DebugEndpoints.kt` — geofence endpoints + TFR state
- `app/src/main/res/layout/activity_main.xml` — toolbar height/insets + include
- `cache-proxy/server.js` + `package.json` — /tfrs endpoint, new dependencies

### Version
v1.5.35

---

## Session: 2026-03-02c (Weather Feature Overhaul — v1.5.34)

### Changes Made

#### Proxy: `/weather` Composite Endpoint (cache-proxy/server.js)
- **`GET /weather?lat=X&lon=Y`**: makes 5 NWS API calls in parallel with per-section TTLs
  - `/points/{lat},{lon}` → grid coordinates + nearest station (24h cache)
  - `/stations/{stationId}/observations/latest` → current conditions (5min cache)
  - `/gridpoints/{office}/{gridX},{gridY}/forecast/hourly` → 48 hourly periods (30min cache)
  - `/gridpoints/{office}/{gridX},{gridY}/forecast` → 7-day daily forecast (30min cache)
  - `/alerts/active?point={lat},{lon}` → location-specific alerts (5min cache)
- Snaps lat/lon to 2 decimal places (~1km resolution) for cache keys
- Extracts icon codes from NWS icon URLs (`/icons/land/day/sct,20` → `"sct"`)
- Converts units: °C→°F, km/h→mph, Pa→inHg, m→miles
- `degToCompass()` helper converts wind direction degrees to compass points
- Returns merged JSON: location, current, hourly[], daily[], alerts[], fetchedAt

#### Data Models (Models.kt)
- **New data classes**: `WeatherData`, `WeatherLocation`, `CurrentConditions`, `HourlyForecast`, `DailyForecast`
- **Updated `WeatherAlert`**: added `urgency: String = ""` and `instruction: String = ""` fields

#### Repository + ViewModel (WeatherRepository.kt, MainViewModel.kt)
- **`fetchWeather(lat, lon): WeatherData`**: calls proxy `/weather`, full JSON parser for composite response
- **`weatherData: LiveData<WeatherData?>`**: new LiveData in ViewModel
- **`fetchWeather(lat, lon)`**: coroutine launch method
- **`fetchWeatherDirectly(lat, lon): WeatherData?`**: suspend call for dialog inline usage

#### Weather Icon Drawables (22 files)
- Created 22 vector XML drawables in `app/src/main/res/drawable/ic_wx_*.xml`
- Day/night variants: clear_day, clear_night, few_clouds_day/night, partly_cloudy_day/night
- Shared: mostly_cloudy, overcast, rain, showers, thunderstorm, snow, sleet, freezing_rain, fog, haze, wind, hot, cold, tornado, hurricane, default
- **`WeatherIconHelper.kt`**: `drawableForCode(code, isDaytime): Int` mapping function

#### Toolbar Change: Alerts → Weather (menu XML, AppBarMenuManager.kt, MenuEventListener.kt)
- **`menu_main_toolbar.xml`**: replaced `menu_top_gps_alerts` (Alerts) → `menu_top_weather` (Weather, `ic_wx_default`)
- **`AppBarMenuManager.kt`**: `menu_top_weather` dispatches to `menuEventListener.onWeatherRequested()` (direct dialog, no submenu); deleted `showGpsAlertsMenu()` entirely; moved METAR toggle + frequency to `showRadarMenu()`; removed dead pref constants (PREF_WEATHER_ALERTS, PREF_WEATHER_BANNER, PREF_HWY_*, PREF_TRAFFIC_*)
- **`MenuEventListener.kt`**: added `onWeatherRequested()`; removed 6 dead stubs (onWeatherAlertsToggled, onWeatherBannerToggled, onHighwayAlertsToggled, onHighwayAlertsFrequencyChanged, onTrafficSpeedToggled, onTrafficSpeedFrequencyChanged)
- **`menu_radar.xml`**: added `menu_metar_display` (checkable) and `menu_metar_frequency` items
- **Deleted**: `menu_gps_alerts.xml`

#### Weather Dialog + Auto-Fetch (MainActivity.kt)
- **Auto-fetch**: on every GPS update, checks if 30 min elapsed since last fetch → `viewModel.fetchWeather(lat, lon)`
- **Observer**: `viewModel.weatherData.observe` → `updateWeatherToolbarIcon(data)`
- **`updateWeatherToolbarIcon()`**: sets toolbar icon to current condition drawable; when alerts exist, draws icon inside a red rounded-rect border using programmatic Bitmap+Canvas+Paint
- **`showWeatherDialog()`**: 90%×85% dark dialog with async loading spinner
  - Header: weather icon + "Weather for City, ST" + close button
  - Current conditions: 48dp icon, 28sp temperature, description, feels-like, detail rows (wind, humidity, visibility, dewpoint, barometer)
  - Alerts: severity-colored backgrounds (Extreme=dark red, Severe=red, Moderate=orange, Minor=yellow), tap to expand/collapse with headline, description, instructions, expiry
  - 48-hour strip: HorizontalScrollView → 48 cells (60dp wide, day/night backgrounds), time label, 24dp icon, bold temperature, optional precip % in blue
  - 7-day outlook: day/night pairs with abbreviated name, icon, hi°/lo°, short forecast, optional precip %
  - Footer: station ID + formatted update time
- **`buildWeatherDialogContent()`**: separated from `showWeatherDialog()` for clarity

#### Cleanup (MainActivity.kt)
- FAB speed dial: Weather entry now calls `showWeatherDialog()` instead of `viewModel.fetchWeatherAlerts()`; icon changed to `ic_wx_default`
- Debug `/state` endpoint: added `weather` object (location, station, temperature, description, iconCode, hourly/daily/alert counts, fetchedAt)
- Removed dead toggle map entries for `PREF_WEATHER_ALERTS` and `PREF_NAT_ALERTS` from both onStart intent handler and `debugTogglePref()`
- Removed dead `onWeatherAlertsToggled` override and 5 stub overrides (weather banner, highway alerts/freq, traffic speed/freq)

### Files Modified
| File | Change |
|------|--------|
| `cache-proxy/server.js` | Added `/weather?lat=&lon=` composite endpoint (~150 lines), `degToCompass()`, updated startup log |
| `app/.../data/model/Models.kt` | 5 new data classes, updated WeatherAlert with urgency+instruction |
| `app/.../data/repository/WeatherRepository.kt` | `fetchWeather()` + `parseWeatherJson()` (~100 lines) |
| `app/.../ui/MainViewModel.kt` | `weatherData` LiveData, `fetchWeather()`, `fetchWeatherDirectly()` |
| `app/.../ui/WeatherIconHelper.kt` | **NEW** — icon code → drawable mapping (~60 lines) |
| `app/.../ui/MainActivity.kt` | `showWeatherDialog()`, `buildWeatherDialogContent()`, `updateWeatherToolbarIcon()`, auto-fetch, observer, cleanup |
| `app/.../ui/menu/AppBarMenuManager.kt` | Weather dispatch, METAR in radar, deleted alerts menu, removed dead prefs |
| `app/.../ui/menu/MenuEventListener.kt` | Added `onWeatherRequested()`, removed 6 dead stubs |
| `app/src/main/res/menu/menu_main_toolbar.xml` | Alerts → Weather icon |
| `app/src/main/res/menu/menu_radar.xml` | Added METAR toggle + frequency items |
| `app/src/main/res/menu/menu_gps_alerts.xml` | **DELETED** |
| `app/src/main/res/drawable/ic_wx_*.xml` | **NEW** — 22 weather condition vector icons |
| `STATE.md` | Updated for v1.5.34 |
| `SESSION-LOG.md` | This entry |
| `CHANGELOG.md` | v1.5.34 entry |

## Session: 2026-03-02b (Idle Auto-Populate + Delta Cache — v1.5.33)

### Changes Made

#### Idle Auto-Populate (MainActivity.kt)
- **New state variables**: `idlePopulateJob: Job?`, `lastSignificantMoveTime: Long`
- **GPS observer idle detection**: in jitter branch (<100m), checks `now - lastSignificantMoveTime > 60s` with guards (no manual populate, no follow, speed ≤20mph). Starts `startIdlePopulate(gpsPoint)` when conditions met
- **GPS observer movement cancellation**: in significant-move branch (≥100m), resets `lastSignificantMoveTime` and cancels any active idle populate
- **`startIdlePopulate(center: GeoPoint)`**: full probe-calibrate-spiral scanner identical to manual `startPopulatePois()` but uses passed GPS center (not map center), 45s inter-cell delays (not 30s), stored in `idlePopulateJob`, "Idle scan:" banner prefix
- **`stopIdlePopulate()`**: cancels job, removes scanning marker, hides banner
- **`showIdlePopulateBanner()`**: like `showPopulateBanner` but "Idle scan:" prefix and banner tap calls `stopIdlePopulate()`
- **6 cancellation points**: long-press, vehicle tap, aircraft tap, goToLocation, startPopulatePois, GPS movement >100m
- **Debug state**: added `idlePopulate` (boolean), `idleTimeSec` (seconds since last significant move), `populate` (manual scanner active)

#### X-Client-ID Header (PlacesRepository.kt + AppModule.kt)
- **PlacesRepository constructor**: now takes `@ApplicationContext private val appContext: Context`
- **`clientId: String` lazy property**: generates UUID on first launch, stores in `places_repo_prefs` SharedPreferences
- **Header sent on all 3 Overpass methods**: `searchPois()`, `searchPoisCacheOnly()`, `searchPoisForPopulate()` — `.header("X-Client-ID", clientId)`
- **AppModule**: `providePlacesRepository()` updated to pass `@ApplicationContext context`

#### Delta Cache Optimization (cache-proxy/server.js)
- **`crypto` import**: added for MD5 content hashing
- **`findCoveringCache(lat, lon, radius, tags)`**: checks cache at 2x and 4x radius (up to 5000m) at same grid point — a larger-radius cached result is a superset
- **Covering cache check at 2 points**: (1) in initial `/overpass` handler before queuing, (2) in `overpassWorker()` before upstream fetch
- **`computeElementHash(jsonBody)`**: MD5 of sorted `type:id` pairs for delta detection
- **Content hash in worker**: after upstream response, if new hash matches previous for that key, skips `cacheIndividualPois()` entirely (logs "content unchanged — skipping update")
- **`contentHashes` Map**: stores cache key → last known element hash (in-memory only)

#### Per-Client Fair Queuing (cache-proxy/server.js)
- **`CLIENT_QUEUE_CAP = 5`**: max queued requests per client
- **`enqueueOverpassRequest(clientId, item)`**: checks per-client count, rejects at cap
- **`shiftFairQueue()`**: round-robin across client IDs instead of FIFO — naturally interleaves ABAB instead of AABB
- **`/overpass` handler**: reads `X-Client-ID` header, uses `enqueueOverpassRequest()`, returns 429 with `Retry-After: 30` when client at cap
- **Worker**: uses `shiftFairQueue()` instead of `overpassQueue.shift()`, logs client ID in queue processing

### Files Modified
| File | Change |
|------|--------|
| `app/.../ui/MainActivity.kt` | Idle detection in GPS observer, `startIdlePopulate()`, `stopIdlePopulate()`, `showIdlePopulateBanner()`, 6 cancellation points, debug state fields |
| `app/.../data/repository/PlacesRepository.kt` | `@ApplicationContext` injection, `clientId` UUID generation, `X-Client-ID` header on 3 methods |
| `app/.../di/AppModule.kt` | `providePlacesRepository()` passes context |
| `cache-proxy/server.js` | `crypto` import, `findCoveringCache()`, `computeElementHash()`, `contentHashes`, `enqueueOverpassRequest()`, `shiftFairQueue()`, covering cache in handler+worker, content hash in worker, client ID in queue |
| `CHANGELOG.md` | v1.5.33 entry |
| `STATE.md` | Updated header, added idle auto-populate + proxy delta cache sections |

## Session: 2026-03-02a (Geocode Autocomplete + Tooltips — v1.5.32)

### Changes Made

#### Toolbar Tooltips (menu_main_toolbar.xml + MainActivity.kt)
- Added `android:tooltipText` attribute to all 9 menu items in XML
- Added programmatic tooltip setup in `onCreateOptionsMenu()`: posts Runnable after layout, iterates menu items, sets `tooltipText` on action views found via `findViewById(item.itemId)`
- Tooltips: Alerts, Transit, Webcams, Aircraft, Radar, POI Categories, Utility, Find POI, Go to Location

#### Geocode Autocomplete (MainActivity.kt)
- **Auto-suggest TextWatcher**: added to Go to Location input field
  - 500ms debounce via `autoSearchJob` (cancels previous on each keystroke)
  - Fires at >= 3 characters, clears results below threshold
  - Calls same `doSearch` lambda used by Search button and Enter key
- **Replaced Android Geocoder with Photon**: `android.location.Geocoder` does literal name matching (no prefix/fuzzy), Photon does proper autocomplete
  - OkHttp call to proxy `GET /geocode?q=&limit=5`
  - Parses JSON array of `GeocodeSuggestion` via Gson
  - On tap: uses `city, state` for short toast label, `display_name` for row text
  - Added `import com.example.locationmapapp.data.model.GeocodeSuggestion`

#### GeocodeSuggestion Data Class (Models.kt)
- New `data class GeocodeSuggestion(lat, lon, display_name, type, city, state)`

#### Proxy Geocode Endpoint (cache-proxy/server.js)
- **`GET /geocode?q=&limit=`** — forwards to Photon (photon.komoot.io) with US bbox
  - Bounding box: `-125,24,-66,50` (continental US)
  - Extracts: lat, lon, display_name (assembled from name/city/state/country), type, city, state
  - 24h in-memory cache keyed by `geocode:query:limit`
  - Logs query and result count

### Testing
- "roch" → Rochester NY, Rochester MN, Rochester Hills MI, RIT, Rochelle IL (all US)
- Tapped Rochester NY → map navigated, POIs populated, toast "Moved to: Rochester, New York"
- Auto-suggest fires after typing pause, no double-fire, results update live
- Build: SUCCESSFUL, no new warnings

## Session: 2026-03-01n (Icon Toolbar + Go to Location — v1.5.31)

### Changes Made

#### Icon Toolbar (menu_main_toolbar.xml)
- Converted all 8 existing toolbar buttons from text labels to icon-only buttons
- Added `android:icon` attribute to each item pointing to existing drawable vectors
- Changed `app:showAsAction="always|withText"` → `app:showAsAction="always"` to drop text labels
- Kept `android:title` — Android shows it as a long-press tooltip automatically
- Icon mapping: Alerts→ic_weather_alert, Transit→ic_transit_rail, CAMs→ic_camera, Air→ic_aircraft, Radar→ic_radar, POI→ic_poi, Utility→ic_debug, Find→ic_search

#### Go to Location (9th toolbar button)
- **`ic_goto_location.xml`** (NEW) — 24dp crosshair/target vector icon (two concentric circles + four crosshair lines)
- **`menu_main_toolbar.xml`** — added 9th item `menu_top_goto` with crosshair icon
- **`AppBarMenuManager.kt`** — added `R.id.menu_top_goto` click handler → delegates to `menuEventListener.onGoToLocationRequested()`
- **`MenuEventListener.kt`** — added `onGoToLocationRequested()` callback
- **`MainActivity.kt`** — added ~170 lines:
  - `onGoToLocationRequested()` override → calls `showGoToLocationDialog()`
  - `showGoToLocationDialog()` — full-screen dark dialog matching existing Find/Legend style
    - EditText input with hint, Search button, IME Enter key support
    - `android.location.Geocoder` on `Dispatchers.IO` — up to 5 results
    - Clickable result rows with formatted address
    - Error/empty state handling, auto-show keyboard
  - `goToLocation(point, label)` — mirrors long-press handler:
    - Stops populate scanner + silent fill
    - Sets MANUAL location mode
    - Animates map (zoom 14 if < 14)
    - Triggers full POI search + bbox cache refresh + silent fill
    - Toast: "Moved to: <address>"

### Testing
- Build: SUCCESSFUL, no new warnings

## Session: 2026-03-01m (Smart GPS — v1.5.30)

### Changes Made
- Smart GPS position updates: dead zone filtering, speed-adaptive polling, 3km POI threshold

## Session: 2026-03-01l (Labeled Vehicle Markers — v1.5.29)

### Changes Made
- Labeled vehicle markers at zoom >= 18

## Session: 2026-03-01k (Silent POI Fill + Category Expansion — v1.5.28)

### Changes Made

#### Silent Background POI Fill (MainActivity.kt, ~100 lines)
- **`silentFillJob: Job?`** + **`silentFillRunnable: Runnable?`** — trackable coroutine + delayed post
- **`startSilentFill(center)`** — single `populateSearchAt()` call, guards against populate/follow active
- **`scheduleSilentFill(center, delayMs)`** — cancels pending runnable before posting new one (prevents double-fire)
- **`stopSilentFill()`** — cancels both pending runnable and running coroutine, hides banner
- **`showSilentFillBanner(text)` / `hideSilentFillBanner()`** — reuses `followBanner` TextView pattern
- **Trigger points**: first GPS fix (3s delay), saved position restore (4s delay), long-press (3s delay)
- **Cancellation points**: long-press, vehicle tap, aircraft tap, full populate scanner start, banner tap
- **Debug state**: `silentFill` boolean added to `/state` endpoint

#### Menu Infrastructure
- **`menu_utility.xml`** — added `menu_util_silent_fill_debug` checkable item
- **`AppBarMenuManager.kt`** — added `PREF_SILENT_FILL_DEBUG` constant, handler, checkbox sync
- **`MenuEventListener.kt`** — added `onSilentFillDebugToggled(enabled)` callback
- **`MainActivity.kt`** — `onSilentFillDebugToggled` implementation (hides banner when disabled)

#### POI Category Expansion (PoiCategories.kt)
- **Food & Drink**: +`shop=bakery`, `shop=alcohol`, `shop=deli` (3 subtypes)
- **Civic & Gov**: +`amenity=community_centre`, `amenity=social_facility` (2 subtypes)
- **Parks & Rec**: +`leisure=garden`, `tourism=picnic_site`, `amenity=drinking_water`, `amenity=toilets` (4 subtypes)
- **Shopping**: +`shop=hairdresser`, `shop=beauty` (2 subtypes)
- **Tourism & History**: +`tourism=artwork`, `tourism=gallery`, `tourism=information`, `historic=cemetery`, `historic=building` (5 subtypes)
- **Auto Services**: +`shop=car`, `shop=car_parts` — now has subtypes (was null): 6 subtypes total
- **Entertainment**: +`amenity=theatre`, `amenity=cinema`, `amenity=nightclub`, `amenity=events_venue`, `amenity=arts_centre` (5 subtypes)
- Total: +23 new tags, ~3,571 previously uncategorized POIs now visible

#### Database
- POI cache re-imported after Hollywood/LA populate session: 23,343 → 39,266 POIs

### Testing
- Startup silent fill: verified at home location (14 POIs at 1500m) and LA (174 POIs at 750m, 555 new)
- Long-press silent fill: verified in rural MA (118 POIs at 3000m), NYC (192 POIs at 750m, cap-retry)
- Saved position restore: verified at LA defaults (174 POIs at 750m)
- Double-fire bug: fixed with tracked Runnable, confirmed no duplicate in NYC test
- Cancellation: silent fill correctly skipped during active populate scanner
- Build: SUCCESSFUL, no new warnings

## Session: 2026-03-01j (POI Detail Dialog — v1.5.27)

### Changes Made

#### Proxy (cache-proxy/)
- **package.json** — Added `duck-duck-scrape: ^2.2.5` dependency
- **server.js** — Added `require('duck-duck-scrape')` + `GET /pois/website` endpoint
  - 3-tier website resolution waterfall: OSM tags → Wikidata P856 → DuckDuckGo search
  - `cacheResolvedWebsite()` helper writes `_resolved_website`/`_resolved_source` to pois JSONB tags
  - Directory site filter (yelp, facebook, tripadvisor, yellowpages, foursquare, bbb, etc.)
  - Always returns phone/hours/address from existing tags
  - Graceful error handling — never throws, returns `{ url: null, source: "none" }` on failure

#### App Data Layer
- **Models.kt** — Added `PoiWebsite` data class (url, source, phone, hours, address)
- **FindRepository.kt** — Added `fetchWebsite()` suspend function calling `/pois/website`
- **MainViewModel.kt** — Added `fetchPoiWebsiteDirectly()` pass-through suspend call

#### POI Detail Dialog (MainActivity.kt, ~250 lines)
- **`poiCategoryColor()`** — maps category tag to PoiCategory color from central config
- **`showPoiDetailDialog(result)`** — 90%w × 85%h dark dialog
  - Header: category color dot + compact GPS distance (cyan "1.4mi(NE)") + name + close (✕)
  - Category color bar (4dp)
  - Info rows: Distance, Type (with detail), Address, Phone (tappable cyan → ACTION_DIAL), Hours
  - Website area: spinner → "Load Website" button (async resolved) or "No website available"
  - Action buttons: Directions (green), Call (blue, dimmed if no phone), Reviews (amber), Map (gray → zoom 18)
- **`showFullScreenWebView(url, title)`** — full-screen dialog with WebView
  - Top bar: back arrow (← = browser back or dismiss) + title + close (✕)
  - `useWideViewPort + loadWithOverviewMode` — sites scale to fit screen
  - Pinch-to-zoom enabled, `onRenderProcessGone` crash handler returns `true` (no ANR)
  - WebView destroyed on dialog dismiss
- **Rewired Find results tap**: `showPoiDetailDialog(result)` instead of direct map animate
- **External intents**: `FLAG_ACTIVITY_NO_HISTORY` on Directions + Call — auto-killed on return

#### Bug Fixes During Session
- WebView renderer crash (emulator) caused ANR → added `onRenderProcessGone` handler
- Auto-loading WebView blocked main thread during Chromium init → switched to deferred "Load Website" button
- Website rendered at desktop scale → enabled `useWideViewPort + loadWithOverviewMode`
- Directions opened Google Maps with raw coordinates → uses named destination URL
- Google Maps lingered in back stack → `FLAG_ACTIVITY_NO_HISTORY` kills on return

### Testing
- Proxy `/pois/website` tested: Tier 1 (Starbucks → OSM tag), Tier 2 (Salem Five → Wikidata), cache hit (instant)
- DDG Tier 3 rate-limited during rapid testing (expected) — graceful fallback to "none"
- All resolved URLs persisted in DB as `_resolved_website` JSONB tags
- Android build: BUILD SUCCESSFUL, no new warnings
- Dialog tested on device: info rows, Load Website, Directions, back navigation all working

## Session: 2026-03-01i (Find Dialog — POI Discovery — v1.5.26)

### Changes Made

#### Proxy Endpoints (2 new, cache-proxy/server.js + schema.sql)
- **`GET /db/pois/counts`** — category counts with 10-min in-memory server cache
  - SQL: `SELECT category, COUNT(*) FROM pois WHERE category IS NOT NULL GROUP BY category`
  - Response: `{ counts: { "amenity=bar": 65, ... }, total: 23343, cachedAt: "..." }`
- **`GET /db/pois/find`** — distance-sorted POIs by category
  - Params: lat, lon (required), categories (comma-separated), limit (default 50, max 200), offset
  - Strategy: bbox pre-filter (50km), Haversine sort, auto-expand to 200km if < limit results
  - Inlines lat/lon in Haversine SQL to avoid pg type inference issues (learned from initial `$1`/`$2` failure)
  - Returns full JSONB tags for cuisine, denomination, address extraction
- **`idx_pois_category_lat_lon`** composite index added to schema.sql

#### App Data Layer (4 files)
- **Models.kt** — `FindResult` (with `typeValue`, `detail`, `toPlaceResult()`), `FindCounts`, `FindResponse`
- **FindRepository.kt** (NEW) — `@Singleton`, OkHttpClient, 10-min client counts cache, `fetchCounts()`, `findNearby()`
- **AppModule.kt** — `provideFindRepository()` DI binding
- **MainViewModel.kt** — `findCounts` LiveData, `loadFindCounts()`, `findNearbyDirectly()` suspend call

#### Menu Wiring (5 files)
- **menu_main_toolbar.xml** — `menu_top_legend` → `menu_top_find` ("Find")
- **menu_utility.xml** — Added `menu_util_legend` ("Map Legend") at bottom
- **menu_poi.xml** — Added `menu_poi_all_on` ("All POIs On") at top
- **MenuEventListener.kt** — `onFindRequested()` in new FIND / LEGEND section
- **AppBarMenuManager.kt** — `menu_top_find` → `onFindRequested()`, `menu_util_legend` → `onLegendRequested()`, `menu_poi_all_on` → `enableAllPois()` helper

#### Find Dialog (MainActivity.kt, ~350 lines)
- **`showFindDialog()`** — entry point, auto-exits filter mode, loads counts
- **`showFindCategoryGrid(dialog)`** — 4×4 GridLayout: color cells (30% alpha), label (12sp bold), count badge (10sp gray)
  - Short tap: subtypes → `showFindSubtypeGrid()`, else → `showFindResults()`
  - Long press: `dialog.dismiss()` + `enterFindFilterMode()`
- **`showFindSubtypeGrid(dialog, cat)`** — dynamic 2-3 col grid, back navigation, same tap/long-press behavior
- **`showFindResults(dialog, title, tags, parentCat)`** — async-loaded ScrollView results list
  - Each row: distance+direction (light blue #4FC3F7, 65dp col) + name (14sp bold) + detail (12sp gray) + address (11sp)
  - Footer: "Showing N nearest (within X.X mi)"
  - Tap row → dismiss, animateTo(zoom 17, 800ms), schedule loadCachedPoisForVisibleArea after 1s
- **`formatDistanceDirection()`** — `Location.distanceBetween()` + 8-point compass; "150 ft N" / "0.3 mi NW" / "12 mi SE"

#### Map Filter Mode (MainActivity.kt, ~60 lines)
- State: `findFilterActive`, `findFilterTags`, `findFilterLabel`, `findFilterBanner`
- **`enterFindFilterMode(tags, label)`** — sets state, shows banner, calls `loadFilteredPois()`
- **`loadFilteredPois()`** — `findNearbyDirectly()` with 200 limit → `toPlaceResult()` → `replaceAllPoiMarkers()`
- **`exitFindFilterMode()`** — clears state, removes banner, restores `loadCachedPoisForVisibleArea()`
- Scroll debounce: `if (findFilterActive) loadFilteredPois() else loadCachedPoisForVisibleArea()`
- Auto-exit: `showFindDialog()` calls `exitFindFilterMode()` if active

#### Debug Integration (DebugEndpoints.kt via MainActivity)
- `debugState()` returns `findFilter: { active, label, tags }` in `/state` response

### Bug Fixes During Implementation
- **Haversine pg type error** — initial `haversine('$1', '$2')` failed with "could not determine data type of parameter $1"; fixed by inlining lat/lon values directly into SQL string (matches existing `/db/pois/nearby` pattern)

---

## Session: 2026-03-01h (Legend Dialog + Zoom/Transit UX Fixes — v1.5.25)

### Changes Made

#### Legend Toolbar Button (4 files)
- **`menu_main_toolbar.xml`** — 8th toolbar item `menu_top_legend` ("Legend") after Utility
- **`MenuEventListener.kt`** — `onLegendRequested()` callback in new LEGEND section
- **`AppBarMenuManager.kt`** — direct callback in `when` block (no sub-menu XML)
- **`MainActivity.kt`** — `showLegendDialog()`: dark scrollable dialog (90% width, 85% height, #1A1A1A)
  - 7 sections: Your Location, POIs (iterates PoiCategories.ALL), Weather (METAR swatches + radar gradient), Transit Vehicles (7 line colors), Transit Stops, Aircraft (4 altitude colors + SPI + trail), Cameras
  - Programmatic icon rendering: colored dots, bordered rects, gradient bar, colored lines
  - 6 new graphics imports (Bitmap, Canvas, Paint, LinearGradient, Shader, RectF)

#### POI Bbox Refresh Fix (long-press)
- **Bug**: programmatic `animateTo()` doesn't trigger osmdroid's `onScroll` listener, so `loadCachedPoisForVisibleArea()` never ran after long-press location change — POIs fetched into cache but never displayed
- **Fix**: explicit `loadCachedPoisForVisibleArea()` scheduled 2s after long-press `animateTo()` via debounced `cachePoiJob`

#### Long-Press Auto-Zoom
- If zoom < 14 on long-press → zooms to 14; if already 14+ → leaves current zoom
- Replaces previous behavior of no zoom change (user had to manually zoom in)

#### Transit Zoom Guard (zoom ≤ 10)
- `transitMarkersVisible` flag tracks state, toggled in `onZoom` listener
- Zoom ≤ 10: clears all transit markers (trains, subway, buses, stations, bus stops)
- Zoom > 10: re-adds markers from latest LiveData values
- LiveData observers guarded: skip `addMarker` calls when `!transitMarkersVisible`

#### Populate Scanner Zoom Fix
- `placeScanningMarker()` no longer forces zoom to 14 — only sets zoom 14 if current zoom < 14
- Allows populate to run at user's preferred zoom (e.g., zoom 18 for detailed view)

---

## Session: 2026-03-01g (Aircraft Flight Path Visualization — v1.5.24)

### Changes Made

#### Flight Path Trail (4 files modified)
- **`FlightPathPoint` data class** (Models.kt) — lat, lon, altitudeMeters (nullable), timestamp (epoch ms), `toGeoPoint()`
- **`fetchFlightHistory(icao24)`** (AircraftRepository.kt) — calls `GET /db/aircraft/:icao24`, parses `path` array into `List<FlightPathPoint>`. Each sighting yields 2 points (first + last position). Uses `java.time.Instant.parse()` for ISO timestamps.
- **`fetchFlightHistoryDirectly(icao24)`** (MainViewModel.kt) — suspend bridge, same pattern as `fetchPredictionsDirectly`
- **MainActivity.kt trail implementation**:
  - `flightTrailPoints: MutableList<FlightPathPoint>` + `flightTrailOverlays: MutableList<Polyline>`
  - Extracted `altitudeColor(altitudeMeters, onGround)` helper — replaces inline logic in `addAircraftMarker`, shared with trail
  - `redrawFlightTrail()` — full rebuild from points, one Polyline per continuous segment, skips >30min gaps, caps 1000 points, inserts before aircraft markers (z-order)
  - `appendToFlightTrail(state)` — incremental single-segment add on live updates, deduplicates by position
  - `clearFlightTrail()` — removes all overlays + clears points
  - `loadFlightTrailHistory(icao24, currentState?)` — coroutine: fetches DB history, appends current position, redraws
  - `startFollowingAircraft()` → calls `clearFlightTrail()` + `loadFlightTrailHistory()`
  - `followedAircraft` observer → calls `appendToFlightTrail(state)` on each live update
  - `stopFollowing()` → calls `clearFlightTrail()` at top
  - `debugFollowAircraft()` → also loads trail history
  - `debugState()` → includes `flightTrailPoints` and `flightTrailSegments` in markers map
  - Removed unused `routeOverlay: Polyline?` field
- Polyline styling: 6f width, alpha 200, round caps, anti-alias, non-interactive (`isEnabled = false`)

### No Proxy Changes
- Uses existing `/db/aircraft/:icao24` endpoint (v1.5.23) — no server modifications needed

---

## Session: 2026-03-01f (Bug Fixes + Cache Optimization + Aircraft DB — v1.5.23)

### Changes Made

#### Bug Fixes
- **overnight-test.sh ANSI grep fix** — `grep -c '^\[PASS\]'` on test-app.sh output returned 0 because ANSI color codes preceded `[PASS]`. Fixed by piping through `sed 's/\x1b\[[0-9;]*m//g'` to strip escape codes before counting. Only affected lines 471-472 in overnight-test.sh (events.log was already plain text).

- **Bus routeName 100%** (was 80%) — Root cause: MBTA shuttle routes (`Shuttle-Generic`, `Shuttle-Generic-Red`) have `long_name: ""` (empty string, not null). Kotlin's `?:` Elvis operator doesn't trigger on `""`, so the fallback to `short_name` never fired. Fixed by adding `.takeIf { it.isNotBlank() }` to the chain, plus `description` as a third fallback. Both parser locations in MbtaRepository.kt updated. Result: 0 empty route names (was 59/317).

#### Fuzzy Search
- **Fuzzy match helper** in `DebugEndpoints.kt` — `fuzzyMatch(target, query)` splits query into words, matches each against target independently, with abbreviation expansion (17 common abbreviations: Mass→Massachusetts, Ave→Avenue, Sq→Square, St→Street, Ctr→Center, etc.)
- Applied to `/bus-stops?q=`, `/stations?q=`, and `/markers/search?q=` endpoints
- "Mass Ave" → 163 bus stops, "Harvard Sq" → 2 bus stops (was 0 for both)

#### Cache Hit Rate Optimization
- **`snapBbox()` helper** in server.js — rounds bbox coordinates to a grid so small scrolls reuse the same cache key
  - `Math.floor` for south/west, `Math.ceil` for north/east → snapped bbox always contains original
  - METAR: 0.01° precision (~1.1km), 1h TTL
  - Webcams: 0.01° precision (~1.1km), 10min TTL
  - Aircraft: 0.1° precision (~11km), 15s TTL
- Before: every scroll generated unique 15-decimal-place bbox keys → near-zero cache hits for metar/webcams/aircraft (95 webcam entries, 24 metar entries, 20 aircraft entries in cache)
- After: small pans produce identical snapped keys → much higher cache reuse

#### Aircraft DB Query Endpoints
- **4 new `/db/aircraft/*` endpoints** in server.js:
  - `GET /db/aircraft/search` — filter by q (callsign/icao24), icao24, callsign, country, bbox (s/w/n/e), since/until time range, on_ground; sorted by last_seen DESC
  - `GET /db/aircraft/stats` — totalSightings, uniqueAircraft, topCountries, topCallsigns, timeRange, altitudeDistribution (ground/<5k/5-20k/>20k ft)
  - `GET /db/aircraft/recent` — most recently seen aircraft deduplicated by icao24
  - `GET /db/aircraft/:icao24` — full sighting history + flight path with first/last positions
- Route ordering: /stats and /recent defined before /:icao24 to avoid Express param matching
- `toSighting()` helper formats DB rows to camelCase JSON with computed durationSec

#### Database
- POIs re-imported: 6,631 → 23,343 (proxy cache growth from POI building sessions)
- Aircraft sightings: 501 across 195 unique aircraft, 204 unique callsigns
- Top traffic: JetBlue (JBU), Delta (DAL); 10 countries represented

### Commits
- `80adf86` — Fix ANSI grep in overnight-test.sh
- `fb7c2a2` — Fix bus routeName empty for shuttle routes
- `715a657` — Add fuzzy search to bus stops, stations, and marker search
- `2df1aa1` — Add bbox snapping to metar/aircraft/webcam cache keys
- `d6fd93f` — Add /db/aircraft/* query endpoints

---

## Session: 2026-03-01e (Automated Test Harness — v1.5.22)

### Changes Made

#### Overnight Test Harness
- **`overnight-test.sh`** (new, ~1,850 lines) — unattended 6-8 hour automated test suite
  - Phase 1: Setup & Baseline (verify connectivity, capture state, run test-app.sh)
  - Phase 1.5: Layer Initialization Timing (enable→refresh→count→elapsed for each layer)
  - Phase 2: Feature Exercise (15 MBTA vehicle tests, 7 station tests, 4 bus stop tests, 5 aircraft tests, 3 webcam tests, 2 METAR tests, 2 POI tests, 3 map nav tests, 11 layer toggles, 8 marker interaction tests, radar, follow modes)
  - Phase 3: Endurance Loop (30s iterations, 5-min snapshots, 15-min screenshots, 30-min deep refresh, 60-min map moves, aircraft follow cycles)
  - Phase 4: Late-Night Validation (vehicle staleness, station persistence, memory assessment)
  - Phase 5: Report Generation (summary table, memory trend, cache stats, OpenSky usage, event timeline, coverage matrix, recommendations)
  - `transit_service_window()` helper classifies active/winding_down/overnight/starting_up
  - Service transition detection triggers full vehicle refresh + screenshot when window changes
  - Output in `overnight-runs/YYYY-MM-DD_HHMM/` (events.log, time-series.csv, report.md, screenshots, snapshots)
  - Trap handler generates report on Ctrl+C with data collected so far

#### Morning Transit Test
- **`morning-transit-test.sh`** (new, ~500 lines) — deep transit validation for active service hours
  - `--wait-for-service` flag polls until vehicles appear (30s intervals, 60 min max)
  - `deep_vehicle_test()`: completeness analysis, staleness check, marker cross-check, unique routes
  - `deep_station_test()`: LiveData vs endpoint vs marker count comparison, 10-station search
  - `deep_bus_stop_test()`: 3-location viewport test at zoom 16, 4-term name search
  - `follow_endurance_test()`: 60s follow with 10s check intervals
  - `multi_location_density()`: 6 locations, all transit type counts
  - `api_reliability_test()`: 5 rapid requests to 9 endpoints, failure rate

#### Master Test Runner
- **`run-full-test-suite.sh`** (new, ~100 lines) — chains overnight → morning test
  - `--overnight N`, `--morning N`, `--morning-only`, `--quick` (30+30 min) flags
  - Auto-detects transit availability for `--wait-for-service`

#### Other Changes
- **`.gitignore`** — added `overnight-runs/` directory

#### Debug API Follow-Mode Fix
- **`MainActivity.kt`** — `debugFollowVehicleByIndex()` now calls `startFollowing(vehicle)` directly via `relatedObject` instead of `debugTapMarker()` (which opens the detail dialog and doesn't auto-follow)
- **`debugFollowAircraft()`** — starts follow directly without tapping marker (bypasses dialog)
- Enables automated vehicle follow testing — verified: `followedVehicle: "y1908"` returned correctly

### Bugs Found & Resolved
- **Station/bus-stop LiveData returning 0**: resolved by rebuilding and deploying app with v1.5.21 endpoints
- **Vehicle follow not working via API**: `/follow?type=buses&index=0` opened detail dialog instead of starting follow — fixed by calling `startFollowing()` directly
- **Aircraft altitude null**: correctly null for ground aircraft (`onGround=true`) — not a bug
- **test-app.sh ANSI grep**: color codes cause `grep -c '^\[PASS\]'` to miss all matches (open)

### Full Suite Results (2026-03-01)

**Overnight (5.5 hrs, 2:19 AM → 7:50 AM): 67 PASS, 0 FAIL, 4 WARN**
- Memory stable 9-27MB, no leak (peak 62MB = GC spike)
- OpenSky: 183/3600 used — well within budget
- Transit detected coming online at 5:25 AM: buses 0→171, trains 0→9, subway 0→32
- 27 screenshots, 69 CSV rows, 64 JSON snapshots captured
- Warns: bus headsign/stopName 25% (early AM), aircraft altitude null (ground), METAR 0 overnight

**Morning (1 hr, 7:50 AM → 8:50 AM): 36 PASS, 0 FAIL, 2 WARN**
- Buses: 240→270, headsign 80%, tripId 100%, stopName 80%
- Commuter Rail: 11→16, all fields 100%
- Subway: 69→77, all fields 100%
- Follow endurance: 6/6 checks active; API reliability: 45/45 = 100%
- Warns: bus routeName 80%, bus stop search 'Mass Ave' = 0

**Monitor (14 snapshots, 30-min, 2:22 AM → 8:53 AM)**
- Transit ramp: 52→100→198→273 buses, 0→9→54→75 subway
- 0 failures across entire 7-hour run

## Session: 2026-03-01d (Debug API Enhancements + Test Script — v1.5.21)

### Changes Made

#### Debug API Enhancements
- **`DebugEndpoints.kt`** — 3 new endpoints (22 total):
  - `GET /vehicles?type=buses|trains|subway` — raw `MbtaVehicle` fields from ViewModel LiveData (headsign, tripId, stopName etc.)
  - `GET /stations?limit=N&q=X` — raw `MbtaStop` data with name search
  - `GET /bus-stops?limit=N&q=X` — all cached bus stops with name search
- **`MainActivity.kt`** — `debugVehicles(type)`, `debugStations()`, `debugBusStops()` accessor methods

#### Fix /markers/tap — Custom Click Listeners
- **`debugTapMarker()`** — was calling `onMarkerClickDefault()` (protected, shows osmdroid info window)
  - Now uses reflection to get `mOnMarkerClickListener` field and invoke it
  - Falls back to `showInfoWindow()` for markers without custom listeners (e.g. METAR)

#### relatedObject on All Markers
- **`MainActivity.kt`** — `marker.relatedObject = vehicle/stop/state/webcam` set in all `addXxxMarker()` methods:
  - `addTrainMarker`, `addSubwayMarker`, `addBusMarker` → `MbtaVehicle`
  - `addStationMarker`, `addBusStopMarker` → `MbtaStop`
  - Aircraft marker → `AircraftState`
  - `addWebcamMarker` → `Webcam`
- **`debugMarkers()`** — enhanced to serialize `relatedObject` fields into response (vehicleId, headsign, tripId, icao24, etc.)

#### Automated Test Script
- **`test-app.sh`** (new, project root) — bash test suite using curl + jq against debug API
  - 10 test suites: core, buses, trains, subway, stations, bus-stops, toggles, aircraft, webcams, metar, markers
  - 30+ individual tests with PASS/FAIL/WARN/SKIP reporting
  - Supports `--skip-setup` and `--suite X` flags
  - Color-coded output with summary

### Testing
- Build: `assembleDebug` passes cleanly
- All endpoints respond correctly (verified via manual curl)

## Session: 2026-03-01c (Bus Stops Proxy Cache + TcpLogStreamer Removal — v1.5.20)

### Changes Made

#### Bus Stops Proxy Caching
- **`cache-proxy/server.js`** — added `GET /mbta/bus-stops` route
  - Fetches all ~6,904 bus stops from MBTA API (`route_type=3`, `page[limit]=10000`)
  - 24-hour cache TTL (bus stop locations rarely change)
  - Uses existing `cacheGet()`/`cacheSet()` infrastructure
  - `MBTA_API_KEY` constant added to proxy for upstream auth
- **`MbtaRepository.kt`** — `fetchBusStops()` now routes through proxy (`http://10.0.0.4:3000/mbta/bus-stops`) instead of direct MBTA API
  - Same `executeGet()` + `parseBusStops()` pipeline, just different URL
  - Subsequent fetches (app restart, toggle) served from proxy cache instantly

#### TcpLogStreamer Disabled
- **`MainActivity.kt`** — removed `TcpLogStreamer.start()` call from `onCreate()`
  - Was retrying TCP connection to `10.0.0.4:3333` every 10 seconds, spamming logs with ECONNREFUSED
  - Fully superseded by embedded debug HTTP server (`/logs` endpoint, port 8085)
  - `TcpLogStreamer.kt` still exists as dead code (not started)
  - Removed unused `import com.example.locationmapapp.util.TcpLogStreamer`

### Testing
- Proxy `/mbta/bus-stops`: first call = cache miss (upstream fetch), subsequent = cache hit
- 6,904 stops returned, 47 visible in downtown Boston at zoom 16
- 211–220 live bus vehicles via direct MBTA API (unchanged)
- No more port 3333 retry spam in logs

## Session: 2026-03-01b (Bus Stops + Vehicle Detail Dialog — v1.5.19)

### Changes Made

#### Feature 1: MBTA Bus Stop Markers
- **`MbtaRepository.kt`** — added `fetchBusStops()` + `parseBusStops()` (route_type=3, page limit 10,000)
- **`MainViewModel.kt`** — added `_mbtaBusStops` LiveData, `fetchMbtaBusStops()`, `clearMbtaBusStops()`
- **`ic_bus_stop.xml`** — new 24dp vector drawable (bus stop sign with "B" letter)
- **`MarkerIconHelper.kt`** — added `busStopIcon()` method (20dp, teal tint)
- **`menu_transit.xml`** — added "Bus Stops" checkable menu item
- **`AppBarMenuManager.kt`** — added `PREF_MBTA_BUS_STOPS` constant (defaults OFF), wired toggle + syncCheckStates
- **`MenuEventListener.kt`** — added `onMbtaBusStopsToggled(enabled: Boolean)` callback
- **`MainActivity.kt`**:
  - `busStopMarkers` list + `allBusStops` in-memory list + `busStopReloadJob` debounce
  - Observer stores full list, calls `refreshBusStopMarkersForViewport()`
  - Zoom >= 15 guard, bounding box filter, 300ms debounced reload on scroll/zoom
  - `addBusStopMarker()`: teal tint, tap → `showArrivalBoardDialog(stop)`
  - `onMbtaBusStopsToggled()` handler, `onStart()` restore from persisted pref

#### Feature 2: Vehicle Detail Dialog
- **`MainActivity.kt`**:
  - `onVehicleMarkerTapped()` now calls `showVehicleDetailDialog()` instead of `startFollowing()`
  - New `showVehicleDetailDialog()`: 85% width dark dialog, color bar, info rows (route, vehicle, status, speed, updated)
  - Three action buttons: Follow (teal), View Route (gray), Arrivals (blue)
  - Follow calls existing `startFollowing(vehicle)` / `stopFollowing()`
  - View Route creates synthetic `MbtaPrediction` from vehicle's `tripId`, opens `showTripScheduleDialog()`
  - Arrivals creates synthetic `MbtaStop` from vehicle's `stopId`, opens `showArrivalBoardDialog()`
  - Buttons dimmed (alpha 0.4) when tripId/stopId unavailable
  - `vehicleRouteColor()` helper: teal for buses (routeType 3), `routeColor()` for rail/subway

#### Debug Endpoints
- `/state` now includes `busStops` and `busStopsTotal` marker counts
- `/markers`, `/markers/tap`, `/refresh` support `bus_stops` type

### Files Modified
- `MbtaRepository.kt`, `MainViewModel.kt`, `MarkerIconHelper.kt`
- `menu_transit.xml`, `AppBarMenuManager.kt`, `MenuEventListener.kt`
- `MainActivity.kt` (bus stops + vehicle detail dialog + debug endpoints)
- `ic_bus_stop.xml` (new)
- `CHANGELOG.md`, `STATE.md`, `SESSION-LOG.md`

## Session: 2026-03-01a (Debug Server Fix + DB Re-import)

### Context
Debug HTTP server (port 8085) wouldn't start — `Connection refused` in logcat. Root cause: `DebugHttpServer` is a singleton `object` whose `start()` had no guard against double-start on Activity recreation. When the Activity was destroyed and recreated, `ServerSocket(PORT)` threw `BindException` which was silently swallowed. Also, PostgreSQL tables were empty and needed re-import.

### Changes Made

#### `DebugHttpServer.kt` — double-start fix
- Added `private var job: Job?` to track the server coroutine
- `start()` now returns early if `job?.isActive == true` (already running)
- Closes any leftover `serverSocket` before starting new one
- Added `fun stop()` — cancels job, closes socket, logs shutdown
- Catch block now always logs via `Log.e` (was only logging when `isActive`)

#### `MainActivity.kt` — onDestroy cleanup
- Added `onDestroy()` override calling `DebugHttpServer.stop()` to release port 8085 on Activity recreation

#### PostgreSQL re-import
- Ran `import-pois.js` — imported 6,631 POIs from proxy cache into `pois` table
- Aircraft sightings start empty, accumulate in real-time

### Verification
- `curl localhost:8085/state` — returns JSON with map state, marker counts
- `curl localhost:8085/perf` — returns memory/thread stats
- `curl localhost:8085/livedata` — returns LiveData values (538 POIs, 257 stations)
- `curl localhost:8085/screenshot -o test.png` — valid PNG (2400x1080)
- `SELECT COUNT(*) FROM pois` — 6,631 rows

---

## Session: 2026-02-28q (Debug HTTP Server — Embedded in App)

### Context
Testing the app required guessing pixel coordinates for `adb shell input tap`. We needed a way to programmatically interrogate and control the running app from the terminal. Solution: an embedded HTTP server accessed via `adb forward tcp:8085 tcp:8085` + `curl`.

### Changes Made

#### New file: `DebugHttpServer.kt`
- Singleton `object` with `ServerSocket` accept loop on `Dispatchers.IO`
- Port 8085, minimal HTTP/1.0 parser (method, path, query params)
- Routes to `DebugEndpoints`, always `Connection: close`
- URL-decoded query parameter parsing
- `@Volatile var endpoints` — set/cleared by Activity lifecycle

#### New file: `DebugEndpoints.kt`
- Holds `MainActivity` + `MainViewModel` references
- `EndpointResult` data class for responses (status, contentType, body, bodyBytes for PNG)
- `runOnMain` helper: `suspendCancellableCoroutine` + `Handler(Looper.getMainLooper())`
- 19 endpoint handlers:
  - `/` — endpoint listing
  - `/state` — map center, zoom, bounds, marker counts, follow state
  - `/logs` — DebugLogger entries with tail/filter/level params
  - `/logs/clear` — clear buffer
  - `/map` — read or set map position (animates via controller)
  - `/markers` — list markers by type with lat/lon/title/snippet
  - `/markers/tap` — trigger click via `debugTapMarker()` (synthetic MotionEvent)
  - `/markers/nearest` — Haversine distance sort from a point
  - `/markers/search` — text search on title/snippet
  - `/screenshot` — `@Suppress("DEPRECATION")` drawing cache → PNG bytes
  - `/livedata` — all ViewModel LiveData current values
  - `/prefs` — dump SharedPreferences
  - `/toggle` — toggle pref + fire layer handler (reuses handleDebugIntent pattern)
  - `/search` — trigger POI search at lat/lon
  - `/refresh` — force refresh any layer
  - `/follow` — follow aircraft by icao or vehicle by type+index
  - `/stop-follow` — stop following
  - `/perf` — Runtime memory, thread count, uptime
  - `/overlays` — map overlay list with types and counts

#### Modified: `MainActivity.kt`
- Added imports: `DebugEndpoints`, `DebugHttpServer`
- `onCreate()`: `DebugHttpServer.start()` after `TcpLogStreamer.start()`
- `onResume()`: `DebugHttpServer.endpoints = DebugEndpoints(this, viewModel)`
- `onPause()`: `DebugHttpServer.endpoints = null`
- New `internal` accessor methods:
  - `debugMapView()` — returns binding.mapView
  - `debugState()` — snapshot map of center, zoom, bounds, all marker counts, follow IDs, overlay count
  - `debugMarkers(type)` — serializable list of marker info (type/index/lat/lon/title/snippet)
  - `debugRawMarkers(type)` — raw Marker objects for tap
  - `debugTapMarker(marker)` — synthetic MotionEvent at projected screen position → `onSingleTapConfirmed`
  - `debugTogglePref(pref, value)` — sets pref + invokes menuEventListenerImpl handler
  - `debugRefreshLayer(layer)` — dispatches to ViewModel fetch or local load method
  - `debugFollowAircraft(icao)` — finds marker or starts icao24 tracking
  - `debugFollowVehicleByIndex(type, index)` — taps marker at index
  - `debugStopFollow()` — delegates to `stopFollowing()`

### Build Issues & Fixes
1. `onMarkerClickDefault` is `protected` in osmdroid `Marker` — switched to synthetic `MotionEvent` + `onSingleTapConfirmed`
2. Drawing cache deprecation warnings — suppressed with `@Suppress("DEPRECATION")`
3. `runOnMain` catch block was re-calling `block()` — fixed to `resumeWithException(e)`

### Version
- v1.5.18

---

## Session: 2026-02-28p (MBTA Train Station Markers with Arrivals & Schedules)

### Context
The app shows live MBTA vehicle positions but had no station markers. User wants to see all subway (~123) and commuter rail (~150) stations on the map, tap a station to see arriving trains with destinations, and tap a train to see its full schedule. MBTA v3 API supports all of this via `/stops`, `/predictions`, `/schedules`.

### Changes Made

#### Data classes (`Models.kt`)
- Added `MbtaStop(id, name, lat, lon, routeIds)` with `toGeoPoint()`
- Added `MbtaPrediction(id, routeId, routeName, tripId, headsign, arrivalTime, departureTime, directionId, status, vehicleId)`
- Added `MbtaTripScheduleEntry(stopId, stopName, stopSequence, arrivalTime, departureTime, platformCode)`

#### Station icon (`ic_train_station.xml`)
- 24dp vector drawable — building shape with canopy, door, two windows, clock accent at top
- Tinted at runtime per transit line color

#### MBTA API methods (`MbtaRepository.kt`)
- Extracted shared `executeGet(url, label)` helper from existing vehicle fetch logic
- `fetchStations()` — 2 API calls: subway routes (Red,Orange,Blue,Green-B/C/D/E,Mattapan) filtered by location_type=1 + CR route_type=2; merges by stop ID to combine routeIds for multi-line stations
- `fetchPredictions(stopId)` — real-time arrivals from `/predictions?filter[stop]=X&include=trip,route&sort=departure_time`; headsign from included trip, routeName from included route
- `fetchTripSchedule(tripId)` — full timetable from `/schedules?filter[trip]=X&include=stop&sort=stop_sequence`; stopName and platformCode from included stops

#### MarkerIconHelper changes
- Added `"train_station"` to CATEGORY_MAP with dark gray default
- Added `stationIcon(context, tintColor)` — 26dp tinted station icon

#### ViewModel (`MainViewModel.kt`)
- `_mbtaStations` / `mbtaStations` LiveData for station list
- `fetchMbtaStations()` — launches coroutine to call repository
- `clearMbtaStations()` — empties LiveData
- `fetchPredictionsDirectly(stopId)` — suspend, returns directly for dialog use
- `fetchTripScheduleDirectly(tripId)` — suspend, returns directly for dialog use

#### Menu wiring
- `MenuEventListener.kt` — added `onMbtaStationsToggled(enabled: Boolean)`
- `menu_transit.xml` — added checkable "Train Stations" item before national alerts
- `AppBarMenuManager.kt` — added `PREF_MBTA_STATIONS` constant (default ON), toggle handler, syncCheckStates

#### MainActivity — markers, dialogs, glue
- `stationMarkers` list with `addStationMarker()` / `clearStationMarkers()`
- `routeColor(routeId)` — centralized MBTA line color mapping (Red→#C62828, Orange→#E65100, Blue→#1565C0, Green→#2E7D32, CR→#6A1B9A, Silver→#546E7A)
- `routeAbbrev(routeId)` — short labels for arrival board (RL, OL, BL, GL-B, CR, M, SL)
- Multi-line stations (>1 routeId) get neutral dark gray tint; single-line get line color
- Observer on `mbtaStations` LiveData: clear + rebuild markers
- `onStart()` restore block: fetches stations if pref ON and markers empty
- Station tap does NOT interfere with vehicle/aircraft follow mode
- **Arrival board dialog** (`showArrivalBoardDialog`): 90% fullscreen dark, header + subtitle, column headers, prediction rows with colored dot + abbreviation + headsign + arrival time, 30s auto-refresh, tap row → trip schedule
- **Trip schedule dialog** (`showTripScheduleDialog`): back+close header, route color bar, stop list with dot + name + time + track number
- `formatArrivalTime()` — "Now" (≤0 min), "X min" (<60), "H:MM AM/PM" (else)
- `formatScheduleTime()` — 12h AM/PM format
- `onMbtaStationsToggled()` in menuEventListenerImpl

### Files Changed (9 files, 1 new)
1. `app/src/main/res/drawable/ic_train_station.xml` — **NEW** station icon
2. `app/.../data/model/Models.kt` — 3 new data classes
3. `app/.../data/repository/MbtaRepository.kt` — 3 API methods + parsers + shared executeGet
4. `app/.../ui/MarkerIconHelper.kt` — train_station category + stationIcon method
5. `app/.../ui/MainViewModel.kt` — stations LiveData + 3 functions
6. `app/.../ui/menu/MenuEventListener.kt` — onMbtaStationsToggled interface method
7. `app/src/main/res/menu/menu_transit.xml` — Train Stations checkable item
8. `app/.../ui/menu/AppBarMenuManager.kt` — PREF_MBTA_STATIONS + toggle + sync
9. `app/.../ui/MainActivity.kt` — station markers, arrival/schedule dialogs, menu handler

### Build
- `assembleDebug` passes cleanly (only pre-existing deprecation warning)

### Version
- v1.5.17

---

## Session: 2026-02-28o (Populate v2: Probe-Calibrate-Subdivide)

### Context
Testing populate utility on Beverly, MA downtown revealed multiple issues: 16 parallel Overpass queries on startup caused 504 storms, 504 errors poisoned radius hints (shrinking to 100m), populate grid was hardcoded at 3000m spacing regardless of actual density, and dense pockets within the grid left gaps.

### Changes Made

#### Populate v2: Three-phase approach (`MainActivity.kt`)
- Phase 1 (Probe): searches center first to discover settled radius, 3 retry attempts on errors
- Phase 2 (Calibrate): grid step calculated from settled radius, not hardcoded 3000m
- Phase 3 (Spiral): starts ring 1, each cell searches with retry-to-fit
- Removed old caller-side subdivision logic (2x2 mini-grid, sub-cell loops)

#### Recursive 3×3 subdivision (`MainActivity.kt`)
- New `searchCellSubdivisions()` function: when a cell settles smaller than grid radius, searches 8 fill points
- Recurses if fill points settle even smaller (tested: depth 0→1→2, 1500m→750m→375m)
- Tracks fill progress: "Fill 3/8 at 750m (depth 1)"
- Fixed bug: probe was unnecessarily subdividing (compared vs DEFAULT_RADIUS instead of grid radius)

#### searchPoisForPopulate retry-to-fit (`PlacesRepository.kt`)
- Added same cap-detection retry loop as `searchPois()` — halves radius on cap, retries in-place
- Returns settled radius and accumulated new/known POI counts in `PopulateSearchResult`
- `PopulateSearchResult` model extended with `poiNew` and `poiKnown` fields

#### Overpass request queue (`server.js`)
- All upstream Overpass cache misses serialized through a FIFO queue
- 10-second minimum interval between upstream requests (OVERPASS_MIN_INTERVAL_MS)
- Re-checks cache before processing (earlier queued request may have populated the same key)
- Queue depth exposed in `/cache/stats` → `overpassQueue`

#### Error radius immunity (`server.js`)
- `adjustRadiusHint()` now returns early on errors without changing the radius
- 504/429 timeouts are transient infrastructure problems, not density signals
- Prevents hint poisoning cascade (was: error→shrink→100m→all future searches use 100m)

#### Startup POI optimization (`MainActivity.kt`)
- Removed: loop over 16 `PoiCategories.ALL` firing `searchPoisAt()` for each enabled category
- Replaced with: single deferred `loadCachedPoisForVisibleArea()` (bbox display)
- Startup went from ~16 parallel Overpass queries to 0

#### Narrative populate banner (`MainActivity.kt`)
- Two-line banner with real-time diagnostics
- Shows: ring, cells, POIs (new count), grid radius, current action narrative
- Actions: "Probing center…", "Searching cell 3/8 at 1500m…", "Dense area! 1500m→750m — filling 8 gaps", "Fill 3/8: 45 POIs (8 new) at 750m"

#### Proxy POI count headers (`server.js`)
- `cacheIndividualPois()` now returns `{ added, updated }` counts
- Overpass responses include `X-POI-New` and `X-POI-Known` headers
- App reads headers in `searchPoisForPopulate` and accumulates in PopulateStats

### Testing
- Beverly, MA downtown: probe settled at 1500m, grid calibrated correctly
- Recursive subdivision fired at depths 0→1→2 (1500m→750m→375m) in dense pockets
- 1,741 POIs found in ~20 searches, zero 504 errors with throttle active
- Clean startup: only METAR + webcam cache hits, zero Overpass queries

### Memory note
- **NEVER attempt sudo or postgres-owned database commands** — must tell user to run manually

## Session: 2026-02-28n (Auto Cap Detection & Retry-to-Fit)

### Context
The Overpass API `out center 200` silently truncates results in dense areas. Only the populate scanner detected this. Regular `searchPois()` discarded the raw count, so GPS startup, long-press, category toggles, and follow-mode prefetches all silently lost POIs in dense areas. The 1-mile fuzzy radius hint range was too small — a search 2km from a known dense area would start at the default 3000m and retry through the full chain.

### Changes Made

#### Phase 1: Subdivision queue (later replaced)
- Added `CapEvent` model, `SharedFlow<CapEvent>` in PlacesRepository, recursive `subdivideCell()` in MainViewModel
- This was over-engineered — replaced in Phase 2

#### Phase 2: Retry-to-fit (`PlacesRepository.kt`)
- `searchPois()` now retries in-place when capped: halves radius, re-queries same center
- Loop continues until results fit under 500-element cap or 100m floor reached
- `postRadiusFeedback()` sends `capped: Boolean` to proxy for aggressive hint shrinking
- Removed: CapEvent model, SharedFlow, subdivision queue, ViewModel cap collection, Activity observers

#### Overpass cap raised to 500 (`PlacesRepository.kt`)
- `out center 200` → `out center 500` — reduces cap frequency significantly
- `OVERPASS_RESULT_LIMIT` constant updated to 500

#### MIN_RADIUS lowered to 100m (`PlacesRepository.kt`, `MainViewModel.kt`, `server.js`)
- Was 500m → now 100m in app and proxy
- Subdivision floor also 100m

#### 20km fuzzy radius hints (`PlacesRepository.kt`, `server.js`)
- Fuzzy hint search range expanded from 1 mile (~0.01449°) to 20km (~0.1798°)
- Proxy logs distance in km instead of miles
- Effect: one capped search in downtown Boston seeds hints for entire metro area

#### Proxy capped radius halving (`server.js`)
- `adjustRadiusHint(lat, lon, resultCount, error, capped)` — new 5th parameter
- When `capped=true`: halves radius (×0.5) instead of confirming
- POST `/radius-hint` reads `capped` from request body

#### Database sync
- Imported 70,808 POIs from proxy cache into PostgreSQL (was 22,494)

### Testing
- Downtown Boston (42.358, -71.058): 500 cap at all radii down to 375m, clears at 250m (290 elements)
- Beverly, MA (42.558, -70.880): 6/9 grid cells clear at 3000m, 3 cap (Salem/Danvers density)
- 20km fuzzy: Back Bay (2km) → 188m, Cambridge (5km) → 188m, Quincy (13km) → 188m, Plymouth (60km) → 3000m default

### Files Modified
- `app/.../data/model/Models.kt` — CapEvent added then removed (clean)
- `app/.../data/repository/PlacesRepository.kt` — retry loop, postRadiusFeedback capped flag, 20km fuzzy, MIN_RADIUS=100, OVERPASS_RESULT_LIMIT=500
- `app/.../ui/MainViewModel.kt` — subdivision queue added then removed (clean)
- `app/.../ui/MainActivity.kt` — cap observers added then removed (clean)
- `cache-proxy/server.js` — adjustRadiusHint capped param, 20km fuzzy, MIN_RADIUS=100

### Version
v1.5.15 — committed as two commits (cap detection + retry, then raised cap/min radius)

---

## Session: 2026-02-28m (Populate POIs — Hardening)

### Context
Populate scanner from v1.5.13 had several issues discovered during testing:
- Overpass 429s from aggressive adaptive delay (200ms/4s)
- Webcam reload spam triggered by crosshair animation moving the map
- POIs not appearing on map during scan (no bbox refresh after search)
- Overpass `out center 200` silently truncating dense areas (cap detection checked post-filter count)
- Cap retries hitting same cached response (proxy cache key didn't include radius)
- GC pressure from rendering thousands of POI markers at wide zoom

### Changes Made

#### Populate scanner pacing (`MainActivity.kt`)
- Replaced adaptive delay (200ms hit / 4s miss / 10s error) with **30s fixed pacing**
- Failed cells **retry in-place** instead of advancing to next cell
- Added `loadCachedPoisForVisibleArea()` after each successful search so POIs appear immediately

#### Populate scanner cap detection (`PlacesRepository.kt`, `Models.kt`, `MainActivity.kt`)
- `parseOverpassJson` now returns `Pair<List<PlaceResult>, Int>` — results + raw element count
- Cap detection checks **raw element count >= 200** (not post-filter named count)
- `PopulateSearchResult` gains `radiusM` and `capped` fields
- `searchPoisForPopulate()` accepts optional `radiusOverride` parameter
- When capped: subdivides cell into **mini-grid** (2x2 at half radius) instead of retrying center point
- Sub-grid step recomputed from halved radius: `0.8 * 2 * subRadius / 111320`

#### Populate banner improvements (`MainActivity.kt`)
- Shows success/fail/capped counts: `✓ok ⚠fail ✂capped`
- Per-second countdown timer: `Next: 25s`
- Shows `(retry 1500m)` during sub-cell searches

#### Populate UX changes (`MainActivity.kt`, `AppBarMenuManager.kt`, `menu_utility.xml`)
- Menu item no longer checkable — title changes to "⌖ Populate POIs (active)" when running
- Pref cleared in `onStart()` — never auto-restarts on app launch
- Stops on user interaction: long-press on map, vehicle marker tap, aircraft marker tap
- Map zooms to **zoom 14** at each scan point — smaller viewport = fewer bbox POIs = less GC pressure

#### Scroll event suppression (`MainActivity.kt`)
- Webcam reloads suppressed during populate (crosshair animation was triggering every 2-3s)
- Bbox POI reloads kept active so user sees new POIs appear

#### POI zoom guard (`MainActivity.kt`)
- `loadCachedPoisForVisibleArea()` skips loading at zoom ≤ 8 and clears existing markers
- Prevents overwhelming map with thousands of POI dots at wide zoom

#### Proxy cache key fix (`cache-proxy/server.js`)
- Overpass cache key now includes radius: `overpass:lat:lon:rRADIUS:tags`
- Prevents cap-retry with smaller radius from returning same cached 200-element response

## Session: 2026-02-28l (Populate POIs — Grid Scanner)

### Context
POI coverage was built passively (manual long-press, auto-follow aircraft). No way to systematically fill a geographic area. Implemented a "Populate" utility that takes the current map center and spirals outward through a square grid, searching every cell for POIs.

### Changes Made

#### Proxy: X-Cache header (`cache-proxy/server.js`)
- Added `X-Cache: HIT` on cache hit path, neighbor cache hit path
- Added `X-Cache: MISS` on upstream response
- Enables app-side adaptive delay (fast on cache hits, slow on upstream calls)

#### Data model (`Models.kt`)
- New `PopulateSearchResult(results, cacheHit, gridKey)` data class

#### Repository (`PlacesRepository.kt`)
- New `searchPoisForPopulate()` — reuses existing Overpass query building/parsing, reads X-Cache header
- Returns `PopulateSearchResult` with cache status for adaptive timing

#### ViewModel (`MainViewModel.kt`)
- New `populateSearchAt()` — direct suspend function, not LiveData-based

#### Scanning marker (`ic_crosshair.xml`)
- New 24dp VectorDrawable: orange crosshair with center dot and cross lines

#### Menu + wiring
- `menu_utility.xml` — checkable "Populate POIs" item after divider, before Debug Log
- `MenuEventListener.kt` — `onPopulatePoisToggled(enabled: Boolean)` callback
- `AppBarMenuManager.kt` — `PREF_POPULATE_POIS` constant (defaults OFF), toggle wiring, sync
- `MarkerIconHelper.kt` — "crosshair" category entry with orange tint

#### MainActivity — Core populate logic
- State: `populateJob`, `scanningMarker`
- `startPopulatePois()` — guards against active follow, computes step from latitude, launches spiral coroutine
- `stopPopulatePois()` — cancels job, removes marker, hides banner, resets pref, triggers bbox refresh
- `generateRingPoints()` — square spiral perimeter for ring N (8N points, ring 0 = center)
- Adaptive delay: 200ms on cache HIT, 4000ms on MISS, 10000ms on error
- Auto-stop after 5 consecutive errors
- Progress banner reuses `followBanner` — ring, cells, POIs, hit rate, errors; tap to stop

### Status
- **BUILD SUCCESSFUL** — compiles clean
- Version: v1.5.13

### Files Created
- `app/src/main/res/drawable/ic_crosshair.xml`

### Files Changed
- `cache-proxy/server.js` — X-Cache header on /overpass
- `app/.../data/model/Models.kt` — PopulateSearchResult
- `app/.../data/repository/PlacesRepository.kt` — searchPoisForPopulate()
- `app/.../ui/MainViewModel.kt` — populateSearchAt()
- `app/.../ui/MarkerIconHelper.kt` — crosshair category
- `app/.../ui/menu/MenuEventListener.kt` — onPopulatePoisToggled()
- `app/.../ui/menu/AppBarMenuManager.kt` — PREF_POPULATE_POIS, wiring
- `app/src/main/res/menu/menu_utility.xml` — Populate POIs menu item
- `app/.../ui/MainActivity.kt` — populate logic (183 lines added)

---

## Session: 2026-02-28k (Aircraft "Air" Menu, Vehicle Staleness Detection)

### Context
Aircraft controls were buried in the Alerts menu alongside weather/METAR items. Auto-follow was in Utility. Needed a dedicated top-level menu. Also discovered MBTA trains can report stale GPS positions (hours old) while still appearing in the API — needed staleness detection in the follow banner and tap info.

### Changes Made

#### Dedicated "Air" Top-Level Menu (`menu_aircraft.xml`, `menu_main_toolbar.xml`, `AppBarMenuManager.kt`)
- New `menu_aircraft.xml` with: Aircraft Tracking toggle, Update Frequency slider, Auto-Follow (POI Builder)
- New 7th toolbar button "Air" between CAMs and Radar
- `showAircraftMenu()` method in AppBarMenuManager with full toggle/slider/sync logic
- Removed aircraft items from `menu_gps_alerts.xml` (was alongside weather/METAR)
- Removed Auto-Follow Aircraft from `menu_utility.xml` (moved to Air menu)
- Toolbar now: `Alerts | Transit | CAMs | Air | Radar | POI | Utility`

#### Vehicle Staleness Detection (`MainActivity.kt`)
- New `vehicleStalenessTag(isoTimestamp)` — parses ISO-8601 timestamp, returns "" if fresh (≤2 min) or " — STALE (Xm ago)" / " — STALE (Xh Ym ago)"
- Follow banner shows staleness on first line: "Following Train 1704 — Newburyport Line — STALE (5h 12m ago)"
- Tap snippet (`buildTrainSnippet`) also shows staleness after the update timestamp
- Discovered: MBTA API returns ghost vehicles with hours-old GPS data (e.g., train 1704 on shuttle-replaced weekend service)

### MBTA Investigation
- User reported missing trains near Beverly, MA
- Root cause: Newburyport/Rockport line under weekend shuttle bus replacement (MBTA alert active Feb 28–Mar 1)
- MBTA `/vehicles` API only reports vehicles actively broadcasting GPS — schedule shows service but no real-time vehicles
- Train 1704 was a stale ghost entry (GPS frozen at 11:12 AM, API still reporting at 4 PM with 45 mph speed)

### Status
- **BUILD SUCCESSFUL** — compiles clean
- **Installed on emulator** — Air menu verified working, 7 toolbar buttons visible
- **Not yet committed**

### Files Created
- `app/src/main/res/menu/menu_aircraft.xml`

### Files Changed
- `app/src/main/res/menu/menu_main_toolbar.xml` — added Air button
- `app/src/main/res/menu/menu_gps_alerts.xml` — removed aircraft items
- `app/src/main/res/menu/menu_utility.xml` — removed auto-follow aircraft item
- `app/.../ui/menu/AppBarMenuManager.kt` — showAircraftMenu(), removed aircraft from GPS Alerts and Utility handlers
- `app/.../ui/MainActivity.kt` — vehicleStalenessTag(), staleness in follow banner and tap snippet

---

## Session: 2026-02-28j (OpenSky Rate Limiter, Webcam Enhancements, Testing Fixes)

### Context
OpenSky API was being hammered with requests — 5 independent app-side request paths (periodic refresh, scroll debounce, followed aircraft, auto-follow wide bbox, POI prefetch) with zero backoff at either proxy or app level. 429 responses cascaded into more requests. Also tested webcam layer for the first time, found and fixed several issues.

### Changes Made

#### OpenSky Rate Limiter (`cache-proxy/server.js`)
- **Proxy-level rate limiter** — single throttle point for all aircraft requests
- Rolling 24h request counter, 90% safety margin (3,600 of 4,000 authenticated limit)
- Minimum interval between upstream requests (~24s authenticated, ~960s anonymous)
- **Exponential backoff** on 429 responses: 10s → 20s → 40s → 80s → 160s → 300s cap
- **Stale cache fallback**: when throttled, returns expired cached data (app doesn't see errors)
- `Retry-After` header when no cache available
- Backoff resets on successful response
- `openskyCanRequest()`, `openskyRecordRequest()`, `openskyRecord429()`, `openskyRecordSuccess()`
- Rate state exposed in `/cache/stats` → `opensky` object

#### Webcam Live Player (`MainActivity.kt`, `Models.kt`, `WebcamRepository.kt`, `server.js`)
- Proxy now fetches `include=images,location,categories,player,urls` from Windy API
- Response includes `playerUrl` (Windy embed player) and `detailUrl` (Windy webcam page)
- `Webcam` data class: added `playerUrl`, `detailUrl` fields
- `WebcamRepository`: parses new fields with null safety
- **Preview dialog redesigned**: 90% fullscreen dark panel with title + X close button
- **"View Live" button**: tapping swaps preview image for in-app WebView loading Windy player
- WebView: JavaScript enabled, DOM storage, no user gesture required for media
- WebView destroyed on dialog dismiss to free resources

#### Webcam Bbox Minimum (`MainActivity.kt`)
- Windy API returns 0 results for small bboxes (discovered during testing)
- `loadWebcamsForVisibleArea()` enforces minimum 0.5° span in both lat and lon
- Ensures webcams appear even at high zoom levels

#### Webcam Zoom Reload (`MainActivity.kt`)
- `scheduleWebcamReload()` was only called from `onScroll`, not `onZoom`
- Added call in `onZoom` handler — webcams now reload on zoom changes too

#### Webcam Categories (`AppBarMenuManager.kt`)
- Updated to match actual Windy API v3 categories (was 18 with 5 dead ones)
- **Added**: coast, port, river, village, square, observatory, sportArea
- **Removed** (don't exist in Windy API): outdoor, harbor, animals, island, golf, resort, sportsite
- `sportArea` label formatted as "Sport Area" via camelCase splitting regex

#### Aircraft + Auto-Follow Default OFF (`MainActivity.kt`, `AppBarMenuManager.kt`)
- Aircraft display defaults **OFF** on fresh install (was ON)
- Auto-follow aircraft already defaulted OFF
- New `prefDefault(prefKey)` helper in AppBarMenuManager — returns correct default per pref key
- `syncCheckStates()` and `toggleBinary()` use `prefDefault()` instead of hardcoded `true`

#### Logging Enhancement (`cache-proxy/server.js`)
- `log()` function accepts optional `extra` parameter for contextual suffixes
- Rate limiter events logged with context: `[stale (backoff 19s)]`, `[throttled (min interval)]`, `[upstream 429]`

### Test Results
- **Rate limiter**: verified — only 2 upstream requests in 60s when app was hammering (was 10-20+)
- **Exponential backoff**: confirmed escalation 10s → 20s → 40s → 80s on consecutive 429s
- **Webcam markers**: appear on map after bbox fix (4-9 visible in Massachusetts)
- **Webcam preview dialog**: 90% fullscreen, image loads, "View Live" button present
- **Webcam live player**: WebView loads Windy player in-app (no Chrome fork)
- **Vehicle follow POI prefetch**: confirmed working via logcat (57K chars of POI data returned)

### Status
- **BUILD SUCCESSFUL** — compiles clean
- **Committed & pushed**: `43e7ff6` on `master`
- **Proxy running** with rate limiter + OAuth2

### Files Changed
- `cache-proxy/server.js` — rate limiter, log enhancement, webcam player+urls fields, rate stats in /cache/stats
- `app/.../data/model/Models.kt` — Webcam: added playerUrl, detailUrl
- `app/.../data/repository/WebcamRepository.kt` — parse playerUrl, detailUrl
- `app/.../ui/MainActivity.kt` — 90% webcam dialog, WebView live player, bbox minimum, zoom reload, aircraft default OFF
- `app/.../ui/menu/AppBarMenuManager.kt` — updated webcam categories, prefDefault(), aircraft default OFF

---

## Session: 2026-02-28i (Webcam Layer — Windy Webcams API Integration)

### Context
Adding a webcam layer to the map using the Windy Webcams API (free tier). The CAMs menu button was already stubbed — wired it to real functionality with multi-select webcam categories and camera preview on tap.

### Changes Made

#### Proxy Route (`cache-proxy/server.js`)
- `GET /webcams?s=&w=&n=&e=&categories=` — proxies Windy Webcams API v3
- API key: `x-windy-api-key` header (free tier)
- Upstream URL: `api.windy.com/webcams/api/v3/webcams?bbox=...&category=...&limit=50&include=images,location,categories`
- 10-minute TTL cache (matches image URL expiry on free tier)
- Response transformed to simplified JSON array: `[{ id, title, lat, lon, categories, previewUrl, thumbnailUrl, status, lastUpdated }]`
- Startup log updated to include `/webcams` route

#### Data Model (`Models.kt`)
- New `Webcam` data class: id (Long), title, lat, lon, categories (List<String>), previewUrl, thumbnailUrl, status, lastUpdated, `toGeoPoint()`

#### Repository (`WebcamRepository.kt` — new file)
- `@Singleton` with `@Inject constructor()`, OkHttp client (15s/30s timeouts)
- `fetchWebcams(south, west, north, east, categories)` — hits proxy `/webcams`, parses JSON array

#### DI + ViewModel (`AppModule.kt`, `MainViewModel.kt`)
- `provideWebcamRepository()` added to AppModule
- `WebcamRepository` injected into MainViewModel
- `_webcams` / `webcams` LiveData, `loadWebcams()`, `clearWebcams()`

#### Menu System (`menu_cams.xml`, `MenuEventListener.kt`, `AppBarMenuManager.kt`)
- `menu_cams.xml` replaced: "Webcams" checkable toggle + "Camera Types..." action
- `MenuEventListener`: replaced `onTrafficCamsToggled` + `onCamsMoreRequested` with `onWebcamToggled(enabled)` + `onWebcamCategoriesChanged(categories: Set<String>)`
- `AppBarMenuManager.showCamsMenu()` rewired for new menu items
- `showWebcamCategoryDialog()` — AlertDialog with all 18 Windy categories as multi-select checkboxes
  - "traffic" pre-selected by default
  - "Select All / Deselect All" neutral button
  - Stored as `StringSet` pref `"webcam_categories"`
- Pref keys: `PREF_WEBCAMS_ON`, `PREF_WEBCAM_CATEGORIES` (replaces old `PREF_TRAFFIC_CAMS`)

#### MainActivity — Markers & Tap Dialog
- `webcamMarkers` list, `webcamReloadJob`, `pendingWebcamRestore` state variables
- Observer: clears + adds markers on LiveData update
- `addWebcamMarker()`: camera icon (20dp, existing "camera" category mapping), tap opens preview dialog
- `showWebcamPreviewDialog()`: AlertDialog with ImageView + info text, async OkHttp image download in coroutine
- `loadWebcamsForVisibleArea()`: gets bbox from map, reads active categories from prefs
- `scheduleWebcamReload()`: 500ms debounce on scroll (same pattern as POI bbox)
- Deferred restore in `onStart()` via `pendingWebcamRestore` — fires after GPS fix + 2s
- Toggle off: cancels pending loads, clears LiveData + markers
- Category change: reloads if webcams enabled, clears if empty category set

### Status
- **BUILD SUCCESSFUL** — compiles clean (only pre-existing deprecation warning)
- **APK installed on emulator** — ready for testing
- **Proxy restarted** with webcams route — verified: returns real webcam data for Massachusetts bbox
- **Committed & pushed**: `4cce176` on `master` → `github.com/deanmauriceellis-cloud/LocationMapApp`
- **GitHub auth configured**: `gh auth login` + `gh auth setup-git` (credential helper persists)
- **Not yet tested on device** — needs manual testing of markers, scroll reload, tap dialog, category dialog

### Files Created
- `app/.../data/repository/WebcamRepository.kt`

### Files Changed
- `cache-proxy/server.js` — `/webcams` route, startup log
- `app/.../data/model/Models.kt` — `Webcam` data class
- `app/.../di/AppModule.kt` — `provideWebcamRepository()`
- `app/.../ui/MainViewModel.kt` — webcam LiveData + methods
- `app/src/main/res/menu/menu_cams.xml` — replaced with Webcams toggle + Camera Types
- `app/.../ui/menu/MenuEventListener.kt` — new webcam callbacks
- `app/.../ui/menu/AppBarMenuManager.kt` — showCamsMenu(), showWebcamCategoryDialog(), new pref keys
- `app/.../ui/MainActivity.kt` — webcam markers, observer, scroll reload, tap dialog, deferred restore

---

## Session: 2026-02-28h (Viewport-Only POI Markers with Eviction, LRU Icon Cache)

### Context
Emulator OOM after ~3 hours with all layers active + 22K POIs. POI Marker objects accumulated across all 16 category layers + the `all_cached` layer and were never evicted. The proxy already has a `GET /pois/bbox` endpoint that returns POIs within the visible bounding box, and `loadCachedPoisForVisibleArea()` fires on every scroll/zoom with 500ms debounce. Used this as the recovery mechanism — evict everything off-screen and let the bbox fetch re-materialize markers when the user scrolls back.

### Changes Made

#### Viewport-Only POI Display (`MainActivity.kt`, `MainViewModel.kt`)
- **Places observer refactored**: two-path handler based on layerId
  - `layerId == "bbox"` (from viewport bbox fetch): calls `replaceAllPoiMarkers()` — clears ALL POI markers from every layer, adds only visible results under single `"bbox"` key
  - Any other layerId (from `searchPoisAt`): skips marker creation, schedules bbox refresh after 1s delay so newly cached data appears
- **New `replaceAllPoiMarkers(places)`**: clears `poiMarkers` map entirely, removes all POI markers from overlays, adds only viewport results
- **New `clearAllPoiMarkers()`**: helper to remove all POI markers from all layers at once
- **`onPoiLayerToggled()` simplified**: toggle-off no longer calls `clearPoiMarkers(layerId)` — markers are viewport-driven, category toggles only control searching
- **Renamed layerId**: `"all_cached"` → `"bbox"` in `loadCachedPoisForBbox()` for clarity

#### LRU Icon Cache (`MarkerIconHelper.kt`)
- Converted `cache` from `HashMap<String, BitmapDrawable>` to access-order `LinkedHashMap` with `removeEldestEntry()` override
- Capped at 500 entries — evicts least-recently-used when exceeded
- Prevents `labeledDot()` cache from growing unbounded with unique POI names (was 22K+ entries)

### Memory Impact
| Metric | Before | After |
|--------|--------|-------|
| POI Marker objects | ~22,000 (all categories accumulated) | ~100-400 (viewport only) |
| Icon cache entries | unbounded (22K+ labeled dots) | capped at 500 (LRU) |
| Estimated POI RAM | ~50-100 MB | ~1-2 MB |

### Build Environment Note
- Gradle requires Java 21 (`gradle/gradle-daemon-jvm.properties`)
- System Java 17 is NOT sufficient
- Must use JBR (JetBrains Runtime) 21.0.9 bundled with Android Studio:
  `JAVA_HOME=/home/witchdoctor/AndroidStudio/android-studio/jbr ./gradlew assembleDebug`

### Status
- **BUILD SUCCESSFUL** — compiles clean with 2 warnings (deprecated `setBuiltInZoomControls`, always-true condition)
- **Not yet tested on emulator** — needs extended run to verify OOM fix

### Files Changed
- `app/.../ui/MainActivity.kt` — observer refactored, `replaceAllPoiMarkers()`, `clearAllPoiMarkers()`, simplified `onPoiLayerToggled()`
- `app/.../ui/MainViewModel.kt` — renamed `"all_cached"` → `"bbox"`
- `app/.../ui/MarkerIconHelper.kt` — LRU cache cap at 500 entries

---

## Session: 2026-02-28g (PostgreSQL Query API, Aircraft Sightings DB, OpenSky OAuth2, Smart Auto-Follow)

### Context
POI cache had grown to 8,198 POIs (7,797 in PostgreSQL). The `pg` dependency was installed but unused — all endpoints used in-memory JSON. Added DB-backed query endpoints, real-time aircraft sighting tracking, OpenSky OAuth2 authentication, and smarter auto-follow logic.

### Changes Made

#### PostgreSQL POI Query API (`cache-proxy/server.js`, `cache-proxy/schema.sql`)
- Added `pg` Pool init with `DATABASE_URL` env var (max 5 connections, 5s timeout)
- `requirePg` middleware: `/db/*` routes return 503 if no DATABASE_URL
- Added compound index `idx_pois_lat_lon ON pois (lat, lon)` for bbox queries
- **6 new `/db/*` endpoints** (all parameterized SQL, Haversine distance):
  - `GET /db/pois/search` — combined filtered search (q, category, category_like, bbox, lat/lon, radius, tag, tag_value, limit, offset)
  - `GET /db/pois/nearby` — nearby POIs sorted by distance with bbox pre-filter
  - `GET /db/poi/:type/:id` — single POI lookup with first_seen/last_seen
  - `GET /db/pois/stats` — 5 parallel queries: total, named, top categories, bounds, time range
  - `GET /db/pois/categories` — GROUP BY with key/value split
  - `GET /db/pois/coverage` — rounded lat/lon grid with configurable resolution
- Response format matches Overpass JSON (`{ count, elements: [{ type, id, lat, lon, tags }] }`)

#### Aircraft Sightings Database (`cache-proxy/server.js`, `cache-proxy/schema.sql`)
- New `aircraft_sightings` table: serial PK, icao24, callsign, origin_country, first/last seen+lat+lon+altitude+heading, velocity, vertical_rate, squawk, on_ground
- Each continuous observation = separate row; 5-min gap = new sighting (enables flight history)
- In-memory `activeSightings` map tracks which DB row to update
- `trackAircraftSightings()` called on every aircraft response (cache hits AND misses)
- Stale sighting purge every 10 minutes
- Indexes: icao24, callsign, first_seen, last_seen, last_lat+lon
- Results after ~8 hours: 28,690 sightings, 8,337 unique aircraft, 9,342 unique callsigns

#### OpenSky OAuth2 Authentication (`cache-proxy/server.js`)
- Replaced basic auth with OAuth2 client credentials flow
- Token endpoint: `auth.opensky-network.org/.../openid-connect/token`
- `getOpenskyToken()`: caches token, auto-refreshes 5 min before expiry (30-min tokens)
- `OPENSKY_CLIENT_ID` + `OPENSKY_CLIENT_SECRET` env vars
- Graceful degradation: no credentials = anonymous (100 req/day)
- Authenticated: 4,000 req/day

#### Smart Auto-Follow Improvements (`MainActivity.kt`)
- **Wider search bbox**: 1.5°×2° → 6°×8° (covers most of the northeast/CONUS)
- **Lower altitude floor**: 20,000 ft → 10,000 ft (more candidates at night)
- **Altitude switch**: below 10,000 ft → picks any new aircraft
- **Over water switch**: 0 POIs → `pickFurthestWestAircraft()` (most inland candidate)
- **US boundary check**: lat >49°, <25°, lon >-66°, <-125° → `pickInteriorAircraft()` (closest to geographic center of US ~39°N, -98°W)
- `pickAndFollowRandomAircraft(westboundOnly)` parameter for forced westbound selection
- `selectAndFollow(candidates, westboundOnly)` enforces westbound-only when flag set

### Test Results
- All 6 `/db/*` endpoints tested with curl — search, nearby, stats, categories, coverage, single lookup all working
- Existing endpoints (`/pois/bbox`, `/pois/stats`, `/cache/stats`) unchanged
- OpenSky OAuth2: token refresh working, HTTP 200 on authenticated requests
- Aircraft DB: INSERT + UPDATE paths verified, positions updating in real-time
- Auto-follow ran for ~8 hours: POI cache grew from 8,198 → 22,494; aircraft sightings collected 28,690 rows
- Emulator OOM after ~3 hours with all layers active (memory pressure kill, not crash)

### Files Changed
- `cache-proxy/server.js` — PG pool, 6 `/db/*` endpoints, aircraft sighting tracker, OpenSky OAuth2, startup log
- `cache-proxy/schema.sql` — lat/lon compound index, aircraft_sightings table + indexes
- `app/.../ui/MainActivity.kt` — smart auto-follow (altitude check, furthest-west, interior US, wider bbox, lower altitude floor)

---

## Session: 2026-02-28f (Auto-Follow Aircraft POI Builder, Labeled POI Markers)

### Context
User wants to passively build the POI cache by automatically following random high-altitude aircraft. Also wants POI markers to show category type and business name at high zoom levels.

### Changes Made

#### Auto-Follow Aircraft — POI Builder (`menu_utility.xml`, `AppBarMenuManager.kt`, `MenuEventListener.kt`, `MainActivity.kt`)
- New checkable Utility menu item "Auto-Follow Aircraft (POI Builder)"
- `PREF_AUTO_FOLLOW_AIRCRAFT` constant (defaults false/off)
- `onAutoFollowAircraftToggled()` callback wired through MenuEventListener
- `startAutoFollowAircraft()` — ensures aircraft layer on, picks immediately, starts 20-min rotation job
- `stopAutoFollowAircraft()` — cancels job, stops follow, toasts
- `pickAndFollowRandomAircraft()` — computes zoom-11-equivalent bbox centered on map (1.5° × 2°) without changing user's zoom, queries aircraft, filters ≥ 20,000 ft altitude
- `selectAndFollow()` — prioritizes westbound aircraft (track 180–360°) since this is New England east coast, excludes currently followed icao24 for variety
- `filterHighAltitude()` — filters by `baroAltitude * 3.28084 >= 20000`
- Banner prefix: "Auto-following ✈" when auto-follow active, "Following ✈" for manual
- Edge cases:
  - Aircraft lost from feed → if auto-follow active, immediately picks replacement
  - No-POI zone: after 2 consecutive empty POI prefetches, switches to new aircraft
  - Aircraft layer toggled off (menu or FAB) → cancels auto-follow, clears pref
  - `onStart()` restore: deferred 5s after GPS fix so aircraft data has loaded
- **3-strike failure tolerance**: `followedAircraftFailCount` tracks consecutive null responses from icao24 query; only declares "lost" after 3 failures (handles HTTP 429 rate limits)

#### Labeled POI Markers at Zoom 18+ (`MarkerIconHelper.kt`, `MainActivity.kt`)
- New `MarkerIconHelper.labeledDot()` — composite icon: category label → dot → name label
  - Category humanized ("fast_food" → "Fast Food"), bold, colored to match category
  - Name in dark gray below dot
  - White pill backgrounds for readability, cached by color|type|name
- `addPoiMarker()` checks `zoomLevelDouble >= 18.0` — uses `labeledDot` or `dot`
- `PlaceResult` stored on `marker.relatedObject` for icon refresh without re-query
- `refreshPoiMarkerIcons()` swaps all POI marker icons when crossing zoom threshold
- `poiLabelsShowing` flag tracked in `onZoom` handler — triggers refresh on threshold crossing

### Test Results
- Auto-follow: toggled on, queried wide bbox (20 aircraft), filtered to 1 at 35,000 ft (FIN16), followed correctly
- Pref persists across restart, auto-restores with deferred timing
- POI labels: verified at zoom 18 — "Nature Reserve" / "Sarah Doublet Forest", "Park" / "Bumblebee Park", "Place Of Worship" / "Abundant Life Assembly Church"
- Labels disappear when zooming below 18 (back to dots)
- OpenSky 429 rate limit observed — 3-strike tolerance prevents premature aircraft loss

## Session: 2026-02-28e (Enhanced Aircraft Markers, Aircraft Follow, POI Coverage Display)

### Context
Aircraft markers were basic (small icon with arrow). Needed: rotated airplane pointing to heading, callsign labels, vertical rate indicators, SPI emergency rings, aircraft follow mode, and cached POI coverage display for database building.

### Changes Made

#### Enhanced Aircraft Markers (`MarkerIconHelper.kt`)
- New `aircraftMarker()` method replaces `withArrow()` for aircraft
- Airplane icon **rotated to heading** — the plane itself points where it's flying
- **Callsign text label** above icon with white pill background
- **Vertical rate indicator**: ↑ climbing, ↓ descending, — level (next to callsign)
- **SPI emergency ring**: thick red circle around marker when Special Purpose Indicator active

#### New OpenSky Fields (`Models.kt`, `AircraftRepository.kt`)
- Added `timePosition`, `lastContact`, `spi`, `positionSource` to `AircraftState`
- Parses all 18 state vector fields (indices 3, 4, 15, 16 added)
- Tap info shows: position source (ADS-B/MLAT/ASTERIX/FLARM), data age, SPI warning

#### Aircraft Follow Mode (`MainActivity.kt`, `MainViewModel.kt`)
- Tap aircraft marker to follow — map centers, dark banner shows flight info
- **Global tracking via icao24 query** — not limited to visible bbox
  - Proxy: `/aircraft?icao24=hex` route (no bbox needed, queries OpenSky globally)
  - Dedicated `followedAircraftRefreshJob` polls at aircraft refresh interval
  - `followedAircraft` LiveData in ViewModel for icao24 query results
- Banner: callsign, altitude, speed, heading, vertical rate, SPI flag
- Tap banner to stop; auto-stops when aircraft disappears from feed
- Starting vehicle follow cancels aircraft follow and vice versa
- Toggling aircraft layer off cancels follow

#### POI Prefetch on Aircraft Follow
- Each aircraft follow refresh fires `searchPoisAt()` at the aircraft's position
- Same pattern as existing MBTA vehicle follow POI prefetch
- Fills proxy cache + poi-cache.json as the plane flies over new territory

#### Cached POI Coverage Display
- **Proxy**: new `GET /pois/bbox?s=...&w=...&n=...&e=...` endpoint
  - Returns all cached POIs within bounding box from poi-cache.json
  - Server-side filtering — app only receives visible subset
- **App**: `loadCachedPoisForVisibleArea()` calls bbox endpoint
  - Fires on startup (deferred after GPS fix)
  - Fires on scroll/zoom (500ms debounce)
  - Fires 3s after each follow prefetch (aircraft or vehicle)
- Replaces old per-grid-cell cache-only Overpass queries
- No in-memory cache of all POIs — proxy handles filtering

#### PostgreSQL Import
- 7797 POIs imported (up from 1334)
- DB user `witchdoctor` created with password auth

### Status
- **Builds clean** — BUILD SUCCESSFUL
- **Tested on emulator** — aircraft follow working, POI prefetch populating cache along flight paths
- **7797 POIs in PostgreSQL** after import

### Files Created
- None (all changes to existing files)

### Files Changed
- `app/.../data/model/Models.kt` — 4 new AircraftState fields
- `app/.../data/repository/AircraftRepository.kt` — parse new fields + fetchAircraftByIcao()
- `app/.../data/repository/PlacesRepository.kt` — fetchCachedPoisInBbox()
- `app/.../ui/MarkerIconHelper.kt` — aircraftMarker() method
- `app/.../ui/MainActivity.kt` — aircraft follow mode, cached POI bbox display, scroll handler
- `app/.../ui/MainViewModel.kt` — followedAircraft LiveData, loadCachedPoisForBbox()
- `cache-proxy/server.js` — /aircraft?icao24= support, /pois/bbox endpoint

---

## Session: 2026-02-28d (OpenSky Aircraft Tracking, GPS Center Fix)

### Context
Adding live aircraft positions to the map using the OpenSky Network API. Aircraft displayed as airplane markers with directional arrows showing heading, color-coded by altitude. Also fixed a long-standing issue where GPS updates constantly re-centered the map.

### Changes Made

#### Aircraft Tracking — Full Stack
- **Proxy** (`cache-proxy/server.js`): Added `GET /aircraft?bbox=s,w,n,e` route
  - Upstream: `opensky-network.org/api/states/all?lamin=...`
  - 15-second TTL cache per bbox
- **Model** (`Models.kt`): `AircraftState` data class — icao24, callsign, origin, lat/lon, altitude (baro+geo), velocity, track, vertical rate, squawk, category
- **Repository** (`AircraftRepository.kt`): new `@Singleton`, parses OpenSky state vectors (mixed-type JSON arrays)
  - Guards index 17 (category) with `s.size() > 17` — not always present
  - Filters null lat/lon entries
- **ViewModel** (`MainViewModel.kt`): `_aircraft`/`aircraft` LiveData, `loadAircraft()`, `clearAircraft()`
  - Injected `AircraftRepository` via Hilt
- **Menu** (`menu_gps_alerts.xml`): Aircraft Tracking toggle + frequency slider
  - `MenuEventListener.kt`: `onAircraftDisplayToggled()`, `onAircraftFrequencyChanged()`
  - `AppBarMenuManager.kt`: `PREF_AIRCRAFT_DISPLAY`, `PREF_AIRCRAFT_FREQ`, slider range 30–300s
- **Drawable** (`ic_aircraft.xml`): 24dp airplane silhouette vector icon
- **MarkerIconHelper.kt**: Added `aircraft` category entry
- **MainActivity.kt**: Full integration
  - `aircraftMarkers` list, `aircraftRefreshJob`, `aircraftRefreshIntervalSec` (default 60s)
  - `addAircraftMarker()`: altitude-colored (green/blue/purple/gray), `withArrow()` for heading
  - `buildAircraftSnippet()`: altitude ft, speed kt+mph, heading, vertical rate fpm, squawk, origin, category name
  - `startAircraftRefresh()`/`stopAircraftRefresh()`: coroutine loop at configurable interval
  - `loadAircraftForVisibleArea()`: zoom ≥ 10 guard
  - `scheduleAircraftReload()`: 1s debounced reload on scroll/zoom (via MapListener)
  - `pendingAircraftRestore`: deferred load after GPS fix + 1.5s settle
  - `toggleAircraftFromFab()`: FAB quick-toggle mirrors menu logic
  - Menu callbacks: `onAircraftDisplayToggled`, `onAircraftFrequencyChanged`

#### GPS Center Fix
- Map was re-centering on every GPS update (~60s), preventing the user from panning away
- Added `initialCenterDone` flag — map only auto-centers on the **first** GPS fix
- Subsequent GPS updates still move the GPS marker but don't pan the map

#### Defaults
- Aircraft tracking defaults **ON** (all layers default ON)
- Frequency slider: 30s–5min range, default 60s

### Bug Fixes During Testing
- **IndexOutOfBoundsException**: OpenSky state vectors sometimes have 17 elements (indices 0–16), category at index 17 missing. Guarded with `s.size() > 17`.
- **Stuck bbox**: Aircraft refresh loop wasn't picking up user's zoom/scroll changes. Fixed by adding `scheduleAircraftReload()` in MapListener's `onScroll`/`onZoom`.

### Status
- **Builds clean** — BUILD SUCCESSFUL
- **Tested on emulator** — 26 aircraft visible near Boston at zoom 10-11
- **Next**: enhance markers to show rotated airplane icon with callsign + altitude text labels

### Files Created
- `app/.../data/repository/AircraftRepository.kt`
- `app/src/main/res/drawable/ic_aircraft.xml`

### Files Changed
- `cache-proxy/server.js` — /aircraft route
- `app/.../data/model/Models.kt` — AircraftState
- `app/.../ui/MainViewModel.kt` — aircraft LiveData + loadAircraft
- `app/src/main/res/menu/menu_gps_alerts.xml` — aircraft menu items
- `app/.../ui/menu/MenuEventListener.kt` — aircraft callbacks
- `app/.../ui/menu/AppBarMenuManager.kt` — aircraft prefs + handling
- `app/.../ui/MarkerIconHelper.kt` — aircraft category
- `app/.../ui/MainActivity.kt` — aircraft markers, refresh, restore, FAB, GPS center fix

---

## Session: 2026-02-28c (METAR deferred load, human-readable snippets, vehicle direction arrows)

### Context
METAR stations were not appearing on startup because `loadMetarsForVisibleArea()` fired during `onStart()` before the map had a valid bounding box (returned `0,0,0,0`). METAR tap info was compact/abbreviated. MBTA vehicle markers had no indication of travel direction.

### Changes Made

#### METAR Deferred Load
- Added `pendingMetarRestore` flag alongside existing `pendingPoiRestore`
- `onStart()` now defers METAR load instead of calling `loadMetarsForVisibleArea()` immediately
- METAR fires after GPS fix + `postDelayed(1500ms)` to let `animateTo()` animation settle
- Verified: bbox now correctly reflects Beverly area (42.55,-71.01) instead of 0,0,0,0

#### METAR HTTP 204 Handling
- `WeatherRepository.fetchMetars()`: changed `response.body!!.string()` to `response.body?.string().orEmpty()`
- Returns empty list when body is blank instead of crashing on `JsonParser.parseString("")`

#### Human-Readable METAR Tap Info
- Rewrote `buildMetarSnippet()` in MainActivity
- Wind: compass direction ("Southwest") instead of degrees (200°)
- Sky: decoded ("Scattered clouds") instead of abbreviation ("SCT")
- Weather phenomena: expanded via `decodeWx()` helper ("Light Rain" not "-RA")
- Flight category: explained ("VFR (Visual Flight Rules)")
- Observation time: formatted to local time ("9:53 PM")
- Added `degreesToCompass()` and `decodeWx()` helper methods
- Raw METAR kept at bottom for reference

#### Vehicle Direction Arrows
- Added `MarkerIconHelper.withArrow()` method
- Composites a small triangular arrow above the base vehicle icon, rotated to bearing
- Arrow is 8dp, same color as the vehicle icon
- Applied to all three vehicle types: trains (30dp), subway (26dp), buses (22dp)
- Arrow not cached by bearing to avoid excessive cache entries — cached per (resId, size, color, bearing)

### Status
- **Builds clean** — BUILD SUCCESSFUL
- **Tested** — METAR loads correctly after GPS fix, KBVY shows for Beverly area

### Files Changed
- `app/.../data/repository/WeatherRepository.kt` — HTTP 204 handling
- `app/.../ui/MainActivity.kt` — deferred METAR, human-readable snippet, arrow calls
- `app/.../ui/MarkerIconHelper.kt` — `withArrow()` method

---

## Session: 2026-02-28b (Defaults ON, Layer-aware LiveData, METAR Overhaul)

### Context
After expanding to 16 POI categories, all layers defaulted to OFF on fresh install. POI markers overwrote each other because all 16 categories shared a single LiveData. METAR was failing with HTTP 400 (API now requires bbox). METAR markers showed only a small icon with no weather data visible on the map.

### Changes Made

#### All Layers Default ON
- POI categories: `getBoolean(prefKey, false)` → `true` in MainActivity restore + AppBarMenuManager toggle/sync
- MBTA trains, subway, buses: restore defaults changed to `true`
- Radar and METAR: restore defaults changed to `true`
- Fresh install now shows everything immediately

#### Layer-Aware POI LiveData
- `_places` changed from `MutableLiveData<List<PlaceResult>>` to `MutableLiveData<Pair<String, List<PlaceResult>>>`
- `searchPoisAt()` now takes `layerId` parameter, emits `layerId to results`
- `searchPoisFromCache()` emits with `"cache"` layerId
- Observer destructures pair: `{ (layerId, places) -> }` — only clears/replaces that specific layer
- Removed `activePoiLayerId` variable (no longer needed)
- Fixes: all 16 categories now coexist on map simultaneously

#### METAR Bbox Passthrough
- Proxy: replaced static `proxyGet('/metar', ...)` with custom route accepting `?bbox=lat0,lon0,lat1,lon1`
- Proxy caches per-bbox key with 1h TTL
- App: `fetchMetars()` now takes `(south, west, north, east)` bounds
- ViewModel: `loadAllUsMetars()` → `loadMetars(south, west, north, east)`
- MainActivity: `loadMetarsForVisibleArea()` helper gets map bounding box

#### Rich METAR Station Markers
- `MetarStation` model: added `name`, `windGustKt`, `slpMb`, `skyCover`, `wxString`
- Parser: fixed `fltCat` field name (was `fltcat`), handles `visib` as string (`"10+"`), parses all new fields
- Map marker: text-based bitmap with temp (°F), wind arrow+speed, sky/wx — color-coded border by flight category
- Tap snippet: full METAR details (temp °F/°C, dewpoint, wind/gusts, vis, sky, wx, altimeter, SLP, raw METAR)
- `windDirToArrow()` helper converts degrees to unicode arrows

### Status
- **Builds clean** — BUILD SUCCESSFUL
- **Tested** — all layers load on fresh install, METAR stations display with weather data

### Files Changed
- `app/.../data/model/Models.kt` — MetarStation expanded
- `app/.../data/repository/WeatherRepository.kt` — bbox param, new field parsing
- `app/.../ui/MainActivity.kt` — defaults ON, layer-aware observer, METAR station icons, loadMetarsForVisibleArea()
- `app/.../ui/MainViewModel.kt` — Pair LiveData, loadMetars(bbox)
- `app/.../ui/menu/AppBarMenuManager.kt` — toggle/sync defaults to true
- `cache-proxy/server.js` — METAR bbox route

---

## Session: 2026-02-27b (16 POI Categories with Submenu Refinement)

### Context
App had 6 POI toggles (Restaurants, Gas, Transit, Civic, Parks, Earthquakes). Expanded to 16 useful categories, dropped Earthquakes entirely. Categories with natural subtypes get an AlertDialog submenu for refinement.

### Changes Made

#### New: `PoiCategories.kt` — Central Category Config
- `PoiCategory` data class: id, label, prefKey, tags, subtypes, color
- `PoiSubtype` data class: label, tags (for submenu checkboxes)
- `PoiCategories.ALL` — single source of truth for all 16 categories
- Menu, toggles, restore, queries, and marker colors all driven from this list

#### `MenuEventListener.kt` — PoiLayerId Expanded
- 6 constants → 16: `FOOD_DRINK`, `FUEL_CHARGING`, `TRANSIT`, `CIVIC`, `PARKS_REC`, `SHOPPING`, `HEALTHCARE`, `EDUCATION`, `LODGING`, `PARKING`, `FINANCE`, `WORSHIP`, `TOURISM_HISTORY`, `EMERGENCY`, `AUTO_SERVICES`, `ENTERTAINMENT`
- Old IDs removed: `RESTAURANTS`, `GAS_STATIONS`, `EARTHQUAKES`, `TRANSIT_ACCESS`, `PARKS`

#### `menu_poi.xml` — 16 Menu Items
- Categories with subtypes show `▸` suffix (e.g., "Food & Drink ▸")

#### `AppBarMenuManager.kt` — Data-Driven POI Menu
- Old 6 `PREF_POI_*` constants removed (now driven by `PoiCategory.prefKey`)
- `showPoiMenu()` rewritten: `menuIdToCategory` lookup map, iterates `PoiCategories.ALL`
- Simple categories toggle directly; subtype categories open `showPoiSubtypeDialog()`
- `showPoiSubtypeDialog()`: AlertDialog with multi-choice checkboxes, stores selections as `StringSet` pref
- `getActiveTags(categoryId)`: returns Overpass tags filtered by selected subtypes

#### `MarkerIconHelper.kt` — ~80 Category Mappings
- Expanded from 20 to ~80 entries covering all subtypes across all 16 categories
- Each subtype maps to its parent category's color
- Earthquake entry removed

#### `PlacesRepository.kt` — Expanded Category Extraction, Earthquake Code Removed
- `parseOverpassJson()` category chain: `amenity → shop → tourism → leisure → historic → office → "place"`
- `searchGasStations()` removed (use `searchPois()` with `amenity=fuel`)
- `fetchEarthquakes()` and `parseEarthquakeJson()` removed entirely

#### `MainViewModel.kt` — Removed Gas/Earthquake LiveData
- `gasStations` LiveData and `loadGasStations()` removed
- `earthquakes` LiveData and `loadEarthquakesForMap()` removed
- `searchPoisAt()` is the unified entry point for all POI searches

#### `MainActivity.kt` — Unified Marker Tracking
- 3 separate marker lists → `poiMarkers: MutableMap<String, MutableList<Marker>>`
- `activePoiLayerId` tracks which layer owns current places observer result
- `clearPoiMarkers(layerId)` and `addPoiMarker(layerId, place)` — per-layer ops
- `onPoiLayerToggled()` rewritten: lookup category, get active tags, unified search
- FAB speed dial: removed Earthquakes and Gas Stations buttons
- `onStart()` restore: iterates all 16 categories from `PoiCategories.ALL`
- Deferred restore: fires searches for all enabled categories with subtype filtering

### Status
- **Builds clean** (`./gradlew assembleDebug` — BUILD SUCCESSFUL)
- **Not yet committed** — changes are unstaged

### Files Changed
- `app/src/main/java/.../ui/menu/PoiCategories.kt` (new)
- `app/src/main/java/.../ui/menu/MenuEventListener.kt`
- `app/src/main/java/.../ui/menu/AppBarMenuManager.kt`
- `app/src/main/res/menu/menu_poi.xml`
- `app/src/main/java/.../ui/MarkerIconHelper.kt`
- `app/src/main/java/.../data/repository/PlacesRepository.kt`
- `app/src/main/java/.../ui/MainViewModel.kt`
- `app/src/main/java/.../ui/MainActivity.kt`

---

## Session: 2026-02-28 (POI Database — PostgreSQL)

### Context
The proxy's individual POI cache (`poi-cache.json`) had grown to 1334 unique POIs with rich Overpass data (280 unique tag keys, avg 7.3 tags/POI). Needed permanent storage for querying, analytics, and eventual API endpoints.

### Changes Made

#### PostgreSQL Schema (`cache-proxy/schema.sql`)
- `pois` table with composite PK `(osm_type, osm_id)` — globally unique OSM identifiers
- Promoted columns: `name` (from `tags.name`), `category` (derived: first match of amenity/shop/tourism/leisure/historic/office → `"key=value"`)
- `tags` JSONB column preserves all OSM tag keys; GIN index for flexible queries
- `first_seen`/`last_seen` TIMESTAMPTZ for discovery tracking
- Indexes: `category`, `name` (partial WHERE NOT NULL), `tags` (GIN)

#### Import Script (`cache-proxy/import-pois.js`)
- Standalone Node.js script, no dependency on proxy server code
- Fetches `http://localhost:3000/pois/export`, parses all POIs
- Extracts lat/lon (top-level for nodes, `center` for ways)
- Derives category from first matching tag key (amenity > shop > tourism > leisure > historic > office)
- Batch UPSERT in single transaction: `INSERT ... ON CONFLICT DO UPDATE` (preserves original `first_seen`)
- Connection via `DATABASE_URL` environment variable
- Prints summary: upserted count, total in database

#### Dependencies (`cache-proxy/package.json`)
- Added `pg` ^8.13.0 (node-postgres)

### Results
- Schema applied, all indexes created
- 1334 POIs imported successfully
- Top categories: parking (167), restaurant (94), bench (89), pitch (65), school (58)
- Re-import verified idempotent: count stays at 1334, no duplicates

### Files Changed
- `cache-proxy/schema.sql` (new)
- `cache-proxy/import-pois.js` (new)
- `cache-proxy/package.json`
- `cache-proxy/package-lock.json`

---

## Session: 2026-02-27 (Adaptive POI Radius)

### Context
POI search used a hardcoded 3000m radius. Dense metros (Boston downtown) triggered 429/504 errors from Overpass; rural areas returned zero results.

### Changes Made

#### Adaptive Radius — Proxy (`cache-proxy/server.js`)
- Added `radiusHints` Map with separate disk persistence (`radius-hints.json`)
- Grid key: 3dp lat/lon (~111m cells), same grid as POI cache
- `GET /radius-hint?lat=X&lon=Y` → returns `{ radius }` (default 3000)
- `POST /radius-hint` with `{ lat, lon, resultCount, error }` → applies adaptation rules:
  - Error → shrink 30% (× 0.7)
  - 0–4 results → grow 30% (× 1.3)
  - 5+ results → confirm (no change, refresh timestamp)
- Bounds: min 500m, max 15000m
- `GET /radius-hints` → admin dump of all hints
- `/cache/stats` now includes `radiusHints` count
- `/cache/clear` now also clears hints + deletes `radius-hints.json`

#### Adaptive Radius — App (`PlacesRepository.kt`)
- Replaced `RADIUS_M = 3000` with companion constants: `DEFAULT_RADIUS_M`, `MIN_RADIUS_M`, `MAX_RADIUS_M`, `MIN_USEFUL_POI_COUNT`
- Added `radiusHintCache` (ConcurrentHashMap, session-level)
- `fetchRadiusHint()`: GET from proxy, cache locally in `radiusHintCache`
- `postRadiusFeedback()`: POST result count / error to proxy, update local cache with response
- `buildOverpassQuery()` now takes `radiusM` parameter
- `searchPois()`: fetch hint → build query → execute → post feedback
- `searchPoisCacheOnly()`: uses local hint cache (no network call for hint)
- No changes to MainViewModel or MainActivity — adaptation is transparent

### Files Changed
- `cache-proxy/server.js`
- `app/src/main/java/.../data/repository/PlacesRepository.kt`

---

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

---

## Session: 2026-03-02 (v1.5.40 — Slim Toolbar + Status Line + Grid Dropdown)

### Context
The two-row icon toolbar (10 white icons in 2×5 grid, v1.5.35) was functional but cryptic — icons had no labels and the redesign had eliminated the status line that showed live tracking info. Banners were dynamic TextViews added/removed from the CoordinatorLayout. User wanted the status area restored and icons converted to a labeled button grid for better discoverability.

### Changes Made

#### New Layout: Slim Toolbar → Status Line → Grid Dropdown → Map
- **Slim toolbar** (40dp): Weather icon (left) | spacer | Alerts icon + Grid menu button (right)
- **Status line** (24dp): persistent priority-based info bar below toolbar
  - Idle: GPS coordinates + speed + weather (e.g., "42.5557, -70.8730 • 61°F")
  - Active: highest-priority state wins — follow info, scan progress, or geofence alerts
  - 7 priority levels: GPS_IDLE(0) → SILENT_FILL(1) → IDLE_POPULATE(2) → POPULATE(3) → AIRCRAFT_FOLLOW(4) → VEHICLE_FOLLOW(5) → GEOFENCE_ALERT(6)
  - Geofence alerts show zone-type-colored background; tap to dismiss + show zone detail
  - Tap on follow/scan entries to stop the active operation
- **Grid dropdown**: PopupWindow triggered by grid button
  - 8 labeled buttons (icon + text) in 2×4 grid
  - Row 1: Transit, Webcams, Aircraft, Radar | Row 2: POI, Utility, Find, Go To
  - Dark semi-transparent background (#E8212121), ripple feedback, auto-dismiss
- Net height: 72dp → 64dp = 8dp saved

#### New Files
- `app/.../ui/StatusLineManager.kt` — priority-based status line manager
  - `StatusEntry` data class: priority, text, bgColor, textColor, onClick
  - `set(entry)` / `clear(priority)` / `updateIdle(lat, lon, speed, temp, desc)`
  - `currentText()` / `currentPriority()` for debug endpoint
- `app/.../res/drawable/ic_grid_menu.xml` — Material 3×3 grid icon
- `app/.../res/layout/grid_dropdown_panel.xml` — PopupWindow content

#### Modified Files
- `toolbar_two_row.xml` — replaced 2×5 grid with slim icon row + status line TextView
- `activity_main.xml` — added `fitsSystemWindows="true"` to AppBarLayout (icons were bleeding into system status bar)
- `AppBarMenuManager.kt`:
  - Removed: `setupTwoRowToolbar()`, `findToolbarIcon()`, 10 `ICON_*` constants
  - Added: `SlimToolbarRefs` data class, `setupSlimToolbar()`, `showGridDropdown()`, `dp()` helper
  - Added imports: PopupWindow, ColorDrawable, Gravity, LayoutInflater, ViewGroup
- `MainActivity.kt`:
  - Replaced `followBanner: TextView?` with `statusLineManager: StatusLineManager`
  - Rewired toolbar init: `setupSlimToolbar()` returns `SlimToolbarRefs`
  - Migrated 6 banner functions to StatusLineManager set/clear calls:
    - `showFollowBanner()` → `set(VEHICLE_FOLLOW, ...)`
    - `showAircraftFollowBanner()` → `set(AIRCRAFT_FOLLOW, ...)`
    - `showSilentFillBanner()` → `set(SILENT_FILL, ...)`
    - `showIdlePopulateBanner()` → `set(IDLE_POPULATE, ...)`
    - `showPopulateBanner()` → `set(POPULATE, ...)`
    - `showGeofenceAlertBanner()` → `set(GEOFENCE_ALERT, ..., bgColor)`
    - `hideFollowBanner()` → `clear(VEHICLE_FOLLOW) + clear(AIRCRAFT_FOLLOW)`
    - `hideSilentFillBanner()` → `clear(SILENT_FILL)`
    - `stopIdlePopulate()` → `clear(IDLE_POPULATE)` (was `hideFollowBanner()`)
    - `stopPopulatePois()` → `clear(POPULATE)` (was `hideFollowBanner()`)
  - Added `updateIdleStatusLine()` helper — called from GPS observer (both jitter and significant-move paths) and weather data observer
  - Added `statusLine` field to `debugState()` map
  - All dynamic `TextView` creation + `addView()` patterns eliminated

### Testing
- Deployed to emulator, verified:
  - Slim toolbar properly below system status bar (fitsSystemWindows fix)
  - Status line shows GPS coords + weather when idle
  - Grid dropdown opens with 8 labeled buttons
  - Status line priority system: idle scan overrides GPS idle correctly
  - Debug endpoint: `curl localhost:8085/state | jq .statusLine` returns text + priority

### Files Changed
- `app/src/main/res/drawable/ic_grid_menu.xml` (new)
- `app/src/main/res/layout/grid_dropdown_panel.xml` (new)
- `app/src/main/res/layout/toolbar_two_row.xml` (rewritten)
- `app/src/main/res/layout/activity_main.xml` (fitsSystemWindows)
- `app/src/main/java/.../ui/StatusLineManager.kt` (new)
- `app/src/main/java/.../ui/menu/AppBarMenuManager.kt` (major rewrite)
- `app/src/main/java/.../ui/MainActivity.kt` (toolbar init + banner migration)
