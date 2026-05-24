/*
 * LocationMapApp v1.5 — admin-auth unit tests (S290)
 *
 * Built-in node:test runner (zero deps). Run with `npm test` in cache-proxy/.
 * Covers the multi-role credential mapping + the historian field guard helper
 * added for the restricted 'historian' admin login.
 */
const { test } = require('node:test');
const assert = require('node:assert/strict');

// resolveRole reads process.env at call time, so set the fixtures up front.
process.env.ADMIN_USER = 'dean';
process.env.ADMIN_PASS = 'admin-secret';
process.env.ADMIN_HISTORIAN_USER = 'katrina';
process.env.ADMIN_HISTORIAN_PASS = 'hist-secret';

const {
  parseBasicAuth,
  resolveRole,
  filterHistorianViolations,
} = require('../lib/admin-auth');

test('parseBasicAuth decodes a valid Basic header', () => {
  const header = 'Basic ' + Buffer.from('katrina:hist-secret').toString('base64');
  assert.deepEqual(parseBasicAuth(header), { user: 'katrina', pass: 'hist-secret' });
});

test('parseBasicAuth rejects non-Basic / malformed headers', () => {
  assert.equal(parseBasicAuth('Bearer abc'), null);
  assert.equal(parseBasicAuth(''), null);
  assert.equal(parseBasicAuth(undefined), null);
  // base64 with no colon → null
  assert.equal(parseBasicAuth('Basic ' + Buffer.from('nocolon').toString('base64')), null);
});

test('resolveRole maps the full-admin credential to "admin"', () => {
  assert.equal(resolveRole({ user: 'dean', pass: 'admin-secret' }), 'admin');
});

test('resolveRole maps the historian credential to "historian"', () => {
  assert.equal(resolveRole({ user: 'katrina', pass: 'hist-secret' }), 'historian');
});

test('resolveRole returns null on a wrong password or unknown user', () => {
  assert.equal(resolveRole({ user: 'dean', pass: 'wrong' }), null);
  assert.equal(resolveRole({ user: 'katrina', pass: 'wrong' }), null);
  assert.equal(resolveRole({ user: 'nobody', pass: 'whatever' }), null);
});

test('resolveRole ignores the historian pair when its env vars are unset', () => {
  const u = process.env.ADMIN_HISTORIAN_USER;
  const p = process.env.ADMIN_HISTORIAN_PASS;
  delete process.env.ADMIN_HISTORIAN_USER;
  delete process.env.ADMIN_HISTORIAN_PASS;
  try {
    assert.equal(resolveRole({ user: 'katrina', pass: 'hist-secret' }), null);
    // Full admin still resolves.
    assert.equal(resolveRole({ user: 'dean', pass: 'admin-secret' }), 'admin');
  } finally {
    process.env.ADMIN_HISTORIAN_USER = u;
    process.env.ADMIN_HISTORIAN_PASS = p;
  }
});

test('filterHistorianViolations returns [] when every key is allowed', () => {
  const allowed = new Set(['short_narration', 'category', 'is_tour_poi']);
  const body = { short_narration: 'x', category: 'CIVIC', is_tour_poi: true };
  assert.deepEqual(filterHistorianViolations(body, allowed), []);
});

test('filterHistorianViolations names the out-of-scope keys', () => {
  const allowed = new Set(['short_narration', 'category']);
  const body = { short_narration: 'x', lat: 42.5, name: 'Hale Farm' };
  assert.deepEqual(filterHistorianViolations(body, allowed).sort(), ['lat', 'name']);
});

test('filterHistorianViolations tolerates a null/non-object body', () => {
  const allowed = new Set(['short_narration']);
  assert.deepEqual(filterHistorianViolations(null, allowed), []);
  assert.deepEqual(filterHistorianViolations(undefined, allowed), []);
});
