/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module config.js';

const crypto = require('crypto');

// ── JWT Configuration ────────────────────────────────────────────────────────

const JWT_SECRET = process.env.JWT_SECRET || (() => {
  const generated = crypto.randomBytes(32).toString('hex');
  console.warn('[Auth] WARNING: JWT_SECRET not set — using random secret (tokens won\'t survive restarts)');
  return generated;
})();
const JWT_ACCESS_EXPIRY  = '15m';
const JWT_REFRESH_DAYS   = 365;  // device-bonded accounts — effectively permanent

// ── Rate limiters (social endpoints) ────────────────────────────────────────

const rateLimit = require('express-rate-limit');

const authLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,  // 15 minutes
  max: 10,
  keyGenerator: (req) => req.ip,
  message: { error: 'Too many auth requests — try again later' },
  standardHeaders: true,
  legacyHeaders: false,
});

const commentLimiter = rateLimit({
  windowMs: 60 * 1000,  // 1 minute
  max: 10,
  keyGenerator: (req) => req.user?.id || req.ip,
  message: { error: 'Too many comments — slow down' },
  standardHeaders: true,
  legacyHeaders: false,
});

const roomCreateLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,  // 1 hour
  max: 5,
  keyGenerator: (req) => req.user?.id || req.ip,
  message: { error: 'Too many rooms created — try again later' },
  standardHeaders: true,
  legacyHeaders: false,
});

module.exports = {
  JWT_SECRET,
  JWT_ACCESS_EXPIRY,
  JWT_REFRESH_DAYS,
  authLimiter,
  commentLimiter,
  roomCreateLimiter,
};
