// nonce.js — stateless, time-boxed HMAC nonce (OMEN-025 Phase 1, Session 4)
//
// No KV/D1/Durable Objects. The nonce carries its own issue-time + a random body
// + an HMAC tag over (ts ‖ random) keyed by NONCE_SECRET. Verification recomputes
// the tag and checks freshness — the Worker stores nothing. Single-use is NOT
// enforced (recorded residual, NOTE-L023 #4); anti-replay leans on Play Integrity
// token freshness, and the nonce is bound into the token's requestHash.
//
// Layout (40 bytes): ts(8B big-endian unix-ms) ‖ rand(16B) ‖ tag(16B) where
// tag = HMAC-SHA256(NONCE_SECRET, ts‖rand) truncated to 16 bytes. Encoded base64url.
//
// Web Crypto only — runs identically on Cloudflare Workers and Node 22 (`node --test`).

const TS_LEN = 8;
const RAND_LEN = 16;
const TAG_LEN = 16; // truncated HMAC-SHA256
const NONCE_LEN = TS_LEN + RAND_LEN + TAG_LEN;

export function b64urlEncode(bytes) {
  let bin = '';
  for (const b of bytes) bin += String.fromCharCode(b);
  return btoa(bin).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

export function b64urlDecode(str) {
  const pad = str.length % 4 === 0 ? '' : '='.repeat(4 - (str.length % 4));
  const bin = atob(str.replace(/-/g, '+').replace(/_/g, '/') + pad);
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function hmacKey(secret) {
  return crypto.subtle.importKey(
    'raw',
    new TextEncoder().encode(secret),
    { name: 'HMAC', hash: 'SHA-256' },
    false,
    ['sign'],
  );
}

async function tagFor(secret, prefix) {
  const key = await hmacKey(secret);
  const full = new Uint8Array(await crypto.subtle.sign('HMAC', key, prefix));
  return full.slice(0, TAG_LEN);
}

function writeTs(view, ms) {
  // 8-byte big-endian unix-ms. Number is safe to ~2^53, well past year 2200.
  const hi = Math.floor(ms / 0x100000000);
  const lo = ms >>> 0;
  view.setUint32(0, hi, false);
  view.setUint32(4, lo, false);
}

function readTs(bytes) {
  const view = new DataView(bytes.buffer, bytes.byteOffset, TS_LEN);
  return view.getUint32(0, false) * 0x100000000 + view.getUint32(4, false);
}

// Constant-time-ish compare (length-equal byte arrays).
function timingSafeEqual(a, b) {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a[i] ^ b[i];
  return diff === 0;
}

export async function mintNonce(secret, now = Date.now()) {
  const prefix = new Uint8Array(TS_LEN + RAND_LEN);
  writeTs(new DataView(prefix.buffer, 0, TS_LEN), now);
  crypto.getRandomValues(prefix.subarray(TS_LEN));
  const tag = await tagFor(secret, prefix);

  const nonce = new Uint8Array(NONCE_LEN);
  nonce.set(prefix, 0);
  nonce.set(tag, TS_LEN + RAND_LEN);
  return b64urlEncode(nonce);
}

// Returns { ok: true } | { ok: false, reason: 'NONCE_INVALID' | 'NONCE_EXPIRED' }.
export async function verifyNonce(secret, nonceStr, ttlMs, now = Date.now()) {
  let bytes;
  try {
    bytes = b64urlDecode(nonceStr);
  } catch {
    return { ok: false, reason: 'NONCE_INVALID' };
  }
  if (bytes.length !== NONCE_LEN) return { ok: false, reason: 'NONCE_INVALID' };

  const prefix = bytes.subarray(0, TS_LEN + RAND_LEN);
  const got = bytes.subarray(TS_LEN + RAND_LEN);
  const want = await tagFor(secret, prefix);
  if (!timingSafeEqual(got, want)) return { ok: false, reason: 'NONCE_INVALID' };

  const ts = readTs(bytes.subarray(0, TS_LEN));
  if (now - ts > ttlMs || ts - now > 5000) {
    // expired, or issued implausibly in the future (>5s clock skew allowance)
    return { ok: false, reason: 'NONCE_EXPIRED' };
  }
  return { ok: true, issuedAt: ts };
}
