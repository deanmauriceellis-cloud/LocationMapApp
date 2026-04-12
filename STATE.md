# LocationMapApp v1.5 — Project State

> **Snapshot only.** This file is the current-state pointer. Session-by-session history lives in `SESSION-LOG.md` (last 10 sessions) and `SESSION-LOG-ARCHIVE.md` (older). Live conversation logs are in `docs/session-logs/`. Per-file decisions and code changes are in those logs and in `git log`. Do not let this file grow into a changelog — it should stay under 200 lines.

**Last updated:** 2026-04-12 — Session 117 (Phase 9U Steps 9U.1-9U.6 shipped — unified salem_pois table, three-table merge, admin tool rewrite)

---

## TOP PRIORITY — Next Session (S117)

**Phase 9U: Unified POI Table & SalemIntelligence Import — Session 118 (Steps 9U.7-9U.11)**

S117 completed Steps 9U.1-9U.6: unified `salem_pois` table live with 976 rows (817 narration + 133 businesses + 26 tour-only), 14 categories, 71 columns, 6 FK constraints repointed, admin tool fully rewritten. Old tables renamed to `_legacy`.

**Session 118 scope:** Pull SalemIntelligence BCS snapshot (1,724 entities), build category mapping (BCS→LMA), add 4 new subcategories, import ~900 new entities, set default visibility.

**Session 119:** Admin tool tree rework for unified table, publish loop (PG→Room DB), Room migration, Android DAO updates.

**Session 120:** End-to-end verification, drop legacy tables, heading-up rotation smoothness fix (deferred from S115/S116).

**Key decisions made in S116:**
- V1 commercial release is fully offline — no hosted LLM/KB. SalemIntelligence is build-time data source only.
- No new top-level categories needed. All 19 BCS types map to existing 22 LMA categories. 4 new subcategories: `tour_operator`, `antiques`, `cannabis`, `souvenir`.
- `service_professional` (429 BCS entities) decomposes into AUTO_SERVICES (83), FINANCE (66), OFFICES (56+132 fallback), SHOPPING (43), and smaller buckets via keyword heuristics.
- Tourist-relevant POIs (~750) `default_visible=true`, utility POIs (~1,850) `default_visible=false` but available via layer toggles.
- Supersedes the old taxonomy arc (Steps 2-4 of `docs/poi-taxonomy-plan.md`).

---

## Phase Status

| Phase | Status | Notes |
|---|---|---|
| 1-9 + 9A+ + 9T (8/9) | COMPLETE | Core dev, offline foundation, ambient narration |
| **9P.A** Backend Foundation | **COMPLETE** (S98-S101) | Schema, importer, admin auth, write endpoints, duplicates, per-mode visibility |
| **9P.B** Admin UI | **6/8 done** | 9P.6-9P.10b complete. Pending: 9P.11 (demoted), 9P.13 (folded into 9U). 9P.10a blocked on 9Q. |
| **9U** Unified POI Table | **S117 DONE (schema + merge + admin rewrite)** | 3 sessions left (S118-S120). BCS import + publish loop + Room migration. |
| **9Q** Salem Domain Content Bridge | not started | building→POI translation, 425 buildings, 202 newspapers. Simplified by 9U (no `poi_kind` column). |
| **9R** Historic Tour Mode | not started | opt-in chapter-based 1692 tour |
| **10** Production readiness | DEFERRED behind 9U+9Q+9R | Firebase, photos, DB hardening, emulator verification |
| **11** Branding, ASO, Play Store | target 2026-09-01 | Salem 400+ launch window |
| **Cross-project** SalemIntelligence | **Phase 1 KB LIVE** at :8089 | 1,724 BCS POIs, 116K entities, 238 buildings, 5.67M relations. Phase 2 (narration gen) pending operator gate. |

**Sessions completed:** 117. Salem 400+ quadricentennial is 2026 — app must be in Play Store by Sept to capture October's 1M+ visitors.

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

---

## OMEN Open Items

1. **NOTE-L014 / OMEN-008 — Privacy Policy** — **DRAFTED S114 at `docs/PRIVACY-POLICY.md`**. Pending OMEN review. Ball is in OMEN's court.
2. **NOTE-L015 — `~/Development/SalemCommercial/` cutover never executed.** 12 sessions running. Needs OMEN to execute or retract.
3. **OMEN-002 history rotation** — operator action only.
4. **OMEN-004 — first real Kotlin unit test** (Phase 1 deadline 2026-04-30, 18 days out).
5. **Phase 9T.9 walk simulator end-to-end verification** still TODO.
6. **Cross-project: SalemIntelligence** — Phase 1 KB live. Phase 2 (narration gen) pending operator gate. Hero regen deferred behind Phase 2.

---

## Salem Oracle & SalemIntelligence Integration

**Two distinct services now available:**
- **Salem Oracle** at `:8088` — 1692 historical corpus (63 POIs, NPCs, trial events, newspapers). Used by admin tool's "Generate with AI" feature (Phase 9P.10b).
- **SalemIntelligence** at `:8089` — modern business KB (1,724 BCS entities, 116K total entities, 238 historic buildings, 5.67M relations). Build-time data source for Phase 9U POI import. Integration guide: `~/Development/SalemIntelligence/docs/lma-integration-guide.md`.

---

## Expanded vision (updated S116)

- **Phase 9U** — Unified POI Table + BCS Import. 4 sessions (S117-S120). Merge 3 tables, import ~900 new BCS entities, ~2,600 total unified POIs. **NEXT.**
- **Phase 9P.C** — Publish loop. Folded into 9U Session 119 (Step 9U.13).
- **Phase 9Q** — Salem Domain Content Bridge. 2-3 sessions. Simplified by 9U unified table.
- **Phase 9R** — Historic Tour Mode. 4-6 sessions.

**Total runway:** ~10-13 sessions for 9U+9Q+9R. Launch deadline Sept 1, 2026.

---

## POI Inventory

- **Current PG:** **976 unified POIs** in `salem_pois` (817 narrated + 133 business-only + 26 tour-only). Old 3-table split renamed to `_legacy`.
- **After Phase 9U S118:** ~2,600 unified POIs (976 current + ~900 new from BCS import + enrichment)
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
