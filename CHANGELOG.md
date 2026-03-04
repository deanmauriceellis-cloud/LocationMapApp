# LocationMapApp — Changelog

> Releases prior to v1.5.30 archived in `CHANGELOG-ARCHIVE.md`.

## [1.5.50] — 2026-03-03

### Changed
- **Find dialog ScrollView wrapping** — main category grid and subtype grids wrapped in ScrollView with weight=1f layout; dialog height now 85% of screen; all 18 cells (Favorites + 17 categories) and large subtype grids (26 Shopping, 18 Entertainment) scroll smoothly instead of clipping at screen bottom
- **Cell height cap** — auto-fit cell height now capped at dp(120) max to prevent oversized cells on categories with few subtypes
- **Find distance unlimited** — `/db/pois/find` scope expands through 50km → 200km → 1000km → global (no bbox); rare POIs like zoos and theme parks now return results regardless of distance

### Added
- **16 new POI subtypes** across 6 categories (122 → 138 total subtypes):
  - Food & Drink: Breweries (`craft=brewery`), Wineries (`craft=winery`), Distilleries (`craft=distillery`)
  - Civic & Gov: Recycling (`amenity=recycling`), Embassies (`office=diplomatic`)
  - Shopping: Pet Stores, Electronics, Bicycle Shops, Garden Centers
  - Tourism & History: Zoos, Aquariums, Theme Parks
  - Entertainment: Water Parks, Mini Golf, Escape Rooms
  - Parks & Rec: Beaches (`leisure=beach_resort`)
- **`craft` category key** — added to `POI_CATEGORY_KEYS` in server.js for brewery/winery/distillery POI import recognition

## [1.5.49] — 2026-03-03

### Added
- **Automated POI database import** — delta upsert every 15 minutes, inline in server.js
  - Initial full import 30s after proxy startup (cache loaded from disk by then)
  - Delta approach: only imports POIs with `lastSeen >= lastDbImportTime`, avoiding redundant full-cache upserts
  - Batched in groups of 500 with individual transactions (short DB locks)
  - Mutex prevents overlapping runs; failed imports retry next cycle without advancing timestamp
  - `POST /db/import` — manual trigger, returns `{ upserted, skipped, totalInDb, elapsed }`
  - `GET /db/import/status` — read-only: lastImportTime, running, pendingDelta, cacheSize, interval, stats
  - `dbImport` object added to `GET /cache/stats` response
  - Startup banner shows auto-import enabled/disabled state

## [1.5.48] — 2026-03-03

### Changed
- **Default zoom level 14 → 18** — first GPS fix, Go To location, and long-press all zoom to 18 for a street-level default view
- **Radar transparency 70% → 35%** — NEXRAD radar overlay is now 50% more translucent by default; user can still adjust via menu
- **Idle auto-populate POI density guard** — before starting the idle scanner, queries `/db/pois/counts` for a 10km radius; skips if ≥100 POIs already exist nearby (prevents redundant scanning in dense areas)

## [1.5.47] — 2026-03-03

### Added
- **Security hardening — social layer input protection**
  - **Server-side sanitization**: `sanitizeText()` and `sanitizeMultiline()` strip HTML tags, control chars, collapse whitespace, enforce length caps
  - **Rate limiting** (express-rate-limit): auth 10/15min per IP, comments 10/1min per user, room create 5/1hr per user
  - **Socket.IO rate limiting**: 30 messages/60s sliding window per connection, emits `error_message` on exceed
  - **Failed login lockout**: 5 attempts → 15min IP lockout, auto-cleanup every 30min
  - **Endpoint validation**: displayName 2-50 chars unicode whitelist, password max 128 chars, email format check, osmType enum validation, rating integer check, roomType enum validation, comment content reduced from 2000→1000 chars
  - **JSON body limit**: 16kb (was unlimited)
  - **Debug endpoint protected**: `/auth/debug/users` now requires auth + owner/support role
  - **Client-side input limits**: `InputFilter.LengthFilter` on all social EditTexts (displayName 50, email 255, password 128, room name 100, room desc 255, message 1000, comment 1000)
  - **Client-side validation**: displayName min 2 chars, email format check, room name min 2 chars
  - **Comment character counter**: "0 / 1000" live counter in add-comment dialog

