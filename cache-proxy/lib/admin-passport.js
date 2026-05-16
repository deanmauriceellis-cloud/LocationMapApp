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

// ════════════════════════════════════════════════════════════════════════════
// S269 — Walk-derived passport candidates.
//
// "Walk" the tour's baked polyline (salem_tour_legs) and surface every POI
// whose own geofence the walked path would cross, ordered by encounter
// index along the walk. Used by the Tour-editor "Build Passport from Walk"
// dialog.
// ════════════════════════════════════════════════════════════════════════════

/** Decode the bake's "lat,lng;lat,lng;..." geometry string into [{lat,lng}]. */
function decodeGeometry(geometry) {
  if (!geometry) return [];
  const out = [];
  for (const pair of String(geometry).split(';')) {
    const comma = pair.indexOf(',');
    if (comma <= 0) continue;
    const lat = parseFloat(pair.slice(0, comma));
    const lng = parseFloat(pair.slice(comma + 1));
    if (Number.isFinite(lat) && Number.isFinite(lng)) out.push({ lat, lng });
  }
  return out;
}

/** Equirectangular distance in metres — accurate enough for sub-km walks
 *  in Salem and ~10× faster than haversine on the inner loop. */
function approxDistanceM(latA, lngA, latB, lngB) {
  const R = 6_371_000;
  const φ1 = (latA * Math.PI) / 180;
  const dLat = ((latB - latA) * Math.PI) / 180;
  const dLng = ((lngB - lngA) * Math.PI) / 180;
  const x = dLng * Math.cos(φ1);
  const y = dLat;
  return Math.sqrt(x * x + y * y) * R;
}

/**
 * Minimum point-to-polyline distance. Treats each segment as a 2D line
 * (lat/lng degrees, scaled by cos(lat) for longitude) and returns
 * { distanceM, segmentIndex } for the closest segment, where
 * segmentIndex is the 0-based index of the polyline segment whose nearest
 * point is closest. Used for ordering candidates by walk-encounter order.
 */
