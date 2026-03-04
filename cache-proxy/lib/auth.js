/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module auth.js';

const crypto = require('crypto');
const argon2 = require('argon2');
const jwt = require('jsonwebtoken');

function sha256(str) {
  return crypto.createHash('sha256').update(str).digest('hex');
}

/**
 * Sanitize single-line text: strip HTML tags, control chars, collapse whitespace, trim, enforce max length.
 * Returns null if empty after sanitization.
 */
function sanitizeText(str, maxLen) {
  if (typeof str !== 'string') return null;
  let s = str
    .replace(/<[^>]*>/g, '')                       // strip HTML tags
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '')  // strip control chars (keep \t \n \r)
    .replace(/[\t\n\r]/g, ' ')                     // convert remaining whitespace to spaces
    .replace(/ {2,}/g, ' ')                        // collapse multiple spaces
    .trim();
  if (s.length === 0) return null;
  return s.slice(0, maxLen);
}

/**
 * Sanitize multiline text: strip HTML tags, control chars, normalize line endings, cap consecutive newlines, enforce max length.
 * Returns null if empty after sanitization.
 */
function sanitizeMultiline(str, maxLen) {
  if (typeof str !== 'string') return null;
  let s = str
    .replace(/<[^>]*>/g, '')                       // strip HTML tags
    .replace(/[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g, '')  // strip control chars (keep \n via exclusion — \n is \x0A, kept)
    .replace(/\r\n/g, '\n').replace(/\r/g, '\n')   // normalize line endings
    .replace(/\n{3,}/g, '\n\n')                    // max 2 consecutive newlines
    .replace(/[ \t]{2,}/g, ' ')                    // collapse horizontal whitespace
    .trim();
  if (s.length === 0) return null;
  return s.slice(0, maxLen);
}

async function generateTokens(pgPool, user, JWT_SECRET, JWT_ACCESS_EXPIRY, JWT_REFRESH_DAYS) {
  const accessToken = jwt.sign(
    { sub: user.id, name: user.display_name, role: user.platform_role },
    JWT_SECRET,
    { expiresIn: JWT_ACCESS_EXPIRY }
  );
  const refreshToken = crypto.randomBytes(40).toString('hex');
  const familyId = crypto.randomUUID();
  const expiresAt = new Date(Date.now() + JWT_REFRESH_DAYS * 86400000);

  await pgPool.query(
    `INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at)
     VALUES ($1, $2, $3, $4)`,
    [user.id, sha256(refreshToken), familyId, expiresAt]
  );
  return { accessToken, refreshToken, expiresAt: expiresAt.toISOString() };
}

// ── Failed login tracking (IP-based lockout) ────────────────────────────────

const loginAttempts = new Map();  // IP → { count, lockedUntil }
const LOGIN_MAX_ATTEMPTS = 5;
const LOGIN_LOCKOUT_MS = 15 * 60 * 1000;  // 15 minutes

function checkLoginLockout(ip) {
  const entry = loginAttempts.get(ip);
  if (!entry) return false;
  if (entry.lockedUntil && Date.now() < entry.lockedUntil) return true;
  if (entry.lockedUntil && Date.now() >= entry.lockedUntil) {
    loginAttempts.delete(ip);
    return false;
  }
  return false;
}

function recordLoginFailure(ip) {
  const entry = loginAttempts.get(ip) || { count: 0, lockedUntil: null };
  entry.count++;
  if (entry.count >= LOGIN_MAX_ATTEMPTS) {
    entry.lockedUntil = Date.now() + LOGIN_LOCKOUT_MS;
    console.warn(`[Auth] IP ${ip} locked out after ${entry.count} failed login attempts`);
  }
  loginAttempts.set(ip, entry);
}

function clearLoginFailures(ip) {
  loginAttempts.delete(ip);
}

// Cleanup stale lockout entries every 30 minutes
setInterval(() => {
  const now = Date.now();
  for (const [ip, entry] of loginAttempts) {
    if (entry.lockedUntil && now >= entry.lockedUntil) loginAttempts.delete(ip);
  }
}, 30 * 60 * 1000);

function requireAuth(JWT_SECRET) {
  return function(req, res, next) {
    const header = req.headers.authorization;
    if (!header || !header.startsWith('Bearer ')) {
      return res.status(401).json({ error: 'Missing or invalid Authorization header' });
    }
    try {
      const decoded = jwt.verify(header.slice(7), JWT_SECRET);
      req.user = { id: decoded.sub, displayName: decoded.name, role: decoded.role };
      next();
    } catch (err) {
      return res.status(401).json({ error: 'Invalid or expired token' });
    }
  };
}

module.exports = function(app, deps) {
  const { pgPool, requirePg, JWT_SECRET, JWT_ACCESS_EXPIRY, JWT_REFRESH_DAYS, authLimiter } = deps;

  const requireAuthMiddleware = requireAuth(JWT_SECRET);

  // ── Auth Endpoints ───────────────────────────────────────────────────────────

  app.post('/auth/register', requirePg, authLimiter, async (req, res) => {
    try {
      const { displayName: rawName, email, password } = req.body;
      if (!rawName || !password) {
        return res.status(400).json({ error: 'displayName and password are required' });
      }
      const displayName = sanitizeText(rawName, 50);
      if (!displayName || displayName.length < 2) {
        return res.status(400).json({ error: 'displayName must be 2-50 characters' });
      }
      if (!/^[\p{L}\p{N} '\-\.]+$/u.test(displayName)) {
        return res.status(400).json({ error: 'displayName contains invalid characters' });
      }
      if (password.length < 8) {
        return res.status(400).json({ error: 'Password must be at least 8 characters' });
      }
      if (password.length > 128) {
        return res.status(400).json({ error: 'Password must be 128 characters or less' });
      }
      if (email && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        return res.status(400).json({ error: 'Invalid email format' });
      }

      // Check email uniqueness via auth_lookup
      if (email) {
        const emailHash = sha256(email.toLowerCase().trim());
        const existing = await pgPool.query('SELECT user_id FROM auth_lookup WHERE email_hash = $1', [emailHash]);
        if (existing.rows.length > 0) {
          return res.status(409).json({ error: 'Email already registered' });
        }
      }

      const passwordHash = await argon2.hash(password, { type: argon2.argon2id });
      const result = await pgPool.query(
        `INSERT INTO users (display_name, email, password_hash) VALUES ($1, $2, $3) RETURNING id, display_name, platform_role, created_at`,
        [displayName, email ? email.toLowerCase().trim() : null, passwordHash]
      );
      const user = result.rows[0];

      // Store email lookup
      if (email) {
        await pgPool.query(
          'INSERT INTO auth_lookup (email_hash, user_id) VALUES ($1, $2)',
          [sha256(email.toLowerCase().trim()), user.id]
        );
      }

      const tokens = await generateTokens(pgPool, user, JWT_SECRET, JWT_ACCESS_EXPIRY, JWT_REFRESH_DAYS);
      console.log(`[Auth] Registered user ${user.display_name} (${user.id})`);
      res.status(201).json({
        user: { id: user.id, displayName: user.display_name, role: user.platform_role, createdAt: user.created_at },
        ...tokens
      });
    } catch (err) {
      console.error('[Auth] Register error:', err.message);
      res.status(500).json({ error: 'Registration failed' });
    }
  });

  app.post('/auth/login', requirePg, authLimiter, async (req, res) => {
    try {
      const { email, password } = req.body;
      if (!email || !password) {
        return res.status(400).json({ error: 'email and password are required' });
      }
      if (password.length > 128) {
        return res.status(400).json({ error: 'Password must be 128 characters or less' });
      }

      // Check IP lockout
      if (checkLoginLockout(req.ip)) {
        return res.status(429).json({ error: 'Too many failed login attempts — try again later' });
      }

      const emailHash = sha256(email.toLowerCase().trim());
      const lookup = await pgPool.query('SELECT user_id FROM auth_lookup WHERE email_hash = $1', [emailHash]);
      if (lookup.rows.length === 0) {
        recordLoginFailure(req.ip);
        return res.status(401).json({ error: 'Invalid email or password' });
      }

      const result = await pgPool.query(
        'SELECT id, display_name, email, password_hash, platform_role, is_banned, created_at FROM users WHERE id = $1',
        [lookup.rows[0].user_id]
      );
      if (result.rows.length === 0) {
        recordLoginFailure(req.ip);
        return res.status(401).json({ error: 'Invalid email or password' });
      }
      const user = result.rows[0];

      if (user.is_banned) {
        return res.status(403).json({ error: 'Account is banned' });
      }

      const valid = await argon2.verify(user.password_hash, password);
      if (!valid) {
        recordLoginFailure(req.ip);
        return res.status(401).json({ error: 'Invalid email or password' });
      }

      clearLoginFailures(req.ip);
      const tokens = await generateTokens(pgPool, user, JWT_SECRET, JWT_ACCESS_EXPIRY, JWT_REFRESH_DAYS);
      console.log(`[Auth] Login: ${user.display_name} (${user.id})`);
      res.json({
        user: { id: user.id, displayName: user.display_name, role: user.platform_role, createdAt: user.created_at },
        ...tokens
      });
    } catch (err) {
      console.error('[Auth] Login error:', err.message);
      res.status(500).json({ error: 'Login failed' });
    }
  });

  app.post('/auth/refresh', requirePg, authLimiter, async (req, res) => {
    try {
      const { refreshToken } = req.body;
      if (!refreshToken) {
        return res.status(400).json({ error: 'refreshToken is required' });
      }

      const tokenHash = sha256(refreshToken);
      const result = await pgPool.query(
        `SELECT rt.id, rt.user_id, rt.family_id, rt.revoked_at, rt.expires_at,
                u.display_name, u.platform_role, u.is_banned
         FROM refresh_tokens rt JOIN users u ON rt.user_id = u.id
         WHERE rt.token_hash = $1`,
        [tokenHash]
      );

      if (result.rows.length === 0) {
        return res.status(401).json({ error: 'Invalid refresh token' });
      }
      const row = result.rows[0];

      // Family-based revocation: if this token was already used, revoke the whole family
      if (row.revoked_at) {
        await pgPool.query(
          'UPDATE refresh_tokens SET revoked_at = NOW() WHERE family_id = $1 AND revoked_at IS NULL',
          [row.family_id]
        );
        console.warn(`[Auth] Refresh token reuse detected — revoked family ${row.family_id}`);
        return res.status(401).json({ error: 'Token reuse detected — all sessions revoked' });
      }

      if (new Date(row.expires_at) < new Date()) {
        return res.status(401).json({ error: 'Refresh token expired' });
      }

      if (row.is_banned) {
        return res.status(403).json({ error: 'Account is banned' });
      }

      // Revoke the old token
      await pgPool.query('UPDATE refresh_tokens SET revoked_at = NOW() WHERE id = $1', [row.id]);

      // Issue new token pair in the same family
      const newAccessToken = jwt.sign(
        { sub: row.user_id, name: row.display_name, role: row.platform_role },
        JWT_SECRET,
        { expiresIn: JWT_ACCESS_EXPIRY }
      );
      const newRefreshToken = crypto.randomBytes(40).toString('hex');
      const expiresAt = new Date(Date.now() + JWT_REFRESH_DAYS * 86400000);
      await pgPool.query(
        `INSERT INTO refresh_tokens (user_id, token_hash, family_id, expires_at)
         VALUES ($1, $2, $3, $4)`,
        [row.user_id, sha256(newRefreshToken), row.family_id, expiresAt]
      );

      console.log(`[Auth] Token refreshed for ${row.display_name}`);
      res.json({
        accessToken: newAccessToken,
        refreshToken: newRefreshToken,
        expiresAt: expiresAt.toISOString()
      });
    } catch (err) {
      console.error('[Auth] Refresh error:', err.message);
      res.status(500).json({ error: 'Token refresh failed' });
    }
  });

  app.post('/auth/logout', requirePg, requireAuthMiddleware, async (req, res) => {
    try {
      const { refreshToken } = req.body;
      if (refreshToken) {
        const tokenHash = sha256(refreshToken);
        const result = await pgPool.query(
          'SELECT family_id FROM refresh_tokens WHERE token_hash = $1 AND user_id = $2',
          [tokenHash, req.user.id]
        );
        if (result.rows.length > 0) {
          await pgPool.query(
            'UPDATE refresh_tokens SET revoked_at = NOW() WHERE family_id = $1 AND revoked_at IS NULL',
            [result.rows[0].family_id]
          );
        }
      }
      console.log(`[Auth] Logout: ${req.user.displayName}`);
      res.json({ ok: true });
    } catch (err) {
      console.error('[Auth] Logout error:', err.message);
      res.status(500).json({ error: 'Logout failed' });
    }
  });

  app.get('/auth/me', requirePg, requireAuthMiddleware, async (req, res) => {
    try {
      const result = await pgPool.query(
        'SELECT id, display_name, platform_role, is_banned, created_at FROM users WHERE id = $1',
        [req.user.id]
      );
      if (result.rows.length === 0) {
        return res.status(404).json({ error: 'User not found' });
      }
      const u = result.rows[0];
      res.json({ id: u.id, displayName: u.display_name, role: u.platform_role, createdAt: u.created_at });
    } catch (err) {
      console.error('[Auth] /me error:', err.message);
      res.status(500).json({ error: 'Failed to fetch profile' });
    }
  });

  // Debug-only: list registered users (remove in production)
  app.get('/auth/debug/users', requirePg, requireAuthMiddleware, async (req, res) => {
    if (!['owner', 'support'].includes(req.user.role)) {
      return res.status(403).json({ error: 'Insufficient privileges' });
    }
    try {
      const result = await pgPool.query(
        'SELECT id, display_name, platform_role, is_banned, created_at FROM users ORDER BY created_at DESC LIMIT 50'
      );
      res.json(result.rows);
    } catch (err) {
      res.status(500).json({ error: err.message });
    }
  });

  // Export middleware and helpers for use by comments and chat modules
  return {
    requireAuth: requireAuthMiddleware,
    sanitizeText,
    sanitizeMultiline,
  };
};
