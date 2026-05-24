// index.js — activation Worker router (OMEN-025 Phase 1, Session 4)
//
// Stateless, retain-nothing. GET /nonce issues a time-boxed HMAC nonce; POST
// /activate decodes a Standard Play Integrity token and returns ok/deny. Nothing
// is stored; nothing identifying is logged. Rate-limiting is a Cloudflare native
// rule, not application state. Contract: CONTRACT.md.

import { mintNonce, verifyNonce } from './nonce.js';
import { requestHash, decodeIntegrityToken, evaluateVerdict, signAssertion } from './integrity.js';

const JSON_HEADERS = { 'Content-Type': 'application/json' };

const json = (status, body) => new Response(JSON.stringify(body), { status, headers: JSON_HEADERS });
const deny = (reason, status = 403) => json(status, { ok: false, reason });

function num(v, dflt) {
  const n = Number(v);
  return Number.isFinite(n) ? n : dflt;
}

async function handleNonce(env) {
  if (!env.NONCE_SECRET) return json(500, { ok: false, reason: 'MISCONFIGURED' });
  const ttlMs = num(env.NONCE_TTL_MS, 120000);
  const nonce = await mintNonce(env.NONCE_SECRET);
  return json(200, { nonce, ttlMs, expiresAt: Date.now() + ttlMs });
}

async function handleActivate(request, env) {
  if (!env.NONCE_SECRET || !env.PACKAGE_NAME || !env.EXPECTED_MANIFEST_HASHES) {
    return json(500, { ok: false, reason: 'MISCONFIGURED' });
  }

  let body;
  try {
    body = await request.json();
  } catch {
    return deny('BAD_REQUEST', 400);
  }
  const { integrity_token, content_manifest_hash, nonce } = body || {};
  if (typeof content_manifest_hash !== 'string' || typeof nonce !== 'string') {
    return deny('BAD_REQUEST', 400);
  }

  // 1. nonce freshness + integrity (stateless HMAC)
  const ttlMs = num(env.NONCE_TTL_MS, 120000);
  const nv = await verifyNonce(env.NONCE_SECRET, nonce, ttlMs);
  if (!nv.ok) return deny(nv.reason);

  // 2. manifest hash must be one we ship (current or previous, staged rollout)
  const expectedHashes = env.EXPECTED_MANIFEST_HASHES.split(',').map((s) => s.trim()).filter(Boolean);
  if (!expectedHashes.includes(content_manifest_hash)) return deny('MANIFEST_MISMATCH');

  // 3. re-derive the requestHash the client must have bound into the token
  const expectedRequestHash = await requestHash(nonce, content_manifest_hash);

  // 4. decode the integrity token (Google call; TEST_MODE injects a decoded payload)
  let decoded;
  if (env.TEST_MODE === 'true' && body.__test_decoded) {
    decoded = body.__test_decoded;
  } else {
    if (typeof integrity_token !== 'string' || !integrity_token) return deny('BAD_REQUEST', 400);
    try {
      decoded = await decodeIntegrityToken(env, integrity_token);
    } catch {
      return deny('DECODE_FAILED', 502); // upstream Google error; client retries
    }
  }

  // 5. verdict
  const result = evaluateVerdict(decoded, {
    packageName: env.PACKAGE_NAME,
    expectedRequestHash,
    tokenMaxAgeMs: num(env.TOKEN_MAX_AGE_MS, 300000),
  });
  if (!result.ok) return deny(result.reason);

  // 6. activated — optional server-signed assertion (defense-in-depth)
  const issuedAt = Date.now();
  const assertion = await signAssertion(env, content_manifest_hash, issuedAt);
  const resp = {
    ok: true,
    issuedAt,
    manifestHash: content_manifest_hash,
    verdict: result.verdict,
  };
  if (assertion) resp.assertion = assertion;
  return json(200, resp);
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (url.pathname === '/nonce') {
      if (request.method !== 'GET') return json(405, { ok: false, reason: 'METHOD_NOT_ALLOWED' });
      return handleNonce(env);
    }
    if (url.pathname === '/activate') {
      if (request.method !== 'POST') return json(405, { ok: false, reason: 'METHOD_NOT_ALLOWED' });
      return handleActivate(request, env);
    }
    return json(404, { ok: false, reason: 'NOT_FOUND' });
  },
};
