/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module mbta.js';

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, stats, log } = deps;

  const MBTA_API_KEY = 'd2dbf0064a5a4e80b9384fea24c43c9b';

  app.get('/mbta/bus-stops', async (req, res) => {
    const cacheKey = 'mbta:bus-stops';
    const ttlMs = 24 * 60 * 60 * 1000;  // 24 hours

    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/mbta/bus-stops', true);
      res.set('Content-Type', 'application/json');
      return res.send(cached.data);
    }

    stats.misses++;
    try {
      const upstreamUrl = `https://api-v3.mbta.com/stops?filter%5Broute_type%5D=3&page%5Blimit%5D=10000&api_key=${MBTA_API_KEY}`;
      const t0 = Date.now();
      const upstream = await fetch(upstreamUrl);
      const elapsed = Date.now() - t0;

      if (!upstream.ok) {
        const errBody = await upstream.text();
        console.error(`[MBTA] Bus stops upstream HTTP ${upstream.status}: ${errBody.substring(0, 300)}`);
        return res.status(upstream.status).json({ error: 'MBTA upstream error', status: upstream.status });
      }

      const body = await upstream.text();
      const parsed = JSON.parse(body);
      const count = parsed.data?.length || 0;
      console.log(`[MBTA] Bus stops: ${count} stops in ${elapsed}ms`);
      log('/mbta/bus-stops', false, elapsed);
      cacheSet(cacheKey, body, { 'content-type': 'application/json' });
      res.set('Content-Type', 'application/json').send(body);
    } catch (err) {
      console.error('[MBTA bus stops upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });
};
