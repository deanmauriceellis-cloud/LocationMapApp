/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module overpass.js';

module.exports = function(app, deps) {
  const { cacheGet, cacheSet, stats, log, poiCache, contentHashes, cacheIndividualPois, computeElementHash,
          checkCoverage, markScanned, collectPoisInRadius } = deps;

  const OVERPASS_TTL = 365 * 24 * 60 * 60 * 1000;  // 365 days
  const OVERPASS_MIN_INTERVAL_MS = 10_000;  // 10s between upstream requests

  const OVERPASS_ENDPOINTS = [
    'https://overpass-api.de/api/interpreter',
    'https://lz4.overpass-api.de/api/interpreter',
    'https://z.overpass-api.de/api/interpreter',
  ];
  const MAX_RETRIES = 3;  // total attempts = MAX_RETRIES + 1
  const RETRY_BACKOFFS_MS = [15_000, 30_000, 60_000];

  function detectOverpassError(status, contentType, body) {
    if (status === 429) return `rate-limited (429)`;
    if (status >= 500) return `server error (${status})`;
    const ct = (contentType || '').toLowerCase();
    if (ct.includes('text/html')) return 'content-type is text/html';
    const trimmed = (body || '').trimStart().substring(0, 200).toLowerCase();
    if (trimmed.startsWith('<html') || trimmed.startsWith('<!doctype')) return 'body starts with HTML';
    if (trimmed.includes('dispatcher_client')) return 'Overpass dispatcher error';
    return null;
  }

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

      let lastError = null;
      for (let attempt = 0; attempt <= MAX_RETRIES; attempt++) {
        const endpointIdx = attempt % OVERPASS_ENDPOINTS.length;
        const endpoint = OVERPASS_ENDPOINTS[endpointIdx];

        // Respect throttle on each attempt
        if (attempt > 0) {
          const backoff = RETRY_BACKOFFS_MS[attempt - 1] || 60_000;
          console.log(`[Overpass retry] attempt ${attempt + 1}/${MAX_RETRIES + 1} using ${endpoint} after ${(backoff / 1000).toFixed(0)}s backoff`);
          await new Promise(r => setTimeout(r, backoff));
        }

        // Re-check throttle before each attempt
        const elapsedSinceLastUpstream = Date.now() - overpassLastUpstream;
        if (elapsedSinceLastUpstream < OVERPASS_MIN_INTERVAL_MS) {
          const wait = OVERPASS_MIN_INTERVAL_MS - elapsedSinceLastUpstream;
          await new Promise(r => setTimeout(r, wait));
        }

        try {
          const t0 = Date.now();
          overpassLastUpstream = t0;
          const upstream = await fetch(endpoint, {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `data=${encodeURIComponent(dataField)}`,
          });
          const elapsedMs = Date.now() - t0;
          const body = await upstream.text();
          const contentType = upstream.headers.get('content-type') || 'application/json';

          // Check for Overpass errors (504, HTML responses, dispatcher errors)
          const errorReason = detectOverpassError(upstream.status, contentType, body);
          if (errorReason) {
            lastError = errorReason;
            console.warn(`[Overpass retry] attempt ${attempt + 1} failed: ${errorReason} (endpoint=${endpoint}, ${elapsedMs}ms)`);
            if (attempt < MAX_RETRIES) continue;
            // Exhausted all retries
            console.error(`[Overpass retry] all ${MAX_RETRIES + 1} attempts failed — last error: ${errorReason}`);
            resolve({ hit: false, error: true, message: `Overpass failed after ${MAX_RETRIES + 1} attempts: ${errorReason}` });
            break;
          }

          log('/overpass', false, elapsedMs);
          if (attempt > 0) {
            console.log(`[Overpass retry] succeeded on attempt ${attempt + 1} using ${endpoint}`);
          }

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

          // Mark scan cells as covered after successful upstream
          if (markScanned) {
            const aroundMatch2 = dataField.match(/around:(\d+),([-\d.]+),([-\d.]+)/);
            if (aroundMatch2) {
              const rMark = parseInt(aroundMatch2[1]);
              const latMark = parseFloat(aroundMatch2[2]);
              const lonMark = parseFloat(aroundMatch2[3]);
              const totalPois = poiStats.added + poiStats.updated;
              markScanned(latMark, lonMark, rMark, totalPois);
            }
          }

          resolve({ hit: false, status: upstream.status, data: body, contentType, poiNew: poiStats.added, poiKnown: poiStats.updated });
          break;
        } catch (err) {
          lastError = err.message;
          console.error(`[Overpass retry] attempt ${attempt + 1} network error: ${err.message} (endpoint=${endpoint})`);
          if (attempt >= MAX_RETRIES) {
            console.error(`[Overpass retry] all ${MAX_RETRIES + 1} attempts failed — last error: ${err.message}`);
            resolve({ hit: false, error: true, message: `Overpass failed after ${MAX_RETRIES + 1} attempts: ${err.message}` });
          }
        }
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

    // Determine coverage status for this request's location
    let coverageStatus = 'EMPTY';
    const aroundForCoverage = dataField.match(/around:(\d+),([-\d.]+),([-\d.]+)/);
    if (checkCoverage && aroundForCoverage) {
      coverageStatus = checkCoverage(parseFloat(aroundForCoverage[2]), parseFloat(aroundForCoverage[3])).coverage;
    }

    if (cacheKey) {
      const cached = cacheGet(cacheKey, OVERPASS_TTL);
      if (cached) {
        stats.hits++;
        log('/overpass', true);
        res.set('Content-Type', cached.headers['content-type'] || 'application/json');
        res.set('X-Cache', 'HIT');
        res.set('X-Coverage', coverageStatus);
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
          res.set('X-Coverage', coverageStatus);
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

    // ── Scan cell coverage check — skip upstream if area recently scanned ──
    if (checkCoverage) {
      const aroundMatch = dataField.match(/around:(\d+),([-\d.]+),([-\d.]+)/);
      if (aroundMatch) {
        const radius = parseInt(aroundMatch[1]);
        const lat = parseFloat(aroundMatch[2]);
        const lon = parseFloat(aroundMatch[3]);
        const coverage = checkCoverage(lat, lon);
        if (coverage.fresh) {
          const elements = collectPoisInRadius(lat, lon, radius);
          if (elements.length > 0) {
            stats.hits++;
            log('/overpass (scan-cell)', true, 0, `FRESH — ${elements.length} POIs from poiCache`);
            res.set('Content-Type', 'application/json');
            res.set('X-Cache', 'CELL');
            res.set('X-Coverage', 'FRESH');
            return res.json({ elements });
          }
        }
      }
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

    if (result.cancelled) {
      return res.status(499).json({ error: 'Cancelled', detail: result.message });
    }

    if (result.error) {
      return res.status(502).json({ error: 'Upstream request failed', detail: result.message });
    }
    res.status(result.status || 200)
      .set('Content-Type', result.contentType)
      .set('X-Cache', result.hit ? 'HIT' : 'MISS')
      .set('X-Coverage', coverageStatus)
      .set('X-POI-New', String(result.poiNew || 0))
      .set('X-POI-Known', String(result.poiKnown || 0))
      .send(result.data);
  });

  // Cancel all queued requests for a specific client
  app.post('/overpass/cancel', (req, res) => {
    const clientId = req.headers['x-client-id'] || req.body.clientId;
    if (!clientId) return res.status(400).json({ error: 'Missing X-Client-ID header or clientId body' });

    let cancelled = 0;
    for (let i = overpassQueue.length - 1; i >= 0; i--) {
      if (overpassQueue[i].clientId === clientId) {
        const item = overpassQueue.splice(i, 1)[0];
        item.resolve({ hit: false, error: true, cancelled: true, message: 'Cancelled by client' });
        cancelled++;
      }
    }
    console.log(`[Overpass queue] client ${clientId} cancelled ${cancelled} queued requests (${overpassQueue.length} remaining)`);
    res.json({ cancelled, remaining: overpassQueue.length });
  });

  // Expose queue length for admin
  return {
    getOverpassQueueLength: () => overpassQueue.length,
  };
};
