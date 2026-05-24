# worker-activate — OMEN-025 Phase 1 activation Worker

Stateless Cloudflare Worker that gates first-run activation of the Salem app
(`com.destructiveaigurus.katrinasmysticvisitorsguide`). It pairs a one-time
**Play Integrity (Standard)** verdict with the app's **signed content manifest**
hash, then the app runs offline forever. **Retain-nothing:** no KV/D1/Durable
Objects, no logging of the integrity token, IP, or any device identifier.

- **Endpoint contract:** [`CONTRACT.md`](./CONTRACT.md) — frozen for the Android
  client (Session 5/6) and the OMEN NOTE-L023 #2 response.
- **Plan:** `../docs/plans/OMEN-025-anti-piracy-phase1.md` (Session 4).
- **Host:** `https://activate-salem.<account>.workers.dev` (operator decision S293 —
  plain `*.workers.dev`, not destructiveaigurus.com).

## Layout

```
worker-activate/
├── wrangler.toml      # workers.dev config + non-secret vars
├── src/
│   ├── index.js       # router: GET /nonce, POST /activate
│   ├── nonce.js       # stateless time-boxed HMAC nonce (Web Crypto)
│   └── integrity.js   # SA-JWT mint + decodeIntegrityToken + pure evaluateVerdict + requestHash
└── test/              # node --test (no creds needed; TEST_MODE injects a decoded payload)
```

## Test

```bash
npm test          # 25 tests — nonce round-trip, verdict matrix, full router via TEST_MODE
```

Pure JS / Web Crypto only — runs under Node 22 with no Wrangler runtime and no
Google service-account credentials.

## Local smoke (`wrangler dev`, no real Google call)

```bash
npx wrangler dev
# in another shell:
NONCE=$(curl -s http://localhost:8787/nonce | jq -r .nonce)
# compute requestHash = base64url(sha256(nonce + manifestHash)) — see CONTRACT.md;
# then POST /activate with TEST_MODE=true and a __test_decoded payload.
```
Set `TEST_MODE=true` in `[vars]` (or `--var TEST_MODE:true`) to exercise `/activate`
without the Play Integrity SA creds. **Never** deploy with `TEST_MODE` set.

## Deploy (operator-gated)

Needs (Session 0 prerequisites in the plan):
1. Scoped Cloudflare `wrangler` API token (the operator's account).
2. Play Integrity API enabled in the Google Cloud project linked to the Play Console app.
3. A service account with Play Integrity access; its PKCS8 private key.

```bash
# secrets (offline, never committed — OMEN-002):
npx wrangler secret put NONCE_SECRET            # random >=32 bytes
npx wrangler secret put SA_CLIENT_EMAIL
npx wrangler secret put SA_PRIVATE_KEY          # PKCS8 PEM
npx wrangler secret put ACTIVATION_SIGNING_KEY  # RSA-2048 PKCS8 PEM (optional assertion)

# set EXPECTED_MANIFEST_HASHES in wrangler.toml to the manifestHash printed by
#   node ../cache-proxy/scripts/sign-content-manifest.js   (run AFTER align)
npx wrangler deploy

# rate-limit: add a Cloudflare native rule (dashboard / API), NOT app state.
```

## Anti-replay residual (NOTE-L023 #4)

The stateless HMAC nonce is time-boxed but **not single-use** (no KV). Anti-replay
leans on Play Integrity token freshness + the nonce being bound into the token's
`requestHash`. Acceptable: the casual-piracy threat is sideload-of-a-copy (fails
`PLAY_RECOGNIZED`), not token replay; the local receipt is device-key-bound (S293).
Recorded as a decision, not an omission.
