const express = require('express');
const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');
const app = express();
const PORT = 3000;

// ── PostgreSQL pool (optional — /db/* endpoints require DATABASE_URL) ────────

let pgPool = null;
if (process.env.DATABASE_URL) {
  pgPool = new Pool({
    connectionString: process.env.DATABASE_URL,
    max: 5,
    connectionTimeoutMillis: 5000,
  });
  pgPool.on('error', (err) => console.error('[PG Pool] Unexpected error:', err.message));
}

function requirePg(req, res, next) {
  if (!pgPool) return res.status(503).json({ error: 'Database not configured (set DATABASE_URL)' });
  next();
}

// ── OpenSky OAuth2 (optional — set OPENSKY_CLIENT_ID + OPENSKY_CLIENT_SECRET) ──

const OPENSKY_TOKEN_URL = 'https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token';
let openskyToken = null;    // { access_token, expiresAt }

async function getOpenskyToken() {
  const clientId = process.env.OPENSKY_CLIENT_ID;
  const clientSecret = process.env.OPENSKY_CLIENT_SECRET;
  if (!clientId || !clientSecret) return null;

  // Reuse token if still valid (5 min buffer)
  if (openskyToken && Date.now() < openskyToken.expiresAt - 300000) {
    return openskyToken.access_token;
  }

  try {
    const resp = await fetch(OPENSKY_TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=client_credentials&client_id=${encodeURIComponent(clientId)}&client_secret=${encodeURIComponent(clientSecret)}`,
    });
    if (!resp.ok) {
      console.error(`[OpenSky] Token request failed: ${resp.status}`);
      return null;
    }
    const data = await resp.json();
    openskyToken = {
      access_token: data.access_token,
      expiresAt: Date.now() + data.expires_in * 1000,
    };
    console.log(`[OpenSky] Token refreshed, expires in ${data.expires_in}s`);
    return openskyToken.access_token;
  } catch (err) {
    console.error('[OpenSky] Token error:', err.message);
    return null;
  }
}

const openskyConfigured = !!(process.env.OPENSKY_CLIENT_ID && process.env.OPENSKY_CLIENT_SECRET);

// Parse URL-encoded bodies (Overpass POST sends form data)
app.use(express.urlencoded({ extended: true, limit: '1mb' }));
app.use(express.json());

// ── In-memory cache with disk persistence ───────────────────────────────────

const CACHE_FILE = path.join(__dirname, 'cache-data.json');
const cache = new Map();   // key → { data, headers, timestamp }
let stats = { hits: 0, misses: 0 };
let savePending = false;

// ── Radius hints ────────────────────────────────────────────────────────────

const RADIUS_HINTS_FILE = path.join(__dirname, 'radius-hints.json');
const radiusHints = new Map();   // "LAT3:LON3" → { radius, updatedAt }
const DEFAULT_RADIUS = 3000;
const MIN_RADIUS = 500;
const MAX_RADIUS = 15000;
const RADIUS_SHRINK = 0.70;
const RADIUS_GROW   = 1.30;
const MIN_USEFUL_POI = 5;
let hintSavePending = false;

function loadRadiusHints() {
  try {
    if (!fs.existsSync(RADIUS_HINTS_FILE)) return;
    const raw = fs.readFileSync(RADIUS_HINTS_FILE, 'utf8');
    const entries = JSON.parse(raw);
    for (const [key, value] of entries) {
      radiusHints.set(key, value);
    }
    console.log(`Loaded ${radiusHints.size} radius hints from disk`);
  } catch (err) {
    console.error('Failed to load radius hints from disk:', err.message);
  }
}

function saveRadiusHints() {
  if (hintSavePending) return;
  hintSavePending = true;
  setTimeout(() => {
    hintSavePending = false;
    try {
      const entries = [...radiusHints.entries()];
      fs.writeFileSync(RADIUS_HINTS_FILE, JSON.stringify(entries));
      console.log(`Saved ${entries.length} radius hints to disk`);
    } catch (err) {
      console.error('Failed to save radius hints to disk:', err.message);
    }
  }, 2000);
}

function gridKey(lat, lon) {
  return `${parseFloat(lat).toFixed(3)}:${parseFloat(lon).toFixed(3)}`;
}

function getRadiusHint(lat, lon) {
  const key = gridKey(lat, lon);
  const hint = radiusHints.get(key);
  if (hint) return hint.radius;

  // Fuzzy: find nearest hint within 1 mile (~0.01449°)
  const latF = parseFloat(lat);
  const lonF = parseFloat(lon);
  const ONE_MILE_DEG = 0.01449;
  let nearest = null;
  let nearestDist = Infinity;

  for (const [k, v] of radiusHints) {
    const parts = k.split(':');
    const hLat = parseFloat(parts[0]);
    const hLon = parseFloat(parts[1]);
    const dLat = hLat - latF;
    const dLon = (hLon - lonF) * Math.cos(latF * Math.PI / 180);
    const dist = Math.sqrt(dLat * dLat + dLon * dLon);
    if (dist <= ONE_MILE_DEG && dist < nearestDist) {
      nearest = v;
      nearestDist = dist;
    }
  }

  if (nearest) {
    const miles = (nearestDist / ONE_MILE_DEG).toFixed(2);
    console.log(`[Radius] Fuzzy hit for ${key} — nearest hint ${miles}mi away → ${nearest.radius}m`);
    return nearest.radius;
  }

  return DEFAULT_RADIUS;
}

function adjustRadiusHint(lat, lon, resultCount, error) {
  const key = gridKey(lat, lon);
  const current = radiusHints.get(key);
  let radius = current ? current.radius : DEFAULT_RADIUS;

  if (error) {
    radius = Math.round(radius * RADIUS_SHRINK);
  } else if (resultCount < MIN_USEFUL_POI) {
    radius = Math.round(radius * RADIUS_GROW);
  }

  radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));

  if (error) {
    console.log(`[Radius] ${key} error → shrink to ${radius}m`);
  } else if (resultCount < MIN_USEFUL_POI) {
    console.log(`[Radius] ${key} only ${resultCount} POIs → grow to ${radius}m`);
  } else {
    console.log(`[Radius] ${key} ${resultCount} POIs — confirmed at ${radius}m`);
  }
  radiusHints.set(key, { radius, updatedAt: Date.now() });
  saveRadiusHints();
  return radius;
}

function loadCache() {
  try {
    if (!fs.existsSync(CACHE_FILE)) return;
    const raw = fs.readFileSync(CACHE_FILE, 'utf8');
    const entries = JSON.parse(raw);
    for (const [key, value] of entries) {
      cache.set(key, value);
    }
    console.log(`Loaded ${cache.size} cache entries from disk`);
  } catch (err) {
    console.error('Failed to load cache from disk:', err.message);
  }
}

function saveCache() {
  if (savePending) return;
  savePending = true;
  // Debounce: write 2s after the last change to batch rapid inserts
  setTimeout(() => {
    savePending = false;
    try {
      const entries = [...cache.entries()];
      fs.writeFileSync(CACHE_FILE, JSON.stringify(entries));
      console.log(`Saved ${entries.length} cache entries to disk (${(fs.statSync(CACHE_FILE).size / 1024 / 1024).toFixed(1)}MB)`);
    } catch (err) {
      console.error('Failed to save cache to disk:', err.message);
    }
  }, 2000);
}

function cacheGet(key, ttlMs) {
  const entry = cache.get(key);
  if (!entry) return null;
  if (Date.now() - entry.timestamp > ttlMs) {
    cache.delete(key);
    saveCache();
    return null;
  }
  return entry;
}

function cacheSet(key, data, headers) {
  cache.set(key, { data, headers, timestamp: Date.now() });
  saveCache();
}

// ── Individual POI cache ─────────────────────────────────────────────────────

const POI_CACHE_FILE = path.join(__dirname, 'poi-cache.json');
const poiCache = new Map();   // "poi:TYPE:ID" → { element, firstSeen, lastSeen }
let poiSavePending = false;

function loadPoiCache() {
  try {
    if (!fs.existsSync(POI_CACHE_FILE)) return;
    const raw = fs.readFileSync(POI_CACHE_FILE, 'utf8');
    const entries = JSON.parse(raw);
    for (const [key, value] of entries) {
      poiCache.set(key, value);
    }
    console.log(`Loaded ${poiCache.size} individual POIs from disk`);
  } catch (err) {
    console.error('Failed to load POI cache from disk:', err.message);
  }
}

function savePoiCache() {
  if (poiSavePending) return;
  poiSavePending = true;
  setTimeout(() => {
    poiSavePending = false;
    try {
      const entries = [...poiCache.entries()];
      fs.writeFileSync(POI_CACHE_FILE, JSON.stringify(entries));
      console.log(`Saved ${entries.length} POIs to disk (${(fs.statSync(POI_CACHE_FILE).size / 1024 / 1024).toFixed(1)}MB)`);
    } catch (err) {
      console.error('Failed to save POI cache to disk:', err.message);
    }
  }, 2000);
}

function cacheIndividualPois(jsonBody) {
  try {
    const parsed = JSON.parse(jsonBody);
    if (!parsed.elements || !Array.isArray(parsed.elements)) return;

    const now = Date.now();
    let added = 0;
    let updated = 0;

    for (const element of parsed.elements) {
      if (!element.type || !element.id) continue;
      const key = `poi:${element.type}:${element.id}`;
      const existing = poiCache.get(key);

      if (existing) {
        existing.element = element;
        existing.lastSeen = now;
        updated++;
      } else {
        poiCache.set(key, { element, firstSeen: now, lastSeen: now });
        added++;
      }
    }

    console.log(`[POI Cache] +${added} new, ${updated} updated (${poiCache.size} total)`);
    if (added > 0 || updated > 0) savePoiCache();
  } catch (err) {
    console.error('[POI Cache] Failed to parse response:', err.message);
  }
}

// ── Load all caches from disk ────────────────────────────────────────────────

loadCache();
loadRadiusHints();
loadPoiCache();

// ── Logging ─────────────────────────────────────────────────────────────────

function log(endpoint, hit, upstreamMs) {
  const ts = new Date().toISOString();
  const status = hit ? 'HIT' : 'MISS';
  const timing = hit ? '' : ` upstream=${upstreamMs}ms`;
  console.log(`[${ts}] ${endpoint} ${status}${timing} (${cache.size} cached)`);
}

// ── Overpass POI proxy ──────────────────────────────────────────────────────

const OVERPASS_TTL = 365 * 24 * 60 * 60 * 1000;  // 365 days

function parseOverpassCacheKey(dataField) {
  // Extract around:RADIUS,LAT,LON from the Overpass QL query
  const aroundMatch = dataField.match(/around:(\d+),([-\d.]+),([-\d.]+)/);
  if (!aroundMatch) return null;

  const lat = parseFloat(aroundMatch[2]).toFixed(3);
  const lon = parseFloat(aroundMatch[3]).toFixed(3);

  // Extract all tag filters like ["amenity"="restaurant"] or ["shop"]
  const tagMatches = [...dataField.matchAll(/\["([^"]+)"(?:="([^"]+)")?\]/g)];
  const tags = [...new Set(tagMatches.map(m => m[2] ? `${m[1]}=${m[2]}` : m[1]))].sort();

  return `overpass:${lat}:${lon}:${tags.join(',')}`;
}

app.post('/overpass', async (req, res) => {
  const dataField = req.body.data || '';
  const cacheKey = parseOverpassCacheKey(dataField);
  const cacheOnly = req.headers['x-cache-only'] === 'true';

  if (cacheKey) {
    const cached = cacheGet(cacheKey, OVERPASS_TTL);
    if (cached) {
      stats.hits++;
      log('/overpass', true);
      res.set('Content-Type', cached.headers['content-type'] || 'application/json');
      return res.send(cached.data);
    }
  }

  // Cache-only mode: search neighboring grid cells (3x3) and merge results
  if (cacheOnly) {
    const aroundMatch = dataField.match(/around:(\d+),([-\d.]+),([-\d.]+)/);
    if (aroundMatch) {
      const tagMatches = [...dataField.matchAll(/\["([^"]+)"(?:="([^"]+)")?\]/g)];
      const tags = [...new Set(tagMatches.map(m => m[2] ? `${m[1]}=${m[2]}` : m[1]))].sort();
      const tagStr = tags.join(',');
      const lat = parseFloat(aroundMatch[2]);
      const lon = parseFloat(aroundMatch[3]);
      const step = 0.001; // 3dp grid step
      const merged = new Map(); // dedup by element id
      for (let dlat = -1; dlat <= 1; dlat++) {
        for (let dlon = -1; dlon <= 1; dlon++) {
          const nlat = (lat + dlat * step).toFixed(3);
          const nlon = (lon + dlon * step).toFixed(3);
          const nkey = `overpass:${nlat}:${nlon}:${tagStr}`;
          const cached = cacheGet(nkey, OVERPASS_TTL);
          if (cached) {
            try {
              const data = JSON.parse(cached.data);
              if (data.elements) {
                for (const el of data.elements) {
                  merged.set(`${el.type}/${el.id}`, el);
                }
              }
            } catch (e) { /* skip unparseable */ }
          }
        }
      }
      if (merged.size > 0) {
        stats.hits++;
        log('/overpass (cache-nearby)', true, 0, `${merged.size} POIs from neighbors`);
        return res.json({ elements: [...merged.values()] });
      }
    }
    log('/overpass (cache-only)', false, 0);
    return res.status(204).end();
  }

  stats.misses++;
  try {
    const t0 = Date.now();
    const upstream = await fetch('https://overpass-api.de/api/interpreter', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `data=${encodeURIComponent(dataField)}`,
    });
    const elapsed = Date.now() - t0;
    const body = await upstream.text();
    const contentType = upstream.headers.get('content-type') || 'application/json';

    log('/overpass', false, elapsed);

    if (upstream.ok && cacheKey) {
      cacheSet(cacheKey, body, { 'content-type': contentType });
      cacheIndividualPois(body);
    }

    res.status(upstream.status).set('Content-Type', contentType).send(body);
  } catch (err) {
    console.error('[Overpass upstream error]', err.message);
    res.status(502).json({ error: 'Upstream request failed', detail: err.message });
  }
});

// ── Generic GET proxy helper ────────────────────────────────────────────────

function proxyGet(route, upstreamUrl, cacheKey, ttlMs, extraHeaders = {}) {
  app.get(route, async (req, res) => {
    const cached = cacheGet(cacheKey, ttlMs);
    if (cached) {
      stats.hits++;
      log(route, true);
      res.set('Content-Type', cached.headers['content-type'] || 'application/json');
      return res.send(cached.data);
    }

    stats.misses++;
    try {
      const t0 = Date.now();
      const upstream = await fetch(upstreamUrl, { headers: extraHeaders });
      const elapsed = Date.now() - t0;
      const body = await upstream.text();
      const contentType = upstream.headers.get('content-type') || 'application/json';

      log(route, false, elapsed);

      if (upstream.ok) {
        cacheSet(cacheKey, body, { 'content-type': contentType });
      }

      res.status(upstream.status).set('Content-Type', contentType).send(body);
    } catch (err) {
      console.error(`[${route} upstream error]`, err.message);
      res.status(502).json({ error: 'Upstream request failed', detail: err.message });
    }
  });
}

// ── USGS Earthquakes (2h TTL) ───────────────────────────────────────────────

proxyGet(
  '/earthquakes',
  'https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_week.geojson',
  'earthquakes',
  2 * 60 * 60 * 1000
);

// ── NWS Weather Alerts (1h TTL) ─────────────────────────────────────────────

proxyGet(
  '/nws-alerts',
  'https://api.weather.gov/alerts/active?status=actual&message_type=alert',
  'nws-alerts',
  60 * 60 * 1000,
  { 'User-Agent': 'LocationMapApp/1.1 contact@example.com' }
);

// ── METAR (1h TTL, bbox passthrough) ────────────────────────────────────────

app.get('/metar', async (req, res) => {
  const { bbox } = req.query;
  if (!bbox) return res.status(400).json({ error: 'Must specify bbox (lat0,lon0,lat1,lon1)' });

  const cacheKey = `metar:${bbox}`;
  const ttlMs = 60 * 60 * 1000;
  const cached = cacheGet(cacheKey, ttlMs);
  if (cached) {
    stats.hits++;
    log('/metar', true);
    res.set('Content-Type', cached.headers['content-type'] || 'application/json');
    return res.send(cached.data);
  }

  stats.misses++;
  try {
    const upstreamUrl = `https://aviationweather.gov/api/data/metar?format=json&hours=1&taf=false&bbox=${encodeURIComponent(bbox)}`;
    const t0 = Date.now();
    const upstream = await fetch(upstreamUrl);
    const elapsed = Date.now() - t0;
    const body = await upstream.text();
    const contentType = upstream.headers.get('content-type') || 'application/json';

    log('/metar', false, elapsed);

    if (upstream.ok) {
      cacheSet(cacheKey, body, { 'content-type': contentType });
    }

    res.status(upstream.status).set('Content-Type', contentType).send(body);
  } catch (err) {
    console.error('[METAR upstream error]', err.message);
    res.status(502).json({ error: 'Upstream request failed', detail: err.message });
  }
});

