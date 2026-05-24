// node --test — requestHash determinism + pure verdict evaluation.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { requestHash, evaluateVerdict } from '../src/integrity.js';

const PKG = 'com.destructiveaigurus.katrinasmysticvisitorsguide';
const MANIFEST = '978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28';

test('requestHash is deterministic + base64url', async () => {
  const a = await requestHash('nonce-abc', MANIFEST);
  const b = await requestHash('nonce-abc', MANIFEST);
  assert.equal(a, b);
  assert.match(a, /^[A-Za-z0-9_-]+$/); // base64url, no padding
});

test('requestHash changes with nonce or manifest', async () => {
  const base = await requestHash('nonce-abc', MANIFEST);
  assert.notEqual(base, await requestHash('nonce-xyz', MANIFEST));
  assert.notEqual(base, await requestHash('nonce-abc', MANIFEST.replace('9', '8')));
});

function goodDecoded(reqHash, now = Date.now()) {
  return {
    requestDetails: { requestPackageName: PKG, requestHash: reqHash, timestampMillis: String(now) },
    appIntegrity: { appRecognitionVerdict: 'PLAY_RECOGNIZED' },
    accountDetails: { appLicensingVerdict: 'LICENSED' },
    deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_DEVICE_INTEGRITY', 'MEETS_BASIC_INTEGRITY'] },
  };
}

const OPTS = (rh, now) => ({ packageName: PKG, expectedRequestHash: rh, tokenMaxAgeMs: 300000, now });

test('genuine token passes', async () => {
  const rh = await requestHash('n', MANIFEST);
  const now = Date.now();
  const r = evaluateVerdict(goodDecoded(rh, now), OPTS(rh, now));
  assert.equal(r.ok, true);
  assert.equal(r.verdict.licensing, 'LICENSED');
});

test('package mismatch', async () => {
  const rh = await requestHash('n', MANIFEST);
  const d = goodDecoded(rh);
  d.requestDetails.requestPackageName = 'com.evil.clone';
  assert.deepEqual(evaluateVerdict(d, OPTS(rh)), { ok: false, reason: 'PACKAGE_MISMATCH' });
});

test('requestHash mismatch (token not bound to our nonce)', async () => {
  const rh = await requestHash('n', MANIFEST);
  const d = goodDecoded('some-other-hash');
  assert.deepEqual(evaluateVerdict(d, OPTS(rh)), { ok: false, reason: 'REQUEST_HASH_MISMATCH' });
});

test('stale token rejected', async () => {
  const rh = await requestHash('n', MANIFEST);
  const now = Date.now();
  const d = goodDecoded(rh, now - 600000); // 10 min old
  assert.deepEqual(evaluateVerdict(d, OPTS(rh, now)), { ok: false, reason: 'TOKEN_STALE' });
});

test('unrecognized app (sideloaded)', async () => {
  const rh = await requestHash('n', MANIFEST);
  const d = goodDecoded(rh);
  d.appIntegrity.appRecognitionVerdict = 'UNRECOGNIZED_VERSION';
  assert.deepEqual(evaluateVerdict(d, OPTS(rh)), { ok: false, reason: 'UNRECOGNIZED' });
});

test('unlicensed (not a Play purchase)', async () => {
  const rh = await requestHash('n', MANIFEST);
  const d = goodDecoded(rh);
  d.accountDetails.appLicensingVerdict = 'UNLICENSED';
  assert.deepEqual(evaluateVerdict(d, OPTS(rh)), { ok: false, reason: 'UNLICENSED' });
});

test('device integrity not met', async () => {
  const rh = await requestHash('n', MANIFEST);
  const d = goodDecoded(rh);
  d.deviceIntegrity.deviceRecognitionVerdict = ['MEETS_BASIC_INTEGRITY'];
  assert.deepEqual(evaluateVerdict(d, OPTS(rh)), { ok: false, reason: 'DEVICE_INTEGRITY' });
});

test('empty/missing fields rejected, not thrown', async () => {
  const rh = await requestHash('n', MANIFEST);
  assert.equal(evaluateVerdict({}, OPTS(rh)).ok, false);
  assert.equal(evaluateVerdict(null, OPTS(rh)).ok, false);
});
