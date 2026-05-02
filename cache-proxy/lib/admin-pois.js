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

const {
  tigerPool,
  geocodeOneAddress,
  snapToNearestEdge,
  findEdgeByStreetName,
  extractStreetFromAddress,
  haversineKm,
} = require('./tiger-geocode');

// S218 — Salem-area sanity filter. Tiger.geocode can match a fuzzy
// "Wharf St, Salem MA" to a Wharf St in another town when the city/zip
// match is weak (operator hit a 33 km proposal mid-S218 review, rating 18).
// For the per-POI Validate workflow, candidates outside the Salem area are
// almost always wrong-town hits — drop them and let the operator fix the
// address. Center matches admin-lint.js. 15 km radius covers Salem,
// Beverly, Peabody, Danvers, Marblehead, Lynn — anywhere a Salem-listed
// POI could plausibly sit.
const VALIDATE_SALEM_CENTER_LAT = 42.5223;
const VALIDATE_SALEM_CENTER_LNG = -70.8950;
const VALIDATE_SALEM_RADIUS_M = 15000;

// ─── Unified field whitelist ────────────────────────────────────────────────

const UPDATABLE_FIELDS = [
  // Core identity
  'name', 'lat', 'lng', 'address', 'status',
  // Taxonomy
  'category', 'subcategory',
  // Narration
  'short_narration', 'long_narration', 'historical_narration',
  // S219 — JSONB array of {header, body, source_kind?, source_ref?}; powers
  // scrollable subtopic cards on the POI detail sheet (storytelling-with-subtopics rule)
  'narration_subtopics',
  'geofence_radius_m', 'geofence_shape', 'corridor_points',
  'priority', 'wave',
  'voice_clip_asset', 'custom_voice_asset',
  // Business
  'cuisine_type', 'price_range', 'rating',
  'merchant_tier', 'ad_priority',
  // Historical/tour
  'historical_period', 'admission_info',
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
  // Operational flags
  'is_tour_poi', 'is_civic_poi', 'is_narrated', 'default_visible', 'has_announce_narration',
  // Classification flags (S165)
  'is_historical_property', 'is_witch_trial_site', 'is_free_admission',
  'is_indoor', 'is_family_friendly',
  // Phase 9Y — MassGIS / MHC Inventory / L3 parcel enrichment (added S159 for schema,
  // exposed to admin S160). `building_footprint_geojson` is polygon-shaped and
  // normally populated by the 9Y.3 enrichment script; manual override allowed.
  'building_footprint_geojson',
  'mhc_id', 'mhc_year_built', 'mhc_style', 'mhc_nr_status', 'mhc_narrative',
  'canonical_address_point_id', 'local_historic_district', 'parcel_owner_class',
  // S162 — POI location verification (TigerLine/MassGIS workflow).
  // `location_truth_of_record` lets a reviewer pin a POI's lat/lng so the
  // batch verifier never overwrites it. `location_status` is normally set
  // by the verifier ('verified' | 'needs_review' | 'no_match' | 'no_address')
  // but exposed here so the admin can re-flag a row manually.
  // Proposed coords (lat_proposed/lng_proposed) and provenance fields
  // (location_source/location_drift_m/location_geocoder_rating/location_verified_at)
  // are verifier-owned and intentionally NOT in the whitelist — use the
  // /accept-proposed-location endpoint to commit a proposal.
  'location_truth_of_record', 'location_status',
];

