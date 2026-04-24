# LocationMapApp — Session Log (Archive: v1.5.0 through Session 110, April 2026)

> Archived from SESSION-LOG.md. Contains all sessions through Session 156, plus the original v1.5.0–v1.5.50 archive at the bottom.
> SESSION-LOG.md keeps only the most recent 10 sessions. On every session end, the oldest session in SESSION-LOG.md is moved here (newest archived first).

## Session 156: 2026-04-21 — SI content refresh + TigerLine+MassGIS foundation plan + Step 0 discovery + Phase 9Y PG schema extension

Started as a routine SI-rewrite absorption (1,400 POI narrations OVERWRITE + 34 historical_notes + 2 coord drift fixes, Room DB rebaked 9.23 → 9.26 MB). Operator directed the session into a major pivot: commit LMA V1 to TigerLine + MassGIS as the foundational mapping substrate, add a time-slider over public-domain historical maps, and retire OSM/osmdroid entirely before V1. Approved plan at `docs/tigerline-integration-plan.md`; discovery at `docs/tigerline-integration-discovery.md`. PG `salem_pois` extended with 9 nullable Phase 9Y columns; `feedback_adb_install_after_db_rebake.md` memory saved; OMEN asks filed.

Full session detail: `docs/session-logs/session-156-2026-04-21.md`. Commits: `7632961` + `be99c86` + `7720837`.

---

## Session 155: 2026-04-20 — Thin investigation session: resource check, "almost OOM" traced to WWWatcher on a different box

Same-day continuation after S154 close. Resource check: no LMA services running, OOM was on a different box (WWWatcher). No code changes.

Full session detail: `docs/session-logs/session-155-2026-04-20.md`. Commit: `a3a7b40`.

---

## Session 154: 2026-04-20 — PG-13 business-strip + merchant-license gate, 73 MB hero prune, GPS cursor-freeze fix (Lenovo motion sensor)

Counsel engagement meeting was initial-talk only (cost estimate + retainer letter pending async); operator redirected to product refinement. Three passes shipped: (1) **PG-13 content-strip render gate** — non-licensed commercial POIs render name+address+category-graphic only; SI-generated narration/historical_note/description hidden. Reused `merchant_tier` column as unlock flag; new `PoiContentPolicy.kt` centralizes logic. (2) **Commercial hero prune** — 2,301 per-POI WebP files removed from APK (-81 MB). (3) **GPS cursor freeze** — Lenovo `TYPE_SIGNIFICANT_MOTION` never fires; added derived-speed escape hatch (≥ 0.3 m/s → unfreeze).

Full session detail: `docs/session-logs/session-154-2026-04-20.md`. Commit: `4f40de5`.

---

# Sessions S001-S153 (rolled here from SESSION-LOG.md by the rolling-window protocol introduced in Session 111)

## Session 153: 2026-04-20 — Webex demo crash → npc_bios + articles table fix, ASSETS-MANIFEST + pre-build verifier, SI anomaly relay, computer-crash recovery

