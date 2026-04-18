# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-18 — Session 148 (#44 splash voiceover: screech prototype rejected as "too jolting", friendly girly-kitty meow direction locked via `feedback_splash_voiceover_vibe.md`; AudioGen→sox pipeline built end-to-end in `/tmp/splash-voiceover-candidates/` with reusable `mix.sh` recipe; friendly-meow regen blocked on `sudo systemctl stop ollama` — Ollama grabbed 20 GB VRAM mid-session, 8 MiB free; no code changes to `app-salem/`)

---

## TOP PRIORITY — Next Session (S149)

**Operator-directed starting points:**

0. **PRE-MONDAY HARD PRIORITY — Webex demo: emulator + working TTS routed into the call.** The 2026-04-20 lawyer meeting is a **Webex call**, not in-person. Operator will screen-share from their Linux workstation, which means the demo runs on an **emulator** (not the Lenovo), AND the demo must include TTS narration playing audibly THROUGH THE WEBEX CALL (so the lawyer hears it). Webex-on-Linux specifics: "Share Computer Audio" toggle must be on, or a PulseAudio `module-loopback` must route the emulator's audio sink into the Webex mic input. **Operator said "webex we will resolve before Monday" at S147 start — audio-routing is operator-side, not Claude's.** Target AVD: `Salem_Tour_API34` on port 5570 per `feedback_salem_emulator.md`. **GPU is now FREE (released at S147 close, 0% util, 441 MiB baseline)** — no longer blocked. Tasks: (a) boot the AVD cold; (b) `ANDROID_SERIAL=emulator-5570 ./gradlew :app-salem:installDebug`; (c) verify TTS engine + US English voice data on the AVD (install Google TTS if default system image lacks it); (d) dry-run the demo flow (splash voiceover → welcome dialog → Heritage Trail start → hear a POI narrate → hear a newspaper cut off by a POI ENTRY to demonstrate S146 priority fix → see the triptych thumbnail on the hero banner to demonstrate S147 wiring). Full detail in S147 live log.

1. **#44 Splash voiceover (pivoted S148 from screech → friendly meow)** — last remaining Monday Must-Have (12 of 13 shipped across S144/145/146). **Creative direction is now locked:** long friendly girly-kitty meow, NOT a screech or jumpscare (operator rejected 4 AudioGen screech mixes at S148 15:09 as "too jolting"; full context in `feedback_splash_voiceover_vibe.md`). Replaces `app-salem/src/main/res/raw/splash_voiceover.wav` (stereo 44.1 kHz PCM 16-bit, ~8 sec target — format the `mix.sh` pipeline already produces correctly). **BLOCKING step to unblock S149:** `sudo systemctl stop ollama` (Ollama woke up mid-S148 and grabbed 20 GB VRAM; only 8 MiB free; AudioGen needs ~4 GB). Once Ollama is stopped, re-run the friendly-meow prompt set (4 candidates prepared in S148: "long friendly cat meow, sweet high-pitched kitty greeting" / "cute girly kitten meow, soft affectionate long mrow" / "content housecat meow, warm friendly female cat call" / "sweet little cat meow hello, soft high-pitched friendly feline greeting") via `~/AI-Studio/audiocraft-generate.sh`, then feed into the existing `/tmp/splash-voiceover-candidates/mix.sh` recipe (atmos bed optional — may conflict with friendly tone; worth testing cat-only first). Reminder to restart Ollama afterward: `sudo systemctl start ollama`. Must pass PG-13 / IARC Teen.

2. **Device-verify S146 + S147 fixes** on the Lenovo (HNY0CY0W — returns 12+ h after S147 end; S147 APK not yet installed). Rebuild first with `./gradlew :app-salem:assembleDebug` to pick up the synced `assets/heroes/`. Then walk-test:
   - **S146:** (a) two adjacent POIs → second queues (not interrupt); (b) newspaper cuts off for POI ENTRY (priority-race fix); (c) hero banner above map with title + thumb + gold speaker icon; (d) Salem Heritage Trail featured first with gold border + "★ FEATURED" badge.
   - **S147:** (e) hero banner thumbnail is now the per-POI triptych panel 1 (Katrina cat-POV), not the generic Katrina avatar; (f) `PoiDetailSheet` hero strip is now the full 800×200 triptych for every POI (via `PoiHeroResolver` Tier 0).

