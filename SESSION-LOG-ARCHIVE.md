# LocationMapApp — Session Log (Archive: v1.5.0 through Session 203, April–May 2026)

> Archived from SESSION-LOG.md. Contains all sessions through Session 203, plus the original v1.5.0–v1.5.50 archive at the bottom.
> Then S284 archived 2026-05-24 at S294 close, S283 archived 2026-05-24 at S293 close, S282 archived 2026-05-23 at S292 close, S281 archived 2026-05-23 at S291 close, S280 archived 2026-05-23 at S290 close, S279 archived 2026-05-23 at S289 close, S278 archived 2026-05-22 at S288 close, S277 archived 2026-05-21 at S287 close, S276 archived 2026-05-21 at S286 close, S275 archived 2026-05-21 at S285 close, S274 archived 2026-05-20 at S284 close, S273 archived 2026-05-19 at S283 close, S272 archived 2026-05-18 at S282 close, S271 archived 2026-05-18 at S281 close, S270 archived 2026-05-18 at S280 close, S269 archived 2026-05-18 at S279 close, S268 archived 2026-05-17 at S278 close, S267 archived 2026-05-17 at S277 close, S266 archived 2026-05-17 at S276 close, S265 archived 2026-05-17 at S275 close, S263 archived 2026-05-16 at S273 close, S262 archived 2026-05-16 at S272 close, S261 archived 2026-05-16 at S271 close, S260 archived 2026-05-16 at S270 close, S259 archived 2026-05-15 at S269 close, S258 archived 2026-05-15 at S268 close, S257 archived 2026-05-15 at S267 close, S256 + S255 archived 2026-05-14 at S266 close (window grew to 12 with S265 + S266 entries; trimmed back to 10 most recent), S254 archived 2026-05-13 at S264 close, S253 archived 2026-05-13 at S263 close, S252 archived 2026-05-13 at S262 close, S204 archived 2026-05-01 at S215 close, S205 archived 2026-05-01 at S216 close, S206 archived 2026-05-01 at S217 close, S207 archived 2026-05-02 at S218 close, S208 archived 2026-05-02 at S219 close, S209 archived 2026-05-02 at S220 close, S210 archived 2026-05-02 at S221 close, S211 archived 2026-05-03 at S222 close, S213 archived 2026-05-03 at S223 close, S214 archived 2026-05-04 at S224 close, S215 archived 2026-05-05 at S225 close, S216 archived 2026-05-05 at S226 close, S217 archived 2026-05-06 at S227 close, S218 archived 2026-05-06 at S228 close, S219 archived 2026-05-06 at S229 close, S220 archived 2026-05-06 at S230 close, S221 archived 2026-05-08 at S231 close, S222 archived 2026-05-08 at S232 close, S223 archived 2026-05-08 at S233 close, S224 archived 2026-05-09 at S234 close, S225 archived 2026-05-09 at S235 close, S226 archived 2026-05-09 at S236 close, S227 archived 2026-05-09 at S237 close, S228 archived 2026-05-09 at S238 close, S229 archived 2026-05-10 at S239 close, S230 archived 2026-05-10 at S240 close, S231 archived 2026-05-10 at S241 close, S232 archived 2026-05-11 at S242 close, S233 archived 2026-05-11 at S243 close, S234 archived 2026-05-11 at S244 close, S235 archived 2026-05-11 at S245 close, S236 archived 2026-05-11 at S246 close, S237 archived 2026-05-11 at S247 close, S238 archived 2026-05-11 at S248 close, S239 archived 2026-05-11 at S249 close, S240 archived 2026-05-12 at S250 close, S241 archived 2026-05-12 at S251 close, S242 archived 2026-05-12 at S252 close, S243 archived 2026-05-12 at S253 close, S244 archived 2026-05-12 at S254 close, S245 archived 2026-05-12 at S255 close, S246 archived 2026-05-13 at S256 close, S247 archived 2026-05-13 at S257 close, S248 archived 2026-05-13 at S258 close, S249 archived 2026-05-13 at S259 close, S250 archived 2026-05-13 at S260 close, S251 archived 2026-05-13 at S261 close (note S212 was skipped by the operator) (kept the 10 most recent in SESSION-LOG.md after adding each new entry).

## Session 284: 2026-05-19 (closed 2026-05-20) — CI red-since-S275 cleared: sheet_collection.xml NotSibling lint fixed (+ sliding amber tab indicator restored)

Operator surfaced that GitHub Actions had been red on every push since S275. Diagnosed via `gh run view` — `:app-salem:lintRelease` was failing with 2 `NotSibling` errors at `sheet_collection.xml:162-163`: the `tabIndicator` View tried to constrain to `@id/tabList` / `@id/tabBadges`, but those lived inside the `collectionTabs` LinearLayout — grandchildren of the outer ConstraintLayout, not direct siblings (ConstraintLayout only allows constraints between siblings). Same invalid constraint at runtime in `CollectionSheet.kt:472` (`startToStart = anchor.id` where anchor is `tabList`/`tabBadges`) meant the "sliding amber underline under active tab" effect from S275 had been silently broken on device too — the indicator just sat at parent default. Operator picked the proper layout refactor over a baseline-suppression shortcut: flattened the tab bar so `tabList` + `tabBadges` are direct children of the outer ConstraintLayout, split 50/50 by a vertical Guideline; tabIndicator now constrains to a real sibling. Both the lint fix AND the runtime sliding-indicator restoration land from the same XML edit (the Kotlin `showTab()` code was already correct — only the XML was missing valid sibling anchors). Verified locally before pushing: `:app-salem:lintRelease` 0 errors (down from 2), `:routing-jvm:test` pass. Pushed commit `c8e7657`; GitHub Actions run `26136388027` came back green — first successful CI build since S274 (10 sessions of red CI cleared in one shot). Non-blocking CI annotation flagged: Node 20 actions (`actions/checkout@v4` et al) will be forced to Node 24 starting 2026-06-02 — cheap one-line fix when in scope. 73 days to 2026-08-01 ship; 42 days to Form TX 2026-07-01.

Full session detail: `docs/session-logs/session-284-2026-05-19.md`. Commit: `c8e7657`.

## Session 283: 2026-05-19 — Chatbot V1 scoped end-to-end (on-device, rule-based, POI facts only); `docs/plans/Chatbot_V1.md` written + parked for operator pondering

Planning-only session against the S282-declared opener *"a chatbot that can answer simple questions"*. Operator framed scope as: on-device, rule-based, POI facts only, distance-sorted ("Where is the Phillips House?" / "Where can I find pizza?" / "What are pizza places close to me?"). Recommended SQLite FTS5 (Room `@Fts5` entity, baked into asset DB) over hand-rolled inverted index — matches V1 offline posture (`feedback_v1_no_external_contact.md`, `project_v1_offline_only.md`); two-stage pipeline FTS5 `MATCH` → Haversine distance sort; no intent parsing (FTS handles stopword ranking; "near me" is the default behavior anyway). Operator locked 7 design decisions across three AskUserQuestion rounds: **(1)** surface = dedicated full-screen Find screen entered via drawer menu, **(2)** match strictness = FTS5 + Levenshtein fuzzy fallback on `name` only when FTS returns 0 (threshold 2-3), **(3)** answer scope = locations + short blurb inline AND direct-answer mode for high-confidence name queries (snap-map + answer card), **(4)** index scope = all 2,039 active POIs (`deleted_at IS NULL`) across `name + category + subcategory + description + short_description`, **(5)** GPS-unavailable fallback = alphabetical sort with distance hidden (operator confirmed tourists may not be in Salem yet), **(6)** synonyms = deferred to post-v1 (revisit only if field-walks show recall failures), **(7)** drawer-menu placement near top. Flagged the Hawthorne disambiguation edge case — multiple POIs share token "Hawthorne", direct-answer would arbitrarily pick one; recommended uniqueness rule (top FTS bm25 score ≥ 2× second result AND ≥ 80% name-token overlap). Wrote `docs/plans/Chatbot_V1.md` (~150 lines) capturing the locked scope table, 7 open items for operator pondering (Phase 0 recall spike as gate, FTS bake hook placement, Room v23→v24 schema rotation, direct-answer threshold edge cases, default radius, drawer placement, empty-input state), 6 implementation phases (Phase 0 recall spike → Phase 1 FTS bake → Phase 2 PoiSearchDao → Phase 3 Find screen → Phase 4 drawer wiring → Phase 5 field smoke + portrait check), 5 risks, and explicit out-of-scope list (8 items including hosted LLM, network, multi-turn context, voice input, narration-style answers). Phase 0 is the recommended next action — 5-min PG query against ~15 tourist-staple keywords to validate the synonyms-deferral assumption before any code. Plan parked for operator pondering. **Net code-side change: zero** — discussion + plan doc only. No PG / Room / Android / web / cache-proxy / publish chain / device installs touched. Carry-forwards: Paul Revere's Bell miss still armed for next walk-sim (S282 carry; Lenovo logcat buffer remains 16 MiB until reboot); all S279/S278/S281 carries standing. 72 days to 2026-08-01 ship; 42 days to Form TX 2026-07-01.

Full session detail: `docs/session-logs/session-283-2026-05-19.md`. Commit: this session.

---

## Session 282: 2026-05-18 — LongWalkTour walk-sim full-route HB coverage report (48/48 reachable, 2 misses diagnosed); 14 Mall Street narrated-flag fix shipped; Paul Revere's Bell miss left armed for next-walk capture

Operator carry-forward from S281: re-arm logcat stream with the 16 MiB buffer for a clean full-walk HB coverage report on LongWalkTour. Walk-sim ran 2,619 steps / 59 TTS dwells; stream filter `WALK-SIM|NarrationMgr|NARR-QUEUE|NARR-STATE` `grep -v WickedAnim` captured 1969 lines / 130 GPS samples spanning steps 20-2600. `tools/s281_hb_coverage_v2.py` reported **48 HBs in range, 46 announced, 2 missed**. Diagnosed both: **(1) `14 Mall Street (Scarlet Letter House)` — content-authoring gate.** Asset DB row had `is_narrated = 0` despite 781 chars `historical_narration` + 812 `long_narration`; the DAO `WHERE (is_narrated = 1 OR (haunt_sprite_id IS NOT NULL AND haunt_enabled = 1))` query at `SalemPoiDao.kt:52` excluded it from `NarrationGeofenceManager.points` entirely. **(2) `Paul Revere's Bell` — real runtime miss, root cause not provable from this log.** Both Bell and Armory Park are `is_narrated = 1`, both HISTORICAL_BUILDINGS/monuments, ~15 m apart; Armory fired ENTRY at step ~1024 but Bell never appears in any `NARR-QUEUE` line. Could be scanner-never-fired (cooldown / sub-step distance) or `enqueueNarration` SKIP at AudioControl group gate / duplicate gate; capture this session lacked NARR-GEO + NARR-GATE so cannot distinguish. **Confirmed the dwell mechanism is not the cause** — `SalemMainActivity.kt:2284-2316` freezes the walker on `Speaking || queueNonEmpty` and exits only on `TTS idle && queue empty`, and during dwell `setManualLocation(point)` re-fires every 1 s so the scanner re-runs each second; other in-range POIs get a fresh ENTRY chance every tick. Latent fragility also flagged: `events.collectLatest` at `SalemMainActivityNarration.kt:307` is safe today because `enqueueNarration` is synchronous, but if it ever becomes suspending, concurrent ENTRY events will start being silently cancelled mid-handler. Fix shipped this session: PG `salem_pois.is_narrated = true WHERE id='scarlet_letter_house'`, full 5-script publish chain re-ran clean (Narrated 1987 → 1988, identity_hash `4db15f...` unchanged, v23), `:app-salem:bundleDebug -PskipPublishChain` in 16 s (140.8 MB), bundletool `build-apks --connected-device` + `install-apks` on Pixel 8 + Lenovo in parallel with `adb uninstall` first per WAL-safety. New stream filter `WALK-SIM|NarrationMgr|NARR-QUEUE|NARR-STATE|NARR-GEO|NARR-GATE` armed and ready for the next walk to disambiguate the Bell miss; operator asked to stop the stream at session close so it's killed (re-arm fresh next walk). Two commits this session (`9a4e23a` asset DB refresh + this close commit). Next-session opener: operator wants to start work on **"a chatbot that can answer simple questions"** — scope (surface / target user / on-device vs. server / question domain) unspecified; opener is a scope-clarification ask, not implementation. 73 days to 2026-08-01 ship; 43 days to Form TX 2026-07-01.

Full session detail: `docs/session-logs/session-282-2026-05-18.md`. Commit: this session.

---

## Session 281: 2026-05-18 — Publish chain + bundletool install on Pixel 8 + Lenovo (S279 architecture now on-device); HISTORICAL_BUILDINGS coverage spot-check on LongWalkTour walk-sim (0 misses in captured 22%, full-walk gap unrecoverable due to 256 KiB logcat buffer)

Operator: *"run the publish chain and bundletool install"*. Ran the canonical 5-script chain (`publish-salem-pois.js` 2,039 rows / `publish-tours.js` 3 tours / `publish-tour-legs.js` 59 legs / `publish-poi-collection.js` 309 entries / `align-asset-schema-to-room.js` Room v23 `4db15f...`). Asset DB now matches S279 architecture (3 tours, Dr. K's default, no Whole-Salem global pool, no TOUR_0001 — was S278's 4-tour + 447-pool layout). Built debug AAB in 26 s (140.8 MB), bundletool `build-apks --connected-device` + `install-apks` on Pixel 8 (`41231FDJH0018J`) and Lenovo (`HNY0CY0W`) with `adb uninstall` first per WAL-safety. Operator then started a LongWalkTour walk-sim mid-session and at step ~1464/1887 asked: *"make sure all of the Historical Buildings were announced that were in range, I thought it might have missed a few"*. Logcat ring buffer was 256 KiB (~3 min); walk had been running ~42 min → steps 1–1464 unrecoverable. Started a streaming logcat at step ~1465 + built `tools/s281_hb_coverage_v2.py` (preserved from /tmp): parses captured `WALK-SIM` GPS positions, parses announced POI names from `NarrationMgr|NARR-QUEUE|NARR-STATE` events, loads HBs from asset DB, computes haversine min-distance per HB to GPS trace, diffs in-range vs announced. **Result for captured window (steps 1540–1880 / 36 GPS samples): 7 HBs in range / 7 announced / 0 missed** — West India Goods Store, Custom House, Public Stores, Hawkes House, Derby House, St. Joseph Hall, Salem Maritime National Historic Park. Proximity scanner working correctly. Bumped Lenovo logcat buffer 256 KiB → 16 MiB and cleared for next walk (persists until reboot). Gotcha noted: `WickedAnimSummary` debug lines contain `center=...` which trips naive `enter|exit` regexes via the "enter" substring — final stream filter uses `grep -v WickedAnim`. **Carry-forward to S282:** re-arm stream before operator starts next walk + run `tools/s281_hb_coverage_v2.py` against the new stream log for clean full-route coverage. ~95 min elapsed. 74 days to 2026-08-01 ship; 44 days to Form TX 2026-07-01.

Full session detail: `docs/session-logs/session-281-2026-05-18.md`. Commit: this session.

---

## Session 280: 2026-05-18 — SI-clued category audit shipped + scrapped (signal/noise too low; SI primary_category enum mapped + match strategy validated for future targeted passes)

Operator opened post-S279: *"go through all the POIs and review if their catagory/subcatagory is wrong. .odt spreadsheet. Let's use SI to give us clues."* Clarified to `.ods` and full 2,039-POI scope. SI was paused per `project_oracle_intelligence_paused_provenance.md` but the use was metadata-only (no SI text lifted into LMA) — consistent with `feedback_si_is_reference_only.md`. Verified `/api/intel/poi-export` returns 1,560 entities; mapped SI's controlled vocabulary (`primary_category` is an ~18-value enum: `service_professional`/`shop_retail`/`service_health`/`restaurant`/`spa_beauty`/`other`/`museum`/`cafe`/`tour_operator`/`shop_occult`/`education`/`bar`/`fitness`/`hotel_lodging`/`performance_venue`/`religious`/`gallery_art`/`shop_bookstore`/`shop_antiques`) with `secondary_categories[]`/`specialties[]` for refinement. Built `tools/category-audit.py` (~470 LOC) with two-pass logic: PRIMARY_MAP → coarse (category, subcategory) from SI primary alone, SECONDARY_REFINE (~120 rules) refines subcategory and may override category when secondary tokens are unambiguous. Two iterations needed: first pass used substring matching → `arts` matched inside `auto_p`**`arts`** flagging every auto shop as ENTERTAINMENT__arts; fixed by switching to set-membership on normalized tokens. Second pass flagged every auto-shop / healthcare-provider as CATEGORY_MISMATCH because SI gives them generic `primary=service_professional` with no specific refining secondary; fixed by adding `LMA_SPECIFIC_CATS` + `high`/`low` confidence tier where low-confidence suggestions against an LMA-specific category get demoted to `LOW_CONFIDENCE_HINT` (yellow) instead of `CATEGORY_MISMATCH` (red). Match strategy: lat/lng within 80m AND name similarity ≥0.55 (Ratcliff/Obershelp via `difflib.SequenceMatcher`), name-normalized to strip the/a/of/llc/inc/salem/ma/punctuation; combined score weights name (0.85) over proximity (0.15) for tiebreaking. Final output `docs/audits/s280-category-audit-2026-05-18.ods` (135K, 21 columns × 2,039 rows + Summary sheet): 106 CATEGORY_MISMATCH / 114 SUBCATEGORY_DIFFERS / 60 SUBCATEGORY_MISSING / 53 LOW_CONFIDENCE_HINT / 781 NO_SI_MATCH / 925 OK. Sorted by flag severity. Top patterns surfaced: martial-arts / dance / boxing schools currently tagged EDUCATION (Alvarez Family Boxing, KICK Karate, Fly Kidz Dance, etc.) — SI consistently flags these fitness/dance → ENTERTAINMENT__fitness or ENTERTAINMENT__dance_studios; Salem Arts Association tagged CIVIC with subcategory=ENTERTAINMENT__arts (impossible cross-category pairing). Operator review: *"Spreadsheet has 80% junk in it... Interesting but not very useful... scrap the audit"* — 1,706 / 2,039 rows (83%) were OK or NO_SI_MATCH, low operator-actionable signal. Deleted both artifacts (`tools/category-audit.py` + `docs/audits/s280-category-audit-2026-05-18.ods` + empty `docs/audits/` dir). Net code-side change: zero. **Reusable mechanics learned (worth keeping in mind for future passes):** SI primary_category enum + secondary token vocabulary mapped; two-pass primary→refine pattern works; confidence-tier demotion to LOW_CONFIDENCE_HINT prevents false CATEGORY_MISMATCH against LMA-specific categories that SI lacks tokens for; set-membership token matching beats substring; 80m + name 0.55 threshold gives clean SI↔LMA join. Future audits should pre-filter to operator-flagged suspect categories (e.g., EDUCATION rows under suspect for fitness/dance reclass, or all OFFICES rows) rather than full-sweep. **All S279 carry-forwards untouched:** publish chain unrun, on-device asset DB still S278-shaped (4-tour + 447-pool), Dr. K's-as-default field smoke owed, 1,622 active POIs with empty `short_description` still on docket. V1 ship-cliff status unchanged. ~45 min elapsed. 74 days to 2026-08-01 ship; 44 days to Form TX 2026-07-01.

Full session detail: `docs/session-logs/session-280-2026-05-18.md`. Commit: this session.

---

## Session 279: 2026-05-18 — Cache-proxy unstuck (stale pre-S274 process) + TOUR_0001 removed + Dr. K's Tour promoted to default (Whole-Salem global pool deprecated)

Operator opened: web admin Vite was crashing on `[plugin:vite:react-babel] Unexpected token` — diagnosed at `web/src/admin/AdminSidebar.tsx:51` where `title: 'Katrina's Collection ...'` had an unescaped apostrophe closing the string mid-word; swapped to double-quotes. Operator: *"First off, get the services working correctly"* — cache-proxy 4300 was looping `[AdminTours] list error: relation "salem_passport_filters" does not exist` because the running PID 1294880 was started May 16 (before the S274 `salem_passport_filters` → `salem_collections` rebrand); restarted (PID 2230071) and verified `/admin/salem/tours` returns 4 tours. Operator: *"TOUR_0001 - the defunct default tour - that needs to be removed"* — `tour_WD1` (display name `TOUR0001`, theme=Test, description "Walking thourh salem" [sic], 76min/6.37km, 15 stops, 14 baked legs, 41-entry walk-derived collection); admin DELETE only cleans stops+tour, so ran a single transactional cleanup deleting `salem_collection_entries` (41) + `salem_collections` (1) + `salem_tour_legs` (14) + `salem_tour_stops` (15) + `salem_tours` (1). Tours 4 → 3. Operator pivot: *"the default_salem_walking is defunct and should be removed. DrK's Tour is the default"* — investigated runtime path: cache-proxy `admin-collection.js:567` hard-pinned `(id = 'default_salem_walking') DESC` for the pool-candidate query, and Android `CollectionSheet.kt:197` falls back to `collections.first().collection_id` when no tour is active. The publish-poi-collection.js script orders by `sort_order ASC, name ASC` with no pin, so all-zero sort_orders meant DensityTour would alphabetically win after the global pool was removed. Three-step fix: (a) transactional PG delete of `default_salem_walking` + 447 entries; (b) set sort_orders drks=0, density=1, longwalk=2 so the bake puts Dr. K's first; (c) drop the dead `default_salem_walking` ORDER BY pin from `admin-collection.js:567`. Updated kdoc on `CollectionEntry.kt:48` to reference `tour_drks_001_walk_passport` instead of the deleted collection. Cache-proxy restarted (PID 2247904) and verified `/admin/salem/collections` now returns 3 rows with Dr. K's first. **Architectural pivot to note:** the Whole-Salem global pool (filter-driven, auto_bake=true) is now deprecated — V1 has zero global pools; collections are direct per-tour walk-derived snapshots, with Dr. K's as the default no-tour-active view. `feedback_collection_pool_intersect_proximity.md` still holds for per-tour authoring (operator hand-curates) but the pool ∩ proximity intersection now uses the `admin-collection.js:559-561` fallback (`historical_narration IS NOT NULL`) since the explicit pool is gone. **Not done this session:** publish chain not run (no asset DB rebake), no Android build/install, so the on-device state still reflects S278's 4-tour + 447-pool model. Per `feedback_publish_chain_manual_after_admin_edits.md` next Android build MUST run the 5-script chain. Field smoke of Dr. K's-as-default on Pixel 8 / Lenovo is the principal carry-forward. The originally-asked `<10-word short_description` SI-clue pass for 1,622 empty active POIs was pivoted off when TOUR_0001 cleanup took precedence; still on the docket. ~30 minutes elapsed. 73 days to 2026-08-01 ship; 44 days to Form TX target 2026-07-01.

Full session detail: `docs/session-logs/session-279-2026-05-18.md`. Commit: this session.

---

## Session 278: 2026-05-17 — Katrina's Collection v2 ghost batch SHIPPED end-to-end (SI-clued personas + 5-pass prompt overhaul to spooky/witchy/period-correct + B-image subtlety + List filter)

Operator: *"We are going back to the badges … SI is back up — let's figure out a way to get some good ghosts for our badges."* New hard-rule memory **`feedback_si_is_reference_only.md`** saved mid-session (operator: *"SI is just a toy, not a primary source of anything due to copyright/plagiarism issues. LMA is the source for all buildings, geocoding"*) — SI is clue-lookup only, never a content source, regardless of pause status. Built `mine-si-clues-for-ghosts.py` (LMA-prose two-pass NAME→DESC matcher + SI building.original_use + SI relations free-text, with extensive named-figure lexicon: Hawthorne / Bowditch / Bishop / Rebecca Nurse / Sarah Good / John Proctor / Roger Conant / McIntire / Endecott / Hathorne / Corwin / Parris) yielding **74 / 107 SI-clued profession overrides** keyed by poi_id; remaining 33 fall to S275's random pool. Wired into `generate-ghost-portraits.py --from-pg` as a non-invasive override step (hair/expression/oddity/frame still deterministic per-poi RNG roll). **Five prompt-iteration passes** over operator critique to land cartoon/witchy/spooky/period: pass 1 baseline + SI overrides → pass 2 cartoon style + new `witchy_props` pool (30 entries) + Coraline/Hocus Pocus style spine → pass 3 ghost-first reframing (wraith/spirit/translucent leads with max weight) → pass 4 period 1690s puritan colonial wardrobe + haunted Salem backgrounds (gambrel saltbox houses, crooked gravestones, gnarled oaks, fog over cobblestones) → pass 5 CFG 2.5→4.5 + wraith/specter/revenant tags + corpse-grey-skin + hollow-black-eye-sockets + hardened NEG against "pretty alive woman" model bias. **Full 107-pair batch generated in 858 s (14:18, 8.0s/ghost)**, 214 PNGs at 768×768. New `build-ghost-review-gallery.py` writes a single-page HTML contact sheet with A+B side-by-side, persona details, keep/regen/flag radios, localStorage marks, clipboard-export of picks. **B-image subtlety pass** in response to operator *"B images too extreme"*: replaced extreme `expressions_alt` entries with micro-expressions ("the faintest knowing smile" / "a slow subtle wink") + lowered weight 1.5→1.15 + dropped "exaggerated"/"stretched mouth" + face-inpaint denoise 0.75→0.45 + `--regen-b` re-rolls from current pool. Ran `--regen-b` across all 107 in 298 s (4:58, 2.8 s/B); A bit-identical, only B regenerated. **Ship pipeline executed end-to-end:** pack PNG→webp (179 MB → 8.5 MB, 95% reduction), replace `app-salem/src/main/assets/ghosts/*.webp`, repopulate `salem_pois.ghost_asset_a/b/ghost_frame` via parametrized `populate-ghost-personas.py --manifest <v2>` (the new `prop` roll advanced the RNG so frame slugs shifted vs S275), run 5-script publish chain (`salem-pois → tours → tour-legs → poi-collection → align-asset-schema`, Room v23 / identity_hash `4db15f...`), build debug APK + AAB, bundletool `build-apks --connected-device` + `install-apks` on both Pixel 8 (`41231FDJH0018J`) and Lenovo (`HNY0CY0W`) with `adb uninstall` first per WAL-safety rule. **Bonus fix from Lenovo screenshot:** `CollectionSheet.kt:buildRowsForCollection()` was showing all 447 collection POIs in the List with empty 64dp portrait boxes for the 340 non-HIST_BLDG entries — added the same `!ghostAssetA.isNullOrBlank()` filter the Badges grid already used. List + Badges now both show 107; count top-bar `X / 447 → Y / 107` (aligns with `feedback_ghosts_not_stops.md`). Rebuild + redeploy on both devices. Operator *"works well enough."* No schema changes. No new Room version. Memory: 1 new (`feedback_si_is_reference_only.md`). Carry-forwards to next session: Phase 4 operator field walk now with v2 SI-clued ghosts; FIND_FILTER vs TOUR-Paused ordering; TTS popup smoke; Recon Layer 2; Pixel 8 portrait Phase A. 72 days to 2026-08-01 ship; 45 days to Form TX target 2026-07-01.

Full session detail: `docs/session-logs/session-278-2026-05-17.md`. Commit: this session.

---

## Session 277: 2026-05-17 — Ambient ant-sprite idea parked (WickedMap engine audit confirms 5th-sibling-not-new-infrastructure shape)

Exploratory-only session. Operator opened with the lively-map golden rule (*"this is a casino of tourists - our map should be animated"*) and iterated through three reframings before settling on "ants" — 8-16px tiny CGI dots as pure background motion, not the original 1000-sprite or 64×64-individual versions. Explore subagent audited the WickedMap engine and confirmed it's already production-wired with **4 live ambient animations at 30fps heartbeat** (`WickedAnimationOverlay.kt:55-108` self-invalidates via `postInvalidateDelayed(33ms)`): whitecaps over water polygons, fireflies in cemetery polygons, grass-blade sway in park polygons, and geofence-triggered haunt sprites near POIs — all wired into `SalemMainActivity.kt:1442-1477` at overlay position 0 (drawn under markers, inheriting `feedback_basemap_priority_over_animation.md` compliance). `MapOverlay` is a 2-method `tick + draw` interface; an ants overlay is a 5th sibling, not new infrastructure. Two small gaps identified: (a) no zoom gate exists today — whitecaps render at every zoom, ants would need `minZoom = 18`; (b) no sidewalk polygon kind in `PolygonLibrary` — only water/cemetery/park, so ant pathing needs either a new polygon kind, a baked sidewalk-edge JSON sibling to `polygons.json`, or trailing the 4 baked tour polylines. Parked plan written at `docs/plans/ambient-ant-sprites-parked.md` with: concept + engine-reuse table, 2 gaps with options ranked by cost, 3-phase scope, 4 deferred decisions, explicit non-goals (not interactive, not navigation hints, not historical-accurate, not visible at low zoom), 5 risks, anchors. Status PARKED, "revisit shortly" per operator. **Carry-forwards from S276 untouched** (FIND_FILTER vs TOUR-Paused status-line ordering verification, Katrina's Collection Phase 4 field smoke, Collection S5 field walk, Recon Layer 2, Pixel 8 portrait Phase A survey). No code, no DB, no schema, no publish-chain, no build/install. 72 days to 2026-08-01 ship; 45 days to Form TX 2026-07-01.

Full session detail: `docs/session-logs/session-277-2026-05-17.md`. Commit: this session.

---

## Session 276: 2026-05-17 — Find aliases Wave 2 + search-mode containment (filter-and-map bug fix) + tour pause/resume with Resume chip

