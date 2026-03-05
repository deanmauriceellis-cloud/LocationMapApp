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

  // Helper: build lookup from JSON:API included[] array
  function buildIncludedMap(included) {
    const map = {};
    if (!Array.isArray(included)) return map;
    for (const item of included) {
      map[`${item.type}:${item.id}`] = item;
    }
    return map;
  }

  // Helper: resolve a relationship from included map
  function resolveRel(item, relName, includedMap) {
    const rel = item.relationships?.[relName]?.data;
    if (!rel) return null;
    return includedMap[`${rel.type}:${rel.id}`] || null;
  }

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

  // ── GET /mbta/vehicles?route_type=0|1|2|3 — flat vehicle list ─────────
  app.get('/mbta/vehicles', async (req, res) => {
    const routeType = req.query.route_type;
    if (routeType == null) return res.status(400).json({ error: 'route_type required (0=LightRail, 1=HeavyRail, 2=CR, 3=Bus)' });

    const cacheKey = `mbta:vehicles:${routeType}`;
    const ttlMs = 15 * 1000;  // 15s

    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/mbta/vehicles', true);
      return res.json(JSON.parse(cached.data));
    }

    stats.misses++;
    try {
      const params = new URLSearchParams({
        'filter[route_type]': routeType,
        'include': 'stop,route,trip',
        'api_key': MBTA_API_KEY,
      });
      const t0 = Date.now();
      const upstream = await fetch(`https://api-v3.mbta.com/vehicles?${params}`);
      const elapsed = Date.now() - t0;

      if (!upstream.ok) {
        const errBody = await upstream.text();
        console.error(`[MBTA] Vehicles upstream HTTP ${upstream.status}: ${errBody.substring(0, 300)}`);
        return res.status(upstream.status).json({ error: 'MBTA upstream error', status: upstream.status });
      }

      const json = await upstream.json();
      const includedMap = buildIncludedMap(json.included);

      const vehicles = (json.data || []).map(v => {
        const attrs = v.attributes;
        const stop = resolveRel(v, 'stop', includedMap);
        const route = resolveRel(v, 'route', includedMap);
        const trip = resolveRel(v, 'trip', includedMap);
        return {
          id: v.id,
          label: attrs.label || v.id,
          routeId: route?.id || '',
          routeName: route?.attributes?.long_name || route?.attributes?.short_name || route?.id || '',
          headsign: trip?.attributes?.headsign || '',
          stopName: stop?.attributes?.name || '',
          lat: attrs.latitude,
          lon: attrs.longitude,
          bearing: attrs.bearing,
          speed: attrs.speed,
          status: attrs.current_status || 'IN_TRANSIT_TO',
          routeType: Number(routeType),
          updatedAt: attrs.updated_at,
        };
      }).filter(v => v.lat != null && v.lon != null);

      console.log(`[MBTA] Vehicles type=${routeType}: ${vehicles.length} in ${elapsed}ms`);
      log('/mbta/vehicles', false, elapsed);
      cacheSet(cacheKey, JSON.stringify(vehicles), { 'content-type': 'application/json' });
      res.json(vehicles);
    } catch (err) {
      console.error('[MBTA vehicles upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });

  // ── GET /mbta/stations?route_type=0,1,2 — flat station list ───────────
  app.get('/mbta/stations', async (req, res) => {
    const routeType = req.query.route_type || '0,1,2';
    const cacheKey = `mbta:stations:${routeType}`;
    const ttlMs = 60 * 60 * 1000;  // 1 hour

    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/mbta/stations', true);
      return res.json(JSON.parse(cached.data));
    }

    stats.misses++;
    try {
      const params = new URLSearchParams({
        'filter[route_type]': routeType,
        'page[limit]': '1000',
        'api_key': MBTA_API_KEY,
      });
      const t0 = Date.now();
      const upstream = await fetch(`https://api-v3.mbta.com/stops?${params}`);
      const elapsed = Date.now() - t0;

      if (!upstream.ok) {
        const errBody = await upstream.text();
        console.error(`[MBTA] Stations upstream HTTP ${upstream.status}: ${errBody.substring(0, 300)}`);
        return res.status(upstream.status).json({ error: 'MBTA upstream error', status: upstream.status });
      }

      const json = await upstream.json();

      const stations = (json.data || []).map(s => ({
        id: s.id,
        name: s.attributes?.name || s.id,
        lat: s.attributes?.latitude,
        lon: s.attributes?.longitude,
        routeIds: [],  // MBTA stops endpoint doesn't include routes by default
      })).filter(s => s.lat != null && s.lon != null);

      console.log(`[MBTA] Stations type=${routeType}: ${stations.length} in ${elapsed}ms`);
      log('/mbta/stations', false, elapsed);
      cacheSet(cacheKey, JSON.stringify(stations), { 'content-type': 'application/json' });
      res.json(stations);
    } catch (err) {
      console.error('[MBTA stations upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });

  // ── GET /mbta/predictions?stop=STOP_ID — flat prediction list ─────────
  app.get('/mbta/predictions', async (req, res) => {
    const stop = req.query.stop;
    if (!stop) return res.status(400).json({ error: 'stop parameter required' });

    const cacheKey = `mbta:predictions:${stop}`;
    const ttlMs = 30 * 1000;  // 30s

    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/mbta/predictions', true);
      return res.json(JSON.parse(cached.data));
    }

    stats.misses++;
    try {
      const params = new URLSearchParams({
        'filter[stop]': stop,
        'include': 'route,trip',
        'sort': 'arrival_time',
        'page[limit]': '20',
        'api_key': MBTA_API_KEY,
      });
      const t0 = Date.now();
      const upstream = await fetch(`https://api-v3.mbta.com/predictions?${params}`);
      const elapsed = Date.now() - t0;

      if (!upstream.ok) {
        const errBody = await upstream.text();
        console.error(`[MBTA] Predictions upstream HTTP ${upstream.status}: ${errBody.substring(0, 300)}`);
        return res.status(upstream.status).json({ error: 'MBTA upstream error', status: upstream.status });
      }

      const json = await upstream.json();
      const includedMap = buildIncludedMap(json.included);

      const predictions = (json.data || []).map(p => {
        const attrs = p.attributes;
        const route = resolveRel(p, 'route', includedMap);
        const trip = resolveRel(p, 'trip', includedMap);
        return {
          id: p.id,
          routeId: route?.id || '',
          routeName: route?.attributes?.long_name || route?.attributes?.short_name || route?.id || '',
          headsign: trip?.attributes?.headsign || '',
          arrivalTime: attrs.arrival_time,
          departureTime: attrs.departure_time,
          status: attrs.status,
          routeColor: route?.attributes?.color ? `#${route.attributes.color}` : null,
        };
      });

      console.log(`[MBTA] Predictions stop=${stop}: ${predictions.length} in ${elapsed}ms`);
      log('/mbta/predictions', false, elapsed);
      cacheSet(cacheKey, JSON.stringify(predictions), { 'content-type': 'application/json' });
      res.json(predictions);
    } catch (err) {
      console.error('[MBTA predictions upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });
};
