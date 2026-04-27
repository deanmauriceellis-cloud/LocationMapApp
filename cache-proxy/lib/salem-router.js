/*
 * LocationMapApp v1.5 — Salem walking router (web side)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * JS port of core/.../routing/RoutingBundle.kt + Router.kt. Reads the same
 * `salem-routing-graph.sqlite` bundle the APK ships in app-salem assets, so
 * web and Android produce identical routes for identical inputs.
 *
 * Endpoints:
 *   GET  /api/salem/route?from_lat&from_lng&to_lat&to_lng[&source=live|bundle]
 *   POST /api/salem/route-multi
 *        { stops: [{lat,lng}, ...] [, source: "live"|"bundle"] }
 *
 * source=live falls through to TigerLine's tiger.route_walking() and logs a
 * divergence warning if the bundled result differs by >5%.
 *
 * Bundle schema is owned by tools/routing-bake/SCHEMA.md. If you change the
 * schema there, update both this loader and core/.../RoutingBundleLoader.kt.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module salem-router.js';

const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');
const { Pool } = require('pg');

const BUNDLE_PATH = path.resolve(
  __dirname,
  '../../app-salem/src/main/assets/routing/salem-routing-graph.sqlite',
);
const EXPECTED_SCHEMA_VERSION = 1;
const PARITY_DIVERGENCE_PCT = 5.0; // log warning when bundle vs live differ by >5%

// ── Bundle loader ───────────────────────────────────────────────────────────

function parsePolyline(src) {
  if (!src) return new Float64Array(0);
  // "lat,lng;lat,lng;..." → packed Float64 [lat0,lng0,lat1,lng1,...]
  const parts = src.split(';');
  const out = new Float64Array(parts.length * 2);
  for (let i = 0; i < parts.length; i++) {
    const pair = parts[i];
    const comma = pair.indexOf(',');
    out[i * 2] = +pair.substring(0, comma);
    out[i * 2 + 1] = +pair.substring(comma + 1);
  }
  return out;
}

function loadBundle(filePath) {
  if (!fs.existsSync(filePath)) {
    throw new Error(`routing bundle not found: ${filePath}`);
  }
  const db = new Database(filePath, { readonly: true, fileMustExist: true });
  try {
    db.pragma('query_only = ON');

    const meta = {};
    for (const row of db.prepare('SELECT key, value FROM meta').iterate()) {
      meta[row.key] = row.value;
    }
    const schemaVersion = parseInt(meta.schema_version, 10) || 0;
    if (schemaVersion !== EXPECTED_SCHEMA_VERSION) {
      throw new Error(
        `Unsupported routing bundle schema version ${schemaVersion} (expected ${EXPECTED_SCHEMA_VERSION})`,
      );
    }
    const nodeCount = parseInt(meta.node_count, 10) || 0;
    const edgeCount = parseInt(meta.edge_count, 10) || 0;
    if (nodeCount <= 0 || edgeCount <= 0) {
      throw new Error('Routing bundle reports zero nodes/edges (corrupt?)');
    }

    // Nodes — order by id, mirror Kotlin's loader exactly.
    const nodeIds = new Float64Array(nodeCount);   // node ids fit in 32-bit safely but Float64 is simpler for the idToIdx map key path
    const nodeLat = new Float64Array(nodeCount);
    const nodeLng = new Float64Array(nodeCount);
    const nodeWalkable = new Uint8Array(nodeCount);
    const idToIdx = new Map();
    {
      let i = 0;
      for (const r of db.prepare('SELECT id, lat, lng, walkable FROM nodes ORDER BY id').iterate()) {
        nodeIds[i] = r.id;
        nodeLat[i] = r.lat;
        nodeLng[i] = r.lng;
        nodeWalkable[i] = r.walkable ? 1 : 0;
        idToIdx.set(r.id, i);
        i++;
      }
      if (i !== nodeCount) throw new Error(`node row count ${i} != meta.node_count ${nodeCount}`);
    }

    // Edges
    const edgeId = new Float64Array(edgeCount);
    const srcIdx = new Int32Array(edgeCount);
    const tgtIdx = new Int32Array(edgeCount);
    const edgeLengthM = new Float64Array(edgeCount);
    const edgeFullname = new Array(edgeCount);
    const edgeMtfcc = new Array(edgeCount);
    const edgePolylines = new Array(edgeCount);
    {
      let i = 0;
      for (const r of db
        .prepare('SELECT id, source, target, length_m, mtfcc, fullname, geom_polyline FROM edges')
        .iterate()) {
        const s = idToIdx.get(r.source);
        const t = idToIdx.get(r.target);
        if (s === undefined || t === undefined) {
          throw new Error(`edge ${r.id} references unknown node (source=${r.source} target=${r.target})`);
        }
        edgeId[i] = r.id;
        srcIdx[i] = s;
        tgtIdx[i] = t;
        edgeLengthM[i] = r.length_m;
        edgeMtfcc[i] = r.mtfcc; // null preserved
        edgeFullname[i] = r.fullname || '';
        edgePolylines[i] = parsePolyline(r.geom_polyline || '');
        i++;
      }
      if (i !== edgeCount) throw new Error(`edge row count ${i} != meta.edge_count ${edgeCount}`);
    }

    // Build CSR adjacency (mirrors RoutingBundle.build).
    const degree = new Int32Array(nodeCount);
    for (let i = 0; i < edgeCount; i++) {
      degree[srcIdx[i]]++;
      degree[tgtIdx[i]]++;
    }
    const edgeOffset = new Int32Array(nodeCount + 1);
    for (let i = 0; i < nodeCount; i++) edgeOffset[i + 1] = edgeOffset[i] + degree[i];

    const total = edgeOffset[nodeCount];
    const adjNeighbor = new Int32Array(total);
    const adjEdgeIdx = new Int32Array(total);
    const adjReversed = new Uint8Array(total);
    const cursor = new Int32Array(nodeCount);
    for (let i = 0; i < edgeCount; i++) {
      const s = srcIdx[i], t = tgtIdx[i];
      let k = edgeOffset[s] + cursor[s]; cursor[s]++;
      adjNeighbor[k] = t; adjEdgeIdx[k] = i; adjReversed[k] = 0;
      k = edgeOffset[t] + cursor[t]; cursor[t]++;
      adjNeighbor[k] = s; adjEdgeIdx[k] = i; adjReversed[k] = 1;
    }

    // Spatial grid over walkable nodes (matches NodeGrid in Kotlin).
    const cellLat = 0.0015;
    const cellLng = 0.0020;
    let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity;
    for (let i = 0; i < nodeCount; i++) {
      if (!nodeWalkable[i]) continue;
      if (nodeLat[i] < minLat) minLat = nodeLat[i];
      if (nodeLat[i] > maxLat) maxLat = nodeLat[i];
      if (nodeLng[i] < minLng) minLng = nodeLng[i];
      if (nodeLng[i] > maxLng) maxLng = nodeLng[i];
    }
    const rows = Math.max(1, ((maxLat - minLat) / cellLat) | 0) + 1;
    const cols = Math.max(1, ((maxLng - minLng) / cellLng) | 0) + 1;
    const tally = new Int32Array(rows * cols);
    for (let i = 0; i < nodeCount; i++) {
      if (!nodeWalkable[i]) continue;
      const r = clamp(((nodeLat[i] - minLat) / cellLat) | 0, 0, rows - 1);
      const c = clamp(((nodeLng[i] - minLng) / cellLng) | 0, 0, cols - 1);
      tally[r * cols + c]++;
    }
    const cells = new Array(rows * cols);
    for (let k = 0; k < cells.length; k++) cells[k] = new Int32Array(tally[k]);
    const cur = new Int32Array(rows * cols);
    for (let i = 0; i < nodeCount; i++) {
      if (!nodeWalkable[i]) continue;
      const r = clamp(((nodeLat[i] - minLat) / cellLat) | 0, 0, rows - 1);
      const c = clamp(((nodeLng[i] - minLng) / cellLng) | 0, 0, cols - 1);
      const cellIdx = r * cols + c;
      cells[cellIdx][cur[cellIdx]++] = i;
    }

    return {
      nodeCount,
      edgeCount,
      nodeIds, nodeLat, nodeLng, nodeWalkable,
      edgeOffset, adjNeighbor, adjEdgeIdx, adjReversed,
      edgeId, edgeLengthM, edgeFullname, edgeMtfcc, edgePolylines,
      grid: { minLat, minLng, cellLat, cellLng, rows, cols, cells },
      meta,
      walkingPaceMps: parseFloat(meta.walking_pace_mps) || 1.4,
    };
  } finally {
    db.close();
  }
}

function clamp(v, lo, hi) { return v < lo ? lo : v > hi ? hi : v; }

// ── KNN snap (planar SRID-4269 degree distance — matches tiger.<-> semantic) ─

function nearestWalkableNode(b, lat, lng) {
  const g = b.grid;
  const centerRow = clamp(((lat - g.minLat) / g.cellLat) | 0, 0, g.rows - 1);
  const centerCol = clamp(((lng - g.minLng) / g.cellLng) | 0, 0, g.cols - 1);
  let best = -1;
  let bestD2 = Infinity;
  for (let ring = 0; ring <= 16; ring++) {
    const r0 = Math.max(0, centerRow - ring);
    const r1 = Math.min(g.rows - 1, centerRow + ring);
    const c0 = Math.max(0, centerCol - ring);
    const c1 = Math.min(g.cols - 1, centerCol + ring);
    for (let r = r0; r <= r1; r++) {
      for (let c = c0; c <= c1; c++) {
        if (ring > 0 && r !== r0 && r !== r1 && c !== c0 && c !== c1) continue;
        const cell = g.cells[r * g.cols + c];
        for (let j = 0; j < cell.length; j++) {
          const idx = cell[j];
          const dlat = b.nodeLat[idx] - lat;
          const dlng = b.nodeLng[idx] - lng;
          const d2 = dlat * dlat + dlng * dlng;
          if (d2 < bestD2) { bestD2 = d2; best = idx; }
        }
      }
    }
    if (best >= 0 && ring >= 1) return best;
  }
  return best >= 0 ? best : -1;
}

// ── Dijkstra (mirrors Router.kt) ────────────────────────────────────────────

class MinHeap {
  // Pairs of (distance, nodeIdx) packed into two parallel arrays for speed.
  constructor() { this.dist = []; this.idx = []; }
  get size() { return this.dist.length; }
  push(d, i) {
    this.dist.push(d); this.idx.push(i);
    let k = this.dist.length - 1;
    while (k > 0) {
      const parent = (k - 1) >> 1;
      if (this.dist[parent] <= this.dist[k]) break;
      this._swap(parent, k);
      k = parent;
    }
  }
  pop() {
    const n = this.dist.length;
    if (n === 0) return null;
    const d = this.dist[0], i = this.idx[0];
    const lastD = this.dist.pop();
    const lastI = this.idx.pop();
    if (n > 1) {
      this.dist[0] = lastD; this.idx[0] = lastI;
      let k = 0;
      const len = this.dist.length;
      while (true) {
        const l = 2 * k + 1, r = 2 * k + 2;
        let smallest = k;
        if (l < len && this.dist[l] < this.dist[smallest]) smallest = l;
        if (r < len && this.dist[r] < this.dist[smallest]) smallest = r;
        if (smallest === k) break;
        this._swap(smallest, k);
        k = smallest;
      }
    }
    return [d, i];
  }
  _swap(a, b) {
    const td = this.dist[a]; this.dist[a] = this.dist[b]; this.dist[b] = td;
    const ti = this.idx[a]; this.idx[a] = this.idx[b]; this.idx[b] = ti;
  }
}

function routeBetween(b, sIdx, tIdx) {
  if (sIdx === tIdx) return { geometry: [], distanceM: 0, durationS: 0, edges: [] };

  const n = b.nodeCount;
  const dist = new Float64Array(n);
  for (let i = 0; i < n; i++) dist[i] = Infinity;
  const prevNode = new Int32Array(n);
  const prevEdgeIdx = new Int32Array(n);
  const prevReversed = new Uint8Array(n);
  for (let i = 0; i < n; i++) { prevNode[i] = -1; prevEdgeIdx[i] = -1; }
  dist[sIdx] = 0;

  const heap = new MinHeap();
  heap.push(0, sIdx);
  while (heap.size > 0) {
    const top = heap.pop();
    const d = top[0], u = top[1];
    if (d > dist[u]) continue;
    if (u === tIdx) break;
    const start = b.edgeOffset[u];
    const end = b.edgeOffset[u + 1];
    for (let k = start; k < end; k++) {
      const v = b.adjNeighbor[k];
      const eIdx = b.adjEdgeIdx[k];
      const nd = d + b.edgeLengthM[eIdx];
      if (nd < dist[v]) {
        dist[v] = nd;
        prevNode[v] = u;
        prevEdgeIdx[v] = eIdx;
        prevReversed[v] = b.adjReversed[k];
        heap.push(nd, v);
      }
    }
  }

  if (prevNode[tIdx] === -1) return { geometry: [], distanceM: 0, durationS: 0, edges: [] };

  // Walk back, collect edges in travel order.
  const edgeStack = [];
  const reversedStack = [];
  let cur = tIdx;
  while (cur !== sIdx) {
    edgeStack.push(prevEdgeIdx[cur]);
    reversedStack.push(prevReversed[cur]);
    cur = prevNode[cur];
  }
  edgeStack.reverse();
  reversedStack.reverse();

  const steps = [];
  const geometry = [];
  let totalM = 0;
  for (let i = 0; i < edgeStack.length; i++) {
    const eIdx = edgeStack[i];
    const rev = reversedStack[i] === 1;
    const packed = b.edgePolylines[eIdx];
    const lengthM = b.edgeLengthM[eIdx];
    totalM += lengthM;

    const polyline = [];
    if (!rev) {
      for (let p = 0; p < packed.length; p += 2) polyline.push([packed[p], packed[p + 1]]);
    } else {
      for (let p = packed.length - 2; p >= 0; p -= 2) polyline.push([packed[p], packed[p + 1]]);
    }
    steps.push({
      edge_id: b.edgeId[eIdx],
      fullname: b.edgeFullname[eIdx],
      mtfcc: b.edgeMtfcc[eIdx],
      length_m: lengthM,
      polyline,
    });

    const skipFirst = i > 0;
    for (let j = 0; j < polyline.length; j++) {
      if (skipFirst && j === 0) continue;
      geometry.push(polyline[j]);
    }
  }
  return {
    geometry,
    distanceM: totalM,
    durationS: totalM / b.walkingPaceMps,
    edges: steps,
  };
}

function routeBundle(b, fromLat, fromLng, toLat, toLng) {
  const sIdx = nearestWalkableNode(b, fromLat, fromLng);
  const tIdx = nearestWalkableNode(b, toLat, toLng);
  if (sIdx < 0 || tIdx < 0) return null;
  return routeBetween(b, sIdx, tIdx);
}

// S190 — diagnostic-only helper for the admin tools layer. Returns the
// nearest-node info that routeBundle uses internally, plus haversine
// meter distances, so the admin tour-route logger can explain WHY a leg
// failed (same-node snap, snap too far from graph, isolated component).
//
// Does NOT route or alter routing behavior — pure introspection.
function _haversineMSimple(lat1, lng1, lat2, lng2) {
  if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return null;
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function routeBundleDiag(b, fromLat, fromLng, toLat, toLng) {
  const sIdx = nearestWalkableNode(b, fromLat, fromLng);
  const tIdx = nearestWalkableNode(b, toLat, toLng);
  const fromSnapM =
    sIdx >= 0
      ? _haversineMSimple(fromLat, fromLng, b.nodeLat[sIdx], b.nodeLng[sIdx])
      : null;
  const toSnapM =
    tIdx >= 0
      ? _haversineMSimple(toLat, toLng, b.nodeLat[tIdx], b.nodeLng[tIdx])
      : null;
  return {
    from_node_idx: sIdx,
    to_node_idx: tIdx,
    from_snap_m: fromSnapM == null ? null : Math.round(fromSnapM),
    to_snap_m: toSnapM == null ? null : Math.round(toSnapM),
    same_node: sIdx >= 0 && sIdx === tIdx,
    from_snapped_lat: sIdx >= 0 ? b.nodeLat[sIdx] : null,
    from_snapped_lng: sIdx >= 0 ? b.nodeLng[sIdx] : null,
    to_snapped_lat: tIdx >= 0 ? b.nodeLat[tIdx] : null,
    to_snapped_lng: tIdx >= 0 ? b.nodeLng[tIdx] : null,
  };
}

function routeMultiBundle(b, stops) {
  if (!Array.isArray(stops) || stops.length < 2) return null;
  const parts = [];
  for (let i = 0; i < stops.length - 1; i++) {
    const seg = routeBundle(b, stops[i].lat, stops[i].lng, stops[i + 1].lat, stops[i + 1].lng);
    if (!seg) return null;
    parts.push(seg);
  }
  // Concat (skip duplicate join points).
  const geometry = [];
  const edges = [];
  let totalM = 0;
  for (let i = 0; i < parts.length; i++) {
    const p = parts[i];
    edges.push(...p.edges);
    for (let j = 0; j < p.geometry.length; j++) {
      if (i > 0 && j === 0) continue;
      geometry.push(p.geometry[j]);
    }
    totalM += p.distanceM;
  }
  return { geometry, distanceM: totalM, durationS: totalM / b.walkingPaceMps, edges };
}

// ── Tiger live fallthrough ──────────────────────────────────────────────────

let _tigerPool = null;
function tigerPool() {
  if (_tigerPool) return _tigerPool;
  // Peer auth: Postgres connects as the OS user, no password. The cache-proxy
  // runs as the operator; tiger DB is local. If TIGER_DATABASE_URL is set
  // (e.g. on a deploy host), prefer it.
  // Peer auth requires the unix socket; without a host, node-postgres defaults
  // to TCP (which then asks for a password). PGHOST=/var/run/postgresql works
  // on Debian/Ubuntu installs of Postgres.
  const opts = process.env.TIGER_DATABASE_URL
    ? { connectionString: process.env.TIGER_DATABASE_URL, max: 2 }
    : { host: process.env.PGHOST || '/var/run/postgresql', database: 'tiger', max: 2 };
  _tigerPool = new Pool({ ...opts, connectionTimeoutMillis: 5000 });
  _tigerPool.on('error', (err) => console.error('[Salem Router] tiger pool error:', err.message));
  return _tigerPool;
}

async function routeLive(fromLat, fromLng, toLat, toLng) {
  const pool = tigerPool();
  const { rows } = await pool.query(
    'SELECT geojson, distance_m, duration_s FROM tiger.route_walking($1::float8, $2::float8, $3::float8, $4::float8)',
    [fromLat, fromLng, toLat, toLng],
  );
  if (!rows.length || rows[0].distance_m == null) return null;
  const r = rows[0];
  let geometry = [];
  if (r.geojson) {
    try {
      const gj = JSON.parse(r.geojson);
      // GeoJSON coords are [lng, lat]; flip to [lat, lng] to match bundle output.
      if (gj && gj.type === 'LineString' && Array.isArray(gj.coordinates)) {
        geometry = gj.coordinates.map((c) => [c[1], c[0]]);
      } else if (gj && gj.type === 'MultiLineString' && Array.isArray(gj.coordinates)) {
        // tiger.route_walking() returns a MultiLineString in graph order, NOT
        // travel order — segments may be reversed individually. Stitch them
        // by walking endpoints starting from the segment closest to (fromLat,
        // fromLng).
        geometry = stitchLiveMultiLineString(gj.coordinates, fromLat, fromLng);
      }
    } catch (e) {
      console.warn('[Salem Router] failed to parse live geojson:', e.message);
    }
  }
  return {
    geometry,
    distanceM: r.distance_m,
    durationS: r.duration_s,
    edges: [], // tiger.route_walking() doesn't break out per-edge steps
  };
}

/**
 * Reorder + reverse-as-needed the segments of a MultiLineString so they form
 * a single travelable polyline starting from the segment closest to
 * (fromLat, fromLng). Each segment in `coords` is an array of [lng, lat] pairs
 * (raw GeoJSON convention). Returns a flat array of [lat, lng] points.
 *
 * O(n²) on segment count; n is small (typical Salem route is <30 edges).
 */
