// node --test — stateless HMAC nonce round-trip + tamper/expiry rejection.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mintNonce, verifyNonce } from '../src/nonce.js';

const SECRET = 'unit-test-secret-at-least-32-bytes-long!!';

test('fresh nonce verifies', async () => {
  const now = Date.now();
  const nonce = await mintNonce(SECRET, now);
  const r = await verifyNonce(SECRET, nonce, 120000, now + 1000);
  assert.equal(r.ok, true);
});

test('expired nonce rejected', async () => {
  const t0 = Date.now();
  const nonce = await mintNonce(SECRET, t0);
  const r = await verifyNonce(SECRET, nonce, 120000, t0 + 120001);
  assert.deepEqual(r, { ok: false, reason: 'NONCE_EXPIRED' });
});

test('wrong secret rejected (HMAC fails)', async () => {
  const nonce = await mintNonce(SECRET);
  const r = await verifyNonce('a-different-secret-of-similar-length!!', nonce, 120000);
  assert.deepEqual(r, { ok: false, reason: 'NONCE_INVALID' });
});

test('byte-flipped nonce rejected', async () => {
  const nonce = await mintNonce(SECRET);
  // flip a char in the body
  const flipped = nonce.slice(0, 5) + (nonce[5] === 'A' ? 'B' : 'A') + nonce.slice(6);
  const r = await verifyNonce(SECRET, flipped, 120000);
  assert.equal(r.ok, false);
  assert.match(r.reason, /NONCE_/);
});

test('garbage string rejected, not thrown', async () => {
  const r = await verifyNonce(SECRET, '!!!not-base64!!!', 120000);
  assert.deepEqual(r, { ok: false, reason: 'NONCE_INVALID' });
});

test('future-dated nonce rejected (clock skew guard)', async () => {
  const future = Date.now() + 60000;
  const nonce = await mintNonce(SECRET, future);
  const r = await verifyNonce(SECRET, nonce, 120000, Date.now());
  assert.deepEqual(r, { ok: false, reason: 'NONCE_EXPIRED' });
});
