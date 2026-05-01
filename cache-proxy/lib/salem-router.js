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

    // S214 — spatial grid over walkable EDGES for the admin tour-waypoint
    // edge-foot snap. Uses the same cell grid as the node index (re-uses
    // bounds + cellLat/cellLng) so the two indices agree on cell coordinates.
    // An edge is "walkable" iff both endpoints are walkable. For each edge we
    // place its index in every cell its bounding box overlaps — bbox is a
    // tiny over-approximation but the grid is coarse enough that it's fine.
    const edgeWalkable = new Uint8Array(edgeCount);
    for (let i = 0; i < edgeCount; i++) {
      edgeWalkable[i] = (nodeWalkable[srcIdx[i]] && nodeWalkable[tgtIdx[i]]) ? 1 : 0;
    }
    const edgeTally = new Int32Array(rows * cols);
    function _edgeBbox(i) {
      const p = edgePolylines[i];
      if (!p || p.length < 4) {
        const sLat = nodeLat[srcIdx[i]], sLng = nodeLng[srcIdx[i]];
        const tLat = nodeLat[tgtIdx[i]], tLng = nodeLng[tgtIdx[i]];
        return [Math.min(sLat, tLat), Math.min(sLng, tLng), Math.max(sLat, tLat), Math.max(sLng, tLng)];
      }
      let lo0 = Infinity, lo1 = Infinity, hi0 = -Infinity, hi1 = -Infinity;
      for (let k = 0; k < p.length; k += 2) {
        const a = p[k], b = p[k + 1];
        if (a < lo0) lo0 = a; if (a > hi0) hi0 = a;
        if (b < lo1) lo1 = b; if (b > hi1) hi1 = b;
      }
      return [lo0, lo1, hi0, hi1];
    }
    function _edgeCellRange(bbox) {
      const r0 = clamp(((bbox[0] - minLat) / cellLat) | 0, 0, rows - 1);
      const c0 = clamp(((bbox[1] - minLng) / cellLng) | 0, 0, cols - 1);
      const r1 = clamp(((bbox[2] - minLat) / cellLat) | 0, 0, rows - 1);
      const c1 = clamp(((bbox[3] - minLng) / cellLng) | 0, 0, cols - 1);
      return [r0, c0, r1, c1];
    }
    for (let i = 0; i < edgeCount; i++) {
      if (!edgeWalkable[i]) continue;
      const [r0, c0, r1, c1] = _edgeCellRange(_edgeBbox(i));
      for (let r = r0; r <= r1; r++) {
        for (let c = c0; c <= c1; c++) edgeTally[r * cols + c]++;
      }
    }
    const edgeCells = new Array(rows * cols);
    for (let k = 0; k < edgeCells.length; k++) edgeCells[k] = new Int32Array(edgeTally[k]);
    const edgeCur = new Int32Array(rows * cols);
    for (let i = 0; i < edgeCount; i++) {
      if (!edgeWalkable[i]) continue;
      const [r0, c0, r1, c1] = _edgeCellRange(_edgeBbox(i));
      for (let r = r0; r <= r1; r++) {
        for (let c = c0; c <= c1; c++) {
          const cellIdx = r * cols + c;
          edgeCells[cellIdx][edgeCur[cellIdx]++] = i;
        }
      }
    }

    // Cumulative segment lengths per edge (degrees, planar) — used to convert
    // the snapped (segment, t) pair into a single fraction along the whole edge.
    const edgeSegCum = new Array(edgeCount);
    const edgeSegTotal = new Float64Array(edgeCount);
    for (let i = 0; i < edgeCount; i++) {
      const p = edgePolylines[i];
      if (!p || p.length < 4) { edgeSegCum[i] = null; edgeSegTotal[i] = 0; continue; }
      const segCount = (p.length / 2) - 1;
      const cum = new Float64Array(segCount + 1);
      let total = 0;
      for (let s = 0; s < segCount; s++) {
        const dlat = p[2 * (s + 1)] - p[2 * s];
        const dlng = p[2 * (s + 1) + 1] - p[2 * s + 1];
        total += Math.sqrt(dlat * dlat + dlng * dlng);
        cum[s + 1] = total;
      }
      edgeSegCum[i] = cum;
      edgeSegTotal[i] = total;
    }

    return {
      nodeCount,
      edgeCount,
      nodeIds, nodeLat, nodeLng, nodeWalkable,
      edgeOffset, adjNeighbor, adjEdgeIdx, adjReversed,
      edgeId, edgeLengthM, edgeFullname, edgeMtfcc, edgePolylines,
      srcIdx, tgtIdx,
      edgeWalkable, edgeSegCum, edgeSegTotal,
      grid: { minLat, minLng, cellLat, cellLng, rows, cols, cells },
      edgeGrid: { cells: edgeCells },
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

// S214 — Edge-foot snap. Finds the perpendicular projection of (lat,lng) onto
// the nearest walkable TigerLine edge polyline. Returns null if no edge found
// within the search rings.
//
// Result shape:
//   { edge_idx, edge_id, fraction (0..1 along whole edge), snap_lat, snap_lng,
//     snap_m (haversine), fullname, mtfcc, from_node_idx, to_node_idx }
//
// Used by the admin tour-waypoint placement / drag-drop flow so every saved
// waypoint sits on a routable edge.
function nearestWalkableEdge(b, lat, lng) {
  const g = b.grid;
  const eg = b.edgeGrid;
  if (!eg) return null;
  const centerRow = clamp(((lat - g.minLat) / g.cellLat) | 0, 0, g.rows - 1);
  const centerCol = clamp(((lng - g.minLng) / g.cellLng) | 0, 0, g.cols - 1);
  let bestIdx = -1;
  let bestD2 = Infinity;
  let bestFootLat = 0, bestFootLng = 0;
  let bestSeg = -1;
  let bestT = 0;
  // Track candidates seen this scan so we don't re-evaluate an edge that lives
  // in multiple cells (large/diagonal edges spill across cells).
  const seen = new Set();
  for (let ring = 0; ring <= 16; ring++) {
    const r0 = Math.max(0, centerRow - ring);
    const r1 = Math.min(g.rows - 1, centerRow + ring);
    const c0 = Math.max(0, centerCol - ring);
    const c1 = Math.min(g.cols - 1, centerCol + ring);
    for (let r = r0; r <= r1; r++) {
      for (let c = c0; c <= c1; c++) {
        if (ring > 0 && r !== r0 && r !== r1 && c !== c0 && c !== c1) continue;
        const cell = eg.cells[r * g.cols + c];
        for (let j = 0; j < cell.length; j++) {
          const ei = cell[j];
          if (seen.has(ei)) continue;
          seen.add(ei);
          const p = b.edgePolylines[ei];
          const segCount = p && p.length >= 4 ? (p.length / 2) - 1 : 0;
          if (segCount === 0) continue;
          for (let s = 0; s < segCount; s++) {
            const aLat = p[2 * s], aLng = p[2 * s + 1];
            const bLat = p[2 * (s + 1)], bLng = p[2 * (s + 1) + 1];
            const dlat = bLat - aLat;
            const dlng = bLng - aLng;
            const segLen2 = dlat * dlat + dlng * dlng;
            let t = 0;
            if (segLen2 > 0) {
              t = ((lat - aLat) * dlat + (lng - aLng) * dlng) / segLen2;
              if (t < 0) t = 0; else if (t > 1) t = 1;
            }
            const footLat = aLat + t * dlat;
            const footLng = aLng + t * dlng;
            const ddl = lat - footLat;
            const ddg = lng - footLng;
            const d2 = ddl * ddl + ddg * ddg;
            if (d2 < bestD2) {
              bestD2 = d2; bestIdx = ei;
              bestFootLat = footLat; bestFootLng = footLng;
              bestSeg = s; bestT = t;
            }
          }
        }
      }
    }
    // Once we have any hit, expand one more ring so a marginally-farther edge
    // in a neighboring cell can still beat the current best, then stop. Same
    // pattern as nearestWalkableNode.
    if (bestIdx >= 0 && ring >= 1) break;
  }
  if (bestIdx < 0) return null;

  // Convert (segment, t) → fraction along the whole edge.
  const cum = b.edgeSegCum[bestIdx];
  const total = b.edgeSegTotal[bestIdx];
  let fraction = 0;
  if (cum && total > 0) {
    const segLen = cum[bestSeg + 1] - cum[bestSeg];
    fraction = (cum[bestSeg] + bestT * segLen) / total;
    if (fraction < 0) fraction = 0; else if (fraction > 1) fraction = 1;
  }

  const snapM = _haversineMSimple(lat, lng, bestFootLat, bestFootLng);
  return {
    edge_idx: bestIdx,
    edge_id: b.edgeId[bestIdx],
    fraction,
    snap_lat: bestFootLat,
    snap_lng: bestFootLng,
    snap_m: snapM == null ? null : Math.round(snapM * 100) / 100,
    fullname: b.edgeFullname[bestIdx] || null,
    mtfcc: b.edgeMtfcc[bestIdx] || null,
    from_node_idx: b.srcIdx[bestIdx],
    to_node_idx: b.tgtIdx[bestIdx],
    edge_length_m: b.edgeLengthM[bestIdx],
  };
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

// ── Edge-point Dijkstra (S215 Phase 2 of waypoint-binding) ────────────────────
//
// Allows a route endpoint to be a point on a TigerLine edge — (edge_idx,
// fraction) — instead of an integer node index. This is what makes legs
// actually pass through mid-block waypoints persisted in salem_tour_stops by
// S214 (edge_id, edge_fraction). Without this, routeBetween still pulls each
// waypoint to the nearer node at compute time and the leg corner-cuts past
// the snap point.
//
// Algorithm:
//   - Same-edge fast path: src and dst on the same edge → polyline-between
//     fractions, no Dijkstra.
//   - Otherwise, seed Dijkstra from BOTH endpoints of src's bound edge with
//     initial distances fraction*L (srcIdx end) and (1-fraction)*L (tgtIdx
//     end). Run Dijkstra normally. The dst is reached when we relax to
//     either endpoint of dst's bound edge; we pick whichever endpoint gives
//     the lower (dist[X] + dst-side partial cost) total.
//   - Geometry: snap-tail (src-edge partial polyline from src snap to chosen
//     exit endpoint) + Dijkstra path + snap-head (dst-edge partial polyline
//     from chosen entry endpoint to dst snap).
//
// An endpoint with no edge binding falls back to nearest-node snap (same as
// routeBundle), so admin can mix POI-based stops (no edge binding — POI
// lat/lng is canonical) with free waypoints (edge-bound) in one tour.

// Returns interpolated [lat, lng] at the given fraction along an edge polyline.
function _interpAlongEdge(b, edgeIdx, fraction) {
  const p = b.edgePolylines[edgeIdx];
  if (!p || p.length < 2) return null;
  if (p.length < 4) return [p[0], p[1]];
  if (fraction <= 0) return [p[0], p[1]];
  if (fraction >= 1) return [p[p.length - 2], p[p.length - 1]];
  const cum = b.edgeSegCum[edgeIdx];
  const total = b.edgeSegTotal[edgeIdx];
  if (!cum || total <= 0) return [p[0], p[1]];
  const targetD = fraction * total;
  // Binary search the segment containing targetD.
  let lo = 0, hi = cum.length - 1;
  while (lo + 1 < hi) {
    const mid = (lo + hi) >> 1;
    if (cum[mid] <= targetD) lo = mid; else hi = mid;
  }
  const segStart = cum[lo];
  const segEnd = cum[lo + 1];
  const segLen = segEnd - segStart;
  const t = segLen > 0 ? (targetD - segStart) / segLen : 0;
  const aLat = p[2 * lo], aLng = p[2 * lo + 1];
  const bLat = p[2 * (lo + 1)], bLng = p[2 * (lo + 1) + 1];
  return [aLat + t * (bLat - aLat), aLng + t * (bLng - aLng)];
}

// Returns [[lat, lng], ...] along an edge polyline from fromFrac to toFrac.
// Reverses orientation if fromFrac > toFrac. Includes interpolated start/end
// points so the returned polyline begins exactly at fromFrac and ends exactly
// at toFrac.
function edgePartialPolyline(b, edgeIdx, fromFrac, toFrac) {
  const p = b.edgePolylines[edgeIdx];
  if (!p || p.length < 2) return [];
  const f0 = clamp(fromFrac, 0, 1);
  const f1 = clamp(toFrac, 0, 1);
  if (f0 === f1) {
    const pt = _interpAlongEdge(b, edgeIdx, f0);
    return pt ? [pt] : [];
  }
  const reversed = f0 > f1;
  const a = reversed ? f1 : f0;
  const c = reversed ? f0 : f1;

  const startPt = _interpAlongEdge(b, edgeIdx, a);
  const endPt = _interpAlongEdge(b, edgeIdx, c);
  if (!startPt || !endPt) return [];

  // Collect the polyline vertices strictly between a and c.
  const out = [startPt];
  if (p.length >= 4) {
    const cum = b.edgeSegCum[edgeIdx];
    const total = b.edgeSegTotal[edgeIdx];
    if (cum && total > 0) {
      const aD = a * total;
      const cD = c * total;
      // Vertex k corresponds to cumulative distance cum[k]. Polyline has
      // (cum.length) vertices indexed 0..cum.length-1.
      for (let k = 0; k < cum.length; k++) {
        if (cum[k] > aD && cum[k] < cD) {
          out.push([p[2 * k], p[2 * k + 1]]);
        }
      }
    }
  }
  out.push(endPt);
  if (reversed) out.reverse();
  return out;
}

// Resolves an endpoint spec to {node_idx?, edge_idx?, fraction?, snap_lat?,
// snap_lng?}. Returns null if no valid resolution.
function _resolveEndpoint(b, ep) {
  if (!ep) return null;
  if (ep.edge_idx != null && ep.fraction != null) {
    const fIdx = ep.edge_idx | 0;
    if (fIdx < 0 || fIdx >= b.edgeCount) return null;
    if (!b.edgeWalkable[fIdx]) return null;
    const f = clamp(+ep.fraction, 0, 1);
    const snap = _interpAlongEdge(b, fIdx, f);
    return {
      edge_idx: fIdx,
      fraction: f,
      snap_lat: snap ? snap[0] : (ep.lat ?? null),
      snap_lng: snap ? snap[1] : (ep.lng ?? null),
    };
  }
  if (ep.node_idx != null) {
    const nIdx = ep.node_idx | 0;
    if (nIdx < 0 || nIdx >= b.nodeCount) return null;
    return {
      node_idx: nIdx,
      snap_lat: b.nodeLat[nIdx],
      snap_lng: b.nodeLng[nIdx],
    };
  }
  if (ep.lat != null && ep.lng != null) {
    const nIdx = nearestWalkableNode(b, +ep.lat, +ep.lng);
    if (nIdx < 0) return null;
    return {
      node_idx: nIdx,
      snap_lat: b.nodeLat[nIdx],
      snap_lng: b.nodeLng[nIdx],
    };
  }
  return null;
}

// Two-pseudo-source Dijkstra: src endpoints are already-relaxed nodes with
// given initial distances. Returns prev arrays + dist array.
function _dijkstraTwoSource(b, sources, stopAtSet) {
  const n = b.nodeCount;
  const dist = new Float64Array(n);
  for (let i = 0; i < n; i++) dist[i] = Infinity;
  const prevNode = new Int32Array(n);
  const prevEdgeIdx = new Int32Array(n);
  const prevReversed = new Uint8Array(n);
  for (let i = 0; i < n; i++) { prevNode[i] = -1; prevEdgeIdx[i] = -1; }
  const heap = new MinHeap();
  for (const s of sources) {
    if (s.idx >= 0 && s.idx < n && s.cost < dist[s.idx]) {
      dist[s.idx] = s.cost;
      heap.push(s.cost, s.idx);
    }
  }
  let stopHits = 0;
  const stopWanted = stopAtSet ? stopAtSet.size : 0;
  while (heap.size > 0) {
    const top = heap.pop();
    const d = top[0], u = top[1];
    if (d > dist[u]) continue;
    if (stopAtSet && stopAtSet.has(u)) {
      stopHits++;
      if (stopHits >= stopWanted) break;
    }
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
  return { dist, prevNode, prevEdgeIdx, prevReversed };
}

// Builds the geometry+edges segment for a Dijkstra-reconstructed node-to-node
// path. `targetIdx` is the end node; the walk-back stops when prevNode goes
// negative (initial-source node). Returns { geometry, edges, distanceM }
// where geometry is [[lat,lng],...] in travel order from the source endpoint
// to targetIdx (exclusive of any pre-pended snap-tail).
function _reconstructFromDijkstra(b, prevNode, prevEdgeIdx, prevReversed, sourceIdx, targetIdx) {
  if (sourceIdx === targetIdx) {
    return {
      geometry: [[b.nodeLat[targetIdx], b.nodeLng[targetIdx]]],
      edges: [],
      distanceM: 0,
    };
  }
  const edgeStack = [];
  const reversedStack = [];
  let cur = targetIdx;
  while (cur !== sourceIdx && prevNode[cur] !== -1) {
    edgeStack.push(prevEdgeIdx[cur]);
    reversedStack.push(prevReversed[cur]);
    cur = prevNode[cur];
  }
  if (cur !== sourceIdx) return null; // didn't reach the actual source
  edgeStack.reverse();
  reversedStack.reverse();

  const geometry = [];
  const edges = [];
  let totalM = 0;
  // Seed with source endpoint coords.
  geometry.push([b.nodeLat[sourceIdx], b.nodeLng[sourceIdx]]);
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
    edges.push({
      edge_id: b.edgeId[eIdx],
      fullname: b.edgeFullname[eIdx],
      mtfcc: b.edgeMtfcc[eIdx],
      length_m: lengthM,
      polyline,
    });
    // Skip duplicate join (first vertex of each edge equals last of prior).
    for (let j = 1; j < polyline.length; j++) geometry.push(polyline[j]);
  }
  return { geometry, edges, distanceM: totalM };
}

function routeBetweenEdgePoints(b, srcEP, dstEP) {
  const src = _resolveEndpoint(b, srcEP);
  const dst = _resolveEndpoint(b, dstEP);
  if (!src || !dst) return null;

  // ── Same-edge fast path. Both endpoints bound to the same edge: just emit
  // the polyline-between-fractions, no Dijkstra needed.
  if (src.edge_idx != null && dst.edge_idx != null && src.edge_idx === dst.edge_idx) {
    const partial = edgePartialPolyline(b, src.edge_idx, src.fraction, dst.fraction);
    const distanceM =
      Math.abs(dst.fraction - src.fraction) * b.edgeLengthM[src.edge_idx];
    return {
      geometry: partial,
      distanceM,
      durationS: distanceM / b.walkingPaceMps,
      edges: [{
        edge_id: b.edgeId[src.edge_idx],
        fullname: b.edgeFullname[src.edge_idx],
        mtfcc: b.edgeMtfcc[src.edge_idx],
        length_m: distanceM,
        polyline: partial,
      }],
    };
  }

  // ── Seed Dijkstra. Edge-point endpoints contribute TWO sources (both edge
  // endpoints, with partial-edge initial costs). Node endpoints contribute one.
  const sources = [];
  if (src.edge_idx != null) {
    const eIdx = src.edge_idx;
    const L = b.edgeLengthM[eIdx];
    sources.push({ idx: b.srcIdx[eIdx], cost: src.fraction * L });
    sources.push({ idx: b.tgtIdx[eIdx], cost: (1 - src.fraction) * L });
  } else {
    sources.push({ idx: src.node_idx, cost: 0 });
  }

  // ── Build the dst stop-set and partial-cost map.
  const stopAt = new Set();
  const dstPartial = new Map(); // node_idx → partial cost from that endpoint to dst snap
  if (dst.edge_idx != null) {
    const eIdx = dst.edge_idx;
    const L = b.edgeLengthM[eIdx];
    const u = b.srcIdx[eIdx], v = b.tgtIdx[eIdx];
    stopAt.add(u); stopAt.add(v);
    dstPartial.set(u, dst.fraction * L);
    dstPartial.set(v, (1 - dst.fraction) * L);
  } else {
    stopAt.add(dst.node_idx);
    dstPartial.set(dst.node_idx, 0);
  }

  const { dist, prevNode, prevEdgeIdx, prevReversed } =
    _dijkstraTwoSource(b, sources, stopAt);

  // ── Pick the dst endpoint with the lowest total cost.
  let bestEntry = -1;
  let bestTotal = Infinity;
  for (const [nIdx, partial] of dstPartial) {
    const dv = dist[nIdx];
    if (!isFinite(dv)) continue;
    const total = dv + partial;
    if (total < bestTotal) { bestTotal = total; bestEntry = nIdx; }
  }
  if (bestEntry < 0) return null;

  // ── Walk back to determine which src endpoint we exited from.
  let cur = bestEntry;
  while (prevNode[cur] !== -1) cur = prevNode[cur];
  const exitNode = cur; // initial-source node we ultimately came from

  // ── Reconstruct the Dijkstra-portion geometry.
  const recon = _reconstructFromDijkstra(b, prevNode, prevEdgeIdx, prevReversed, exitNode, bestEntry);
  if (!recon) return null;

  // ── Build the full geometry: snap-tail (src) + recon + snap-head (dst).
  const geometry = [];
  const edges = [];
  let totalM = 0;

  if (src.edge_idx != null) {
    // Snap-tail: from src snap (src.fraction) to whichever edge endpoint we
    // exited at. exitNode is one of {srcIdx[edge], tgtIdx[edge]} of the src
    // edge; pick the corresponding endpoint fraction.
    const eIdx = src.edge_idx;
    const exitFrac = exitNode === b.srcIdx[eIdx] ? 0 : 1;
    const tail = edgePartialPolyline(b, eIdx, src.fraction, exitFrac);
    const tailM = Math.abs(exitFrac - src.fraction) * b.edgeLengthM[eIdx];
    if (tail.length > 0) {
      for (let i = 0; i < tail.length; i++) geometry.push(tail[i]);
      edges.push({
        edge_id: b.edgeId[eIdx],
        fullname: b.edgeFullname[eIdx],
        mtfcc: b.edgeMtfcc[eIdx],
        length_m: tailM,
        polyline: tail,
      });
      totalM += tailM;
    }
  }

  // Append Dijkstra polyline (skip first vertex if we already have a tail —
  // it'll be the same as the exit endpoint).
  const skipFirst = geometry.length > 0;
  for (let i = 0; i < recon.geometry.length; i++) {
    if (skipFirst && i === 0) continue;
    geometry.push(recon.geometry[i]);
  }
  for (const e of recon.edges) edges.push(e);
  totalM += recon.distanceM;

  if (dst.edge_idx != null) {
    // Snap-head: from chosen entry endpoint to dst snap.
    const eIdx = dst.edge_idx;
    const entryFrac = bestEntry === b.srcIdx[eIdx] ? 0 : 1;
    const head = edgePartialPolyline(b, eIdx, entryFrac, dst.fraction);
    const headM = Math.abs(dst.fraction - entryFrac) * b.edgeLengthM[eIdx];
    if (head.length > 0) {
      // Skip first vertex (same as bestEntry node).
      for (let i = 1; i < head.length; i++) geometry.push(head[i]);
      edges.push({
        edge_id: b.edgeId[eIdx],
        fullname: b.edgeFullname[eIdx],
        mtfcc: b.edgeMtfcc[eIdx],
        length_m: headM,
        polyline: head,
      });
      totalM += headM;
    }
  }

  return {
    geometry,
    distanceM: totalM,
    durationS: totalM / b.walkingPaceMps,
    edges,
  };
}

// Stop-object signature: each stop = { lat, lng, edge_id?, edge_fraction? }.
// edge_id here is the TigerLine edge_id (from salem_tour_stops), NOT the
// router-internal edge_idx — we look it up.
function _stopToEndpoint(b, stop) {
  if (!stop) return null;
  if (stop.edge_id != null && stop.edge_fraction != null) {
    const eId = +stop.edge_id;
    // Find edge_idx by scanning edgeId. Cache the lookup on the bundle so
    // repeated leg computes don't re-scan. ~17K edges → fine to scan once.
    if (!b._edgeIdToIdx) {
      const m = new Map();
      for (let i = 0; i < b.edgeCount; i++) m.set(b.edgeId[i], i);
      b._edgeIdToIdx = m;
    }
    const eIdx = b._edgeIdToIdx.get(eId);
    if (eIdx != null) {
      return { edge_idx: eIdx, fraction: +stop.edge_fraction, lat: stop.lat, lng: stop.lng };
    }
    // Fall through to nearest-node snap if the edge_id is stale (e.g. a
    // routing-bundle rebake renumbered an edge — shouldn't happen but be safe).
  }
  return { lat: stop.lat, lng: stop.lng };
}

function routeBundleEx(b, srcStop, dstStop) {
  const srcEP = _stopToEndpoint(b, srcStop);
  const dstEP = _stopToEndpoint(b, dstStop);
  if (!srcEP || !dstEP) return null;
  return routeBetweenEdgePoints(b, srcEP, dstEP);
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
    // S214 — admin tour waypoint edge-foot snap.
    _snapEdge: (lat, lng) => (bundle ? nearestWalkableEdge(bundle, lat, lng) : null),
    // S215 — edge-point Dijkstra. Routes between two stop objects that may
    // carry edge_id+edge_fraction bindings, so legs actually pass through
    // mid-block waypoints persisted by S214.
    _routeEx: (srcStop, dstStop) => (bundle ? routeBundleEx(bundle, srcStop, dstStop) : null),
  };
};
