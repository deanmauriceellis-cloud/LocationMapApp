/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module geocode.js';

module.exports = function(app, deps) {
  const geocodeCache = new Map();   // query → { results, ts }
  const GEOCODE_TTL = 24 * 60 * 60 * 1000;  // 24h

  app.get('/geocode', async (req, res) => {
    const q = (req.query.q || '').trim();
    if (!q) return res.status(400).json({ error: 'Missing q parameter' });

    const limit = Math.min(parseInt(req.query.limit) || 5, 10);
    const cacheKey = `geocode:${q.toLowerCase()}:${limit}`;

    // Check cache
    const cached = geocodeCache.get(cacheKey);
    if (cached && Date.now() - cached.ts < GEOCODE_TTL) {
      return res.json(cached.results);
    }

    try {
      const params = new URLSearchParams({
        q,
        lang: 'en',
        limit: String(limit),
        bbox: '-125,24,-66,50',   // Continental US
      });
      const url = `https://photon.komoot.io/api/?${params}`;
      const resp = await fetch(url);
      if (!resp.ok) throw new Error(`Photon ${resp.status}`);
      const data = await resp.json();

      const results = (data.features || []).map(f => {
        const p = f.properties;
        const [lon, lat] = f.geometry.coordinates;
        const city = p.city || p.name || null;
        const state = p.state || null;
        const parts = [p.name, p.city !== p.name ? p.city : null, p.state, p.country]
          .filter(Boolean);
        return {
          lat,
          lon,
          display_name: parts.join(', '),
          type: p.type || null,
          city,
          state,
        };
      });

      geocodeCache.set(cacheKey, { results, ts: Date.now() });
      console.log(`[Geocode] "${q}" → ${results.length} results`);
      res.json(results);
    } catch (err) {
      console.error('[Geocode error]', err.message);
      res.status(502).json({ error: 'Geocode failed', detail: err.message });
    }
  });
};
