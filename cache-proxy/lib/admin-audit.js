/*
 * admin-audit.js — S196
 *
 * Audit log endpoints for the web admin tool. The PG trigger
 * salem_audit_trigger_fn (see scripts/install-audit-trigger.sql) writes
 * one salem_audit_log row per real change on the audited tables. These
 * endpoints surface that log in the admin UI and provide a revert path.
 *
 * Endpoints:
 *   GET  /admin/salem/audit                          — paginated list, filterable
 *   GET  /admin/salem/audit/entity/:table/:id        — per-entity history
 *   GET  /admin/salem/audit/:id                      — single audit row (full old/new JSON)
 *   POST /admin/salem/audit/:id/revert               — re-applies old_values, marks reverted
 *   GET  /admin/salem/audit/stats                    — summary counts (last 24h, 7d)
 *
 * Filter params (GET /admin/salem/audit):
 *   table    — single table name
 *   entity   — entity_id substring match
 *   actor    — actor substring match
 *   source   — source substring match
 *   action   — INSERT|UPDATE|DELETE
 *   field    — changed_fields contains this column name
 *   since    — ISO timestamp lower bound
 *   until    — ISO timestamp upper bound
 *   reverted — 'true'|'false' (omit for all)
 *   limit    — max 500, default 100
 *   offset   — for pagination
 */
'use strict';