// ── Aircraft sighting tracker (real-time DB writes) ──────────────────────

const activeSightings = new Map();  // icao24 → { id, lastSeen }
const SIGHTING_GAP_MS = 5 * 60 * 1000;  // 5 min gap = new sighting

async function trackAircraftSightings(body) {
  if (!pgPool) return;
  try {
    const data = JSON.parse(body);
    const states = data.states || [];
    if (states.length === 0) return;

    const now = new Date();
    let inserted = 0, updated = 0;

    for (const s of states) {
      const icao24 = s[0];
      const callsign = s[1]?.trim() || null;
      const origin = s[2] || null;
      const lon = s[5], lat = s[6];
      const baro = s[7], onGround = s[8];
      const velocity = s[9], heading = s[10];
      const vertRate = s[11], squawk = s[14];

      if (!icao24 || lat == null || lon == null) continue;

      const active = activeSightings.get(icao24);
      if (active && (now - active.lastSeen) < SIGHTING_GAP_MS) {
        // Update existing sighting
        await pgPool.query(
          `UPDATE aircraft_sightings SET
            last_seen=$1, last_lat=$2, last_lon=$3, last_altitude=$4,
            last_heading=$5, last_velocity=$6, last_vertical_rate=$7,
            squawk=$8, on_ground=$9, callsign=COALESCE($10, callsign)
          WHERE id=$11`,
          [now, lat, lon, baro, heading, velocity, vertRate, squawk, !!onGround, callsign, active.id]
        );
        active.lastSeen = now;
        updated++;
      } else {
        // New sighting
        const res = await pgPool.query(
          `INSERT INTO aircraft_sightings
            (icao24, callsign, origin_country, first_seen, last_seen,
             first_lat, first_lon, first_altitude, first_heading,
             last_lat, last_lon, last_altitude, last_heading,
             last_velocity, last_vertical_rate, squawk, on_ground)
          VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17)
          RETURNING id`,
          [icao24, callsign, origin, now, now,
           lat, lon, baro, heading,
           lat, lon, baro, heading,
           velocity, vertRate, squawk, !!onGround]
        );
        activeSightings.set(icao24, { id: res.rows[0].id, lastSeen: now });
        inserted++;
      }
    }

    if (inserted > 0 || updated > 0) {
      console.log(`[Aircraft DB] +${inserted} new sightings, ${updated} updated (${activeSightings.size} active)`);
    }
  } catch (err) {
    console.error('[Aircraft DB]', err.message);
  }
}