3. **APK size concern (new, S147):** debug APK is 780 MB, dominated by `poi-icons/` 544 MB (pre-existing, gitignored, never in git). S147 triptychs are 73 MB / 9% of the total. Pre-Play-Store audit will need to prune or compress `poi-icons/` — verify how many of those PNGs are actually referenced by live code (`PoiHeroResolver.categoryToFolder` + `ProximityDock.categoryToFolder` are the two known consumers) and whether 256×256 or lossy WebP at q=75 is acceptable. Not blocking S148 but must land before the first release AAB upload.

**No campaign monitor needed** — S147 triptych campaign finished clean at 13:17 EDT. No background processes left running.

**Background items (not blocking S148):**
- **37-item parking lot walkthrough** — Clusters C/D/E/F/G/H/I items wait. Operator will walk through the rest post-Monday.
- **Long-run device soak (8-12 h)** — operator-run at own pace. Confirms S141 GPS-OBS backoff.
- **OMEN-004 — first real Kotlin unit test** — deadline 2026-08-30 (~4.5 months out). Small scope.
- **NOTE-L014 Privacy Policy** — V1-minimal artifact shipped S145 at `docs/PRIVACY-POLICY-V1.md` (Posture A). Full OMEN-008-compliant draft at `docs/PRIVACY-POLICY.md` remains pending OMEN review (32 sessions); decoupled from V1 ship because V1 doesn't collect RadioIntelligence Salem data.
- **NOTE-L015 `~/Development/SalemCommercial/` cutover** — **parked post-V1 per operator direction S145** (reversal of initial "abide" direction). Filesystem check: cutover never executed on this workstation; `~/Development/Salem/` still canonical. Nothing to do LMA-side until the physical move happens; decision is post-V1-ship.
- **NOTE-L018 PG-13 content rule** — pending OMEN acceptance + upstream relay.
- **NOTE-L019 restrooms_zombie.png regen** — LOW content-art item, no deadline.
- **SalemIntelligence bug report** — `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` drafted S144 for operator to forward to SI. 10 BCS POIs with phantom coords 0.2 m off Samantha statue (geocoding fallback bug).

