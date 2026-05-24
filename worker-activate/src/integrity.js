// integrity.js — Play Integrity (Standard) decode + verdict (OMEN-025 Phase 1, S4)
//
// Two real-credential functions (mintSaAccessToken, decodeIntegrityToken) call
// Google and are exercised only with the operator's service-account JSON (deploy-
// gated). evaluateVerdict + requestHash are PURE and unit-tested under `node --test`.
//
// Standard request flow: the client computes requestHash, requests a token bound to
// it, and POSTs the token here. We decode via Google and confirm requestHash +
// package + license + recognition + device + freshness. Retain-nothing: nothing is
// stored or logged.

import { b64urlEncode } from './nonce.js';

// requestHash = base64url( SHA-256( utf8(nonce) ‖ utf8(manifestHash) ) ).
// MUST byte-match the Android PlayIntegrityClient (Session 6). nonce is the
// base64url string; manifestHash is the 64-char lowercase-hex string.
export async function requestHash(nonce, manifestHash) {
  const data = new TextEncoder().encode(nonce + manifestHash);
  const digest = new Uint8Array(await crypto.subtle.digest('SHA-256', data));
  return b64urlEncode(digest);
}

// ── Pure verdict evaluation over a decoded tokenPayloadExternal ──────────────
// decoded shape (Google Play Integrity tokenPayloadExternal):
//   requestDetails: { requestPackageName, requestHash, timestampMillis }
//   appIntegrity:   { appRecognitionVerdict }
//   accountDetails: { appLicensingVerdict }
//   deviceIntegrity:{ deviceRecognitionVerdict: [...] }
// opts: { packageName, expectedRequestHash, tokenMaxAgeMs, now }
// Returns { ok: true, verdict } | { ok: false, reason }.
export function evaluateVerdict(decoded, opts) {
  const rd = decoded?.requestDetails ?? {};
  const app = decoded?.appIntegrity ?? {};
  const acct = decoded?.accountDetails ?? {};
  const dev = decoded?.deviceIntegrity ?? {};

  if (rd.requestPackageName !== opts.packageName) {
    return { ok: false, reason: 'PACKAGE_MISMATCH' };
  }
  if (rd.requestHash !== opts.expectedRequestHash) {
    return { ok: false, reason: 'REQUEST_HASH_MISMATCH' };
  }

  const ts = Number(rd.timestampMillis);
  const now = opts.now ?? Date.now();
  if (!Number.isFinite(ts) || now - ts > opts.tokenMaxAgeMs || ts - now > 60000) {
    return { ok: false, reason: 'TOKEN_STALE' };
  }

  if (app.appRecognitionVerdict !== 'PLAY_RECOGNIZED') {
    return { ok: false, reason: 'UNRECOGNIZED' };
  }
  if (acct.appLicensingVerdict !== 'LICENSED') {
    return { ok: false, reason: 'UNLICENSED' };
  }
  const device = Array.isArray(dev.deviceRecognitionVerdict) ? dev.deviceRecognitionVerdict : [];
  if (!device.includes('MEETS_DEVICE_INTEGRITY')) {
    return { ok: false, reason: 'DEVICE_INTEGRITY' };
  }

  return {
    ok: true,
    verdict: {
      licensing: acct.appLicensingVerdict,
      appRecognition: app.appRecognitionVerdict,
      device,
    },
  };
}

// ── Google credential path (deploy-gated; needs SA JSON) ─────────────────────

function pemToPkcs8Bytes(pem) {
  const body = pem
    .replace(/-----BEGIN [^-]+-----/g, '')
    .replace(/-----END [^-]+-----/g, '')
    .replace(/\s+/g, '');
  const bin = atob(body);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function importRsaSigningKey(pem) {
  return crypto.subtle.importKey(
    'pkcs8',
    pemToPkcs8Bytes(pem),
    { name: 'RSASSA-PKCS1-v1_5', hash: 'SHA-256' },
    false,
    ['sign'],
  );
}

// Mint a Google OAuth2 access token via the service-account JWT-bearer grant,
// scoped to Play Integrity. Returns the access_token string.
export async function mintSaAccessToken(env, now = Date.now()) {
  const iat = Math.floor(now / 1000);
  const claim = {
    iss: env.SA_CLIENT_EMAIL,
    scope: 'https://www.googleapis.com/auth/playintegrity',
    aud: 'https://oauth2.googleapis.com/token',
    iat,
    exp: iat + 3600,
  };
  const header = { alg: 'RS256', typ: 'JWT' };
  const enc = (obj) => b64urlEncode(new TextEncoder().encode(JSON.stringify(obj)));
  const signingInput = `${enc(header)}.${enc(claim)}`;

  const key = await importRsaSigningKey(env.SA_PRIVATE_KEY);
  const sig = new Uint8Array(
    await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(signingInput)),
  );
  const jwt = `${signingInput}.${b64urlEncode(sig)}`;

  const res = await fetch('https://oauth2.googleapis.com/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
      assertion: jwt,
    }),
  });
  if (!res.ok) throw new Error(`token exchange failed: ${res.status}`);
  const json = await res.json();
  return json.access_token;
}

// Call Google's decodeIntegrityToken. Returns the decoded tokenPayloadExternal,
// or throws (router maps to DECODE_FAILED / 502).
export async function decodeIntegrityToken(env, integrityToken) {
  const accessToken = await mintSaAccessToken(env);
  const url = `https://playintegrity.googleapis.com/v1/${encodeURIComponent(env.PACKAGE_NAME)}:decodeIntegrityToken`;
  const res = await fetch(url, {
    method: 'POST',
    headers: { Authorization: `Bearer ${accessToken}`, 'Content-Type': 'application/json' },
    body: JSON.stringify({ integrity_token: integrityToken }),
  });
  if (!res.ok) throw new Error(`decodeIntegrityToken failed: ${res.status}`);
  const json = await res.json();
  return json.tokenPayloadExternal;
}

// ── Activation assertion (optional, ACTIVATION_SIGNING_KEY) ──────────────────
// RSASSA-PKCS1-v1_5/SHA-256 over `${pkg}|${manifestHash}|${issuedAt}`. base64url.
export async function signAssertion(env, manifestHash, issuedAt) {
  if (!env.ACTIVATION_SIGNING_KEY) return null;
  const key = await importRsaSigningKey(env.ACTIVATION_SIGNING_KEY);
  const payload = `${env.PACKAGE_NAME}|${manifestHash}|${issuedAt}`;
  const sig = new Uint8Array(
    await crypto.subtle.sign('RSASSA-PKCS1-v1_5', key, new TextEncoder().encode(payload)),
  );
  return b64urlEncode(sig);
}
