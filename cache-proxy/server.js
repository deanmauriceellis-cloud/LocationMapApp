const express = require('express');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { Pool } = require('pg');
const DDG = require('duck-duck-scrape');
const cheerio = require('cheerio');
const { XMLParser } = require('fast-xml-parser');
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

// ── OpenSky rate limiter — stay within API daily quota ────────────────────────

const OPENSKY_DAILY_LIMIT = openskyConfigured ? 4000 : 100;
const OPENSKY_SAFETY_MARGIN = 0.9;  // use 90% of quota to leave headroom
const OPENSKY_EFFECTIVE_LIMIT = Math.floor(OPENSKY_DAILY_LIMIT * OPENSKY_SAFETY_MARGIN);
const OPENSKY_MIN_INTERVAL_MS = Math.ceil((24 * 60 * 60 * 1000) / OPENSKY_EFFECTIVE_LIMIT);  // ms between requests

const openskyRateState = {
  requests: [],         // timestamps of upstream requests (rolling 24h window)
  backoffUntil: 0,      // timestamp: don't hit upstream before this time
  backoffSeconds: 10,   // current backoff delay (exponential)
  consecutive429s: 0,   // track consecutive 429s for backoff scaling
};

function openskyCanRequest() {
  const now = Date.now();
  // Prune requests older than 24h
  const cutoff = now - 24 * 60 * 60 * 1000;
  openskyRateState.requests = openskyRateState.requests.filter(t => t > cutoff);

  // Check backoff
  if (now < openskyRateState.backoffUntil) {
    const waitSec = Math.ceil((openskyRateState.backoffUntil - now) / 1000);
    return { allowed: false, reason: `backoff (${waitSec}s remaining)`, retryAfter: waitSec };
  }

  // Check daily quota
  if (openskyRateState.requests.length >= OPENSKY_EFFECTIVE_LIMIT) {
    const oldestExpires = openskyRateState.requests[0] + 24 * 60 * 60 * 1000;
    const waitSec = Math.ceil((oldestExpires - now) / 1000);
    return { allowed: false, reason: `daily quota (${openskyRateState.requests.length}/${OPENSKY_EFFECTIVE_LIMIT})`, retryAfter: waitSec };
  }

  // Check minimum interval between requests
  const lastReq = openskyRateState.requests[openskyRateState.requests.length - 1] || 0;
  if (now - lastReq < OPENSKY_MIN_INTERVAL_MS) {
    const waitMs = OPENSKY_MIN_INTERVAL_MS - (now - lastReq);
    return { allowed: false, reason: `min interval (${Math.ceil(waitMs / 1000)}s)`, retryAfter: Math.ceil(waitMs / 1000) };
  }

  return { allowed: true };
}

function openskyRecordRequest() {
  openskyRateState.requests.push(Date.now());
}

function openskyRecord429() {
  openskyRateState.consecutive429s++;
  // Exponential backoff: 10s, 20s, 40s, 80s, 160s, capped at 300s (5 min)
  openskyRateState.backoffSeconds = Math.min(300, 10 * Math.pow(2, openskyRateState.consecutive429s - 1));
  openskyRateState.backoffUntil = Date.now() + openskyRateState.backoffSeconds * 1000;
  console.log(`[OpenSky] 429 rate limited — backoff ${openskyRateState.backoffSeconds}s (consecutive: ${openskyRateState.consecutive429s})`);
}

function openskyRecordSuccess() {
  if (openskyRateState.consecutive429s > 0) {
    console.log(`[OpenSky] Recovered from rate limit (was ${openskyRateState.consecutive429s} consecutive 429s)`);
  }
  openskyRateState.consecutive429s = 0;
  openskyRateState.backoffSeconds = 10;
}

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
const MIN_RADIUS = 100;
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

  // Fuzzy: find nearest hint within 20km (~0.1798°)
  const latF = parseFloat(lat);
  const lonF = parseFloat(lon);
  const TWENTY_KM_DEG = 0.1798;
  let nearest = null;
  let nearestDist = Infinity;

  for (const [k, v] of radiusHints) {
    const parts = k.split(':');
    const hLat = parseFloat(parts[0]);
    const hLon = parseFloat(parts[1]);
    const dLat = hLat - latF;
    const dLon = (hLon - lonF) * Math.cos(latF * Math.PI / 180);
    const dist = Math.sqrt(dLat * dLat + dLon * dLon);
    if (dist <= TWENTY_KM_DEG && dist < nearestDist) {
      nearest = v;
      nearestDist = dist;
    }
  }

  if (nearest) {
    const km = (nearestDist / 0.008993).toFixed(1);
    console.log(`[Radius] Fuzzy hit for ${key} — nearest hint ${km}km away → ${nearest.radius}m`);
    return nearest.radius;
  }

  return DEFAULT_RADIUS;
}

function adjustRadiusHint(lat, lon, resultCount, error, capped) {
  const key = gridKey(lat, lon);
  const current = radiusHints.get(key);
  let radius = current ? current.radius : DEFAULT_RADIUS;

  if (error) {
    // Errors (504, 429) are transient — don't change the radius hint
    console.log(`[Radius] ${key} error — keeping at ${radius}m (transient)`);
    return radius;
  }

  if (capped) {
    radius = Math.round(radius * 0.5);
    console.log(`[Radius] ${key} CAPPED (${resultCount} POIs) → halve to ${radius}m`);
  } else if (resultCount < MIN_USEFUL_POI) {
    radius = Math.round(radius * RADIUS_GROW);
    console.log(`[Radius] ${key} only ${resultCount} POIs → grow to ${radius}m`);
  } else {
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
    if (!parsed.elements || !Array.isArray(parsed.elements)) return { added: 0, updated: 0 };

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
    return { added, updated };
  } catch (err) {
    console.error('[POI Cache] Failed to parse response:', err.message);
    return { added: 0, updated: 0 };
  }
}

// ── Load all caches from disk ────────────────────────────────────────────────

loadCache();
loadRadiusHints();
loadPoiCache();

// ── Bbox snapping (improve cache hit rate for scroll-heavy layers) ───────────

/**
 * Round bbox coordinates to a grid so small scrolls reuse the same cache key.
 * precision=2 → snap to 0.01° (~1.1 km), precision=1 → snap to 0.1° (~11 km).
 * South/west snap down, north/east snap up to ensure the snapped bbox fully
 * contains the original viewport.
 */
function snapBbox(s, w, n, e, precision = 2) {
  const f = Math.pow(10, precision);
  return {
    s: Math.floor(s * f) / f,
    w: Math.floor(w * f) / f,
    n: Math.ceil(n * f) / f,
    e: Math.ceil(e * f) / f,
  };
}

// ── Logging ─────────────────────────────────────────────────────────────────

function log(endpoint, hit, upstreamMs, extra) {
  const ts = new Date().toISOString();
  const status = hit ? 'HIT' : 'MISS';
  const timing = hit ? '' : ` upstream=${upstreamMs}ms`;
  const suffix = extra ? ` [${extra}]` : '';
  console.log(`[${ts}] ${endpoint} ${status}${timing} (${cache.size} cached)${suffix}`);
}

// ── Overpass POI proxy ──────────────────────────────────────────────────────

const OVERPASS_TTL = 365 * 24 * 60 * 60 * 1000;  // 365 days
const OVERPASS_MIN_INTERVAL_MS = 10_000;  // 10s between upstream requests

// Upstream request queue — serializes cache misses, 10s apart
const overpassQueue = [];
let overpassWorkerRunning = false;
let overpassLastUpstream = 0;

async function overpassWorker() {
  if (overpassWorkerRunning) return;
  overpassWorkerRunning = true;

  while (overpassQueue.length > 0) {
    const item = shiftFairQueue();
    if (!item) break;
    const { dataField, cacheKey, resolve, clientId } = item;
    console.log(`[Overpass queue] processing client=${clientId || 'unknown'} (${overpassQueue.length} remaining)`);

    // Wait for minimum interval since last upstream request
    const elapsed = Date.now() - overpassLastUpstream;
    if (elapsed < OVERPASS_MIN_INTERVAL_MS) {
      const wait = OVERPASS_MIN_INTERVAL_MS - elapsed;
      console.log(`[Overpass queue] throttle — waiting ${(wait / 1000).toFixed(1)}s`);
      await new Promise(r => setTimeout(r, wait));
    }

    // Check cache again — a previous queued request for the same key may have populated it
    if (cacheKey) {
      const cached = cacheGet(cacheKey, OVERPASS_TTL);
      if (cached) {
        console.log(`[Overpass queue] cache hit while queued — ${cacheKey}`);
        resolve({ hit: true, data: cached.data, contentType: cached.headers['content-type'] || 'application/json' });
        continue;
      }

      // Check if a larger-radius cached result covers this area
      const aroundMatch = dataField.match(/around:(\d+),([-\d.]+),([-\d.]+)/);
      if (aroundMatch) {
        const lat = parseFloat(aroundMatch[2]).toFixed(3);
        const lon = parseFloat(aroundMatch[3]).toFixed(3);
        const radius = parseInt(aroundMatch[1]);
        const tagMatches = [...dataField.matchAll(/\["([^"]+)"(?:="([^"]+)")?\]/g)];
        const tags = [...new Set(tagMatches.map(m => m[2] ? `${m[1]}=${m[2]}` : m[1]))].sort();
        const covering = findCoveringCache(lat, lon, radius, tags);
        if (covering) {
          console.log(`[Overpass queue] covered by larger cache — ${covering.key}`);
          resolve({ hit: true, data: covering.cached.data, contentType: covering.cached.headers['content-type'] || 'application/json' });
          continue;
        }
      }
    }

    try {
      const t0 = Date.now();
      overpassLastUpstream = t0;
      const upstream = await fetch('https://overpass-api.de/api/interpreter', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `data=${encodeURIComponent(dataField)}`,
      });
      const elapsedMs = Date.now() - t0;
      const body = await upstream.text();
      const contentType = upstream.headers.get('content-type') || 'application/json';

      log('/overpass', false, elapsedMs);

      let poiStats = { added: 0, updated: 0 };
      if (upstream.ok && cacheKey) {
        cacheSet(cacheKey, body, { 'content-type': contentType });

        // Content hash delta detection — skip POI cache update if data unchanged
        const newHash = computeElementHash(body);
        const oldHash = contentHashes.get(cacheKey);
        if (newHash && oldHash && newHash === oldHash) {
          console.log(`[POI Cache] content unchanged (hash=${newHash.slice(0, 8)}) — skipping update`);
          poiStats = { added: 0, updated: 0 };
        } else {
          poiStats = cacheIndividualPois(body);
          if (newHash) contentHashes.set(cacheKey, newHash);
        }
      }

      resolve({ hit: false, status: upstream.status, data: body, contentType, poiNew: poiStats.added, poiKnown: poiStats.updated });
    } catch (err) {
      console.error('[Overpass upstream error]', err.message);
      resolve({ hit: false, error: true, message: err.message });
    }
  }

  overpassWorkerRunning = false;
}

