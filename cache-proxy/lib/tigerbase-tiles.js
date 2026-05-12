/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Destructive AI Gurus, LLC. All rights reserved.
 *
 * TigerBase tile endpoint (S252, post-V1 lawyer-gated).
 *
 * Serves baked TIGER/Line raster tiles out of per-layer PMTiles archives at
 * tools/tigerbase/out/<scope>.<layer>.pmtiles. Layered architecture: water,
 * boundaries, roads, places, labels each served independently and composited
 * by the client.
 *
 * Route:
 *   GET /tigerbase/:layer/:z/:x/:y       → 200 image/webp | 404
 *   GET /tigerbase/manifest              → JSON list of available scopes + layers
 *
 * V1 ship posture: this route is registered but never called by the Android
 * app — V1_OFFLINE_ONLY + manifest INTERNET strip + SuperAdmin gate. The web
 * admin can use it for preview; the Android app gets it post-V1 only.
 */
const MODULE_ID = '(C) Destructive AI Gurus, LLC, 2026 - Module tigerbase-tiles.js';

const fs = require('fs');
const path = require('path');

const TIGERBASE_OUT = path.resolve(__dirname, '../../tools/tigerbase/out');
const KNOWN_LAYERS = ['water', 'boundaries', 'roads', 'places', 'labels'];

// Minimal Node fs-based Source for the `pmtiles` npm package. The packaged
// FileSource targets the browser File API; we read offsets via fs.read.
class NodeFileSource {
  constructor(filePath) {
    this.filePath = filePath;
    this._fd = null;
  }
  getKey() { return this.filePath; }
  _open() {
    if (this._fd === null) this._fd = fs.openSync(this.filePath, 'r');
    return this._fd;
  }
  async getBytes(offset, length /*, signal, etag */) {
    const fd = this._open();
    const buf = Buffer.alloc(length);
    let read = 0;
    while (read < length) {
      const n = fs.readSync(fd, buf, read, length - read, offset + read);
      if (n === 0) break;
      read += n;
    }
    const ab = buf.buffer.slice(buf.byteOffset, buf.byteOffset + read);
    return { data: ab };
  }
  close() {
    if (this._fd !== null) { try { fs.closeSync(this._fd); } catch (_) {} this._fd = null; }
  }
}

// Per-archive cached PMTiles handles. Lazy-loaded since pmtiles is ESM-only.
const archiveCache = new Map(); // key="<scope>:<layer>" → PMTiles instance
let pmtilesModule = null;

async function getPmtilesModule() {
  if (!pmtilesModule) {
    pmtilesModule = await import('pmtiles');
  }
  return pmtilesModule;
}

function archivePath(scope, layer) {
  return path.join(TIGERBASE_OUT, `${scope}.${layer}.pmtiles`);
}

async function openArchive(scope, layer) {
  const cacheKey = `${scope}:${layer}`;
  if (archiveCache.has(cacheKey)) return archiveCache.get(cacheKey);
  const p = archivePath(scope, layer);
  if (!fs.existsSync(p)) return null;
  const { PMTiles } = await getPmtilesModule();
  const src = new NodeFileSource(p);
  const archive = new PMTiles(src);
  archiveCache.set(cacheKey, archive);
  return archive;
}

