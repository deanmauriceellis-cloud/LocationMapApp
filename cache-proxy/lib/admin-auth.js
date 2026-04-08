/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin Basic Auth middleware (Phase 9P.3).
 *
 * Reads ADMIN_USER and ADMIN_PASS from environment. Mounted on /admin/* and
 * on /cache/clear (the latent unauthenticated admin route from S96 survey).
 * Returns 401 + WWW-Authenticate: Basic on missing/wrong credentials.
 *
 * Uses crypto.timingSafeEqual to avoid timing attacks during comparison.
 *
 * Hard-fails at startup if either env var is unset — see ensureConfigured().
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-auth.js';

const crypto = require('crypto');

const REALM = 'LocationMapApp Admin';

/**
 * Constant-time string equality. Both inputs are coerced to UTF-8 buffers
 * of the maximum of their two lengths so timingSafeEqual never throws on
 * length mismatch (which would leak length information).
 */
function constantTimeEqual(a, b) {
  const aBuf = Buffer.from(String(a), 'utf8');
  const bBuf = Buffer.from(String(b), 'utf8');
  const maxLen = Math.max(aBuf.length, bBuf.length);
  const aPad = Buffer.alloc(maxLen);
  const bPad = Buffer.alloc(maxLen);
  aBuf.copy(aPad);
  bBuf.copy(bPad);
  // Equal length AND content match
  return crypto.timingSafeEqual(aPad, bPad) && aBuf.length === bBuf.length;
}

/**
 * Decode `Authorization: Basic <base64>` into { user, pass } or null on failure.
 */
function parseBasicAuth(headerValue) {
  if (!headerValue || typeof headerValue !== 'string') return null;
  const parts = headerValue.split(' ');
  if (parts.length !== 2 || parts[0].toLowerCase() !== 'basic') return null;
  let decoded;
  try {
    decoded = Buffer.from(parts[1], 'base64').toString('utf8');
  } catch (_) {
    return null;
  }
  const colonIdx = decoded.indexOf(':');
  if (colonIdx === -1) return null;
  return {
    user: decoded.slice(0, colonIdx),
    pass: decoded.slice(colonIdx + 1),
  };
}

/**
 * Send a 401 with the appropriate WWW-Authenticate challenge.
 */
function challenge(res, message) {
  res.set('WWW-Authenticate', `Basic realm="${REALM}", charset="UTF-8"`);
  res.status(401).json({ error: message || 'Authentication required' });
}

/**
 * Express middleware. Rejects the request with 401 unless it carries valid
 * Basic auth credentials matching ADMIN_USER and ADMIN_PASS.
 */
function requireBasicAuth(req, res, next) {
  const expectedUser = process.env.ADMIN_USER;
  const expectedPass = process.env.ADMIN_PASS;

  // Defense-in-depth: even though ensureConfigured() should have caught this
  // at startup, refuse to authenticate against blank credentials at runtime.
  if (!expectedUser || !expectedPass) {
    return res.status(503).json({
      error: 'Admin auth not configured (ADMIN_USER and ADMIN_PASS env vars required)',
    });
  }

  const creds = parseBasicAuth(req.headers.authorization);
  if (!creds) {
    return challenge(res, 'Authentication required');
  }

  const userOk = constantTimeEqual(creds.user, expectedUser);
  const passOk = constantTimeEqual(creds.pass, expectedPass);

  // Always evaluate both comparisons (no short-circuit) so failure timing is
  // independent of which field was wrong.
  if (!(userOk && passOk)) {
    return challenge(res, 'Invalid credentials');
  }

  next();
}

/**
 * Call this once at server startup. If ADMIN_USER or ADMIN_PASS is missing,
 * log a loud warning and return false. The caller decides whether to refuse
 * to start or to continue with admin routes effectively disabled.
 */
function ensureConfigured() {
  const user = process.env.ADMIN_USER;
  const pass = process.env.ADMIN_PASS;
  if (!user || !pass) {
    console.error('=========================================================');
    console.error(' ADMIN AUTH NOT CONFIGURED');
    console.error(' ADMIN_USER and ADMIN_PASS env vars must be set.');
    console.error(' All /admin/* and /cache/clear routes will return 503.');
    console.error(' See cache-proxy/.env.example.');
    console.error('=========================================================');
    return false;
  }
  return true;
}

module.exports = {
  requireBasicAuth,
  ensureConfigured,
  // exported for unit tests
  constantTimeEqual,
  parseBasicAuth,
};
