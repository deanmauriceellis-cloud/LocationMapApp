/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module scan-cells.js';

const fs = require('fs');
const path = require('path');

module.exports = function(app, deps) {
  const { pgPool, log } = deps;

  // ── Config (env vars with defaults) ──────────────────────────────────────
  const SCAN_FRESHNESS_MS = parseInt(process.env.SCAN_FRESHNESS_MS) || 24 * 60 * 60 * 1000;  // 24h
  const MIN_COVERAGE_POIS = parseInt(process.env.MIN_COVERAGE_POIS) || 10;

  // ── Data structure ───────────────────────────────────────────────────────
  // Key: "42.36:-71.06" (2dp lat:lon) → Value: { lastScanned, poiCount }
  const scanCells = new Map();
  const SCAN_CELLS_FILE = path.join(__dirname, '..', 'scan-cells.json');

  // ── Persistence (debounced, same pattern as radius-hints.json) ──────────
  let savePending = false;

  function loadScanCells() {
    try {
      if (!fs.existsSync(SCAN_CELLS_FILE)) return;
      const raw = fs.readFileSync(SCAN_CELLS_FILE, 'utf8');
      const entries = JSON.parse(raw);
      for (const [key, value] of entries) {
        scanCells.set(key, value);
      }
      console.log(`Loaded ${scanCells.size} scan cells from disk`);
    } catch (err) {
      console.error('Failed to load scan cells from disk:', err.message);
    }
  }

  function saveScanCells() {
    if (savePending) return;
    savePending = true;
    setTimeout(() => {
      savePending = false;
      try {
        const entries = [...scanCells.entries()];
        fs.writeFileSync(SCAN_CELLS_FILE, JSON.stringify(entries));
        console.log(`Saved ${entries.length} scan cells to disk`);
      } catch (err) {
        console.error('Failed to save scan cells to disk:', err.message);
      }
    }, 2000);
  }

  // ── Core functions ─────────────────────────────────────────────────────

  /** 2dp grid key — each cell is ~1.1km */
  function scanCellKey(lat, lon) {
    return `${parseFloat(lat).toFixed(2)}:${parseFloat(lon).toFixed(2)}`;
  }

  /** Check if a scan cell is fresh (recently scanned with sufficient POIs). */
  function checkCoverage(lat, lon) {
    const key = scanCellKey(lat, lon);
    const cell = scanCells.get(key);
    if (!cell) return { fresh: false, coverage: 'EMPTY' };
    const age = Date.now() - cell.lastScanned;
    if (age > SCAN_FRESHNESS_MS) return { fresh: false, coverage: 'STALE' };
    if (cell.poiCount < MIN_COVERAGE_POIS) return { fresh: false, coverage: 'STALE' };
    return { fresh: true, coverage: 'FRESH' };
  }

  /** Compute which 2dp cells fall within a circle of given radius. */
  function getCoveredCells(lat, lon, radius) {
    const latF = parseFloat(lat);
    const lonF = parseFloat(lon);
    // 2dp = 0.01° ≈ 1.1km at equator
    const cellSizeDeg = 0.01;
    // How many cells the radius covers in each direction
    const latSpan = radius / 111320.0;  // degrees latitude per meter
    const lonSpan = latSpan / Math.cos(latF * Math.PI / 180);
    const latCells = Math.ceil(latSpan / cellSizeDeg);
    const lonCells = Math.ceil(lonSpan / cellSizeDeg);

    const cells = [];
    const centerCellLat = Math.round(latF * 100) / 100;
    const centerCellLon = Math.round(lonF * 100) / 100;

    for (let dlat = -latCells; dlat <= latCells; dlat++) {
      for (let dlon = -lonCells; dlon <= lonCells; dlon++) {
        const cellLat = (centerCellLat + dlat * cellSizeDeg).toFixed(2);
        const cellLon = (centerCellLon + dlon * cellSizeDeg).toFixed(2);
        cells.push(`${cellLat}:${cellLon}`);
      }
    }
    return cells;
  }

  /** Mark all ~1km cells covered by a circle as scanned. */
  function markScanned(lat, lon, radius, poiCount) {
    const cells = getCoveredCells(lat, lon, radius);
    const now = Date.now();
    const perCell = Math.max(1, Math.round(poiCount / cells.length));
    for (const key of cells) {
      const existing = scanCells.get(key);
      scanCells.set(key, {
        lastScanned: now,
        poiCount: existing ? Math.max(existing.poiCount, perCell) : perCell,
      });
    }
    console.log(`[Scan Cells] Marked ${cells.length} cells (${poiCount} POIs, ~${perCell}/cell) at ${parseFloat(lat).toFixed(2)},${parseFloat(lon).toFixed(2)} r=${radius}m`);
    saveScanCells();
  }

  /**
   * Collect POIs from PostgreSQL that fall within a radius of (lat, lon).
   * Uses bbox filtering — same approach as /pois/bbox.
   * Returns array of Overpass-compatible elements.
   */
  async function collectPoisInRadius(lat, lon, radius) {
    if (!pgPool) return [];
    const latF = parseFloat(lat);
    const lonF = parseFloat(lon);
    const latSpan = radius / 111320.0;
    const lonSpan = latSpan / Math.cos(latF * Math.PI / 180);
    const south = latF - latSpan;
    const north = latF + latSpan;
    const west = lonF - lonSpan;
    const east = lonF + lonSpan;

    try {
      const result = await pgPool.query(
        'SELECT osm_type AS type, osm_id AS id, lat, lon, tags FROM pois WHERE lat BETWEEN $1 AND $2 AND lon BETWEEN $3 AND $4',
        [south, north, west, east]
      );
      return result.rows.map(r => ({
        type: r.type,
        id: r.id,
        lat: parseFloat(r.lat),
        lon: parseFloat(r.lon),
        tags: typeof r.tags === 'string' ? JSON.parse(r.tags) : r.tags,
      }));
    } catch (err) {
      console.error('[Scan Cells] PostgreSQL collectPoisInRadius error:', err.message);
      return [];
    }
  }

  // ── Debug endpoint ───────────────────────────────────────────────────────
  app.get('/scan-cells', (req, res) => {
    const { lat, lon } = req.query;
    if (lat && lon) {
      const coverage = checkCoverage(lat, lon);
      const key = scanCellKey(lat, lon);
      const cell = scanCells.get(key);
      return res.json({
        key,
        ...coverage,
        cell: cell || null,
        config: { freshnessMs: SCAN_FRESHNESS_MS, minCoveragePois: MIN_COVERAGE_POIS },
      });
    }
    // Dump all cells (summary)
    const entries = [];
    for (const [key, value] of scanCells) {
      entries.push({ key, ...value, age: Date.now() - value.lastScanned });
    }
    entries.sort((a, b) => b.lastScanned - a.lastScanned);
    res.json({
      count: scanCells.size,
      config: { freshnessMs: SCAN_FRESHNESS_MS, minCoveragePois: MIN_COVERAGE_POIS },
      cells: entries.slice(0, 200),  // limit to 200 most recent
    });
  });

  // Load from disk on startup
  loadScanCells();

  return {
    scanCells,
    scanCellKey,
    checkCoverage,
    markScanned,
    getCoveredCells,
    collectPoisInRadius,
    SCAN_CELLS_FILE,
  };
};
