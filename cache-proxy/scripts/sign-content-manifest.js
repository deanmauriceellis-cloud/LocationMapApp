#!/usr/bin/env node
/*
 * sign-content-manifest.js — S293 (OMEN-025 Phase 1, Layer 2: content integrity)
 *
 * Builds a SHA-256 manifest of the shipped content assets and signs it with the
 * operator's offline RSA private key. The app embeds the matching public key
 * and verifies the signature + recomputes the in-scope hashes at runtime — a
 * bare hash in the DB is patchable, a *signed* manifest is not (a tamperer can't
 * forge new valid hashes without the private key).
 *
 * MUST run LAST in the Android publish chain — AFTER align-asset-schema-to-room.js,
 * because align rewrites every Room table in salem_content.db and re-stamps the
 * identity_hash + PRAGMA user_version. Signing before align produces a hash that
 * won't match the shipped bytes. This script asserts the manifest's roomIdentityHash
 * equals room_master_table id=42 to catch a skipped/stale align.
 *
 * Outputs (both ship in the APK, both committed like salem_content.db):
 *   app-salem/src/main/assets/content-manifest.json   — the manifest
 *   app-salem/src/main/assets/content-manifest.sig    — detached base64 RSA sig
 *
 * Prints `manifestHash` at the end — paste it into the activation Worker's
 * EXPECTED_MANIFEST_HASHES on deploy (the stateless verifier confirms a
 * known-good content shape without storing per-device state).
 *
 * Key handling (OMEN-002 — no hardcoded credentials):
 *   private key read from $MANIFEST_KEY_PATH (default ~/keys/content-manifest.pem),
 *   offline, gitignored, never in the repo. On CI (no private key by design) the
 *   manifest is emitted UNSIGNED with a warning — the signed .sig is produced only
 *   on the operator's machine during the release bake.
 *
 * Usage:
 *   node cache-proxy/scripts/sign-content-manifest.js
 *   echo $?   # 0 = ok, 1 = fail
 */

const path = require('path');
const fs = require('fs');
const os = require('os');
const crypto = require('crypto');
const Database = require('better-sqlite3');

const ASSETS = path.resolve(__dirname, '../../app-salem/src/main/assets');
const SCHEMAS_DIR = path.resolve(
  __dirname,
  '../../app-salem/schemas/com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase'
);
const TILES_DB = path.resolve(__dirname, '../../app-salem-tiles-pack/src/main/assets/salem_tiles.sqlite');

const MANIFEST_OUT = path.join(ASSETS, 'content-manifest.json');
const SIG_OUT = path.join(ASSETS, 'content-manifest.sig');

const MANIFEST_SCHEMA_VERSION = 1;

// CI mode — GitHub Actions sets CI=true. CI runners don't have the offline
// private key (by design) nor the gitignored large assets; signing is demoted
// to a warning and large-asset presence is skipped. Local operator runs sign.
const CI_MODE = process.env.CI === 'true';
const KEY_PATH = process.env.MANIFEST_KEY_PATH || path.join(os.homedir(), 'keys', 'content-manifest.pem');

// ── In-scope assets ────────────────────────────────────────────────────────
// Committed, runtime-read, and small enough to SHA-256 on-device EVERY launch
// (D4 in the OMEN-025 plan). salem_content.db is the high-value tamper anchor
// (all paid POI/narration/witch-trials content). Total ~7.6 MB → sub-second
// hash even on the Lenovo min-spec floor.
// NOTE: tours/*.json are intentionally excluded — legacy since S185 (runtime
// reads tour geometry from the tour_legs Room table, not these JSONs).
const IN_SCOPE = [
  'salem_content.db',
  'splash_tree_v1.json',
  'us_places_v1.sqlite',
];

// ── Recorded but NOT runtime-hashed in Phase 1 (D4) ──────────────────────────
// Too large for per-launch hashing on low-end devices. Presence + size only.
// Phase 1 verifies these in full ONCE at activation time (Android side caches a
// signed "big-assets-verified" flag); Phase 2 can opt into chunked hashing
// without changing this manifest format.
const UNVERIFIED_LARGE = [
  { path: 'salem_tiles.sqlite', file: TILES_DB, note: 'install-time asset pack (:app-salem-tiles-pack)' },
  { path: 'heroes', dir: path.join(ASSETS, 'heroes') },
  { path: 'hero', dir: path.join(ASSETS, 'hero') },
  { path: 'poi-icons', dir: path.join(ASSETS, 'poi-icons') },
];

