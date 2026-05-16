# STATE.md carry-forwards removed 2026-05-16 (S271)

Per plan `~/.claude/plans/steady-sprouting-toast.md` Phase 1.6 — moved out of `STATE.md` because each item had been carry-forward for 70+ sessions with no movement, OR was operator-side and already covered elsewhere, OR was de-facto abandoned. STATE.md is a snapshot, not a changelog; multi-session-old items belong in archive where they remain discoverable via `git log` and grep but don't bleed context every session.

---

## Removed from "Other carry-forwards" (was STATE.md line 123-124)

- **Rebuild signed AAB + 30-min Lenovo smoke** (was tagged "first since S180", 90 sessions stale). Multiple AABs have shipped since S180 (e.g. S256 ship-cliff close, every asset-pack flow S268+). The "first since" framing is rotted; if a fresh AAB smoke is needed, the operator runs the standard build + bundletool install workflow now in CLAUDE.md.
- **Operator field-walks (S204): GPS-OBS heartbeat + Witch Trials bios + airplane-mode drive regression.** Operator does field walks continuously per `feedback_lenovo_over_emulator.md`. The 86-session-stale S204-specific checklist is dead.
- **GPS-OBS heartbeat investigation (S185).** Lenovo TB305FU tour-observer staleness. 86 sessions with no movement. Either the bug self-resolved or operator stopped seeing it post-S264 GPS work. If it reappears in a future field walk, re-open then with fresh logs.
- **Re-author 5 deleted Kotlin tours in PG via web admin (S185, operator).** 86 sessions stale, operator-side. The 4 tours that exist (per S224) are functioning; the 5 deleted ones are not blocking V1 ship.
- **McIntire content hand-author drain (S200).** 70 sessions stale, operator-side content task. Not a code blocker; can be picked up any time pre- or post-V1.

## Removed from "Older backlog (S159–S200, TRIAGED S203)" (was STATE.md line 145)

### V1 (formerly open) — moved out
- ~~McIntire content hand-author (S200)~~ — see above
- ~~re-author 5 deleted Kotlin tours in PG (S185, operator)~~ — see above
- ~~GPS + V1-offline drive regression (S161, operator)~~ — 90 sessions; operator does drive regressions routinely now. Generic carry-forward without a specific bug attached is dead.

### V2 — moved out
- ~~osmdroid → WickedMapView migration~~ — de-facto abandoned for V1; current state has 72 `import org.osmdroid` lines vs 37 WickedMap references. The custom engine ships background polygon animation only; osmdroid stays as the basemap surface per operator memory `feedback_basemap_priority_over_animation.md`. If a real WickedMap-only basemap is ever pursued it will be a separate explicit plan, not a multi-year carry-forward.

## Removed from "Other carry-forwards" (was STATE.md line 121, half-stale)

- ~~"CLAUDE.md 'Pinned' Room version stale (v11 → v19)"~~ — resolved S271 (the entire Pinned block was deleted in this session). The other half of that line, "PoiDetailSheet/Find cleanup (S224): clearXXX/stopXXX no-ops", stays in STATE.md.

---

## Also moved out — S245+ aged backlog (was STATE.md lines 109-113)

These were 26-55 sessions stale by S271. Listed here for discoverability; none are V1 ship-blocking.

- **Rendering:** RollingGrassOverlay green-patches on Bluestacks; sprite field-walk near bridge POI; field-validate S238 perspective at z19/z20 + tilt 30/36/42/48°; first-frame layout race in `applyMapExtension` (post-extension `mv.requestLayout()`); cold-start tile-decode spike (Choreographer 1134ms); optional overlay culling / batched Overlay perf candidates.
- **Content:** lint review of 51-POI newly-renderable set; Sweet Boba regression; Layers menu category gap (FOOD_DRINK/SHOPPING toggles don't gate narration overlay); tilt-mode odds-and-ends (S239); splash variants (32 TODO in `docs/SPLASH-ANNOUNCEMENTS-V1.md`); POI/path alignment review of 1962-photo session; Find menu tile + hero review.
- **Tools:** TTS-settings deep-link reliability; SheetJS CVE audit; cascading subcategory pick-list; field-edit UPDATE/CREATE loops; Rapid Recon fresh-walk 1 Hz tick + true-north; auto-fire camera field-test.

---

Anything genuinely needing rescue from this list can be pulled back via `git log -p` or by searching session logs S159-S270.
