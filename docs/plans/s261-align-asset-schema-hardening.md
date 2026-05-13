# S261 — align-asset-schema partial-failure hardening

## Context

The Room asset DB at `app-salem/src/main/assets/salem_content.db` is rebuilt by `cache-proxy/scripts/align-asset-schema-to-room.js` as the final step of the publish chain (`publish-salem-pois.js` → `publish-tours.js` → `publish-tour-legs.js` → **align-asset-schema-to-room.js**). Per S185 the alignment is required so Room's `TableInfo.equals` comparison doesn't trigger `fallbackToDestructiveMigration` (wiping every row) on first launch.

For each Room entity, the script currently:

1. Snapshots all rows in memory
2. `DROP TABLE IF EXISTS <name>` (line 78)
3. `CREATE TABLE <name> ...` from the Room schema JSON (line 79)
4. Re-creates indexes (lines 80–82)
5. Re-inserts rows inside a `db.transaction()` (lines 93–100)

Steps 2-4 (DROP + CREATE + INDEX) are **not** wrapped in a transaction. If CREATE or an index creation throws (e.g., malformed `createSql` in the schema JSON after a Room version bump that wasn't caught by `kspDebugKotlin` — a real S185-class failure mode), the script crashes mid-table. SQLite has already committed the DROP. The asset DB is left with a missing table.

The publish chain then aborts (sync exception → non-zero exit → Gradle `Exec` task fails — the script does **not** silently fail despite a prior audit's claim; `main()` at line 109 is synchronous, not async). But the asset DB is corrupt on disk. The next align run sees `tableExists=false`, re-creates from the schema, but has no rows to restore (snapshot was in-memory and lost). The table ships **empty** in the AAB.

Risk surface is narrow but real: any schema-JSON malformation introduces silent row loss on the next chain run after the script crash. With Room versions bumping ~3-5 times per year (v11→v19 across the last six months), the window is non-zero.

## Scope

**One file:** `cache-proxy/scripts/align-asset-schema-to-room.js`.

**Out of scope:** `.catch()` on `main()` (skipped — main is sync, sibling scripts need it because they're async, this script doesn't have the bug). TigerBase/Super Admin tab gating (operator wants these visible for lawyer review). Content refinement track (66 short narrations, 221 missing subtopics, 14 oversized historicals, ~5–10 pre-1860 landmark backfills) — multi-session authoring docket, logged as carry-forward for future sessions.

## The change

Wrap the per-entity DROP + CREATE + INDEX + INSERT sequence in a single `db.transaction()` so partial failure rolls back to the pre-DROP table state. SQLite supports DDL inside transactions; better-sqlite3's `.transaction()` API issues BEGIN/COMMIT/ROLLBACK and rolls back DROP/CREATE on throw.

**Single function edit:** `alignDb()` at lines 56–107. Pull the existing operations into one `rebuildTable = db.transaction(() => { ... })` then call `rebuildTable()`. The existing inner re-insert transaction (lines 93–100) collapses into the outer one — better-sqlite3 does not nest transactions, so the inner `db.transaction(...)()` call must be removed and its loop body inlined.

**Diff sketch (~15 LOC delta):**

```javascript
// Inside alignDb(), replace lines 78-101 with:
const rebuildTable = db.transaction(() => {
  db.exec(`DROP TABLE IF EXISTS ${tableName};`);
  db.exec(sqlForEntity(entity));
  for (const idx of entity.indices || []) {
    db.exec(idx.createSql.replace('${TABLE_NAME}', tableName));
  }
  if (rows.length) {
    const newCols = db.prepare(`PRAGMA table_info(${tableName})`).all().map((c) => c.name);
    const sharedCols = columns.filter((c) => newCols.includes(c));
    const placeholders = sharedCols.map((c) => `@${c}`).join(', ');
    const colList = sharedCols.map((c) => `"${c}"`).join(', ');
    const insert = db.prepare(
      `INSERT INTO ${tableName} (${colList}) VALUES (${placeholders})`
    );
    for (const r of rows) {
      const slim = {};
      for (const c of sharedCols) slim[c] = r[c];
      insert.run(slim);
    }
  }
});
rebuildTable();
```

Lines outside the transaction (rows snapshot at 70-75, post-COMMIT row-count log at 102-103) stay as-is.

**Behavioral effect:**

- Success path: identical. Same SQL executed in the same order. Existing PRAGMA `foreign_keys = OFF` set at line 59 stays in effect for the transaction's duration (per SQLite docs, foreign_keys cannot change *inside* a transaction, but we set it *before*, so we're fine).
- Failure path: any throw inside the transaction triggers ROLLBACK. Table reverts to pre-DROP state. Sync exception still propagates and crashes the script with non-zero exit + stack trace, halting the publish chain. Operator sees the loud failure and the asset DB is intact, so the next run after fixing the schema JSON works against the original data, not an empty post-DROP state.

## Files to modify

- `cache-proxy/scripts/align-asset-schema-to-room.js` — single function edit in `alignDb()`, ~15 LOC delta. No new dependencies, no API changes, no callers affected.

## Verification

1. **Smoke (must pass):** Run `node cache-proxy/scripts/align-asset-schema-to-room.js` against the current asset DB. Expected output identical to a pre-change run: `=== Align Asset Schema to Room v19 ===`, identity hash `745afa3eb4ce04bd7873671ea297b6e0`, every entity logged `<name> N → N rows` with matching before/after counts, ending `ALIGN COMPLETE`. Idempotency check — re-run a second time, results match.

2. **Row-count parity (must pass):** Before running, capture row counts from the asset DB:
   ```
   sqlite3 app-salem/src/main/assets/salem_content.db "SELECT name, (SELECT COUNT(*) FROM \"' || name || '\"') FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name NOT LIKE 'android_metadata' AND name NOT LIKE 'room_master_table'"
   ```
   Re-run after alignment. Counts must match exactly. (Existing align-script behavior is to preserve all rows; this is a regression-guard for the transaction restructuring.)

3. **Room identity hash (must pass):**
   ```
   sqlite3 app-salem/src/main/assets/salem_content.db "SELECT identity_hash FROM room_master_table WHERE id=42"
   sqlite3 app-salem/src/main/assets/salem_content.db "PRAGMA user_version"
   ```
   Must return `745afa3eb4ce04bd7873671ea297b6e0` and `19`. (Re-stamping happens at lines 130-139, outside the per-entity transaction loop — that code is unchanged.)

4. **Rollback behavior (optional, higher-confidence):** Skip unless operator wants extra proof. Would require temporarily mutating one entity's `createSql` in the schema JSON to invalid SQL, running the script, confirming the script crashes with a stack trace AND the target table still has its original row count (proving rollback worked). Then revert the schema mutation and confirm a clean run still succeeds. Adds ~10 min; would catch the regression case the fix is designed for. Decision deferred to operator.

5. **Downstream parity (must pass):** Run `node cache-proxy/scripts/verify-bundled-assets.js`. Must exit 0 (existing post-align verification — same gate the preBuild hook runs). Confirms the asset DB still satisfies all 13 Room table presence + identity-hash + row-count minimums.

No Gradle build needed for this change — the script is invoked from preBuild but the script's own outputs are validated by `verify-bundled-assets.js`. A full `./gradlew :app-salem:assembleDebug` would also work as end-to-end verification but is overkill (~2 min) for a self-contained script fix.

## Carry-forward (not in this plan)

These were surfaced by the S261 audit but explicitly excluded from this session:

- **TigerBase + Super Admin tab gating** — operator keeps both visible for lawyer review (S261 decision).
- **Content refinement docket** (multi-session, ~4-8 hours total across sessions):
  - 66 tour-gated POIs with `short_narration` < 50 chars — author 100-char intros OR consolidate into adjacent clusters
  - 221/580 tour-gated POIs with empty `narration_subtopics` (S219 v17 infra unused for non-`adjacent_poi` cards)
  - 14 tour-gated `historical_narration` > 900 chars overflow TTS — trim or escalate to detail-sheet-only
  - ~5–10 genuine pre-1860 HISTORICAL_LANDMARKS worth `historical_narration` backfill
- **Admin UX polish** (Mass Edit re-export-stale button, Field Edits sync error mapping, Mass Edit success toast) — operator-time-saver, not ship blocker.

Logged for S262+ docket.