const failures = [];
const warnings = [];
const ok = [];
const fail = (m) => failures.push(m);
const warn = (m) => warnings.push(m);
const pass = (m) => ok.push(m);

function failOrWarnOnCi(msg) {
  if (CI_MODE) warnings.push(`[CI-skipped] ${msg}`);
  else failures.push(msg);
}

// Dynamic Room schema read — mirrors verify-bundled-assets.js:62-72 and
// align-asset-schema-to-room.js:41-50 so this never drifts against a
// @Database(version=N) bump.
function readLatestRoomIdentity() {
  try {
    const files = fs.readdirSync(SCHEMAS_DIR).filter((f) => /^\d+\.json$/.test(f));
    if (!files.length) return null;
    files.sort((a, b) => parseInt(b) - parseInt(a));
    const data = JSON.parse(fs.readFileSync(path.join(SCHEMAS_DIR, files[0]), 'utf8'));
    return { version: parseInt(files[0]), hash: data.database.identityHash };
  } catch (e) {
    return null;
  }
}

function sha256File(p) {
  return crypto.createHash('sha256').update(fs.readFileSync(p)).digest('hex');
}

// Cheap directory fingerprint for the UNVERIFIED_LARGE record — file count +
// total bytes. Not a security hash; lets Phase 2 / the runtime cheap-proxy
// detect gross changes without hashing 350 MB.
function dirStats(d) {
  let count = 0;
  let bytes = 0;
  const walk = (dir) => {
    for (const ent of fs.readdirSync(dir, { withFileTypes: true })) {
      if (ent.name.startsWith('.')) continue;
      const full = path.join(dir, ent.name);
      if (ent.isDirectory()) walk(full);
      else { count++; bytes += fs.statSync(full).size; }
    }
  };
  walk(d);
  return { count, bytes };
}

// Canonical serialization of the files array for manifestHash — sorted by path,
// only {path, sha256, bytes}, no whitespace. Deterministic across rebuilds with
// identical content, so the same content build always yields the same
// manifestHash (the value baked into the Worker's EXPECTED_MANIFEST_HASHES).
function manifestHashOf(files) {
  const canonical = JSON.stringify(
    files
      .slice()
      .sort((a, b) => (a.path < b.path ? -1 : a.path > b.path ? 1 : 0))
      .map((f) => ({ path: f.path, sha256: f.sha256, bytes: f.bytes }))
  );
  return crypto.createHash('sha256').update(canonical).digest('hex');
}