// Columns that hold JSONB and must be JSON.stringify'd before binding.
const JSONB_FIELDS = new Set([
  'hours', 'action_buttons',
  'secondary_categories', 'specialties', 'owners', 'amenities',
  'related_figure_ids', 'related_fact_ids', 'related_source_ids',
  'source_categories', 'tags',
  'narration_subtopics',
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

  // S199: when category changes and the caller didn't explicitly set
  // is_civic_poi, auto-sync the flag to the new category. Drift between the
  // two was the root cause of M&M Contractors and ~178 other recategorized
  // POIs still narrating during tour-mode under the Civic layer.
  const bodyHasCategory = Object.prototype.hasOwnProperty.call(body, 'category');
  const bodyHasCivic = Object.prototype.hasOwnProperty.call(body, 'is_civic_poi');
  if (bodyHasCategory && !bodyHasCivic) {
    const cat = String(body.category || '').toUpperCase();
    setParts.push(`is_civic_poi = $${idx}`);
    values.push(cat === 'CIVIC');
    idx++;
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
      // S125: stamp admin_dirty on every admin field edit so bulk rebuild
      // scripts (re-narrate, re-icon, re-geofence) can find POIs that
      // changed via the operator tool. Bulk scripts themselves MUST NOT
      // flip this flag — admin_dirty tracks human edits only.
      const { rows } = await pgPool.query(
        `UPDATE salem_pois
            SET ${setSql},
                admin_dirty = TRUE,
                admin_dirty_at = NOW()
          WHERE id = $${values.length}
          RETURNING *`,
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

      // S125: move is an admin edit — stamp dirty so downstream rebuilds
      // (geofence corridor recalc, route regeneration, etc.) can find it.
      const { rows } = await pgPool.query(
        `UPDATE salem_pois
            SET lat = $1,
                lng = $2,
                admin_dirty = TRUE,
                admin_dirty_at = NOW(),
                updated_at = NOW()
          WHERE id = $3
          RETURNING lat, lng`,
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

  // ─── POST /admin/salem/pois/:id/accept-proposed-location ─────────────────
  // S162: Commit a verifier-proposed coordinate. Copies lat_proposed/lng_proposed
  // into lat/lng, marks status='accepted', clears the proposal columns. Mirrors
  // /move semantics (admin_dirty stamped). 409 if there's no proposal to accept.
  //
  // S218 — body {lat, lng} optional override. The proposal-review UI lets the
  // operator drag the proposed pin before accepting; the dragged position is
  // sent in the body and committed instead of the stored lat_proposed. Without
  // a body, behavior is unchanged from S162.
  app.post('/admin/salem/pois/:id/accept-proposed-location', requirePg, async (req, res) => {
    try {
      const id = getId(req, res);
      if (!id) return;

      const overrideLat = req.body && req.body.lat != null ? Number(req.body.lat) : null;
      const overrideLng = req.body && req.body.lng != null ? Number(req.body.lng) : null;
      const hasOverride = overrideLat != null && overrideLng != null
        && Number.isFinite(overrideLat) && Number.isFinite(overrideLng);
      if (hasOverride && (overrideLat < -90 || overrideLat > 90 || overrideLng < -180 || overrideLng > 180)) {
        return res.status(400).json({ error: 'lat/lng out of range' });
      }

      const existing = await pgPool.query(
        `SELECT id, lat, lng, lat_proposed, lng_proposed, deleted_at
           FROM salem_pois WHERE id = $1`,
        [id]
      );
      if (!existing.rows.length) return res.status(404).json({ error: 'Not found' });
      const row = existing.rows[0];
      if (row.deleted_at) {
        return res.status(409).json({ error: 'POI is soft-deleted; restore it before accepting' });
      }
      if (!hasOverride && (row.lat_proposed == null || row.lng_proposed == null)) {
        return res.status(409).json({ error: 'No proposed coordinates to accept' });
      }

      const finalLat = hasOverride ? overrideLat : parseFloat(row.lat_proposed);
      const finalLng = hasOverride ? overrideLng : parseFloat(row.lng_proposed);

      const { rows } = await pgPool.query(
        `UPDATE salem_pois
            SET lat = $2,
                lng = $3,
                location_status = 'accepted',
                lat_proposed = NULL,
                lng_proposed = NULL,
                location_drift_m = NULL,
                location_geocoder_rating = NULL,
                location_verified_at = NOW(),
                admin_dirty = TRUE,
                admin_dirty_at = NOW(),
                updated_at = NOW()
          WHERE id = $1
          RETURNING lat, lng`,
        [id, finalLat, finalLng]
      );

      res.json({
        id,
        from: { lat: parseFloat(row.lat), lng: parseFloat(row.lng) },
        to: { lat: parseFloat(rows[0].lat), lng: parseFloat(rows[0].lng) },
        location_status: 'accepted',
        used_override: hasOverride,
      });
    } catch (err) {
      console.error('[AdminPois] accept-proposed-location error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/pois/:id/discard-proposed-location ────────────────
  // S218: Operator clicked Cancel in the proposal-review panel. Clears the
  // proposal columns and resets location_status to 'unverified' so the next
  // Validate run starts clean. Does NOT touch live lat/lng.
  app.post('/admin/salem/pois/:id/discard-proposed-location', requirePg, async (req, res) => {
    try {
      const id = getId(req, res);
      if (!id) return;

      const { rows } = await pgPool.query(
        `UPDATE salem_pois
            SET lat_proposed = NULL,
                lng_proposed = NULL,
                location_source = NULL,
                location_status = 'unverified',
                location_drift_m = NULL,
                location_geocoder_rating = NULL,
                updated_at = NOW()
          WHERE id = $1
          RETURNING id, lat_proposed, lng_proposed, location_status`,
        [id]
      );
      if (!rows.length) return res.status(404).json({ error: 'Not found' });
      res.json({ id, location_status: rows[0].location_status });
    } catch (err) {
      console.error('[AdminPois] discard-proposed-location error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/pois/:id/validate-location-tiger ──────────────────
  // S218: Per-POI TigerLine validation. Reads the POI's address, runs
  // tiger.geocode() with the same variant ladder as the lint deep scan,
  // snaps the best candidate to the nearest tiger.edges centerline so the
  // proposal sits literally on the road, and writes
  // lat_proposed/lng_proposed/location_source='tigerline'/drift/rating.
  // Does NOT touch live lat/lng — operator commits via the existing
  // /accept-proposed-location endpoint after eyeballing the proposal on
  // the admin map.
  //
  // Response shapes:
  //   200 { status: 'proposed', proposal: {...}, poi: {...} }
  //   200 { status: 'no_match', warnings: [...] }   — Tiger returned 0 candidates
  //   400 { error: 'no_address' }                   — POI has no usable address
  //   404 { error: 'Not found' }
  //   409 { error: 'POI is soft-deleted' }
  app.post('/admin/salem/pois/:id/validate-location-tiger', requirePg, async (req, res) => {
    let tigerClient = null;
    const reqStart = Date.now();
    try {
      const id = getId(req, res);
      if (!id) return;
      const tag = `[validate-tiger ${id}]`;
      console.log(`${tag} ───────────────────────────────────────────────────────────`);
      console.log(`${tag} START`);

      const existing = await pgPool.query(
        `SELECT id, name, lat, lng, address, deleted_at
           FROM salem_pois WHERE id = $1`,
        [id]
      );
      if (!existing.rows.length) {
        console.log(`${tag} POI not found → 404`);
        return res.status(404).json({ error: 'Not found' });
      }
      const poi = existing.rows[0];
      console.log(`${tag} POI name="${poi.name}" current=(${Number(poi.lat).toFixed(5)}, ${Number(poi.lng).toFixed(5)}) address="${poi.address}"`);
      if (poi.deleted_at) {
        console.log(`${tag} POI is soft-deleted → 409`);
        return res.status(409).json({ error: 'POI is soft-deleted; restore it before validating' });
      }
      if (!poi.address || !String(poi.address).trim()) {
        console.log(`${tag} POI has no address → 400 no_address`);
        return res.status(400).json({ error: 'no_address', message: 'POI has no usable address to geocode' });
      }

      tigerClient = await tigerPool().connect();
      // Per-call statement_timeout matches the lint flow (5s — the modal-
      // budget tightening from S188). A single-POI validate doesn't need
      // the 15s ceiling the deep scan uses.
      await tigerClient.query(`SET statement_timeout = '5s'`);

      const lat0 = poi.lat != null ? parseFloat(poi.lat) : null;
      const lng0 = poi.lng != null ? parseFloat(poi.lng) : null;
      const { candidates, warnings } = await geocodeOneAddress(
        tigerClient, poi.address, lat0, lng0, 3, `${tag} [geocode]`
      );

      if (candidates.length === 0) {
        console.log(`${tag} geocode returned ZERO candidates`);
        tigerClient.release();
        tigerClient = null;
        console.log(`${tag} END status=no_match (${Date.now() - reqStart}ms)`);
        return res.json({ status: 'no_match', warnings });
      }

      // S218 — drop candidates outside the Salem area (15 km radius). Tiger
      // can return high-rating matches for a wrong-town street when the city
      // tag is fuzzy; those land kilometers away and would write a bogus
      // proposal. Annotate dropped candidates in warnings so the operator
      // can tell address-fix from no-match-at-all.
      const inArea = [];
      console.log(`${tag} Salem-area filter: ${candidates.length} candidates, radius ${VALIDATE_SALEM_RADIUS_M}m`);
      for (const c of candidates) {
        const dFromSalem = haversineKm(
          VALIDATE_SALEM_CENTER_LAT, VALIDATE_SALEM_CENTER_LNG, c.lat, c.lng,
        ) * 1000;
        const inSalem = dFromSalem <= VALIDATE_SALEM_RADIUS_M;
        console.log(`${tag}   r${c.rating} (${c.lat.toFixed(5)}, ${c.lng.toFixed(5)}) ${(dFromSalem/1000).toFixed(1)}km from Salem ${inSalem ? '✓ kept' : '✗ DROPPED ' + c.normalized_address}`);
        if (inSalem) {
          inArea.push(c);
        } else {
          warnings.push(
            `Dropped Tiger candidate (rating ${c.rating}, ${(dFromSalem / 1000).toFixed(1)} km from Salem) — likely wrong-town match for "${c.normalized_address}".`
          );
        }
      }
      console.log(`${tag} Salem-area filter result: ${inArea.length} kept / ${candidates.length - inArea.length} dropped`);
      // S218 — street-name fallback. When tiger.geocode can't land on a
      // Salem-area candidate (Tiger has the street but not the house number,
      // or has the street only outside Salem), search tiger.edges by street
      // name within 2 km of the POI's current coords. Lets us still propose
      // a useful location for POIs like Sea Level Oyster Bar (94 Wharf St,
      // Salem — Tiger's Salem Wharf St addresses go 1–72 only). The
      // candidate is on the actual named street near the POI, so the
      // operator can drag to fine-tune the building location.
      if (inArea.length === 0) {
        const street = extractStreetFromAddress(poi.address);
        console.log(`${tag} street fallback: extracted street="${street}" from address="${poi.address}"`);
        if (lat0 != null && lng0 != null && street) {
          const fb = await findEdgeByStreetName(tigerClient, street, lat0, lng0, 2000, `${tag} [fallback]`);
          if (fb) {
            tigerClient.release();
            tigerClient = null;
            const driftM = Math.round(haversineKm(lat0, lng0, fb.lat, fb.lng) * 1000);
            const fallbackWarning =
              `Tiger has no house-numbered match for "${poi.address}" in Salem. ` +
              `Snapped to ${fb.edge_fullname} ${fb.snap_distance_m} m from current coords. ` +
              `Drag the pin to the actual building before accepting.`;
            warnings.push(fallbackWarning);
            const updated = await pgPool.query(
              `UPDATE salem_pois
                  SET lat_proposed = $2,
                      lng_proposed = $3,
                      location_source = 'tigerline_street_fallback',
                      location_status = 'needs_review',
                      location_drift_m = $4,
                      location_geocoder_rating = NULL,
                      location_verified_at = NOW(),
                      updated_at = NOW()
                WHERE id = $1
                RETURNING id, lat, lng, lat_proposed, lng_proposed,
                          location_source, location_status, location_drift_m,
                          location_geocoder_rating, location_verified_at,
                          location_truth_of_record`,
              [id, fb.lat, fb.lng, driftM]
            );
            console.log(`${tag} END status=proposed (street_fallback) drift=${driftM}m source=tigerline_street_fallback (${Date.now() - reqStart}ms)`);
            return res.json({
              status: 'proposed',
              proposal: {
                lat: fb.lat,
                lng: fb.lng,
                rating: null,
                drift_m: driftM,
                normalized_address: `${street} (street fallback — no house number in Tiger)`,
                snapped_to_edge: {
                  tlid: fb.edge_tlid,
                  fullname: fb.edge_fullname,
                  snap_distance_m: fb.snap_distance_m,
                },
                candidate_count: 0,
                fallback: 'street_name_only',
              },
              warnings,
              poi: updated.rows[0],
            });
          }
          console.log(`${tag} street fallback found no edge near POI`);
        } else {
          console.log(`${tag} cannot run street fallback (lat0=${lat0} lng0=${lng0} street=${street})`);
        }
        tigerClient.release();
        tigerClient = null;
        console.log(`${tag} END status=no_match (Salem filter dropped all + fallback exhausted) (${Date.now() - reqStart}ms)`);
        return res.json({
          status: 'no_match',
          warnings: warnings.length
            ? warnings
            : ['No Tiger candidates within 15 km of Salem.'],
        });
      }

      // Lowest rating wins (0 = exact house-number match, 100 = city centroid).
      const best = inArea[0];
      console.log(`${tag} BEST candidate: r${best.rating} (${best.lat.toFixed(5)}, ${best.lng.toFixed(5)}) "${best.normalized_address}"`);

      // Snap to nearest street centerline within 50m. If the geocode result
      // is already on a road, the snap moves it 0–8 m onto the centerline;
      // if no edge is within 50m (rare — open lot, lakeside), we keep the
      // raw Tiger point.
      const snapped = await snapToNearestEdge(tigerClient, best.lat, best.lng, 50, `${tag} [snap]`);
      const finalLat = snapped ? snapped.lat : best.lat;
      const finalLng = snapped ? snapped.lng : best.lng;

      tigerClient.release();
      tigerClient = null;

      const driftM = lat0 != null && lng0 != null
        ? Math.round(haversineKm(lat0, lng0, finalLat, finalLng) * 1000)
        : null;

      // Status: verified if rating < 5 AND drift < 10m, else needs_review.
      // Operator confirms via Accept proposed coordinates regardless.
      const newStatus = (best.rating < 5 && driftM != null && driftM < 10)
        ? 'verified' : 'needs_review';
      console.log(`${tag} writing proposal: final=(${finalLat.toFixed(5)}, ${finalLng.toFixed(5)}) drift=${driftM}m status=${newStatus} source=tigerline rating=${best.rating}`);

      const updated = await pgPool.query(
        `UPDATE salem_pois
            SET lat_proposed = $2,
                lng_proposed = $3,
                location_source = 'tigerline',
                location_status = $4,
                location_drift_m = $5,
                location_geocoder_rating = $6,
                location_verified_at = NOW(),
                updated_at = NOW()
          WHERE id = $1
          RETURNING id, lat, lng, lat_proposed, lng_proposed,
                    location_source, location_status, location_drift_m,
                    location_geocoder_rating, location_verified_at,
                    location_truth_of_record`,
        [id, finalLat, finalLng, newStatus, driftM, best.rating]
      );

      console.log(`${tag} END status=proposed (${Date.now() - reqStart}ms)`);
      res.json({
        status: 'proposed',
        proposal: {
          lat: finalLat,
          lng: finalLng,
          rating: best.rating,
          drift_m: driftM,
          normalized_address: best.normalized_address,
          snapped_to_edge: snapped ? {
            tlid: snapped.edge_tlid,
            fullname: snapped.edge_fullname,
            snap_distance_m: snapped.snap_distance_m,
          } : null,
          candidate_count: candidates.length,
        },
        warnings,
        poi: updated.rows[0],
      });
    } catch (err) {
      console.error(`[validate-tiger] ERROR after ${Date.now() - reqStart}ms:`, err.stack || err.message);
      res.status(500).json({ error: err.message });
    } finally {
      if (tigerClient) {
        try { tigerClient.release(); } catch (_) {}
      }
    }
  });

  // ─── POST /admin/salem/pois/:id/geocode-via-google ───────────────────────
  // S218: ask Google's Geocoding API where a POI's address actually is, then
  // hand off to the same proposal-review flow Tiger validate uses (same
  // lat_proposed columns, same review panel, same Accept/Cancel/Drag UX).
  // Useful when Tiger interpolation lands in a parking lot — Google's
  // ROOFTOP / RANGE_INTERPOLATED matches honor real building positions.
  //
  // Requires GOOGLE_GEOCODE_API_KEY env var. Same Salem-area sanity filter
  // as the Tiger flow (15 km from Salem center) so wrong-town matches don't
  // write a bogus proposal.
  app.post('/admin/salem/pois/:id/geocode-via-google', requirePg, async (req, res) => {
    const reqStart = Date.now();
    try {
      const id = getId(req, res);
      if (!id) return;
      const tag = `[geocode-google ${id}]`;
      console.log(`${tag} ───────────────────────────────────────────────────────────`);
      console.log(`${tag} START`);

      const apiKey = process.env.GOOGLE_GEOCODE_API_KEY;
      if (!apiKey) {
        console.log(`${tag} GOOGLE_GEOCODE_API_KEY not configured → 503`);
        return res.status(503).json({ error: 'GOOGLE_GEOCODE_API_KEY not configured on cache-proxy' });
      }

      const existing = await pgPool.query(
        `SELECT id, name, lat, lng, address, deleted_at
           FROM salem_pois WHERE id = $1`,
        [id]
      );
      if (!existing.rows.length) {
        console.log(`${tag} POI not found → 404`);
        return res.status(404).json({ error: 'Not found' });
      }
      const poi = existing.rows[0];
      console.log(`${tag} POI name="${poi.name}" current=(${Number(poi.lat).toFixed(5)}, ${Number(poi.lng).toFixed(5)}) address="${poi.address}"`);
      if (poi.deleted_at) {
        return res.status(409).json({ error: 'POI is soft-deleted; restore it before validating' });
      }
      if (!poi.address || !String(poi.address).trim()) {
        console.log(`${tag} POI has no address → 400 no_address`);
        return res.status(400).json({ error: 'no_address', message: 'POI has no usable address to geocode' });
      }

      // Use Places API (New) Text Search. The legacy /place/textsearch
      // endpoint returns REQUEST_DENIED on projects created after Google
      // deprecated it. New API: POST with JSON body, key in header, field
      // mask required. Better for business POIs because it resolves to the
      // actual storefront, not address-interpolation.
      const query = `${poi.name || ''} ${poi.address}`.trim();
      const url = `https://places.googleapis.com/v1/places:searchText`;
      console.log(`${tag} Places API (New) Text Search query="${query}"`);
      const t0 = Date.now();
      const ctrl = new AbortController();
      const timer = setTimeout(() => ctrl.abort(), 8000);
      let body;
      try {
        const r = await fetch(url, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
            'X-Goog-Api-Key': apiKey,
            'X-Goog-FieldMask': 'places.displayName,places.formattedAddress,places.location,places.id,places.types',
          },
          body: JSON.stringify({ textQuery: query }),
          signal: ctrl.signal,
        });
        body = await r.json();
      } catch (e) {
        clearTimeout(timer);
        console.log(`${tag} Google fetch FAILED in ${Date.now() - t0}ms: ${e.message}`);
        return res.status(502).json({ error: 'Google Places API request failed', message: e.message });
      }
      clearTimeout(timer);
      const places = body.places || [];
      console.log(`${tag} Google responded in ${Date.now() - t0}ms places=${places.length}${body.error ? ' error=' + JSON.stringify(body.error) : ''}`);

      if (body.error) {
        const errMsg = body.error.message || JSON.stringify(body.error);
        console.log(`${tag} Google API error: ${body.error.status || ''} ${errMsg}`);
        return res.status(502).json({ error: `Google Places API: ${body.error.status || 'error'}`, message: errMsg });
      }
      if (places.length === 0) {
        console.log(`${tag} END status=no_match (zero places) (${Date.now() - reqStart}ms)`);
        return res.json({ status: 'no_match', warnings: ['Google returned no places for this query.'] });
      }

      // Take the top result (Google sorts by relevance). Filter to Salem
      // area to catch wrong-town matches just like the Tiger flow.
      const warnings = [];
      let chosen = null;
      for (const r of places) {
        const lat = r.location?.latitude;
        const lng = r.location?.longitude;
        if (lat == null || lng == null) continue;
        const displayName = r.displayName?.text || r.id;
        const dFromSalem = haversineKm(
          VALIDATE_SALEM_CENTER_LAT, VALIDATE_SALEM_CENTER_LNG, lat, lng,
        ) * 1000;
        const inArea = dFromSalem <= VALIDATE_SALEM_RADIUS_M;
        console.log(`${tag}   "${displayName}" (${lat.toFixed(5)}, ${lng.toFixed(5)}) ${(dFromSalem/1000).toFixed(1)}km from Salem ${inArea ? '✓' : '✗ DROPPED'} "${r.formattedAddress}"`);
        if (!inArea) {
          warnings.push(
            `Dropped Google candidate "${displayName}" (${(dFromSalem / 1000).toFixed(1)} km from Salem) — likely wrong-town match for "${r.formattedAddress}".`
          );
          continue;
        }
        if (!chosen) {
          chosen = {
            geometry: { location: { lat, lng } },
            formatted_address: r.formattedAddress,
            name: displayName,
            place_id: r.id,
          };
        }
      }

      if (!chosen) {
        console.log(`${tag} END status=no_match (all results outside Salem) (${Date.now() - reqStart}ms)`);
        return res.json({
          status: 'no_match',
          warnings: warnings.length ? warnings : ['No Google candidates within 15 km of Salem.'],
        });
      }

      const lat0 = poi.lat != null ? parseFloat(poi.lat) : null;
      const lng0 = poi.lng != null ? parseFloat(poi.lng) : null;
      const finalLat = chosen.geometry.location.lat;
      const finalLng = chosen.geometry.location.lng;
      const driftM = lat0 != null && lng0 != null
        ? Math.round(haversineKm(lat0, lng0, finalLat, finalLng) * 1000)
        : null;
      // Places Text Search returns business storefronts directly. Treat as
      // 'verified' if drift < 30m (operator already had it close), else
      // needs_review so they can confirm via map.
      const newStatus = (driftM != null && driftM < 30)
        ? 'verified' : 'needs_review';
      console.log(`${tag} writing proposal: final=(${finalLat.toFixed(5)}, ${finalLng.toFixed(5)}) drift=${driftM}m status=${newStatus} source=google name="${chosen.name}"`);

      const updated = await pgPool.query(
        `UPDATE salem_pois
            SET lat_proposed = $2,
                lng_proposed = $3,
                location_source = 'google',
                location_status = $4,
                location_drift_m = $5,
                location_geocoder_rating = NULL,
                location_verified_at = NOW(),
                updated_at = NOW()
          WHERE id = $1
          RETURNING id, lat, lng, lat_proposed, lng_proposed,
                    location_source, location_status, location_drift_m,
                    location_geocoder_rating, location_verified_at,
                    location_truth_of_record`,
        [id, finalLat, finalLng, newStatus, driftM]
      );

      console.log(`${tag} END status=proposed (${Date.now() - reqStart}ms)`);
      res.json({
        status: 'proposed',
        proposal: {
          lat: finalLat,
          lng: finalLng,
          rating: null,
          drift_m: driftM,
          normalized_address: chosen.formatted_address,
          snapped_to_edge: null,
          candidate_count: places.length,
          source: 'google',
          google_name: chosen.name,
          place_id: chosen.place_id,
        },
        warnings,
        poi: updated.rows[0],
      });
    } catch (err) {
      console.error(`[geocode-google] ERROR after ${Date.now() - reqStart}ms:`, err.stack || err.message);
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

      // S125: restoring a soft-delete is an admin action — stamp dirty
      // so bulk rebuild pipelines reprocess it alongside normal edits.
      const { rows } = await pgPool.query(
        `UPDATE salem_pois
            SET deleted_at = NULL,
                admin_dirty = TRUE,
                admin_dirty_at = NOW(),
                updated_at = NOW()
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
