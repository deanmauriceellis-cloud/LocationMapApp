# STATE.md — content removed 2026-04-21 (S156)

Session-granularity fact blocks that were trimmed from STATE.md when Phase 9Y became the priority track. These are reference-only; the active state lives in STATE.md.

## S144-S150 facts still current (compressed) — as of S155

- **Counsel packet shipped S151** — `docs/counsel-packet/` holds Tier 1 pre-NDA PDF (46 pages, for counsel Monday), Tier 2 post-NDA PDF (133 pages, held pending NDA), Operator prep memo PDF (7 pages). Source markdown + reproducible build scripts also in that dir. Old lawyer packet `docs/lawyer-packet/10-legal-walkthrough.md` + `11-pricing-and-age-gate.md` remain as historical source.
- **V1 Privacy Policy V1-minimal Posture A** at `docs/PRIVACY-POLICY-V1.md` (4 TBDs for Monday counsel). Full OMEN-008 draft at `docs/PRIVACY-POLICY.md` held for future RI Salem activation.
- **Hosting decision:** DestructiveAIGurus.com subpages `/katrinas-mystic-guide/{privacy,support,terms}`. Entity: C-corp (2026-04-20 counsel meeting); interim fallback sole prop Dean Maurice Ellis.
- **#45 Universal Audio Control shipped (S145)** — `audio/AudioControl.kt` + `audio/NarrationHistory.kt` with 4 group toggles (Oracle / Meaningful / Ambient / Businesses) + Detail radio (Brief/Standard/Deep). S150 split `speakTaggedNarration` from `speakTaggedHint` so body segments emit `SegmentType.LONG_NARRATION`. Category→group mapping: MEANINGFUL = HISTORICAL_BUILDINGS+CIVIC+WITCH_SHOP+WORSHIP; AMBIENT = PARKS_REC+EDUCATION; BUSINESSES = FOOD_DRINK+SHOPPING+LODGING+HEALTHCARE+ENTERTAINMENT+AUTO_SERVICES+OFFICES+TOUR_COMPANIES+PSYCHIC+FINANCE+FUEL_CHARGING+TRANSIT+PARKING+EMERGENCY. Unknown → MEANINGFUL.
- **Content-art & UX shipped S144-S146:** 12 Katrina splash variants + library-tea app icon + welcome dialog restructure (Take-a-Tour/Witch Trials/Explore cards) + Find menu rework (4 online tiles hidden, 16 painterly tiles, Salem-voice labels) + V1 toolbar/grid trimming (Home | Grid | About; 2×4 grid rows; Social/Chat/Profile removed) + TTS chunker honoring 40+ abbreviations.
- **Samantha clamp (S144)** — `SalemBounds.kt`; GPS outside bbox snaps to Samantha Statue. Raw still logs to GPS-OBS. S150 `PREF_GPS_BBOX_OVERRIDE_DEFAULT = true` so fresh install from outside Salem bypasses clamp.
- **Cold-start tier-first narration (S144)** + S150 detail-aware `getNarrationForPass` (DEEP→long_narration).
- **23 POIs recategorized S150** (12 HISTORICAL_BUILDINGS + 1 CIVIC + 10 WORSHIP) tagged `|category-fix-s150-2026-04-18`. Museums (Salem Witch Museum et al.) intentionally left ENTERTAINMENT pending broader MEANINGFUL-vs-BUSINESSES group decision.
- **GPS fixes S150:** speed-based stationary-unfreeze (>0.5 m/s bypasses MotionTracker); adaptive polling ladder (10s driving / 2.5s walking-or-narrating / 30s idle). Newspaper table now baked into assets DB via `bundle-witch-trials-newspapers-into-db.js`.
- **Parking-lot #35 = HARD PRE-V1 RELEASE BLOCKER** — all assets encrypted + ProGuard/R8 obfuscation before first Play Store submission.

## S141 facts still current — as of S155

- **V1 offline-mode enforcement shipped** — `FeatureFlags.V1_OFFLINE_ONLY = true` (compile-time const in `:core`). Three-layer enforcement: ViewModel gates (early-return) → OkHttp `OfflineModeInterceptor` (hard backstop in all 13 client sites) → offline-only tile sources (empty URL so osmdroid downloader refuses). V2 resumes by flipping one boolean.
- **Find feature fully offline** — `FindViewModel` rewritten from cache-proxy `FindRepository` to `SalemPoiDao` (Room). Proximity search, text search, category counts, website lookup all served from the bundled 1,837-POI `salem_content.db`. Public API preserved; `SalemMainActivityFind` unchanged. Session-level `hashCode → SalemPoi` cache for `fetchPoiWebsiteDirectly` with name+coord fallback.
- **Walking directions offline** — `WalkingDirections.getRoute()` returns straight-line, `getMultiStopRoute()` returns multi-segment straight-line, `getBundledTourRoute(tourId)` loads the pre-computed OSRM polyline from `assets/tours/{tourId}.json` (already generated for all 5 tours by S125's `backfill-tour-routes.js`). `TourViewModel.getFullTourRoute` prefers the bundled polyline for the active tour. `WalkingRoute.road` is nullable.
- **Log tuning shipped:** GPS-OBS stale-heartbeat backoff (30s → 5m → 15m by stale age; S140 showed 1,208 W-lines over 12h — projected to drop to ~15). `NarrationMgr.intentionallyStopping` flag distinguishes cancel vs real TTS error (spurious `E TTS ERROR` gone from cancel paths). `WALK_SIM_DWELL_MAX_MS` 60 → 180s and waives cap while TTS still actively speaking (Oracle tiles of 2-3 min no longer get cut off).
- **Device-verified** on Lenovo HNY0CY0W: zero outbound-network W-lines at cold boot, `TileSourceManager: buildSource(SATELLITE) v1Offline=true`, tour restored at stop 2/10, 1,837 POIs loaded from Room, no crashes, no ANRs.
- **Phase 9X COMPLETE** (from S140) — 14 actual sessions (S127-S140) vs 8 originally planned.
- **V1 commercial posture locked** (S138): $19.99 flat paid, fully offline, no ads, no LLM, no tiers. IARC Teen (PG-13). S141 brought the code into full compliance with the offline-only half.
- **PG-13 standing content rule** in effect. Rule memory: `feedback_pg13_content_rule.md`.
- **Device-verify preference:** Lenovo TB305FU (HNY0CY0W) over any emulator. Memory: `feedback_lenovo_over_emulator.md`.
- **BCS dedup fully resolved** (from S136). Current inventory tracked in POI Inventory section below.
- **Walk-sim dwell cap** now 180s (was 60s); waived while TTS speaking.
- **Room `@Insert` silent-drop** from S129 still latent.

**Salem 400+ launch deadline 2026-09-01 still tracks.** 4.5 months runway. _(Since superseded by operator's 2026-04-21 direction: quality over schedule.)_