Counsel engagement meeting + Webex demo day. Morning work: SI anomaly OMEN relay filed (ANOM-001 GP-rooftop phantom coords, ANOM-002 Heritage Trail verified_fact tier-leak), field walk checklist written, counsel packet verified. Mid-day the Webex demo crashed when the operator tapped a 1691-11-22 newspaper banner — `SQLiteException: no such table: salem_witch_trials_npc_bios`. Diff against `@Entity` declarations surfaced **two** tables missing from both source DB and bundled assets DB (`salem_witch_trials_npc_bios` 49 rows / `salem_witch_trials_articles` 16 rows); same S149 pattern that bit the newspapers table, where the bundle scripts write to assets-only and `publish-salem-pois.js` later clobbers. Fixed both `bundle-witch-trials-*-into-db.js` to the S150 pattern (CREATE TABLE IF NOT EXISTS, write source → mirror to assets) and rebuilt Room DB (8.4 MB → 9.2 MB). Introduced permanent guard rails: `app-salem/src/main/assets/ASSETS-MANIFEST.md` (asset registry + S149/S153 regression history) and `cache-proxy/scripts/verify-bundled-assets.js` (fail-fast pre-build check, 28 OK / 1 WARN / 0 FAIL). APK rebuilt and installed on Lenovo HNY0CY0W. Computer then crashed before commit — full recovery from git working tree (no stash, reflog clean, no data loss). Walk-sim demo log (`docs/session-logs/assets/s153/logs/logs/debug-20260420.log`, 23,456 lines) incidentally exercised the S150 fixes: Fix 2 ✅ (Salem Maritime NHS as HISTORICAL_BUILDINGS/MEANINGFUL), Fix 7 ✅ (NARR-GATE lines emit with B=false cross-check), Fix 1 ⚠️ (infra confirmed via `type=LONG_NARRATION` segments, but detail=DEEP not toggled), Fix 3 🚫 (walk-sim can't exercise real-motion escape hatch). Patched-APK smoke-test + counsel meeting outcome capture + real outdoor walk all carry to S154.

Full session detail: `docs/session-logs/session-153-2026-04-20.md`. Commit: `a3f1569`.

---

## Session 152: 2026-04-19 — Absorb SalemIntelligence KB rewrite (verified_facts landed; Heritage Trail confabulation cleared)

Operator reported SI had completely rewritten its KB to cure bad AI generalization; LMA re-synced and rebuilt. Ran a 7-step absorption: baseline probe surfaced 3 live fabrication hits + 9 rows still at the 30 Church St GP-rooftop phantom coord; coord sync returned 0 updates (SI export matched LMA — 9 phantom-rooftop rows filed as ANOM-001 for SI); historical_note sync updated 33 rows (including the S021 Black Mary Widow correction landing in `gallows_hill_salem`); narration sync updated 1,399 rows with SI's `1f5e051`-SHA regen (+269 short, +290 long). Audit caught two remaining confabulations: `salem_heritage_trail` had SI's verified_fact propagate only to short_narration ("yellow painted line") while long/medium/historical_note still carried an LLM-invented "In 2020 the trail underwent revisions to remove references to its original 'Red Line' history" narrative (filed as ANOM-002); `national_park_service_visitor_center` (LMA-side legacy, not intel-linked) said "red line painted on the sidewalk" directly. Operator-approved option 1: nulled the 4+2 bad fields on the two POIs (tagged `|overridden-s152-heritage-confabulation`), then fixed the identical fabrication hardcoded at `salem-content/.../SalemBusinessesExpanded.kt:1403` so the legacy JVM pipeline can't resurrect it. Rebaked Room DB: 1,769 narrated POIs (+286 vs S150). Built and installed a fresh 820 MB APK on Lenovo HNY0CY0W; cold-boot log verified S150 fixes 4/5/6 are live (GPS polling emits both 2500/30000ms branches, `setupMap` shows `bypassBbox=true` on first call, zero `no such table` errors — 202 newspapers baked). S150 fixes 1/2/3/7 need a field walk to exercise (pending for S153). Anomaly report for SI filed at `docs/SalemIntelligence-anomalies-s152-2026-04-19.md`.

Full session detail: `docs/session-logs/session-152-2026-04-19.md`. Commits: `d249073` (sync + Room bake + hand-nullings) + `a11a9fd` (Kotlin confabulation fix + APK rebuild).

---

## Session 151: 2026-04-19 — Counsel packet PDFs (pre-NDA / post-NDA) for 2026-04-20 counsel engagement meeting

Pivoted from S150 field-test verification to operator's top priority: produce PDF documents for tomorrow's counsel engagement meeting. Mid-session operator redirected to NDA-gated two-tier disclosure after flagging "don't expose IP without NDA." Shipped three PDFs under `docs/counsel-packet/`: **Tier 1 pre-NDA (46 pages, 176 outline entries, 256 links)** covering cover + NDA request, redacted legal walkthrough (IP §9.3 held), pricing + age gate, Privacy Policy V1, new V1 ToS stub, Data Safety pre-filled answers, Play Store checklist, counsel decision checklist, Tier-2 holdback manifest, and a mutual NDA template with explicit patent-novelty and successors-bind clauses; **Tier 2 post-NDA (133 pages, 462 outline entries)** bundling IP register + GOVERNANCE + COMMERCIALIZATION + DESIGN-REVIEW + future-state Privacy Policy; **Operator prep memo (7 pages)** flagging AI-image copyright post-Thaler, accidental-COPPA store-listing risks, Form TX 3-month statutory-damages window, patent 12-month on-sale bar, Play Store 20-tester/14-day rule, Webex-demo IP hygiene, and the likely need for both a corporate and a patent lawyer. Pipeline: markdown-pdf (PyMuPDF-backed) in ephemeral venv at `/tmp/pdfvenv` (libreoffice HTML→PDF was tested and discarded — only outlined `<h2>`). Reproducible via `docs/counsel-packet/build/build_{tier1,tier2,memo}.py`.

Full session detail: `docs/session-logs/session-151-2026-04-19.md`. Commit: `fccf67d`.

---

## Session 150: 2026-04-18 — Field-test root-cause fixes: detail level flows to long_narration, speakTaggedNarration split, 23 POIs recategorized, GPS trail + polling + bbox defaults

Opened with S149 field-test debug. Pulled Lenovo logs and diagnosed seven bugs from the Beverly→Salem drive: (1) "so little historical narration" = `getNarrationForPass` ignored `AudioControl.detailLevel`, hardcoded to `shortNarration` — DEEP setting was a no-op on the ambient walk path (tour-only `pickVariantText` was the only honoring path); (2) every Speaking event was `type=HINT` because `TourViewModel.speakTaggedNarration` delegated to `speakTaggedHint`; (3) "Phillip's house on Chestnut" not narrating = mis-categorized ENTERTAINMENT (stuck in muted BUSINESSES group); (4) GPS trail froze on drive because accelerometer significant-motion sensor doesn't fire in a stable car + 25m escape-hatch meant trail only updated every 25m of travel; (5) GPS polling pinned at 2.5s because `narrationActive=true` hardcoded, making the 60s battery-saver branch unreachable (494 fixes in 90 min, mostly parked); (6) bbox override defaulted to `false` so fresh install from Beverly clamped to Samantha; (7) newspaper panel broken because `salem_witch_trials_newspapers` table was missing from bundled Room DB. Shipped fixes for all seven plus instrumented the still-mysterious AudioControl gate leak (Grace Episcopal / Golden Dawn Contracting bypassed the S149 gate even with Businesses off — no toggle events, no code bypass, reason unknown). Recategorized 23 POIs (12 HISTORICAL_BUILDINGS, 1 CIVIC, 10 WORSHIP) tagged `|category-fix-s150-2026-04-18`. Republished assets DB (1,830 POIs + 202 newspaper articles). Build clean. APK installed on Lenovo for operator re-test.

Full session detail: `docs/session-logs/session-150-2026-04-18.md`. Commit: `5c833a3`.

---

## Session 149: 2026-04-18 — Splash TTS warm-start, businesses narration gate, SI coord sync, Use-Real-GPS override

Pivoted #44 splash voiceover from cat sounds to runtime Android TTS (welcome line "Welcome to Katrina's Mystic Visitors Guide, Historic Salem Tour App") — new process-scoped `SplashVoice` warms up the TTS engine in `WickedSalemApp.onCreate`, SplashActivity holds the visual splash until `UtteranceProgressListener.onDone` fires (15 s safety cap). Fixed AutoZone narrating at cold-start by gating `enqueueNarration` on the existing S145 `AudioControl` group system and flipping the Businesses toggle default to OFF. Pulled 18 corrected coordinates from SalemIntelligence (all 10 phantom-Samantha POIs relocated to their real positions; Starbird Dispensary moved 1.8 km, AutoZone 2.2 km) via new `sync-coords-from-intel.js`, then soft-deleted 7 Samantha-cluster junk duplicates. Added a "Use Real GPS Outside Salem" toggle to the Journey menu so operator can follow real GPS during drives from Beverly → Salem; setter re-emits `_currentLocation` via `requestLastKnownLocation()` for instant map refresh. Session closed with operator heading out on a field test — S150 will debug the tour log.

Full session detail: `docs/session-logs/session-149-2026-04-18.md`. Commit: `dff701a`.

---

## Session 148: 2026-04-18 — #44 splash voiceover — screech prototype rejected, friendly-meow direction locked, blocked on Ollama VRAM release

Second session of the day, focused on the last Monday Must-Have (#44 screaming-cat splash voiceover). Built the full AudioGen→sox pipeline end-to-end: 4 cat-screech candidates + 4 haunted-Salem atmospheric underlays generated via AudioCraft on RTX 3090, mixed into 4 stereo-44.1-kHz-PCM candidates via a new `mix.sh` recipe (upsample, pad, attenuate bed to −15 dB, hi-pass 60 Hz, soft-knee compand, plate reverb, fade, peak-normalize to −1 dB). Operator auditioned all 4 through workstation speakers and rejected them as "too jolting" — creative pivot to a long friendly girly-kitty meow instead. Saved `feedback_splash_voiceover_vibe.md` memory capturing the direction (why: operator rejection; how to apply: prefer warm/welcoming prompts over alarm-style). Friendly-meow regen attempted but blocked: Ollama woke up between the first and second AudioGen pass and grabbed 20 GB VRAM, leaving only 8 MiB free — regen requires `sudo systemctl stop ollama` to unblock. Operator did not run it before session end; carries to S149. No code changes to `app-salem/` — all artifacts in `/tmp/splash-voiceover-candidates/` (ephemeral). Phase status unchanged (9X still COMPLETE from S140). Session count 147 → 148.

Full session detail: `docs/session-logs/session-148-2026-04-18.md`. Commit: `5dc2f58`.

---

## Session 147: 2026-04-18 — Triptych campaign complete (1,837/1,837, 0 failed) + APK-wiring plumbing live

Ran the full 1,837-POI triptych generation campaign to completion on RTX 3090 (625.8 min, zero failures, OCR retry loop catching every SDXL text hallucination). Built the APK-wiring plumbing while the campaign ran: new `HeroAssetLoader` (LruCache + panel-1 slicing), Tier 0 triptych in `PoiHeroResolver`, `NarrationHero` thumbnail swap, idempotent `sync-to-apk.sh`, `.gitignore` covering `heroes/`. Synced all 1,837 triptychs into `assets/heroes/` (73 MB) and confirmed a clean debug APK build bundles every one. Released Forge WebUI + SD model at session end — 24 GB VRAM free for #44 voiceover or emulator.

Full session detail: `docs/session-logs/session-147-2026-04-18.md`. Commit: `e72b170`.

---

## Session 146: 2026-04-17 / 2026-04-18 — Narration queue fix + Heritage Trail featured + triptych pipeline + #27 hero banner + POI-priority race fix

Heavy session spanning into pre-dawn. Opened with operator bug report "POIs interrupt each other, they should queue" — traced to aggressive `cancelSegmentsWithTag("poi_narration")` in `enqueueNarration`; fix preserves the newspaper cancel and the jumpToFront user-tap preempt but deletes the auto-preempt path so auto-ENTRY POIs queue instead. Then operator: "I need the first tour highlighted as being the Salem Heritage tour" → pulled `HERITAGE_TRAIL` theme into its own "Featured — Salem Heritage Trail" section at the top of the tour selection dialog with gold-stroke border + "★ FEATURED" badge + 18 sp title. Then the main event: operator greenlit a full triptych hero-bar pipeline covering all 1,837 active POIs, with each POI rendered as a 3-panel WebP (Katrina cat-POV of the business / exterior with small cat / friendly cartoon monster at work). Built the framework from scratch — `tools/hero-triptych/{prompts_lib.py, generate_full.py, status_writer.py}` with ~105 name-keyword overrides, 11 subcategory maps, 16 category fallbacks, 12 famous-landmark overrides (Witch House / Witch Trials Memorial / Proctor's Ledge etc. with tribute-reverent phrasing that pre-empts OMEN-item-#10 burial-hallucinations). Iterated the anti-text defense through four stages after operator flagged sign-gibberish: stronger prompt → bottom-crop hack → FLUX-dev attempt (produced black images, aborted) → landed on **SDXL Turbo + easyocr retry loop on panel 2 only** with auto-abort if failure rate > 10%. Operator-validated 5-POI preflight (1/5 retry, all clean), launched the full overnight campaign (background PID 144843 at session close, 164/1837 @ 3.0/min, ETA ~9.5 h). Shipped **#27 persistent top-of-map narration hero banner** (design dialog-locked: Jump = pan + sheet, persistent-keeps-last, play/stop icon) — new `NarrationHero.kt` + 2 vector drawables + `activity_main.xml` overlay anchored to AppBarLayout + Jump click rewired from Toast to real routing + `replayNarrationHistory` wrapper on TourViewModel. Shipped **POI-priority race fix** after operator reported "historical POIs not taking priority over Oracle newspaper facts" — traced to the `NarrationState.Idle` observer racing main-thread `enqueueNarration` and stomping `currentNarration` during the transient Idle flash between newspaper cancel and POI speak; fix adds `narrationCancelForPoiAtMs` timestamp + 500 ms suppression guard. Parked two new OMEN items: #10 Oracle/SalemIntelligence burial-grounds-tribute hallucinations (narrowed scope to the 3–4 tribute POIs per operator follow-up), #11 9-dot menu needs witchy illustrated backgrounds with strong foreground titles. Saved `feedback_dialog_questions.md` memory (ask design decisions one-at-a-time via AskUserQuestion, not bulleted-text dumps). Device-verified the queue fix + Heritage Trail featured + hero banner + priority-race fix on Lenovo HNY0CY0W (three install passes across the session). Phase status unchanged (9X still COMPLETE from S140). Session count 145 → 146. Background triptych campaign + status_writer left running at close; cron monitor `ff100bed` dies with the session.

Full session detail: `docs/session-logs/session-146-2026-04-17.md`. Commit: `9f75058`.

---

## Session 145: 2026-04-17 — Lawyer packet (Privacy Policy V1 + pricing + walkthrough) + #45 universal audio control

Counsel-prep + the biggest Monday Must-Have of the lot. Walked the operator through 4 dialog-style Privacy Policy decisions (Posture A V1-minimal, DestructiveAIGurus.com subpage hosting, contact/mailing TBD for counsel, C-corp-to-be-formed) and 6 design questions for #45 (separate from #27 hero, 4-toggle grouping, Next+Pause combo, rolling 25-entry history, Jump as 4th nav button, graceful detail fallback). Drafted 3 counsel-ready artifacts: **`docs/PRIVACY-POLICY-V1.md`** (V1-minimal Posture A, ~180 lines — only what V1 actually does: GPS on-device, no servers, no SDKs, no ads, no data collection; 4 TBDs flagged for Monday; OMEN-008 full draft preserved at `docs/PRIVACY-POLICY.md` for future-state RI Salem), **`docs/lawyer-packet/11-pricing-and-age-gate.md`** (~220 lines — $19.99 flat paid, Google's 15%/30% commission, IARC Teen 13+ + all-None Data Safety → Google Family Link handles age enforcement, no dev-side age gate; corrected the operator's original "paid → bypass age" mental model to "Teen + 13+ + no-data → bypass age"), and **`docs/lawyer-packet/10-legal-walkthrough.md`** (~260 lines — 12-item open-decision menu covering C-corp formation, app name, domain, Privacy Policy pointer, pricing pointer, ToS stub, IP posture: copyright $65/trademark $300/4 provisional patents $1,280, Play Store checklist, Data Safety, insurance, timeline to Sept 1 ship). Then shipped **#45 Universal Audio Control**: new `audio/AudioControl.kt` singleton (4 group toggles + Detail enum + category→group mapping + listener pattern), new `audio/NarrationHistory.kt` ring buffer (25 entries, session-lifetime), 6 vector drawables, toolbar_two_row.xml layout (4 nav icons top-left, audio icon between Grid and About), `AppBarMenuManager.setupAudioAndNavCluster` with AlertDialog content popup (4 CheckBoxes + RadioGroup for Brief/Standard/Deep) + long-press detail cycle, NarrationManager enqueue-gate + `replayHistoryEntry()` + tag-based kind inference (witchtrials_/newspaper_/oracle_→ORACLE; sheet_/poi_→POI) + detail-aware `pickVariantText` with graceful fallback, TourViewModel nav wrappers, SalemMainActivity listener wiring, SplashActivity reverted to always-on voiceover per mid-build operator direction. NOTE-L015 path cutover investigated but parked post-V1 per operator direction (filesystem check confirmed `~/Development/SalemCommercial/` doesn't exist; Salem still at `~/Development/Salem/`). Device-verified on Lenovo HNY0CY0W — toolbar renders with all 5 new icons at correct bounds, popup opens with 4 toggles + radio, First-tap replays oldest history entry (confirmed "Lappin Park" replay → TTS), long-press Audio cycles detail STANDARD→DEEP (confirmed). Remaining Monday Must-Haves for S146 pre-Monday: #27 hero view (M), #44 screaming-cat voiceover (½ session content-art, GPU-caution). Phase status unchanged (9X still COMPLETE from S140). Session count 144 → 145.

Full session detail: `docs/session-logs/session-145-2026-04-17.md`. Commit: `5a28475`.

---

## Session 144: 2026-04-17 — Samantha clamp + 7 Monday Must-Haves (Katrina mascot pass)

Monster session — 8 ship items, including the full Katrina mascot rollout. Operator opened with a GPS-outside-bbox clamp directive ("default to Samantha statue when outside Salem") which exposed a data-quality bug: 10 BCS POIs share phantom coordinates 0.2 m off the Samantha statue, so the clamp triggered a commercial (AutoZone) as the opening narration. Shipped the clamp (`SalemBounds.kt` + 4 MainViewModel emission-site clamps + 5 hardcoded-fallback swaps), cold-start tier-first narration (HISTORIC wins near the anchor so Salem-flavored POIs open the session), and wrote a formal bug report to SalemIntelligence at `docs/SalemIntelligence-report-phantom-samantha-coords-2026-04-17.md` for the operator to forward. Then drove into the Monday Must-Haves block — 7 of 13 shipped: **#40** hub-card retitle, **#46** splash stale-concept cleanup, **#43** Katrina splash (12 randomized painterly moods, picked at runtime) + app icon (library-tea scene wired through the full adaptive-icon stack at all 5 mipmap densities), **#50** TTS pause-only-at-sentence-end (40+ abbreviation set + initial/continuation guard replacing the naive `[.!?]` regex that chopped at every "Dr." and "Mrs."), **#49** Find menu full (hide 4 online-only/empty tiles, Salem-voice relabels for the remaining 16, 16 painterly cartoon tile illustrations generated in Forge and wired as layered backgrounds with black-tint + shadow-label for legibility), **#47** welcome dialog reinforcement (Katrina avatar medallion + Creepster title matching splash + Salem-voice card copy), **#47b** welcome restructure after operator feedback (reordered Take-a-Tour → Witch Trials → Explore, icons moved to left column, one-line descriptive titles + one-line action blurbs, three pose-specific Katrina icons: tour-guide/scholar/explorer), and **#13 + #48** top toolbar + grid dropdown (V1 collapsed to 2 clean rows of 4 tiles — POI/Find/Go To/Journey and Tours/Events/Witch Trials/Legend; Social/Chat/Profile stripped since their auth flow requires a backend; Utility popup renamed Journey and slimmed to user-facing items only). Parking-lot #35 escalated to **HARD PRE-V1 RELEASE BLOCKER** per operator direction (all assets encrypted + ProGuard/R8 obfuscation before the first Play Store submission). Remaining Monday items: #10/#11 legal write-ups, #27 hero-view rebuild, #45 universal audio control, #44 screaming-cat voiceover. Phase status unchanged (9X still COMPLETE from S140). Session count 143 → 144.

Full session detail: `docs/session-logs/session-144-2026-04-17.md`. Commit: `729018b`.

---

## Session 143: 2026-04-17 — Tile coverage + red-ball + POI icon/hero fixes + z21 overzoom + Monday-lawyer parking-lot restructure

Operator redirected pre-parking-lot to four map-layer concerns. **(1) Tile coverage** — the post-rebrand virgin install exposed that bundled tiles only covered tight downtown Salem; rewrote `tools/download_salem_tiles.py` to a per-zoom/per-bbox schema (z19+z18 downtown, z16-17 full Salem city 42.475–42.545 N × -70.958 to -70.835 W). Downloaded 3,838 new tiles in ~20 min (0 errors), bundle grew 40 MB → 89 MB with 7,133 total tiles across Esri satellite + OSM Mapnik. **(2) Red-ball low-zoom fallback** — new `SalemLocationBallOverlay.kt` paints a 40dp red disc at Salem center whenever `zoom < 16` (the floor of bundled tiles); screen-pixel rather than metric radius so the ball stays the same size from z3→z15. Added at overlay index 0 so POI markers/GPS dot draw on top. **(3) Orphaned POI categories** — `MarkerIconHelper.CATEGORY_MAP` + `CIRCLE_ICON_MAP` had no entries for `HISTORICAL_BUILDINGS` (115 POIs, added by S134 historic-sites reclass) or `TOUR_COMPANIES` (16 POIs) so 131 markers fell through to the DEFAULT 5px generic purple dot and visually disappeared; added both to both maps. **(4) Orphaned hero-image folders** — `PoiHeroResolver.categoryToFolder` + `ProximityDock.categoryToFolder` pointed HISTORICAL_BUILDINGS and TOUR_COMPANIES at folders that never shipped (`historical_buildings/`, `tour_companies/`), and omitted AUTO_SERVICES (52 POIs) + FINANCE (5 POIs) entirely; remapped to existing art sets (`historic_house/` 40 variants, `ghost_tour/` 32 variants) and added the two missing entries so ~87 POIs recovered their themed hero instead of showing the red "ASSIGN HERO" placeholder. **(5) Overzoom to z21** — added `MAP_MAX_OVERZOOM = 21.0` constant; tiles still top out at z19 (`USGS_MAX_ZOOM`), osmdroid upsamples z19 tiles for z20/z21 view (pixel detail degrades, POI markers separate further at tight downtown clusters); kept `performCinematicZoom()` capped at 19 so splash entry still lands on clean tiles. Device-verified all five on Lenovo HNY0CY0W after a clean `adb uninstall`/reinstall (the `install -r` path silent-crashed on stale app-data Room caches — treated as transient dev friction, not a code bug). Screenshots at `docs/session-logs/assets/s143/` confirm tile coverage, red ball at z10, and Historical Buildings markers now distinct around the Samantha statue.

**Parking-lot re-cluster for Monday lawyer meeting:** operator declared a Monday 2026-04-20 lawyer appointment for C-corp formation and directed a full parking-lot restructure prioritized around what must-land before that meeting. Added a **cluster view** at the top of `docs/parking-lot-S138-master-review.md` regrouping all 45 items (37 original + 8 S143 additions #38-#45) into nine thematic clusters A–I ordered by operator value. Inserted a **⭐ Monday Must-Haves** block declaring 13 items committed for the Monday meeting: #10 legal walkthrough write-up, #11 $19.99+age-gate counsel write-up, #13 top menu redesign, #27 hero-view rebuild, #40 hub-card retitle ("Wicked Salem" residue), #43 Katrina splash graphic, #44 screaming-cat voiceover, #45 universal audio control (promoted from Cluster D), and new items #46 splash stale-concept cleanup, #47 opening menu reinforcement, #48 menu-keys/grid-dropdown reinforcement, #49 find-menu reinforcement, #50 TTS pause-at-sentence-end tuning. Each new item (#46-#50) got a full detail block with investigation notes and scope. Total must-have scope: 5 Easy, 7 Medium, 2 content-art half-sessions across ~3-4 focused sessions Fri 2026-04-17 → Mon 2026-04-20 — operator flagged "it is going to be a busy weekend!"

Full session detail: `docs/session-logs/session-143-2026-04-17.md`. Commits: (S143 shipped six edited files plus one new overlay file plus a 50 MB tile-bundle regen plus the parking-lot restructure; everything rode on one session-end commit at S143 close).

---

## Session 142: 2026-04-17 — V1 UI hide + full rebrand (applicationId + display label)

Two-step session. **Step 1 (UI hide):** operator directed picking up S141's deferred UI hide pass, deferring the 8-12h soak to their own schedule, and moving OMEN-004's deadline from 2026-04-30 to 2026-08-30. Explore subagent found all 7 online-only UI entry points (radar, weather, transit, aircraft, webcams, TFR, online tile switcher) concentrated in `app-salem/.../ui/menu/AppBarMenuManager.kt`. Shipped six surgical edits behind `FeatureFlags.V1_OFFLINE_ONLY`: hid the dead-code top-bar XML items defensively, hid the slim-toolbar weather + tile-source icons, hid grid-dropdown row 1, added early-return guards to all four `show*Menu()` paths, removed `menu_tfr_overlay` from the alerts popup, and stopped `computeActiveLayerCount()` from counting the 8 online-only prefs. Device-verified on Lenovo HNY0CY0W. Combined with S141's data-layer work, V1 now has three-layer offline coverage — OkHttp interceptor + ViewModel gates + UI hide. **Step 2 (full rebrand):** operator directed a Play-Store-ready install identity: `applicationId` changed from `com.example.wickedsalemwitchcitytour` (which Google Play rejects) to `com.destructiveaigurus.katrinasmysticvisitorsguide`, and `app_name` from "Wicked Salem" to "Katrina's Mystic Visitors Guide - Salem". Internal code namespace, class names, themes, module name, and docs intentionally left unchanged per operator direction — rebrand is at the install-identity / display-label layer only. Four file edits: `build.gradle` (applicationId), `strings.xml` (label), `AndroidManifest.xml` (FileProvider authority switched to `${applicationId}.fileprovider` to auto-sync), `SalemMainActivityGeofences.kt:651` (caller switched to `$packageName.fileprovider`). Uninstalled the old package on Lenovo and installed the new APK clean — launcher resolves, FileProvider authority registered as the new `…katrinasmysticvisitorsguide.fileprovider`, no crashes, S141+S142-Step1 V1 offline enforcement intact, 1,837 POIs loaded. `aapt2 dump badging` confirms display label `Katrina's Mystic Visitors Guide - Salem`. `applicationId` is now immutable once we ship; frozen before the submission window.

Full session detail: `docs/session-logs/session-142-2026-04-17.md`. Commits: `8b4c210` (UI hide) + `c92e1b3` (Step 1 close) + `c69282b` (rebrand) + `88e5687` (Step 2 re-close) + `bc0e778` (splash retitle) + the S142 final-close commit.

---

## Session 141: 2026-04-17 — V1 offline-mode enforcement + log tuning

Operator opened the session with a 12-hour Lenovo logcat dump (the S140 device build still running) and asked for a tuning pass. Log review found no crashes / ANRs / OOMs but five real issues: ~720 cache-proxy circuit-breaker W-lines, 1,208 GPS-OBS stale-heartbeat W-lines, 118 `TransitViewModel` main-thread blocks averaging 1.1s (the MBTA fleet StateFlow recompose), 3 spurious `TTS ERROR` E-lines (cancel-vs-real-error mislogging), and 2 walk-sim `DWELL CAP` hits. Operator directed **disable all outbound network features for V1** (including MBTA, so the 1.1s recompose goes away too), keep the three log-tuning fixes, and for V1 walking directions use pre-computed polylines. S141 shipped seven commits: `FeatureFlags.V1_OFFLINE_ONLY` + `OfflineModeInterceptor` installed in all 13 OkHttpClient sites, ViewModel early-return gates on Transit / Weather / Geofence / Aircraft / Main, `FindViewModel` rewritten to `SalemPoiDao` (A1 — offline Room search over 1,837 bundled POIs), `WalkingDirections` V1 offline path using the already-bundled `assets/tours/{tourId}.json` OSRM polylines from S125, `TileSourceManager` forced to empty-URL offline mode (DARK hidden in V1), GPS-OBS backoff cadence (30s → 5m → 15m by stale age), `NarrationMgr.intentionallyStopping` flag to distinguish cancel vs real TTS error, walk-sim dwell cap 60 → 180s that also waives cap while TTS still speaking. Device-verified on Lenovo HNY0CY0W cold boot: zero outbound-network W-lines, tile source reports `v1Offline=true`, GPS-OBS banner shows new backoff message, tour restored at stop 2/10, 1,837 POIs loaded from Room, no crashes. V2 = flip one boolean in `FeatureFlags`.

Full session detail: `docs/session-logs/session-141-2026-04-17.md`. Commits: `e50966b` (interceptor) + `e5208af` (VM gates) + `0f98b69` (FindVM→Room) + `d68440c` (WalkingDirections offline) + `d10c9d8` (tiles + log tuning) + `7a2e886` (MainVM gates) + `9d412c0` (log finalized) + the S141 close-out commit.

---

## Session 140: 2026-04-16/17 — Phase 9X Step 3 complete; Oracle tiles imported, bundled, device-verified on Lenovo; Phase 9X now COMPLETE

Shipped the twice-deferred Phase 9X Step 3. Discovered Salem Oracle had already generated and SalemIntelligence had already ingested 16 PG-13-compliant Oracle Newspaper Digest tiles (`:8089/api/intel/salem-1692/tiles`); PG-13 spot-checked the two highest-risk months (Aug 1692 Burroughs/Proctor and Sep 1692 Corey pressing) — both passed the IARC Teen bar. Wrote `cache-proxy/scripts/import-witch-trials-tiles-from-intel.js` to pull all 16 from SI into PG `salem_witch_trials_articles` under the new Oracle id convention (`intro_pre_1692` / `month_1692_01..12` / `fallout_1693` / `closing_reckoning` / `epilogue_outcomes`, replacing the stale S128 ids). Patched `bundle-witch-trials-into-db.js` with a DELETE-before-INSERT-OR-REPLACE step to avoid id-convention-change orphans in the bundled SQLite. Re-baked `salem_content.db`, refreshed fallback `articles.json`, built `:app-salem:assembleDebug` (31s, clean). Operator chose Lenovo TB305FU over the emulator for device-verify; unloaded Ollama's gemma3:27b first to free GPU, installed on HNY0CY0W, confirmed `WitchTrialsRepo: existing=16 / articles already hydrated` and `showTileDetail id=month_1692_07 order=8` + `id=month_1692_09 order=10`. HTML/WebView renderer, S133 NPC auto-links, TTS Speak button all working on fresh Oracle content. New feedback memory `feedback_lenovo_over_emulator.md`. **Phase 9X now COMPLETE** (14 actual sessions S127-S140 vs 8 originally planned).

Full session detail: `docs/session-logs/session-140-2026-04-16.md`. Commits: `9374380` (importer) + `c588658` (bundle fix + refreshed assets) + the S140 close-out commit.

---

## Session 139: 2026-04-16 — Retroactive close-out of S138 paperwork (no code work)

Operator opened the session with *"I thought I did a session end last session — if that was not captured, make it happen."* Evidence confirmed S138's five code commits were on master and pushed, but the session-end paperwork (live-log close block, SESSION-LOG entry, STATE refresh, OMEN report) had been skipped. S139 executed the retroactive close in a single commit (`675cc58`): appended a clearly-labelled close block to the S138 live log with honest narrative of what S138 actually shipped vs. the planned TOP PRIORITY, prepended the S138 entry to SESSION-LOG.md while rolling S128 to the archive, refreshed STATE.md (TOP PRIORITY rewritten for S139+, V1-posture key facts added, sessions count 137 → 138), and wrote the thin-template OMEN report at `~/Development/OMEN/reports/locationmapapp/session-138-2026-04-16.md`. Operator then ended S139; Phase 9X Step 3 (Oracle tile import + device-verify) rolls to S140.

Full session detail: `docs/session-logs/session-139-2026-04-16.md`. Commits: `675cc58` (retroactive close of S138) + the S139 close-out commit. No code changes. Phase 9X Step 3 still pending.

---

## Session 138: 2026-04-16 — 37-item parking lot + V1 commercial posture locked + PG-13 standing content rule

Session pivoted off the planned Oracle tile import / device verify (rolled forward to S139+) into operator-driven strategy work: triaged a 37-item "master review" prep list into `docs/parking-lot-S138-master-review.md` (status / complexity / dependencies per item, 16 clusters, 6 proposed new phases); resolved four decisions via dialog — V1 is **$19.99 flat paid, offline-only, no ads, no LLM, no tiers** (tiered/ads/LLM deferred to V2), operator's William Woodbury house (1676) in Beverly saved as PRIVATE user memory, GPS FAB #34 target confirmed, age-gate posture finalized (accept likely IARC Teen rating). Operator then clarified **PG-13 / Teen content rule** as a standing rule across all channels (narration, text, TTS/sox audio, SD prompts, portraits, external-generator briefs to Oracle / SalemIntelligence / Forge / ComfyUI / Bark / Piper); wrote `feedback_pg13_content_rule.md` memory, indexed at top of MEMORY.md, applied immediately to `docs/oracle-tile-brief.md`. Filed out-of-cycle OMEN notification (`S138-urgent-pg13-content-rule-2026-04-16.md`) asking OMEN to note-ack NOTE-L018 and relay the PG-13 constraint to upstream Salem Oracle / SalemIntelligence / GeoInbox projects.

Full session detail: `docs/session-logs/session-138-2026-04-16.md`. Commits: `b3b16ff`, `cd3393e`, `59b5034`, `c6c3a64`, `746163e`. **Note:** Session 138's end-of-session close-out was executed retroactively at the start of Session 139 (2026-04-16 late-evening) — operator noticed the paperwork had not landed though the commits were already pushed.

---

## Session 137: 2026-04-16 — HTML/WebView "The Oracle" newspaper renderer + tile brief

Shipped Step 2 of the newspaper UI enhancement: replaced the native-TextView `showWitchTrialsNewspaperDetailDialog()` with a full HTML/WebView newspaper page — "The Oracle" masthead with double-rule gold border, info row with dateline + crisis phase, ALL-CAPS headline, italic deck, flowing justified paragraphs with first-letter drop cap, gold dotted-underline NPC cross-links handled via `shouldOverrideUrlLoading` custom-scheme interception (npc:// and newspaper://). Added `renderHtmlWithLinks()` (HTML equivalent of Spannable cross-linker) and `buildNewspaperHtml()` (CSS-styled page builder). Wrote `docs/oracle-tile-brief.md` — complete content brief for all 16 History panel tiles (intro + 12 months + fallout + closing + epilogue) for Salem Oracle to generate as Oracle Newspaper Digests.

Full session detail: `docs/session-logs/session-137-2026-04-16.md`. Commit: `1d4e778`.

---

## Session 136: 2026-04-16 — BCS dedup finalization + device verification + newspaper dock mode

BCS re-import from fresh SI export (1,560 entities): 1,375 enriched, 89 orphans soft-deleted, all 7 BCS-vs-BCS dedup groups resolved. Walk-sim speed reverted to 1.4 m/s. Device verification confirmed newspaper dispatches fire during tour mode (S132 gate fix working). Built narration dock newspaper mode — "THE ORACLE" masthead + date on bottom sheet during newspaper playback, tap opens detail dialog. Next: HTML/WebView newspaper renderer (step 2) + SI-generated month tiles (step 3).

Full session detail: `docs/session-logs/session-136-2026-04-16.md`. Commit: `5aa5032`.

---

## Session 135: 2026-04-16 — BCS dedup + ATTRACTION removal + SI sync + newspaper overhaul

Major data quality and narration overhaul. BCS-prioritized POI dedup (39 soft-deleted, 12 tour stops repointed). Removed ATTRACTION tier entirely (PAID/HISTORIC/REST). Fixed Vampfangs historic false-positive (year ≤1860 threshold). Full SalemIntelligence re-sync with corrected geocoords (1,281 enriched, 100 new, 1,928 total). Regenerated Heritage Trail OSRM route (was cutting through buildings). Newspaper dispatch overhaul: Room DB source with dateline+headline+body format, yield-to-POI, 3s delay, fixed S132 tour-active gate that was blocking newspapers during tours. Walk-sim tuned: 3.0 m/s, 2.5s GPS, 15s POI dwell. Publish pipeline hardened (Room-compatible DDL, no DEFAULTs).

Full session detail: `docs/session-logs/session-135-2026-04-16.md`. Commit: `d186ad6`.

---

## Session 134: 2026-04-16 — POI category reclassification + Historic Sites of Salem feature

Shipped two major deliverables. (1) POI category reclassification: split `TOURISM_HISTORY` into `HISTORICAL_BUILDINGS` (117 POIs) and `TOUR_COMPANIES` (17 POIs), deleted `HAUNTED_ATTRACTION` and `GHOST_TOUR` categories, backfilled `year_established` for 77 POIs from historical notes. 22→20 categories, 12+ code files updated across Kotlin + TypeScript. (2) "Historic Sites of Salem" — new 4th tile on Witch Trials menu with era filter chips (ALL / 1692 & Before / Colonial / Federal), scrollable site list, and detail dialog with NPC cross-linking + TTS. Fixed bundled SQLite publish pipeline — new `publish-witch-trials-to-sqlite.js` populates all 3 witch trials tables. Device verification deferred to S135.

Full session detail: `docs/session-logs/session-134-2026-04-16.md`. Commit: `25dbb49`.

---

## Session 133: 2026-04-16 — Phase 9X.7 cross-linking + Today-in-1692 card + admin integration

Shipped Phase 9X.7 end-to-end: `EntityLinkRenderer.kt` auto-detects 49 NPC names (1,110+ mentions across all bios) as gold underlined ClickableSpans in article, bio, and newspaper detail dialogs — tapping navigates to the bio detail with self-linking excluded. "Today in 1692" gold-bordered card at top of Witch Trials menu matches the current calendar month-day to the 202-newspaper corpus (±3 day window). Admin tool integration: cache-proxy REST endpoints for all 3 witch trials tables + React `WitchTrialsPanel` with tabbed article/bio/newspaper browsers and inline editing, accessed via POIs/Witch Trials toggle in the admin header.

Full session detail: `docs/session-logs/session-133-2026-04-16.md`. Commit: `2669ea6`.

---

## Session 132: 2026-04-15/16 — Phase 9X.6 pencil-sketch portraits for 49 figures + bug fixes

Shipped Phase 9X.6: Oracle-extracted period-accurate appearance descriptions (role-aware vestments — Geneva bands for clergy, judicial justaucorps, per-station dress codes) distilled into SD prompt tails, then rendered through 4 checkpoints × 2 prompt versions (392 total portraits). Operator selected RealVisXL V5.0 v2 (role-aware). 49 grayscale JPGs (2.5 MB) bundled in APK, wired as 160dp hero portrait in bio detail and 48dp circular thumbnails in People browser, all decoded async via `Dispatchers.IO` + `LruCache(60)`. Bug fixes: ambient HINT narrations now suppressed during active tours (`runSilenceFill` tour-state gate); portrait bitmap ANR eliminated. AI Studio expanded with 3 new checkpoints (RealVisXL, Juggernaut XL, Flux.1 dev nf4) and `--api` flag.

Full session detail: `docs/session-logs/session-132-2026-04-15.md`. Commit: `9b81ce4`.

---

## Session 131: 2026-04-15 — Phase 9X.5 People of Salem 1692 panel + TTS chunking for long bios

Shipped Phase 9X.5 end-to-end: imported 49 Tier-1/2 historical figures from `~/Development/Salem/data/json/npcs/` into `salem_witch_trials_npc_bios` (bios assembled by concatenating narrative sections — life_before_1692, role_in_crisis, emotional_arc, etc. — into a `## Header`-delimited body, 10K–21K chars each), baked into the asset DB, and built the `WitchTrialsPeopleBrowserDialog` + `WitchTrialsBioDetailDialog` pair. Browser sorts by role bucket → tier → name, filter chips (All / Judges / Accusers / Accused / Clergy / Officials / Others) colour-coded per STATE.md (scarlet / gold / gray / purple / silver / slate); detail dialog renders gold `## ` subheadings, dates + age-in-1692, historical outcome, and Speak TTS. Root-caused and fixed the long-bio TTS error by adding `chunkForTts()` — splits on paragraph then sentence boundaries at a 3500-char cap; the 14K Stoughton bio now enqueues as 5 segments under the `witchtrials_bio_` tag prefix, and Stop still drains all chunks via `cancelSegmentsWithTag`.

Full session detail: `docs/session-logs/session-131-2026-04-15.md`. Commit: `f312b5e`.

---

## Session 130: 2026-04-15 — Phase 9X.4 Oracle Newspaper panel + tabloid-headline UX overhaul

Shipped Phase 9X.4 end-to-end: `WitchTrialsNewspaperBrowserDialog` (full-screen chronological list of all 202 Salem 1692-era dispatches 1691-11-01 → 1693-05-09, horizontal crisis-phase filter chips) + `WitchTrialsNewspaperDetailDialog` (phase eyebrow / date header / day-of-week / italic summary / Speak pill with `witchtrials_newspaper` TTS tag / bullet body-points list). Consolidated the 202-row `salem_newspapers_1692` corpus into the canonical `salem_witch_trials_newspapers` table, then bumped Room v7 → v8 with two additive columns (`headline`, `headline_summary`), batch-generated all 202 AI tabloid headlines via Ollama `salem-village:latest` (10.9 min, 0 failures; e.g. "FIVE WITCHES SWING FROM PROCTOR'S LEDGE!"), and rebuilt the list row into a 2-line layout: big gold serif "Mmm D, YYYY: ALL-CAPS HEADLINE!" + single-sentence event summary. Bonus: Witch Trials is now also reachable from the 9-dot tour menu (row 4, alongside Tours/Events) via new `onWitchTrialsRequested` event + `ic_witch_trials.xml`.

Full session detail: `docs/session-logs/session-130-2026-04-15.md`. Commit: `d8df2ab`.

---

## Session 129: 2026-04-15 — Phase 9X.3 Salem Witch Trials History 4×4 tile UI + detail dialog shipped

Shipped Phase 9X.3 end-to-end: `WitchTrialsHistoryDialog` (full-screen 4×4 GridLayout of tile cards with tile-kind iconography, period label, bold title, teaser), `WitchTrialsTileDetailDialog` (hero header, italic teaser, pill "▶ Speak" button bound to `tourViewModel.speakSheetSection(tag="witchtrials_article", …)` with auto-cancel-on-dismiss and narrator-mode auto-play). Two publish paths shipped: `publish-witch-trials.js` (JSON asset fallback) + `bundle-witch-trials-into-db.js` (primary — uses `better-sqlite3` to pre-populate the bundled Room DB directly), the latter needed because a retrofitted asset DB silently dropped Room `@Insert` writes on a journal-mode flip. Had to bake the three Phase 9X tables + v7 identity hash (`0545a31e8fb7ce05e575755b61532d46`) into the asset DB to close the original install-crash (`SQLiteException: no such table: salem_witch_trials_articles`). Verified on Lenovo HNY0CY0W with fresh install: welcome → hero → sub-menu → grid → tile #2 → Speak TTS → close → tile #16 all work clean.

Full session detail: `docs/session-logs/session-129-2026-04-15.md`. Commit: `3a7d65b`.

---

## Session 128: 2026-04-15 — Phase 9X.2 Salem Witch Trials History articles: 16/16 generated + loaded into PG

Built `tools/witch-trials-generator/` — Python pipeline that buckets the Salem corpus (`_all_events.json` + `_all_facts.json`'s 1,743 dated facts) per tile, renders Jinja prompts (intro / monthly / quiet_month for Nov+Dec / fallout / closing / epilogue), and drafts all 16 history articles via direct calls to Ollama (`salem-village:latest` = same Gemma3:27B Q4_K_M the Salem Oracle uses internally). Pivoted off Salem Oracle's `/api/oracle/ask` wrapper after the smoke test failed at its hard 30s timeout — bypassing the wrapper saved both the timeout problem and the planned GPU-swap dance with SalemIntelligence (the model can be talked to directly while SI runs). Full 16-article run took 6.8 min total (vs the 50-60 min plan estimate). All 16 imported into `salem_witch_trials_articles` via idempotent UPSERT (`admin_dirty=TRUE` rows protected) — body lengths 3,138-4,232 chars / 494-695 words, all `verified_date=NULL`, `confidence=0.7`, provenance label `data_source='ollama_direct_salem_village'`.

Full session detail: `docs/session-logs/session-128-2026-04-15.md`. Commit: pending. Master plan: Phase 9X.2 marked DONE; architecture + provenance + risks sections updated to reflect the direct-Ollama path. STATE.md TOP PRIORITY rolled to S129 (Phase 9X.3 — wire articles into the History 4×4 tile UI + detail dialog).

---

## Session 127: 2026-04-15 — Phase 9X.1 Salem Witch Trials feature foundation: schema + splash + 3-panel skeleton

Operator pivoted ahead of Phase 9Q/9R into a new top-level "The Salem Witch Trials" feature (hero+2-below welcome dialog → 3-panel sub-menu: History 4×4 tile grid + Oracle Newspaper browser + People of Salem 1692 portraits/bios). Plan locked via 13 dialog-box Q&A (8 sessions S127-S134, full scope, OMEN-004 deliberately slipped). Phase 1 shipped foundation: 3 PG tables (`salem_witch_trials_articles/npc_bios/newspapers` mirroring `salem_pois` admin_dirty + soft-delete + provenance pattern), 3 Room entities + DAOs + Hilt providers (DB v6 → v7), `WitchTrialsRepository` + `WitchTrialsViewModel`, `MenuPrefs.PREF_NARRATOR_MODE_ENABLED` + utility menu toggle, redesigned welcome dialog (gold-bordered crystal-ball hero card + horizontal Explore/Tour row), `WitchTrialsMenuDialog` with 3 panel cards + "coming in Phase N" placeholders, asset directory `app-salem/src/main/assets/witch_trials/` with empty stubs. Verified visually on Lenovo HNY0CY0W (uninstall+install due to DB version bump, granted location, force-stop+launcher restart, screenshots confirm all 3 navigation paths).

Full session detail: `docs/session-logs/session-126-2026-04-15.md` (Phase 9X.1 section). Commit: `ebc9e30`. Master plan: new Phase 9X section. Plan file: `~/.claude/plans/rosy-shimmying-stream.md`.

---

## Session 126: 2026-04-15 — Phase 9U closeout + GPS-off-means-off + walk-from-long-press + tour-broken hotfix

Cleaned up Phase 9U: deleted NarrationPoint + SalemBusiness Room entities, rerouted `TourPoi` to project from `salem_pois` (no longer `@Entity`), dropped 23 dead repository methods, bumped DB version 5→6. Walk button now starts the tour from the long-press location. GPS toggle FAB + `setManualLocation` now actually cancel the GMS provider. Tour-selection-broken regression fixed.

Full session detail: `docs/session-logs/session-126-2026-04-15.md`. Commits: `5335ff3` → `ebc9e30`.

---

## Session 125: 2026-04-14 — Overnight-test fixes + narration coverage to 100% + legacy-tour restoration + admin SI bridge

Single-day megasession (23 commits). Shipped all 11 overnight-test P0/P1/P2/P3/P4 fixes. Generated narrations for 422 silent POIs via SalemIntelligence. Swept 216 stale SI UUIDs. Re-imported 4 legacy tours with OSRM polylines. Admin editorial-AI client retargeted from Oracle to SI.

Full session detail: `docs/session-logs/session-125-2026-04-14.md`. Commit: `4be98ce`.

---

## Session 124: 2026-04-14 — Heritage Trail Phase 9R.0 + 1692 newspaper corpus + overnight endurance walk

Shipped Phase 9R.0: Heritage Trail bundled asset, 202 newspaper dispatches, `HistoricalHeadlineQueue`, 2:1 silence-fill interleave, AU female voice. 1,143 POIs now have `historical_note`. Ran 7.5-hour overnight endurance walk.

Full session detail: `docs/session-logs/session-124-2026-04-13.md`. Commit: `12fa5a8`.

---

## Session 123: 2026-04-13 — POI dedup (110 soft-deleted), narration resync (+719 narrated), Phase 9R Historical Tour Mode spec

Two-pass POI dedup: name-based (86 losers, prefix-stripping) + address-based (24 losers, with stoplist + same-category guard + min-unique-tokens guard + BCS-uniqueness preservation). Re-ran SalemIntelligence narration sync (1,227 PG updates, +567 short / +719 is_narrated). Wrote Phase 9R Historical Tour Mode requirements spec (`docs/salem-intelligence-historical-tour-request.md`) plus cross-project coordination notes (NOTE-SI006, NOTE-S008) and direct doc copies to SI/Salem trees. Admin map zoom expanded to z22. Net POI count 2,190 → 2,080; 110 losers tagged for pre-Play-Store hard-delete.

Full session detail: `docs/session-logs/session-123-2026-04-13.md`.

---

## Session 122: 2026-04-13 — AudioCraft (AudioGen) installation for Salem sound effects

Installed AudioCraft 1.4.0a2 with AudioGen medium model at `~/AI-Studio/audiocraft/` for generating custom sound effects (geofence triggers, atmospheric audio, notifications). Resolved Python 3.12 / CUDA 13.0 / torchaudio 2.11 compatibility chain. Built Gradio web UI with Salem preset categories and CLI generation script. Created `~/AI-Studio/USAGE.md` service reference. No changes to LocationMapApp codebase — tooling session only.

Full session detail: `docs/session-logs/session-122-2026-04-13.md`.

---

## Session 121: 2026-04-13 — SalemIntelligence narration sync, drop multipass, tap-to-speak detail sheet, icon + dwell fixes

Built `sync-narrations-from-intel.js` and pulled narrations from SalemIntelligence into 1,211 POIs. Dropped multipass narration (pass_2/pass_3 columns) — promoted 717 entries to long_narration, simplified to 2-tier model. POI detail sheet sections now tap-to-speak with TTS interrupt. Fixed missing circle icons for all 19 SalemPoi categories, zoom-out marker sticky size bug, and tightened dwell expansion to 20m→35m→50m max (was 100m). Exported 572 unlinked POIs + handoff doc for SalemIntelligence.

Full session detail: `docs/session-logs/session-121-2026-04-13.md`.

---

## Session 120: 2026-04-13 — Phase 9U consumer migration (NarrationPoint→SalemPoi), admin tree rework, parking lot triage

Consumer migration across 13 Android files — entire narration pipeline now queries unified `salem_pois` table via SalemPoi entity. PoiHeroResolver upgraded to use S119 hero images. Admin PoiTree gets 3-level tree (category→subcategory→POI), "Visible only" filter, human-readable labels. AdminMap renders hidden POIs at 35% opacity. Default visibility fixed for 210 POIs. 29 S119 parking lot items triaged into master plan backlog.

Full session detail: `docs/session-logs/session-120-2026-04-13.md`.

---

## Session 119: 2026-04-12 — Hero image generation pipeline — 1,295 cartoon Halloween hero images

Built complete hero image generation pipeline: SalemIntelligence export → Forge API batch generation (DreamShaper 8) → live QC web viewer → DB population. 1,295 images generated, 1,013 POIs updated with image_asset paths, bundled DB republished. 29 parking lot items captured covering app modes, UX overhaul, search, onboarding, and hero image refinement roadmap.

Full session detail: `docs/session-logs/session-119-2026-04-12.md`. Commit: `67874e8`.

---

## Session 118: 2026-04-12 — Phase 9U BCS import (976→2190 POIs), SalemPoi Room entity, walk sim overhaul

Imported 1,766 BCS POIs from SalemIntelligence — 404 existing enriched, 1,214 new inserted, total 2,190. Built PG→SQLite publish pipeline, new unified SalemPoi Room entity + DAO (version 5). Walk sim fixes: 3-then-30s narration pacing, dwell rate-limiting, direct-play bypass, skip button, icon refresh. Tour-aware walk sim: Walk button follows selected tour route.

Full session detail: `docs/session-logs/session-118-2026-04-12.md`. Commits: `5213be7`, `a4ac369`.

---

## Session 117: 2026-04-12 — Phase 9U Session 117: unified salem_pois table — three-table merge complete

Merged `salem_narration_points` (817), `salem_businesses` (861), and `salem_tour_pois` (45) into a single `salem_pois` table (71 columns, 976 rows, 14 categories). Steps 9U.1-9U.6 shipped: unified DDL, migration script, FK repointing, admin backend + frontend rewritten for single-table queries, old tables renamed to `_legacy`. `PoiKind` type removed entirely from frontend. Zero TypeScript errors.

Full session detail: `docs/session-logs/session-117-2026-04-12.md`.

---

## Session 116: 2026-04-12 — Phase 9U planning: Unified POI table + SalemIntelligence import

Planning session — no code changes. Three strategic decisions: (1) V1 commercial release is fully offline, no hosted LLM/KB, SalemIntelligence is build-time data source only. (2) All 19 BCS categories map to existing 22 LMA categories with no new top-level types (4 new subcategories). (3) Merge `salem_narration_points` (817), `salem_businesses` (861), and `salem_tour_pois` (45) into a single `salem_pois` table — analysis confirmed 817/817 narration points match businesses by name+coordinates, they're the same entities stored twice. Added Phase 9U to the master plan (4 sessions S117-S120, 17 steps) covering schema migration, BCS import (~900 new entities → ~2,600 total), admin tool adaptation, publish loop, and Room migration. Heading-up rotation smoothness deferred to S120.

Full session detail: `docs/session-logs/session-116-2026-04-12.md`.

---

## Session 115: 2026-04-10 — Android debug + tour polish: 18 fixes, heading-up smoothness deferred

Android debug pass on the Lenovo TB305FU surfaced a cascade of related issues and shipped 18 fixes across 7 modified files + 2 new classes (`DeviceOrientationTracker`, `MotionTracker`). Highlights: GPS prune 1h→5min; near-zone escape hatch for POIs inside the behind-user filter; dwell expansion ladder (20→35→60→100m, cap 6) for stopping at the Witch House; new heading-up `N↑` FAB with hybrid heading source (GPS bearing → rotation vector sensor → stale GPS); walk sim auto-dwell at each POI (15m trigger, 3s "look at POI" pause via new `setMovementBearing` setter); `android:configChanges` on the Salem activity to survive rotation without destroying the walk sim coroutine — which **also kills the S110 lifecycle churn** that was on the carry-forward list for 6+ sessions; magnetic declination correction via `GeomagneticField` for true-north rotation; stationary GPS freeze via `TYPE_SIGNIFICANT_MOTION`; adaptive smoothing with static-mode detection and SNAP-on-rotation-complete; compass accuracy toasts. Heading-up rotation smoothness is not yet solved — root cause identified as 100 Hz sensor delivery + main-thread saturation from per-sample log writes, plan documented for S116.

Full session detail (sensor inventory findings via `dumpsys`, 5 test/iteration cycles, three separate walk sim behavior iterations, three separate rotation-responsiveness iterations, and the final discovery that the Lenovo fires sensor updates at 100 Hz via SENSOR_DELAY_UI): `docs/session-logs/session-115-2026-04-10.md`.

---

## Session 114: 2026-04-10 — OMEN NOTEs cleared + POI taxonomy foundation (Step 1 of 4-step arc)

Cleared the outstanding OMEN NOTE backlog (L013 VoiceAuditionActivity deleted, L014 Privacy Policy drafted for OMEN-008 Salem stream pending review, L015 confirmed no-op for the 9th consecutive session) and then pivoted mid-session from a planned hero-image regen into an architecture conversation that surfaced seven parallel POI taxonomies across the codebase. Shipped Step 1 of a new 1→4 taxonomy-alignment arc: two PG lookup tables (`salem_poi_categories` 22 rows, `salem_poi_subcategories` 175 rows) seeded from `web/src/config/poiCategories.ts` via a new idempotent Node script, new nullable `category`/`subcategory` columns on `salem_businesses`, three FK constraints enforced (narration_points.subcategory, businesses.category, businesses.subcategory), and a multi-session plan doc (`docs/poi-taxonomy-plan.md`) with explicit S115/S116 handoff notes and hard scope decisions (`salem_tour_pois` excluded — tour-chapter themes, different concept; no table merge per operator direction).

Full session detail (architecture conversation, seven-taxonomy mapping, two bugs caught and fixed during seed-script testing, decisions locked in, deferred items, hero regen still blocked on SalemIntelligence): `docs/session-logs/session-114-2026-04-10.md`. Commit: `e32a4b7`.

---

## Session 113: 2026-04-09 — POI Detail Sheet: full-screen dialog with hero + descriptions + TTS read-through

Built a new POI detail window that opens on tap of a dock icon, map marker, or narration banner — hero image strip (20% of screen, fitXY stretch from hash-pinned `poi-icons/` pool or red "ASSIGN HERO" placeholder), overview row, Visit Website through in-app WebView, short/about/story narrative sections, and render-time-synthesized action buttons. Three iterations in one session: initial BottomSheetDialogFragment build, full-screen DialogFragment conversion + TTS trim (drops action button narration, reads only name/address/phone/website-ack + descriptions), then highlight-ring tap-interception fix (polygons moved to overlay index 0 with null titles and non-consuming click listeners) plus narration banner click handlers plus verbose click-path logging. End-of-session hero regen pass deferred — operator escalated it into a new sibling product at `~/Development/SalemIntelligence` (knowledgebase + LLM for per-business research-driven prompts) that takes a day to build.

Full session detail: `docs/session-logs/session-113-2026-04-09.md`. Commit: `2826145`.

---

## Session 112: 2026-04-09 — Major narration overhaul: tiered queue, density cascade, ahead-only filter, glowing highlight ring, walk-sim 3x removal, fresh-start reset, POI button tier filter, home-stops-walksim

Operator field test surfaced three distinct narration bugs (APPROACH-cooldown, walk-sim 3x starvation, sequencing) plus the GPS journey line backgrounding issue (diagnosed not fixed). Shipped 5 commits ending with a 3-level density cascade (40m/30m/20m discard tiers) + bearing-based ahead-only filter that picks the closest in-front POI within the highest non-empty tier, plus a glowing pulsing highlight ring on the active POI and a debug overlay showing the discard radii as outline circles around the walking icon. Operator confirmed "working much better. Success!" on the final walk.

Full session detail (all 5 commits, 7 design questions, debug log analysis, tier classifier, bearing math): `docs/session-logs/session-112-2026-04-09.md`. Commits: `7ca0602`, `95f25f2`, `dded0e6`, `1f63113`, `8a94a06`.

---

## Session 111: 2026-04-09 — Protocol optimization (compress STATE.md, archive S001-S101, rewrite session protocols, thin OMEN report template)

Compressed STATE.md from 784 → 112 lines (snapshot-only), archived sessions S001-S101 to SESSION-LOG-ARCHIVE.md (rolling-window protocol now active), and rewrote CLAUDE.md session-start protocol for parallel + head-only reads and session-end protocol for write-once-in-live-log with a new ~80-line thin OMEN report template. Operator-queued from S110 wrap-up; net per-session read cost drops ~70% at session start.

Full session detail: `docs/session-logs/session-111-2026-04-09.md`.

---

## Session 110: 2026-04-09 — Five-pivot side quest: layout fix + runaway-loop fix + verbose persistent logging + cache-proxy circuit breaker + POI proximity encounter tracker

Five operator pivots, all unplanned, all driven by real bug reports from a field test on the Lenovo TB305FU. Major fixes: (1) NarrationGeofenceManager promoted to Hilt @Singleton — survives orientation change; (2) reach-out radius tightened to 15m/25m; (3) GPS staleness gate (30s threshold); (4) verbose persistent file logging with PID prefix; (5) OkHttp circuit breaker for local server; (6) PoiEncounter Room entity for proximity analytics. Zero progress on carry-over 9P.11.

Full session detail: `docs/session-logs/session-110-2026-04-09.md`.

---

## Session 109: 2026-04-09 — Side quest: column drift + GPS observer + GPS Journey recorder

Column drift fix (PG narration_points now has 817 fully populated rows — switched importer from .sql to .db), GPS proportional dead zone (accuracy*2 instead of fixed 100m), idlePopulate gated to tour mode, GPS Journey always-on recorder with 24h rolling retention and toggle FAB. No progress on 9P.11.

Full session detail: `docs/session-logs/session-109-2026-04-09.md`.

---

## Session 108: 2026-04-09 — Admin UI polish (tab wrap + backdrop opacity + legend z-index)

Short polish session. Three single-line edits: TabList flex-wrap, backdrop 40→65% opacity, legend z-index 400→100. No 9P.11 progress.

Full session detail: `docs/session-logs/session-108-2026-04-09.md`.

---

## Session 107: 2026-04-09 — Phase 9P.B Step 9P.10b finalization (recovery + commit)

Crash-recovery + commit session for the cross-midnight S106 work. Verified S106's Salem Oracle integration implementation was complete and clean (tsc + build, Oracle still live), committed as `a498562`. No code changes — purely recovery, verification, and commit.

Full session detail: `docs/session-logs/session-107-2026-04-09.md`. Commit: `a498562`.

---

## Session 106: 2026-04-09 — Phase 9P.B Step 9P.10b (Salem Oracle "Generate with AI" integration)

Built the Salem Oracle integration end-to-end: `oracleClient.ts` typed wrapper, `OraclePill` status indicator in admin header (polled, 3-state), nested "Generate with AI" sub-dialog in both Narration and General tabs of `PoiEditDialog`, Insert buttons that flow through react-hook-form dirty tracking, localStorage audit log capped at 500 entries. Smoke-tested against live Oracle.

Full session detail: `docs/session-logs/session-106-2026-04-09.md`.

---


## Session 105: 2026-04-08 — Phase 9P.B Step 9P.10 (POI edit dialog — tabbed Headless UI modal)

### Summary
Built `web/src/admin/PoiEditDialog.tsx` (~1,193 lines) and `web/src/admin/poiAdminFields.ts` (~75 lines, TS mirror of `cache-proxy/lib/admin-pois.js` field whitelists). The dialog is a Headless UI v2 `<Dialog>` + `<TabGroup>` modal with kind-aware tabs (General · Location · Hours & Contact · Narration · Provenance · Linked Historical (9P.10a stub) · Danger Zone) covering every column in `TOUR_FIELDS`/`BUSINESS_FIELDS`/`NARRATION_FIELDS`. Form state via `react-hook-form`; only `formState.dirtyFields` are sent to `PUT /api/admin/salem/pois/:kind/:id` so the partial-update endpoint never gets a clobbering full row. JSONB textareas pretty-print on load (`JSON.stringify(value, null, 2)`) and `JSON.parse` on submit — parse failures surface inline and abort save. Numeric fields coerce empty→null and abort on non-numeric strings. Date fields slice to YYYY-MM-DD. Tour-only boolean flags render as checkboxes. The category field is a free-text input + `<datalist>` of observed values from the loaded snapshot (computed in AdminLayout via a new `knownCategories` memo: 7 categories for tour, 19 business_types, 29 categories for narration). Soft-delete in the Danger Zone tab uses an inline confirm prompt (no nested dialog) → `DELETE /api/admin/salem/pois/:kind/:id` → `onDeleted` callback patches the row's `deleted_at` in the shared `byKind` snapshot so the tree ghosts it and the map drops it. Save flow: PUT → `onSaved(kind, updated)` patches the row in `byKind` + resets the form to the new baseline + closes. Cancel with dirty fields → `window.confirm("Discard and close?")`. Footer shows dirty-field count "N field(s) modified" or "No changes". **Open-trigger split:** marker click in `AdminMap` opens the dialog AND selects (per master plan §1296); tree click only selects + flies the map. Implemented via two new AdminLayout callbacks (`handleTreeSelect` and `handleMapSelect`) replacing the single `handlePoiSelect` from S104. `react-hook-form@7.72.1` and `@headlessui/react@2.2.10` installed (20 packages added, no new audit warnings). `npx tsc --noEmit` clean. `npm run build` clean (538 modules, 790KB bundle, no new warnings beyond pre-existing chunk-size note).

### Decisions
1. **Single dialog component, kind-aware via prop, conditional fields with `has(field)` guards.** Three separate dialog components (one per kind) would have ~70% duplicated JSX for the shared fields (name, lat, lng, address, phone, website, hours, image_asset, provenance) and would force the maintainer to remember to mirror changes across three files. One file with `has('subcategories') && (...)` blocks is uglier per-line but objectively easier to keep aligned with the cache-proxy whitelist.
2. **TS mirror of cache-proxy whitelists in `poiAdminFields.ts`** rather than fetching the schema or re-deriving from PG metadata. The cache-proxy already enforces the whitelist server-side; the client mirror just keeps the form scoped to writable fields. The two files must stay aligned when columns are added — comment at the top of `poiAdminFields.ts` calls this out explicitly.
3. **`react-hook-form` `formState.dirtyFields` for partial-update payloads.** The PUT endpoint is partial-update friendly. Sending only dirty fields means: no accidental clobbering of server-only fields, no needless `updated_at` bumps, no full-row payloads for one-field changes, and the dialog footer can show an honest "N field(s) modified" count.
4. **JSONB editor as raw textarea + JSON.parse on submit.** A real array editor would have been nicer for `tags`, `subcategories`, `related_*_ids`, etc., but the JSONB columns store heterogeneous shapes (some are string arrays, some are object arrays like `action_buttons`) so a generic editor is the only honest option for now. Parse-on-submit with inline error surfacing is the simplest correct path; a proper structured editor can land in a later step if the operator pushes back.
5. **Category field is free-text + `<datalist>`, NOT a fixed dropdown from `poiCategories.ts`.** The Android app's 22-category taxonomy doesn't match what's actually in the DB (7 tour categories, 19 business_types, 29 narration categories). Forcing a dropdown would lock the operator out of new values. Free text + datalist of observed values gives autocomplete without the lockout. Computed in AdminLayout via a `knownCategories` memo so the dialog doesn't need access to the full dataset.
6. **Open trigger split: marker click opens, tree click does not.** The master plan §1296 says "click marker → opens edit dialog (placeholder until 9P.10)" — opening the modal on every tree click would launch a dialog while the operator is just navigating. Two callbacks: `handleTreeSelect` (select-only) and `handleMapSelect` (select + flip `editOpen=true`). The single `handlePoiSelect` from S104 was split.
7. **9P.10a Linked Historical Content tab is a stub** ("No links yet — see Phase 9Q") since Phase 9Q hasn't run. The tab exists in the dialog so the muscle memory is right; the real implementation lands after the building→POI bridge is built.
8. **9P.10b Salem Oracle integration deferred to its own step** — the master plan calls for a "Generate with Salem Oracle" button on the Narration tab that opens its own sub-dialog and POSTs to `http://localhost:8088/api/oracle/ask`. Marked with a TODO comment in the file at the slot where the button will land. Keeping S105 scoped to the form itself.
9. **Soft-delete confirm is an inline prompt inside the Danger Zone tab, not a nested dialog.** Nested Headless UI dialogs would fight for focus management and add a dependency on `Disclosure` or another nesting primitive. An inline `confirmDelete` boolean state with cancel/confirm buttons is simpler and stays within the tab.
10. **Did NOT add an Oracle button to the General tab's `description` field even though master plan §1355 says to.** That's part of 9P.10b. Added a TODO comment at the top of the Narration tab pointing at the slot.

### Files Changed
- `web/src/admin/PoiEditDialog.tsx` — NEW (~1,193 lines)
- `web/src/admin/poiAdminFields.ts` — NEW (~75 lines, TS mirror of cache-proxy whitelists)
- `web/src/admin/AdminLayout.tsx` — modified (split `handlePoiSelect` into `handleTreeSelect`/`handleMapSelect`; added `editOpen` state, `handlePoiSaved`, `handlePoiDeleted`, `handleEditClose` callbacks, `knownCategories` memo; rendered `<PoiEditDialog>` at the layout root; doc comment block refreshed)
- `web/package.json` + `web/package-lock.json` — `react-hook-form@^7.72.1` + `@headlessui/react@^2.2.10` added (20 packages, no new audit warnings)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.10 marked DONE with full implementation checklist
- `STATE.md` — Last Updated → S105; TOP PRIORITY → Step 9P.10b; Phase 9P.B status row for 9P.10
- `CLAUDE.md` — session count 104 → 105; Phase 9P.B status 3/8 → 5/8; "next" pointer → 9P.10b
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-105-2026-04-08.md` — live conversation log
- `~/.claude/.../memory/project_next_session_priority.md` — refreshed to point at Step 9P.10b (Salem Oracle)

### Verification
- `cd web && npx tsc --noEmit` → exit 0, zero diagnostics
- `cd web && npm run build` → success, 538 modules transformed, dist/assets/index-*.js 790.08 kB / gzip 231.06 kB, no new warnings
- NOT browser-tested by Claude — the save/delete flows and the JSONB parse-on-submit handling are unverified end-to-end. First operator smoke test happens next session.

---


## Session 104: 2026-04-08 — Phase 9P.B Step 9P.9 (Admin map view via Leaflet + markercluster) + assessment delta review

### Summary
Built `web/src/admin/AdminMap.tsx` (~370 lines) and wired it into the AdminLayout center pane, replacing the 9P.9 placeholder. Map renders all ~1,720 active POIs as a single `L.markerClusterGroup` driven imperatively via `useMap()` (rather than the `react-leaflet-markercluster` wrapper, which is stuck on react-leaflet v4 while we're on v5). Markers are color-coded by kind (tour=red `#dc2626`, business=blue `#2563eb`, narration=green `#059669`) using cached `L.divIcon` SVG circles, with a separate single-marker recolor effect for selection highlighting (gold ring) so selection clicks don't trigger full cluster rebuilds. Tree-click → AdminLayout `selectedPoi` state → `<FlyToSelected>` calls `map.flyTo([lat,lng], max(currentZoom,17), {duration: 0.6})`. Marker drag captures origin at `dragstart`, fires confirm modal at `dragend` with kind/name/id/from/to/Haversine distance, Confirm POSTs to `/api/admin/salem/pois/:kind/:id/move`, AdminLayout's `onPoiMoved` callback patches the matching row in the shared `byKind` snapshot so PoiTree (consuming the same data via the new `externalByKind` prop) stays consistent. Cancel and HTTP failure both call `marker.setLatLng(origin)` to revert. Soft-deleted rows are filtered from the map (still visible in the tree behind its existing toggle). Top-right legend overlay shows kind colors + visible counts. Cluster config: `disableClusteringAtZoom: 18`, `maxClusterRadius: 50`, `spiderfyOnMaxZoom: true`. PoiTree gained two optional props: `onDataLoaded` (fires once after initial fetch) and `externalByKind` (snapshot override) — lets AdminLayout hoist the dataset and feed both panes from one shared fetch with one Basic Auth prompt. `leaflet.markercluster@1.5.3` + `@types/leaflet.markercluster` installed (2 packages added, NO new audit warnings — the 3 pre-existing high-severity transitives from S103 are unchanged). MarkerCluster CSS imported in `web/src/index.css`; `.admin-poi-marker` reset added to strip leaflet's default white divIcon background. `npx tsc --noEmit` clean. Side-task before code work: per user request, did a delta review of all OMEN notes (L001–L014) and directives (OMEN-002 through OMEN-011) against current project state — see live log for full table. Live demo via `bin/restart-proxy.sh` + `bin/start-web.sh` confirmed end-to-end: cache-proxy 4300 + vite dev 4302 + Basic Auth + 1,720 POI counts.

### Decisions
1. **Imperative cluster layer over `react-leaflet-markercluster`.** The wrapper is built for react-leaflet v4. We're on v5, which introduced internal API changes the wrapper hasn't caught up to. The official escape hatch is to use `useMap()` and drive the cluster group imperatively. ~30 lines of imperative code beats another upstream-lagging dependency, especially given the project already has 3 pre-existing high-severity audit warnings.
2. **Color by KIND, not category.** The 22-category taxonomy belongs to the tree's grouping. On the map the operator needs at-a-glance "what kind is this" — and there are too many categories to color-distinguish meaningfully. Per-category coloring can be revisited in 9P.11 (highlight duplicates) if needed.
3. **Single shared `byKind` dataset hoisted to AdminLayout.** Both the tree and the map need the same 1,720-row snapshot. Two fetches would mean two Basic Auth prompts (or a race) and double the network. Lifted state via PoiTree's new `onDataLoaded` callback + `externalByKind` prop. Drag-to-move patches the snapshot in place via `onPoiMoved`, and the tree re-renders off the new state without a re-fetch. This is the same pattern the edit dialog (9P.10) will use for save flows.
4. **Selection highlight via single-marker recolor, not full cluster rebuild.** `markersByKeyRef` is a `Map<"kind:id", {marker, kind}>` that lets a separate `useEffect` touch only the previously- and currently-selected marker icons on selection change. Full cluster rebuild on every selection click would be ~1,700 marker allocations + cluster reflow per click.
5. **Confirm modal is an overlay div, not a Leaflet popup.** Z-index 500, plain JSX. Easier to style with the rest of the admin tool's Tailwind palette, easier to show error messages inline, easier to manage focus and busy state. Leaflet popups would have fought all three.
6. **Soft-deleted rows filtered from the map but kept in the tree.** Tree is the operator's hierarchical inventory (tombstones useful for restore decisions); map is the spatial workspace (tombstones add clutter).
7. **`disableClusteringAtZoom: 18`** so the operator can drag individual POIs at deepest zoom without fighting cluster aggregation. **`maxClusterRadius: 50`** — half the leaflet default (80); at 1,720 POIs in downtown Salem the default produces too-large meta-clusters that hide merchant clumps.
8. **Did NOT install `react-hook-form` or `@headlessui/react`** despite the master plan listing them — those are 9P.10 dependencies, not 9P.9 dependencies. Keeping the install scoped to what this step actually needs.
9. **Defer click→edit dialog to 9P.10.** User asked at end of demo whether marker click could open the dialog. Currently the click sets `selectedPoi` but no dialog component exists. Offered a 5-10min read-only `PoiInspector` stub vs full Step 9P.10 work. User chose to defer entirely to next session as proper 9P.10. The selection-lifting hook is already in place — 9P.10 just renders against `selectedPoi`.

### Files Changed
- `web/src/admin/AdminMap.tsx` — NEW (~370 lines)
- `web/src/admin/AdminLayout.tsx` — modified (lifted `byKind` + `selectedPoi` state, new `handleDataLoaded` and `handlePoiMoved` callbacks, replaced center-pane placeholder with `<AdminMap />`, header diagram comment block updated)
- `web/src/admin/PoiTree.tsx` — modified (new optional props `onDataLoaded` + `externalByKind`, internal `effectiveByKind` threaded through render path, no behavior change for standalone usage)
- `web/src/index.css` — added MarkerCluster CSS imports + `.admin-poi-marker` reset
- `web/package.json` + `web/package-lock.json` — `leaflet.markercluster@^1.5.3` + types added
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.9 marked DONE with full notes; Stack additions section refreshed
- `STATE.md` — Last Updated → S104; Phase 9P.B status row for 9P.9; TOP PRIORITY → Step 9P.10
- `CLAUDE.md` — Project Status section refreshed: session count 76 → 104, "Phase 10 next" replaced with current 9P/9Q/9R structure
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-104-2026-04-08.md` — live conversation log
- `~/.claude/.../memory/project_next_session_priority.md` — refreshed to point at Step 9P.10

### Verification
- `cd web && npx tsc --noEmit` → exit 0, zero diagnostics
- Live demo confirmed end-to-end: counts = 45 + 861 + 814 = 1,720 (matches PG canonical from S101 9P.A.3); 401 challenge confirmed with `WWW-Authenticate: Basic realm="LocationMapApp Admin"`; user opened the page in browser
- NOT browser-tested by Claude — drag-to-move and fly-to flows are unverified end-to-end. Type system says they're correct; first user smoke test of those flows happens next session

### Side-task — OMEN assessment delta review
User requested a review of all old OMEN assessments since "a lot has changed". Findings (full table in live log):
- **Notes:** L001 done; L002 ⚠️ partial (history rotation pending); L003 deprioritized; **L004 ⚠️ unmet (deadline 2026-04-30)**; L005 stale premise (Phase 10 deferred behind 9P/9Q/9R); L006 evolving (PG canonical for POIs, Salem path stable); L007/L008/L010/L011 unchanged; L009 mostly met; L012 done; **L013 scope expanded** (VoiceTestActivity.kt also tracked now); L014 still owed
- **Directives:** OMEN-002 partial; **OMEN-004 unmet (deadline 2026-04-30)**; OMEN-006/007 done; OMEN-008 Privacy Policy still owed; **OMEN-011 confirmed Salem stayed in `~/Development/Salem/`** so cross-project path is unchanged
- Memory file `project_next_session_priority.md` was stale (still 9P.8); refreshed mid-session to 9P.10

### What's next
Phase 9P.B Step 9P.10 — POI edit dialog (tabbed modal). `react-hook-form` + `@headlessui/react` install, six tabs (General · Location · Hours & Contact · Narration · Provenance · Danger Zone), per-kind field whitelists copied from `cache-proxy/lib/admin-pois.js`, JSONB editors, PUT to `/admin/salem/pois/:kind/:id`, optimistic update via the same `byKind` patch pattern AdminLayout's `handlePoiMoved` already uses, dirty-tracking with warn-before-close. Memory pointer is current; AdminLayout's `selectedPoi` state is the open trigger.

---

## Session 103: 2026-04-08 — Phase 9P.B Step 9P.8 (POI tree via react-arborist)

### Summary
Built `web/src/admin/PoiTree.tsx` (~330 lines) and wired it into the AdminLayout left pane, replacing the 9P.7 placeholder. Tree fetches all 1,720 active POIs across the three kinds (`tour` 45 sequential first to fire the Basic Auth prompt cleanly, then `business` 861 + `narration` 814 in parallel reusing cached credentials) via `/api/admin/salem/pois?kind=...&include_deleted=true&limit=2000`, groups them as **kind → category → POI rows**, renders with react-arborist's virtualized list, supports client-side name search via `searchTerm`/`searchMatch`, soft-deleted toggle (client-side filter on the cached rows — no re-fetch, no re-prompt), counts per kind/category in the row labels and a totals strip in the toolbar, and emits `onSelect({kind, poi})` events into AdminLayout (currently `console.log`'d — the map pan/zoom in 9P.9 and edit dialog in 9P.10 will consume them). `useElementSize` ResizeObserver hook supplies the explicit pixel `width`/`height` that react-arborist needs for its virtualized list. `react-arborist@^3.4.3` added to `web/package.json` (16 packages, peer requirement is open-ended `react: >= 16.14` so React 19 is fine). Three pre-existing high-severity transitive audit warnings (`vite`, `picomatch`, `socket.io-parser`) surfaced but NOT introduced by this install — flagged for a separate cleanup pass. `npx tsc --noEmit` clean.

### Decisions
1. **Three-level tree, not four.** The master plan and the next-session memory both said "kind → category → subcategory → POI rows" (4 levels). Recon at session start queried PG and found: tour uses 7 distinct `category` values, business uses 19 distinct `business_type` values, narration uses 29 distinct `category` values — and `salem_narration_points.subcategory` is **NULL for all 814 rows**. `salem_tour_pois.subcategories` is a JSONB list of refinements (not a single column suitable for grouping). The fourth level isn't physically present in the data, so the tree collapses to three. Documented the deviation in the `PoiTree.tsx` header so a future session can re-add the fourth level when narration's subcategory column gets backfilled (or tour's JSONB list gets promoted) by editing `buildTree()` only.
2. **Sequential `tour` first, then parallel `business`+`narration`.** The first call to `/api/admin/salem/pois` triggers the browser's native HTTP Basic Auth dialog. Firing 3 simultaneous 401-eligible requests races the dialog and can cause some browsers to show the prompt multiple times. Awaiting `tour` (the smallest, 45 rows) sequentially first ensures the user sees exactly one prompt; once the browser caches credentials, the other two kinds fetch in parallel reusing them. Universal modern browser behavior — if a future session sees auth prompts firing 3x, the fix is to also serialize business+narration.
3. **Fetch with `include_deleted=true` once, filter client-side.** The "Show soft-deleted POIs" toggle rebuilds the tree from the cached rows. Flipping it is instant and never re-prompts for auth. Deleted rows render greyed-out italic with a "(deleted)" suffix.
4. **No taxonomy lookup at grouping time.** Categories in the DB are domain-specific OSM-style strings (`witch_trials`, `shop_retail`, `historic_site`, etc.), NOT layer IDs from `web/src/config/poiCategories.ts` (which uses `FOOD_DRINK`, `SHOPPING`, etc.). Decided not to attempt a lookup — just `groupBy` on the raw field. Each kind has its own vocabulary; overlapping names (e.g. `restaurant` in both business and narration) are scoped under their kind so no collision. The taxonomy file remains the source of truth for the *edit dialog* (9P.10) when the operator picks a category from a dropdown, but it's not needed for the tree's grouping shape.
5. **`useElementSize` ResizeObserver hook.** react-arborist requires explicit pixel `width`/`height` for its virtualized list. The hook handles measurement and reflow on browser resize so the tree fills the left pane. Future drag-handle for the left pane gets resize-responsiveness for free.
6. **`disableMultiSelection`/`disableEdit`/`disableDrag`/`disableDrop` all true.** The tree is read-only; mutations happen through the edit dialog (9P.10) and the admin map (9P.9). Locking down the unused react-arborist features is one less surface area to reason about.
7. **Defensive `parseFloat` on lat/lng.** node-postgres returns DOUBLE PRECISION as JS numbers in the current driver version, but the coerce is cheap insurance for the 9P.9 map view in case a future serializer ever string-ifies them.
8. **Pre-existing audit warnings deferred.** `npm audit` after the react-arborist install reported 3 high-severity transitive deps (`picomatch`, `socket.io-parser`, `vite`) — none introduced by react-arborist. Verified they live under `node_modules/{vite,tinyglobby,socket.io}/...`, all pre-existing. Tracked for a separate cleanup pass; not blocking 9P.8.

### Files Changed
- `web/src/admin/PoiTree.tsx` — NEW (~330 lines)
- `web/src/admin/AdminLayout.tsx` — modified (import `PoiTree`/`PoiSelection`, add `handlePoiSelect` callback, replace left-pane placeholder, update header comment)
- `web/package.json` + `web/package-lock.json` — `react-arborist@^3.4.3` added
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.8 marked DONE with full implementation notes including the 3-vs-4-level tree decision
- `STATE.md` — Last Updated → S103; Phase 9P.B status row for 9P.8; TOP PRIORITY → Step 9P.9 (Admin map view)
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-103-2026-04-08.md` — live conversation log

### Verification
- `cd web && npx tsc --noEmit` → clean exit, zero errors

---

## Session 102: 2026-04-08 — Phase 9P.B Step 9P.7 (Admin route + AdminLayout shell)

### Summary
First step of Phase 9P.B Admin UI build-out. Added a `/admin` route to the web app via path-based dispatch in `web/src/main.tsx` — chose this over installing `react-router-dom` because the public web app has no other routing needs and the admin tool is a single screen, so the dep was unjustified weight. Created `web/src/admin/AdminLayout.tsx` (~140 lines) with the full three-pane shell: header bar (slate-800) carrying **[Highlight Duplicates]** and **[Publish]** stubs (wired up in 9P.11 and 9P.13), an **Oracle: —** placeholder pill (wired up in 9P.10b), and a **[Logout]** button that uses the synchronous XMLHttpRequest 401 trick (sync XHR to `/api/admin/salem/pois` with deliberately wrong `logout:wrongpassword` credentials in `xhr.open()`, then `window.location.reload()` — overwrites the browser's cached Basic Auth credentials so the next admin call re-prompts). Left tree pane (white, 320px wide) and center map pane (slate-200) are placeholders with TODO comments pointing at the steps that fill them in (9P.8 and 9P.9). ASCII layout diagram and inline rationale for every design decision are included in the file header. Auth model: no in-page login form — the first admin API call from the page (the tree fetch in 9P.8) triggers the browser's native Basic Auth dialog because the cache-proxy admin endpoints (Phase 9P.3 middleware) return 401 with `WWW-Authenticate: Basic`. Subsequent requests reuse the browser's cached credentials automatically (same-origin via the vite `/api` proxy). `npx tsc --noEmit` clean. Also: refreshed the stale `project_next_session_priority.md` memory entry that still pointed at Privacy Policy drafting from S94 — now points at Phase 9P.B Step 9P.8 with concrete next-session steps and explicitly demotes NOTE-L014 to secondary.

### Decisions
1. **Path-based dispatch over `react-router-dom`.** The public web app currently has zero routing logic. The admin tool is a single screen. Adding a router dep is unjustified weight. `window.location.pathname.startsWith('/admin')` in `main.tsx` is one line and produces the same UX. Vite's dev server falls back to `index.html` for unknown paths so `/admin` works in `vite dev` and `vite preview`. Production hosting must mirror that fallback if `/admin` is ever exposed beyond dev — documented in the master plan and inline.
2. **No in-page login form.** Browser-native Basic Auth is the right tool when the auth surface is exactly one operator and the credentials live in `cache-proxy/.env`. The browser handles the prompt automatically when an admin endpoint returns 401, caches the credentials for the origin, and includes them on subsequent requests transparently (same-origin via the vite `/api` proxy). The first call to `/api/admin/salem/pois` (which lands in 9P.8) is the trigger.
3. **Logout via XMLHttpRequest 401 trick.** HTTP Basic Auth has no formal logout. The widely-used workaround is to fire a synchronous XHR with deliberately wrong credentials at a URL the server will reject; the browser caches the new (wrong) credentials, replacing the good ones. We point at `/api/admin/salem/pois` with `logout:wrongpassword` in `xhr.open()`, then `window.location.reload()` to force a fresh prompt on the next admin call. Wrapped in try/catch because some browsers throw on deprecated sync XHR. Documented inline as a known footgun, not a blocker.
4. **Oracle pill is a static placeholder for now.** Phase 9P.10b will wire it to poll `/api/oracle/status` (Salem Oracle on port 8088). The pill's `title` attribute documents this so a future session sees the contract without re-reading the master plan.
5. **Light mode only for the admin tool.** No spec for dark mode in 9P.7. Public web app's dark mode toggle is irrelevant here — admin tool is a different consumer and a different audience (operator-only, dev-side).
6. **Tailwind for styling.** Consistent with the rest of the web app. No new CSS deps.
7. **Stale memory must be refreshed at session start, not session end.** The `project_next_session_priority.md` entry was written after S94 and still listed Privacy Policy drafting as the top priority — but S101 reprioritized in STATE.md and the memory was never updated. Session 102 caught the stale state in the start protocol and refreshed it. Lesson: when STATE.md and a memory entry conflict, STATE.md is more recent and authoritative.

### Files Changed
- `web/src/admin/AdminLayout.tsx` — NEW (~140 lines)
- `web/src/main.tsx` — modified (path-based dispatch)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.7 marked DONE with full notes
- `STATE.md` — Last Updated → S102; new Phase 9P.B status section; TOP PRIORITY → Step 9P.8
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-102-2026-04-08.md` — live conversation log
- `~/.claude/.../memory/project_next_session_priority.md` — stale entry refreshed

### Verification
- `cd web && npx tsc --noEmit` → clean exit, zero errors across the project

### What's next
- **Phase 9P.B Step 9P.8** — POI tree (`react-arborist`). `npm install react-arborist`, create `web/src/admin/PoiTree.tsx`, fetch all 1,720 POIs once via `/api/admin/salem/pois`, group by kind → category → subcategory, render in the left pane (replacing the current placeholder), search bar at top filters by name client-side, soft-deleted POIs hidden by default. The first admin API call from this is when the browser's Basic Auth prompt actually fires.
- After 9P.8: 9P.9 (admin map), 9P.10 (edit dialog), 9P.10b (Salem Oracle integration), 9P.11 (Highlight Duplicates wiring), 9P.13 (Publish wiring), Phase 9P.C (publish loop regenerating bundled `.db` from PG).

---

---


## Session 101: 2026-04-08 — POI Inventory PDF + Phase 9P.A.3 Migration + Phase 9P.6 TypeScript Port + Salem Oracle API Spec (Phase 9P.10b)

### Summary
Four pieces of work in one session, all converging on making the POI Admin Tool actually buildable. **(1) POI inventory PDF** — `tools/generate-poi-inventory-pdf.py` produces a 1,163-page PDF (`docs/poi-inventory-2026-04-08.pdf`, 2.5 MB, gitignored) listing all 1,723 POIs from the bundled `salem-content/salem_content.db` (45 tour + 861 business + 817 narration) with every field of every POI fully written out, organized by category → subcategory. Implementation: Python + reportlab Platypus, 2-column key/value tables, JSON pretty-printing, NULL → '—'. New isolated venv at `tools/.poi-pdf-venv/` (gitignored, fully reversible). **(2) Phase 9P.A.3 — Migrate `tour_pois` and `salem_businesses` to PostgreSQL** — closes the architectural gap that made the admin tool non-functional for two-thirds of POI kinds. Before this commit, `salem_tour_pois` and `salem_businesses` PG tables were empty, so admin PUT/DELETE/move on those kinds returned 404. New `cache-proxy/scripts/import-tour-pois-and-businesses.js` (~270 lines) reads directly from the bundled SQLite via `better-sqlite3` and UPSERTs into PG (UPSERT, not TRUNCATE+INSERT, because six tables FK-reference `salem_tour_pois` for the future Phase 9Q backfill). Type conversions: JSON TEXT → JSONB, INTEGER 0/1 → BOOLEAN, Unix-millis → TIMESTAMPTZ. After migration: tour=45, business=861, narration=814 — 1,720 active POIs all canonical in PG. 9/9 smoke tests pass. The duplicates endpoint jumped from 101 clusters to 550 (top cluster 27 → 54 members) — cross-kind duplicate detection now active. **(3) Phase 9P.6 — TypeScript port of POI category taxonomy** — new `web/src/config/poiCategories.ts` (~535 lines) faithfully mirrors `app-salem/.../ui/menu/PoiCategories.kt` with all 22 categories (17 standard + 5 Salem-specific), all 175+ subtypes, all per-mode visibility flags including `historicTourDefault` from 9P.4a, plus helper functions `freeRoamPrefKey`, `tourPrefKey`, `enabledTagValues`, `findPoiCategory`, and the `PoiLayerId` constant. Cross-reference comments added to BOTH files pointing at each other. The existing `web/src/config/categories.ts` (17 categories, different shape) is left untouched — the two coexist for now, TODO Phase 9C+ unification. `npx tsc --noEmit` clean. **(4) Salem Oracle API integration spec (Phase 9P.10b)** — user surfaced the cross-project capability at session end: the Salem sibling project exposes a dev-side LLM-backed API at `http://localhost:8088` (gemma3:27b on the operator's RTX 3090, backed by 3,891 facts + 4,950 primary source chunks + 63 historical POIs + 202 newspaper articles) that the admin tool can call to compose, revise, summarize, or expand POI descriptions. Read the full Oracle contract at `~/Development/Salem/docs/oracle-api.md` (492 lines, owned by Salem). Added a new Step 9P.10b to the master plan specifying the integration: typed `oracleClient.ts`, "Generate with Salem Oracle" button in the Narration and General tabs of the edit dialog, sub-dialog with prompt + reset + generate, 5-15s spinner, primary source citations surfaced in UI, insert / iterate workflow per the Oracle's 6-turn rolling context, client-side audit log per accepted generation. Critical conceptual point: Salem's 63 historical POIs are DISTINCT from LocationMapApp's 1,720 POIs — same geography, different datasets — so the Oracle is editorial brain only (read from Salem, write to LocationMapApp PG). New memory entry `reference_salem_oracle_api.md` so future sessions don't have to re-read the Salem doc.

### Decisions
1. **PDF source = bundled SQLite, not PG.** The bundled `salem-content/salem_content.db` is the most inclusive single source — narration_points has 817 rows there vs 814 in PG (3-row delta from S98 dedup, the bundled DB is more inclusive). Sourcing from one place is also simpler than mixing PG + bundled.
2. **Skip psycopg2.** Don't need PG access from the PDF script if I source from bundled SQLite. sqlite3 is built-in to Python; only reportlab needs to be installed.
3. **Isolated venv, not system pip install.** Python 3.12 on Ubuntu has PEP 668; venv is the right pattern. Created `tools/.poi-pdf-venv/`, gitignored, fully reversible.
4. **Use bundled SQLite directly via `better-sqlite3` for the migration**, not the SQL file parsing approach S98 used. Cleaner, less error-prone, and `better-sqlite3` was already a cache-proxy dep.
5. **UPSERT, not TRUNCATE+INSERT, for the migration.** Six tables FK-reference `salem_tour_pois` (`salem_historical_figures`, `salem_historical_facts`, `salem_timeline_events`, `salem_primary_sources`, `salem_tour_stops`, `salem_events_calendar`) — even though those references are currently NULL, PG refuses TRUNCATE without CASCADE. UPSERT avoids the issue entirely and is fully idempotent.
6. **Public read endpoints in `lib/salem.js` were already updated in S99** to filter `deleted_at IS NULL` for tour_pois and businesses — so the migration didn't need any further read-side change.
7. **For Phase 9P.6, keep existing `categories.ts` untouched and create a new `poiCategories.ts`.** Two different shapes (`tagMatches` grouped by key vs flat `tags: string[]`), two different consumers (public web app vs Salem admin tool). Don't risk a behavior change in the public web app. TODO Phase 9C+ unification via codegen or shared JSON config.
8. **Salem Oracle API integration is editorial-only.** Salem corpus is read-only from this API; admin tool only writes to LocationMapApp's PG. Explicitly do NOT try to write to Salem from the admin tool; that's against the API contract and Salem's editorial workflow.
9. **The Oracle's `current_poi_id` field accepts any string.** When calling from the LocationMapApp admin tool, pass the LocationMapApp POI id directly (not a Salem catalog id) — the Oracle uses it for context pinning regardless of source.
10. **5-15s latency is unavoidable.** The Oracle runs on a single RTX 3090 with one model loaded; concurrent requests serialize naturally. Don't fight it — show a spinner of at least 5s expected duration, set client timeout ≥60s, and queue follow-ups instead of firing parallel.

### Files Changed
- `tools/generate-poi-inventory-pdf.py` — NEW (~280 lines, Python + reportlab)
- `tools/.poi-pdf-venv/` — NEW (gitignored, isolated venv with reportlab 4.4.10)
- `docs/poi-inventory-2026-04-08.pdf` — NEW (gitignored, 1,163 pages, 2.5 MB)
- `cache-proxy/scripts/import-tour-pois-and-businesses.js` — NEW (~270 lines)
- `web/src/config/poiCategories.ts` — NEW (~535 lines)
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/menu/PoiCategories.kt` — added cross-reference comment to the data class doc block
- `.gitignore` — added `tools/.poi-pdf-venv/`, `docs/poi-inventory*.pdf`
- `STATE.md` — TOP PRIORITY rewritten for Phase 9P.B Step 9P.7; added Salem Oracle API integration section
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — Step 9P.A.3, Step 9P.6, Step 9P.10b all added/marked DONE
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-101-2026-04-08.md` — live conversation log
- `~/.claude/.../memory/reference_salem_oracle_api.md` — NEW memory entry (cross-session persistence)
- `~/.claude/.../memory/MEMORY.md` — index updated

### Verification
- **PDF:** `pdfinfo` confirms 1,163 pages, PDF v1.4, title set, valid metadata. `pdftotext` spot-checks at page 1 (cover, "1723 total") and page 100 (mid-doc, "The Peabody Diner" cafe in Peabody) render cleanly.
- **9P.A.3 migration:** PG row counts verified (tour=45, business=861, narration=814 → 1,720 active). Sample rows (`witch_trials_memorial`, `hex`) verified with JSONB arrays parsed cleanly. 9/9 smoke tests against admin endpoints all pass — list, GET single, PUT update (was 404 before), DELETE/restore round-trip, duplicates endpoint surfaces 550 clusters (up from 101).
- **9P.6 TypeScript port:** `cd web && npx tsc --noEmit` → no errors across the entire web project. The new `poiCategories.ts` compiles cleanly with the existing types and doesn't break any existing imports.
- **Oracle API spec:** read the full 492-line Salem doc; Phase 9P.10b spec covers all 6 endpoints used by the admin tool plus the constraints, latency, conceptual gotchas, and operator UI affordances.

### Build & Deploy
- PDF generation: `tools/.poi-pdf-venv/bin/python tools/generate-poi-inventory-pdf.py` (re-run anytime, idempotent)
- Migration: `node cache-proxy/scripts/import-tour-pois-and-businesses.js` (idempotent UPSERT)
- TypeScript verification: `cd web && npx tsc --noEmit`
- Three commits pushed: `8271e6e` (PDF), `7f81b0d` (9P.A.3), `9e25c61` (9P.6)

### Open Items for Session 102
1. **Phase 9P.B Step 9P.7** — `/admin` route in `web/src/App.tsx` + `web/src/admin/AdminLayout.tsx` shell with header (Highlight Duplicates / Publish / Logout / **Oracle status pill**), 2-pane layout
2. **Step 9P.8** — POI tree via `react-arborist` (the user's left-side tree list requirement); needs `npm install react-arborist`
3. **Step 9P.9** — map view with `react-leaflet` (already a dep)
4. **Step 9P.10** — edit dialog with tabbed attribute groups (`react-hook-form`, `@headlessui/react`)
5. **Step 9P.10b — Salem Oracle "Generate with AI" integration** — typed client + UI buttons + sub-dialog (per master plan spec)
6. **Phase 9P.C — publish loop** (regenerate `salem-content/salem_content.db` from PG so APK builds pick up admin edits)
7. **OMEN-002 history rotation** still pending operator action (rotate DB password via `ALTER USER`, regenerate `OPENSKY_CLIENT_SECRET` via portal, set real `ADMIN_PASS` in `cache-proxy/.env`)
8. **Long-deferred carryover:** NOTE-L013 voice debug cleanup, 9T.9 walk simulator verification, NOTE-L014 OMEN-008 Privacy Policy drafting

## Session 100: 2026-04-08 — Phase 9P.A COMPLETE (9P.4a Per-Mode Visibility Schema + 9P.5 Duplicates Detection)

### Summary
Two small steps that finish Phase 9P.A (Backend Foundation). **Phase 9P.4a (Per-mode category visibility schema)** added a `historicTourDefault: Boolean = false` field to the `PoiCategory` data class in `app-salem/.../ui/menu/PoiCategories.kt`, plus helper getters `freeRoamPrefKey` and `tourPrefKey` codifying the two-prefKey naming convention (`<existing prefKey>` for free-roam, `<existing prefKey>_tour` for tour mode). Six categories opted in to historic tour mode with inline justification: CIVIC (1692 courthouses), WORSHIP (Salem Village Church central to narrative — deliberate split where free-roam keeps it OFF as clutter but tour mode turns it ON), TOURISM_HISTORY (heart of historic content), WITCH_SHOP (modern witch shops fit the atmosphere), GHOST_TOUR (historic-tour-adjacent), and HISTORIC_HOUSE (witch trial houses). Three categories go the opposite way (free-roam ON, tour OFF) — ENTERTAINMENT, PSYCHIC, HAUNTED_ATTRACTION — because their modern flavor distracts from the 1692 narrative. The actual SharedPreferences migration code is deferred to Phase 9R when historic tour mode ships; 9P.4a only ships the schema. The migration plan is documented in the master plan as a Phase 9R rollout note. `./gradlew :app-salem:compileDebugKotlin` BUILD SUCCESSFUL. **Phase 9P.5 (Duplicates detection endpoint)** added `GET /admin/salem/pois/duplicates?radius=15` to `cache-proxy/lib/admin-pois.js` (~140 new lines). Architecture: bbox-prefiltered self-join across all three POI tables via a `WITH all_pois AS (...UNION ALL...)` CTE; Haversine distance computed in pure SQL (no PostGIS); JS-side cluster grouping via union-find with path compression; transitive clustering (chained POIs collapse into one cluster — correct semantics for duplicate detection). Default radius 15m, max 100m (silently capped above 100), `radius=0` and `radius=banana` rejected with 400. Excludes soft-deleted rows. 7/7 smoke tests pass on the live ~1,696-POI Salem dataset in ~500ms. Notable real-data findings: top 27-member cluster at 15m radius chains POIs around the corner of Essex Street in downtown Salem (Pamplemousse, Tipples and Mash Tours, Witch City Ink, and 24 more — a known dense block where Destination Salem and OSM imports both saw the same shops); a 3-POI cluster at exact identical coordinates (42.5235097, -70.8952337) shows "Bluebikes", "St. Peter's Church", and "Downtown Salem" sharing one location, a clear import default that the admin UI should help the operator fix. **Phase 9P.A — Backend Foundation — COMPLETE.** Phase 9P.B (Admin UI in `web/`) starts next session.

### Decisions
1. **Schema-only for 9P.4a, no migration code yet.** The master plan says "schema". Adding the data shape (the new field + helpers + doc comment) is what 9P.4a delivers; the actual SharedPreferences migration to populate `<prefKey>_tour` keys and the mode-aware reads are Phase 9R deliverables. This keeps 9P.4a small (~30 lines of Kotlin total) and doesn't ship dead code.
2. **`historicTourDefault = true` only on 6 categories, implicit false on the other 16.** Matches the existing project house style where `defaultEnabled = true` only appears on categories that need it. Adding `historicTourDefault = false` to 16 categories would be verbose churn that obscures intent.
3. **WORSHIP gets a deliberate free-roam/tour split.** Free-roam keeps it OFF (clutter — there are many places of worship in any city, not all 1692-relevant). Tour mode turns it ON (Salem Village Church, First Church of Salem, etc. are central to the witch trials narrative). This is exactly the kind of category where the per-mode flag earns its keep.
4. **Bbox prefilter on the duplicates self-join.** Without it, the join is O(N²) ≈ 2.9M Haversine evaluations on the ~1,696-POI dataset. The lat prefilter (`ABS(a.lat - b.lat) <= radius/111000`) is exact since 1° latitude is uniformly ~111 km. The lng prefilter (`ABS(a.lng - b.lng) <= radius/60000`) uses 60000 m/degree as a conservative lower bound that overshoots in Salem (~82000 m/degree at 42.5°N) — overshoots without affecting correctness, just doesn't prune as much. Net result: ~500ms query latency.
5. **Transitive clustering via union-find, not strict pairwise.** When POI A is within 15m of B, B within 15m of C, and C within 15m of D, all four collapse into one cluster of 4 — even if A and D are 60m apart. This is the correct semantics for duplicate detection in the context of this admin tool: chained dense POIs in one tight area are likely the same physical thing fragmented across imports. Strict pairwise would miss the chain and fragment the cluster into pairs.
6. **Members within each cluster sorted by distance from centroid asc; clusters sorted by member_count desc.** Biggest dupes first in the response so the admin UI can lead with the worst offenders. Members within a cluster surface the centroid-closest first so the operator sees the "anchor" POI before the outliers.
7. **One commit shipping Phase 9P.A complete.** User chose "one commit" at session start. 9P.4a + 9P.5 are conceptually distinct but they're the last two steps of a coherent unit (Backend Foundation). Cleaner history to land them together as "Phase 9P.A complete" than to split.

### Files Changed
- `app-salem/src/main/java/com/example/wickedsalemwitchcitytour/ui/menu/PoiCategories.kt` — added `historicTourDefault` field + helper getters + doc comment block; opted 6 categories into historic tour mode with inline justification comments
- `cache-proxy/lib/admin-pois.js` — added `GET /admin/salem/pois/duplicates` endpoint (~140 new lines)
- `cache-proxy/server.js` — banner updated to advertise the new route
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — 9P.4a + 9P.5 marked DONE with implementation notes; Phase 9P.A marked COMPLETE
- `STATE.md` — TOP PRIORITY rewritten for Session 101 (Phase 9P.B Admin UI in `web/`)
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-100-2026-04-08.md` — live conversation log

### Verification
- **9P.4a:** `./gradlew :app-salem:compileDebugKotlin` → BUILD SUCCESSFUL in 24s. Only an unrelated pre-existing deprecation warning on `setBuiltInZoomControls` in `SalemMainActivity.kt:611`. `grep historicTourDefault` confirms 6 `historicTourDefault = true` lines on the right categories plus the data class default at line 48.
- **9P.5 smoke tests** (7/7 pass): no auth → 401; default radius=15m → 200, 101 clusters with top cluster of 27 members near downtown Essex Street; radius=30m → 84 clusters; radius=100m → 53 clusters; radius=200 silently capped at 100; radius=0 → 400; radius=banana → 400
- **Real-data spot check:** the duplicates endpoint surfaced a real cleanup target — three POIs at exact identical coordinates (Bluebikes / St. Peter's Church / Downtown Salem) that were clearly imported with default coordinates and need operator attention. This is exactly the use case the duplicates endpoint exists for.
- **Performance:** full query against 1,696 POIs returned in ~500ms with the bbox prefilter. Without prefilter would be O(N²) ≈ 2.9M Haversine evaluations.

### Build & Deploy
- Kotlin compile: `./gradlew :app-salem:compileDebugKotlin` (no APK rebuild needed for this session — schema-only Kotlin change)
- Proxy restart: `bin/restart-proxy.sh` (now sources `cache-proxy/.env` from S99 cleanup; admin auth still uses smoke-test password `salem-tour-9P3-test`)
- Banner now shows: `AdminPOI: GET /admin/salem/pois?kind=tour|business|narration, GET /admin/salem/pois/duplicates?radius=, GET/PUT/DELETE /admin/salem/pois/:kind/:id, POST .../move, POST .../restore (Basic Auth)`

### Open Items for Session 101
1. **Phase 9P.B starts** — Phase 9P.A (Backend Foundation) is complete; Admin UI work begins in `web/`
2. **Step 9P.6** (next concrete code task) — hand-port the 22-category POI taxonomy from `PoiCategories.kt` to `web/src/config/poiCategories.ts` with the same shape (including the new `historicTourDefault` field from 9P.4a)
3. **Step 9P.7** — `/admin` route in `web/src/App.tsx`, `web/src/admin/AdminLayout.tsx` shell with header/tree/map panes
4. **OMEN-002 history rotation** still pending operator action (rotate DB password + OpenSky secret via portal)
5. **Real ADMIN_PASS** still needs to be set in `cache-proxy/.env` (currently smoke-test value)
6. **Long-deferred carryover:** NOTE-L013 voice debug cleanup, 9T.9 walk simulator verification, NOTE-L014 OMEN-008 Privacy Policy drafting

---

## Session 99: 2026-04-08 — Phase 9P.3 + 9P.4 DONE (Admin Auth + Admin POI Endpoints) + OMEN-002 File-Side Cleanup

### Summary
Three things in one session, all on a single architectural arc: harden the cache-proxy admin surface, then build the first write endpoints on top of it. **Phase 9P.3** added `cache-proxy/lib/admin-auth.js` — a `requireBasicAuth` middleware with constant-time comparison via `crypto.timingSafeEqual` (length-padded so even length comparison can't leak info), reading `ADMIN_USER`/`ADMIN_PASS` from env at request time, returning `401 + WWW-Authenticate: Basic realm="LocationMapApp Admin"` on failure and `503` if env vars are unset. Mounted globally on `/admin/*` in `server.js` and applied per-route to `POST /cache/clear` to fix the latent S96 unauthenticated-admin-route bug. 7/7 smoke tests pass. **Phase 9P.4** built `cache-proxy/lib/admin-pois.js` (~290 lines): six endpoints under `/admin/salem/pois/*` covering all three POI kinds (`tour`, `business`, `narration`) with kind-specific field whitelists, JSONB-aware update clause builder, soft-delete semantics (PUT and move refuse against deleted rows; DELETE/restore distinguish 404 vs 409), and lat/lng range validation. Required an in-scope schema migration: only `salem_narration_points` had `deleted_at` going in, so added the column + active partial indexes to `salem_tour_pois` and `salem_businesses`, applied the ALTER TABLE live, updated `salem-schema.sql` with `IF NOT EXISTS` clauses for reproducibility, and updated `lib/salem.js` public reads (`/salem/pois`, `/salem/pois/:id`, `/salem/businesses`) to filter `deleted_at IS NULL` so soft delete is functional end-to-end (otherwise it would be theatrical — admin "deletes" a POI but the public app still shows it). 24/24 smoke tests pass. **OMEN-002 file-side cleanup** done as a precondition: lifted `DATABASE_URL`, `OPENSKY_CLIENT_ID`, `OPENSKY_CLIENT_SECRET` out of `bin/restart-proxy.sh` into a new gitignored `cache-proxy/.env`; rewrote the script to source `.env` via `set -a; source ...; set +a`; created tracked `cache-proxy/.env.example` template; added `cache-proxy/.env`, `.env`, `.env.local` to `.gitignore`. Both old credential strings confirmed absent from the tracked file via grep. Credentials are still in git history — full closure requires operator-side rotation flagged for Session 100.

### Decisions
1. **OMEN-002 cleanup before 9P.3, not after.** User chose option (A): clean up the existing leaked credentials before introducing the new `ADMIN_USER`/`ADMIN_PASS` env vars, so the new vars don't perpetuate the bad pattern. File-side migration was straightforward (move secrets, gitignore, script sources `.env`); credential rotation is left for the operator because OpenSky requires a portal login and DB password rotation is a one-line `ALTER USER` the operator should run themselves.
2. **Constant-time comparison with length padding.** `crypto.timingSafeEqual` throws on length mismatch, which would leak length information about the expected credentials. Padded both sides to `max(len)` with zero-fill, then verified equal lengths separately. Standard pattern, but worth doing right.
3. **Read env vars at request time, not module load.** Means the operator can change `ADMIN_USER`/`ADMIN_PASS` in `cache-proxy/.env`, restart, and the new values take effect without any module-cache concerns. Also enables future runtime rotation if needed.
4. **`/cache/stats` left public, only `/cache/clear` gated.** Stats is read-only telemetry; clear is destructive. Different blast radius, different access policy.
5. **Schema migration for `deleted_at` was in-scope, not scope creep.** The 9P.4 master plan calls for soft delete on all three kinds. Only `salem_narration_points` had the column. Adding it to the other two (with `IF NOT EXISTS` for idempotency) is a precondition for the master plan task, not a tangent.
6. **Public read endpoints in `lib/salem.js` had to filter `deleted_at IS NULL`.** Without this, soft delete is theatrical — admin "deletes" something but the user-facing app still shows it. Three-line change, behavior is what users would expect from soft delete. Flagged in the session log as a small scope expansion beyond the literal master plan task.
7. **Whitelisted partial updates, not PUT-replace semantics.** "Full update" in the master plan is interpreted as "update any field you provide, leave others alone". Safer for an admin tool — accidental missing field doesn't clobber existing data. Whitelist enforced per kind so non-existent or system-managed columns (`created_at`, `updated_at`, `deleted_at`) cannot be written via the API.
8. **JSONB-aware update clause builder.** Seven JSONB columns across the three tables (`subcategories`, `tags`, `related_figure_ids`, etc.). Auto-stringify and cast via `$N::jsonb`. Centralized in `buildUpdateClause` so each endpoint stays small.
9. **Soft delete semantics: PUT/move refuse against deleted rows.** Forces a deliberate "restore first" workflow rather than letting an admin update a tombstoned POI by accident. DELETE returns 409 (not 404) when the row exists but is already deleted, distinguishing it from "does not exist".
10. **GET single still returns deleted rows; GET list excludes them by default.** Admin workflows often need to inspect a tombstone before deciding to restore. List view defaults to "active only" but supports `?include_deleted=true` for cleanup workflows.

### Files Changed
- `.gitignore` — added `cache-proxy/.env`, `.env`, `.env.local`
- `bin/restart-proxy.sh` — sources `cache-proxy/.env`, no hardcoded credentials
- `cache-proxy/.env.example` — NEW (placeholder template)
- `cache-proxy/lib/admin-auth.js` — NEW (~140 lines, Basic Auth middleware)
- `cache-proxy/lib/admin.js` — `/cache/clear` gated by `requireBasicAuth`
- `cache-proxy/lib/admin-pois.js` — NEW (~290 lines, six endpoints)
- `cache-proxy/lib/salem.js` — public reads filter `deleted_at IS NULL`
- `cache-proxy/salem-schema.sql` — `deleted_at` added to tour_pois and businesses with active indexes
- `cache-proxy/server.js` — admin auth wired in, `admin-pois` module registered, banner updated
- `STATE.md` — TOP PRIORITY rewritten for Session 100 (9P.4a + 9P.5)
- `WickedSalemWitchCityTour_MASTER_PLAN.md` — 9P.3 + 9P.4 marked DONE with bonus notes
- `SESSION-LOG.md` — this entry
- `docs/session-logs/session-099-2026-04-08.md` — live conversation log

### Verification
- **9P.3 smoke tests** (7/7 pass): `GET /admin/ping` no auth → 401 + WWW-Authenticate; wrong pass → 401; wrong user → 401; correct creds → 200; `POST /cache/clear` no auth → 401 (was 200 before — latent bug fixed); correct creds → 200; `GET /cache/stats` no auth → 200 (intentionally public)
- **9P.4 smoke tests** (24/24 pass): list w/o kind → 400; list narration limit=3 → 200 with 3 rows; list no auth → 401; GET single → 200; GET nonexistent → 404; GET bad kind → 400; PUT description+priority → 200, updated_at refreshed; PUT lat=999 → 400; PUT empty body → 400; PUT non-whitelisted-only → 400 (whitelist works); POST move → 200 with from/to JSON; POST move bad lng → 400; DELETE → 200; DELETE again → 409; PUT against deleted → 409; GET single still returns deleted row; list include_deleted=false → count=0; list include_deleted=true → count=1; restore → 200; restore again → 409; move back to original coords → 200; PUT priority back → 200; PUT description=null (clean restore) → 200, NULL in DB
- **Test row** (`andrew_safford_house`) verified back to bundled-SQL state via direct PG query: `description=NULL, priority=2, wave=1, lat=42.5230254, lng=-70.8909076`
- **Live PG migration** applied: both new `deleted_at` columns and active indexes confirmed via `information_schema.columns`
- **Both old credential strings** (`fuckers123`, `6m3uBQ5HXwSzJeLemRJK12G8Ux4L5veR`) confirmed absent from `bin/restart-proxy.sh` via grep

### Build & Deploy
- Schema migration: `psql -U witchdoctor -d locationmapapp -h localhost -c "ALTER TABLE salem_tour_pois ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ; ..."` (idempotent)
- Proxy startup: `bin/restart-proxy.sh` (now sources `cache-proxy/.env`). New banner lines: `Admin: GET /cache/stats, POST /cache/clear (Basic Auth), GET /admin/ping (Basic Auth)` and `AdminPOI: GET /admin/salem/pois?kind=..., GET/PUT/DELETE /admin/salem/pois/:kind/:id, POST .../move, POST .../restore (Basic Auth)` and `Admin auth: configured`

### Open Items for Session 100
1. **OMEN-002 history rotation** — needs operator action: rotate DB password via `ALTER USER witchdoctor WITH PASSWORD '<new>';`, rotate `OPENSKY_CLIENT_SECRET` via OpenSky web portal, then update `cache-proxy/.env`
2. **Set real `ADMIN_PASS`** in `cache-proxy/.env` (currently the smoke-test value `salem-tour-9P3-test`)
3. **Phase 9P.4a** — Per-mode category visibility schema (small, schema-only)
4. **Phase 9P.5** — Duplicates detection endpoint (one endpoint, ~50 lines)
5. **Carryover** — NOTE-L013 cleanup, 9T.9 walk simulator verification, NOTE-L014 OMEN-008 Privacy Policy drafting (still on the books, still blocking RadioIntelligence Salem ingest)

---

## Session 98: 2026-04-08 — Phase 9P.A.1 + 9P.A.2 DONE (Schema + Importer)

### Summary
First code session of the Phase 9P series. Added the new `salem_narration_points` PostgreSQL table (42 columns, 7 indexes — mirroring the bundled SQL's full column set including merchant_tier and ad_priority for the future advertising business) and wrote a one-shot importer at `cache-proxy/scripts/import-narration-points.js` that brings 814 narration points out of the bundled `salem_content.sql` and into PostgreSQL. PostgreSQL is now the canonical source of truth for editable Salem narration points, exactly per the Session 96 architectural decision. Wave breakdown verified: W1=112, W2=85, W3=109, W4=508 — exact match to the bundled SQL (3 fewer than STATE.md's stale 817; bundled SQL is the truth). Three honest investigation iterations were needed to land cleanly: (1) the schema initially missed 12 columns from the bundled SQL including the merchant fields, fixed by dropping the empty table and re-running; (2) the line-based extractor was breaking on multi-line INSERTs where ~7 rows have literal newlines inside the `hours` field, fixed by accumulating until reaching `;` outside a string literal with proper escape-pair handling; (3) the importer's idempotency was verified by TRUNCATE + re-run. Stopped at the 9P.A.1+9P.A.2 milestone per the conservative recommendation rather than racing through 9P.3-9P.5 in the same session.

### OMEN-002 finding (predates Session 98, flagged for cleanup before 9P.3)
While hunting for the DATABASE_URL, found `bin/restart-proxy.sh:19` contains the PostgreSQL password as a hardcoded credential, and line 22 contains `OPENSKY_CLIENT_SECRET`. Both files are tracked in git. This is an OMEN-002 violation that predates this session. Recommended cleanup: rotate both credentials (since they're in git history), move to a gitignored `cache-proxy/.env`, have the script `source` it. Should be done **before Phase 9P.3** (which adds `ADMIN_USER` / `ADMIN_PASS` to the env model) so the new vars don't perpetuate the bad pattern. Surfaced to user, awaiting decision on rotation timing.

### Decisions
1. **Schema mirrors bundled SQL exactly.** Initial schema was missing 12 columns including `merchant_tier` and `ad_priority` (foundation fields for the future advertising business). Caught the gap during data inspection, dropped the empty table, expanded the schema, re-ran the migration. Net result: PostgreSQL preserves 100% of bundled-SQL data fidelity. 42 columns total. The `merchant_tier`/`ad_priority` fields ship NULL/0 today and become editable in Phase 17 — schema is ready ahead of time.
2. **Importer reads from bundled SQL, not JSON.** The bundled SQL is the most complete source — JSON files have raw POIs without narrations. better-sqlite3 (already a cache-proxy dep) loads extracted INSERTs into in-memory SQLite, Node reads rows, UPSERTs to PostgreSQL. No fragile regex parsing of SQL.
3. **Multi-line INSERT extractor.** ~7 rows have literal newlines in `hours` (Satanic Temple Salem Art Gallery, etc). The extractor now accumulates lines until it sees `;` at end-of-line outside a string literal. Single-quote escape pairs (`''`) are properly skipped.
4. **Stopped at the 9P.A.1+9P.A.2 milestone.** Could have pushed through to 9P.5 but the schema migration + canonical-source establishment is the load-bearing piece of Phase 9P.A. Better to commit + push that cleanly than to race through three more endpoints in the same session.
5. **STATE.md was stale by 3 rows.** STATE.md said 817 narration points; bundled SQL has 814. Bundled SQL is the truth. Updated STATE.md.

### Files Changed
- `cache-proxy/salem-schema.sql` — added `salem_narration_points` table (42 cols, 7 indexes) between `salem_businesses` and `salem_historical_figures`
- `cache-proxy/scripts/import-narration-points.js` — NEW (~290 lines, idempotent UPSERT importer with multi-line SQL parsing)
- STATE.md — updated for 9P.A.1+9P.A.2 done, next priority is 9P.3
- SESSION-LOG.md — this entry
- `docs/session-logs/session-098-2026-04-08.md` — live conversation log

### Verification
- `\d salem_narration_points` shows all 42 columns and 7 indexes
- `SELECT count(*) FROM salem_narration_points` = 814
- Wave breakdown: W1=112, W2=85, W3=109, W4=508 (matches bundled SQL exactly)
- 29 distinct categories, 10 distinct data_source values (preserves multi-source dedup provenance)
- Spot-check: Salem Witch Museum, 1692 Before and After both present with expected attributes
- 675 (83%) have descriptions, 324 (40%) have phones, 393 (48%) have websites
- Importer is idempotent: re-run after TRUNCATE produced identical results

### Build & Deploy
- Schema migration: `psql -U witchdoctor -d locationmapapp -h localhost -f cache-proxy/salem-schema.sql` (idempotent, only added new table)
- Importer: `DATABASE_URL=postgres://... node cache-proxy/scripts/import-narration-points.js` (run twice — first run to surface schema gaps, second run after fix to land cleanly)

### Open Items for Session 99
1. **OMEN-002 cleanup decision** — rotate DATABASE_URL password + OPENSKY_CLIENT_SECRET, move to gitignored `.env`, BEFORE adding ADMIN_USER/ADMIN_PASS in 9P.3. Or accept the existing exposure and only fix the new vars. User decision needed.
2. **Phase 9P.3** — HTTP Basic Auth middleware in `cache-proxy/lib/admin-auth.js`, mount on `/admin/*`, lock down `/cache/clear`
3. **Phase 9P.4** — Admin POI write endpoints (GET/PUT/DELETE/MOVE under `/admin/salem/pois/*`)
4. **Phase 9P.4a** — Per-mode category visibility schema (two prefKeys per category, free-roam vs tour-mode defaults)
5. **Phase 9P.5** — Duplicates detection endpoint
6. **Carryover** — NOTE-L013 cleanup, 9T.9 walk simulator verification, OMEN-008 Privacy Policy drafting (deferred)

---

## Session 97: 2026-04-08 — Phase 9Q + 9R Added; Hybrid Historic Tour Model Committed

### Summary
Planning session, no code. Started with the goal of kicking off Phase 9P.1 (`salem_narration_points` table), but a clarifying question from the user about the multipass narration columns surfaced a much larger architectural picture that needed to be designed before code touched the schema. User clarified: Pass1/2/3 are POI engagement passes (a separate concept), and the historical narrative content for the historic tour mode comes from a separate stream (`~/Development/Salem`). A spawned Explore agent confirmed the survey from Session 96 plus added new findings: Salem domain entities use `building_id` (not POI ID), historical FK columns in PostgreSQL are all currently NULL, and Salem's session 044 added a 202-article daily newspaper corpus with TTS-ready `tts_full_text` and `events_referenced` join fields. User confirmed: the building→POI translation layer must live on the LocationMapApp side (in `salem-content/`), Salem has no knowledge of POIs by design. Drafted a three-phase architecture (9P additions + new 9Q + new 9R), got user approval for the hybrid historic tour model, and inserted everything into the master plan. Phase 9P.1 still queued as the immediate next code action — Session 97 ends without any code, Session 98 picks up there.

### Architectural Insights (Session 97)
1. **`building_id` is Salem's spatial unit, not POI.** Salem JSON entities (NPCs, facts, events, primary sources, newspapers) reference historical 1692 buildings. They have no POI field by design. The translation to LocationMapApp POIs must happen on the LocationMapApp side via a curated `building_id → poi_id` lookup table inside `salem-content/`.
2. **Historical FK columns are still NULL.** `salem_historical_figures.primary_poi_id`, `salem_historical_facts.poi_id`, `salem_timeline_events.poi_id`, `salem_primary_sources.poi_id` — none of them have data. The schema is ready, the bridge has just never been built.
3. **NEW content stream: Salem Oracle newspapers.** 202 daily articles, Nov 1 1691 → May 9 1693, ~13.5 hours of TTS-ready content. Schema at `~/Development/Salem/data/json/newspapers/README.md`. **Each article has `events_referenced` (list of anchor/minor event IDs)** — this is the join key. POI ↔ newspaper join chain: POI → event → newspaper article (transitive 2-hop).
4. **Pass1/2/3 are POI engagement passes, NOT historical curriculum.** Multipass columns in `salem_narration_points` are for user-engagement layering ("first visit gets quick teaser, third visit gets primary sources"). They are unrelated to the historical narrative content stream.

### Decisions
1. **Phase 9P stays focused on POI management** — admin tool, not bloated with bridge work or newspaper ingest. Two small additions only: 9P.4a (per-mode category visibility schema) and 9P.10a (read-only "Linked Historical Content" tab in edit dialog).
2. **NEW Phase 9Q — Salem Domain Content Bridge.** 7 steps. Imports 425 buildings, builds the `salem_building_poi_map` join table (many-to-many, with link types: exact / memorial / representative / nearby), drafts auto-mappings via name + geo proximity (operator manually curates ~50-80 expected mappings out of 425 buildings), imports 202 newspaper articles, backfills historical FKs by graph traversal, adds a Building Map admin panel as a new view in the Phase 9P admin tool.
3. **NEW Phase 9R — Historic Tour Mode (App-Side).** 7 steps. Hybrid tour model: user picks a chapter (5-7 chapters covering the 1692 timeline), each chapter is a curated route through 4-8 POIs in chronological story order, content fires by GPS proximity but in guaranteed-narrative order. Newspaper articles play as long-form TTS readings (~4 min each, one per chapter, not all 202). Per-mode category visibility (free-roam keeps current toggles, historic tour gets restrictive defaults). Ambient narration fully suppressed in tour mode. Pre-rendered audio deferred to v1.1 (use on-device TTS for v1).
4. **Hybrid model selected over spatial-only or temporal-only.** User confirmed. Pure spatial breaks storytelling; pure temporal feels like a forced march; hybrid keeps walking agency while locking narrative coherence at the chapter level.
5. **Phase 9P.1 + 9P.2 are unaffected by all of this.** They migrate `narration_points` to PostgreSQL, which is the prerequisite to everything else. Session 98 starts there.
6. **Total runway for full vision:** ~9-12 sessions across 9P + 9Q + 9R. Salem 400+ launch deadline is September 1, 2026. Plan: ship 9P first (operator gets the tool), assess 9Q+9R against runway after.

### Concerns Surfaced
- **Scope is growing fast.** What started as "build a POI editor" is now POI editor + Salem domain bridge + tour mode app feature. Three coherent phases, but ~9-12 sessions instead of ~4-5.
- **The labor in 9Q.3 (manually seeding 50-80 building↔POI mappings) is real.** Auto-suggest by name + geo proximity helps but doesn't eliminate the need for human review.
- **13.5 hours of newspaper TTS content is too much to play in a single tour.** Curate hard — one article per chapter, not all 202. Future enhancement: "next article" button for users who want more.
- **Per-mode category visibility migration is sensitive** — first-launch users get new tour-mode prefs initialized to defaults; document this in 9R rollout notes.

### Master Plan Updates
- Phase 9P.4a added (per-mode category visibility schema, between 9P.4 and 9P.5)
- Phase 9P.10a added ("Linked Historical Content" tab, between 9P.10 and 9P.11)
- Phase 9P "Out of scope" updated to reference Phase 9Q and 9R
- NEW Phase 9Q section inserted (7 steps + rationale + cross-project dependency note + out-of-scope list)
- NEW Phase 9R section inserted (7 steps + rationale + pre-requisites + out-of-scope list, hybrid model spec)
- TOC updated with both new phases

### State Updates
- STATE.md — TOP PRIORITY rewritten to point at Session 98 / Phase 9P.1 with the three-phase expanded vision documented
- WickedSalemWitchCityTour_MASTER_PLAN.md — Phases 9Q + 9R added, Phase 9P expanded
- SESSION-LOG.md — this entry

### Build & Deploy
None. Planning session.

### Open Items for Session 98
1. **TOP PRIORITY** — Phase 9P.1: Add `salem_narration_points` table to `cache-proxy/salem-schema.sql`, run migration against local PostgreSQL
2. Phase 9P.2: Write `cache-proxy/scripts/import-narration-points.js`, verify row count matches bundled SQL (~814)
3. Carryover (low priority): NOTE-L013 cleanup (delete VoiceAuditionActivity), 9T.9 walk simulator verification, OMEN-008 Privacy Policy drafting (deferred)

---

## Session 96: 2026-04-08 — POI Admin Tool Planning (Phase 9P added to master plan)

### Summary
Planning session, no code. User redirected away from RadioIntelligence Privacy Policy work and toward a web-based POI admin tool as the foundation for the eventual merchant advertising business. Spawned an Explore agent for a thorough survey of the existing POI infrastructure (PostgreSQL `salem_*` tables, cache-proxy endpoints, web app, sync mechanism, audit/admin precedent). Critical data archaeology finding: the ~814 narration points the user wants to manage live **only** in the bundled `salem-content/salem_content.sql`, generated by `tools/salem-data/generate_narration_sql.py` from JSON files — PostgreSQL has no idea they exist. Surfaced four architectural decisions via AskUserQuestion dialog; all four came back as the recommended options. Wrote the Phase 9P section into the master plan with all 14 steps across three sub-phases (A: migration & backend, B: admin UI, C: publish loop). Updated STATE.md to point at Phase 9P.A as top priority. OMEN-008 Privacy Policy drafting deferred (not abandoned).

### Architectural Decisions Locked (AskUserQuestion dialog)
1. **Data foundation:** Migrate `narration_points` to PostgreSQL (`salem_narration_points` table). PostgreSQL becomes single source of truth. JSON files retire.
2. **Auth:** HTTP Basic Auth via `ADMIN_USER` / `ADMIN_PASS` env vars. Browser handles native prompt. Localnet single-admin model.
3. **Categories:** Hand-port `PoiCategories.kt` (520 lines, 22 categories, 153+ subtypes) to TypeScript at `web/src/config/poiCategories.ts`. Cross-reference both files. Unification deferred.
4. **Sync:** No live OTA. Edit → Publish → rebuild `salem_content.sql` from PostgreSQL → next APK ships fresh.

### Master Plan Updates
- New Phase 9P — POI Admin Tool (Developer Infrastructure), 14 steps across 9P.A / 9P.B / 9P.C
- Phase 9P marked PRIORITY in TOC (line 31)
- Phase 9T PRIORITY marker removed (still IN PROGRESS at 9T.9 verification, but displaced)
- Three new web stack dependencies queued: react-arborist, react-hook-form, @headlessui/react

### Out of Scope for v1 (deferred)
- Photo upload (GeoInbox integration)
- Merchant accounts and self-service editing
- Audit log and change-tracking (Phase 17 when payments enter)
- Live OTA sync from PostgreSQL to Android
- Multi-user roles
- Categories table in PostgreSQL (vs. duplicated TypeScript)
- Full duplicate-merge UI

### State Updates
- STATE.md — TOP PRIORITY rewritten to point at Phase 9P.A
- WickedSalemWitchCityTour_MASTER_PLAN.md — Phase 9P inserted, TOC updated, 9T priority marker moved
- SESSION-LOG.md — this entry

### Build & Deploy
None. Planning session.

### Open Items for Session 97
1. **TOP PRIORITY** — Phase 9P.1: Add `salem_narration_points` table to `cache-proxy/salem-schema.sql`, run migration
2. Phase 9P.2: Write narration points importer in `cache-proxy/scripts/import-narration-points.js`, verify row count matches bundled SQL
3. Coordinate with WWWatcher's parallel shell on JSON `SensorMessage` wire format (deferred from S94/S95, low priority since Privacy Policy is deferred)
4. NOTE-L013 cleanup: delete `app/src/main/java/com/example/locationmapapp/debug/VoiceAuditionActivity.kt` (low priority, can fold into a Phase 9P commit)
5. **Deferred (still on books):** OMEN-008 Privacy Policy drafting (NOTE-L014). Can be drafted asynchronously.

---

## Session 95: 2026-04-07 — Stub (no work performed)

Session opened with the standard start protocol but stalled at "Awaiting User Direction." No work performed, no commits, no decisions. Live log preserved at `docs/session-logs/session-095-2026-04-07.md` for completeness. Session 96 picks up the next-session priority from Session 94's plan.

---

## Session 94: 2026-04-07 — RadioIntelligence Collector Role Intake (Research)

### Summary
Research session, no code. Mapped the portfolio active-scanning layer across WWWatcher Phase 20, RadioLogger, and LocationMapApp Salem in response to the new OMEN-008 directive (NOTE-L014). Identified shared analyzer DNA (WWWatcher session 068 ported analyzers from RadioLogger Kotlin → Rust, 89 unit tests), the consent-model asymmetry between operator-owned vs consenting-proxy collectors, and the unified-hash-space load-bearing legal disclosure requirement. Saved deep architectural picture to memory so next session can pick up immediately. Flagged Privacy Policy drafting (OMEN-008 §8) as the top priority for next session — it is the only currently unblocked work on the RadioIntelligence track and gates everything downstream.

### Cross-Project Intake
- WWWatcher Phase 20 — foundation done session 068; live capture sessions 069–075 in progress in parallel shell
- RadioLogger — field-running on operator test fleet; source designation per NOTE-RL002
- RadioIntelligence — pre-code, constituted by OMEN-008 (2026-04-07); LocationMapApp Salem is the constrained third collector
- Wire format coordination: WWWatcher session 073 plans JSON `SensorMessage` over TCP — natural template for Salem's eventual uploader

### Memory Updates
- NEW: `project_radiointelligence_collector_role.md` — full cross-project picture, three collectors, shared analyzer DNA, OMEN-008 constraints, file map
- NEW: `project_next_session_priority.md` — Privacy Policy drafting top priority with three drafting constraints + 10-element required Privacy Policy structure
- MEMORY.md index updated with both new entries at top, next-session priority bolded

### State Updates
- STATE.md — TOP PRIORITY section added at top
- SESSION-LOG.md — this entry

### Build & Deploy
None. Research session.

### Open Items for Next Session
1. **TOP PRIORITY** — Draft layered Privacy Policy text per OMEN-008 §8 (BLOCKING all RadioIntelligence Salem ingest)
2. Coordinate JSON `SensorMessage` wire format with WWWatcher's parallel shell (sessions 069/073)
3. User visual verification of Session 93 marker icon fix on tablet HNY0CY0W
4. 9T.9 walk simulator end-to-end verification (pending since Session 91)
5. NOTE-L013 cleanup: delete `app/src/main/java/com/example/locationmapapp/debug/VoiceAuditionActivity.kt`

---

## Session 93: 2026-04-06 — Crash Recovery + Marker Icon ANR Fix

### Summary
Recovered crashed Session 92 (committed 169 files / 8,618 insertions as `40778c2`). Diagnosed root cause of POI marker icon ANR on the Salem map: `MarkerIconHelper.labeledDot` allocating 817 bitmaps synchronously on main thread during zoom-bucket transition (17→18), compounded by undersized LRU cache. Applied surgical fix preserving labeled-icon visual.

### Changes Made
- `MarkerIconHelper.kt:37` — `MAX_CACHE_SIZE` 500 → 2000 (fits 817 narration + POI dots + system markers)
- `MarkerIconHelper.kt:165` — added `"shop_retail" to "shopping/gift_shop"` (covers 122 businesses falling through to colored-dot fallback)
- `SalemMainActivity.kt:1080` — viewport filter in `refreshNarrationIcons`, only iterates markers in current `boundingBox`
- Memory: new `feedback_poi_icon_architecture.md` (POI icons are per-app, not server-side)
- Memory: updated `feedback_conversation_log.md` (strict real-time append rule, cited Session 92 incident)

### Bugs Found via ANR Trace
- Pulled `/data/anr/anr_2026-04-06-21-52-14-034` from emulator-5570 (Input dispatching timed out 5010ms)
- Stack: `Bitmap.createBitmap (4 nested) ← labeledDot ← narrationIconForZoom ← refreshNarrationIcons ← onZoom ← MapAnimatorListener.onAnimationUpdate`
- 817 cache misses on first zoom into bucket 3 (zoom ≥18) — name-specific cache key + 500-entry cap = thrash forever

### Build & Deploy
- `:app-salem:assembleDebug` BUILD SUCCESSFUL in 22s
- Installed on tablet HNY0CY0W
- Awaiting user visual verification

---

## Session 92: 2026-04-06 — GPS Narration Bugfixes, Category Voices, Witch Circle Icons

### Summary
Crashed mid-session (context overflow). Recovered and committed in Session 93 as `40778c2`. Live log captured first 3 bug fixes; remaining work reconstructed from git diff.

### Changes Made
- **3 GPS narration bug fixes (real-world Salem walk):**
  1. `SalemMainActivity.kt:1217` — 100m GPS dead zone was skipping narration system entirely; added `updateTourLocation(point)` inside dead zone block (map skips jitter, narration system gets every update)
  2. `SalemMainActivity.kt:1204` — 60s GPS interval too slow at walking speed (90m gaps); added 5s tier when narration active
  3. `SalemMainActivityNarration.kt` — TTS queue race condition: `currentNarration` never cleared on Idle → blocked all future playback. Fix: clear on Idle + retry queue after silence delay
- **12-hour per-POI repeat window** — replaced midnight reset (`narratedToday: Set`) with `narratedAt: Map<String, Long>` + `purgeExpired()`
- **Category voice switching** — new `CategoryVoiceMap.kt`, NarrationManager voice cache + per-segment voice override
- **NarrationPoint schema** — added `voice_override` and `audio_asset` columns
- **Witch-themed circle icons** — `MarkerIconHelper.kt` extended with 100+ category→PNG mappings, `loadCircleIcon()` loads/scales/caches PNGs from `assets/poi-circle-icons/`
- **150 generated icons** across 22 categories via `tools/generate-poi-circle-icons.py` (Stable Diffusion via Forge, DreamShaper XL Turbo)
- **Reach-out tuning** — real GPS reduced from 500m to 150m, distance-first with 50m banding
- **Downtown test route** — `TourRouteLoader.kt` prefers comprehensive street coverage route
- **VoiceTestActivity** — new debug activity for voice audition
- **Extensive NARR-GEO debug logging** throughout pipeline

### Commit
- `40778c2 Session 92: GPS narration bugfixes, category voices, witch circle icons` — 169 files, 8,618 insertions

---

## Session 91: 2026-04-06 — Walk Sim Fixes, Narration Density, 100% Overpass Coverage

### Summary
Fixed 3 walk simulator bugs (interaction, interpolation, session reset), added walk sim narration mode with 3x expanded geofence entry radius and proximity-first reach-out, audited Overpass POI coverage and added 3 missing narration points for 100% coverage (817 total).

### Changes Made
- Walk sim stops on tap/long-press (was overwriting user position every second)
- Route interpolation residual distance bug fixed (short segments not consuming carry-over)
- Narration session resets on walk sim start (was skipping previously-narrated POIs)
- Walk sim mode in NarrationGeofenceManager: 3x entry radius, 200m distance-first reach-out, 500ms delays
- 3 new narration points (2x Salem Fire Dept, 1x Walgreens) — 817 total, 100% Overpass match
- Reverted VoiceAuditionActivity manifest entry (NOTE-L013 cleanup)

---

## Session 90: 2026-04-06 — Dual Narration Tiers (Free/Paid)
- Ultra-short teaser narrations for all 814 POIs (avg 12 words, ~10 sec TTS) — free tier ambient audio
- Promoted 702 existing short_narrations to long_narration — preserved as paid tier content
- Smart generator: description mining, cuisine inference, name parsing, web junk filtering, 8-25 word enforcement
- 100% coverage: all 814 POIs have both short (teaser) and long (full) narrations
- Deployed to Lenovo TB305FU tablet

## Session 89: 2026-04-06 — Universal POI Narrations + Silence Reach-Out
- Wave 4: 508 new narration_points for all remaining businesses (814 total, 100% coverage)
- Smart narration templates by category (cuisine-aware restaurants, atmospheric historic sites, etc.)
- Silence reach-out: 5s quiet → auto-narrates nearest valuable POI within 500m
- Priority narration queue: merchants first (adPriority), then historical value (priority), then distance
- Database rebuilt: 2.7MB, 814 narration_points all with short_narration
- Deployed to Lenovo TB305FU tablet

## Session: 2026-04-06 — Session 87: Tour 1 rename + downtown street route
- Renamed Tour 1: "Salem Essentials" → "Walking Through Salem" (SalemTours.kt, tour_essentials.json, SQL, DB)
- Walk simulator: replaced tour-stop-hopping with 352-point OSRM street-level route through downtown Salem
- New asset: downtown_salem_route.json (PEM → Essex → Common → Liberty → Charter → Derby → back, 5.9km)
- TourRouteLoader: added loadDowntownRoute() + loadStopCoordinates() fallback
- Deployed to Lenovo tablet, pending verification next session

## Session: 2026-04-05 — Session 85: Salem Walking Tour Restructure (planning)

### Work Performed
- Planning session: total restructure of the tour system from linear stop-to-stop to ambient content layer
- Analyzed current assets (45 POIs, 49 figures, 500 facts, geofence/narration systems)
- Delivered architectural recommendations: narration point taxonomy, content queue priority, dialog UI, walking loops, street corridor geofences, GPS mitigation, user fatigue controls
- User approved approach
- Created Phase 9T in master plan (9 steps, 5-8 session estimate) — inserted before Phase 9B as highest priority
- Updated STATE.md with Phase 9T summary

### Next Session
Begin Phase 9T Step 1: geographic boundary definition + POI audit

---

## Session: 2026-04-05 — Session 84: POI defaults, WitchKitty splash, map magnify, OSRM routes, walk simulator

### Work Performed
- POI layer defaults: only Tourism/History, Civic, Entertainment ON by default; added bbox display filtering
- Splash screen: WitchKitty.png image + Creepster horror font "Wicked Salem Witch Tours"
- Map magnify button (x1-x5): visual zoom 1.0-3.0x without changing map zoom level
- Pre-computed OSRM walking routes for witch trials tour: 19 segments + loop-back, 471 points, 8.6km
- Tour route rendering: street-following polylines replace straight lines
- GPS walk simulator: in-app Walk button + /walk-tour debug endpoint, 1.4 m/s walking pace
- Continuous GPS follow: map tracks user position on every update (bypasses 100m dead zone in manual mode)
- Show All POIs debug button: temporarily bypasses layer filter for testing
- Bottom-left button stack: POI toggle, Walk toggle, Magnify toggle

### User Note
Next session: wants to redo the tour in a different way.

## Session: 2026-04-05 ��� Session 82: Tour Hardening Plan — Phase 9A+ Added to MASTER_PLAN

### Work Performed
- Planning session — no code changes
- Deep research: tour engine architecture, offline readiness audit, tablet capabilities, Frida GPS simulation
- Verified Lenovo TB305FU tablet connection (Android 15, Magisk root, Frida 17.9.1, Google TTS)
- Added Phase 9A+ (Tour Hardening & Offline Foundation) to MASTER_PLAN — highest priority, 9 steps
- Key gaps identified: no offline tiles (blank map), no offline routes (OSRM dependency), no offline general POIs
- Decision: Phase 9B deferred. Offline-first + Frida walk simulator on tablet = proven tour foundation

### Files Changed
- WickedSalemWitchCityTour_MASTER_PLAN.md (Phase 9A+ inserted between 9A and 9B)
- STATE.md (updated direction, test device, offline requirements)
- docs/session-logs/session-082-2026-04-05.md (created)

---

## Session: 2026-04-05 — Session 81: Tour Startup UX + Satellite Upgrade + Tour Files

### Work Performed
- Welcome dialog after splash: "Explore Salem" / "Take a Tour" choices
- Enhanced tour selection: themed sections, POI category toggles, async loading spinner
- Long-press teleport feeds tour engine (geofences trigger on teleport)
- Fixed missing tour data in bundled Room DB (3 tours, 58 stops)
- Satellite tiles: USGS zoom 16 → Esri World Imagery zoom 19
- Created 3 self-contained tour JSON files (assets/tours/)
- Full emulator test: welcome → tour select → start → geofence → TTS

### Files Changed
- SalemMainActivity.kt, SalemMainActivityTour.kt, TileSourceManager.kt, salem_content.db
- NEW: assets/tours/tour_essentials.json, tour_explorer.json, tour_grand.json

---

## Session: 2026-04-05 — Session 80: Phase 9A Emulator Test (PASSES)

### Context
Phase 9A code complete and build-verified from Session 79. This session focused on emulator testing.

### Work Performed
- Installed app-salem on Salem_Tour_API34 emulator (port 5570)
- Tested full Phase 9A splash flow: dark purple background + WitchKitty Lottie animation → crossfade to SalemMainActivity → cinematic zoom (US → Salem street level) → USGS satellite tiles + POI labels
- All Phase 9A features pass emulator test
- Identified Forge (AI Art Studio, port 7860) is down — deferred

### Decisions Made
1. Phase 9A passes emulator test — more testing + commit next session
2. First cold launch on emulator showed white splash background (class verification delay) — emulator-only, not a real-device concern

### Open Items
- Phase 9A: Commit pending (user wants more testing next session)
- Tile source picker: not UI-tested via tap this session
- Forge restart needed

---

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

---

# Original v1.5.0–v1.5.50 archive

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
## Session: 2026-03-03c (v1.5.50 — Find Dialog Overhaul)

### Context
Find dialog had three issues: bottom rows clipped (5-row main grid, 8+ row subtype grids overflow screen), 200km distance cap preventing results for rare POIs (zoos, theme parks), and missing useful subtypes. Wrapped grids in ScrollView, removed distance cap, and added 16 new subtypes.

### Changes Made

#### 1. ScrollView Wrapping (MainActivity.kt)
- **Main category grid** (`showFindCategoryGrid`): wrapped `grid` in `ScrollView` with `layoutParams = LayoutParams(MATCH_PARENT, 0, 1f)`; search bar stays fixed above
- **Subtype grid** (`showFindSubtypeGrid`): same ScrollView wrapping pattern
- **Dialog height**: changed from `WRAP_CONTENT` to `(dm.heightPixels * 0.85).toInt()` for both grids
- **Cell height cap**: added `minOf(dp(120), ...)` to prevent oversized cells on small categories

#### 2. Unlimited Distance (server.js)
- Changed scope array from `[50, 200]` to `[50, 200, 1000, 0]`
- When `tryKm === 0`: skips bbox WHERE clause entirely, queries full database ordered by distance
- Verified: zoo search from Beverly MA returned results at 694–1413km (previously 0 results)

#### 3. New Subtypes (PoiCategories.kt + server.js)
- Added `'craft'` to `POI_CATEGORY_KEYS` array in server.js
- 16 new subtypes added to PoiCategories.kt across 6 categories (tags + subtypes lists)
- Total subtypes: 122 → 138

### Test Results
- Build passes, APK installed successfully
- Find main grid: all 18 cells visible, scrolls to Entertainment/Offices row
- Shopping: all 26 subtypes visible with scroll (Pet Stores 6, Electronics 2, Bicycle Shops 3, Garden Centers 9)
- Entertainment: all 18 subtypes visible with scroll (Water Parks, Mini Golf 1, Escape Rooms 1)
- Count badges displaying correctly on all cells
- `/db/pois/find` zoo search: 5 results returned at 694–1413km with `scope_m: 0` (global fallback)

### Files Modified
- `app/.../ui/MainActivity.kt` — ScrollView wrapping + cell height cap (~20 lines changed)
- `app/.../ui/menu/PoiCategories.kt` — 16 new subtypes + tags (~50 lines added)
- `cache-proxy/server.js` — `craft` key + unlimited distance find (~40 lines changed)

---

## Session: 2026-03-03b (v1.5.49 — Automated POI DB Import)

### Context
POI imports from proxy's in-memory cache into PostgreSQL required manually running `node import-pois.js`. Automated this on a 15-minute cycle with delta approach — only imports POIs updated since last successful run. Follows existing `setInterval` timer patterns (aircraft sighting purge, login lockout cleanup).

### Changes Made

#### Automated Delta Import (server.js)
- **State variables**: `lastDbImportTime`, `dbImportRunning` mutex, `dbImportStats` tracking
- **Helper functions**: `derivePoiCategory()`, `extractPoiCoords()` — inlined from `import-pois.js`
- **`runPoiDbImport(manual)`**: filters `poiCache` for entries with `lastSeen >= lastDbImportTime`, upserts in batches of 500, per-batch transactions, timestamp-only-on-success
- **Timer**: `setTimeout` 30s (initial full import), `setInterval` 15min (delta imports), guarded by `if (pgPool)`
- **Endpoints**: `POST /db/import` (manual trigger), `GET /db/import/status` (read-only status)
- **Stats**: `dbImport` object in `/cache/stats` response
- **Startup banner**: shows auto-import enabled/disabled state

### Test Results
- Full import: 178,395 POIs in 217s (~3.6 min)
- Delta import: 1,996 POIs in 9s (only new arrivals)
- `pendingDelta` drops to 0 after import; `totalRuns` counter increments correctly
- DB verified: `SELECT count(*) FROM pois` = 180,059

### Files Modified
- `cache-proxy/server.js` — 185 lines added (import logic, endpoints, stats, banner)

---

## Session: 2026-03-03 (v1.5.48 — Startup/Behavior Tuning)

### Context
Running on BlueStacks (Washington DC) and physical Android device. Three quality-of-life tunings for a better default experience: zoomed-in startup, less opaque radar, and smarter idle populate.

### Changes Made

#### 1. Default Zoom 14 → 18
- **First GPS fix** (`MainActivity.kt` ~line 926): `setZoom(18.0)`
- **Long-press** (~line 577): threshold `< 18.0`, zoom to 18
- **goToLocation** (~line 5833): threshold `< 18.0`, zoom to 18
- **Populate scanning marker** (~line 7607): threshold `< 18.0`, zoom to 18

#### 2. Radar Transparency 70% → 35%
- `radarAlphaPercent` default changed from 70 to 35 (`MainActivity.kt` line 71)
- Applies to both static and animated radar (shared variable)
- User can still adjust via menu slider

#### 3. Idle Auto-Populate POI Density Guard
- **`MainViewModel.fetchNearbyPoiCount()`**: new suspend function, delegates to `FindRepository.fetchCounts()`, returns total POI count or -1 on error
- **`MainActivity.kt` idle trigger** (~line 892): wraps `startIdlePopulate()` in a `lifecycleScope.launch` coroutine that first queries `/db/pois/counts?lat=&lon=&radius=10000`
  - If total ≥ 100: logs skip reason, does not start scanner
  - If total < 100 or query fails (-1): proceeds as normal

### Files Modified
- `app/src/main/java/com/example/locationmapapp/ui/MainActivity.kt` — zoom, radar alpha, idle guard
- `app/src/main/java/com/example/locationmapapp/ui/MainViewModel.kt` — `fetchNearbyPoiCount()`

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
- `app/.../res/drawable/ic_grid_menu.xml` — Material 3×3 grid icon
- `app/.../res/layout/grid_dropdown_panel.xml` — PopupWindow content

#### Modified Files
- `toolbar_two_row.xml` — replaced 2×5 grid with slim icon row + status line TextView
- `activity_main.xml` — added `fitsSystemWindows="true"` to AppBarLayout
- `AppBarMenuManager.kt` — removed `setupTwoRowToolbar()`, added `setupSlimToolbar()` + `showGridDropdown()`
- `MainActivity.kt` — replaced `followBanner: TextView?` with `statusLineManager: StatusLineManager`; migrated 6 banner functions to StatusLineManager set/clear calls

---

## Session: 2026-03-02f (Geofence Phase 4 — Database Import & Export — v1.5.39)

### Changes Made
- **Import SQLite .db**: SAF file picker → schema validation → install with duplicate detection
- **Import CSV**: SAF file picker → config dialog (name, zone type, radius) → parsed with column aliases → converted to SQLite
- **Export**: installed databases shareable via FileProvider + Android share intent
- **Local-only databases**: catalog merges locally-imported DBs not in remote catalog; works offline

### Files Changed
- `GeofenceDatabaseRepository.kt` — 6 new methods (validate, import SQLite, import CSV, parse CSV, get file, get local-only DBs)
- `MainViewModel.kt` — import result LiveData, import/export methods, catalog offline fallback
- `MainActivity.kt` — SAF launchers, CSV config dialog, overwrite confirmation, export
- `AndroidManifest.xml` + `file_paths.xml` — FileProvider setup

---

## Session: 2026-03-02e (Geofence Phase 2 — Additional Zone Types — v1.5.36)

### Changes Made
- 4 new zone types (speed cameras, school zones, flood zones, railroad crossings)
- `ZoneType` enum, `GeofenceRepository.kt` (4 fetch methods + `generateCircleShape()`)
- 4 proxy endpoints: `/cameras`, `/schools`, `/flood-zones`, `/crossings`
- Per-type LiveData, `rebuildGeofenceIndex()`, zone-type-aware UI
- Zoom guards: cameras ≥ 10, others ≥ 12

### Files Changed
- Models.kt, GeofenceEngine.kt, server.js, GeofenceRepository.kt (new), AppModule.kt, MainViewModel.kt, MenuEventListener.kt, AppBarMenuManager.kt, menu_alerts.xml, MainActivity.kt, DebugEndpoints.kt

---

## Session: 2026-03-02d (Geofence Alert System — TFR Phase 1 — v1.5.35)

### Changes Made
- JTS R-tree spatial engine (`GeofenceEngine.kt`), FAA TFR scraping via proxy `/tfrs`
- Semi-transparent red TFR polygon overlays, detail dialog on tap
- Entry/proximity/exit detection with cooldowns
- Severity-colored Alerts toolbar icon, alert banner
- Two-row toolbar layout (10 icons in 2×5 grid)
- GPS integration: user position + followed aircraft

### New Files (6)
- TfrRepository.kt, GeofenceEngine.kt, toolbar_two_row.xml, ic_alerts.xml, ic_tfr_zone.xml, menu_alerts.xml

### Files Modified (10)
- build.gradle, Models.kt, MainViewModel.kt, MainActivity.kt, AppBarMenuManager.kt, MenuEventListener.kt, AppModule.kt, DebugEndpoints.kt, activity_main.xml, server.js + package.json

---

## Session: 2026-03-02c (Weather Feature Overhaul — v1.5.34)

### Changes Made
- Proxy `/weather` composite endpoint (5 NWS API calls, per-section TTLs)
- Weather dialog: current conditions, 48-hour hourly strip, 7-day outlook, expandable alerts
- 22 weather condition vector icons, `WeatherIconHelper.kt`
- Dynamic toolbar icon with red border when alerts active
- Auto-fetch on GPS fix + every 30 minutes
- Deleted old Alerts submenu; METAR moved to Radar menu

---

## Session: 2026-03-02b (Idle Auto-Populate + Delta Cache — v1.5.33)

### Changes Made
- Idle auto-populate: 60s GPS stationarity → full scanner, 45s delays, GPS-centered
- X-Client-ID header on all Overpass requests (per-device UUID)
- Proxy delta cache: covering cache check, content hash skip, per-client fair queue (5-cap, round-robin)

---

## Session: 2026-03-02a (Geocode Autocomplete + Tooltips — v1.5.32)

### Changes Made
- Photon geocoder via proxy `/geocode` — prefix matching, US-only, 24h cache
- Auto-suggest TextWatcher with 500ms debounce at >= 3 chars
- Replaced `android.location.Geocoder` (no prefix matching)
- Toolbar tooltips on all 9 items
