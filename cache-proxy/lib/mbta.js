/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Destructive AI Gurus, LLC, 2026 - Module mbta.js';

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, stats, log } = deps;

  const MBTA_API_KEY = process.env.MBTA_API_KEY;
  if (!MBTA_API_KEY) {
    log('mbta', 'WARN: MBTA_API_KEY not set in .env — MBTA routes will return 503');
  }

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

  // ── GET /mbta/bus-stops/bbox?s=&w=&n=&e= — bus stops in viewport, max 200 ──
  // Reads from the cached /mbta/bus-stops full list, filters by bbox,
  // and samples down to MAX_BUS_STOPS if the area is too dense.
  const MAX_BUS_STOPS = 200;
  let _busStopsParsed = null;  // lazy-parsed flat array from cached JSON:API

  function ensureBusStopsParsed() {
    if (_busStopsParsed) return _busStopsParsed;
    const cached = cacheGet('mbta:bus-stops', 24 * 60 * 60 * 1000);
    if (!cached) return null;
    const json = JSON.parse(cached.data);
    _busStopsParsed = (json.data || []).map(s => ({
      id: s.id,
      name: s.attributes?.name || s.id,
      lat: s.attributes?.latitude,
      lon: s.attributes?.longitude,
    })).filter(s => s.lat != null && s.lon != null);
    return _busStopsParsed;
  }

  app.get('/mbta/bus-stops/bbox', async (req, res) => {
    const { s, w, n, e } = req.query;
    if (!s || !w || !n || !e) {
      return res.status(400).json({ error: 'bbox params required: s, w, n, e' });
    }
    const south = +s, west = +w, north = +n, east = +e;

    // Ensure we have the full bus stops list cached
    let stops = ensureBusStopsParsed();
    if (!stops) {
      // Trigger a fetch of the full list first
      try {
        const upstreamUrl = `https://api-v3.mbta.com/stops?filter%5Broute_type%5D=3&page%5Blimit%5D=10000&api_key=${MBTA_API_KEY}`;
        const upstream = await fetch(upstreamUrl);
        if (upstream.ok) {
          const body = await upstream.text();
          cacheSet('mbta:bus-stops', body, { 'content-type': 'application/json' });
          _busStopsParsed = null;  // force re-parse
          stops = ensureBusStopsParsed();
        }
      } catch (err) {
        console.error('[MBTA bus-stops/bbox] Failed to fetch full list:', err.message);
      }
      if (!stops) return res.json([]);
    }

    // Filter to bbox
    let inBbox = stops.filter(st => st.lat >= south && st.lat <= north && st.lon >= west && st.lon <= east);

    // Sample if too many
    if (inBbox.length > MAX_BUS_STOPS) {
      // Uniform sampling to keep spatial distribution
      const step = inBbox.length / MAX_BUS_STOPS;
      const sampled = [];
      for (let i = 0; i < MAX_BUS_STOPS; i++) {
        sampled.push(inBbox[Math.floor(i * step)]);
      }
      inBbox = sampled;
    }

    res.json(inBbox);
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
          tripId: trip?.id || '',
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
  // Accepts a single stop ID or comma-separated list. Also supports
  // ?stop_name=X to auto-resolve all child platforms for a station name.
  app.get('/mbta/predictions', async (req, res) => {
    let stopIds = req.query.stop;
    const stopName = req.query.stop_name;

    // Resolve station name → all child stop IDs from cached stations
    if (stopName && !stopIds) {
      const stationCacheKey = 'mbta:stations:0,1,2';
      const stationCache = cacheGet(stationCacheKey, 3600 * 1000);
      if (stationCache) {
        const allStations = JSON.parse(stationCache.data);
        const matching = allStations.filter(s => s.name === stopName);
        if (matching.length > 0) {
          stopIds = matching.map(s => s.id).join(',');
        }
      }
      if (!stopIds) {
        return res.status(400).json({ error: 'Could not resolve stop_name to IDs' });
      }
    }

    if (!stopIds) return res.status(400).json({ error: 'stop or stop_name parameter required' });

    const cacheKey = `mbta:predictions:${stopIds}`;
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
        'filter[stop]': stopIds,
        'include': 'route,trip',
        'page[limit]': '40',
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
      })
      // Filter out predictions with no times at all
      .filter(p => p.arrivalTime || p.departureTime)
      // Sort by earliest available time
      .sort((a, b) => {
        const ta = new Date(a.arrivalTime || a.departureTime).getTime();
        const tb = new Date(b.arrivalTime || b.departureTime).getTime();
        return ta - tb;
      });

      console.log(`[MBTA] Predictions stop=${stopIds}: ${predictions.length} in ${elapsed}ms`);
      log('/mbta/predictions', false, elapsed);
      cacheSet(cacheKey, JSON.stringify(predictions), { 'content-type': 'application/json' });
      res.json(predictions);
    } catch (err) {
      console.error('[MBTA predictions upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });

  // ── GET /mbta/upstream/* — pass-through to api-v3.mbta.com with key injection ──
  //
  // S255: lets the Android app talk to MBTA via the cache-proxy WITHOUT
  // shipping the API key inside the APK. The OkHttp client points at
  // `http://10.0.0.229:4300/mbta/upstream` and builds the rest of the URL
  // exactly as it would for api-v3.mbta.com. We append `&api_key=...` server
  // side and proxy the JSON:API response verbatim so the existing Android
  // JSON:API parsers keep working.
  //
  // Cached by full upstream URL with 15s TTL — matches `/mbta/vehicles`
  // cache cadence. Short cache because positions move; predictions/stops
  // tolerate the same window without UX impact.
  app.get('/mbta/upstream/*', async (req, res) => {
    if (!MBTA_API_KEY) {
      return res.status(503).json({ error: 'MBTA_API_KEY not configured on cache-proxy' });
    }
    // Express captures everything after /mbta/upstream/ in req.params[0].
    const tail = req.params[0] || '';
    if (!tail) return res.status(400).json({ error: 'path required after /mbta/upstream/' });

    // Pass the raw query string through to MBTA verbatim — DO NOT roundtrip
    // via req.query / URLSearchParams. Express's default qs parser collapses
    // `filter[route]=Red` into `{filter: {route: 'Red'}}`, which then renders
    // back as `filter=[object+Object]` and silently drops the filter. The raw
    // string from req.originalUrl preserves MBTA's bracket-encoded params
    // exactly as the Android client built them.
    const qIdx = req.originalUrl.indexOf('?');
    const rawQuery = qIdx === -1 ? '' : req.originalUrl.slice(qIdx + 1);
    // Strip any client-sent api_key=... segment so we can re-append ours and
    // avoid double-keying. Match the param at start-of-string or after `&`,
    // through the next `&` or end-of-string.
    const sanitized = rawQuery.replace(/(^|&)api_key=[^&]*/g, '').replace(/^&/, '');
    const upstreamQuery = sanitized
      ? `${sanitized}&api_key=${MBTA_API_KEY}`
      : `api_key=${MBTA_API_KEY}`;
    const upstreamUrl = `https://api-v3.mbta.com/${tail}?${upstreamQuery}`;

    // Cache key uses the sanitized (key-stripped) query so hits don't depend
    // on the secret value.
    const cacheKey = `mbta:upstream:${tail}?${sanitized}`;
    const ttlMs = 15 * 1000;

    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/mbta/upstream', true);
      res.set('Content-Type', 'application/vnd.api+json');
      return res.send(cached.data);
    }

    stats.misses++;
    try {
      const t0 = Date.now();
      const upstream = await fetch(upstreamUrl);
      const elapsed = Date.now() - t0;
      if (!upstream.ok) {
        const errBody = await upstream.text();
        console.error(`[MBTA] upstream ${tail} HTTP ${upstream.status}: ${errBody.substring(0, 200)}`);
        return res.status(upstream.status).json({ error: 'MBTA upstream error', status: upstream.status, path: tail });
      }
      const body = await upstream.text();
      console.log(`[MBTA] upstream ${tail} ${upstream.status} in ${elapsed}ms (${body.length}B)`);
      log('/mbta/upstream', false, elapsed);
      cacheSet(cacheKey, body, { 'content-type': 'application/vnd.api+json' });
      res.set('Content-Type', 'application/vnd.api+json').send(body);
    } catch (err) {
      console.error('[MBTA upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });

  // ── GET /mbta/trip-predictions?trip=TRIP_ID — next stops for a vehicle's trip ──
  app.get('/mbta/trip-predictions', async (req, res) => {
    const tripId = req.query.trip;
    if (!tripId) return res.status(400).json({ error: 'trip parameter required' });

    const cacheKey = `mbta:trip-pred:${tripId}`;
    const ttlMs = 30 * 1000;  // 30s

    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log('/mbta/trip-predictions', true);
      return res.json(JSON.parse(cached.data));
    }

    stats.misses++;
    try {
      const params = new URLSearchParams({
        'filter[trip]': tripId,
        'include': 'route,stop',
        'sort': 'stop_sequence',
        'api_key': MBTA_API_KEY,
      });
      const t0 = Date.now();
      const upstream = await fetch(`https://api-v3.mbta.com/predictions?${params}`);
      const elapsed = Date.now() - t0;

      if (!upstream.ok) {
        const errBody = await upstream.text();
        console.error(`[MBTA] Trip predictions upstream HTTP ${upstream.status}: ${errBody.substring(0, 300)}`);
        return res.status(upstream.status).json({ error: 'MBTA upstream error', status: upstream.status });
      }

      const json = await upstream.json();
      const includedMap = buildIncludedMap(json.included);

      const predictions = (json.data || []).map(p => {
        const attrs = p.attributes;
        const route = resolveRel(p, 'route', includedMap);
        const stop = resolveRel(p, 'stop', includedMap);
        return {
          id: p.id,
          routeId: route?.id || '',
          routeName: route?.attributes?.long_name || route?.attributes?.short_name || route?.id || '',
          stopName: stop?.attributes?.name || '',
          arrivalTime: attrs.arrival_time,
          departureTime: attrs.departure_time,
          status: attrs.status,
          stopSequence: attrs.stop_sequence,
          routeColor: route?.attributes?.color ? `#${route.attributes.color}` : null,
        };
      })
      .filter(p => p.arrivalTime || p.departureTime)
      // Only future predictions
      .filter(p => {
        const t = new Date(p.arrivalTime || p.departureTime).getTime();
        return t > Date.now() - 60_000;  // include "arriving now" within 1 min
      })
      .sort((a, b) => (a.stopSequence || 0) - (b.stopSequence || 0))
      .slice(0, 5);  // next 5 stops

      console.log(`[MBTA] Trip predictions trip=${tripId}: ${predictions.length} in ${elapsed}ms`);
      log('/mbta/trip-predictions', false, elapsed);
      cacheSet(cacheKey, JSON.stringify(predictions), { 'content-type': 'application/json' });
      res.json(predictions);
    } catch (err) {
      console.error('[MBTA trip-predictions upstream error]', err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });
};