function minDistanceToPolyline(latP, lngP, polyline) {
  if (!polyline.length) return { distanceM: Infinity, segmentIndex: -1 };
  if (polyline.length === 1) {
    const d = approxDistanceM(latP, lngP, polyline[0].lat, polyline[0].lng);
    return { distanceM: d, segmentIndex: 0 };
  }
  // Scale longitude into a flat plane using cos(midlat) so segment math
  // is in roughly-isotropic units. Convert back to metres via R later.
  const R = 6_371_000;
  const toRad = (d) => (d * Math.PI) / 180;
  const cosLat = Math.cos(toRad(latP));
  const px = lngP * cosLat;
  const py = latP;
  let bestSq = Infinity;
  let bestSeg = 0;
  for (let i = 0; i < polyline.length - 1; i++) {
    const a = polyline[i];
    const b = polyline[i + 1];
    const ax = a.lng * cosLat;
    const ay = a.lat;
    const bx = b.lng * cosLat;
    const by = b.lat;
    const dx = bx - ax;
    const dy = by - ay;
    const lenSq = dx * dx + dy * dy;
    let cx;
    let cy;
    if (lenSq <= 0) {
      cx = ax;
      cy = ay;
    } else {
      let t = ((px - ax) * dx + (py - ay) * dy) / lenSq;
      if (t < 0) t = 0;
      if (t > 1) t = 1;
      cx = ax + t * dx;
      cy = ay + t * dy;
    }
    const ex = px - cx;
    const ey = py - cy;
    const dSq = ex * ex + ey * ey;
    if (dSq < bestSq) {
      bestSq = dSq;
      bestSeg = i;
    }
  }
  // Convert squared-degrees → metres. sqrt(dSq) is in degrees on the
  // scaled plane; one degree of latitude ≈ R * π / 180. Longitude is
  // already pre-scaled by cos(lat), so the same conversion applies.
  const degToM = (R * Math.PI) / 180;
  return { distanceM: Math.sqrt(bestSq) * degToM, segmentIndex: bestSeg };
}

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // S269 — idempotent forward-migration for the auto_bake column. Existing
  // dev installs that pre-date this column would otherwise 500 on the
  // walk-derived endpoints. Run-once per process start; safe to repeat
  // because ADD COLUMN IF NOT EXISTS is a no-op when the column is present.
  (async () => {
    try {
      await pgPool.query(
        `ALTER TABLE salem_passport_filters
           ADD COLUMN IF NOT EXISTS auto_bake BOOLEAN NOT NULL DEFAULT TRUE`,
      );
    } catch (err) {
      console.warn('[AdminPassport] auto_bake migration warning:', err.message);
    }
  })();

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

  // ─── GET /admin/salem/tours/:tourId/passport-walk-candidates ────────────────
  //
  // S269 — "Walk" the tour's polyline and return every POI with a non-empty
  // historical_narration, with its minimum distance from the path and the
  // polyline segment index where that minimum occurs. Frontend filters by
  // an operator-tunable radius slider client-side (no re-query needed as
  // the slider moves).
  //
  // Response also carries `tour_polyline` so the dialog can render the path
  // on its embedded map without a second fetch.
  app.get('/admin/salem/tours/:tourId/passport-walk-candidates', requirePg, async (req, res) => {
    try {
      const tourId = req.params.tourId;
      if (!tourId || typeof tourId !== 'string') {
        return res.status(400).json({ error: 'tourId required' });
      }

      // Load tour + baked legs
      const tourQ = await pgPool.query(
        `SELECT id, name, theme FROM salem_tours WHERE id = $1`,
        [tourId],
      );
      if (!tourQ.rows.length) {
        return res.status(404).json({ error: 'tour not found' });
      }
      const legsQ = await pgPool.query(
        `SELECT leg_order, polyline_json
           FROM salem_tour_legs
          WHERE tour_id = $1
       ORDER BY leg_order ASC`,
        [tourId],
      );

      // PG salem_tour_legs stores polylines as JSONB arrays of [lat, lng]
      // pairs. The first and last entries are the segment anchors (the
      // runtime asset translates this into the from_lat/to_lat columns;
      // here we just read both halves at once). Dedup the join vertex
      // between consecutive legs so we don't pile up duplicate points.
      const polyline = [];
      for (const leg of legsQ.rows) {
        const arr = Array.isArray(leg.polyline_json) ? leg.polyline_json : [];
        const seg = [];
        for (const pair of arr) {
          if (Array.isArray(pair) && pair.length >= 2) {
            const lat = Number(pair[0]);
            const lng = Number(pair[1]);
            if (Number.isFinite(lat) && Number.isFinite(lng)) seg.push({ lat, lng });
          }
        }
        if (seg.length < 2) continue;
        if (polyline.length === 0) {
          polyline.push(...seg);
        } else {
          polyline.push(...seg.slice(1));
        }
      }

      if (polyline.length < 2) {
        return res.json({
          tour: tourQ.rows[0],
          tour_polyline: polyline,
          candidates: [],
          warning: 'tour has no baked legs — author the tour route first',
        });
      }

      // S269 — pool ∩ proximity. Tour passport candidates must be in the
      // operator-defined global pool (categories, require_historical_narration,
      // min_geofence_radius_m, year range). Falls back to "every POI with a
      // non-empty historical_narration" if no global passport is authored,
      // matching the prior behaviour so existing dev installs don't surprise.
      const poolQ = await pgPool.query(
        `SELECT id, name, categories, require_historical_narration,
                min_geofence_radius_m, min_year_established, max_year_established
           FROM salem_passport_filters
          WHERE tour_id IS NULL
       ORDER BY (id = 'default_salem_walking') DESC, sort_order ASC, name ASC
          LIMIT 1`,
      );
      const pool = poolQ.rows[0] || null;

      // Bounding box of the polyline + a generous buffer so a wide slider
      // still catches POIs just outside the box.
      let minLat = Infinity;
      let maxLat = -Infinity;
      let minLng = Infinity;
      let maxLng = -Infinity;
      for (const p of polyline) {
        if (p.lat < minLat) minLat = p.lat;
        if (p.lat > maxLat) maxLat = p.lat;
        if (p.lng < minLng) minLng = p.lng;
        if (p.lng > maxLng) maxLng = p.lng;
      }
      // 500 m buffer in either direction (≈ 0.0045°, ≈ 0.006° lng at lat 42)
      const latBuf = 0.005;
      const lngBuf = 0.007;

      // Build the WHERE clauses: bbox first, then pool filter (when present).
      const wheres = [
        'p.deleted_at IS NULL',
        'p.lat BETWEEN $1 AND $2',
        'p.lng BETWEEN $3 AND $4',
      ];
      const args = [minLat - latBuf, maxLat + latBuf, minLng - lngBuf, maxLng + lngBuf];

      if (pool) {
        const cats = Array.isArray(pool.categories) ? pool.categories.filter(c => c) : [];
        if (cats.length) {
          args.push(cats);
          wheres.push(`p.category = ANY($${args.length}::text[])`);
        }
        if (pool.require_historical_narration) {
          wheres.push(`p.historical_narration IS NOT NULL`);
          wheres.push(`length(trim(p.historical_narration)) > 0`);
        }
        if (Number.isFinite(pool.min_geofence_radius_m) && pool.min_geofence_radius_m > 0) {
          args.push(pool.min_geofence_radius_m);
          wheres.push(`COALESCE(p.geofence_radius_m, 0) >= $${args.length}`);
        }
        if (pool.min_year_established != null) {
          args.push(pool.min_year_established);
          wheres.push(`p.year_established >= $${args.length}`);
        }
        if (pool.max_year_established != null) {
          args.push(pool.max_year_established);
          wheres.push(`p.year_established <= $${args.length}`);
        }
      } else {
        // No global pool authored — preserve prior behaviour as a soft
        // fallback so dev installs without a default passport still see
        // sensible candidates instead of nothing.
        wheres.push(`length(trim(COALESCE(p.historical_narration, ''))) > 0`);
      }

      const poisQ = await pgPool.query(
        `SELECT p.id, p.name, p.category, p.lat, p.lng,
                COALESCE(p.geofence_radius_m, 40) AS geofence_radius_m,
                p.year_established,
                CASE WHEN length(trim(COALESCE(p.historical_narration, ''))) > 0 THEN TRUE ELSE FALSE END AS has_historical_narration
           FROM salem_pois p
          WHERE ${wheres.join('\n            AND ')}`,
        args,
      );

      // Compute min distance + segment index for every candidate.
      const enriched = [];
      for (const p of poisQ.rows) {
        const { distanceM, segmentIndex } = minDistanceToPolyline(p.lat, p.lng, polyline);
        enriched.push({
          id: p.id,
          name: p.name,
          category: p.category,
          lat: p.lat,
          lng: p.lng,
          geofence_radius_m: p.geofence_radius_m,
          year_established: p.year_established,
          min_distance_m: distanceM,
          encounter_index: segmentIndex,
        });
      }
      // Sort by walk encounter order; tie-break by distance.
      enriched.sort((a, b) => {
        if (a.encounter_index !== b.encounter_index) return a.encounter_index - b.encounter_index;
        return a.min_distance_m - b.min_distance_m;
      });

      // What's already saved for this tour? Find the auto_bake=false filter
      // bound to this tour (if any) so the dialog can pre-check the saved
      // POIs and mark the radius slider sensibly.
      const savedQ = await pgPool.query(
        `SELECT pf.id AS filter_id, pf.name AS filter_name,
                ARRAY(
                  SELECT pp.poi_id FROM salem_passport_pois pp
                   WHERE pp.filter_id = pf.id
                ORDER BY pp.display_order ASC
                ) AS poi_ids
           FROM salem_passport_filters pf
          WHERE pf.tour_id = $1
            AND pf.auto_bake = FALSE
       ORDER BY pf.updated_at DESC
          LIMIT 1`,
        [tourId],
      );

      res.json({
        tour: tourQ.rows[0],
        tour_polyline: polyline,
        polyline_length_pts: polyline.length,
        candidate_count: enriched.length,
        candidates: enriched,
        saved: savedQ.rows[0] || null,
        // S269 — surfaces which global pool the candidates were filtered
        // through, so the dialog can label the source ("filtered through
        // Default Salem Walking — 85 POIs in pool"). null when no global
        // passport is authored (fallback path used).
        pool: pool
          ? {
              id: pool.id,
              name: pool.name,
              categories: pool.categories,
              require_historical_narration: pool.require_historical_narration,
              min_geofence_radius_m: pool.min_geofence_radius_m,
              min_year_established: pool.min_year_established,
              max_year_established: pool.max_year_established,
            }
          : null,
      });
    } catch (err) {
      console.error('[AdminPassport] walk-candidates error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/tours/:tourId/walk-derived-passport ──────────────────
  //
  // S269 — save the operator's edited POI list as a walk-derived passport
  // for this tour. UPSERTs a salem_passport_filters row with auto_bake=false
  // (which tells publish-poi-passport.js to preserve the salem_passport_pois
  // rows on subsequent bakes), then replaces salem_passport_pois with the
  // supplied poi_ids in the supplied order.
  app.post('/admin/salem/tours/:tourId/walk-derived-passport', requirePg, async (req, res) => {
    const client = await pgPool.connect();
    try {
      const tourId = req.params.tourId;
      if (!tourId || typeof tourId !== 'string') {
        return res.status(400).json({ error: 'tourId required' });
      }
      const poiIds = Array.isArray(req.body?.poi_ids) ? req.body.poi_ids : null;
      if (!poiIds) return res.status(400).json({ error: 'poi_ids[] required' });
      const cleanIds = poiIds.filter((x) => typeof x === 'string' && x.trim()).map((x) => x.trim());
      // De-dup, preserve order.
      const seen = new Set();
      const orderedIds = [];
      for (const id of cleanIds) {
        if (!seen.has(id)) {
          seen.add(id);
          orderedIds.push(id);
        }
      }

      // Verify tour exists + load name for the synthetic filter row.
      const tourQ = await client.query(
        `SELECT id, name FROM salem_tours WHERE id = $1`,
        [tourId],
      );
      if (!tourQ.rows.length) {
        client.release();
        return res.status(404).json({ error: 'tour not found' });
      }

      // Derive deterministic slug for the filter id: <tour_id>_walk_passport.
      // Sanitize to lowercase + underscores in case the tour id carries
      // hyphens or capitals; matches VALID_ID_RE for the public passports
      // API.
      const filterId = `${tourId.toLowerCase().replace(/[^a-z0-9_]/g, '_')}_walk_passport`;
      const filterName = `${tourQ.rows[0].name} — Walk-derived Passport`;

      await client.query('BEGIN');

      // UPSERT the filter shell. categories=[] and require_historical_narration=true
      // mean the published filter row is consistent with the runtime
      // "historical only" semantics even though the bake won't re-run.
      await client.query(
        `INSERT INTO salem_passport_filters
           (id, name, tour_id, categories, require_historical_narration,
            min_geofence_radius_m, auto_bake, sort_order)
         VALUES ($1, $2, $3, '{}', TRUE, 0, FALSE, 0)
         ON CONFLICT (id) DO UPDATE
            SET name      = EXCLUDED.name,
                tour_id   = EXCLUDED.tour_id,
                auto_bake = FALSE,
                updated_at = NOW()`,
        [filterId, filterName, tourId],
      );

      // Replace salem_passport_pois rows for this filter.
      await client.query(`DELETE FROM salem_passport_pois WHERE filter_id = $1`, [filterId]);
      if (orderedIds.length) {
        const valuesSql = [];
        const args = [filterId];
        let argIdx = 2;
        for (let i = 0; i < orderedIds.length; i++) {
          valuesSql.push(`($1, $${argIdx++}, $${argIdx++})`);
          args.push(orderedIds[i], i);
        }
        await client.query(
          `INSERT INTO salem_passport_pois (filter_id, poi_id, display_order)
           VALUES ${valuesSql.join(', ')}`,
          args,
        );
      }

      await client.query('COMMIT');

      res.json({
        ok: true,
        filter_id: filterId,
        filter_name: filterName,
        poi_count: orderedIds.length,
        note: 'salem_passport_pois replaced. Re-run the publish chain (publish-poi-passport.js + align-asset-schema-to-room.js) to bake into salem_content.db.',
      });
    } catch (err) {
      try { await client.query('ROLLBACK'); } catch (_) {}
      console.error('[AdminPassport] walk-derived save error:', err.message);
      res.status(500).json({ error: err.message });
    } finally {
      client.release();
    }
  });
};

// Export for publish-poi-passport.js to reuse.
module.exports.buildFilterQuery = buildFilterQuery;