function main() {
  const room = readLatestRoomIdentity();
  if (!room) {
    fail(`could not read latest Room schema from ${SCHEMAS_DIR} (build with exportSchema=true first)`);
    return report();
  }

  // ── hash in-scope assets ──────────────────────────────────────────────────
  const files = [];
  for (const rel of IN_SCOPE) {
    const p = path.join(ASSETS, rel);
    if (!fs.existsSync(p)) {
      fail(`in-scope asset missing: ${rel}`);
      continue;
    }
    const bytes = fs.statSync(p).size;
    const sha256 = sha256File(p);
    files.push({ path: rel, sha256, bytes });
    pass(`hashed ${rel} (${bytes} bytes) → ${sha256.slice(0, 16)}…`);
  }

  // ── assert salem_content.db was aligned (catches skipped/stale align) ──────
  const contentDb = path.join(ASSETS, 'salem_content.db');
  if (fs.existsSync(contentDb)) {
    let db;
    try {
      db = new Database(contentDb, { readonly: true });
      const master = db.prepare('SELECT identity_hash FROM room_master_table WHERE id=42').get();
      if (!master) {
        fail('salem_content.db: room_master_table id=42 missing — run align-asset-schema-to-room.js first');
      } else if (master.identity_hash !== room.hash) {
        fail(
          `salem_content.db: identity_hash ${master.identity_hash} != Room v${room.version} ${room.hash} ` +
            `— signing a non-aligned DB. Run the publish chain through align-asset-schema-to-room.js first.`
        );
      } else {
        pass(`align verified: salem_content.db identity_hash = ${master.identity_hash} (Room v${room.version})`);
      }
    } catch (e) {
      fail(`salem_content.db: open/query failed: ${e.message}`);
    } finally {
      if (db) db.close();
    }
  }

  // ── record large assets (presence + size only) ─────────────────────────────
  const unverifiedLarge = [];
  for (const item of UNVERIFIED_LARGE) {
    if (item.file) {
      if (!fs.existsSync(item.file)) {
        failOrWarnOnCi(`unverified-large asset missing: ${item.path} (${item.file})`);
        continue;
      }
      const bytes = fs.statSync(item.file).size;
      unverifiedLarge.push({ path: item.path, bytes, note: item.note });
      pass(`recorded ${item.path} (${bytes} bytes, presence-only)`);
    } else if (item.dir) {
      if (!fs.existsSync(item.dir)) {
        failOrWarnOnCi(`unverified-large dir missing: ${item.path}/`);
        continue;
      }
      const { count, bytes } = dirStats(item.dir);
      unverifiedLarge.push({ path: item.path, fileCount: count, bytes });
      pass(`recorded ${item.path}/ (${count} files, ${bytes} bytes, presence-only)`);
    }
  }

  if (failures.length) return report();

  // ── build + write manifest ──────────────────────────────────────────────
  const manifestHash = manifestHashOf(files);
  const manifest = {
    schemaVersion: MANIFEST_SCHEMA_VERSION,
    generatedAt: new Date().toISOString(),
    roomVersion: room.version,
    roomIdentityHash: room.hash,
    files,
    manifestHash,
    unverifiedLarge,
  };
  // Stable 2-space JSON; the signature covers these exact bytes.
  const manifestBytes = Buffer.from(JSON.stringify(manifest, null, 2) + '\n', 'utf8');
  fs.writeFileSync(MANIFEST_OUT, manifestBytes);
  pass(`wrote ${path.relative(process.cwd(), MANIFEST_OUT)} (${files.length} in-scope files)`);

  // ── sign ──────────────────────────────────────────────────────────────────
  if (fs.existsSync(KEY_PATH)) {
    try {
      const privateKey = fs.readFileSync(KEY_PATH);
      // RSASSA-PKCS1-v1_5 over SHA-256 → Android Signature "SHA256withRSA".
      const sig = crypto.sign('sha256', manifestBytes, privateKey);
      fs.writeFileSync(SIG_OUT, sig.toString('base64') + '\n');
      pass(`signed → ${path.relative(process.cwd(), SIG_OUT)} (RSA/SHA-256, ${sig.length} bytes)`);
    } catch (e) {
      fail(`signing failed with key ${KEY_PATH}: ${e.message}`);
    }
  } else if (CI_MODE) {
    // Remove a stale signature so CI never ships a sig that doesn't match the
    // freshly-emitted (unsigned-on-CI) manifest.
    if (fs.existsSync(SIG_OUT)) fs.rmSync(SIG_OUT);
    warn(`[CI-skipped] no private key at ${KEY_PATH} — manifest emitted UNSIGNED (operator signs at release bake)`);
  } else {
    fail(`private key not found at ${KEY_PATH} (set MANIFEST_KEY_PATH or generate the keypair — see CLAUDE.md Key Paths)`);
  }

  report(manifestHash);
}

function report(manifestHash) {
  console.log('\n=== Content Manifest Signing ===');
  if (CI_MODE) console.log('  MODE  CI (signing skipped — no offline private key; see [CI-skipped])');
  for (const m of ok) console.log(`  OK    ${m}`);
  for (const m of warnings) console.log(`  WARN  ${m}`);
  for (const m of failures) console.log(`  FAIL  ${m}`);
  console.log(`\n${ok.length} OK, ${warnings.length} warning, ${failures.length} failure`);

  if (failures.length > 0) {
    console.error('\nManifest signing FAILED. Do not ship this APK.');
    process.exit(1);
  }
  if (manifestHash) {
    console.log(`\n  manifestHash = ${manifestHash}`);
    console.log('  → paste into the activation Worker EXPECTED_MANIFEST_HASHES on deploy.\n');
  }
  console.log('Manifest signing PASSED.');
  process.exit(0);
}

main();
