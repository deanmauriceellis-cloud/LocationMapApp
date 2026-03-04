# LocationMapApp v1.5 — Comprehensive Design Review

**Version:** v1.5.49 | **Date:** 2026-03-03 | **Branch:** master
**Build:** 51 sessions of iterative development (v1.5.0 through v1.5.49)

---

## TABLE OF CONTENTS

1. [Executive Summary](#1-executive-summary)
2. [System Architecture](#2-system-architecture)
3. [Build Environment & Dependencies](#3-build-environment--dependencies)
4. [Version History & Feature Cross-Reference](#4-version-history--feature-cross-reference)
5. [Android Application Layer](#5-android-application-layer)
   - 5.1 MainActivity.kt
   - 5.2 MainViewModel.kt
   - 5.3 MarkerIconHelper.kt
   - 5.4 StatusLineManager.kt
   - 5.5 AppBarMenuManager.kt
   - 5.6 MenuEventListener.kt
   - 5.7 PoiCategories.kt
   - 5.8 GeofenceEngine.kt
   - 5.9 WeatherIconHelper.kt
6. [Data Layer](#6-data-layer)
   - 6.1 Data Models (Models.kt)
   - 6.2 Repository Specifications (9 repositories)
   - 6.3 Dependency Injection (AppModule.kt)
7. [Cache Proxy Server](#7-cache-proxy-server)
   - 7.1 Infrastructure & Caching
   - 7.2 Complete Endpoint Reference (40+ endpoints)
   - 7.3 OpenSky Rate Limiter
   - 7.4 Overpass Queue System
8. [Database Layer](#8-database-layer)
   - 8.1 PostgreSQL Schema
   - 8.2 Geofence Database Schema (SQLite)
   - 8.3 Build Scripts
9. [Debug & Testing Infrastructure](#9-debug--testing-infrastructure)
   - 9.1 Debug HTTP Server
   - 9.2 Debug Endpoints (24 endpoints)
   - 9.3 Test Harness Scripts
10. [XML Layouts & Resources](#10-xml-layouts--resources)
11. [Map Interaction Model](#11-map-interaction-model)
12. [External API Routing Table](#12-external-api-routing-table)
13. [Project State (STATE.md)](#13-project-state)
14. [Full Changelog](#14-full-changelog)
15. [Known Issues & Future Work](#15-known-issues--future-work)

---

## 1. EXECUTIVE SUMMARY

LocationMapApp is a feature-rich Android mapping application built for real-time tracking and discovery of points of interest (POIs), public transit vehicles, aircraft, weather conditions, webcams, and geofence zones. The system consists of three tightly integrated components:

1. **Android App** (Kotlin/Hilt/osmdroid) — Map rendering, GPS tracking, marker management, dialogs, social layer
2. **Cache Proxy** (Node.js/Express/Socket.IO, port 3000) — Transparent caching, rate limiting, fair queuing, API aggregation, auth, real-time chat
3. **PostgreSQL Database** — Permanent POI storage (180,059 POIs, auto-imported every 15min), aircraft sighting history (500+ sightings), social tables (users, comments, chat)

The app communicates with 10+ external APIs through the proxy, which manages caching (15-second to 365-day TTLs), OpenSky rate limiting (4,000 req/day), Overpass fair queuing (per-client round-robin), data enrichment (website resolution, weather compositing), JWT authentication, and real-time messaging via Socket.IO.

**Key Metrics:**
- ~11,500 lines of Kotlin across 13 main classes
- ~3,700 lines of JavaScript (proxy server.js)
- 50+ proxy endpoints, 24 debug endpoints
- 17 POI categories with 127 subtypes
- 8 geofence zone types with 220,657+ downloadable zones
- 103 automated test cases (0 failures as of 2026-03-01)
- 58 custom vector drawable icons
- Social layer: auth, POI comments with ratings, real-time chat (Socket.IO)

---

## 2. SYSTEM ARCHITECTURE

```
+------------------+        +-------------------+        +--------------------+
|   Android App    |  HTTP  |   Cache Proxy     |  HTTP  |  External APIs     |
|  (Kotlin/Hilt)   | -----> | (Node.js:3000)    | -----> |  Overpass, NWS,    |
|  osmdroid map    |  WS    |  In-memory cache  |        |  OpenSky, MBTA,    |
|  12 repositories |        |  Disk persistence |        |  Windy, FAA, FEMA, |
|  Debug :8085     |        |  Fair queuing     |        |  Aviation Weather, |
+------------------+        |  Rate limiting    |        |  Photon geocoder   |
                            |  JWT auth         |        +--------------------+
                            |  Socket.IO chat   |
                            +--------+----------+
                                     |
                            +--------v----------+
                            |    PostgreSQL      |
                            |  locationmapapp    |
                            |  - pois (180,059)  |
                            |  - aircraft_sightings|
                            |  - 8 social tables |
                            +--------------------+
```

**Data Flow:**
- App → Proxy (10.0.0.4:3000) → External APIs
- Exception: MBTA vehicles/stations/predictions go direct to api-v3.mbta.com
- Exception: Radar tiles go direct to Iowa State Mesonet

---

## 3. BUILD ENVIRONMENT & DEPENDENCIES

### 3.1 Build Configuration

| Setting | Value |
|---------|-------|
| Compile SDK | 35 |
| Min SDK | 26 (Android 8.0 Oreo) |
| Target SDK | 35 (Android 15) |
| Java Compatibility | 17 |
| Kotlin jvmTarget | 17 |
| Java Runtime | JBR 21.0.9 (JetBrains Runtime, bundled with Android Studio) |
| JBR Path | `/home/witchdoctor/AndroidStudio/android-studio/jbr` |
| Version Code | 5 |
| Version Name | 1.5.0 |

**Build Command:**
```bash
JAVA_HOME=/home/witchdoctor/AndroidStudio/android-studio/jbr ./gradlew assembleDebug
```

### 3.2 Android Dependencies

| Category | Dependency | Version |
|----------|-----------|---------|
| **Core** | androidx.core:core-ktx | 1.12.0 |
| | androidx.appcompat:appcompat | 1.6.1 |
| | com.google.android.material:material | 1.11.0 |
| | androidx.constraintlayout | 2.1.4 |
| | androidx.lifecycle:lifecycle-viewmodel-ktx | 2.7.0 |
| | androidx.lifecycle:lifecycle-runtime-ktx | 2.7.0 |
| | androidx.activity:activity-ktx | 1.8.2 |
| **DI** | com.google.dagger:hilt-android | 2.51 |
| | com.google.dagger:hilt-compiler (KSP) | 2.51 |
| **Location** | com.google.android.gms:play-services-location | 21.1.0 |
| **Map** | org.osmdroid:osmdroid-android | 6.1.18 |
| | org.osmdroid:osmdroid-mapsforge | 6.1.18 |
| | com.github.MKergall:osmbonuspack | 6.9.0 |
| **Network** | com.squareup.retrofit2:retrofit | 2.9.0 |
| | com.squareup.retrofit2:converter-gson | 2.9.0 |
| | com.squareup.okhttp3:okhttp | 4.12.0 |
| | com.squareup.okhttp3:logging-interceptor | 4.12.0 |
| **Async** | org.jetbrains.kotlinx:kotlinx-coroutines-android | 1.7.3 |
| **Database** | androidx.room:room-runtime | 2.6.1 |
| **Browser** | androidx.browser:browser | 1.7.0 |
| **JSON** | com.google.code.gson:gson | 2.10.1 |
| **Spatial** | org.locationtech.jts:jts-core | 1.19.0 |
| **Preferences** | androidx.preference:preference-ktx | 1.2.1 |
| **Socket.IO** | io.socket:socket.io-client | 2.1.0 |

### 3.3 Proxy Dependencies (package.json)

| Dependency | Version | Purpose |
|-----------|---------|---------|
| express | 4.21.0 | HTTP server framework |
| pg | 8.13.0 | PostgreSQL client |
| undici | 6.23.0 | HTTP fetch |
| cheerio | 1.0.0 | HTML/XML parsing (FAA TFR scraping) |
| duck-duck-scrape | 2.2.5 | DuckDuckGo search (website resolution) |
| fast-xml-parser | 4.3.0 | AIXM XML parsing (TFR details) |
| better-sqlite3 | 11.10.0 | SQLite for geofence databases |
| lzma-native | 8.0.6 | XZ decompression (ExCam cameras) |
| argon2 | latest | Password hashing (social auth) |
| jsonwebtoken | latest | JWT access/refresh tokens |
| socket.io | latest | Real-time chat messaging |
| express-rate-limit | ^7.5.1 | API rate limiting (auth, comments, rooms) |

### 3.4 Proxy Startup

```bash
cd cache-proxy && DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp \
  JWT_SECRET=$(openssl rand -hex 32) \
  OPENSKY_CLIENT_ID=deanmauriceellis-api-client \
  OPENSKY_CLIENT_SECRET=6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR node server.js
```

### 3.5 Android Manifest

**Permissions:** ACCESS_FINE_LOCATION, ACCESS_COARSE_LOCATION, INTERNET, ACCESS_NETWORK_STATE, WRITE_EXTERNAL_STORAGE (maxSdk 28), READ_EXTERNAL_STORAGE (maxSdk 32)

**Activities:** MainActivity (singleTop, launcher), DebugLogActivity

**Content Provider:** FileProvider for geofence database export (authority: `com.example.locationmapapp.fileprovider`, path: `geofence_databases/`)

**Cleartext Traffic:** Enabled (proxy on local network)

---

## 4. VERSION HISTORY & FEATURE CROSS-REFERENCE

This table maps every feature to the version it was introduced, its key files, and current status.

### v1.5.0 — 2026-02-27 — Foundation
| Feature | Key Files | Status |
|---------|-----------|--------|
| Cache proxy server | `cache-proxy/server.js` | Active |
| Vehicle follow mode | `MainActivity.kt` | Active |
| Cache-only POI display on scroll | `PlacesRepository.kt`, `server.js` | Active |
| Manual location mode (long-press) | `MainActivity.kt` | Active |
| NWS NEXRAD radar (Iowa State) | `MainActivity.kt` | Active |
| 5dp colored POI dots | `MarkerIconHelper.kt` | Active |
| 365-day Overpass cache TTL | `server.js` | Active |

### v1.5.1 — 2026-02-27 — Adaptive Radius
| Feature | Key Files | Status |
|---------|-----------|--------|
| Adaptive POI search radius | `server.js`, `PlacesRepository.kt` | Active |
| Per-grid-cell radius hints | `radius-hints.json` | Active |
| GET/POST /radius-hint endpoints | `server.js` | Active |

### v1.5.2 — 2026-02-28 — PostgreSQL
| Feature | Key Files | Status |
|---------|-----------|--------|
| PostgreSQL POI database | `schema.sql`, `server.js` | Active (180,059 POIs, auto-import) |
| JSONB tags with GIN index | `schema.sql` | Active |
| POI import script | `import-pois.js` | Active |

### v1.5.3 — 2026-02-28 — 16 POI Categories
| Feature | Key Files | Status |
|---------|-----------|--------|
| 16 POI categories (was 6) | `PoiCategories.kt`, `menu_poi.xml` | Updated to 17 in v1.5.43 |
| Submenu refinement dialogs | `AppBarMenuManager.kt` | Active |
| Central category config | `PoiCategories.kt` | Active |
| Rich METAR station markers | `MarkerIconHelper.kt`, `WeatherRepository.kt` | Active |
| Layer-aware POI LiveData | `MainViewModel.kt` | Active |

### v1.5.4 — 2026-02-28 — Vehicle Arrows & METAR
| Feature | Key Files | Status |
|---------|-----------|--------|
| Vehicle direction arrows | `MarkerIconHelper.kt` | Active |
| Human-readable METAR tap info | `MainActivity.kt` | Active |
| Deferred METAR restore | `MainActivity.kt` | Active |

### v1.5.5 — 2026-02-28 — Aircraft Tracking
| Feature | Key Files | Status |
|---------|-----------|--------|
| Aircraft tracking (OpenSky) | `AircraftRepository.kt`, `server.js` | Active |
| Altitude-colored airplane icons | `MarkerIconHelper.kt` | Active |
| Configurable refresh (30s-5min) | `AppBarMenuManager.kt` | Active |
| Zoom >= 10 guard | `MainActivity.kt` | Active |
| Proxy /aircraft endpoint | `server.js` | Active (15s TTL) |

### v1.5.6 — 2026-02-28 — Aircraft Follow
| Feature | Key Files | Status |
|---------|-----------|--------|
| Aircraft follow mode (icao24 tracking) | `MainActivity.kt`, `AircraftRepository.kt` | Active |
| Enhanced aircraft markers (callsign, vert rate) | `MarkerIconHelper.kt` | Active |
| SPI emergency indicator | `MarkerIconHelper.kt` | Active |
| Cached POI bbox display | `server.js` (`/pois/bbox`) | Active |

### v1.5.7 — 2026-02-28 — Auto-Follow & POI Labels
| Feature | Key Files | Status |
|---------|-----------|--------|
| Auto-follow aircraft (POI Builder) | `MainActivity.kt` | Active |
| POI labels at zoom >= 18 | `MarkerIconHelper.kt` (`labeledDot()`) | Active |
| 20-min aircraft rotation | `MainActivity.kt` | Active |

### v1.5.8 — 2026-02-28 — DB Queries & OpenSky Auth
| Feature | Key Files | Status |
|---------|-----------|--------|
| 6 PostgreSQL POI endpoints | `server.js` (`/db/pois/*`) | Active (now 8) |
| Aircraft sightings database | `server.js`, `schema.sql` | Active (501+ sightings) |
| OpenSky OAuth2 (4,000 req/day) | `server.js` | Active |
| Smart auto-follow | `MainActivity.kt` | Active |

### v1.5.9 — 2026-02-28 — Viewport-Only POIs
| Feature | Key Files | Status |
|---------|-----------|--------|
| Viewport-only POI markers (~100-400) | `MainActivity.kt` | Active |
| LRU icon cache (max 500) | `MarkerIconHelper.kt` | Active |

### v1.5.10 — 2026-02-28 — Webcams
| Feature | Key Files | Status |
|---------|-----------|--------|
| Webcam layer (Windy API, 18 categories) | `WebcamRepository.kt`, `server.js` | Active |
| Webcam preview + live player | `MainActivity.kt` | Active |

### v1.5.11 — 2026-02-28 — Rate Limiter
| Feature | Key Files | Status |
|---------|-----------|--------|
| OpenSky rate limiter (exponential backoff) | `server.js` | Active |
| Stale cache fallback | `server.js` | Active |
| Dedicated Air menu | `menu_aircraft.xml` | Active |

### v1.5.12 — 2026-02-28 — Vehicle Staleness
| Feature | Key Files | Status |
|---------|-----------|--------|
| Vehicle staleness detection (>2 min) | `MainActivity.kt` | Active |
| Dedicated Air toolbar button | `menu_main_toolbar.xml` | Active |

### v1.5.13 — 2026-02-28 — Populate Scanner v1
| Feature | Key Files | Status |
|---------|-----------|--------|
| Populate POIs (spiral grid scanner) | `MainActivity.kt` | Superseded by v1.5.16 |
| X-Cache header on Overpass | `server.js` | Active |
| PopulateSearchResult model | `Models.kt` | Active |

### v1.5.14 — 2026-02-28 — Populate v1 Tuning
| Feature | Key Files | Status |
|---------|-----------|--------|
| 30s fixed pacing | `MainActivity.kt` | Active |
| Cap detection & cell subdivision | `MainActivity.kt` | Superseded by v1.5.16 |
| Zoom-14 scan view | `MainActivity.kt` | Active |

### v1.5.15 — 2026-02-28 — Cap Retry & Fuzzy Hints
| Feature | Key Files | Status |
|---------|-----------|--------|
| Auto-retry on 500-element cap | `PlacesRepository.kt` | Active |
| 20km fuzzy radius hints | `server.js`, `PlacesRepository.kt` | Active |
| MIN_RADIUS 100m | `server.js`, `PlacesRepository.kt` | Active |

### v1.5.16 — 2026-02-28 — Populate v2
| Feature | Key Files | Status |
|---------|-----------|--------|
| Probe-calibrate-subdivide scanner | `MainActivity.kt` | Active |
| Recursive 3x3 subdivision | `MainActivity.kt` | Active |
| Narrative populate banner | `MainActivity.kt` | Active |
| Overpass request queue (serialized, 10s gap) | `server.js` | Active |
| Per-client fair queuing | `server.js` | Active |
| Error radius immunity (504/429 don't shrink) | `server.js` | Active |

### v1.5.17 — 2026-02-28 — Train Stations
| Feature | Key Files | Status |
|---------|-----------|--------|
| ~270 train station markers | `MbtaRepository.kt`, `MainActivity.kt` | Active |
| Arrival board dialog | `MainActivity.kt` | Active |
| Trip schedule dialog | `MainActivity.kt` | Active |
| MbtaStop, MbtaPrediction, MbtaTripScheduleEntry models | `Models.kt` | Active |

### v1.5.18 — 2026-02-28 — Debug HTTP Server
| Feature | Key Files | Status |
|---------|-----------|--------|
| Debug HTTP server (port 8085) | `DebugHttpServer.kt`, `DebugEndpoints.kt` | Active (24 endpoints) |
| 19 initial debug endpoints | `DebugEndpoints.kt` | Active (expanded to 24) |
| Lifecycle-aware endpoint registration | `DebugHttpServer.kt` | Active |

### v1.5.19 — 2026-03-01 — Bus Stops
| Feature | Key Files | Status |
|---------|-----------|--------|
| ~7,900 bus stop markers | `MbtaRepository.kt`, `MainActivity.kt` | Active |
| Zoom >= 15 guard for bus stops | `MainActivity.kt` | Active |
| Vehicle detail dialog | `MainActivity.kt` | Active |

### v1.5.20 — 2026-03-01 — Bus Stops via Proxy
| Feature | Key Files | Status |
|---------|-----------|--------|
| Bus stops routed through proxy cache | `server.js` (`/mbta/bus-stops`) | Active (24h TTL) |

### v1.5.21 — 2026-03-01 — Debug API Expansion
| Feature | Key Files | Status |
|---------|-----------|--------|
| /vehicles, /stations, /bus-stops endpoints | `DebugEndpoints.kt` | Active |
| relatedObject on all markers | `DebugEndpoints.kt` | Active |
| test-app.sh (30+ tests) | `test-app.sh` | Active |

### v1.5.22 — 2026-03-01 — Test Harness
| Feature | Key Files | Status |
|---------|-----------|--------|
| overnight-test.sh (6-8hr harness) | `overnight-test.sh` | Active |
| morning-transit-test.sh | `morning-transit-test.sh` | Active |
| run-full-test-suite.sh | `run-full-test-suite.sh` | Active |
| 103 PASS, 0 FAIL | — | Verified 2026-03-01 |

### v1.5.23 — 2026-03-01 — Aircraft DB & Fuzzy Search
| Feature | Key Files | Status |
|---------|-----------|--------|
| 4 aircraft DB endpoints | `server.js` (`/db/aircraft/*`) | Active |
| Fuzzy search (abbreviation expansion) | `DebugEndpoints.kt` | Active |
| Bbox snapping (0.01° / 0.1°) | `server.js` | Active |

### v1.5.24 — 2026-03-01 — Flight Trail
| Feature | Key Files | Status |
|---------|-----------|--------|
| Aircraft flight path visualization | `MainActivity.kt`, `AircraftRepository.kt` | Active |
| Altitude-colored polyline trail | `MainActivity.kt` | Active |
| DB history + live path (1000-point cap) | `AircraftRepository.kt` | Active |

### v1.5.25 — 2026-03-01 — Legend & Zoom Guards
| Feature | Key Files | Status |
|---------|-----------|--------|
| Legend dialog (7 sections) | `MainActivity.kt` | Active (moved to Utility) |
| Transit zoom guard (zoom <= 10 hides) | `MainActivity.kt` | Active |
| Long-press auto-zoom to 14 | `MainActivity.kt` | Updated to zoom 18 in v1.5.48 |

### v1.5.26 — 2026-03-01 — Find Dialog
| Feature | Key Files | Status |
|---------|-----------|--------|
| Find dialog (category grid -> results) | `MainActivity.kt`, `FindRepository.kt` | Active |
| /db/pois/counts and /db/pois/find endpoints | `server.js` | Active |
| FindRepository with client cache | `FindRepository.kt` | Active |
| Long-press filter mode | `MainActivity.kt` | Active |

### v1.5.27 — 2026-03-01 — POI Detail Dialog
| Feature | Key Files | Status |
|---------|-----------|--------|
| POI Detail dialog (info + actions) | `MainActivity.kt` | Active |
| Website resolution (3-tier waterfall) | `server.js` (`/pois/website`) | Active |
| In-app WebView for websites | `MainActivity.kt` | Active |
| Directions/Call/Reviews/Map buttons | `MainActivity.kt` | Active |

### v1.5.28 — 2026-03-01 — Silent Fill & 23 Subtypes
| Feature | Key Files | Status |
|---------|-----------|--------|
| Silent background POI fill | `MainActivity.kt` | Active |
| 23 new POI subtypes | `PoiCategories.kt` | Active (expanded to 46+ in v1.5.43) |

### v1.5.29 — 2026-03-01 — Labeled Vehicles
| Feature | Key Files | Status |
|---------|-----------|--------|
| Labeled vehicle markers at zoom >= 18 | `MarkerIconHelper.kt` (`labeledVehicle()`) | Active |

### v1.5.30 — 2026-03-01 — Smart GPS
| Feature | Key Files | Status |
|---------|-----------|--------|
| Dead zone filtering (100m) | `MainActivity.kt` | Active |
| Speed-adaptive polling (10s/60s) | `MainActivity.kt` | Active |
| 3km POI fetch threshold | `MainActivity.kt` | Active |

### v1.5.31 — 2026-03-01 — Icon Toolbar & GoTo
| Feature | Key Files | Status |
|---------|-----------|--------|
| Icon toolbar (8 icons) | `menu_main_toolbar.xml` | Superseded by v1.5.40 |
| Go to Location dialog | `MainActivity.kt`, `GeocodingRepository.kt` | Active |

### v1.5.32 — 2026-03-02 — Geocode Autocomplete
| Feature | Key Files | Status |
|---------|-----------|--------|
| Photon geocoder via proxy /geocode | `server.js`, `GeocodingRepository.kt` | Active |
| 500ms debounce, US-only bbox | `server.js` | Active |

### v1.5.33 — 2026-03-02 — Idle Populate & Delta Cache
| Feature | Key Files | Status |
|---------|-----------|--------|
| Idle auto-populate (60s stationarity) | `MainActivity.kt` | Updated to 5min in v1.5.41 |
| X-Client-ID header | `PlacesRepository.kt` | Active |
| Covering cache, content hash delta | `server.js` | Active |

### v1.5.34 — 2026-03-02 — Weather Dialog
| Feature | Key Files | Status |
|---------|-----------|--------|
| Rich weather dialog | `MainActivity.kt`, `WeatherRepository.kt` | Active |
| Composite /weather endpoint (5 NWS calls) | `server.js` | Active |
| 22 weather vector icons | `res/drawable/ic_wx_*.xml` | Active |
| WeatherIconHelper.kt | `WeatherIconHelper.kt` | Active |

### v1.5.35 — 2026-03-02 — TFR Geofence System
| Feature | Key Files | Status |
|---------|-----------|--------|
| GeofenceEngine (JTS R-tree) | `GeofenceEngine.kt` | Active |
| TFR polygon overlays (FAA data) | `TfrRepository.kt`, `server.js` | Active |
| Entry/proximity/exit alerting | `GeofenceEngine.kt` | Active |
| Severity-colored Alerts icon | `MainActivity.kt` | Active |
| /tfrs proxy endpoint (AIXM parser) | `server.js` | Active |

### v1.5.36 — 2026-03-02 — 4 Additional Zone Types
| Feature | Key Files | Status |
|---------|-----------|--------|
| Speed cameras (Overpass, 200m circle) | `GeofenceRepository.kt`, `server.js` | Active |
| School zones (Overpass, polygon/300m) | `GeofenceRepository.kt`, `server.js` | Active |
| Flood zones (FEMA NFHL ArcGIS) | `GeofenceRepository.kt`, `server.js` | Active |
| Railroad crossings (Overpass, 100m) | `GeofenceRepository.kt`, `server.js` | Active |

### v1.5.37 — 2026-03-02 — Downloadable DB Infrastructure
| Feature | Key Files | Status |
|---------|-----------|--------|
| GeofenceDatabaseRepository | `GeofenceDatabaseRepository.kt` | Active |
| Database Manager dialog | `MainActivity.kt` | Active |
| Military bases database (1,944 zones) | `build-military.js` | Active |
| Catalog + download endpoints | `server.js` | Active |

### v1.5.38 — 2026-03-02 — 3 More Databases
| Feature | Key Files | Status |
|---------|-----------|--------|
| ExCam cameras (109,500 zones) | `build-excam.js` | Active |
| NCES schools (101,390 zones) | `build-nces.js` | Active |
| DJI no-fly zones (7,823 zones) | `build-dji-nofly.js` | Active |
| 4 databases, 220,657 total zones | `catalog.json` | Active |

### v1.5.39 — 2026-03-02 — Database Import & Export
| Feature | Key Files | Status |
|---------|-----------|--------|
| Import SQLite .db (SAF picker) | `GeofenceDatabaseRepository.kt` | Active |
| Import CSV (config dialog + parser) | `GeofenceDatabaseRepository.kt` | Active |
| Export via share intent (FileProvider) | `GeofenceDatabaseRepository.kt` | Active |
| Local-only database display | `GeofenceDatabaseRepository.kt` | Active |

### v1.5.40 — 2026-03-02 — Slim Toolbar Redesign
| Feature | Key Files | Status |
|---------|-----------|--------|
| Slim toolbar (40dp) + status line (24dp) | `toolbar_two_row.xml`, `StatusLineManager.kt` | Active |
| Grid dropdown (2x4 PopupWindow) | `grid_dropdown_panel.xml`, `AppBarMenuManager.kt` | Active |
| 7-priority StatusLineManager | `StatusLineManager.kt` | Active |

### v1.5.41 — 2026-03-02 — Idle Populate Improvements
| Feature | Key Files | Status |
|---------|-----------|--------|
| 5-min GPS stationarity threshold | `MainActivity.kt` | Active |
| Touch-to-stop idle populate | `MainActivity.kt` | Active |
| State preservation (resume from last point) | `MainActivity.kt` | Active |
| Long-press fetches weather + alerts | `MainActivity.kt` | Active |

### v1.5.42 — 2026-03-02 — 10km Probe Populate
| Feature | Key Files | Status |
|---------|-----------|--------|
| 10km probe populate (expanding spiral) | `MainActivity.kt` | Active |
| estimateFillRadius() | `MainActivity.kt` | Active |
| panOnly mode for placeScanningMarker | `MainActivity.kt` | Active |
| POI display zoom guard lowered to 10 | `MainActivity.kt` | Active |
| 5000-marker count guard | `MainActivity.kt` | Active |

### v1.5.43 — 2026-03-02 — Train ETA & POI Expansion
| Feature | Key Files | Status |
|---------|-----------|--------|
| Train next-stop ETA badge | `MbtaRepository.kt`, `MarkerIconHelper.kt` | Active |
| enrichWithNextStopEta() (batch predictions) | `MbtaRepository.kt` | Active |
| 17th POI category (Offices & Services) | `PoiCategories.kt` | Active |
| 46 new subtypes across 9 categories | `PoiCategories.kt` | Active |
| Find dialog radius-scoped counts (10km) | `FindRepository.kt`, `server.js` | Active |
| Find dialog auto-fit cell heights | `MainActivity.kt` | Active |
| Fixed: 10km probe zoom (panMap=false) | `MainActivity.kt` | Fixed |
| Fixed: GoTo stops 10km probe | `MainActivity.kt` | Fixed |

### v1.5.44 — 2026-03-03 — Share, Dark Mode, Favorites, Search, Animated Radar
| Feature | Key Files | Status |
|---------|-----------|--------|
| Share POI (5th action button) | `MainActivity.kt` | Active |
| Dark mode (CartoDB Dark Matter tiles) | `AppBarMenuManager.kt`, `MainActivity.kt` | Active |
| Favorites (star in POI detail, Favorites cell in Find) | `FavoritesManager.kt`, `MainActivity.kt` | Active |
| Text search in Find dialog | `FindRepository.kt`, `MainActivity.kt` | Active |
| Animated radar (7-frame NEXRAD loop) | `MainActivity.kt` | Active |

### v1.5.45 — 2026-03-03 — Social Layer (Auth, Comments, Chat)
| Feature | Key Files | Status |
|---------|-----------|--------|
| Auth system (Argon2id + JWT) | `AuthRepository.kt`, `server.js` | Active |
| POI Comments (ratings, votes, soft-delete) | `CommentRepository.kt`, `server.js` | Active |
| Real-time chat (Socket.IO) | `ChatRepository.kt`, `server.js` | Active |
| Grid dropdown 3×4 (12 buttons) | `grid_dropdown_panel.xml`, `AppBarMenuManager.kt` | Active |
| Profile dialog | `MainActivity.kt` | Active |
| 8 social DB tables | `create-social-tables.sh` | Active |

### v1.5.46 — 2026-03-03 — POI Click-Through & Device-Bonded Auth
| Feature | Key Files | Status |
|---------|-----------|--------|
| POI marker tap → detail dialog | `MainActivity.kt` | Active |
| Device-bonded auth (register-only, no login/logout) | `AuthRepository.kt`, `server.js` | Active |
| PlaceResult.id "type:id" format | `PlacesRepository.kt`, `MainViewModel.kt` | Active |

### v1.5.47 — 2026-03-03 — Security Hardening
| Feature | Key Files | Status |
|---------|-----------|--------|
| Server-side sanitization (sanitizeText/sanitizeMultiline) | `server.js` | Active |
| Rate limiting (express-rate-limit) | `server.js` | Active |
| Login lockout (5 fails → 15min) | `server.js` | Active |
| Client-side InputFilter.LengthFilter | `MainActivity.kt` | Active |
| Comment character counter | `MainActivity.kt` | Active |

### v1.5.48 — 2026-03-03 — Startup/Behavior Tuning
| Feature | Key Files | Status |
|---------|-----------|--------|
| Default zoom 14 → 18 | `MainActivity.kt` | Active |
| Radar transparency 70% → 35% | `MainActivity.kt` | Active |
| Idle populate POI density guard (≥100 → skip) | `MainActivity.kt`, `MainViewModel.kt` | Active |

### v1.5.49 — 2026-03-03 — Automated POI DB Import
| Feature | Key Files | Status |
|---------|-----------|--------|
| Delta POI import (every 15min) | `server.js` | Active |
| POST /db/import (manual trigger) | `server.js` | Active |
| GET /db/import/status | `server.js` | Active |
| dbImport in /cache/stats | `server.js` | Active |

---

## 5. ANDROID APPLICATION LAYER

### 5.1 MainActivity.kt

**File:** `app/src/main/java/com/example/locationmapapp/ui/MainActivity.kt`
**Lines:** ~8,500 | **Introduced:** v1.5.0

The single Activity manages all UI: map, markers, dialogs, toolbar, and user interactions. Implements `MenuEventListener` interface.

#### Initialization Sequence

**onCreate():**
1. Initialize DebugHttpServer on port 8085
2. Configure OSMDroid (Mapnik tiles, multi-touch, zoom 3-19)
3. Inflate ActivityMainBinding (slim toolbar + status line + grid dropdown)
4. Create AppBarMenuManager, StatusLineManager
5. Call setupMap(), buildFabSpeedDial(), observeViewModel()
6. Request location permission
7. Handle debug intents (lat/lon/zoom/enable/disable)

**onStart():**
1. Set populate state to OFF
2. Restore radar overlay + refresh scheduler
3. Restore MBTA transit layers
4. Defer POI, METAR, Aircraft, Webcam, Geofence restore until GPS fix

#### GPS Location Handling

- **Adaptive interval:** 10s if speed > 20 mph, else 60s
- **100m dead zone:** Ignore GPS jitter < 100m
- **Idle timer:** 10 min stationary → startIdlePopulate() (skips if ≥100 POIs within 10km)
- **Initial center:** First fix zooms to 18 (street-level default)
- **Weather fetch:** Every 30 min (or on first fix)
- **Geofence check:** If zones loaded, checkPosition(lat, lon, bearing)
- **3km POI threshold:** Trigger cached POI load if moved > 3km (1 min cooldown)
- **Speed guard:** Skip API calls if speed > 20 mph

#### POI Populate System (4 modes)

| Mode | Trigger | Behavior | Version |
|------|---------|----------|---------|
| **Populate Scanner** | Utility menu | Probe-calibrate-spiral, 30s pacing, 3x3 recursive subdivision | v1.5.16 |
| **10km Probe** | Utility menu | Expanding spiral of 10km wide probes, pan without zoom | v1.5.42 |
| **Idle Auto-Populate** | 5 min GPS stationary | Same as scanner but 45s delays, touch-to-stop, state preserving | v1.5.33/v1.5.41 |
| **Silent Fill** | Startup, long-press, GPS move | Single Overpass query, no UI (unless debug mode) | v1.5.28 |

#### Vehicle Following

| Type | Tracking | Refresh | Tolerance | Trail |
|------|----------|---------|-----------|-------|
| **Aircraft** | Global ICAO24 | 10s | 3-strike | Altitude-colored polyline, DB history + live |
| **Train** | Vehicle ID | 60s (30-300s) | Standard | Position trail |
| **Subway** | Vehicle ID | 60s (30-300s) | Standard | Position trail |
| **Bus** | Vehicle ID | 60s (30-300s) | Standard | Position trail |
| **Auto-Follow** | Random >10k ft | 20-min rotation | Smart switching | Same as aircraft |

#### Dialog Systems

| Dialog | Trigger | Content | Version |
|--------|---------|---------|---------|
| **Weather** | Weather icon tap | Current + 48hr hourly + 7-day + alerts | v1.5.34 |
| **Find** | Grid > Find | Category grid → subtype grid → distance-sorted results | v1.5.26 |
| **POI Detail** | Find result tap or POI marker tap | Info rows, website, comments, star/favorite, Directions/Call/Reviews/Map/Share | v1.5.27/v1.5.44/v1.5.46 |
| **GoTo** | Grid > Go To | Geocoding autocomplete, tappable results | v1.5.31 |
| **Arrival Board** | Station/stop tap | Real-time predictions, 30s auto-refresh | v1.5.17 |
| **Trip Schedule** | Arrival row tap | Full itinerary, platform codes | v1.5.17 |
| **Vehicle Detail** | Vehicle marker tap | Route, status, speed + Follow/Route/Arrivals buttons | v1.5.19 |
| **Webcam Preview** | Webcam marker tap | 400x224 image + View Live button | v1.5.10 |
| **METAR Detail** | METAR marker tap | Decoded wind, sky, visibility, altimeter | v1.5.4 |
| **Legend** | Utility > Legend | 7 sections explaining all marker types | v1.5.25 |
| **DB Manager** | Alerts > Zone Databases | Catalog, download/delete, import/export | v1.5.37 |
| **Register** | Grid > Social | Display name, email, password (device-bonded) | v1.5.45 |
| **Profile** | Grid > Profile | Avatar, display name, role badge, user ID | v1.5.45 |
| **Chat Rooms** | Grid > Chat | Room list, member counts, + New button | v1.5.45 |
| **Chat Room** | Room list tap | Message bubbles, send bar, back arrow | v1.5.45 |
| **Add Comment** | POI detail > + Add | 5-star rating selector, text input, char counter | v1.5.45 |

#### Map Scroll/Zoom Handlers

- Schedule aircraft reload, bus stop reload, POI cache load, webcam reload, geofence reload
- Hide transit markers when zoom <= 10
- Toggle POI labels at zoom >= 18
- Toggle vehicle labels at zoom >= 18
- POI display guard: zoom >= 10, max 5000 markers

---

### 5.2 MainViewModel.kt

**File:** `app/src/main/java/com/example/locationmapapp/ui/MainViewModel.kt`
**Lines:** ~900 | **Introduced:** v1.5.0

Hilt-injected ViewModel connecting 12 repositories to 22+ LiveData fields.

#### Injected Dependencies
LocationManager, PlacesRepository, WeatherRepository, MbtaRepository, AircraftRepository, WebcamRepository, FindRepository, TfrRepository, GeofenceRepository, GeofenceDatabaseRepository, AuthRepository, CommentRepository, ChatRepository

#### LiveData Fields (22+)

| Category | Field | Type |
|----------|-------|------|
| **Location** | currentLocation | LocationUpdate |
| | locationMode | LocationMode (GPS/MANUAL) |
| **POI** | places | Pair<String, List<PlaceResult>> |
| **Transit** | mbtaTrains | List<MbtaVehicle> |
| | mbtaSubway | List<MbtaVehicle> |
| | mbtaBuses | List<MbtaVehicle> |
| | mbtaStations | List<MbtaStop> |
| | mbtaBusStops | List<MbtaStop> |
| **Aircraft** | aircraft | List<AircraftState> |
| | followedAircraft | AircraftState? |
| **Weather** | metars | List<MetarStation> |
| | weatherAlerts | List<WeatherAlert> |
| | weatherData | WeatherData? |
| | radarRefreshTick | Long |
| **Webcams** | webcams | List<Webcam> |
| **Find** | findCounts | FindCounts? |
| **Geofences** | geofenceAlerts | List<GeofenceAlert> |
| **Social** | authState | AuthState? |
| | comments | List<Comment> |
| | chatRooms | List<ChatRoom> |
| | importResult | ImportResult? |
| **Error** | error | String |

#### Public Methods (~60)

**Location:** onPermissionGranted(), restartLocationUpdates(), requestLastKnownLocation(), toggleLocationMode(), setManualLocation()

**POI:** searchPoisAt(), searchPoisFromCache(), loadCachedPoisForBbox(), populateSearchAt()

**Transit:** fetchMbtaTrains/Subway/Buses/Stations/BusStops(), clearMbta*(), fetchPredictionsDirectly(), fetchTripScheduleDirectly()

**Weather:** fetchWeatherAlerts(), fetchWeather(), fetchWeatherDirectly(), loadMetars()

**Aircraft:** loadAircraft(), loadFollowedAircraft(), clearFollowedAircraft(), fetchFlightHistoryDirectly()

**Webcams:** loadWebcams(), clearWebcams(), refreshRadar()

**Find:** loadFindCounts(lat, lon), findNearbyDirectly(), fetchPoiWebsiteDirectly()

**Geofences:** loadTfrs/Cameras/Schools/FloodZones/Crossings(), checkGeofences(), rebuildGeofenceIndex(), fetchGeofenceCatalog(), downloadGeofenceDatabase(), importGeofenceDatabase(), importCsvAsGeofenceDatabase(), deleteGeofenceDatabase(), loadDatabaseZonesForVisibleArea()

**Social:** register(), login(), refreshToken(), logout(), getProfile(), fetchComments(), postComment(), voteComment(), deleteComment(), fetchRooms(), createRoom(), sendMessage(), fetchNearbyPoiCount(), searchPoisByName()

---

### 5.3 MarkerIconHelper.kt

**File:** `app/src/main/java/com/example/locationmapapp/ui/MarkerIconHelper.kt`
**Lines:** 680 | **Introduced:** v1.5.0

Singleton object that rasterizes VectorDrawables into tinted BitmapDrawables with LRU caching (max 500 entries).

#### Icon Rendering Methods

| Method | Purpose | Output | Version |
|--------|---------|--------|---------|
| `forCategory(ctx, category, sizeDp)` | POI icon by category name | BitmapDrawable | v1.5.0 |
| `get(ctx, resId, sizeDp, tintColor)` | Icon by resource ID | BitmapDrawable | v1.5.0 |
| `dot(ctx, category)` | 5dp colored circle (low zoom) | BitmapDrawable | v1.5.0 |
| `labeledDot(ctx, category, name)` | Category + dot + name (zoom >= 18) | BitmapDrawable | v1.5.7 |
| `withArrow(ctx, resId, sizeDp, tint, bearing)` | Icon + rotated direction arrow | BitmapDrawable | v1.5.4 |
| `labeledVehicle(ctx, ..., nextStopMinutes)` | Complex vehicle marker with ETA | Pair<BitmapDrawable, anchorY> | v1.5.29/v1.5.43 |
| `aircraftMarker(ctx, heading, tint, callsign, vertRate, spi)` | Rotated airplane + callsign | BitmapDrawable | v1.5.6 |
| `stationIcon(ctx, tintColor)` | 26dp train station | BitmapDrawable | v1.5.17 |
| `busStopIcon(ctx, tintColor)` | 20dp bus stop sign | BitmapDrawable | v1.5.19 |
| `clearCache()` | Flush LRU | void | v1.5.9 |

#### labeledVehicle Layout (zoom >= 18)

```
  ┌─────────────────────┐
  │ → Stop Name         │  (status pill, tintColor bg)
  ├─────────────────────┤
  │  ↑ arrow            │  (rotated to bearing)
  │ [icon]   [3m]       │  (icon center + ETA badge right)
  │ [25 mph]            │  (speed badge left)
  ├─────────────────────┤
  │ R1 · To Downtown    │  (route pill, white bg)
  └─────────────────────┘
```

The ETA badge (e.g., "3m") was added in v1.5.43 — colored pill rendered to the right of the train icon.

#### Category Color Map (30+ mappings)

| Category | Color | Hex |
|----------|-------|-----|
| Food & Drink | Deep Orange | #BF360C |
| Fuel & Charging | Orange | #E65100 |
| Civic & Gov | Navy | #1A237E |
| Parks & Rec | Green | #2E7D32 |
| Shopping | Amber | #F57F17 |
| Healthcare | Red | #D32F2F |
| Education | Brown | #5D4037 |
| Lodging | Purple | #7B1FA2 |
| Parking | Blue Gray | #455A64 |
| Finance | Teal | #00695C |
| Worship | Dark Brown | #4E342E |
| Tourism | Orange | #FF6F00 |
| Emergency | Dark Red | #B71C1C |
| Auto Services | Dark Gray | #37474F |
| Entertainment | Cyan | #00838F |
| Offices | Blue Gray | #546E7A |
| Default | Purple | #6A1B9A |

---

### 5.4 StatusLineManager.kt

**File:** `app/src/main/java/com/example/locationmapapp/ui/StatusLineManager.kt`
**Lines:** 105 | **Introduced:** v1.5.40

Priority-based text display in the 24dp status line below the toolbar. Highest priority wins; lower priorities queue and display when higher ones clear.

#### Priority Levels

| Level | Name | Example Text |
|-------|------|-------------|
| 0 | GPS_IDLE | "42.3601, -71.0589 * 25 mph * 68F Mostly Cloudy" |
| 1 | SILENT_FILL | "Filling POIs..." |
| 2 | IDLE_POPULATE | "Idle scan: ring 2, 45 POIs" |
| 3 | POPULATE | "Scanning ring 3, cell 5/8, 120 POIs" |
| 4 | AIRCRAFT_FOLLOW | "UAL456 FL350 450kt" |
| 5 | VEHICLE_FOLLOW | "Fitchburg Line -> North Station" |
| 6 | GEOFENCE_ALERT | "ENTERING: TFR P-049 Washington DC" |

#### API

- `set(priority, text, onClick?)` — Set/update a priority level
- `set(priority, text, bgColor, textColor, onClick?)` — With custom colors (geofence alerts)
- `clear(priority)` — Remove priority, fall back to next highest
- `updateIdle(lat, lon, speedMph, tempF, description)` — GPS_IDLE convenience
- `currentText()` / `currentPriority()` — For debug endpoint

---

### 5.5 AppBarMenuManager.kt

**File:** `app/src/main/java/com/example/locationmapapp/ui/menu/AppBarMenuManager.kt`
**Lines:** 808 | **Introduced:** v1.5.0 (redesigned v1.5.40)

Manages the slim toolbar, grid dropdown, and all sub-menus. Handles SharedPreferences persistence for toggles and frequencies.

#### Grid Dropdown (3×4)

| Row 1 | Row 2 | Row 3 |
|-------|-------|-------|
| Transit | POI | Social |
| Webcams | Utility | Chat |
| Aircraft | Find | Profile |
| Radar | Go To | Legend |

#### Sub-Menu Systems

| Menu | Items | Key Prefs |
|------|-------|-----------|
| **Transit** | Stations, Bus Stops, Trains (+ freq), Subway (+ freq), Buses (+ freq), Alerts (+ freq) | PREF_MBTA_* |
| **Cameras** | Webcams toggle, Category filter (18 types) | PREF_WEBCAMS_ON |
| **Aircraft** | Display toggle, Frequency (30-300s), Auto-Follow | PREF_AIRCRAFT_* |
| **Radar** | Radar toggle, Visibility (0-100%), Frequency (5-15min), METAR, METAR freq | PREF_RADAR_* |
| **POI** | All On + 17 categories (each with subtype dialogs) | poi_*_on, poi_*_subtypes |
| **Utility** | Record GPS, Build Story, Analyze, Anomalies, Email GPX, Populate, 10km Probe, Fill Probe, Debug Log, GPS Mode, Silent Fill Debug, Legend | PREF_RECORD_GPS, PREF_GPS_MODE |
| **Alerts** | TFR/Camera/School/Flood/Crossing overlays, Sound, Distance (1-20 NM), DB Manager | PREF_*_OVERLAY |

**50+ preference keys** stored in `app_bar_menu_prefs` SharedPreferences.

---

### 5.6 MenuEventListener.kt

**File:** `app/src/main/java/com/example/locationmapapp/ui/menu/MenuEventListener.kt`
**Lines:** 225 | **Introduced:** v1.5.0

Interface with 45+ named callbacks — every toggle, slider, and action has its own method. No generic "onAction(String)" fallback.

#### Callback Groups

| Group | Count | Key Methods |
|-------|-------|-------------|
| Weather | 6 | onWeatherRequested, onMetarDisplayToggled, onAircraftDisplayToggled, etc. |
| Transit | 10 | onMbtaStationsToggled, onMbtaTrainsToggled, onMbtaTrainsFrequencyChanged, etc. |
| Cameras | 2 | onWebcamToggled, onWebcamCategoriesChanged |
| Radar | 3 | onRadarToggled, onRadarVisibilityChanged, onRadarFrequencyChanged |
| POI | 1 | onPoiLayerToggled(layerId, enabled) |
| Utility | 11 | onPopulatePoisToggled, onProbe10kmRequested, onGpsRecordingToggled, etc. |
| Alerts | 8 | onTfrOverlayToggled, onCameraOverlayToggled, onAlertDistanceChanged, etc. |
| Social | 3 | onSocialRequested, onChatRequested, onProfileRequested |
| Dark Mode | 1 | onDarkModeToggled |
| Radar Anim | 2 | onRadarAnimateToggled, onRadarAnimSpeedChanged |
| Find | 3 | onFindRequested, onLegendRequested, onGoToLocationRequested |

#### PoiLayerId Constants (17)

FOOD_DRINK, FUEL_CHARGING, TRANSIT, CIVIC, PARKS_REC, SHOPPING, HEALTHCARE, EDUCATION, LODGING, PARKING, FINANCE, WORSHIP, TOURISM_HISTORY, EMERGENCY, AUTO_SERVICES, ENTERTAINMENT, OFFICES

---

### 5.7 PoiCategories.kt

**File:** `app/src/main/java/com/example/locationmapapp/ui/menu/PoiCategories.kt`
**Lines:** 366 | **Introduced:** v1.5.3 (expanded v1.5.28, v1.5.43)

Central configuration for all 17 POI categories and their subtypes. Single source of truth used by menus, markers, Find dialog, and Legend.

#### All 17 Categories

| # | Category | Subtypes | Color | Key Tags |
|---|----------|----------|-------|----------|
| 1 | Food & Drink | 12 | #BF360C | restaurant, fast_food, cafe, bar, pub, ice_cream, bakery, pastry, confectionery, alcohol, deli, marketplace |
| 2 | Fuel & Charging | 2 | #E65100 | fuel, charging_station |
| 3 | Transit | 4 | #0277BD | station, bus_station, bicycle_rental, ferry_terminal |
| 4 | Civic & Gov | 7 | #1A237E | townhall, courthouse, post_office, post_box, government, community_centre, social_facility |
| 5 | Parks & Rec | 14 | #2E7D32 | park, nature_reserve, playground, pitch, track, recreation_ground, pool, dog_park, garden, picnic_site, shelter, fountain, water, toilets |
| 6 | Shopping | 22 | #F57F17 | supermarket, convenience, mall, department_store, clothes, shoes, jewelry, hairdresser, beauty, books, gift, florist, furniture, hardware, mobile_phone, optician, chemist, laundry, dry_cleaning, variety_store, tobacco, storage_rental |
| 7 | Healthcare | 7 | #D32F2F | hospital, pharmacy, clinic, dentist, doctors, veterinary, nursing_home |
| 8 | Education | 6 | #5D4037 | school, library, college, university, childcare, kindergarten |
| 9 | Lodging | 6 | #7B1FA2 | hotel, motel, hostel, camp_site, guest_house, caravan_site |
| 10 | Parking | 0 | #455A64 | parking |
| 11 | Finance | 2 | #00695C | bank, atm |
| 12 | Places of Worship | 0 | #4E342E | place_of_worship |
| 13 | Tourism & History | 12 | #FF6F00 | museum, attraction, viewpoint, memorial, monument, artwork, gallery, information, cemetery, historic, ruins, maritime |
| 14 | Emergency Svc | 2 | #B71C1C | police, fire_station |
| 15 | Auto Services | 6 | #37474F | car_repair, car_wash, car_rental, tyres, car, car_parts |
| 16 | Entertainment | 15 | #00838F | fitness_centre, sports_centre, golf_course, marina, stadium, theatre, cinema, nightclub, events_venue, arts_centre, studio, dance, amusement_arcade, ice_rink, bowling_alley |
| 17 | Offices & Services | 5 | #546E7A | company, estate_agent, lawyer, insurance, tax_advisor |

---

### 5.8 GeofenceEngine.kt

**File:** `app/src/main/java/com/example/locationmapapp/util/GeofenceEngine.kt`
**Lines:** 282 | **Introduced:** v1.5.35

JTS-based spatial geofence engine with STRtree (Sort-Tile-Recursive R-tree) for fast spatial indexing.

#### Zone Types & Alert Severity

| Zone Type | Entry | Proximity | Source | Version |
|-----------|-------|-----------|--------|---------|
| TFR | CRITICAL | WARNING | FAA tfr.faa.gov | v1.5.35 |
| SPEED_CAMERA | WARNING | WARNING | Overpass / ExCam DB | v1.5.36/v1.5.38 |
| SCHOOL_ZONE | WARNING | WARNING | Overpass / NCES DB | v1.5.36/v1.5.38 |
| FLOOD_ZONE | WARNING | INFO | FEMA NFHL ArcGIS | v1.5.36 |
| RAILROAD_CROSSING | WARNING | WARNING | Overpass | v1.5.36 |
| MILITARY_BASE | CRITICAL | WARNING | NTAD ArcGIS DB | v1.5.37 |
| NO_FLY_ZONE | CRITICAL | WARNING | DJI NFZDB | v1.5.38 |
| CUSTOM | WARNING | WARNING | User import | v1.5.39 |

#### Detection Logic (checkPosition)

1. **Expand search envelope** by proximity threshold (default 5 NM)
2. **Query R-tree** for candidate zones
3. For each candidate:
   - **School zone time filter:** Weekdays 7-9 AM or 2-4 PM only
   - **Altitude filter:** Skip if known altitude outside floor/ceiling
   - **Point-in-polygon test** (JTS)
   - **Entry alert:** Inside + not in activeZones + not on cooldown (10 min)
   - **Proximity alert:** Outside + within threshold + heading toward (+-60 deg) + not on cooldown (5 min)
4. **Exit alert:** Was in activeZones, now outside (INFO severity)

#### Constants

| Constant | Value | Purpose |
|----------|-------|---------|
| PROXIMITY_COOLDOWN_MS | 5 min | Min time between proximity alerts for same zone |
| ENTRY_COOLDOWN_MS | 10 min | Min time between entry alerts for same zone |
| BEARING_WINDOW_DEG | 60 deg | +/- heading toward zone to trigger proximity |
| proximityThresholdNm | 5.0 NM | Default proximity detection distance |

---

### 5.9 WeatherIconHelper.kt

**File:** `app/src/main/java/com/example/locationmapapp/ui/WeatherIconHelper.kt`
**Introduced:** v1.5.34

Maps NWS icon codes to 22 custom vector drawable icons. Handles day/night variants and fallback to default icon.

---

## 6. DATA LAYER

### 6.1 Data Models (Models.kt)

**File:** `app/src/main/java/com/example/locationmapapp/data/model/Models.kt`

#### Transit Models

| Model | Fields | Notes |
|-------|--------|-------|
| **MbtaVehicle** | id, label, routeId, routeName, tripId, headsign, stopId, stopName, lat, lon, bearing, speedMps, currentStatus, updatedAt, routeType, nextStopMinutes | nextStopMinutes added v1.5.43; computed speedMph, speedDisplay |
| **MbtaVehicleStatus** | INCOMING_AT, STOPPED_AT, IN_TRANSIT_TO, UNKNOWN | Enum with display strings |
| **MbtaStop** | id, name, lat, lon, routeIds | Parent station grouping |
| **MbtaPrediction** | id, routeId, routeName, tripId, headsign, arrivalTime, departureTime, directionId, status, vehicleId | v1.5.17 |
| **MbtaTripScheduleEntry** | stopId, stopName, stopSequence, arrivalTime, departureTime, platformCode | v1.5.17 |

#### Aircraft Models

| Model | Fields | Notes |
|-------|--------|-------|
| **AircraftState** | icao24, callsign, originCountry, lat, lon, baroAltitude, onGround, velocity, track, verticalRate, geoAltitude, squawk, spi, positionSource, category | 18 state vector fields |
| **FlightPathPoint** | lat, lon, altitudeMeters, timestamp | v1.5.24 |

#### Weather Models

| Model | Fields | Notes |
|-------|--------|-------|
| **WeatherData** | location, current, hourly, daily, alerts, fetchedAt | Composite, v1.5.34 |
| **CurrentConditions** | temperature, humidity, windSpeed, windDirection, windChill, heatIndex, dewpoint, description, iconCode, isDaytime, visibility, barometer | |
| **HourlyForecast** | time, temperature, windSpeed, windDirection, precipProbability, shortForecast, iconCode, isDaytime | |
| **DailyForecast** | name, isDaytime, temperature, windSpeed, shortForecast, detailedForecast, iconCode, precipProbability | |
| **WeatherAlert** | id, event, headline, description, severity, urgency, instruction, effective, expires, areaDesc | |
| **MetarStation** | stationId, name, lat, lon, rawMetar, tempC, dewpointC, windDirDeg, windSpeedKt, windGustKt, visibilityMiles, altimeterInHg, slpMb, flightCategory, skyCover, wxString | v1.5.3 |

#### POI Models

| Model | Fields | Notes |
|-------|--------|-------|
| **PlaceResult** | id, name, lat, lon, category, address, phone, website, openingHours | |
| **PopulateSearchResult** | results, cacheHit, gridKey, radiusM, capped, poiNew, poiKnown | v1.5.13 |
| **FindResult** | id, type, name, lat, lon, category, distanceM, tags, address, cuisine, phone, openingHours | v1.5.26 |
| **FindCounts** | counts (Map<String,Int>), total | v1.5.26 |
| **FindResponse** | results, totalInRange, scopeM | v1.5.26 |
| **PoiWebsite** | url, source, phone, hours, address | v1.5.27 |

#### Geofence Models

| Model | Fields | Notes |
|-------|--------|-------|
| **TfrZone** | id, notam, type, description, effectiveDate, expireDate, shapes, facility, state, zoneType, metadata | v1.5.35 |
| **TfrShape** | type, points, floorAltFt, ceilingAltFt, radiusNm | Points in GeoJSON [lon,lat] |
| **GeofenceDatabaseInfo** | id, name, description, version, zoneType, zoneCount, fileSize, updatedAt, source, license, installed, installedVersion | v1.5.37 |
| **GeofenceAlert** | zoneId, zoneName, alertType, severity, distanceNm, timestamp, description, zoneType | v1.5.35 |
| **AlertSeverity** | INFO(0), WARNING(1), CRITICAL(2), EMERGENCY(3) | Enum |
| **ZoneType** | TFR, SPEED_CAMERA, SCHOOL_ZONE, FLOOD_ZONE, RAILROAD_CROSSING, MILITARY_BASE, NO_FLY_ZONE, CUSTOM | Enum |

#### Other Models

| Model | Fields | Notes |
|-------|--------|-------|
| **Webcam** | id, title, lat, lon, categories, previewUrl, thumbnailUrl, playerUrl, detailUrl, status, lastUpdated | v1.5.10 |

---

### 6.2 Repository Specifications

All repositories use OkHttp3 with 15s connect / 30s read timeouts (except GeofenceDatabaseRepository: 120s read).

#### MbtaRepository (v1.5.0)

**Direct API:** `https://api-v3.mbta.com` | **API Key:** `d2dbf0064a5a4e80b9384fea24c43c9b`

| Method | Returns | Notes |
|--------|---------|-------|
| fetchCommuterRailVehicles() | List<MbtaVehicle> | route_type=2, enriched with ETA (v1.5.43) |
| fetchSubwayVehicles() | List<MbtaVehicle> | route_type=0+1 |
| fetchBusVehicles() | List<MbtaVehicle> | route_type=3 |
| fetchStations() | List<MbtaStop> | Subway + CR, deduped by parent station |
| fetchPredictions(stopId) | List<MbtaPrediction> | Real-time arrivals |
| fetchTripSchedule(tripId) | List<MbtaTripScheduleEntry> | Full itinerary |
| fetchBusStops() | List<MbtaStop> | ~7,900 stops via proxy |
| enrichWithNextStopEta(vehicles) | List<MbtaVehicle> | Batch predictions for all trips (v1.5.43) |

#### AircraftRepository (v1.5.5)

**Proxy:** `http://10.0.0.4:3000`

| Method | Returns | Notes |
|--------|---------|-------|
| fetchAircraft(s, w, n, e) | List<AircraftState> | OpenSky bbox query |
| fetchAircraftByIcao(icao24) | AircraftState? | Global single-aircraft query |
| fetchFlightHistory(icao24) | List<FlightPathPoint> | DB flight history (v1.5.24) |

#### PlacesRepository (v1.5.0)

**Proxy:** `http://10.0.0.4:3000/overpass`

| Method | Returns | Notes |
|--------|---------|-------|
| searchPois(center, categories) | List<PlaceResult> | Adaptive radius with cap-retry |
| searchPoisCacheOnly(center, categories) | List<PlaceResult> | Cache-only, returns [] if miss |
| searchPoisForPopulate(center, categories, radiusOverride) | PopulateSearchResult | Returns cache hit flag + new/known counts |
| fetchCachedPoisInBbox(s, w, n, e) | List<PlaceResult> | Proxy POI cache bbox query |

**Adaptive Radius:** Fetch hint → execute query → if capped (>=500 results) → halve radius and retry → post feedback.

#### FindRepository (v1.5.26)

**Proxy:** `http://10.0.0.4:3000/db/pois/*`

| Method | Returns | Notes |
|--------|---------|-------|
| fetchCounts(lat, lon, radiusM) | FindCounts? | 10-min client cache, 500m move invalidation (v1.5.43) |
| findNearby(lat, lon, categories, limit, offset) | FindResponse? | Distance-sorted |
| fetchWebsite(osmType, osmId, name, lat, lon) | PoiWebsite? | 3-tier waterfall (v1.5.27) |

#### WeatherRepository (v1.5.0)

**Proxy:** `http://10.0.0.4:3000`

| Method | Returns | Notes |
|--------|---------|-------|
| fetchAlerts() | List<WeatherAlert> | All US alerts |
| fetchWeather(lat, lon) | WeatherData | Composite: current + hourly + daily + alerts (v1.5.34) |
| fetchMetars(s, w, n, e) | List<MetarStation> | Aviation weather |

#### WebcamRepository (v1.5.10)

| Method | Returns | Notes |
|--------|---------|-------|
| fetchWebcams(s, w, n, e, categories) | List<Webcam> | Windy API via proxy |

#### TfrRepository (v1.5.35)

| Method | Returns | Notes |
|--------|---------|-------|
| fetchTfrs(s, w, n, e) | List<TfrZone> | FAA TFR data via proxy |

#### GeofenceRepository (v1.5.36)

| Method | Returns | Notes |
|--------|---------|-------|
| fetchCameras(s, w, n, e) | List<TfrZone> | 200m circles |
| fetchSchools(s, w, n, e) | List<TfrZone> | Polygon or 300m circles |
| fetchFloodZones(s, w, n, e) | List<TfrZone> | FEMA polygons |
| fetchCrossings(s, w, n, e) | List<TfrZone> | 100m circles |

#### GeofenceDatabaseRepository (v1.5.37)

| Method | Returns | Notes |
|--------|---------|-------|
| fetchCatalog() | List<GeofenceDatabaseInfo> | Remote + local-only |
| downloadDatabase(id, onProgress) | Boolean | Download + save |
| deleteDatabase(id) | Boolean | Remove file |
| loadZonesFromDatabaseInBbox(id, s, w, n, e) | List<TfrZone> | Spatial query |
| importSqliteDatabase(uri, overwriteId) | Pair<Boolean, String> | SAF import (v1.5.39) |
| importCsvAsDatabase(uri, ...) | Pair<Boolean, String> | CSV import (v1.5.39) |
| getDatabaseFile(id) | File? | For export (v1.5.39) |
| validateDatabase(dbPath) | String? | Schema validation |

#### AuthRepository (v1.5.45)

**Proxy:** `http://10.0.0.4:3000/auth/*`

| Method | Returns | Notes |
|--------|---------|-------|
| register(displayName, email, password) | AuthTokens? | Device-bonded registration |
| login(email, password) | AuthTokens? | Deprecated — device-bonded model |
| refreshToken() | AuthTokens? | Auto-refresh on 401 |
| logout() | Boolean | Server-side token revocation |
| getProfile() | UserProfile? | Current user info |

Token storage in SharedPreferences (`auth_prefs`). Auto-refresh interceptor on 401 responses.

#### CommentRepository (v1.5.45)

**Proxy:** `http://10.0.0.4:3000/comments/*`

| Method | Returns | Notes |
|--------|---------|-------|
| fetchComments(osmType, osmId) | List<Comment> | With author names, ratings, votes |
| postComment(osmType, osmId, content, rating) | Comment? | Requires auth |
| voteComment(commentId, direction) | VoteResult? | Upvote/downvote |
| deleteComment(commentId) | Boolean | Soft delete (own or support/owner) |

#### ChatRepository (v1.5.45)

**Proxy:** `http://10.0.0.4:3000/chat/*` + `ws://10.0.0.4:3000` (Socket.IO)

| Method | Returns | Notes |
|--------|---------|-------|
| fetchRooms() | List<ChatRoom> | REST, includes member counts |
| createRoom(name, description) | ChatRoom? | REST |
| fetchMessages(roomId, limit, before) | List<Message> | REST, paginated |
| connect(token) | Unit | Socket.IO with JWT auth |
| disconnect() | Unit | |
| joinRoom(roomId) | Unit | Socket.IO event |
| sendMessage(roomId, content) | Unit | Socket.IO event |

Real-time events: `new_message`, `user_typing`. Connect-before-join pattern with suspendCancellableCoroutine.

### 6.3 Dependency Injection (AppModule.kt)

All repositories provided as `@Singleton` via Hilt. PlacesRepository and GeofenceDatabaseRepository require `@ApplicationContext`.

---

## 7. CACHE PROXY SERVER

**File:** `cache-proxy/server.js` (~3,700 lines) | **Port:** 3000

### 7.1 Infrastructure

#### Three Caches with Disk Persistence

| Cache | File | Purpose | Debounce |
|-------|------|---------|----------|
| General cache | `cache-data.json` | API response cache (key -> data + headers + timestamp) | 2s |
| Radius hints | `radius-hints.json` | Adaptive search radius per grid cell ("lat3:lon3" -> radius) | 2s |
| POI cache | `poi-cache.json` | Individual OSM elements deduped by type:id | 2s |

#### Bbox Snapping

| API | Precision | Grid Size | Purpose |
|-----|-----------|-----------|---------|
| METAR, Webcams | 0.01 deg | ~1.1 km | Reduce cache misses from scrolling |
| Aircraft | 0.1 deg | ~11 km | Coarser for high-frequency data |

#### Content Hash Delta Detection

MD5 of sorted `type:id` pairs from Overpass responses — skips POI cache update when data unchanged.

### 7.2 Complete Endpoint Reference

#### POI & Overpass Endpoints

| Method | Path | TTL | Upstream |
|--------|------|-----|----------|
| POST | /overpass | 365 days | overpass-api.de (serialized, fair queue) |
| GET | /pois/stats | — | In-memory |
| GET | /pois/export | — | In-memory |
| GET | /pois/bbox?s=&w=&n=&e= | — | In-memory |
| GET | /poi/:type/:id | — | In-memory |
| GET | /radius-hint?lat=&lon= | — | In-memory |
| POST | /radius-hint | — | Updates in-memory |
| GET | /radius-hints | — | In-memory |

#### PostgreSQL POI Endpoints (require DATABASE_URL)

| Method | Path | Parameters | Notes |
|--------|------|------------|-------|
| GET | /db/pois/search | q, category, s/w/n/e, lat/lon/radius, tag, limit, offset | ILIKE + Haversine |
| GET | /db/pois/nearby | lat, lon, radius (1000m), category, limit | Bbox pre-filter + Haversine sort |
| GET | /db/poi/:type/:id | — | Single POI |
| GET | /db/pois/stats | — | 5 parallel queries |
| GET | /db/pois/categories | — | Category breakdown |
| GET | /db/pois/coverage | resolution (1-4) | Geographic grid |
| GET | /db/pois/counts | lat, lon, radius | 10-min server cache (global only) |
| GET | /db/pois/find | lat, lon, categories, limit, offset | Auto-expand 50km -> 200km |
| GET | /pois/website | osm_type, osm_id, name, lat, lon | 3-tier waterfall |

#### PostgreSQL Aircraft Endpoints

| Method | Path | Parameters | Notes |
|--------|------|------------|-------|
| GET | /db/aircraft/search | q, icao24, callsign, country, s/w/n/e, since, until | Filtered search |
| GET | /db/aircraft/recent | limit | Deduped by icao24 |
| GET | /db/aircraft/stats | — | Totals, altitude distribution |
| GET | /db/aircraft/:icao24 | — | Full history + flight path |

#### External API Proxies

| Method | Path | TTL | Upstream API |
|--------|------|-----|-------------|
| GET | /aircraft?bbox= or ?icao24= | 15s | OpenSky Network (rate limited) |
| GET | /metar?bbox= | 1 hr | aviationweather.gov |
| GET | /webcams?s=&w=&n=&e= | 10 min | Windy Webcams API v3 |
| GET | /earthquakes | 2 hr | USGS |
| GET | /nws-alerts | 1 hr | NWS |
| GET | /weather?lat=&lon= | 5min-24h | NWS composite (5 calls) |
| GET | /geocode?q=&limit= | 24 hr | Photon/OSM |
| GET | /mbta/bus-stops | 24 hr | MBTA API v3 |
| GET | /tfrs?bbox= | 5-10 min | FAA tfr.faa.gov (HTML + AIXM XML) |
| GET | /cameras?bbox= | 24 hr | Overpass (speed cameras) |
| GET | /schools?bbox= | 24 hr | Overpass (schools) |
| GET | /flood-zones?bbox= | 30 days | FEMA NFHL ArcGIS |
| GET | /crossings?bbox= | 7 days | Overpass (railway crossings) |

#### POI DB Import Endpoints (v1.5.49)

| Method | Path | Parameters | Notes |
|--------|------|------------|-------|
| POST | /db/import | — | Manual trigger, returns { upserted, skipped, totalInDb, elapsed } |
| GET | /db/import/status | — | lastImportTime, running, pendingDelta, cacheSize, interval, stats |

Auto-import: initial full import 30s after startup, then delta every 15 minutes. Only imports POIs updated since last successful run. Batches of 500 with per-batch transactions.

#### Auth Endpoints (v1.5.45)

| Method | Path | Purpose |
|--------|------|---------|
| POST | /auth/register | Create account (Argon2id + JWT) |
| POST | /auth/login | Login (deprecated — device-bonded) |
| POST | /auth/refresh | Refresh tokens (365d lifetime) |
| POST | /auth/logout | Revoke refresh token |
| GET | /auth/me | Current user profile |
| GET | /auth/debug/users | All users (requires auth + owner/support role) |

#### Comment Endpoints (v1.5.45)

| Method | Path | Purpose |
|--------|------|---------|
| GET | /comments/:osm_type/:osm_id | Fetch comments for a POI |
| POST | /comments/:osm_type/:osm_id | Post comment with rating (requires auth) |
| POST | /comments/:id/vote | Upvote/downvote (requires auth) |
| DELETE | /comments/:id | Soft delete (own or support/owner) |

#### Chat Endpoints (v1.5.45)

| Method | Path | Purpose |
|--------|------|---------|
| GET | /chat/rooms | List all rooms with member counts |
| POST | /chat/rooms | Create new room (requires auth) |
| GET | /chat/rooms/:id/messages | Message history (paginated) |
| WS | Socket.IO | join_room, leave_room, send_message, typing events |

#### Geofence & Admin Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | /geofences/catalog | Database catalog (enriched with file sizes) |
| GET | /geofences/database/:id/download | SQLite file download |
| GET | /cache/stats | Cache performance + OpenSky rate + DB import stats |
| POST | /cache/clear | Clear all caches + disk files |

### 7.3 OpenSky Rate Limiter

| Setting | Value |
|---------|-------|
| Daily limit | 4,000 (authenticated) / 100 (anonymous) |
| Safety margin | 90% = 3,600 effective |
| Min interval | ~24s between upstream requests |
| Backoff on 429 | 10s -> 20s -> 40s -> 80s -> 160s -> 300s cap |
| Stale fallback | Returns expired cache with X-Cache: STALE |
| Account | DeanMauriceEllis, client credentials flow |

### 7.4 Overpass Queue System

| Setting | Value |
|---------|-------|
| Min upstream interval | 10s |
| Per-client queue cap | 5 requests |
| Fair scheduling | Round-robin across clients |
| Cache-only mode | X-Cache-Only: true, merges 3x3 grid neighbors |
| Content dedup | MD5 hash of sorted element IDs |
| Covering cache | Checks 2x and 4x radius cached results |

---

## 8. DATABASE LAYER

### 8.1 PostgreSQL Schema

**Database:** `locationmapapp` | **User:** `witchdoctor` | **Tables owned by:** `postgres`

#### pois table (180,059 rows — auto-imported every 15min)

| Column | Type | Notes |
|--------|------|-------|
| osm_type | TEXT | PK part 1 (node/way/relation) |
| osm_id | BIGINT | PK part 2 |
| lat, lon | DOUBLE PRECISION | |
| name | TEXT | Nullable |
| category | TEXT | e.g., "amenity=restaurant" |
| tags | JSONB | Full OSM tags (GIN indexed) |
| first_seen, last_seen | TIMESTAMPTZ | |

**Indexes:** category, name (partial), tags (GIN), (lat,lon), (category,lat,lon)

#### aircraft_sightings table (501+ rows)

| Column | Type | Notes |
|--------|------|-------|
| id | SERIAL | PK |
| icao24 | TEXT | Aircraft identifier |
| callsign, origin_country | TEXT | |
| first_seen, last_seen | TIMESTAMPTZ | |
| first/last_lat/lon/altitude/heading | DOUBLE PRECISION | |
| last_velocity, last_vertical_rate | DOUBLE PRECISION | m/s |
| squawk | TEXT | |
| on_ground | BOOLEAN | |

**Sighting logic:** 5-min gap = new row. 195 unique aircraft as of 2026-03-01.

#### Social Tables (v1.5.45, 8 tables)

| Table | Purpose |
|-------|---------|
| users | User accounts (UUID PK, display_name, role, created_at) |
| auth_lookup | Email → user mapping (Argon2id password hash) |
| refresh_tokens | JWT refresh tokens (family-based rotation, 365d lifetime) |
| poi_comments | Comments on POIs (content, rating 1-5, soft delete) |
| comment_votes | Upvote/downvote tracking (user + comment, direction) |
| chat_rooms | Chat rooms (name, description, type, created_by) |
| room_memberships | User ↔ room join tracking |
| messages | Chat messages (content, author, room, timestamp) |

All granted to `witchdoctor` user. DDL runs as `sudo -u postgres bash create-social-tables.sh`.

**Security hardening** (v1.5.47): sanitizeText/sanitizeMultiline on all text inputs, rate limiting (auth 10/15min, comments 10/1min, rooms 5/1hr, Socket.IO 30/60s), login lockout (5 fails → 15min), JSON body limit 16kb.

### 8.2 Geofence SQLite Schema

Shared across all 4 downloadable databases + user imports:

| Table | Purpose |
|-------|---------|
| db_meta | Key-value metadata (id, name, version, zone_type, zone_count, etc.) |
| zones | Zone definitions with geometry (polygon or circle) + bbox index |

**4 Pre-Built Databases (220,657 total zones):**

| Database | Zones | Source | Build Script |
|----------|-------|--------|-------------|
| military-bases.db | 1,944 | HIFLD/NTAD ArcGIS | build-military.js |
| excam-cameras.db | 109,500 | WzSabre XZ/NDJSON | build-excam.js |
| nces-schools.db | 101,390 | NCES EDGE ArcGIS | build-nces.js |
| dji-nofly.db | 7,823 | DJI NFZDB CSV | build-dji-nofly.js |

### 8.3 Build Scripts

All in `cache-proxy/geofence-databases/`:

| Script | Source | Fetch Method | Output |
|--------|--------|-------------|--------|
| build-military.js | ArcGIS NTAD Military Bases | Paginated GeoJSON (200/batch) | Polygons |
| build-excam.js | wzsabre.rocks cameras.download | XZ decompression + NDJSON | 200m circles |
| build-nces.js | NCES EDGE ArcGIS MapServer | Paginated JSON (1000/batch) | 300m circles |
| build-dji-nofly.js | GitHub DJI NFZDB CSV | CSV download + parse | Variable circles |

All scripts auto-update `catalog.json` with final zone count, file size, and date.

**POI Import (Automated — v1.5.49):**
Server.js runs delta imports every 15 minutes (only POIs updated since last successful run). Initial full import 30s after startup. Manual trigger: `POST /db/import` or standalone `node import-pois.js`.

Category derivation: amenity > shop > tourism > leisure > historic > office. UPSERTs into pois table (ON CONFLICT osm_type, osm_id).

---

## 9. DEBUG & TESTING INFRASTRUCTURE

### 9.1 Debug HTTP Server

**File:** `app/src/main/java/com/example/locationmapapp/util/DebugHttpServer.kt`
**Port:** 8085 | **Introduced:** v1.5.18

Minimal embedded HTTP/1.0 server running on `Dispatchers.IO`. CORS enabled. Lifecycle-aware (start in onCreate, stop in onDestroy).

**Access:** `adb forward tcp:8085 tcp:8085 && curl localhost:8085/state | jq .`

### 9.2 Debug Endpoints (24)

**File:** `app/src/main/java/com/example/locationmapapp/util/DebugEndpoints.kt`

| Endpoint | Description | Version |
|----------|-------------|---------|
| GET / | List all endpoints | v1.5.18 |
| GET /state | Map center, zoom, bounds, marker counts, follow state, statusLine | v1.5.18 |
| GET /logs?tail=&filter=&level= | Debug log entries (filtered) | v1.5.18 |
| GET /logs/clear | Clear log buffer | v1.5.18 |
| GET /map?lat=&lon=&zoom= | Set/read map position | v1.5.18 |
| GET /markers?type=&limit= | List markers by type | v1.5.18 |
| GET /markers/tap?type=&index= | Trigger marker click (reflection) | v1.5.18 |
| GET /markers/nearest?lat=&lon=&type= | Find nearest markers | v1.5.18 |
| GET /markers/search?q=&type= | Search markers (fuzzy) | v1.5.18 |
| GET /vehicles?type=&limit=&index= | Raw vehicle data | v1.5.21 |
| GET /stations?limit=&q= | Station data with search | v1.5.21 |
| GET /bus-stops?limit=&q= | Bus stop data with search | v1.5.21 |
| GET /screenshot | PNG of root view | v1.5.18 |
| GET /livedata | All ViewModel LiveData values | v1.5.18 |
| GET /prefs | Dump SharedPreferences | v1.5.18 |
| GET /toggle?pref=&value= | Toggle preference + fire handler | v1.5.18 |
| GET /search?lat=&lon= | Trigger POI search at point | v1.5.18 |
| GET /refresh?layer= | Force refresh a layer | v1.5.18 |
| GET /follow?type=&icao=/index= | Follow aircraft/vehicle | v1.5.18 |
| GET /stop-follow | Stop following | v1.5.18 |
| GET /perf | Memory, threads, uptime | v1.5.18 |
| GET /overlays | Map overlay inventory | v1.5.18 |
| GET /geofences | Loaded geofence zones by type | v1.5.35 |
| GET /geofences/alerts | Active geofence alerts | v1.5.35 |

**Marker Types:** poi, stations, trains, subway, buses, aircraft, webcams, metar, gps

**Fuzzy Search:** Abbreviation expansion (mass->massachusetts, ave->avenue, st->street, sq->square, etc.)

### 9.3 Test Harness Scripts

| Script | Lines | Duration | Tests | Purpose |
|--------|-------|----------|-------|---------|
| test-app.sh | ~650 | ~1 min | 30+ | Quick smoke test (9 suites) |
| overnight-test.sh | ~1,850 | 6-8 hrs | 67+ | Endurance: memory, cache, OpenSky, transit ramp |
| morning-transit-test.sh | ~500 | ~1 hr | 36+ | Deep transit validation (peak hours) |
| run-full-test-suite.sh | ~100 | 7-9 hrs | All | Chains overnight -> morning |

**Latest Results (2026-03-01): 103 PASS, 0 FAIL**

**Test Output Structure:**
```
overnight-runs/YYYY-MM-DD_HHMM/
  events.log          — timestamped PASS/FAIL/WARN/INFO
  time-series.csv     — 30-column CSV (memory, markers, cache, OpenSky)
  report.md           — final summary with recommendations
  baseline/           — initial state snapshots
  snapshots/          — periodic JSON state every 5 min
  screenshots/        — PNG captures every 15 min
  logs/               — error snapshots
```

---

## 10. XML LAYOUTS & RESOURCES

### Layouts (4 files)

| Layout | Purpose | Key Views |
|--------|---------|-----------|
| activity_main.xml | Main screen | CoordinatorLayout, AppBarLayout, MapView, zoom slider, FAB |
| toolbar_two_row.xml | Toolbar | 40dp icon row (Weather + Alerts + Grid) + 24dp status line |
| grid_dropdown_panel.xml | Grid menu | 3×4 PopupWindow with 12 labeled buttons |
| fab_menu_item.xml | Menu item | Icon (26dp) + label (13sp) on pill background |

### Drawable Resources (58+ files)

| Category | Count | Examples |
|----------|-------|---------|
| Weather icons | 22 | ic_wx_clear_day, ic_wx_rain, ic_wx_thunderstorm, ic_wx_tornado |
| Grid/toolbar icons | 13 | ic_grid_menu, ic_transit_rail, ic_aircraft, ic_radar, ic_search, ic_social, ic_chat, ic_profile, ic_legend |
| POI category icons | 8 | ic_restaurant, ic_gas_station, ic_park, ic_civic |
| Utility icons | 7 | ic_crosshair, ic_record_gps, ic_gpx_export, ic_gps |
| Feature icons | 4 | ic_share, ic_dark_mode, ic_star, ic_star_outline |
| Shapes | 4 | fab_item_bg, zoom_button_bg, zoom_bubble_bg, zoom_slider_track |
| Launcher | 3 | ic_launcher_background, ic_launcher_foreground, ic_launcher |

### Menu Resources (8 files)

| Menu | Items | Purpose |
|------|-------|---------|
| menu_main_toolbar.xml | 9 | Top-level toolbar items |
| menu_poi.xml | 17 | POI category toggles (all checkable) |
| menu_transit.xml | 8 | MBTA layers + frequencies |
| menu_utility.xml | 13 | GPS, populate, debug tools |
| menu_alerts.xml | 8 | Geofence overlays + DB manager |
| menu_radar.xml | 5 | Radar + METAR toggles + sliders |
| menu_aircraft.xml | 3 | Aircraft toggle + frequency + auto-follow |
| menu_cams.xml | 2 | Webcam toggle + categories |

### Theme

**Base:** Theme.MaterialComponents.DayNight.NoActionBar
**Primary:** #1565C0 (Material Blue 700)
**Accent:** #FF6F00 (Material Deep Orange 700)
**Dark mode:** Auto via DayNight parent

---

## 11. MAP INTERACTION MODEL

| Gesture | Action | Version |
|---------|--------|---------|
| **Tap POI marker** | Open POI detail dialog (info, comments, actions) | v1.5.46 |
| **Long press (~2s)** | Manual mode: center map, auto-zoom to 18, search POIs, fetch weather + alerts | v1.5.0/v1.5.48 |
| **Scroll/pan** | Load cached POIs for visible area (viewport-only), debounced layer reloads | v1.5.6 |
| **Zoom in** | Show POI labels at >=18, vehicle labels at >=18, bus stops at >=15 | v1.5.7/v1.5.29 |
| **Zoom out** | Hide transit at <=10, hide POIs at <10, 5000-marker guard | v1.5.25/v1.5.42 |
| **Tap vehicle** | Vehicle detail dialog (Follow/Route/Arrivals) | v1.5.19 |
| **Tap station** | Arrival board (30s auto-refresh) -> tap row -> trip schedule | v1.5.17 |
| **Tap bus stop** | Arrival board with bus predictions | v1.5.19 |
| **Tap aircraft** | Follow mode (global ICAO24 tracking, flight trail) | v1.5.6 |
| **Tap webcam** | Preview image + View Live button | v1.5.10 |
| **Tap METAR** | Decoded weather info (wind, sky, flight category) | v1.5.4 |
| **Tap geofence** | Zone detail dialog (color bar, metadata) | v1.5.35 |
| **Tap status line** | Stop follow/scan (context-sensitive) | v1.5.40 |
| **Tap Find result** | POI Detail dialog (info, website, action buttons) | v1.5.27 |
| **Long-press Find** | Filter map to single category | v1.5.26 |
| **Touch during idle scan** | Cancel idle populate, reset 10-min timer | v1.5.41/v1.5.48 |

---

## 12. EXTERNAL API ROUTING TABLE

| API | App Endpoint | Proxy Route | TTL | Auth |
|-----|-------------|-------------|-----|------|
| Overpass (POI) | POST 10.0.0.4:3000/overpass | POST /overpass | 365 days | X-Client-ID |
| NWS Alerts | GET 10.0.0.4:3000/nws-alerts | GET /nws-alerts | 1 hour | User-Agent |
| NWS Weather | GET 10.0.0.4:3000/weather | GET /weather (composite) | 5min-24h | User-Agent |
| Aviation Weather | GET 10.0.0.4:3000/metar | GET /metar | 1 hour | None |
| OpenSky Network | GET 10.0.0.4:3000/aircraft | GET /aircraft | 15s | OAuth2 Bearer |
| Windy Webcams | GET 10.0.0.4:3000/webcams | GET /webcams | 10 min | API key |
| FAA TFRs | GET 10.0.0.4:3000/tfrs | GET /tfrs | 5-10 min | None (scraping) |
| Overpass (cameras) | GET 10.0.0.4:3000/cameras | GET /cameras | 24 hr | None |
| Overpass (schools) | GET 10.0.0.4:3000/schools | GET /schools | 24 hr | None |
| FEMA NFHL | GET 10.0.0.4:3000/flood-zones | GET /flood-zones | 30 days | None |
| Overpass (crossings) | GET 10.0.0.4:3000/crossings | GET /crossings | 7 days | None |
| Photon Geocoder | GET 10.0.0.4:3000/geocode | GET /geocode | 24 hr | None |
| MBTA Bus Stops | GET 10.0.0.4:3000/mbta/bus-stops | GET /mbta/bus-stops | 24 hr | API key |
| USGS Earthquakes | GET 10.0.0.4:3000/earthquakes | GET /earthquakes | 2 hr | None |
| Auth | POST 10.0.0.4:3000/auth/* | POST /auth/register, /login, /refresh, /logout; GET /me | — | JWT |
| Comments | GET/POST 10.0.0.4:3000/comments/* | GET/POST/DELETE /comments/* | live | JWT |
| Chat | GET/POST 10.0.0.4:3000/chat/* | REST + Socket.IO | live | JWT |
| DB Import | POST 10.0.0.4:3000/db/import | POST /db/import; GET /db/import/status | live | None |
| MBTA Vehicles | direct api-v3.mbta.com | — | — | API key |
| MBTA Stations | direct api-v3.mbta.com | — | — | API key |
| MBTA Predictions | direct api-v3.mbta.com | — | — | API key |
| MBTA Schedules | direct api-v3.mbta.com | — | — | API key |
| Radar Tiles | direct mesonet.agron.iastate.edu | — | — | None |

---

## 13. PROJECT STATE

The complete STATE.md file is maintained at the project root and serves as the primary reference for current feature status, architecture, and configuration. Key metrics as of v1.5.49:

- **17 POI categories** with 127 subtypes
- **180,059 POIs** in PostgreSQL (auto-imported every 15min)
- **501+ aircraft sightings** across 195 unique aircraft
- **220,657 geofence zones** across 4 downloadable databases
- **8 geofence zone types** (5 live + 3 downloadable-only)
- **50+ proxy endpoints**, **24 debug endpoints**
- **103 automated tests**, 0 failures
- **7-priority StatusLineManager**
- **3-tier website resolution** (OSM -> Wikidata -> DuckDuckGo)
- **Social layer**: device-bonded auth, POI comments with ratings, real-time Socket.IO chat
- **Security hardening**: server-side sanitization, rate limiting, login lockout, client-side input limits

---

## 14. FULL CHANGELOG

### v1.5.49 (2026-03-03) — Automated POI DB Import
- Delta POI import every 15 minutes (inline in server.js)
- Initial full import 30s after startup, then delta-only
- POST /db/import (manual trigger), GET /db/import/status
- dbImport stats in /cache/stats

### v1.5.48 (2026-03-03) — Startup/Behavior Tuning
- Default zoom 14 → 18 (first GPS fix, GoTo, long-press)
- Radar transparency 70% → 35%
- Idle auto-populate POI density guard (≥100 POIs within 10km → skip)

### v1.5.47 (2026-03-03) — Security Hardening
- Server-side sanitization (sanitizeText/sanitizeMultiline)
- Rate limiting (express-rate-limit): auth, comments, rooms, Socket.IO
- Login lockout: 5 fails → 15min IP lock
- Client-side InputFilter.LengthFilter, comment char counter
- Debug endpoint auth-gated

### v1.5.46 (2026-03-03) — POI Click-Through & Device-Bonded Auth
- POI marker tap → full detail dialog (was info-window only)
- Device-bonded auth model (register-only, no login/logout)
- PlaceResult.id "type:id" format for OSM type preservation
- Fixed: delete button hidden on deleted comments

### v1.5.45 (2026-03-03) — Social Layer (Auth, Comments, Chat)
- Auth: register/login, Argon2id + JWT (15min access + 365d refresh)
- POI Comments: ratings, votes, soft-delete, in detail dialog
- Chat: Socket.IO real-time, room list, global room, create rooms
- Grid dropdown 3×4 (12 buttons) — Row 3: Social, Chat, Profile, Legend
- 8 social DB tables, 3 new Android repositories

### v1.5.44 (2026-03-03) — Share, Dark Mode, Favorites, Search, Animated Radar
- Share POI: 5th action button, shares via Android share sheet
- Dark mode: CartoDB Dark Matter tiles, toolbar moon icon
- Favorites: star in POI detail, SharedPreferences+JSON storage
- Text search: search bar in Find dialog, 500ms debounce
- Animated radar: 7-frame NEXRAD loop (35 min), configurable speed

### v1.5.43 (2026-03-02) — Train ETA, POI Categories, Find Radius
- Train next-stop ETA badge (batch predictions API)
- 17th POI category (Offices & Services) + 46 new subtypes
- Find dialog radius-scoped counts (10km Haversine)
- Find dialog auto-fit cell heights
- Fixed: 10km probe zoom (panMap=false)
- Fixed: GoTo stops 10km probe

### v1.5.42 (2026-03-02) — 10km Probe Populate
- 10km Probe Populate (expanding spiral, pan without zoom)
- estimateFillRadius() for density-based radius recommendation
- POI display zoom guard lowered to 10, 5000-marker count guard

### v1.5.41 (2026-03-02) — Idle Populate Improvements
- 5-min GPS stationarity threshold (was 60s)
- Touch-to-stop, state preservation, no auto-pan
- Long-press fetches weather + alerts

### v1.5.40 (2026-03-02) — Slim Toolbar Redesign
- Slim toolbar (40dp) + status line (24dp) + grid dropdown (2x4)
- StatusLineManager with 7 priority levels
- Eliminated followBanner + 10 ICON_* constants

### v1.5.39 (2026-03-02) — Database Import & Export
- Import SQLite .db and CSV via SAF file picker
- Export via share intent (FileProvider)
- Local-only database display

### v1.5.38 (2026-03-02) — 3 More Geofence Databases
- ExCam cameras (109,500), NCES schools (101,390), DJI no-fly (7,823)
- Total: 4 databases, 220,657 zones

### v1.5.37 (2026-03-02) — Downloadable DB Infrastructure
- GeofenceDatabaseRepository, Database Manager dialog
- Military bases database (1,944 zones)
- Catalog + download proxy endpoints

### v1.5.36 (2026-03-02) — 4 Additional Zone Types
- Speed cameras, school zones, flood zones, railroad crossings
- GeofenceRepository with 4 proxy endpoints

### v1.5.35 (2026-03-02) — TFR Geofence System
- GeofenceEngine (JTS R-tree), TFR polygons, entry/proximity/exit alerting
- FAA TFR scraping (AIXM XML), severity-colored alerts icon

### v1.5.34 (2026-03-02) — Weather Dialog
- Rich weather dialog (current + hourly + daily + alerts)
- Composite /weather endpoint (5 NWS calls), 22 weather icons

### v1.5.33 (2026-03-02) — Idle Populate & Delta Cache
- Idle auto-populate (60s stationarity), X-Client-ID header
- Covering cache, content hash delta, per-client fair queue

### v1.5.32 (2026-03-02) — Geocode Autocomplete
- Photon geocoder via proxy /geocode, US-only, 500ms debounce

### v1.5.31 (2026-03-01) — Icon Toolbar & GoTo
- Icon toolbar (8 icons), Go to Location dialog

### v1.5.30 (2026-03-01) — Smart GPS
- Dead zone filtering (100m), speed-adaptive polling, 3km POI threshold

### v1.5.29 (2026-03-01) — Labeled Vehicles
- Labeled vehicle markers at zoom >= 18

### v1.5.28 (2026-03-01) — Silent Fill & 23 Subtypes
- Silent background POI fill, 23 new POI subtypes

### v1.5.27 (2026-03-01) — POI Detail Dialog
- POI Detail dialog with website resolution (3-tier waterfall)
- In-app WebView, Directions/Call/Reviews/Map buttons

### v1.5.26 (2026-03-01) — Find Dialog
- Find dialog (category grid -> subtype grid -> results)
- /db/pois/counts and /db/pois/find endpoints, long-press filter mode

### v1.5.25 (2026-03-01) — Legend & Zoom Guards
- Legend dialog (7 sections), transit zoom guard, long-press auto-zoom

### v1.5.24 (2026-03-01) — Flight Trail
- Aircraft flight path visualization (altitude-colored polyline)
- DB history + live path, 1000-point cap

### v1.5.23 (2026-03-01) — Aircraft DB & Fuzzy Search
- 4 aircraft DB endpoints, fuzzy search with abbreviations
- Bbox snapping (0.01/0.1 deg)

### v1.5.22 (2026-03-01) — Test Harness
- overnight-test.sh, morning-transit-test.sh, run-full-test-suite.sh
- 103 PASS, 0 FAIL

### v1.5.21 (2026-03-01) — Debug API Expansion
- /vehicles, /stations, /bus-stops endpoints, relatedObject on markers
- test-app.sh (30+ tests)

### v1.5.20 (2026-03-01) — Bus Stops via Proxy
- Bus stops cached through proxy (24h TTL)

### v1.5.19 (2026-03-01) — Bus Stops & Vehicle Dialog
- ~7,900 bus stop markers, zoom >= 15 guard
- Vehicle detail dialog (Follow/Route/Arrivals)

### v1.5.18 (2026-02-28) — Debug HTTP Server
- Embedded debug server (port 8085), 19 initial endpoints

### v1.5.17 (2026-02-28) — Train Stations
- ~270 station markers, arrival board dialog, trip schedule dialog

### v1.5.16 (2026-02-28) — Populate v2
- Probe-calibrate-subdivide scanner, recursive 3x3 subdivision
- Overpass queue (serialized, 10s gap, fair queue)

### v1.5.15 (2026-02-28) — Cap Retry & Fuzzy Hints
- Auto-retry on 500-element cap, 20km fuzzy hints, MIN_RADIUS 100m

### v1.5.14 (2026-02-28) — Populate Tuning
- 30s fixed pacing, cap detection, zoom-14 view

### v1.5.13 (2026-02-28) — Populate Scanner v1
- Spiral grid scanner, X-Cache header

### v1.5.12 (2026-02-28) — Air Menu & Staleness
- Dedicated Air menu, vehicle staleness detection (>2 min)

### v1.5.11 (2026-02-28) — Rate Limiter
- OpenSky rate limiter (exponential backoff), stale cache fallback
- Webcam live player in WebView

### v1.5.10 (2026-02-28) — Webcams
- Webcam layer (Windy API, 18 categories, preview + live player)

### v1.5.9 (2026-02-28) — Viewport-Only POIs
- Viewport-only markers (~100-400), LRU icon cache (500 entries)

### v1.5.8 (2026-02-28) — DB Queries & OpenSky Auth
- 6 PostgreSQL endpoints, aircraft sightings DB, OpenSky OAuth2

### v1.5.7 (2026-02-28) — Auto-Follow & POI Labels
- Auto-follow aircraft (POI Builder), POI labels at zoom >= 18

### v1.5.6 (2026-02-28) — Aircraft Follow
- Aircraft follow mode (icao24), enhanced markers (callsign, SPI)
- Cached POI bbox display

### v1.5.5 (2026-02-28) — Aircraft Tracking
- Aircraft tracking (OpenSky), altitude-colored airplane icons

### v1.5.4 (2026-02-28) — Arrows & METAR
- Vehicle direction arrows, human-readable METAR tap info

### v1.5.3 (2026-02-28) — 16 POI Categories
- 16 categories with submenus, rich METAR markers, central config

### v1.5.2 (2026-02-28) — PostgreSQL
- POI database (PostgreSQL), JSONB tags, import script

### v1.5.1 (2026-02-27) — Adaptive Radius
- Adaptive POI search radius, per-grid-cell hints

### v1.5.0 (2026-02-27) — Foundation
- Cache proxy, vehicle follow, manual location, radar, 5dp dots

---

## 15. KNOWN ISSUES & FUTURE WORK

### Known Issues
- MBTA API key hardcoded in MbtaRepository.kt (should be in BuildConfig/secrets)
- Windy Webcams API key hardcoded in server.js (free tier)
- 10.0.0.4 proxy IP hardcoded (local network only)
- OpenSky state vector category field (index 17) not always present (guarded)

### Future Work
- Monitor cache growth and hit rates over time
- Evaluate proxy -> remote deployment for non-local testing
- Find dialog enhancements: pagination, search within results, recent searches
- Fill Probe Populate implementation (stub exists in v1.5.42)
- Geofence Phase 5: Advanced Sources (deferred — sufficient coverage)
- Social Phase D: room bans, promote/demote mod, mute/kick/ban
- Social governance: COPPA age gate, email encryption, content moderation pipeline

---

## APPENDIX: KEY FILE INDEX

| File | Lines | Purpose |
|------|-------|---------|
| `ui/MainActivity.kt` | ~8,500 | Main activity, all UI, map, markers, dialogs, social |
| `ui/MainViewModel.kt` | ~900 | ViewModel, 22+ LiveData fields, 60+ methods |
| `ui/MarkerIconHelper.kt` | 680 | Icon rendering, LRU cache, labeled markers |
| `ui/StatusLineManager.kt` | 105 | 7-priority toolbar status line |
| `ui/WeatherIconHelper.kt` | ~50 | NWS icon code mapping |
| `ui/menu/AppBarMenuManager.kt` | ~900 | Toolbar, 3×4 grid dropdown, sub-menus, prefs |
| `ui/menu/MenuEventListener.kt` | ~250 | 45+ callback interface |
| `ui/menu/PoiCategories.kt` | 366 | 17 categories, 127 subtypes |
| `data/model/Models.kt` | ~450 | 32+ data classes |
| `data/repository/MbtaRepository.kt` | ~500 | MBTA transit + predictions |
| `data/repository/AircraftRepository.kt` | ~200 | OpenSky aircraft |
| `data/repository/PlacesRepository.kt` | ~350 | Overpass POI with adaptive radius |
| `data/repository/WeatherRepository.kt` | ~200 | NWS + METAR |
| `data/repository/FindRepository.kt` | ~150 | Find dialog DB queries + text search |
| `data/repository/WebcamRepository.kt` | ~100 | Windy webcams |
| `data/repository/TfrRepository.kt` | ~100 | FAA TFRs |
| `data/repository/GeofenceRepository.kt` | ~250 | 4 live zone types |
| `data/repository/GeofenceDatabaseRepository.kt` | ~400 | Downloadable DBs + import/export |
| `data/repository/AuthRepository.kt` | ~200 | JWT auth, token storage, auto-refresh |
| `data/repository/CommentRepository.kt` | ~150 | POI comments CRUD |
| `data/repository/ChatRepository.kt` | ~250 | Socket.IO chat + REST rooms/messages |
| `di/AppModule.kt` | ~60 | Hilt DI configuration (12 repositories) |
| `util/GeofenceEngine.kt` | 282 | JTS R-tree spatial engine |
| `util/FavoritesManager.kt` | ~100 | SharedPreferences+JSON favorites CRUD |
| `util/DebugHttpServer.kt` | ~100 | Embedded HTTP server |
| `util/DebugEndpoints.kt` | ~600 | 24 debug endpoint handlers |
| `cache-proxy/server.js` | ~3,700 | Express proxy, 50+ endpoints, auth, chat, auto-import |
| `cache-proxy/schema.sql` | ~50 | PostgreSQL schema |
| `cache-proxy/import-pois.js` | ~100 | POI bulk import (standalone, superseded by auto-import) |
| `cache-proxy/geofence-databases/build-military.js` | ~200 | Military base builder |
| `cache-proxy/geofence-databases/build-excam.js` | ~200 | Speed camera builder |
| `cache-proxy/geofence-databases/build-nces.js` | ~200 | School builder |
| `cache-proxy/geofence-databases/build-dji-nofly.js` | ~150 | DJI no-fly builder |
| `test-app.sh` | ~650 | Quick test suite (30+ tests) |
| `overnight-test.sh` | ~1,850 | Overnight harness |
| `morning-transit-test.sh` | ~500 | Morning transit test |
| `run-full-test-suite.sh` | ~100 | Master test runner |

---

**Document Generated:** 2026-03-03 (updated from v1.5.43 to v1.5.49)
**Total Kotlin Lines:** ~11,500
**Total JavaScript Lines:** ~4,800
**Total Test Script Lines:** ~3,100
**GitHub:** https://github.com/deanmauriceellis-cloud/LocationMapApp.git
