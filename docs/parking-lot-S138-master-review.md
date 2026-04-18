# Parking Lot — S138 Master Review (2026-04-16, re-clustered S143 2026-04-17)

> **Purpose:** Capture 37 critical items from operator's S138 "prep list" verbatim, triage each against current code state, and hold them until the user picks what to pull into the master plan.
> **Nothing here is scheduled yet.** This is a triage document — the master plan only gets updated after operator review.
> **Origin conversation:** Session 138, 2026-04-16, operator direction: "parking items… need to figure out what is easy and what doesn't work."
> **S143 update (2026-04-17):** Operator meeting a lawyer **Monday 2026-04-20** for C-corp formation and legal kickoff. S144 will walk the full parking lot (45 items total: 37 original + 8 S143 additions). Priorities split into **Monday cutoff** (what matters for the lawyer meeting), **post-Monday pre-launch** (before 2026-09-01 Salem 400+ launch), and **V2 / deferred**.

---

## S143 walkthrough roadmap — cluster view (use this for S144)

> **How to read this:** Items are regrouped into thematic clusters by "what makes sense to work on together." Each item keeps its original number (1-37 from S138 + 38-45 from S143) so detailed writeups below remain findable. Clusters are ordered by operator-value priority: legal/brand items that help the C-corp pitch first, then content/UX polish for launch, then back-office work.

### ⭐ Monday Must-Haves (operator-declared, S143)

