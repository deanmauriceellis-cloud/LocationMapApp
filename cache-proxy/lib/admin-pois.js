/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin POI write endpoints (Phase 9U — unified salem_pois table).
 *
 * All routes are mounted under /admin/salem/pois/* and gated by the
 * /admin Basic Auth middleware (see lib/admin-auth.js, Phase 9P.3).
 *
 * Single unified table: salem_pois
 *   Replaces the three-kind dispatch (tour/business/narration) from
 *   Phase 9P.4. Filters by category, is_tour_poi, is_narrated, and
 *   default_visible instead of kind.
 *
 * Soft delete model: deleted_at column. Public reads in lib/salem.js
 * filter `deleted_at IS NULL`. Admin reads can opt in to including
 * deleted rows via ?include_deleted=true.
 *
 * Routes:
 *   GET    /admin/salem/pois?category=&is_tour_poi=&is_narrated=&s=&w=&n=&e=&include_deleted=&q=&limit=
 *   GET    /admin/salem/pois/duplicates?radius=15  (Phase 9P.5)
 *   GET    /admin/salem/pois/:id
 *   PUT    /admin/salem/pois/:id          — partial update
 *   POST   /admin/salem/pois/:id/move     — lat/lng-only update
 *   DELETE /admin/salem/pois/:id          — soft delete
 *   POST   /admin/salem/pois/:id/restore  — undo soft delete
 *
 * Validation:
 *   - lat ∈ [-90, 90], lng ∈ [-180, 180]
 *   - id is non-empty string
 *   - update fields are whitelisted (no arbitrary column writes)
 *   - JSONB fields are coerced via JSON.stringify before parameterization
 *   - category is non-empty when provided
 *
 * All write endpoints set updated_at = NOW() automatically.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-pois.js';

// ─── Unified field whitelist ────────────────────────────────────────────────

const UPDATABLE_FIELDS = [
  // Core identity
  'name', 'lat', 'lng', 'address', 'status',
  // Taxonomy
  'category', 'subcategory',
  // Narration
  'short_narration', 'long_narration', 'narration_pass_2', 'narration_pass_3',
  'geofence_radius_m', 'geofence_shape', 'corridor_points',
  'priority', 'wave',
  'voice_clip_asset', 'custom_voice_asset',
  // Business
  'cuisine_type', 'price_range', 'rating',
  'merchant_tier', 'ad_priority',
  // Historical/tour
  'historical_period', 'historical_note', 'admission_info',
  'requires_transportation', 'wheelchair_accessible', 'seasonal',
  // Contact/hours
  'phone', 'email', 'website',
  'hours', 'hours_text',
  'menu_url', 'reservations_url', 'order_url',
  // Content
  'description', 'short_description', 'custom_description', 'origin_story',
  'image_asset', 'custom_icon_asset',
  'action_buttons',
  // SalemIntelligence enrichment
  'intel_entity_id', 'secondary_categories', 'specialties',
  'owners', 'year_established', 'amenities', 'district',
  // Relations
  'related_figure_ids', 'related_fact_ids', 'related_source_ids',
  'source_id', 'source_categories', 'tags',
  // Provenance
  'data_source', 'confidence', 'verified_date', 'stale_after',
  // Flags
  'is_tour_poi', 'is_narrated', 'default_visible',
];

// Columns that hold JSONB and must be JSON.stringify'd before binding.
const JSONB_FIELDS = new Set([
  'hours', 'action_buttons',
  'secondary_categories', 'specialties', 'owners', 'amenities',
  'related_figure_ids', 'related_fact_ids', 'related_source_ids',
  'source_categories', 'tags',
]);

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

function getId(req, res) {
  const id = req.params.id;
  if (typeof id !== 'string' || !id.trim()) {
    res.status(400).json({ error: 'id is required' });
    return null;
  }
  return id;
}

/**
 * Build a parameterized SET clause from a body object, restricted to the
 * field whitelist. Returns { setSql, values, error }. JSONB fields are
 * stringified. Always appends `updated_at = NOW()` to the SET clause.
 */