// Purge stale entries from active map every 10 min
setInterval(() => {
  const cutoff = Date.now() - SIGHTING_GAP_MS;
  let purged = 0;
  for (const [icao, entry] of activeSightings) {
    if (entry.lastSeen < cutoff) {
      activeSightings.delete(icao);
      purged++;
    }
  }
  if (purged > 0) console.log(`[Aircraft DB] Purged ${purged} stale sightings (${activeSightings.size} active)`);
}, 10 * 60 * 1000);

// ── OpenSky Aircraft (15s TTL, bbox passthrough) ─────────────────────────

app.get('/aircraft', async (req, res) => {
  const { bbox, icao24 } = req.query;
  if (!bbox && !icao24) return res.status(400).json({ error: 'Must specify bbox (south,west,north,east) or icao24' });

  const cacheKey = icao24 ? `aircraft:icao:${icao24}` : `aircraft:${bbox}`;
  const ttlMs = 15 * 1000;
  const cached = cacheGet(cacheKey, ttlMs);
  if (cached) {
    stats.hits++;
    log('/aircraft', true);
    trackAircraftSightings(cached.data);  // track on cache hits too
    res.set('Content-Type', cached.headers['content-type'] || 'application/json');
    return res.send(cached.data);
  }

  stats.misses++;
  try {
    let upstreamUrl;
    if (icao24) {
      upstreamUrl = `https://opensky-network.org/api/states/all?icao24=${icao24.toLowerCase()}`;
    } else {
      const parts = bbox.split(',');
      upstreamUrl = `https://opensky-network.org/api/states/all?lamin=${parts[0]}&lomin=${parts[1]}&lamax=${parts[2]}&lomax=${parts[3]}`;
    }
    const token = await getOpenskyToken();
    const fetchOpts = token ? { headers: { Authorization: `Bearer ${token}` } } : {};
    const t0 = Date.now();
    const upstream = await fetch(upstreamUrl, fetchOpts);
    const elapsed = Date.now() - t0;
    const body = await upstream.text();
    const contentType = upstream.headers.get('content-type') || 'application/json';

    log('/aircraft', false, elapsed);

    if (upstream.ok) {
      cacheSet(cacheKey, body, { 'content-type': contentType });
      trackAircraftSightings(body);  // fire-and-forget DB write
    }

    res.status(upstream.status).set('Content-Type', contentType).send(body);
  } catch (err) {
    console.error('[Aircraft upstream error]', err.message);
    res.status(502).json({ error: 'Upstream request failed', detail: err.message });
  }
});