function listScopes() {
  if (!fs.existsSync(TIGERBASE_OUT)) return [];
  const scopes = new Map(); // scope → {layers: Set, sources: Set('pmtiles'|'tree')}

  // 1) PMTiles archives (preferred for distribution).
  for (const f of fs.readdirSync(TIGERBASE_OUT).filter(f => f.endsWith('.pmtiles'))) {
    const m = f.match(/^(.+)\.([^.]+)\.pmtiles$/);
    if (!m) continue;
    const [, scope, layer] = m;
    if (!KNOWN_LAYERS.includes(layer)) continue;
    if (!scopes.has(scope)) scopes.set(scope, { layers: new Set(), source: 'pmtiles' });
    scopes.get(scope).layers.add(layer);
  }

  // 2) Loose tile trees at <scope>/<layer>/{z}/{x}/{y}.webp (lets us serve in-flight bakes).
  for (const entry of fs.readdirSync(TIGERBASE_OUT, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const scope = entry.name;
    const scopeDir = path.join(TIGERBASE_OUT, scope);
    for (const layer of KNOWN_LAYERS) {
      const layerDir = path.join(scopeDir, layer);
      if (!fs.existsSync(layerDir)) continue;
      if (!scopes.has(scope)) scopes.set(scope, { layers: new Set(), source: 'tree' });
      scopes.get(scope).layers.add(layer);
      // If the scope already exists as a pmtiles entry, leave source='pmtiles'; otherwise mark 'tree'.
      // (When both forms exist, pmtiles wins for serving.)
    }
  }

  return Array.from(scopes.entries()).map(([scope, info]) => ({
    scope, layers: Array.from(info.layers).sort(), source: info.source,
  }));
}

// Cheap scan of a loose tree to discover (minZoom, maxZoom, bbox-of-z0-coverage).
// Bbox in this mode is approximated from min/max tile coords at the lowest zoom present.
function describeTreeLayer(scope, layer) {
  const dir = path.join(TIGERBASE_OUT, scope, layer);
  if (!fs.existsSync(dir)) return null;
  let minZoom = Infinity, maxZoom = -Infinity;
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  let zForBbox = Infinity;
  for (const z of fs.readdirSync(dir)) {
    if (!/^\d+$/.test(z)) continue;
    const zi = parseInt(z, 10);
    minZoom = Math.min(minZoom, zi);
    maxZoom = Math.max(maxZoom, zi);
    // Use the smallest available zoom for bbox approximation
    if (zi < zForBbox) {
      zForBbox = zi;
      minX = Infinity; maxX = -Infinity; minY = Infinity; maxY = -Infinity;
      const zdir = path.join(dir, z);
      for (const x of fs.readdirSync(zdir)) {
        if (!/^\d+$/.test(x)) continue;
        const xi = parseInt(x, 10);
        minX = Math.min(minX, xi); maxX = Math.max(maxX, xi);
        for (const y of fs.readdirSync(path.join(zdir, x))) {
          const m = y.match(/^(\d+)\.webp$/);
          if (!m) continue;
          const yi = parseInt(m[1], 10);
          minY = Math.min(minY, yi); maxY = Math.max(maxY, yi);
        }
      }
    }
  }
  if (!Number.isFinite(minZoom)) return null;
  // Convert tile (z, x, y) → lon/lat. (256-tile slippy-map; web mercator.)
  const n = Math.pow(2, zForBbox);
  const tileToLon = (xx) => xx / n * 360 - 180;
  const tileToLat = (yy) => {
    const yt = Math.PI - 2 * Math.PI * yy / n;
    return Math.atan(Math.sinh(yt)) * 180 / Math.PI;
  };
  const bbox = [tileToLon(minX), tileToLat(maxY + 1), tileToLon(maxX + 1), tileToLat(minY)];
  return { minZoom, maxZoom, bbox };
}

function looseTilePath(scope, layer, z, x, y) {
  return path.join(TIGERBASE_OUT, scope, layer, String(z), String(x), `${y}.webp`);
}

module.exports = function(app /*, deps */) {
  // Manifest — which scopes + layers are baked
  app.get('/tigerbase/manifest', async (req, res) => {
    const scopes = listScopes();
    if (!scopes.length) {
      return res.status(503).json({
        error: 'no_tigerbase_bakes',
        message: 'No PMTiles archives found at tools/tigerbase/out/. Bake first.',
      });
    }
    // Pull headers for bounds + zoom range
    const detail = [];
    for (const { scope, layers, source } of scopes) {
      const entry = { scope, source, layers: {} };
      for (const layer of layers) {
        if (source === 'pmtiles') {
          try {
            const archive = await openArchive(scope, layer);
            if (!archive) continue;
            const h = await archive.getHeader();
            entry.layers[layer] = {
              minZoom: h.minZoom, maxZoom: h.maxZoom,
              bbox: [h.minLon, h.minLat, h.maxLon, h.maxLat],
            };
          } catch (err) {
            entry.layers[layer] = { error: err.message };
          }
        } else {
          // Loose tree
          const meta = describeTreeLayer(scope, layer);
          if (meta) entry.layers[layer] = meta;
          else entry.layers[layer] = { error: 'no tiles found' };
        }
      }
      detail.push(entry);
    }
    res.json({ tigerbase_out: TIGERBASE_OUT, scopes: detail });
  });

  // Tile fetch. Path uses :layer first so route doesn't collide with admin/tiles.
  app.get('/tigerbase/:layer/:z/:x/:y', async (req, res) => {
    const { layer } = req.params;
    if (!KNOWN_LAYERS.includes(layer)) {
      return res.status(404).json({ error: 'unknown_layer', layer });
    }
    const z = parseInt(req.params.z, 10);
    const x = parseInt(req.params.x, 10);
    const y = parseInt(req.params.y.replace(/\.webp$/, ''), 10);
    if (!Number.isFinite(z) || !Number.isFinite(x) || !Number.isFinite(y)) {
      return res.status(400).json({ error: 'bad_zxy' });
    }
    if (z < 0 || z > 25 || x < 0 || y < 0 || x >= (1 << z) || y >= (1 << z)) {
      return res.status(400).json({ error: 'out_of_range_zxy' });
    }

    // Scope selection: explicit ?scope= query, or default to the first scope that has the layer.
    let scope = req.query.scope;
    if (!scope) {
      const scopes = listScopes();
      const first = scopes.find(s => s.layers.includes(layer));
      if (!first) return res.status(503).json({ error: 'no_bake_for_layer', layer });
      scope = first.scope;
    }

    // 1. PMTiles archive (preferred — distribution-ready). If present, use it.
    let archive = null;
    try { archive = await openArchive(scope, layer); }
    catch (err) { return res.status(500).json({ error: 'archive_open_failed', message: err.message }); }

    if (archive) {
      try {
        const tile = await archive.getZxy(z, x, y);
        if (!tile || !tile.data) return res.status(404).end();
        res.set('Content-Type', 'image/webp');
        res.set('Cache-Control', 'public, max-age=86400');
        return res.send(Buffer.from(tile.data));
      } catch (err) {
        return res.status(500).json({ error: 'tile_fetch_failed', message: err.message });
      }
    }

    // 2. Loose tile tree fallback (live bake-in-progress, no pack yet).
    const loosePath = looseTilePath(scope, layer, z, x, y);
    if (fs.existsSync(loosePath)) {
      res.set('Content-Type', 'image/webp');
      res.set('Cache-Control', 'public, max-age=3600');
      return res.sendFile(loosePath);
    }

    return res.status(404).end();
  });
};
