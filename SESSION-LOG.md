# LocationMapApp — Session Log

> Sessions prior to v1.5.51 archived in `SESSION-LOG-ARCHIVE.md`.

## Session: 2026-04-05 — Session 79: Recovery & Live Conversation Log (OMEN-006)

### Context
Previous session 79 was killed by context overflow during Phase 9A implementation. All in-flight context lost. This session recovered state from git diffs and implemented a crash-recovery mechanism.

### Work Performed

**Recovery**
- Reconstructed Phase 9A state from uncommitted changes on disk
- Confirmed all code survived: SplashActivity, TileSourceManager, cinematic zoom, tile source picker, layouts, themes, Lottie dep
- Build verified: `assembleDebug` passes cleanly

**OMEN-006 — Live Conversation Log (NEW GLOBAL DIRECTIVE)**
- Implemented live append-only conversation log system at `docs/session-logs/`
- Updated CLAUDE.md: Session Start step 4 (create log), Session End step 4 (finalize log), new "Live Conversation Log" section
- Issued OMEN-006 global directive to `~/Development/OMEN/directives/ACTIVE.md` — applies to ALL projects, never expires
- Pushed action items to all 7 project OMEN notes files (Salem, Vampires, Qabalah, TheWitchesOfSalem, KeyPadZoom, GeoInbox, LocationMapApp)
- LocationMapApp marked as reference implementation (COMPLETE)
- Created memory entry: `feedback_conversation_log.md`

### Decisions Made
1. Live conversation logs are mandatory for all projects (OMEN-006)
2. Logs are append-only, timestamped, written to disk incrementally
3. Phase 9A confirmed code-complete — needs emulator test and commit

### Files Created
- `docs/session-logs/session-079-2026-04-05.md` — First live conversation log
- `~/Development/OMEN/directives/ACTIVE.md` — OMEN-006 added

### Files Modified
- `CLAUDE.md` — Live log protocol added to session start/end, architecture, reference table
- `SESSION-LOG.md` — This entry
- `~/Development/OMEN/notes/*.md` — Action items for OMEN-006 added to all 7 projects

### Open Items
- Phase 9A: Emulator test + commit (code complete, build verified)
- COPPA deadline: 17 days remaining (April 22)
- Credential audit (OMEN-002): Outstanding
- Testing (OMEN-004): Outstanding

### OMEN Compliance
- OMEN-006 (Live Conversation Log): **COMPLETE** — reference implementation
- NOTE-L012: Marked complete
- All other notes: Unchanged from Session 78

### Live Log
Full conversation log: `docs/session-logs/session-079-2026-04-05.md`

---

## Session: 2026-04-04 — Session 78: UX Transformation Plan (Phases 9A-9D)

### Context
Post Session 77. User directed a strategic pivot: recenter the app as a commercial Salem tour guide product before continuing with Phase 10 production readiness. Focus on branded launch experience, satellite imagery, feature tier gating, user settings, and contextual alerts.

### Work Performed

**UX Transformation Plan Created**
- Designed and documented 4 new phases (9A-9D) inserted between Phase 9 and Phase 10 in the master plan
- Phase 9A: Splash Screen & Satellite Map Entry (Lottie WitchKitty animation, USGS aerial tiles, cinematic zoom-in, tile source picker)
- Phase 9B: Feature Tier Matrix & Gating Infrastructure (FREE/EXPLORER/PREMIUM/LLM tiers, FeatureGate singleton, stub billing, onboarding dialog)
- Phase 9C: User Settings & Alert Preferences (SettingsActivity with PreferenceFragmentCompat, alert toggles, narration prefs, quiet hours)
- Phase 9D: Contextual Alert System (ContextualAlertManager replacing ambient mode, historical facts, figure connections, business promos, frequency control)
- 20 new files identified across all 4 phases
- Detailed implementation plan saved to `.claude/plans/distributed-coalescing-pearl.md`

**Tile Source Research**
- Evaluated 7 satellite tile providers for commercial use with osmdroid
- Selected USGS National Map Imagery: free, public domain, no API key, unlimited, 15cm resolution over Salem MA
- Fallback identified: Mapbox Satellite (750K free tiles/month)
- Rejected: ESRI (ToS violation for commercial), Bing (deprecated), MapTiler/Stadia (too expensive)

**Master Plan Updated**
- Inserted Phases 9A-9D into Table of Contents and body of WickedSalemWitchCityTour_MASTER_PLAN.md
- Renumbered ToC entries (9A-9D at items 12-15, Phase 10 now item 16)
- Phase numbering uses letter suffixes (9A, 9B, 9C, 9D) to avoid renumbering all subsequent phases

### Decisions Made
1. **Lottie** chosen for splash animation (over AnimatedVectorDrawable) — richer animation for WitchKitty
2. **FREE tier is minimal** — map + basic POIs + 1 tour preview + ads only. Transit, weather, events, full tours all require EXPLORER ($4.99). Strong upgrade incentive.
3. **USGS National Map** for satellite tiles — only option that is free, public domain, and commercially licensed
4. **Phases 9A-9D prioritized before Phase 10** — UX transformation must happen before production readiness work
5. **Phase dependency chain**: 9A and 9B are independent → 9C depends on both → 9D depends on 9B+9C

### Files Modified
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Inserted Phases 9A-9D (ToC + body)
- `STATE.md` — Updated current direction, added Session 78 decisions
- `SESSION-LOG.md` — This entry
- `.claude/plans/distributed-coalescing-pearl.md` — Detailed implementation plan (new)

### Open Items
- WitchKitty Lottie JSON file needs to be created/exported from AI Art Studio assets
- Tours/Events still not loading on fresh install (bundled Room DB — Phase 10 work)
- COPPA deadline April 22 (18 days) — not addressed this session
- Credential audit (OMEN-002) — not addressed this session
- Room migration needed for `min_tier` columns on Tour and TourPoi (Phase 9B)

### OMEN Compliance
- NOTE-L001 (CLAUDE.md): Previously completed
- NOTE-L002/OMEN-002 (Credentials): Not addressed
- NOTE-L003 (COPPA): Not addressed — 18 days remaining
- NOTE-L005 (Phase 10 prereqs): Acknowledged — UX transformation now prioritized before Phase 10
- NOTE-L008 (Pricing tiers): **ADDRESSED** — Feature tier matrix defined with exact feature-to-tier mapping
- NOTE-L009 (Self-contained design): **PARTIALLY ADDRESSED** — FREE tier defined as offline-capable (map + POIs + bundled data)
- OMEN-004 (Testing): Not addressed

---

## Session: 2026-04-04 — Session 77: Emulator Testing, GeoInbox Init, AI Art Studio & Splash Screen Prototyping

### Context
Post Session 76 (master plan overhaul). Session focused on app verification, infrastructure setup, and branding exploration.

### Work Performed

**GeoInbox Infrastructure**
- Initialized git repo at `~/Development/GeoInbox/` (NOTE-G001)
- Created private GitHub repo: `deanmauriceellis-cloud/GeoInbox`
- Initial commit pushed — scaffold, gmail_auth.py, gitignore, env template
- Gmail API already authenticated with `omenpicturefeed@gmail.com`

**Emulator Testing (Salem_Tour_API34)**
- Launched app on emulator, identified proxy IP mismatch (10.0.0.4 → 10.0.0.229)
- Updated proxy IP in 16 Kotlin source files + 2 web config files
- Created `locationmapapp` PostgreSQL database, applied schemas, set password auth
- Verified features: Weather (working), Grid menu (working), Dark mode (working), Find/search (UI working, empty DB), About (working), Long-press GPS override (working), Alerts menu (working), POI markers from Overpass (working)
- Issues found: Tours "No tours available", Events "No events loaded" — bundled Room DB not populated on fresh install; Chat table missing

**AI Art Studio Installation**
- Created install script: `~/Development/scripts/install-ai-art-studio.sh`
- Installed Stable Diffusion WebUI Forge + ComfyUI at `~/AI-Studio/`
- Downloaded models: DreamShaper XL Turbo, DreamShaper 8 (SD1.5), AnimateDiff v3, ControlNet, IP-Adapter, upscalers
- Resolved multiple setup issues: pgAdmin GPG key, libgl1-mesa-glx rename, setuptools/pkg_resources, numpy/scikit-image compat

**Splash Screen Prototyping**
- Generated "WitchKitty" — black cat witch with cauldron, green glow (3 variations)
- Explored AnimateDiff animation — found img2img approach produces minimal motion
- Built anchor-based SLERP morph pipeline for image transitions
- Created American Gothic → Wizard/Witch transformation (10-stage directed narrative)
- Prototyped epic transformation: storm → fear → lightning → demonic → red eyes → transformation → green wisps → pentagram
- Best approach identified: generate independent anchor keyframes, SLERP interpolate in latent space

**Key UI Decision**
- LocationMapApp map view becomes a **utility** (used when maps needed)
- Tour guide view becomes the **primary screen**

### Files Modified
- `core/src/.../repository/*.kt` (12 files) — proxy IP 10.0.0.4 → 10.0.0.229
- `app-salem/src/.../TcpLogStreamer.kt`, `SalemMainActivityFind.kt` — proxy IP update
- `app/src/.../TcpLogStreamer.kt`, `MainActivityFind.kt` — proxy IP update
- `web/.env.development`, `web/vite.config.ts` — proxy IP update
- `STATE.md` — updated with session 77 changes
- `SESSION-LOG.md` — this entry

### Files Created
- `~/Development/scripts/install-ai-art-studio.sh` — AI art studio installer
- `~/AI-Studio/` — full Stable Diffusion installation (Forge + ComfyUI)
- `~/AI-Studio/PROMPTS.md` — tuned prompts for WickedSalemWitchCityTour art
- `~/Development/GeoInbox/.git/` — initialized repository

### Decisions Made
- Tour guide view is the primary screen, not the map
- Local Stable Diffusion (RTX 3090) for all art generation — no subscription costs
- Anchor-based SLERP is the best morph technique (vs chaining, AnimateDiff, or independent frames)
- DreamShaper 8 (SD1.5) works better for animation than SDXL at 512x640 resolution

### Open Items
- Splash screen needs significant refinement
- Tours/Events not loading — bundled Room DB issue (Phase 10 work)
- COPPA deadline April 22 (18 days) — not addressed this session
- Credential audit (OMEN-002) — not addressed this session
- PostgreSQL chat_rooms table missing — non-critical

### OMEN Compliance
- NOTE-G001 (GeoInbox git init): **COMPLETED**
- NOTE-L001 (CLAUDE.md): Previously completed
- NOTE-L002/OMEN-002 (Credentials): Not addressed — proxy IP is not a credential
- NOTE-L003 (COPPA): Not addressed — 18 days remaining
- NOTE-L010 (GeoInbox): Acknowledged, repo initialized, pipeline scaffold exists

---

## Session: 2026-04-04 — Session 76: Full Re-Evaluation & Master Plan Overhaul

### Context
Post Phases 6-9 completion. User requested a total honest re-evaluation of the app from business, marketing, competitive, and feature perspectives. Goal: make the app profitable.

