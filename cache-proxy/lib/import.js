/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module import.js';

// ── Automated POI Database Import (delta every 15 minutes) ───────────────────

let dbImportRunning = false;    // mutex against overlapping runs
let dbImportStats = { lastUpserted: 0, lastSkipped: 0, lastElapsedMs: 0, totalRuns: 0, totalUpserted: 0 };
const DB_IMPORT_INTERVAL_MS = 15 * 60 * 1000;
const DB_IMPORT_BATCH_SIZE = 500;

const POI_CATEGORY_KEYS = ['amenity', 'shop', 'tourism', 'leisure', 'historic', 'office', 'craft', 'aeroway', 'healthcare'];

function derivePoiCategory(tags) {
  if (!tags) return null;
  for (const key of POI_CATEGORY_KEYS) {
    if (tags[key]) return `${key}=${tags[key]}`;
  }
  return null;
}

function extractPoiCoords(element) {
  if (element.lat != null && element.lon != null) {
    return { lat: element.lat, lon: element.lon };
  }
  if (element.center && element.center.lat != null && element.center.lon != null) {
    return { lat: element.center.lat, lon: element.center.lon };
  }
  return { lat: null, lon: null };
}

async function runPoiDbImport(pgPool, drainImportBuffer, manual = false) {
  if (!pgPool) {
    if (manual) throw new Error('Database not configured (set DATABASE_URL)');
    return;
  }
  if (dbImportRunning) {
    if (manual) throw new Error('Import already in progress');
    console.log('[DB Import] Skipping — previous import still running');
    return;
  }

  dbImportRunning = true;
  const t0 = Date.now();
  const label = manual ? 'manual' : 'scheduled';

  try {
    // Drain buffered elements from Overpass responses
    const rawElements = drainImportBuffer();
    if (rawElements.length === 0) {
      console.log(`[DB Import] No buffered elements to import (${label})`);
      dbImportRunning = false;
      return { upserted: 0, skipped: 0, elapsed: 0 };
    }

    // Dedupe by type:id (keep last occurrence)
    const deduped = new Map();
    for (const el of rawElements) {
      if (!el.type || !el.id) continue;
      deduped.set(`${el.type}:${el.id}`, el);
    }
    const elements = [...deduped.values()];

    console.log(`[DB Import] Running ${label} import — ${elements.length} POIs to upsert (${rawElements.length} raw, ${rawElements.length - elements.length} dupes)...`);

    const UPSERT_SQL = `
      INSERT INTO pois (osm_type, osm_id, lat, lon, name, category, tags, first_seen, last_seen)
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
      ON CONFLICT (osm_type, osm_id) DO UPDATE SET
        lat = EXCLUDED.lat,
        lon = EXCLUDED.lon,
        name = EXCLUDED.name,
        category = EXCLUDED.category,
        tags = EXCLUDED.tags,
        last_seen = EXCLUDED.last_seen
    `;

    let upserted = 0;
    let skipped = 0;
    const now = new Date().toISOString();

    // Process in batches with individual transactions (short locks)
    for (let i = 0; i < elements.length; i += DB_IMPORT_BATCH_SIZE) {
      const batch = elements.slice(i, i + DB_IMPORT_BATCH_SIZE);
      const client = await pgPool.connect();
      try {
        await client.query('BEGIN');
        for (const el of batch) {
          const { lat, lon } = extractPoiCoords(el);
          const tags = el.tags || {};
          const name = tags.name || null;
          const category = derivePoiCategory(tags);
          const result = await client.query(UPSERT_SQL, [
            el.type, el.id, lat, lon, name, category, JSON.stringify(tags), now, now,
          ]);
          if (result.rowCount > 0) upserted++;
          else skipped++;
        }
        await client.query('COMMIT');
      } catch (batchErr) {
        await client.query('ROLLBACK');
        throw batchErr;
      } finally {
        client.release();
      }
    }

    const elapsed = Date.now() - t0;

    // Get total count in DB
    const countResult = await pgPool.query('SELECT count(*) AS total FROM pois');
    const totalInDb = parseInt(countResult.rows[0].total, 10);

    dbImportStats = {
      lastUpserted: upserted,
      lastSkipped: skipped,
      lastElapsedMs: elapsed,
      totalRuns: dbImportStats.totalRuns + 1,
      totalUpserted: dbImportStats.totalUpserted + upserted,
    };

    console.log(`[DB Import] ${label} complete — ${upserted} upserted, ${skipped} skipped, ${totalInDb} total in DB (${elapsed}ms)`);
    return { upserted, skipped, totalInDb, elapsed };
  } catch (err) {
    console.error(`[DB Import] ${label} failed:`, err.message);
    dbImportRunning = false;
    if (manual) throw err;
    return;
  } finally {
    dbImportRunning = false;
  }
}

module.exports = function(app, deps) {
  const { pgPool, requirePg, drainImportBuffer, importBuffer } = deps;

  // Schedule automatic imports (only if PostgreSQL is configured)
  if (pgPool) {
    // Initial import 30s after startup (buffer may have elements by then)
    setTimeout(() => {
      console.log('[DB Import] Running initial import...');
      runPoiDbImport(pgPool, drainImportBuffer).catch(err => console.error('[DB Import] Initial import error:', err.message));
    }, 30 * 1000);

    // Delta imports every 15 minutes
    setInterval(() => {
      runPoiDbImport(pgPool, drainImportBuffer).catch(err => console.error('[DB Import] Scheduled import error:', err.message));
    }, DB_IMPORT_INTERVAL_MS);
  }

  // ── POI Database Import endpoints ────────────────────────────────────────────

  app.post('/db/import', requirePg, async (req, res) => {
    try {
      const result = await runPoiDbImport(pgPool, drainImportBuffer, true);
      res.json(result);
    } catch (err) {
      if (err.message === 'Import already in progress') {
        return res.status(409).json({ error: err.message });
      }
      res.status(500).json({ error: 'Import failed', detail: err.message });
    }
  });

  app.get('/db/import/status', (req, res) => {
    res.json({
      running: dbImportRunning,
      intervalMinutes: DB_IMPORT_INTERVAL_MS / 60000,
      pendingDelta: importBuffer.length,
      enabled: !!pgPool,
      stats: dbImportStats,
    });
  });

  // Expose internal state for admin module
  return {
    getImportState: () => ({
      dbImportRunning,
      dbImportStats,
      DB_IMPORT_INTERVAL_MS,
    }),
  };
};
