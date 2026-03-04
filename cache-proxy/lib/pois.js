/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module pois.js';

module.exports = function(app, deps) {
  const { radiusHints, pgPool, getRadiusHint, adjustRadiusHint } = deps;

  // ── Radius hint endpoints ───────────────────────────────────────────────

  app.get('/radius-hint', (req, res) => {
    const { lat, lon } = req.query;
    if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });
    const radius = getRadiusHint(lat, lon);
    res.json({ radius });
  });

  app.post('/radius-hint', (req, res) => {
    const { lat, lon, resultCount, error, capped } = req.body;
    if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });
    const isError = !!error;
    const isCapped = !!capped;
    const count = typeof resultCount === 'number' ? resultCount : 0;
    const radius = adjustRadiusHint(lat, lon, count, isError, isCapped);
    res.json({ radius });
  });

  app.get('/radius-hints', (req, res) => {
    const hints = {};
    for (const [key, value] of radiusHints) {
      hints[key] = value;
    }
    res.json({ count: radiusHints.size, hints });
  });

  // ── POI endpoints (PostgreSQL-backed) ─────────────────────────────────────

  app.get('/pois/stats', async (req, res) => {
    if (!pgPool) return res.json({ count: 0 });
    try {
      const result = await pgPool.query('SELECT COUNT(*)::int AS count FROM pois');
      res.json({ count: result.rows[0].count });
    } catch (err) {
      console.error('[POIs] stats error:', err.message);
      res.status(500).json({ error: 'Database query failed' });
    }
  });

  app.get('/pois/export', async (req, res) => {
    if (!pgPool) return res.json({ count: 0, pois: [] });
    try {
      const limit = Math.min(parseInt(req.query.limit) || 1000, 10000);
      const result = await pgPool.query(
        'SELECT osm_type, osm_id, lat, lon, tags, first_seen, last_seen FROM pois ORDER BY last_seen DESC LIMIT $1',
        [limit]
      );
      res.json({ count: result.rows.length, pois: result.rows });
    } catch (err) {
      console.error('[POIs] export error:', err.message);
      res.status(500).json({ error: 'Database query failed' });
    }
  });

  app.get('/pois/bbox', async (req, res) => {
    const { s, w, n, e } = req.query;
    if (!s || !w || !n || !e) return res.status(400).json({ error: 'Must specify s, w, n, e bounds' });
    if (!pgPool) return res.json({ count: 0, elements: [] });
    try {
      const result = await pgPool.query(
        'SELECT osm_type AS type, osm_id AS id, lat, lon, tags FROM pois WHERE lat BETWEEN $1 AND $2 AND lon BETWEEN $3 AND $4',
        [parseFloat(s), parseFloat(n), parseFloat(w), parseFloat(e)]
      );
      const elements = result.rows.map(r => ({
        type: r.type,
        id: r.id,
        lat: parseFloat(r.lat),
        lon: parseFloat(r.lon),
        tags: typeof r.tags === 'string' ? JSON.parse(r.tags) : r.tags,
      }));
      res.json({ count: elements.length, elements });
    } catch (err) {
      console.error('[POIs] bbox error:', err.message);
      res.status(500).json({ error: 'Database query failed' });
    }
  });

  app.get('/poi/:type/:id', async (req, res) => {
    if (!pgPool) return res.status(404).json({ error: 'Database not configured' });
    try {
      const result = await pgPool.query(
        'SELECT osm_type AS type, osm_id AS id, lat, lon, name, category, tags, first_seen, last_seen FROM pois WHERE osm_type = $1 AND osm_id = $2',
        [req.params.type, parseInt(req.params.id)]
      );
      if (result.rows.length === 0) return res.status(404).json({ error: 'POI not found' });
      const row = result.rows[0];
      res.json({
        element: {
          type: row.type,
          id: row.id,
          lat: parseFloat(row.lat),
          lon: parseFloat(row.lon),
          tags: typeof row.tags === 'string' ? JSON.parse(row.tags) : row.tags,
        },
        firstSeen: row.first_seen,
        lastSeen: row.last_seen,
      });
    } catch (err) {
      console.error('[POIs] single POI error:', err.message);
      res.status(500).json({ error: 'Database query failed' });
    }
  });
};