### Work Performed
- **Comprehensive app evaluation**: identified critical gaps (no photos, no tests, no crash reporting, no iOS, TTS quality, pricing complexity, no web presence, no social media)
- **6 parallel research agents** deployed covering:
  1. Offline map tile caching (osmdroid CacheManager, MOBAC, SQLite archives)
  2. Firebase Crashlytics + Analytics (setup, events, GDPR, alternatives)
  3. Salem tourism partnerships (Destination Salem, NPS, Chamber, Salem 400+, local government)
  4. Social media & marketing strategy (Instagram, TikTok, ASO, paid ads, influencer strategy)
  5. Photo sourcing (Wikimedia Commons per-POI audit, NPS/LOC public domain, field photography guide)
  6. iOS/cross-platform (PWA recommended first, then KMP for native iOS)
- **Master plan restructured** from 10+3 phases to 16+4 phases:
  - Phases 10-11: Production readiness + Play Store launch (CODE)
  - Phases 12-14: Social media + fieldwork + community engagement (NO CODE)
  - Phases 15-16: Virality/gamification + iOS/PWA (CODE)
  - Phases 17-20: Merchant network, custom narration, LLM, revenue features (POST-LAUNCH)
- **New sections added**: Competitive Landscape, Social Media Content Calendar, Community Engagement Contacts, Fieldwork Planning Guide
- **Critical discovery**: 2026 is Salem's quadricentennial (Salem 400+) — once-in-a-generation marketing opportunity
- **Key competitors identified**: Action Tour Guide (direct), Salem On Your Own (direct), VoiceMap, GPSmyCity
- **Old Phase 10 + Future Phases archived** to MASTER_PLAN_ARCHIVE_Phase10_FuturePhases.md

### Files Modified
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Major restructure: new ToC, expanded Phase 10, new Phases 11-16, Competitive Landscape, Social Media Calendar, Community Contacts, Fieldwork Guide, renumbered Future Phases 17-20
- `SESSION-LOG.md` — This entry

### Files Created
- `MASTER_PLAN_ARCHIVE_Phase10_FuturePhases.md` — Archive of pre-Session 76 Phase 10 and Future Phases content

### Decisions Made
- Keep Android TTS for v1.0; plan transition to pre-recorded audio in Phase 18
- Keep price tiers as-is ($0/$4.99/$9.99/$49.99-mo); adjust based on market data later
- Social media starts IMMEDIATELY (no code dependency)
- PWA is the fastest path to iOS users (2-3 weeks, leverage existing React web app)
- Firebase Crashlytics + Analytics for production monitoring (free, lowest friction)
- MOBAC for offline tile generation, bundle zoom 12-15 in APK (~60MB)
- Coil 2.7.0 for image loading; WebP format for photos
- Salem Chamber of Commerce membership ($360/yr) is the #1 business development priority
- App must be on Play Store by September 1, 2026 (before October peak)

### Open Items
- Phase 10 work begins next session (emulator verification → Firebase → offline tiles → photos)
- Social media account registration can happen today (no code needed)
- Salem Chamber membership application can happen today
- Field trip to Salem planned for late September 2026

---

## Session: 2026-04-04 — Session 75: Phases 6-9 Complete (Tour Engine, Geofence, Narration, Directions, Events)

### Context
Continuing from Phase 5 completion. Built Phases 6-9 in a single session — all four phases code-complete, all three modules build clean.

### Phase 6 — Tour Engine (Steps 6.1-6.8)

#### Files Created (5)
- `app-salem/.../tour/TourModels.kt` — TourTheme enum, TourProgress, ActiveTour, TourState sealed class, TourSummary
- `app-salem/.../tour/TourEngine.kt` — Full tour lifecycle: start/advance/skip/reorder/add/remove/pause/resume/end, SharedPreferences persistence, custom tour builder, time-budget tours, nearest-neighbor TSP route optimization, haversine distance
- `app-salem/.../ui/TourViewModel.kt` — Bridges TourEngine to UI, exposes tour list/state/actions
- `app-salem/.../ui/SalemMainActivityTour.kt` — Tour selection dialog, active tour dialog, stop detail dialog, route polyline + numbered markers, tour HUD bar, completion dialog, custom tour builder, time-budget dialog
- `app-salem/res/drawable/ic_tour.xml` — Walking tour icon

#### Files Modified (7)
- `core/.../ui/menu/MenuEventListener.kt` — Added onTourRequested()
- `app/.../ui/MainActivity.kt` — No-op onTourRequested() override
- `app-salem/.../ui/SalemMainActivity.kt` — TourViewModel, observeTourState(), GPS location feed
- `app-salem/.../ui/menu/AppBarMenuManager.kt` — Tours button in grid row 4
- `app-salem/.../ui/StatusLineManager.kt` — TOUR priority level (4)
- `app-salem/.../ui/MarkerIconHelper.kt` — createNumberedCircle() for stop markers
- `app-salem/res/layout/grid_dropdown_panel.xml` — Row 4

### Phase 7 — GPS Geofence Triggers & Narration (Steps 7.1-7.6)

#### Files Created (2)
- `app-salem/.../tour/TourGeofenceManager.kt` — Lightweight haversine proximity engine: APPROACH/ENTRY/EXIT events, 60s cooldown, SharedFlow
- `app-salem/.../tour/NarrationManager.kt` — Android TTS wrapper: queue management, speed control (0.75-1.25x), ringer mode check, segment types (short/long/quote/transition/hint)

#### Files Modified (4)
- `tour/TourEngine.kt` — Integrated geofence+narration managers, ambient mode, auto-triggers on GPS
- `ui/TourViewModel.kt` — Exposed geofence events, narration controls, ambient mode toggle
- `ui/SalemMainActivityTour.kt` — Geofence event observer, narration controls bar
- `ui/SalemMainActivity.kt` — Wired geofence+narration observers

### Phase 8 — Walking Directions (Steps 8.1-8.6)

#### Files Created (2)
- `app-salem/.../tour/WalkingDirections.kt` — OSRM routing via OSMBonusPack OSRMRoadManager, route caching, multi-stop support
- `app-salem/.../ui/SalemMainActivityDirections.kt` — Route display (bordered gold polyline), directions info bar, turn-by-turn dialog, walkTo() helper

#### Files Modified (4)
- `tour/TourEngine.kt` — Exposed lastLocation
- `ui/TourViewModel.kt` — Walking directions state, getDirectionsTo/getFullTourRoute/clearDirections
- `ui/SalemMainActivityTour.kt` — "Walk Here" + "Narrate" buttons in stop detail
- `ui/SalemMainActivity.kt` — Wired observeWalkingRoute()

### Phase 9 — Haunted Happenings & Events (Steps 9.1-9.4)

#### Files Created (4)
- `salem-content/.../data/SalemEvents.kt` — 20 curated events (Haunted Happenings, festivals, museums, ghost tours)
- `app-salem/.../ui/EventsViewModel.kt` — Events state, today/upcoming/monthly, "on this day in 1692", October detection
- `app-salem/.../ui/SalemMainActivityEvents.kt` — Events dialog, event cards, category chips, "on this date" section
- `app-salem/res/drawable/ic_events.xml` — Calendar icon

#### Files Modified (6)
- `content/dao/TimelineEventDao.kt` — findByMonthDay()
- `content/dao/EventsCalendarDao.kt` — findByType()
- `content/SalemContentRepository.kt` — getEventsByType(), getAllEvents(), getTimelineByMonthDay()
- `salem-content/pipeline/ContentPipeline.kt` — Wired SalemEvents + SQL writer for events_calendar
- `core/MenuEventListener.kt` — onEventsRequested()
- `app-salem/menu/AppBarMenuManager.kt` — Events button in grid row 4

### Decisions Made
- Tour geofence uses lightweight haversine distance (not polygon-based GeofenceEngine) — tour stops are simple circles
- Overpass POI auto-populate gated to $19.99+ tier (not free/$4.99/$9.99)
- MediaStyle notification for narration deferred to Phase 10
- Halloween map overlays and daily historical notification deferred to Phase 10

### Open Items
- Emulator verification for all four phases
- Re-run content pipeline to regenerate salem_content.db with 20 calendar events
- Phase 10 (Polish, Branding & Play Store) is next

---

## Session: 2026-04-03f — Phase 5 Complete + Tour Data + Business Model + Full Audit

### Context
Session 74. Major session — completed Phase 5 POI gaps, created 3 circular walking tour definitions with 60 stops and TTS narrations, ran end-to-end architecture audit, captured tiered business model and monetization strategy, updated master plan with audit recommendations and future phases.

### Work Performed

**1. Tour Data (SalemTours.kt — NEW)**
Created 3 circular walking tours as loop routes that start/end where the user is:
- **Salem Essentials** (14 stops, ~90 min, 2.2 km, easy) — compact downtown must-sees
- **Salem Explorer** (20 stops, ~2 hr 15 min, 3.3 km, moderate) — extended with dining/shopping areas
- **Grand Salem Tour** (26 stops, ~3.5 hr, 5.2 km, challenging) — Derby St → Proctor's Ledge → back
- Every stop has: transition narration (TTS-optimized), walking time, distance from prev
- All tours minimize backtracking, different directional routes for variety

**2. Phase 5 POI Gaps Completed (8 new POIs, 29→37 total)**
- Witch Trials: Judge Hathorne's Home, Sheriff Corwin's Home
- Maritime: Derby Wharf Light Station, Narbonne House
- Literary: Castle Dismal (Hawthorne boyhood), 14 Mall St (Scarlet Letter house)
- Landmarks: McIntire Historic District
- Visitor Services: South Harbor Garage
- Salem Wax Museum verified closed — removed from plan

**3. Content Pipeline Integration**
- Wired SalemTours into ContentPipeline (replaced emptyList() with tour data)
- Added SQL generation for tours + tour_stops tables in writeSql()
- Enhanced ContentValidator with tour-specific checks (sequential stops, count matching, distance validation)
- Pipeline output: 37 POIs + 23 businesses + 3 tours + 60 stops, 0 errors, 0 warnings

**4. Full Architecture Audit**
Three-agent parallel audit covering app-salem, content pipeline, and core/architecture:
- **Critical**: Missing DB indexes (all entities), fallbackToDestructiveMigration, cleartext traffic, minifyEnabled false, LIKE '%query%' search
- **Major**: No @Delete/@Update in DAOs, no FK constraints, no @Transaction, no app-side sync
- **Architecture**: Solid 9/10. Core is production-ready for tours. Server/API aligned. Schema match perfect.
- **Data quality**: Excellent. Historical accuracy verified. Coordinates verified. Tour routes geographically sound.

**5. Business Model & Monetization (NEW in Master Plan)**
Captured tiered pricing strategy:
- Free ($0): limited tours, Google Ads, no transit/weather
- Explorer ($4.99): moderate tours, North Shore POIs
- Premium ($9.99): full content, transit, all tours
- Salem Village LLM ($49.99/mo): AI conversations with historical figures
Revenue: ads, geofenced merchant advertising, loyalty program, business partnerships

**6. Master Plan Major Update (1,122→1,315 lines)**
New sections added:
- Business Model & Monetization (pricing tiers, revenue streams, feature gating)
- Technical Foundation — Audit Recommendations (7 priorities: indexes, FKs, JSON packages, FTS5, API keys, Socket.IO→OkHttp, network security)
- Future Phases: Phase 11 (Merchant Network), Phase 12 (Salem LLM), Phase 13 (Additional Revenue)

### Files Created (2)
- `salem-content/src/main/kotlin/.../data/SalemTours.kt` — 3 tour definitions + 60 stops
- `/home/witchdoctor/.claude/plans/glowing-gliding-starlight.md` — Phase 5 implementation plan