### Dependencies
- Proxy: express-rate-limit ^7.5.1

## [1.5.46] — 2026-03-03

### Added
- **POI marker click-through** — tapping any POI marker on the map now opens the full detail dialog
  - `openPoiDetailFromPlace()`: converts PlaceResult → FindResult for detail dialog
  - Previously, marker tap only showed a basic info bubble with no click-through

### Changed
- **Device-bonded auth model** — accounts are permanently tied to the device
  - Auth dialog is now Register-only (no login/logout toggle)
  - Profile dialog: Logout button removed
  - Refresh token lifetime extended from 30 days to 365 days
  - Info text: "Your account is bonded to this device"
- **PlaceResult.id format** — now stores `"type:id"` (e.g., "way:292110841") for OSM type preservation
  - Both `parseOverpassJson` and `parsePoiExportJson` updated

### Fixed
- **Delete button on deleted comments** — now hidden when comment `isDeleted` is true

## [1.5.45] — 2026-03-03

### Added
- **Social layer prototype** — auth, POI comments, real-time chat
- **Auth system** — register/login with Argon2id + JWT (15min access + 30d refresh tokens)
  - Family-based refresh token rotation with reuse revocation
  - `AuthRepository.kt`: auto-refresh expired tokens, SharedPreferences token storage
  - Proxy endpoints: POST /auth/register, /auth/login, /auth/refresh, /auth/logout, GET /auth/me
  - Debug endpoint: GET /auth/debug/users
- **POI Comments** — comments on any POI with star ratings and voting
  - `CommentRepository.kt`: fetch, post, vote, delete with authenticated requests
  - Comments section in POI detail dialog: author name, relative time, star ratings, vote arrows
  - "Add Comment" sub-dialog with 5-star rating selector and text input
  - Proxy endpoints: GET/POST /comments, POST /comments/:id/vote, DELETE /comments/:id
  - Soft delete, role-based authorization (own comments or platform support/owner)
- **Chat rooms** — real-time messaging via Socket.IO
  - `ChatRepository.kt`: Socket.IO connect/disconnect, room join/leave, send/receive messages
  - Auto-creates "Global" room on proxy startup
  - Room list dialog with member counts, create room dialog
  - Chat room dialog with message bubbles, author names, relative timestamps, send bar
  - Proxy: Socket.IO auth middleware (JWT), REST endpoints for rooms and message history
- **Grid dropdown Row 3** — Social, Chat, Profile, Legend (3×4 grid, 12 buttons)
- **Profile dialog** — avatar initial circle, display name, role badge, user ID, logout button
- **Login/Register dialog** — email+password form with toggle, validation, error display

### New Files
- `app/src/main/java/.../data/repository/AuthRepository.kt`
- `app/src/main/java/.../data/repository/CommentRepository.kt`
- `app/src/main/java/.../data/repository/ChatRepository.kt`
- `app/src/main/res/drawable/ic_social.xml`, `ic_chat.xml`, `ic_profile.xml`, `ic_legend.xml`

### Dependencies
- Proxy: argon2, jsonwebtoken, socket.io
- Android: io.socket:socket.io-client:2.1.0

### Database (DDL — run as sudo -u postgres)
- 6 new tables: users, auth_lookup, refresh_tokens, poi_comments, comment_votes, chat_rooms, room_memberships, messages
- Proxy startup now accepts JWT_SECRET env var for token persistence

### Changed
- `server.js`: refactored from `app.listen()` to `http.createServer()` + Socket.IO attach
- `grid_dropdown_panel.xml`: added gridRow3
- `MenuEventListener.kt`: 3 new callbacks (onSocialRequested, onChatRequested, onProfileRequested)
- `MainViewModel.kt`: auth, comment, and chat LiveData + methods
- `AppModule.kt`: 3 new DI providers (AuthRepository, CommentRepository, ChatRepository)

## [1.5.44] — 2026-03-03

### Added
- **Share POI** — 5th action button in POI detail dialog (teal)
  - Shares name, address, phone, hours + Google Maps link via Android share sheet
  - Button text sizes reduced from 13f to 11f to fit 5 buttons
