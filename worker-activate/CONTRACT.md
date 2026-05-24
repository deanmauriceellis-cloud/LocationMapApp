# Activation Worker — Endpoint Contract (OMEN-025 Phase 1, Session 4)

**Status:** v1 contract, frozen for the Android client (Session 5/6) and the OMEN
NOTE-L023 #2 response. **Deploy gated** on the operator's Cloudflare API token +
Google service-account JSON; this document and the implementing code are unblocked.

**Host:** `https://activate-salem.<account>.workers.dev` (plain `*.workers.dev`,
NOT destructiveaigurus.com — operator decision, S293; withdraws OMEN NOTE-DA005).

**Posture:** stateless, retain-nothing. No KV/D1/Durable Objects. No logging of the
integrity token, IP, ANDROID_ID, or any device identifier. Rate-limiting is a
Cloudflare native rule (dashboard/`wrangler`), not application state. Anti-replay
leans on Play Integrity token freshness — the HMAC nonce is time-boxed but
single-use is NOT enforced (recorded residual, NOTE-L023 #4).

---

## Why two calls

The app uses a **Standard** Play Integrity request, which carries a `requestHash`
(not a server nonce field). We still want a server-issued freshness anchor, so:

1. **`GET /nonce`** issues a stateless, HMAC-bound, time-boxed nonce.
2. The app computes `requestHash = base64url(SHA-256( utf8(nonce) ‖ utf8(contentManifestHash) ))`,
   warms/requests a Standard integrity token bound to that `requestHash`, and POSTs
   the token + nonce + manifest hash to **`POST /activate`**.
3. The Worker re-derives the expected `requestHash` from the (HMAC-verified, unexpired)
   nonce + manifest hash, decodes the token via Google, and checks the decoded
   `requestDetails.requestHash` matches. This binds the token to a fresh nonce
   without the Worker storing anything.

`‖` is byte concatenation. `nonce` and `contentManifestHash` are concatenated in
their **string** form (the base64url nonce string and the 64-char lowercase-hex
manifest hash string), UTF-8 encoded, then SHA-256'd, then base64url-encoded
(no padding). The Android `PlayIntegrityClient` (Session 6) MUST match this exactly.

---

## `GET /nonce`

No request body. No auth.

**200 OK**
```json
{
  "nonce": "<base64url, ~54 chars>",
  "expiresAt": 1716523200000,
  "ttlMs": 120000
}
```

The nonce is opaque to the client — it is `base64url( ts(8B BE) ‖ rand(16B) ‖ HMAC-SHA256(NONCE_SECRET, ts‖rand)[0:16] )`.
The client treats it as a string and echoes it verbatim to `/activate`. `ttlMs` is
informational; the Worker enforces expiry from the embedded `ts`.

**405** for any method other than GET. **5xx** only on misconfiguration (missing secret).

---

## `POST /activate`

**Request** (`Content-Type: application/json`):
```json
{
  "integrity_token": "<Standard Play Integrity token string>",
  "content_manifest_hash": "978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28",
  "nonce": "<the base64url string from GET /nonce>"
}
```
`package_name` is NOT accepted from the client — the expected package is a Worker
config var (`PACKAGE_NAME`) and is checked against the decoded token's
`requestDetails.requestPackageName`. (A client-supplied package would be trivially
spoofable and is meaningless when the token itself names the package.)

**200 OK — activated**
```json
{
  "ok": true,
  "issuedAt": 1716523200500,
  "manifestHash": "978937...c28",
  "verdict": { "licensing": "LICENSED", "appRecognition": "PLAY_RECOGNIZED", "device": ["MEETS_DEVICE_INTEGRITY"] },
  "assertion": "<base64url RSASSA-PKCS1-v1_5/SHA-256 over `${PACKAGE_NAME}|${manifestHash}|${issuedAt}`>"
}
```
`assertion` is signed with `ACTIVATION_SIGNING_KEY` (RSA-2048 PKCS8, Worker secret).
The app MAY embed the matching public key to verify the activation genuinely came
from the Worker (defense-in-depth on top of TLS). Whether the Android client
verifies it is a Session-6 wiring decision — the device-key-bound local receipt
(`ActivationStore`, S293) is the primary tamper-evidence; this assertion is
additive. If the secret is absent the field is omitted (not fatal).

**403 Forbidden — denied** (no body PII; coarse machine-readable reason only):
```json
{ "ok": false, "reason": "UNLICENSED" }
```

| `reason` | Meaning |
|---|---|
| `BAD_REQUEST` | Malformed/missing body field (400, not 403). |
| `NONCE_INVALID` | Nonce failed HMAC verification (forged/corrupt). |
| `NONCE_EXPIRED` | Nonce older than `NONCE_TTL_MS`. |
| `MANIFEST_MISMATCH` | `content_manifest_hash` ∉ `EXPECTED_MANIFEST_HASHES`. |
| `REQUEST_HASH_MISMATCH` | Decoded `requestDetails.requestHash` ≠ re-derived value. |
| `PACKAGE_MISMATCH` | Decoded `requestPackageName` ≠ `PACKAGE_NAME`. |
| `TOKEN_STALE` | `requestDetails.timestampMillis` outside `TOKEN_MAX_AGE_MS`. |
| `UNRECOGNIZED` | `appRecognitionVerdict` ≠ `PLAY_RECOGNIZED` (sideloaded/modified). |
| `UNLICENSED` | `appLicensingVerdict` ≠ `LICENSED` (not a Play purchase). |
| `DEVICE_INTEGRITY` | `deviceRecognitionVerdict` lacks `MEETS_DEVICE_INTEGRITY`. |
| `DECODE_FAILED` | Google `decodeIntegrityToken` returned non-200 / unparseable (502). |

Status codes: `400` BAD_REQUEST, `403` all verdict/nonce/manifest denials,
`405` wrong method, `502` DECODE_FAILED (upstream Google error). The client treats
any non-200 as "not activated"; `403` is a hard UNLICENSED/LOCKED state, `502`/
network errors map to the retryable BLOCKED_NO_INTERNET hard-block (operator: no
grace window).

---

## Config (`wrangler.toml` `[vars]`) and secrets (`wrangler secret put`)

**Vars (non-secret, committed):**
- `PACKAGE_NAME` = `com.destructiveaigurus.katrinasmysticvisitorsguide`
- `EXPECTED_MANIFEST_HASHES` = comma-separated lowercase-hex hashes (current + previous,
  for staged rollout). Paste the `manifestHash` printed by `sign-content-manifest.js`.
- `NONCE_TTL_MS` = `120000`
- `TOKEN_MAX_AGE_MS` = `300000`

**Secrets (offline, never in repo — `wrangler secret put`):**
- `NONCE_SECRET` — HMAC key for the stateless nonce (random ≥32 bytes).
- `SA_CLIENT_EMAIL` — Google service-account email with Play Integrity access.
- `SA_PRIVATE_KEY` — that SA's PKCS8 private key (PEM), for minting the
  `decodeIntegrityToken` access JWT via Web Crypto.
- `ACTIVATION_SIGNING_KEY` — RSA-2048 PKCS8 PEM for the `assertion` (optional;
  distinct from `~/keys/content-manifest.pem` and from the APK upload keystore).

**Test-only:** `TEST_MODE=true` makes `/activate` accept an injected pre-decoded
payload field `__test_decoded` instead of calling Google (for `wrangler dev` smoke
without SA creds). MUST be unset/false in any deployed environment; `src/index.js`
refuses `__test_decoded` unless `TEST_MODE === "true"`.