### Files Modified (5)
- `salem-content/src/main/kotlin/.../data/SalemPois.kt` — 8 new POIs (29→37)
- `salem-content/src/main/kotlin/.../pipeline/ContentPipeline.kt` — Tours wired in, SQL generation for tours/stops
- `salem-content/src/main/kotlin/.../pipeline/ContentValidator.kt` — Tour-specific validation
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Business model, audit recs, future phases, Phase 5 checkboxes
- `STATE.md` — Updated to Session 74

### Key Decisions
1. **JSON content packages** as source of truth, Room DB as cache. Startup checks for updates.
2. **Core must remain backward compatible** — Salem features in :app-salem only, never in :core
3. **FTS5 replaces LIKE search** — no table scans
4. **OkHttp WebSocket replaces Socket.IO** — already in dependency tree, eliminates outdated dep
5. **Tiered feature gating at app level** — core is tier-agnostic
6. **North Shore merchant expansion** — 500+ businesses needed for Explorer tier
7. **Geofenced merchant advertising** — proximity-triggered cards for paying businesses

### Open Items
- [ ] Phase 6 code: TourEngine, TourModels, tour UI (data is ready, engine is not)
- [ ] DB hardening: indexes, FK constraints, explicit migrations, CRUD ops (Tier 1 audit items)
- [ ] JSON content package system implementation
- [ ] FTS5 search implementation
- [ ] App-side /salem/sync call implementation
- [ ] API key security (BuildConfig/secrets-gradle-plugin)
- [ ] Network security config (disable cleartext, enable minify)

---

## Session: 2026-04-03e — Phase 5: POI Catalog + Provenance + Staleness + API

### Context
Session 73. Major architecture session — established offline-first + API sync dual-database architecture, added data provenance and staleness hooks across all entities (local Room + remote PostgreSQL), curated Phase 5 POI catalog, completed Phase 4 loose ends (DB asset loading), and created Salem content API endpoints.

### Key Architectural Decisions
1. **Offline-first**: App ships with bundled Room DB (salem_content.db asset, 1.7MB, 841 records). Works 100% without internet.
2. **Online-enhanced**: When connected, syncs via `/salem/sync` endpoint. Core features (transit, weather, Overpass POIs) require network.
3. **Provenance on everything**: Every entity carries data_source, confidence (0-1), verified_date, created_at, updated_at, stale_after. Built as hooks to evolve later.
4. **Staleness TTL**: Historical content = never stale. Businesses = 180 days. Events = expire at end date.
5. **Backward compatible**: All new endpoints under `/salem/*`. Existing pois table gets optional provenance columns (ALTER ADD, nullable). No breaking changes to LocationMapApp.
6. **Cloud-ready**: Salem API endpoints are stateless, horizontally scalable for future cloud deployment.

### Changes Made

**Room Entities (9 files)** — Added 6 provenance/staleness fields to all entities:
- TourPoi, SalemBusiness, HistoricalFigure, HistoricalFact, TimelineEvent, PrimarySource, Tour, TourStop, EventsCalendar
- Room DB version 1 → 2, fallbackToDestructiveMigration (no production users yet)

**DAOs (9 files)** — Added staleness-aware queries:
- findStale(now), findBySource(source), markUpdated(id, now), setStaleAfter(id, staleAfter)

**SalemContentRepository** — Added provenance methods:
- getStaleTourPois(), getStaleBusinesses(), getStaleEvents()
- getTourPoisBySource(), getBusinessesBySource()
- markTourPoiUpdated(), markBusinessUpdated()

**SalemModule (DI)** — Added createFromAsset("salem_content.db") + fallbackToDestructiveMigration()

**Content Pipeline** — Provenance throughout:
- New `Provenance` data class in PipelineOutput.kt
- All 9 Output* classes carry provenance
- SQL generation includes all provenance columns
- Pipeline now loads curated POIs (SalemPois.kt) and businesses (SalemBusinesses.kt)