- **Dark mode** — toolbar moon icon toggles between MAPNIK and CartoDB Dark Matter tiles
  - Persisted via `PREF_DARK_MODE` pref, restored on startup
  - Icon alpha indicates state (0.4 = light, 1.0 = dark)
  - CartoDB Dark Matter: `https://cartodb-basemaps-{a-d}.global.ssl.fastly.net/dark_all/`
- **Favorites** — star/save POIs from detail dialog, access from Find dialog
  - Star icon in POI detail dialog header (filled gold when saved, white outline when not)
  - `FavoritesManager.kt`: SharedPreferences + Gson JSON storage, full CRUD
  - `FavoriteEntry` data class in Models.kt with Haversine distance calculation
  - Gold "Favorites" cell as first item in Find category grid with count badge
  - `showFavoritesResults()`: sorted by distance from map center
- **Text search** — search bar in Find dialog for name-based POI search
  - `EditText` above category grid with 500ms debounce, min 2 chars
  - Clear button (X) returns to category grid
  - Results show color dot + distance + name; tap opens POI detail dialog
  - `searchByName()` in FindRepository, `searchPoisByName()` in MainViewModel
- **Animated radar** — 7-frame NEXRAD loop showing 35 min of storm movement
  - "Animate Radar" + "Animation Speed..." items in Radar menu
  - Iowa State Mesonet timestamped tiles (5-min intervals, client-side timestamp generation)
  - Handler-based animation loop with configurable speed (300-2000ms, default 800ms)
  - Status line shows time range + speed; tap to stop
  - Stops in `onStop()`, `toggleRadar()` for lifecycle safety
  - Restores static radar overlay when animation stops

### Changed
- **Proxy `/db/pois/search`** — now includes `name` and `category` columns in SELECT
- **Toolbar layout** — added dark mode icon between spacer and Alerts icon
- **MenuEventListener** — 3 new callbacks: `onDarkModeToggled()`, `onRadarAnimateToggled()`, `onRadarAnimSpeedChanged()`

### New Files
- `app/src/main/res/drawable/ic_share.xml` — teal share icon
- `app/src/main/res/drawable/ic_dark_mode.xml` — white crescent moon
- `app/src/main/res/drawable/ic_star.xml` — filled gold star
- `app/src/main/res/drawable/ic_star_outline.xml` — white outline star
- `app/src/main/res/drawable/ic_play.xml` — white play triangle
- `app/src/main/res/drawable/ic_pause.xml` — white pause bars
- `app/src/main/java/.../util/FavoritesManager.kt` — favorites CRUD manager

## [1.5.43] — 2026-03-02

### Added
- **Train next-stop ETA** — batch predictions API enriches commuter rail vehicles with arrival minutes
  - Colored ETA badge rendered to the right of train icon at zoom ≥ 18 (e.g., "3m")
  - `enrichWithNextStopEta()` in MbtaRepository: single API call for all active trips
- **17 POI categories** (was 16) — new "Offices & Services" category (Companies, Real Estate, Law, Insurance, Tax)
- **46 new subtypes** across 9 existing categories:
  - Shopping: +15 (Gift, Laundry, Books, Furniture, Jewelry, Florist, Hardware, etc.)
  - Parks & Rec: +5 (Shelters, Fountains, Dog Parks, Tracks, Rec Grounds)
  - Entertainment: +5 (Studios, Dance, Arcades, Ice Rinks, Bowling)
  - Food & Drink: +3 (Pastry, Candy, Marketplaces)
  - Lodging: +3 (Campgrounds, Guest Houses, RV Parks)
  - Transit: +2 (Bike Rentals, Ferry Terminals)
  - Tourism & History: +2 (Ruins, Maritime)
  - Education: +2 (Childcare, Kindergartens)
  - Healthcare: +1 (Nursing Homes), Civic: +1 (Post Boxes)
- **Find dialog radius-scoped counts** — category counts filtered to 10km around map center
  - Proxy `/db/pois/counts` gains `lat`, `lon`, `radius` params with Haversine filter
  - Client-side cache invalidates on 500m move
- **Find dialog auto-fit cells** — cell heights calculated from screen size and row count, no scrolling needed

### Fixed
- **10km probe zoom** — `placeScanningMarker` panOnly calls now explicitly set `panMap=false`
- **GoTo + 10km probe** — `goToLocation()` now calls `stopProbe10km()` to cancel running probe