> **Operator direction (2026-04-17):** "I have a lot of tuning I will need to do to the UI to remove stale concepts (Splash screen for instance) and need to reinforce the opening menu and the menu keys and the find menu along with the audio control — those are must have to me before Monday."
>
> These are the items the operator commits to shipping before the **2026-04-20 lawyer meeting**. Some are existing parking-lot items promoted here; some are new items (#46–#49) added S143 to cover surfaces the original S138 list didn't explicitly flag. Clusters A and B below still hold the detailed groupings — this block is the "what must land" list.

| # | Item | Origin | Scope |
|---|---|---|---|
| #10 | Legal walkthrough write-up | S138 | M — consolidate COPPA/IARC/privacy for counsel |
| #11 | Play Store $19.99 + age-gate reasoning (counsel-ready) | S138 | M — write up the S138 decision |
| #13 | Top menu strip redesign (Salem-witchy buttons) | S138 | M — brand-forward grid dropdown |
| #27 | Rebuild top hero view | S138 | M — brand-forward home/hub |
| #40 | Hub-card retitle ("Wicked Salem" residue on Explore Salem card) | S143 | E |
| #43 | Katrina splash graphic | S143 | ½ session (content-art) |
| #44 | Screaming-cat splash voiceover | S143 | ½ session (content-art, pairs w/ #43) |
| **#45** | **Universal audio control at top menu** | S143 | **M — promoted from Cluster D** |
| **#46** | **Splash screen stale-concept cleanup** | **S143 new** | E–M — text, flow, leftover wording |
| **#47** | **Opening menu reinforcement** | **S143 new** | M — hub card layout, entries, brand |
| **#48** | **Menu keys / grid dropdown reinforcement** | **S143 new** | M — tile labels, icons, order, brand |
| **#49** | **Find menu reinforcement** | **S143 new** | M — category tiles, search UX, results |
| **#50** | **TTS tuning — pause only at sentence ends** | **S143 new** | E–M — chunker + pause logic |

**Total must-have scope estimate:** 5 Easy, 7 Medium, 2 content-art (½ each). Roughly **3–4 working sessions** between now (Friday 2026-04-17) and Monday 2026-04-20 — **busy weekend**, workable if dedicated. Cluster A items #10/#11 are writing, not code; run in parallel with UI work. #35 (IP hardening) deliberately NOT in must-haves — operator may want that decision to follow counsel's input.

**Risk flags for operator to confirm S144 walkthrough:**
- Scope fit for 3-day window — any items to drop/defer?
- Content-art (#43/#44) needs GPU — confirm SalemIntelligence / AudioCraft free time.
- #46–#49 are brand-new items with no prior triage — S144 walkthrough should drill into each to confirm what "reinforcement" means in code terms.

---

### 🏛️ Cluster A — Legal & IP (**Monday-facing**)
Directly affects the lawyer meeting or material for counsel to review.
- **#10** Legal walkthrough write-up — consolidated COPPA/IARC Teen/privacy/ads posture
- **#11** Play Store $19.99 paid + age-gate reasoning — decision resolved S138; counsel-ready write-up needed
- **#35** Encrypt all objects / harden IP — defensive IP posture for C-corp valuation
- **Reference docs (read, don't re-do):** `IP.md` (14 patentable innovations), `GOVERNANCE.md`, `NOTE-L017` Play Store checklist, `NOTE-L014` Privacy Policy (pending OMEN)

### 🎨 Cluster B — Brand & Identity (**Monday-facing polish**)
So the app visually matches the new "Katrina's Mystic Visitors Guide / DestructiveAIGurus" identity when the operator demos it to counsel.
- **#40** Hub-card retitle — last "Wicked Salem" residue on the "Explore Salem" hub card (E)
- **#43** Katrina splash graphic — replace `splash_witchkitty.png` (½ session content-art)
- **#44** Screaming-cat voiceover — replace `splash_voiceover.wav` (½ session content-art, pairs with #43)
- **#13** Top menu strip redesign (Salem-witchy buttons) (M)
- **#27** Rebuild top hero view (M)

### 📖 Cluster C — Content & Narration polish (**post-Monday, pre-launch**)
V1 narrative experience — what the paying visitor hears and reads.
- **#2** Narration repetition while moving
- **#12** Easter eggs (witch-fly-by, last words)
- **#19** Graveyard souls / ambient memorial eggs
- **#25** Transit narration wrap-around
- **#29** Dedicated "Salem Witch History" entry activity
- **#30** History narrative quit on silence
- **#32** Repeat history narrative after POI interruption
- **#36** Pencil-sketch image per newspaper day
- **#37** Narration FAB (Off / Historical / Historical+Civil / All)

### 🎯 Cluster D — UX polish (**post-Monday, pre-launch**)
Feel-good improvements that raise quality bar but aren't ship-blockers.
- **#4** Bearing-up / GPS+sensor movement priority
- **#14** POI experience overhaul (tour vs wander)
- **#20** Fuzzy find rework (v2)
- **#21** Investigate running bugs + log triage
- **#26** No POI photos ever (confirm + enforce)
- **#28** POI graphics tiered by merchant payment (V1 skip — tiering deferred to V2)
- **#34** GPS FAB scope audit (app-only vs system-wide)
- **#38** Spiderfy overlapping POIs (partial mitigation shipped S143 via z21 overzoom)
- **#42** Waypoint numbered-badge overlap with POI icon
- **#45** Universal audio control at top menu

### 🗂️ Cluster E — Admin & Data infrastructure (**post-Monday**)
Back-office work that unlocks content pipelines. Will touch the Room migration debt from NOTE-L015.
- **#1** Polygon POIs + sensor lines + multi-location triggers → **NEW-PHASE 9P.C**
- **#5** Still-duplicate POI merging + OMEN SI-side coordination

### ⚙️ Cluster F — Ops tooling (**operator productivity, any time**)
Makes the operator more efficient; ship as time allows. Invisible to end-users.
- **#3** Randomize "walk" start location outside Salem
- **#6** Walk speedup 2× in dead zones
- **#7** Tour inventory PDF tool
- **#9** DME_ global start/stop scripts + USAGE.md
- **#15** Debug logging standards doc
- **#17** Periodic `/events` review cadence
- **#18** Periodic POI revalidation cadence
- **#41** Dev broadcast map-center handle (added S143)

### 🚧 Cluster G — Pre-launch hardening (**close to 2026-09-01**)
Final-mile items before Play Store submission.
- **#35** IP hardening (also in Cluster A — lands wherever post-counsel decides)
- **#39** Willows tile gap close (+50 tiles, trivial regen, added S143)

### 🔷 Cluster H — OMEN cross-project (**not in operator's hands**)
Upstream dependencies; OMEN sequences them.
- **#5** (SI side) still-duplicate POI merging
- **#8** Merge `~/Development/Salem` → SalemIntelligence
- **#16** Salem 1692 knowledge in SalemIntelligence
- **#22** Accusers' ends (historical content)
- **#23** Accusers tracked (historical content)
- **#24** Cross-project items via OMEN (meta)
- **#33** William Woodbury / Beverly historical integration (SI content; display folds back to LMA)

### 🚫 Cluster I — V2 / deferred
Explicitly post-launch.
- **#31** Self-contained chatbot (conflicts with V1 offline-only posture)

---

### S144 walkthrough checklist

- [ ] For each Cluster A + B item: confirm "Monday cutoff" OR move to post-Monday
- [ ] For each post-Monday item: confirm cluster assignment; promote or demote as needed
- [ ] Pick top 3–5 items to start S144+ work on
- [ ] OMEN relay: bundle any Cluster H promotions into next OMEN report
- [ ] Resolve Cluster E Room-migration dependency (NOTE-L015 DB wipe debt) — Cluster E work forces this conversation
- [ ] Decide whether #35 (IP hardening) is lawyer-prep (Cluster A) or pre-launch-prep (Cluster G) — depends on C-corp / patent-filing timeline counsel advises

---

## Triage legend

- **Status:** ✅ SHIPPED · 🟡 PARTIAL (some infrastructure exists) · 🔴 NOT STARTED · 🔷 OMEN-OWNED (cross-project)
- **Complexity:** **E** easy (≤½ session) · **M** medium (1 session) · **H** hard (2-4 sessions) · **X** very hard / new architecture (5+ sessions) · **?** needs investigation
- **Dependencies:** blockers or precursor work
- **Rec:** `FOLD` (into existing phase) · `NEW-PHASE` (new phase needed) · `OMEN` (kick upstream) · `V2` (post-launch) · `DROP` (already done / duplicate)

---

## Summary roll-up

| # | Item (short) | Status | Complexity | Rec |
|---|---|---|---|---|
| 1 | Polygon POIs + sensor lines in admin | 🔴 | H | NEW-PHASE (9P.C) |
| 2 | Narration repetition (newspaper stories while moving) | 🟡 | M | FOLD (9X close-out) |
| 3 | Randomize "walk" start location outside Salem | 🔴 | E | FOLD (debug tooling) |
| 4 | Global movement priority (GPS + sensor) / bearing-up | 🟡 | M | FOLD (S120 Step 9U.17 already queued) |
| 5 | Still-duplicate POI merging + SI-side | 🟡 | M | FOLD + OMEN |
| 6 | Walk speedup 2x in dead zones | 🔴 | E | FOLD (debug tooling) |
| 7 | Tour inventory PDF tool | 🟡 | M | FOLD (extend existing `generate-poi-inventory-pdf.py`) |
| 8 | Merge ~/Development/Salem → SalemIntelligence | 🔷 | X | OMEN (duplicate of 16, 24) |
| 9 | DME_ global start/stop + USAGE.md | 🔴 | M | NEW-PHASE (ops infra) |
| 10 | Legal walkthrough write-up | 🟡 | M | FOLD (NOTE-L017, GOVERNANCE.md) |
| 11 | Play Store $19.99 paid + age-gate avoidance | 🟡 | M | FOLD (NOTE-L017) |
| 12 | Easter eggs (witch-fly-by, last words) | 🔴 | M | NEW-PHASE (content eggs) |
| 13 | Top menu strip redesign, Salem-witchy buttons | 🔴 | M | NEW-PHASE (UX refresh) |
| 14 | POI experience overhaul (tour vs wander) | 🔴 | X | NEW-PHASE (UX arch) |
| 15 | Extensive debug logging everywhere | 🟡 | E | FOLD (standards doc) |
| 16 | Salem knowledge in SalemIntelligence | 🔷 | X | OMEN (dup of 8, 24) |
| 17 | Periodic `/events` review cadence | 🔴 | E | FOLD (ops cadence) |
| 18 | Periodic POI revalidation cadence | 🔴 | M | FOLD (ops cadence) |
| 19 | Graveyard souls / ambient memorial eggs | 🔴 | H | NEW-PHASE (with #12) |
| 20 | Fuzzy find rework | 🟡 | M | NEW-PHASE (Find v2) |
| 21 | Investigate running bugs + logs | 🟡 | ? | FOLD (ongoing) |
| 22 | Accusers' ends (historical content) | 🔴 | M | OMEN (SI content ask) |
| 23 | Accusers tracked (historical content) | 🔴 | M | OMEN (SI content ask) |
| 24 | Cross-project items via OMEN | 🔷 | — | OMEN (meta) |
| 25 | Transit narration wrap-around | 🔴 | E | FOLD (9X close-out) |
| 26 | No POI photos ever | 🟡 | E | FOLD (confirm + enforce) |
| 27 | Rebuild top hero view | 🔴 | M | NEW-PHASE (UX refresh) |
| 28 | POI graphics tiered by merchant payment | 🟡 | M | FOLD (Phase 11 commerce) |
| 29 | Dedicated "Salem Witch History" entry activity | 🔴 | M | NEW-PHASE (UX refresh) |
| 30 | History narrative quit + restart on silence | 🔴 | E | FOLD (narration behavior) |
| 31 | Self-contained chatbot | 🔴 | H | V2 (conflicts with V1 offline-only) |
| 32 | Repeat history narrative after POI interruption | 🔴 | E | FOLD (narration behavior) |
| 33 | William Woodbury house / Beverly historical integration | 🔴 | M | OMEN (SI content) + FOLD (display) |
| 34 | GPS FAB scope: app-only, not system-wide | 🟡 | ? | FOLD (verify existing behavior matches intent) |
| 35 | Encrypt all objects / harden IP | 🔴 | H | NEW-PHASE (pre-launch hardening) |
| 36 | Pencil-sketch images for every newspaper day | 🟡 | M | FOLD (Phase 9X+ extension of S132 portrait work) |
| 37 | Narration FAB (Off / Historical / Historical+Civil / All) | 🔴 | M | FOLD (UX refresh) |

**Cluster totals:** 11 Easy · 14 Medium · 6 Hard · 2 Very hard · 4 need investigation.

---

## Item-by-item triage

### 1 — Admin polygon POIs + sensor lines + multi-location triggers

> "adding polygons to POIs to make sure they get called out – has to be done manually, admin tool needs to do it, create a box 100m x 100m that I can adjust and create more points if I double click on a line to create a polygon. I also need to create sensors for things that need more precise activation. I will need to allow the POI to have multiple locations I can draw a line from that will trigger the POI."

- **Category:** admin-tool · geofencing · data-model
- **Status:** 🔴 NOT STARTED. `grep polygon` in `web/src/` returns nothing. Current geofence model is point + radius only (`NarrationGeofenceManager.kt` uses `REACH_RADIUS_M` / `REACH_RADIUS_WALKSIM_M`).
- **Complexity:** **H** — three features in one:
  - (a) Admin polygon editor UI (draw, reshape, double-click to add vertex) on the existing Leaflet map. Leaflet-draw or similar.
  - (b) Data model change: `salem_pois` needs a polygon column (PostGIS `GEOMETRY` or JSON array), Room needs a matching column, cache-proxy publish script updated.
  - (c) Multi-location POI — entity gets a set of points/lines rather than one centroid; geofence manager needs point-in-polygon OR nearest-line-segment logic.
- **Dependencies:** depends on the Room schema migration (touches NOTE-L015 "DB wipe across APK reinstalls" carry-forward — this is the catalyst for finally writing real Room migrations).
- **Rec:** **NEW-PHASE 9P.C (Admin Geofencing Polygons)** — 2-3 sessions.
  - Step 1: data model + PG migration (polygons as GeoJSON)
  - Step 2: admin tool Leaflet draw UI
  - Step 3: geofence manager polygon hit-test + bake into Room
  - Step 4: bundled asset DB regen + device verify

---

### 2 — Repetition in Salem newspaper stories read while moving

> "we seem to be getting too much repetition sometimes, are we reading multiple narrations on the same entity? This is about the Salem newspaper stories that get read while we are moving and have no content"

- **Category:** narration behavior
- **Status:** 🟡 PARTIAL. `HistoricalHeadlineQueue.kt` exists and is the path for transit-time newspaper dispatches. It already has some dedup logic ("daily no-repeat" per memory `project_multipass_narration.md`), but the operator is still hearing repeats.
- **Complexity:** **M** — investigate `HistoricalHeadlineQueue` dedup keys; likely either keying on date vs. entity-id and producing dupes across entities, OR the queue state resets between walk segments. Log review + fix.
- **Dependencies:** none — isolated.
- **Rec:** **FOLD into 9X close-out.** Add `DebugLogger` dispatch trace + fix in same session as #25 (wrap-around) + #30 + #32.

---

### 3 — Randomize "walk" start location if not in Salem

> "when we do we tour start, we need to make sure we randomize where we are if we click walk and we are not in Salem. This is very important for testing but not a final product."

- **Category:** debug tooling · walk-sim
- **Status:** 🔴 NOT STARTED. Current walk-sim starts from current GPS OR a hardcoded tour anchor (`SalemMainActivity.kt` lines 1516+).
- **Complexity:** **E** — if current location is outside the Salem bounding box, pick a random POI from `salem_pois` and teleport walk-sim origin there before starting the route. Debug-only guard.
- **Dependencies:** none.
- **Rec:** **FOLD into debug tooling.** Fits in the same session as #6 (dead-zone speedup) — both are walk-sim polish.

---

### 4 — Global movement priority (GPS + local sensor) + bearing-up + static-detection

> "we need to figure out a priority structure to handle movement. We need global movement (if we can trust the GPS and verify with local sensors) to show bearing as up as we move and more concise if we can do it if the user is stationary."

- **Category:** movement · GPS · sensors
- **Status:** 🟡 PARTIAL. Carry-forward item in STATE.md: *"Heading-up rotation smoothness — root cause identified in S115 (100 Hz sensor + main-thread saturation). Plan: cut log chatter, rate-limit apply, move sensor processing to background HandlerThread, switch static detection to wall-clock. Scheduled for S120 Step 9U.17."*
- **Complexity:** **M** — the fix plan is already written.
- **Dependencies:** none.
- **Rec:** **FOLD — already queued as S120 Step 9U.17.** Confirm scope matches user's intent (GPS-trust check + sensor verification is new vs. the existing smoothness fix — may need a separate step).

---

### 5 — Duplicate POI entries still exist; merge them + SI-side too

> "I still see duplicate entries – we need to find a way to merge them and Salem Intelligence needs to do the same"

- **Category:** data quality · dedup
- **Status:** 🟡 PARTIAL. S123 + S136 soft-deleted 110 + 97 POIs across multiple dedup passes. Pre-launch hard-delete tracked as carry-forward in STATE.md. But operator says dupes remain — need a fresh audit.
- **Complexity:** **M** — admin-tool merge UI (pick two, keep one, transfer references) + one more dedup pass.
- **Dependencies:** Requires cross-project coordination — SI owns its own entity store; LMA can only dedup its `salem_pois` slice.
- **Rec:** **FOLD (LMA side)** + **OMEN (ask SI to run their own dedup)**. Admin merge UI is one session.

---

### 6 — Walk speedup 2x in dead zones, return to 1x on discovery

> "If we are in dead zones using 'walk' we need to speed up the walk x2 permitting, we find something new, we go back to x1"

- **Category:** debug tooling · walk-sim
- **Status:** 🔴 NOT STARTED. Walk-sim is a fixed 1.4 m/s.
- **Complexity:** **E** — dynamic speed multiplier. When the narration geofence queue has been empty for N seconds AND no POI within lookahead radius, 2x; on entry or queue-pop, back to 1x.
- **Dependencies:** none.
- **Rec:** **FOLD with #3** — walk-sim polish session.

---

### 7 — Tour inventory PDF dump tool

> "I need a tool that will dump all the information for all tours and put it into a pdf that I can interrogate so I can adjust things that are wrong. It has to have all the details including all the POI's tha will execute along that tour."

- **Category:** admin tooling · content audit
- **Status:** 🟡 PARTIAL. `tools/generate-poi-inventory-pdf.py` exists (per STATE.md + ls) but dumps ALL POIs, not tour-scoped.
- **Complexity:** **M** — extend existing script: for each tour JSON in `app-salem/src/main/assets/tours/`, emit tour section with ordered stops + full POI detail + geofence radii + narration scripts + image paths.
- **Dependencies:** none.
- **Rec:** **FOLD.** Extend the existing Python tool. Half a session.

---

### 8 — Merge ~/Development/Salem into SalemIntelligence

> "How do we Merge ~/Development/Salem into Salem Intelligence – I need all of Salem in Salem Intelligence – everything – we will keep Salem Game isolated, but I need everything in Salem Intelligence to be available as the one primary source for LMA/Salem tours"

- **Category:** OMEN cross-project · data consolidation
- **Status:** 🔷 OMEN-OWNED. Duplicate of #16 and #24.
- **Complexity:** **X** — from LMA's side it's zero code; from OMEN/SI's side this is an ingest pipeline that handles 49 figures, ~500 facts, ~80 events, ~200 primary sources, 202 newspapers. Salem NPCs have the richest schema (dialogue, trust triggers, soul architecture) — SI has to decide what to absorb.
- **Dependencies:** Operator decision — Salem Game data (dialogue, game state) vs. Salem Intelligence data (historical facts, POIs, timeline). Clean split is non-trivial.
- **Rec:** **OMEN.** Raise in S138 OMEN report as a formal ask. Do NOT touch from LMA side — LMA consumes whatever SI exposes.

---

### 9 — DME_ global scripts + extensive USAGE.md

> "I need global DME_ scripts to start and stop everything. The DME_ Scripts will be the foundation of how I control the application when Claude code is down. I will need extensive documentation in the form of USAGE.md for every script we have with the DME_ scripts being global commands to start/continue any process we need and was running/needs to start from the last session. We will create scripts that I can easily start that will automate both starting applications and services I need along with scripts that ensure for the long events, we can continue"

- **Category:** ops infrastructure
- **Status:** 🔴 NOT STARTED. `grep DME_` returns zero matches. Existing scripts are `test-app.sh`, `overnight-test.sh`, `run-full-test-suite.sh`, `morning-transit-test.sh`, `monitor-snapshots.sh` (no naming convention).
- **Complexity:** **M** — the scripts themselves are a thin shell over existing commands, but "continuing long events from last session" implies state persistence (session-ID-aware resume logic). USAGE.md per script is straightforward.
  - Likely global path: `~/bin/DME_<verb>.sh` → symlinked PATH.
  - Candidate verbs: `DME_start_servers`, `DME_stop_servers`, `DME_launch_salem_emulator`, `DME_launch_admin_tool`, `DME_cache_proxy`, `DME_salem_intelligence`, `DME_resume_session`, `DME_overnight_test`.
- **Dependencies:** cross-project — some DME_ scripts will start things in other repos (SalemIntelligence, Salem Oracle). OMEN coordination for global PATH placement.
- **Rec:** **NEW-PHASE (Ops Infrastructure).** 1-2 sessions. Propose scope in OMEN report since it touches shared infra.

---

### 10 — Legal write-up for operator walkthrough before legal aid

> "Write up the legal so I can walk through it before I engage in legal aid – our name of our application and the final URL is not set - I need to consider all of these things in defining our application as an application that can be downloaded from google/etc."

- **Category:** legal · commerce
- **Status:** 🟡 PARTIAL. `GOVERNANCE.md` (56KB, lawyer-ready), `docs/PRIVACY-POLICY.md` (drafted S114), `IP.md` (14 patentable innovations), `COMMERCIALIZATION.md` exist. Missing: consolidated operator-walkthrough doc + app name decision + URL decision.
- **Complexity:** **M** — read + consolidate existing docs into one `docs/LEGAL-WALKTHROUGH.md` with: app name options, URL options, TOS stub, hosted-URL requirement for Privacy Policy (NOTE-L017), Google commission (15%/30%), age gate decision tree, data-safety form prep, trademark/copyright status.
- **Dependencies:** operator decides app name + URL; NOTE-L014 Privacy Policy is still "in OMEN's court."
- **Rec:** **FOLD into Phase 11 Launch Readiness.** One session to write the walkthrough doc.
- **S138 update:** Operator clarified this is a **PG-13 app** (IARC Teen / 13+ target audience). Legal walkthrough scope simplified — no adult-only framing needed. Bar POIs = non-promotional map data (no opinion content).

---

### 11 — Play Store $19.99 paid + age-gate avoidance via paid gate

> "I need to know everything about how to place an application for commercial user in google. I want to make any user spend $19.99 to buy it and I want to avoid the crazy new validations we have to do on age. I figure if I make people on Google pay $19.99 for the application – Google will do the new age requirement nonsense."

- **Category:** commerce · play-store
- **Status:** 🟡 PARTIAL. NOTE-L017 Play Store release checklist exists. Business model memory says tiered pricing ($4.99/$9.99/$49.99mo). User is now saying **$19.99 flat paid** — this is a pricing change. Memory `project_business_model.md` needs updating.
- **Complexity:** **M** — most items in NOTE-L017 are operator setup (dev account, merchant profile, content rating). Claude-Code-side items: target SDK, AAB build, signing key, store listing assets, data-safety form. Age-gate-via-paid hypothesis needs research — Google's age-assurance applies to apps rated Teen+ regardless of paid status; paid apps still need content rating; "avoid age validation" may not hold.
- **Dependencies:** NOTE-L017, NOTE-L014, #10.
- **Rec:** **FOLD into Phase 11.** Verify the age-gate-via-paid assumption in the legal walkthrough (#10) first. Update `project_business_model.md` memory with the $19.99 pivot.
- **S138 RESOLVED (operator clarification):** Content rating = **Teen (PG-13)**, Target Audience = **13+**. Google's Family Link handles age enforcement automatically per Teen content rating; no developer-side age gate needed. Bar POIs = non-promotional references. No adult-only EULA click-through. Data Safety form = all "none" (offline V1). Privacy Policy hosted publicly (already drafted). Play Integrity API folded into #35 hardening. No state-level age-law exposure (no adult content, no data collection).

---

### 12 — Easter eggs (witch fly-by, last words, pressed Giles Corey)

> "I am going to want silly eggs in the application. Unknown spots that trigger unique things. Maybe a witch flying by with staged graphics of a witch on a broom in case they get close to a 'known witch spot' (al la Near the Witch House). There, we should also hear a witch laughing and the sound of a witch on a broom going by. Or if very near plots of people in a cemetery, we hear their last words. Or at things like where Giloy was pressed, his last moaning moments"

- **Category:** content · UX · ambient
- **Status:** 🔴 NOT STARTED. No easter-egg infrastructure.
- **Complexity:** **M** — requires:
  - (a) new data model: `salem_eggs` table (id, trigger_type {point/polygon/near-entity}, lat/lng, radius_m, trigger_once_per_session, audio_clip_path, overlay_image_path, caption).
  - (b) trigger manager: tight-radius (5m) geofence checks in `NarrationGeofenceManager`, distinct from POI dispatch.
  - (c) overlay renderer: transient bottom-sheet or full-screen overlay with image + audio.
  - (d) content: audio clips (witch laugh, broom whoosh, Giles Corey "more weight") + graphics (witch on broom, gallows, hanging tree).
- **Dependencies:** #19 (graveyard souls) is a specialization of this infrastructure. Merge the two into one phase.
- **Rec:** **NEW-PHASE (Content Eggs + Graveyard Souls)** combining #12 + #19. 2-3 sessions. Generates audio via Bark/Piper + sox (per existing voice pipeline), generates images via local SD (Forge).

---

### 13 — Top menu strip redesign, Salem-witchy buttons

> "We need to redo all the top menu strip that is very wasteful and redo all the buttons to make them Salem Tour aware including making button graphical with a background that is fun and we need to redo the entire menu system. We need to make every top menu item Salem and witchy and concise in what it does. The Menu system needs to be the gateway to control what they see"

- **Category:** UX · branding
- **Status:** 🔴 NOT STARTED. `AppBarMenuManager.kt` exists and manages the top bar today.
- **Complexity:** **M** — design pass + drawable generation (AI Studio / SD) + wiring. Menu taxonomy needs a design doc first.
- **Dependencies:** pair with #14 (POI UX overhaul), #27 (hero view), #29 (history activity), #37 (narration FAB). These five are the "UX refresh" cluster.
- **Rec:** **NEW-PHASE (UX Refresh)** bundling #13 + #14 + #27 + #29 + #37. Start with a design review doc (update `DESIGN-REVIEW.md`), then incremental sessions per surface.

---

### 14 — POI experience overhaul (tour-focused vs. wander-focused)

> "We need to do a much better way of dealing with the POIs. I need your help to figure out how to sort this out. For tours, they need to be tour focused. For general wandering around, we need to be very attuned to what the user is doing… we need everything to present a screen with what are their priorities and what do they want to experience/hear/etc. Users need to have general ways to control the content that is being told to them in detailed ways that are easy to navigate."

- **Category:** UX architecture · narration
- **Status:** 🔴 NOT STARTED. Current model: ambient narration is the default, tours are optional overlays (per memory `feedback_ambient_narration.md`). Operator is now asking for explicit mode switching + per-user preferences.
- **Complexity:** **X** — preference screen + mode switcher + narration filter pipeline + persisted state. This is the biggest UX change on the list.
- **Dependencies:** #13 (menu), #37 (narration FAB), #27 (hero view) are the surfaces; this is the brain.
- **Rec:** **NEW-PHASE (UX Refresh)** — same cluster as #13. Begin with a design-doc session before any code.

---

### 15 — Extensive debug logging everywhere

> "Everything we are doing now needs to have extensive debug logging – Anything we create needs to have that built into that"

- **Category:** standards · ops
- **Status:** 🟡 PARTIAL. `DebugLogger` is used throughout (seen in `SalemMainActivity.kt`, `NarrationGeofenceManager.kt`). Standard exists but not enforced as a norm.
- **Complexity:** **E** — add a standards section to `CLAUDE.md` (or a `docs/STANDARDS.md`) that says every new method/feature must log entry/exit + decision points through `DebugLogger`. Enforce in review.
- **Dependencies:** none.
- **Rec:** **FOLD.** Write the standard, reference it in every future session's work-log. Optional: a lint rule later.

---

### 16 — Salem knowledge in SalemIntelligence (OMEN)

> "I need all the knowledge of ~/Development/Salem in SalemIntelligence – this is a OMEN thing."

- **Category:** OMEN cross-project
- **Status:** 🔷 OMEN-OWNED. Duplicate of #8 and #24.
- **Rec:** **OMEN.** Consolidate #8 + #16 + #24 into a single OMEN ask.

---

### 17 — Periodic /events review cadence

> "The /events – this will be something we will need to review on a periodic basis. Need to have that built into the overland cadence when we get to live"

- **Category:** ops cadence
- **Status:** 🔴 NOT STARTED as a scheduled process. Events calendar DB exists (Phase 9, 20 events).
- **Complexity:** **E** — post-launch ops cadence. For now, a dated TODO in STATE.md + a recurring entry in a future `ops/` doc.
- **Dependencies:** Phase 11 (launch) first.
- **Rec:** **FOLD into post-launch ops cadence doc.** Nothing to build now.

---

### 18 — Periodic POI revalidation cadence

> "We will also need to do a global POI review every 'X' amount of time to revalidate since businesses come and go as does updates to web pages/etc."

- **Category:** ops cadence · data quality
- **Status:** 🔴 NOT STARTED. Existing provenance framework has `verified_date` + `stale_after` fields — the data model supports this, nothing calls it on a schedule.
- **Complexity:** **M** — cache-proxy cron: for POIs past `stale_after`, fire re-verification (Google Places API, website check, or admin-tool queue). Emit a "needs review" list.
- **Dependencies:** Phase 11 launch defines the cadence. Post-launch.
- **Rec:** **FOLD into post-launch ops cadence.** Build the script now so it's ready, schedule activation post-launch.

---

### 19 — Graveyard souls / ambient memorial eggs

> "we will need to be able to make special fun things that do some interesting local thing. If you it that point (<5m) – you might get something special depending of what that 'egg' spot you hit that we create outside of the real data just to make it fun. For instance, we should have some inventory of all the souls in a graveyard and we should know who they are and we should be able to memorialize some soul that was us before us to share what they have to say in 15-30 words with us showing something that identifies why this happened. This is about making sure all the souls of Salem can have something to say about what is important to them in their own way and this application allows them to place their passion into this application."

- **Category:** content · UX · ambient · data
- **Status:** 🔴 NOT STARTED.
- **Complexity:** **H** — specialization of #12 infrastructure, plus a graveyard-soul data model (name, dates, death-cause, 15-30 word last-words snippet, optional image). Content generation pipeline (Oracle/SI → JSON → bundled assets). Salem has multiple historic graveyards; Old Burying Point alone has ~340 stones.
- **Dependencies:** builds on #12. Content generation needs Oracle time.
- **Rec:** **NEW-PHASE (Content Eggs + Graveyard Souls)** — bundled with #12.

---

### 20 — Fuzzy find rework

> "The 'find' from the old LMA must be greatly improved. We need the ability to find everything fuzzy. We need to entirely rework how the 'find' works"

- **Category:** UX · search
- **Status:** 🟡 PARTIAL. `SalemMainActivityFind.kt` + `FindViewModel.kt` exist.
- **Complexity:** **M** — current find is likely exact-match. Rework: fuzzy matcher (trigram / FTS / Levenshtein), search across names + descriptions + historical_note + tags, scoped filters (POI type, tour, period).
- **Dependencies:** benefits from #14 (UX overhaul) for the screen design.
- **Rec:** **NEW-PHASE (Find v2)** — can pair with or follow #14.

---

### 21 — Investigate running bugs + logs

> "we have bugs running all time time while we watch this over time, we need to investigate those bugs and the logs first"

- **Category:** maintenance · investigation
- **Status:** 🟡 PARTIAL. Known carry-forwards in STATE.md: heading-up rotation smoothness, GPS journey line backgrounding, Room `@Insert` silent-drop, DB wipe across reinstalls.
- **Complexity:** **?** — depends on what's found.
- **Dependencies:** none.
- **Rec:** **FOLD into ongoing work.** Each session pick one. The silent Room `@Insert` drop (S129) is the most important — data corruption risk.

---

### 22 — Accusers' ends (historical content)

> "We need to get the historical facts of all those that incriminated anyone, what was their ends."

- **Category:** historical content · SI data
- **Status:** 🔴 NOT STARTED. Salem project has 49 NPC figures; accusers overlap with that set but "what became of them" isn't explicit. Oracle/SI has the corpus.
- **Complexity:** **M** — content sourcing + structured data model. Accuser → accused links, final fate (died-in-prison, fled, recanted, lived-out-days, etc).
- **Dependencies:** SI has to own the source-of-truth data.
- **Rec:** **OMEN (SI content ask)**. LMA consumes.

---

### 23 — Accusers tracked (historical content)

> "for all of those that incriminated anyone, we will know that too"

- **Category:** historical content · SI data
- **Status:** 🔴 NOT STARTED.
- **Complexity:** **M** — pair with #22. This is the "who" side; #22 is the "fate" side. Same data model.
- **Dependencies:** #22.
- **Rec:** **OMEN.** Bundle with #22.

---

### 24 — Cross-project items via OMEN

> "these are probably things to send to Salem Intelligence via OMEN"

- **Category:** meta / routing
- **Status:** 🔷 — acknowledgment, not an item.
- **Rec:** **OMEN.** Items to route: #8, #16, #22, #23, possibly #5 (SI-side dedup), possibly #33 (Beverly/William Woodbury content).

---

### 25 — Transit narration wrap-around (restart after end)

> "if we end at the Salem witch dialog that is in the background that fills the space in transit from place to place to fill, that needs to wrap over and after it ends, it has to restart"

- **Category:** narration behavior
- **Status:** 🔴 NOT STARTED. `HistoricalHeadlineQueue` likely exhausts and stops rather than looping.
- **Complexity:** **E** — on queue-end, re-seed the queue with a fresh shuffle.
- **Dependencies:** pair with #2 (repetition fix) to make sure restart doesn't immediately repeat the last entry.
- **Rec:** **FOLD into 9X close-out narration session.** Bundled with #2, #30, #32.

---

### 26 — No POI photos ever

> "we DO NOT use any photos of any POI"

- **Category:** content policy
- **Status:** 🟡 PARTIAL. Memory `feedback_ai_image_generation.md` says imagery is AI-generated. But Play Store listing assets + historical portraits + pencil sketches are all non-photo. Need to confirm no `photo_url` or equivalent is plumbed into any POI display path.
- **Complexity:** **E** — audit: grep for `photo_url`, `image_url`, `poi.image`; remove any photo-fetch plumbing; replace with AI-generated category icons for unpaid POIs (per #28 tiered graphics).
- **Dependencies:** #28 for the "paid = unique image, unpaid = default" rule.
- **Rec:** **FOLD + confirm.** Half a session to audit + document the policy.

---

### 27 — Rebuild top hero view

> "We need to rebuild to the top Hero view."

- **Category:** UX
- **Status:** 🔴 NOT STARTED (in the sense of a redesign). Current hero view exists.
- **Complexity:** **M** — redesign + new assets.
- **Dependencies:** part of UX Refresh cluster with #13, #14, #29, #37.
- **Rec:** **NEW-PHASE (UX Refresh)** cluster.

---

### 28 — POI graphics tiered by merchant payment

> "The POIs should always be aligned to the graphic of the POI, unless businesses pay, they get the default graphics"

- **Category:** commerce · content
- **Status:** 🟡 PARTIAL. Memory `project_poi_monetization.md`: *"Every POI controls its own content (icons, narration, photos) based on merchant payment tier."* Architecture memory, not yet implemented.
- **Complexity:** **M** — add `merchant_tier` column to `salem_pois`, add tier-aware icon resolver (paid → custom graphic path, unpaid → default category icon). Admin tool needs a tier-edit control.
- **Dependencies:** #26 (no photos policy).
- **Rec:** **FOLD into Phase 11 commerce features.** Don't touch yet — pre-launch polish.

---

### 29 — Dedicated "Salem Witch History" entry activity

> "we will need a different activity with the opening opening, current we have explore sal-em and take a tour, we will need to have a dedicated Salem witch history as part of the initial menu that does nothing more than allow people to listen and watch all the Salem history go by"

- **Category:** UX · entry-point architecture
- **Status:** 🔴 NOT STARTED. Current entry: Explore Salem, Take a Tour (2 cards).
- **Complexity:** **M** — new activity, new entry card, pipes into the existing witch-trials newspaper corpus (#25 wrap-around applies here too).
- **Dependencies:** benefits from #25, #30, #32 narration behavior fixes; benefits from #36 pencil-sketch images for visual track.
- **Rec:** **NEW-PHASE (UX Refresh)** cluster.

---

### 30 — History narrative quits + restarts on silence

> "the history narrative should now quit and restart if there is silence…"

- **Category:** narration behavior
- **Status:** 🔴 NOT STARTED.
- **Complexity:** **E** — detect TTS silence > threshold (2-5s) during history playback; abort current utterance, restart queue from top.
- **Dependencies:** Implementation needs a decision on what counts as "silence" — TTS engine idle OR user muting the device? Clarify with operator.
- **Rec:** **FOLD into 9X close-out narration session.** Bundled with #2, #25, #32.

---

### 31 — Self-contained chatbot

> "we will need a self-contained chat-bot to answer questions"

- **Category:** LLM · feature
- **Status:** 🔴 NOT STARTED.
- **Complexity:** **H** — conflicts with **memory `project_v1_offline_only.md`: "V1 commercial release is fully offline, no hosted LLM/KB. SalemIntelligence is build-time data source only."** A self-contained chatbot would either need (a) an on-device LLM (Gemma 2B / similar, +1GB APK size) or (b) a retrieval-only QA system keyed off a bundled FAQ/KB (feasible offline, limited).
- **Dependencies:** massive decision — V1 scope or V2?
- **Rec:** **V2 (post-launch).** Do not build for 2026-09-01 ship. If operator wants it for V1, need explicit scope exception + tier definition (per NOTE-L008 premium tier).

---

### 32 — Repeat history narrative after POI interruption

> "if the auto-conversation about the history is stopped by a new POI taking control, we should make sure it Is repeated before we more on"

- **Category:** narration behavior
- **Status:** 🔴 NOT STARTED.
- **Complexity:** **E** — when `HistoricalHeadlineQueue` is pre-empted by a POI geofence trigger, save the interrupted utterance; after POI narration ends, re-read it from the top before popping next.
- **Dependencies:** none.
- **Rec:** **FOLD into 9X close-out narration session.** Bundled with #2, #25, #30.

---

### 33 — William Woodbury house / Beverly historical integration

> "How did the William Woodbury house interact with this history? I want to know how people in the house I live in were affected by that and it should be part of the history. I live in the William historical house that was build in 1676. This is personal to me, not random. I want to make sure the application and Claude code knows that I have a personal history and view of this and I am very close to Beverly and Salem. My work is to make this a good things for everyone – I ant our first pass to give everyone equal footing. I want to have what Beverly and the William Woodbury house did as the Salem with trialed continued. Beverly was old planters – there is a lot of things that happened that involved all of Salem village that Beverly was once part of."

- **Category:** historical content · OMEN cross-project · personal context
- **Status:** 🔴 NOT STARTED. Beverly is geographically just north of Salem; in 1692 it was part of Salem Village. Old Planters community separated from Salem earlier. "William Woodbury house 1676" → Beverly has a Woodbury family documented in old planter records.
- **Complexity:** **M** — this is both a content ask (SI has to source Beverly / Old Planters / Woodbury content) and a display ask (LMA has to expose it as a dedicated narrative thread or POI set).
- **Dependencies:** OMEN routes the content ask to SI. LMA consumes.
- **Rec:** **OMEN (SI content)** + **FOLD (LMA display)**. Also: save a user memory that operator lives in William Woodbury house 1676 in Beverly, personal stake in Beverly / Old Planters historical content.
- **Also:** per operator direction, treat this as one-shot personal context worth preserving in memory.

---

### 34 — GPS FAB scope: app-only, not system-wide

> "extremely important – we need to not disable all GPS when we hit the FAB that disables it – it needs only to keep our application from touching GPS"

- **Category:** GPS behavior
- **Status:** 🟡 NEEDS VERIFICATION. The comment in `SalemMainActivity.kt:95` says: *"the bottom-left 'GPS' FAB controls only whether the path polyline is displayed on the map."* That's a display toggle, not a GPS-off toggle. Operator may be referring to a different FAB, OR the code comment is stale and the FAB actually does more than it claims.
- **Complexity:** **?** — need to find which FAB operator means, then confirm its real effect. If it actually kills the app's location client, that's a bug; fix is to stop the internal client but never touch Android's global GPS state (which an app can't do anyway — user may be confusing app-state with Android-state).
- **Dependencies:** investigation first.
- **Rec:** **FOLD (investigate).** 30 min — find which FAB, read its handler, confirm behavior, document or fix.

---

### 35 — Encrypt all objects / harden IP

> "every object we use for information must be encrypted. I don't want people reverse hacking us and stealing our IP. The entire application has to be hardened."

- **Category:** security · pre-launch hardening
- **Status:** 🔴 NOT STARTED. Mentions exist in `GOVERNANCE.md`, `DESIGN-REVIEW.md`, `COMMERCIALIZATION.md` — policy but no implementation.
- **Complexity:** **H** — options:
  - (a) SQLCipher for bundled Room DB (~10MB added, CPU overhead on first open).
  - (b) AES-encrypt bundled JSON assets; decrypt in-memory on load. Key in keystore or obfuscated (not actually secure — attacker can pull it from APK).
  - (c) ProGuard/R8 obfuscation for code (already planned in NOTE-L017 target SDK + AAB line-items).
  - (d) Key attestation + Play Integrity to gate LLM calls (premium tier only).
  - **Honest assessment:** full IP protection against a motivated attacker is impossible on a distributed Android APK. Best-realistic goal: raise the bar high enough to deter casual extraction.
- **Dependencies:** pre-launch Phase 11.
- **Rec:** **NEW-PHASE (Pre-Launch Hardening)**. 2-3 sessions. Propose a realistic threat model in the plan doc first.

---

### 36 — Pencil-sketch images for every newspaper day

> "We will want pencil sketch images for every newpaper day we populated, the first image being the image of the most important story of that day (Witches at the gallows being readied to be hung) and an image either of the affected showing their suffering for that day, the soulful retrospective of the end of day events, or a view of old Salem at dusk with the heavy air over it. Feeding it clues to the days locations, their details and images of the individuals in close proximity as it would be a picture taken from 10-20 yards."

- **Category:** content · imagery
- **Status:** 🟡 PARTIAL. S132 shipped pencil-sketch **portraits** for NPCs. Extending the same style to **per-newspaper-day scene images** is straightforward.
- **Complexity:** **M** — ~202 newspaper articles; per day, generate (i) headline scene and (ii) end-of-day retrospective image. SD/Forge pipeline exists. Prompt engineering takes the most time. Storage ~10-50 MB for 400 pencil sketches at 512x768.
- **Dependencies:** Oracle tiles (TOP PRIORITY) should land first so we know the exact per-day story inventory.
- **Rec:** **FOLD into 9X+ extension.** Run after Oracle tiles are imported; same SD pipeline as portraits.

---

### 37 — Narration FAB (Off / Historical / Historical+Civil+Parks / All)

> "Need a narration FAB (Off/Historical/historical+civil+parks/etc/All POIs that are visible)"

- **Category:** UX · narration control
- **Status:** 🔴 NOT STARTED. Existing FABs: heading-up, GPS polyline, aircraft, show-all-POIs. No narration-scope FAB.
- **Complexity:** **M** — new FAB with a speed-dial or cycle of modes; backend is a `narrationScopeFilter` that `NarrationGeofenceManager` consults before dispatching.
- **Dependencies:** category taxonomy decision (what's "historical" vs "civil" vs "park" etc.). Needs operator input.
- **Rec:** **FOLD into UX Refresh cluster** (#13, #14, #27, #29, #37 all together).

---

## Natural phase clusters (for master plan integration)

1. **9X close-out (narration behavior)** — FOLD: #2, #25, #30, #32. One session after Oracle tiles land.
2. **Debug tooling session** — FOLD: #3, #6. Half a session.
3. **Standards** — FOLD: #15 (logging), #21 (bug sweep). Ongoing.
4. **NEW-PHASE 9P.C — Admin Polygon POIs** — #1. 2-3 sessions.
5. **NEW-PHASE — Find v2** — #20. 1 session.
6. **NEW-PHASE — Content Eggs + Graveyard Souls** — #12, #19. 2-3 sessions.
7. **NEW-PHASE — UX Refresh** — #13, #14, #27, #29, #37. 3-5 sessions, kicked off with a DESIGN-REVIEW.md update.
8. **NEW-PHASE — Ops Infrastructure (DME_ scripts)** — #9. 1-2 sessions.
9. **NEW-PHASE — Pre-Launch Hardening** — #35. 2-3 sessions. Phase 11 prerequisite.
10. **FOLD into Phase 11 — Launch Readiness** — #10 (legal walkthrough), #11 (pricing + Play Store), #26 (no-photos policy), #28 (tiered POI graphics).
11. **Post-launch ops cadence** — #17 (events review), #18 (POI revalidation).
12. **OMEN asks (one consolidated report)** — #5 (SI dedup), #8 + #16 + #24 (Salem → SI merge), #22 + #23 (accuser ends & tracking), #33 (Beverly / Woodbury content).
13. **V2 (post-launch)** — #31 (chatbot).
14. **Investigate** — #34 (FAB GPS scope confirmation, 30 min).
15. **Already queued** — #4 (S120 Step 9U.17 heading-up fix).
16. **Extend existing tools** — #7 (tour PDF from existing script), #36 (pencil-sketch days from existing portrait pipeline).

---

## Recommended next-session order (operator picks when ready)

**Fast wins (one session each, low risk):**
- Debug tooling session (#3, #6)
- 9X close-out narration behavior (#2, #25, #30, #32)
- Tour PDF extension (#7)
- FAB GPS scope investigation (#34) + no-photos audit (#26)
- Standards doc (#15)

**Planning sessions (no code, produce design docs):**
- UX Refresh design review (#13 + #14 + #27 + #29 + #37 feeder)
- Pre-Launch Hardening threat model (#35)
- Legal walkthrough consolidation (#10 + #11)

**New phases to kick off after planning:**
- 9P.C Admin Polygons (#1)
- Content Eggs + Graveyard Souls (#12, #19)
- Ops Infrastructure DME_ scripts (#9)
- Find v2 (#20)

**Out-of-session / OMEN-owned:**
- Salem → SalemIntelligence merge (#8, #16, #24)
- Accuser content (#22, #23)
- Beverly / Woodbury content (#33)
- SI-side dedup (#5)

**Gated / deferred:**
- Chatbot (#31) — V2 post-launch
- Periodic cadence ops (#17, #18) — post-launch

---

## Operator review checklist (please confirm before master-plan integration)

- [ ] Triage accuracy — any item marked ✅/🟡 that is actually 🔴 (i.e., not working as described)?
- [ ] Cluster groupings — agree with the 16 clusters above, or split/merge?
- [ ] Priority order — of the "fast wins," which first after Oracle tiles land?
- [ ] V2 items — #31 (chatbot) accepted as post-launch?
- [ ] OMEN batch — OK to bundle #5, #8, #16, #22, #23, #24, #33 into the S138 OMEN report?
- [x] **$19.99 pricing — RESOLVED (S138 dialog):** Replace tiered model with $19.99 flat. V1 is single paid SKU, offline-only, no ads, no LLM. `project_business_model.md` memory rewritten.
- [x] **Personal context — RESOLVED (S138 dialog):** William Woodbury house / Beverly / Old Planters residency saved as PRIVATE user memory (`user_personal_context_beverly.md`). Never surfaced in public artifacts.
- [x] **GPS FAB (#34) — RESOLVED which one:** Bottom-left GPS / journey FAB. Still needs code audit next session to confirm handler scope matches code-comment claim.
- [x] **Age-gate theory (#11) — RESOLVED via operator reframe:** V1 goes fully offline, no network, user can delete all on-device data. Claude response: offline-only solves data/privacy concerns but NOT IARC content-rating-driven age-assurance. Salem trials content probably rates Teen, which triggers age gate regardless of paid/offline. Legal walkthrough (#10) to confirm under Google's current Developer Program Policies.

---

**Next action:** awaiting operator review of remaining open items above. No code changes this session unless explicitly directed.

---

## S143 additions (2026-04-17)

### #38 — Spiderfy overlapping-POI disambiguation
**Origin:** S143 operator idea while device-testing.

**Problem:** Salem downtown has many co-located POIs (historic house + museum + gift shop at the same footprint). Tapping the map over a cluster currently selects one at random — the user can't reach the others without zooming in to the exact pixel where each POI sits.

**Proposed UX (operator's spec):**
- Tap hits a cluster → a small red-filled circle marks the tap point
- Overlapping POIs fan out onto a ring around the tap point
- Leader lines drawn from tap-point to each fanned POI
- Tapping a fanned POI selects it; tap elsewhere collapses the fan

**Claude recommendation (hybrid):**
- 2–8 overlapping → spiderfy (as described)
- 9+ overlapping → bottom-sheet list (too crowded for a ring)
- Single tap with no overlap → current behavior (direct select)

**Scope estimate:** ~1–2 sessions. Covers: overlap detection (collect all POIs within N screen-pixels of tap), ring layout math, animated fan open/close, hit-testing on fanned ring, dismiss handling (tap-outside + back button), leader-line polygons, z-order management against existing overlays (GPS dot, trigger rings, red location ball). Partial mitigation already shipped in S143 via overzoom-to-z21 (POI markers separate further at z20/z21, reducing but not eliminating the need).

**Dependencies:** none — pure client-side UI over existing POI markers.

**Status:** Parked.

---

### #39 — Tile bbox north-edge gap (Salem Willows)
**Origin:** S143 device-verify — bbox set to 42.475–42.545 N covers Salem city proper but cuts off the Salem Willows peninsula tip (lighthouse + park extend to ~42.560 N). Operator's test device was parked at 42.5567 and saw gray.

**Proposed fix:** extend bbox north from 42.545 → 42.560. Cost: ~50 extra tiles at z16–17 full-Salem (under 1 MB, ~20s download time). Trivial regen.

**Status:** Parked — operator doesn't live in Salem so the gap only affects NE-peninsula coverage. Revisit if/when Willows content gets built out.

---

### #40 — Post-splash "Explore Salem" hub card still says "Wicked Salem / Witch City Tour"
**Origin:** S143 device-verify after S142 splash retitle.

**Context:** S142 retitled `activity_splash.xml` to "Katrina's Mystic Visitors Guide / Historic Salem Tour App" but the hub screen reached by tapping "Explore Salem" on the post-splash home card still shows the old "Wicked Salem / Witch City Tour" banner. Launcher label + splash are under the new identity; this intermediate card was missed.

**Scope:** 1 string edit + device-verify. Small.

**Status:** Parked.

---

### #41 — Dev broadcast handle for map-center from adb
**Origin:** S143 operator request while device-testing — "we need a dev test way to do that."

**Problem:** No way from adb/terminal to tell the app "center the map at lat,lng and zoom." Every device-verify pass requires physical taps through the UI (grid → Find → search → tap result → tap Map button), which is slow and fragile under automation.

**Proposed fix:** `BroadcastReceiver` registered in `SalemMainActivity.onCreate` gated behind `BuildConfig.DEBUG` (debug builds only, stripped from release). Accepts:
```
adb shell am broadcast -a com.destructiveaigurus.dev.GOTO --ef lat 42.5215 --ef lng -70.8928 --ei zoom 18
```

Also needs `buildFeatures { buildConfig = true }` in `app-salem/build.gradle` so `BuildConfig.DEBUG` is generated.

**Scope:** ~15 min. Receiver class + manifest registration (runtime only, not static) + one-line call to `map.controller.animateTo(GeoPoint(lat, lng), zoom, 800L)`.

**Status:** Parked — operator said "we'll work on that" (deferred) during S143 device-verify.

---

### #42 — Waypoint numbered badge overlaps POI marker
**Origin:** S143 operator observation while device-testing.

**Problem:** When a tour waypoint (numbered circle 1/10, 2/10, …) sits at the same lat/lng as a POI (which is the common case — a tour stop IS a POI), the numbered waypoint marker is drawn on top of the POI's category icon at CENTER_CENTER anchor, completely covering it. Users see the waypoint number but lose the POI identity cue (color + category icon) that tells them what kind of place this stop is.

**Root cause (from S143 survey):**
- POI markers: `MarkerIconHelper.forCategory()` → 28dp tinted bitmap, added via `loadNarrationPointMarkers()` at `SalemMainActivity.kt:2141-2207`.
- Waypoint badges: `MarkerIconHelper.createNumberedCircle()` (line 1027) → 28dp filled color circle with white border + centered number.
- Waypoint marker added to `binding.mapView.overlays` AFTER POI markers in `drawTourRoute()` at `SalemMainActivityTour.kt:1004-1022`, with `setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)`.
- Same lat/lng + same anchor + filled disc = full cover.

**Proposed fix (operator picked option 1 — offset badge):**
Composite the waypoint number as a smaller badge in the upper-right corner of the POI icon, not a separate marker. One bitmap = POI icon (28dp, centered) + small filled circle (14-16dp, positioned at upper-right quadrant) with number. POI category/color cue stays visible; number remains readable.

**Implementation sketch:**
- New helper in `MarkerIconHelper`: `compositePoiWithBadge(context, category, number, color, sizeDp = 36)` — bigger canvas to fit icon + corner badge
- In `drawTourRoute()`, when building the waypoint marker, check the corresponding POI's category and use the composite icon instead of the raw numbered circle
- Keep `setAnchor(CENTER_CENTER)` — composite bitmap is already laid out correctly
- Remove the separate numbered-only marker path for tour-stop POIs; keep the raw numbered circle only for waypoints with no associated POI (shouldn't happen in the Salem tours but future-proof)

**Scope:** ~1 session. Canvas composite drawing, badge position math, z-order verify, active-tour vs. preview handling (operator said "just active tour" implicitly by picking option 1 without clarifying — if preview tours also show waypoints, same treatment applies).

**Status:** Parked.

---

### #43 — Splash opening graphic (replace splash_witchkitty.png with Katrina)
**Origin:** S142 operator direction, formalized to parking lot S143.

**Problem:** Current splash illustration `app-salem/src/main/res/drawable/splash_witchkitty.png` is a generic AI-generated witch-cat cartoon. After the rebrand to "Katrina's Mystic Visitors Guide" (S142 Step 2), the splash illustration should be **Katrina** — the operator's actual cat and the app's namesake — so the brand identity and the visual art match.

**Proposed art direction (TBD):**
- **Photographic** — a clean photo of Katrina, color-graded to match the splash background (deep purple #2A1B3D · gold accents #C9A84C).
- **Stylized (SD img2img)** — a real Katrina photo run through Forge/ComfyUI with a witchy/Salem illustration prompt, preserving recognition but matching the existing cartoon-ish splash tone.
- **Hybrid** — stylized primary pass with a photographic accent crop (operator can pick the final once variants are generated).

**Dependencies:**
- Operator supplies source photos of Katrina.
- GPU-caution rule applies (`feedback_gpu_caution.md`) — confirm SalemIntelligence / AudioCraft not running before firing up SD.
- Final asset replaces `splash_witchkitty.png` in place (keeps the same res id wiring in `activity_splash.xml`).

**Scope:** ~½ session of operator+GPU time to generate candidates, pick, finalize, rebuild APK, device-verify.

**Status:** Parked — content-art session, not bug-fix work.

---

### #44 — Splash voiceover (replace splash_voiceover.wav with screaming-cat)
**Origin:** S142 operator direction, formalized to parking lot S143.

**Problem:** Current splash audio `app-salem/src/main/res/raw/splash_voiceover.wav` is placeholder TTS. Operator wants a **screaming-cat sound** for the splash (matches the new Katrina-themed splash illustration and the "Mystic" brand tone).

**Proposed approach (TBD):**
- **Sample-based** — freesound.org screaming-cat recordings run through sox effects pipeline (per `project_voice_audio.md` memory: "commanding witches/warlocks, no hesitation, heavy sox post-processing"). Fast, license-safe if sourced from CC0 packs.
- **Generative (AudioCraft / AudioGen on RTX 3090)** — prompt "distressed Halloween cat scream with mild reverb." Slower, needs GPU, but fully original.
- **Hybrid** — sample as base layer + generative pass for extra menace + sox polish on both.

**Dependencies:**
- GPU-caution rule (`feedback_gpu_caution.md`) — confirm RTX 3090 is idle before kicking off AudioCraft.
- Output must pass PG-13 / IARC Teen content rule (`feedback_pg13_content_rule.md`) — no gore-coded audio, no suggestive vocalizations.
- Final asset replaces `splash_voiceover.wav` in place (keeps the same res id wiring in `SplashActivity`).

**Scope:** ~½ session of sox + optional GPU time to generate, audition, pick, drop in, device-verify.

**Status:** Parked — content-art session, paired naturally with #43 (same session = "splash refresh" block).

---

### #45 — Universal audio control at the top menu
**Origin:** S143 operator direction (2026-04-17) — "next session I want universal audio control at the top menu," then reclassified to parking lot same session.

**Problem:** Audio playback in the app is fragmented across several subsystems (POI narration via `NarrationMgr`, Oracle tile TTS, splash voiceover, voice clips on geofence triggers). There's no single user-facing control to mute / unmute / change volume across all of them. A user who wants silence has to find the right subsystem's toggle or lean on device volume.

**Proposed UX:**
- New top-bar toolbar icon next to the existing slim-toolbar icons (Home / Alerts / Grid / About) — a speaker icon with on / off state.
- Tap toggles global mute.
- Long-press opens a mini volume slider + per-source toggles (narration / Oracle / voice clips / splash) for finer control.
- State persists to SharedPreferences.

**Dependencies:**
- `AppBarMenuManager.kt` slim-toolbar wiring (already the pattern used for Weather / TileSource icons that S142 hid).
- Need to route the global mute state into every playback site: `NarrationMgr`, Oracle-tile TTS starter, voice-clip player, `SplashActivity.playVoiceover()`.
- Respect Android's `AudioManager.STREAM_MUSIC` volume — global mute should not override explicit device volume, just add an in-app mute layer.

**Scope estimate:** 1 session (toolbar icon + state holder + 4 playback-site wire-ups + device-verify).

**Status:** Parked. Not the S144 starter after all — operator moved it to the lot to prioritize freely.

---

### #46 — Splash screen stale-concept cleanup
**Origin:** S143 operator direction — "tuning I will need to do to the UI to remove stale concepts (Splash screen for instance)."

**Problem:** S142 retitled the splash (title → "Katrina's Mystic Visitors Guide", subtitle → "Historic Salem Tour App") but other splash surfaces may still carry stale brand/concept residue: layout text, progress hints, any leftover "tour-type chooser" or "witch-tour" copy that predated the V1 posture lock, stale ads/LLM hints, etc. Operator hasn't enumerated specifics — requires a walkthrough of `activity_splash.xml` + `SplashActivity.kt` + referenced strings.

**Investigation needed in S144:**
- Grep `activity_splash.xml` + `SplashActivity.kt` for every string it uses.
- Walk the live splash on device and catalog anything that reads wrong / stale.
- Candidate suspects: the "Wicked Salem" internal module name bleeding into any visible text, pre-V1-posture wording about tiers/ads/LLM, pre-offline-only hints about network.

**Scope:** E-M — likely string swaps + one or two flow tweaks, not a redesign. Budget 1–3 hours.

**Status:** Parked — promoted to Monday must-haves block.

---

### #47 — Opening menu reinforcement
**Origin:** S143 operator direction — "need to reinforce the opening menu."

**Problem:** The "opening menu" is the hub card reached after splash (the one with "Explore Salem" / "Take a Tour" + "The Salem Witch Trials" card + "Long-press the map to jump to any location" hint). Per S143 device-verify screenshot, it still says "Wicked Salem / Witch City Tour" as its banner (#40 covers that retitle but ONLY the banner). The operator wants this surface **reinforced** — which likely means: brand-forward visuals, clearer entry-point labels, maybe iconography that reads "Katrina / Mystic" rather than the current crystal-ball + map graphics.

**Investigation needed in S144:**
- Identify the activity/layout file (likely `activity_hub.xml` or similar; find via `grep "Explore Salem"` in `app-salem/src/main/res`).
- Current entries: "Explore Salem", "Take a Tour", "The Salem Witch Trials" (history / newspapers / people). Confirm these are the final V1 entry points — any missing (e.g., Witch Trials parent tile → child selector)?
- Operator to spec what "reinforcement" looks like: just text/icon refresh, or layout reflow?

**Scope:** M — 1 session.

**Status:** Parked — promoted to Monday must-haves block.

---

### #48 — Menu keys / grid dropdown reinforcement
**Origin:** S143 operator direction — "need to reinforce... the menu keys."

**Problem:** The "menu keys" are the grid dropdown tiles (POI / Utility / Find / Go To / Social / Chat / Profile / Legend / Tours / Events / Witch Trials). S142 hid row 1 (online-only: Transit / Webcams / Aircraft / Radar) leaving 11 tiles across 3 rows. Operator wants this grid **reinforced** — likely: brand-appropriate icons (Salem-witchy per the related #13 item), rewording where generic ("Utility" is vague), reordering by likely-use, and possibly dropping tiles that aren't V1-relevant.

**Overlap with #13:** This is effectively the implementation of #13 (top-menu strip redesign, Salem-witchy buttons). Could fold #48 into #13 and execute both as one session, OR keep #48 as the scoped content-rewording pass and #13 as the visual-icon redesign. **Operator to decide S144.**

**Investigation needed in S144:**
- Audit each tile's live function (Social / Chat / Profile / Legend — which are wired in V1? Any are stubs?).
- Propose rewording for "Utility" if kept.
- Icon direction: witchy silhouettes (broom, cauldron, crystal ball, tombstone, book) or clean modern with Creepster gold accents?

**Scope:** M — 1 session if icon-set is already sketched; 1-2 if operator wants generative art for each.

**Status:** Parked — promoted to Monday must-haves block. Pairs with #13.

---

### #49 — Find menu reinforcement
**Origin:** S143 operator direction — "need to reinforce... the find menu."

**Problem:** The Find modal (search box + 20 category tiles — Favorites, Food & Drink, Fuel & Charging, Transit, Civic & Gov, Parks & Rec, Shopping, Healthcare, Education, Lodging, Parking, Finance, Places of Worship, Historic Sites, Emergency Svc, Auto Services, Entertainment, Offices & Services, Witch & Occult Shops, Psychic & Tarot — per S143 screenshot) includes **online-only categories that V1 should have hidden** — "Fuel & Charging" and "Transit" are network-dependent. Also the tile design is generic-colored-rectangle rather than brand-forward.

**Investigation needed in S144:**
- Confirm which categories the offline DB actually has POIs for. Those 2 we know (FUEL_CHARGING, TRANSIT) — verify they have zero POIs in offline DB and hide the tiles.
- Audit category label wording for V1-appropriate Salem-voice (e.g., "Psychic & Tarot" already has Salem voice; "Civic & Gov" is generic).
- Propose tile icons/art that read consistent with the new brand.
- Confirm search UX behavior on offline DB (already rewritten S141 to use `SalemPoiDao`).
- Confirm PG-13 content rule is respected on any category imagery.

**Scope:** M — 1 session.

**Status:** Parked — promoted to Monday must-haves block.

---

### #50 — TTS tuning: pause only at sentence ends
**Origin:** S143 operator direction — "TTS tuning so we only pause at sentence ends, etc."

**Problem:** Current TTS narration (Piper + Android TTS paths used across `NarrationMgr`, Oracle tile reader, Witch Trials reader) chunks long text into playback segments. Pauses between segments land mid-phrase or at comma boundaries, producing an unnatural cadence. Operator wants pauses restricted to **sentence-final** punctuation (`.`, `!`, `?`, possibly `…` and `;` — to confirm) so the narration reads like a human storyteller rather than a chopped-up feed. The "etc." in the operator ask leaves room for related tuning: volume ducking on interruptions, fade-in/fade-out on chunk boundaries, silence-gap duration between sentences.

**Investigation needed in S144:**
- Find the TTS chunker. Likely candidates:
  - `NarrationMgr.kt` — primary narration pipeline (exists per memory `project_voice_clips.md`, S131 "People panel + TTS chunker")
  - Oracle tile WebView reader (HTML/WebView rendered article, click "▶ Speak" — separate TTS hook, visible in S143 screenshots)
  - Witch Trials article reader — same family as Oracle
- Identify the split regex. Current pattern is probably `\b[.,;:!?]\s+\b` or similar; needs to become sentence-boundary-only (`[.!?]+\s+` with lookaround for Mr./Mrs./Dr./abbreviations to avoid false splits).
- Test with known problematic content: 202 Oracle newspaper articles, primary source quotes (common abbreviation densities), multi-paragraph narration.
- Confirm pause duration between sentences — is it the current "chunk gap" or a configurable pause value? Operator may want this tunable.

**Scope:** E–M — 1 focused session. Risk: the chunker may serve multiple consumers with different requirements (Oracle reads formal journalism; POI narration reads conversational prose). May need a per-consumer regex config instead of one global.

**Status:** Parked — promoted to Monday must-haves block.

---

**Next action after S143:** S144 walkthrough of the parking lot, **driven by the Monday Must-Haves block**. Operator declared items #10, #11, #13, #27, #40, #43, #44, #45, #46, #47, #48, #49, #50 as committed for the Monday 2026-04-20 lawyer meeting. Parking-lot items #38, #39, #41, #42 + Clusters C/D/E/F/G/H/I defer. Busy weekend ahead.
