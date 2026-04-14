# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-13 — Session 123 (POI dedup, narration resync, Phase 9R Historical Tour Mode spec)

---

## TOP PRIORITY — Next Session (S124)

**Phase 9U: Unified POI Table — Session 124 (Legacy Cleanup, deferred from S122/S123)**

S122 was tooling (AudioCraft install). S123 pivoted to POI dedup + narration resync + Phase 9R requirements spec. The original cleanup scope is now S124.

**Session 124 scope:**
1. Drop legacy tables (narration_points, tour_pois, salem_businesses) from Room DB
2. Remove NarrationPoint entity/DAO and legacy repository methods
3. Heading-up rotation smoothness fix (deferred from S115)
4. End-to-end device verification with new narrations
5. **NEW (small UI):** surface `historical_note` field unconditionally in admin POI editor (General tab) so operator can hand-author samples while waiting for SalemIntelligence Tasks 1-5 to ship

**Post-S123 key facts:**
- PG: **2,080 active POIs** (down from 2,190 by S123 dedup; 110 soft-deleted, all tagged for pre-Play-Store hard-delete)
- Narration model: short_narration (1,417 POIs) + long_narration (1,555 POIs). No multipass.
- is_narrated=true on 1,543 POIs (up from 824 pre-sync)
- 572 unlinked POIs still missing intel_entity_id; ~510 BCS-linked POIs still pending narration generation in SalemIntelligence Phase 2

**Phase 9R Historical Tour Mode requirements doc:** `docs/salem-intelligence-historical-tour-request.md` (5 tasks for SalemIntelligence; LMA-side work blocked on those + Phase 9Q building bridge)

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A** Backend Foundation | **COMPLETE** (S98-S101) | Schema, importer, admin auth, write endpoints, duplicates, per-mode visibility |
| **9P.B** Admin UI | **6/8 done** | 9P.6-9P.10b complete. Pending: 9P.11 (demoted), 9P.13 (folded into 9U). 9P.10a blocked on 9Q. |
| **9U** Unified POI Table | **S123 DONE (dedup + narration resync)** | 1 session left (S124). Legacy table drop, NarrationPoint removal, heading-up fix. |
| **9Q** Salem Domain Content Bridge | not started | building→POI translation, 425 buildings, 202 newspapers. Simplified by 9U (no `poi_kind` column). |
| **9R** Historic Tour Mode | not started | opt-in chapter-based 1692 tour |
| **10** Production readiness | DEFERRED behind 9U+9Q+9R | Firebase, photos, DB hardening, emulator verification |
| **11** Branding, ASO, Play Store | target 2026-09-01 | Salem 400+ launch window |
| **Cross-project** SalemIntelligence | **Phase 1 KB LIVE** at :8089 | 1,724 BCS POIs, 116K entities, 238 buildings, 5.67M relations. Phase 2 (narration gen) pending operator gate. |

**Sessions completed:** 123. Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

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
7. **Cross-project: stale intel_entity_id UUIDs in salem_pois (S125 finding)** — of the 388 linked silent POIs the S125 narration-fill script processed, **194 returned "Entity not found" from SI** (`/api/intel/entity/{uuid}/narration` → 404 → also POST generate → "Entity not found"). These BCS UUIDs are stale — SI has rotated its entity registry at some point and LMA's linkage is from an older version. Not blocking (S125 falls back to local stubs) but the POIs miss out on SI's richer prose. Needs cross-project re-linking work (likely name-fuzzy-match + BCS current-UUID export from SI → update salem_pois.intel_entity_id). Full numbers in commit `f4626bb`. Surface to OMEN for coordination.

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
