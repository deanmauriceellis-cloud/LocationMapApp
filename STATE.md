# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-15 — Session 131 (Phase 9X.5 People of Salem 1692 panel + TTS chunking shipped)

---

## TOP PRIORITY — Next Session (S132)

**Phase 9X.6 — pencil-sketch portraits for the 49 principal figures via local Stable Diffusion.**

Master plan section: Phase 9X.

Step-by-step for S132 (per master plan):
1. Fixed prompt template for uniform pencil-sketch style (no free-likeness mixing). Generate 49 portraits at 512×512 via Forge `:7860` — budget 5-10 MB total asset weight.
2. Populate `salem_witch_trials_npc_bios.portrait_asset` with the bundled filename (`portraits/{npc_id}.jpg`).
3. Wire the portrait into `WitchTrialsBioDetailDialog` (hero image above the name, round or square-with-corner-radius per design taste).
4. Optionally show a small circular thumbnail on `buildBioRow` — verify that doesn't blow out the row height.
5. Manual review pass for any quality drift; re-prompt the worst offenders with seed adjustments.
6. Verify on Lenovo HNY0CY0W.
7. Commit + push.

**GPU gate:** Forge on `:7860` contends with SalemIntelligence + Ollama for the RTX 3090. Confirm workload before starting the generation batch (49 × ~15s/portrait at 512² ≈ 12-15 min, can run with SI up if VRAM headroom holds).

After S132, Phase 9X will be 6/8 done. S133 = cross-linking + admin integration + Today-in-1692 card. S134 = polish + field test + master plan/STATE/OMEN report updates.

**Post-S131 key facts:**
- **Phase 9X.5 shipped end-to-end.** 49 Tier-1/2 figures imported from `~/Development/Salem/data/json/npcs/` (filter: `tier == 1 || tier == 2`). Bios assembled from narrative subfields (life_before_1692 / role_in_crisis / emotional_arc / how_they_see_world / emotional_landscape / sermon_influence / appearance) into `## Header\n\nbody` markdown — 10K-21K chars per figure. Imported via `cache-proxy/scripts/import-witch-trials-npc-bios.js`, baked via `bundle-witch-trials-npc-bios-into-db.js`.
- **People browser** (`WitchTrialsPeopleBrowserDialog`) — horizontal filter chip row (All / Judges / Accusers / Accused / Clergy / Officials / Others), vertical rows sorted by role bucket → tier → name. Each row: 17sp gold serif name, 13sp italic role descriptor, footer with colored role chip + `born – died` span (or "b. YYYY" / "d. YYYY" fallback). Border tinted per role (scarlet / gold / gray / purple / silver / slate).
- **Bio detail** (`WitchTrialsBioDetailDialog`) — role eyebrow tinted to role color, 24sp bold gold name, `YYYY – YYYY · age N in 1692` dates, italic short-bio, optional "Outcome: …" line, Speak pill, `renderBioBody()` that splits `\n\n` blocks and renders `## ` lines as gold bold subheadings.
- **Role bucketing is heuristic** — `roleTypeOf(bio)` text-matches `role + faction.lowercase()` with precedence JUDGE → CLERGY → ACCUSER → ACCUSED → OFFICIAL → OTHER. If any figure bucket looks wrong in deep-dive testing, tweak the regex order or add explicit id overrides.
- **TTS chunking (`chunkForTts`, 3500-char cap)** — Android TTS caps per-utterance speech at ~4000 chars. Long bios (14K for Stoughton, 21K for ann_putnam_jr) now split into sentence-bounded chunks that enqueue sequentially under one `witchtrials_bio_` tag prefix. Verified: 5 chunks for Stoughton (3454/3500/3253/3127/1018), `TTS start` with no error, Stop drains all queued segments.
- **PG now has**: `salem_witch_trials_articles` (16 rows, S128), `salem_witch_trials_newspapers` (202 rows + headlines, S130), **`salem_witch_trials_npc_bios` (49 rows, S131)**. Room schema still v8 (no bump needed — npc_bios table structure unchanged since v7).
- **Asset DB** now carries all three witch-trials tables populated: 16 articles + 202 newspapers + 49 bios. `salem_content.db` size bumped modestly by the ~700KB of bio text.
- **Portrait generation (Phase 9X.6)** is the last content-generation step. After that, 9X.7 = cross-linking + admin integration, 9X.8 = polish + field test.
- **Room `@Insert` silent-drop** from S129 still latent — S131 sidestepped it once more via `better-sqlite3` bake. Worth root-causing before Play Store.
- **OMEN-004 still deliberately slipped** (deadline 2026-04-30, 15 days out).
- **PG: 1,868 active POIs** unchanged. **Narration coverage: 100%**. **5 tours** with OSRM polylines.

