#!/usr/bin/env node
/*
 * align-asset-schema-to-room.js — S185
 *
 * Rewrites every Room-managed table in salem_content.db using the canonical
 * CREATE TABLE statement Room expects (sourced from
 * app-salem/schemas/<DB>/<version>.json). Without this, Room's
 * TableInfo.equals comparison detects DEFAULT-clause drift between the asset
 * (which still carries `salem-content/create_db.sql`'s legacy DEFAULT
 * clauses) and Room's codegen schema (no defaults), and triggers
 * fallbackToDestructiveMigration on first launch — wiping every row in
 * every table.
 *
 * Algorithm per table:
 *   1. snapshot all rows in memory
 *   2. drop the table
 *   3. CREATE TABLE using Room's exact schema (table + indexes)
 *   4. re-insert the rows
 *
 * Idempotent. Safe to re-run. Run AFTER all publish-* scripts and BEFORE
 * shipping the asset.
 *
 * Usage:
 *   node scripts/align-asset-schema-to-room.js
 */

const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');

const SCHEMAS_DIR = path.resolve(
  __dirname,
  '../../app-salem/schemas/com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase'
);
const SQLITE_PATH = path.resolve(__dirname, '../../salem-content/salem_content.db');
const ASSETS_PATH = path.resolve(__dirname, '../../app-salem/src/main/assets/salem_content.db');

function latestSchema() {
  const files = fs.readdirSync(SCHEMAS_DIR).filter((f) => /^\d+\.json$/.test(f));
  if (!files.length) {
    console.error(`No schema JSON in ${SCHEMAS_DIR}. Build with exportSchema=true first.`);
    process.exit(1);
  }
  files.sort((a, b) => parseInt(b) - parseInt(a));
  const file = files[0];
  return { version: parseInt(file), data: JSON.parse(fs.readFileSync(path.join(SCHEMAS_DIR, file), 'utf8')) };
}

function sqlForEntity(entity) {
  return entity.createSql.replace('${TABLE_NAME}', entity.tableName);
}

function alignDb(dbPath, entities) {
  console.log(`\nAligning: ${dbPath}`);
  const db = new Database(dbPath);
  db.pragma('foreign_keys = OFF');

  for (const entity of entities) {
    const tableName = entity.tableName;
    const tableExists = db.prepare(
      "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?"
    ).get(tableName);
    if (!tableExists) {
      console.log(`  ${tableName}: not present, will create from Room schema`);
    }

    let rows = [];
    let columns = [];
    if (tableExists) {
      columns = db.prepare(`PRAGMA table_info(${tableName})`).all().map((c) => c.name);
      rows = db.prepare(`SELECT * FROM ${tableName}`).all();
    }
    const before = rows.length;

    db.exec(`DROP TABLE IF EXISTS ${tableName};`);
    db.exec(sqlForEntity(entity));
    for (const idx of entity.indices || []) {
      db.exec(idx.createSql.replace('${TABLE_NAME}', tableName));
    }

    if (rows.length) {
      // Restrict re-inserted columns to those present in the new schema.
      const newCols = db.prepare(`PRAGMA table_info(${tableName})`).all().map((c) => c.name);
      const sharedCols = columns.filter((c) => newCols.includes(c));
      const placeholders = sharedCols.map((c) => `@${c}`).join(', ');
      const colList = sharedCols.map((c) => `"${c}"`).join(', ');
      const insert = db.prepare(
        `INSERT INTO ${tableName} (${colList}) VALUES (${placeholders})`
      );
      const tx = db.transaction(() => {
        for (const r of rows) {
          const slim = {};
          for (const c of sharedCols) slim[c] = r[c];
          insert.run(slim);
        }
      });
      tx();
    }
    const after = db.prepare(`SELECT COUNT(*) AS c FROM ${tableName}`).get().c;
    console.log(`  ${tableName.padEnd(34)} ${before} → ${after} rows`);
  }

  db.close();
}

function main() {
  const { version, data } = latestSchema();
  console.log(`=== Align Asset Schema to Room v${version} ===`);
  console.log(`identityHash: ${data.database.identityHash}`);
  const entities = data.database.entities;
  console.log(`Entities: ${entities.length}`);

  for (const dbPath of [SQLITE_PATH, ASSETS_PATH]) {
    if (!fs.existsSync(dbPath)) {
      console.warn(`Skipping (not present): ${dbPath}`);
      continue;
    }
    alignDb(dbPath, entities);

    // Re-stamp the identity_hash to match Room's expected value AND bump
    // PRAGMA user_version. Both must match Room's expectations — without
    // user_version, Room sees the asset as an old schema and runs
    // fallbackToDestructiveMigration on first launch, wiping every row.
    const db = new Database(dbPath);
    db.exec(`
      CREATE TABLE IF NOT EXISTS room_master_table (
        id INTEGER PRIMARY KEY,
        identity_hash TEXT
      );
    `);
    db.prepare(
      'INSERT OR REPLACE INTO room_master_table (id, identity_hash) VALUES (42, ?)'
    ).run(data.database.identityHash);
    db.pragma(`user_version = ${version}`);
    db.close();
    console.log(`  identity_hash → ${data.database.identityHash}`);
    console.log(`  user_version  → ${version}`);
  }

  console.log('\nALIGN COMPLETE');
}

main();