// ── Radius hint endpoints ───────────────────────────────────────────────

app.get('/radius-hint', (req, res) => {
  const { lat, lon } = req.query;
  if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });
  const radius = getRadiusHint(lat, lon);
  res.json({ radius });
});

app.post('/radius-hint', (req, res) => {
  const { lat, lon, resultCount, error } = req.body;
  if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });
  const isError = !!error;
  const count = typeof resultCount === 'number' ? resultCount : 0;
  const radius = adjustRadiusHint(lat, lon, count, isError);
  res.json({ radius });
});

app.get('/radius-hints', (req, res) => {
  const hints = {};
  for (const [key, value] of radiusHints) {
    hints[key] = value;
  }
  res.json({ count: radiusHints.size, hints });
});

// ── POI cache endpoints ──────────────────────────────────────────────────────

app.get('/pois/stats', (req, res) => {
  let diskSize = 0;
  try {
    if (fs.existsSync(POI_CACHE_FILE)) {
      diskSize = fs.statSync(POI_CACHE_FILE).size;
    }
  } catch (_) {}
  res.json({
    count: poiCache.size,
    diskSizeMB: (diskSize / 1024 / 1024).toFixed(2),
  });
});

app.get('/pois/export', (req, res) => {
  const pois = [];
  for (const [key, value] of poiCache) {
    pois.push({ key, ...value });
  }
  res.json({ count: pois.length, pois });
});