const PAGE_LIMIT_MAX = 500;
const PAGE_LIMIT_DEFAULT = 100;

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // Tables the trigger covers — used to validate the :table param on
  // entity-history queries and to constrain the revert path. Must match
  // the list in scripts/install-audit-trigger.sql.
  const AUDITED_TABLES = new Set([
    'salem_pois',
    'salem_tours',
    'salem_tour_legs',
    'salem_tour_stops',
    'salem_poi_categories',
    'salem_poi_subcategories',
    'salem_witch_trials_articles',
    'salem_witch_trials_newspapers',
    'salem_witch_trials_npc_bios',
    'salem_geocode_blacklist',
  ]);

  // Tables keyed by composite or non-'id' primary key. The trigger's
  // entity_id helper picks an id from common columns; for revert we need
  // to know which column to filter on.
  const ENTITY_KEY = {
    salem_pois: 'id',
    salem_tours: 'id',
    salem_tour_legs: 'id',
    salem_tour_stops: 'id',
    salem_poi_categories: 'id',
    salem_poi_subcategories: 'id',
    salem_witch_trials_articles: 'id',
    salem_witch_trials_newspapers: 'id',
    salem_witch_trials_npc_bios: 'id',
    salem_geocode_blacklist: 'poi_id', // composite PK; revert by poi_id is approximate
  };

  // ── List ─────────────────────────────────────────────────────────────
  app.get('/admin/salem/audit', requirePg, async (req, res) => {
    try {
      const wh = [];
      const p = [];
      const push = (clause, val) => { p.push(val); wh.push(clause.replace('?', `$${p.length}`)); };

      if (req.query.table)    push('table_name = ?', String(req.query.table));
      if (req.query.entity)   push('entity_id ILIKE ?', `%${req.query.entity}%`);
      if (req.query.actor)    push('actor ILIKE ?', `%${req.query.actor}%`);
      if (req.query.source)   push('source ILIKE ?', `%${req.query.source}%`);
      if (req.query.action)   push('action = ?', String(req.query.action).toUpperCase());
      if (req.query.field)    push('? = ANY(changed_fields)', String(req.query.field));
      if (req.query.since)    push('recorded_at >= ?', new Date(String(req.query.since)));
      if (req.query.until)    push('recorded_at <= ?', new Date(String(req.query.until)));
      if (req.query.reverted === 'true')  wh.push('reverted = true');
      if (req.query.reverted === 'false') wh.push('reverted = false');

      const limit = Math.min(PAGE_LIMIT_MAX, parseInt(String(req.query.limit || ''), 10) || PAGE_LIMIT_DEFAULT);
      const offset = Math.max(0, parseInt(String(req.query.offset || ''), 10) || 0);

      const where = wh.length ? `WHERE ${wh.join(' AND ')}` : '';
      // total
      const totalRes = await pgPool.query(`SELECT COUNT(*)::int AS n FROM salem_audit_log ${where}`, p);
      // page
      p.push(limit); p.push(offset);
      const rowsRes = await pgPool.query(
        `SELECT id, recorded_at, table_name, entity_id, action, changed_fields,
                actor, source, reverted, reverted_at, reverted_by, revert_audit_id
           FROM salem_audit_log
           ${where}
           ORDER BY recorded_at DESC, id DESC
           LIMIT $${p.length - 1} OFFSET $${p.length}`,
        p,
      );
      res.json({ total: totalRes.rows[0].n, limit, offset, rows: rowsRes.rows });
    } catch (e) {
      console.error('[admin-audit] list:', e);
      res.status(500).json({ error: 'audit list failed', detail: e.message });
    }
  });

  // ── Stats ────────────────────────────────────────────────────────────
  app.get('/admin/salem/audit/stats', requirePg, async (req, res) => {
    try {
      const out = await pgPool.query(`
        SELECT
          (SELECT COUNT(*)::int FROM salem_audit_log) AS total,
          (SELECT COUNT(*)::int FROM salem_audit_log WHERE recorded_at > NOW() - INTERVAL '24 hours') AS last_24h,
          (SELECT COUNT(*)::int FROM salem_audit_log WHERE recorded_at > NOW() - INTERVAL '7 days')  AS last_7d,
          (SELECT COUNT(*)::int FROM salem_audit_log WHERE reverted = true) AS reverted_count,
          (SELECT COUNT(DISTINCT entity_id)::int FROM salem_audit_log WHERE recorded_at > NOW() - INTERVAL '24 hours') AS entities_24h,
          (SELECT COUNT(*)::int FROM salem_audit_log WHERE actor IS NULL OR source IS NULL) AS uninstrumented_count
      `);
      const byTable = await pgPool.query(`
        SELECT table_name, COUNT(*)::int AS n
          FROM salem_audit_log
          GROUP BY table_name
          ORDER BY n DESC
      `);
      const byField = await pgPool.query(`
        SELECT unnest(changed_fields) AS field, COUNT(*)::int AS n
          FROM salem_audit_log
          WHERE recorded_at > NOW() - INTERVAL '7 days'
          GROUP BY field
          ORDER BY n DESC
          LIMIT 20
      `);
      res.json({ summary: out.rows[0], by_table: byTable.rows, top_fields_7d: byField.rows });
    } catch (e) {
      console.error('[admin-audit] stats:', e);
      res.status(500).json({ error: 'stats failed', detail: e.message });
    }
  });

  // ── Single row (full old/new JSON) ───────────────────────────────────
  app.get('/admin/salem/audit/:id(\\d+)', requirePg, async (req, res) => {
    try {
      const id = parseInt(req.params.id, 10);
      const r = await pgPool.query('SELECT * FROM salem_audit_log WHERE id = $1', [id]);
      if (!r.rows.length) return res.status(404).json({ error: 'not found' });
      res.json(r.rows[0]);
    } catch (e) {
      console.error('[admin-audit] get:', e);
      res.status(500).json({ error: 'fetch failed', detail: e.message });
    }
  });

  // ── Per-entity history ───────────────────────────────────────────────
  app.get('/admin/salem/audit/entity/:table/:id', requirePg, async (req, res) => {
    try {
      const table = String(req.params.table);
      if (!AUDITED_TABLES.has(table)) {
        return res.status(400).json({ error: `table not audited: ${table}` });
      }
      const limit = Math.min(100, parseInt(String(req.query.limit || ''), 10) || 50);
      const r = await pgPool.query(
        `SELECT id, recorded_at, action, changed_fields, actor, source,
                reverted, reverted_at, revert_audit_id
           FROM salem_audit_log
          WHERE table_name = $1 AND entity_id = $2
          ORDER BY recorded_at DESC, id DESC
          LIMIT $3`,
        [table, String(req.params.id), limit],
      );
      res.json({ table, entity_id: req.params.id, rows: r.rows });
    } catch (e) {
      console.error('[admin-audit] entity:', e);
      res.status(500).json({ error: 'entity history failed', detail: e.message });
    }
  });

  // ── Revert ───────────────────────────────────────────────────────────
  // Re-applies the audit row's old_values to the live row. Writes a NEW
  // audit row capturing the revert as a separate change (with action=UPDATE
  // for revert-of-update, INSERT for revert-of-DELETE, DELETE for
  // revert-of-INSERT). Marks the original row as reverted, stores the
  // revert audit's id for cross-reference.
  //
  // Limitations:
  //   - Composite-PK tables (salem_geocode_blacklist) — revert uses entity_id
  //     as the simple key; multi-key edge cases may need manual fix.
  //   - Revert of INSERT does NOT cascade to dependents; if you reverted a
  //     POI INSERT that already has narration regen log entries, those
  //     stay.
  //   - The revert is a fresh trigger-firing UPDATE, so it's itself audited.
  app.post('/admin/salem/audit/:id(\\d+)/revert', requirePg, async (req, res) => {
    const auditId = parseInt(req.params.id, 10);
    const reverter = (req.body && req.body.actor) || req.user || 'admin-ui';
    const reason = (req.body && req.body.reason) || null;

    const client = await pgPool.connect();
    try {
      await client.query('BEGIN');
      // Set audit context for the revert's own trigger fire. PG SET LOCAL
      // does NOT support parameter binding ($1), so we escape literals
      // manually — single-quote any single-quote in the value.
      const escapeLiteral = (v) => `'${String(v).replace(/'/g, "''")}'`;
      await client.query(`SET LOCAL "app.actor" = ${escapeLiteral(reverter)}`);
      await client.query(`SET LOCAL "app.source" = ${escapeLiteral(`admin-ui:revert(${auditId})`)}`);

      const auditRes = await client.query(
        'SELECT * FROM salem_audit_log WHERE id = $1 FOR UPDATE',
        [auditId],
      );
      if (!auditRes.rows.length) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'audit row not found' });
      }
      const row = auditRes.rows[0];
      if (row.reverted) {
        await client.query('ROLLBACK');
        return res.status(409).json({ error: 'already reverted', revert_audit_id: row.revert_audit_id });
      }
      const table = row.table_name;
      if (!AUDITED_TABLES.has(table)) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: `table not audited: ${table}` });
      }
      const keyCol = ENTITY_KEY[table] || 'id';

      // Apply the inverse operation
      let preNewAuditId = null;
      try {
        if (row.action === 'UPDATE') {
          // Restore each changed field to its OLD value
          const sets = [];
          const params = [];
          for (const f of row.changed_fields) {
            // Skip meta fields (the trigger will re-stamp them naturally)
            if (f === 'updated_at' || f === 'admin_dirty_at' || f === 'admin_dirty') continue;
            params.push(row.old_values[f] ?? null);
            // Cast JSON value to text — pg will coerce during the column-typed assignment
            sets.push(`${f} = $${params.length}`);
          }
          if (!sets.length) {
            await client.query('ROLLBACK');
            return res.status(400).json({ error: 'nothing to revert (changed_fields were all meta)' });
          }
          params.push(row.entity_id);
          const sql = `UPDATE ${table} SET ${sets.join(', ')} WHERE ${keyCol} = $${params.length}`;
          const updRes = await client.query(sql, params);
          if (updRes.rowCount === 0) {
            await client.query('ROLLBACK');
            return res.status(404).json({ error: 'live row missing — cannot revert UPDATE (entity may have been deleted later)' });
          }
        } else if (row.action === 'INSERT') {
          // Revert an INSERT by deleting the inserted row
          const sql = `DELETE FROM ${table} WHERE ${keyCol} = $1`;
          const delRes = await client.query(sql, [row.entity_id]);
          if (delRes.rowCount === 0) {
            await client.query('ROLLBACK');
            return res.status(404).json({ error: 'live row missing — already deleted?' });
          }
        } else if (row.action === 'DELETE') {
          // Revert a DELETE by re-inserting with the captured old_values
          const cols = Object.keys(row.old_values).filter((k) => k !== 'updated_at' && k !== 'admin_dirty_at');
          const placeholders = cols.map((_, i) => `$${i + 1}`);
          const params = cols.map((c) => row.old_values[c]);
          const sql = `INSERT INTO ${table} (${cols.map((c) => `"${c}"`).join(', ')}) VALUES (${placeholders.join(', ')})`;
          await client.query(sql, params);
        }

        // Mark the original audit row as reverted; stash the revert's own
        // audit_id (the most recent row in salem_audit_log written by the
        // operation we just performed) for cross-reference.
        const newest = await client.query('SELECT MAX(id)::int AS id FROM salem_audit_log');
        preNewAuditId = newest.rows[0].id;
      } catch (innerErr) {
        await client.query('ROLLBACK');
        console.error('[admin-audit] revert inner failure:', innerErr);
        return res.status(500).json({ error: 'revert apply failed', detail: innerErr.message });
      }

      await client.query(
        `UPDATE salem_audit_log
            SET reverted = true,
                reverted_at = NOW(),
                reverted_by = $1,
                revert_audit_id = $2
          WHERE id = $3`,
        [reverter, preNewAuditId, auditId],
      );
      await client.query('COMMIT');
      res.json({ ok: true, audit_id: auditId, revert_audit_id: preNewAuditId, reason });
    } catch (e) {
      try { await client.query('ROLLBACK'); } catch (_) {}
      console.error('[admin-audit] revert:', e);
      res.status(500).json({ error: 'revert failed', detail: e.message });
    } finally {
      client.release();
    }
  });
};