## [1.5.42] — 2026-03-02

### Added
- **10km Probe Populate** — new Utility menu item for wide-area POI discovery
  - Expanding spiral of 10km probes: center → ring 1 (8 pts) → ring 2 (16 pts) → forever
  - Crosshairs pan to each probe point without changing zoom level
  - Status line shows: ring, probe count, total POIs, new POIs, last probe count, recommended fill radius
  - `estimateFillRadius()` calculates ideal fill radius based on POI density (targets ~200 per search)
  - 30s countdown between probes; tap status line or re-select menu to stop
  - 0.8 overlap factor on grid spacing to prevent diagonal gaps
- **Fill Probe Populate** — stub menu item for future fill implementation

### Changed
- **Utility populate** initial probe now starts at 10km radius (was 3km default), halves down via cap-retry
- **POI display** — zoom guard lowered from 13 to 10; added 5000-marker count guard to prevent OOM at wide zoom
- `placeScanningMarker()` gains `panOnly` mode (pan without zoom change) used by 10km probe

## [1.5.41] — 2026-03-02

### Changed
- **Idle auto-populate** — reworked with several improvements:
  - Threshold increased from 60s to 5 minutes of GPS stationarity
  - Touch-to-stop: any map tap cancels idle populate and resets the 5-min idle timer
  - State preservation: stopped idle scanner resumes from last ring/point instead of restarting
  - No auto-pan: crosshair marker moves silently without hijacking map position
  - State cleared on long-press, GPS move >100m, goToLocation, or manual populate start
- **Long press** now fetches weather + alerts at the new location
- **Idle populate** blocked while utility populate is active (guard already existed, now documented)

## [1.5.40] — 2026-03-02

### Added
- **Slim Toolbar + Status Line + Grid Dropdown** — complete toolbar redesign
  - **Slim toolbar** (40dp): Weather icon (left), spacer, Alerts icon + Grid menu button (right)
  - **Status line** (24dp): persistent priority-based info bar below toolbar
    - Idle: GPS coordinates + speed + weather (e.g., "42.5557, -70.8730 • 61°F")
    - Active: follow info, scan progress, or geofence alerts — highest priority wins
    - 7 priority levels: GPS_IDLE (0) → SILENT_FILL (1) → IDLE_POPULATE (2) → POPULATE (3) → AIRCRAFT_FOLLOW (4) → VEHICLE_FOLLOW (5) → GEOFENCE_ALERT (6)
    - Geofence alerts show zone-type-colored background
    - Tap status line to stop follow/scan (context-sensitive click handler)
  - **Grid dropdown**: PopupWindow with 8 labeled buttons (icon + text) in 2×4 grid
    - Row 1: Transit, Webcams, Aircraft, Radar | Row 2: POI, Utility, Find, Go To
    - Dark semi-transparent background, ripple feedback, auto-dismiss on outside tap
  - **`StatusLineManager.kt`** (new): priority-based status manager with set/clear/updateIdle API
  - **`ic_grid_menu.xml`** (new): Material Design 3×3 grid icon
  - **`grid_dropdown_panel.xml`** (new): PopupWindow layout for grid dropdown

### Changed
- **`toolbar_two_row.xml`** — replaced 2×5 icon grid with slim icon row + status line
- **`activity_main.xml`** — added `fitsSystemWindows="true"` to AppBarLayout
- **`AppBarMenuManager.kt`** — `setupSlimToolbar()` + `showGridDropdown()` replace `setupTwoRowToolbar()`; removed 10 ICON_* constants
- **`MainActivity.kt`** — rewired toolbar init; migrated 6 banner functions to StatusLineManager; eliminated `followBanner` field; GPS + weather observers update idle status; `statusLine` added to `debugState()`

### Removed
- `followBanner` field and all dynamic banner `TextView` creation (6 places)
- 10 `ICON_*` constants from `AppBarMenuManager.companion`

## [1.5.39] — 2026-03-02

### Added
- **Geofence Phase 4 — Database Import & Export**
  - **Import SQLite .db files** via Android SAF file picker — validates schema, copies to geofence_databases directory
  - **Import CSV files** via SAF file picker — config dialog, column aliases, converts to SQLite
  - **Export installed databases** as .db files via Android share intent (FileProvider)
  - **Duplicate detection**: importing existing ID shows overwrite confirmation dialog
  - **Local-only database display**: merges locally-imported databases not in remote catalog; works offline

