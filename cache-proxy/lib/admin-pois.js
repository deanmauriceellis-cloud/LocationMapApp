/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin POI write endpoints (Phase 9P.4).
 *
 * All routes are mounted under /admin/salem/pois/* and gated by the
 * /admin Basic Auth middleware (see lib/admin-auth.js, Phase 9P.3).
 *
 * Three POI kinds, three tables:
 *   tour       → salem_tour_pois
 *   business   → salem_businesses
 *   narration  → salem_narration_points
 *
 * Soft delete model: every table has a deleted_at column. Public reads in
 * lib/salem.js filter `deleted_at IS NULL`. Admin reads can opt in to
 * including deleted rows via ?include_deleted=true.
 *
 * Routes:
 *   GET    /admin/salem/pois?kind=&category=&s=&w=&n=&e=&include_deleted=
 *          (kind required; category and bbox optional)
 *   GET    /admin/salem/pois/:kind/:id
 *   PUT    /admin/salem/pois/:kind/:id          — partial update
 *   POST   /admin/salem/pois/:kind/:id/move     — lat/lng-only update
 *   DELETE /admin/salem/pois/:kind/:id          — soft delete
 *   POST   /admin/salem/pois/:kind/:id/restore  — undo soft delete
 *
 * Validation:
 *   - lat ∈ [-90, 90], lng ∈ [-180, 180]
 *   - id is non-empty string
 *   - update fields are whitelisted per kind (no arbitrary column writes)
 *   - JSONB fields are coerced via JSON.stringify before parameterization
 *   - category is non-empty when provided. Full taxonomy validation deferred
 *     to Phase 9P.6 (TypeScript port of PoiCategories.kt).
 *
 * All write endpoints set updated_at = NOW() automatically.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-pois.js';

// ─── Per-kind config ─────────────────────────────────────────────────────────

// Whitelisted update fields per kind. Provenance fields (data_source,
// confidence, verified_date, stale_after) are intentionally writable so the
// admin tool can mark a POI as freshly verified or override a low confidence.
// Timestamps (created_at, updated_at, deleted_at) are NOT in the whitelist —
// they are managed by the endpoints themselves.
const TOUR_FIELDS = [
  'name', 'lat', 'lng', 'address', 'category', 'subcategories',
  'short_narration', 'long_narration', 'description', 'historical_period',
  'admission_info', 'hours', 'phone', 'website', 'image_asset',
  'geofence_radius_m', 'requires_transportation', 'wheelchair_accessible',
  'seasonal', 'priority',
  'data_source', 'confidence', 'verified_date', 'stale_after',
];

const BUSINESS_FIELDS = [
  'name', 'lat', 'lng', 'address', 'business_type', 'cuisine_type',
  'price_range', 'hours', 'phone', 'website', 'description',
  'historical_note', 'tags', 'rating', 'image_asset',
  'data_source', 'confidence', 'verified_date', 'stale_after',
];

const NARRATION_FIELDS = [
  'name', 'lat', 'lng', 'address', 'category', 'subcategory',
  'short_narration', 'long_narration', 'description',
  'pass1_narration', 'pass2_narration', 'pass3_narration',
  'geofence_radius_m', 'geofence_shape', 'corridor_points',
  'priority', 'wave', 'phone', 'website', 'hours',
  'image_asset', 'voice_clip_asset',
  'related_figure_ids', 'related_fact_ids', 'related_source_ids',
  'action_buttons',
  'merchant_tier', 'ad_priority',
  'custom_icon_asset', 'custom_voice_asset', 'custom_description',
  'source_id', 'source_categories', 'tags',
  'data_source', 'confidence', 'verified_date', 'stale_after',
];

// Columns that hold JSONB and must be JSON.stringify'd before binding.
const JSONB_FIELDS = new Set([
  'subcategories', 'tags', 'related_figure_ids', 'related_fact_ids',
  'related_source_ids', 'action_buttons', 'source_categories',
]);

const KINDS = {
  tour: {
    table: 'salem_tour_pois',
    categoryColumn: 'category',
    updatableFields: TOUR_FIELDS,
    listOrderBy: 'priority ASC, name ASC',
  },
  business: {
    table: 'salem_businesses',
    categoryColumn: 'business_type',
    updatableFields: BUSINESS_FIELDS,
    listOrderBy: 'name ASC',
  },
  narration: {
    table: 'salem_narration_points',
    categoryColumn: 'category',
    updatableFields: NARRATION_FIELDS,
    listOrderBy: 'wave ASC NULLS LAST, priority ASC, name ASC',
  },
};

// ─── Validation helpers ──────────────────────────────────────────────────────

function isFiniteNumber(v) {
  return typeof v === 'number' && Number.isFinite(v);
}

function validateLatLng(lat, lng) {
  if (lat !== undefined) {
    if (!isFiniteNumber(lat) || lat < -90 || lat > 90) {
      return 'lat must be a finite number in [-90, 90]';
    }
  }
  if (lng !== undefined) {
    if (!isFiniteNumber(lng) || lng < -180 || lng > 180) {
      return 'lng must be a finite number in [-180, 180]';
    }
  }
  return null;
}

function getKindConfig(req, res) {
  const cfg = KINDS[req.params.kind];
  if (!cfg) {
    res.status(400).json({ error: `kind must be one of: ${Object.keys(KINDS).join(', ')}` });
    return null;
  }
  return cfg;
}

function getId(req, res) {
  const id = req.params.id;
  if (typeof id !== 'string' || !id.trim()) {
    res.status(400).json({ error: 'id is required' });
    return null;
  }
  return id;
}

/**
 * Build a parameterized SET clause from a body object, restricted to a
 * field whitelist. Returns { setSql, values, error }. JSONB fields are
 * stringified. Always appends `updated_at = NOW()` to the SET clause.
 */