function buildUpdateClause(body) {
  if (!body || typeof body !== 'object' || Array.isArray(body)) {
    return { error: 'request body must be a JSON object' };
  }

  const setParts = [];
  const values = [];
  let idx = 1;

  for (const field of UPDATABLE_FIELDS) {
    if (!Object.prototype.hasOwnProperty.call(body, field)) continue;
    let value = body[field];

    // Lat/lng range check
    if (field === 'lat' || field === 'lng') {
      const err = validateLatLng(field === 'lat' ? value : undefined,
                                 field === 'lng' ? value : undefined);
      if (err) return { error: err };
    }

    // Category presence check
    if (field === 'category' && (value === '' || value === null)) {
      return { error: 'category cannot be empty' };
    }

    // JSONB serialization
    if (JSONB_FIELDS.has(field)) {
      try {
        value = JSON.stringify(value ?? (field === 'amenities' ? {} : []));
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

  // ─── GET /admin/salem/pois — list with filters ─────────────────────────────
  app.get('/admin/salem/pois', requirePg, async (req, res) => {
    try {
      const {
        category, is_tour_poi, is_narrated, default_visible,
        s, w, n, e,
        include_deleted, q, limit = 2000,
      } = req.query;

      let sql = `SELECT * FROM salem_pois WHERE 1=1`;
      const params = [];
      let idx = 1;

      if (include_deleted !== 'true') {
        sql += ` AND deleted_at IS NULL`;
      }

      if (category) {
        sql += ` AND category = $${idx++}`;
        params.push(category);
      }

      if (is_tour_poi === 'true') sql += ` AND is_tour_poi = true`;
      if (is_tour_poi === 'false') sql += ` AND is_tour_poi = false`;
      if (is_narrated === 'true') sql += ` AND is_narrated = true`;
      if (is_narrated === 'false') sql += ` AND is_narrated = false`;
      if (default_visible === 'true') sql += ` AND default_visible = true`;
      if (default_visible === 'false') sql += ` AND default_visible = false`;

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

      sql += ` ORDER BY category ASC, priority ASC, name ASC LIMIT $${idx}`;
      const lim = Math.min(parseInt(limit) || 2000, 5000);
      params.push(lim);

      const { rows } = await pgPool.query(sql, params);
      res.json({ count: rows.length, pois: rows });
    } catch (err) {
      console.error('[AdminPois] list error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── GET /admin/salem/pois/duplicates — duplicate detection ────────────────
  app.get('/admin/salem/pois/duplicates', requirePg, async (req, res) => {
    try {
      const radiusRaw = parseFloat(req.query.radius || '15');
      if (!Number.isFinite(radiusRaw) || radiusRaw <= 0) {
        return res.status(400).json({ error: 'radius must be a positive number (meters)' });
      }
      const radius = Math.min(radiusRaw, 100);

      const latBoxDegrees = radius / 111000.0;
      const lngBoxDegrees = radius / 60000.0;

      const sql = `
        SELECT
          a.id AS a_id, a.name AS a_name, a.lat AS a_lat, a.lng AS a_lng,
          b.id AS b_id, b.name AS b_name, b.lat AS b_lat, b.lng AS b_lng,
          6371000.0 * 2.0 * ASIN(SQRT(
            POWER(SIN(RADIANS(b.lat - a.lat) / 2.0), 2) +
            COS(RADIANS(a.lat)) * COS(RADIANS(b.lat)) *
            POWER(SIN(RADIANS(b.lng - a.lng) / 2.0), 2)
          )) AS distance_m
        FROM salem_pois a
        JOIN salem_pois b
          ON a.id < b.id
         AND ABS(a.lat - b.lat) <= $2
         AND ABS(a.lng - b.lng) <= $3
        WHERE a.deleted_at IS NULL AND b.deleted_at IS NULL
          AND 6371000.0 * 2.0 * ASIN(SQRT(
                POWER(SIN(RADIANS(b.lat - a.lat) / 2.0), 2) +
                COS(RADIANS(a.lat)) * COS(RADIANS(b.lat)) *
                POWER(SIN(RADIANS(b.lng - a.lng) / 2.0), 2)
              )) <= $1
        ORDER BY distance_m ASC
      `;

      const { rows: pairs } = await pgPool.query(sql, [radius, latBoxDegrees, lngBoxDegrees]);

      if (pairs.length === 0) {
        return res.json({ radius_m: radius, count: 0, clusters: [] });
      }

      // Union-find clustering
      const parent = new Map();
      const nodeData = new Map();

      function find(x) {
        let root = x;
        while (parent.get(root) !== root) root = parent.get(root);
        let cur = x;
        while (parent.get(cur) !== root) {
          const next = parent.get(cur);
          parent.set(cur, root);
          cur = next;
        }
        return root;
      }

      function union(x, y) {
        const rx = find(x);
        const ry = find(y);
        if (rx !== ry) parent.set(rx, ry);
      }

      function ensureNode(id, name, lat, lng) {
        if (!parent.has(id)) {
          parent.set(id, id);
          nodeData.set(id, { id, name, lat: parseFloat(lat), lng: parseFloat(lng) });
        }
        return id;
      }

      for (const p of pairs) {
        ensureNode(p.a_id, p.a_name, p.a_lat, p.a_lng);
        ensureNode(p.b_id, p.b_name, p.b_lat, p.b_lng);
        union(p.a_id, p.b_id);
      }

      const clusters = new Map();
      for (const key of parent.keys()) {
        const root = find(key);
        if (!clusters.has(root)) clusters.set(root, []);
        clusters.get(root).push(nodeData.get(key));
      }

      function haversineMeters(lat1, lng1, lat2, lng2) {
        const toRad = (d) => d * Math.PI / 180;
        const R = 6371000;
        const dLat = toRad(lat2 - lat1);
        const dLng = toRad(lng2 - lng1);
        const a = Math.sin(dLat / 2) ** 2 +
                  Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
                  Math.sin(dLng / 2) ** 2;
        return R * 2 * Math.asin(Math.sqrt(a));
      }

      const result = [];
      for (const members of clusters.values()) {
        if (members.length < 2) continue;
        const centroidLat = members.reduce((s, m) => s + m.lat, 0) / members.length;
        const centroidLng = members.reduce((s, m) => s + m.lng, 0) / members.length;
        const enriched = members.map(m => ({
          ...m,
          distance_m_from_centroid: Math.round(
            haversineMeters(centroidLat, centroidLng, m.lat, m.lng) * 100
          ) / 100,
        }));
        enriched.sort((a, b) => a.distance_m_from_centroid - b.distance_m_from_centroid);
        result.push({
          centroid: { lat: centroidLat, lng: centroidLng },
          member_count: members.length,
          members: enriched,
        });
      }

      result.sort((a, b) => b.member_count - a.member_count);
      res.json({ radius_m: radius, count: result.length, clusters: result });
    } catch (err) {
      console.error('[AdminPois] duplicates error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── GET /admin/salem/pois/:id — single ──────────────────────────────────
  app.get('/admin/salem/pois/:id', requirePg, async (req, res) => {
    try {
      const id = getId(req, res);
      if (!id) return;

      const { rows } = await pgPool.query(
        `SELECT * FROM salem_pois WHERE id = $1`,
        [id]
      );
      if (!rows.length) return res.status(404).json({ error: 'Not found' });
      res.json(rows[0]);
    } catch (err) {
      console.error('[AdminPois] get single error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── PUT /admin/salem/pois/:id — partial update ──────────────────────────
  app.put('/admin/salem/pois/:id', requirePg, async (req, res) => {
    try {
      const id = getId(req, res);
      if (!id) return;

      const { setSql, values, error } = buildUpdateClause(req.body);
      if (error) return res.status(400).json({ error });

      const existing = await pgPool.query(
        `SELECT id, deleted_at FROM salem_pois WHERE id = $1`,
        [id]
      );
      if (!existing.rows.length) return res.status(404).json({ error: 'Not found' });
      if (existing.rows[0].deleted_at) {
        return res.status(409).json({ error: 'POI is soft-deleted; restore it before updating' });
      }

      values.push(id);
      const { rows } = await pgPool.query(
        `UPDATE salem_pois SET ${setSql} WHERE id = $${values.length} RETURNING *`,
        values
      );
      res.json(rows[0]);
    } catch (err) {
      console.error('[AdminPois] update error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/pois/:id/move — lat/lng only ──────────────────────
  app.post('/admin/salem/pois/:id/move', requirePg, async (req, res) => {
    try {
      const id = getId(req, res);
      if (!id) return;

      const { lat, lng } = req.body || {};
      if (lat === undefined || lng === undefined) {
        return res.status(400).json({ error: 'lat and lng are required' });
      }
      const err = validateLatLng(lat, lng);
      if (err) return res.status(400).json({ error: err });

      const existing = await pgPool.query(
        `SELECT id, lat, lng, deleted_at FROM salem_pois WHERE id = $1`,
        [id]
      );
      if (!existing.rows.length) return res.status(404).json({ error: 'Not found' });
      if (existing.rows[0].deleted_at) {
        return res.status(409).json({ error: 'POI is soft-deleted; restore it before moving' });
      }

      const oldLat = parseFloat(existing.rows[0].lat);
      const oldLng = parseFloat(existing.rows[0].lng);

      const { rows } = await pgPool.query(
        `UPDATE salem_pois SET lat = $1, lng = $2, updated_at = NOW() WHERE id = $3 RETURNING lat, lng`,
        [lat, lng, id]
      );

      res.json({
        id,
        from: { lat: oldLat, lng: oldLng },
        to: { lat: parseFloat(rows[0].lat), lng: parseFloat(rows[0].lng) },
      });
    } catch (err) {
      console.error('[AdminPois] move error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── DELETE /admin/salem/pois/:id — soft delete ──────────────────────────
  app.delete('/admin/salem/pois/:id', requirePg, async (req, res) => {
    try {
      const id = getId(req, res);
      if (!id) return;

      const { rows } = await pgPool.query(
        `UPDATE salem_pois
            SET deleted_at = NOW(), updated_at = NOW()
          WHERE id = $1 AND deleted_at IS NULL
          RETURNING id, deleted_at`,
        [id]
      );
      if (!rows.length) {
        const check = await pgPool.query(
          `SELECT id, deleted_at FROM salem_pois WHERE id = $1`,
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

  // ─── POST /admin/salem/pois/:id/restore — undo soft delete ───────────────
  app.post('/admin/salem/pois/:id/restore', requirePg, async (req, res) => {
    try {
      const id = getId(req, res);
      if (!id) return;

      const { rows } = await pgPool.query(
        `UPDATE salem_pois
            SET deleted_at = NULL, updated_at = NOW()
          WHERE id = $1 AND deleted_at IS NOT NULL
          RETURNING id`,
        [id]
      );
      if (!rows.length) {
        const check = await pgPool.query(
          `SELECT id, deleted_at FROM salem_pois WHERE id = $1`,
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
module.exports.UPDATABLE_FIELDS = UPDATABLE_FIELDS;
module.exports.JSONB_FIELDS = JSONB_FIELDS;