app.get('/pois/bbox', (req, res) => {
  const { s, w, n, e } = req.query;
  if (!s || !w || !n || !e) return res.status(400).json({ error: 'Must specify s, w, n, e bounds' });
  const south = parseFloat(s), west = parseFloat(w), north = parseFloat(n), east = parseFloat(e);
  const elements = [];
  for (const [, value] of poiCache) {
    const el = value.element;
    const lat = el.lat ?? el.center?.lat;
    const lon = el.lon ?? el.center?.lon;
    if (lat == null || lon == null) continue;
    if (lat >= south && lat <= north && lon >= west && lon <= east) {
      elements.push(el);
    }
  }
  res.json({ count: elements.length, elements });
});

app.get('/poi/:type/:id', (req, res) => {
  const key = `poi:${req.params.type}:${req.params.id}`;
  const entry = poiCache.get(key);
  if (!entry) return res.status(404).json({ error: 'POI not found', key });
  res.json(entry);
});

// ── PostgreSQL-backed /db/* endpoints ────────────────────────────────────────

const HAVERSINE_SQL = `
  6371000 * 2 * ASIN(SQRT(
    POWER(SIN(RADIANS(lat - $LAT) / 2), 2) +
    COS(RADIANS($LAT)) * COS(RADIANS(lat)) *
    POWER(SIN(RADIANS(lon - $LON) / 2), 2)
  ))`;