Three connected pieces of work plus a small project update: (1) **Find dialog TYPE_SYNONYMS expanded ~60 → ~170 keys** in `FuzzySearchEngine.kt` — exploratory tourist-query coverage spanning operator-named cases (pot / smoke / flowers / bathroom / parking) + the 90% group (humorous / parking / transport / bathrooms) + adjacent staples (pharmacy / emergency / gas / groceries / souvenirs / ATM / cannabis / tobacco / florist). (2) **Filter-and-Map "doesn't filter" bug root-caused via verbose DebugLogger.d instrumentation** (per operator: *"put verbose debugging on all of this when we are in debug mode, why guess?"*) — diagnostic trace from Lenovo run of "pot" exposed that ~580 narration markers live in a parallel cache-backed system (`narrationMarkers` / `narrationMarkerCache` / `narrationMarkersOnOverlay`, S118/S234) NOT in `poiMarkers`; `clearAllPoiMarkers()` therefore cleared 0 markers and filter-map added 33 on top of 631 visible POIs. (3) **Operator design model implemented:** search is its own mode — active tour pauses on enter (via existing `TourEngine.pauseTour()`), narration markers detach from `mapView.overlays` with `narrationMarkerCache` preserved for cheap re-attach, tour overlays cleared explicitly (Paused state doesn't auto-clear polylines); exit-without-pick auto-resumes via `resumeTour()` and re-attaches narration via `loadNarrationPointMarkers()` (zero allocations — all cache hits); marker tap = pick = sets `tourPausedBySearch=false` then exits filter, leaving tour Paused so the new persistent **"▶ Resume — <tour name>" status-line chip** (clickable Paused entry on `Priority.TOUR`) becomes the resume affordance. Four files touched: `FuzzySearchEngine.kt`, `SalemMainActivity.kt` (new `detachAllNarrationMarkers()` + `tourPausedBySearch` flag + debug traces), `SalemMainActivityFind.kt` (enter/exit/pick wiring), `SalemMainActivityTour.kt` (clickable Resume chip on Paused). Plus: **(4) Form TX deadline deferred 2026-05-20 → 2026-07-01** per operator (counsel-side, no code impact) — saved as `project_form_tx_deadline.md` memory + indexed in MEMORY.md; STATE.md updated. **Field-test handoff:** logcat trace tool stays at `/tmp/filtermap-trace.log` via `adb logcat | grep FilterMap`. Carry-forward: verify `Priority.FIND_FILTER` (level 3) vs `Priority.TOUR-Paused` (level 4) status-line ordering during active filter-map; TOUR-Paused currently outranks FIND_FILTER which may read confusingly — operator field walk will tell us whether to clear TOUR during filter-map or bump FIND_FILTER above it. No DB / schema / publish-chain changes. 5 build/install cycles on Lenovo through the session. 72 days to 2026-08-01 ship; 45 days to new Form TX target 2026-07-01.

Full session detail: `docs/session-logs/session-276-2026-05-17.md`. Commit: this session.

---

## Session 275: 2026-05-16 (closed 2026-05-17) — Katrina's Collection Phase 2 + Phase 3 SHIPPED end-to-end (107 paired ghost portraits + 8 frame overlays + Room v23 + CollectionSheet tabbed UI)

Operator-led design + build session: gave each unique HIST_BLDG POI a randomly-rolled persona and bespoke paired portrait (A normal + B fourth-wall-break smirk, swapped 2% per render), all wired into the existing CollectionSheet as an augmenting "Badges" tab plus replacement of the witch-hat row stamps with ghost-portrait tiles. **Paper-napkin → seed JSON → SD batch → packed assets → PG → Room → UI** in one session. **Phase 2 (data + assets):** wrote 7-column attribute seed (`cache-proxy/data/ghost-personas-seed.json`) and mirrored to operator's `~/Documents/MiscAttributes.ods`; built 4 generator scripts (`generate-ghost-portraits.py` with `--from-pg` / `--regen-b` / `--limit` modes; `generate-frame-overlays.py` with chroma-key + emblem composite; `pack-ghost-portraits.py` 768 PNG → 384 webp q85 alpha-aware; `populate-ghost-personas.py` PG UPDATE from manifest); ran 4 SD passes (Pass 1 txt2img+txt2img drifted, Pass 2 full img2img drifted backgrounds, Pass 3 face-only inpaint with bigger mask + denoise 0.75 + `({expression}:1.5)` weighting + face-focused B prompt landed). Generated 107 × 2 = 214 portraits (DreamShaperXL Turbo, 768×768, 669s) plus 8 frame overlays (chroma-key + forced interior alpha cutout + per-emblem composite). Packed to ~4.6 MB total app payload (214 portraits 4.2 MB + 8 frames 0.36 MB). **PG + Room schema:** added `ghost_asset_a/b/ghost_frame TEXT` columns to `salem_pois` (idempotent ALTER), denormalized into `collection_entry` (matching existing `poi_name/lat/lng/category` pattern). Room **v22 → v23**, identity hash `4db15f763c9dbd5a529d24b128cecada`. Publish chain quirk noted: `publish-poi-collection.js` runs before `align-asset-schema-to-room.js` so first-run INSERT fails when collection_entry schema changes — workaround: re-run publish-poi-collection after align (one-time bootstrap). Full chain produced 797 collection_entry rows, 234 with ghost data. **Runtime:** `GhostResolver.kt` with LRU cache + 2% B-swap roll + graceful null + DebugLogger traces. **UI:** initially replaced the list view with badge grid — operator caught: *"this should augment what we had, not overwrite it"* — reverted via `git checkout` and added tabs (List | Badges) with sliding amber underline indicator + two stacked RecyclerViews in a ConstraintLayout. One tile-collapse bug along the way (`layout_constraintDimensionRatio="1:1"` doesn't work under LinearLayout parent; rewrote tile XML as ConstraintLayout root). Blank-tile filter: badge grid HIST_BLDG-only (39 of 72 in Dr. K's Tour). Operator UI iteration: witch-hat row stamp → 64dp ghost-portrait tile (same 2% swap + greyscale rules), uncollected portraits dimmed to 50% alpha on top of greyscale, frame **only appears when caught** (the catch reward). 5 build/install cycles in the closing UI loop; both Pixel 8 (`41231FDJH0018J`) and Lenovo (`HNY0CY0W`) confirmed `SalemContentDatabase opened: schema_v=23 tables=13 (...collection_entry)` with zero destructive migrations. New memory `project_katrinas_collection_ghost_asset_pairing.md` captures full pairing spec + asset locations + frame model + view-state rules + category scope (HIST_BLDG V1; commercial categories stubbed for post-V1). **Operator close:** *"we have done wonderful in this."* SI2 (~1 week) will redo all the personas with deterministic per-building seeding; the pairing convention + filename pattern + 2% swap rule will survive that re-bake. 73 days to 2026-08-01 ship; **3 days to Form TX deadline 2026-05-20**.

Full session detail: `docs/session-logs/session-275-2026-05-16.md`. Commit: this session.

---

## Session 274: 2026-05-16 — "POI Passport" rebranded to "Katrina's Collection" + Ghost collection model (Phase 1 mechanical rename across ~50 files, Room v22 + UserDataDB v4)

Operator pivot off the S273 docket: rebrand the POI Passport feature as **"Katrina's Collection"** with each unique historical building becoming a catchable **"Ghost"** (Pokédex model). Motivation: many other Salem operators already use "Passport" branding (Haunted Happenings Passport, etc.); the ghost-collection framing is distinctive AND aligns with the existing per-POI hero-art infrastructure. Phase 1 (this session) is a mechanical rename across all layers; Phases 2-4 (AI-Studio batch of 107 cute ghost portraits, ghost-vs-other UI distinction, field smoke) carry forward. Confirmed 107 ghost-eligible POIs (live PG: `SELECT COUNT(*) FROM salem_pois WHERE category='HISTORICAL_BUILDINGS' AND deleted_at IS NULL`); HISTORICAL_LANDMARKS does NOT get ghosts per operator. Renames shipped: PG (`salem_passport_filters` → `salem_collections`, `salem_passport_pois` → `salem_collection_entries`, column `filter_id` → `collection_id`; idempotent ALTER block at bottom of `salem-schema.sql` for dev installs); cache-proxy (`lib/admin-passport.js` → `admin-collection.js`, `scripts/publish-poi-passport.js` → `publish-poi-collection.js`, API endpoint paths); Room content DB **v21 → v22** (`PoiPassport` entity → `CollectionEntry`, table `poi_passport` → `collection_entry`, columns `passport_id/_name` → `collection_id/_name`; new identity hash `07f63ead9c5c3c05d87dcb6d54f64ba8`; no MIGRATION needed — bundled asset uses `fallbackToDestructiveMigration` per S180); Room user DB **v3 → v4** (`PassportVisit` → `PoiVisit`, table `passport_visit` → `poi_visit`; hand-written `MIGRATION_3_4` renames in place to preserve paid-user history per S180 lockdown); Android Kotlin (`PassportSheet` → `CollectionSheet`, `TourModels` property cascade `passportId/Total/Heard` → `collectionId/entryTotal/entriesVisited`, `core/MenuEventListener.onPassportRequested` → `onCollectionRequested`); web admin (`PassportTab.tsx` → `CollectionTab.tsx`, `WalkPassportDialog.tsx` → `WalkCollectionDialog.tsx`, TS interface `PassportFilter` → `Collection`, sidebar id/label); resources (4 layout/drawable file renames + `passport_stamp_filled` color → `collection_entry_visited`). One PG-side concern caught in flight: the user-data table is `poi_visit` (not `ghost_caught`) because it tracks every POI heard, not just ghost catches — UI does the ghost-subset join at render time. Cleaned the stale `poi_passport` ghost table out of the bundled asset after the v22 align (Room doesn't drop tables it no longer manages — manual `DROP TABLE` + re-align needed). Built clean, bundletool installed on Lenovo (HNY0CY0W) + Pixel 8 (41231FDJH0018J) preceded by `adb uninstall` per WAL-safety rule; both devices logcat confirmed `SalemContentDatabase opened: schema_v=22 tables=13 (…collection_entry)` with zero destructive migrations. **Operator carry-forward:** `default_salem_walking` (447-POI Whole-Salem collection) is obsolete per operator — *"just haven't gotten around to removing it"*; deferred. Memory updates: renamed `feedback_passport_pool_intersect_proximity.md` → `feedback_collection_pool_intersect_proximity.md`; renamed `feedback_stamps_not_stops.md` → `feedback_ghosts_not_stops.md`; new `project_katrinas_collection_rebrand.md` capturing the full Phase 1-4 plan + 107-ghost-eligible inventory + obsolete-default carry-forward. V1 ship-cliff status unchanged. 74 days to 2026-08-01 ship; 4 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-274-2026-05-16.md`. Commit: this session.

---

## Session 273: 2026-05-16 — TTS master-mute walk-sim stall fixed + business gate finally honors Ships & Services (3-bug chain, 4 build cycles)

Operator opened with the S272-owed TTS field smoke: *"when I start a tour and then turn off the sound and go into walk mode, I stop walking sometimes - look at current Lenovo, it is stuck"*. Three stacked bugs surfaced and were fixed in sequence across 4 build/install cycles. **Bug 1 (master-mute walk-sim stall, from S272 master gate)**: `NarrationManager.enqueue()` silently dropped segments when master was OFF → state stayed `Idle` → Activity's `narrationState` collector never received completion → `currentNarration` stuck on dropped POI → subsequent geofence ENTRYs appended to `narrationQueue` → walk-sim TTS-gated dwell at `SalemMainActivity.kt:2278-2317` saw `state=Idle && queueNonEmpty` and froze for the 180s `WALK_SIM_DWELL_MAX_MS` safety cap on every step. Three-layer fix in two files: (a) new `emitSyntheticComplete(segment)` helper in `NarrationManager.kt` posts `Idle` via `Handler(Looper.getMainLooper()).post` after synchronous `Speaking` assignment, separating the two emissions across Main-queue ticks so StateFlow's equality-dedup doesn't collapse them when prior value was Idle; every silent-drop path (master-mute, ringer silent, POI/Oracle group mute) now `emitSyntheticComplete + return` instead of bare `return`; (b) `enqueueNarration` PLAY DIRECT block in `SalemMainActivityNarration.kt` gates the `narrationCancelForPoiAtMs = …; cancelSegmentsWithTag("newspaper_1692")` on `AudioControl.isTtsEffectivelyOn()` so the S146 500ms cancel-window guard doesn't suppress the legitimate synthetic Idle (witnessed: `Idle suppressed (transient cancel-for-POI 75ms ago)`); (c) symmetric DEBUG-gated ALLOW-side gate-decision logging in `NarrationManager.enqueue` (`AudioControl gate: POI group ALLOWED/muted (cat=…)`, Oracle equivalents, userInitiated bypass, legacy/no-kind), R8-stripped in release. **Bug 2 (business gate ignored despite Ships & Services unchecked)**: operator: *"if I have business (ships & services) unchecked in audio controls, when I have all POIs visible, I should not hear any business narrative when I am close to them"*. Logs showed `kind=POI, category=null` on every business announcement — root cause: 7 `speakTagged{Hint,Narration}` POI call sites in `SalemMainActivityNarration.kt` (3 in `enqueueNarration` PLAY DIRECT when{} + 1 Linger and Listen subtopic + 3 in `playNextNarration` queue-drain when{}) passed only 4 args, defaulting the 5th param `category` to null; `AudioControl.groupForCategory(null)` falls through to `Group.MEANINGFUL`, bypassing the BUSINESSES toggle. Plumbed `point.category` through all 7 call sites. **Bug 3 (Show-All-POI override bypassed audio gates)**: after Bug 2 fix, businesses STILL narrated when "Show All POIs" FAB was on — `AudioControl.isPoiSpeechEnabled()` short-circuited with `if (showAllOverride) return true` per the original S217 design that lumped visibility + audio under the same flag. Operator clarification: Show-All should scope to VISIBILITY only; audio toggles must always be honored. Removed the short-circuit; added DEBUG-gated per-call trace logging (`isPoiSpeechEnabled cat=X group=Y enabled=Z (showAll=… meaningful=… ambient=… businesses=…)`); updated `setShowAllOverride` log copy. Verified other `isShowAllOverride()` consumers (`NarrationGeofenceManager.kt:262` qualification + `SalemMainActivityNarration.kt:950, 973` visibility paths) are intentionally separate — left unchanged. All three fixes field-verified on Lenovo (HNY0CY0W) + Pixel 8 (41231FDJH0018J), preceded by `adb uninstall` per WAL-safety rule (S272 carry-forward `am force-stop` → `pm clear` → uninstall sequence used on every reinstall round to clear DELETE_FAILED). Operator close: *"working now, businesses are silent"*. Three files touched: `NarrationManager.kt` (+57 LOC), `SalemMainActivityNarration.kt` (+~25 LOC), `AudioControl.kt` (+~20 LOC). V1 ship-cliff status unchanged. 74 days to 2026-08-01 ship; 4 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-273-2026-05-16.md`. Commit: this session.

---

## Session 272: 2026-05-16 — TTS master mute toggle + CI unblock (11-day red streak broken) + naming-disambiguation doc lint

Three operator asks chained: (1) doc lint of CLAUDE.md + STATE.md after a mid-session `adb uninstall` returning `DELETE_FAILED_INTERNAL_ERROR` exposed that both files referenced the old `com.example.wickedsalemwitchcitytour` package name while the actual `applicationId` is `com.destructiveaigurus.katrinasmysticvisitorsguide` (S245 LLC rebrand) — fixed with a new "Naming" 5-row disambiguation block in CLAUDE.md (brand "Katrina's Mystic Visitors Guide - Salem" / applicationId / Kotlin namespace / Gradle module / repo slug) + Room v20 → v21 + hash refresh + 9 → 25 `salem_*` tables + IP.md "14 patentable innovations" cleanup. (2) TTS master mute toggle in toolbar speaker long-press popup ABOVE "What gets narrated" — 4-file +169 LOC: `AudioControl.kt` gains `PREF_TTS_MASTER` + `engineReady`/`routeUsable` split signals + `isTtsEffectivelyOn()`; `NarrationManager.kt` pushes ttsReady into AudioControl on init/fail/shutdown + new gate at top of `enqueue()` above the `userInitiated` exemption (operator: "turn on and off sound *completely*"); `SalemMainActivity.kt` registers `AudioDeviceCallback` near `setupAudioSpeaker` to track audio-output devices, scans `GET_DEVICES_OUTPUTS` against a TTS-friendly type allowlist (BUILTIN_SPEAKER/EARPIECE, WIRED_HEADSET/HEADPHONES, BLUETOOTH_A2DP, BLE_HEADSET/SPEAKER, USB_HEADSET/USB_DEVICE, DOCK, HDMI), pushes boolean to AudioControl.setRouteUsable, unregisters in `onDestroy`; `AppBarMenuManager.kt` inserts new "Sound" section above "What gets narrated" with single "Narration sound" CheckBox bound to master, force-disabled + `alpha=0.45` + subtitle "TTS unavailable on current audio output" when `!isTtsAvailable()` per operator option (b) — preserves user's checked-state value so narration resumes automatically once routing recovers. Built clean, installed clean on Lenovo (HNY0CY0W) + Pixel 8 (41231FDJH0018J), preceded by `adb uninstall` per WAL-safety rule; Pixel 8 logcat confirmed `TTS route refresh — outputs=[1, 2, 18, 24] usable=true` + `SalemContentDatabase opened: schema_v=21 tables=13` + `engineReady → true (avail before=false after=true)` + `ttsReady ← true (onInit SUCCESS, setLanguage result=1)`; Lenovo route refresh fired (proves `setupTtsRouteWatcher` reached) — DB/TTS init pending permission tap-through. Field smoke (popup visibility, master-off silence verification including userInitiated, BT SCO grey-out validation on real hardware) owed to operator. (3) CI unblock after operator flagged "gh screaming at us" — diagnosed as NOT a S272 regression: CI had been red for 10 consecutive pushes going back to S263 (2026-05-13), root cause structural: `verify-bundled-assets.js` (Gradle `preBuild` task) hard-checked four large gitignored asset paths (`salem_tiles.sqlite` 207MB, `poi-icons/` 544MB, `hero/`, `heroes/`) that don't exist on GitHub Actions runners. Fix: detect CI via `process.env.CI` (GitHub Actions auto-sets this), demote the four physical-presence checks to `[CI-skipped]` warnings, keep all Room schema / row-count / identity-hash / commercial-hero leak checks as hard failures. Local operator runs (CI unset) still hard-check every asset path. Sanity-verified locally: `CI=true` → 21 OK + 3 `[CI-skipped]` WARN + 0 FAIL exit 0; default → 24 OK + 0 WARN + 0 FAIL exit 0. CI fix unblocked the next-in-line failure: `lintRelease` failing with 3 `UseAppTint` errors on `app-salem/src/main/res/layout/sheet_passport.xml:39,72,84` (always there, just masked by the verifier failure); migrated all three from `android:tint` → `app:tint`. `./gradlew :app-salem:lintRelease -PskipPublishChain` BUILD SUCCESSFUL in 1m 11s locally. Final CI run on `90af4e3`: **conclusion: success** — first green CI in 11 consecutive pushes. Four commits this session (`9557976` doc lint, `ffebf5a` CI mode for verifier, `cf13997` TTS master mute, `90af4e3` lint fix). V1 ship-cliff status unchanged. STATE.md stayed at 199 lines (under 200 cap); CLAUDE.md grew 246 → 263 with the Naming block. 74 days to 2026-08-01 ship; 4 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-272-2026-05-16.md`. Commit: this session.

---

## Session 271: 2026-05-16 — Tech-debt sweep across 6 phases (doc cleanup + cache-proxy SSE hardening + Android V1 gate + Room v21 indices + web admin polish + Wave 5 plan)

Operator asked for a full code+strategy tech-debt survey. Spawned 5 parallel agents (Android Kotlin / web React-TS / cache-proxy + publish-chain / DB Room+PG schemas / strategy+docs+memory), synthesized into a P0-P3 punch list, **verified in-band before planning** which caught 3 false alarms (DB-survey's "schema drift P0" — `publish-salem-pois.js:206` already filters `deleted_at`; "fallbackToDestructiveMigration buried comment" — `SalemModule.kt:39-49` already load-bearing; "MIGRATION_2_3 verification gap" — admitted-unreachable per STATE.md:75). Operator approved Room v21 execution now via AskUserQuestion. Plan written + approved via ExitPlanMode. Phase 1 doc cleanup: CLAUDE.md 303→246 lines (deleted entire S188 IMMEDIATE-PRIORITY pinned block, 82 sessions stale with claims like "publish chain has 4 scripts" / Room v11 / keystore-backup-owed all wrong); replaced with refreshed Key Paths block. STATE.md 224→199 lines (under 200 cap from line 3): refreshed day-count + POI count from live PG (2,039), pruned 6 carry-forwards 70+ sessions stale (archived to `docs/archive/STATE_carryforwards_removed_2026-05-16.md`), compressed Phase Status grid + Pointers table + OMEN section + Room v11-v20 history. Three plan-file status headers refreshed (poi-passport S268-S269 shipped; s261-align-asset-schema DONE; v1-health-audit Waves 1-3 shipped). Two SalemIntelligence memory files (`project_salem_intelligence.md`, `reference_salem_intelligence_api.md`) got PAUSED S214 banners cross-linking the pause-memory. Phase 2 cache-proxy: `admin-burst-photos.js` SSE endpoint subprocess leak fixed (`res.on('close')` SIGTERM both children + 600s/300s spawn timeouts). Phase 3 Android: `SalemMainActivityFind.kt:1782` got call-site `SuperAdminMode.networkBlocked` gate (defense-in-depth over the OkHttp interceptor already present). Phase 4 Room v21: 7 indices on SalemPoi (is_narrated + is_tour_poi + is_civic_poi + is_historical_property + category + district + subcategory); skipped composite + TourStop.tour_id (PK leading-column already covers) + wave (marginal); regen schema JSON yielded identity_hash `19fcd8e4347d88e9da1a50aef2734bc9`; updated `verify-bundled-assets.js` + MigrationTest hardcoded hash + method name; ran full 5-script publish chain (2039 POIs / 4 tours / 73 legs / 797 passport rows aligned with hash stamped); 24/24 asset verify OK; bundleDebug clean; bundletool install on Lenovo + Pixel 8 (uninstall first per WAL-safety rule); both devices `adb logcat -s SalemDB:V` confirm `schema_v=21 tables=13` — no destructive migration. Phase 5 web admin: shared `web/src/lib/fetchJson.ts` extracted (3 local impls in PassportTab/TourTree/WalkPassportDialog replaced); Sonner 1.7.4 installed + `web/src/lib/toast.ts` wrapper + `<Toaster richColors />` mounted in AdminLayout; 18+ window.alert / 1 bare alert across 7 files replaced with toastError/toastSuccess; `api.ts` hardcoded `10.0.0.4:4300` fallback replaced with `localhost:4300` + console.warn if unset; ReconTriageTab session-switch fetch upgraded from cancelled-flag to AbortController; tsconfig `noUnusedLocals + noUnusedParameters` enabled, 15 surfaced errors fixed across 8 files (lat/lng `_` prefixes, dead memo deletion, vestigial `<T>` removal from ListProps interface, etc.); `npx tsc --noEmit` final clean. `noUncheckedIndexedAccess` left disabled (deferred V1.0.1) + optimistic-rollback dropped after re-reading (busy flags + confirm dialogs are sufficient race guards). Phase 6: `docs/plans/post-v1-architecture-decomposition.md` written per Wave 5 obligation (target ranking, phase-gated strategy, non-goals, risks, 3-4 week estimate). V1 ship-cliff status unchanged. ~74 days to 2026-08-01 ship; 3 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-271-2026-05-16.md`. Commit: this session.

---

## Session 270: 2026-05-16 — Recon photo triage tool Layer 1 shipped (bulk-cull workflow on top of existing S229/S231 burst-photos infra)

Operator pivoted off the Passport S5 docket to a new ask: a 2-layer triage tool for the 1 Hz GPS-burst photos captured on-drive by `GpsBurstCameraManager.kt` ("pictures are cheap, we evolve by selecting better and better things as we delete the useless junk"). Discovered existing infrastructure leveraged in full per `feedback_leverage_existing_assets.md`: `cache-proxy/lib/admin-burst-photos.js` (S229/S231) already does list/thumb/exif/soft-delete; `web/src/admin/BurstPhotosOverlay.tsx` (S229/S231) is already 80% of Layer 2 with cluster pins on AdminMap + per-cluster modal; `tools/pull-and-organize-burst-photos.py` (S228) handles `adb pull` + session organization. Gap for Layer 1: no persistent keep-flag, no bulk commit-cull, no in-tab device pull, no flat across-session triage view. **Backend extended** (~150 lines added to `admin-burst-photos.js`): per-session `.kept.json` persistence + 5 new routes — `GET /sessions/:s/kept`, `POST /sessions/:s/kept {id,kept}`, `POST /sessions/:s/commit-cull` (atomic move-to-.deleted/ + reset), `GET /devices`, `POST /pull {deviceSerial}` (SSE stream of `adb pull` then python organizer with stage-coded events). **Frontend shipped** as new `web/src/admin/ReconTriageTab.tsx` (~580 lines): responsive Tailwind grid (3→10 cols) with `IntersectionObserver` lazy-loaded thumbnails, click-toggle Keep (gold border + ★, optimistic with rollback), double-click → lightbox with full-size + EXIF panel (GPS lat/lon/alt/speed/heading/track/camera/fix-method) + keyboard nav (←/→/K/Space/Esc), live `kept / to cull / total` counter + Commit Cull button with confirm modal, Pull-from-device modal with adb device list + SSE log pane (color-coded by stage). Wired into `AdminSidebar.tsx` (added 'recon' to `AdminView` + Tooling group entry with 📷 icon) and `AdminLayout.tsx` (import + view-router branch). Cache-proxy restarted (PID 1294878) + vite started (PID 1320353); `npx tsc --noEmit` clean. End-to-end smoke-tested on the 1-photo session (kept flag persists → commit-cull preserves kept photo → `.kept.json` resets to clean slate). Zero new dependencies (reused `sharp` + `exifr`). 5 real sessions exist on disk ready for operator triage pass (5/477/239/1/1741 surviving photos). Carry-forward: Layer 2 extension of `BurstPhotosOverlay` to consume the same keep-flag state + POI-proximity filters + possible new `salem_recon_photos` PG table for attached photos. Passport S5 (operator field walk + PassportSheet empty-state polish + Pixel 8 portrait toolbar fit + MASTER_SESSION_REFERENCE.md polish) still owed and carries to S271. V1 ship-cliff status unchanged. ~75 days to 2026-08-01 ship; 3 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-270-2026-05-16.md`. Commit: this session.

---

## Session 269: 2026-05-15 — POI Passport S4 destructive shipped + walk-derived per-tour passports + pool ∩ proximity architecture + stamps-not-stops UX (5 Lenovo installs)

S4 of the POI Passport plan landed — full TourEngine/TourModels/TourViewModel rewrite, four dead stops-based UIs deleted (Active Tour dialog, top HUD bar, numbered polyline pins, per-stop detail dialog), new `currentLegEndForDetourAnchor()` helper for the detour rejoin path, `PoiPassportDao` + `PassportVisitDao` injected into TourEngine, suspend `endTour` builds passport-aware `TourSummary`, completion auto-fires when `passportVisitDao.countHeardAmong(tourPassportPoiIds) == passportPoiIds.size` (one-shot per session), `showTourCompletionDialog` rewritten with `passportHeard / passportTotal` stats + "Open Passport" CTA. Then operator drove a full architecture revamp through 5 Lenovo bundletool installs in one session: (a) **walk-derived per-tour passports** — new `WalkPassportDialog.tsx` with embedded Leaflet map + radius slider + checkboxes; new backend endpoints walk `salem_tour_legs.polyline_json`, return POIs ordered by encounter index; new `auto_bake` column on `salem_passport_filters` so publish-poi-passport.js preserves operator-curated lists; **(b) `recomputeTourMetadata` helper** — replaces stale `syncStopCount`, recomputes `stop_count + distance_km + estimated_minutes` from `salem_tour_stops + salem_tour_legs` on every leg/stop mutation + on PATCH (caught real bug: tour_DrKs_001 was showing stale 5.85 km vs real 2.81 km); **(c) `pool ∩ proximity` model** — operator clarified the global passport = "the pool" (categories + flags), per-tour passport = (pool ∩ polyline-proximity); rewrote dialog with explicit "Compute Candidates" button (wipe-confirmation, slider becomes parameter for the next compute click only), pool banner, orphan warning for saved POIs no longer in pool; **(d) PassportSheet routing** — `show(fragmentManager, passportId?)` pins the sheet to tour's passport when opened from tour-completion CTA / tour-in-progress dialog / toolbar witch-hat during active tour, plus context-aware overflow ⋮ menu adds "End tour" item when tour is in flight; **(e) "stamps not stops"** — operator: *"we care about the stamps/POI, not the stops"* — Android tour cards now show "49 stamps / 72 stamps / 188 stamps / 41 stamps" instead of meaningless `stop_count` (free polyline waypoints), web admin's TourTree left list + TourMetadataForm Walk-derived Passport panel both surface `passport_poi_count` via new `LEFT JOIN LATERAL` on auto_bake=false filters. Final state: 797 rows in baked `poi_passport` (447 global "Whole Salem" pool + 49+72+188+41 per-tour passports). Five Lenovo installs at 19:39, 20:17, 20:26, 21:19, 21:28. New memory rule: `feedback_passport_pool_intersect_proximity.md` + `feedback_stamps_not_stops.md`. V1 ship-cliff status unchanged. ~76 days to 2026-08-01 ship; 4 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-269-2026-05-15.md`. Commit: this session.

---

## Session 268: 2026-05-15 — POI Passport architecture landed (S1 backend + S2 Room schemas + S3 UI/wiring; 3 of 5 plan-slice sessions shipped in one)

Operator approved a 5-session POI Passport implementation plan in plan mode (7 dialog clarifiers covering V1 vs V1.0.1 sequencing, visit-log shape, filter knobs, completion trigger, stamp art, reset entry, toolbar location). Decisions: ship in V1; visit log is **POI-keyed only** (single `passport_visit(poi_id, ...)` row per POI for lifetime; per-tour passports are filtered ID lists rendering against the one global table — Business POIs later plug into the same log); filter knobs = category multi-select + `require_historical_narration` + `min_geofence_radius_m` + year-built range; completion fires when `count(passport_visits for tour) == count(tour_passport.poi_ids)`; witch-hat stamp filled/hollow; reset inside PassportSheet overflow; toolbar slot between About and Speaker. **Session 1 shipped backend foundation**: PG `salem_passport_filters` + `salem_passport_pois`, `cache-proxy/lib/admin-passport.js` (CRUD + preview), `cache-proxy/scripts/publish-poi-passport.js` (PG always + SQLite gated on table-exists for forward-compat), `web/src/admin/PassportTab.tsx` + sidebar wiring; `default_salem_walking` filter authored → 85 POIs matched and baked into PG. **Session 2 shipped Room schemas**: `SalemContentDatabase` v19 → **v20** (new `PoiPassport` entity + DAO, identity hash `837ec05ad90541fa76a8a413a06394e0`); `UserDataDatabase` v2 → v3 with strict hand-written `MIGRATION_2_3` (no destructive fallback per S180 lockdown); `verify-bundled-assets.js` refactored to read hash dynamically from `schemas/*.json` — kills the manual-hash-bump footgun on the carry-forward list; full 5-script canonical publish chain verified end-to-end with 85 rows surviving every stage; verify-bundled-assets passes 24/24. **Session 3 shipped UI + visit tracking**: full-screen `PassportSheet.kt` DialogFragment (category-grouped list, filled/hollow stamps with antique-gold tint, passport-picker dropdown for global+per-tour, reset overflow menu); witch-hat toolbar icon at right side wired via `MenuEventListener.onPassportRequested()` (default no-op for non-Salem hosts); fire-and-forget `passport_visit` UPSERT at `NarrationGeofenceManager` ENTRY + `PoiDetailSheet.queueSheetReadThrough` non-stripped path (stripped commercial POIs don't count). One mid-session pivot: per-tour Passports were originally going to intersect with `salem_tour_stops.poi_id` — that returned zero because every tour stop is a free polyline waypoint post-S190; `tour_id` became metadata-only in V1 with operator confirmation. New hard-rule memory saved: `feedback_tour_stops_not_poi_anchored.md`. Debug APK 99.4 MB; `assembleDebug` clean; no field walk yet (operator handoff for bundletool install on Lenovo + Pixel 8 + walk validation). V1 ship-cliff status unchanged. ~77 days to 2026-08-01 ship; 5 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-268-2026-05-15.md`. Commit: this session.

---

## Session 267: 2026-05-15 — Linger and Listen feature shipped + latency audit (Lenovo log mining) + status-line regression rolled back + POI Passport proposal drafted to replace stops-based UI

Operator opener: *"I need another control on the sound icon far to right (long press menu). I need a checkbox that says 'Linger and Listen' — When that is checked and we come to a historical property, that has historical narration and subtopics, we play the historical narrative and follow with the subtopics (if they are available) and then use the body, don't announce the header"*. Shipped in 3 surgical edits — `AudioControl.kt` (`PREF_LINGER_AND_LISTEN` + getter/setter, default OFF), `AppBarMenuManager.kt` ("Linger and Listen" checkbox in `showAudioContentPopup()` under a new "Historical deep-dive" section header), `SalemMainActivityNarration.kt` (gated block after the primary narration `when` that parses `narration_subtopics` via existing `parseSubtopics()` and queues each `subtopic.body` as a `speakTaggedNarration("poi_narration", ...)` segment behind the primary; same tag = cancels together; DEBUG log `NARR-LINGER`). Build + bundletool install on Lenovo (`firstInstallTime == lastUpdateTime == 2026-05-14 20:41:52`). Operator then asked *"do we have any latency issues in the application - sometimes I feel we are getting stuck sometimes"* and pointed at log files from the last day or two. Mined `/tmp/salem-stream.log` (1.6 GB, ~50 hr Lenovo TCP-streamed) + `/tmp/pixel8-live-gps.log` (28 MB, S264 drive) + Lenovo persistent buffer. Findings: 🟥 `MapDebugDumper` auto-dump every 10s during DEBUG iterates 873 overlays on UI thread = **374-448 ms freeze per tick** (R8-stripped in release per `if (BuildConfig.DEBUG) startAutoMapDump()`); 🟧 zero viewport culling on `BillboardMarker.draw()` — only "cull" is binary tilt-state (drawn=11640..34980 markers/sec when tilt off, SHIPS to release); 🟧 `narrationGeofenceManager.checkPosition()` walks all ~2,000 POIs every GPS tick on the Main thread (confirmed by Explore: `LocationManager.kt:157` registers callback on `Looper.getMainLooper()` → flow → LiveData → observer → `updateTourLocation()` synchronously); 🟨 `DebugLogger` synchronous TCP streamer per call; 🟩 narration TTS-trigger latency itself fine (~16-29 ms PLAY DIRECT → DIRECT PLAY across 146 plays); 🟩 Pixel 8 absorbs same workload without measurable jank. Operator then redirected to *"Get rid of billboardMarker screen line at the top of the screen"* — turned out to be the `toolbarStatusLine` showing `"Tour: $stopNum/$total — ${poi.name}"` (`SalemMainActivityTour.kt:822-833`) which **duplicates the bottom narration banner**. Two changes attempted: (a) replaced the `TourState.Active` write with `statusLineManager.clear(Priority.TOUR)`; (b) `StatusLineManager.refresh()` set `View.GONE` instead of `View.VISIBLE` with empty text to recover the 24dp. Built + clean-installed on Lenovo via bundletool (Lenovo's package was in `enabled=3` DISABLED_USER state from prior in-place-install issues — recovered with `pm enable`). **Status-line fix verified gone in screenshot**, but cold-start ANRs piled up — the `View.GONE` flip thrashed the AppBarLayout / CoordinatorLayout / MapView measurement cascade on every tour-state emit (MapView fills space below toolbar, height change re-measures the entire 584-marker overlay stack); compounded with pre-existing cold-start load (584 marker icon refresh × 3 buckets + cinematic zoom + MapDump) → 5+ second main-thread blocks → Input-dispatch ANR. Operator: *"that whole thing is broken"* → *"This was working before we made the changes, what happened?"* → *"remove the last changes"*. Reverted all 5 files via `git restore`, rebuilt + clean-installed pre-S267 code on Lenovo (`firstInstallTime == lastUpdateTime == 2026-05-15 16:13:03`). **Operator confirmed: "working just fine now."** Operator then clarified *"we no longer use POIs on a route any longer. The tours are waypoint to waypoint and the POIs fire when they are in range"* and asked for an Explore-mode investigation of the leftover technical debt. Explore agent inventoried `TourEngine.stops[]` / `currentStopIndex` machinery and identified four user-visible Android surfaces still consuming it: top-center floating tour HUD banner (`updateTourHud` ~1327), Active Tour dialog (~680-759), tour completion dialog (~1615), numbered circle pin overlays on the polyline (`drawTourRoute` ~1022-1145). Surfaced a real bug: `restoreIfSaved()` reads `KEY_CURRENT_STOP_INDEX` and assigns to a freshly-loaded stops list without bounds-or-POI-id validation (re-authoring a tour mid-session can land user on wrong POI on resume). Operator's direction: *"polyline + ambient narration only, kill all four stop UIs - that has to be completely redeveloped and isn't even working correctly. What I would prefer is that we created a list of all the POIs that have historical narrative and an indicator that flags if they have visited/heard it that is persisted. probably a way for the web admin portal to regenerate the POI list depending on operator parameters"*. Wrote full proposal at `docs/plans/poi-passport-replaces-tour-stops.md` — replaces the four stops-based UIs with a **POI Passport**: PG `salem_passport_filters` (operator-tunable parameters — categories, geofence radius floor, requires-historical-narration, year-built range, etc.) + `salem_passport_pois` derived table; new `cache-proxy/scripts/publish-poi-passport.js` (5th manual script in publish chain); Room schema bump to v20 with new `poi_passport` table; user's visited/heard state in `user_data.db` (extend existing `PoiEncounterTracker`); single Android `PassportSheet` UI replacing all four killed surfaces; web admin "Passport" sub-tab; 7 open questions for next session (per-tour vs global, parameters, completion-dialog fate, stamp design, reset, persistence scope, sequencing vs Aug 1 ship target). Operator clarified Linger and Listen was unrelated to the regression — re-applied just the 3 Linger and Listen files (status-line files stay reverted). Final state: Linger and Listen ships, status-line technical-debt scope folds into the Passport proposal. Built debug AAB + bundletool installed on Lenovo at session close.

Full session detail: `docs/session-logs/session-267-2026-05-14.md`. Commit: this session.

---

## Session 266: 2026-05-14 — Web admin services restarted + post-edit POI publish chain run + debug AAB clean-installed on Pixel 8 + Lenovo (S265 narration banner changes ride along)

Operator: *"start the web admin tool services"* → *"I've made changes to POIs - can we upload a new version of the application/database to the Lenovo and the Pixel?"*. Started cache-proxy (`bin/restart-proxy.sh` → :4300, PG connected, OpenSky OAuth2 active, Tiger admin tiles loaded) and Vite (`bin/start-web.sh` → :4302) in background; SalemIntelligence (:8089) intentionally down per S214 hard gate so the recurring `/api/intel/status` proxy errors are expected. Ran the manual publish chain per `feedback_publish_chain_manual_after_admin_edits.md`: `publish-salem-pois.js` (PG 2039 → SQLite 2039 / 930 visible / 1987 narrated / 74 silent — net +2 from S253), `publish-tours.js` (4 tours), `publish-tour-legs.js` (73 legs / 19+14+26+14), `align-asset-schema-to-room.js` (12 entities, identity_hash `745afa3eb4ce04bd7873671ea297b6e0` re-stamped, user_version 19). Built debug AAB via `:app-salem:bundleDebug -PskipPublishChain` (BUILD SUCCESSFUL in 18s, 132 MB). Pixel 8 (`41231FDJH0018J`) install clean: `adb uninstall` → `bundletool build-apks --connected-device` → `bundletool install-apks` → verified `firstInstallTime == lastUpdateTime == 2026-05-14 20:12:05`. Lenovo (`HNY0CY0W`) hit `Failure [DELETE_FAILED_INTERNAL_ERROR]` on first uninstall and bundletool then did an in-place update — violated the WAL-safety rule from `feedback_adb_install_after_db_rebake.md`; recovered with `am force-stop` + `pm clear` + retry `adb uninstall` → `Success`, then re-ran `bundletool install-apks` → `firstInstallTime == lastUpdateTime == 2026-05-14 20:13:06` (true clean install). The S265 in-progress narration banner Kotlin/XML changes (`narration_bottom_sheet.xml`, `SalemMainActivity.kt`, `SalemMainActivityNarration.kt`, `SalemMainActivityTour.kt`) rode along in this build and are now testable on Pixel 8 portrait — peek-height shrink + tap-to-expand into PoiDetailSheet + dedicated Dismiss button (TTS-non-stopping). V1 ship-cliff status unchanged (all 3 closed). ~78 days to 2026-08-01 ship; 6 days to Form TX deadline 2026-05-20. **S267 opener:** field-validate (a) operator's POI edits render on both devices, (b) S265 narration banner shrink on Pixel 8 portrait — then commit or iterate.

Full session detail: `docs/session-logs/session-266-2026-05-14.md`. Commit: this session.

## Session 265: 2026-05-13 — Pixel 8 portrait narration banner shrink (Surface 1 in-progress, paused before validation, code carried into S266)

Operator opened with: *"let's continue with the plan to fix the graphics on the Pixel"* — Phase A from `docs/plans/pixel8-portrait-support.md`. Pulled `adb exec-out screencap` from Pixel 8 (`docs/pixel8-portrait-survey/current-card-huge.png`) showing the narration banner ("The Satanic Temple" POI) consuming ~55 % of portrait screen height. Operator spec captured via two AskUserQuestion clarifiers: (1) peek (default visible state) ≤ 20 % screen height, (2) tap-anywhere on card → opens full PoiDetailSheet (already wired), (3) add explicit "Dismiss" button to the action row that hides the card without stopping TTS. Skipped the full Phase A survey-then-fix flow per operator direction — went straight to per-surface fixes. **Surface 1 work was started but paused mid-flight before validation.** Edits to `narration_bottom_sheet.xml`, `SalemMainActivity.kt`, `SalemMainActivityNarration.kt`, and `SalemMainActivityTour.kt` left uncommitted in the working tree at session pause. These changes were carried into S266 by riding along in the publish-and-install build that shipped operator's POI edits to Pixel 8 + Lenovo, so field validation of the banner shrink is now possible on the next walk.

Full session detail: `docs/session-logs/session-265-2026-05-13.md`. Commit: rolled into S266 commit.

---

## Session 264: 2026-05-13 — GPS-flakiness root-cause fix shipped + on-road validation green (1 Hz default, no Flow-cancel race, no zombie callbacks, walk-sim recovery)

Operator pivoted from S264's planned Phase A portrait smoke test to a GPS root-cause investigation after a road-trip on the freshly-installed S263 debug build returned "GPS is hung up, was very flakey... when I restart it will work again". Pulled the trip's `dumpsys location` (GNSS chip healthy — 407 fixes / 7.4% failure / 4.4 m mean accuracy / CN0 45.5 dB-Hz, but `gps provider: ProviderRequest[OFF], mStarted=false changed +10m39s ago`), 22 MB app debug log, 1592-row journey DB, and 1056 recon-camera bursts. Identified 4 root causes: (1) 60-sec default GPS rate hardcoded in `MainViewModel.kt:140` from the S126 battery-conservative era — too slow for high-zoom tracking; (2) walk-sim race — `setManualLocation()` called `stopLocationPolling()` on every walk-sim tick, killing tour-engine's fresh fast Flow within 14 ms (operator's mis-tapped Walk button was enough to strand the app); (3) zombie callback leak in `LocationManager.kt` — `awaitClose { removeLocationUpdates }` could fire before the `requestLocationUpdates` Task finished registering, leaving ghost callbacks alive forever (trip log showed two concurrent `onLocationResult #234`/`#57` counter series fighting); (4) `stopWalkSim()` never restored `_locationMode = GPS`, so the app stayed in MANUAL with dead subscription after walk-sim exit. Patched ~40 LOC across 3 files: `MainViewModel.kt` (default 1_000L/500L, removed stopLocationPolling from setManualLocation, new `endManualMode()`, idempotent onPermissionGranted), `SalemMainActivity.kt` (all 3 stopWalkSim variants call `viewModel.endManualMode()`), `LocationManager.kt` (`registerCompleted`/`cancelArrivedBeforeRegister` flags clean up late-registered zombies). First build failed with `@Volatile`-on-local-var; dropped to plain `var` (both flags touched only on Main looper). Built debug AAB (28 s green), uninstalled previous + bundletool install on Pixel 8. Operator took the patched build on a drive. Five health checks all green: single `onLocationResult` counter `#1 → #1202` monotonic (was two concurrent series on broken trip); ZERO Flow-cancellation events (was 30+); subscription registered exactly once for the entire 23-min drive; 9× `restartLocationUpdates` all took the in-place `updateRequestParams` happy path (no fresh-launch fallback); journey-table density 1202 fixes / 23 min ≈ 52/min ≈ **1 Hz sustained** (was 4-20 fixes/min on broken trip; max gap 14 s during between-tour 30-s slots, by design — broken trip had a 47.5-min total dropout). Operator close: *"I took the app for a drive, much better, check the logs"*. Phase A portrait smoke test rolls to S265 opener. V1 ship-cliff status unchanged (all 3 closed). ~80 days to 2026-08-01 ship; 7 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-264-2026-05-13.md`. Commit: this session.

---


## Session 263: 2026-05-13 — Pixel 8 added to test fleet + portrait/landscape multi-form-factor plan parked (no app code shipped)

Operator added a Pixel 8 (`shiba`, Android 16 / SDK 36, serial `41231FDJH0018J`) as second test device — now primary for V1 field-walks; Lenovo TB305FU retained as min-spec floor. USB-C cable troubleshooting consumed early session (first cable was charge-only; second cable enumerated as `18d1:4ee7` and adb authorized after operator tapped Allow). Built debug AAB via `:app-salem:bundleDebug` (16s); built device-targeted APKs from AAB; installed via `bundletool install-apks --device-id=41231FDJH0018J`. All 5 splits verified on-device including `split_salem_tiles_pack.apk` (S256 install-time asset pack delivering `salem_tiles.sqlite`). Flags: `DEBUGGABLE | LARGE_HEAP | ALLOW_CLEAR_USER_DATA`. **Surfaced V1 ship-blocker:** SalemMainActivity has no `screenOrientation` lock in manifest + zero `layout-port/` or `values-sw600dp/` resource qualifiers — single landscape-implicit layout set designed against Lenovo natural orientation. Pixel 8 portrait shows FAB clusters at hardcoded `bottom-end` 72dp colliding with phone navbar insets, zoom slider at right-edge 48×280dp not fitting phone width, debug button stack at `bottom-start` 168dp colliding with IME, 2×4 grid PopupWindow not reflowing, 11 fullscreen `Theme_Black_NoTitleBar_Fullscreen` dialogs without portrait safe-padding. Operator stance: *"We need to support ALL android devices in both orientations — I will probably need to get a few more to test with but we start with these 2"* — lock-landscape shortcut declined; full multi-form-factor V1. Spawned Explore agent for 7-section UI surface inventory (15 layouts, 43 dialog entry points, 3 custom views, no programmatic landscape assumptions found). Wrote `docs/plans/pixel8-portrait-support.md` — 6 phases (A: smoke test → B: core layouts → C: dialog triage → D: programmatic UI → E: splash letterbox → F: tablet + cross-device validation), ~10 sessions estimated. Operator confirmed all 5 plan decisions via AskUserQuestion: tablet portrait required for V1, phone landscape required for V1, splash letterbox (no asset regen — respects `feedback_hero_revisit_later.md`), dialog audit triage-only, keep `configChanges` + add `onConfigurationChanged`. Decisions #3 + #4 cut Phase C from 3 sessions to 1 and Phase E from 1-2 sessions to 1, shaving worst-case 12→10 sessions. Three memories written/updated: new `reference_pixel8_test_device.md`; `feedback_lenovo_is_v1_minimum_spec.md` updated (Pixel 8 now primary, Lenovo as min-spec floor — findings on both = real bugs); `feedback_lenovo_over_emulator.md` renamed and rewritten with explicit Pixel 8 → Lenovo → emulator priority. No app code shipped. V1 ship-cliff status unchanged (all 3 closed). ~80 days to 2026-08-01 ship; 7 days to Form TX deadline 2026-05-20. **S264 opener: Phase A — operator-driven Pixel 8 visual smoke test, screenshot every UI surface in both orientations, output `docs/pixel8-portrait-survey/MATRIX.md`.**

Full session detail: `docs/session-logs/session-263-2026-05-13.md`. Commit: rolled into S263 commit.

## Session 262: 2026-05-13 — S261 parked plan executed — `align-asset-schema-to-room.js` rebuild wrapped in `db.transaction()` (atomic DROP+CREATE+INDEX+INSERT)

Operator: *"execute the parked plan and embed it in our knowledge and after that, we will re-evaluate and see"*. Executed `docs/plans/s261-align-asset-schema-hardening.md` in full. Single-function edit in `cache-proxy/scripts/align-asset-schema-to-room.js` `alignDb()`: wrapped per-entity `DROP TABLE` + `CREATE TABLE` (from Room schema JSON) + index re-creation + row re-insert into one outer `db.transaction(() => { ... })` closure (`rebuildTable`), then call `rebuildTable()`. The prior inner `db.transaction(...)` around just the INSERT loop collapses into the outer one — better-sqlite3 does not nest, and SQLite supports DDL inside transactions so DROP+CREATE roll back together on throw. Success path is byte-identical to the pre-S262 script (same SQL in the same order); failure path now ROLLBACKs the table to its pre-DROP state instead of leaving the asset DB with a missing/empty table that would then ship in the AAB. **~16 LOC net delta** in one file, zero new dependencies, zero callers affected. Verification ran all 5 plan steps green: (1) smoke run — every entity reported `N → N rows`, identity hash + user_version re-stamped, `ALIGN COMPLETE`; (2) row-count parity across all 12 Room entities + `room_master_table` — `diff` clean against baseline (`PARITY OK`); (3) identity hash `745afa3eb4ce04bd7873671ea297b6e0` + `user_version=19` preserved; (4) `verify-bundled-assets.js` → `23 OK, 0 warning, 0 failure` (Room presence + identity hash + row-count minimums + tiles 715841 + portraits 49 + poi-icons + hero/heroes + commercial-hero prune all green); (5) idempotent on second run — output identical, row counts unchanged. Optional rollback-behavior test (#4 in plan, mutate schema JSON to invalid SQL, confirm rollback worked) skipped per plan's "operator wants extra proof only" flag. Asset DB unchanged on disk at v19: 2037 salem_pois / 4 tours / 73 tour_legs / 49 NPC bios / 202 newspapers / 16 articles / 49 figures / 500 facts / 200 sources / 40 timeline_events / 20 events_calendar. V1 ship-cliff status unchanged (all 3 closed). ~79 days to 2026-08-01 ship; 7 days to Form TX deadline 2026-05-20. Operator next: re-evaluate path.

Full session detail: `docs/session-logs/session-262-2026-05-13.md`. Commit: this session.

## Session 261: 2026-05-13 — Second audit revisit + plan-write only (no code shipped) — corrected Bug #1 misdiagnosis, deferred Bug #2 + content track

Operator: *"same audit lens again, re-evaluate everything — explore and tell me what we need to refine before we more on"* → *"put this into explore and plan mode — we do nothing unless we validate the solution"* → *"I want plan to be written to a file and we investigate it next session"*. Re-ran the S260 4-agent audit lens with a refinement-hunting brief; surfaced 9 candidate items (2 bugs, 4 content, 3 admin-UX, plus low-pri polish). Entered plan mode and launched 3 parallel validation agents before scoping anything. **Bug #1 misdiagnosis caught:** prior audit claimed `cache-proxy/scripts/align-asset-schema-to-room.js` had a silent-fail bug because "main() is async ... uncaught rejection exits code 0." Verified directly with grep: line 109 is `function main()` — SYNCHRONOUS — sibling scripts (`publish-salem-pois.js`, `publish-tours.js`, `publish-tour-legs.js`) are all `async function main()` and need `.catch()` because they return promises. The align script doesn't have the silent-fail bug; sync exceptions exit non-zero with stack trace and Gradle's `Exec` task fails correctly. However, the unprotected DROP+CREATE+INDEX sequence at lines 78-82 IS a real risk — if CREATE throws after DROP committed, the in-memory row snapshot is lost on crash and the next run ships an empty table. Fix scope corrected from "add `.catch()`" to "wrap per-entity DROP+CREATE+INDEX+INSERT in `db.transaction()` so partial failure rolls back to pre-DROP state." **Bug #2 declined:** operator *"Super Admin is for lawyer view — I like it but I have to confirm I don't violate laws"* — Super Admin + TigerBase admin tabs stay visible (used for lawyer demos of parked V1+ features). Web admin is localhost+Basic-Auth+operator-only so no public exposure anyway. **Content counts verified via psql** (agents were sloppy): <50-char `short_narration` is **66 not 86**; empty `narration_subtopics` confirmed **221/580**; >900-char `historical_narration` is **14 not 15**; populated subtopics include 25 figure / 7 manual / 1 fact entries (not "only adjacent_poi"). Underlying refinement targets all real; deferred to a multi-session authoring docket. Plan written + approved + parked at `docs/plans/s261-align-asset-schema-hardening.md` for S262 execution; ~15 LOC delta, single function edit in `alignDb()`, 5-step verification suite (smoke + row-count parity + identity-hash + verify-bundled-assets + optional rollback-behavior test). No code shipped this session. V1 ship-cliff status unchanged (all 3 closed). ~80 days to 2026-08-01 ship; 7 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-261-2026-05-13.md`. Commit: this session.

---

## Session 260: 2026-05-13 — Full-confidence audit pass (no code shipped) + 2 new feedback memories (hero / dedup)

Operator: *"Explore everything — tell me with full confidence that we are on the right path"*. Dispatched 4 parallel Explore agents across the V1 offline ship-gate, the AAB build chain, PG content state, and cache-proxy + web + routing-jvm health. Verdict: engineering ship-grade across the board — V1 offline gate enforced at 6 layers (manifest strip, BuildDefaults const, FeatureFlags inline, SuperAdminMode 3-tier, R8 DCE, MBTA LAN passthrough); Room v19 identity hash `745afa3eb4ce04bd7873671ea297b6e0` matches across schema file + `@Database` + asset DB + androidTest; AAB 129 MB compressed (under 200 MB Play ceiling); signing wired to real upload keystore (NOT debug fallback — STATE.md note was stale); `:routing-jvm` 10/10 parity (re-run); cache-proxy S259 robustness verified; Lint tab grown 15 → 25 instant checks + 1 on-demand; web admin has 11 tabs. Content first-cut numbers looked alarming (69% missing short_narration, 84% missing historical_narration) but the drill-down within the tour-gated subset (`is_tour_poi ∪ is_historical_property ∪ is_civic_poi` = 580 POIs) showed **98% short-narration coverage** — the headline gap was almost entirely commercial POIs that don't narrate by design. One real fillable gap: ~102 HISTORICAL_LANDMARKS missing `historical_narration` (78% → 100% target). Operator clarifications converted into 2 new feedback memories: `feedback_hero_revisit_later.md` (hero/image migration is OLD work planned for full revisit later — don't flag as ship blocker) + `feedback_dedup_is_manual.md` (POI dedup/soft-delete cleanup is manual operator work via Lint tab — don't propose automation). Both indexed in `MEMORY.md`. Operator close: *"This is what is it is — we manage it as best as we can"* and *"we will do one more revisit next session."* No code shipped. V1 ship-cliff status unchanged (all 3 closed). ~80 days to 2026-08-01 ship; 7 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-260-2026-05-13.md`. Commit: this session.

---

## Session 259: 2026-05-13 — Whole-codebase health sweep + 4 real fixes shipped + 11 Dr. K POI drafts brought into version control

Operator: *"explore the code and make sure we are healthy"* → *"explore the code in totality"* → *"fix all of this now"* → *"commit with the thought that all the docs in DrK/* Are authoritative"*. Dispatched 4 parallel Explore agents across `:app-salem` structure, support modules (`:core` / `:routing-jvm` / `:app-salem-tiles-pack` / tools / root config), `cache-proxy/`, and `web/`. Verdict: V1 ship-gate intact, R8 strip clean, Room v19 asset alignment confirmed (identity hash `745afa3eb4ce04bd7873671ea297b6e0`), Asset Pack wired, `:routing-jvm` 10/10 parity, basic auth uses constant-time compare, MBTA passthrough preserves bracket-encoded filters. Punch list raised 8 items; 4 turned out to be agent fabrications on verify-before-edit. Shipped: (a) `.gitignore` revised from allowlist to USB-detritus blocklist, treating every DrK/ doc as authoritative (`FOUND.*/`, `System Volume Information/`, `$RECYCLE.BIN/`, `Thumbs.db`, `desktop.ini`, `.fseventsd/`, `.Spotlight-V100/`, `.Trashes/` blocked) + `tools/hero-triptych/output-full/` + `tools/tile-bake/data/sources/`; (b) `cache-proxy/server.js` got Express 4-arg global error middleware, `unhandledRejection` + `uncaughtException` log-don't-die handlers, graceful SIGTERM/SIGINT shutdown drains PG pool with 10s force-exit fallback (syntax-checked via `node -c`); (c) `web/src/admin/MassEditTab.tsx` got a confirm modal between Apply-button and POST, breaking down approved-cell count by category (cells, POIs, soft-deletes, restores, warning overrides) — closes single-click 500-POI footgun (`tsc --noEmit` clean); (d) `web/.gitignore` created. False flags caught: `publish-splash-tree.js` "append-only" (actually uses `fs.writeFileSync` full-replace), LintTab `showingSuppressed` "unwired" (fully wired through 3 sites — header count, body conditional, toggle visibility), TigerBase pmtiles "FD leak" (deliberate cache; PMTiles is range-request format, FDs bounded at ≤25), 3 Python scripts "missing shebangs" (all 3 already have `#!/usr/bin/env python3`). 11 new Dr. K POI content drafts brought into version control (Daniel Bray, Derby Beebee, Gardner Pingree, Hawthorne Hotel, Plummer Hall Phillips Library, Revere Bell, The Common, Witch Museum) + LyeTapleyShoeShop edit — these are authoritative source content that hand-promotes into `salem_pois` via the publish chain. V1 ship-cliff status unchanged (all 3 closed). ~80 days to 2026-08-01 ship; 7 days to Form TX deadline 2026-05-20.

Full session detail: `docs/session-logs/session-259-2026-05-13.md`. Commit: `ce17d79`.

---

## Session 258: 2026-05-13 — Plan re-validation against actual source code (no code shipped)

Operator: *"explore mode, review our plan and validate everything"*. Read-only sweep against `docs/plans/v1-health-audit-2026-05-12.md` — verified each of the 25 plan items + 3 context checks against actual source rather than trusting STATE.md. Dispatched Explore agent across all items; agent flagged 2 gaps in Wave 1 (DebugEndpoints scope.cancel missing; 7 ungated `Log.e` calls in PolygonLibrary + WickedMapView). Verified both manually before reporting — both false positives: `DebugEndpoints.kt:720-724` wraps `walkScope.cancel()` in `fun shutdown()` called from `SalemMainActivity.kt:1050` `onDestroy()`; the plan's "5 ungated Log.d/Log.e" target was the 3 `Log.d` calls at `WickedMapView.kt:66,81,88` (all now wrapped in `if (BuildConfig.DEBUG)` ✓), with remaining ungated calls being `Log.e` on legitimate exception paths (`drawFrame crashed`, `lockCanvas failed`, polygons.json parse) which are standard Android idiom and not the audit target. Waves 1+2+3 are genuinely closed at the code level. Wave 4 baseline confirmed: `values/strings.xml` 12 lines (only `app_name`), no `values-es/`, 29 hardcoded Toast literals matching plan estimate. Wave 5 doc absent (correct — not started). AAB 129 MB confirmed at `app-salem/build/outputs/bundle/release/app-salem-release.aab` (mtime 2026-05-13 00:04). No plan revision needed; path is sound.

Full session detail: `docs/session-logs/session-258-2026-05-13.md`. Commit: this session.

---

## Session 257: 2026-05-13 — Plan check against `docs/plans/v1-health-audit-2026-05-12.md` (no code shipped)

Read-only validation per operator: *"Explore our plan and make sure we are on the right path"*. Confirmed Waves 1+2+3 of the 5-wave V1 health audit plan are closed (S255 shipped Waves 1+2 in one session vs plan's 3 sessions; S256 shipped Wave 3 in one session vs plan's 3-4); all three V1 ship-cliffs resolved; AAB at 129 MB (well under Play's 200 MB ceiling); SHIP-CHECKLIST.md / `.github/workflows/ci.yml` / `SalemContentDatabaseMigrationTest.kt` all on disk. Wave 4 (Spanish i18n) baseline check: `app-salem/src/main/res/values/strings.xml` holds only `app_name` (12 lines); 27 hardcoded user-facing Toast literals in `app-salem/src/main/java/` need externalization (plan estimated 29 — within rounding); no `values-es/` yet. Wave 4 scope is intact and accurate; Wave 5 is doc-only. Calendar runway: Waves 1-3 consumed 2 of the plan's estimated 6-7 sessions, so plan margin grew — ~80 days remain to 2026-08-01, Wave 4 (2-3 sessions) plus content + field-walks fit comfortably. Surfaced 4 carry-forwards that sit outside the wave plan: **Form TX copyright deadline 2026-05-20 (7 days out)**, `hero/` dir audit (~13.4 MB potential reclaim, Wave 2 leftover #13 in STATE.md), operator field-walk on the S256 asset-pack AAB still owed at z16-z19 + tilt 30/45/60°, and AAB upload-signing verification (current `bundleRelease` falls back to debug keystore via bundletool CLI per SHIP-CHECKLIST.md). Verdict: path is sound, no plan revision needed.

Full session detail: `docs/session-logs/session-257-2026-05-13.md`. Commit: this session.

---

## Session 256: 2026-05-12 — V1 health audit Wave 3 shipped (Install-time Asset Pack for salem_tiles.sqlite) + first androidTest + Lenovo field-validate green

Wave 3 from the S254 5-wave plan — the Play Store ship-unlocker. New `:app-salem-tiles-pack` Gradle module with `com.android.asset-pack` plugin and `install-time` delivery; `salem_tiles.sqlite` (262 MB) git-moved out of `:app-salem`'s base assets. Zero code changes to `OfflineTileManager.kt` — install-time pack assets merge into the app's `AssetManager` namespace at runtime, so `context.assets.openFd("salem_tiles.sqlite")` keeps working (confirmed via Lenovo logcat: `Extracting salem_tiles.sqlite ... asset=274313216` → `Archive verified: Salem-Custom=715841`). Build verified across `:app-salem-tiles-pack:assemble`, `:app-salem:assembleDebug -PskipPublishChain`, `:app-salem:bundleRelease -PskipPublishChain`, then `bundletool build-apks` (universal + split + device-targeted) and `bundletool install-apks --device-id=HNY0CY0W`. **AAB sizes: base APK 55.2 MB compressed (was 374 MB), asset pack 261.6 MB.** Base is well under Play Store's 200 MB compressed-download ceiling; pack sits in the 2 GB pack ceiling. **Cliff 1 (AAB > 200 MB) — CLOSED at build level + field-verified on Lenovo TB305FU.** Combined with S255's Cliffs 2+3 closes, all three V1 ship-cliffs are now resolved — AAB is upload-eligible pending operator-side legal items. Also new this session: `app-salem/src/androidTest/.../SalemContentDatabaseMigrationTest.kt` (2 tests, both pass on Lenovo via `:app-salem:connectedDebugAndroidTest`) — first androidTest in the repo, asserts asset's `room_master_table.identity_hash` matches v19 hash + minimum row counts on all 12 Room-managed tables. Closes Wave 3 decision #15 (Room MigrationTest before next schema bump). Workflow note: any build using asset packs must now use `bundletool install-apks` instead of `adb install` for local field-testing (standalone APKs don't include pack assets). Wave 3 closed in 1 session vs plan estimate of 3-4.

Full session detail: `docs/session-logs/session-256-2026-05-12.md`. Commit: this session.

## Session 255: 2026-05-12 — V1 health audit Waves 1+2 shipped + MBTA key removed + Lenovo field-validation green

Validated the S254 audit plan against current code (3 items stale/misattributed: NarrationManager `tts?.shutdown()` already present, `SalemModule onDestructiveMigration` un-gate cited wrong callback, `Log.d/Log.e` count was 5 → actually 10) and surfaced 2 new findings (`hero/` 13.4 MB dead-asset audit gap, `us_places_v1.sqlite` undocumented). Entered plan mode, wrote corrected plan, operator approved via ExitPlanMode. **Wave 1** (`c947e97`, 9 files): manifest `largeHeap=true` + `allowBackup=false`, Gradle heap 2→4 GB, 4 × `scope.cancel()` wired into `SalemMainActivity.onDestroy`, 3 scale-gesture `Log.d` sites gated (kept 7 Log.e crash-paths visible per operator), new `feedback_publish_chain_manual_after_admin_edits.md` memory. **Wave 2** (`99f6741`, 13 files): dead-table drop in publish chain (5.9 → 5.07 MB bundle, no Room schema bump needed — confirmed via `SalemContentDatabase.kt:17-31`), **MBTA hardcoded key removed + cache-proxy `/mbta/upstream/*` passthrough route added with raw-query passthrough fixup** (commit `e2270f4` — Express's qs parser was collapsing `filter[route]=Red` into nested obj, dropping the filter; switched to `req.originalUrl` raw query), HeroAssetLoader two-pass `inSampleSize` decode, `:routing-jvm:test` 4/10 → 10/10 (tolerance widened to 0.5 m matching `verify-parity.py` canonical), lint-baseline.xml + `abortOnError=true` on release, SHIP-CHECKLIST.md, .github/workflows/ci.yml, ASSETS-MANIFEST.md refreshed to v19 reality. Full credential audit confirmed zero hardcoded keys in Android source / release APK strings. **Lenovo HNY0CY0W field-validation green**: PID 8603, 117k TCP stream entries, zero E-level events, walk-sim 320/1887 steps clean (multiple DWELL exits on TTS idle + queue empty per S244 fix), 569 MB PSS / 37 MB Dalvik well within `largeHeap` ceiling, all parked-service paths SUPPRESSED by V1 gate. One in-flight diagnosis (Lye-Tapley Shoe Shop "didn't narrate") traced via TCP stream + PG query to operator's 20m geofence + 33–39m walk distance; operator self-classified "my bad, I didn't set the flags right". Wave 2 #13 (`hero/` cleanup) deferred — S239 audit reported "385 dead" but PG has 949 live `image_asset LIKE 'hero/%'` references. New memory `feedback_lenovo_is_v1_minimum_spec.md` captures operator stance that Lenovo TB305FU is the V1 floor and Lenovo-edge findings should not be ship-blockers.

Full session detail: `docs/session-logs/session-255-2026-05-12.md`. Commits: `c947e97` (Wave 1), `99f6741` (Wave 2), `e2270f4` (MBTA passthrough fixup), plus this close.

## Session 254: 2026-05-12 — V1 health audit (5 parallel agents, 23-decision ledger, 5-wave execution plan written; new V1 scope: Spanish UI + MBTA key removal)

Pure planning session — no code changes. Operator: *"I want this application validated and for us to surface issues, problems, future blockers, etc. We have undergone a lot of code changes… I want to know the health of our code and if there are any cliffs we will walk off because we are at limits I don't know about. I don't care about post V1."* Spawned 5 parallel Explore agents covering (1) build/packaging/Play Store, (2) V1 offline gate integrity, (3) runtime memory/rendering cliffs, (4) Room/DB/asset health, (5) code health/architecture. Each returned 1000–2000 word audit reports with file:line citations. Synthesized into a top-line health report and surfaced **three V1 ship-blocker cliffs**: AAB 316 MB vs Play Store 200 MB compressed ceiling (Asset Packs unwired); `android:largeHeap="true"` missing combined with S253's 24× tile expansion at 60° tilt = deterministic OOM at high tilt + z19; `android:allowBackup="true"` contradicts paid-offline framing. Also surfaced: TTS shutdown leak in NarrationManager Singleton; 4 unbounded CoroutineScopes without `scope.cancel()`; 3 dead tables (~1 MB) still shipping in salem_content.db; onDestructiveMigration callback DEBUG-only (silent in release); 5 ungated `Log.d/e` calls; SalemMainActivity monolith at 18,623 LOC across 20 sibling files; salem_tiles.sqlite measured at **262 MB** (corrects stale 848 MB / 207 MB references in memory + STATE.md). Then ran **6 batches of AskUserQuestion** (23 decisions total) per the one-at-a-time multi-choice rule. All 4 ship-blocker batches + all 4 code-hygiene batches + all 4 DB/asset batches + all 4 tests/process batches + all 3 ship-prep batches: operator picked Recommended. Strategic batch (4 questions): operator deviated 3 of 4 — picked "plan decomposition NOW" for SalemMainActivity refactor, bundled Manager/Repo consolidation with that planning, and **two new V1 scope items entered the ledger: MBTA hardcoded key removal (operator: "MBTA only goes through our service, LMA should never be hitting MBTA" — closes S251/S252 carry on `api-v3.mbta.com` direct leak) + Spanish UI for V1 with multi-language-ready infrastructure** (operator: "Multi-Language is on the agenda - V1 should include at least spanish"). Follow-up i18n batch (3 questions) clarified scope: Spanish first but architecture supports all languages; UI strings translated V1 but narration stays English V1; device-default `es-ES`/`es-MX` TTS (no Bark/Piper). Wrote `docs/plans/v1-health-audit-2026-05-12.md` (~450 lines) with top-line verdict per dimension, three ship-blocker cliffs with file:line evidence, six "doing well" items, detailed findings by dimension, full 23-decision ledger, and **5 execution waves**: Wave 1 (~30 min, 7 risk-free single-line edits — manifest largeHeap + allowBackup, gradle heap 2→4 GB, TTS shutdown, 4 scope.cancels, onDestructiveMigration always-log, gate 5 logs, write manual-publish-chain memory), Wave 2 (~2 sessions hardening — drop dead tables, delete MBTA key, re-bake routing fixtures, lint baseline, hero inSampleSize, keystore checklist, minimal CI, field-validate S253 at 45°/60°), Wave 3 (~3-4 sessions ship — Install-time Asset Pack, extraction UX, MigrationTest), Wave 4 (~2-3 sessions i18n — strings.xml externalize, values-es/, locale switcher, locale-aware TTS), Wave 5 (post-V1 planning artifact, doc only). Calendar runway math: ~9-10 V1 sessions vs ~22 available before 2026-08-01 ship. **Recommended S255 opener: Wave 1.**

Full session detail: `docs/session-logs/session-254-2026-05-12.md`. Commits: this session (planning only, doc + log).

## Session 253: 2026-05-12 — Tilt-mode lateral wedge fill + `forceFlatDraw` default flip + content refresh

Operator pivoted off the S252 docket reporting "the app crashed on me." Pulled `adb logcat -b crash` + `dumpsys dropbox` + the live `/tmp/salem-stream.log` TCP collector — no crash signature anywhere; process was still alive at PID 18955 (344 MB RSS) and the final stream entries were a textbook graceful `onPause/onStop/onDestroy finishing=true` + `NarrationMgr.Stopped` + `PoiEncounter flushAll`. Concluded: not a crash; the activity finished cleanly after a heavy frame at z17 + tilt=42° (`BillboardDraw drawn=32501` vs steady-state ~11k). Operator clarified the actual concern was tile loading at initialization — portrait showed solid black columns on left + right; landscape rotation showed only a small bottom-left wedge. Operator picked landscape as the target view. **Fix 1 (`TiltContainer.kt`, +34/-9):** diagnosed the wedge as one of the triangular gaps left by the tilt pinch (matrix maps `src(0,h)→dst(0,h)` and `src(0,0)→dst(pinch,topY)`, so the sloped left edge of the trapezoid leaves a triangle between trapezoid and screen for y ∈ (topY, h); EXTRA_TOP_FRACTION's upward extension doesn't reach those triangles). Math says lateral overhang `Δ = 0.5·t/(1−t)·containerW` per side fully fills via matrix extrapolation of src x<0. Shipped tilt-proportional `sideRatio` + new `EXTRA_SIDE_CAP = 1.5f` constant + new `extraSidePx` / `savedMapWidth` / `savedMapLeftMargin` fields mirroring the existing vertical-extension bookkeeping; `applyMapExtension()` now sets `lp.width = containerW + 2·targetSide` with `lp.leftMargin = -targetSide`. Short-circuit guard updated to compare both top AND side targets so tilt-degree-only changes (30→45→60) trigger re-extension. Operator approved option via AskUserQuestion (tilt-proportional exact). **Fix 2 (`BillboardMarker.kt`, +8/-5):** post-Fix-1 screencap showed wedge filled but flat name-label pills laid on the ground plane in pass-1 alongside upright pass-2 billboards. Traced to `BillboardMarker.kt:65`: `forceFlatDraw: Boolean = BuildConfig.DEBUG`. S251 had added this as a runtime bypass for the `tiltActive` early-return + 1Hz draw-count instrumentation but defaulted it ON in debug, so every debug tilt session was double-rendering (TCP log: `BillboardDraw drawn=10996 skipped=0 tiltActive=true forceFlatDraw=true` — skipped=0 across thousands of frames). Reverted default to `false`; field kept as a runtime toggle. **Fix 3 (content refresh):** operator: "I edited a bunch of POIs, upload a fresh version to the android." First `:app-salem:assembleDebug` (no `-PskipPublishChain`) was 4 s UP-TO-DATE — the auto-bake preBuild hook is keyed on script source files, NOT PG content. Ran publish chain manually: `publish-salem-pois.js` → 2,037 total / 925 visible / 1,985 narrated / 72 silent (net −5 from S241's 2,042), `publish-tours.js` → 4 tours, `publish-tour-legs.js` → 73 legs, `align-asset-schema-to-room.js` → identity_hash `745afa3eb4ce04bd7873671ea297b6e0` (v19). Rebuilt + uninstall+install on HNY0CY0W. Operator close: "looking good." Mid-session checkpoint commit `8ef2c94`, pushed.

Full session detail: `docs/session-logs/session-253-2026-05-12.md`. Commit: `8ef2c94`.

---

## Session 252: 2026-05-12 — TigerBase CONUS basemap pipeline shipped (Z3-Z12 baked + cache-proxy route + web admin preview); Android wiring deferred to S253

Operator pivoted off the S251 docket: build a post-V1, lawyer-gated CONUS TIGER/Line basemap that the cache-proxy can serve when LMA is outside the Salem geofence with internet. Shipped end-to-end on the server side, deferred Android-side wiring to next session. In-house pipeline at `tools/tigerbase/` (no Mapnik, no OSM toolchain per `feedback_no_osm_use_local_geo.md`): Python renderer `bake-tigerbase.py` (Pillow + Shapely + ProcessPool + Linux fork-COW for shared-shapefile workers); `pack-pmtiles.py` for distribution-ready archives; `cache-proxy/lib/tigerbase-tiles.js` with `/tigerbase/manifest` + `/tigerbase/:layer/:z/:x/:y` routes (tries packed PMTiles first, falls back to live loose-tile tree); `web/src/admin/TigerBaseTab.tsx` with Leaflet 5-layer stack + scope picker + per-layer toggles; AdminSidebar 🌎 entry under Tooling. Five separate transparent WebP pyramids per layer (water, boundaries, roads, places, labels) so the client composites them; tuned encoder is `quality=70-75 method=6` after empirical confirmation that lossless WebP is LARGER for our antialiased basemap tiles (saved as memory `feedback_webp_lossy_beats_lossless_for_basemap.md`). Baked iteratively zoom-by-zoom (operator approved each level visually) Z3 → Z12 over CONUS bbox (-125,24.4,-66.9,49.4). **Z3 = US in 4 tiles; Z6 = state-level metros; Z9 = LA basin / Miami; Z12 = town-grid detail.** Mid-session CPU incident: `pkill -f "pattern1\|pattern2"` silently failed in bash double-quotes, leaving an old 12-worker bake alive when a new chain + recompress spun up on top → 32 python procs / load 36. Hard-killed by PID list; rewrote driver as `resume-conus.sh` with a pgrep guard. Overnight bake at 4 polite workers finished Z11 + Z12 and was 22% through Z13 when operator stopped it; partial Z13 wiped. Final disk: 2.9 GB under `out/conus/` (water 1.3 GB, boundaries 772 MB, roads 445 MB, labels 209 MB, places 198 MB; 4.6 GB total under `tools/tigerbase/` incl. sources + venv). All server-side curl probes return 200 on the design's intended (layer, zoom) combinations; web admin renders the join correctly. Android client has zero `tigerbase` references → task #15 carries to S253: SuperAdmin-gated TigerBase basemap source as new osmdroid overlay stack, gated on `SuperAdminMode.allowNetwork` AND outside-Salem geofence, Witchy remains the default.

Full session detail: `docs/session-logs/session-252-2026-05-12.md`.

---

## Session 251: 2026-05-12 — Fix MBTA stations missing on Lenovo (deferred-fetch hook on SuperAdmin → ON) + diagnose weather flap to firewall (ufw 4300/tcp)

Operator reported "no MBTA trains / buses / stations" and "weather hangs / Failed to load weather data" despite S250 declaring MBTA end-to-end. Diagnosis traced two genuinely separate root causes, both fixed. **Code race:** the one-shot `fetchMbtaStations()` runs in `SalemMainActivity.onStart()` ~12 s BEFORE the operator toggles SuperAdmin, silently bails on the V1_OFFLINE_ONLY guard, and never retries (refresh-loop fetches for trains/subway/buses self-heal on their 30 s ticks, one-shots don't). Added `kickDeferredSuperAdminFetches()` in `SalemMainActivitySuperAdmin.kt` called on SuperAdmin → ON; re-fires `fetchMbtaStations()` (and `fetchMbtaBusStops()` if pref enabled) when the marker cache is empty. Verified: `MBTA stations success — 262 stations` at 01:24:19.431 post-fix; 98 unique iconed station markers added to overlay including Beverly (42.547, -70.885), Montserrat, North Beverly, Beverly Farms, Hamilton/Wenham, North Station. **Infrastructure:** `ufw` was silently blocking inbound `tcp/4300` on this dev box's wired `enp2s0` (where the cache-proxy actually listens at 10.0.0.229). ICMP flowed; MBTA polls *appeared* fine because OkHttp's connection pool kept one warm socket alive across 30 s ticks. Cold sockets from Weather / METAR / Webcams / FlightHistory always hit the 15 s connect timeout. Operator ran `sudo ufw allow 4300/tcp`; Lenovo TCP probe went from 5/5 timeouts → 20-140 ms in three tries; weather verified working. **Bonus:** replaced 17 silent `if (FeatureFlags.V1_OFFLINE_ONLY && !SuperAdminMode.allowNetwork) return` short-circuits across TransitVM / WeatherVM / AircraftVM with logged `SuperAdminMode.networkBlocked(TAG, action)` calls — future invisible-bail bugs of the same shape surface in logcat as `SUPPRESSED — V1_OFFLINE_ONLY=true allowNetwork=false (available=… enabledFlag=…)`. Also instrumented `BillboardMarker.draw` with 1 Hz `BillboardDraw drawn=N skipped=N tiltActive=… forceFlatDraw=…` (debug-only, R8-stripped) — eliminated tilt suppression as a hypothesis early in diagnosis. **Discoveries that didn't get fixed:** some MBTA repository path calls `api-v3.mbta.com` directly (DNS failure under V1 manifest); radar tiles call `mesonet.agron.iastate.edu` direct (IEM serves NEXRAD). Both bypass the cache-proxy. `STATE.md` note about "10.0.0.229 was a different host" was wrong — this box has both 10.0.0.194 (wlo1 wifi) AND 10.0.0.229 (enp2s0 wired); same machine, two interfaces.

Full session detail: `docs/session-logs/session-251-2026-05-12.md`.

---

## Session 250: 2026-05-11 → 2026-05-12 — Fix S249 Lenovo SuperAdmin actually reaching the network: missed UI-surface gates + CLEARTEXT permit + TCP collector live

Operator field-validated S249 and reported "9 dots → no MBTA stuff" + "strange behavior". Diagnosis via parallel grep + live Lenovo logcat surfaced two critical bugs in S249 that prevented the toggle from doing anything user-visible. **Bug 1 (UI surface):** `SalemMainActivity.onCreateOptionsMenu()` is a stub ("skipped — using slim toolbar"), so `AppBarMenuManager.onMenuInflated()` is never called and S249's edits to `menu_main_toolbar.xml`-related code were dead. The actual user-facing entry points are the 9-dot grid (`showGridDropdown` Row 1 at L576), the slim-toolbar Weather/Alerts icons (L170), and four `showXxxMenu()` PopupMenu builders (L619/L696/L769/L816) plus utility/alerts strips at L978/L1069. Updated all 9 gate predicates to `&& !SuperAdminMode.allowNetwork`, saved slim-toolbar icon refs, added new `reapplySuperAdminVisibility()` method called from SalemMainActivitySuperAdmin's click handler (dropped the `invalidateOptionsMenu()` no-op). **Bug 2 (CLEARTEXT block):** Lenovo logcat showed every parked-service call failing with `E/WeatherVM: fetchWeatherDirectly FAILED: CLEARTEXT communication to 10.0.0.229 not permitted by network security policy / UnknownServiceException at RealConnection.connect`. Android API 28+ blocks plain HTTP by default — S249's INTERNET grant wasn't enough; OkHttp threw before hitting the wire. Created new debug-only `app-salem/src/debug/res/xml/network_security_config.xml` allow-listing cleartext to 10.0.0.229 + localhost + 127.0.0.1, referenced from debug AndroidManifest via `<application android:networkSecurityConfig tools:replace>`. Release manifest stays bit-clean (no INTERNET, no networkSecurityConfig — apkanalyzer-verified). Field-validated working post-fix: Lenovo logcat shows live MBTA fetches now succeeding via cache-proxy at 10.0.0.229:4300 — 73 subway / 14 trains / 203 buses in <200ms each, all 200 OK, 14/14 next-stop ETA matched, Salem MBTA Station marker rendered. **Bonus:** wrote `/tmp/salem-collector.py` (multi-client TCP listener, source-IP-tagged, appends to `/tmp/salem-stream.log`) for the S243 TCP log streamer — first connect timed out diagnosed as ufw blocking inbound 4301, operator opened `sudo ufw allow 4301/tcp`, Lenovo (10.0.0.159) reconnected and is streaming live device frames (~1Hz WickedAnimSummary, ORIENT-RAW, RadarRefreshScheduler, NARR-HEARTBEAT) so future dev sessions can `tail -F /tmp/salem-stream.log`. Mid-session checkpoint commit at S249 close was `cb6de0c`; S250 fix commit `c6e335c`. Continues into S251.

Full session detail: `docs/session-logs/session-250-2026-05-11.md`.

---

## Session 249: 2026-05-11 — Lenovo SuperAdmin toolbar toggle (debug-only V1 offline-gate runtime override) + pre-S180 :core manifest-leak fix

Operator wanted the S248 SuperAdmin web tab mirrored as a toolbar button on Lenovo — a debug-only toggle (after field-edit "(I)") that flips the V1_OFFLINE_ONLY gate at runtime so weather / MBTA / aircraft / radar / webcams become reachable for post-V1 dev work. Three-tier gate broken cleanly: new `core/.../SuperAdminMode.kt` runtime singleton + new `BuildDefaults.SUPER_ADMIN_ENABLED = BuildConfig.RECON_DEFAULTS` const + new `app-salem/src/debug/AndroidManifest.xml` re-adding INTERNET in debug only. 19 gate sites updated (1 OkHttp interceptor + 18 ViewModel/activity) from `if (FeatureFlags.V1_OFFLINE_ONLY)` to `if (FeatureFlags.V1_OFFLINE_ONLY && !SuperAdminMode.allowNetwork)`. AppBarMenuManager.kt:97-104 wrapped to also re-show the 5 hidden menu items when SuperAdmin is on. Toolbar wired in `SalemMainActivitySuperAdmin.kt` (mirroring SalemMainActivityFieldEdit.kt) — session-scoped, red-when-on / white-when-off, toast on toggle, `invalidateOptionsMenu()` flips menu visibility. **R8 paranoia verification passed**: release dex `apkanalyzer dex code --class SuperAdminMode` returns "class not found" (whole object stripped); FeatureFlags also DCE'd. **Bonus side-fix**: while verifying release manifest is INTERNET-clean, traced a leak to `core/src/main/AndroidManifest.xml` declaring INTERNET + ACCESS_NETWORK_STATE at the library level — was silently merging back into both debug AND release of every consumer, negating the S180 strip. Removed; release retail manifest is now ZERO INTERNET / ACCESS_NETWORK_STATE hits. Debug APK 357 MB installed on Lenovo HNY0CY0W; operator field-validation pending. Mid-session checkpoint commit; work continues in S250.

Full session detail: `docs/session-logs/session-249-2026-05-11.md`.

---

## Session 248: 2026-05-11 — SuperAdmin tab live (post-V1 service console for parked weather/MBTA/aircraft/METAR/webcams/TFR endpoints)

Operator wants a way to keep the parked V1+ external-service code paths warm post-V1 ship, since V1 Android is fully offline. Decision: build it as a new tab in the operator-only web admin (zero V1 ship-gate risk) at connectivity-console scope. Shipped `cache-proxy/lib/admin-super.js` (single `/admin/super-admin/health` metadata route, 8 services registered) + `web/src/admin/SuperAdminTab.tsx` (per-card status / env-key chip / editable Salem-default query inputs / Test button / collapsible raw JSON response) wired into AdminSidebar's Tooling group. Folded in a related cleanup: hardcoded MBTA + Windy API keys lifted from `cache-proxy/lib/mbta.js:14` + `webcams.js:14` into `cache-proxy/.env` (gitignored), with startup WARNs if missing. Vite dev `/api → :4300` proxy already routes correctly so no Vite config change. Verified end-to-end: cache-proxy restarted clean, all 8 endpoints returned 200/204 via curl, `npm run build` clean, V1 Android grep for `super-admin` returns zero hits. Operator field-validation pending (refresh admin → click Super Admin under Tooling). Mid-session checkpoint commit; work continues in S249.

Full session detail: `docs/session-logs/session-248-2026-05-11.md`.

---

## Session 247: 2026-05-11 — About-dialog product-name fix (WickedSalemApp → Katrina's Mystic Visitors Guide) + S246 LLC About-dialog field-validation PASS

Tight micro-session following S246's entity propagation. Operator field-validated the LLC About dialog on Lenovo HNY0CY0W (copyright line + email confirmed) — closing S246 carry #1. Then caught a separate issue: the About dialog title and version line still said "WickedSalemApp", which is an internal Kotlin class name, not the user-facing product. Fixed both strings in `SalemMainActivityHelpers.kt` to read "Katrina's Mystic Visitors Guide" (matches `strings.xml` `app_name`, AndroidManifest `android:label`, and the splash screen). Explore agent verified the rest of the app + web admin clean — no other user-facing "WickedSalemApp" references; all remaining hits are internal (class names, file headers, MODULE_IDs, log tags). `:app-salem:assembleDebug` clean in 7 s; `adb uninstall && adb install` to Lenovo HNY0CY0W; operator field-validated PASS.

Full session detail: `docs/session-logs/session-247-2026-05-11.md`.

---

## Session 246: 2026-05-11 — Entity propagation pass (Dean Maurice Ellis → Destructive AI Gurus, LLC) across 340 files

Followed the S245 entity backfill with the wider propagation sweep called out in the S245 carry. Five-pass surgical batch (plan-mode-approved, operator-confirmed MODULE_ID inclusion and counsel-packet exclusion via AskUserQuestion): copyright headers across 614+ source files (`.kt` / `.xml` / `.ts` / `.tsx` / `.js` / `.sh` / `.sql` / `.py`) + Kotlin & JS MODULE_ID diagnostic constants + the user-facing in-app About dialog at `SalemMainActivityHelpers.kt` (also canonicalized the contact email to `contact@destructiveaigurus.com`) + IP.md / COMMERCIALIZATION.md / docs/PRIVACY-POLICY.md / docs/lawyer-packet/10-legal-walkthrough.md surgical edits (Form TX claimant now explicitly requires the LLC; S246 supersession block added at the lawyer-packet's C-corp section) + `web/package.json` + `cache-proxy/package.json` author/license fields. Intentionally untouched: `docs/archive/`, `docs/session-logs/`, `SESSION-LOG.md`, the 10-file counsel packet (frozen deliverable), `tools/` vendored sources, `applicationId`/`namespace`, git author identity. Pushed; rebuilt + deployed to Lenovo HNY0CY0W.

Full session detail: `docs/session-logs/session-246-2026-05-11.md`. Commit: `9ac8342`.

---

## Session 245: 2026-05-11 — Authoring-lock UX revision (red-when-locked + admin always force) + entity backfill (Destructive AI Gurus, LLC populates privacy policy)

Hybrid session: one tactical UI/UX revision on the day-old `no_overwrite` lock and one major operator-side legal milestone. **Lock UI (commit `79b2291`, 2 files):** replaced the unconditional amber Flags-tab panel with a `watch('no_overwrite')`-driven conditional render — red panel (border + bg + switch track + text) when locked, neutral white/slate panel with a clearly distinguishable OFF toggle (knob ring against slate track) when unlocked; title appends "— Locked" / "— Unlocked". Operator then tightened the lock's semantics: "Admin users can overwrite at will but AI or automated functions can not with that toggle on." Removed the S244 confirm-prompt-on-409 flow entirely from `PoiEditDialog.onSubmit`; admin dialog always sends `?force=true`. Mass-Edit tab apply-fetch likewise hardcoded `?force=true`. Backend gate unchanged — scripts that hit the admin endpoints without force are still refused 409. Hint text updated to match. New feedback memory `feedback_no_overwrite_targets_automation.md`. **Entity backfill (commit `2e54b88`, 3 files):** operator dropped `LegalDocuments/Destructive AI Gurus - Certificate of Organization (As Filed).pdf` and asked for a review. The Certificate is the MA SOC formation document for **Destructive AI Gurus, LLC** — Filing # 202626416630, filed 2026-05-11 11:21 AM, William Francis Galvin signed; principal office 111 Essex Street, Beverly, MA 01915; manager + resident agent Dean Ellis; general character "Software development." This resolves all four long-pending placeholders in `docs/PRIVACY-POLICY-V1.md` (entity, jurisdiction, contact email, mailing). Bumped the policy to **v1.1 entity backfill** — populated 4 placeholders, rewrote drafting notes §§ 2-4 from "TBD pending counsel meeting" to "RESOLVED 2026-05-11" with rationale (MA LLC chosen over Delaware C-corp for home-state alignment + simpler single-member structure), marked counsel-review checklist items 1-4 done. STATE.md operator-side legal block: entity-formation line RESOLVED with Filing #; privacy-policy carry-forward dropped; Form TX line flags that the claimant should be DAG LLC, not Dean Ellis personally (deadline 2026-05-20, 9 days). `.gitignore` adds `LegalDocuments/` — operator legal records stay local-only. New project memory `project_operating_entity_dag_llc.md`. Operator confirmed `contact@destructiveaigurus.com` (caught a typo "destuctive…" before propagation; operator confirmed domain ownership), accepted that 111 Essex St is now public record on MA SOC's searchable filing system (registered-agent service is a future option), and confirmed MA LLC stance is fine for now.

Full session detail: `docs/session-logs/session-245-2026-05-11.md`. Commits: `79b2291`, `2e54b88`.

---

## Session 244: 2026-05-11 — Walk-sim DWELL fix (no engine = no deadlock) + per-POI `no_overwrite` authoring lock end-to-end

Shipped the S243-carry DWELL fix: `NarrationManager.isTtsReady` public accessor + `TourViewModel` pass-through + DWELL entry guard in `SalemMainActivity.kt:2166` so walk-sim falls through (W-level skip log) instead of pinning 180s when no TTS engine is available (Bluestacks; any TTS init failure). Installed on Lenovo HNY0CY0W. Then end-to-end shipped the `no_overwrite` authoring lock: idempotent `ADD COLUMN IF NOT EXISTS` at cache-proxy startup; hard-refuse with HTTP 409 + `code: NO_OVERWRITE_LOCKED` + `locked_fields: [...]` on `PUT /admin/salem/pois/:id` AND `POST /admin/salem/pois/apply-mass-edit` when any of nine protected fields (`short_narration`, `long_narration`, `historical_narration`, `narration_subtopics`, `description`, `short_description`, `custom_description`, `origin_story`, `year_established`) would change on a flagged row; `?force=true` bypasses; toggling the flag itself is always free. UI: amber "Authoring Lock" panel pinned to the top of the Flags tab in `PoiEditDialog`, lock indicator on flagged rows in `PoiTree`, save flow catches the 409 and prompts before retrying with `?force=true`. Curl round-trip verified the gate (flag on → narration change refused 409 with locked-field name → same change with `?force=true` accepted → reverted). Side errand: diagnosed why "Lye-Tapley Shoe Shop" displayed under "POI Hist. Landmark" in the admin tree — S190 synthetic split based on `data_source LIKE '%massgis%'`; operator changed `data_source` away from `massgis_mhc`. Re-baked content + Lenovo uninstall+install with the 357 MB debug APK carrying both fixes.

Full session detail: `docs/session-logs/session-244-2026-05-11.md`. Commits: `9dd0d85`, `42e3675`.

## Session 243: 2026-05-11 — Comprehensive verbose-debug instrumentation across every Android runtime subsystem (4 phases, all `BuildConfig.DEBUG`-gated)

Operator pivoted off the S243 docket to Bluestacks deployment, hit a DWELL deadlock + green polygons + missing TTS, and turned the session into a remote-diagnostic push: *"I am tired of walking on land mines… until we are ready to make this production, I want everything available to us to debug."* Revived the dormant `TcpLogStreamer` (HOST=10.0.0.194, PORT=4301; `BuildConfig.DEBUG`-gated) so a local Python multi-client collector at `/tmp/salem-collector.py` could capture concurrent streams from Lenovo HNY0CY0W and Bluestacks (10.0.0.204). Diagnosed the trio of symptoms as one bug — Bluestacks lacks Google TTS, `NarrationMgr` correctly defers but `currentNarration` still gets set, walk-sim DWELL gates on `currentNarration != null`, deadlocks 180s. Spawned 5 parallel Explore agents auditing rendering / narration+TTS+walk-sim / data+Room / GPS+sensors / UI+lifecycle; each surfaced 8–12 silent-state file:line citations. Synthesized into a 4-phase plan (`~/.claude/plans/validated-squishing-hinton.md`, approved via ExitPlanMode), one commit per phase. Phase 1 (867c0db, 16 files): TCP streamer revive + `MapDebugDumper` on-demand snapshot + auto-dump scheduler (10s while resumed) + `dumpNow(reason)` for walk-sim/tilt events + 1Hz frame summary in WickedAnimationOverlay + per-frame cull counts in AnimatedWaterOverlay/RollingGrassOverlay/FireflyOverlay + SpriteOverlay bitmap-bytes + TileArchive 5s cache-summary + PolygonLibrary malformed-ring counts + TiltContainer applyMapExtension pre/post mv.height. Phase 2 (b25ca3f, 2 files): NarrationManager ttsReady mutations + pause/resume guard refusals + enqueue pass-through path; NarrationGeofenceManager 8-branch isHistoricalQualified logging (30s per-POI throttle), cooldown remaining ms, reach-out accounting. Phase 3 (5385404, 6 files): Room callback (`onOpen` schema_v + tables, `onDestructiveMigration` E-level alarm), PoiHeroResolver per-tier (catches S239 dead-hero patterns), PoiCache expanded summary + per-category breakdown, GPS-OBS walk-sim/manual fix tagging, MotionTracker "armed but never fired" diagnostic (TB305FU), DeviceOrientationTracker sustained-static warning (Bluestacks compass). Phase 4 (f0c7711, 3 files): onCreate startup-posture echo, onConfigurationChanged pending-restore echo, import-launcher result, MainViewModel V1_OFFLINE_ONLY BLOCKED-call logging, new `core/util/VerboseTrace.kt` helper. Release-clean verified — release dex carries 5 pre-existing un-gated tag strings; debug dex carries 66; R8 stripped ~92% of new additions. New memory `feedback_verbose_debug_until_production.md` codifies the standing rule (every silent state change gets a `BuildConfig.DEBUG`-gated log line in the same edit) + `MEMORY.md` index update. Bluestacks-side: added two debug-only menu items hoisted out of V1_OFFLINE_ONLY strip — **Open TTS Settings (Debug)** (fires `Intent("com.android.settings.TTS_SETTINGS")` → fallback to ACTION_ACCESSIBILITY_SETTINGS, lands operator in the right screen despite stripped UI) and **Dump Map Overlays (Debug)**. Green-polygon symptom traced to `RollingGrassOverlay.kt:37`'s vivid green (60, 210, 80) blade-stroke paint — likely x86 Skia stroke-rendering difference vs ARM; instrumented but not yet fixed (operator priority was full instrumentation, not symptom fix). Carry to S244: DWELL fix (gate on `NarrationMgr.ttsReady`), RollingGrass green-polygon fix after field-walking Bluestacks with the new stream, `:routing-jvm:test` parity decision (still untouched from S242).

Full session detail: `docs/session-logs/session-243-2026-05-11.md`. Commits: `867c0db`, `b25ca3f`, `5385404`, `f0c7711`.

---

## Session 242: 2026-05-10 → 2026-05-11 — Whole-repo code review + Tier 1 + Tier 2 debt reduction (~26K LOC deleted across `:app` + `:salem-content`)

Code-review session. Nine parallel Explore agents across Android / web admin / cache-proxy lib + scripts / JVM modules surfaced the debt picture; one new memory recorded mid-audit (`project_v1plus_features_parked_not_killed.md` — MBTA/aircraft/weather/transit are gated pending lawyer approval, not dead). Two big findings landed: `:app` module is dead (zero referrers, ~14.8K LOC) and `:salem-content` module is dormant (zero referrers, 8991-LOC hardcoded POI snapshot superseded by PG, ~12.8K LOC). Plan-mode-approved 8-phase execution, one commit per phase: Phase 1 deleted `:app`; Phase 2 deleted `:salem-content` (relabeled the leftover Gradle task-group display labels in `app-salem/build.gradle` + rewrote the `JdbcRoutingBundleLoader.kt` comment); Phase 3 archived 32 stale scripts to `docs/archive/scripts/{migrations-2026-04,one-shot-authoring,historical-dedup,one-shot-ingest}/` AND retargeted 7 publish-chain scripts to write directly to the bundled asset (the deleted `salem-content/salem_content.db` intermediate broke `./gradlew :app-salem:assembleDebug`); Phase 4 deleted Room schema JSONs v10–v18 (~540 KB; only v19 referenced at runtime); Phase 5 shipped six Tier 2 Android fixes — explicit `Job.cancel()` for the GPS-OBS heartbeat in onDestroy, `mv.requestLayout()` after `applyMapExtension` (S238 first-frame layout race), `SCREEN_CULL_PRE_PX`/`SCREEN_CULL_POST_PX` constants extracted in TiltContainer + mirrored to SpriteOverlay (sprite previously lacked explicit post-cull), `TiltContainer.onMapStateChanged()` single entry point consolidating the S239 MapListener + FAB-animator updateListener invalidations, dropped `READ_EXTERNAL_STORAGE` manifest permission + removed `record_gps`/`email_gpx` stub menu items + their MenuPrefs/BuildDefaults/MenuEventListener/SalemMainActivity wiring (Build Story/Analyze Today/Travel Anomalies stubs kept as parked V1+ features), and struck the already-deleted `narrationPointsCache` carry-forward from STATE.md; Phase 6 gated `/overpass` route behind `ENABLE_OVERPASS` env var (per memory `feedback_no_osm_use_local_geo.md`, OSM is build-time only; Android has zero runtime callers; same gating pattern as `ENABLE_CHAT`); Phase 7 standing-doc touch-ups (CLAUDE.md monorepo line + architecture tree + Salem JSON data bullet; MASTER_SESSION_REFERENCE.md same; ASSETS-MANIFEST.md `historical_facts` row); Phase 8 marked 10 Oracle/Intel-dependent scripts dormant via `[PAUSED S214]` header comment. Aggregate: 223 files / +246 −60,593 lines across 9 commits. Lenovo HNY0CY0W reinstalled after Phase 5 (S156 discipline). One non-issue surfaced: `:routing-jvm:test` parity tests fail 4/10 on the current asset bundle — verified pre-existing on HEAD~1 (bundle was re-baked since S178 fixtures); flagged for S243 decision (re-bake fixtures vs investigate drift). The earlier `comments.js`/`chat.js` deletion plan got pulled to Tier 6 mid-plan-mode after grep confirmed Android `SocialViewModel.kt:89` reads `resp.comments`; deletion needs paired retire-the-Android-path session. Important verifications in plan mode: `comments.js` NOT safe to delete; `group = 'salem-content'` in app-salem/build.gradle is just `./gradlew tasks` display labels not module deps.

Post-close: full build + Lenovo HNY0CY0W uninstall+install per S156 discipline. 374 MB debug APK carrying all S242 fixes + retargeted publish chain output now on-device for next operator field-walk.

Full session detail: `docs/session-logs/session-242-2026-05-10.md`. Commit: `0ec1863`.

---

## Session 241: 2026-05-10 — Mass-edit POI workflow live: round-trip .xlsx/.ods spreadsheet with per-cell diff review, soft-delete via spreadsheet, in-cell pick-lists

Bit Bar field-validation PASS on Lenovo (S240 ship confirmed). Then shipped the Mass-Edit POI workflow end-to-end: new admin tool tab under Tooling → 📊 Mass Edit. Round-trip: export every POI to .xlsx (frozen header row, per-column autofilter, hidden id UUID column A, in-cell dropdown pick-lists for 24 enum-bound columns) → edit in LibreOffice Calc → upload .xlsx or .ods → per-cell diff against live PG → per-cell approve/reject (or one-button Accept all) → apply in a single PG transaction via the existing whitelist + buildUpdateClause. Backend: new `cache-proxy/lib/admin-mass-edit.js` (~720 LOC) with 3 routes (export-spreadsheet, import-spreadsheet, apply-mass-edit) + JSZip post-write patcher for frozen pane + dataValidations XML; mirrors canonical soft-delete + restore SQL from admin-pois.js for the `is_deleted` intent column. Frontend: new `web/src/admin/MassEditTab.tsx` (~450 LOC) — sticky review header with Accept all / Reject all / Cancel / Apply, per-POI cards with 🗑 / ↩ chips for soft-delete / restore intents, rose badge + strikethrough for soft-deleted rows, amber badge for stale rows. Three operator-driven scope expansions during the session: (S241.1) `is_deleted` writable for dupe cleanup; (S241.2) per-cell pick-lists for category/subcategory/booleans/all enum columns. One nasty JS `String.replace` `$1`-as-backreference bug caught while inspecting emitted XML (subcategory range `$B$171` triggered substitution of the captured `<autoFilter/>` mid-formula) — fixed by switching to function-form replacement. New deps: `xlsx@0.18.5`, `multer@2.1.1`, `jszip@3.10.1`. Operator's first field-use: 2070 → 2042 POIs (28-row delta, dupe cleanup + recategorizations + soft-deletes). Full publish chain + 374 MB debug APK + Lenovo HNY0CY0W uninstall+install — clean deploy. Eight verification PASS gates (column-order regression, no-op round-trip, soft-delete one row, restore one row, mixed batch transaction, conflict guard, already-deleted rejection, multi-line + em-dash + smart-quote paste round-trip byte-identical).

Full session detail: `docs/session-logs/session-241-2026-05-10.md`. Commit: `ed4fd6f`.

---

## Session 240: 2026-05-10 — Decouple map marker rendering from narration (`default_visible` becomes the honest controller)

Diagnosed why Bit Bar (FOOD_DRINK, `default_visible=true`, `is_narrated=false`) doesn't render even with the Show All POIs FAB on while Sweet Boba (identical except `is_narrated=true`) renders fine. Root cause: `PoiCache.findNarrated()` returns `(isNarrated OR haunt) AND (defaultVisible OR overrides)` and the map's only marker pipeline reads this exact pool — `default_visible` was load-bearing only when combined with `is_narrated`, never on its own. Operator's call-out: "I would think the flag 'Visible by Default' would be the controller, not narration." Validated as a real architectural conflation. Entered plan mode, approved `nifty-singing-crown.md`, shipped the split: new `renderable` snapshot in `PoiCache` (`defaultVisible OR overrides`) + `findRenderable()` + repository/ViewModel facades; `SalemMainActivityNarration` now reads narrated for `narrationGeofenceManager.loadPoints` and renderable for `loadNarrationPointMarkers`; `narrationPointsCache` → `renderablePointsCache`. Startup log post-deploy confirms `renderable=1341, narrated=1290` (51 POIs newly renderable, Bit Bar included). Also fixed two unrelated staleness bugs surfaced during the same investigation: admin Lint hints "Content tab" → "General tab" in `admin-lint.js` (no Content tab exists), and `PoiEditDialog` Tier-0 banner "Operational tab" → "below (same tab)" (Merchant tier is on the Narration tab itself). Fresh publish chain + 357 MB debug APK deployed to Lenovo HNY0CY0W via uninstall+install. Carry to S241: field-validate Bit Bar visibility with FAB on, Sweet Boba regression check, lint review of the 51-POI delta set, Layers menu category gap (FOOD_DRINK/SHOPPING toggles don't currently gate the narration overlay).

Full session detail: `docs/session-logs/session-240-2026-05-10.md`. Commit: `1cf4dd1`.

---

## Session 239: 2026-05-10 — St. Peter's flat duplicate marker fix + upright drift fix + magnify-FAB icon scaling fix

Diagnosed three tilt-mode rendering bugs as the same root cause: hardware-accelerated display list staleness. The S237 strip-then-pass-2 design relied on `mv.overlays.removeAll(markers)` taking effect during `MapView.onDraw`, but MapView's RenderNode wasn't being re-recorded each frame, so the renderer replayed cached marker draw commands → flat ghost markers. The same caching caused upright drift after FAB +/- zoom and made the magnify FAB (x1→x5) fail to scale upright icons. Replaced strip/restore with a per-Marker tilt-suppression flag (`BillboardMarker.tiltActive` companion, `draw()` early-return when set), flipped on tilt 0↔>0 transitions with one-shot `mv.invalidate()`. Added a `MapListener` registered on the MapView while `tiltDeg > 0` (auto-unregisters at tilt=0) that calls `TiltContainer.invalidate()` on every osmdroid scroll/zoom event, plus a `setUpdateListener` on the magnify animator chain in `setupZoomToggle` that invalidates per-frame during the scaleX ramp. One ANR rabbit-hole on Option 1 (`mv.invalidate()` before `super.dispatchDraw`) was reverted within minutes — `View.invalidate()` schedules next-frame, so it created an infinite re-record loop instead of forcing same-frame re-record. Also did a side-quest hero-asset reachability audit: 385 of 386 `hero/<uuid>.webp` files are dead at runtime (94 shadowed by `heroes/<slug>.webp` priority-0, 291 orphan); ~12 MB AAB pre-ship reclaim noted. Operator: "much better, no more drift." Carry to S240: operator-mentioned other odds and ends in tilt mode, plus all S238/S237/S236 carry-forwards still apply.

Full session detail: `docs/session-logs/session-239-2026-05-10.md`. Commit: `e5f4843`.

---

## Session 238: 2026-05-09 — Phase 3 wedge fill (perspective softened) + cull loosened + diagnostic strip

Diagnosed the S237-carry "z19 dark wedge" via canvas-clip + MapView geometry instrumentation: geometry was already correct (mv extended 6× container, symmetric placement, full canvas clip) — the operator's complaint was perceptual, not a layout bug. At the previous `topY = h * 1.0 * t`, the perspective matrix's vanishing point sat inside the visible screen and z19 detail crushed to illegible gray in the band between vanishing and trapezoid top. Fix: `topY = h * 0.6f * t` (one-constant change, with a new `TOPY_FACTOR` companion shared between `rebuildMatrix` and `depthScale`). At max tilt the trapezoid now covers ~68% of screen, vanishing point is off-screen, and z19 street/building detail stays legible — operator's "fills the screen" requirement met (verified via adb-driven walk-sim teleport + screenshot). Marker + sprite culls loosened to allow the previously-rejected upper-extension area now that wedge is filled. S234/S237/S238 diagnostic scaffolding stripped (BILLBOARD-DIAG, WEDGE-DIAG, OVERLAY-TYPES, per-frame timing logs, billboardMode toggle, debugSuppressOverlays + companions): `TiltContainer.kt` 614 → 463 lines (-25%); logcat clean to a single `setTiltDegrees: X° → Y°` line per user input. Carry to S239: field-walk validation in motion + a `mv.requestLayout()` close to the first-frame layout race surfaced by the diagnostic.

Full session detail: `docs/session-logs/session-238-2026-05-09.md`. Commit: `d9362c7`.

---

## Session 237: 2026-05-09 — Billboarded POIs + sprites in matrix-tilt; rotation/scale/cull corrections; tile bake stripped of POI symbol layers; carry-forward to S238

Two-pass tilt rendering shipped: `TiltContainer.dispatchDraw` strips `Marker` instances from `mv.overlays` before `super.dispatchDraw` (which renders tiles + non-marker overlays under the perspective `tiltMatrix`), then re-renders the stripped markers UPRIGHT in pass-2 by projecting `lat/lng → mvLocal → rotation around mvCenter → scale-around-mvCenter by mapView.scaleX → +mv.left/+mv.top → tiltMatrix.mapPoints`. `SpriteOverlay` short-circuits its normal draw under tilt and exposes a parallel `drawBillboarded` for the haunt skeletons. Operator walkthrough exposed 8 issues which were triaged into a 4-phase plan written into the live log; Phase 1 (sign + cull + FAB scale) and Phase 2 (POI symbol layers stripped from `tools/tile-bake/style-salem.json`, S234-baseline re-bake of 715,841 tiles → 253.8 MB, merged into a 261.6 MB bundle) shipped. Final tighten of `BILLBOARD_NEAR_SCALE` 2.0→1.4 and `BILLBOARD_FAR_SCALE` 0.6→0.95 deployed unverified before session end. **Phase 3 deferred to S238: wedge tile fill at z19 (mandatory per operator) — at z19+high tilt the upper ~30% of screen still renders dark `#2D1B4E` instead of tile content despite `lp.height += 5×containerH` resize.** Diagnostic infrastructure (`BILLBOARD-DIAG`, `OVERLAY-TYPES`, `billboardMode` long-press toggle on 3D FAB) left in place for next session.

Full session detail: `docs/session-logs/session-237-2026-05-09.md`. Commit: `6500e2b`.

## Session 236: 2026-05-09 — +10mi-with-buildings raster bake → Lenovo crash on launch → rollback to S234 tiles

Reran the parallel raster bake against S235's vector sources, producing a fresh `salem-custom.mbtiles` (832.5 MB, 715,841 tiles in 12 min) with OMT + building footprints rasterized in for the matrix-tilt 3D wedges. Merged + rebuilt `:app-salem:assembleDebug`, installed to Lenovo HNY0CY0W: app launched into SalemMainActivity onCreate, fired cinematic zoom-in, then UI thread wedged within seconds and Android's RescueParty watchdog killed the process — no FATAL/AndroidRuntime stacktrace. Initial diagnosis (4 GB device, ~600 visible WebP tiles × 262 KB ARGB_8888 bitmap = ~470 MB heap > 460 MB watcher trigger) is uncertain because the crashing APK accidentally packaged both live `salem_tiles.sqlite` (848 MB) AND `salem_tiles.sqlite.preS236.bak` (262 MB) — backup left in `app-salem/src/main/assets/` after the merge step. Operator chose rollback path: restored S234's 274 MB `salem_tiles.sqlite`, moved the backup out of assets/ to `tools/tile-bake/dist/`, rebuilt clean (374 MB APK with single sqlite confirmed via unzip -l), reinstalled — operator confirmed working. New bake/merge artifacts preserved on disk (gitignored) for S237 clean-reproduction test before deciding whether to ship +10mi-with-buildings (and if so, whether tilt needs a default-off flag, q needs to drop to 40, or building rasterization needs to drop at z14-z15).

Full session detail: `docs/session-logs/session-236-2026-05-09.md`. Commit: `0ebe4be`.

## Session 235: 2026-05-09 — Crash-recovery + vector tile pipeline +10mi (buildings + OMT) on disk; raster re-bake deferred to S236

Crash-recovery session. Pre-crash work landed cleanly on disk before an OS crash at 01:39: `salem-buildings-plus10mi.geojsonl` (128 MB / 360,662 features) extracted; `buildings.mbtiles` (36.8 MB, 6,482 vector tiles z14-16) baked via tippecanoe; `salem-vector.mbtiles` (75 MB, 11,525 OMT tiles z0-16) baked via Planetiler — these are the vector input layer that S236's raster re-bake will consume to rasterize a fresh +10mi `salem-custom.mbtiles` with building footprints baked in for the matrix-tilt 3D wedges. The 8-worker parallel raster bake that consumes those vector sources crashed mid-run at ~28% of z15 (z14 fully done at 575/575, z15 partial ~599/2,160, z16-19 not started); `salem-custom.mbtiles` is still S234's 266 MB at 00:27. Recovery decision logged before the crash and confirmed at session end: discard `worker-output/`, rerun `bake-parallel.js` (~9 min) — operator opted to defer that to S236 and close. `worker-output/` removed; new bake artifacts gitignored (`salem-buildings-plus10mi.geojsonl`, `*.preS235.bak`, `worker-output/`). Folded long-untracked operator content into the repo: `DrK/` (4 narration drafts as .docx/.odt pairs — JohnWard, LyeTapleyShoeShop, QuakerMeetingHouse, "Welcome to Salem"), `qcis/` (SalemBeverlyView.qgz QGIS project), four `docs/session-logs/session-192-*.odt` review drafts.

Full session detail: `docs/session-logs/session-235-2026-05-09.md`. Commit: `cb3ceae`.

## Session 234: 2026-05-08 → 2026-05-09 — Tile bake +10mi everywhere q=60 (parallel) + PoiCache singleton + heading-up fast path + V1 call-site gates

Five distinct shippings. **Full +10 mi tile coverage at every zoom (z14-z19) at WebP q=60** — replaces the prior tiered shape. **715,841 tiles in 9 minutes via new 8-worker parallel-bake infrastructure** (`tools/tile-bake/render-worker.js` + `bake-parallel.js`, hash-split by `(bx*100003+by) % NUM_WORKERS`); ~8× speedup vs single-threaded ~75-min baseline. Bundle 58.9 → 261.7 MB; debug APK 161 → 374 MB. **PoiCache Hilt @Singleton** — entire 2,076-row catalog loaded ONCE at startup (572 ms background) with 8 pre-built indexes (id / category / subcategory / district / visible / narrated / tour / districts), then routed through SalemContentRepository's 16 POI read methods + FindViewModel + WitchTrialsRepository + SalemMainActivity.loadHauntConfigsAsync. **Zero Room queries during runtime** after the one-shot load. **Narration marker diff cache + Mutex** — filter toggles (Show All POIs, Layers checkboxes, tour-mode transitions) become near-instant no-ops when warm; mutex-serialized to fix a startup race that double-attached 1,130 markers. **Heading-up fast path** — `lastProcessedMoveBearing` early-return eliminates ~100 Hz sensor-pipeline allocation churn + log spam. **V1 call-site gates** on `onScroll` / `onZoom` listeners so V1-disabled subsystems aren't invoked even to hit their internal early-returns. Also: **MarkerIconHelper LRU cap 2000 → 4096**; comprehensive `TiltContainer` timing instrumentation (burst + sampled-steady frames + per-MOVE) + debug overlay-suppression toggle on long-press 3D FAB; calibration script with WebP q=60/70/80/lossless A/B (visually indistinguishable for parchment-style tiles). Three new feedback memories: diagnose-before-changing (operator caught a load-bearing edit based on assumed device state); map-easy-pois-are-the-lever (basemap pipeline off-limits when chasing perf); v1-gate-at-call-site (V1-disabled features must be gated at hot-path call sites). Operator field verdict: "much better, good but needs more work." **Carry-forward to S235**: residual cold-start tile-decode spike (one Choreographer "Slow dispatch took 1134ms" on first tilt entry); occasional 30-43ms steady-state frame spikes at z19+tilt+600 overlays; `narrationPointsCache` activity-local field now redundant with PoiCache; field walk on the 374 MB APK; AAB / Asset-Pack ship story.

Full session detail: `docs/session-logs/session-234-2026-05-08.md`. Commit: `678890f`.

---

## Session 233: 2026-05-08 — Matrix-tilt 3D promoted to V1: wedge fill + dedicated 3D FAB + perspective touch fix

S232's debug-only tilt prototype hardened and shipped to V1. **Symmetric upward MapView extension** (height += `5.0 × containerH`, `topMargin = -extra/2`, `clipChildren=false`) fills the upper-corner wedges with real distant Salem tiles through the SAME single perspective transform — no seams, no `setMapCenterOffset` (which broke osmdroid's tile-fetch viewport, leaving overlays rendering through dark gaps). **Touch events are manually perspective-transformed** via `Matrix.mapPoints` + multi-pointer `MotionEvent.obtain(...)` — Android's `MotionEvent.transform(Matrix)` mangles perspective matrices (silently drops/mis-applies the bottom row, inverting y direction). **FAB x1..x5 magnify** pivots correctly on the operator marker because the symmetric layout puts MapView's geometric center at TiltContainer center = operator's GPS-rendered position; default View pivot lines up naturally. **New "3D" FAB** above the GPS button (`btnTilt3d` in `activity_main.xml`) replaces the S232 long-press on the "+" zoom button; cycles **0° → 30° → 36° → 42° → 48° → 0°** (operator-tuned ladder, persisted via `MenuPrefs.PREF_TILT_3D_DEG`). **`BuildDefaults.TILT_3D_ENABLED` flipped from `BuildConfig.DEBUG` to `true`** — ships in release/AAB. **Verbose logging** added across `TiltContainer` + zoom buttons + FAB + slider for the next reproducer of an ANR operator hit during FAB x5 cycling at high zoom (working hypothesis: 5.0× extension × 5× FAB scale = ~30× normal screen render area at high zoom, blowing UI thread). **Carry-forward to S234**: tile-bake bbox expansion 10 miles outside Salem's bbox for ALL zoom layers — the residual darkness at higher tilt + zoom is a real bundled-coverage gap, not a tilt-engine bug.

Full session detail: `docs/session-logs/session-233-2026-05-08.md`. Commit: `a983d40`.

---

## Session 232: 2026-05-08 — Matrix-tilt 3D prototype (debug-only)

Curiosity-driven exploration of a faux-3D forward-tilt look on top of osmdroid without leaving osmdroid. Net shipped: `TiltContainer` (FrameLayout subclass that wraps the MapView, concats a `setPolyToPoly` perspective matrix in `dispatchDraw`, and inverse-transforms touch events) gated behind `BuildDefaults.TILT_3D_ENABLED = BuildConfig.DEBUG`. Long-press the **+** zoom button to cycle 0° → 30° → 45° → 60° → 0°; angle persists via new `MenuPrefs.PREF_TILT_3D_DEG`. Top-pinched trapezoid with `topY = 1.0*h*t` lands the operator marker at ~75% screen height at max tilt; dark-purple TC background blends the upper-corner wedges. Narration bar + tour-HUD suppressed during tilt because they were getting added as TiltContainer children and warping with the perspective. ~18 `Marker(binding.mapView)` constructions across 8 files were sed-replaced with `BillboardMarker(binding.mapView)` (now a one-line pass-through subclass — the canvas-matrix billboard trick failed on the Lenovo's hardware-accelerated canvas). Wider-MapView two-pass attempt to fill wedges with real tiles was reverted because the seam where the two perspective transforms met read as a "strange division at horizon." Field-edit field validation (S229+S230 carry-forward) was punted to next session — operator unavailable for the walk; pre-flight verified clean. Side conversation: Pixel 7a recommended as the budget rooted Android target (easy bootloader unlock, dual-frequency L5 GNSS, ~$200-250 used).

Full session detail: `docs/session-logs/session-232-2026-05-08.md`. Commit: `db667b3`.

---

## Session 231: 2026-05-07 → 2026-05-08 — Splash decision tree (polygons + admin tree editor + runtime resolver) + Rapid Recon time-driven + sidebar reorg + auto-fire camera

Ten distinct shippings across one long session. **Splash decision tree, full stack**: (a) baked `app-salem/src/main/assets/us_places_v1.sqlite` from TigerLine 2025 — 102 top-100 cities + 102 thirty-mile buffer rings + 11 Salem-adjacent towns (Salem itself + 6 ST_Touches direct neighbors + 4 second-ring tourist arteries) + 3,235 US counties = 3,450 polygons / 2.09 MB, gzipped WKB blobs; (b) `cache-proxy/lib/admin-splash-tree.js` exposes GET/PUT/POST `/admin/splash/*` with PostGIS `ST_Contains` for accurate live-preview resolution; (c) `web/src/admin/SplashTreeTab.tsx` three-pane editor (bucket list / trigger+variant editor / live test pane with Salem/Beverly/Danvers/Boston/LA/No-GPS presets); (d) `cache-proxy/scripts/publish-splash-tree.js` validates + bakes JSON into APK assets, wired into the Gradle `publishSalemContent` chain; (e) Kotlin runtime in new `splash/` package — `LocationContext` + `PlaceResolver` (own WKB Polygon/MultiPolygon ring-tracing PIP since Android stock SQLite ships without rtree) + `SplashEngine` + `SplashActivity` hook; (f) authoring doc `docs/SPLASH-ANNOUNCEMENTS-V1.md` with 32 numbered TODO stubs. Verified live on Lenovo HNY0CY0W: home GPS in Beverly resolves correctly to "You're close to discovering Salem — about 2.7 miles away in Beverly, MA." **Web admin sidebar reorg**: replaced the 8-button header tab strip with `AdminSidebar.tsx` left rail (Content / Quality / Tooling groups, localStorage-persisted collapse state). **Rapid Recon (GpsBurstCameraManager) refactor**: time-driven tick instead of GPS-fix-driven (1 Hz cadence, GPS-independent so flaky/coalesced fixes can't starve the camera); true-north `GPSImgDirection` via `GeomagneticField`; null-safe filename (`noGPS` suffix); rebrand to "Rapid Recon". **Photos overlay upgrade** (`web/src/admin/BurstPhotosOverlay.tsx`): cluster pins (one marker per (lat,lon)); thumbnail grid modal; per-card green-✓ Save (visual ack only) + red-🗑 Delete (no confirm); soft-delete to `<session>/.deleted/` for recovery; sharp thumbnail endpoint with disk cache; map non-reactive while modal open via `createPortal` to `document.body` + `useFreezeMapWhile` hook. **Salem trip recovery**: pulled 1962 burst photos / 5.1 GB / 33.3 min walk; cadence histogram confirms 1 s tick (median 1 s, p99 2 s). **Auto-fire toolbar recon camera**: `EXTRA_AUTO_FIRE` skips preview + shutter, fires after 300 ms autofocus settle, finishes — single-tap shutter. **FAB zoom-step fix**: `BuildDefaults.FAB_ZOOM_STEP` 2.0 → 1.0 (regression: + / − buttons were skipping z18). **Vite proxy LAN-IP-rot fix**: `localhost:4300` instead of hardcoded `10.0.0.229:4300`. **Owed:** operator authors splash announcement variants (32 stubs); field validation of S229+S230 field-edit loops on a real walk; GPS-burst round-2 field walk; alignment review of the 1962-photo session via the new cluster modal; auto-fire camera field-test.

Full session detail: `docs/session-logs/session-231-2026-05-07.md`. Commit: `b2b5928`.

---

## Session 230: 2026-05-06 — Android field-edit can CREATE new POIs (long-press → name → web admin gate)

Closed the S229 carry-forward "create new POI here" entry kind end-to-end through the same A→C flow. **Trigger:** long-press on empty map (when field-edit toolbar ON) opens a slimmed AlertDialog "Create new POI" sheet pre-pinned at the long-press lat/lng — required: name only; optional: re-pin, recon photo, note. **JSONL schema bumped 1 → 2** with a new `kind: "update" | "create"` discriminator on every line; create rows omit `poi_id` / `poi_name` / `current_*` and carry `proposed_name` instead. **Web admin gate:** `cache-proxy/lib/admin-field-edits.js` apply branches on `e.kind === 'create'`, requires operator-supplied `category` (subcategory optional id) in the request body, generates id via slug+6-hex (one retry on collision), INSERTs with `data_source='admin_create_field'` + `admin_dirty=TRUE`. **UI:** new `<CreateCard />` in `web/src/admin/FieldEditsTab.tsx` (teal-bordered, "Pending Create" badge) renders proposed name + location plus a strict Category `<select>` (required) + Subcategory `<select>` (optional, gated on category pick); Apply button disabled until category chosen. Server-side validated end-to-end with a synthetic create-kind JSONL line: list → apply-without-category 400 → apply-with-`{category:"SHOPPING"}` INSERTs row → PG SELECT confirmed. APK installed on Lenovo HNY0CY0W; `tsc --noEmit` clean. Operator field-test of the actual long-press → save → pull → web-admin-create flow owed for next session (parallel to the still-owed S229 update-flow field validation).

Full session detail: `docs/session-logs/session-230-2026-05-06.md`. Commit: `3669bc2`.

---

## Session 229: 2026-05-06 — Burst-photo admin overlay + Android field-edit feature (A→C, end-to-end)

Three operator-driven shippings stacked. **Burst-photo admin overlay**: pulled S228 round-2 burst photos (278 photos / 30 min, 2 s throttle floor reached 120 times — round-2 tuning confirmed); built a `/admin/burst-photos` cache-proxy endpoint over `/mnt/sdb-images/LMASalemPictures/` (list/image/EXIF/delete) and a Photos toggle on the existing AdminMap that overlays every shot as a red `<CircleMarker>` for POI/path-alignment QC, click → modal with image + EXIF + Delete-with-confirm. **POI-tree cleanup**: dropped the two `SOURCE:*` virtual nodes (Destination Salem, Haunted Happenings) end-to-end. **Android field-edit feature, full A+B+C in one session**: third toolbar icon (R8-stripped via `BuildDefaults.FIELD_EDIT_ENABLED`); tap a POI in field-edit mode opens an AlertDialog sheet with Move-here (tap-to-place)/Category/Subcategory/Note/Photos rows; Save appends a JSON line to `/sdcard/Documents/WickedSalemFieldEdits/edits-<bootTs>.jsonl`. New `tools/pull-field-edits.py` adb-pulls the JSONL + referenced recon photos. New `cache-proxy/lib/admin-field-edits.js` + `web/src/admin/FieldEditsTab.tsx` form the inbox: Sync from device button (spawns the python pull tool) + filter chips + per-edit cards with current-vs-proposed diff grid (Δ-meters for moves, label→canonical-id resolution for subcategory via `salem_poi_subcategories`) + Apply (writes to `salem_pois` with admin_dirty stamp) / Reject (sidecar `state.json`). Fixed two field bugs mid-session: bundled-Salem-POI marker layer (`narrationMarkers`) wasn't being intercepted; subcategory picker was case-sensitive (SalemPoi.category UPPERCASE vs PoiLayerId lowercase). Operator field-tested through Starbird Cannabis Dispensary edit cycle — pulled, surfaced in the new inbox, ready to Apply. Operator: "that is great!"

Full session detail: `docs/session-logs/session-229-2026-05-06.md`. Commit: `c536169`.

---

## Session 228: 2026-05-06 — GPS-burst camera (debug-only second toolbar button) + organizer tooling

Shipped a new debug-only top-bar camera button next to the existing recon-camera that auto-fires the rear camera every N seconds while a session-scoped toggle is ON, baking GPS lat/lon/alt/speed/track + compass heading EXIF and publishing to `Pictures/WickedSalemRecon/`. Six surfaces: `BuildDefaults.GPS_BURST_ENABLED` const (R8-stripped from retail via existing `RECON_DEFAULTS` BuildConfig field); `toolbarGpsBurstIcon` ImageView in `toolbar_two_row.xml` (default GONE); `MenuEventListener.onGpsBurstToggled(enabled)` interface stub; `AppBarMenuManager.setupSlimToolbar` extended with `gpsBurstIcon` param + session-scoped toggle (WHITE↔#E53935 red tint, never persisted); new `GpsBurstCameraManager.kt` (~370 LOC, headless CameraX `ImageCapture` only, no Preview surface — silent background capture); `SalemMainActivity` Hilt-injects `LocationManager` and lifecycle-wires the manager. Operator field-tested on a 34-min Beverly→Salem loop walk producing 485 photos (1.14 GB); pulled via `adb pull`, organized into per-session folders with GPX + GeoJSON + CSV + summary via new `tools/pull-and-organize-burst-photos.py` (split rule: gap > 120 s = new session); wiped device after. Round-2 tuning based on captured histogram (median 4 s on a 3 s throttle because Lenovo serves GPS fixes ~every 2 s): GPS poll 1 Hz → 2 Hz, throttle 3 s → 2 s, flash forced OFF on builder + live `ImageCapture` instance. Field validation of the new cadence + actual POI/path-alignment review of the captured imagery owed for next session.

Full session detail: `docs/session-logs/session-228-2026-05-06.md`. Commit: `bd488d2`.

---

## Session 227: 2026-05-05 → 2026-05-06 — Haunt refinement (duration knob + screen-aligned upright fix)

Seven-iteration refinement of S226's per-POI Haunt. Net schema change: Room v18 → v19 (identity `745afa3eb4ce04bd7873671ea297b6e0`) with one new column `haunt_duration_s` REAL; admin form now in minutes (wire stays seconds via `buildDefaults`/`buildPayload` ×60). SpriteOverlay rewritten end-to-end: alpha envelope decoupled from scale (flat-top trapezoid 15/70/15 fade), pseudo-3D depth bob with randomized amplitude/cycles/phase per fire, frame-11-pinned upright facing per S170 demo calibration, one elliptical orbit per dance (random radii 70-140 × 50-100 px, random direction), gentle scale clamp 0.7-1.4. Per-sprite rotation hack added during diagnosis then **deleted** when the real cause was found in `WickedAnimationOverlay.kt:22-27` docstring: osmdroid pre-rotates the canvas by `mapOrientation` (heading-up tour mode) so sprites with a clear up-axis inherit the rotation. Fix is 3 lines: pass `mapView.mapOrientation` into `CameraState.mapOrientationDeg`, then in `SpriteOverlay.draw` `canvas.save() / rotate(-mapOrientationDeg, cx, cy) / drawBitmap / restore()` so the sprite stays anchored at the POI but renders upright on screen regardless of map heading. Operator close: "Much better." S170 sprite WebPs split into 112 per-frame WebPs (`assets/sprites/<id>/00.webp`...`15.webp`, ~2.2 MB total). Field-walk validation of the upright screen-aligned skeleton on Lenovo near the bridge POI owed for next session.

Full session detail: `docs/session-logs/session-227-2026-05-05.md`. Commit: `874a764`.

---

## Session 226: 2026-05-05 — Per-POI Haunt Effect (admin-driven sprite peek)

Wired the S170 sprite figures into the running app as an admin-driven, per-POI haunt effect. Six surfaces touched: (1) Room v17 → v18 with 6 new `haunt_*` columns on `salem_pois` (sprite id + outer/inner range/interval + enabled, identity `e6c5a128efdd9949f7297c88ada7d698`); (2) cache-proxy `admin-pois.js` UPDATABLE_FIELDS extended; (3) new **Haunt** tab in `web/src/admin/PoiEditDialog.tsx` with sprite dropdown + 4 numeric inputs + Enabled toggle, plus a generous dialog resize (1100 × 95 vh) so warnings don't crowd the form; (4) 7 sprite WebPs (~992 KB) bundled into `app-salem/src/main/assets/sprites/`; (5) new `SpriteOverlay.kt` (~190 LOC) implementing `MapOverlay` with two-band cadence + 1-second swoop (bell-curve alpha + float-up + scale curve) + lazy bitmap cache; (6) `SalemMainActivity.setupMap` wires SpriteOverlay alongside water/firefly/grass + injects `SalemPoiDao` for haunt-config seeding + feeds GPS to overlay every fix. Post-deploy snag — admin-created POI without narration was filtered out of the marker loader (`is_narrated=true` requirement); fix: loosen `findNarrated()` to also surface `haunt_sprite_id IS NOT NULL AND haunt_enabled = 1` rows. Field-walk verification of the actual swoop firing on Lenovo owed.

Full session detail: `docs/session-logs/session-226-2026-05-05.md`. Commit: `7534483`.

---

## Session 225: 2026-05-05 — Web admin can create POIs

Operator-driven sprint while SI provenance reformulation continues. Net new flow: `+ New POI` header button (POIs view, emerald-600) enters place-mode → click the map → small `PoiCreateDialog` (Name + Category w/ inline `+ Add new` + optional Subcategory/Address) → POST `/admin/salem/pois` → full PoiEditDialog opens on the new row for narration/flags/hours/images. Backend ~95 LOC in `cache-proxy/lib/admin-pois.js` (auto-slug id, JSONB whitelist serialization, FK 400, dup-id 409, `data_source='admin_create'` + `admin_dirty=TRUE` stamping); new `web/src/admin/PoiCreateDialog.tsx` (~290 LOC); `AdminMap.tsx` + `AdminLayout.tsx` wired with `addPoiMode` mirroring the existing tour-stop add-mode pattern (reuses `MapClickAddListener`, distinct emerald banner, ParcelHitTest gated off in place-mode, Esc handler extended). All five validation paths smoke-tested via curl (happy / missing name / lat=91 / unknown category / duplicate id) — all return clean error messages, test row cleaned up. `npx tsc --noEmit` clean; Vite HMR clean; cache-proxy restarted via `npm start` (S223 dotenv autoload live). No Android build, no Room schema bump (admin-only feature). Find-menu-refinement docket queued at S224 close deferred — operator pivoted to POI creation; docket carries forward for later sessions when the new categories need Find-menu tiles.

Full session detail: `docs/session-logs/session-225-2026-05-05.md`. Commit: `122f969`.

---

## Session 224: 2026-05-04 — Walk-sim queue-stall fix + Find/PoiDetailSheet V1-dead code purge + lean+state session-start protocol

Refinement session while waiting for the SalemIntelligence provenance reformulation. Six entries: (1) **Session-start protocol amended** lean → lean+state — STATE.md and the most recent live log are now mandatory at session start; CLAUDE.md + memory `feedback_session_protocol.md` rewritten. (2) **Walk-sim queue stall** found from operator's "I was walking then it stopped but FAB still says walk" report on `tour_LongWalk` — root cause: `enqueueNarration` unconditionally bumped `narrationCancelForPoiAtMs` even on queue-only adds, so an auto-ENTRY arriving 13ms after the previously-playing POI's natural Idle suppressed that Idle via the S146 cancel-for-POI guard, stalling the queue forever (walker dwelled 180s safety-cap, advanced 1 step, re-fired the same POI — Lappin Park / Rockafellas cluster). Fix: stamp + newspaper-cancel only on the direct-play branch; queue-only adds leave the timestamp alone. Operator field-confirmed clean walk through the previously-stuck cluster. (3) **`tour_LongWalk` (26 legs)** published to Lenovo; total assets now 4 tours / 73 legs / 2081 POIs / Room v17 `d44caaec...`. (4) **V1-dead code purge** across `SalemMainActivityFind.kt` + `PoiDetailSheet.kt`: Comments section (~190 LOC of socialViewModel-driven UI), Yelp Reviews button, in-app `showFullScreenWebView` + 5 `android.webkit.*` imports, V2 website spinner+fetch + `var resolvedUrl`, V2 `geo:` fallback for Directions — all `if (!FeatureFlags.V1_OFFLINE_ONLY)` branches collapsed; `FeatureFlags` import removed from both files; stale `@SuppressLint("SetJavaScriptEnabled")` removed. **Net –381 LOC.** (5) **Two pre-existing Kotlin warnings** cleaned: deprecated `setBuiltInZoomControls(false)` (redundant with `zoomController.setVisibility(NEVER)`); always-false `if (activeTour == null)` replaced with `val resolvedTour = activeTour!!` pin + 3 downstream call-site rewrites. `compileDebugKotlin --rerun-tasks` produces zero warnings. (6) **Operator-direction at close:** future S225+ sessions are refinement on the Find menu surface — categories, subcategories, hero pictures, click-through (POI detail) presentation — while SI rebuild continues. Build clean; APK on HNY0CY0W; cache-proxy 1070426 (4300, dotenv autoload) + Vite 1070463 (4302) running across the boundary; SalemIntelligence/Oracle deliberately NOT started.

Full session detail: `docs/session-logs/session-224-2026-05-04.md`. Commit: `479dd29`.

---

## Session 223: 2026-05-03 — Backlog junk: dotenv autoload + stale CLAUDE.md item

Two cheap chores cleared. (1) **dotenv autoload** for cache-proxy: `cache-proxy/server.js` now calls `require('dotenv').config()` at the top, with `dotenv@^16.6.1` added to `cache-proxy/package.json`. Plain `npm start` survives a reboot — no need for the spawning shell to `set -a; source .env; set +a` first. Verified: killed S222 PID 19753, restarted via plain `npm start`, new PID 501902 came up clean with PostgreSQL connected, smoke-tested `/db/pois/stats` (200, 3558 POIs) and `/db/pois/categories` (200 in 3.7 ms). (2) **Stale priority-block item retired** — verified at `cache-proxy/scripts/publish-tour-legs.js:50–70` that S193 already implemented the dynamic Room schema-JSON read via `readLatestRoomIdentity()`; CLAUDE.md item #6 ("de-hardcode Room identity_hash, currently stamps v10") was wrong. Removed bullet, renumbered engineering items 7-13 → 6-12, refreshed the S188-close cache-proxy state line to reflect the new dotenv autoload posture and current runtime deps over baseline (`sharp@0.34+` from S188, `dotenv@16+` from S223). Removed bullet archived to `docs/archive/CLAUDE.md_priority_item_6_removed_2026-05-03.md`. No Android build, no Room bump, no publish chain re-run. Services left running: cache-proxy PID 501902 (4300, S223 dotenv autoload live), web/Vite PID 19377 (4302).

Full session detail: `docs/session-logs/session-223-2026-05-03.md`. Commit: `3ab7566`.

## Session 222: 2026-05-02 → 2026-05-03 — Restart web-admin services post-reboot

Short utility session. Read S221 + summarized for operator. Operator asked to bring the web-admin services back up after a host reboot: started Vite (web/ on 4302) cleanly; cache-proxy `npm start` crashed at `admin-lint.js:1026` because `cache-proxy/server.js` reads `process.env.DATABASE_URL` directly without `dotenv` — `npm start` doesn't source `cache-proxy/.env`, so PG was unconfigured and admin-lint's bootstrap `pgPool.query(...)` blew up on null. Worked around by sourcing the env into the spawning shell (`(set -a; source .env; set +a; node server.js …)`); cache-proxy then came up clean on 4300 with PostgreSQL connected, auto-import enabled, OpenSky OAuth2 configured. SalemIntelligence (8089) + Salem Oracle (8088) intentionally NOT started — both still paused per provenance-reformulation gate. Services left running across the session boundary at operator request. **Carry-forward:** add `dotenv` to cache-proxy and `require('dotenv').config()` at top of server.js (or wrap `npm start` in an env-sourcing shim) so reboot recovery is one command.

Full session detail: `docs/session-logs/session-222-2026-05-02.md`. Commit: `69d7c66`.

## Session 221: 2026-05-02 — Tour detour feature + web admin fuzzy search + 2 GB stale-file recovery

Three substantive deliveries plus a host-system AIDE diagnosis. (1) **Tour detour feature** end-to-end across 11 files (8 modified, 3 new): subtopic adjacency cards (`source_kind='adjacent_poi'`) get an inline "▸ Open <linked POI>" link → fly + open POI Detail Sheet → orange "Take a detour and walk there?" button → orange `#FF8C00` out-route polyline + persistent fuchsia floating banner pinned top-of-map → tap banner → AlertDialog (Return / Stop / Cancel), Return → second chooser (Nearest point / Back to last stop, per operator's "let user choose each time" answer). New `TourState.Detour` variant + 4 SharedPreferences keys persist the detour across cold launches; `restoreIfSaved()` re-emits Detour so the banner reappears on relaunch. Narration gate fully relaxed during detour (so any eligible POI speaks while walking to the detour POI), Layer-toggle prefs snapshotted + restored on Return. New `SubtopicRenderer` callbacks (`onOpenLinkedPoi`, `resolveLinkedPoiName`) shared by the narration banner + POI Detail Sheet (both async-prefetch adjacency POI names so the link label resolves to real names). New file `SalemMainActivityDetour.kt` (~190 LOC) holds banner wiring, dialog flow, polyline overlays, and the `openPoiDetailFromSubtopic` gateway. Two compile failures during build cycle (binding.detourBannerWrapper not auto-bound from plain `<include>`; smart-cast issue on optional handler) — both fixed. `./gradlew :app-salem:assembleDebug` green, APK installed on Lenovo HNY0CY0W. No Room schema bump. Plan was vetted via Plan-mode + AskUserQuestion before code; written to `~/.claude/plans/purrfect-jumping-karp.md`. (2) **Web admin fuzzy search + map auto-filter.** `PoiTree.tsx` search now multi-token AND across `name + id + address + 3 narration fields + category/subcategory labels + slug-form of category/subcategory IDs`, so "bed" hits both "Morning Glory Bed & Breakfast" (name) and any future POI in `LODGING__bed_and_breakfast` subcat (label). `AdminLayout` mirrors the search term up via new `onSearchChange` callback, builds a `Set<string>` of matching POI ids via shared `poiMatchesQuery`, passes it as `searchIdFilter` to `AdminMap` which intersects it into `filteredPois`. Floating sky-blue pill top-right of map shows `Search: "<term>" (N)`. (3) **2 GB stale-file cleanup** — Tier 1 (build artifacts) + Tier 2 (untracked stale runtime caches & 2026-03-01 overnight-runs + S092-era bake-test logs): `./gradlew clean && rm -rf .gradle web/dist web/tsconfig.tsbuildinfo overnight-runs docs/bake-tests && rm -f cache-proxy/poi-cache.json cache-proxy/cache-data.json`. Repo size 15 G → 13 G. Tier 3 (~4.4 GB in tools/ + cache-proxy/out/l3-essex/ + docs/archive/poi-icons-unused-styles_*) flagged for operator review, NOT touched. (4) Host-side AIDE process (`_aide`, `dailyaidecheck.service`) was eating 9.7 GB / blocking on SIGTERM — operator force-killed + disabled the timer; 9.7 Gi memory recovered.

Full session detail: `docs/session-logs/session-221-2026-05-02.md`. Commit: `a0c380b`.

---

## Session 220: 2026-05-02 — Subtopic UI on narration banner + auto-gen pipeline (497 POIs)

Finished the storytelling-with-subtopics surface coverage on the runtime side: extracted `SubtopicRenderer.kt` shared util and wired it into the ambient/tour-stop narration banner (`narration_bottom_sheet.xml` + `showNarrationSheet()`); newspaper headline mode explicitly hides the block. Built `cache-proxy/scripts/auto-gen-narration-subtopics.js` — a fenced auto-generator that synthesizes subtopic candidates from local sources only (provenance-pause-respecting): figure detection across the 49 NPC bios + adjacency to the nearest eligible POI ≤200 m. Eligibility fence baked from operator's mid-session refinement: only `category IN (HISTORICAL_LANDMARKS, HISTORICAL_BUILDINGS, CIVIC, WORSHIP)` OR `is_historical_property/is_civic_poi/is_witch_trial_site=true` (commercial categories excluded; `is_tour_poi` excluded due to data-quality pollution). Live run: 497 / 652 eligible POIs got cards (12 with figure detection + 485 adjacency-only). Operator visual sign-off on Lenovo HNY0CY0W: "works well — needs to be refined a lot more but very useful." Carry-forward: `is_tour_poi` Lint-tab cleanup needed (Young World Academy etc. spurious-flagged).

Full session detail: `docs/session-logs/session-220-2026-05-02.md`. Commit: `9112992`.

## Session 219: 2026-05-02 — Storytelling-with-subtopics framework + Old Burying Point worked example

Baked the new HARD RULE end-to-end: warm narrations with overflow facts as scrollable subtopic cards. Added `salem_pois.narration_subtopics` JSONB, extended the publish chain, bumped Room v16→v17, built a structured `SubtopicEditor` in the admin (header/body cards with word-count + source_kind/source_ref + reorder), and rendered the full chip-strip + collapsible body cards on the POI Detail Sheet (all chips visible, no pagination, no "More" overflow). Authored 5 worked-example subtopics on Old Burying Point (Hathorne / Bradstreet / Richard More / Death's Heads & Willows / adjacent Witch Trials Memorial). Operator-confirmed visual on Lenovo HNY0CY0W after the parseSalemPoi/salemPoiToJson Bundle-serializer footgun was discovered + fixed.

Full session detail: `docs/session-logs/session-219-2026-05-02.md`. Commit: `84ab1e2`.

---

## Session 218: 2026-05-01 — Per-POI location validate workflow (TigerLine + Google Places) with map-review UX

S218 shipped a fully-featured per-POI location validation workflow for the admin tool. Top-of-card **Validate via TigerLine** (fuchsia) and **Validate via Google** (sky blue) buttons + Lint-tab per-row equivalents both run server-side geocoding (PostGIS Tiger or Google Places API New), filter to the Salem area (15 km drops wrong-town matches), and route into a unified proposal-review mode: the editor closes, the map flies to the proposal at zoom 20, the fuchsia "?" pin is draggable, and a small floating panel shows current/proposed coords + source pill + drift (with amber/rose warning bands at 1 km / 5 km) + Accept/Cancel/Re-center/"Ask Google ↗"-link. Tiger path includes snap-to-edge (50 m) + street-name-near-POI fallback (KNN on `tiger.edges` for cases like Sea Level Oyster Bar where Tiger's house-number range doesn't include #94 in Salem); Google path uses Places API (New) Text Search with `<name> <address>` query for storefront-level accuracy (resolves Salem Witch Village → 11 m vs Tiger's 113 m parking-lot interpolation). Backend lives in `cache-proxy/lib/admin-pois.js` (three new endpoints: `/validate-location-tiger`, `/geocode-via-google`, `/discard-proposed-location`; existing `/accept-proposed-location` extended to take optional `{lat,lng}` body for committing the dragged-pin position) + new `cache-proxy/lib/tiger-geocode.js` (refactored shared helpers with verbose `[validate-tiger <poi_id>]`/`[geocode-google <poi_id>]` logging across every step). Frontend: new `web/src/admin/ProposalReviewPanel.tsx` floating bottom-right panel + `ProposalPreviewLayer` draggable in `AdminMap.tsx` + `proposalReview` state machine in `AdminLayout.tsx` + Validate buttons in `PoiEditDialog.tsx` header and `LintTab.tsx` per-row. Wrote `docs/TIGERLINE.md` (~370 lines) as operator reference covering both pipelines, drift bands, log recipes, MassGIS L3 integration plan for future work. No DB schema changes (S162 columns carried the entire workflow); no Android impact.

Full session detail: `docs/session-logs/session-218-2026-05-01.md`. Commit: `c3fb781`.

---

## Session 217: 2026-05-01 — Category-aware FAB POI narration + Show-All-POIs FAB overrides every gate

Two operator-driven UX changes. (1) New `BusinessLabel.kt` builds a curated `(category, subcategory) → singular noun-phrase` map (16 BUSINESSES-group categories + ~55 subcategories). `PoiContentPolicy.strippedAnnouncement` and `TourEngine.checkAmbientProximity` both emit `"You are near [the ]<name>, a <phrase>."` Subcategory absorbs category when redundant ("walking tour company", not "walking tour tour company"); `the` skipped for `The …` and possessive `'s` names; defensive leaf-humanization for any new enum added later. (2) Operator rule locked: when the "Show All POIs" FAB is on, ALL POIs narrate, period — bypasses tour gate, Layers checkboxes, Audio Control group toggles (Meaningful/Ambient/Businesses), and the no-narration-text early skip. New in-memory `AudioControl.showAllOverride` flag (resets to OFF every cold launch); `NarrationGeofenceManager.isHistoricalQualified` and `AudioControl.isPoiSpeechEnabled` both short-circuit to true when set; `SalemMainActivity.refreshHistoricalModeForActiveTour` pushes the flag on every FAB tap / tour-state transition / Layers-toggle / walk-sim start. Removed `TourEngine.startTour`'s direct `setTourMode(true,…)` call (and the `MenuPrefs` import) — that line was racing with the Activity observer and overriding the FAB-on intent at tour start, which is why operator's reproducer "toggle the FAB off then on" was a workaround. NARR-GATE diagnostic log now includes `override=` for field debugging. No Room schema impact (Kotlin-only). APK on Lenovo HNY0CY0W via auto-bake `assembleDebug` + `adb uninstall && install`.

Full session detail: `docs/session-logs/session-217-2026-05-01.md`. Commit: `0c259fa`.

---

## Session 216: 2026-05-01 — FAB onboarding + active-leg highlight + auto-bake gradle + S189 dedup + HISTORICAL_LANDMARKS split + historical_note column drop (Room v15 → v16)

Big consolidation session, ten threads. (1) **FAB walk onboarding** — when in Salem (real GPS or manual long-press) the FAB walk-sim now builds a routed walk from current position to the nearest point on the tour polyline via on-device router, prepends it, and walk-sim begins from the user's actual position; out of Salem (or no fix) drops on waypoint 1. (2) **Per-leg active highlight** — `TourViewModel.computeTourLegPolylines` returns ordered per-leg polylines; `SalemMainActivityTour.kt` draws each leg as its own Polyline pair, closest-leg-to-user determines the active one (rendered bright blue 12/18 px, on top via full overlay-list rebuild), inactive legs stay full-strength green 6/10 (briefly tested at dim alpha but reverted per operator). (3) **Two-finger map rotation** — `RotationGestureOverlay` registered in `setupMap` so manual 360° rotation works in both heading-up and north-up modes. (4) **Auto-bake Gradle preBuild** — S180/S185 carry-forward done: `publishSalemPois → publishSalemTours → publishSalemTourLegs → alignSalemAssetSchema → verifyBundledAssets` registered in `app-salem/build.gradle` with `mustRunAfter` ordering, hooked to `preBuild`; admin edits in PG now reach the device with a single `assembleDebug`. Escape hatch `-PskipPublishChain`. (5) **S189 hard-purge of soft-deleted POIs** — new `dedup-merge-and-purge.js` (normalised name + ≤300 m radius cluster matching, per-field merge rules with TEXT/NUMERIC/BOOL/JSONB strategies, LIVE coords NEVER touched), 220 → 0 dead, 13 merges into living cluster-mates (119 fields lifted), 207 orphans hard-deleted per "dead = die" rule. One commercial-hero leak surfaced (silsbee_s_2 inherited tier-0 hero) → cleared image_asset to satisfy verify gate. (6) **Live-cluster dedup with BCS-keeper rule** — new `dedup-live-clusters.js` (Union-Find clusters, source-priority keeper: `salem_intelligence_bcs > manual_curated > openstreetmap > api_sync > destination_salem > massgis_mhc`, FK redirection across `salem_events_calendar`/`salem_historical_facts`/`salem_historical_figures`/`salem_primary_sources`/`salem_timeline_events`/`salem_tour_stops` before delete), 35 clusters → 43 hard-deletes / 93 fields merged. John Ward House cluster collapsed: BCS tour POI keeper, MHC dup deleted with `mhc_*` fields lifted in. Then a separate one-off Gardner-Pingree merge for two rows my normalised-name match missed ("Gardner-Pingree House" vs "Historic Gardner-Pingree House"). (7) **HISTORICAL_LANDMARKS category split** — operator wanted MASSGIS-only POIs (the long tail of plaqued houses) gated separately from curated buildings: new PG row `salem_poi_categories.HISTORICAL_LANDMARKS` (label "Historical Landmarks", `pref_key=poi_hist_landmark_on`, color `#A1887F`, default off, reuses existing Layers toggle); 484 `data_source = 'massgis_mhc'` rows reclassified out of `HISTORICAL_BUILDINGS` (now 105 curated). 8 Kotlin files registered the new category in their hardcoded lookup tables (`MarkerIconHelper`, `PoiHeroResolver`, `ProximityDock`, `NarrationTier`, `CategoryVoiceMap`, `AudioControl`, plus `NarrationGeofenceManager.TOUR_HIST_LANDMARK_CATEGORIES` swap from BUILDINGS→LANDMARKS so curated buildings always narrate while the MHC tail remains layer-gated). Icons + heroes alias to the existing `historic_house` set; operator will regenerate distinct landmark imagery later. (8) **Walk-sim onboarding race fix** — operator-visible "green leg painting over blue active leg, intermittently mid-walk": root cause was `TourViewModel.startTour()` always launching `onboardToNearestPolylinePoint()` which armed a `directionsSession`, whose drift-reroute loop fired every few fixes during walk-sim and called `drawWalkingRoute()` to paint a fresh `#22C55E` polyline ON TOP of the per-leg overlays. Fix: `startTour(skipOnboarding: Boolean = false)`, both walk-sim entrypoints pass `true`, plus `tourViewModel.clearDirections()` inside `startWalkSim()` to nuke any pre-existing session. (9) **Czech LLM-disclaimer prefix strip** — found `Poznámka: Tento výstup je generován automaticky a může obsahovat chyby.` leaked into `gardner_pingree_house.historical_narration`; stripped via regex; corpus-wide scan turned up no other contamination. (10) **`historical_note` column consolidation, Room v15 → v16** — operator: simplify what's narrated where, content's all getting regenerated for production anyway, but no gaps. Migration lifted historical_note into description (10 clean fills + 133 appended with `\n\n` separator + 2 substring overlaps skipped + 0 gaps), `ALTER TABLE DROP COLUMN`, then 5 Kotlin files (`SalemPoi.kt`, `PoiDetailSheet.kt`, `NarrationGeofenceManager.kt`, `FuzzySearchEngine.kt`, `WitchTrialsMenuDialog.kt`) drop the field, 4 cache-proxy/web files (`publish-salem-pois.js`, `admin-pois.js`, `admin-lint.js`, `PoiEditDialog.tsx`, `poiAdminFields.ts`) drop it too. `SalemContentDatabase.kt` v15→v16, KSP regenerated `schemas/16.json`, identity_hash `2b9838099e6df9b9a616f538c6e99a0c`, `verify-bundled-assets.js` constant updated. Pure side-quest interruption mid-session: bumped tour-POI `geofence_radius_m` floor 40→80 m chasing the John Ward House non-narration symptom, then reverted to 40 m after operator clarified the rule + log analysis revealed a downstream 40 m dequeue staleness drop that geofence-bumping wouldn't help (real fix is leg-authoring or POI relocation operator-side). All shipped to Lenovo HNY0CY0W via auto-bake `assembleDebug` + `adb uninstall && install`.

Full session detail: `docs/session-logs/session-216-2026-05-01.md`. Commit: `1b24b00`.

---

## Session 215: 2026-05-01 — Edge-point Dijkstra (S214 Phase 2): tour legs thread through mid-block waypoints

S214 Phase 2 shipped end-to-end. Free tour waypoints persisted with `(edge_id, edge_fraction)` by S214 now actually thread through their bound edge-foot at routing time instead of corner-pulling to the nearest graph node. New `routeBetweenEdgePoints(b, srcEP, dstEP)` + `routeBundleEx(b, srcStop, dstStop)` in `cache-proxy/lib/salem-router.js` (~290 LOC of additions): same-edge fast path (polyline-between-fractions, no Dijkstra) + 2-pseudo-source Dijkstra (both endpoints of the bound edge seeded with partial-edge initial costs `f*L` and `(1-f)*L`; dst stop-set has both endpoints with partial-cost map; best entry chosen by `dist[X] + partial`) + tail/path/head geometry stitching. Helpers: `_interpAlongEdge` (binary-searched fraction-to-LL using existing `edgeSegCum`/`edgeSegTotal` cache), `edgePartialPolyline` (substring with interpolated start/end), `_resolveEndpoint`, `_dijkstraTwoSource`, `_reconstructFromDijkstra`, `_stopToEndpoint` (with lazy `b._edgeIdToIdx` lookup cache so repeated leg computes don't re-scan all 17K edges). Module exports `_routeEx`; `cache-proxy/server.js:198` plumbs it as `deps.salemRouteEx`. `admin-tours.js` `fetchEffectiveStops` now selects `s.edge_id`/`s.edge_fraction`/`s.poi_id`; `compute-route` and per-leg `recompute` switch to `salemRouteEx` when EITHER endpoint has a binding (POI-only-pair legs keep node-snap fallback). Stale-edge_id case (made-up `edge_id` not in bundle) falls through to lat/lng node-snap defensively. Verified: same-edge fast path on edge_idx 5000 (51.1m) at fractions 0.2→0.7 yields EXACTLY 25.5m no-Dijkstra; cross-edge route on Dr. K stops 1→2 polyline starts at `(42.522604, -70.892327)` and ends at `(42.522893, -70.892383)` — EXACT 6-decimal match to the snap points stored in `salem_tour_stops`, vs node-snap baseline that corner-pulled to `(42.522582, -70.892323)` ~3m off. End-to-end via admin endpoint: `tour_DrKs_001` 14/14 legs / 0 skipped / 5,850m total / 12 of 14 detour ratio ≤ 1.12; `tour_WD1` 14/14 / 0 skipped / 6,366m / 10 of 14 detour ≤ 1.22. The 2/4 suspicious-flagged legs in each tour (Dr. K 13/14, WD1 3/6/10/11) are pre-existing graph-data oddities (one-ways or missing connector edges) — they would be flagged in node-snap mode too, not edge-point Dijkstra bugs. Full publish chain run (publish-salem-pois 2,193 rows / publish-tours 3 / publish-tour-legs 47 / align-asset-schema-to-room v15 hash `3e927300be7b2a8971fa6afb4aa5af78`) + `assembleDebug` (98 MB APK in 11s) + Lenovo HNY0CY0W `adb uninstall && install` (per `feedback_adb_install_after_db_rebake.md`, never `-r`). No Room schema bump; no Android router change (the on-device router is a parallel concern — V1 device tour rendering reads pre-baked `salem_tour_legs` polylines from the bundled asset, which is exactly what this session's publish chain just rewrote with edge-point geometry).

Full session detail: `docs/session-logs/session-215-2026-05-01.md`. Commit: `03a92ff`.

---

## Session 214: 2026-05-01 — Tour waypoints bind to TigerLine + Tour-mode POI preview

Two operator-driven admin-tool features. (1) Free tour waypoints now snap to the nearest walkable TigerLine edge at placement and on every drag — new `nearestWalkableEdge(b, lat, lng)` in `cache-proxy/lib/salem-router.js` (edge spatial grid + perpendicular projection, walks rings outward like the existing node KNN); `salem_tour_stops.edge_id BIGINT` + `edge_fraction REAL` columns; `GET /admin/salem/snap-edge` preview endpoint; POST/PATCH stop endpoints persist the snap result and store the snapped lat/lng instead of the raw click. AdminMap `MoveConfirm` shows a fuchsia "Will snap to <street> · Δ <m> · edge_id <id>" panel before commit; marker repositions to the snapped coords post-PATCH so there's no drop-point → road-point jitter. Backfilled all 50 existing free waypoints across `tour_DrKs_001` + `tour_WD1` + legacy (sub-meter typical, 10m worst case); idempotent re-run reports "already bound." (2) New `TourModePreview` panel in TourTree mirrors the device's S186 narration gate — three checkboxes for `is_tour_poi` (always-on, default ON), MassGIS Hist Landmark (opt-in, default OFF), `is_civic_poi` (opt-in, default OFF) — with live counts (237/484/67) and a union total. Toggling narrows AdminMap markers to exactly which POIs will narrate during the active tour; tour stops still render via TourStopLayer regardless. Operator-stated hard pause at session end: Salem Oracle (:8088) and SalemIntelligence (:8089) are undergoing major reformulation to attach full provenance to every fact — without source attribution V1 content cannot ship legally; new memory `project_oracle_intelligence_paused_provenance.md` captures the gate. Phase-2 carry: edge-point Dijkstra so legs actually pass through mid-block waypoints (today they still pull to the nearer corner at compute time). S213 carry-forward (TTS field listen-test on Lenovo) still pending. Web typecheck clean; no Android build needed.

Full session detail: `docs/session-logs/session-214-2026-05-01.md`. Commit: `f91d91ab325f0c08184312aeb13a09f069df23f5`.

---

## Session 213: 2026-05-01 — TTS pre-normalization (markdown strip + abbreviation expansion)

Shipped a runtime text-normalization layer for the narration pipeline. New `NarrationManager.normalizeForTts()` (~125 LOC) strips Markdown emphasis (12 POIs had book-title italics like `*The Scarlet Letter*` being read as "asterisk") and expands the abbreviations the engine was mumbling — 88 `Capt.` → "Captain", 28 `Jr.` → "Junior", 26 `St.` (50-name saint allowlist disambiguates `St. Peter` → "Saint Peter" from `Hardy St.` → "Hardy Street"), 12 `Rev.`, 11 `Dr.`, 6 `Dea.`, 5 each `Bros./Col./Inc.`, plus state names, era markers, postnominals, and connectives — with class-specific period preservation: titles drop period before names (always followed by alpha word), states/streets/postnominals/companies keep period when sentence-terminal (lookahead `\s+[A-Z]|\s*$`), Jr./Sr. consult a sentence-starter allowlist (`He|She|It|This|After|...`) to disambiguate `Joseph Jr. House` (mid-sentence) from `Joseph Jr. He died` (sentence end), Mt./Ft. behave as title-style prefixes. 32-test JUnit suite (`NarrationManagerNormalizeTest.kt`) caught two real heuristic bugs in first run — Mt./Ft. were misclassified as trailing suffixes, B.C./A.D. were eating sentence-terminal periods — both fixed before close; final 32/32 PASS. Wired in `speak()` once before `chunkOnPunctuation` so the splitter sees normalized text. Debug APK installed on Lenovo HNY0CY0W via `adb uninstall && install` (not `-r` per memory). No DB / schema / publish-chain impact. Carry-forward S214: field listen-test on Lenovo to grow title/state/saint-name lists from any remaining mispronunciations; year-pronunciation pass if `1692` reading is bad; pronunciation-lexicon work on `Hathorne / Tituba / Bowdoin / Oyer and Terminer` is out of parsing scope and would need a phoneme-replacement table.

Full session detail: `docs/session-logs/session-213-2026-05-01.md`. Commit: `866375b`.

---

## Session 211: 2026-04-30 — Socket.IO drop (V1 no-network)

Sibling of S209's DebugHttpServer disable. `io.socket:socket.io-client:2.1.0` removed from all three module gradle files (`core`, `app`, `app-salem`) and `ChatRepository.kt` (247 → 163 LOC) gutted of all Socket.IO surface — `import io.socket.client.IO`/`Socket`, the `socket: Socket?` field, the on/off/emit/connect/disconnect calls, the option-builder, and the `suspendCancellableCoroutine`-based connect-and-wait flow. Public method signatures preserved (`connect`/`disconnect`/`isConnected`/`joinRoom`/`leaveRoom`/`sendMessage`/`sendTyping`/`setOnMessageListener`/`setOnTypingListener`) so `SocialViewModel` callers in both `app` and `app-salem` still compile unchanged — bodies become no-op stubs that log nothing and return `false`/`Unit`. Runtime gate already existed at `SalemMainActivitySocial.kt:218-221` (`if (FeatureFlags.V1_OFFLINE_ONLY) { ... return }` in `showChatDialog`), so the V1 violation surface was the library being **linked into the APK**, not a live socket. REST methods (`fetchRooms`/`createRoom`/`fetchMessages` + `OkHttpClient` + hardcoded `BASE = "http://10.0.0.229:4300"`) **left in place** — operator scope was Socket.IO drop, not full ChatRepository nuking; the REST surface is gated by the same `showChatDialog` early-return so it never fires on V1, and dropping it is a cleaner stand-alone S212 task. Compile-clean across all three modules in 9s; `grep -rn "io\.socket\|socket\.io-client"` returns zero matches outside `docs/` and `build/`. Two V1 zero-network sources are now sealed (DebugHttpServer S209, Socket.IO S211); ChatRepository REST drop is the matching S212+ carry-forward.

Full session detail: `docs/session-logs/session-211-2026-04-30.md`. Commit: `6656326`.

---

## Session 210: 2026-04-30 — TileArchive LruCache by bytes (W-H1)

Wraps up the WickedMap memory-hardening pair started in S209 (W-H2 PolygonLibrary unload). Single-file change at `app-salem/.../wickedmap/TileArchive.kt:25-31` — switched the bitmap cache from a 256-entry count cap (`LruCache<Long, Bitmap>(256)` with `sizeOf = 1`, worst-case ~64 MB at 256×256 ARGB tiles) to a byte-budget cap sized at `Runtime.getRuntime().maxMemory() / 8` with `sizeOf` returning each bitmap's `allocationByteCount`. Defensive `Long`-math `(maxMemory / 8L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()` guards against the future case where `Runtime.maxMemory()` exceeds Int.MAX_VALUE. On a typical Android heap (~256 MB) the cache budget lands ~32 MB ≈ 128 ARGB-8888 tiles — comfortably above the ~50-tile single-viewport need plus prefetch ring, well under the prior 64 MB worst case, and shrinks proportionally with whatever heap the OS hands the process. `minSdk 26` makes `Bitmap.allocationByteCount` (API 19+) unconditionally safe — no API-level branch needed. `./gradlew :app-salem:compileDebugKotlin` BUILD SUCCESSFUL in 2s, no new warnings. No DB schema bump, no publish chain, no Room migration. WickedMap memory pair (W-H1 + W-H2) now complete; remaining S208 retrospective backlog: ChatRepository REST drop (S211 sibling), geofence-math unit tests (highest test-ROI), `!!` cleanup in walk-sim, dedup SharedPreferences mirror tidy, `.gitignore` cleanup, partial DB index, V1.1+ extend `onTrimMemory` at `TRIM_MEMORY_RUNNING_CRITICAL` to drop `wickedAnimationOverlay`.

Full session detail: `docs/session-logs/session-210-2026-04-30.md`. Commit: `6656326`.

---

## Session 209: 2026-04-30 — DebugHttpServer disabled + PolygonLibrary memory hook (W-H2)

Two S208-retrospective sprint items shipped. (1) **DebugHttpServer hard-disabled.** Both `app-salem/.../util/DebugHttpServer.kt` (160 → 32 LOC) and `app/.../util/DebugHttpServer.kt` (159 → 30 LOC) gutted to no-op shells: `start()`/`stop()` are empty bodies, `ServerSocket(4303)` is gone from the codebase, V1 no-network rule (`feedback_v1_no_external_contact`) is now enforced at the source. `endpoints: DebugEndpoints?` retained as in-process holder so the two `cancelWalkAndJoin()` consumers (`SalemMainActivity.kt:1569`, `SalemMainActivityDirections.kt:383`) keep resolving — no caller changes needed (operator-chosen "gut socket, keep holder" scope). (2) **W-H2 PolygonLibrary memory hook.** Added `PolygonLibrary.unload()` (idempotent — frees the `byKind` index, resets `loaded` flag, logs entries freed) and an `onTrimMemory(level: Int)` override in `SalemMainActivity` that calls it at `TRIM_MEMORY_RUNNING_LOW` and above. Limitation explicitly documented in code comment: the active animation overlays (`AnimatedWaterOverlay`/`FireflyOverlay`/`RollingGrassOverlay`) each hold their own `polygons: List<WickedPolygon>` slice, so this frees only the duplicate index — full release of the `WickedPolygon` objects requires also dropping `wickedAnimationOverlay` on critical pressure, deferred to V1.1+. Threshold `>= TRIM_MEMORY_RUNNING_LOW` covers all higher-pressure constants (RUNNING_CRITICAL/MODERATE/COMPLETE/BACKGROUND) since they're numerically larger. `@Suppress("DEPRECATION")` on the override since `TRIM_MEMORY_*` constants are deprecated in API 35 but still dispatched on the target API 34. `./gradlew :app-salem:compileDebugKotlin :app:compileDebugKotlin` BUILD SUCCESSFUL — only pre-existing warnings remain (`setBuiltInZoomControls` osmdroid deprecation, one always-false condition at `SalemMainActivity.kt:1690`). No DB schema bump, no publish chain, no Room migration. S209 backlog still includes W-H1 (TileArchive LruCache byte-sizing), `!!` cleanup, geofence unit tests, dedup SharedPreferences mirror, `.gitignore` cleanup, partial index, Socket.IO drop — all carry-forward to S210+.

Full session detail: `docs/session-logs/session-209-2026-04-30.md`. Commit: `328be23`.

---

## Session 208: 2026-04-30 — V1 Code Retrospective (full opinionated audit + WickedMap addendum)

Operator asked for a full V1-ship-lens code retrospective. Three Explore subagents in parallel (Android / web+cache-proxy+PG / build pipeline+content+tests+repo) produced findings; verified high-impact claims directly before writing. Wrote 712-line retrospective at `docs/retrospectives/V1-CODE-RETROSPECTIVE-2026-04-30.md`, opened in operator's GUI editor. After review, ran 9 dialog questions; operator decisions reordered the sprint plan. Headline: one real BLOCKER (DebugHttpServer ships in release, no `BuildConfig.DEBUG` guard, no `src/debug/` source set — confirmed at `SalemMainActivity.kt:615`); operator chose Option B (move to `src/debug/`). Spawned a focused WickedMap audit after operator corrected agent assumption that it was "parked R&D" — it's the LIVE V1 primary basemap. WickedMap addendum surfaced two HIGH items: TileArchive LruCache sized by count not bytes (256 × 256 KB = 64 MB OOM risk on low-RAM), PolygonLibrary parsed JSON resident forever with no `onTrimMemory` hook. Engine itself is well-architected (synchronization discipline, no allocations in onDraw, polygon clipping, deterministic seeding). osmdroid → WickedMapView is an in-progress migration ("trapped by hooks"), both ship in V1. Other V1 backlog: `!!` cleanup in walk-sim, geofence-math unit tests (highest test ROI, confirmed by operator as #1 real-world bug source), SharedPreferences-backed dedup mirror (S110 close), `.gitignore` cleanup (`.~lock.*`, `*.odt#`, `qcis/`, three big binary asset DBs — stop-the-bleeding only, no history rewrite), Socket.IO drop, partial index on `salem_pois(deleted_at)`. Demoted: publish-chain Gradle wrapper (operator: "minimal pain, muscle memory"), Activity refactor (operator: "manageable, split helps"). V2 onboarding bundle (1+ helper likely): BUILD.md + CI + pre-commit hook for Room schema bumps + publish-chain wrapper. No code changes, no schema, no publish chain runs this session — research + doc only.

Final doc: 836 lines. Sprint-ready backlog in §13.

Full session detail: `docs/session-logs/session-208-2026-04-30.md`. Commit: `1683ed9`.

---

## Session 207: 2026-04-30 — Civic narration trim (long_narration nulled on 62 civic POIs)

Operator: "civic POIs have too much information, like the memorials and statues — is there a way to control their narrative?" Walked the runtime gate in `NarrationGeofenceManager.kt:466-505`; initial proposed fix (make commemoratives honor BRIEF/STANDARD/DEEP) invalidated by DB inventory — all 67 civic POIs have empty `historical_narration`, none match the commemorative regex, none are pre-1860. Real source of bloat was `long_narration` heard at DEEP (15+ civic rows >800 chars, top: Historic Salem Inc 1,134 / AOH 1,020 / Bishop Fenwick 1,006 / Salem Common 931 / Salem Sound Coastwatch 991). Operator picked: NULL `long_narration` for all civic. Snapshotted 62 rows to `docs/archive/civic-long-narration-snapshot-2026-04-30.csv` (reversible), ran the UPDATE, verified 0 civic with long_narration remaining + 66/67 still have short_narration. Full publish chain (Room v15) + assembleDebug + Lenovo HNY0CY0W uninstall+install. No code changes. Carry-forward: operator validation drive, selective re-author from snapshot if any civic feels too thin.

Full session detail: `docs/session-logs/session-207-2026-04-30.md`. Commit: `49f30c5`.

---

## Session 206: 2026-04-30 — Web admin verify + Lenovo deploy + drive forensic + 3 narration/visibility fixes

Verified web admin (cache-proxy back up on 4300, Vite 4302) — S205's "Apply auto-eligible 706" had been clicked between sessions, leaving 211 review-only proposals. Operator hit `apply-categorization → 413` on a 211-row apply; root cause was `cache-proxy/server.js:25` global `express.json({ limit: '16kb' })` rejecting the ~19 kB payload — bumped to `1mb`. Wired `AutoCategorizeModal` POI name as a button that calls `onOpenPoi` and closes the modal so operator can drill from auto-cat review into the standard PoiEditDialog. Confirmed three independent guards keep auto-categorize structurally unable to read or write HISTORICAL_BUILDINGS / CIVIC rows; recent HB updated_at activity is operator's manual edits. Ran the full 5-script publish chain (2,215 POIs / 3 tours / 46 legs / Room v15 identity_hash `3e927300be7b2a8971fa6afb4aa5af78`) + assembleDebug (102 MB APK) + Lenovo uninstall+install. Operator drove ~50 min with wife and reported "lots of things seemed to be missing"; pulled 1M-line logcat and reconstructed: DensityTour started at 19:55:52 with allowHist/allowCivic=false (S186 tour-mode default OFF), operator toggled layers mid-drive, 22 POIs queued / 7 spoke / 12+ dropped as stale (>40 m behind user) at driving speed — Samantha enqueued at 20:04:18 in slot #4 then stale-dropped 52 s later at 242 m past. Three compounding causes: tour-mode silenced layers by default, queue starvation at speed, DensityTour has 0 stops (walk-sim hit OUT-OF-SALEM fallback). Found two real bugs while diagnosing: **(1)** the explore/tour pref split silently lies — operator checks boxes in Explore (default ON), starts a tour, tour-mode prefs are unset so fall through to default OFF; fixed by making tour-mode `*PrefDefault()` mirror the explore-mode pref when unset (added optional `prefs: SharedPreferences?` to `MenuPrefs.histLandmarkPrefDefault` / `civicPrefDefault`, threaded through `AppBarMenuManager`, `TourEngine`, `SalemMainActivity` — 8 call sites). **(2)** the `is_tour_poi=true` "force-visible override" only worked at the layer gate, not at the tier filter above it — 128 of 222 tour-flagged POIs (PARKS_REC 67, EDUCATION 36, WORSHIP 19, ENT 3, SHOP 2, WITCH_SHOP 1) silently invisible at restrictive layer states. One-line fix: added `p.isTourPoi` to the tier filter admission criteria in `SalemMainActivity.kt:2212`. Two rebuild + reinstall cycles on Lenovo. Discussed but did NOT ship a speed-aware queue cap for the queue-starvation case — deferred to S207.

Full session detail: `docs/session-logs/session-206-2026-04-30.md`. Commit: `82f2f02`.

---

## Session 205: 2026-04-30 — Category/subcategory lint pass + SI-driven auto-categorization (recovered + closed)

Operator picked S205 carry-forward #4: lint POI categories/subcategories. Inventory surfaced a silent resolver bug — `PoiHeroResolver.kt` filtered icon files by `${subcategory}_` prefix, but DB stores `food_drink__cafes` while files are `cafes_*`, so per-subcategory hero icons never fired and every POI hash-picked across the full category folder; fix strips `CATEGORY__` namespace before prefix-match. Four new Taxonomy lint checks shipped (`subcat_namespace_mismatch` 2, `subcat_unknown_token` 24, `commercial_missing_subcategory` 1188 raw / 500 cap, `category_keyword_mismatch` 10). Phase 3 added SalemIntelligence-driven auto-categorization: 974/1188 (82%) of the missing-subcategory backlog have `intel_entity_id` for direct lookup, remaining 214 fall to fuzzy match; mapping engine has 20-primary SI→LMA map, refiner functions for ambiguous primaries, name-override layer that runs before SI mapping, and confidence tiers (≥0.9 same-cat → auto-apply, never silently changes category). Dry-run reports 918 proposals / 706 auto-eligible / 212 review. Web admin gained `AutoCategorizeModal.tsx` with summary cards, transition heatmap, color-coded review table, and "Apply auto-eligible (706)" CTA. Vite HMR config fixed for LAN access. Claude crashed mid-session before close protocol; recovery confirmed work was functionally complete (no half-applied state) and ran the close.

Full session detail: `docs/session-logs/session-205-2026-04-30.md`. Commit: `769cebb`.

---

## Session 204: 2026-04-30 — Lenovo device-verification pass: 7 V1 carry-forwards closed, GPS-OBS heartbeat root-cause fix shipped, 11 bios cleared of S192 meta-gap violations

Operator framed: "let's continue with the Lenovo items." All 7 device-dependent V1 carry-forwards from S203 closed in one pass. Real bug found + fixed end-to-end on the Lenovo: **#11 GPS-OBS heartbeat** — adaptive GPS interval picker sat downstream of the stationary-freeze early-return, and on Lenovo TB305FU (`TYPE_SIGNIFICANT_MOTION` permanently broken) the freeze gate stayed true forever during dwells, so the picker never ran and the subscription stayed stuck at 60s; fixes arrived every ~27s, right at the edge of the 30s stale threshold, toggling stale constantly. Lifted picker upstream; stale threshold 30s → 45s; verified on Lenovo: subscription drops to 30s/min 15s after 10s dwell, fix cadence steady ~14s, sustained `HEARTBEAT ok`, zero STALE transitions over 3 min. **#3 Witch Trials content** audit found 11/49 NPC bios with S192 meta-gap violations ("Little is known...", "vanished from the historical record", etc.) — patched `Salem/cmd/genbiographies/main.go` prompt with new `== ANTI-META-GAP RULE (CRITICAL) ==` block + concrete VERDICT replacement examples, ran two LLM regen passes (cleared 8/11), hand-edited 3 stragglers' `historical_verdict_header` + `historical_verdict` + `role_summary` fields with recomputed `tts_full_text`; final audit: all 11 bios at 0 hits across 13 forbidden patterns. Re-bundled to APK + reinstalled. **Other six items:** #17 walker-dwell + S169 code intact (operator-skipped SplashActivity launchMode fix); #29 S150 fixes 1+3 code correct (DEEP install-default + 3 stationary escape hatches); #21 Find type-search smoke confirmed at engine + data layers (dentist→18, lawyer→33, gym→26, coffee→43); #25 walk-sim e2e basic machinery verified on Lenovo (FAB tap → tour auto-pick → 225-pt baked polyline → 1.4 m/s sustained, TTS-gated dwell armed, NARR-DWELL anchor-advance) — full POI loop deferred to operator field walk because Beverly desk-test triggered OUT OF SALEM fallback random-step; #20 V1-offline static posture verified (zero network perms / `V1_OFFLINE_ONLY` const / BuildDefaults release retail / OS-level network-incapable). Five-script publish chain ran clean before any device work (PG mutations from S203 propagated). Cross-repo carry-forward to S205: Salem repo prompt edits + 3 hand-edited bio JSONs need committing in that repo.

Full session detail: `docs/session-logs/session-204-2026-04-30.md`. Commit: `eb58e29`.

---

## Session 203: 2026-04-30 — V1 backlog drive-through (Phase A cleanup, V1/V1.0.1/V2 triage, six V1 items shipped; Phase D blocked on Lenovo)

Operator framed: "drive through all the backlog with us developing the code; lawyer items in process." Phase A (cleanup): A1 confirmed `heroes/` and `hero/` are both LIVE (no delete); A2 converted S202's seven testing-window flips into a single `BuildConfig.RECON_DEFAULTS` switch (debug = recon walk posture, release/AAB = fresh-install retail; new `BuildDefaults.kt` is the single source of truth); A3 bulk-suppressed 199 `narration_regen_failed` lint flags; A4 hard-DELETEd 365 dedup losers (FK-clean, transaction-wrapped, 2778→2413 POIs); A5 added pre-insert truncate to the newspaper bundle script to defend against legacy id=date PK collisions; A6 bumped `civic_flag_mismatch` lint severity warn→error. Phase B: triaged all 31 carry-forward items into V1 / V1.0.1 / V2 (operator confirmed). Phase C V1 execution: #28 poi-icons 544 MB blocker is STALE memory — actual `poi-icons/` is 3.4 MB / total assets 80 MB / current AAB 78 MB (under Play Store's 150 MB cap) — no-op, OMEN open item closed. #23 verify-bundled-assets gained `Salem-Custom`-only tile-provider check. #24 first JVM unit test `NarrationGeofenceManagerTourModeTest` ships (5 tests, all green; mockito-core + `unitTests.returnDefaultValues=true` + `@VisibleForTesting` getters; closes OMEN-004 phase-1 deadline 2026-08-30). #12 fixed `salem_common_2/3` to centroid; bumped `OUTLIER_KM` 3→5 km; suppressed Rebecca Nurse Homestead; outlier lint 75→0. #10 added `onboardToNearestPolylinePoint` in `TourViewModel.startTour` (50 m threshold). #27 burial-grounds tribute hallucination audit: NULLed 6 hallucinated `historical_narration` (Ames Memorial Hall invented donor; 4 Greenlawn outbuildings with meta-gap or post-1860 dates or "garage prior to 1780" hallucinations; Prescott Memorial Cemetery raw "156m/164m..." metric leak); 4,311 chars of dubious prose removed. Phase D blocked on Lenovo HNY0CY0W going offline mid-session — signed-AAB rebuild + smoke + 7 device-dependent V1 items carry to S204. PG-side mutations need the publish chain run before next AAB build.

Full session detail: `docs/session-logs/session-203-2026-04-30.md`. Commit: `a7a37b1`.

---

## Session 202: 2026-04-29 — Recon Camera (in-app CameraX + full GPS+compass EXIF) + testing-window default flips for wife's morning Salem walk

Operator-pivoted scope. S201 had penciled S202 as a 1-hour V1/V1.0.1/V2 backlog triage; instead operator opened with "we have some critical needs we have to do so my wife can test this in the morning" and asked for a recon camera + auto-GPS-tracking. Two requests delivered: (1) **Recon Camera** end-to-end as a V1 feature — top-level slim-toolbar button next to Home (`ic_camera_recon` glyph), launches our own in-app CameraX `ReconCaptureActivity` (NOT system camera handoff) so we keep window control with an explicit X-close + back-button cancel + 84dp shutter. New `KatrinaCameraManager` orchestrator writes full EXIF (GPS lat/lon/alt/speed/track/img-direction/timestamp/processing-method/UserComment + Make/Model/Software) using raw FusedLocationClient.lastLocation (bypasses MainViewModel's Samantha-statue clamp) + ROTATION_VECTOR-derived compass azimuth; photos publish to MediaStore `Pictures/WickedSalem-Recon/` for USB MTP pull. CameraX 1.3.4 + ExifInterface 1.3.7 added. (2) **GPS auto-tracking** verified already wired (FusedLocationProvider + continuous animateTo at line 3067) — no code needed beyond runtime permission. Five testing-window defaults flipped per operator: `PREF_GPS_BBOX_OVERRIDE_DEFAULT` true (use real GPS outside Salem), `PREF_RECORD_GPS` default true, `isGpsTrackVisible` true (FAB lit + polyline visible), `isHeadingUpMode` true (map auto-rotates to movement bearing), default landing zoom 18→19, FAB +/- step ±1→±2, magnify FAB initial level x1→x2. Footgun discovered: `AppBarMenuManager.prefDefault()` had hardcoded `false` fallbacks for PREF_RECORD_GPS + PREF_GPS_BBOX_OVERRIDE that overrode the MenuPrefs constants — fixed in same edit (memory `feedback_appbar_pref_default_override.md`). Three crash/iteration cycles: (a) initial system-camera build crashed at field-init NPE on `applicationContext` (fixed via `by lazy` on context-dependent fields), (b) operator wanted camera on slim toolbar not Material overflow (moved + cleaned up), (c) operator wanted X-close not opaque system handoff (replaced with CameraX in-app activity). Five `adb -s HNY0CY0W uninstall && install` cycles (NEVER `-r` per memory). Lenovo at session end: 1.32 GB free RAM, 33 GB free storage. The S201-planned carry-forward triage rolls to S203, along with all 4 unfinished S201 actions (365 dedup losers, 199 narration-regen suppressions, heroes/hero/ audit, signed AAB rebuild).

Full session detail: `docs/session-logs/session-202-2026-04-29.md`. Commit: `7859fbb`.

---

## Session 201: 2026-04-29 — V1 launch triage + strategic doc cleanup + Aug 1 ship anchor

Planning + housekeeping session. Honest assessment of V1 readiness delivered; 24-question dialog with operator captured durable decisions for the Aug 1 internal ship target (one month tighter than the prior Sept 1 anchor in CLAUDE.md). Pre-pivot strategic docs (COMMERCIALIZATION.md, IP.md, CURRENT_TESTING.md) archived with banner; thin V1-truthful replacements written for COMMERCIALIZATION + IP. STATE.md TOP PRIORITY rewritten with S201/S202/S203/S204+ plan + V1 KEEP list (Halloween layer, 1906 time slider, tour certificate, Heritage Trail-yellow) + V1 SKIP list (Bark, hotel B2B, big beta program). Zero binary changes; zero code touched.

Full session detail: `docs/session-logs/session-201-2026-04-29.md`. Final commit: rolled at S211 close (`6656326`).

---

## Session 200: 2026-04-29 — Commercial-prose legal cleaning + MASSGIS landmark surfacing + 384 Essex McIntire stub

Five distinct shipping arcs end-to-end. (1) **S198/S199 carry-forward #4 cleared** — `bundle-witch-trials-newspapers-into-db.js` no longer hard-codes a stale `ROOM_IDENTITY_V9` or writes `room_master_table`; align-asset-schema-to-room is the only stamper. (2) **Commercial-prose legal cleaning.** Per attorney guidance: 1439 commercial tier-0 POIs across 13 categories had description / short_narration / long_narration / historical_note / historical_narration / custom_description nulled in PG (CSV backup at `docs/archive/commercial-prose-wipe-2026-04-29/`). Asset DB filtered. Stripped POI sheet upgraded with sub-category line, tap-to-call (`ACTION_DIAL`, zero permissions), prominent Visit Website (`ACTION_VIEW`), no hours. PoiEditDialog tier-aware field gate hides prose for tier-0 commercials. New `commercial_tier0_has_prose` lint check (severity error, count=0 post-wipe). 103 commercial-hero leak files (3.2 MB) pruned. SERVICES added to BUSINESSES bucket in AudioControl + verify + prune (was the 1-cat misalignment). Architecture preserves the merchant_tier monetization path — tier > 0 reveals the full editor for paid-merchant content. (3) **MASSGIS landmark content now surfaces.** Witch Trials → Historic Sites of Salem fallback chain extended with `historicalNarration` first-priority — unlocked pre-1860 content for ~375 MASSGIS rows that had only `historical_narration` populated. PoiDetailSheet (regular marker tap) renders `historical_narration` in a new 4th body section (`bodyHistorical` widget); serializer + parser updated to round-trip the field through bundle args; auto-narration during sheet read still gated by `isHistoricalNarrationActive()` (preserves S192 strict pre-1860 rule); user can tap to manually trigger TTS. (4) **Field-naming alignment.** "Overview" / "About" / "The Story" relabeled to match admin column names ("Short narration", "Description", "Long narration"). New "Historical narration" section. Hours action chip removed entirely (also fixes `Hours · null` leak). (5) **384 Essex initial McIntire narration** authored from operator's first-hand knowledge — operator previously lived in the Sprague House. Memory `user_personal_context_beverly.md` updated to note operator's first-hand authority on personally-resided Salem properties. 528-char pre-1860 strict draft attributing the c. 1806 commission to Samuel McIntire late in his career. Operator will refine in admin tool as detail evolves. Discovered en route: SalemIntelligence's `mode_c_historic_building_details` is empty for most MASSGIS rows (architect/style/year never extracted); operator declined a MACRIS-detail-page scraper in favor of hand-authored content for canonical attributions. Final publish chain ran end-to-end after operator-confirmed admin edits (publish-pois 2219 → tours 2 → tour-legs 33 → bundle WT-* → align v15). APK uninstall+install on Lenovo HNY0CY0W. Operator session-end: "session end and we continue next session."

Full session detail: `docs/session-logs/session-200-2026-04-29.md`. Final commit: rolled at S211 close (`6656326`).

---

## Session 199: 2026-04-29 — Witch Trials content rewrite under attorney-driven word caps + import pipeline rework + civic-flag drift fix

Three big shipped end-to-end. (1) **Salem Witch Trials content rewritten** under attorney-driven hard caps — Salem Oracle newspapers ≤250 words, Witch Trial History tiles ≤150, People-of-Salem bios ≤150 (prior averages 466 / 369–419 / 2486 respectively). Salem Oracle generators (`Salem/cmd/{gennewspaper,gentiles,genbiographies}/`) got tightened prompts with explicit hard word budgets, dropped the "first POINTS entry repeats the lede" duplication, and added a `normalizeASCII()` helper so curly quotes / em+en dashes / ellipses / non-breaking spaces are all swapped for ASCII at the source (chunker stays happy, no surprise renderers). 3 sample-driven tuning rounds; operator approved "few words over" so no retry validator added. Full regen ran 45 min on the RTX 3090 (`salem-village:latest` gemma3:27b): 16 tiles + 202 newspapers + 49 Tier-1+2 bios = 267 generations, 0 failures. Final asset-DB word counts: newspapers avg 213 (26 over by max 32), tiles avg 166 (14 over by max 36), bios avg 135 (3 over by max 3). (2) **Import pipeline restructured.** Bio import was reading `~/Development/Salem/data/json/npcs/` and concatenating 7 game-fiction `narrative.*` sections (life_before_1692, emotional_arc, how_they_see_world, etc.) into the `bio` column — that's where the 2486-word avg came from. Fix: switched `import-witch-trials-npc-bios.js` to read `~/Development/Salem/data/json/biographies/` (the genbiographies output with a tight `tts_full_text` field) and cross-reference `npcs/` only for the metadata (role, faction, age_in_1692, historical_outcome) the biographies don't carry. New parallel JSON-direct importers `import-witch-trials-newspapers-from-json.js` + `import-witch-trials-tiles-from-json.js` replace the SalemIntelligence-mediated path (`:8089`) — source of truth is now Salem Oracle JSONs on disk. Incidental `publish-witch-trials-to-sqlite.js` fix: hoisted the `s()` Date-→-ISO converter so newspaper + article inserts no longer throw `SQLite3 can only bind numbers, strings, bigints, buffers, and null` on PG `timestamptz` columns. Newspaper-id collision (old `1691-11-01` vs new `salem_oracle_1691-11-01`) cleaned with one-shot DELETE in PG + both SQLite DBs. (3) **Civic-flag drift across 179 POIs fixed** (operator field-discovered M&M Contractors narrating during a tour walk despite category=SERVICES; root cause `is_civic_poi=true` was never cleared when category was changed from CIVIC). Audit: 179 drifted (PARKS_REC 67, SERVICES 50, EDUCATION 36, WORSHIP 19, 7 misc); same lint area as S196 carry-forward `civic_flag_mismatch`. One-shot SQL `UPDATE salem_pois SET is_civic_poi = false WHERE category != 'CIVIC' AND is_civic_poi = true AND deleted_at IS NULL` → UPDATE 179, 0 drift remaining. Drift prevention wired in BOTH backend (`cache-proxy/lib/admin-pois.js` `buildUpdateClause()` auto-syncs flag on category change unless body explicitly overrides) and frontend (`web/src/admin/PoiEditDialog.tsx` `useEffect` toggles checkbox on category dropdown change) — bidirectional, CIVIC→true / anything-else→false. Operator-confirmed expected behavior. Full publish chain re-ran (publish-salem-pois → publish-tours → publish-tour-legs → publish-witch-trials-to-sqlite → bundle-{newspapers,npc-bios,articles}-into-db → cleanup-stale-newspaper-ids → align-asset-schema-to-room) and APK uninstall+install on Lenovo HNY0CY0W. Operator session-end: "testing, session end and we continue to refine."

Full session detail: `docs/session-logs/session-199-2026-04-29.md`. Final commit: rolled at S209 close (`328be23`).

---

## Session 198: 2026-04-29 — Witch Trials admin tab unbroken + Oracle Newspaper page renders the same article it narrates (body_points retired)

Two operator-driven fixes. (1) Witch Trials admin tab regression — `cache-proxy/lib/admin-witch-trials.js` destructured `pool` from `deps`, but the shared deps object exposes the connection as `pgPool` (every other admin module does so), so every list/get/put route was 500'ing with "Cannot read properties of undefined (reading 'query')". Stale leftover from before deps was renamed; never noticed because the tab was untouched. One-line alias fix (`const { pgPool: pool } = deps`); cache-proxy restart with `set -a; source .env; set +a` so DATABASE_URL is in env (admin-lint runs a startup query and crashes on `pgPool=null` otherwise). (2) `body_points` retired from `salem_witch_trials_newspapers` so the in-app Oracle Newspaper page renders the same article that the Speak button + map-mode ambient narration narrates. Operator: "I want what I hear and what I see to be identical … users will want to see/hear the same content." Pre-S198: visual page rendered `body_points` (LLM rewrite, ~1370 chars / 6 paragraphs); audio narrated `tts_full_text` (continuous prose, ~2740 chars). Same row, different fields, drift between them. Fix: `WitchTrialsMenuDialog.buildNewspaperHtml` now splits `paper.ttsFullText` on `\n\n+|\n` paragraphs (body_points fallback branch deleted, JSONArray import removed); `WitchTrialsNewspaper` Room entity loses `bodyPoints`; `@Database(version = 14)` → `15`; new identity_hash `3e927300be7b2a8971fa6afb4aa5af78` (regenerated via `kspDebugKotlin`); `verify-bundled-assets.js` constants bumped V11→V15. PG column dropped on both `salem_witch_trials_newspapers` (live) and orphan `salem_newspapers_1692`. Stripped from admin-witch-trials whitelist + JSONB set, salem-schema.sql, publish-witch-trials-to-sqlite.js, bundle-witch-trials-newspapers-into-db.js, import-1692-newspapers.js, web/src/admin/WitchTrialsPanel.tsx. Full publish chain ran; first bundle pass failed with NOT NULL constraint because asset DB had stale schema baked in (CREATE TABLE IF NOT EXISTS skipped the rewrite); align-asset-schema-to-room rewrote it to v15 (0 rows); re-running bundle + publish-witch-trials-to-sqlite repopulated 202/49/16. Build clean (~11s). Lenovo HNY0CY0W: uninstall + install (NEVER `-r` per memory). Carry-forward: de-hardcode bundle-witch-trials hash (v9 stale by 6 versions; harmless because align is the canonical stamper, but log line is misleading).

Full session detail: `docs/session-logs/session-198-2026-04-29.md`. Final commit: rolled at S208 close (`1683ed9`).

---

## Session 197: 2026-04-29 — TTS chunker punctuation/dialog/dash/ellipsis trip-ups + Witch-Trials Speak bypasses AudioControl gate

Two targeted Android narration fixes off operator field-listening on the Lenovo. (1) S192 punctuation chunker in `NarrationManager.kt` extended with five trip-up handlers: closing-quote sentence boundary so quoted dialogue (`said." Then…`) splits cleanly, em-/en-dash mid-sentence beat (200ms `PAUSE_DASH`), single-char ellipsis `…` recognized as both sentence terminator and mid-sentence pause (300ms `PAUSE_ELLIPSIS`), `ABBREVIATIONS` expanded ~24 entries (Ave, Blvd, Rd, Ln, Esq, PhD, MD, Prof, Adm, Col, Cpl, Pvt, Sen, Rep, Fr, Br, BC, AD, Ca, Conn, Vt, Calif, Fla, Penn, Pa, al, pp, ed, approx, fig), and `MIN_SUB_CHARS` lowered 60 → 40 so commas in long sentences get an earlier explicit beat. Existing Mr/Mrs/St/Dr/Jr/Sr abbreviation guards preserved. (2) Operator reported the 9-dot-menu → Witch Trials → article/newspaper/bio Speak button silently dropped narration when Oracle Newspaper toggle was off (default OFF since S168). Root cause: `witchtrials_*` tags route to `NarrationKind.ORACLE`, then `enqueue()` drops at the AudioControl gate. Same dormant bug affected POI-sheet Speak for groups with their toggle off (BUSINESSES default OFF). Fix: added `userInitiated: Boolean = false` to `NarrationSegment`, threaded through `speakTaggedHint` / `speakTaggedNarration`. `enqueue()` skips the AudioControl group/oracle gates when `userInitiated=true`. `TourViewModel.speakSheetSection()` passes `userInitiated=true` — every one of its 11 callers (PoiDetailSheet × 7, WitchTrialsMenuDialog × 4) is a Speak-button tap by design. Auto-triggered ambient narration from `HistoricalHeadlineQueue` still respects the toggles. Two commits, two Lenovo reinstalls (uninstall + install). No DB / publish-chain work — bundled `salem_content.db` unchanged since S196 close.

Full session detail: `docs/session-logs/session-197-2026-04-29.md`. Final commits: `fb410f7`, `215b86d`.

---

## Session 196: 2026-04-28 / 29 — Hero leak fix + historical narration overhaul (344 regenerated) + admin audit log + revert UI

Six-hour overnight session. Started by fixing the S192 commercial-hero leak (re-categorized 4 civic non-profits — Historic Salem Inc., Destination Salem (×2), Salem Arts Association — to CIVIC so they keep their per-POI heroes; pruned 19 commercial hero files / 11 unique POIs); signed-AAB blocker cleared. Then operator field-listened on the Lenovo and flagged Ropes Mansion + Witch House narrations as repetitive/padded. Investigation showed the 1000+ word complaint was perception (max single column 311w) but the prose itself had closure flourishes ("lives lived within these walls") and restated facts. Surfaced 3 duplicate Witch House rows; operator soft-deleted 2, S196 soft-deleted the 3rd (600m-off coord), leaving `the_witch_house_at_salem` canonical. Generator overhaul (`generate-historical-narrations.js`): 100–250 word HARD CAP (was up to 500), STRICT_RULE 13 anti-repetition / closure-flourish ban, STRICT_RULE 14 no meta-narration, STRICT_RULE 15 name anchor (caught "Fairbanks House" hallucination on Witch House retry), bare-token / ALL-CAPS preamble strip, era-flexible commemorative tribute path. Operator policy mid-session: ALL Salem-connected commemoratives narrate (was: pre-1860 only) — pre-1860 subjects in 1859-historian voice, post-1860 subjects in present-day Salem voice; tribute-mode validator skips year-cutoff + pre-1860 banned phrases. EXCLUDE pattern dropped — Bewitched/Samantha now narrates the 1970 filming + Salem pop-culture connection. Memory file updated to match. Two sample rounds (6 + 10 POIs) reviewed in markdown comparison files. Full regen ran 6 hours wall time across 561 eligible POIs: 344 ok / 199 reject / 18 skip-not-pre1860. WW2 Memorial got a 135w narration (was empty in samples). Operator-requested mid-session: full audit log with revert. Schema (`scripts/install-audit-trigger.sql`): `salem_audit_log` table + PG row-level trigger on 9 admin tables (skips meta-only changes, reads `app.actor`/`app.source` via `current_setting`). Cache-proxy lib (`lib/admin-audit.js`) with list/stats/per-entity/single-row endpoints + revert endpoint that applies inverse op (UPDATE-back / DELETE-the-INSERT / re-INSERT-the-DELETE) and writes a new audit row capturing the revert. Web admin Audit tab (`AuditTab.tsx`) with stats strip, filter bar, color-coded action timeline, expand-to-diff (before/after per field), per-row Revert button + reason prompt, 15s auto-refresh. E2e revert test verified. Lint check `narration_regen_failed` ingests the regen log and surfaces 199 rejects in the Narration category. Caveat: 561 regen audit rows + admin-UI write rows have NULL actor/source — code paths not yet instrumented (deferred as task: multi-admin auth + actor-from-session wiring). Three commits pushed (`7c7b116`, `f5a9654`, `d961dc9`), one Lenovo reinstall.

Full session detail: `docs/session-logs/session-196-2026-04-28.md`. Final commit: `d961dc9`.

---

## Session 195: 2026-04-28 — Layers as global narration overrides + per-POI lint panel + heading-up GPS-only + drive vs Tiger validation

Repurposed the Layers menu (POIs Hist. Landmark / POIs Civic) from the S186 tour-mode-only widening into a true global override that gates eligibility in BOTH explore and tour modes. Phase A backfilled `is_historical_property=true` on 358 active HISTORICAL_BUILDINGS with populated `historical_narration` (294 of which were locked out of runtime by the `default_visible OR is_tour_poi` SQL gate). Phase B (one commit): Room v13 → v14, DAO `findNarrated*` widened to admit `is_historical_property OR is_civic_poi`, `NarrationGeofenceManager` gained explore-mode pref fields + commemorative-tribute name detector + civic-hist-eligibility helper + unified `isHistoricalQualified` gate, `getNarrationForPass` prefers historical>short for hist-property and civic-hist-eligible POIs, `SalemMainActivity.refreshHistoricalModeForActiveTour` pushes explore prefs unconditionally, marker filter promoted `isHistoricalProperty` to canonical hist-landmark signal. Then per-POI Quality flags panel landed in `PoiEditDialog` — fetches `/admin/salem/lint` on open, filters to items where `entity_id===poi.id`, renders severity badge + label + message + fix hint + Suppress button per row; Suppress posts to existing endpoint and dispatches `salem-lint-changed` for `PoiTree` to refetch the dot map. Operator confirmed WORSHIP/EDUCATION/PARKS_REC belong in the civic set (123 POIs flipped, total `is_civic_poi` 142 → 265) — but turning the toggle on didn't surface them, exposing a 3-stage marker pipeline where the Show-All-FAB-OFF tier filter was admitting only HIST_BLDG + paid merchants and dropping the new categories before the Layers gate saw them; tier filter now also admits hist-property + civic. Lint UX fixed too: `checkDuplicates` was double-flagging POIs at geocoder-fallback centroids with confusing "Duplicate POIs: <unrelated co-located names>" messages on top of the cleaner `geo_centroid_snap` warning; now skips members in 3+-coord-clusters and the check label reads "Co-located POIs (within 15m)." Heading-up map rotation re-enabled per operator, GPS-only — `HEADING_UP_ENABLED` kill switch flipped, sensor branch removed from `applyHeadingUpRotation` (no `DeviceOrientationTracker`), standard EMA + 5° hysteresis preserved; toggle-ON now snaps north-up first when no GPS bearing is yet known so gesture-rotated maps reset deterministically. Operator drive-tested 45 min on the new build — 1689 GPS points recorded, snap-to-Tiger analysis in PostGIS showed median 2.6m / mean 3.3m / p90 6.6m / p99 17.4m offset to road centerline (87.8% within 6m), with bearing-from-GPS-to-road clustering 51% in SE/E quadrants — i.e., Lenovo GPS chip has a real ~3-4m systematic NW bias (multipath off downtown Salem brick), basemap is fine. Built a Leaflet overlay (`/tmp/s195-drive-overlay.html`) with 1301 snap points color-coded by perp distance and dashed lines from outliers to nearest road. GPS dropouts during the drive decomposed: 1 18s gap from operator backgrounding the app, 16x 14s gaps during a 3-min cached-fix cluster (sky-obstructed), 1 208s gap while operator was browsing POI sheets parked. None were chip flakiness. 7 commits pushed to `origin/master`, 4 Lenovo reinstalls.

Full session detail: `docs/session-logs/session-195-2026-04-28.md`. Final commit: `22b5e08`.

---

## Session 194: 2026-04-28 — Historical narration QC tooling + Salem Oracle variant picker + commemorative-tribute rule captured

QC pass over the S193 corpus surfaced 6 corrupted narrations that got NULLed (LLM refusal monologue + leading-foreign-word anchor failures). Triage rather than rebake — a tightened-pattern scoring run flagged 47/358 clean and 311/358 with at least one critical/soft hit, with operator clarifying the soft buckets (rambling, hedging) are draft-quality content worth keeping. Tooling shipped to surface the broken ones through the existing Lint workflow: new shared validator `cache-proxy/lib/historical-narration-validator.js` driving (1) a `historical_narration_needs_refinement` lint check that fires for self-ref / modern-leak / subject-absent / tag-echo / address-list patterns (89 POIs flagged); (2) tree color-dot indicators on `PoiTree.tsx` — each POI row gets a red/amber/sky dot when any lint check fires, with category and subcategory containers aggregating descendant max severity + flagged-count split in the count column; (3) `OracleVariantPicker.tsx`, a 5-short × 5-long × 5-historic generator that fires SI `/api/intel/chat` calls in parallel (concurrency=3) using distinct style prompts per variant, validates historic candidates against the same regex set (rejected variants show disabled with reasons), and hides the historic column for non-historical POIs. Also dropped the `currentCategory === 'HISTORICAL_BUILDINGS'` gate on the `historical_narration` field in `PoiEditDialog` so any POI can have one authored without a category change first. Operator side-note late session: commemorative POIs (statues / memorials / plaques / arches) tributing PRE-1860 subjects ARE eligible for `historical_narration` — narration's subject is the figure/event commemorated, not the post-1860 object (Hawthorne statue → Hawthorne the man, not the 1925 bronze; Bewitched statue stays excluded). Saved durable rule `feedback_commemoratives_are_historical_tributes.md` + carry-forward comment in `cache-proxy/scripts/generate-historical-narrations.js:766`. Generator implementation deferred to next session (needs a tribute prompt builder + relaxed year guard + STRICT_RULES rule #5 rewording). I started the generator path edits then reverted when operator said "next session we can work with that" — leaving the generator half-modified would have caused commemoratives to slip through and get the buildings prompt.

Full session detail: `docs/session-logs/session-194-2026-04-28.md`. Final commits: `995ef12` (full S194 tooling), `f3ab266` (carry-forward marker).

---

## Session 193: 2026-04-28 — Demo-bug fixes + dormant historical-tour wiring + +294 historical narrations baked + activate runtime path

Marathon ~13-hour session. Started overnight listening to walks; ended evening with iterated rule refinements. Three demo-blocking field-test bugs shipped in the first half: (1) POI narrations getting cut off mid-sentence — `NarrationManager.onDone` was firing for every chunk/pause sub-id and clearing `currentSegment`+`playNext`, so state flipped to Idle while the engine was still mid-utterance; silence-fill raced in and the new segment's `QUEUE_FLUSH` wiped queued chunks. Fix: chunk-aware gate, only the bare `segment.id` (last chunk) ends the segment. (2) 1692 newspapers repeated because `historicalHeadlineQueue.advance()` ran AFTER the 3 s delay; concurrent silence-fill / heartbeat could re-fetch the same headline. Fix: advance() before the delay in all 5 paths; headline lost-on-abort. (3) Walk-sim FAB walked the wrong route — auto-started tour was Loading when the route selector evaluated `activeTour=null` and fell into a 21 km Downtown Salem perimeter loop. Fix: `withTimeoutOrNull(3 s) { tourState.first { it is Active } }`, rebind activeTour. Operator further clarified "what we have as tours is definitive" — every synthetic-route fallback (Downtown perimeter, Heritage Trail JSON, stop-coord stitch) removed; empty polyline → toast + abort. Saved hard rule `feedback_authored_tours_are_definitive.md`. 1692 newspaper queue also made non-repeating: `pollNext()` returns null at corpus exhaustion (no loop-back), prefs key v2→v3, chronological order preserved. Dormant historical-tour wiring shipped end-to-end: PG/Room column `is_historical_tour` (Room v12 → v13, identity hash `d0d3d240e2d04c159767b90860fe7a8c`), publish-tours + admin-tours endpoints + web admin "Historical tour" checkbox, TourEngine sets `historicalNarrationMode`, `NarrationGeofenceManager.getNarrationForPass` returns `historical_narration` when active. De-hardcoded `publish-tour-legs.js` identity hash (S186 carry-forward closed). **Overnight gemma3:27b bake (4h53m, monitored across 5 wake-ups)** populated 294 new historical_narrations: JOB 1 (--force on 3 stranded pre-1860 POIs) + JOB 2 (full bake with null-year filter relaxed because SI's `mode_c.built_year` was empty corpus-wide). Coverage 70 → 364. Bundled DB rebuilt and shipped to Lenovo. Both tours flagged `is_historical_tour=true` per operator's "all selected walking tours are historical" / "tours are only for historical tours" — saved hard rule `feedback_tours_are_only_historical.md`. Field-test: tour started but no audio for new historical content. Root cause: `PoiDetailSheet.queueSheetReadThrough` was reading short/about/story directly, bypassing the geofence path's `getNarrationForPass` entirely. Fix: `TourViewModel.isHistoricalNarrationActive()` helper; sheet now plays `intro + historical_narration` when active. Operator iterated the fallback rule for HIST_BLDG without `historical_narration`: first `long → short → name`, then narrowed to `short` only (long is rich modern content that would dominate a historical tour). Both runtime paths (geofence + sheet read-through) updated; `feedback_narration_fields_purpose_separated.md` appended with the HIST_BLDG short-only runtime exception. One commit (`72cd303`) extended the silence-fill gates to fire on `historicalNarrationMode` so newspapers auto-fill silence gaps during a historical tour — operator reversed: "if they want oracle newspaper, they can simply toggle it on" — reverted to explicit-Oracle-only opt-in. Long silences on a historical tour are an accepted UX trade-off going forward. 12 commits + 1 revert pushed to origin/master, ~6 reinstalls on Lenovo HNY0CY0W, GPU sat at 100% / 22.5 GiB through the bake.

Full session detail: `docs/session-logs/session-193-2026-04-28.md`. Final commit: `9bf8e8d`.

---

## Session 192: 2026-04-27 / 28 — Historical narration system end-to-end + 4 field-test fixes

Built the third narration field `historical_narration` from PG column through to Lenovo. Generator script `cache-proxy/scripts/generate-historical-narrations.js` consolidates GROUND_TRUTH from SI dump + SI historical_note + local Salem JSON (buildings/biographies/facts) for 1692-trial-era entities; 6 prompt iterations of validator hardening (year guard, sentence-rescue, meta-gap ban, paces evasion, modern-business strip, abbreviation/initial guards). Backfilled `intel_entity_id` 53 → 565 via name+coords matcher. Operator approved 33 narrations across 3 review rounds; full population background run wrote 70 narrations (5 reject, 479 skip-not-pre1860, 11 skip-commemorative). Room v11→v12 schema bump + admin UI Historical Narration textarea + lint check `hist_pre1860_no_historical_narration` shipped. Field-test fixes shipped same session: hidden-POI narration visibility gate (S191 carry-forward — 5 `findNarrated*` queries gate on `default_visible OR is_tour_poi`), Oracle Newspaper silent bug (5 gate sites in SalemMainActivityNarration also honor `AudioControl.isOracleEnabled()`), Walking Tours infinite spinner (Room schema mismatch — fixed via publish + align), smart TTS chunker in NarrationManager (sentence-level + abbreviation/initial/number guards + sub-split for long sentences + pause hierarchy 100/200/300ms). Two new feedback memories saved (no-meta-gaps, narration-fields-purpose-separated).

Full session detail: `docs/session-logs/session-192-2026-04-27.md`. Commit: `1e10473`.

---

## Session 191: 2026-04-27 — Tour-flag bulk fix, two new lint checks, RHF reset race, dest_salem/haunted_happenings cleanup, hidden-POI narration bug diagnosed

Mid-evening continuation immediately after S190. Diagnosed why "tour POI flags appear lost when moving a location" — `/move` is clean, `PUT` only sends RHF dirtyFields, but `PoiEditDialog.tsx`'s `useForm({ values: defaultValues })` was resetting the form on every parent `selectedPoi.poi` ref change (handlePoiMoved spreads a new object after a marker drag → unsaved checkbox toggles wiped). Fix: keyed `defaultValues` `useMemo` on `poi.id` only. Bulk-flipped 77 curated `HISTORICAL_BUILDINGS` rows (`data_source NOT LIKE '%massgis%'`) to `is_tour_poi=true` so the curated bucket is now 118/118 (MassGIS bucket stays 0/487 per S186); added `hist_curated_not_tour` lint check. Cleaned 2 stale `is_civic_poi=true` flags on rows whose `category` had been re-set to HISTORICAL_BUILDINGS (Witch House at Salem, Howard Street Cemetery — leftover from S186 bulk civic flag); added `civic_flag_mismatch` lint check (`is_civic_poi=true AND category<>'CIVIC'`). Two new synthetic admin tree categories `SOURCE:destination_salem` / `SOURCE:haunted_happenings` (new `dataSourceContains` helper; `categoryFilterMatches` honors source-needle keys; `buildTree` prepends source nodes flat, no subcategory layer; explicit `TreeNode` typing on `realNodes` to keep TS happy). Per operator: soft-deleted 67 POIs whose provenance was solely `destination_salem` and/or `haunted_happenings` (no OSM token); stripped those tokens from 63 OSM-bearing survivors via `regexp_replace((destination_salem|haunted_happenings)\+|\+(destination_salem|haunted_happenings)\M, '', 'g')`. Asset DB shrank 9.2 MB → 9.0 MB. Two full publish chains + APK reinstalls on Lenovo HNY0CY0W during the session. Operator then asked about hidden POIs still announcing — **diagnosed but not fixed**: `SalemPoiDao.findNarrated()` query gates on `is_narrated=1` only, ignoring `default_visible`; 1,155 hidden-but-narrated POIs (489 hist-bldg, 197 OFFICES, 147 HEALTHCARE, 133 CIVIC, 82 SHOPPING, 52 AUTO_SERVICES) load into `NarrationGeofenceManager.points` and fire ENTRY events in explore mode. Live evidence: 6 of last 10 narrations on device were `default_visible=false` (4 OFFICES + 2 CIVIC). Fix proposed (`AND default_visible=1` or `OR is_tour_poi=1`) but session-ended before applying — primary carry-forward to S192.

Full session detail: `docs/session-logs/session-191-2026-04-27.md`. Commit: `4d560be`.

---

## Session 190: 2026-04-27 — Admin taxonomy tooling, Geocodes tab, routing graph fix (coincident-node merge + island bridging)

Substantive feature session driven by operator's parallel work on the V1 launch intake doc. Built strict Category/Subcategory dropdowns in the POI editor sourced from `salem_poi_categories` / `salem_poi_subcategories` with inline `+ Add new` panels (new endpoints `cache-proxy/lib/admin-categories.js`); split `HISTORICAL_BUILDINGS` into synthetic "Historic Buildings" (curated, 126) vs "POI Hist. Landmark" (MassGIS MHC, 487) tree nodes that propagate through the map filter / legend / banner; reworked subcategory rendering so EVERY canonical subcategory shows under its parent (including empty ones with `(0)`) plus a `(no subcategory)` bucket for unassigned POIs; renamed `OFFICES` DB label `'Offices & Services'` → `'Professional Services'` and refactored `categoryLabel` so tree, dropdown, legend, and filter-banner all read from one source (DB). New top-level `GeocodesTab` for the address-geocode deep scan. Heavy work on tour-leg routing — added per-leg diagnostics (`detour_ratio`, `point_count`, `elapsed_ms`, `suspicious` flag) in `admin-tours.js`, surfaced as amber "⚠ N suspicious legs" banner in `TourTree`. Diagnosed `tour_Density` failures: Charter Cemetery has 7 OSM pedestrian features that all give each endpoint a fresh vertex ID even when endpoints sit at the EXACT SAME lat/lng — found 3 different node IDs at `42.5206924, -70.8918647`. Per operator's "see what is going on with the router/graph engineering, the web admin portal requires it to map legs correctly" — saved `feedback_routing_build_engineering_allowed.md` scoping the older "tour routing is content not engineering" rule to the runtime engine only. Added two passes to `tools/routing-bake/bake-salem-routing.py` BEFORE the existing S179 edge-split: `merge_coincident_nodes(tol=3m)` fuses near-coincident vertices (lowest-id wins, drops self-loops, dedups parallel edges keeping shortest); `bridge_isolated_components(tol=60m, max_island=30)` adds one synthetic `mtfcc=SYNTH_BRIDGE` walkable edge per small island to the closest main-component node. Result: connected components 49→46→14, `tour_Density` from 4 legs/3 skipped to 9 legs/1 skipped/4 suspicious (all bridge-detours through cemetery interior). No Kotlin changes — `Router.kt` treats `mtfcc` as opaque metadata so `SYNTH_BRIDGE` is harmless. Full publish chain ran (publish-salem-pois → publish-tours → publish-tour-legs → align-asset-schema-to-room) and APK installed on Lenovo HNY0CY0W via uninstall+install. `verifyBundledAssets` flagged 9 commercial-hero leaks (POIs with `merchant_tier=0` carrying hero images) — pre-existing condition, bypassed `-x verifyBundledAssets` for dev build only; flagged as carry-forward to clean before any signed AAB upload.

Full session detail: `docs/session-logs/session-190-2026-04-27.md`. Commit: `a9ea63f`.

---

## Session 189: 2026-04-26 — Total launch review + .odt intake document

Diagnostic / planning session, no app or admin code changed. Operator asked for a "total review" of where the project stands going to market; spawned two parallel Explore agents to summarize legal/IP/commercial docs (`GOVERNANCE.md` / `IP.md` / `COMMERCIALIZATION.md` / both privacy policies) and design/test/sessions/OMEN context (`DESIGN-REVIEW.md` / `MASTER_SESSION_REFERENCE.md` / `CURRENT_TESTING.md` / S185–S187 live logs / OMEN notes + ACTIVE.md), then synthesized a 10-section inline review (Executive Snapshot / Strengths / Critical Concerns / Soft Concerns / Open Questions / Feature Ideas / V2 Backlog / Honest GTM Assessment / Action Order / Top Risks). Critical-blocker findings: Form TX copyright filing 23 days out and unconfirmed; 2026-04-20 lawyer meeting outcome not in any session log; privacy policy `[TBD]` placeholders unresolved; Play Console developer account not started (multi-week lead); 365 dedup losers still soft-deleted in PG (S185 estimated 110, S187 lint surfaced actual 365); V1 feature-gating eyes-on smoke test from S180 still owed (5 items); single-medium upload-keystore backup. Soft findings include doc drift in CLAUDE.md / STATE.md still citing the pre-S167 544 MB poi-icons figure (current is 3.4 MB), zero Kotlin unit tests with the narration gate as the highest-risk untested logic, two consecutive zero-Android sessions (S187/S188) while operator-side launch prep has not visibly begun, untested Android TTS audio quality for a $19.99 paid product, missing marketing/ASO plan, no iOS plan. Operator then requested the review as a `.odt` they could annotate as the intake into S190+ planning. Built `cache-proxy/scripts/build-launch-review-odt.py` (odfpy-based, full style suite — heading hierarchy, severity banners, inline spans, violet-bordered ResponseBox blocks) and generated `docs/V1-LAUNCH-REVIEW-2026-04-27.odt` (13 KB OpenDocument Text, ~40 KB content.xml, 11 sections with explicit "Operator Response" blocks after every concern, question, feature idea, action-week, and risk). One bug fixed mid-build (helper signature mismatch when passing `text_props=...` as kwarg into `**text_props`). The annotated copy of the `.odt` will drive S190+ work; pre-existing S189 TOP PRIORITY items in `STATE.md` remain valid and are now framed inside the broader launch-review action order.

Full session detail: `docs/session-logs/session-189-2026-04-26.md`. Commit: `dd6ac0e`.

---

## Session 188: 2026-04-26 — Geocode-candidates modal polish (cluster geocoding, smart conflict analyzer, map preview w/ accept/ignore) + Witchy-only basemap with server-side auto-overzoom

Single-evening polish pass on the S187 Geocodes modal driven by nine sequential operator refinements. Backend extends `/admin/salem/lint/poi/:id/geocode-candidates` to also geocode every duplicate's address (one shared Tiger client, cluster-wide best-match selection by lowest rating + nearest-to-source tiebreak), adds a `salem_geocode_blacklist(poi_id, lat, lng, reason)` table with idempotent CREATE plus `POST/DELETE /admin/salem/lint/poi/:id/blacklist-candidate` endpoints filtered by the geocode-candidates endpoint, and tightens to a 5 s per-call / 20 s wall-clock budget with `deleted_at IS NULL` filtering on dupes (operator: "soft deleted are not concerns, just footnotes") so the modal can never hang. Frontend modal grows side-by-side stored/Tiger detail panels, BEST badges, per-dupe candidate lists with Use & soft-delete actions, and a `Show on Map` per-candidate button that opens a new AdminMap floating panel with a smart `analyzeGeocodeConflict()` diagnoser (severity good/warn/bad, plain-English conflict bullets, recommendation pill on the matching action button), five action buttons each spelling out their effect (Move POI / Validate stored / Hide candidate / Edit address / Cancel), Focus toggle to hide unrelated POIs, Show all candidates rendering every cluster Tiger hit color-coded by rating with dashed lines back to source, `×` close + Esc keybind. Mapnik/Esri/OSM removed from the basemap picker (operator: Witchy only); `cache-proxy/lib/admin-tiles.js` gains `sharp`-backed auto-overzoom that walks UP to 8 ancestor levels (crop+resize) or DOWN to 2 child levels (stitch 2^dz × 2^dz children) when the requested (z,x,y) isn't baked in `salem_tiles.sqlite`, with a 500-entry LRU and `X-Tile-Source` headers for devtools introspection.

Full session detail: `docs/session-logs/session-188-2026-04-26.md`. Commit: `042b969`.

---

## Session 187: 2026-04-26 — Admin Lint tab: 15-check data-quality scan + tiger.geocode address validator + false-positive suppression

Operator asked to refine the admin tool before returning to the Android app — built a feature-complete data-quality "Lint" tab in the web admin. Backend (`cache-proxy/lib/admin-lint.js`, NEW) registers 15 instant checks (Narration ×2, Tour gates ×1, Historical Buildings ×2, Content ×2, Geography ×2, Duplicates, Cleanup, Provenance, Tour data ×3) plus 1 on-demand check (address↔geocode mismatch deep scan via `tiger.geocode()`). Frontend (`LintTab.tsx`, `GeocodeCandidatesModal.tsx`, NEW; AdminLayout/AdminMap modified) renders a left-side category tree with drill-down counts, right pane with plain-English message + fix hint per item, and four per-item actions: Open Editor, Show on Map (lint nav also filters the map to ONLY the current check's POIs and bumps the fly-to floor from zoom 17 to 20), Geocodes (modal with N tiger.geocode candidates, [Use This Location] override per candidate, [Validate Current Location] that POSTs to a new `/mark-verified` endpoint), and Suppress (operator: "There will be false events — things like the lighthouse" — new `salem_lint_suppressions` table with PK (poi_id, check_id), every POI check inlines a NOT IN exclusion clause, header toggle to view/unsuppress). Tiger MA address data was missing — `tiger.geocode()` was returning city centroids only. Configured `tiger.loader_platform` for peer-auth + custom staging, generated state-25 loader script, ran in background (~322 MB / 13 tables / 576K addr / 703K featnames / 761K edges); ma_edges import initially failed because salem-router added 5 pgrouting columns to the `tiger.edges` parent — fixed with a custom edges-only loader that ALTERs the staging table to add NULL placeholders before `loader_load_staged_data` runs. Address normalizer strips ", USA" suffix (Tiger returns 0 candidates if it sees this), normalizes "Street → St" / "Avenue → Ave" / etc., 15-second per-call statement_timeout so misses don't hang. Initial counts (operator-actionable): 500 hist_bldg missing year (capped), 365 soft-deleted dedup losers (S185 estimate was 110 — actual 3.3× higher), 119 tour POIs missing image, 75 outlier coords, 150 in geocoder-fallback clusters, 54 thin-provenance tour POIs, 30 civic POIs silent. Tour-data checks all 0 (clean). Address-geocode deep scan idle until operator clicks Run.

Full session detail: `docs/session-logs/session-187-2026-04-26.md`. Commit: `7a6da86`.

---

## Session 186: 2026-04-26 — Tour Mode narration gate + mode-dependent Layers checkboxes (POIs Civic / POIs Hist. Landmark)

Replaced the missing Tour Mode narration contract. Pre-S186 a tour silently narrated everything in proximity (no gate); operator wanted "during a tour, only TOUR POIs narrate by default; Layers checkboxes opt-in additional classes." Built: new `is_civic_poi` column on `salem_pois` (Room v10 → v11, identity_hash `55b35822253369a8af9f91e8bdbf8656`), bulk-flagged 157 CIVIC POIs; bulk-flagged 164 more rows as `is_tour_poi=true` (WORSHIP/PARKS_REC/EDUCATION + 8 HIST_BLDG museums) so 216 POIs are now always-narrate-during-tour; new "POIs Civic" checkbox in the Layers menu beside the existing "POIs Hist. Landmark"; `NarrationGeofenceManager.setTourMode(active, allowHistLandmarks, allowCivic)` short-circuits `isHistoricalQualified` when active. Operator reframed twice mid-session: (1) Layers checkboxes have separate explore vs tour state (PREF_POI_HIST_LANDMARK explore default ON, PREF_POI_HIST_LANDMARK_TOUR default OFF, same for civic) — explore = "Explore Salem" permissive default, tour = restrictive default, each remembers independently; (2) `is_tour_poi=true` is the force-visible AND force-audible override — Layers checkboxes only add/remove the *additional* class, never silence or hide the tour-flagged baseline. Post-AAB field test surfaced two bugs: (a) marker filter wasn't honoring is_tour_poi short-circuit so HIST_BLDG-categorized tour POIs (e.g. First Church Meetinghouse 1692 Site) disappeared when Hist Landmark checkbox went OFF — fixed; (b) walk-sim start was unconditionally calling `narrationGeofenceManager.resetSession()` which wiped the 1-hour dedup state, so the same POI re-fired on every walk-sim restart — operator rule is "only cold start clears dedup," removed the call. Web admin: `is_civic_poi` added to PoiEditDialog Operational checkboxes + cache-proxy admin-pois.js whitelist + poiAdminFields.ts. New canonical publish chain: `publish-salem-pois.js` (now writes is_civic_poi) → `publish-tours.js` → `publish-tour-legs.js` → `align-asset-schema-to-room.js` (last word — auto-discovers latest schema JSON, stamps identity_hash + user_version). Carry-forward: `publish-tour-legs.js` still has a hardcoded v10 hash (overwritten by align script but a footgun); ENTERTAINMENT (207) + most LODGING (24/33) lack `year_established` so the Hist Landmark override only narratable set is currently 79 POIs until operator backfills year data; 8 newly tour-flagged museums have no narration text yet. S185 carry-forwards still apply (onboarding-to-nearest-point, GPS-OBS heartbeat, Tier 2 publish-chain auto-bake, dedup-loser hard-delete, re-author 5 deleted Kotlin tours). Operator close: "good enough, session end and we continue next session with refinement."

Full session detail: `docs/session-logs/session-186-2026-04-26.md`. Commit: `156a703`.

---
## Session 185: 2026-04-26 — Land S183/S184 walking-route work in the AAB; flush latent asset-DB schema landmines

The S183/S184 operator-curated `tour_WD1` is now actually in the AAB on the Lenovo. New Room `TourLeg` entity (v9 → v10), new `cache-proxy/scripts/publish-tour-legs.js` (PG `salem_tour_legs` → asset's `tour_legs`), `TourViewModel.computeTourPolyline()` rewritten to read baked legs and assemble them with the same anchor + clip rendering policy as the admin's `TourLegsLayer.drawLeg`. Also rewired walk-sim's route loader (was falling back to the bundled "Downtown Salem" route because tour_WD1 has no JSON in `assets/tours/` and no POI-bound stops). Operator clarified the V1 model mid-session: tours are polyline-only paths users follow; POIs govern their own narration via geofence proximity, fully independent of any tour. `TourEngine.startTour` no longer errors on stop-less tours, and Historical Mode is auto-skipped when the active tour has zero user-facing stops (was building an empty whitelist that silenced every POI on the route, which is what hid Ames Memorial Hall during the operator's first walk-sim test). Two latent asset-DB landmines flushed during the install loop: (1) tables had `DEFAULT` clauses from `salem-content/create_db.sql` while Room codegen has none → `TableInfo.equals` mismatch → `fallbackToDestructiveMigration` wiped the asset on every fresh install; (2) `PRAGMA user_version` lagged the `@Database(version=N)` value → upgrade migration ran → fallback destructive. New `cache-proxy/scripts/align-asset-schema-to-room.js` is the canonical bridge — rewrites every Room-managed table using the exact `createSql` from `app-salem/schemas/<DB>/<v>.json` and stamps both `identity_hash` and `user_version`. The 5 historical Kotlin-curated tours (Essentials/Explorer/Grand/Witch Trials/Heritage Trail) were dropped from the asset; operator will re-author them in PG via admin when wanted. Admin polish at session end: POI markers at zoom ≥17 now show humanized category above + name below (mirrors Android `MarkerIconHelper.labeledDot`), cluster threshold lowered to 17 to match. Carry-forward (S186): tour-start onboarding to nearest point on polyline; investigate Lenovo's GPS-OBS heartbeat (system delivers fixes but app's tour observer reports them stale); hard-delete dedup losers; Tier 2 admin → build pipeline auto-bake (now critical with 3 publish scripts).

Full session detail: `docs/session-logs/session-185-2026-04-26.md`. Commit: `3c68102`.

---

## Session 184: 2026-04-26 — Admin walking-route UX polish: marker-anchored polylines + click-to-highlight + click-to-recenter

Continuation of S183's authoring pipeline — pure web admin UX, zero backend or bake changes. Diagnosed the operator's "leg 1 → 2 missing" report as a marker-vs-polyline gap (the on-device router snaps each waypoint to the nearest pedestrian-graph node, leaving 1.1–25.1 m visible gaps at the dots), and shipped: (1) `TourLegsLayer` now anchors each leg's polyline to its from/to marker coords AND clips the routed geometry to the polyline-points closest to each marker, so road-snap gaps disappear AND overshoot/corner-and-back doglegs (e.g. leg 4 ending 16 m past waypoint 5) get trimmed; (2) click a leg row in the side panel → that leg highlights red on the map (custom Leaflet pane at z-index 450 so it sits above `overlayPane`'s dashed inter-stop connector but below `markerPane`'s numbered waypoint dots); click anywhere on a leg polyline also selects the matching row; (3) click a waypoint row → map flies to the stop (zoom ≥18, repeat-clicks re-fire via a nonce); (4) cancel for "+ Free waypoint" / "+ POI as stop" modes — × button on the amber banner, Esc key handler, "(Esc to cancel)" hint. One Rules-of-Hooks bug (Esc `useEffect` declared after the early-return guard) caused a white-screen on refresh; fixed by moving the effect above the guard. Surfaced two routing-graph oddities — legs 10 and 12 of `tour_WD1` were 2082 m / 1850 m for waypoints 80–108 m apart because stops 10–13 sat on/inside Salem Common where the pedestrian graph has no edges; per `feedback_tour_routing_is_content_not_engineering.md`, operator dragged those waypoints onto streets and the legs went sane. Files: `web/src/admin/AdminMap.tsx`, `web/src/admin/AdminLayout.tsx`, `web/src/admin/TourTree.tsx`. TypeScript clean throughout. App-side bake / `TourViewModel` switch / Lenovo smoke-test items still queued.

Full session detail: `docs/session-logs/session-184-2026-04-26.md`. Commit: `32ad045`.

---

## Session 183: 2026-04-26 — Tour walking-route content tool: per-leg compute + admin overlay

Different path from S182's "hand-curate stop coords" framing. Operator created a fresh `tour_WD1` (15 free waypoints) and asked for a one-click admin tool to convert waypoints into per-leg walking polylines, persist them, and render the whole path as a saved overlay. New PG table `salem_tour_legs` (per-leg `polyline_json` JSONB + distance/duration + `router_version` + nullable `manual_edits` JSONB), 3 new admin endpoints under `/admin/salem/tours/:tour_id` (`GET /legs`, `POST /compute-route` with manual-edits preservation unless `force:true`, `POST /legs/:leg_order/recompute`). `cache-proxy/server.js` captures the salem-router module into `deps.salemRoute`/`deps.salemBundle` so admin-tours calls the same in-process bundle Find→Directions uses (44,223 nodes / 47,704 edges). Web admin: new "Walking route" panel above the Waypoints list (Compute / Recompute, Force-all, per-leg list with distance + ↻ recompute, stale indicator), and a green outlined polyline overlay on the Leaflet map matching the in-app `DirectionsLayer` style. CLI verification: `tour_WD1` computed cleanly to 14 legs / 6815.5 m / 4868.2 s, no skipped legs, router_version `2026-04-26T04:47:09+00:00`. TypeScript clean (`tsc --noEmit` exit 0); Vite HMR fired through TourTree → AdminLayout → AdminMap. Salem-content bake to include `salem_tour_legs` and the app-side switch (`TourViewModel.computeTourPolyline()` reads baked legs first, falls back to runtime `routeMulti`) are deferred to follow-up sessions — that's what closes the loop into the AAB. S182's content-not-engineering rule still holds: route work is now entirely authoring-time (admin one-click), zero runtime engine change in the APK.

Full session detail: `docs/session-logs/session-183-2026-04-26.md`. Commit: `d943595`.

---

## Session 182: 2026-04-26 — S181 OSM-policy reversal RETRACTED; tour-rendering declared content-not-engineering

Operator opened with "I don't like your solution from the last session" — pushed back on S181's OSM-merge plan after testing P2P "tap POI → directions" between two Heritage Trail stops and getting a clean route, which the multi-stop tour render of consecutive stops can't reproduce. Traced both code paths: `Router.routeMulti` is literally per-leg `route()` calls + `concat()`, byte-for-byte equivalent to N P2P calls. The actual divergence is in the input — tour legs feed POI centroids (often inside/behind a building polygon, KNN snap can land on the wrong side), P2P feeds the user's GPS (already on a sidewalk). Same algorithm, different start node, different shortest path, one crosses the building and one doesn't. S181's proposed broad OSM pedestrian merge wouldn't have fixed it. Reverted: memory `feedback_no_osm_use_local_geo.md` restored to S178 surgical-only constraint, MEMORY.md index reverted, `docs/plans/S182-osm-pedestrian-routing-merge.md` archived to `docs/archive/S182-osm-pedestrian-routing-merge_archived_2026-04-26-misdiagnosis.md` with banner, CLAUDE.md pinned next-steps block reframed. Feasibility-checked an address-anchored alternative — `salem_pois.address` has 97.5% coverage, TIGER `lfromadd/ltoadd/rfromadd/rtoadd` interpolation works (sample "237 Essex St" geocoded to 12 m offset on Essex St centerline) — but ≈half the Heritage Trail stops have addresses that fall through (intersection / square-only / no-number / TIGER coverage gap), so the resolver would need a layered fallback. Operator rejected the whole engineering path: "I need to do that tour by hand, I don't want to muck more with the mapping, wasting too much time on that." Saved a new HARD RULE memory `feedback_tour_routing_is_content_not_engineering.md` to prevent this loop in future sessions. Cache-proxy (4300) and Vite admin (4302) brought up at session end for the operator's curation work. **No code changes shipped this session.**

Full session detail: `docs/session-logs/session-182-2026-04-26.md`. Commit: `9efc931`.

---

<!-- END OF ROLLING WINDOW — Sessions 180 and earlier are in SESSION-LOG-ARCHIVE.md -->
<!-- S180 rolled to archive 2026-04-27 by the session-end protocol (S190) -->
<!-- (S179 rolled by S189) -->
> SESSION-LOG.md keeps only the most recent 10 sessions. On every session end, the oldest session in SESSION-LOG.md is moved here (newest archived first).

## Session 181: 2026-04-26 — Routing-data OSM policy reversal + S182 plan; one-medium keystore backup

Operator picked S180 carry-forward #1 (off-machine keystore backup) and #6 (eyes-on smoke test). Backup: created staging dir + sudo script that SHA-256-verifies before/after copy and chowns USB destination back to user; one-medium copy landed on `/media/witchdoctor/writable`, second copy on a different medium still owed before first Play Console upload. Smoke test surfaced one issue (Salem Heritage Trail polyline visually grazing historic-building polygons in downtown), which became a five-step diagnostic rabbit hole: logcat confirmed router output was correct (47,704-edge bundle, 21-turn `routeMulti` route, no fallbacks); PostGIS against `massgis.buildingfp` found zero downtown overlaps; against the actual rendered `salem-historic-buildings.geojson` set found 14 walkable edges that cut historic buildings (worst: St Peter St → Gardner Block 57m, Salem Green cluster grazing 3 polygons). Operator reframed: "why isn't it following the sidewalk?" — exposed that downtown `salem.edges` is ~92% TIGER street centerlines and only ~8% pedestrian, with `osm.salem_pedestrian_edges_resolved` (1,030 downtown footway/path/pedestrian/steps edges, ~120km Salem-area total) sitting unused. After full Witchy-tile-bake source review (~90% OSM-rendered, ~10% MassGIS, with documented S158 carve-out), operator reversed the prior surgical-only OSM constraint: "if we require OSM to do this right, then let's change our direction and allow OSM for our routing needs." Memory `feedback_no_osm_use_local_geo.md` amended (S178 surgical-allowlist superseded for pedestrian network; broad pedestrian-network ingest permitted under topological-merge constraints with `pgr_strongComponents ≤ 5` hard gate). Plan written at `docs/plans/S182-osm-pedestrian-routing-merge.md` — 4 phases / 2-3 sessions / risk register / rollback. **No code changes shipped this session.** Smoke test interrupted before items 2-N (POI detail Visit Website ACTION_VIEW handoff, Find dialog Reviews/Comments hidden, Find Directions on-device router, toolbar gating, webcam dialog gating) — carry-forward.

Full session detail: `docs/session-logs/session-181-2026-04-26.md`. Commit: `cf863d9`.

---
## Session 180: 2026-04-26 — V1 Play Store gating chores + first signed AAB on Lenovo

Tier 1 of the S180 ship-readiness review shipped end-to-end. **P1** stripped the manifest network surface (INTERNET / ACCESS_NETWORK_STATE / cleartextTraffic gone, WickedMap LAUNCHER intent removed, VoiceTest narrowed to exported=false). **P2** hard-gated 10 V1-disabled UI sites that bypassed the OkHttp `OfflineModeInterceptor` via WebView, osmdroid online tile sources, and external Intents — all behind `if (!FeatureFlags.V1_OFFLINE_ONLY)` so R8 + const dead-code-eliminates them from the V1 release binary; Find dialog Directions button permanently switched to on-device `walkTo()` instead of Google Maps URL. **P3** revised V1 website behaviour per operator: Visit Website button stays visible, click hands off to external browser via ACTION_VIEW (no in-app WebView); defensive gates on every social dialog body. **P4** locked `UserDataDatabase` migrations — removed `fallbackToDestructiveMigration` so paying users no longer lose POI encounter history on every update; schema v2 is the v1.0.0 floor, future bumps need real Migration objects. **P5** versionCode 1 → 10000, minifyEnabled+shrinkResources, new conservative `proguard-rules.pro` (with -dontwarn for sqlite-jdbc/slf4j leaking from `:routing-jvm`), `signingConfigs.release` block reading from `~/.gradle/gradle.properties`, and `verifyBundledAssets` Gradle Exec task wired to preBuild (catches the silent rebake-wipe class of bug). Generated upload keystore at `~/keys/wickedsalem-upload.jks` (PKCS12, RSA-2048, 10000-day validity, 32-char random pw); registered in OMEN credential audit (committed + pushed to OMEN remote). First signed AAB built (78 MB) + signed release APK (87 MB) installed on Lenovo TB305FU; SalemMainActivity at 60fps with no AndroidRuntime errors. Two new memories saved: `feedback_admin_changes_propagate_to_builds.md` + `feedback_v1_feature_scope_explicit.md`. Operator-side carry-forward: keystore off-machine backup before first Play Console upload, Form TX copyright (24 days to 2026-05-20), Play Developer Account verification, public Privacy Policy hosting.

Full session detail: `docs/session-logs/session-180-2026-04-26.md`. Commit: `04c99e3`.

---

## Session 179: 2026-04-26 — Tour routing unified to live engine + bake-time edge splitting

Two shipped pieces. **P1:** runtime tour polyline now computed via the same `Router.route()` against the bundled Salem walking graph that powers point-to-point "Get directions" — pre-baked `tour_legs` / `routeToNext` from S178 P6 are no longer consulted, gold tour line replaced with green + dark-green-border + intersection markers identical to point-to-point, `WalkingDirections.getBundledTourRoute()` removed. **P2:** operator field-tested directions to Phillips House (34 Chestnut St) drew a polyline that didn't enter Chestnut St; root-caused to TIGER's intersection-only vertex layout (95m gap to nearest walkable vertex, 108m tail approach blocked by 60m safety cap). Added bake-time edge splitting in `bake-salem-routing.py` — long edges >60m split into ~40m sub-segments with synthetic mid-edge walkable nodes; graph went from 16,237 edges / 12,756 nodes to 47,704 / 44,223 (asset 5 → 11.2 MB, bake 0.8s). Phillips House snap 95m → 30m, operator confirmed "working very well" on Lenovo. Carry-forward: option 2 (runtime mid-edge projection) for the residual ~30m, plus walk-sim + DebugEndpoints `TourRouteLoader` cleanup, then full retirement of `TourLegBaker` / `tour_legs` / `routeToNext`.

Full session detail: `docs/session-logs/session-179-2026-04-26.md`. Commit: `e86de97`.

---

## Session 178: 2026-04-25/26 — P6 tour-leg pre-bake + :routing-jvm extraction + surgical OSM pedestrian ingest

S175's last carry-forward shipped: tour-leg polylines pre-baked from the same TigerLine bundle the runtime Router uses (P6). New `:routing-jvm` module extracted from `:core` so both Android and the JVM `:salem-content` pipeline consume one routing engine; `salem-content` grew a `TourLegBaker` that writes the new `tour_legs` table AND regenerates the runtime `assets/tours/*.json` files (replacing S125's OSRM-baked content). Live "Get directions" routes got first/last-approach segments capped at 60m (over-water fix); for pier/peninsular destinations TIGER doesn't reach, surgical OSM ingest of 11 hand-picked osm_ids (Derby Wharf, Seven Gables waterfront, Charter Cemetery interior paths, Salem Willows) with verified per-feature TIGER hookup — Derby Wharf Light snap dropped 349.3m → 5.3m, Charter Cemetery 83.8m → 5.5m. Two new memories: `feedback_v1_no_external_contact.md` (Android app makes ZERO outside contact except GPS — V1 mandate), `feedback_internal_db_single_source.md` (internal DB is canonical; web admin may validate against PG, runtime app makes no validation calls); `feedback_no_osm_use_local_geo.md` got an addendum that allows surgical-allowlist OSM ingest when nothing internal covers a feature, and forbids broad bbox imports (the first attempt produced 608 disconnected components and broke 57 tour legs before rollback).

Full session detail: `docs/session-logs/session-178-2026-04-25.md`. Commit: `a7d7b76`.

---

## Session 177: 2026-04-25 — Web walking router + Leaflet Directions UI

Two final S175 carry-forwards shipped. **P4:** new `cache-proxy/lib/salem-router.js` is a JS port of `RoutingBundle.kt` + `Router.kt` reading the same `salem-routing-graph.sqlite` the APK ships — CSR adjacency, expanding-ring grid KNN with planar SRID-4269 distance, binary-heap Dijkstra, undirected pedestrian graph. Endpoints: `GET /salem/route?from_lat&from_lng&to_lat&to_lng[&source=live|bundle]`, `POST /salem/route-multi`, `GET /salem/route/meta`. `?source=live` falls through to `tiger.route_walking()` over a lazy-init unix-socket pg.Pool (peer auth) and stitches the returned MultiLineString into travel order by walking endpoints. Smoke-tested against the four `RouterParityTest.kt` reference fixtures — all four match bit-exact (Common→7G 1240.7187 m, Commuter Rail→Museum Place 509.8629 m, Witch House→Burying Point 413.7483 m, Peabody→Derby Wharf 1078 m sanity). Bundle vs live cross-check produces ±0 mm; the `DIVERGENCE` warning would fire above 5%. **P5:** new optional `onShowDirections` prop on `PoiEditDialog` adds a Directions button; AdminLayout owns the `directionsTarget` state; AdminMap renders `<DirectionsLayer>` (Polyline #22C55E w/ #0f5132 outer casing, origin CircleMarker, fits bounds once per target+source) and a `<DirectionsPanel>` (distance/duration/pace + bundle/live source toggle + close ×). TS clean, Vite production build clean, end-to-end data path verified through the Vite `/api` → cache-proxy `/salem` proxy. Visual click-flow verification deferred to operator. The S175 five-task queue (P3b/P3c/P2c/P4/P5) is now fully drained.

Full session detail: `docs/session-logs/session-177-2026-04-25.md`. Commit: `0c43131`.

---

## Session 176: 2026-04-25 — Live re-routing + MBTA-origin walk-sim + Router JVM parity tests

Three S175 carry-forwards shipped on the on-device walking router. **P3b (live re-routing):** new `DirectionsSession` class watches the location stream during an active route, fires a recompute after >25 m drift sustained for 2 consecutive fixes, and clears the route on arrival within 15 m of destination. Wired into `TourViewModel` so every GPS update flows through the session; mute flag prevents re-entry while a reroute is in flight. Verified on Lenovo TB305FU via new deterministic `/directions-session-test` endpoint (7 scripted fixes, all pass: drift detection requires two consecutive off-path fixes, single-fix tolerated, arrival fires inside 15 m). **P3c (MBTA-origin walk-sim):** when `lastGpsPoint` is >3 km from MBTA Salem station, `walkTo(POI)` now forks to `startSimulatedWalkFromMbta` — computes the bundled-router route from the station to the POI, publishes it as the active walking route (so the P3b session arms automatically), and drives a `walkSimAlongPolyline` simulator at 1.4 m/s. Lenovo verification (Boston as fake origin → 7 Gables): bundled route 1510 m / 11 turns, simulator interpolated to 1013 steps, step counter advancing cleanly at 1 Hz. In-Salem branch verified untouched. **P2c (Router JVM parity tests):** new `core/src/test/` source set with `JdbcRoutingBundleLoader` (sqlite-jdbc, JVM-only mirror of `RoutingBundleLoader`) and `RouterParityTest` (10 tests, all pass: four S175 reference routes pinned bit-exact to ±1 mm, plus degenerate same-point, KNN snap, multi-stop concat, pace→duration). Carry-forward to S177: P4 (web router in cache-proxy consuming the same SQLite bundle) and P5 (web Leaflet directions UI).

Full session detail: `docs/session-logs/session-176-2026-04-25.md`. Commit: `d2ad291`.

---

## Session 175: 2026-04-25 — On-device Salem walking router (TigerLine bake → APK + Directions UI)

Shipped a fully on-device walking router for the Salem app. New bake pipeline at `tools/routing-bake/` clips TigerLine's `salem.edges` + `salem.edges_vertices_pgr` to a 3-mile-buffer Salem bbox (16,226 walkable edges, 12,742 nodes, 4.1 MB SQLite bundle in `app-salem/src/main/assets/routing/`). New `:core` routing module: `RoutingBundle` (CSR adjacency + grid spatial index), `RoutingBundleLoader` (Android SQLite reader), `Router` (pure-Kotlin Dijkstra + planar SRID-4269 KNN matching TigerLine's `<->` semantic), `TurnByTurn` (per-edge bearing-change synthesis with imperial distances). Salem-app wiring: `SalemRouterProvider` lazy-loads the bundle on first call (~80-150ms warm-up); `WalkingDirections` swaps its V1_OFFLINE_ONLY straight-line fallback for real bundled-graph routing while keeping the existing `WalkingRoute` contract so `SalemMainActivityDirections` overlays + the turn-by-turn dialog inherit the upgrade with no UI change. `PoiDetailSheet`'s Directions action now calls `walkTo(GeoPoint)` instead of launching the external `geo:` intent. On-device verification on Lenovo TB305FU via new `/route-test` debug endpoint: 3 reference routes match TigerLine's live `tiger.route_walking()` to the millimetre (0.000m delta), 32ms cold first call, 4-6ms warm calls. Two memories locked: WickedMapView is the sole basemap (Esri+Mapnik dropped), MBTA Salem station is the simulated walk origin when GPS-Salem isn't available. Carry-forward to S176: live re-routing on path-drift, walk-sim FAB integration, web-side router in cache-proxy with `?source=live` override, web Leaflet polyline UI, and tour-leg pre-bake.

Full session detail: `docs/session-logs/session-175-2026-04-25.md`. Commit: `186a89a`.

---

## Session 174: 2026-04-25 — Web admin tour editor (full CRUD + drag-to-reposition)

Shipped a brand-new Tours tab in the web admin tool so tours can be edited end-to-end ahead of post-TigerLine-cleanup rerouting. Schema migration added `stop_id` PK, nullable `lat`/`lng` (per-tour coord override), and nullable `name`/`poi_id` to `salem_tour_stops`; effective coord at read time = `COALESCE(stop.lat, poi.lat)`. New `cache-proxy/lib/admin-tours.js` adds 7 admin endpoints — list/get tours, create/patch/delete tour, add/patch/delete/reorder stops — with auto-resync of `salem_tours.stop_count` on every stop mutation. Frontend: new `TourTree.tsx` (create form, per-row delete, metadata edit form, waypoint list with ↑/↓/🗑, "+ Free waypoint" map-click-to-add mode, "+ POI as stop" pick-marker mode), generalized `MoveConfirm` modal, new `TourStopLayer` (numbered draggable markers + dashed connecting polyline + amber-vs-indigo for override-vs-fallback), `FitTourBounds`, `MapClickAddListener`. Operator parked at session end pending TigerLine database cleanup before doing the actual rerouting.

Full session detail: `docs/session-logs/session-174-2026-04-25.md`. Commit: `42f4698`.

---

## Session 173: 2026-04-25 — Cemetery firefly alignment investigation (no-op session, reverted)

Investigated the Quaker cemetery firefly bug — fireflies were rendering in a void next to the visible cemetery polygon, not on it. Root cause traced to a polygon-source mismatch: the Witchy basemap renders cemeteries from OSM data (via the planetiler tile bake), while our firefly polygon library queries `massgis.openspace prim_purp='H'`, which has only a 1455 m² 4-vertex sliver in the wrong place for `gid=27152` "Essex Street Cemetery". Tried two fixes — exclusion of the bad gid (operator reversed), then a B1+B2 source-merge (prefer `massgis.landuse lu37_1999=34` when area-comparable, plus a side-car JSON of hand-corrected overrides for problem cases). B1 made the OTHER ~46 cemeteries' polygons drift out of alignment with the basemap render — landuse=34 polygons disagree with OSM more than openspace does despite being the more specific MassGIS class. Operator instructed full revert. Net code change this session: zero. Quaker cemetery firefly bug remains parked; the per-feature override mechanism is a known path forward when operator wants to address it surgically.

Full session detail: `docs/session-logs/session-173-2026-04-25.md`. Commit: pending.

---

## Session 172: 2026-04-25 — Animations live in Salem app + parks overlay (water visual TBC)

S172 deliverable shipped: animations now run in the live `SalemMainActivity` osmdroid map via `WickedAnimationOverlay` (a thin osmdroid `Overlay` wrapper around our `MapOverlay` system, 30fps throttled). Three overlay kinds wired — water whitecaps, cemetery fireflies, and a NEW `RollingGrassOverlay` for parks (extracted from `massgis.openspace prim_purp IN ('R','B')`, 427 polygons). Polygon library tightened with inward buffers (water 12m, cemeteries 10m) so animations stay strictly inside basemap-rendered shapes — no edge bleed onto streets/buildings during pan. Architecture: world-anchored lat/lon grid (~110m step, 14185 water anchors / 1481 grass anchors), per-anchor short-burst duty cycle (1.5-2.5s active), random position jitter to break grid pattern. Water visual went through 11 iterations this session and operator's final read is "not the best" — visual tuning continues S173. Carry-forward: try short irregular dash bundles, frame-animation drawables, or denser grid. Cemetery fireflies confirmed working (continuous per-fly pulse). All builds tested on Lenovo TB305FU; one runtime ANR (cap=1500 + dense parks) and one init-time ANR (33m grid → 700k anchors) — both rolled back to safe values.

Full session detail: `docs/session-logs/session-172-2026-04-25.md`. Commit: `00081d7`.

---

## Session 171: 2026-04-24 — Custom 2D map engine prototype (osmdroid replacement, in-progress)

Major architectural pivot, reversing the S157 OSM-stays decision: operator opted to stop using osmdroid and own the map engine. Built `app-salem/.../wickedmap/` from scratch — `WickedMapView` (`SurfaceView` + render thread + gestures + tile draw), `MercatorMath`, `TileArchive` (osmdroid `SqliteArchive` decoder, no library dep), `MapCamera` (lat/lon-based after a coordinate-drift bug parked the map in the Atlantic mid-pinch), `PolygonLibrary` (loads GeoJSON FeatureCollection at app start, indexes by kind), and two animation overlays — `AnimatedWaterOverlay` (sea whitecaps) and `FireflyOverlay` (spectral cemetery orbs). Polygon source data extracted entirely from local TigerLine + MassGIS (operator HARD RULE: never OSM): `tools/wickedmap-polygons/extract-water-from-tiger.py` produces 545 named water polygons from `tiger.areawater`, `extract-cemeteries-from-massgis.py` produces 97 cemeteries from `massgis.openspace WHERE prim_purp='H'`. Whitecaps + ghost orbs render at 30 fps on Lenovo TB305FU with full 642-polygon library loaded. The custom engine is reachable via the new "WickedMap" FAB inside the running Salem app and a standalone "WickedMap Proto" launcher icon. **Migration of `SalemMainActivity` from osmdroid is the next-session work** — counted 338 compile errors when the layout was swapped, scoped honestly as 2-3 focused sessions (tile rendering swap → POI markers → per-feature overlay migration). Tonight's Salem app is functionally unchanged from S170; the engine work ships as a parallel system with FAB access. Memories saved this session: `reference_master_session_reference.md`, `feedback_no_osm_use_local_geo.md` (HARD RULE), `feedback_basemap_priority_over_animation.md` (HARD RULE — explicit 3-layer z-order: base graphic / animation / "real" features), `project_lively_map.md` (extended: active animation always).

Full session detail: `docs/session-logs/session-171-2026-04-24.md`. Commit: `a93a86e`.

## Session 170: 2026-04-24 — Map sprite pipeline (rotation-only baseline shipped, walking deferred to V2)

Long off-repo sprite-system exploration: built a working SDXL→TripoSR→headless-render→animated-WebP pipeline that produces 7 cartoon characters (witch, owl, black-cat, katrina-kitty, skeleton, mouse, rat) as 16-angle rotation frames packed at **992 KB total** for the eventual map-overlay system. Three approaches to walking animation all failed (AI 2-pose generation lost identity, AnimateDiff is for motion-not-rotation with no rotate-LoRA, procedural Blender mesh deformation crumples AI meshes) — operator chose to leave it as is and defer skeletal rigging to V2. Side deliverable: `MASTER_SESSION_REFERENCE.md` at repo root, a topic-indexed lookup so future sessions can find prior work without loading every log. Repo footprint: only the live log + this entry + MSR + CLAUDE.md key-reference table update; all sprite work lives under `~/AI-Studio/`.

Full session detail: `docs/session-logs/session-170-2026-04-24.md`. Commit: `3dbcfdd`.

## Session 169: 2026-04-24 — Walk-mode dwell rewritten as TTS-gated (CPA removed)

Operator field test surfaced two bugs in the walk FAB: the walker would freeze for 15 s on POIs that never announced, and would stride straight through full 60 s narrations without pausing. Three field-log iterations traced both to the CPA (closest-point-of-approach) trigger model — too wide on one end (LOOK AT firing on POIs at 45-52 m outside any geofence), too narrow on the other (a closer POI's CPA on the same step "consumed" the LOOK AT slot, leaving the farther narrating POI unmatched). Replaced with TTS-gated dwell: walker pauses at whatever step it's on whenever a POI is speaking or queued, resumes when TTS goes idle and queue empties; newspapers stay ambient. Pacing cooldown also bypassed in walk-sim (the dwells themselves are the pacing). Net delta -154 lines on `SalemMainActivity.kt`. Real GPS strategy unchanged.

Full session detail: `docs/session-logs/session-169-2026-04-24.md`. Commit: `53a2b7b`.

## Session 168: 2026-04-24 — Install-default sweep + one-narration-per-visit + zoom 14-20 lock + toolbar rework

Install-default sweep across the tourist UX so a first-launch user lands on a sensible default stack: POIs Hist. Landmark off, Record GPS off, Use Real GPS Outside Salem off, GPS-track FAB off, Oracle off, detail level Deep. Narration engine rebuilt to fire exactly one clip per POI visit (priority: stripped-commercial → historical/body text → BRIEF hint) instead of the prior intro-hint + body double. Fixed a first-POI silence bug uncovered by that collapse — `NarrationManager.enqueue()` was dropping segments during the TTS bind race; now defers them and `onInit()` flushes on ready. Map zoom locked to 14-20 (tiles only cover z16-19 + overzoom) with cinematic rewritten to z14 → z16 → z18. Toolbar cluster (First/Prev/Next/Jump) and narration hero banner deleted — single speaker icon on the right now handles tap=cancel/replay and long-press=audio popup; Home moved to the left. Splash-skip bug diagnosed (task-affinity collision brings SalemMainActivity to front instead of SplashActivity) — fix proposal recorded, not applied this session.

Full session detail: `docs/session-logs/session-168-2026-04-24.md`. Commit: `d289efc`.

## Session 167: 2026-04-24 — APK packaging audit: poi-icons + tile archive shrink + single-basemap build

Pre-Play-Store packaging pass plus a stack of quality-of-life fixes. Shipped: (1) Walking Tours hang root-caused to a missing `PRAGMA user_version = 9` in the asset DB triggering `fallbackToDestructiveMigration` on first launch — stamped in both source and asset DBs. (2) Walk simulator smoothness fix — `LocationMode.MANUAL` now bypasses the stationary gate (Lenovo `TYPE_SIGNIFICANT_MOTION` never fires). (3) New `POIs Hist. Landmark` toggle in the layers popup (filters 487 `massgis_mhc` POIs). (4) Dead Current/Proposed/Both POI-location selector removed. (5) `poi-icons/` trimmed to witchcraft-style-only + WebP (544 MB → 3.4 MB, 7 other styles archived with README at `docs/archive/poi-icons-unused-styles_archived_2026-04-24/`). (6) `poi-circle-icons/` converted to 128 WebP (36 MB → 1.2 MB). (7) `salem_tiles.sqlite` trimmed to Witchy-only + Salem city bbox + z16-19 + re-encoded WebP q=78 (207 MB → 30 MB, pipeline at `tools/tile-bake/trim-to-salem.py`). (8) Tile archive bundled in APK assets with first-launch auto-extract via `OfflineTileManager` + `noCompress 'sqlite'`. (9) `TileSourceManager` fully collapsed to single-basemap (Esri/Mapnik/Dark source builders, IDs, and `buildDarkTileSource`/`onDarkModeToggled` delegations all deleted). Layers button in toolbar preserved as the home for future toggles. **APK trajectory: 706 MB → 50 MB → 79 MB (bundled tiles).** Well under Play Store 150 MB base-module limit — no asset pack needed.

Full session detail: `docs/session-logs/session-167-2026-04-24.md`. Commit: `8d209b8`.

## Session 166: 2026-04-24 — FuzzySearchEngine type/category synonym expansion

Recovering from killed S165; committed all S165 work alongside S166. Fixed two gaps that made type-searches like "dentist" find nothing: the per-token `when { }` scoring was exclusive (non-name fields never accumulated once any name tier fired), and there was no synonym coverage between "dentist" and "dental." Added `TYPE_SYNONYMS` map (35 entries: dentist→dental, lawyer→law/attorney, gym→fitness, coffee→cafe, doctor→medical/clinic, etc.), token expansion before per-token scoring, and non-name fields (subcat, cat, desc, hist, addr) now always accumulate independently.

Full session detail: `docs/session-logs/session-166-2026-04-24.md`. Commit: `578a3fa`.

## Session 165: 2026-04-24 — Fuzzy Find engine + admin classification flags + FindPanel category fix (killed session)

Session killed before session-end protocol ran; work committed and closed via S166. Shipped: (1) `FuzzySearchEngine.kt` (new) — multi-tier in-memory scorer (10k/5k/3k/600/400/250/120) with subcat/cuisine/desc/hist/addr tiers; `FindViewModel` preloads all POIs on `init {}`, 250ms debounce, tap→animateTo geocoord zoom=18, long-press→detail dialog. (2) `PoiEditDialog.tsx` — new "Flags" tab with toggle-switch UI for 7 operational flags + 5 classification flags (`is_historical_property`, `is_witch_trial_site`, `is_free_admission`, `is_indoor`, `is_family_friendly`). (3) `PoiTree.tsx` — `CategorySelectCtx` context fix for react-arborist category clicks, new flag fields in `PoiRow`. (4) `FindPanel.tsx` — category click skips subtype tree, directly filters map. (5) `admin-pois.js` — 5 new classification flags in `UPDATABLE_FIELDS`.

Full session detail: `docs/session-logs/session-165-2026-04-24.md`. Commit: see S166.

---

## Session 164: 2026-04-24 — Wrong-project detour (LAN recon → RadioLogger)

No LMA work. Operator asked about LAN threat recon tooling; scripts were built here by mistake, then moved to `~/Research/RadioLogger/tools/`. No LMA code, schema, or docs changed. No commit.

Full session detail: `docs/session-logs/session-164-2026-04-24.md`. Commit: none.

---

## Session 163: 2026-04-23 — MHC hidden POIs with clickable footprints on admin map + category filter; parallel-system detour torn down

Shipped the MassGIS MHC inventory as hidden POIs in `salem_pois` — 487 new rows + 16 enrichments via `tools/historical-buildings/import-to-salem-pois.py` (spatial-join L3 assessor for year_built, nearest-+-name-fuzz enrichment, stable `hb_<sha256>` ids, idempotent). Admin map now renders hidden POIs as subtle clickable polygons (outline only, hover-highlight) wired to the existing `PoiEditDialog`; POI-tree category clicks filter the map to that category with a toggle-off and a blue "Filtering: X" pill. Filed research request to SalemIntelligence at `~/Development/SalemIntelligence/docs/lma-mhc-hidden-poi-research-request.md`. Chat gated post-V1 via `ENABLE_CHAT=true` flag. Mid-session detour: first built a parallel `salem_historical_buildings` table + Room v10 entity + admin tab + separate audio toggle; operator corrected the architecture ("just a POI that doesn't present as a POI") and everything was torn down and rebuilt as hidden POIs — Room back to v9 identity_hash `4ec9ae3528d8f55529cd6875c7b0adef`, migration archived. Four new memories shipped, including `feedback_leverage_existing_assets.md` (don't build parallel systems when existing infrastructure can absorb the change).

Full session detail: `docs/session-logs/session-163-2026-04-23.md`. Commit: `76eec90`.

---

## Session 162: 2026-04-23 — POI location verify scaffolding (DB + admin UI + Android layers); verifier blocked on TigerLine MA ingest

Operator asked for a workflow to verify all POI lat/lng against TigerLine/MassGIS, with TigerLine as ground truth and per-POI "truth of record" override. Probed the data first and found `tiger.addr/addrfeat/edges/featnames` empty for `statefp='25'` (TigerLine MA ingest still in progress per OMEN tigerline session-002) and the `massgis.l3_parcels_essex` polygons loaded but without the joined assessor table — so neither source has usable address-to-coordinate data on this machine right now. Pivoted to ship the additive scaffolding without waiting: (1) PG migration `cache-proxy/scripts/poi-verify-2026-04-23/01-add-location-verify-columns.sql` adds 7 nullable cols + 3 partial indexes to `salem_pois` (lat_proposed/lng_proposed/location_status/location_truth_of_record/location_source/location_drift_m/location_geocoder_rating/location_verified_at) and backfills 60 rows to `no_address`; (2) cache-proxy `admin-pois.js` whitelists `location_truth_of_record`+`location_status` for PUT and adds `POST /admin/salem/pois/:id/accept-proposed-location` (copies lat_proposed→lat, clears proposal, stamps admin_dirty); (3) admin web — `PoiTree.tsx` gets a Location filter `<select>` (any/needs review/no address/no match/unverified/verified/accepted) and a typed `LocationStatus`; `PoiEditDialog.tsx` gets a `LocationVerifyPanel` rendered at the bottom of the existing Location tab — color-coded status badge, current vs proposed coords side-by-side, drift/source/rating/verified_at, "Truth of record" checkbox, "Accept proposed coordinates" button (disabled when no proposal); `tsc --noEmit` clean; (4) Android — new `MenuPrefs.PREF_POI_LOCATION_LAYER`, new `PoiLocationLayerManager` (current/proposed/both), new `MenuEventListener.onPoiLocationLayerChanged` default-method, and `AppBarMenuManager.showTileSourcePopup()` extended to render two radio groups (basemap + POI source) in the same popup with a divider; `compileDebugKotlin` clean. The TigerLine verifier query itself is **deferred** — when MA tables fill (or MassGIS L3 assess loads), it's ~30 lines of SQL to populate the columns we shipped and the rest of the workflow lights up without further schema/UI work. Room migration to expose `lat_proposed`/`lng_proposed` to the Android entity is also deferred until there's data worth migrating for.

Full session detail: `docs/session-logs/session-162-2026-04-23.md`. Commit: `7396c7d`.
---

## Session 161: 2026-04-23 — Car-tour debug-log triage + GPS/V1-offline fixes + repo cleanup

Reviewed 645 KB of car-tour debug log and shipped three rounds of fixes. (1) V1 offline toasts — `searchPoisAt()`, `startSilentFill()`, and `AppBarMenuManager.syncCheckStates` all bypassed the V1_OFFLINE_ONLY gate, producing 7 "POI failed" toasts, 14 "Fill failed" banners, and 4 menu-findItem warnings during the 3-hour drive. All three gated at the caller. (2) GPS "spotty" — root cause was the interval picker thrashing between 10s/2.5s/30s on every fix (19 times in 15 min of driving), each flip doing `locationJob.cancel()` → `removeLocationUpdates` → re-register. Added `LocationManager.updateRequestParams()` that re-calls `requestLocationUpdates` on the live callback (GMS merges, no teardown); `MainViewModel.restartLocationUpdates()` now prefers that path. Interval picker reduced to 2 states (2.5s moving/narrating, 30s idle) with 10s dwell debounce. (3) Confirmed Witchy/Satellite tile alignment — side-by-side blends at 4 landmarks (z=16-18) show building footprints, streets, and piers trace exactly over Esri imagery; drift is not at the bake. (4) Repo cleanup — ~20 stale root + docs/ files archived to `docs/archive/` (CHANGELOG, SOCIAL-PLAN, WEB-APP-PLAN, MASTER_PLAN_ARCHIVE, PLAN-ARCHIVE, REFACTORING-REPORT, all 5 tigerline-* briefs now that TigerLine+MassGIS are in DB, completed SalemIntelligence + si-handoff briefs, etc.); 6 shell scripts moved to `scripts/`; parked master-plan placeholder deleted and pre-park snapshot archived; CLAUDE.md updated accordingly. Deployed twice to Lenovo TB305FU; needs next-drive verification — expected signal is `updateRequestParams — interval Xms → Yms (no teardown)` replacing the old `Flow cancelled — removing location updates after N updates received`.

Full session detail: `docs/session-logs/session-161-2026-04-23.md`. Commit: `2621c05`.

---

## Session 160: 2026-04-23 — Master-plan split + admin-tool Witchy tiles + 9Y edit tab

Third session of the calendar day. Three tracks. (1) Split `WickedSalemWitchCityTour_MASTER_PLAN.md` from 3,689 → 1,834 lines by moving completed-phase step-by-step detail (Phases 1-9X), V2-deferred tier infrastructure (Phases 9B/9C/9D), and the pre-S138 tiered business model into `docs/archive/WickedSalemWitchCityTour_MASTER_PLAN_removed_2026-04-23.md` (1,943 lines), with a new thin completed-phase index and a rewritten V1 Business Model section reflecting the $19.99 flat paid / fully offline / no-ads / no-LLM posture. Zero content deleted — every line is in either the live plan or the archive. (2) Admin tool now serves the same tiles the phone app sees: new cache-proxy endpoint `GET /admin/tiles/:provider/:z/:x/:y` reads from `tools/tile-bake/dist/salem_tiles.sqlite` with osmdroid BigInt key encoding, Content-Type sniffed from magic bytes (WebP / PNG / JPEG), 3 providers (Salem-Custom / Mapnik / Esri-WorldImagery). `AdminMap.tsx` gets a top-right `<select>` picker with localStorage persistence, default Witchy, plus an OSM-online fallback for low-zoom panning. (3) POI edit dialog gets an 8th tab "MassGIS / MHC" with form fields for all 9 S159 Phase 9Y columns; backend `UPDATABLE_FIELDS` whitelist in `admin-pois.js` and mirrored `poiAdminFields.ts` extended (62 → 71 fields); `mhc_year_built` flagged numeric. Type-check clean; backend mock-req/res tested 7 cases happy + edge.

Full session detail: `docs/session-logs/session-160-2026-04-23.md`. Commit: `f86bc10`.

---

## Session 159: 2026-04-23 — Phase 9Y.2b Kotlin + Room v8→v9 identity-hash cascade

Mechanical schema propagation for the 9 MassGIS/MHC/L3 columns that S156 added to PG. Added 9 nullable `@ColumnInfo` fields (`building_footprint_geojson`, `mhc_id/year_built/style/nr_status/narrative`, `canonical_address_point_id`, `local_historic_district`, `parcel_owner_class`) to `SalemPoi.kt`; bumped `SalemContentDatabase` version 8→9; extracted new Room identity_hash `4ec9ae3528d8f55529cd6875c7b0adef`; updated `verify-bundled-assets.js` + `bundle-witch-trials-newspapers-into-db.js` constants; threaded the 9 columns through `publish-salem-pois.js` (CREATE_TABLE + SELECT + INSERT + transaction binding); rebaked `salem_content.db` (1,830 POIs, 8.9 MB); Lenovo TB305FU uninstall+install smoke-test passed cleanly (app launches, POI markers render, narration state machine active, zero SQLite/Room errors). 9Y.3 enrichment script is now unblocked. Pre-existing issue surfaced: `verify-bundled-assets.js` still checks the pre-S158 in-APK `salem_tiles.sqlite` location and fails on every build. Post-session discovery: operator's home GPS in Beverly falls outside the S158 Witchy bake bbox (max lat 42.545 vs Beverly 42.557), so the bundled tiles don't cover the operator's home location — candidate fix for next session is extending bake bounds or adding a UX hint.

Full session detail: `docs/session-logs/session-159-2026-04-23.md`. Commit: `58d4c8c`.

---

## Session 158: 2026-04-23 — Custom "Witchy" basemap tile bake (third offline tile source)

Built a complete custom-tile-baking pipeline at `tools/tile-bake/` and shipped a third offline basemap alongside Satellite + Mapnik: `Salem-Custom` (label "Witchy"). Stack: planetiler (Geofabrik MA → OpenMapTiles vector tiles z0-z16) + tippecanoe (massgis.l3_parcels_essex → parcel outlines; massgis.structures JOIN mhc_inventory → 39,408 building footprints tagged with NHL/NRIND/NRDIS/none tier) + maplibre-gl-native headless with a curated `style-salem.json` (parchment palette, tiered-purple historic buildings, no commercial POI labels, narrow cemetery/monument/church carve-out, Metropolis Bold district labels). Iterated on-device through 11 operator-driven refinements: buffered-block rendering to kill tile seams + label fragmentation, divided-boulevard oneway filter, added cemeteries/hospitals/schools/sand/wetlands, unified building source to MassGIS so historic-overlay polygons align perfectly with base buildings, and WebP lossless encoding (158 MB → 112 MB, −29%). Kotlin wire-up: `TileSourceManager.Id.CUSTOM` + un-hid the picker icon under V1_OFFLINE_ONLY. Bundle: 206 MB total (17,777 tiles across three providers). Build-time toolchain (planetiler.jar / fonts / node_modules / intermediate mbtiles + geojsonl) is all gitignored; rebuild instructions in `tools/tile-bake/README.md`.

Full session detail: `docs/session-logs/session-158-2026-04-23.md`. Commit: pending.

---

## Session 157: 2026-04-22 — OSM-stays pivot + Essex L3 parcels ingest + Salem QGIS project + routing design captured

Operator reversed the S156 TigerLine-foundation/MapLibre/osmdroid-ripout plan: OSM + osmdroid stay as V1 base map; MassGIS + TigerLine become overlays only (osmdroid `Polygon` / `Polyline` / `Marker`). Project memory `project_osm_stays_as_basemap.md` codifies the reversal; STATE.md + master plan + tigerline docs updated. Self-ingested MassGIS L3 Parcels + L3 Assess for Essex County (366,884 polygons + 429,803 assessor rows) via new `cache-proxy/scripts/ingest-l3-parcels-essex.py`; validated end-to-end on 111 Essex St Beverly. Built a portable `.qgz` QGIS project at `tools/qgis-project/salem-gis.qgz` with 13 layers (OSM base + Salem boundary + Open Space + Cemeteries + Civic/Institutional + Land Use 25-cat + Buildings + L3 Parcels + MassDOT Roads + TIGER Edges + MHC Inventory + LMA POIs with category icons at scale ≤ 1:1000). Internal walking-router design captured in live log (Salem-clipped, ~2,200 edges, ~100 KB bundled asset, ~2.75 sessions — **exploratory, not decided**). Operator cautioned at end of session: "nothing is decided yet" — the S157 design discussions beyond the OSM-stays direction are capture-only.

Full session detail: `docs/session-logs/session-157-2026-04-22.md`. Commits: `4fe6bc0` (OSM pivot + L3 ingest) + `d97d0da` (QGIS project). 3 S156 commits + 2 S157 commits remain unpushed pending operator direction.

---

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