function parseOverpassCacheKey(dataField) {
  // Extract around:RADIUS,LAT,LON from the Overpass QL query
  const aroundMatch = dataField.match(/around:(\d+),([-\d.]+),([-\d.]+)/);
  if (!aroundMatch) return null;

  const radius = parseInt(aroundMatch[1]);
  const lat = parseFloat(aroundMatch[2]).toFixed(3);
  const lon = parseFloat(aroundMatch[3]).toFixed(3);

  // Extract all tag filters like ["amenity"="restaurant"] or ["shop"]
  const tagMatches = [...dataField.matchAll(/\["([^"]+)"(?:="([^"]+)")?\]/g)];
  const tags = [...new Set(tagMatches.map(m => m[2] ? `${m[1]}=${m[2]}` : m[1]))].sort();

  // Include radius in key so different-radius queries for the same point don't collide
  return `overpass:${lat}:${lon}:r${radius}:${tags.join(',')}`;
}

/**
 * Check if a larger-radius cached result already covers the requested area.
 * Same grid point at radius >= requested radius is a superset.
 */
function findCoveringCache(lat, lon, radius, tags) {
  const tagStr = tags.join(',');
  const checkRadii = [radius * 2, radius * 4];
  for (const r of checkRadii) {
    if (r > 5000) continue; // don't check unreasonably large
    const key = `overpass:${lat}:${lon}:r${r}:${tagStr}`;
    const cached = cacheGet(key, OVERPASS_TTL);
    if (cached) return { key, cached };
  }
  return null;
}

/**
 * Compute a fast content hash of Overpass elements for delta detection.
 * Hash is based on sorted element type+id pairs — stable across re-fetches of identical data.
 */
function computeElementHash(jsonBody) {
  try {
    const parsed = JSON.parse(jsonBody);
    if (!parsed.elements || !Array.isArray(parsed.elements)) return null;
    const ids = parsed.elements
      .filter(e => e.type && e.id)
      .map(e => `${e.type}:${e.id}`)
      .sort();
    return crypto.createHash('md5').update(ids.join(',')).digest('hex');
  } catch (_) {
    return null;
  }
}

// Content hash store — maps cache key to last known element hash
const contentHashes = new Map();

// ── Per-client fair queuing ──────────────────────────────────────────────────

const CLIENT_QUEUE_CAP = 5;  // max queued requests per client

/**
 * Enqueue an Overpass request with per-client fair ordering.
 * Round-robin: worker picks from the client with fewest processed items.
 */
function enqueueOverpassRequest(clientId, item) {
  // Count existing queued items for this client
  const clientCount = overpassQueue.filter(q => q.clientId === clientId).length;
  if (clientCount >= CLIENT_QUEUE_CAP) {
    console.log(`[Overpass queue] client ${clientId} at cap (${CLIENT_QUEUE_CAP}) — rejecting`);
    return false;
  }
  item.clientId = clientId;
  overpassQueue.push(item);
  return true;
}

/**
 * Shift the next item from the queue using round-robin across clients.
 * Picks from the client whose item appears earliest but hasn't been overserved.
 */
