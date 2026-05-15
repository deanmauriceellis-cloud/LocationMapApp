/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * Admin Passport endpoints (S268).
 *
 * Replaces the four stops-based tour UIs (HUD banner, Active Tour dialog,
 * completion-stats body, numbered polyline pins) with a persisted POI
 * Passport — operator-tunable filters that generate per-user lists of POIs
 * the user accumulates "stamps" against by hearing the narration.
 *
 * Routes (all gated by /admin Basic Auth):
 *   GET    /admin/salem/passports               — list all filters
 *   GET    /admin/salem/passports/:id           — get one filter + baked POI list
 *   POST   /admin/salem/passports               — create a filter
 *   PUT    /admin/salem/passports/:id           — update a filter
 *   DELETE /admin/salem/passports/:id           — delete a filter (cascades to pois)
 *   POST   /admin/salem/passports/preview       — run filter SQL without saving, returns count + sample
 *
 * Filter SQL semantics:
 *   - Excludes soft-deleted POIs (deleted_at IS NULL)
 *   - If categories[] non-empty: salem_pois.category IN categories[]
 *   - If require_historical_narration: historical_narration IS NOT NULL AND != ''
 *   - geofence_radius_m >= min_geofence_radius_m
 *   - year_established BETWEEN min/max when set (NULL year handled gracefully)
 *   - tour_id is metadata-only in V1 (groups passports under a tour name).
 *     Operator tunes filter params per-tour to create meaningful subsets.
 *     Future: polyline-proximity SQL for true per-tour POI lists.
 *
 * The actual bake (writing rows into salem_passport_pois) lives in
 * cache-proxy/scripts/publish-poi-passport.js.
 */
const MODULE_ID = '(C) Destructive AI Gurus, LLC, 2026 - Module admin-passport.js';

const VALID_ID_RE = /^[a-z0-9_]+$/;

function asArray(v) {
  if (Array.isArray(v)) return v;
  if (v == null) return [];
  return [v];
}

function normalizeFilterParams(body) {
  const errors = [];
  const out = {};

  if (typeof body.id === 'string' && body.id.trim()) {
    out.id = body.id.trim();
    if (!VALID_ID_RE.test(out.id)) {
      errors.push('id must contain only lowercase letters, digits, and underscores');
    }
  }

  if (typeof body.name === 'string' && body.name.trim()) {
    out.name = body.name.trim();
  } else if (body.name !== undefined) {
    errors.push('name must be a non-empty string');
  }

  if (body.tour_id === null || body.tour_id === '' || body.tour_id === undefined) {
    out.tour_id = null;
  } else if (typeof body.tour_id === 'string') {
    out.tour_id = body.tour_id.trim();
  } else {
    errors.push('tour_id must be a string or null');
  }

  const cats = asArray(body.categories).filter(c => typeof c === 'string' && c.trim());
  out.categories = cats.map(c => c.trim());

  if (body.require_historical_narration !== undefined) {
    out.require_historical_narration = !!body.require_historical_narration;
  }

  if (body.min_geofence_radius_m !== undefined) {
    const n = parseInt(body.min_geofence_radius_m, 10);
    if (!Number.isFinite(n) || n < 0) {
      errors.push('min_geofence_radius_m must be a non-negative integer');
    } else {
      out.min_geofence_radius_m = n;
    }
  }

  if (body.min_year_established !== undefined) {
    if (body.min_year_established === null || body.min_year_established === '') {
      out.min_year_established = null;
    } else {
      const n = parseInt(body.min_year_established, 10);
      if (!Number.isFinite(n)) {
        errors.push('min_year_established must be an integer or null');
      } else {
        out.min_year_established = n;
      }
    }
  }

  if (body.max_year_established !== undefined) {
    if (body.max_year_established === null || body.max_year_established === '') {
      out.max_year_established = null;
    } else {
      const n = parseInt(body.max_year_established, 10);
      if (!Number.isFinite(n)) {
        errors.push('max_year_established must be an integer or null');
      } else {
        out.max_year_established = n;
      }
    }
  }

  if (body.sort_order !== undefined) {
    const n = parseInt(body.sort_order, 10);
    if (!Number.isFinite(n)) {
      errors.push('sort_order must be an integer');
    } else {
      out.sort_order = n;
    }
  }

  return { params: out, errors };
}

