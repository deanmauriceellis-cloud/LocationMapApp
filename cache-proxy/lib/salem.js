/*
 * WickedSalemWitchCityTour — Salem Content API
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Salem-specific content endpoints. Backward compatible — all routes
 * are under /salem/* prefix and do not affect existing LocationMapApp
 * endpoints.
 *
 * Phase 9U: All POI reads now use the unified `salem_pois` table.
 * The old /salem/businesses endpoint still works (filters by non-tour,
 * non-narrated POIs with business-specific columns) for backward
 * compatibility.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module salem.js';

module.exports = function (app, deps) {
  const { pgPool, requirePg } = deps;

  // ── Helper: staleness-aware wrapper ──────────────────────────────────────
  function provenanceDefaults(row) {
    const now = new Date().toISOString();
    return {
      data_source: row.data_source || 'manual_curated',
      confidence: row.confidence ?? 1.0,
      verified_date: row.verified_date || null,
      created_at: row.created_at || now,
      updated_at: now,
      stale_after: row.stale_after || null,
    };
  }

  // ── POIs (unified) ───────────────────────────────────────────────────────

  app.get('/salem/pois', requirePg, async (req, res) => {
    try {
      const { category, source, stale, lat, lng, radius, q, is_tour_poi, is_narrated, limit = 500 } = req.query;
      let sql = 'SELECT * FROM salem_pois WHERE deleted_at IS NULL';
      const params = [];
      let idx = 1;

      if (category) { sql += ` AND category = $${idx++}`; params.push(category); }
      if (source) { sql += ` AND data_source = $${idx++}`; params.push(source); }
      if (stale === 'true') { sql += ` AND stale_after IS NOT NULL AND stale_after < NOW()`; }
      if (is_tour_poi === 'true') sql += ` AND is_tour_poi = true`;
      if (is_tour_poi === 'false') sql += ` AND is_tour_poi = false`;
      if (is_narrated === 'true') sql += ` AND is_narrated = true`;
      if (is_narrated === 'false') sql += ` AND is_narrated = false`;
      if (lat && lng && radius) {
        sql += ` AND lat BETWEEN $${idx} - $${idx + 2} / 111000.0 AND $${idx} + $${idx + 2} / 111000.0`;
        sql += ` AND lng BETWEEN $${idx + 1} - $${idx + 2} / 111000.0 AND $${idx + 1} + $${idx + 2} / 111000.0`;
        params.push(parseFloat(lat), parseFloat(lng), parseFloat(radius));
        idx += 3;
      }
      if (q) { sql += ` AND (name ILIKE $${idx} OR description ILIKE $${idx})`; params.push(`%${q}%`); idx++; }

      sql += ` ORDER BY priority ASC, name ASC LIMIT $${idx}`;
      params.push(parseInt(limit));

      const { rows } = await pgPool.query(sql, params);
      res.json({ count: rows.length, pois: rows });
    } catch (err) {
      console.error('[Salem] GET /salem/pois error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  app.get('/salem/pois/:id', requirePg, async (req, res) => {
    try {
      const { rows } = await pgPool.query('SELECT * FROM salem_pois WHERE id = $1 AND deleted_at IS NULL', [req.params.id]);
      if (!rows.length) return res.status(404).json({ error: 'Not found' });
      res.json(rows[0]);
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  // ── Businesses (backward compat — filters unified table) ─────────────────

  app.get('/salem/businesses', requirePg, async (req, res) => {
    try {
      const { type, category, source, stale, q, limit = 500 } = req.query;
      let sql = 'SELECT * FROM salem_pois WHERE deleted_at IS NULL';
      const params = [];
      let idx = 1;

      // Legacy business_type filter maps to category
      if (type) { sql += ` AND category = $${idx++}`; params.push(type); }
      if (category) { sql += ` AND category = $${idx++}`; params.push(category); }
      if (source) { sql += ` AND data_source = $${idx++}`; params.push(source); }
      if (stale === 'true') { sql += ` AND stale_after IS NOT NULL AND stale_after < NOW()`; }
      if (q) { sql += ` AND (name ILIKE $${idx} OR description ILIKE $${idx})`; params.push(`%${q}%`); idx++; }

      sql += ` ORDER BY name ASC LIMIT $${idx}`;
      params.push(parseInt(limit));

      const { rows } = await pgPool.query(sql, params);
      res.json({ count: rows.length, businesses: rows });
    } catch (err) {
      console.error('[Salem] GET /salem/businesses error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ── Historical Figures ────────────────────────────────────────────────────

  app.get('/salem/figures', requirePg, async (req, res) => {
    try {
      const { role, q, limit = 100 } = req.query;
      let sql = 'SELECT * FROM salem_historical_figures WHERE 1=1';
      const params = [];
      let idx = 1;

      if (role) { sql += ` AND role = $${idx++}`; params.push(role); }
      if (q) { sql += ` AND (name ILIKE $${idx} OR short_bio ILIKE $${idx})`; params.push(`%${q}%`); idx++; }

      sql += ` ORDER BY surname ASC LIMIT $${idx}`;
      params.push(parseInt(limit));

      const { rows } = await pgPool.query(sql, params);
      res.json({ count: rows.length, figures: rows });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  app.get('/salem/figures/:id', requirePg, async (req, res) => {
    try {
      const { rows } = await pgPool.query('SELECT * FROM salem_historical_figures WHERE id = $1', [req.params.id]);
      if (!rows.length) return res.status(404).json({ error: 'Not found' });
      const facts = await pgPool.query('SELECT * FROM salem_historical_facts WHERE figure_id = $1 ORDER BY date ASC', [req.params.id]);
      const sources = await pgPool.query('SELECT * FROM salem_primary_sources WHERE figure_id = $1 ORDER BY date ASC', [req.params.id]);
      res.json({ ...rows[0], facts: facts.rows, sources: sources.rows });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  // ── Timeline ──────────────────────────────────────────────────────────────

  app.get('/salem/timeline', requirePg, async (req, res) => {
    try {
      const { phase, anchor } = req.query;
      let sql = 'SELECT * FROM salem_timeline_events WHERE 1=1';
      const params = [];
      let idx = 1;

      if (phase) { sql += ` AND crisis_phase = $${idx++}`; params.push(phase); }
      if (anchor === 'true') { sql += ' AND is_anchor = TRUE'; }

      sql += ' ORDER BY date ASC';
      const { rows } = await pgPool.query(sql, params);
      res.json({ count: rows.length, events: rows });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  // ── Primary Sources ───────────────────────────────────────────────────────

  app.get('/salem/sources', requirePg, async (req, res) => {
    try {
      const { type, figure_id, q, limit = 200 } = req.query;
      let sql = 'SELECT * FROM salem_primary_sources WHERE 1=1';
      const params = [];
      let idx = 1;

      if (type) { sql += ` AND source_type = $${idx++}`; params.push(type); }
      if (figure_id) { sql += ` AND figure_id = $${idx++}`; params.push(figure_id); }
      if (q) { sql += ` AND (title ILIKE $${idx} OR excerpt ILIKE $${idx})`; params.push(`%${q}%`); idx++; }

      sql += ` ORDER BY date ASC LIMIT $${idx}`;
      params.push(parseInt(limit));

      const { rows } = await pgPool.query(sql, params);
      res.json({ count: rows.length, sources: rows });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  // ── Tours ─────────────────────────────────────────────────────────────────

  app.get('/salem/tours', requirePg, async (req, res) => {
    try {
      const { rows } = await pgPool.query('SELECT * FROM salem_tours ORDER BY sort_order ASC');
      res.json({ count: rows.length, tours: rows });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  app.get('/salem/tours/:id', requirePg, async (req, res) => {
    try {
      const tour = await pgPool.query('SELECT * FROM salem_tours WHERE id = $1', [req.params.id]);
      if (!tour.rows.length) return res.status(404).json({ error: 'Not found' });
      const stops = await pgPool.query(
        `SELECT s.*, p.name, p.lat, p.lng, p.category, p.short_narration
         FROM salem_tour_stops s
         JOIN salem_pois p ON s.poi_id = p.id
         WHERE s.tour_id = $1
         ORDER BY s.stop_order ASC`,
        [req.params.id]
      );
      res.json({ ...tour.rows[0], stops: stops.rows });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  // ── Events Calendar ───────────────────────────────────────────────────────

  app.get('/salem/events', requirePg, async (req, res) => {
    try {
      const { month, active, upcoming } = req.query;
      let sql = 'SELECT * FROM salem_events_calendar WHERE 1=1';
      const params = [];
      let idx = 1;

      if (month) { sql += ` AND seasonal_month = $${idx++}`; params.push(parseInt(month)); }
      if (active === 'true') {
        sql += ` AND start_date <= $${idx} AND (end_date >= $${idx} OR end_date IS NULL)`;
        params.push(new Date().toISOString().slice(0, 10));
        idx++;
      }
      if (upcoming === 'true') {
        sql += ` AND (start_date >= $${idx} OR end_date >= $${idx})`;
        params.push(new Date().toISOString().slice(0, 10));
        idx++;
      }

      sql += ' ORDER BY start_date ASC';
      const { rows } = await pgPool.query(sql, params);
      res.json({ count: rows.length, events: rows });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  // ── Sync Endpoint ─────────────────────────────────────────────────────────

  app.get('/salem/sync', requirePg, async (req, res) => {
    try {
      const since = req.query.since || '1970-01-01T00:00:00Z';
      const tables = [
        { key: 'pois', table: 'salem_pois' },
        { key: 'figures', table: 'salem_historical_figures' },
        { key: 'facts', table: 'salem_historical_facts' },
        { key: 'timeline', table: 'salem_timeline_events' },
        { key: 'sources', table: 'salem_primary_sources' },
        { key: 'tours', table: 'salem_tours' },
        { key: 'tour_stops', table: 'salem_tour_stops' },
        { key: 'events', table: 'salem_events_calendar' },
      ];

      const result = { server_time: new Date().toISOString() };
      for (const { key, table } of tables) {
        const { rows } = await pgPool.query(
          `SELECT * FROM ${table} WHERE updated_at > $1 ORDER BY updated_at ASC`,
          [since]
        );
        result[key] = rows;
      }

      res.json(result);
    } catch (err) {
      console.error('[Salem] GET /salem/sync error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ── Stats ─────────────────────────────────────────────────────────────────

  app.get('/salem/stats', requirePg, async (req, res) => {
    try {
      const tables = [
        'salem_pois', 'salem_historical_figures',
        'salem_historical_facts', 'salem_timeline_events', 'salem_primary_sources',
        'salem_tours', 'salem_tour_stops', 'salem_events_calendar'
      ];
      const counts = {};
      for (const t of tables) {
        const { rows } = await pgPool.query(`SELECT COUNT(*) AS c FROM ${t}`);
        counts[t] = parseInt(rows[0].c);
      }

      const stale = {};
      const { rows } = await pgPool.query(
        `SELECT COUNT(*) AS c FROM salem_pois WHERE stale_after IS NOT NULL AND stale_after < NOW()`
      );
      stale['salem_pois'] = parseInt(rows[0].c);

      res.json({ counts, stale });
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });
};