function buildUpdateClause(body, whitelist) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    return { error: 'request body must be a JSON object' };
  }

  const setParts = [];
  const values = [];
  let idx = 1;

  for (const field of whitelist) {
    if (!Object.prototype.hasOwnProperty.call(body, field)) continue;
    let value = body[field];

    // Lat/lng range check on update
    if (field === 'lat' || field === 'lng') {
      const err = validateLatLng(field === 'lat' ? value : undefined,
                                 field === 'lng' ? value : undefined);
      if (err) return { error: err };
    }

    // Category presence check (admin tool can clear other strings, but not
    // category — leave a POI without a category and the app filtering breaks)
    if ((field === 'category' || field === 'business_type') &&
        (value === '' || value === null)) {
      return { error: `${field} cannot be empty` };
    }

    // JSONB serialization
    if (JSONB_FIELDS.has(field)) {
      try {
        value = JSON.stringify(value ?? []);
      } catch (e) {
        return { error: `${field}: invalid JSON value (${e.message})` };
      }
      setParts.push(`${field} = $${idx}::jsonb`);
    } else {
      setParts.push(`${field} = $${idx}`);
    }

    values.push(value);
    idx++;
  }

  if (setParts.length === 0) {
    return { error: 'no updatable fields provided' };
  }

  setParts.push(`updated_at = NOW()`);
  return { setSql: setParts.join(', '), values };
}