// Build the WHERE clauses + bind params for a filter's POI query. Returns
// { whereSql, joinSql, args } where args are positional ($1, $2, …).
// Used by both /preview and publish-poi-passport.js (kept in this module so
// the bake script can require() it and stay in lock-step).
function buildFilterQuery(filter) {
  const wheres = ['p.deleted_at IS NULL'];
  const args = [];

  const cats = Array.isArray(filter.categories) ? filter.categories.filter(c => c) : [];
  if (cats.length) {
    args.push(cats);
    wheres.push(`p.category = ANY($${args.length}::text[])`);
  }

  if (filter.require_historical_narration) {
    wheres.push(`p.historical_narration IS NOT NULL`);
    wheres.push(`length(trim(p.historical_narration)) > 0`);
  }

  if (Number.isFinite(filter.min_geofence_radius_m) && filter.min_geofence_radius_m > 0) {
    args.push(filter.min_geofence_radius_m);
    wheres.push(`COALESCE(p.geofence_radius_m, 0) >= $${args.length}`);
  }

  if (Number.isFinite(filter.min_year_established) && filter.min_year_established !== null) {
    args.push(filter.min_year_established);
    wheres.push(`p.year_established >= $${args.length}`);
  }

  if (Number.isFinite(filter.max_year_established) && filter.max_year_established !== null) {
    args.push(filter.max_year_established);
    wheres.push(`p.year_established <= $${args.length}`);
  }

  // tour_id is metadata-only in V1 — operator tunes filter params (categories,
  // year range, geofence floor) per-tour to create meaningful subsets. Today's
  // tours are polyline-only (free waypoints, no POI anchors per the post-S190
  // architecture), so a salem_tour_stops join would always be empty. Future
  // enhancement: polyline-proximity SQL using PostGIS or a hand-rolled
  // Haversine, gated behind a per-filter flag.
  const joinSql = '';

  return {
    whereSql: wheres.join('\n   AND '),
    joinSql,
    args,
  };
}

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // ─── GET /admin/salem/passports ─────────────────────────────────────────────
  app.get('/admin/salem/passports', requirePg, async (_req, res) => {
    try {
      const { rows } = await pgPool.query(`
        SELECT pf.id, pf.name, pf.tour_id, pf.categories, pf.require_historical_narration,
               pf.min_geofence_radius_m, pf.min_year_established, pf.max_year_established,
               pf.sort_order, pf.created_at, pf.updated_at,
               t.name AS tour_name,
               (SELECT COUNT(*)::int FROM salem_passport_pois pp WHERE pp.filter_id = pf.id) AS poi_count
          FROM salem_passport_filters pf
     LEFT JOIN salem_tours t ON t.id = pf.tour_id
      ORDER BY pf.sort_order ASC, pf.name ASC
      `);
      res.json({ count: rows.length, passports: rows });
    } catch (err) {
      console.error('[AdminPassport] list error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── GET /admin/salem/passports/:id ─────────────────────────────────────────
  app.get('/admin/salem/passports/:id', requirePg, async (req, res) => {
    try {
      const id = req.params.id;
      if (!VALID_ID_RE.test(id)) {
        return res.status(400).json({ error: 'invalid passport id format' });
      }

      const filterQ = await pgPool.query(
        `SELECT pf.*, t.name AS tour_name
           FROM salem_passport_filters pf
      LEFT JOIN salem_tours t ON t.id = pf.tour_id
          WHERE pf.id = $1`,
        [id],
      );
      if (!filterQ.rows.length) return res.status(404).json({ error: 'passport not found' });

      const poisQ = await pgPool.query(
        `SELECT pp.poi_id, pp.display_order,
                p.name, p.category, p.lat, p.lng,
                p.geofence_radius_m, p.year_established
           FROM salem_passport_pois pp
           JOIN salem_pois p ON p.id = pp.poi_id
          WHERE pp.filter_id = $1
       ORDER BY pp.display_order ASC, p.name ASC`,
        [id],
      );

      res.json({
        passport: filterQ.rows[0],
        pois: poisQ.rows,
      });
    } catch (err) {
      console.error('[AdminPassport] get error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/passports ────────────────────────────────────────────
  app.post('/admin/salem/passports', requirePg, async (req, res) => {
    try {
      const { params, errors } = normalizeFilterParams(req.body || {});
      if (!params.id) errors.push('id is required');
      if (!params.name) errors.push('name is required');
      if (errors.length) return res.status(400).json({ error: errors.join('; ') });

      const r = await pgPool.query(
        `INSERT INTO salem_passport_filters
           (id, name, tour_id, categories, require_historical_narration,
            min_geofence_radius_m, min_year_established, max_year_established, sort_order)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
         RETURNING *`,
        [
          params.id,
          params.name,
          params.tour_id ?? null,
          params.categories ?? [],
          params.require_historical_narration !== undefined ? params.require_historical_narration : true,
          params.min_geofence_radius_m ?? 0,
          params.min_year_established ?? null,
          params.max_year_established ?? null,
          params.sort_order ?? 0,
        ],
      );
      res.status(201).json({ passport: r.rows[0] });
    } catch (err) {
      if (err.code === '23505') {
        return res.status(409).json({ error: `passport id already exists` });
      }
      if (err.code === '23503') {
        return res.status(400).json({ error: `tour_id does not reference an existing tour` });
      }
      console.error('[AdminPassport] create error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── PUT /admin/salem/passports/:id ─────────────────────────────────────────
  app.put('/admin/salem/passports/:id', requirePg, async (req, res) => {
    try {
      const id = req.params.id;
      if (!VALID_ID_RE.test(id)) {
        return res.status(400).json({ error: 'invalid passport id format' });
      }
      const { params, errors } = normalizeFilterParams(req.body || {});
      if (errors.length) return res.status(400).json({ error: errors.join('; ') });

      const setClauses = [];
      const args = [];
      const set = (col, val) => {
        args.push(val);
        setClauses.push(`${col} = $${args.length}`);
      };
      if (params.name !== undefined) set('name', params.name);
      if (params.tour_id !== undefined) set('tour_id', params.tour_id);
      if (params.categories !== undefined) set('categories', params.categories);
      if (params.require_historical_narration !== undefined) set('require_historical_narration', params.require_historical_narration);
      if (params.min_geofence_radius_m !== undefined) set('min_geofence_radius_m', params.min_geofence_radius_m);
      if (params.min_year_established !== undefined) set('min_year_established', params.min_year_established);
      if (params.max_year_established !== undefined) set('max_year_established', params.max_year_established);
      if (params.sort_order !== undefined) set('sort_order', params.sort_order);
      setClauses.push(`updated_at = NOW()`);
      if (setClauses.length === 1) {
        return res.status(400).json({ error: 'no updatable fields provided' });
      }
      args.push(id);

      const r = await pgPool.query(
        `UPDATE salem_passport_filters SET ${setClauses.join(', ')} WHERE id = $${args.length} RETURNING *`,
        args,
      );
      if (!r.rows.length) return res.status(404).json({ error: 'passport not found' });
      res.json({ passport: r.rows[0] });
    } catch (err) {
      if (err.code === '23503') {
        return res.status(400).json({ error: `tour_id does not reference an existing tour` });
      }
      console.error('[AdminPassport] update error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── DELETE /admin/salem/passports/:id ──────────────────────────────────────
  app.delete('/admin/salem/passports/:id', requirePg, async (req, res) => {
    try {
      const id = req.params.id;
      if (!VALID_ID_RE.test(id)) {
        return res.status(400).json({ error: 'invalid passport id format' });
      }
      const r = await pgPool.query(
        `DELETE FROM salem_passport_filters WHERE id = $1`,
        [id],
      );
      if (!r.rowCount) return res.status(404).json({ error: 'passport not found' });
      res.json({ ok: true, deleted: id });
    } catch (err) {
      console.error('[AdminPassport] delete error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/passports/preview ────────────────────────────────────
  // Runs the filter SQL without saving. Body carries the unsaved filter
  // parameters. Returns count + sample of matched POIs (cap 25).
  app.post('/admin/salem/passports/preview', requirePg, async (req, res) => {
    try {
      const { params, errors } = normalizeFilterParams(req.body || {});
      if (errors.length) return res.status(400).json({ error: errors.join('; ') });

      const { whereSql, joinSql, args } = buildFilterQuery({
        categories: params.categories || [],
        require_historical_narration: params.require_historical_narration !== undefined
          ? params.require_historical_narration : true,
        min_geofence_radius_m: params.min_geofence_radius_m ?? 0,
        min_year_established: params.min_year_established ?? null,
        max_year_established: params.max_year_established ?? null,
        tour_id: params.tour_id ?? null,
      });

      const countQ = await pgPool.query(
        `SELECT COUNT(*)::int AS n
           FROM salem_pois p
           ${joinSql}
          WHERE ${whereSql}`,
        args,
      );
      const sampleQ = await pgPool.query(
        `SELECT p.id, p.name, p.category, p.lat, p.lng,
                p.geofence_radius_m, p.year_established,
                CASE WHEN length(trim(COALESCE(p.historical_narration, ''))) > 0 THEN TRUE ELSE FALSE END AS has_historical_narration
           FROM salem_pois p
           ${joinSql}
          WHERE ${whereSql}
       ORDER BY p.name ASC
          LIMIT 25`,
        args,
      );

      res.json({
        count: countQ.rows[0].n,
        sample: sampleQ.rows,
      });
    } catch (err) {
      console.error('[AdminPassport] preview error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });
};

// Export for publish-poi-passport.js to reuse.
module.exports.buildFilterQuery = buildFilterQuery;
