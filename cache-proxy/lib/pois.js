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
    if (!pgPool) return res.json({ count: 0, elements: [], clusters: null });
    const sF = parseFloat(s), nF = parseFloat(n), wF = parseFloat(w), eF = parseFloat(e);
    try {
      // Quick count first
      const countResult = await pgPool.query(
        'SELECT COUNT(*)::int AS cnt FROM pois WHERE lat BETWEEN $1 AND $2 AND lon BETWEEN $3 AND $4',
        [sF, nF, wF, eF]
      );
      const total = countResult.rows[0].cnt;

      if (total <= 1000) {
        // Normal mode — return individual POIs
        const result = await pgPool.query(
          'SELECT osm_type AS type, osm_id AS id, lat, lon, tags FROM pois WHERE lat BETWEEN $1 AND $2 AND lon BETWEEN $3 AND $4',
          [sF, nF, wF, eF]
        );
        const elements = result.rows.map(r => ({
          type: r.type, id: r.id,
          lat: parseFloat(r.lat), lon: parseFloat(r.lon),
          tags: typeof r.tags === 'string' ? JSON.parse(r.tags) : r.tags,
        }));
        res.json({ count: elements.length, elements, clusters: null });
      } else {
        // Cluster mode — grid-aggregate in SQL, return clusters only
        // Cell size adapts to bbox span: ~20 cells across each axis
        const latSpan = nF - sF;
        const lonSpan = eF - wF;
        const latCell = Math.max(latSpan / 20, 0.001);
        const lonCell = Math.max(lonSpan / 20, 0.001);
        const result = await pgPool.query(
          `SELECT
            FLOOR(lat / $5) AS clat, FLOOR(lon / $6) AS clon,
            COUNT(*)::int AS cnt,
            AVG(lat) AS avg_lat, AVG(lon) AS avg_lon,
            MODE() WITHIN GROUP (ORDER BY COALESCE(
              CASE WHEN tags->>'amenity' IS NOT NULL THEN 'amenity=' || (tags->>'amenity')
                   WHEN tags->>'shop' IS NOT NULL THEN 'shop=' || (tags->>'shop')
                   WHEN tags->>'tourism' IS NOT NULL THEN 'tourism=' || (tags->>'tourism')
                   WHEN tags->>'leisure' IS NOT NULL THEN 'leisure=' || (tags->>'leisure')
                   ELSE 'other'
              END, 'other'
            )) AS dominant_tag
          FROM pois
          WHERE lat BETWEEN $1 AND $2 AND lon BETWEEN $3 AND $4
          GROUP BY clat, clon`,
          [sF, nF, wF, eF, latCell, lonCell]
        );
        const clusters = result.rows.map(r => ({
          lat: parseFloat(r.avg_lat),
          lon: parseFloat(r.avg_lon),
          count: r.cnt,
          tag: r.dominant_tag,
        }));
        res.json({ count: total, elements: [], clusters });
      }
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
