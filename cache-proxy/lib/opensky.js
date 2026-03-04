/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * This source code is proprietary and confidential.
 * Unauthorized copying, modification, or distribution is
 * strictly prohibited.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module opensky.js';

// ── OpenSky OAuth2 (optional — set OPENSKY_CLIENT_ID + OPENSKY_CLIENT_SECRET) ──

const OPENSKY_TOKEN_URL = 'https://auth.opensky-network.org/auth/realms/opensky-network/protocol/openid-connect/token';
let openskyToken = null;    // { access_token, expiresAt }

async function getOpenskyToken() {
  const clientId = process.env.OPENSKY_CLIENT_ID;
  const clientSecret = process.env.OPENSKY_CLIENT_SECRET;
  if (!clientId || !clientSecret) return null;

  // Reuse token if still valid (5 min buffer)
  if (openskyToken && Date.now() < openskyToken.expiresAt - 300000) {
    return openskyToken.access_token;
  }

  try {
    const resp = await fetch(OPENSKY_TOKEN_URL, {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: `grant_type=client_credentials&client_id=${encodeURIComponent(clientId)}&client_secret=${encodeURIComponent(clientSecret)}`,
    });
    if (!resp.ok) {
      console.error(`[OpenSky] Token request failed: ${resp.status}`);
      return null;
    }
    const data = await resp.json();
    openskyToken = {
      access_token: data.access_token,
      expiresAt: Date.now() + data.expires_in * 1000,
    };
    console.log(`[OpenSky] Token refreshed, expires in ${data.expires_in}s`);
    return openskyToken.access_token;
  } catch (err) {
    console.error('[OpenSky] Token error:', err.message);
    return null;
  }
}

const openskyConfigured = !!(process.env.OPENSKY_CLIENT_ID && process.env.OPENSKY_CLIENT_SECRET);

// ── OpenSky rate limiter — stay within API daily quota ────────────────────────

const OPENSKY_DAILY_LIMIT = openskyConfigured ? 4000 : 100;
const OPENSKY_SAFETY_MARGIN = 0.9;  // use 90% of quota to leave headroom
const OPENSKY_EFFECTIVE_LIMIT = Math.floor(OPENSKY_DAILY_LIMIT * OPENSKY_SAFETY_MARGIN);
const OPENSKY_MIN_INTERVAL_MS = Math.ceil((24 * 60 * 60 * 1000) / OPENSKY_EFFECTIVE_LIMIT);  // ms between requests

const openskyRateState = {
  requests: [],         // timestamps of upstream requests (rolling 24h window)
  backoffUntil: 0,      // timestamp: don't hit upstream before this time
  backoffSeconds: 10,   // current backoff delay (exponential)
  consecutive429s: 0,   // track consecutive 429s for backoff scaling
};

function openskyCanRequest() {
  const now = Date.now();
  // Prune requests older than 24h
  const cutoff = now - 24 * 60 * 60 * 1000;
  openskyRateState.requests = openskyRateState.requests.filter(t => t > cutoff);

  // Check backoff
  if (now < openskyRateState.backoffUntil) {
    const waitSec = Math.ceil((openskyRateState.backoffUntil - now) / 1000);
    return { allowed: false, reason: `backoff (${waitSec}s remaining)`, retryAfter: waitSec };
  }

  // Check daily quota
  if (openskyRateState.requests.length >= OPENSKY_EFFECTIVE_LIMIT) {
    const oldestExpires = openskyRateState.requests[0] + 24 * 60 * 60 * 1000;
    const waitSec = Math.ceil((oldestExpires - now) / 1000);
    return { allowed: false, reason: `daily quota (${openskyRateState.requests.length}/${OPENSKY_EFFECTIVE_LIMIT})`, retryAfter: waitSec };
  }

  // Check minimum interval between requests
  const lastReq = openskyRateState.requests[openskyRateState.requests.length - 1] || 0;
  if (now - lastReq < OPENSKY_MIN_INTERVAL_MS) {
    const waitMs = OPENSKY_MIN_INTERVAL_MS - (now - lastReq);
    return { allowed: false, reason: `min interval (${Math.ceil(waitMs / 1000)}s)`, retryAfter: Math.ceil(waitMs / 1000) };
  }

  return { allowed: true };
}

function openskyRecordRequest() {
  openskyRateState.requests.push(Date.now());
}

function openskyRecord429() {
  openskyRateState.consecutive429s++;
  // Exponential backoff: 10s, 20s, 40s, 80s, 160s, capped at 300s (5 min)
  openskyRateState.backoffSeconds = Math.min(300, 10 * Math.pow(2, openskyRateState.consecutive429s - 1));
  openskyRateState.backoffUntil = Date.now() + openskyRateState.backoffSeconds * 1000;
  console.log(`[OpenSky] 429 rate limited — backoff ${openskyRateState.backoffSeconds}s (consecutive: ${openskyRateState.consecutive429s})`);
}

function openskyRecordSuccess() {
  if (openskyRateState.consecutive429s > 0) {
    console.log(`[OpenSky] Recovered from rate limit (was ${openskyRateState.consecutive429s} consecutive 429s)`);
  }
  openskyRateState.consecutive429s = 0;
  openskyRateState.backoffSeconds = 10;
}

module.exports = {
  getOpenskyToken,
  openskyConfigured,
  openskyRateState,
  openskyCanRequest,
  openskyRecordRequest,
  openskyRecord429,
  openskyRecordSuccess,
  OPENSKY_DAILY_LIMIT,
  OPENSKY_SAFETY_MARGIN,
  OPENSKY_EFFECTIVE_LIMIT,
  OPENSKY_MIN_INTERVAL_MS,
};
