# Pixel 8 / Portrait Orientation Support — V1 Plan

**Status:** Draft, S263 (2026-05-13). No code shipped yet.
**Owner:** Operator + Claude pair.
**Ship target:** V1 (2026-08-01). ~80 days from draft.
**Trigger:** S263 Pixel 8 install surfaced that the app has zero portrait resources and was developed entirely against the Lenovo TB305FU tablet (natural orientation = landscape). Operator decision: full multi-form-factor V1 — both phones AND tablets, both portrait AND landscape. Lock-landscape shortcut declined.

---

## 1. Current state (from S263 Explore inventory)

| Dimension | State |
|---|---|
| `app-salem/src/main/res/layout/` | 15 XML layouts, no `layout-port/` or `layout-land/` qualifiers |
| `values-*` qualifiers | No `values-sw600dp/`, `values-land/`, etc. — single resource set |
| `SalemMainActivity` orientation lock | None in manifest; `configChanges` handles rotation in-place |
| `setRequestedOrientation()` in code | None found |
| Dialog surfaces | 43 unique `showXxxDialog()` entry points across `SalemMainActivity*.kt` companions |
| Fullscreen dialogs | 11+ using `Theme_Black_NoTitleBar_Fullscreen` — will clip / stretch on phone portrait |
| Custom views | `ProximityDock`, `PoiDetailSheet` (BottomSheetDialogFragment), `TiltContainer` — no orientation handling |
| Splash images | 12 `splash_katrina_*.webp`, assumed landscape aspect ratio |
| `ReconCaptureActivity` | Already locked `portrait` (works on phone) |
| Pixel 8 install (S263) | Debug AAB + asset pack delivered; runs landscape because Pixel system was rotation-locked at ROTATION_90 |

**Severity by surface (Explore agent triage):**

| Surface | Risk | Notes |
|---|---|---|
| `activity_main.xml` FAB cluster (72dp margin from bottom-right) | HIGH | Will overlap dock / action buttons / navbar insets on Pixel 8 portrait |
| `activity_main.xml` zoom slider (48dp × 280dp at end\|center_vertical) | HIGH | Fixed dimensions assume right-edge space |
| `activity_main.xml` bottom-left debug button stack (168dp margin) | HIGH | Will collide with Pixel 8 navbar / IME insets |
| `toolbar_two_row.xml` (40dp tall, horizontal icon row) | HIGH | Icons will wrap awkwardly on narrow widths |
| 11+ fullscreen dialogs (`Theme_Black_NoTitleBar_Fullscreen`) | HIGH | No portrait safe-padding |
| `grid_dropdown_panel.xml` (2×4 grid PopupWindow) | MEDIUM | Grid assumes wide container |
| `narration_bottom_sheet.xml` (200dp maxHeight ScrollView) | MEDIUM | Cuts narration on tall phones |
| `poi_detail_sheet.xml` hero (160dp default, runtime 20% screen height) | LOW | Runtime override handles it; verify |
| Splash images | UNKNOWN | Need to inspect aspect ratio of 12 WebP files |

---

## 2. Goal + scope

**Goal:** WickedSalemWitchCityTour V1 ships supporting **all four cells**:

|                      | Phone (e.g. Pixel 8) | Tablet (e.g. Lenovo TB305FU) |
|----------------------|----------------------|------------------------------|
| **Portrait**         | YES                  | YES                          |
| **Landscape**        | YES                  | YES (current state)          |

**In scope:**
- Portrait layout variants for every UI surface that breaks visually.
- Responsive theming for fullscreen dialogs (no edge clipping on phone portrait).
- Phone-vs-tablet resource qualifiers where the same layout doesn't work for both form factors.
- Splash variants for portrait aspect ratio.
- Window insets handling (Pixel navbar + IME) — particularly bottom-edge floating UI.
- androidTest coverage that exercises both orientations.

**Out of scope (V1):**
- Foldable / inner-display rotation handling.
- 7" mini-tablet specific layouts (Lenovo TB305FU is 10.6"; Pixel 8 is 6.2"; covering those two endpoints is V1 ship).
- Compose migration (still raw View/XML).
- iOS port (separate decision per S201).

---

## 3. Decisions — LOCKED (S263)

All five decisions confirmed by operator in S263 before plan execution:

| # | Decision | Resolution |
|---|---|---|
| 1 | Tablet portrait | **Required for V1.** Operator stance: *"We need to support ALL android devices in both orientations — I will probably need to get a few more to test with but we start with these 2."* `values-sw600dp-port/` qualifiers + Lenovo-vertical regression pass are in scope. |
| 2 | Phone landscape | **Required for V1.** Pixel 8 supports both orientations. Closest to current Lenovo UI, cheapest of the four cells. |
| 3 | Splash assets | **Letterbox existing landscape WebPs.** `scaleType="centerInside"` + tinted background fill. Zero new assets — respects [[feedback-hero-revisit-later]] (splash regen is the later revisit's job). |
| 4 | Dialog audit depth | **Triage-only.** Walk Pixel 8 portrait once, fix only dialogs that visibly clip. Operator confirms portrait already looks acceptable for most surfaces. Cuts Phase C from ~3 sessions to ~1. |
| 5 | Rotation posture | **Keep configChanges, add `onConfigurationChanged`.** Activity does NOT recreate on rotation. Preserves in-flight state (narration queue, tour progress, walk-sim, map camera). Re-bind dynamic UI on rotation event. |

---

## 4. Phased plan

Sessions are rough; each phase is independently parallelizable with content work (Form TX / authoring / etc.).

### Phase A — Pixel 8 visual smoke test + severity matrix (1 session)

Operator-driven on the installed debug build. Walk every UI surface (every menu, every dialog, every sheet, every screen state) in BOTH landscape and portrait on the Pixel 8, capturing screenshots into `docs/pixel8-portrait-survey/<surface>-<orient>.png`. Output: a CSV/markdown matrix annotating each surface as `OK / minor / major / blocker` per orientation. This drives Phase B-D scope.

**Why first:** the Explore inventory is structural. We need the visual reality before committing to layout variants. Some screens may render acceptably in portrait already (the OS's default behavior is often "good enough").

**Deliverable:** `docs/pixel8-portrait-survey/MATRIX.md` + screenshots.

### Phase B — Core activity layouts (~3 sessions)

Build `app-salem/src/main/res/layout-port/` variants for:
- `activity_main.xml` — reflow FAB cluster (bottom-end → maybe a column on the right edge), zoom slider (right edge → maybe bottom edge or hidden behind a toggle), debug button stack (bottom-left → drawer).
- `toolbar_two_row.xml` — overflow icons into a "more" menu on narrow widths, or restructure as a vertical drawer.
- `proximity_dock.xml` — verify bottom-edge dock works at phone width (it should).
- `narration_bottom_sheet.xml` — replace `200dp maxHeight` with `wrap_content` or peek-height fraction.
- `grid_dropdown_panel.xml` — let 2×4 reflow to 4×2 in portrait.

Wire `onConfigurationChanged` in `SalemMainActivity` to re-inflate or re-bind the affected surfaces on rotation.

**Deliverable:** Pixel 8 portrait usable for primary navigation + map view + opening a POI.

### Phase C — Dialog triage (~1 session)

Per decision #4: triage-only, not a full 43-surface audit. From the Phase A matrix, take the dialogs flagged `major` / `blocker` in portrait and fix them. Most candidates from the 11 fullscreen dialogs (TourSelection / Welcome / TourCompletion / ZoneDetail / TfrDetail / WebcamPreview / HistoricSitesBrowser / WitchTrialsBioDetail / EventsDialog / WeatherDialog / WitchTrialsNewspaperDetail) — but only fix what visually breaks on Pixel 8 portrait. Per-dialog fix:
1. If content is `ScrollView`/`RecyclerView` with safe padding, mark OK and move on.
2. If horizontal LinearLayout assumptions, build `layout-port/` variant or refactor to ConstraintLayout that flows.
3. If hero image is wide-aspect, scale-fit instead of scale-crop.

**Deliverable:** Every Phase-A-flagged dialog opens without clipping on Pixel 8 portrait.

### Phase D — Programmatic UI + custom views (~2 sessions)

- `PoiDetailSheet.kt:160` — verify the 20% hero-height calc looks right in portrait (160-180dp); adjust if needed.
- `ProximityDock.kt` — confirm child layout flows; cap dock icon count if width forces clipping (or scroll horizontally).
- `TiltContainer.kt` — verify 3D matrix-tilt math is unaffected by portrait (it shouldn't be, but tilt-mode in portrait at 60° is new territory).
- `DeviceOrientationTracker.kt:51,70,259` — `windowManager.defaultDisplay.rotation` reads need to be re-checked; reported rotation differs by natural orientation.
- `WickedMap` engine — if any code in `app-salem/src/main/java/.../wickedmap/` assumes landscape width > height, fix.

**Deliverable:** No code path silently breaks under portrait.

### Phase E — Splash letterbox (~1 session)

Per decision #3: letterbox path only — no asset regen.
- `activity_splash.xml`: switch `scaleType` to `centerInside`, add a tinted background fill (probably a deep purple matching Witchy theme) so portrait-rotated landscape WebPs sit centered with bands above/below.
- Verify all 12 `splash_katrina_*.webp` images letterbox acceptably; if any single one looks broken, swap to a less-cropped variant rather than regenerating.
- `SplashActivity.kt` SPLASH_SAFETY_CAP timing (15s) re-validated on Pixel 8 — fast NVMe vs Lenovo eMMC may change splash duration.

**Deliverable:** Splash renders cleanly in both orientations on both devices via letterboxing only (no new assets).

### Phase F — Tablet portrait + cross-device validation (~2 sessions)

Per decision #1: tablet portrait is required.
- Add `values-sw600dp/` + `layout-sw600dp-port/` qualifiers where the phone-portrait layout needs more breathing room on a tablet vertical.
- Full Lenovo walk in BOTH orientations.
- Full Pixel 8 walk in BOTH orientations.
- New `androidTest` that exercises orientation change mid-tour (rotates the device while a narration is playing; asserts state preserved through the configChanges path).

**Deliverable:** All four form-factor × orientation cells pass a manual walk + automated test.

### Total estimate (post-decisions)

| Phase | Sessions | Cumulative |
|---|---|---|
| A — Smoke test + matrix | 1 | 1 |
| B — Core layouts | 3 | 4 |
| C — Dialog triage | 1 | 5 |
| D — Programmatic UI | 2 | 7 |
| E — Splash letterbox | 1 | 8 |
| F — Tablet + validation | 2 | 10 |
| **TOTAL** | **~10** | — |

Decisions #3 (letterbox) and #4 (triage-only) shaved ~2 sessions vs the worst-case plan. With Aug 1 ship target (~80 days from S263) and ~3 sessions/week of pace, ~10 sessions ≈ 3.5 calendar weeks of *just* this work. Interleaves with content authoring, Form TX coordination, and signed-AAB / Play-Console operator-side items. **Mid-to-end June is a reasonable internal milestone for Phase F completion.**

---

## 5. Acceptance criteria

A surface passes when:

1. Pixel 8 portrait: no content clips off-screen, no text smaller than 12sp due to squishing, no overlapping floating UI elements, no horizontal scrollbars unless intentional.
2. Pixel 8 landscape: parity with Lenovo landscape (regression baseline).
3. Lenovo landscape: no regression vs current S262 state.
4. Lenovo portrait: subject to decision #1; default OK if same code paths as Pixel portrait pass.
5. Rotating the device mid-tour does not crash, does not lose narration queue, does not lose tour progress, does not reset the map camera.

---

## 6. Risks + open questions

- **Risk: scope creep.** 43 dialog surfaces is a lot to audit manually. Phase C as scoped may underestimate. If Phase C bleeds into 5+ sessions, fall back to landscape-lock as a Plan B for V1 with portrait as V1.0.1.
- **Risk: hero/image assets.** Per [[feedback-hero-revisit-later]], the hero/image-asset migration is OLD work planned for a full revisit. We should NOT regenerate splash/hero images for portrait as part of this plan if it triggers the broader revisit; ideally we letterbox for V1 and let the hero revisit handle portrait splashes properly.
- **Risk: tilt-mode behavior on portrait at 60°.** `TiltContainer` math + `WickedMap` perspective extension haven't been validated in portrait. Wide-screen assumptions may have leaked into the matrix calc.
- **Risk: `onConfigurationChanged` partial implementations.** Adding the handler is straightforward; making it actually re-bind every dynamic UI surface (FAB labels, dock icons, narration sheet contents) is the long tail.
- **Risk: V1.0 ship slip.** 12 sessions is a real chunk. If Form TX / Play Console / signing keystore / privacy policy items also need attention in this window, Aug 1 gets tight.

---

## 7. Carry-forwards into next session

S264 opener: **Phase A** — operator-driven Pixel 8 visual smoke test + screenshot matrix. Claude waits for operator to walk the surfaces, then collates `MATRIX.md`.

Pre-S264 setup:
- Enable auto-rotate on Pixel 8 (operator-side).
- Confirm Pixel 8 settings: GPS on, location permission granted to the app (granted=false in S263 install).
- Plug Lenovo + Pixel into adb concurrently (both serials known to S263 memory).

---

## 8. Related memories + references

- `reference_pixel8_test_device.md` — Pixel 8 serial / Android version / install pattern.
- `feedback_lenovo_is_v1_minimum_spec.md` — Lenovo retained as min-spec floor; Pixel 8 primary.
- `feedback_lenovo_over_emulator.md` — physical device priority list.
- `feedback_hero_revisit_later.md` — flag for Phase E splash work.
- `STATE.md` § "TOP PRIORITY — V1 launch triage" — Aug 1 ship target.
- S263 live log — `docs/session-logs/session-263-2026-05-13.md`.
- Explore agent inventory output — captured in S263 live log.