**Phase 5 POI Curation** — 29 tour POIs + 23 businesses:
- Witch Trials: 8 sites (Memorial, Museum, Witch House, Proctor's Ledge, Cemetery, Jail, Court, Rebecca Nurse)
- Maritime: 3 sites (Salem Maritime NHP, Custom House, Derby Wharf)
- Museums: 5 sites (PEM, Seven Gables, Pioneer Village, Witch Dungeon, Pirate Museum)
- Literary: 3 sites (Hawthorne Birthplace, statue, hotel)
- Parks: 6 sites (Common, Winter Island, Willows, Conant statue, Ropes Mansion, Chestnut St)
- Visitor: 4 sites (NPS Center, MBTA Station, Ferry Terminal, Museum Place Garage)
- Occult shops: 5 (Crow Haven Corner, HEX, OMEN, Artemisia, Coven's Cottage)
- Restaurants: 7 (Turner's, Sea Level, Finz, Flying Saucer, Rockafellas, Ledger, Opus)
- Bars: 3 (Mercy Tavern, Notch Brewing, Bit Bar)
- Cafes: 3 (Gulu-Gulu, Jaho, Brew Box)
- Lodging: 5 (Hawthorne Hotel, Waterfront, Salem Inn, Coach House, Morning Glory)

**PostgreSQL Schema** — `cache-proxy/salem-schema.sql`:
- 9 Salem content tables mirroring Room entities (all with provenance)
- Provenance columns added to existing `pois` table (backward compatible ALTER ADD)
- `salem_sync_state` table for tracking sync status

**Node.js API** — `cache-proxy/lib/salem.js`:
- GET /salem/pois, /salem/pois/:id (filterable by category, source, stale, nearby, search)
- GET /salem/businesses (filterable by type, source, stale, search)
- GET /salem/figures, /salem/figures/:id (includes related facts/sources)
- GET /salem/timeline, /salem/sources, /salem/tours, /salem/tours/:id, /salem/events
- GET /salem/sync?since= (incremental sync for mobile app)
- GET /salem/stats (counts + stale record counts)

**Database Asset** — `app-salem/src/main/assets/salem_content.db`:
- 1.7MB SQLite, Room identity hash 1ab2eea2c8c64126e88af7a9ce8ba38f
- 841 records: 29 POIs, 23 businesses, 49 figures, 500 facts, 40 events, 200 sources

### Emulator Verification
- Salem_Tour_API34 on port 5570
- APK installs and launches successfully
- Map renders Salem downtown with correct tiles
- Purple toolbar branding (#2D1B4E) renders correctly
- "Filling POIs..." status line shows Room DB loading
- MBTA transit loaded (20 trains, 266 buses, 257 stations)
- No crashes, no Room errors, no FATAL exceptions
- ANR was system resource contention (multiple emulators running), not app bug

### Files Created (5)
- `cache-proxy/salem-schema.sql` — PostgreSQL Salem content schema
- `cache-proxy/lib/salem.js` — Node.js Salem content API
- `salem-content/src/main/kotlin/.../data/SalemPois.kt` — Curated tour POIs
- `salem-content/src/main/kotlin/.../data/SalemBusinesses.kt` — Curated businesses
- `salem-content/create_db.sql` — Room-compatible SQLite schema for asset DB

### Files Modified (23)
- 9 Room entity files (provenance fields)
- 8 DAO files (staleness queries) + TourStopDao unchanged
- `SalemContentRepository.kt` (provenance methods)
- `SalemContentDatabase.kt` (version 2)
- `SalemModule.kt` (createFromAsset + fallbackToDestructiveMigration)
- `PipelineOutput.kt` (Provenance class + fields on all outputs)
- `ContentPipeline.kt` (provenance SQL generation + curated data loading)
- `cache-proxy/server.js` (Salem module registration + startup log)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` (architecture docs + Phase 5 checkboxes)
- `STATE.md`, `SESSION-LOG.md`

### Open Items
- Phase 5 remaining POIs: ~7 uncurated (Hathorne home, Corwin home, Derby Wharf Light, Narbonne/Derby/Scale Houses, Castle Dismal, Mall St, McIntire District, South Harbor Garage)
- Step 5.5 (Overpass overlay merge) deferred to Phase 6+ UI integration
- Phase 6 (Tour Engine) is next
- Emulator performance tuning (avoid running multiple AVDs)

---

## Session: 2026-04-03d — POI Data Provenance Strategy Note

### Context
Session 72. Brief session — user raised strategic concern about POI data quality before Phase 5 begins.

### Key Decision
POIs need data provenance tracking for a paid app. Every POI must know its source (manual_curated, overpass_import, google_places, user_report, salem_project), have a verified_date, and a confidence score. This affects Phase 5 entity schema design — must add source/confidence/verified fields to TourPoi and SalemBusiness entities before populating them.

### Changes Made
- Saved project memory: `project_poi_provenance.md`
- No code changes

### Open Items
- Phase 5 must incorporate provenance fields into Room entities before curating POI data
- Need strategy for staleness detection (businesses closing, hours changing)
- Consider correction/verification workflow

---

## Session: 2026-04-03c — Phases 2-4: Salem App Shell, Content Database, Content Pipeline

### Context
Session 71. Phase 1 emulator test confirmed working. Executed Phases 2, 3, and 4 of the WickedSalemWitchCityTour master plan.

### Changes Made

**Phase 2 — Salem App Shell (`:app-salem`)**
- Created `app-salem/build.gradle` — Android application module, applicationId `com.example.wickedsalemwitchcitytour`
- Updated `settings.gradle` with `:app-salem`
- Created `AndroidManifest.xml` — `WickedSalemApp`, `SalemMainActivity`
- Copied and adapted 31 Kotlin source files from `:app`:
  - `WickedSalemApp.kt` (Application class)
  - `SalemMainActivity.kt` + 12 extension files (map centered on Salem 42.521, -70.887, zoom 15)
  - 7 ViewModels, 3 helpers, menu system, radar scheduler, debug tools
  - Package: `com.example.wickedsalemwitchcitytour` (core imports preserved as `com.example.locationmapapp.*`)
- Added missing core imports (MenuPrefs, MenuEventListener, PoiLayerId, DebugLogger) for cross-package access
- Salem branding: deep purple (#2D1B4E), antique gold (#C9A84C), Theme.WickedSalem, placeholder "W" icon
- Copied all resources (67 drawables, 4 layouts, 8 menus)

**Phase 3 — Salem Content Database**
- Created 9 Room entity classes under `app-salem/.../content/model/`:
  TourPoi, SalemBusiness, HistoricalFigure, HistoricalFact, TimelineEvent, PrimarySource, Tour, TourStop, EventsCalendar
- Created 9 DAO interfaces under `content/dao/` with proximity queries, search, joins
- Created `SalemContentDatabase.kt` (Room, version 1, 9 entities)
- Created `SalemModule.kt` (Hilt DI — database singleton + 9 DAO providers)
- Created `SalemContentRepository.kt` — unified repository with bulk insert methods

**Phase 4 — Content Pipeline (`:salem-content`)**
- Created JVM-only module `salem-content/` with Gson + application plugin
- Created 13 Gson model classes matching ~/Development/Salem JSON schemas
- Created 6 readers: Building (424), NPC (Tier 1+2: 49), Fact (3,672 filtered), Event (40), PrimarySource (top 200), Coordinate (29)
- Created `CoordinateMapper.kt` — grid→GPS (7.9m/unit at lat 42.56°)
- Created `NarrationGenerator.kt` — TTS-optimized short/long narration from historical data
- Created `ContentPipeline.kt` — orchestrator with SQL output
- Created `ContentValidator.kt` — data integrity validation
- Pipeline runs successfully: 1.5MB SQL, 789 INSERT statements

### Files Modified
- `settings.gradle` — added `:app-salem`, `:salem-content`
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — checked off Phase 1.7, Phase 2, Phase 3, Phase 4
- `STATE.md` — updated current direction and architecture

### Files Created
- `app-salem/` — 31 Kotlin source files, manifest, build.gradle, all resources (100+ files total)
- `app-salem/.../content/` — 21 Kotlin files (9 entities, 9 DAOs, database, DI module, repository)
- `salem-content/` — 12 Kotlin files (models, readers, mapper, narration, pipeline, validator, main)
- `salem-content/salem_content.sql` — 1.5MB generated SQL

### Build Status
- `:app` — builds clean
- `:app-salem` — builds clean (9.6MB APK)
- `:salem-content` — compiles and runs successfully

### Decisions Made
- Used `.gradle` (Groovy) not `.gradle.kts` for app-salem to match existing convention
- Kept same package structure convention with explicit core imports rather than shared packages
- Pipeline outputs SQL rather than pre-built .db — Room DB generation deferred to Phase 5 when POI GPS data is curated
- Capped facts at 500 (from 3,672 available) — can increase later
- Tour POIs, businesses, tours, events calendar left empty — Phase 5/6/9 will curate with real GPS coordinates

### Open Items
- Phase 2: Splash screen layout not yet created
- Phase 2: Emulator testing of app-salem not yet done
- Phase 4: Pre-built `salem_content.db` for assets deferred to Phase 5
- Next: Phase 5 — Enhanced Salem POI Catalog (curated GPS locations for tour stops, businesses)

---

## Session: 2026-04-03b — Phase 1: Core Module Extraction

### Context
Continuation of WickedSalemWitchCityTour work. Previous session (crashed due to OS reboot) completed all Phase 1 code changes but never committed. This session recovered and verified the work, then performed proper session end.

### Changes Made

#### Phase 1: Core Module Extraction (Steps 1.1–1.7)
- Created `core/` Android library module (`core/build.gradle`, `core/src/main/AndroidManifest.xml`)
- Updated `settings.gradle` to include `:core`, `app/build.gradle` to depend on `:core`
- Moved 22 files from `:app` to `:core`:
  - **Models**: `Models.kt`, `AppException.kt`
  - **12 Repositories**: Places, Mbta, Aircraft, Weather, Webcam, Tfr, Find, Geofence, GeofenceDatabase, Auth, Chat, Comment
  - **Location**: `LocationManager.kt`
  - **Geofencing**: `GeofenceEngine.kt`
  - **Utilities**: `DebugLogger.kt`, `FavoritesManager.kt`
  - **DI**: `AppModule.kt` → `CoreModule.kt` (renamed)
  - **Menu**: `MenuPrefs.kt`, `MenuEventListener.kt`
- Updated all import statements in remaining `:app` files
- `PoiLayerId.kt` not a standalone file (N/A — defined within PoiCategories.kt)

#### Build Verification
- `./gradlew :core:assembleDebug` — BUILD SUCCESSFUL (26 tasks)
- `./gradlew :app:assembleDebug` — BUILD SUCCESSFUL (62 tasks)
- Emulator testing deferred to next session

#### Other Changes (from crashed session)
- `CURRENT_TESTING.md` — updated test status
- `toolbar_two_row.xml` — layout updates
- `MarkerIconHelper.kt` — additions
- `AppBarMenuManager.kt` — import updates
- Cache proxy & web app — minor config/port adjustments
- Shell scripts — updated for current environment

### Files Created (1 new module)
- `core/build.gradle` — Android library module config
- `core/src/main/AndroidManifest.xml` — minimal library manifest
- `core/src/main/java/.../` — 22 Kotlin files moved from `:app`
- `app/src/main/res/drawable/badge_red.xml`, `badge_teal.xml` — badge drawables
- `bin/` — helper scripts

### Files Modified (17)
- `settings.gradle`, `app/build.gradle` — multi-module setup
- `MainActivity.kt`, `MainActivityAircraft.kt`, `MainActivityFind.kt`, `MainActivityGeofences.kt`, `MainActivityMetar.kt`, `MainActivityTransit.kt`, `MainActivityWeather.kt` — import updates
- `MainViewModel.kt`, `TransitViewModel.kt` — import updates
- `AppBarMenuManager.kt`, `MarkerIconHelper.kt` — import updates + additions
- `DebugHttpServer.kt`, `TcpLogStreamer.kt` — minor updates
- `toolbar_two_row.xml` — layout changes
- Cache proxy + web + shell scripts — config adjustments

### Files Deleted (from `:app`, moved to `:core`)
- 22 files: Models.kt, AppException.kt, LocationManager.kt, 12 repositories, GeofenceEngine.kt, DebugLogger.kt, FavoritesManager.kt, AppModule.kt, MenuPrefs.kt, MenuEventListener.kt

### Decisions Made
- `PoiLayerId` is not a standalone file — master plan step marked N/A
- Phase 1 complete pending emulator verification in next session

### Next Steps
- Emulator test to verify all features still work identically (Step 1.7 final checkbox)
- Begin Phase 2: Salem App Shell (`:app-salem` module)

---

## Session: 2026-04-03a — WickedSalemWitchCityTour Master Plan

### Context
Planning session for a new app built on the LocationMapApp platform. The user wants to create a GPS-guided tourist app for Salem, MA that leverages all existing LocationMapApp features (maps, transit, weather, geofencing, POIs, social) plus adds tour-specific features (TTS narration, walking tours, historical content from the Salem Witch Trials project).

### Decisions Made

1. **Architecture**: Multi-module monorepo — `:core` (shared library), `:app` (generic LocationMapApp), `:app-salem` (WickedSalemWitchCityTour)
2. **App name**: WickedSalemWitchCityTour, package `com.example.wickedsalemwitchcitytour`
3. **Price**: $9.99 one-time purchase, no ads, no IAP
4. **Map SDK**: Stay with osmdroid (free, offline tile caching)
5. **Narration**: Android TTS first, upgrade later
6. **Walking directions**: OSRM (free, no API key)
7. **Danvers sites**: Included in tours, flagged as requiring transportation
8. **Content source**: ~/Development/Salem project JSON (2,174 NPCs, 3,891 facts, 4,950 primary sources, 424 buildings)
9. **Session protocol**: Established start/end protocol with WickedSalemWitchCityTour_MASTER_PLAN.md as primary plan document

### Research Performed
- Full LocationMapApp architecture analysis (52 Kotlin files, 12 repositories, all dependencies mapped)
- Full Salem project content inventory (29,800 data files, 1.1M lines of JSON, scholarly sources cataloged)
- Salem tourist destination research (25+ attractions, Heritage Trail route, MBTA access, seasonal events, GPS coordinates)

### Files Created
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Comprehensive 10-phase plan with tour definitions, POI catalog, content pipeline, database schema

### Files Modified
- `STATE.md` — Added current direction section, planned multi-module architecture
- `SESSION-LOG.md` — This session entry

### Next Steps
- Begin Phase 1: Core Module Extraction (create `:core` module, move shared code)

---

## Session: 2026-03-20a — Performance Optimization + Proxy Quick-Drain

### Context
Session startup: recycled servers, fixed missing POIs (DATABASE_URL not set), configured emulator for testing, then addressed ANR/performance issues and POI import latency.

### Issues Fixed

#### 1. POIs not appearing — DATABASE_URL missing
- Cache proxy started without `DATABASE_URL`, so `/db/*` endpoints returned 503
- POIs are PostgreSQL-backed; without the env var, bbox queries return empty
- Fix: always start proxy with `DATABASE_URL=postgres://witchdoctor:fuckers123@localhost/locationmapapp`
- Helper script: `bin/restart-proxy.sh` already includes this

#### 2. Emulator configuration (Pixel_8a_API_34)
- AVD config at `~/.android/avd/Pixel_8a_API_34.avd/config.ini` updated:
  - `hw.initialOrientation=landscape` — app renders landscape
  - `showDeviceFrame=no` — removes phone bezel, fixes touch coordinate mapping
  - `skin.name=1080x2400` / `skin.path=1080x2400` — no pixel_8a skin
  - `hw.ramSize=8192` — prevents OOM with all layers active
- Immersive mode via system setting: `adb shell settings put global policy_control "immersive.full=com.example.locationmapapp"`
- Resize emulator by dragging corner — do NOT use `wmctrl` (crashes emulator or breaks touch coords)

#### 3. ANR / Performance — marker rendering on main thread
- **POI cluster rendering** (`renderPoiClusters`): moved icon generation to `Dispatchers.Default`, marker creation stays on main thread
- **POI marker rendering** (`replaceAllPoiMarkers`): same pattern — icons generated off-thread
- **Station markers** (`addStationMarkersBatched`): new batched method with off-thread icon generation (257 stations at once)
- **Webcam markers** (`addWebcamMarkersBatched`): new batched method with shared icon instance
- **TransitViewModel**: all 5 fetch methods now use `withContext(Dispatchers.IO)` to prevent DNS timeout blocking main thread
- **Staggered MBTA startup**: `onStart()` MBTA restores wrapped in coroutine with 2s initial delay + 500ms gaps between layers
- Result: frame skips reduced from 239 to ~35 per burst (emulator-only residual)

#### 4. POI import latency — quick-drain
- Problem: Overpass search finds POIs → buffered for import → but import only runs every 15 minutes → bbox returns 0
- Fix: added quick-drain in `server.js` — wraps `bufferOverpassElements` to trigger `runPoiDbImport` 2 seconds after new elements are buffered
- POIs now appear on map within ~4 seconds of a long-press search instead of waiting up to 15 minutes
- `lib/import.js` now exports `runImport()` for the quick-drain hook

#### 5. "Emulator is not responding" dialog
- This is **Ubuntu GNOME's** `check-alive-timeout` detecting the emulator process is sluggish — not an Android ANR
- No app-level ANR in logcat — the emulator hardware (Quadro K1100M / Haswell) is the bottleneck
- GNOME timeout set to 20s (`gsettings set org.gnome.mutter check-alive-timeout 20000`)
- Will not occur on real devices

### Files Modified

#### Android App
- `MainActivity.kt` — batched marker rendering (POI clusters, POI markers, webcams), staggered MBTA startup
- `MainActivityTransit.kt` — added `addStationMarkersBatched()` with off-thread icon generation
- `TransitViewModel.kt` — all fetch methods use `withContext(Dispatchers.IO)`

#### Cache Proxy
- `server.js` — quick-drain wrapper on `bufferOverpassElements` triggers import 2s after new elements arrive
- `lib/import.js` — exports `runImport()` function for quick-drain hook

#### AVD Config
- `~/.android/avd/Pixel_8a_API_34.avd/config.ini` — landscape, no frame, 8GB RAM, generic skin

---

## Session: 2026-03-05f (v1.5.68 — Web App Phase 6: Favorites + URL Routing)

### Context
Phase 6 of web app: add localStorage favorites (matching Android app pattern) and shareable URLs via `window.history` + URL search params. Client-only features — no proxy or server changes needed.

### Changes Made

#### New Files (2)
- `web/src/hooks/useFavorites.ts` (~35 lines) — localStorage key `lma_favorites`, JSON array of `FavoriteEntry`, dedup by `(osm_type, osm_id)` with String coercion, newest-first prepend, API: `{ favorites, isFavorite, toggleFavorite, count }`
- `web/src/hooks/useUrlState.ts` (~60 lines) — `parseUrlState()` reads `?lat=&lon=&z=&poi=` on load with validation (lat ±90, lon ±180, z 1-19, poi format `node|way|relation/digits`); `useUrlState()` hook returns `updateMapPosition` (500ms debounced replaceState), `setPoiParam`, `clearPoiParam`

#### Modified Files (5)
- `web/src/lib/types.ts` — added `FavoriteEntry` interface (osm_type, osm_id, name, lat, lon, category, addedAt)
- `web/src/components/Find/PoiDetailPanel.tsx` — added `isFavorite`/`onToggleFavorite` props, amber star button in header between title and close button, share clipboard fallback copies `window.location.href` instead of text
- `web/src/components/Find/FindPanel.tsx` — added `favoriteCount`/`favoriteResults`/`onShowFavorites` props, gold amber Favorites cell before category grid, useEffect to switch to results view when favoriteResults populated
- `web/src/components/Map/MapView.tsx` — added optional `zoom` prop, passed to `MapContainer zoom={zoom ?? 14}`
- `web/src/App.tsx` — imported useFavorites + useUrlState + parseUrlState, URL-based initial mapCenter/zoom, on-mount POI deep linking (fetches via `find.fetchPoiDetail`), `handleToggleFavorite` builds FavoriteEntry from selectedResult, `handleShowFavorites` converts favorites to FindResult[] with haversine distances, URL updates in handleBoundsChange/handleSelectResult/handlePoiClick/handleCloseDetail/handleAircraftClick/handleVehicleClick/handleStopClick/handleToggleFind

### Verification
- `npx tsc --noEmit` — 0 errors
- `npm run build` — clean, 506KB / 149KB gzip (144 modules)

### Testing Needed
- [ ] Open POI detail → tap star → verify amber fill → close → open Find → Favorites cell shows count → tap → favorites list → tap favorite → detail opens with star filled
- [ ] Pan map → URL updates with `?lat=&lon=&z=` → copy URL → open new tab → map loads at same position
- [ ] Open POI detail → URL has `?poi=way/123` → copy → new tab → POI detail opens automatically
- [ ] POI detail → Share button → clipboard contains full URL with poi param

---

## Session: 2026-03-05e (v1.5.67 — Web App: Long-Press + Home Location)

### Context
Two UX improvements: (1) toolbar hover tooltips (already present via title attributes), (2) long-press on map to relocate + persistent home location from Profile dropdown.

### Changes Made

#### Modified Files (5)
- `web/src/hooks/useGeolocation.ts` — rewritten: Promise-based `locate()`, persistent home location via localStorage (`homeLocation` key), `setHome(lat, lon)`, `clearHome()`, `hasHome` flag, skips browser GPS on mount when home is set
- `web/src/components/Map/MapView.tsx` — added `LongPressHandler` component (700ms timer, cancel on drag/zoom/mouseup, context menu suppressed), new `onLongPress` and `hasHome` props
- `web/src/components/Map/MapControls.tsx` — added `hasHome` prop, teal dot indicator on locate button when home is set, dynamic tooltip
- `web/src/components/Social/ProfileDropdown.tsx` — added "Set Current Location as Home" button (with house icon) + "Reset to Browser GPS" button (appears when home is set), works regardless of auth state
- `web/src/App.tsx` — wired `handleLongPress` (fly to point, auto-zoom 18), `handleSetHome` (saves map center), fixed `handleLocate` to use Promise-based locate

### Build
- TypeScript: clean, no errors
- Production: 501KB / 147KB gzip

---

## Session: 2026-03-05d (v1.5.66 — Web App Phase 5: Auth + Social)

### Context
Phase 5 of web app: add social layer matching the Android app's auth, comments, and chat features. All proxy endpoints already exist (auth.js, comments.js, chat.js) — no proxy changes needed. `socket.io-client` already in package.json.

### Changes Made

#### New Files (9)
- `web/src/hooks/useAuth.ts` (83 lines) — auth state, register, login, logout, validates stored token via `/auth/me` on mount
- `web/src/hooks/useComments.ts` (63 lines) — POI comment CRUD, falls back to unauthenticated GET if no token
- `web/src/hooks/useChat.ts` (106 lines) — Socket.IO connection with JWT auth, room list (REST), real-time messaging, typing indicator
- `web/src/lib/timeFormat.ts` (10 lines) — relative time formatter ("just now" / "5m ago" / "2h ago" / "3d ago" / date)
- `web/src/components/Social/AuthDialog.tsx` (117 lines) — modal overlay with register/login toggle, client-side validation (displayName 2-50, password 8+, email format)
- `web/src/components/Social/ProfileDropdown.tsx` (60 lines) — anchored to toolbar profile button, avatar initial + name + role + sign out, click-outside-to-close
- `web/src/components/Social/CommentsSection.tsx` (136 lines) — comment list with author/time/stars/votes/delete, add comment form with star selector + char counter (1000 limit)
- `web/src/components/Social/StarRating.tsx` (28 lines) — filled/empty stars, clickable in interactive mode
- `web/src/components/Social/ChatPanel.tsx` (214 lines) — two views (room list / chat room), room create inline form, message bubbles (own=right/teal, others=left/gray), typing indicator, send bar

#### Modified Files (5)
- `web/src/lib/types.ts` — +6 social types: AuthUser, AuthResponse, PoiComment, CommentsResponse, ChatRoom, ChatMessage
- `web/src/config/api.ts` — +`authFetch<T>()` with Bearer header, proactive token refresh (2-min buffer), singleton refresh de-duplication, 401 auto-retry; +localStorage helpers (getStoredTokens/storeTokens/storeUser/getStoredUser/clearAuth)
- `web/src/components/Layout/Toolbar.tsx` — +Chat button (speech bubble SVG) + Profile button (user circle SVG / initial letter when logged in), +3 new props (chatOpen, profileOpen, userInitial)
- `web/src/components/Find/PoiDetailPanel.tsx` — +CommentsSection embedded in overflow-y-auto area below action buttons, +10 new comment/auth props
- `web/src/App.tsx` — +useAuth/useComments/useChat hooks, +authDialogOpen/profileOpen/chatOpen state, +Chat/Find/Weather mutual exclusion, +comments load on POI open, +auth dialog auto-close on login, +chat connect/disconnect on panel open/close

### Verification
- `npx tsc --noEmit` — 0 errors
- `npx vite build` — clean, 499KB / 147KB gzip (142 modules)
- No proxy changes required — all social endpoints already exist

### Testing Needed
- [ ] Register new account → verify tokens in localStorage → refresh page → auto-login via `/auth/me`
- [ ] Profile dropdown → user info → sign out → icon reverts to generic circle
- [ ] Open POI → "Comments (0)" → add comment with star rating → verify appears → vote up/down → delete
- [ ] Chat panel → see Global room → enter → send message → verify real-time (second tab)
- [ ] Token refresh: wait for expiry or set short JWT_ACCESS_EXPIRY → verify authFetch auto-refreshes
- [ ] Panel mutual exclusion: Chat ↔ Find ↔ Weather close each other
- [ ] Dark mode: all new panels render correctly

---

## Session: 2026-03-05c (Web App External Access)

### Context
Testing web app from an external system — POIs were failing. Diagnosed that the cache proxy wasn't running. Also enabled Vite dev server to listen on all interfaces for LAN access.

### Changes Made
- `web/vite.config.ts` — added `host: '0.0.0.0'` to Vite server config (allows access from other machines on the network)
- `.gitignore` — added `web/tsconfig.tsbuildinfo` (build artifact)

### Notes
- Web app requires the cache proxy to be running (`node server.js` on port 3000) — all POI/weather/aircraft/transit data comes from the proxy
- Web app currently only shows POIs that exist in PostgreSQL (previously scanned areas); no Overpass trigger mechanism yet
- Future: consider adding auto-fetch for unseen areas (Phase 5+ scope)

---

## Session: 2026-03-05b (v1.5.65 — Web App Phase 4: Aircraft + Transit)

### Context
Phase 4 of web app: add aircraft tracking (OpenSky) and MBTA transit (trains/subway/buses) as real-time map layers with detail panels, follow mode, and arrival/departure boards. Also added server-side POI clustering to handle high-density viewports. Continued from v1.5.64 (initial implementation) with extensive bug fixes and enhancements.

### Changes Made

#### New Files (12)
- `web/src/config/aircraft.ts` — altitude color mapping (ground→gray, <5kft→green, 5-20kft→blue, >20kft→purple), unit converters (m/s→mph, m→ft, heading→compass), `aircraftIconHtml()` DivIcon SVG factory
- `web/src/config/transit.ts` — MBTA route colors (Red/Orange/Blue/Green/CR/Bus), `getRouteColor()`, `routeTypeLabel()`, `vehicleStatusLabel()`
- `web/src/hooks/useAircraft.ts` — aircraft state management, 15s auto-refresh, `parseStateVector()` for OpenSky arrays, select/follow/history
- `web/src/hooks/useTransit.ts` — trains/subway/buses/stations/busStops state, per-type refresh timers (rail 15s, bus 30s), vehicle follow, trip predictions, fetchPredictionsById for bus stops
- `web/src/components/Map/AircraftMarkerLayer.tsx` — Leaflet DivIcon markers with rotated airplane SVG, altitude-colored, callsign labels
- `web/src/components/Map/FlightTrailLayer.tsx` — Polyline segments from flight path history, altitude-colored
- `web/src/components/Map/TransitMarkerLayer.tsx` — route-colored CircleMarkers for vehicles (originally DivIcon, replaced after rendering issues), station dots, bus stop dots, selected vehicle highlighting with teal ring
- `web/src/components/Aircraft/AircraftDetailPanel.tsx` — slide-in panel: altitude-colored header, info rows, follow/map buttons, sighting history
- `web/src/components/Transit/VehicleDetailPanel.tsx` — route-colored header, status, numbered next 5 stops with arrival times, follow button
- `web/src/components/Transit/ArrivalBoardPanel.tsx` — dark-themed board with DEP/ARR labels, both-times display for through-stations, "service ended" message
- `web/src/components/Layout/LayersDropdown.tsx` — dropdown with 4 toggle switches + count badges, click-outside-to-close

#### Modified Files (8 web + 2 proxy)
- `web/src/lib/types.ts` — 7 new interfaces: AircraftState, FlightPathPoint, AircraftSighting, AircraftHistory, MbtaVehicle (with tripId), MbtaStop, MbtaPrediction (with stopName/stopSequence)
- `web/src/hooks/usePois.ts` — added clusters state, PoiCluster type for server-side aggregation
- `web/src/App.tsx` — aircraft/transit hooks, layers dropdown, detailView mutual exclusion (5 states), follow effects, bus stops bbox fetch
- `web/src/components/Map/MapView.tsx` — all new layer components + props
- `web/src/components/Map/PoiMarkerLayer.tsx` — cluster rendering (translucent circles, count labels, non-interactive)
- `web/src/components/Layout/Toolbar.tsx` — Layers button with stacked-layers icon + active count badge
- `web/src/components/Layout/StatusBar.tsx` — per-layer vehicle counts
- `web/src/index.css` — aircraft-label, transit-label, cluster-label CSS
- `cache-proxy/lib/pois.js` — `/pois/bbox` server-side clustering (COUNT + SQL grid aggregation when >1000 POIs)
- `cache-proxy/lib/mbta.js` — 5 new endpoints: vehicles, stations, predictions (with stop_name resolution), trip-predictions, bus-stops/bbox

### Bug Fixes During Implementation
1. **DivIcon transit markers invisible**: `Marker` with custom HTML at 14-20px didn't render → replaced with `CircleMarker` (same proven approach as POIs/METARs)
2. **MBTA stations returning 0**: `location_type=1` filter incorrect (MBTA uses 0) → removed filter
3. **28k POIs page unresponsive**: client tried to render 28k CircleMarkers → server-side SQL grid aggregation returns ~77 clusters
4. **Selected vehicle label not showing**: react-leaflet permanent Tooltip doesn't mount on dynamic condition change → force remount via key including selection state
5. **Bus stop predictions empty**: `stop_name` resolver only searched rail stations cache → added `fetchPredictionsById` for direct stop ID queries
6. **Predictions showing null-time entries first**: sorted by arrival_time but nulls come first → filter nulls + sort by earliest available time
7. **North Station showing only one platform**: single stop ID queried → `stop_name` parameter resolves all child platforms (33 predictions across Orange/Green/CR)
8. **Bus stops not appearing on initial toggle**: fetchBusStops not called until map move → added useEffect on busesVisible + ref-based callback in handleBoundsChange

### Verification
- Vite dev server compiles cleanly
- Aircraft markers appear when layer enabled (altitude-colored, rotated)
- Click aircraft → detail panel with info + follow button
- Trains/subway appear as route-colored circles
- Click vehicle → detail with next 5 stops and times
- Click station → arrival/departure board with DEP/ARR labels
- Bus stops appear at zoom >= 15 (max 200 per viewport)
- Vehicle follow mode tracks position across refreshes
- POI clusters render as translucent circles with count labels when zoomed out
- Detail panel mutual exclusion works across all 4 panel types

---

## Session: 2026-03-05 (v1.5.63 — Web App Phase 3: Weather Overlay)

### Context
Phase 3 of web app: add weather visualization — weather panel with current/hourly/daily forecasts, METAR aviation markers, radar overlay with animation, and alert notifications. All proxy endpoints already exist.

### Changes Made

#### New Files (5)
- `web/src/hooks/useWeather.ts` — weather/METAR fetch with AbortController, radar/metar toggles, 5-min auto-refresh timer
- `web/src/config/weatherIcons.ts` — NWS icon code → inline SVG React elements (~25 codes, day/night variants using basic SVG shapes)
- `web/src/components/Weather/WeatherPanel.tsx` — 360px slide-in panel: header (city/state/station), expandable alert banners (red/orange by severity), Current/Hourly/Daily tab bar, layer controls (Radar/Animate/METAR toggles)
- `web/src/components/Map/RadarLayer.tsx` — RainViewer API radar tiles, static (latest frame at 35% opacity) + animated (7-frame loop at 800ms via Leaflet `L.tileLayer` + `setOpacity()`)
- `web/src/components/Map/MetarMarkerLayer.tsx` — flight-category colored CircleMarkers (VFR=#2E7D32, MVFR=#1565C0, IFR=#C62828, LIFR=#AD1457), monospace labels at zoom >= 10

#### Modified Files (6)
- `web/src/lib/types.ts` — added WeatherLocation, WeatherCurrent, WeatherHourly, WeatherDaily, WeatherAlert, WeatherData, MetarStation types
- `web/src/App.tsx` — weatherOpen state, mutual exclusion with Find, METAR bounds-based fetch, alert click handler, stable callback refs via individual function deps
- `web/src/components/Layout/Toolbar.tsx` — weather button (dynamic SVG icon from weatherIcons + red dot alert indicator)
- `web/src/components/Layout/StatusBar.tsx` — red alert banner (event name + count, click opens weather panel)
- `web/src/components/Map/MapView.tsx` — RadarLayer + MetarMarkerLayer integration with new props
- `web/src/index.css` — `.metar-label` styles (monospace, dark mode variant)

### Bug Fixes During Implementation
- Iowa State Mesonet animated tile URLs return 404 (format `nexrad-n0q-{timestamp}` doesn't work for national mosaic) — switched to RainViewer API which provides a JSON frame manifest
- react-leaflet `<TileLayer>` doesn't reactively update `opacity` prop — switched to direct Leaflet API (`L.tileLayer` + `setOpacity()`) for radar animation
- `handleBoundsChange` infinite re-render loop: `wx` (entire hook return object) as dependency recreated callback every render → `BoundsWatcher` useEffect re-fired → POIs never settled. Fixed by using stable individual function refs (`wx.fetchMetars`) + ref for `metarsVisible`

### Verification
- `npm run build` — clean, 0 TypeScript errors, 404KB / 121KB gzip
- Weather panel opens with current conditions, hourly, daily tabs
- Radar toggle shows RainViewer tiles at 35% opacity
- Animate toggle cycles 7 frames smoothly
- METAR markers show colored circles at airports
- Alert banner in status bar when NWS alerts active
- Find ↔ Weather mutual exclusion works
- POIs display normally (no infinite re-render)

---

## Session: 2026-03-04j (v1.5.62 — Web App Phase 2: Find + Search + POI Detail)

### Context
Phase 2 of web app: add Find dialog, fuzzy search, and POI detail panel. All proxy API endpoints already working with CORS.

### Changes Made

#### New Files (5)
- `web/src/lib/distance.ts` — haversine distance calculation + imperial formatting (ft/mi)
- `web/src/hooks/useFind.ts` — API hook: search (1s debounce, AbortController), findByCategory, loadCounts, fetchWebsite, fetchPoiDetail; aggregates tag-string counts into category-level counts
- `web/src/components/Find/FindPanel.tsx` — slide-in panel (360px, 85vw mobile): search bar, 4-col category grid with count badges, subtype drill-down, results list, "Filter and Map" button, back navigation state machine
- `web/src/components/Find/ResultsList.tsx` — shared result row component: formatted distance + category color dot + bold name + detail line + category label
- `web/src/components/Find/PoiDetailPanel.tsx` — POI detail: category color bar header, info rows (distance/type/cuisine/address/phone/hours), async website resolution ("Find Website" button), action buttons (Directions/Call/Map/Share)

#### Modified Files (7)
- `web/src/lib/types.ts` — added FindResult, WebsiteInfo, PoiDetailResponse; widened id types to `string | number`
- `web/src/config/categories.ts` — added `resolveCategory()`, `getCategoryByTag()`, `getCategoryTags()`, `getSubtypeTags()`
- `web/src/components/Layout/Toolbar.tsx` — added Find (magnifying glass) button with teal active highlight
- `web/src/components/Layout/StatusBar.tsx` — filter mode: teal bar "Showing N results for X — click to clear"
- `web/src/components/Map/PoiMarkerLayer.tsx` — click handlers on markers, filter mode (forced labels, filtered markers only)
- `web/src/components/Map/MapView.tsx` — forwards filterResults + onPoiClick to PoiMarkerLayer
- `web/src/App.tsx` — full orchestration: Find/Detail mutual exclusion, filter mode, marker click → detail, fitBounds on Filter and Map

### Bug Fixes During Implementation
- Proxy returns `elements` not `results`, `category_hint` not `hint` — fixed response field mapping in useFind
- Proxy returns tag-string categories (`"amenity=cafe"`) not category IDs (`"FOOD_DRINK"`) — added `resolveCategory()` + count aggregation
- Proxy returns string IDs — widened TypeScript types to `string | number`

### Verification
- `npm run build` — clean, 0 TypeScript errors, 385KB / 117KB gzip
- Search "restaurant" returns results with distances and categories
- Category grid shows counts, subtypes drill down works
- POI marker click opens detail panel

---

## Session: 2026-03-04i (v1.5.61 — Web App Phase 1)

### Context
Build a cross-platform web frontend to consume the existing proxy API (54+ endpoints). Zero backend rewrite — just a new React frontend at `web/` alongside `app/` and `cache-proxy/`.

### Changes Made

#### Proxy: CORS Middleware (2 lines + 1 dep)
- Added `cors ^2.8.5` to `cache-proxy/package.json`
- Added `const cors = require('cors'); app.use(cors({ origin: true, credentials: true }))` to `server.js`
- Verified: `Access-Control-Allow-Origin` header present on all responses

#### Web App: Foundation + Map + POI Markers + Dark Mode
- **20 files** created in `web/` directory
- **Tech stack**: React 19, TypeScript (strict), Vite 6, react-leaflet 5, Tailwind CSS 3, PostCSS
- **Map**: react-leaflet MapContainer with OpenStreetMap light tiles + CartoDB Dark Matter dark tiles
- **POI markers**: colored CircleMarkers using `classifyPoi()` — all 17 categories with exact Android hex colors
- **Labels**: zoom >= 16 shows `tags.name` as permanent Leaflet Tooltips (same threshold as Android)
- **Dark mode**: toggle in toolbar, persisted to localStorage, switches tile layer + UI theme
- **Geolocation**: browser Geolocation API, falls back to Boston (42.36, -71.06)
- **Data loading**: debounced 300ms `/pois/bbox` fetch on viewport change, `/pois/stats` for total count
- **Layout**: 12px toolbar (app name + dark mode) + full-height map + 8px status bar (coords + counts)
- **Controls**: zoom +/- buttons + locate button (bottom-right, z-index 1000)
- **Build**: TypeScript clean (0 errors), Vite production build passes (368KB JS / 112KB gzipped)

### Files Created (20)
```
web/package.json, tsconfig.json, vite.config.ts, tailwind.config.ts, postcss.config.js
web/index.html, .env.development, public/favicon.svg, public/manifest.json
web/src/main.tsx, App.tsx, index.css, vite-env.d.ts
web/src/config/api.ts, categories.ts
web/src/lib/types.ts
web/src/hooks/useGeolocation.ts, usePois.ts, useDarkMode.ts
web/src/components/Map/MapView.tsx, PoiMarkerLayer.tsx, MapControls.tsx
web/src/components/Layout/Toolbar.tsx, StatusBar.tsx
```

### Files Modified (2)
- `cache-proxy/server.js` — CORS middleware
- `cache-proxy/package.json` — cors dependency

### Verification
- `npx tsc --noEmit` — 0 errors
- `npx vite build` — clean, no warnings
- `npm run dev` → http://localhost:5173 — Vite dev server starts in 346ms
- `curl -I -H "Origin: http://localhost:5173" http://10.0.0.4:3000/pois/stats` — CORS headers present

---

## Session: 2026-03-04h (v1.5.60 — Proxy Heap Reduction)

### Context
Cache proxy consuming 643MB heap — dominated by two in-memory Maps: `poiCache` (268k entries, ~90MB) fully redundant with PostgreSQL, and `cache` (7,214 entries, ~320MB) mostly stale 365-day Overpass responses.

### Changes Made

#### Phase 1: Eliminate poiCache → PostgreSQL (saves ~90MB heap + 90MB disk)
- **cache.js**: Removed `poiCache` Map, `loadPoiCache()`, `savePoiCache()`, `cacheIndividualPois()`. Added lightweight `importBuffer` array + `bufferOverpassElements()` + `drainImportBuffer()`
- **overpass.js**: Wired `bufferOverpassElements` (replaces `cacheIndividualPois`), `await` on async `collectPoisInRadius`, updated stats fields (`buffered` replaces `added+updated`)
- **scan-cells.js**: `collectPoisInRadius()` now queries PostgreSQL (`SELECT ... FROM pois WHERE lat BETWEEN ... AND lon BETWEEN ...`) instead of iterating poiCache
- **pois.js**: All 4 endpoints rewritten as async PostgreSQL queries — `/pois/stats` (COUNT), `/pois/export` (with limit param), `/pois/bbox` (bbox SELECT), `/poi/:type/:id` (single lookup)
- **import.js**: `runPoiDbImport()` calls `drainImportBuffer()` to get pending elements, dedupes by `type:id`, batch upserts. Removed `lastDbImportTime` delta tracking. Status endpoint shows `pendingDelta: importBuffer.length`
- **admin.js**: Removed `poiCache` references; `/cache/stats` shows `importBufferPending`; `/cache/clear` clears buffer instead of poiCache

#### Phase 2: LRU Cap on Main Cache (saves ~250MB+ heap)
- **cache.js**: Added `MAX_CACHE_ENTRIES=2000` (env-configurable via `MAX_CACHE_ENTRIES`), `evictOldest()` sorts by timestamp and deletes oldest; called in `cacheSet()` and after `loadCache()`
- **admin.js**: Shows `maxCacheEntries` in `/cache/stats`
- **server.js**: Updated deps wiring — removed poiCache/cacheIndividualPois/POI_CACHE_FILE, added importBuffer/bufferOverpassElements/drainImportBuffer/MAX_CACHE_ENTRIES

### Results
| Metric | Before | After |
|--------|--------|-------|
| Heap | 643 MB | 208 MB |
| Cache entries | 7,214 | 2,000 (LRU cap) |
| cache-data.json | 320 MB | 57 MB |
| poi-cache.json | 90 MB | eliminated |
| poiCache Map | 268k entries | eliminated |
| POI source | in-memory Map | PostgreSQL (268k rows) |

### Verification (all passed)
- `/cache/stats` → memoryMB: 208.2, entries: 2000, maxCacheEntries: 2000, importBufferPending: 0
- `/pois/stats` → count: 268,291 (from PostgreSQL)
- `/pois/bbox?s=42.3&w=-71.1&n=42.4&e=-71.0` → 5,819 POIs
- `/poi/way/497190524` → Orient Heights Main Parking (from PostgreSQL)
- `/db/import/status` → pendingDelta: 0, enabled: true
- Server startup clean: all modules loaded, no errors

### Files Modified (7)
- `cache-proxy/lib/cache.js` — removed poiCache + added import buffer + LRU eviction
- `cache-proxy/lib/overpass.js` — wired buffer, await async collectPoisInRadius
- `cache-proxy/lib/scan-cells.js` — PostgreSQL collectPoisInRadius
- `cache-proxy/lib/pois.js` — all 4 POI endpoints → async PostgreSQL queries
- `cache-proxy/lib/import.js` — drain buffer + dedupe instead of poiCache delta
- `cache-proxy/lib/admin.js` — importBuffer + MAX_CACHE_ENTRIES stats
- `cache-proxy/server.js` — updated deps wiring

---

## Session: 2026-03-04g (v1.5.59 — Scan Cell Coverage + Queue Cancel)

### Context
App made redundant Overpass API calls in areas already well-mapped (211k+ POIs). Overpass cache keys use exact 3dp lat/lon + radius + tags, so moving ~100m creates a cache miss. Also, stopping follow/populate left already-queued proxy requests executing wastefully.

### Changes Made

#### 1. Scan Cell Coverage (`cache-proxy/lib/scan-cells.js` — NEW, ~160 lines)
- Divides world into ~1.1km grid cells (2dp lat:lon), tracks `lastScanned` timestamp + `poiCount`
- `checkCoverage()` returns FRESH/STALE/EMPTY; `markScanned()` marks all cells in a circle
- `collectPoisInRadius()` bbox-scans poiCache to serve POIs without upstream call
- Persists to `scan-cells.json` (debounced write, same pattern as radius-hints.json)
- `GET /scan-cells` debug endpoint (all cells or specific lat/lon query)

#### 2. Overpass Integration (`cache-proxy/lib/overpass.js`)
- Coverage check inserted after cache-only merge, before queue: if FRESH, returns from poiCache with `X-Cache: CELL`
- `X-Coverage` header added to all response paths (HIT, covering-cache, scan-cell, MISS)
- `markScanned()` called after successful upstream with lat/lon/radius/poiCount
- `POST /overpass/cancel` — flushes queued requests for a client ID, resolves with 499

#### 3. Server Wiring (`cache-proxy/server.js`)
- scan-cells loaded before overpass (exports checkCoverage, markScanned, collectPoisInRadius into deps)
- `GET /scan-cells` route registered; startup log updated

#### 4. Admin (`cache-proxy/lib/admin.js`)
- `scanCells` count in `/cache/stats`; scan cells cleared + file deleted in `/cache/clear`

#### 5. App: Coverage-Aware Behavior
- `PopulateSearchResult` — new `coverageStatus: String` field
- `PlacesRepository.kt` — reads `X-Coverage` header, treats `X-Cache: CELL` as cache hit
- `MainViewModel.kt` — new `cancelPendingOverpass()` method (fire-and-forget via IO dispatcher)
- `MainActivityPopulate.kt`:
  - Silent fill: 1.5s "Coverage fresh" banner for FRESH (vs 3s)
  - Idle populate spiral: FRESH cells skipped entirely (`continue` — no search, no countdown, no subdivision)
  - Manual populate spiral: same skip behavior
  - `stopIdlePopulate()`, `stopPopulatePois()`, `stopProbe10km()` all call `cancelPendingOverpass()`

### Live Testing Results
- 185 scan cells marked after first populate spiral run
- `[Scan Cells] Marked 63 cells (500 POIs, ~8/cell)` — correct cell marking
- Persistence working: `Saved 185 scan cells to disk`
- `/scan-cells` endpoint returns correct cell data with age/config
- `/cache/stats` shows `scanCells: 185`

### Files Created (1)
- `cache-proxy/lib/scan-cells.js` — scan cell coverage tracking module

### Files Modified (7)
- `cache-proxy/lib/overpass.js` — coverage check, mark scanned, X-Coverage header, cancel endpoint
- `cache-proxy/server.js` — wire scan-cells module
- `cache-proxy/lib/admin.js` — stats + clear
- `app/.../data/model/Models.kt` — coverageStatus field on PopulateSearchResult
- `app/.../data/repository/PlacesRepository.kt` — read X-Coverage, cancelPendingOverpass()
- `app/.../ui/MainViewModel.kt` — cancelPendingOverpass(), Dispatchers import
- `app/.../ui/MainActivityPopulate.kt` — FRESH skip, cancel on stop, shorter banners

---

## Session: 2026-03-04f (Overpass Retry + Zoom 16 Labels + Tap-to-Stop)

### Context
Overpass API returning intermittent HTML error pages (~13% failure rate during 10km Probe). Also UX improvements: lower zoom threshold for labels, single-tap to cancel follow/populate.

### Changes
- **Proxy retry** (`cache-proxy/lib/overpass.js`): 3 endpoint rotation (overpass-api.de, lz4, z), `detectOverpassError()` helper, 4 total attempts with 15s/30s/60s backoff
- **App retry** (`PlacesRepository.kt`): `isHtmlErrorResponse()` + `executeOverpassWithRetry()` wrapper — 3 attempts, 5s/10s delays, coroutine-cancellable; used by both `searchPois()` and `searchPoisForPopulate()`
- **Zoom 16 labels**: POI full names + train/subway/bus detail labels (route, speed, destination, status) now visible from zoom 16+ (was 18+) — changed in `addTrainMarker`, `addSubwayMarker`, `addBusMarker`, `addPoiMarker`, `refreshPoiMarkerIcons`, `refreshVehicleMarkerIcons`, and scroll handler threshold checks
- **Single tap stop**: `singleTapConfirmedHelper` now stops following (vehicle/aircraft) + all population tasks (populate, 10km probe, idle populate, silent fill)

### Files Modified (4)
- `cache-proxy/lib/overpass.js` — retry loop with endpoint rotation + error detection
- `app/.../data/repository/PlacesRepository.kt` — retry wrapper + HTML detection + constants
- `app/.../ui/MainActivityTransit.kt` — zoom threshold 18→16 for train/subway/bus markers
- `app/.../ui/MainActivity.kt` — zoom threshold 18→16 for POI labels + scroll handler + single tap handler

---

## Session: 2026-03-04e (3-Phase Code Decomposition — server.js + ViewModels + MenuPrefs)

### Context
Monolithic files had grown to unsustainable sizes: `server.js` (3,925 lines), `MainViewModel.kt` (958 lines), `AppBarMenuManager.kt` (879 lines). Pure structural refactoring — zero behavior changes, all endpoints/menus/UI work identically after.

### Phase 1: server.js → 156-line bootstrap + 18 modules in lib/
- Created `cache-proxy/lib/` directory with 18 route/utility modules
- Each module exports `function(app, deps)` receiving shared state
- Key modules: overpass.js (300 lines), db-pois.js (680 lines), auth.js (340 lines), tfr.js (330 lines), chat.js (240 lines), weather.js (220 lines)
- auth.js registered first (exports requireAuth used by comments.js + chat.js)
- Proxy restarted and verified between batches

### Phase 2: MainViewModel.kt → 215 lines + 6 domain ViewModels
- **SocialViewModel** (200 lines): auth, comments, chat — AuthRepository, CommentRepository, ChatRepository
- **TransitViewModel** (165 lines): MBTA trains, subway, buses, stations, bus stops — MbtaRepository
- **AircraftViewModel** (79 lines): aircraft tracking, flight history — AircraftRepository
- **FindViewModel** (86 lines): Find counts, nearby, search, POI website — FindRepository
- **WeatherViewModel** (108 lines): weather, METAR, webcams, radar refresh — WeatherRepository, WebcamRepository
- **GeofenceViewModel** (286 lines): TFR, cameras, schools, flood, crossings, databases, GeofenceEngine — TfrRepository, GeofenceRepository, GeofenceDatabaseRepository
- MainViewModel retained: Location + POI viewport (215 lines, 2 dependencies)
- Each extraction: update ViewModel refs in consumer files + MainActivity observers + DebugEndpoints constructor
- 6 successful incremental builds

### Phase 3: AppBarMenuManager.kt → 812 lines + MenuPrefs.kt (74 lines)
- Removed unused MainViewModel constructor parameter
- Extracted 35 preference key constants to MenuPrefs.kt object
- Updated 65 references across 7 consuming files
- Removed unused ContextCompat import, unused AppBarMenuManager import in MainActivityFind.kt

### Files Created (26)
- `cache-proxy/lib/*.js` — 18 route modules
- `app/.../ui/SocialViewModel.kt` — auth, comments, chat
- `app/.../ui/TransitViewModel.kt` — MBTA transit
- `app/.../ui/AircraftViewModel.kt` — aircraft tracking
- `app/.../ui/FindViewModel.kt` — Find dialog queries
- `app/.../ui/WeatherViewModel.kt` — weather, METAR, webcams
- `app/.../ui/GeofenceViewModel.kt` — geofence system
- `app/.../ui/menu/MenuPrefs.kt` — preference key constants
- `REFACTORING-REPORT.txt` — detailed refactoring report

### Files Modified (15+)
- `cache-proxy/server.js` — 3,925 → 156 lines (bootstrap only)
- `app/.../ui/MainViewModel.kt` — 958 → 215 lines (Location + POI only)
- `app/.../ui/menu/AppBarMenuManager.kt` — 879 → 812 lines (companion removed)
- `app/.../ui/MainActivity.kt` — 7 ViewModel properties, updated observers + DebugEndpoints
- `app/.../ui/MainActivityTransit.kt` — viewModel → transitViewModel
- `app/.../ui/MainActivityAircraft.kt` — viewModel → aircraftViewModel
- `app/.../ui/MainActivityFind.kt` — viewModel → findViewModel
- `app/.../ui/MainActivityWeather.kt` — viewModel → weatherViewModel
- `app/.../ui/MainActivityGeofences.kt` — viewModel → geofenceViewModel
- `app/.../ui/MainActivityDebug.kt` — multiple ViewModel refs
- `app/.../ui/MainActivityRadar.kt` — weatherViewModel + MenuPrefs
- `app/.../ui/MainActivityPopulate.kt` — MenuPrefs
- `app/.../ui/MainActivitySocial.kt` — socialViewModel
- `app/.../util/DebugEndpoints.kt` — 6 ViewModel constructor params

### Commits (3)
- `6e8fa58` Decompose server.js (3,925 → 156 lines) into 18 modules in lib/
- `6762cd5` Decompose MainViewModel.kt (958 → 215 lines) into 6 domain-specific ViewModels
- `494c112` Refactor AppBarMenuManager.kt: extract MenuPrefs.kt, remove unused viewModel param

---

## Session: 2026-03-04d (COMMERCIALIZATION.md v2.0 — Lawyer-Ready Enhancement)

### Context
Rewrote COMMERCIALIZATION.md from a 15-section technical reference (1,019 lines) into a 27-section, 3-part lawyer-ready document (1,897 lines). Goal: hand the document to an attorney who has never seen the app and have them understand the product, business model, legal risks, and give actionable advice.

### Changes Made

#### 1. COMMERCIALIZATION.md — Complete Restructure (1,019 → 1,897 lines)

**Part A — What the Lawyer Needs to Know (4 new/enhanced sections)**
- §1 Product Description: plain-English app overview, features, tech stack, audience, dev status
- §2 Revenue Model & Freemium Design: free+ads / paid tier ($2.99–$4.99/mo), Google's cut, ad revenue estimates, legal implications
- §3 Data Flow Description: what's collected, ASCII data flow diagram, what users see about each other, what third parties receive, encryption status
- §4 Executive Summary: enhanced with Likelihood column and reading guide

**Part B — Legal Analysis (6 new sections + expanded privacy)**
- §5 Finding the Right Attorney: type needed, where to find (MA Bar, SCORE, meetups), what to bring, budget, red flags, timing
- §9.7–9.10 International Privacy: GDPR, UK GDPR, 5 other jurisdictions, phased expansion roadmap
- §11 Dependency Inventory: 22 Android + 12 Node.js libraries with license + risk (flagged: osmbonuspack LGPL, JTS EPL-2.0, duck-duck-scrape ToS risk)
- §12 Ad Network Compliance: AdMob requirements, UMP consent, COPPA+ads, ad content filtering, revenue estimates
- §13 In-App Purchase & Google Play Billing: Billing Library, 15%/30% commission, post-Epic v. Google, subscription legal requirements
- §14 Tax Considerations: sales tax (Google collects), income tax, S-Corp election, quarterly estimates, deductible expenses, CPA timing
- §15 Social Media Integration: current status (none), future options, social login legal requirements
- §17 Competitor Analysis: 8 comparable apps (Google Maps, Yelp, Flightradar24, Waze, Transit, AllTrails, RadarScope, Aloft) with legal approach

**Part C — Implementation & Execution (3 new sections + expanded checklist)**
- §24 Cost Summary: updated with Google Play dev ($25), CPA ($200–500), revenue projections vs costs, breakeven analysis (~1,500–2,500 MAU)
- §25 Risk Matrix: 14 risks scored by Probability × Impact (1–5 scale), priority actions ranked by score
- §26 Specific Questions for Attorney: 17 questions in 3 tiers (Must/Should/Can defer), serves as meeting agenda
- §27 Master Checklist: expanded from 8 → 10 phases, added Monetization Setup + Future Growth phases, ~70 items

All 15 original sections preserved (renumbered, Cloud Deployment moved to Part C as §23).

### Files Modified (4)
- `COMMERCIALIZATION.md` — complete rewrite (1,019 → 1,897 lines)
- `STATE.md` — updated Commercialization section + Next Steps
- `SESSION-LOG.md` — added this session entry
- `memory/MEMORY.md` — updated commercialization references

---

## Session: 2026-03-04c (v1.5.57 — POI Coverage Expansion + Cuisine Search)

### Context
Analyzed 211K POIs — found 1,324 boat ramps, 165 massage shops, 132 tattoo shops, 131 cannabis dispensaries, and 12,800+ cuisine-tagged restaurants not reachable through Find grid or search. Also missing airports, barber shops, skateparks from Overpass scans.

### Changes Made

#### 1. 15 New Subtypes in PoiCategories.kt (138 → 153)
- Food & Drink: +Wine Shops, +Butcher Shops, +Seafood Markets
- Transit: +Airports (`aeroway=aerodrome`), +Taxi Stands
- Parks & Rec: +Boat Ramps (`leisure=slipway`), +Skateparks
- Shopping: +Barber Shops, +Massage, +Tattoo Shops, +Thrift Stores, +Vape Shops, +Cannabis
- Entertainment: +Disc Golf

#### 2. Cuisine-Aware Fuzzy Search (server.js)
- Added CUISINE_KEYWORDS set (30+ entries) + CUISINE_ALIASES for OSM spelling variants
- Search query now adds `OR tags->>'cuisine' ILIKE '%keyword%'` when cuisine keyword detected
- "pizza" finds 1,483 pizza places, "burger" finds 1,693, "mexican" finds 1,103, etc.

#### 3. ~30 New Search Keywords (server.js)
- Shopping: tattoo, barber, thrift, second hand, vape, cannabis, dispensary, massage, spa
- Food: butcher, seafood, wine shop, bbq, burger, steak, ramen, noodle, taco, donut, bagel, chicken, wings, sandwich, japanese, korean, vietnamese, greek, french, mediterranean
- Transit: airport, taxi
- Parks: boat ramp, boat launch, skatepark, skateboard
- Entertainment: disc golf, frisbee golf

#### 4. Expanded Overpass Search (PlacesRepository.kt)
- Default keys: added `craft`, `aeroway`, `healthcare`
- Category extraction: handles new keys in both parseOverpassJson instances
- POI_CATEGORY_KEYS in server.js: added `aeroway`, `healthcare`

### Files Modified (3)
- `app/.../ui/menu/PoiCategories.kt` — 15 new subtypes, new tags
- `cache-proxy/server.js` — cuisine search, 30+ keywords, CATEGORY_LABEL_TAGS, POI_CATEGORY_KEYS
- `app/.../data/repository/PlacesRepository.kt` — 3 new Overpass keys, category extraction

### Testing Needed
- [ ] Proxy restart + verify cuisine search ("pizza", "bbq", "sushi" near Boston)
- [ ] App reinstall + verify new subtypes appear in Find grid
- [ ] Verify "Boat Ramps" shows 1,324 results
- [ ] Verify "Filter and Map" works with new subtypes
- [ ] Trigger a scan to verify airports/barbers/skateparks get imported

---

## Session: 2026-03-04b (Commercialization Roadmap)

### Context
User requested a comprehensive "pathway to making this an application I can sell" — covering cloud deployment, legal structure, IP protection, user content liability, privacy compliance, content moderation, and Google Play requirements.

### Changes Made

#### 1. COMMERCIALIZATION.md (new document, ~1,019 lines)
- Researched cloud hosting (Railway, DigitalOcean, Neon, Cloudflare R2), pricing at 100/1K/10K user scales
- Legal structure: Wyoming LLC recommended ($100 formation, $60/yr)
- Insurance: Tech E&O + Cyber Liability ($1,300–$3,000/yr) — non-optional given safety data
- IP protection: copyright ($130), provisional patents ($60 each micro-entity), trademark ($350), trade secrets ($0), R8 obfuscation ($0)
- User content liability: Section 230 protections, DMCA safe harbor ($6 agent registration), Take It Down Act (May 2026 deadline)
- Privacy: CCPA/CPRA, 21+ state laws treating GPS as sensitive PI, COPPA age gate (13+), data retention schedule
- Third-party APIs: **OpenSky requires commercial license (BLOCKING)**, OSM ODbL share-alike on POI database, MBTA license review needed
- Content moderation: OpenAI Moderation API (free) + Perspective API (free), tiered auto-block/queue/approve system
- Safety data disclaimers for TFR, geofences, weather, flood zones
- Google Play: Data Safety section, prominent location disclosure, R8 release builds
- Master checklist: 8 phases, ~50 action items
- Year 1 estimated cost: $4,578–$10,955

### Files Created (1)
- `COMMERCIALIZATION.md` — full commercialization roadmap

### Files Modified (3)
- `STATE.md` — added Commercialization section + updated Next Steps
- `SESSION-LOG.md` — added this session entry
- `memory/MEMORY.md` — added commercialization reference

---

## Session: 2026-03-04 (v1.5.56 — Search Distance Sort + Filter and Map UX)

### Context
User noticed fuzzy search results were sorted by relevance (fuzzy match score) instead of distance, and the "Filter and Map" button was buried at the bottom of 200+ results. Also, tapping "Filter and Map" teleported to results centroid instead of keeping current position.

### Changes Made

#### 1. Fuzzy Search Distance Sort (server.js)
- Changed `ORDER BY (score) DESC, distance ASC` to `ORDER BY distance ASC`
- All search results now sorted nearest-first regardless of fuzzy match quality

#### 2. "Filter and Map" Button Moved to Top (MainActivity.kt)
- Moved teal button from bottom of `searchResultsList` to top (before result rows)
- Removed duplicate button from bottom of results

#### 3. Filter and Map — Keep Current Position + Adaptive Zoom (MainActivity.kt)
- Removed centroid calculation and `setCenter()` call
- Starts at zoom 18, steps back one level until at least one result is visible (min zoom 3)

### Files Modified (2)
- `cache-proxy/server.js` — ORDER BY change (1 line)
- `app/.../ui/MainActivity.kt` — button move, centroid removal, adaptive zoom (~30 lines)

---

## Session: 2026-03-03g (v1.5.55 — Module IDs + Home + About)

### Changes Made
- MODULE_ID constants in 131 source files
- Home toolbar icon: GPS center at zoom 18
- About toolbar icon: version/copyright/contact dialog

---

## Session: 2026-03-03f (v1.5.53 — Filter and Map Mode)

### Changes Made
- Teal "Filter and Map" button in Find results → exclusive map view
- enterFilterAndMapMode/exitFilterAndMapMode with scroll/zoom guards
- FIND_FILTER status line priority, radar save/restore, auto-exit on Find reopen

---

## Session: 2026-03-03e (v1.5.52 — Fuzzy Search Testing & Fixes)

### Changes Made
- Fixed gridScroll/searchScroll layout weight bugs (search results invisible)
- Header hint bar moved to title, search limit 50→200, distance expansion tuning

---

## Session: 2026-03-03d (v1.5.51 — Smart Fuzzy Search)

### Changes Made
- pg_trgm extension + GIN trigram index on pois.name
- ~80 keyword→category mappings, composite scoring, distance expansion
- SearchResponse model, rewritten /db/pois/search endpoint
- Rich 3-line result rows, 1000ms debounce, keyword hint chips