**Post-S145 key facts:**
- **Lawyer packet drafted** — `docs/lawyer-packet/10-legal-walkthrough.md` (~260 lines, 12-item open-decision menu: C-corp formation, app name, domain, Privacy Policy pointer, pricing pointer, ToS stub, IP copyright/trademark/4-provisional-patents, Play Store checklist, Data Safety, insurance, timeline). `docs/lawyer-packet/11-pricing-and-age-gate.md` (~220 lines, $19.99 flat paid + corrected age-gate mental model: Teen + 13+ + no-data → Google Family Link, not paid-status).
- **V1 Privacy Policy drafted** — `docs/PRIVACY-POLICY-V1.md` (Posture A V1-minimal, ~180 lines). 4 TBDs for Monday counsel: operating entity, jurisdiction, contact email, mailing address. Full OMEN-008 draft preserved at `docs/PRIVACY-POLICY.md` for future-state RadioIntelligence Salem collection.
- **Hosting decision:** DestructiveAIGurus.com subpages. Site redev is follow-on post-V1. Pattern: `destructiveaigurus.com/katrinas-mystic-guide/{privacy,support,terms}`.
- **Entity decision:** C-corp to be formed at 2026-04-20 counsel meeting; interim fallback = sole proprietor (Dean Maurice Ellis) with entity transfer post-formation if timing slips.
- **#45 Universal Audio Control shipped** — `audio/AudioControl.kt` + `audio/NarrationHistory.kt` + 6 vector drawables + toolbar_two_row.xml layout + AppBarMenuManager.setupAudioAndNavCluster + content-popup AlertDialog + NarrationManager enqueue-gate + `replayHistoryEntry` + tag-based kind inference + detail-aware `pickVariantText` with graceful fallback + TourViewModel nav wrappers + SalemMainActivity listener + SplashActivity always-on voiceover. Device-verified on Lenovo HNY0CY0W: 4 nav icons + audio icon render at correct bounds; popup opens with 4 group toggles (Oracle / Meaningful / Ambient / Businesses) + Detail radio (Brief/Standard/Deep); First-tap confirmed replay of "Lappin Park"; long-press Audio cycles STANDARD→DEEP. Pause = long-press on Next. Jump shows a Toast for now; real sheet-routing deferred to #27 hero view.
- **Category→group mapping (#45):** MEANINGFUL = HISTORICAL_BUILDINGS + CIVIC + WITCH_SHOP + WORSHIP; AMBIENT = PARKS_REC + EDUCATION; BUSINESSES = FOOD_DRINK + SHOPPING + LODGING + HEALTHCARE + ENTERTAINMENT + AUTO_SERVICES + OFFICES + TOUR_COMPANIES + PSYCHIC + FINANCE + FUEL_CHARGING + TRANSIT + PARKING + EMERGENCY. Unknown → MEANINGFUL (safe historic default).
- **Detail level semantics:** Brief = POI name only; Standard = short narration; Deep = long narration with graceful fallback to short then name.
- **Rolling narration history:** 25-entry ring buffer, session-lifetime (cleared on process death), not persisted. First = oldest live entry; Prev = step back; Next = step forward (or skip current if at tail).
- **Splash voiceover is always-on** — operator mid-S145 direction "splash is always audio available"; no AudioControl gate. Splash will still be replaced with screaming-cat in #44.

**Post-S144 key facts:**
- **Samantha clamp shipped** — GPS fixes outside the Salem bbox (42.475–42.545 N × -70.958 to -70.835 W) snap to the Bewitched / Samantha Statue (42.5213319, -70.8958518). New `SalemBounds.kt` helper + clamp at all 4 MainViewModel GPS emission sites. Raw GPS still logs to GPS-OBS for diagnostics; only the `_currentLocation` emission gets replaced. Transition logged once per inside/outside flip.
- **Cold-start tier-first narration shipped** — explore-mode narration queue prefers HISTORIC/CIVIC/EDUCATION tier while the user is within 40 m of their initial narration anchor. After crossing the threshold, reverts to S125's closest-first retail-block behavior.
- **Katrina mascot rollout** — 12 Salem/Halloween-themed splash variants (library-tea, pumpkin patch, moonlit rooftop, cozy bookshop, autumn leaves, jack-o-lantern, crystal orb, lighthouse, fireside, starry bat sky, forest ranger, witchy library tea) at `res/drawable-nodpi/splash_katrina_01..12.jpg` (800×1000 Q80, ~125 KB avg, 1.5 MB total). `SplashActivity.KATRINA_SPLASHES.random()` picks one per launch. Library-tea (#12) is the `splash_katrina_12` fallback referenced by `themes.xml` windowSplashScreenAnimatedIcon and `activity_splash.xml` src.
- **App icon — library-tea scene** — center-cropped 1024×1024 from `vibe-12-witchy-library-tea`. Mipmap PNGs at all 5 densities for `ic_launcher.png`, `ic_launcher_round.png`, `ic_launcher_foreground.png`. Adaptive icon XMLs updated to reference bitmap foreground; old vector "W" logo deleted. Background vector kept as deep-purple #2D1B4E.
- **Welcome dialog restructured (#47 + #47b)** — three cards ordered Take-a-Tour (hero, gold border, 84dp icon) → Witch Trials (64dp icon) → Explore (64dp icon). Icons on the left, Katrina avatar medallion + Creepster title at the top for splash continuity. Three pose-specific Katrina icons: `welcome_card_tour` (green velvet colonial coat + brass candle), `welcome_card_trials` (open spellbook + quill), `welcome_card_explore` (teal bowtie + parchment map). All built as local `drawable-nodpi/welcome_card_*.jpg` at 320×320 Q85 (~25 KB each).
- **Find menu reinforced (#49)** — 4 online/empty tiles hidden (Fuel & Charging, Transit, Parking, Emergency); 15 of 16 remaining tiles relabeled in Salem voice via local override map (Food & Drink → Taverns & Cafés, Healthcare → Apothecaries & Clinics, Worship → Churches & Meetinghouses, etc.); 16 painterly cartoon tile illustrations generated in Forge (`batch-find-tiles.py`) and wired as layered backgrounds with 0x99 black tint + white bold label + shadow for legibility. `FIND_SALEM_LABEL` + `FIND_TILE_ASSET` + `FIND_HIDE_IN_V1` are local to `SalemMainActivityFind.kt` so `PoiCategories.kt` mirror contract to `web/src/config/poiCategories.ts` stays untouched.
- **TTS tuning (#50)** — replaced naive `[.!?]` chunker regex in `WitchTrialsMenuDialog.chunkForTts` with a character-walker `splitIntoSentences()` that honors 40+ abbreviations (Mr/Mrs/Dr/Rev/Inc/Ltd/a.m./p.m./Jan/Feb/etc/Mass/Ave/…), single-letter initials, and next-char-lowercase continuation. Only splits at real sentence ends. Short text (< 3500 chars) still speaks as one utterance with natural Android TTS prosody.
- **Top toolbar + grid dropdown (#13 + #48)** — V1 toolbar: Home | [spacer] | Grid | About (Weather, Alerts, MapLayers all hidden). V1 grid: 2 rows of 4 — Row A: POI / Find / Go To / **Journey** (renamed Utility) / Row B: Tours / Events / Witch Trials / Legend. Social/Chat/Profile removed (auth needs online backend). Journey popup slimmed — only user-facing items remain (Record GPS, Build Story, Email GPX, GPS Mode, Narrator Mode, Legend); dev items stripped.
- **Parking-lot #35 escalated to HARD PRE-V1 RELEASE BLOCKER** per operator direction — all assets encrypted + ProGuard/R8 obfuscation before first Play Store submission. Tracked as task #6.

**S141 facts still current:**
- **V1 offline-mode enforcement shipped** — `FeatureFlags.V1_OFFLINE_ONLY = true` (compile-time const in `:core`). Three-layer enforcement: ViewModel gates (early-return) → OkHttp `OfflineModeInterceptor` (hard backstop in all 13 client sites) → offline-only tile sources (empty URL so osmdroid downloader refuses). V2 resumes by flipping one boolean.
- **Find feature fully offline** — `FindViewModel` rewritten from cache-proxy `FindRepository` to `SalemPoiDao` (Room). Proximity search, text search, category counts, website lookup all served from the bundled 1,837-POI `salem_content.db`. Public API preserved; `SalemMainActivityFind` unchanged. Session-level `hashCode → SalemPoi` cache for `fetchPoiWebsiteDirectly` with name+coord fallback.
- **Walking directions offline** — `WalkingDirections.getRoute()` returns straight-line, `getMultiStopRoute()` returns multi-segment straight-line, `getBundledTourRoute(tourId)` loads the pre-computed OSRM polyline from `assets/tours/{tourId}.json` (already generated for all 5 tours by S125's `backfill-tour-routes.js`). `TourViewModel.getFullTourRoute` prefers the bundled polyline for the active tour. `WalkingRoute.road` is nullable.
- **Log tuning shipped:** GPS-OBS stale-heartbeat backoff (30s → 5m → 15m by stale age; S140 showed 1,208 W-lines over 12h — projected to drop to ~15). `NarrationMgr.intentionallyStopping` flag distinguishes cancel vs real TTS error (spurious `E TTS ERROR` gone from cancel paths). `WALK_SIM_DWELL_MAX_MS` 60 → 180s and waives cap while TTS still actively speaking (Oracle tiles of 2-3 min no longer get cut off).
- **Device-verified** on Lenovo HNY0CY0W: zero outbound-network W-lines at cold boot, `TileSourceManager: buildSource(SATELLITE) v1Offline=true`, tour restored at stop 2/10, 1,837 POIs loaded from Room, no crashes, no ANRs.
- **Phase 9X COMPLETE** (from S140) — 14 actual sessions (S127-S140) vs 8 originally planned.
- **V1 commercial posture locked** (S138): $19.99 flat paid, fully offline, no ads, no LLM, no tiers. IARC Teen (PG-13). S141 brought the code into full compliance with the offline-only half.
- **PG-13 standing content rule** in effect. Rule memory: `feedback_pg13_content_rule.md`.
- **Device-verify preference:** Lenovo TB305FU (HNY0CY0W) over any emulator. Memory: `feedback_lenovo_over_emulator.md`.
- **NOTE-L018 (PG-13)** still pending OMEN acceptance + upstream relay.
- **BCS dedup fully resolved** (from S136). **PG: 1,837 active POIs** (1,483 narrated).
- **Walk-sim dwell cap** now 180s (was 60s); waived while TTS speaking.
- **Room `@Insert` silent-drop** from S129 still latent.
- **NVMe advisory** (2026-04-16) — LIFTED 2026-04-17. Normal commit/push cadence resumes.

**Salem 400+ launch deadline 2026-09-01 still tracks.** 4.5 months runway.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A** Backend Foundation | **COMPLETE** (S98-S101) | Schema, importer, admin auth, write endpoints, duplicates, per-mode visibility |
| **9P.B** Admin UI | **6/8 done** | 9P.6-9P.10b complete. Pending: 9P.11 (demoted), 9P.13 (folded into 9U). 9P.10a blocked on 9Q. |
| **9U** Unified POI Table | **DONE (S125-S126)** | Dedup, narration resync, NarrationPoint+SalemBusiness entity removal, TourPoi rerouted to salem_pois, legacy PG schema dropped, inventory PDF tool migrated. |
| **9X** Salem Witch Trials Feature | **COMPLETE (S127-S140, 14 sessions)** | S127 foundation. S128 history-article LLM gen. S129 History 4×4 tile UI. S130 Oracle Newspaper panel + 202 AI headlines. S131 People panel + TTS chunker. S132 pencil-sketch portraits + bug fixes. S133 cross-linking (1,110+ NPC name auto-links) + Today-in-1692 card + admin integration. S134 Historic Sites feature + category reclass. S135-S136 BCS dedup + newspaper dock mode. S137 HTML/WebView newspaper renderer + Oracle tile brief. S138 V1 posture locked + PG-13 standing rule + parking lot. S139 retroactive paperwork. S140 Oracle tile import + bundled Room bake + Lenovo device-verify. |
| **9Q** Salem Domain Content Bridge | not started — queued behind 9X | building→POI translation, 425 buildings, 202 newspapers. Simplified by 9U (no `poi_kind` column). |
| **9R** Historic Tour Mode | not started — queued behind 9X | opt-in chapter-based 1692 tour |
| **10** Production readiness | DEFERRED behind 9X+9Q+9R | Firebase, photos, DB hardening, emulator verification |
| **11** Branding, ASO, Play Store | target 2026-09-01 | Salem 400+ launch window |
| **Cross-project** SalemIntelligence | **Phase 1 KB LIVE** at :8089 | 1,724 BCS POIs, 116K entities, 238 buildings, 5.67M relations. Phase 2 (narration gen) pending operator gate. |

**Sessions completed:** 148. Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

---

## Carry-forward Items (NOT blocking Phase 9U)

**Content-art regen queue (post-S143):**
- **Splash illustration (#43)** — replace `splash_witchkitty.png` with a photo/stylized render of Katrina. Monday must-have per S143 operator direction.
- **Splash voiceover (#44)** — replace `splash_voiceover.wav` with a screaming-cat sound. Monday must-have per S143 operator direction. GPU-caution rule applies.
- **Location-aware art/sound initiative** — broader operator theme; no formal scope yet. Candidate for new phase entry post-launch.

**Still pending (carry into S120+):**
- **Heading-up rotation smoothness** — root cause identified in S115 (100 Hz sensor + main-thread saturation). Plan: cut log chatter, rate-limit apply, move sensor processing to background HandlerThread, switch static detection to wall-clock. **Scheduled for S120 Step 9U.17.**
- **`DWELL_MS = 3_000L` dead code** in `NarrationGeofenceManager` — declared, never wired.
- **GPS journey line backgrounding bug** — diagnosed S112; fix options proposed but not implemented.
- **DB wipe across APK reinstalls** — `fallbackToDestructiveMigration`. Replace with real Room migrations before Play Store.
- **walkSimMode gap-timing differences** — revisit if walk-sim should be 100% indistinguishable from real GPS.
- **Bearing filter cone is 90°** — operator may want to tighten.
- **Admin tool POI creation** — currently only edits existing POIs; Phase 9U BCS import adds 900+ new POIs which partially addresses this.
- **POI encounter review screen** — future in-app debug menu feature.
- **Pre-existing GPS log redundancy** — 4 lines from 4 layers per fix.
- **Narration sheet has no `setPeekHeight`** — 168dp workaround handles common case.
- **PRE-PRODUCTION HARD-DELETE: dedup loser rows** — S123 soft-deleted **110 duplicate POIs** across two passes: 86 from name-based pass (`data_source LIKE '%dedup-2026-04-13-loser%'`) + 24 from address-based pass (`data_source LIKE '%address-dedup-2026-04-13-loser%'`). Before Play Store APK build, hard-delete these so they don't ship in the bundled Room DB. Plan: `DELETE FROM salem_pois WHERE data_source LIKE '%dedup-2026-04-13-loser%' OR data_source LIKE '%address-dedup-2026-04-13-loser%';` (verify zero FK refs first). Dedup scripts: `cache-proxy/scripts/dedup-2026-04-13/`. Operator rule applied: rows with unique `intel_entity_id` (BCS) are kept regardless of address collision.

---

## OMEN Open Items

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — **V1-minimal Posture A shipped S145 at `docs/PRIVACY-POLICY-V1.md`** (decoupled from OMEN-008 review for V1 ship; V1 doesn't collect RadioIntelligence Salem data). Full OMEN-008 draft at `docs/PRIVACY-POLICY.md` still pending OMEN review (32 sessions); remains relevant for future RI Salem activation.
2. **NOTE-L015 — `~/Development/SalemCommercial/` cutover never executed.** **PARKED POST-V1 per operator S145.** Filesystem check confirmed Salem still at `~/Development/Salem/`; LMA runtime references resolve correctly. Decision deferred until after V1 ships.
3. **OMEN-002 history rotation** — operator action only.
4. **OMEN-004 — first real Kotlin unit test** — **deadline moved to 2026-08-30** (operator direction S142). Previously 2026-04-30. Surfaced to OMEN in S142 report for amendment or acknowledgment.
5. **Phase 9T.9 walk simulator end-to-end verification** still TODO.
6. **Cross-project: SalemIntelligence** — Phase 1 KB live. Phase 2 (narration gen) pending operator gate. Hero regen deferred behind Phase 2. **S144 new bug report:** `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` — 10 BCS POIs have phantom coords 0.2 m off the Samantha statue (geocoding fallback anchoring unresolvable POIs to a famous landmark). Operator to forward to SI.
7. **NOTE-L018 — PG-13 standing content rule** proposed to OMEN at S138 via out-of-cycle notification. Pending OMEN acceptance + relay to upstream Salem Oracle / SalemIntelligence / GeoInbox.
8. **NOTE-L019 — `restrooms_zombie.png` regen** (LOW) — one themed POI icon rsync-skipped during the Session 020 NVMe clone. No deadline, no blocker. Regen during a content-art session.
9. **NVMe advisory** — LIFTED 2026-04-17 (OMEN S021). Normal commit/push cadence resumes.
10. **9-dot menu witchy backgrounds + strong foreground titles** — PARKED S146 per operator. Toolbar grid menu (Row A: POI / Find / Go To / Journey; Row B: Tours / Events / Witch Trials / Legend) currently ships plain text items. Design direction: each item gets a witchy illustrated background consistent with Katrina painterly-storybook aesthetic, with a strong readable title in the foreground. UI work for a future session; content-art pipeline reusable from find-tile batch pattern. Full detail in `docs/session-logs/session-146-2026-04-17.md`.
11. **Cross-project: Oracle + SalemIntelligence burial-grounds-tribute hallucinations** — PARKED S146 per operator. Upstream AI content places Salem Witch Trials victims as interred at the Witch Trials Memorial / Charter Street Cemetery / Old Burying Point; those are monument/cemetery complexes where the 20 executed victims are NOT buried. Pre-Play-Store audit needed: grep the 20 canonical victim names against narration text on the 3–4 tribute POIs, excise any burial/resting-place claims. OMEN to relay upstream for Oracle + SI fix. Full detail in `docs/session-logs/session-146-2026-04-17.md`.
12. **APK size pre-Play-Store blocker (new, S147)** — debug APK is 780 MB, dominated by `poi-icons/` 544 MB (pre-existing, gitignored, never in git but bundled at build). S147 triptychs contribute 73 MB / 9% of total. Pre-release-AAB audit required: verify which `poi-icons/` category folders are actually referenced by live code (`PoiHeroResolver.categoryToFolder` + `ProximityDock.categoryToFolder`) and whether downsize to 256×256 or lossy WebP q=75 is acceptable. Must land before first Play Store AAB upload. Full detail in `docs/session-logs/session-147-2026-04-18.md`.
7. ~~**Cross-project: stale intel_entity_id UUIDs in salem_pois**~~ — **CLOSED S125 2026-04-14 (commit `870733b`).** Self-resolved on LMA's side via `cache-proxy/scripts/dedup-stale-intel-links.js`. Pulled SI's `/api/intel/poi-export` (1597 canonical entities), probed every LMA linkage, fuzzy-matched stale UUIDs by name + coord + `/context` validity, then re-linked survivors and soft-deleted the rest. Outcome: 12 canonicals re-linked (chezcasa, lafayette_hotel, rockafellas_restaurant, the_village + 8 others), 211 soft-deleted with `data_source LIKE '%dedup-stale-uuid-2026-04-14-loser%'` (pending pre-Play-Store hard-delete), 0 stale linkages remaining. Bonus: fixed a `generate-narration-from-intel.js` bug that was aborting on GET 404 instead of falling through to POST `/generate` — any future rerun now correctly synthesizes narrations for SI-known-but-uncached entities. No OMEN action required.

---

## Salem Oracle & SalemIntelligence Integration

**Two distinct services now available:**
- **Salem Oracle** at `:8088` — 1692 historical corpus (63 POIs, NPCs, trial events, newspapers). Used by admin tool's "Generate with AI" feature (Phase 9P.10b).
- **SalemIntelligence** at `:8089` — modern business KB (1,724 BCS entities, 116K total entities, 238 historic buildings, 5.67M relations). Build-time data source for Phase 9U POI import. Integration guide: `~/Development/SalemIntelligence/docs/lma-integration-guide.md`.

---

## Expanded vision (updated S123)

- **Phase 9U** — Unified POI Table + BCS Import. 1 session left (S124). Legacy cleanup + heading-up fix + admin historical_note field. **NEXT.**
- **Phase 9P.C** — Publish loop. Operational (publish-salem-pois.js shipped S118).
- **Phase 9Q** — Salem Domain Content Bridge. 2-3 sessions. Simplified by 9U unified table.
- **Phase 9R** — Historic Tour Mode. 4-6 sessions.

**Total runway:** ~9-12 sessions for 9U(cleanup)+9Q+9R. Launch deadline Sept 1, 2026.
- **S119 parking lot** — 29 items triaged into master plan backlog. See `WickedSalemWitchCityTour_MASTER_PLAN.md` → "Backlog — S119 Parking Lot Triage".

---

## POI Inventory

- **Current PG:** **1,837 active POIs** in `salem_pois` (1,483 narrated). S136 BCS re-import + orphan cleanup (93 soft-deleted) + LMA-side dedup (4 soft-deleted). All BCS-vs-BCS dupes resolved.
- **Room DB:** published from PG, in sync (1,837 rows).
- **Inventory PDF tool:** `tools/generate-poi-inventory-pdf.py`

---

## Pointers to detail

| What | Where |
|---|---|
| Recent session summaries (rolling window) | `SESSION-LOG.md` |
| Older session summaries | `SESSION-LOG-ARCHIVE.md` |
| Live conversation logs | `docs/session-logs/session-NNN-YYYY-MM-DD.md` |
| Full build plan | `WickedSalemWitchCityTour_MASTER_PLAN.md` |
| Phase 9U detail | `WickedSalemWitchCityTour_MASTER_PLAN.md` Phase 9U section |
| SalemIntelligence integration | `~/Development/SalemIntelligence/docs/lma-integration-guide.md` |
| Architecture, tech stack | `CLAUDE.md` and the codebase itself |
| Legal/compliance | `GOVERNANCE.md` |
| Patentable innovations | `IP.md` |
| Monetization strategy | `COMMERCIALIZATION.md` |
| UX/architecture decisions | `DESIGN-REVIEW.md` |
| Test checklist | `CURRENT_TESTING.md` |
| Older STATE.md content | `docs/archive/STATE_removed_2026-04-09.md` |