function stitchLiveMultiLineString(coords, fromLat, fromLng) {
  if (!coords.length) return [];
  // Convert each segment to {start: [lat,lng], end: [lat,lng], pts: [[lat,lng],...]}.
  const segs = coords.map((seg) => {
    const pts = seg.map((c) => [c[1], c[0]]);
    return { pts, used: false };
  });

  function d2(a, b) {
    const dlat = a[0] - b[0]; const dlng = a[1] - b[1];
    return dlat * dlat + dlng * dlng;
  }
  const origin = [fromLat, fromLng];

  // Pick the starting segment + the end of that segment closest to the origin.
  let bestSeg = -1;
  let bestReversed = false;
  let bestD2 = Infinity;
  for (let i = 0; i < segs.length; i++) {
    const s = segs[i];
    const dStart = d2(s.pts[0], origin);
    const dEnd = d2(s.pts[s.pts.length - 1], origin);
    if (dStart < bestD2) { bestD2 = dStart; bestSeg = i; bestReversed = false; }
    if (dEnd < bestD2) { bestD2 = dEnd; bestSeg = i; bestReversed = true; }
  }

  const out = [];
  function appendSeg(seg, reversed) {
    const arr = reversed ? seg.pts.slice().reverse() : seg.pts;
    for (let i = 0; i < arr.length; i++) {
      if (out.length > 0 && i === 0) continue; // skip duplicate join
      out.push(arr[i]);
    }
    seg.used = true;
  }
  appendSeg(segs[bestSeg], bestReversed);

  // Walk endpoints. Match next segment whose start or end equals current tail.
  // Equality threshold: 1e-7° (~1 cm) — handles 6-decimal precision rounding.
  const EPS2 = 1e-14;
  while (true) {
    const tail = out[out.length - 1];
    let nextIdx = -1;
    let nextReversed = false;
    let nextD2 = Infinity;
    for (let i = 0; i < segs.length; i++) {
      if (segs[i].used) continue;
      const s = segs[i];
      const dStart = d2(s.pts[0], tail);
      const dEnd = d2(s.pts[s.pts.length - 1], tail);
      if (dStart < EPS2 && dStart < nextD2) { nextD2 = dStart; nextIdx = i; nextReversed = false; }
      if (dEnd < EPS2 && dEnd < nextD2) { nextD2 = dEnd; nextIdx = i; nextReversed = true; }
    }
    if (nextIdx < 0) {
      // No exact match; pick the geometrically closest unused segment to keep
      // going. This handles minor rounding gaps but should be rare.
      for (let i = 0; i < segs.length; i++) {
        if (segs[i].used) continue;
        const s = segs[i];
        const dStart = d2(s.pts[0], tail);
        const dEnd = d2(s.pts[s.pts.length - 1], tail);
        if (dStart < nextD2) { nextD2 = dStart; nextIdx = i; nextReversed = false; }
        if (dEnd < nextD2) { nextD2 = dEnd; nextIdx = i; nextReversed = true; }
      }
      if (nextIdx < 0) break; // all consumed
    }
    appendSeg(segs[nextIdx], nextReversed);
  }
  return out;
}

