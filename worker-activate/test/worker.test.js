// node --test — end-to-end router via TEST_MODE (no Google call).
// Exercises GET /nonce → POST /activate with an injected decoded payload, proving
// the nonce→requestHash→verdict binding works as a whole without SA creds.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/index.js';
import { requestHash } from '../src/integrity.js';

const PKG = 'com.destructiveaigurus.katrinasmysticvisitorsguide';
const MANIFEST = '978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28';

const ENV = {
  TEST_MODE: 'true',
  NONCE_SECRET: 'unit-test-secret-at-least-32-bytes-long!!',
  PACKAGE_NAME: PKG,
  EXPECTED_MANIFEST_HASHES: MANIFEST,
  NONCE_TTL_MS: '120000',
  TOKEN_MAX_AGE_MS: '300000',
  // no ACTIVATION_SIGNING_KEY → assertion omitted (still ok:true)
};

const call = (path, init) => worker.fetch(new Request(`https://x.workers.dev${path}`, init), ENV);

async function getNonce() {
  const res = await call('/nonce', { method: 'GET' });
  assert.equal(res.status, 200);
  return (await res.json()).nonce;
}

function decoded(reqHash) {
  return {
    requestDetails: { requestPackageName: PKG, requestHash: reqHash, timestampMillis: String(Date.now()) },
    appIntegrity: { appRecognitionVerdict: 'PLAY_RECOGNIZED' },
    accountDetails: { appLicensingVerdict: 'LICENSED' },
    deviceIntegrity: { deviceRecognitionVerdict: ['MEETS_DEVICE_INTEGRITY'] },
  };
}

const post = (body) =>
  call('/activate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) });

test('GET /nonce returns a nonce + ttl', async () => {
  const res = await call('/nonce', { method: 'GET' });
  const j = await res.json();
  assert.equal(res.status, 200);
  assert.equal(typeof j.nonce, 'string');
  assert.equal(j.ttlMs, 120000);
});

test('full happy path activates', async () => {
  const nonce = await getNonce();
  const rh = await requestHash(nonce, MANIFEST);
  const res = await post({ content_manifest_hash: MANIFEST, nonce, __test_decoded: decoded(rh) });
  const j = await res.json();
  assert.equal(res.status, 200);
  assert.equal(j.ok, true);
  assert.equal(j.manifestHash, MANIFEST);
  assert.equal(j.assertion, undefined); // no signing key configured
});

test('unknown manifest hash → MANIFEST_MISMATCH', async () => {
  const nonce = await getNonce();
  const res = await post({ content_manifest_hash: 'deadbeef', nonce, __test_decoded: decoded('x') });
  assert.equal(res.status, 403);
  assert.deepEqual(await res.json(), { ok: false, reason: 'MANIFEST_MISMATCH' });
});

test('forged nonce → NONCE_INVALID', async () => {
  const rh = await requestHash('forged', MANIFEST);
  const res = await post({ content_manifest_hash: MANIFEST, nonce: 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA', __test_decoded: decoded(rh) });
  assert.equal(res.status, 403);
  assert.equal((await res.json()).reason.startsWith('NONCE'), true);
});

test('token bound to a different nonce → REQUEST_HASH_MISMATCH', async () => {
  const nonce = await getNonce();
  const wrongRh = await requestHash('a-different-nonce', MANIFEST);
  const res = await post({ content_manifest_hash: MANIFEST, nonce, __test_decoded: decoded(wrongRh) });
  assert.equal(res.status, 403);
  assert.deepEqual(await res.json(), { ok: false, reason: 'REQUEST_HASH_MISMATCH' });
});

test('unlicensed verdict → UNLICENSED', async () => {
  const nonce = await getNonce();
  const rh = await requestHash(nonce, MANIFEST);
  const d = decoded(rh);
  d.accountDetails.appLicensingVerdict = 'UNLICENSED';
  const res = await post({ content_manifest_hash: MANIFEST, nonce, __test_decoded: d });
  assert.equal(res.status, 403);
  assert.deepEqual(await res.json(), { ok: false, reason: 'UNLICENSED' });
});

test('malformed body → 400 BAD_REQUEST', async () => {
  const res = await call('/activate', { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: 'not json' });
  assert.equal(res.status, 400);
  assert.deepEqual(await res.json(), { ok: false, reason: 'BAD_REQUEST' });
});

test('wrong method on /nonce → 405', async () => {
  const res = await call('/nonce', { method: 'POST' });
  assert.equal(res.status, 405);
});

test('__test_decoded ignored when TEST_MODE off (would hit Google → DECODE_FAILED)', async () => {
  const prodEnv = { ...ENV, TEST_MODE: 'false', SA_CLIENT_EMAIL: 'x@y', SA_PRIVATE_KEY: 'invalid' };
  const nonce = await worker.fetch(new Request('https://x/nonce'), prodEnv).then((r) => r.json()).then((j) => j.nonce);
  const rh = await requestHash(nonce, MANIFEST);
  const res = await worker.fetch(
    new Request('https://x/activate', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ integrity_token: 'tok', content_manifest_hash: MANIFEST, nonce, __test_decoded: decoded(rh) }),
    }),
    prodEnv,
  );
  // __test_decoded must be ignored; real decode is attempted and fails closed.
  assert.equal(res.status, 502);
  assert.deepEqual(await res.json(), { ok: false, reason: 'DECODE_FAILED' });
});