**Phase 9X status:** 5 / 8 sessions done. 3 sessions of feature work ahead (S132-S134). Salem 400+ launch deadline 2026-09-01 still tracks.

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A** Backend Foundation | **COMPLETE** (S98-S101) | Schema, importer, admin auth, write endpoints, duplicates, per-mode visibility |
| **9P.B** Admin UI | **6/8 done** | 9P.6-9P.10b complete. Pending: 9P.11 (demoted), 9P.13 (folded into 9U). 9P.10a blocked on 9Q. |
| **9U** Unified POI Table | **DONE (S125-S126)** | Dedup, narration resync, NarrationPoint+SalemBusiness entity removal, TourPoi rerouted to salem_pois, legacy PG schema dropped, inventory PDF tool migrated. |
| **9X** Salem Witch Trials Feature | **IN PROGRESS — 5/8 sessions done (S127, S128, S129, S130, S131)** | **TOP PRIORITY (S132-S134).** S127 foundation. S128 history-article LLM gen (16/16). S129 History 4×4 tile UI. S130 Oracle Newspaper panel (202 articles + 202 AI tabloid headlines). S131 People panel (49 Tier-1/2 figures, role-filtered browser + bio detail + TTS chunker). Phase 9X.6 (S132) adds pencil-sketch portraits for all 49. |
| **9Q** Salem Domain Content Bridge | not started — queued behind 9X | building→POI translation, 425 buildings, 202 newspapers. Simplified by 9U (no `poi_kind` column). |
| **9R** Historic Tour Mode | not started — queued behind 9X | opt-in chapter-based 1692 tour |
| **10** Production readiness | DEFERRED behind 9X+9Q+9R | Firebase, photos, DB hardening, emulator verification |
| **11** Branding, ASO, Play Store | target 2026-09-01 | Salem 400+ launch window |
| **Cross-project** SalemIntelligence | **Phase 1 KB LIVE** at :8089 | 1,724 BCS POIs, 116K entities, 238 buildings, 5.67M relations. Phase 2 (narration gen) pending operator gate. |

**Sessions completed:** 131. Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

---

## Carry-forward Items (NOT blocking Phase 9U)

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

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — **DRAFTED S114 at `docs/PRIVACY-POLICY.md`**. Pending OMEN review. Ball is in OMEN's court.
2. **NOTE-L015 — `~/Development/SalemCommercial/` cutover never executed.** 12 sessions running. Needs OMEN to execute or retract.
3. **OMEN-002 history rotation** — operator action only.
4. **OMEN-004 — first real Kotlin unit test** (Phase 1 deadline 2026-04-30, 18 days out).
5. **Phase 9T.9 walk simulator end-to-end verification** still TODO.
6. **Cross-project: SalemIntelligence** — Phase 1 KB live. Phase 2 (narration gen) pending operator gate. Hero regen deferred behind Phase 2.
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

- **Current PG:** **2,080 active POIs** in `salem_pois` (1,543 narrated, 1,555 with long_narration, 1,417 with short_narration). 110 soft-deleted by S123 dedup, pending pre-Play-Store hard-delete. Multipass columns dropped.
- **Room DB:** stale until next bundle export (will reflect 2,080 once published).
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
