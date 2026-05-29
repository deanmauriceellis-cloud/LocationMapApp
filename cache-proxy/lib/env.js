/*
 * cache-proxy/lib/env.js — shared .env loader (S304 tech-debt Phase 0b).
 *
 * Replaces the hand-rolled inline `.env` parsers that were copy-pasted across
 * ~17 cache-proxy scripts. Uses the already-installed `dotenv` package and
 * loads cache-proxy/.env (one level up from this lib dir).
 *
 * Like the old inline parsers, this does NOT override variables already present
 * in process.env — an explicitly-exported DATABASE_URL (or a CI-provided value)
 * still wins over the .env file (dotenv's default: no `override`). Idempotent:
 * safe to require/call from multiple scripts in the same process.
 */
const path = require('path');

let loaded = false;

/**
 * Load cache-proxy/.env into process.env (no-op on repeat calls). Returns
 * process.env for convenience. Never throws — a missing dotenv module or an
 * absent/unreadable .env file leaves process.env as-is (the value may still be
 * set in the environment; downstream scripts assert required vars themselves).
 */
function loadEnv() {
  if (loaded) return process.env;
  loaded = true;
  try {
    require('dotenv').config({ path: path.resolve(__dirname, '../.env') });
  } catch (_) {
    // dotenv missing or .env unreadable — env may still be set externally.
  }
  return process.env;
}

// Load on require so `require('../lib/env')` mirrors the old
// parse-at-startup behaviour even without an explicit loadEnv() call.
loadEnv();

module.exports = { loadEnv };
