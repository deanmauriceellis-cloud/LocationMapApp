# NEXT STEPS — Tech-Debt Cleanup (analysis handoff, 2026-05-27)

> Working analysis doc. Authored S302 (2026-05-26/27). Purpose: let the operator
> analyze + pressure-test the cleanup plan before any code is written. The full
> plan-of-record is `docs/plans/tech-debt-cleanup-2026-05.md`; the survey that
> produced it is in `docs/session-logs/session-302-2026-05-26.md`. This file is the
> short, decision-focused version + the open questions worth chewing on.

---

## TL;DR

S302 surveyed the whole codebase. It's **clean for 298 sessions** — no hardcoded secrets,
all SQL parameterized, deps current, only 2 real Kotlin TODOs. Debt is concentrated in a
few known places. Operator chose an **aggressive** cleanup (including the 18.8k-LOC
SalemMainActivity decomposition) **with a guaranteed fall-back** (tag each phase on master;
reset to last-good tag to bail). Plan is 7 phases, each shippable + tagged + interruptible
for OMEN-025.

---

## The debt, ranked

| # | Debt | Size | Pre-ship risk? | Disposition |
|---|------|------|----------------|-------------|
| 1 | `SalemMainActivity` god object | 18,880 LOC / 20 partials, ~122 shared mutable fields, 17 Jobs, 2 mutexes | structural, not a ship blocker | Phase 4 (decompose into existing VMs) |
| 2 | Dead `hero/`/`custom_icon_asset` system | ~800 dangling PG rows + orphan dir | none | Phase 2 (code/asset cleanup; column-drop deferred) |
| 3 | Publish-chain fragility | 6 scripts, order uncoded, .env dup 5×, 3 unwrapped DROP+CREATE | **YES — release-bake correctness** | **Phase 0 (the one pre-ship must-do)** |
| 4 | Hardcoded `10.0.0.229:4300` in 18+ core repos | — | none (all gated OFF in V1) | post-V1 (dev/CI friction only) |
| 5 | ~3,300 LOC parked network features in the Activity | Social/Aircraft/Transit/Weather/Radar/Metar/Webcam | none (flag-gated, R8-stripped) | Phase 3 (isolate into `parked/`, NOT delete) |
| 6 | 42 `!!` + 8 empty `catch {}` | clustered in date-parse + squawk | low | Phase 5 (non-parked paths only) |
| 7 | web god-components + paused SI/Oracle wiring | AdminMap 2688 / TourTree 1299 | none (operator-only) | Phase 6 (optional) |

---

## Operator decisions already locked (S302 dialog)

1. **Aggressive**, monolith included, but must be able to fall back.
2. **Feature ViewModels + Managers**; Activity → thin observer; 1 extraction = 1 revertable commit.
3. **Isolate** parked network code into `parked/`; do **not** delete (parked-not-killed rule).
4. **Verify:** compile + JVM unit per commit; on-device Pixel 8 smoke per phase boundary.
5. **Fallback:** stay on master, tag `cleanup-phaseN-done` each phase; `git reset --hard` to bail.

---

## Phase sequence (full detail in `docs/plans/tech-debt-cleanup-2026-05.md`)

- **P0** publish orchestrator + shared `.env` + txn-wrap + footgun unit tests — *only pre-ship-critical item*
- **P1** `JobCoordinator` + `MarkerController` (infra; de-risks the rest)
- **P2** retire dead hero/icon (pure subtraction; Room column-drop deferred post-V1)
- **P3** isolate parked network features into `parked/`
- **P4** migrate active-feature state into the **already-existing** VMs (Events→Detour→Directions→Populate→Geofences→Find→Narration→Tour, smallest-first, one tag each)
- **P5** `!!`/empty-catch hardening (non-parked only)
- **P6** web god-components (optional)

---

## Key insight that lowers the risk

**The target ViewModels already exist.** 10 VMs (~2,484 LOC: Tour/Find/Main/Geofence/Social/
Weather/Aircraft/Transit/Events/WitchTrials) + ~12 Managers. The monolith *bypasses* them and
holds state directly. So Phase 4 is **migration into existing patterns**, not greenfield — and
each domain is independently tagged, so one domain can be reset without losing the others.

**The test net is thin** (2 app-salem unit tests). Per-commit "tests green" is a weak guarantee
alone — real safety = per-phase on-device smoke + characterization tests written *before* moving
each domain's state.

---

## OPEN QUESTIONS to analyze before starting (for the operator)

1. **Start point:** Phase 0 first (safe, ship-relevant, zero app-code risk), or jump to Phase 1 infra?
2. **OMEN-025 interleave:** do we pause this effort the moment the 3 creds land, or finish the current phase first? (Plan assumes pause-at-next-tag.)
3. **Phase 4 stopping point:** is decomposing *all 8* active domains the goal, or is "shrink the Activity below ~8k LOC and stop" acceptable? (Diminishing returns past Find/Narration/Tour.)
4. **Characterization tests:** acceptable to spend ~1 commit/domain writing behavior-capture tests before each Phase-4 move, or too slow? (They're the main safety net given the thin existing coverage.)
5. **`custom_icon_asset` column-drop:** confirm OK to defer the Room v23→v24 migration to post-V1 (schema-bump risk > benefit now), keeping only dead-code/asset cleanup in Phase 2.
6. **Phase 6 (web):** in or out for V1? It never ships to users.
7. **Aug 1 reality check:** with OMEN-025 still ship-blocking + gated, is there actually calendar room for P1–P5 before Aug 1, or is the realistic plan "P0 now, the rest as post-cred / post-ship"?

---

## Pointers

- Full plan: `docs/plans/tech-debt-cleanup-2026-05.md`
- Survey + dialog record: `docs/session-logs/session-302-2026-05-26.md`
- Ship-blocker context (unaffected by this): OMEN-025 in `STATE.md` + `AndroidSecurity.md`