function haversine(latParam, lonParam) {
  return HAVERSINE_SQL.replace(/\$LAT/g, latParam).replace(/\$LON/g, lonParam);
}

function toOverpassElement(row) {
  const el = { type: row.osm_type, id: row.osm_id, lat: row.lat, lon: row.lon, tags: row.tags || {} };
  if (row.distance_m != null) el.distance_m = Math.round(row.distance_m);
  return el;
}

// GET /db/pois/search — combined filtered search
app.get('/db/pois/search', requirePg, async (req, res) => {
  try {
    const { q, category, category_like, s, w, n, e, lat, lon, radius, tag, tag_value, limit, offset } = req.query;
    const conditions = [];
    const params = [];
    let pi = 1;

    if (q) {
      conditions.push(`name ILIKE $${pi}`);
      params.push(`%${q}%`);
      pi++;
    }
    if (category) {
      conditions.push(`category = $${pi}`);
      params.push(category);
      pi++;
    }
    if (category_like) {
      conditions.push(`category ILIKE $${pi}`);
      params.push(`%${category_like}%`);
      pi++;
    }
    if (s && w && n && e) {
      conditions.push(`lat BETWEEN $${pi} AND $${pi + 1}`);
      params.push(parseFloat(s), parseFloat(n));
      pi += 2;
      conditions.push(`lon BETWEEN $${pi} AND $${pi + 1}`);
      params.push(parseFloat(w), parseFloat(e));
      pi += 2;
    }
    if (tag) {
      if (tag_value) {
        conditions.push(`tags->>$${pi} = $${pi + 1}`);
        params.push(tag, tag_value);
        pi += 2;
      } else {
        conditions.push(`tags ? $${pi}`);
        params.push(tag);
        pi++;
      }
    }

    const hasLatLon = lat && lon;
    let distExpr = '';
    let orderClause = 'ORDER BY name NULLS LAST';

    if (hasLatLon) {
      const latP = `$${pi}`, lonP = `$${pi + 1}`;
      params.push(parseFloat(lat), parseFloat(lon));
      distExpr = `, ${haversine(latP, lonP)} AS distance_m`;
      orderClause = 'ORDER BY distance_m';
      pi += 2;

      if (radius) {
        conditions.push(`${haversine(latP, lonP)} <= $${pi}`);
        params.push(parseFloat(radius));
        pi++;
      }
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';
    const lim = Math.min(parseInt(limit) || 100, 500);
    const off = parseInt(offset) || 0;

    const sql = `SELECT osm_type, osm_id, lat, lon, tags${distExpr} FROM pois ${where} ${orderClause} LIMIT ${lim} OFFSET ${off}`;
    const result = await pgPool.query(sql, params);

    res.json({ count: result.rows.length, elements: result.rows.map(toOverpassElement) });
  } catch (err) {
    console.error('[/db/pois/search]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// GET /db/pois/nearby — nearby POIs sorted by distance
app.get('/db/pois/nearby', requirePg, async (req, res) => {
  try {
    const { lat, lon, radius, category, limit } = req.query;
    if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });

    const fLat = parseFloat(lat), fLon = parseFloat(lon);
    const fRadius = parseFloat(radius) || 1000;
    const lim = Math.min(parseInt(limit) || 50, 200);

    // Pre-filter with bbox approximation (~0.009° per km at mid-latitudes)
    const degPerKm = 0.009;
    const kmRadius = fRadius / 1000;
    const latDelta = kmRadius * degPerKm;
    const lonDelta = kmRadius * degPerKm / Math.cos(fLat * Math.PI / 180);

    const conditions = [
      `lat BETWEEN $1 AND $2`,
      `lon BETWEEN $3 AND $4`,
    ];
    const params = [fLat - latDelta, fLat + latDelta, fLon - lonDelta, fLon + lonDelta];
    let pi = 5;

    if (category) {
      conditions.push(`category = $${pi}`);
      params.push(category);
      pi++;
    }

    params.push(fLat, fLon, fRadius);
    const distCol = haversine(`$${pi}`, `$${pi + 1}`);
    const radiusP = `$${pi + 2}`;

    const sql = `SELECT osm_type, osm_id, lat, lon, tags, ${distCol} AS distance_m
      FROM pois WHERE ${conditions.join(' AND ')} AND ${distCol} <= ${radiusP}
      ORDER BY distance_m LIMIT ${lim}`;

    const result = await pgPool.query(sql, params);
    res.json({ count: result.rows.length, elements: result.rows.map(toOverpassElement) });
  } catch (err) {
    console.error('[/db/pois/nearby]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// GET /db/poi/:type/:id — single POI lookup
app.get('/db/poi/:type/:id', requirePg, async (req, res) => {
  try {
    const result = await pgPool.query(
      'SELECT osm_type, osm_id, lat, lon, name, category, tags, first_seen, last_seen FROM pois WHERE osm_type = $1 AND osm_id = $2',
      [req.params.type, req.params.id]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'POI not found' });
    const row = result.rows[0];
    res.json({
      type: row.osm_type, id: row.osm_id, lat: row.lat, lon: row.lon,
      name: row.name, category: row.category, tags: row.tags,
      first_seen: row.first_seen, last_seen: row.last_seen,
    });
  } catch (err) {
    console.error('[/db/poi/:type/:id]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// GET /db/pois/stats — analytics overview
app.get('/db/pois/stats', requirePg, async (req, res) => {
  try {
    const [total, named, topCategories, bounds, timeRange] = await Promise.all([
      pgPool.query('SELECT COUNT(*) AS n FROM pois'),
      pgPool.query('SELECT COUNT(*) AS n FROM pois WHERE name IS NOT NULL'),
      pgPool.query('SELECT category, COUNT(*) AS n FROM pois WHERE category IS NOT NULL GROUP BY category ORDER BY n DESC LIMIT 20'),
      pgPool.query('SELECT MIN(lat) AS min_lat, MAX(lat) AS max_lat, MIN(lon) AS min_lon, MAX(lon) AS max_lon FROM pois'),
      pgPool.query('SELECT MIN(first_seen) AS earliest, MAX(last_seen) AS latest FROM pois'),
    ]);
    res.json({
      total: parseInt(total.rows[0].n),
      named: parseInt(named.rows[0].n),
      topCategories: topCategories.rows.map(r => ({ category: r.category, count: parseInt(r.n) })),
      bounds: bounds.rows[0],
      timeRange: timeRange.rows[0],
    });
  } catch (err) {
    console.error('[/db/pois/stats]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// GET /db/pois/categories — category breakdown
app.get('/db/pois/categories', requirePg, async (req, res) => {
  try {
    const result = await pgPool.query(
      'SELECT category, COUNT(*) AS n FROM pois WHERE category IS NOT NULL GROUP BY category ORDER BY n DESC'
    );
    const categories = result.rows.map(r => {
      const parts = r.category.split('=');
      return { category: r.category, key: parts[0], value: parts[1] || null, count: parseInt(r.n) };
    });
    res.json({ count: categories.length, categories });
  } catch (err) {
    console.error('[/db/pois/categories]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// GET /db/pois/coverage — geographic grid coverage
app.get('/db/pois/coverage', requirePg, async (req, res) => {
  try {
    const resolution = Math.min(Math.max(parseInt(req.query.resolution) || 2, 1), 4);
    const result = await pgPool.query(
      `SELECT ROUND(lat::numeric, $1) AS grid_lat, ROUND(lon::numeric, $1) AS grid_lon, COUNT(*) AS n
       FROM pois GROUP BY grid_lat, grid_lon ORDER BY n DESC`,
      [resolution]
    );
    const cells = result.rows.map(r => ({
      lat: parseFloat(r.grid_lat), lon: parseFloat(r.grid_lon), count: parseInt(r.n),
    }));
    res.json({ resolution, cells: cells.length, grid: cells });
  } catch (err) {
    console.error('[/db/pois/coverage]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// ── Windy Webcams (10 min TTL, bbox passthrough) ─────────────────────────────

const WINDY_API_KEY = 'VyQEq6OkmnafZMqEqbVrbH4MXQmS1xfw';

app.get('/webcams', async (req, res) => {
  const { s, w, n, e, categories } = req.query;
  if (!s || !w || !n || !e) return res.status(400).json({ error: 'Must specify s, w, n, e bounds' });

  const catParam = categories || 'traffic';
  const cacheKey = `webcams:${s},${w},${n},${e}:${catParam}`;
  const ttlMs = 10 * 60 * 1000;  // 10 minutes — matches image URL expiry

  const cached = cacheGet(cacheKey, ttlMs);
  if (cached) {
    stats.hits++;
    log('/webcams', true);
    res.set('Content-Type', 'application/json');
    return res.send(cached.data);
  }

  stats.misses++;
  try {
    const upstreamUrl = `https://api.windy.com/webcams/api/v3/webcams?bbox=${n},${e},${s},${w}&category=${encodeURIComponent(catParam)}&limit=50&include=images,location,categories`;
    const t0 = Date.now();
    const upstream = await fetch(upstreamUrl, {
      headers: { 'x-windy-api-key': WINDY_API_KEY },
    });
    const elapsed = Date.now() - t0;

    if (!upstream.ok) {
      const errBody = await upstream.text();
      console.error(`[Webcams] Upstream HTTP ${upstream.status}: ${errBody.substring(0, 300)}`);
      return res.status(upstream.status).json({ error: 'Webcams upstream error', status: upstream.status });
    }

    const raw = await upstream.json();
    const webcams = (raw.webcams || []).map(wc => ({
      id: wc.webcamId || wc.id,
      title: wc.title || '',
      lat: wc.location?.latitude ?? wc.position?.latitude ?? 0,
      lon: wc.location?.longitude ?? wc.position?.longitude ?? 0,
      categories: (wc.categories || []).map(c => c.id || c),
      previewUrl: wc.images?.current?.preview || wc.images?.daylight?.preview || '',
      thumbnailUrl: wc.images?.current?.thumbnail || wc.images?.daylight?.thumbnail || '',
      status: wc.status || 'active',
      lastUpdated: wc.lastUpdatedOn || null,
    }));

    const body = JSON.stringify(webcams);
    log('/webcams', false, elapsed);
    cacheSet(cacheKey, body, { 'content-type': 'application/json' });
    res.set('Content-Type', 'application/json').send(body);
  } catch (err) {
    console.error('[Webcams upstream error]', err.message);
    res.status(502).json({ error: 'Upstream request failed', detail: err.message });
  }
});

// ── Admin endpoints ─────────────────────────────────────────────────────────

app.get('/cache/stats', (req, res) => {
  const memUsage = process.memoryUsage();
  res.json({
    entries: cache.size,
    radiusHints: radiusHints.size,
    pois: poiCache.size,
    hits: stats.hits,
    misses: stats.misses,
    hitRate: stats.hits + stats.misses > 0
      ? ((stats.hits / (stats.hits + stats.misses)) * 100).toFixed(1) + '%'
      : 'N/A',
    memoryMB: (memUsage.heapUsed / 1024 / 1024).toFixed(1),
    keys: [...cache.keys()],
  });
});

app.post('/cache/clear', (req, res) => {
  const count = cache.size;
  const hintCount = radiusHints.size;
  const poiCount = poiCache.size;
  cache.clear();
  radiusHints.clear();
  poiCache.clear();
  stats = { hits: 0, misses: 0 };
  try { fs.unlinkSync(CACHE_FILE); } catch (_) {}
  try { fs.unlinkSync(RADIUS_HINTS_FILE); } catch (_) {}
  try { fs.unlinkSync(POI_CACHE_FILE); } catch (_) {}
  console.log(`[${new Date().toISOString()}] Cache cleared (${count} entries + ${hintCount} hints + ${poiCount} POIs, disk files removed)`);
  res.json({ cleared: count, hintsCleared: hintCount, poisCleared: poiCount });
});

// ── Start ───────────────────────────────────────────────────────────────────

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Cache proxy listening on http://0.0.0.0:${PORT}`);
  console.log('Routes: POST /overpass, GET /earthquakes, GET /nws-alerts, GET /metar, GET /aircraft, GET /webcams');
  console.log('Radius: GET /radius-hint, POST /radius-hint, GET /radius-hints');
  console.log('POIs:   GET /pois/stats, GET /pois/export, GET /pois/bbox, GET /poi/:type/:id');
  console.log('DB:     GET /db/pois/search, /db/pois/nearby, /db/poi/:type/:id, /db/pois/stats, /db/pois/categories, /db/pois/coverage');
  console.log(`        PostgreSQL: ${pgPool ? 'connected' : 'not configured (set DATABASE_URL)'}`);
  console.log(`        OpenSky:    ${openskyConfigured ? 'OAuth2 configured (4000 req/day)' : 'anonymous (100 req/day — set OPENSKY_CLIENT_ID + OPENSKY_CLIENT_SECRET)'}`);
  console.log('Admin:  GET /cache/stats, POST /cache/clear');
});