function shiftFairQueue() {
  if (overpassQueue.length === 0) return null;

  // Group by clientId and find the client with fewest items already in front
  const clientOrder = [];
  const seen = new Set();
  for (const item of overpassQueue) {
    const cid = item.clientId || 'unknown';
    if (!seen.has(cid)) {
      seen.add(cid);
      clientOrder.push(cid);
    }
  }

  // Round-robin: pick the first item from the first client in order
  // This naturally interleaves — ABAB instead of AABB
  for (const cid of clientOrder) {
    const idx = overpassQueue.findIndex(q => (q.clientId || 'unknown') === cid);
    if (idx !== -1) {
      return overpassQueue.splice(idx, 1)[0];
    }
  }
  return overpassQueue.shift(); // fallback
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
      res.set('X-Cache', 'HIT');
      return res.send(cached.data);
    }

    // Check if a larger-radius cached result covers this area
    const aroundMatch = dataField.match(/around:(\d+),([-\d.]+),([-\d.]+)/);
    if (aroundMatch) {
      const lat = parseFloat(aroundMatch[2]).toFixed(3);
      const lon = parseFloat(aroundMatch[3]).toFixed(3);
      const radius = parseInt(aroundMatch[1]);
      const tagMatches = [...dataField.matchAll(/\["([^"]+)"(?:="([^"]+)")?\]/g)];
      const tags = [...new Set(tagMatches.map(m => m[2] ? `${m[1]}=${m[2]}` : m[1]))].sort();
      const covering = findCoveringCache(lat, lon, radius, tags);
      if (covering) {
        stats.hits++;
        log('/overpass (covering-cache)', true, 0, covering.key);
        res.set('Content-Type', covering.cached.headers['content-type'] || 'application/json');
        res.set('X-Cache', 'HIT');
        return res.send(covering.cached.data);
      }
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
        res.set('X-Cache', 'HIT');
        return res.json({ elements: [...merged.values()] });
      }
    }
    log('/overpass (cache-only)', false, 0);
    return res.status(204).end();
  }

  stats.misses++;
  const clientId = req.headers['x-client-id'] || 'unknown';
  // Queue the upstream request — worker processes one at a time, 10s apart
  const queuePos = overpassQueue.length + 1;
  if (queuePos > 0) {
    console.log(`[Overpass queue] enqueued client=${clientId} (position ${queuePos}, ~${queuePos * 10}s wait)`);
  }
  const result = await new Promise(resolve => {
    const accepted = enqueueOverpassRequest(clientId, { dataField, cacheKey, resolve });
    if (!accepted) {
      // Client at queue cap — return 429
      resolve({ hit: false, error: true, rateLimited: true, message: 'Per-client queue cap reached' });
      return;
    }
    overpassWorker();
  });

  if (result.rateLimited) {
    res.set('Retry-After', '30');
    return res.status(429).json({ error: 'Too many queued requests', detail: result.message });
  }

  if (result.error) {
    return res.status(502).json({ error: 'Upstream request failed', detail: result.message });
  }
  res.status(result.status || 200)
    .set('Content-Type', result.contentType)
    .set('X-Cache', result.hit ? 'HIT' : 'MISS')
    .set('X-POI-New', String(result.poiNew || 0))
    .set('X-POI-Known', String(result.poiKnown || 0))
    .send(result.data);
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

  // Snap bbox to 0.01° grid for cache reuse across small scrolls
  const parts = bbox.split(',').map(Number);
  const snapped = snapBbox(parts[0], parts[1], parts[2], parts[3], 2);
  const snappedBbox = `${snapped.s},${snapped.w},${snapped.n},${snapped.e}`;

  const cacheKey = `metar:${snappedBbox}`;
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
    const upstreamUrl = `https://aviationweather.gov/api/data/metar?format=json&hours=1&taf=false&bbox=${encodeURIComponent(snappedBbox)}`;
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

  // Snap bbox to 0.1° grid for cache reuse (aircraft have 15s TTL, coarse snap is fine)
  let snappedBbox = bbox;
  if (bbox && !icao24) {
    const parts = bbox.split(',').map(Number);
    const snapped = snapBbox(parts[0], parts[1], parts[2], parts[3], 1);
    snappedBbox = `${snapped.s},${snapped.w},${snapped.n},${snapped.e}`;
  }

  const cacheKey = icao24 ? `aircraft:icao:${icao24}` : `aircraft:${snappedBbox}`;
  const ttlMs = 15 * 1000;
  const cached = cacheGet(cacheKey, ttlMs);
  if (cached) {
    stats.hits++;
    log('/aircraft', true);
    trackAircraftSightings(cached.data);  // track on cache hits too
    res.set('Content-Type', cached.headers['content-type'] || 'application/json');
    return res.send(cached.data);
  }

  // Rate limit check — serve stale cache or 429 if we can't hit upstream
  const rateCheck = openskyCanRequest();
  if (!rateCheck.allowed) {
    // Try to serve stale cache (ignore TTL)
    const stale = cache.get(cacheKey);
    if (stale) {
      stats.hits++;
      log('/aircraft', true, 0, `stale (${rateCheck.reason})`);
      trackAircraftSightings(stale.data);
      res.set('Content-Type', stale.headers['content-type'] || 'application/json');
      res.set('X-Cache', 'STALE');
      res.set('X-Rate-Limit-Reason', rateCheck.reason);
      return res.send(stale.data);
    }
    log('/aircraft', false, 0, `throttled (${rateCheck.reason})`);
    return res.status(429)
      .set('Retry-After', String(rateCheck.retryAfter || 30))
      .json({ error: 'Rate limited', reason: rateCheck.reason, retryAfter: rateCheck.retryAfter });
  }

  stats.misses++;
  try {
    let upstreamUrl;
    if (icao24) {
      upstreamUrl = `https://opensky-network.org/api/states/all?icao24=${icao24.toLowerCase()}`;
    } else {
      const parts = snappedBbox.split(',');
      upstreamUrl = `https://opensky-network.org/api/states/all?lamin=${parts[0]}&lomin=${parts[1]}&lamax=${parts[2]}&lomax=${parts[3]}`;
    }
    const token = await getOpenskyToken();
    const fetchOpts = token ? { headers: { Authorization: `Bearer ${token}` } } : {};
    const t0 = Date.now();
    openskyRecordRequest();  // record BEFORE the request
    const upstream = await fetch(upstreamUrl, fetchOpts);
    const elapsed = Date.now() - t0;
    const body = await upstream.text();
    const contentType = upstream.headers.get('content-type') || 'application/json';

    if (upstream.status === 429) {
      openskyRecord429();
      // Try stale cache before returning 429
      const stale = cache.get(cacheKey);
      if (stale) {
        log('/aircraft', true, elapsed, 'stale (upstream 429)');
        trackAircraftSightings(stale.data);
        res.set('Content-Type', stale.headers['content-type'] || 'application/json');
        res.set('X-Cache', 'STALE');
        return res.send(stale.data);
      }
      log('/aircraft', false, elapsed, 'upstream 429');
      return res.status(429)
        .set('Retry-After', String(openskyRateState.backoffSeconds))
        .json({ error: 'Rate limited by OpenSky', retryAfter: openskyRateState.backoffSeconds });
    }

    openskyRecordSuccess();
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

    const sql = `SELECT osm_type, osm_id, name, category, lat, lon, tags${distExpr} FROM pois ${where} ${orderClause} LIMIT ${lim} OFFSET ${off}`;
    const result = await pgPool.query(sql, params);

    res.json({ count: result.rows.length, elements: result.rows.map(r => {
      const el = toOverpassElement(r);
      if (r.name) el.name = r.name;
      if (r.category) el.category = r.category;
      return el;
    }) });
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

// ── /db/pois/counts — category counts with 10-min server cache ───────────────

let poiCountsCache = null;
let poiCountsCachedAt = 0;
const POI_COUNTS_TTL_MS = 10 * 60 * 1000; // 10 minutes

app.get('/db/pois/counts', requirePg, async (req, res) => {
  try {
    const lat = parseFloat(req.query.lat);
    const lon = parseFloat(req.query.lon);
    const radiusM = parseInt(req.query.radius) || 10000;
    const hasLocation = !isNaN(lat) && !isNaN(lon);

    // Use cache only for global (non-location) queries
    if (!hasLocation) {
      const now = Date.now();
      if (poiCountsCache && (now - poiCountsCachedAt) < POI_COUNTS_TTL_MS) {
        return res.json(poiCountsCache);
      }
    }

    let result;
    if (hasLocation) {
      // Haversine distance filter — count only POIs within radius
      const radiusKm = radiusM / 1000;
      result = await pgPool.query(
        `SELECT category, COUNT(*)::int AS count FROM pois
         WHERE category IS NOT NULL
           AND (6371 * acos(LEAST(1.0, cos(radians($1)) * cos(radians(lat)) * cos(radians(lon) - radians($2)) + sin(radians($1)) * sin(radians(lat))))) <= $3
         GROUP BY category ORDER BY count DESC`,
        [lat, lon, radiusKm]
      );
    } else {
      result = await pgPool.query(
        `SELECT category, COUNT(*)::int AS count FROM pois WHERE category IS NOT NULL GROUP BY category ORDER BY count DESC`
      );
    }

    const counts = {};
    let total = 0;
    for (const row of result.rows) {
      counts[row.category] = row.count;
      total += row.count;
    }
    const response = { counts, total, cachedAt: new Date().toISOString() };
    if (hasLocation) response.radiusM = radiusM;

    // Cache only global queries
    if (!hasLocation) {
      poiCountsCache = response;
      poiCountsCachedAt = Date.now();
    }
    res.json(response);
  } catch (err) {
    console.error('[/db/pois/counts]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// ── /db/pois/find — distance-sorted POIs by category ─────────────────────────

app.get('/db/pois/find', requirePg, async (req, res) => {
  try {
    const { lat, lon, categories, limit: rawLimit, offset: rawOffset } = req.query;
    if (!lat || !lon) return res.status(400).json({ error: 'lat and lon required' });

    const userLat = parseFloat(lat);
    const userLon = parseFloat(lon);
    const maxResults = Math.min(parseInt(rawLimit) || 50, 200);
    const skip = parseInt(rawOffset) || 0;
    const catList = categories ? categories.split(',').map(c => c.trim()).filter(Boolean) : [];
    if (catList.length === 0) return res.status(400).json({ error: 'categories required (comma-separated)' });

    // Try 50km first, expand to 200km if too few results
    let scopeKm = 50;
    let rows, totalInRange;

    // Inline lat/lon in Haversine to avoid pg type inference issues
    const distExpr = haversine(String(userLat), String(userLon));

    for (const tryKm of [50, 200]) {
      scopeKm = tryKm;
      const degLat = tryKm / 111.0;
      const degLon = degLat / Math.cos(userLat * Math.PI / 180);

      const params = [
        userLat - degLat, userLat + degLat,   // $1, $2 — lat bbox
        ...catList                             // $3.. — categories
      ];
      const catPlaceholders = catList.map((_, i) => `$${i + 3}`).join(', ');

      // lon bounds after categories
      const lonIdx1 = params.length + 1;
      const lonIdx2 = params.length + 2;
      params.push(userLon - degLon, userLon + degLon);

      const limitIdx = lonIdx2 + 1;
      const offsetIdx = lonIdx2 + 2;
      params.push(maxResults, skip);

      const sql = `
        SELECT osm_type, osm_id, lat, lon, name, category, tags,
               ${distExpr} AS distance_m
        FROM pois
        WHERE category IN (${catPlaceholders})
          AND lat BETWEEN $1 AND $2
          AND lon BETWEEN $${lonIdx1} AND $${lonIdx2}
        ORDER BY distance_m
        LIMIT $${limitIdx} OFFSET $${offsetIdx}
      `;

      // Count query (same filters, no limit)
      const countSql = `
        SELECT COUNT(*)::int AS total
        FROM pois
        WHERE category IN (${catPlaceholders})
          AND lat BETWEEN $1 AND $2
          AND lon BETWEEN $${lonIdx1} AND $${lonIdx2}
      `;
      const countParams = params.slice(0, params.length - 2); // exclude limit/offset

      const [dataResult, countResult] = await Promise.all([
        pgPool.query(sql, params),
        pgPool.query(countSql, countParams)
      ]);
      rows = dataResult.rows;
      totalInRange = countResult.rows[0]?.total || 0;

      if (rows.length >= maxResults || tryKm >= 200) break;
    }

    const elements = rows.map(row => ({
      type: row.osm_type,
      id: row.osm_id,
      lat: row.lat,
      lon: row.lon,
      name: row.name,
      category: row.category,
      distance_m: Math.round(row.distance_m),
      tags: row.tags || {}
    }));

    res.json({
      count: elements.length,
      total_in_range: totalInRange,
      scope_m: scopeKm * 1000,
      elements
    });
  } catch (err) {
    console.error('[/db/pois/find]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// ── GET /pois/website — resolve website URL for a POI (waterfall: OSM → Wikidata → DDG) ──

const DIRECTORY_DOMAINS = ['yelp.com', 'facebook.com', 'tripadvisor.com', 'yellowpages.com',
  'foursquare.com', 'bbb.org', 'mapquest.com', 'whitepages.com', 'manta.com'];

async function cacheResolvedWebsite(osmType, osmId, url, source) {
  if (!pgPool) return;
  try {
    const escaped = (url || '').replace(/'/g, "''");
    const patch = JSON.stringify({ _resolved_website: url || '', _resolved_source: source });
    await pgPool.query(
      `UPDATE pois SET tags = tags || $1::jsonb WHERE osm_type = $2 AND osm_id = $3`,
      [patch, osmType, parseInt(osmId)]
    );
  } catch (err) {
    console.error('[cacheResolvedWebsite]', err.message);
  }
}

app.get('/pois/website', requirePg, async (req, res) => {
  try {
    const { osm_type, osm_id, name, lat, lon } = req.query;
    if (!osm_type || !osm_id) {
      return res.status(400).json({ error: 'osm_type and osm_id required' });
    }

    // Fetch POI row
    const result = await pgPool.query(
      `SELECT tags FROM pois WHERE osm_type = $1 AND osm_id = $2`,
      [osm_type, parseInt(osm_id)]
    );
    const tags = result.rows[0]?.tags || {};

    // Extract contact info from tags regardless of website outcome
    const phone = tags['phone'] || tags['contact:phone'] || null;
    const hours = tags['opening_hours'] || null;
    const addrParts = [];
    if (tags['addr:housenumber']) addrParts.push(tags['addr:housenumber']);
    if (tags['addr:street']) addrParts.push(tags['addr:street']);
    if (tags['addr:city']) addrParts.push(tags['addr:city']);
    if (tags['addr:state']) addrParts.push(tags['addr:state']);
    const address = addrParts.length > 0 ? addrParts.join(' ') : null;

    // Check cached result first
    if (tags._resolved_website) {
      return res.json({ url: tags._resolved_website || null, source: 'cached', phone, hours, address });
    }
    if (tags._resolved_source === 'none') {
      return res.json({ url: null, source: 'cached', phone, hours, address });
    }

    // Tier 1 — OSM tags
    const osmUrl = tags['website'] || tags['contact:website'] || tags['brand:website'] || tags['url'];
    if (osmUrl) {
      cacheResolvedWebsite(osm_type, osm_id, osmUrl, 'osm');
      return res.json({ url: osmUrl, source: 'osm', phone, hours, address });
    }

    // Tier 2 — Wikidata
    const wikidataId = tags['wikidata'] || tags['brand:wikidata'];
    if (wikidataId) {
      try {
        const wdResp = await fetch(
          `https://www.wikidata.org/wiki/Special:EntityData/${wikidataId}.json`
        );
        if (wdResp.ok) {
          const wdData = await wdResp.json();
          const entity = wdData.entities?.[wikidataId];
          const p856 = entity?.claims?.P856?.[0]?.mainsnak?.datavalue?.value;
          if (p856) {
            cacheResolvedWebsite(osm_type, osm_id, p856, 'wikidata');
            return res.json({ url: p856, source: 'wikidata', phone, hours, address });
          }
        }
      } catch (wdErr) {
        console.error('[/pois/website] Wikidata error:', wdErr.message);
      }
    }

    // Tier 3 — DuckDuckGo search
    if (name && lat && lon) {
      try {
        const searchQuery = `${name} ${lat} ${lon}`;
        const ddgResults = await DDG.search(searchQuery, { safeSearch: DDG.SafeSearchType.OFF });
        const hits = (ddgResults?.results || []).filter(r => {
          if (!r.url) return false;
          const hostname = new URL(r.url).hostname.toLowerCase();
          return !DIRECTORY_DOMAINS.some(d => hostname.includes(d));
        });
        if (hits.length > 0) {
          const foundUrl = hits[0].url;
          cacheResolvedWebsite(osm_type, osm_id, foundUrl, 'search');
          return res.json({ url: foundUrl, source: 'search', phone, hours, address });
        }
      } catch (ddgErr) {
        console.error('[/pois/website] DDG search error:', ddgErr.message);
      }
    }

    // No website found
    cacheResolvedWebsite(osm_type, osm_id, '', 'none');
    res.json({ url: null, source: 'none', phone, hours, address });
  } catch (err) {
    console.error('[/pois/website]', err.message);
    res.json({ url: null, source: 'none', phone: null, hours: null, address: null });
  }
});

// ── PostgreSQL-backed /db/aircraft/* endpoints ───────────────────────────────

function toSighting(row) {
  return {
    id: row.id, icao24: row.icao24, callsign: row.callsign,
    originCountry: row.origin_country,
    firstSeen: row.first_seen, lastSeen: row.last_seen,
    firstLat: row.first_lat, firstLon: row.first_lon,
    firstAltitude: row.first_altitude, firstHeading: row.first_heading,
    lastLat: row.last_lat, lastLon: row.last_lon,
    lastAltitude: row.last_altitude, lastHeading: row.last_heading,
    lastVelocity: row.last_velocity, lastVerticalRate: row.last_vertical_rate,
    squawk: row.squawk, onGround: row.on_ground,
    durationSec: row.duration_sec != null ? parseInt(row.duration_sec) : undefined,
    distanceKm: row.distance_km != null ? parseFloat(row.distance_km) : undefined,
  };
}

// GET /db/aircraft/search — filtered search (callsign, icao24, country, bbox, time)
app.get('/db/aircraft/search', requirePg, async (req, res) => {
  try {
    const { q, icao24, callsign, country, s, w, n, e, since, until, on_ground, limit, offset } = req.query;
    const conditions = [];
    const params = [];
    let pi = 1;

    if (q) {
      conditions.push(`(callsign ILIKE $${pi} OR icao24 ILIKE $${pi})`);
      params.push(`%${q}%`);
      pi++;
    }
    if (icao24) {
      conditions.push(`icao24 = $${pi}`);
      params.push(icao24.toLowerCase());
      pi++;
    }
    if (callsign) {
      conditions.push(`callsign ILIKE $${pi}`);
      params.push(`%${callsign}%`);
      pi++;
    }
    if (country) {
      conditions.push(`origin_country ILIKE $${pi}`);
      params.push(`%${country}%`);
      pi++;
    }
    if (s && w && n && e) {
      conditions.push(`last_lat BETWEEN $${pi} AND $${pi + 1}`);
      params.push(parseFloat(s), parseFloat(n));
      pi += 2;
      conditions.push(`last_lon BETWEEN $${pi} AND $${pi + 1}`);
      params.push(parseFloat(w), parseFloat(e));
      pi += 2;
    }
    if (since) {
      conditions.push(`last_seen >= $${pi}`);
      params.push(since);
      pi++;
    }
    if (until) {
      conditions.push(`first_seen <= $${pi}`);
      params.push(until);
      pi++;
    }
    if (on_ground != null) {
      conditions.push(`on_ground = $${pi}`);
      params.push(on_ground === 'true');
      pi++;
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(' AND ')}` : '';
    const lim = Math.min(parseInt(limit) || 100, 500);
    const off = parseInt(offset) || 0;

    const sql = `SELECT *, EXTRACT(EPOCH FROM last_seen - first_seen) AS duration_sec
      FROM aircraft_sightings ${where}
      ORDER BY last_seen DESC LIMIT ${lim} OFFSET ${off}`;
    const result = await pgPool.query(sql, params);
    res.json({ count: result.rows.length, sightings: result.rows.map(toSighting) });
  } catch (err) {
    console.error('[/db/aircraft/search]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// GET /db/aircraft/recent — most recently seen aircraft (deduplicated by icao24)
app.get('/db/aircraft/recent', requirePg, async (req, res) => {
  try {
    const lim = Math.min(parseInt(req.query.limit) || 50, 200);
    const sql = `SELECT DISTINCT ON (icao24) *, EXTRACT(EPOCH FROM last_seen - first_seen) AS duration_sec
      FROM aircraft_sightings ORDER BY icao24, last_seen DESC`;
    const result = await pgPool.query(sql);
    // Sort by last_seen descending after dedup, then limit
    const sorted = result.rows
      .sort((a, b) => new Date(b.last_seen) - new Date(a.last_seen))
      .slice(0, lim);
    res.json({ count: sorted.length, totalAircraft: result.rows.length, sightings: sorted.map(toSighting) });
  } catch (err) {
    console.error('[/db/aircraft/recent]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// GET /db/aircraft/stats — aircraft analytics overview
// NOTE: must be defined before /:icao24 to avoid matching "stats" as a param
app.get('/db/aircraft/stats', requirePg, async (req, res) => {
  try {
    const [total, unique, countries, topCallsigns, timeRange, altitudes] = await Promise.all([
      pgPool.query('SELECT COUNT(*) AS n FROM aircraft_sightings'),
      pgPool.query('SELECT COUNT(DISTINCT icao24) AS n FROM aircraft_sightings'),
      pgPool.query('SELECT origin_country, COUNT(DISTINCT icao24) AS n FROM aircraft_sightings WHERE origin_country IS NOT NULL GROUP BY origin_country ORDER BY n DESC LIMIT 20'),
      pgPool.query(`SELECT callsign, COUNT(*) AS n FROM aircraft_sightings WHERE callsign IS NOT NULL AND callsign != '' GROUP BY callsign ORDER BY n DESC LIMIT 20`),
      pgPool.query('SELECT MIN(first_seen) AS earliest, MAX(last_seen) AS latest FROM aircraft_sightings'),
      pgPool.query(`SELECT
        COUNT(*) FILTER (WHERE last_altitude IS NULL OR on_ground = true) AS ground,
        COUNT(*) FILTER (WHERE last_altitude < 1524 AND on_ground = false) AS below_5k,
        COUNT(*) FILTER (WHERE last_altitude BETWEEN 1524 AND 6096) AS ft5k_20k,
        COUNT(*) FILTER (WHERE last_altitude > 6096) AS above_20k
        FROM aircraft_sightings`),
    ]);
    res.json({
      totalSightings: parseInt(total.rows[0].n),
      uniqueAircraft: parseInt(unique.rows[0].n),
      topCountries: countries.rows.map(r => ({ country: r.origin_country, aircraft: parseInt(r.n) })),
      topCallsigns: topCallsigns.rows.map(r => ({ callsign: r.callsign, sightings: parseInt(r.n) })),
      timeRange: timeRange.rows[0],
      altitudeDistribution: {
        ground: parseInt(altitudes.rows[0].ground),
        below5kFt: parseInt(altitudes.rows[0].below_5k),
        ft5k_20k: parseInt(altitudes.rows[0].ft5k_20k),
        above20kFt: parseInt(altitudes.rows[0].above_20k),
      },
    });
  } catch (err) {
    console.error('[/db/aircraft/stats]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// GET /db/aircraft/:icao24 — all sightings for one aircraft
app.get('/db/aircraft/:icao24', requirePg, async (req, res) => {
  try {
    const icao = req.params.icao24.toLowerCase();
    const result = await pgPool.query(
      `SELECT *, EXTRACT(EPOCH FROM last_seen - first_seen) AS duration_sec
       FROM aircraft_sightings WHERE icao24 = $1 ORDER BY first_seen DESC`,
      [icao]
    );
    if (result.rows.length === 0) return res.status(404).json({ error: 'No sightings found', icao24: icao });

    // Build flight path from all sightings (first→last positions)
    const path = result.rows.map(r => ({
      firstLat: r.first_lat, firstLon: r.first_lon,
      lastLat: r.last_lat, lastLon: r.last_lon,
      firstSeen: r.first_seen, lastSeen: r.last_seen,
      altitude: r.last_altitude, heading: r.last_heading,
    })).reverse();

    res.json({
      icao24: icao,
      callsigns: [...new Set(result.rows.map(r => r.callsign).filter(Boolean))],
      originCountry: result.rows[0].origin_country,
      totalSightings: result.rows.length,
      firstSeen: result.rows[result.rows.length - 1].first_seen,
      lastSeen: result.rows[0].last_seen,
      sightings: result.rows.map(toSighting),
      path,
    });
  } catch (err) {
    console.error('[/db/aircraft/:icao24]', err.message);
    res.status(500).json({ error: err.message });
  }
});

// ── Windy Webcams (10 min TTL, bbox passthrough) ─────────────────────────────

const WINDY_API_KEY = 'VyQEq6OkmnafZMqEqbVrbH4MXQmS1xfw';

app.get('/webcams', async (req, res) => {
  const { s, w, n, e, categories } = req.query;
  if (!s || !w || !n || !e) return res.status(400).json({ error: 'Must specify s, w, n, e bounds' });

  const catParam = categories || 'traffic';

  // Snap bbox to 0.01° grid for cache reuse across small scrolls
  const snapped = snapBbox(Number(s), Number(w), Number(n), Number(e), 2);
  const cacheKey = `webcams:${snapped.s},${snapped.w},${snapped.n},${snapped.e}:${catParam}`;
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
    const upstreamUrl = `https://api.windy.com/webcams/api/v3/webcams?bbox=${snapped.n},${snapped.e},${snapped.s},${snapped.w}&category=${encodeURIComponent(catParam)}&limit=50&include=images,location,categories,player,urls`;
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
      playerUrl: wc.player?.lifetime || wc.player?.day || '',
      detailUrl: wc.urls?.detail || '',
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

// ── MBTA Bus Stops (24h TTL — stops rarely change) ───────────────────────────

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

// ── Geocode (Photon — autocomplete-friendly OSM geocoder) ───────────────────

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

// ── NWS Weather Composite (/weather?lat=&lon=) ────────────────────────────

const NWS_UA = { 'User-Agent': 'LocationMapApp/1.5 contact@example.com', 'Accept': 'application/geo+json' };
const NWS_TTL_POINTS   = 24 * 60 * 60 * 1000;  // 24h
const NWS_TTL_CURRENT  =  5 * 60 * 1000;        // 5min
const NWS_TTL_FORECAST = 30 * 60 * 1000;        // 30min
const NWS_TTL_ALERTS   =  5 * 60 * 1000;        // 5min

function extractNwsIconCode(iconUrl) {
  if (!iconUrl) return 'unknown';
  // e.g. https://api.weather.gov/icons/land/day/sct,20?size=medium → "sct"
  const m = iconUrl.match(/\/icons\/land\/(?:day|night)\/([^,?/]+)/);
  return m ? m[1] : 'unknown';
}

function isDaytimeFromIcon(iconUrl) {
  if (!iconUrl) return true;
  return iconUrl.includes('/day/');
}

function celsiusToF(c) { return c != null ? Math.round(c * 9 / 5 + 32) : null; }
function kmhToMph(k) { return k != null ? Math.round(k * 0.621371) : null; }
function paToInHg(p) { return p != null ? +(p / 3386.39).toFixed(2) : null; }
function metersToMiles(m) { return m != null ? +(m / 1609.344).toFixed(1) : null; }

function parseWindSpeed(s) {
  // "12 mph" or "5 to 10 mph" — pass through as-is
  return s || '';
}

function extractPrecipProb(period) {
  if (period.probabilityOfPrecipitation && period.probabilityOfPrecipitation.value != null) {
    return period.probabilityOfPrecipitation.value;
  }
  return 0;
}

app.get('/weather', async (req, res) => {
  const lat = parseFloat(req.query.lat);
  const lon = parseFloat(req.query.lon);
  if (isNaN(lat) || isNaN(lon)) return res.status(400).json({ error: 'Must specify lat and lon' });

  // Snap to 2 decimal places for cache keys (~1km resolution)
  const sLat = lat.toFixed(2);
  const sLon = lon.toFixed(2);
  const pointKey = `weather:point:${sLat},${sLon}`;

  try {
    // Step 1: Get grid coordinates + station (24h cache)
    let pointData = cacheGet(pointKey, NWS_TTL_POINTS);
    if (!pointData) {
      const pointResp = await fetch(`https://api.weather.gov/points/${sLat},${sLon}`, { headers: NWS_UA });
      if (!pointResp.ok) {
        const text = await pointResp.text();
        console.error(`[Weather] /points failed: ${pointResp.status} ${text.substring(0, 200)}`);
        return res.status(502).json({ error: 'NWS points lookup failed', status: pointResp.status });
      }
      const pointJson = await pointResp.json();
      const props = pointJson.properties;
      pointData = {
        data: {
          gridId: props.gridId,
          gridX: props.gridX,
          gridY: props.gridY,
          city: props.relativeLocation?.properties?.city || '',
          state: props.relativeLocation?.properties?.state || '',
          station: props.observationStations // URL to list
        }
      };
      cacheSet(pointKey, JSON.stringify(pointData.data));
      pointData = { data: JSON.stringify(pointData.data) };
    }
    stats.hits++; // count the composite request
    const grid = JSON.parse(pointData.data);

    // Resolve nearest station (from the station list URL, also cached with points)
    const stationKey = `weather:station:${grid.gridId}:${grid.gridX},${grid.gridY}`;
    let stationId;
    const cachedStation = cacheGet(stationKey, NWS_TTL_POINTS);
    if (cachedStation) {
      stationId = cachedStation.data;
    } else if (grid.station) {
      const stResp = await fetch(grid.station, { headers: NWS_UA });
      if (stResp.ok) {
        const stJson = await stResp.json();
        stationId = stJson.features?.[0]?.properties?.stationIdentifier || 'UNKNOWN';
      } else {
        stationId = 'UNKNOWN';
      }
      cacheSet(stationKey, stationId);
    } else {
      stationId = 'UNKNOWN';
    }

    // Step 2-5: Fetch remaining data in parallel with per-section TTLs
    const currentKey  = `weather:current:${stationId}`;
    const hourlyKey   = `weather:hourly:${grid.gridId}:${grid.gridX},${grid.gridY}`;
    const dailyKey    = `weather:daily:${grid.gridId}:${grid.gridX},${grid.gridY}`;
    const alertKey    = `weather:alerts:${sLat},${sLon}`;

    const [currentData, hourlyData, dailyData, alertData] = await Promise.all([
      // Current conditions (5min)
      (async () => {
        const cached = cacheGet(currentKey, NWS_TTL_CURRENT);
        if (cached) return JSON.parse(cached.data);
        try {
          const r = await fetch(`https://api.weather.gov/stations/${stationId}/observations/latest`, { headers: NWS_UA });
          if (!r.ok) return null;
          const j = await r.json();
          const p = j.properties;
          const result = {
            temperature: celsiusToF(p.temperature?.value),
            temperatureUnit: 'F',
            humidity: p.relativeHumidity?.value != null ? Math.round(p.relativeHumidity.value) : null,
            windSpeed: kmhToMph(p.windSpeed?.value),
            windDirection: p.windDirection?.value != null ? degToCompass(p.windDirection.value) : null,
            windChill: celsiusToF(p.windChill?.value),
            heatIndex: celsiusToF(p.heatIndex?.value),
            dewpoint: celsiusToF(p.dewpoint?.value),
            description: p.textDescription || '',
            iconCode: extractNwsIconCode(p.icon),
            isDaytime: isDaytimeFromIcon(p.icon),
            visibility: metersToMiles(p.visibility?.value),
            barometer: paToInHg(p.barometricPressure?.value)
          };
          cacheSet(currentKey, JSON.stringify(result));
          return result;
        } catch (e) { console.error('[Weather] current conditions error:', e.message); return null; }
      })(),
      // Hourly forecast (30min)
      (async () => {
        const cached = cacheGet(hourlyKey, NWS_TTL_FORECAST);
        if (cached) return JSON.parse(cached.data);
        try {
          const r = await fetch(`https://api.weather.gov/gridpoints/${grid.gridId}/${grid.gridX},${grid.gridY}/forecast/hourly`, { headers: NWS_UA });
          if (!r.ok) return [];
          const j = await r.json();
          const periods = (j.properties?.periods || []).slice(0, 48).map(p => ({
            time: p.startTime,
            temperature: p.temperature,
            windSpeed: parseWindSpeed(p.windSpeed),
            windDirection: p.windDirection || '',
            precipProbability: extractPrecipProb(p),
            shortForecast: p.shortForecast || '',
            iconCode: extractNwsIconCode(p.icon),
            isDaytime: p.isDaytime
          }));
          cacheSet(hourlyKey, JSON.stringify(periods));
          return periods;
        } catch (e) { console.error('[Weather] hourly forecast error:', e.message); return []; }
      })(),
      // Daily forecast (30min)
      (async () => {
        const cached = cacheGet(dailyKey, NWS_TTL_FORECAST);
        if (cached) return JSON.parse(cached.data);
        try {
          const r = await fetch(`https://api.weather.gov/gridpoints/${grid.gridId}/${grid.gridX},${grid.gridY}/forecast`, { headers: NWS_UA });
          if (!r.ok) return [];
          const j = await r.json();
          const periods = (j.properties?.periods || []).map(p => ({
            name: p.name,
            isDaytime: p.isDaytime,
            temperature: p.temperature,
            windSpeed: parseWindSpeed(p.windSpeed),
            shortForecast: p.shortForecast || '',
            detailedForecast: p.detailedForecast || '',
            iconCode: extractNwsIconCode(p.icon),
            precipProbability: extractPrecipProb(p)
          }));
          cacheSet(dailyKey, JSON.stringify(periods));
          return periods;
        } catch (e) { console.error('[Weather] daily forecast error:', e.message); return []; }
      })(),
      // Location-specific alerts (5min)
      (async () => {
        const cached = cacheGet(alertKey, NWS_TTL_ALERTS);
        if (cached) return JSON.parse(cached.data);
        try {
          const r = await fetch(`https://api.weather.gov/alerts/active?point=${sLat},${sLon}`, { headers: NWS_UA });
          if (!r.ok) return [];
          const j = await r.json();
          const alerts = (j.features || []).map(f => {
            const p = f.properties;
            return {
              id: p.id || '',
              event: p.event || '',
              severity: p.severity || '',
              urgency: p.urgency || '',
              headline: p.headline || '',
              description: p.description || '',
              instruction: p.instruction || '',
              effective: p.effective || '',
              expires: p.expires || '',
              areaDesc: p.areaDesc || ''
            };
          });
          cacheSet(alertKey, JSON.stringify(alerts));
          return alerts;
        } catch (e) { console.error('[Weather] alerts error:', e.message); return []; }
      })()
    ]);

    const result = {
      location: { city: grid.city, state: grid.state, station: stationId },
      current: currentData,
      hourly: hourlyData,
      daily: dailyData,
      alerts: alertData,
      fetchedAt: new Date().toISOString()
    };

    log('/weather', false, 0, `${stationId} ${grid.city},${grid.state} alerts=${(alertData||[]).length}`);
    res.json(result);
  } catch (err) {
    console.error('[Weather] composite error:', err.message);
    res.status(502).json({ error: 'Weather fetch failed', detail: err.message });
  }
});

function degToCompass(deg) {
  if (deg == null) return '';
  const dirs = ['N','NNE','NE','ENE','E','ESE','SE','SSE','S','SSW','SW','WSW','W','WNW','NW','NNW'];
  return dirs[Math.round(deg / 22.5) % 16];
}

// ── FAA TFR (Temporary Flight Restrictions) ─────────────────────────────────

const TFR_LIST_TTL = 5 * 60 * 1000;    // 5 min
const TFR_DETAIL_TTL = 10 * 60 * 1000; // 10 min

/** Parse FAA DMS coordinate like "383200N" or "0770305W" to decimal degrees. */
function parseFaaDms(dms) {
  if (!dms || typeof dms !== 'string') return null;
  const m = dms.match(/^(\d{2,3})(\d{2})(\d{2})([NSEW])$/);
  if (!m) return null;
  const deg = parseInt(m[1], 10);
  const min = parseInt(m[2], 10);
  const sec = parseInt(m[3], 10);
  let dd = deg + min / 60 + sec / 3600;
  if (m[4] === 'S' || m[4] === 'W') dd = -dd;
  return dd;
}

/** Parse altitude string from AIXM (feet or FL). Returns feet. */
function parseAltitude(val, uom) {
  if (val == null) return 0;
  const n = typeof val === 'string' ? parseInt(val, 10) : val;
  if (isNaN(n)) return 0;
  if (uom === 'FL') return n * 100;
  return n;
}

/** Generate circle polygon points from center + radius. */
function circleToPolygon(latCenter, lonCenter, radiusNm, numPoints = 32) {
  const points = [];
  const radiusDeg = radiusNm / 60; // 1 nm ≈ 1 arcminute of lat
  for (let i = 0; i <= numPoints; i++) {
    const angle = (2 * Math.PI * i) / numPoints;
    const lat = latCenter + radiusDeg * Math.cos(angle);
    const lon = lonCenter + (radiusDeg / Math.cos(latCenter * Math.PI / 180)) * Math.sin(angle);
    points.push([lon, lat]);
  }
  return points;
}

/** Interpolate arc segment between two bearings around a center point. */
function arcToPoints(latCenter, lonCenter, radiusNm, startBearing, endBearing, stepDeg = 5) {
  const points = [];
  const radiusDeg = radiusNm / 60;
  let b = startBearing;
  const dir = ((endBearing - startBearing + 360) % 360) <= 180 ? 1 : -1;
  for (let i = 0; i < 360 / stepDeg; i++) {
    const rad = (b * Math.PI) / 180;
    const lat = latCenter + radiusDeg * Math.cos(rad);
    const lon = lonCenter + (radiusDeg / Math.cos(latCenter * Math.PI / 180)) * Math.sin(rad);
    points.push([lon, lat]);
    if (Math.abs(((b - endBearing + 360) % 360)) < stepDeg) break;
    b = (b + dir * stepDeg + 360) % 360;
  }
  // Final point at exact end bearing
  const endRad = (endBearing * Math.PI) / 180;
  points.push([
    lonCenter + (radiusDeg / Math.cos(latCenter * Math.PI / 180)) * Math.sin(endRad),
    latCenter + radiusDeg * Math.cos(endRad)
  ]);
  return points;
}

/** Fetch and parse a single TFR detail page XML. Returns parsed shapes or null. */
async function fetchTfrDetail(detailPath) {
  const cacheKey = `tfr_detail:${detailPath}`;
  const cached = cacheGet(cacheKey, TFR_DETAIL_TTL);
  if (cached) { stats.hits++; return JSON.parse(cached.data); }

  try {
    const url = `https://tfr.faa.gov${detailPath}`;
    const resp = await fetch(url, {
      headers: { 'User-Agent': 'LocationMapApp/1.5 cache-proxy' },
      signal: AbortSignal.timeout(15000)
    });
    if (!resp.ok) return null;
    const xml = await resp.text();

    const parser = new XMLParser({
      ignoreAttributes: false,
      attributeNamePrefix: '@_',
      isArray: (name) => ['Group', 'Avx'].includes(name)
    });
    const parsed = parser.parse(xml);

    // Navigate AIXM structure — handle various FAA XML formats
    const tfrDoc = parsed?.NOTAM || parsed?.['aip:NOTAM'] || parsed;
    let notamText = '';
    let tfrType = '';
    let facilityName = '';
    let stateName = '';
    let effectiveDate = '';
    let expireDate = '';

    // Try to extract NOTAM text and metadata
    const extractText = (obj, ...keys) => {
      for (const k of keys) {
        if (obj && obj[k] && typeof obj[k] === 'string') return obj[k];
        if (obj && obj[k] && obj[k]['#text']) return obj[k]['#text'];
      }
      return '';
    };

    // Shapes — look for Group elements with Avx (aviation vertex) arrays
    const shapes = [];
    function findGroups(obj) {
      if (!obj || typeof obj !== 'object') return;
      if (Array.isArray(obj)) { obj.forEach(findGroups); return; }
      if (obj.Group) {
        const groups = Array.isArray(obj.Group) ? obj.Group : [obj.Group];
        for (const g of groups) {
          const shape = parseGroup(g);
          if (shape) shapes.push(shape);
        }
      }
      // Recurse into all child objects
      for (const k of Object.keys(obj)) {
        if (k !== 'Group') findGroups(obj[k]);
      }
    }

    function parseGroup(group) {
      // Extract altitude
      const valDistVerLower = group.valDistVerLower ?? group['@_valDistVerLower'] ?? '0';
      const uomDistVerLower = group.uomDistVerLower ?? group['@_uomDistVerLower'] ?? 'FT';
      const valDistVerUpper = group.valDistVerUpper ?? group['@_valDistVerUpper'] ?? '99999';
      const uomDistVerUpper = group.uomDistVerUpper ?? group['@_uomDistVerUpper'] ?? 'FT';
      const floorAltFt = parseAltitude(valDistVerLower, uomDistVerLower);
      const ceilingAltFt = parseAltitude(valDistVerUpper, uomDistVerUpper);

      const avxList = group.Avx || group.avx;
      if (!avxList || !Array.isArray(avxList) || avxList.length === 0) {
        // Check for single-point circle (codeType=C with radius)
        return null;
      }

      const points = [];
      let shapeType = 'polygon';
      let radiusNm = null;

      for (const avx of avxList) {
        const code = avx.codeType ?? avx['@_codeType'] ?? '';
        const lat = parseFaaDms(avx.geoLat ?? avx['@_geoLat'] ?? '');
        const lon = parseFaaDms(avx.geoLon ?? avx['@_geoLon'] ?? '');

        if (code === 'CWA' || code === 'CCA') {
          // Arc vertex — center + radius
          const arcLat = parseFaaDms(avx.geoLatArc ?? avx['@_geoLatArc'] ?? '');
          const arcLon = parseFaaDms(avx.geoLonArc ?? avx['@_geoLonArc'] ?? '');
          const r = parseFloat(avx.valRadiusArc ?? avx['@_valRadiusArc'] ?? '0');
          const rUom = avx.uomRadiusArc ?? avx['@_uomRadiusArc'] ?? 'NM';
          const rNm = rUom === 'KM' ? r * 0.539957 : r;

          if (arcLat != null && arcLon != null && rNm > 0 && lat != null && lon != null) {
            // Compute start/end bearings and interpolate arc
            const startBearing = Math.atan2(
              Math.sin((points.length > 0 ? points[points.length-1][0] : lon) - arcLon) * Math.cos(arcLat),
              Math.cos(arcLat) * Math.sin(points.length > 0 ? points[points.length-1][1] : lat) -
              Math.sin(arcLat) * Math.cos(points.length > 0 ? points[points.length-1][1] : lat) *
              Math.cos((points.length > 0 ? points[points.length-1][0] : lon) - arcLon)
            ) * 180 / Math.PI;
            const endBearing = Math.atan2(
              Math.sin(lon - arcLon) * Math.cos(arcLat),
              Math.cos(arcLat) * Math.sin(lat) - Math.sin(arcLat) * Math.cos(lat) * Math.cos(lon - arcLon)
            ) * 180 / Math.PI;
            const arcPts = arcToPoints(arcLat, arcLon, rNm, (startBearing + 360) % 360, (endBearing + 360) % 360);
            for (const p of arcPts) points.push(p);
            shapeType = 'polyarc';
          }
        } else if (code === 'C') {
          // Circle center
          const r = parseFloat(avx.valRadiusArc ?? avx['@_valRadiusArc'] ?? '0');
          const rUom = avx.uomRadiusArc ?? avx['@_uomRadiusArc'] ?? 'NM';
          const rNm = rUom === 'KM' ? r * 0.539957 : r;
          if (lat != null && lon != null && rNm > 0) {
            const circlePts = circleToPolygon(lat, lon, rNm);
            for (const p of circlePts) points.push(p);
            shapeType = 'circle';
            radiusNm = rNm;
          }
        } else {
          // GRC (great circle) or regular point
          if (lat != null && lon != null) {
            points.push([lon, lat]);
          }
        }
      }

      if (points.length < 3) return null;

      // Close polygon if needed
      const first = points[0], last = points[points.length - 1];
      if (Math.abs(first[0] - last[0]) > 0.0001 || Math.abs(first[1] - last[1]) > 0.0001) {
        points.push([...first]);
      }

      return { type: shapeType, points, floorAltFt, ceilingAltFt, radiusNm };
    }

    findGroups(parsed);

    // Also try to extract metadata
    function findMeta(obj) {
      if (!obj || typeof obj !== 'object') return;
      if (Array.isArray(obj)) { obj.forEach(findMeta); return; }
      if (obj.txtDescrUSNS && !notamText) notamText = String(obj.txtDescrUSNS);
      if (obj.txtNameUSNS && !notamText) notamText = String(obj.txtNameUSNS);
      if (obj.txtDescrTraditional && !notamText) notamText = String(obj.txtDescrTraditional);
      if (obj.codeTimeZone && !tfrType) tfrType = String(obj.codeTimeZone);
      if (obj.txtNameFacility && !facilityName) facilityName = String(obj.txtNameFacility);
      if (obj.txtNameState && !stateName) stateName = String(obj.txtNameState);
      if (obj.dateEffective && !effectiveDate) effectiveDate = String(obj.dateEffective);
      if (obj.dateExpire && !expireDate) expireDate = String(obj.dateExpire);
      for (const k of Object.keys(obj)) findMeta(obj[k]);
    }
    findMeta(parsed);

    const result = { shapes, notamText, tfrType, facilityName, stateName, effectiveDate, expireDate };
    stats.misses++;
    cacheSet(cacheKey, JSON.stringify(result), { 'content-type': 'application/json' });
    return result;
  } catch (err) {
    console.error(`[TFR detail error] ${detailPath}: ${err.message}`);
    return null;
  }
}

app.get('/tfrs', async (req, res) => {
  const bbox = req.query.bbox;
  if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
  const [bboxS, bboxW, bboxN, bboxE] = bbox.split(',').map(Number);
  if ([bboxS, bboxW, bboxN, bboxE].some(isNaN)) {
    return res.status(400).json({ error: 'Invalid bbox — expected s,w,n,e as numbers' });
  }

  const t0 = Date.now();

  try {
    // Step 1: Fetch TFR list page (5-min cache)
    const listCacheKey = 'tfr_list';
    let tfrLinks;
    const cachedList = cacheGet(listCacheKey, TFR_LIST_TTL);
    if (cachedList) {
      stats.hits++;
      tfrLinks = JSON.parse(cachedList.data);
    } else {
      stats.misses++;
      const listResp = await fetch('https://tfr.faa.gov/tfr2/list.jsp', {
        headers: { 'User-Agent': 'LocationMapApp/1.5 cache-proxy' },
        signal: AbortSignal.timeout(15000)
      });
      if (!listResp.ok) {
        return res.status(502).json({ error: `FAA TFR list returned ${listResp.status}` });
      }
      const listHtml = await listResp.text();
      const $ = cheerio.load(listHtml);
      tfrLinks = [];
      $('a[href*="save_pages"]').each((_, el) => {
        const href = $(el).attr('href');
        if (href && href.includes('.xml')) {
          // Extract NOTAM number from link text or nearby td
          const text = $(el).text().trim();
          const row = $(el).closest('tr');
          const cells = row.find('td');
          const notam = text || $(cells[0]).text().trim();
          const type = cells.length > 1 ? $(cells[1]).text().trim() : '';
          const description = cells.length > 2 ? $(cells[2]).text().trim() : '';
          const state = cells.length > 3 ? $(cells[3]).text().trim() : '';
          tfrLinks.push({ href: href.startsWith('/') ? href : `/${href}`, notam, type, description, state });
        }
      });
      cacheSet(listCacheKey, JSON.stringify(tfrLinks), { 'content-type': 'application/json' });
      log('/tfrs', false, Date.now() - t0, `${tfrLinks.length} TFRs listed`);
    }

    // Step 2: Fetch detail XML for each TFR (parallel, each cached 10 min)
    const detailPromises = tfrLinks.map(async (link) => {
      const detail = await fetchTfrDetail(link.href);
      if (!detail || !detail.shapes || detail.shapes.length === 0) return null;

      // Filter shapes against bbox
      const filteredShapes = detail.shapes.filter(shape => {
        if (!shape.points || shape.points.length === 0) return false;
        let minLat = 90, maxLat = -90, minLon = 180, maxLon = -180;
        for (const [lon, lat] of shape.points) {
          if (lat < minLat) minLat = lat;
          if (lat > maxLat) maxLat = lat;
          if (lon < minLon) minLon = lon;
          if (lon > maxLon) maxLon = lon;
        }
        // Intersects bbox?
        return !(maxLat < bboxS || minLat > bboxN || maxLon < bboxW || minLon > bboxE);
      });

      if (filteredShapes.length === 0) return null;

      const id = link.href.replace(/.*detail_/, '').replace('.xml', '');
      return {
        id,
        notam: link.notam || detail.notamText || id,
        type: link.type || detail.tfrType || 'TFR',
        description: link.description || detail.notamText || '',
        effectiveDate: detail.effectiveDate || '',
        expireDate: detail.expireDate || '',
        facility: detail.facilityName || '',
        state: link.state || detail.stateName || '',
        shapes: filteredShapes
      };
    });

    const results = (await Promise.all(detailPromises)).filter(Boolean);
    const elapsed = Date.now() - t0;
    log('/tfrs', !!cachedList, elapsed, `${results.length} TFRs in bbox`);
    res.json(results);
  } catch (err) {
    console.error(`[TFR error] ${err.message}`);
    res.status(502).json({ error: 'TFR fetch failed', detail: err.message });
  }
});

// ── Speed / Red-Light Cameras (Overpass) ─────────────────────────────────────

const CAMERA_TTL = 24 * 60 * 60 * 1000;  // 24h

app.get('/cameras', async (req, res) => {
  const bbox = req.query.bbox;
  if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
  const parts = bbox.split(',').map(Number);
  if (parts.length !== 4 || parts.some(isNaN)) return res.status(400).json({ error: 'Invalid bbox' });
  const snap = snapBbox(parts[0], parts[1], parts[2], parts[3]);
  const cacheKey = `cameras:${snap.s},${snap.w},${snap.n},${snap.e}`;
  const cached = cacheGet(cacheKey, CAMERA_TTL);
  if (cached) { stats.hits++; log('/cameras', true, 0); return res.json(JSON.parse(cached.data)); }
  stats.misses++;
  const t0 = Date.now();
  try {
    const query = `[out:json][timeout:30];(node["highway"="speed_camera"](${snap.s},${snap.w},${snap.n},${snap.e});node["man_made"="surveillance"]["surveillance:type"="camera"]["surveillance"="enforcement"](${snap.s},${snap.w},${snap.n},${snap.e}););out body;`;
    const resp = await fetch('https://overpass-api.de/api/interpreter', {
      method: 'POST', body: 'data=' + encodeURIComponent(query),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      signal: AbortSignal.timeout(30000)
    });
    if (!resp.ok) return res.status(502).json({ error: `Overpass returned ${resp.status}` });
    const data = await resp.json();
    const results = (data.elements || []).map(el => ({
      id: `camera_${el.id}`,
      lat: el.lat,
      lon: el.lon,
      cameraType: el.tags?.['camera:type'] || el.tags?.['surveillance:type'] || 'speed_camera',
      maxspeed: el.tags?.maxspeed || null,
      direction: el.tags?.direction || null,
      operator: el.tags?.operator || null,
      name: el.tags?.name || el.tags?.description || null,
    }));
    cacheSet(cacheKey, JSON.stringify(results), { 'content-type': 'application/json' });
    log('/cameras', false, Date.now() - t0, `${results.length} cameras`);
    res.json(results);
  } catch (err) {
    console.error(`[cameras error] ${err.message}`);
    res.status(502).json({ error: 'Camera fetch failed', detail: err.message });
  }
});

// ── School Zones (Overpass) ──────────────────────────────────────────────────

const SCHOOL_TTL = 24 * 60 * 60 * 1000;  // 24h

app.get('/schools', async (req, res) => {
  const bbox = req.query.bbox;
  if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
  const parts = bbox.split(',').map(Number);
  if (parts.length !== 4 || parts.some(isNaN)) return res.status(400).json({ error: 'Invalid bbox' });
  const snap = snapBbox(parts[0], parts[1], parts[2], parts[3]);
  const cacheKey = `schools:${snap.s},${snap.w},${snap.n},${snap.e}`;
  const cached = cacheGet(cacheKey, SCHOOL_TTL);
  if (cached) { stats.hits++; log('/schools', true, 0); return res.json(JSON.parse(cached.data)); }
  stats.misses++;
  const t0 = Date.now();
  try {
    const query = `[out:json][timeout:30];(node["amenity"="school"](${snap.s},${snap.w},${snap.n},${snap.e});way["amenity"="school"](${snap.s},${snap.w},${snap.n},${snap.e});relation["amenity"="school"](${snap.s},${snap.w},${snap.n},${snap.e}););out body; >; out skel qt;`;
    const resp = await fetch('https://overpass-api.de/api/interpreter', {
      method: 'POST', body: 'data=' + encodeURIComponent(query),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      signal: AbortSignal.timeout(30000)
    });
    if (!resp.ok) return res.status(502).json({ error: `Overpass returned ${resp.status}` });
    const data = await resp.json();
    // Build node lookup for way/relation geometry
    const nodeLookup = {};
    for (const el of data.elements || []) {
      if (el.type === 'node' && !el.tags) nodeLookup[el.id] = { lat: el.lat, lon: el.lon };
    }
    const results = [];
    for (const el of data.elements || []) {
      if (!el.tags || el.tags.amenity !== 'school') continue;
      const entry = {
        id: `school_${el.type}_${el.id}`,
        name: el.tags.name || 'School',
        lat: el.lat || null,
        lon: el.lon || null,
        grades: el.tags['school:grades'] || el.tags.isced || null,
        operator: el.tags.operator || null,
        isPolygon: false,
        points: null,
      };
      if (el.type === 'way' && el.nodes) {
        const pts = el.nodes.map(nid => nodeLookup[nid]).filter(Boolean);
        if (pts.length >= 3) {
          entry.isPolygon = true;
          entry.points = pts.map(p => [p.lon, p.lat]);
          // Compute centroid for lat/lon
          const sumLat = pts.reduce((s, p) => s + p.lat, 0);
          const sumLon = pts.reduce((s, p) => s + p.lon, 0);
          entry.lat = sumLat / pts.length;
          entry.lon = sumLon / pts.length;
        }
      }
      if (entry.lat != null && entry.lon != null) results.push(entry);
    }
    cacheSet(cacheKey, JSON.stringify(results), { 'content-type': 'application/json' });
    log('/schools', false, Date.now() - t0, `${results.length} schools`);
    res.json(results);
  } catch (err) {
    console.error(`[schools error] ${err.message}`);
    res.status(502).json({ error: 'School fetch failed', detail: err.message });
  }
});

// ── Flood Zones (FEMA NFHL ArcGIS Layer 28) ─────────────────────────────────

const FLOOD_TTL = 30 * 24 * 60 * 60 * 1000;  // 30 days

app.get('/flood-zones', async (req, res) => {
  const bbox = req.query.bbox;
  if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
  const parts = bbox.split(',').map(Number);
  if (parts.length !== 4 || parts.some(isNaN)) return res.status(400).json({ error: 'Invalid bbox' });
  const snap = snapBbox(parts[0], parts[1], parts[2], parts[3]);
  const cacheKey = `flood:${snap.s},${snap.w},${snap.n},${snap.e}`;
  const cached = cacheGet(cacheKey, FLOOD_TTL);
  if (cached) { stats.hits++; log('/flood-zones', true, 0); return res.json(JSON.parse(cached.data)); }
  stats.misses++;
  const t0 = Date.now();
  try {
    const baseUrl = 'https://hazards.fema.gov/gis/nfhl/rest/services/public/NFHL/MapServer/28/query';
    const envelope = `${snap.w},${snap.s},${snap.e},${snap.n}`;
    const allResults = [];
    let offset = 0;
    const pageSize = 2000;
    while (true) {
      const params = new URLSearchParams({
        where: "SFHA_TF='T'",
        geometry: envelope,
        geometryType: 'esriGeometryEnvelope',
        spatialRel: 'esriSpatialRelIntersects',
        outFields: 'FLD_ZONE,ZONE_SUBTY,STATIC_BFE',
        f: 'geojson',
        resultOffset: String(offset),
        resultRecordCount: String(pageSize),
        inSR: '4326',
        outSR: '4326',
      });
      const resp = await fetch(`${baseUrl}?${params}`, { signal: AbortSignal.timeout(30000) });
      if (!resp.ok) return res.status(502).json({ error: `FEMA returned ${resp.status}` });
      const geoJson = await resp.json();
      const features = geoJson.features || [];
      for (const f of features) {
        if (!f.geometry || f.geometry.type !== 'Polygon') continue;
        allResults.push({
          id: `flood_${offset + allResults.length}`,
          zoneCode: f.properties?.FLD_ZONE || '',
          zoneSubtype: f.properties?.ZONE_SUBTY || '',
          bfe: f.properties?.STATIC_BFE || null,
          rings: f.geometry.coordinates,
        });
      }
      if (features.length < pageSize) break;
      offset += pageSize;
    }
    cacheSet(cacheKey, JSON.stringify(allResults), { 'content-type': 'application/json' });
    log('/flood-zones', false, Date.now() - t0, `${allResults.length} flood zones`);
    res.json(allResults);
  } catch (err) {
    console.error(`[flood-zones error] ${err.message}`);
    res.status(502).json({ error: 'Flood zone fetch failed', detail: err.message });
  }
});

// ── Railroad Crossings (Overpass — railway=level_crossing) ───────────────────

const CROSSING_TTL = 7 * 24 * 60 * 60 * 1000;  // 7 days

app.get('/crossings', async (req, res) => {
  const bbox = req.query.bbox;
  if (!bbox) return res.status(400).json({ error: 'bbox required (s,w,n,e)' });
  const parts = bbox.split(',').map(Number);
  if (parts.length !== 4 || parts.some(isNaN)) return res.status(400).json({ error: 'Invalid bbox' });
  const snap = snapBbox(parts[0], parts[1], parts[2], parts[3]);
  const cacheKey = `crossings:${snap.s},${snap.w},${snap.n},${snap.e}`;
  const cached = cacheGet(cacheKey, CROSSING_TTL);
  if (cached) { stats.hits++; log('/crossings', true, 0); return res.json(JSON.parse(cached.data)); }
  stats.misses++;
  const t0 = Date.now();
  try {
    const query = `[out:json][timeout:30];(node["railway"="level_crossing"](${snap.s},${snap.w},${snap.n},${snap.e});node["railway"="crossing"](${snap.s},${snap.w},${snap.n},${snap.e}););out body;`;
    const resp = await fetch('https://overpass-api.de/api/interpreter', {
      method: 'POST', body: 'data=' + encodeURIComponent(query),
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      signal: AbortSignal.timeout(30000)
    });
    if (!resp.ok) return res.status(502).json({ error: `Overpass returned ${resp.status}` });
    const data = await resp.json();
    const results = (data.elements || []).map(el => ({
      id: `crossing_${el.id}`,
      lat: el.lat,
      lon: el.lon,
      crossingId: String(el.id),
      railroad: el.tags?.operator || el.tags?.['network'] || '',
      street: el.tags?.name || el.tags?.['addr:street'] || '',
      warningDevices: el.tags?.['crossing:barrier'] || el.tags?.['crossing:bell'] ? 'gates/bells' : el.tags?.['crossing:light'] ? 'lights' : '',
      crossingType: el.tags?.railway || 'level_crossing',
    }));
    cacheSet(cacheKey, JSON.stringify(results), { 'content-type': 'application/json' });
    log('/crossings', false, Date.now() - t0, `${results.length} crossings`);
    res.json(results);
  } catch (err) {
    console.error(`[crossings error] ${err.message}`);
    res.status(502).json({ error: 'Crossing fetch failed', detail: err.message });
  }
});

// ── Admin endpoints ─────────────────────────────────────────────────────────

app.get('/cache/stats', (req, res) => {
  const memUsage = process.memoryUsage();
  const now = Date.now();
  const cutoff24h = now - 24 * 60 * 60 * 1000;
  const recentRequests = openskyRateState.requests.filter(t => t > cutoff24h);
  res.json({
    entries: cache.size,
    radiusHints: radiusHints.size,
    pois: poiCache.size,
    overpassQueue: overpassQueue.length,
    hits: stats.hits,
    misses: stats.misses,
    hitRate: stats.hits + stats.misses > 0
      ? ((stats.hits / (stats.hits + stats.misses)) * 100).toFixed(1) + '%'
      : 'N/A',
    memoryMB: (memUsage.heapUsed / 1024 / 1024).toFixed(1),
    opensky: {
      requestsLast24h: recentRequests.length,
      dailyLimit: OPENSKY_EFFECTIVE_LIMIT,
      remaining: OPENSKY_EFFECTIVE_LIMIT - recentRequests.length,
      minIntervalMs: OPENSKY_MIN_INTERVAL_MS,
      backoffUntil: openskyRateState.backoffUntil > now ? new Date(openskyRateState.backoffUntil).toISOString() : null,
      backoffSeconds: openskyRateState.consecutive429s > 0 ? openskyRateState.backoffSeconds : 0,
      consecutive429s: openskyRateState.consecutive429s,
      authenticated: openskyConfigured,
    },
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

// ── Geofence Database Catalog & Download ────────────────────────────────────

app.get('/geofences/catalog', (req, res) => {
  const catalogPath = path.join(__dirname, 'geofence-databases', 'catalog.json');
  try {
    if (!fs.existsSync(catalogPath)) {
      return res.json([]);
    }
    const catalog = JSON.parse(fs.readFileSync(catalogPath, 'utf-8'));
    // Enrich with actual file sizes
    const enriched = catalog.map(entry => {
      const dbPath = path.join(__dirname, 'geofence-databases', `${entry.id}.db`);
      let fileSize = entry.fileSize || 0;
      try {
        const stat = fs.statSync(dbPath);
        fileSize = stat.size;
      } catch (_) {}
      return { ...entry, fileSize };
    });
    log('/geofences/catalog', false, 0, `${enriched.length} databases`);
    res.json(enriched);
  } catch (e) {
    console.error('Catalog error:', e.message);
    res.status(500).json({ error: e.message });
  }
});

app.get('/geofences/database/:id/download', (req, res) => {
  const { id } = req.params;
  // Sanitize: only allow alphanumeric, hyphens, underscores
  if (!/^[a-zA-Z0-9_-]+$/.test(id)) {
    return res.status(400).json({ error: 'Invalid database ID' });
  }
  const dbPath = path.join(__dirname, 'geofence-databases', `${id}.db`);
  if (!fs.existsSync(dbPath)) {
    return res.status(404).json({ error: `Database not found: ${id}` });
  }
  const stat = fs.statSync(dbPath);
  res.setHeader('Content-Type', 'application/x-sqlite3');
  res.setHeader('Content-Disposition', `attachment; filename="${id}.db"`);
  res.setHeader('Content-Length', stat.size);
  log(`/geofences/database/${id}/download`, false, 0, `${stat.size} bytes`);
  fs.createReadStream(dbPath).pipe(res);
});

// ── Start ───────────────────────────────────────────────────────────────────

app.listen(PORT, '0.0.0.0', () => {
  console.log(`Cache proxy listening on http://0.0.0.0:${PORT}`);
  console.log('Routes: POST /overpass, GET /earthquakes, GET /nws-alerts, GET /metar, GET /aircraft, GET /webcams, GET /weather, GET /tfrs');
  console.log('Zones:  GET /cameras, GET /schools, GET /flood-zones, GET /crossings');
  console.log('GeoDb:  GET /geofences/catalog, GET /geofences/database/:id/download');
  console.log('Radius: GET /radius-hint, POST /radius-hint, GET /radius-hints');
  console.log('POIs:   GET /pois/stats, GET /pois/export, GET /pois/bbox, GET /poi/:type/:id');
  console.log('DB:     GET /db/pois/search, /db/pois/nearby, /db/poi/:type/:id, /db/pois/stats, /db/pois/categories, /db/pois/coverage');
  console.log(`        PostgreSQL: ${pgPool ? 'connected' : 'not configured (set DATABASE_URL)'}`);
  console.log(`        OpenSky:    ${openskyConfigured ? 'OAuth2 configured' : 'anonymous — set OPENSKY_CLIENT_ID + OPENSKY_CLIENT_SECRET'}`);
  console.log(`        OpenSky:    rate limiter active — ${OPENSKY_EFFECTIVE_LIMIT} req/day (${OPENSKY_DAILY_LIMIT} limit × ${OPENSKY_SAFETY_MARGIN} safety), min interval ${OPENSKY_MIN_INTERVAL_MS}ms`);
  console.log('Admin:  GET /cache/stats, POST /cache/clear');
});
