const express = require('express');
const fs = require('fs');
const path = require('path');
const app = express();
const PORT = 3000;

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
  return hint ? hint.radius : DEFAULT_RADIUS;
}

function adjustRadiusHint(lat, lon, resultCount, error) {
  const key = gridKey(lat, lon);
  const current = radiusHints.get(key);
  let radius = current ? current.radius : DEFAULT_RADIUS;

  if (error) {
    // Error (429/504/502) → shrink 30%
    radius = Math.round(radius * RADIUS_SHRINK);
    console.log(`[Radius] ${key} error → shrink to ${radius}m`);
  } else if (resultCount < MIN_USEFUL_POI) {
    // Too few results → grow 30%
    radius = Math.round(radius * RADIUS_GROW);
    console.log(`[Radius] ${key} only ${resultCount} POIs → grow to ${radius}m`);
  } else {
    // Healthy result count → confirm (refresh timestamp, no radius change)
    console.log(`[Radius] ${key} ${resultCount} POIs — confirmed at ${radius}m`);
  }

  radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
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

loadCache();
loadRadiusHints();

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

  // Cache-only mode: don't hit upstream, just return 204 if not cached
  if (cacheOnly) {
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

// ── METAR (1h TTL) ──────────────────────────────────────────────────────────

proxyGet(
  '/metar',
  'https://aviationweather.gov/api/data/metar?format=json&hours=1&taf=false',
  'metar',
  60 * 60 * 1000
);

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

// ── Admin endpoints ─────────────────────────────────────────────────────────

app.get('/cache/stats', (req, res) => {
  const memUsage = process.memoryUsage();
  res.json({
    entries: cache.size,
    radiusHints: radiusHints.size,
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
  cache.clear();
  radiusHints.clear();
  stats = { hits: 0, misses: 0 };
  try { fs.unlinkSync(CACHE_FILE); } catch (_) {}
  try { fs.unlinkSync(RADIUS_HINTS_FILE); } catch (_) {}
  console.log(`[${new Date().toISOString()}] Cache cleared (${count} entries + ${hintCount} hints, disk files removed)`);
  res.json({ cleared: count, hintsCleared: hintCount });
});

// ── Start ───────────────────────────────────────────────────────────────────

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Cache proxy listening on http://0.0.0.0:${PORT}`);
  console.log('Routes: POST /overpass, GET /earthquakes, GET /nws-alerts, GET /metar');
  console.log('Radius: GET /radius-hint, POST /radius-hint, GET /radius-hints');
  console.log('Admin:  GET /cache/stats, POST /cache/clear');
});
