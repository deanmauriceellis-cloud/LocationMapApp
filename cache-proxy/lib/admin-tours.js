/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin Tour write endpoints (S174 — web admin tour editor).
 *
 * All routes mounted under /admin/salem/tours/* and gated by /admin Basic Auth
 * (see lib/admin-auth.js). Mirrors the conventions of admin-pois.js.
 *
 * Effective waypoint coords:
 *   Each row in salem_tour_stops has nullable lat/lng. The "effective" coord
 *   shown to the admin client is COALESCE(stop.lat, poi.lat). Dragging a
 *   waypoint sets stop.lat/lng directly (per-tour override) without touching
 *   the underlying POI.
 *
 * Routes (S174 foundation slice — full CRUD lands incrementally):
 *   GET   /admin/salem/tours                           — list with stop_count
 *   GET   /admin/salem/tours/:tour_id                  — tour + ordered stops
 *   PATCH /admin/salem/tours/:tour_id/stops/:stop_id   — move / narration
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-tours.js';

function isFiniteNumber(v) {
  return typeof v === 'number' && Number.isFinite(v);
}

// S190 — great-circle distance between two lat/lng pairs, in meters.
// Used to compare a routed polyline's distance against the as-the-crow-flies
// distance so the compute-route logs can flag suspicious detours.
function haversineMeters(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

// S190 — build a small diagnostic record for a routed leg. Used both for
// console logging and to attach to the JSON response so the admin UI can
// surface flagged legs without having to recompute anything client-side.
//
// "Suspicious" heuristic (purely diagnostic — does NOT alter routing):
//   - ratio > 1.6 (routed distance is more than 60% longer than straight line)
//   - OR straight < 200 m but routed > 800 m (small hop, big detour)
//   - OR straight < 100 m but routed > 400 m (very small hop, clear loop)
// Any of those is worth the operator's eyes.
function diagnoseLeg(a, b, r, elapsedMs) {
  const straightM =
    a && b && a.lat != null && b.lat != null
      ? haversineMeters(a.lat, a.lng, b.lat, b.lng)
      : null;
  const routedM = r && Number.isFinite(r.distanceM) ? r.distanceM : null;
  const ratio = routedM != null && straightM != null && straightM > 0
    ? routedM / straightM
    : null;
  const ptCount = r && r.geometry ? r.geometry.length : 0;
  const suspicious =
    ratio != null &&
    (ratio > 1.6 ||
      (straightM < 200 && routedM > 800) ||
      (straightM < 100 && routedM > 400));
  return {
    straight_m: straightM == null ? null : Math.round(straightM),
    routed_m: routedM == null ? null : Math.round(routedM),
    duration_s: r && Number.isFinite(r.durationS) ? r.durationS : null,
    detour_ratio: ratio == null ? null : Math.round(ratio * 100) / 100,
    point_count: ptCount,
    elapsed_ms: elapsedMs,
    suspicious,
  };
}

function validateLatLng(lat, lng) {
  if (lat !== undefined && lat !== null) {
    if (!isFiniteNumber(lat) || lat < -90 || lat > 90) {
      return 'lat must be a finite number in [-90, 90]';
    }
  }
  if (lng !== undefined && lng !== null) {
    if (!isFiniteNumber(lng) || lng < -180 || lng > 180) {
      return 'lng must be a finite number in [-180, 180]';
    }
  }
  return null;
}

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // ─── Helpers for the S183 "Compute Route" tool ─────────────────────────────
  //
  // salemRoute / salemBundle come from lib/salem-router.js (captured into
  // deps in server.js). They drive the same SQLite bundle the APK ships, so
  // a leg routed here matches what the on-device router would produce.
  function routerAvailable() {
    return typeof deps.salemRoute === 'function' && typeof deps.salemBundle === 'function' && !!deps.salemBundle();
  }
  function routerVersion() {
    const b = deps.salemBundle && deps.salemBundle();
    if (!b || !b.meta) return null;
    return b.meta.built_at || `schema_v${b.meta.schema_version}-nodes${b.nodeCount}-edges${b.edgeCount}`;
  }
  // SELECT effective stops for a tour in walk order. Each row has stop_id +
  // effective_lat/lng (override → POI fallback). Drops stops with no usable
  // coords from the leg list (they can't be routed).
  async function fetchEffectiveStops(client, tourId) {
    const { rows } = await client.query(
      `SELECT s.stop_id,
              s.stop_order,
              COALESCE(s.lat, p.lat) AS lat,
              COALESCE(s.lng, p.lng) AS lng,
              COALESCE(s.name, p.name) AS name
         FROM salem_tour_stops s
    LEFT JOIN salem_pois p ON p.id = s.poi_id
        WHERE s.tour_id = $1
     ORDER BY s.stop_order ASC, s.stop_id ASC`,
      [tourId]
    );
    return rows;
  }

  // ─── GET /admin/salem/tours ─────────────────────────────────────────────────
  app.get('/admin/salem/tours', requirePg, async (_req, res) => {
    try {
      const { rows } = await pgPool.query(`
        SELECT t.id, t.name, t.theme, t.description, t.estimated_minutes,
               t.distance_km, t.stop_count, t.difficulty, t.seasonal,
               t.icon_asset, t.sort_order, t.updated_at,
               COUNT(s.stop_id)::int AS stops_actual
          FROM salem_tours t
     LEFT JOIN salem_tour_stops s ON s.tour_id = t.id
      GROUP BY t.id
      ORDER BY t.sort_order ASC NULLS LAST, t.name ASC
      `);
      res.json({ count: rows.length, tours: rows });
    } catch (err) {
      console.error('[AdminTours] list error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── GET /admin/salem/tours/:tour_id ────────────────────────────────────────
  app.get('/admin/salem/tours/:tour_id', requirePg, async (req, res) => {
    try {
      const tourId = req.params.tour_id;
      if (typeof tourId !== 'string' || !tourId.trim()) {
        return res.status(400).json({ error: 'tour_id is required' });
      }

      const tourQ = await pgPool.query(
        `SELECT id, name, theme, description, estimated_minutes, distance_km,
                stop_count, difficulty, seasonal, icon_asset, sort_order,
                updated_at
           FROM salem_tours WHERE id = $1`,
        [tourId]
      );
      if (!tourQ.rows.length) return res.status(404).json({ error: 'Tour not found' });

      const stopsQ = await pgPool.query(
        `SELECT s.stop_id,
                s.tour_id,
                s.poi_id,
                s.stop_order,
                s.transition_narration,
                s.walking_minutes_from_prev,
                s.distance_m_from_prev,
                s.lat AS override_lat,
                s.lng AS override_lng,
                s.name AS override_name,
                s.updated_at,
                p.name AS poi_name,
                p.lat AS poi_lat,
                p.lng AS poi_lng,
                p.category AS poi_category,
                COALESCE(s.lat, p.lat) AS effective_lat,
                COALESCE(s.lng, p.lng) AS effective_lng,
                COALESCE(s.name, p.name) AS effective_name
           FROM salem_tour_stops s
      LEFT JOIN salem_pois p ON p.id = s.poi_id
          WHERE s.tour_id = $1
       ORDER BY s.stop_order ASC, s.stop_id ASC`,
        [tourId]
      );

      res.json({ tour: tourQ.rows[0], stops: stopsQ.rows });
    } catch (err) {
      console.error('[AdminTours] get error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── PATCH /admin/salem/tours/:tour_id/stops/:stop_id ───────────────────────
  // Whitelisted updatable stop fields. lat/lng set the per-tour override.
  app.patch('/admin/salem/tours/:tour_id/stops/:stop_id', requirePg, async (req, res) => {
    try {
      const tourId = req.params.tour_id;
      const stopId = parseInt(req.params.stop_id, 10);
      if (!Number.isInteger(stopId) || stopId <= 0) {
        return res.status(400).json({ error: 'stop_id must be a positive integer' });
      }

      const body = req.body || {};
      const setParts = [];
      const values = [];
      let idx = 1;

      if (Object.prototype.hasOwnProperty.call(body, 'lat') ||
          Object.prototype.hasOwnProperty.call(body, 'lng')) {
        const err = validateLatLng(body.lat, body.lng);
        if (err) return res.status(400).json({ error: err });
        if (Object.prototype.hasOwnProperty.call(body, 'lat')) {
          setParts.push(`lat = $${idx++}`);
          values.push(body.lat);
        }
        if (Object.prototype.hasOwnProperty.call(body, 'lng')) {
          setParts.push(`lng = $${idx++}`);
          values.push(body.lng);
        }
      }

      if (Object.prototype.hasOwnProperty.call(body, 'transition_narration')) {
        setParts.push(`transition_narration = $${idx++}`);
        values.push(body.transition_narration);
      }
      if (Object.prototype.hasOwnProperty.call(body, 'name')) {
        setParts.push(`name = $${idx++}`);
        values.push(body.name);
      }
      if (Object.prototype.hasOwnProperty.call(body, 'stop_order')) {
        const n = parseInt(body.stop_order, 10);
        if (!Number.isInteger(n) || n < 0) {
          return res.status(400).json({ error: 'stop_order must be a non-negative integer' });
        }
        setParts.push(`stop_order = $${idx++}`);
        values.push(n);
      }

      if (!setParts.length) {
        return res.status(400).json({ error: 'no updatable fields provided' });
      }

      setParts.push(`updated_at = NOW()`);
      values.push(stopId, tourId);

      const sql = `UPDATE salem_tour_stops
                      SET ${setParts.join(', ')}
                    WHERE stop_id = $${idx++} AND tour_id = $${idx}
                RETURNING stop_id, tour_id, poi_id, stop_order, lat, lng, name,
                          transition_narration, updated_at`;

      const { rows } = await pgPool.query(sql, values);
      if (!rows.length) return res.status(404).json({ error: 'Stop not found in this tour' });

      // Echo effective coords for the client.
      const stop = rows[0];
      let effectiveLat = stop.lat;
      let effectiveLng = stop.lng;
      if ((effectiveLat == null || effectiveLng == null) && stop.poi_id) {
        const poiQ = await pgPool.query(
          `SELECT lat, lng FROM salem_pois WHERE id = $1`,
          [stop.poi_id]
        );
        if (poiQ.rows.length) {
          if (effectiveLat == null) effectiveLat = poiQ.rows[0].lat;
          if (effectiveLng == null) effectiveLng = poiQ.rows[0].lng;
        }
      }
      stop.effective_lat = effectiveLat;
      stop.effective_lng = effectiveLng;

      res.json(stop);
    } catch (err) {
      console.error('[AdminTours] patch stop error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/tours ────────────────────────────────────────────────
  // Create a new tour. id is required and must be unique. Defaults: difficulty
  // 'moderate', seasonal false, sort_order 0, stop_count 0, distance_km 0,
  // estimated_minutes 0, theme 'GENERAL', description ''.
  app.post('/admin/salem/tours', requirePg, async (req, res) => {
    try {
      const b = req.body || {};
      if (typeof b.id !== 'string' || !b.id.trim()) {
        return res.status(400).json({ error: 'id is required' });
      }
      if (typeof b.name !== 'string' || !b.name.trim()) {
        return res.status(400).json({ error: 'name is required' });
      }
      const row = {
        id: b.id.trim(),
        name: b.name.trim(),
        theme: typeof b.theme === 'string' && b.theme ? b.theme : 'GENERAL',
        description: typeof b.description === 'string' ? b.description : '',
        estimated_minutes: Number.isFinite(b.estimated_minutes) ? b.estimated_minutes : 0,
        distance_km: Number.isFinite(b.distance_km) ? b.distance_km : 0,
        stop_count: 0,
        difficulty: typeof b.difficulty === 'string' && b.difficulty ? b.difficulty : 'moderate',
        seasonal: b.seasonal === true,
        icon_asset: typeof b.icon_asset === 'string' ? b.icon_asset : null,
        sort_order: Number.isInteger(b.sort_order) ? b.sort_order : 0,
      };
      const exists = await pgPool.query(`SELECT 1 FROM salem_tours WHERE id = $1`, [row.id]);
      if (exists.rows.length) return res.status(409).json({ error: 'Tour id already exists' });

      const { rows } = await pgPool.query(
        `INSERT INTO salem_tours (id, name, theme, description, estimated_minutes,
                                  distance_km, stop_count, difficulty, seasonal,
                                  icon_asset, sort_order)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11)
         RETURNING *`,
        [row.id, row.name, row.theme, row.description, row.estimated_minutes,
         row.distance_km, row.stop_count, row.difficulty, row.seasonal,
         row.icon_asset, row.sort_order]
      );
      res.status(201).json(rows[0]);
    } catch (err) {
      console.error('[AdminTours] create error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── PATCH /admin/salem/tours/:tour_id ─────────────────────────────────────
  // Whitelisted metadata edit. stop_count is auto-managed by add/delete stop
  // routes — never accept it here.
  const TOUR_UPDATABLE = [
    'name', 'theme', 'description', 'estimated_minutes', 'distance_km',
    'difficulty', 'seasonal', 'icon_asset', 'sort_order',
  ];
  app.patch('/admin/salem/tours/:tour_id', requirePg, async (req, res) => {
    try {
      const tourId = req.params.tour_id;
      const body = req.body || {};
      const setParts = [];
      const values = [];
      let idx = 1;
      for (const f of TOUR_UPDATABLE) {
        if (!Object.prototype.hasOwnProperty.call(body, f)) continue;
        setParts.push(`${f} = $${idx++}`);
        values.push(body[f]);
      }
      if (!setParts.length) return res.status(400).json({ error: 'no updatable fields' });
      setParts.push(`updated_at = NOW()`);
      values.push(tourId);
      const { rows } = await pgPool.query(
        `UPDATE salem_tours SET ${setParts.join(', ')} WHERE id = $${idx} RETURNING *`,
        values
      );
      if (!rows.length) return res.status(404).json({ error: 'Tour not found' });
      res.json(rows[0]);
    } catch (err) {
      console.error('[AdminTours] patch tour error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── DELETE /admin/salem/tours/:tour_id ────────────────────────────────────
  // Hard delete. Cascades stops (no FK ON DELETE CASCADE was defined, so we
  // do it explicitly inside a transaction).
  app.delete('/admin/salem/tours/:tour_id', requirePg, async (req, res) => {
    const client = await pgPool.connect();
    try {
      const tourId = req.params.tour_id;
      await client.query('BEGIN');
      await client.query(`DELETE FROM salem_tour_stops WHERE tour_id = $1`, [tourId]);
      const tourQ = await client.query(`DELETE FROM salem_tours WHERE id = $1 RETURNING id`, [tourId]);
      await client.query('COMMIT');
      if (!tourQ.rows.length) return res.status(404).json({ error: 'Tour not found' });
      res.json({ id: tourId, deleted: true });
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('[AdminTours] delete tour error:', err.message);
      res.status(500).json({ error: err.message });
    } finally {
      client.release();
    }
  });

  // Helper: keep salem_tours.stop_count in sync after add/delete/reorder.
  async function syncStopCount(client, tourId) {
    await client.query(
      `UPDATE salem_tours
          SET stop_count = (SELECT COUNT(*) FROM salem_tour_stops WHERE tour_id = $1),
              updated_at = NOW()
        WHERE id = $1`,
      [tourId]
    );
  }

  // ─── POST /admin/salem/tours/:tour_id/stops ────────────────────────────────
  // Add a waypoint. Body: { poi_id?, lat?, lng?, name?, stop_order?,
  //   transition_narration? }. If stop_order is omitted, append (max+1). If
  //   stop_order is provided, shift any equal/greater stops down by 1.
  app.post('/admin/salem/tours/:tour_id/stops', requirePg, async (req, res) => {
    const client = await pgPool.connect();
    try {
      const tourId = req.params.tour_id;
      const b = req.body || {};

      const tourQ = await client.query(`SELECT id FROM salem_tours WHERE id = $1`, [tourId]);
      if (!tourQ.rows.length) return res.status(404).json({ error: 'Tour not found' });

      // Validate at least one of poi_id or lat/lng must be present, else the
      // stop has no location at all.
      const hasCoords = Number.isFinite(b.lat) && Number.isFinite(b.lng);
      const hasPoi = typeof b.poi_id === 'string' && b.poi_id.trim();
      if (!hasCoords && !hasPoi) {
        return res.status(400).json({ error: 'must provide poi_id or lat+lng' });
      }
      if (hasCoords) {
        const err = validateLatLng(b.lat, b.lng);
        if (err) return res.status(400).json({ error: err });
      }
      if (hasPoi) {
        const poiQ = await client.query(`SELECT 1 FROM salem_pois WHERE id = $1`, [b.poi_id]);
        if (!poiQ.rows.length) return res.status(400).json({ error: 'poi_id does not exist' });
      }

      await client.query('BEGIN');

      let stopOrder;
      if (Number.isInteger(b.stop_order) && b.stop_order >= 1) {
        stopOrder = b.stop_order;
        // Shift down anything at or after this position.
        await client.query(
          `UPDATE salem_tour_stops
              SET stop_order = stop_order + 1, updated_at = NOW()
            WHERE tour_id = $1 AND stop_order >= $2`,
          [tourId, stopOrder]
        );
      } else {
        const maxQ = await client.query(
          `SELECT COALESCE(MAX(stop_order), 0) AS m FROM salem_tour_stops WHERE tour_id = $1`,
          [tourId]
        );
        stopOrder = maxQ.rows[0].m + 1;
      }

      const insQ = await client.query(
        `INSERT INTO salem_tour_stops
           (tour_id, poi_id, stop_order, lat, lng, name, transition_narration)
         VALUES ($1, $2, $3, $4, $5, $6, $7)
         RETURNING stop_id, tour_id, poi_id, stop_order, lat, lng, name,
                   transition_narration, updated_at`,
        [
          tourId,
          hasPoi ? b.poi_id : null,
          stopOrder,
          hasCoords ? b.lat : null,
          hasCoords ? b.lng : null,
          typeof b.name === 'string' ? b.name : null,
          typeof b.transition_narration === 'string' ? b.transition_narration : null,
        ]
      );

      await syncStopCount(client, tourId);
      await client.query('COMMIT');

      const stop = insQ.rows[0];
      // Echo effective coords/name.
      let effLat = stop.lat, effLng = stop.lng, effName = stop.name;
      if ((effLat == null || effLng == null || effName == null) && stop.poi_id) {
        const poiQ = await pgPool.query(
          `SELECT name, lat, lng FROM salem_pois WHERE id = $1`,
          [stop.poi_id]
        );
        if (poiQ.rows.length) {
          if (effLat == null) effLat = poiQ.rows[0].lat;
          if (effLng == null) effLng = poiQ.rows[0].lng;
          if (effName == null) effName = poiQ.rows[0].name;
        }
      }
      stop.effective_lat = effLat;
      stop.effective_lng = effLng;
      stop.effective_name = effName;
      res.status(201).json(stop);
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('[AdminTours] add stop error:', err.message);
      res.status(500).json({ error: err.message });
    } finally {
      client.release();
    }
  });

  // ─── DELETE /admin/salem/tours/:tour_id/stops/:stop_id ─────────────────────
  // Delete + renumber subsequent stops down by 1 inside a transaction.
  app.delete('/admin/salem/tours/:tour_id/stops/:stop_id', requirePg, async (req, res) => {
    const client = await pgPool.connect();
    try {
      const tourId = req.params.tour_id;
      const stopId = parseInt(req.params.stop_id, 10);
      if (!Number.isInteger(stopId) || stopId <= 0) {
        return res.status(400).json({ error: 'stop_id must be a positive integer' });
      }
      await client.query('BEGIN');
      const delQ = await client.query(
        `DELETE FROM salem_tour_stops
          WHERE stop_id = $1 AND tour_id = $2
          RETURNING stop_order`,
        [stopId, tourId]
      );
      if (!delQ.rows.length) {
        await client.query('ROLLBACK');
        return res.status(404).json({ error: 'Stop not found in this tour' });
      }
      const removedOrder = delQ.rows[0].stop_order;
      await client.query(
        `UPDATE salem_tour_stops
            SET stop_order = stop_order - 1, updated_at = NOW()
          WHERE tour_id = $1 AND stop_order > $2`,
        [tourId, removedOrder]
      );
      await syncStopCount(client, tourId);
      await client.query('COMMIT');
      res.json({ stop_id: stopId, deleted: true });
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('[AdminTours] delete stop error:', err.message);
      res.status(500).json({ error: err.message });
    } finally {
      client.release();
    }
  });

  // ─── POST /admin/salem/tours/:tour_id/stops/reorder ────────────────────────
  // Body: { order: [stop_id, stop_id, ...] }. Must be a permutation of the
  // tour's current stop_id set; rewrites stop_order to 1..N in array order.
  app.post('/admin/salem/tours/:tour_id/stops/reorder', requirePg, async (req, res) => {
    const client = await pgPool.connect();
    try {
      const tourId = req.params.tour_id;
      const order = req.body && Array.isArray(req.body.order) ? req.body.order : null;
      if (!order) return res.status(400).json({ error: 'order array is required' });
      const ids = order.map((v) => parseInt(v, 10));
      if (ids.some((n) => !Number.isInteger(n) || n <= 0)) {
        return res.status(400).json({ error: 'order entries must be positive integers' });
      }

      await client.query('BEGIN');
      const cur = await client.query(
        `SELECT stop_id FROM salem_tour_stops WHERE tour_id = $1`,
        [tourId]
      );
      const have = new Set(cur.rows.map((r) => Number(r.stop_id)));
      if (have.size !== ids.length || ids.some((id) => !have.has(id))) {
        await client.query('ROLLBACK');
        return res.status(400).json({ error: 'order must be a permutation of the tour\'s stop_ids' });
      }

      // Two-pass to dodge the (tour_id, stop_order) collision window.
      // Phase 1: stash everyone at negative offsets.
      await client.query(
        `UPDATE salem_tour_stops SET stop_order = -stop_order WHERE tour_id = $1`,
        [tourId]
      );
      // Phase 2: write final positions.
      for (let i = 0; i < ids.length; i++) {
        await client.query(
          `UPDATE salem_tour_stops
              SET stop_order = $1, updated_at = NOW()
            WHERE stop_id = $2 AND tour_id = $3`,
          [i + 1, ids[i], tourId]
        );
      }

      await syncStopCount(client, tourId);
      await client.query('COMMIT');
      res.json({ tour_id: tourId, count: ids.length });
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('[AdminTours] reorder error:', err.message);
      res.status(500).json({ error: err.message });
    } finally {
      client.release();
    }
  });

  // ─── GET /admin/salem/tours/:tour_id/legs ──────────────────────────────────
  // Returns all precomputed legs for the tour in leg_order. Empty array if
  // the route hasn't been computed yet.
  app.get('/admin/salem/tours/:tour_id/legs', requirePg, async (req, res) => {
    try {
      const tourId = req.params.tour_id;
      const { rows } = await pgPool.query(
        `SELECT tour_id, leg_order, from_stop_id, to_stop_id,
                polyline_json, distance_m, duration_s,
                router_version, manual_edits, computed_at
           FROM salem_tour_legs
          WHERE tour_id = $1
       ORDER BY leg_order ASC`,
        [tourId]
      );
      res.json({ tour_id: tourId, count: rows.length, legs: rows });
    } catch (err) {
      console.error('[AdminTours] list legs error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/tours/:tour_id/compute-route ────────────────────────
  // Routes every consecutive (stop_order n → n+1) pair via the bundle router
  // and persists the result to salem_tour_legs. Replaces any existing legs
  // for the tour. Skips legs whose endpoints have no usable coords (effective
  // lat/lng null) — those rows simply won't have a leg.
  //
  // By default, legs with manual_edits set are PRESERVED (their polyline isn't
  // overwritten). Pass { force: true } in the body to recompute everything.
  app.post('/admin/salem/tours/:tour_id/compute-route', requirePg, async (req, res) => {
    if (!routerAvailable()) {
      return res.status(503).json({ error: 'Salem routing bundle not loaded' });
    }
    const client = await pgPool.connect();
    try {
      const tourId = req.params.tour_id;
      const force = !!(req.body && req.body.force === true);

      const tourQ = await client.query(`SELECT id FROM salem_tours WHERE id = $1`, [tourId]);
      if (!tourQ.rows.length) {
        client.release();
        return res.status(404).json({ error: 'Tour not found' });
      }

      const stops = await fetchEffectiveStops(client, tourId);
      if (stops.length < 2) {
        client.release();
        return res.status(400).json({ error: 'tour has fewer than 2 stops with coords; nothing to route' });
      }

      // Snapshot existing legs so we can preserve manual_edits unless force.
      const prevQ = await client.query(
        `SELECT leg_order, manual_edits, polyline_json, distance_m, duration_s, router_version
           FROM salem_tour_legs WHERE tour_id = $1`,
        [tourId]
      );
      const prevByOrder = new Map();
      for (const r of prevQ.rows) prevByOrder.set(r.leg_order, r);

      const version = routerVersion();
      const legs = [];
      const skipped = [];
      const t0Tour = Date.now();
      // S190 — verbose per-leg diagnostics. Logged to the cache-proxy
      // console AND echoed in the JSON response so the operator can see
      // why a leg looks weird in the admin map.
      console.log(
        `[AdminTours] compute-route start tour=${tourId} stops=${stops.length} force=${force} router=${version}`,
      );
      for (let i = 0; i < stops.length - 1; i++) {
        const a = stops[i];
        const b = stops[i + 1];
        const legOrder = i + 1;
        const fromLabel = `${a.name ?? a.stop_id} (${a.stop_id})`;
        const toLabel = `${b.name ?? b.stop_id} (${b.stop_id})`;
        if (a.lat == null || a.lng == null || b.lat == null || b.lng == null) {
          console.log(
            `[AdminTours]   leg ${legOrder} SKIP missing-coords  ${fromLabel} → ${toLabel}`,
          );
          skipped.push({
            leg_order: legOrder,
            from: fromLabel,
            to: toLabel,
            reason: 'missing coords',
          });
          continue;
        }

        const prev = prevByOrder.get(legOrder);
        if (!force && prev && prev.manual_edits != null) {
          // Preserve hand-edited leg. Echo it back unchanged.
          console.log(
            `[AdminTours]   leg ${legOrder} PRESERVED manual_edits  ${fromLabel} → ${toLabel}  routed=${prev.distance_m}m`,
          );
          legs.push({
            leg_order: legOrder,
            from_stop_id: a.stop_id,
            to_stop_id: b.stop_id,
            polyline_json: prev.polyline_json,
            distance_m: prev.distance_m,
            duration_s: prev.duration_s,
            router_version: prev.router_version,
            manual_edits: prev.manual_edits,
            preserved: true,
            diagnostics: diagnoseLeg(
              a,
              b,
              { distanceM: prev.distance_m, durationS: prev.duration_s, geometry: prev.polyline_json },
              0,
            ),
          });
          continue;
        }

        let r;
        const tLeg = Date.now();
        try {
          r = deps.salemRoute(a.lat, a.lng, b.lat, b.lng);
        } catch (err) {
          console.log(
            `[AdminTours]   leg ${legOrder} ERROR  ${fromLabel} → ${toLabel}  ${err.message}`,
          );
          skipped.push({
            leg_order: legOrder,
            from: fromLabel,
            to: toLabel,
            reason: `router error: ${err.message}`,
          });
          continue;
        }
        if (!r || !r.geometry || r.geometry.length < 2) {
          const straightM = Math.round(haversineMeters(a.lat, a.lng, b.lat, b.lng));
          // S190 — pull snap-info diagnostics out of the router so we can
          // explain WHY no route was found. Three common failure modes:
          //   (1) same_node = true   → both endpoints snap to same graph node
          //   (2) huge from/to_snap_m → endpoint sits far from any walkable edge
          //   (3) snap distances OK but routeBetween couldn't reach → likely
          //       isolated graph component (rare; usually a graph data issue)
          let diagInfo = null;
          let detail = '';
          let cause = 'unreachable in graph';
          if (typeof deps.salemRouteDiag === 'function') {
            try {
              diagInfo = deps.salemRouteDiag(a.lat, a.lng, b.lat, b.lng);
              if (diagInfo) {
                if (diagInfo.same_node) {
                  cause = `endpoints snap to same node #${diagInfo.from_node_idx}`;
                  detail = ` snap=${diagInfo.from_snap_m}m/${diagInfo.to_snap_m}m`;
                } else if (diagInfo.from_node_idx < 0 || diagInfo.to_node_idx < 0) {
                  cause = 'endpoint outside graph coverage';
                } else {
                  cause = 'no path between snapped nodes (graph component)';
                  detail = ` from_node=#${diagInfo.from_node_idx} (snap ${diagInfo.from_snap_m}m), to_node=#${diagInfo.to_node_idx} (snap ${diagInfo.to_snap_m}m)`;
                }
              }
            } catch { /* diag is best-effort */ }
          }
          console.log(
            `[AdminTours]   leg ${legOrder} NO-ROUTE  ${fromLabel} → ${toLabel}  ` +
              `straight=${straightM}m  cause=${cause}${detail}`,
          );
          skipped.push({
            leg_order: legOrder,
            from: fromLabel,
            to: toLabel,
            reason: `no route found (${cause})`,
            straight_m: straightM,
            snap_diag: diagInfo,
          });
          continue;
        }

        const diag = diagnoseLeg(a, b, r, Date.now() - tLeg);
        const flag = diag.suspicious ? '⚠ SUSPICIOUS' : '';
        console.log(
          `[AdminTours]   leg ${legOrder}  ${fromLabel} → ${toLabel}  ` +
            `straight=${diag.straight_m}m routed=${diag.routed_m}m ratio=${diag.detour_ratio} ` +
            `pts=${diag.point_count} dur=${diag.duration_s}s t=${diag.elapsed_ms}ms ${flag}`,
        );

        legs.push({
          leg_order: legOrder,
          from_stop_id: a.stop_id,
          to_stop_id: b.stop_id,
          polyline_json: r.geometry, // [[lat,lng], ...]
          distance_m: r.distanceM,
          duration_s: r.durationS,
          router_version: version,
          manual_edits: null,
          preserved: false,
          diagnostics: diag,
          from_label: fromLabel,
          to_label: toLabel,
        });
      }

      // Replace-and-write inside a transaction.
      await client.query('BEGIN');
      await client.query(`DELETE FROM salem_tour_legs WHERE tour_id = $1`, [tourId]);
      for (const leg of legs) {
        await client.query(
          `INSERT INTO salem_tour_legs
             (tour_id, leg_order, from_stop_id, to_stop_id,
              polyline_json, distance_m, duration_s,
              router_version, manual_edits, computed_at)
           VALUES ($1,$2,$3,$4,$5::jsonb,$6,$7,$8,$9::jsonb, NOW())`,
          [
            tourId, leg.leg_order, leg.from_stop_id, leg.to_stop_id,
            JSON.stringify(leg.polyline_json),
            leg.distance_m, leg.duration_s,
            leg.router_version,
            leg.manual_edits == null ? null : JSON.stringify(leg.manual_edits),
          ]
        );
      }
      // Roll up totals onto salem_tours so the list view stays accurate.
      let totalM = 0, totalS = 0;
      for (const leg of legs) { totalM += leg.distance_m; totalS += leg.duration_s; }
      await client.query(
        `UPDATE salem_tours
            SET distance_km = $1,
                estimated_minutes = $2,
                updated_at = NOW()
          WHERE id = $3`,
        [Math.round((totalM / 1000) * 100) / 100, Math.round(totalS / 60), tourId]
      );
      await client.query('COMMIT');

      // S190 — final summary log.
      const suspiciousCount = legs.filter((l) => l.diagnostics && l.diagnostics.suspicious).length;
      console.log(
        `[AdminTours] compute-route done tour=${tourId} legs=${legs.length} skipped=${skipped.length} ` +
          `suspicious=${suspiciousCount} totalDist=${totalM}m totalDur=${totalS}s ` +
          `wall=${Date.now() - t0Tour}ms`,
      );

      res.json({
        tour_id: tourId,
        leg_count: legs.length,
        skipped,
        total_distance_m: totalM,
        total_duration_s: totalS,
        router_version: version,
        force,
        suspicious_count: suspiciousCount,
        legs,
      });
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('[AdminTours] compute-route error:', err.message);
      res.status(500).json({ error: err.message });
    } finally {
      client.release();
    }
  });

  // ─── POST /admin/salem/tours/:tour_id/legs/:leg_order/recompute ────────────
  // Recompute a single leg. Preserves other legs, and (by default) refuses to
  // overwrite a leg with manual_edits unless force=true.
  app.post('/admin/salem/tours/:tour_id/legs/:leg_order/recompute', requirePg, async (req, res) => {
    if (!routerAvailable()) {
      return res.status(503).json({ error: 'Salem routing bundle not loaded' });
    }
    const client = await pgPool.connect();
    try {
      const tourId = req.params.tour_id;
      const legOrder = parseInt(req.params.leg_order, 10);
      if (!Number.isInteger(legOrder) || legOrder <= 0) {
        return res.status(400).json({ error: 'leg_order must be a positive integer' });
      }
      const force = !!(req.body && req.body.force === true);

      const stops = await fetchEffectiveStops(client, tourId);
      const a = stops[legOrder - 1];
      const b = stops[legOrder];
      if (!a || !b) {
        return res.status(400).json({ error: `leg_order ${legOrder} out of range (tour has ${stops.length} stops)` });
      }
      if (a.lat == null || a.lng == null || b.lat == null || b.lng == null) {
        return res.status(400).json({ error: 'one or both endpoints have no coords' });
      }

      const prevQ = await client.query(
        `SELECT manual_edits FROM salem_tour_legs
          WHERE tour_id = $1 AND leg_order = $2`,
        [tourId, legOrder]
      );
      const prevManual = prevQ.rows[0] ? prevQ.rows[0].manual_edits : null;
      if (!force && prevManual != null) {
        return res.status(409).json({
          error: 'leg has manual edits — pass { force: true } to overwrite',
        });
      }

      // S190 — verbose logging for single-leg recompute.
      const fromLabel = `${a.name ?? a.stop_id} (${a.stop_id})`;
      const toLabel = `${b.name ?? b.stop_id} (${b.stop_id})`;
      const tLeg = Date.now();
      const r = deps.salemRoute(a.lat, a.lng, b.lat, b.lng);
      if (!r || !r.geometry || r.geometry.length < 2) {
        const straightM = Math.round(haversineMeters(a.lat, a.lng, b.lat, b.lng));
        let diagInfo = null;
        let cause = 'unreachable in graph';
        if (typeof deps.salemRouteDiag === 'function') {
          try {
            diagInfo = deps.salemRouteDiag(a.lat, a.lng, b.lat, b.lng);
            if (diagInfo) {
              if (diagInfo.same_node) cause = `endpoints snap to same node #${diagInfo.from_node_idx}`;
              else if (diagInfo.from_node_idx < 0 || diagInfo.to_node_idx < 0) cause = 'endpoint outside graph coverage';
              else cause = 'no path between snapped nodes (graph component)';
            }
          } catch { /* best-effort */ }
        }
        console.log(
          `[AdminTours] recompute leg=${legOrder} NO-ROUTE  ${fromLabel} → ${toLabel}  ` +
            `straight=${straightM}m  cause=${cause}`,
        );
        return res.status(404).json({
          error: 'no route found for this leg',
          from: fromLabel,
          to: toLabel,
          straight_m: straightM,
          snap_diag: diagInfo,
        });
      }
      const diag = diagnoseLeg(a, b, r, Date.now() - tLeg);
      const flag = diag.suspicious ? '⚠ SUSPICIOUS' : '';
      console.log(
        `[AdminTours] recompute leg=${legOrder}  ${fromLabel} → ${toLabel}  ` +
          `straight=${diag.straight_m}m routed=${diag.routed_m}m ratio=${diag.detour_ratio} ` +
          `pts=${diag.point_count} dur=${diag.duration_s}s t=${diag.elapsed_ms}ms ${flag}`,
      );

      const version = routerVersion();
      await client.query(
        `INSERT INTO salem_tour_legs
           (tour_id, leg_order, from_stop_id, to_stop_id,
            polyline_json, distance_m, duration_s,
            router_version, manual_edits, computed_at)
         VALUES ($1,$2,$3,$4,$5::jsonb,$6,$7,$8,NULL,NOW())
         ON CONFLICT (tour_id, leg_order) DO UPDATE
           SET from_stop_id   = EXCLUDED.from_stop_id,
               to_stop_id     = EXCLUDED.to_stop_id,
               polyline_json  = EXCLUDED.polyline_json,
               distance_m     = EXCLUDED.distance_m,
               duration_s     = EXCLUDED.duration_s,
               router_version = EXCLUDED.router_version,
               manual_edits   = NULL,
               computed_at    = NOW()`,
        [
          tourId, legOrder, a.stop_id, b.stop_id,
          JSON.stringify(r.geometry),
          r.distanceM, r.durationS,
          version,
        ]
      );

      const out = await client.query(
        `SELECT tour_id, leg_order, from_stop_id, to_stop_id,
                polyline_json, distance_m, duration_s,
                router_version, manual_edits, computed_at
           FROM salem_tour_legs
          WHERE tour_id = $1 AND leg_order = $2`,
        [tourId, legOrder]
      );
      // S190 — attach the diagnostics block + endpoint labels so the admin
      // tool can show the operator the detour ratio for the leg they just
      // recomputed without having to read it out of the proxy log.
      res.json({
        ...out.rows[0],
        from_label: fromLabel,
        to_label: toLabel,
        diagnostics: diag,
      });
    } catch (err) {
      console.error('[AdminTours] recompute leg error:', err.message);
      res.status(500).json({ error: err.message });
    } finally {
      client.release();
    }
  });
};