// ── Public module factory ───────────────────────────────────────────────────

module.exports = function (app, deps) {
  let bundle;
  try {
    const t0 = Date.now();
    bundle = loadBundle(BUNDLE_PATH);
    const ms = Date.now() - t0;
    console.log(
      `[Salem Router] loaded bundle ${path.basename(BUNDLE_PATH)} — ` +
      `${bundle.nodeCount} nodes / ${bundle.edgeCount} edges / pace ${bundle.walkingPaceMps} m/s ` +
      `(${ms} ms)`,
    );
  } catch (err) {
    console.error('[Salem Router] failed to load bundle:', err.message);
    // Register the endpoints anyway — they'll all 503 — so /api/salem/route
    // returns a clear error rather than 404.
    bundle = null;
  }

  function bundleGuard(req, res) {
    if (bundle) return true;
    res.status(503).json({ error: 'Routing bundle not loaded — see server logs at startup' });
    return false;
  }

  function badNumber(v) {
    return v === undefined || v === null || v === '' || Number.isNaN(+v);
  }

  function shapeBundleResult(r) {
    return {
      source: 'bundle',
      distance_m: r.distanceM,
      duration_s: r.durationS,
      pace_mps: bundle.walkingPaceMps,
      geometry: r.geometry,
      edges: r.edges,
    };
  }

  function shapeLiveResult(r) {
    return {
      source: 'live',
      distance_m: r.distanceM,
      duration_s: r.durationS,
      pace_mps: bundle ? bundle.walkingPaceMps : null,
      geometry: r.geometry,
      edges: r.edges,
    };
  }

  function logDivergence(label, bundleM, liveM) {
    if (bundleM == null || liveM == null || bundleM === 0) return;
    const pct = Math.abs(bundleM - liveM) / bundleM * 100;
    if (pct > PARITY_DIVERGENCE_PCT) {
      console.warn(
        `[Salem Router] DIVERGENCE ${label}: bundle=${bundleM.toFixed(2)}m ` +
        `live=${liveM.toFixed(2)}m (${pct.toFixed(2)}% > ${PARITY_DIVERGENCE_PCT}%)`,
      );
    }
  }

  // GET /api/salem/route?from_lat&from_lng&to_lat&to_lng[&source=live]
  app.get('/salem/route', async (req, res) => {
    if (!bundleGuard(req, res)) return;
    const { from_lat, from_lng, to_lat, to_lng } = req.query;
    if ([from_lat, from_lng, to_lat, to_lng].some(badNumber)) {
      return res.status(400).json({ error: 'from_lat, from_lng, to_lat, to_lng required' });
    }
    const fLat = +from_lat, fLng = +from_lng, tLat = +to_lat, tLng = +to_lng;
    const source = (req.query.source || 'bundle').toLowerCase();

    try {
      if (source === 'live') {
        const live = await routeLive(fLat, fLng, tLat, tLng);
        if (!live) return res.status(404).json({ error: 'No live route found' });
        // Cross-check against bundle for divergence logging.
        const b = routeBundle(bundle, fLat, fLng, tLat, tLng);
        if (b) logDivergence(`${fLat},${fLng}→${tLat},${tLng}`, b.distanceM, live.distanceM);
        return res.json(shapeLiveResult(live));
      }
      const r = routeBundle(bundle, fLat, fLng, tLat, tLng);
      if (!r) return res.status(404).json({ error: 'No bundled route found (KNN snap failed)' });
      return res.json(shapeBundleResult(r));
    } catch (err) {
      console.error('[Salem Router] /route error:', err.message);
      return res.status(500).json({ error: err.message });
    }
  });

  // POST /api/salem/route-multi { stops: [{lat,lng}, ...], source? }
  app.post('/salem/route-multi', async (req, res) => {
    if (!bundleGuard(req, res)) return;
    const stops = (req.body && req.body.stops) || [];
    if (!Array.isArray(stops) || stops.length < 2) {
      return res.status(400).json({ error: 'stops array with >= 2 entries required' });
    }
    for (const s of stops) {
      if (!s || badNumber(s.lat) || badNumber(s.lng)) {
        return res.status(400).json({ error: 'each stop must have numeric lat and lng' });
      }
    }
    const source = (req.body.source || 'bundle').toString().toLowerCase();

    try {
      if (source === 'live') {
        // Live multi-stop = pairwise live routes concatenated; live function
        // is single-pair only.
        const segs = [];
        for (let i = 0; i < stops.length - 1; i++) {
          const seg = await routeLive(stops[i].lat, stops[i].lng, stops[i + 1].lat, stops[i + 1].lng);
          if (!seg) return res.status(404).json({ error: `No live route for segment ${i}` });
          segs.push(seg);
        }
        const geometry = [];
        let totalM = 0;
        for (let i = 0; i < segs.length; i++) {
          const s = segs[i];
          for (let j = 0; j < s.geometry.length; j++) {
            if (i > 0 && j === 0) continue;
            geometry.push(s.geometry[j]);
          }
          totalM += s.distanceM;
        }
        return res.json(
          shapeLiveResult({ geometry, distanceM: totalM, durationS: totalM / 1.4, edges: [] }),
        );
      }
      const r = routeMultiBundle(bundle, stops);
      if (!r) return res.status(404).json({ error: 'No bundled multi-stop route' });
      return res.json(shapeBundleResult(r));
    } catch (err) {
      console.error('[Salem Router] /route-multi error:', err.message);
      return res.status(500).json({ error: err.message });
    }
  });

  // GET /api/salem/route/meta — bundle metadata for clients (admin UI etc.)
  app.get('/salem/route/meta', (req, res) => {
    if (!bundleGuard(req, res)) return;
    res.json({
      bundle_path: path.basename(BUNDLE_PATH),
      node_count: bundle.nodeCount,
      edge_count: bundle.edgeCount,
      pace_mps: bundle.walkingPaceMps,
      meta: bundle.meta,
    });
  });

  return {
    // Exposed for tests / introspection.
    _bundle: () => bundle,
    _route: (a, b, c, d) => (bundle ? routeBundle(bundle, a, b, c, d) : null),
    _routeDiag: (a, b, c, d) => (bundle ? routeBundleDiag(bundle, a, b, c, d) : null),
  };
};