## [1.5.38] — 2026-03-02

### Added
- **Geofence Phase 3B — 3 additional downloadable databases**
  - **Speed & Red-Light Cameras** (`excam-cameras.db`): 109,500 worldwide cameras from WzSabre/ExCam
  - **US Public Schools** (`nces-schools.db`): 101,390 K-12 schools from NCES EDGE ArcGIS
  - **DJI No-Fly Zones** (`dji-nofly.db`): 7,823 drone restriction zones from DJI NFZDB
- Build scripts: `build-excam.js`, `build-nces.js`, `build-dji-nofly.js`
- Catalog now lists 4 databases totaling 220,657 zones

## [1.5.37] — 2026-03-02

### Added
- **Geofence Phase 3A — Downloadable database infrastructure**
  - `GeofenceDatabaseRepository.kt` — catalog fetch, file download with progress, SQLite loading
  - Database Manager dialog — list databases with download/delete, progress bar
  - `build-military.js` — ArcGIS NTAD military base builder (1,944 polygon zones)
  - Proxy endpoints: `GET /geofences/catalog`, `GET /geofences/database/:id/download`
  - 3 new ZoneTypes: MILITARY_BASE, NO_FLY_ZONE, NATIONAL_PARK

## [1.5.36] — 2026-03-02

### Added
- **Geofence Phase 2 — 4 additional zone types**
  - Speed/Red Light Cameras (Overpass, 200m circle, orange)
  - School Zones (Overpass, polygon or 300m circle, amber, weekday time filter)
  - Flood Zones (FEMA NFHL ArcGIS, blue)
  - Railroad Crossings (Overpass, 100m circle, dark+yellow)
- `GeofenceRepository.kt` — consolidated fetching for 4 non-TFR zone types
- 4 proxy endpoints: `/cameras`, `/schools`, `/flood-zones`, `/crossings`
- Zone-type-aware UI and zoom guards

## [1.5.35] — 2026-03-02

### Added
- **TFR Geofence Alert System** — JTS R-tree spatial engine + FAA TFR detection
  - Entry (CRITICAL), proximity (WARNING, 5nm + bearing), exit (INFO) with cooldowns
  - Semi-transparent red TFR polygon overlays, detail dialog on tap
  - Severity-colored Alerts toolbar icon (gray → blue → yellow → red → pulsing)
  - Proxy `/tfrs?bbox=` — scrapes FAA tfr.faa.gov, parses AIXM shapes
  - Two-row toolbar layout (10 icons in 2×5 grid)
  - GPS integration for user + followed aircraft

## [1.5.34] — 2026-03-02

### Added
- **Weather dialog** — rich graphical display via NWS API composite endpoint
  - Current conditions, 48-hour hourly strip, 7-day outlook, expandable alerts
  - 22 weather vector icons, `WeatherIconHelper.kt`, auto-fetch every 30min
  - Proxy `/weather?lat=&lon=` merges 5 NWS calls with per-section TTLs

### Changed
- Toolbar: Alerts → Weather (direct dialog, no submenu)
- METAR toggle moved to Radar menu

### Removed
- `menu_gps_alerts.xml`, 6 dead callbacks, dead pref constants

## [1.5.33] — 2026-03-02

### Added
- **Idle auto-populate** — 60s GPS stationarity triggers full scanner
- **X-Client-ID header** — per-device UUID on all Overpass requests
- **Delta cache** (proxy) — covering cache check, content hash skip, per-client fair queue

## [1.5.32] — 2026-03-02

### Added
- **Geocode autocomplete** — Photon geocoder via proxy `/geocode`, prefix matching, 500ms debounce
- **Toolbar tooltips** on all action views

## [1.5.31] — 2026-03-01

### Added
- **Icon toolbar** — 8 text buttons → icons; long-press tooltips
- **Go to Location** — geocoder dialog with search, tappable results, map navigation

## [1.5.30] — 2026-03-01

### Changed
- **Smart GPS** — dead zone filtering, speed-adaptive polling, 3km POI threshold
