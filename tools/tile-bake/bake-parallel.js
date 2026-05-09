#!/usr/bin/env node
/*
 * S234 — parallel tile-bake coordinator. Spawns N worker processes (each
 * runs render-worker.js with a different WORKER_ID), then merges all the
 * worker mbtiles files into the canonical salem-custom.mbtiles.
 *
 * Each worker handles the (blockX, blockY) blocks where
 *   (bx * 100003 + by) % NUM_WORKERS === WORKER_ID
 * — a hash-based split that gives every worker a roughly even share of
 * tiles per zoom level.
 *
 * Usage:
 *   node bake-parallel.js [num_workers] [quality]
 *
 * Defaults: 8 workers, q=60. Override via CLI args or env:
 *   NUM_WORKERS=12 WORKER_QUALITY=70 node bake-parallel.js
 *
 * Each worker holds its own MapLibre context (~500 MB-1 GB resident),
 * so cap NUM_WORKERS based on available RAM. On a 32 GB box, 8 fits
 * comfortably. CPU is the other limit — typical Lenovo bake is ~160 t/s
 * single-threaded, so 8 workers should land near 1200 t/s.
 */

const fs = require('fs');
const path = require('path');
const { spawn } = require('child_process');
const Database = require('better-sqlite3');

const NUM_WORKERS = parseInt(process.argv[2] || process.env.NUM_WORKERS || '8', 10);
const QUALITY = process.argv[3] || process.env.WORKER_QUALITY || '60';
const FINAL_OUTPUT = path.join(__dirname, 'salem-custom.mbtiles');
const WORKER_DIR = path.join(__dirname, 'worker-output');
const WORKER_SCRIPT = path.join(__dirname, 'render-worker.js');

if (!fs.existsSync(WORKER_DIR)) fs.mkdirSync(WORKER_DIR);

console.log(`[coord] Launching ${NUM_WORKERS} workers, quality=${QUALITY}, output=${FINAL_OUTPUT}`);

const tStart = Date.now();
const workerProcs = [];
const workerOutputs = [];

for (let id = 0; id < NUM_WORKERS; id++) {
  const out = path.join(WORKER_DIR, `worker-${id}.mbtiles`);
  workerOutputs.push(out);
  const proc = spawn('node', [WORKER_SCRIPT], {
    env: {
      ...process.env,
      WORKER_ID: String(id),
      NUM_WORKERS: String(NUM_WORKERS),
      WORKER_OUTPUT: out,
      WORKER_QUALITY: QUALITY,
    },
    stdio: ['ignore', 'inherit', 'inherit'],
  });
  workerProcs.push(proc);
}

const promises = workerProcs.map((proc, id) => new Promise((resolve, reject) => {
  proc.on('exit', (code) => {
    if (code === 0) resolve(id);
    else reject(new Error(`worker ${id} exited with code ${code}`));
  });
  proc.on('error', reject);
}));

(async () => {
  try {
    await Promise.all(promises);
    const tWorkers = Date.now();
    console.log(`[coord] All ${NUM_WORKERS} workers DONE in ${((tWorkers - tStart) / 1000).toFixed(1)}s`);

    // ── Merge worker mbtiles into salem-custom.mbtiles ──────────────────
    if (fs.existsSync(FINAL_OUTPUT)) fs.unlinkSync(FINAL_OUTPUT);
    const finalDb = new Database(FINAL_OUTPUT);
    finalDb.pragma('journal_mode = WAL');
    finalDb.exec(`
      CREATE TABLE metadata (name TEXT PRIMARY KEY, value TEXT);
      CREATE TABLE tiles (
        zoom_level INTEGER, tile_column INTEGER, tile_row INTEGER,
        tile_data BLOB, PRIMARY KEY (zoom_level, tile_column, tile_row)
      );
    `);
    const insertMeta = finalDb.prepare('INSERT OR REPLACE INTO metadata (name, value) VALUES (?, ?)');
    insertMeta.run('name', 'Salem Custom');
    insertMeta.run('format', 'webp');
    insertMeta.run('type', 'baselayer');
    insertMeta.run('version', '4');
    insertMeta.run('description', `Salem curated basemap (S234 full +10mi every zoom, WebP q=${QUALITY}, ${NUM_WORKERS}-worker parallel bake).`);
    insertMeta.run('attribution', '© OpenMapTiles © OpenStreetMap contributors');
    insertMeta.run('bounds', '-71.1547,42.3301,-70.6383,42.6899');
    insertMeta.run('minzoom', '14');
    insertMeta.run('maxzoom', '19');
    const insertTile = finalDb.prepare('INSERT INTO tiles (zoom_level, tile_column, tile_row, tile_data) VALUES (?, ?, ?, ?)');

    let totalMerged = 0;
    let totalBytes = 0;
    const merge = finalDb.transaction(() => {
      for (const out of workerOutputs) {
        const src = new Database(out, { readonly: true });
        const rows = src.prepare('SELECT zoom_level, tile_column, tile_row, tile_data FROM tiles');
        let n = 0;
        for (const r of rows.iterate()) {
          insertTile.run(r.zoom_level, r.tile_column, r.tile_row, r.tile_data);
          totalBytes += r.tile_data.length;
          n += 1;
        }
        src.close();
        console.log(`[coord] Merged ${n} tiles from ${path.basename(out)}`);
        totalMerged += n;
      }
    });
    merge();

    finalDb.pragma('journal_mode = DELETE');
    finalDb.exec('VACUUM');
    finalDb.close();

    const stat = fs.statSync(FINAL_OUTPUT);
    const tEnd = Date.now();
    console.log(`[coord] MERGE complete: ${totalMerged} tiles → ${FINAL_OUTPUT}`);
    console.log(`[coord] Final size: ${(stat.size / 1024 / 1024).toFixed(1)} MB`);
    console.log(`[coord] Total elapsed: ${((tEnd - tStart) / 1000).toFixed(1)}s`);

    // Cleanup worker outputs
    for (const out of workerOutputs) {
      try { fs.unlinkSync(out); } catch (e) {}
    }
    try { fs.rmdirSync(WORKER_DIR); } catch (e) {}
    console.log(`[coord] Cleaned up worker outputs`);

    process.exit(0);
  } catch (err) {
    console.error(`[coord] FAILED:`, err);
    workerProcs.forEach(p => { try { p.kill(); } catch (e) {} });
    process.exit(1);
  }
})();