// ─── Module export ───────────────────────────────────────────────────────────

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // Note: /admin/* is already gated by requireBasicAuth (mounted in server.js
  // BEFORE this module loads). No need to apply per-route here.

  // ─── GET /admin/salem/pois — list with filters ─────────────────────────────
  app.get('/admin/salem/pois', requirePg, async (req, res) => {
    try {
      const { kind, category, s, w, n, e, include_deleted, q, limit = 500 } = req.query;

      if (!kind) {
        return res.status(400).json({ error: 'kind query param is required' });
      }
      const cfg = KINDS[kind];
      if (!cfg) {
        return res.status(400).json({ error: `kind must be one of: ${Object.keys(KINDS).join(', ')}` });
      }

      let sql = `SELECT * FROM ${cfg.table} WHERE 1=1`;
      const params = [];
      let idx = 1;

      if (include_deleted !== 'true') {
        sql += ` AND deleted_at IS NULL`;
      }

      if (category) {
        sql += ` AND ${cfg.categoryColumn} = $${idx++}`;
        params.push(category);
      }

      if (s && w && n && e) {
        const sF = parseFloat(s), nF = parseFloat(n), wF = parseFloat(w), eF = parseFloat(e);
        if ([sF, nF, wF, eF].some(v => !Number.isFinite(v))) {
          return res.status(400).json({ error: 'bbox params s,w,n,e must be numeric' });
        }
        sql += ` AND lat BETWEEN $${idx} AND $${idx + 1} AND lng BETWEEN $${idx + 2} AND $${idx + 3}`;
        params.push(sF, nF, wF, eF);
        idx += 4;
      }

      if (q) {
        sql += ` AND (name ILIKE $${idx} OR COALESCE(description,'') ILIKE $${idx})`;
        params.push(`%${q}%`);
        idx++;
      }

      sql += ` ORDER BY ${cfg.listOrderBy} LIMIT $${idx}`;
      const lim = Math.min(parseInt(limit) || 500, 5000);
      params.push(lim);

      const { rows } = await pgPool.query(sql, params);
      res.json({ kind, count: rows.length, pois: rows });
    } catch (err) {
      console.error('[AdminPois] list error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── GET /admin/salem/pois/:kind/:id — single ──────────────────────────────
  app.get('/admin/salem/pois/:kind/:id', requirePg, async (req, res) => {
    try {
      const cfg = getKindConfig(req, res);
      if (!cfg) return;
      const id = getId(req, res);
      if (!id) return;

      // Admin can see deleted rows via this endpoint by default — admin
      // workflows often need to inspect a tombstoned POI before restoring.
      const { rows } = await pgPool.query(
        `SELECT * FROM ${cfg.table} WHERE id = $1`,
        [id]
      );
      if (!rows.length) return res.status(404).json({ error: 'Not found' });
      res.json(rows[0]);
    } catch (err) {
      console.error('[AdminPois] get single error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── PUT /admin/salem/pois/:kind/:id — partial update ──────────────────────
  app.put('/admin/salem/pois/:kind/:id', requirePg, async (req, res) => {
    try {
      const cfg = getKindConfig(req, res);
      if (!cfg) return;
      const id = getId(req, res);
      if (!id) return;

      const { setSql, values, error } = buildUpdateClause(req.body, cfg.updatableFields);
      if (error) return res.status(400).json({ error });

      // Refuse to update a soft-deleted row — must restore first
      const existing = await pgPool.query(
        `SELECT id, deleted_at FROM ${cfg.table} WHERE id = $1`,
        [id]
      );
      if (!existing.rows.length) return res.status(404).json({ error: 'Not found' });
      if (existing.rows[0].deleted_at) {
        return res.status(409).json({ error: 'POI is soft-deleted; restore it before updating' });
      }

      values.push(id);
      const { rows } = await pgPool.query(
        `UPDATE ${cfg.table} SET ${setSql} WHERE id = $${values.length} RETURNING *`,
        values
      );
      res.json(rows[0]);
    } catch (err) {
      console.error('[AdminPois] update error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/pois/:kind/:id/move — lat/lng only ──────────────────
  app.post('/admin/salem/pois/:kind/:id/move', requirePg, async (req, res) => {
    try {
      const cfg = getKindConfig(req, res);
      if (!cfg) return;
      const id = getId(req, res);
      if (!id) return;

      const { lat, lng } = req.body || {};
      if (lat === undefined || lng === undefined) {
        return res.status(400).json({ error: 'lat and lng are required' });
      }
      const err = validateLatLng(lat, lng);
      if (err) return res.status(400).json({ error: err });

      const existing = await pgPool.query(
        `SELECT id, lat, lng, deleted_at FROM ${cfg.table} WHERE id = $1`,
        [id]
      );
      if (!existing.rows.length) return res.status(404).json({ error: 'Not found' });
      if (existing.rows[0].deleted_at) {
        return res.status(409).json({ error: 'POI is soft-deleted; restore it before moving' });
      }

      const oldLat = parseFloat(existing.rows[0].lat);
      const oldLng = parseFloat(existing.rows[0].lng);

      const { rows } = await pgPool.query(
        `UPDATE ${cfg.table} SET lat = $1, lng = $2, updated_at = NOW() WHERE id = $3 RETURNING lat, lng`,
        [lat, lng, id]
      );

      res.json({
        id,
        kind: req.params.kind,
        from: { lat: oldLat, lng: oldLng },
        to: { lat: parseFloat(rows[0].lat), lng: parseFloat(rows[0].lng) },
      });
    } catch (err) {
      console.error('[AdminPois] move error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── DELETE /admin/salem/pois/:kind/:id — soft delete ──────────────────────
  app.delete('/admin/salem/pois/:kind/:id', requirePg, async (req, res) => {
    try {
      const cfg = getKindConfig(req, res);
      if (!cfg) return;
      const id = getId(req, res);
      if (!id) return;

      const { rows } = await pgPool.query(
        `UPDATE ${cfg.table}
            SET deleted_at = NOW(), updated_at = NOW()
          WHERE id = $1 AND deleted_at IS NULL
          RETURNING id, deleted_at`,
        [id]
      );
      if (!rows.length) {
        // Either the row doesn't exist, or it was already deleted. Distinguish.
        const check = await pgPool.query(
          `SELECT id, deleted_at FROM ${cfg.table} WHERE id = $1`,
          [id]
        );
        if (!check.rows.length) return res.status(404).json({ error: 'Not found' });
        return res.status(409).json({ error: 'POI is already soft-deleted', deleted_at: check.rows[0].deleted_at });
      }
      res.json({ id: rows[0].id, deleted_at: rows[0].deleted_at });
    } catch (err) {
      console.error('[AdminPois] delete error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/pois/:kind/:id/restore — undo soft delete ───────────
  app.post('/admin/salem/pois/:kind/:id/restore', requirePg, async (req, res) => {
    try {
      const cfg = getKindConfig(req, res);
      if (!cfg) return;
      const id = getId(req, res);
      if (!id) return;

      const { rows } = await pgPool.query(
        `UPDATE ${cfg.table}
            SET deleted_at = NULL, updated_at = NOW()
          WHERE id = $1 AND deleted_at IS NOT NULL
          RETURNING id`,
        [id]
      );
      if (!rows.length) {
        const check = await pgPool.query(
          `SELECT id, deleted_at FROM ${cfg.table} WHERE id = $1`,
          [id]
        );
        if (!check.rows.length) return res.status(404).json({ error: 'Not found' });
        return res.status(409).json({ error: 'POI is not soft-deleted; nothing to restore' });
      }
      res.json({ id: rows[0].id, restored: true });
    } catch (err) {
      console.error('[AdminPois] restore error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });
};

// Exposed for unit tests / introspection
module.exports.KINDS = KINDS;
module.exports.JSONB_FIELDS = JSONB_FIELDS;
