### S271 opener: Recon Layer 2 + Passport S5 still owed

S270 shipped Recon Layer 1 (bulk-cull photo triage tool). **S271 carries forward two parallel tracks:**

**Recon Layer 2** — Extend `web/src/admin/BurstPhotosOverlay.tsx` (already 80% built since S229/S231) so the geo cluster pins read the same `.kept.json` keep-flag state (kept = amber pins, unkept = slate); add POI-proximity filter chips (nearest-3 POIs within 50 m via PG lookup); add "Attach to POI" / "Drop" / "Defer" decisions for the curated set; possibly create new `salem_recon_photos` PG table (`id, source_filename, captured_at, lat, lng, heading_true, speed_kmh, poi_id NULLABLE, decision, created_at`) for the post-triage curated photo set that feeds downstream POI hero/gallery work.

**Passport S5** (carried forward from S269, untouched in S270):

1. **Operator field walk** on Lenovo (or Pixel 8 — both attached, the S268-build APK on Pixel 8 needs a fresh bundletool install to carry the S269 code). Smoke flow: tap witch-hat icon outside a tour → Whole-Salem (447 hollow stamps); start a tour → witch-hat now opens that tour's passport (49 / 72 / 188 / 41 hollow stamps); walk a few POIs → stamps fill + status-line shows "Tour: <name>"; full pass through a tour → "Tour Complete!" dialog auto-fires with "X / X stamps" + Open Passport CTA; tap End mid-tour → "Tour Ended" dialog with partial stats; overflow ⋮ → "End tour" item visible during active tour; passport-picker dropdown lets you switch between the 5 baked passports.
2. **PassportSheet empty-state polish** — when a filter yields 0 POIs, show better messaging than the current empty list.
3. **Pixel 8 portrait toolbar fit** check (9 icons including witch-hat).
4. **Memory + doc polish** — already-saved memory files: `feedback_passport_pool_intersect_proximity.md` + `feedback_stamps_not_stops.md`. STATE.md / SESSION-LOG.md updates done at S269 close. MASTER_SESSION_REFERENCE.md update is the last polish item.

**S269 carry-forwards still open:**

- The pre-pivot `default_salem_walking` filter holds operator-set `min_geofence_radius_m=10`. If you want to broaden/narrow, edit it in PassportTab and rerun `node cache-proxy/scripts/publish-poi-passport.js && node cache-proxy/scripts/align-asset-schema-to-room.js` then bundletool install.
- The Passports tab still exposes `min_geofence_radius_m` on per-tour passport rows where it's effectively dead (auto_bake=false skips filter SQL). Could simplify the tab UI to hide the field on tour-bound rows OR drop per-tour rows from the tab entirely (authoring lives in the Tour editor now). Deferred.
- `passport_poi_count` is computed via `LEFT JOIN LATERAL` on every GET — fast at current scale (5 passports). Future: denormalized column on salem_tours if scale demands.
- Linger and Listen (S267) field walk to validate subtopic body queueing still owed.

**S267 latency-fix carry-forwards (untouched in S269):**
- DEBUG-only ~30 min each: drop `MapDebugDumper` auto-cadence 10s → 60s; coalesce `refreshNarrationIcons` 3-bucket → single pass.
- Ships to release: `BillboardMarker` viewport-cull check before `super.draw()` (~1-2 hr); off-thread proximity scan `checkPosition()` → Default dispatcher (~2-4 hr, more invasive — defer until simpler fixes prove insufficient).

**S268 carry-forwards still open:** MIGRATION_2_3 verification (the v2→v3 upgrade path is exercised only on in-place install over the pre-S268 build — every install since S268 has been `adb uninstall && bundletool install-apks` which skips it).

**S266 carry-forwards still open:** field-validate POI edits render on both devices; validate S265 narration banner shrink on Pixel 8 portrait; then continue per-surface portrait iteration or pivot to broader Phase A survey from `docs/plans/pixel8-portrait-support.md`.

**Misc:** Lenovo `enabled=3` (DISABLED_USER) mystery — possibly Zui launcher quirk after S266 in-place-install WAL violation; `pm enable` recovers.

**Phase A portrait smoke test (still owed from S264/S265):** operator-driven Pixel 8 visual smoke test — walk every UI surface (every menu, dialog, sheet, FAB cluster, screen state) in BOTH landscape and portrait on the Pixel 8 (auto-rotate first needs enabling per S263 diagnosis). Screenshot into `docs/pixel8-portrait-survey/<surface>-<orient>.png`. Output: `docs/pixel8-portrait-survey/MATRIX.md` annotating each surface as `OK / minor / major / blocker` per orientation. Drives Phase B-D scope.

**Carry-forward note (Lenovo install recovery):** if `adb uninstall` returns `DELETE_FAILED_INTERNAL_ERROR` again on field-test devices, sequence is `am force-stop` → `pm clear` → retry `uninstall` BEFORE running `bundletool install-apks` — preserves the WAL-safety rule from `feedback_adb_install_after_db_rebake.md`.

**Optional S265 follow-ups on S264 GPS work:**
- Investigate the 5-task-restart cycle observed in the original trip's 19:36-19:45 window (no longer happening on the new build per single-PID drive log — but underlying cause not nailed; may have been a downstream symptom of the same race).
