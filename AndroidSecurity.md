# AndroidSecurity.md — LocationMapApp / Katrina's Mystic Visitors Guide

> **Single source of truth for the app's security & anti-piracy architecture (OMEN-025).**
> Written for **continuity**: the bulk of the runtime/build code is already implemented and
> unit-tested, but live activation is **gated on three operator credentials that have not yet
> arrived** (Cloudflare API token · Google Play Integrity service-account JSON · Play Console
> internal-testing track). When those land — possibly weeks from now, possibly in a different
> session with no memory of this work — this document is the resume-from-here playbook.
>
> **If you read only one section, read [§13 Resume Playbook](#13-resume-playbook).**
>
> **Status as of Session 296 (2026-05-24):** Phase 1 Sessions 1–5 + the Session-6 *core* are
> implemented and tested (46 Android JVM tests + 25 Worker tests, all green). Deferred:
> Session-6 launch-flow wiring, Session-7 manifest/rollout, and live deploy — all gated.
>
> **Document owner:** carried in the LMA repo root alongside `GOVERNANCE.md` / `IP.md`.
> Update it whenever the activation architecture, a key, or a contract changes.

---

## 0. How to use this document

- **Resuming after tokens arrive** → jump to [§13 Resume Playbook](#13-resume-playbook). It is
  ordered by *which credential unblocks what*, with exact commands.
- **Need the exact byte-level crypto a component expects** → [§7 Cryptographic Contracts](#7-cryptographic-contracts).
  These are the things that silently break cross-component if changed on one side only; each has a
  pinned cross-check test.
- **Need to know where a key lives / what touches it** → [§8 Key & Secret Inventory](#8-key--secret-inventory).
- **Need the full file list + build/gated status** → [§5 Component Inventory](#5-component-inventory).
- **The authoritative step-by-step plan** is `docs/plans/OMEN-025-anti-piracy-phase1.md`. This
  document is the *security-architecture + continuity* companion to it; the plan is the *session
  checklist*. Where they overlap, the plan's session numbering wins.

---

## 1. Threat model & design intent (OMEN-025)

**Directive:** OMEN-025 (issued 2026-05-23, LMA-specific, HIGH, pre-V1 gate). Full threat model:
`~/Development/OMEN/architecture/lma-anti-piracy-assessment.md` (relay NOTE-L022). The paid Salem
tour app ($19.99 flat, fully offline) is trivially pirate-able as shipped: pull the APK + the
install-time asset pack, sideload, done. OMEN-025 raises the cost.

**Operator framing:** *kill ~99% of casual piracy; accept the skilled-RE ~1%.* Do not gold-plate
against a determined reverse-engineer at the expense of bricking real buyers.

**The mechanism — a one-time online activation handshake in front of an otherwise-offline app:**

- A genuine, Play-licensed install on a genuine device performs **one** online activation on first
  run, then runs **offline forever**.
- Sideloaded / non-Play / tampered copies **fail to activate**.
- Tampered *content* (e.g. an edited `salem_content.db`) is **detectable** at runtime (Phase 1) and
  will be **cryptographically unusable** (Phase 2, content encryption).

**This does NOT reverse the offline product promise.** The tour still runs fully offline — GPS,
tiles, narration, POIs, zero network. The only literal change is that `INTERNET` returns to the
release manifest for that single handshake. After one successful activation, cold starts reach the
map with **zero sockets** (verifiable in logcat — see [§12 Verification Matrix](#12-verification-matrix)).

**Operator decisions that shape everything below:**

| Decision | Choice | Consequence |
|---|---|---|
| No-signal first run | **Hard-block, no grace window** | First launch with no internet → `BLOCKED_NO_INTERNET`, Retry only. No "try later" offline pass. |
| Verifier host | **Stateless Cloudflare Worker, LMA-owned, on `*.workers.dev`** | NOT destructiveaigurus.com (operator declined entangling the marketing site → withdraws OMEN cross-project relay NOTE-DA005). |
| Cert pinning | **DROPPED** (NOTE-L023 #1) | Plain HTTPS + standard cert validation. A stale pin would brick *every new buyer* — risk outweighed the low-value MITM threat against a stateless verdict endpoint. |
| Retention | **Retain-nothing** | No KV/D1/Durable Objects; no logging of token/IP/device. Rate-limit via Cloudflare native rules, not app state. |
| Anti-replay | **Token-freshness only** (NOTE-L023 #4) | The stateless HMAC nonce is time-boxed but not single-use. Acceptable: the threat is sideload-of-a-copy (fails `PLAY_RECOGNIZED`), not token replay. Recorded as a decision, not an omission. |

---

## 2. Naming disambiguation (security-relevant)

The same product wears four names. For security work the ones that bite:

- **Android `applicationId`:** `com.destructiveaigurus.katrinasmysticvisitorsguide` — this is what
  Play Integrity verdicts, the Worker's `PACKAGE_NAME`, and `decodeIntegrityToken`'s path segment
  all key on. **Use this everywhere a package name is required.**
- **Kotlin/Java namespace:** `com.example.wickedsalemwitchcitytour` (app) +
  `com.example.locationmapapp` (`:core`). Intentionally NOT renamed. The activation code lives in
  `com.example.locationmapapp.activation` (`:core`) and `…wickedsalemwitchcitytour.ui` (`:app-salem`).
- **Operating entity:** Destructive AI Gurus, LLC (MA, Filing # 202626416630). All keys/accounts
  register under the LLC, not Dean Ellis personally.

**Footgun:** `adb uninstall com.example.wickedsalemwitchcitytour` fails — the installed package is
`com.destructiveaigurus.katrinasmysticvisitorsguide`.

---

## 3. Architecture — the layers

```
                        ┌─────────────────────────────────────────────────────┐
   FIRST RUN (online)   │  SplashActivity → ActivationActivity → SalemMain      │
                        │            │  (gated by FeatureFlags                   │
                        │            │   .ACTIVATION_HANDSHAKE_ENABLED)          │
                        │            ▼                                           │
                        │   ActivationManager.runHandshake()                    │
                        │     1. verifyFull() content manifest (Layer 2)        │
                        │     2. GET  /nonce        ──────────►  Cloudflare      │
                        │     3. Play Integrity Standard token   Worker          │
                        │        (requestHash = SHA256(nonce‖manifestHash))      │
                        │     4. POST /activate {token, hash, nonce} ──► Worker  │
                        │            ◄── decodeIntegrityToken via Google ──┐     │
                        │     5. verdict OK → write device-key-signed       │    │
                        │        ActivationReceipt (Layer 1)         Play Integrity│
                        └─────────────────────────────────────────────────┘────┘
                                     │ receipt persisted
   EVERY LATER LAUNCH (offline)      ▼
                        ┌─────────────────────────────────────────────────────┐
                        │   ActivationManager.evaluateLocal()  (no network)    │
                        │     · verifyCheap() content manifest                  │
                        │     · load device-key-signed receipt                  │
                        │     · check tripwires → ACTIVATED → forward to Main   │
                        └─────────────────────────────────────────────────────┘
```

**Layer 1 — purchase validation (Play Integrity + device-bound receipt).** Proves *this install is
a genuine Play purchase on a genuine device*. The verdict is checked once online; the result is
persisted as a receipt **signed by a hardware-backed device key** so it cannot be lifted to another
device. Offline launches re-verify the receipt signature + device-binding tripwires.

**Layer 2 — content integrity (signed manifest).** Proves *the shipped content has not been
tampered with*. A build-time RSA-signed manifest enumerates SHA-256 of each in-scope content asset;
the app embeds the public key and re-verifies the signature + recomputes the hashes at runtime. A
bare hash in the DB is patchable; a *signed* manifest is not (no private key on the device).

**The handshake** ties them together: the Play Integrity `requestHash` binds the server nonce to the
content `manifestHash`, so a passing verdict is cryptographically about *this content on this device*.

**Phase 2 (content encryption)** and **Phase 3 (native gate)** are roadmap — see [§14](#14-phase-2--3-roadmap).
Phase 1 (this document's subject) = Layers 1+2 + handshake + disclosures.

---

## 4. Current offline-lockdown posture (the thing we modify, carefully)

V1 is offline by **four coordinated pieces** — understand them before re-adding `INTERNET`:

1. **`FeatureFlags.V1_OFFLINE_ONLY = true`** (`core/.../util/FeatureFlags.kt`) — const, R8-folded.
2. **`OfflineModeInterceptor`** (`core/.../util/network/OfflineModeInterceptor.kt`) — installed on
   **all 14 OkHttp clients**; throws `OfflineModeException` before any socket unless
   `SuperAdminMode.allowNetwork` (debug-only override).
3. **Manifest INTERNET-strip** — the **release** manifest (`app-salem/src/main/AndroidManifest.xml`)
   has **no `INTERNET` permission**. A **debug overlay** (`app-salem/src/debug/AndroidManifest.xml`)
   re-adds `INTERNET` + `ACCESS_NETWORK_STATE` for the SuperAdmin path; a debug-only
   `network_security_config.xml` allows LAN cleartext to the cache-proxy.
4. **~15 call-site gates** on parked services (weather/MBTA/aircraft/…) — **leave intact**.

**OMEN-025's contract with this posture:** the activation client is the **one** client that does
*not* carry `OfflineModeInterceptor` (decision D1). It gets its own perimeter (`ActivationHostGuard`)
instead. Re-adding `INTERNET` to the release manifest (Session 7) does **not** open the 14 gated
clients — they still default-deny via `V1_OFFLINE_ONLY`. The Session-7 verification explicitly proves
weather/MBTA/aircraft stay blocked despite `INTERNET` being granted.

---

## 5. Component inventory

Legend: ✅ implemented + tested · 🔧 implemented, untested at JVM tier (Android/Play deps) · ⏸ deferred · 🔑 gated on a credential.

### `:core` — `com.example.locationmapapp.activation.*` (all implemented this far)

| File | Role | Status |
|---|---|---|
| `DeviceKeyManager.kt` | Hardware-backed EC P-256 Keystore signing key (StrongBox→TEE fallback, device-bound not user-auth-bound). Signs/verifies the receipt; `publicKeyFingerprint()`. | ✅ (on-device round-trip 🔧 instrumented) |
| `ActivationReceipt.kt` | Gson data model of the local activation proof. | ✅ |
| `ActivationStore.kt` | `EncryptedSharedPreferences` + **detached device-key signature** over the receipt JSON. `load()` returns null unless the sig verifies → copied/edited receipts rejected. | ✅ (Android-coupled) |
| `ContentManifestVerifier.kt` | Layer 2 runtime. Verifies `.sig` over verbatim `content-manifest.json`, recomputes in-scope hashes + canonical `manifestHash`. `verifyCheap()` (every launch) / `verifyFull()` (activation). Pure, injected I/O. | ✅ (9 tests, S295) |
| `ActivationHostGuard.kt` | Fails-closed perimeter for the one non-offline client: exact-host + HTTPS-only + no-redirect. No `CertificatePinner`. Pure `assertAllowed(HttpUrl)`. | ✅ (6 tests, S296) |
| `ActivationHttpClientFactory.kt` | The dedicated OkHttpClient **without** `OfflineModeInterceptor` (D1). `followRedirects(false)`. Placeholder `WORKER_HOST`. | ✅ |
| `ActivationApi.kt` | Transport: `fetchNonce()` / `activate()`. Pure `parseActivateResponse(code, body)` → `ActivationResult` (Activated/Denied/Retryable). | ✅ (9 tests, S296) |
| `PlayIntegrityClient.kt` | Play Integrity **Standard** request. Pure `computeRequestHash(nonce, manifestHash)` (byte-matched to Worker). Android `prepare()`/`requestToken()` over `StandardIntegrityManager`. `cloudProjectNumber` placeholder. | ✅ requestHash (3 tests) / 🔧🔑 token request |
| `ActivationManager.kt` | State machine. Pure `decideLocal` / `decideHandshake` / `buildReceipt`. Orchestration `evaluateLocal()` / `runHandshake()` with injected deps. | ✅ (19 tests, S296) |

### `:core` / `:app-salem` — flags

| Symbol | Where | Value | Notes |
|---|---|---|---|
| `FeatureFlags.ACTIVATION_HANDSHAKE_ENABLED` | `:core` | **`false`** | Master switch. Ships OFF until Worker deployed + flow device-validated. Flipping on with no reachable Worker hard-blocks every launch. |
| `BuildDefaults.ACTIVATION_BYPASS` | `:app-salem` | `BuildConfig.RECON_DEFAULTS` | Debug bypasses the gate (sideloaded, no real verdict); release = false → gate live, R8-folds the bypass branch out. |
| `BuildDefaults.ACTIVATION_TRIPWIRES_LOG_ONLY` | `:app-salem` | `BuildConfig.RECON_DEFAULTS` | Debug logs soft tripwires (installer/cert) instead of locking; release enforces. |

> The `BuildDefaults.*` consts live in `:app-salem` and are **passed into** the `:core`
> `ActivationManager` (it never references app-salem) — same module boundary as
> `ContentManifestVerifier.forAndroidAssets(resId)`.

### `worker-activate/` — LMA-owned Cloudflare Worker (implemented; deploy 🔑)

| File | Role |
|---|---|
| `CONTRACT.md` | Frozen endpoint contract (the NOTE-L023 #2 deliverable for OMEN). |
| `src/index.js` | Router: `GET /nonce`, `POST /activate`. Retain-nothing, coarse reason codes. |
| `src/nonce.js` | Stateless time-boxed HMAC nonce (Web Crypto). |
| `src/integrity.js` | SA-JWT mint + `decodeIntegrityToken` + pure `evaluateVerdict` + `requestHash`. |
| `wrangler.toml` | `*.workers.dev` config + non-secret vars. |
| `test/*.test.js` | `node --test` — 25 tests, no creds needed (`TEST_MODE` injects a decoded payload). |

### Deferred / gated (NOT yet built)

| Item | Session | Gate |
|---|---|---|
| Worker deploy | S4 step 7 | 🔑 Cloudflare token + Google SA JSON |
| `ActivationActivity` + `ActivationViewModel` + `activity_activation.xml` + strings | S6 | builds anytime, but pointless to wire until deploy |
| `SplashActivity → ActivationActivity → Main` wiring; `SalemMainActivity` defensive redirect; flip `ACTIVATION_HANDSHAKE_ENABLED` | S6 | needs reachable Worker to test |
| Re-add `INTERNET`/`ACCESS_NETWORK_STATE` to release manifest; release `network_security_config.xml` (HTTPS-only) | S7 | 🔑 Play Console internal track |
| `apkanalyzer` strip-proof + internal-track AAB upload + end-to-end | S7 | 🔑 Play Console |
| Disclosures (Data Safety, privacy line, cred inventory) | S8 | operator/counsel |

---

## 6. Decision ledger

| ID | Decision | Rationale |
|---|---|---|
| **D1** | Dedicated `ActivationHttpClient`, NOT a host-exception inside `OfflineModeInterceptor`. | Preserves default-deny for the 14 gated clients; parked services stay blocked. |
| **D2** | Gate lives in a new `ActivationActivity` between Splash and Main. | Not `Application.onCreate` (ANR/no-UI risk); not Splash (TTS-timing coupling). Already-activated launches verify the local receipt only and forward in ms. |
| **D3** | Revalidate on **violation**, never on a timer. | `ACTIVATED ⇄ LOCKED-pending-revalidation`. Normal activated launches do zero network. |
| **D4** | **Tiered hashing.** Manifest sig + `salem_content.db` (~5 MB) hashed every launch; 354 MB tile DB + image dirs recorded presence/size only (`unverifiedLarge`). | `salem_content.db` is the high-value tamper target; bulk assets are low-tamper-value and too slow to hash per-launch on min-spec. Phase 2 opts into chunked hashing without a format change. |
| **D5** | Debug bypass reuses the proven `BuildConfig.RECON_DEFAULTS` pattern. | `ACTIVATION_BYPASS` const-folds to false + R8-strips in release; identical safety story to `SuperAdminMode`. Lets sideloaded dev devices test the flow. |
| **D6** | Stateless Cloudflare Worker, LMA-owned, on `*.workers.dev`. | No marketing-site entanglement, no custom domain, no cross-project relay. Stateless HMAC nonce (no KV); `decodeIntegrityToken` via SA-JWT minted with Web Crypto; retain-nothing; native rate-limit. (Alt if consolidating on Google: Cloud Run `*.run.app`.) |
| **D-pin** | **DROPPED.** No `CertificatePinner`. | Plain HTTPS + standard cert validation. Defense kept at `ActivationHostGuard` + HTTPS-only network config + runtime signing-cert self-check. Removes the stale-pin-bricks-all-buyers failure mode. |

**OMEN NOTE-L023 sub-items:** #1 pin dropped (done) · #2 endpoint contract → `worker-activate/CONTRACT.md`
(ready to hand over) · #3 unit tests (46 Android + 25 Worker) · #4 anti-replay residual recorded
(above) · #5 register `content-manifest.pem` + Play Integrity SA + Worker secrets in OMEN cred
inventory (owed at S8).

---

## 7. Cryptographic contracts

> **These are the cross-component invariants. If you change one side, change the other and
> regenerate its pinned test, or genuine installs silently fail.** Each has a unit test that pins
> the exact byte output.

### 7.1 `manifestHash` (Layer 2 content integrity)

- **Definition:** `SHA-256( canonicalJSON(files) )` as lowercase hex, where `canonicalJSON` is the
  `files` array **sorted by `path`**, each element rendered as exactly
  `{"path":…,"sha256":…,"bytes":N}` with **no whitespace**, JS `JSON.stringify` string-escaping.
- **Producer:** `cache-proxy/scripts/sign-content-manifest.js` → `manifestHashOf()`.
- **Consumer:** `ContentManifestVerifier.canonicalManifestHash()` (`:core`) — byte-for-byte
  reimplementation in Kotlin.
- **Pinned test:** `ContentManifestVerifierTest` asserts the Kotlin value == the **real build value**
  `978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28` (S275/S281 content build).
- **Where it travels:** printed by the signer → pasted into the Worker `EXPECTED_MANIFEST_HASHES`
  → sent by the app in `POST /activate` → checked by the Worker → echoed back → re-checked by
  `ActivationManager.decideHandshake`.
- **In-scope files (D4):** `salem_content.db`, `splash_tree_v1.json`, `us_places_v1.sqlite`.
  `unverifiedLarge` (presence/size only): `salem_tiles.sqlite` (asset pack), `heroes/`, `hero/`, `poi-icons/`.

### 7.2 `requestHash` (Play Integrity Standard binding)

- **Definition:** `base64url( SHA-256( utf8(nonce) ‖ utf8(manifestHash) ) )`, **no padding**.
  `nonce` is the base64url string from `GET /nonce`; `manifestHash` is the 64-char lowercase-hex string.
  Concatenation order is part of the contract (`nonce` then `manifestHash`).
- **Producer (device):** `PlayIntegrityClient.computeRequestHash()`. Bound into the Standard token
  via `StandardIntegrityTokenRequest.setRequestHash()`.
- **Verifier (Worker):** `src/integrity.js` `requestHash()` re-derives it from the HMAC-verified
  nonce + manifest hash and checks the decoded token's `requestDetails.requestHash` matches.
- **Pinned test:** `PlayIntegrityRequestHashTest` asserts the Kotlin value == the **real Worker
  value** `8DORFCkZ9qldWh9J-ZslUCcVRmidwj66Hj2FY888g4Q` for the fixed vector
  `nonce="TEST_NONCE_abc-123_xyz"`, `manifestHash="978937…c28"`.
  Regenerate via: `cd worker-activate && node --input-type=module -e 'import {requestHash} from "./src/integrity.js"; console.log(await requestHash("TEST_NONCE_abc-123_xyz","978937075a140cd9e3bc73e5e1d3773f58fd7ee42126c40fafcb6f2c59233c28"))'`

### 7.3 Stateless nonce (anti-trivial-replay)

- **Layout (40 bytes, base64url):** `ts(8B big-endian unix-ms) ‖ rand(16B) ‖ HMAC-SHA256(NONCE_SECRET, ts‖rand)[0:16]`.
- **Producer + verifier:** `worker-activate/src/nonce.js` `mintNonce` / `verifyNonce`. TTL = `NONCE_TTL_MS` (120 s).
  Future-skew guard: rejects nonces dated >5 s ahead. Single-use NOT enforced (NOTE-L023 #4 residual).

### 7.4 Content-manifest signature (Layer 2 trust root)

- **Algorithm:** RSASSA-PKCS1-v1_5 over SHA-256 (`crypto.sign('sha256', …)` / Android `Signature("SHA256withRSA")`).
- **Signed bytes:** the **exact** `content-manifest.json` bytes (read verbatim, never reserialized).
- **Private key:** `~/keys/content-manifest.pem` (RSA-2048, offline, 0600, gitignored). Override via `$MANIFEST_KEY_PATH`.
- **Public key:** committed at `app-salem/src/main/res/raw/content_manifest_pubkey.der` (SPKI DER, 294 bytes).
  The app loads it via `KeyFactory("RSA")` + `X509EncodedKeySpec`. `verify-bundled-assets.js` checks the shipped sig against the same key.
- **CI behavior:** on CI (no private key by design) the manifest is emitted **unsigned** with a warning;
  the runtime verifier rejects an empty sig (`"unsigned manifest — CI bake never ships"`). The signed `.sig` is produced only on the operator's release bake.

### 7.5 Activation receipt signature (Layer 1 device-binding)

- **Algorithm:** `SHA256withECDSA` over the receipt JSON, key = the EC P-256 `DeviceKeyManager` Keystore key
  (alias `omen025_device_key`, StrongBox→TEE, non-exportable, `setUserAuthenticationRequired(false)`).
- **Stored:** `EncryptedSharedPreferences` (prefs `omen025_activation`) + the detached base64 signature.
- **Guarantee:** `ActivationStore.load()` returns the receipt only if the sig verifies on *this device's* key
  → a receipt copied to another phone is unverifiable (different secure element) → re-activation forced.

### 7.6 Activation assertion (optional, Worker→app)

- **Algorithm:** RSASSA-PKCS1-v1_5/SHA-256 over `${PACKAGE_NAME}|${manifestHash}|${issuedAt}`, base64url.
- **Key:** Worker secret `ACTIVATION_SIGNING_KEY` (RSA-2048 PKCS8 PEM) — **distinct** from the content-manifest key and the APK upload keystore.
- **Status:** emitted by the Worker if the secret is set; the local device-key receipt is the *primary* tamper-evidence, so whether the app verifies the assertion is a Session-6 wiring decision (additive defense-in-depth). If the app verifies it, embed the matching public key.

---

## 8. Key & secret inventory

> **OMEN-002: no hardcoded credentials.** Every private key/secret below is offline + gitignored;
> only public keys are committed. **Register the three new ones with OMEN (NOTE-L023 #5) at S8.**

| Credential | Type | Location | Mode | Committed? | Consumer | Status |
|---|---|---|---|---|---|---|
| APK upload keystore | RSA (Play upload) | `~/keys/wickedsalem-upload.jks` | — | No | Gradle release signing (`~/.gradle/gradle.properties`) | ✅ exists |
| Content-manifest **private** key | RSA-2048 PEM | `~/keys/content-manifest.pem` | 0600 | No | `sign-content-manifest.js` | ✅ exists |
| Content-manifest **public** key | SPKI DER | `app-salem/src/main/res/raw/content_manifest_pubkey.der` | — | **Yes (safe)** | `ContentManifestVerifier`, `verify-bundled-assets.js` | ✅ |
| Device key | EC P-256 (Keystore) | On-device secure element (alias `omen025_device_key`) | non-exportable | N/A | `DeviceKeyManager` / receipt | ✅ generated per-install at runtime |
| **Cloudflare API token** | scoped wrangler token | offline (operator) | — | No | `wrangler deploy` | 🔑 **not yet provided** |
| **Google Play Integrity SA** | service-account JSON (PKCS8 key) | `~/keys/` (planned) | 0600 | No | Worker `decodeIntegrityToken` (`SA_CLIENT_EMAIL` + `SA_PRIVATE_KEY` secrets) | 🔑 **not yet provided** |
| `NONCE_SECRET` | random ≥32 bytes | Worker secret (`wrangler secret put`) | — | No | `src/nonce.js` HMAC | ⏸ set at deploy |
| `ACTIVATION_SIGNING_KEY` | RSA-2048 PKCS8 PEM | Worker secret (optional) | — | No | `src/integrity.js` assertion | ⏸ optional at deploy |
| Google Cloud project number | numeric | `PlayIntegrityClient.cloudProjectNumber` (or `BuildConfig`) | — | placeholder `0L` | Play Integrity warmup | 🔑 from the linked GCP project |

---

## 9. Endpoint contract (summary — authoritative copy in `worker-activate/CONTRACT.md`)

**Host:** `https://activate-salem.<account>.workers.dev`

**`GET /nonce`** → `200 {nonce, ttlMs, expiresAt}`. Opaque HMAC nonce, echoed verbatim to `/activate`.

**`POST /activate`** body `{integrity_token, content_manifest_hash, nonce}` → :
- `200 {ok:true, issuedAt, manifestHash, verdict, assertion?}` — activated.
- `400` `BAD_REQUEST` · `403` (all verdict/nonce/manifest denials) · `405` wrong method · `502` `DECODE_FAILED` (upstream Google).
- Deny reasons: `NONCE_INVALID`, `NONCE_EXPIRED`, `MANIFEST_MISMATCH`, `REQUEST_HASH_MISMATCH`,
  `PACKAGE_MISMATCH`, `TOKEN_STALE`, `UNRECOGNIZED`, `UNLICENSED`, `DEVICE_INTEGRITY`, `DECODE_FAILED`.

`package_name` is **not** client-supplied — the Worker checks the decoded token's
`requestPackageName` against its `PACKAGE_NAME` var.

**Worker config:** vars `PACKAGE_NAME`, `EXPECTED_MANIFEST_HASHES` (current+previous, comma-sep),
`NONCE_TTL_MS`, `TOKEN_MAX_AGE_MS`. Secrets `NONCE_SECRET`, `SA_CLIENT_EMAIL`, `SA_PRIVATE_KEY`,
`ACTIVATION_SIGNING_KEY`. Test-only `TEST_MODE` (must be unset in deploy).

**App-side error mapping** (operator: no grace window): `200`→Activated; `400/403`→Denied (hard,
UNLICENSED/LOCKED); `5xx`/network→Retryable→`BLOCKED_NO_INTERNET` (Retry only).

---

## 10. Build-pipeline integration

The Android publish chain MUST run in order before any debug/release build; the manifest signer is
**last** (after align rewrites `salem_content.db`):

```
1. node cache-proxy/scripts/publish-salem-pois.js
2. node cache-proxy/scripts/publish-tours.js
3. node cache-proxy/scripts/publish-tour-legs.js
4. node cache-proxy/scripts/publish-poi-collection.js
5. node cache-proxy/scripts/align-asset-schema-to-room.js   # rewrites salem_content.db, stamps Room identity
6. node cache-proxy/scripts/sign-content-manifest.js        # MUST be last → re-hashes the aligned DB, signs, prints manifestHash
```

- **Skipping step 6 after a content change ships a stale sig** → runtime `verifyCheap` fails →
  (in release) the gate LOCKS. Before any release bake, **re-run step 6 after align.**
- The signer **prints `manifestHash`** — paste it into the Worker `EXPECTED_MANIFEST_HASHES` (keep
  the previous value too, for staged rollout) and regenerate the `PlayIntegrityRequestHashTest` /
  `EXPECTED_MANIFEST_HASHES` if the content changed.
- `verify-bundled-assets.js` checks the shipped sig + in-scope hashes against the committed pubkey
  (catches a stale manifest). Its `Historical-(16xx-20xx)` regex is unrelated to activation.

**proguard (`app-salem/proguard-rules.pro`, already in place):**
`-keep class com.example.locationmapapp.activation.** { *; }` (Gson models),
`-keep class com.google.android.play.core.integrity.** { *; }`,
`-keep class androidx.security.crypto.** { *; }` (Tink).

---

## 11. State machine reference (`ActivationManager`)

**`GateState`:** `ACTIVATED` · `NEEDS_ACTIVATION` · `SUCCESS` · `BLOCKED_NO_INTERNET` · `UNLICENSED` · `LOCKED_REVALIDATE`.

**`decideLocal(receipt, snapshot, manifestOk, bypass, tripwiresLogOnly)`** (pure, every launch):
1. `bypass` → `ACTIVATED` ("BYPASS"). *(debug only; R8-folds out in release)*
2. no receipt → `NEEDS_ACTIVATION`.
3. `!manifestOk` → `LOCKED_REVALIDATE` "CONTENT_TAMPERED" (**hard**).
4. device-key fingerprint missing/changed → `LOCKED_REVALIDATE` "DEVICE_KEY_MISMATCH" (**hard**).
5. androidId hash changed → `LOCKED_REVALIDATE` "ANDROID_ID_MISMATCH" (**hard**).
6. signing-cert changed OR installer ≠ `com.android.vending` → soft: if `tripwiresLogOnly` → `ACTIVATED` ("SOFT_LOGGED:…"); else `LOCKED_REVALIDATE` (soft). *(hard beats soft)*
7. else → `ACTIVATED`.

**`decideHandshake(result, expectedManifestHash)`** (pure, post-Worker):
- `Activated` + hash echo matches → `SUCCESS`; hash mismatch → `LOCKED_REVALIDATE` "MANIFEST_ECHO_MISMATCH" (**hard**).
- `Denied` `UNLICENSED`/`UNRECOGNIZED` → `UNLICENSED`; any other deny → `LOCKED_REVALIDATE` (**hard**).
- `Retryable` → `BLOCKED_NO_INTERNET`.

**Orchestration:** `evaluateLocal()` (offline; `verifyCheap` + receipt + tripwires). `runHandshake()`
(`verifyFull` → `GET /nonce` → `requestToken` → `POST /activate` → on `SUCCESS` persist receipt; on
hard denial clear receipt).

**`DeviceSnapshot`** (gathered by the app layer, fed to the pure functions):
`deviceKeyFingerprint` (`DeviceKeyManager.publicKeyFingerprint()`), `androidIdHash`
(SHA-256 of `Settings.Secure.ANDROID_ID`), `installerPackage` (`getInstallSourceInfo`),
`appSigningCertSha256` (from `PackageInfo` signing info), `deviceSecurityLevel`
(`DeviceKeyManager.ensureKey().name`).

---

## 12. Test inventory

**Android JVM (`./gradlew :core:testDebugUnitTest -x verifyBundledAssets`) — 46, all green:**
`ContentManifestVerifierTest` 9 · `ActivationHostGuardTest` 6 · `ActivationApiParseTest` 9 ·
`PlayIntegrityRequestHashTest` 3 · `ActivationManagerTest` 19.

**Worker (`cd worker-activate && npm test`) — 25, all green:**
`nonce.test.js` (round-trip/expiry/forge/skew) · `integrity.test.js` (requestHash + verdict matrix) ·
`worker.test.js` (router via `TEST_MODE`, incl. proof `__test_decoded` is ignored when `TEST_MODE` off → fails closed 502).

**Not yet covered (instrumented/device tier, gated):** `DeviceKeyManager` on-device round-trip
(StrongBox Pixel 8 / TEE Lenovo), `verifyCheap` benchmark (sub-second target), live Play Integrity
verdict, end-to-end handshake against the deployed Worker.

---

## 13. Resume Playbook

> **Start here when a credential arrives.** Each block lists its precondition, the steps, and how to
> verify. Build everything code-side first, deploy last. Re-read [§4](#4-current-offline-lockdown-posture-the-thing-we-modify-carefully)
> before touching the manifest.

### 13.A — When the **Cloudflare API token** arrives (unblocks Worker deploy infra)

*Cannot fully deploy until the Google SA JSON is also present (the Worker needs it to function), but
you can configure secrets/vars and do a no-creds smoke first.*

1. `cd worker-activate && npm test` — confirm 25 green (regression guard).
2. `npx wrangler deploy --dry-run` — confirm clean bundle.
3. Set vars in `wrangler.toml`: confirm `PACKAGE_NAME=com.destructiveaigurus.katrinasmysticvisitorsguide`
   and `EXPECTED_MANIFEST_HASHES` = the current `manifestHash` (re-run the publish chain step 6 to print it).
4. `npx wrangler secret put NONCE_SECRET` (random ≥32 bytes).
5. Defer `wrangler deploy` until the SA secrets are set (next block) — a deployed Worker without SA
   creds returns `502 DECODE_FAILED` on every real `/activate`.

### 13.B — When the **Google Play Integrity SA JSON** arrives (unblocks real verdicts)

*Precondition: Play Integrity API enabled in the GCP project linked to the Play Console app.*

1. Store the SA JSON offline at `~/keys/` (0600, gitignored). Extract `client_email` + `private_key`.
2. `npx wrangler secret put SA_CLIENT_EMAIL` · `npx wrangler secret put SA_PRIVATE_KEY` (PKCS8 PEM, multi-line — paste exactly).
3. *(optional)* generate an RSA-2048 PKCS8 key for the assertion → `npx wrangler secret put ACTIVATION_SIGNING_KEY`.
4. `npx wrangler deploy` → note the assigned `activate-salem.<account>.workers.dev` host.
5. **Wire the host into the app:** set `ActivationHttpClientFactory.WORKER_HOST` to the real host
   (or route via `BuildConfig`). `ActivationHostGuard` will fail closed if it's wrong.
6. **Wire the GCP project number:** set `PlayIntegrityClient.CLOUD_PROJECT_NUMBER_PLACEHOLDER` to the
   real linked project number (or `BuildConfig`).
7. Add a Cloudflare **native rate-limit rule** (dashboard/API) on `/activate` — NOT app state.
8. Verify: `wrangler dev` (or live) — a genuine token activates; tampered/sample denies; nonce expiry
   honored; **no PII in logs**.

### 13.C — When the **Play Console internal-testing track** is live (unblocks live + S6/S7 wiring)

*Play Integrity verdicts are only meaningful for Play-distributed builds. Build the UI + wiring, then
flip the flag, then re-scope the manifest, then prove the strip.*

**Session 6 — gate UI + wiring:**
1. Build `ActivationActivity` + `ActivationViewModel` + `activity_activation.xml` + strings — 5 states:
   `CHECKING` / `SUCCESS` / `BLOCKED_NO_INTERNET` (hard-block, **Retry only**) / `UNLICENSED` /
   `LOCKED_REVALIDATE`. Back-button is a no-op.
2. Build the app-layer factory that assembles `ActivationManager` (gathers `DeviceSnapshot`, wires
   `ContentManifestVerifier.forAndroidAssets(R.raw.content_manifest_pubkey)` for `verifyCheap`/`verifyFull`,
   `ActivationStore`, `PlayIntegrityClient`, `ActivationApi` via `ActivationHttpClientFactory`,
   `BuildDefaults.ACTIVATION_BYPASS` + `ACTIVATION_TRIPWIRES_LOG_ONLY`).
3. `SplashActivity.launchMainActivity()` → retarget to `ActivationActivity` (pass `EXTRA_FROM_SPLASH`).
4. `SalemMainActivity` — defensive: if state ≠ ACTIVATED and not entered via activation → redirect + finish (anti-`am start` bypass).
5. **Flip `FeatureFlags.ACTIVATION_HANDSHAKE_ENABLED = true`** — only in this same change.
6. Verify on the internal track: real verdict path activates; debug build (`ACTIVATION_BYPASS=true`) still reaches Main.

**Session 7 — offline re-scope + hardening + rollout:**
1. Re-add `<uses-permission INTERNET />` + `ACCESS_NETWORK_STATE` to **`app-salem/src/main/AndroidManifest.xml`**
   (the release manifest — currently has neither; debug overlay already has them).
2. Add **release** `app-salem/src/main/res/xml/network_security_config.xml` — HTTPS-only, no pinning.
   (Debug LAN-cleartext overlay at `src/debug/res/xml/` stays as-is.)
3. **`apkanalyzer` strip-proof** on the release AAB/APK:
   - `apkanalyzer manifest print … | grep INTERNET` → present.
   - no cleartext permitted.
   - a **tampered release build actually LOCKS** — proves `ACTIVATION_BYPASS` const-folded out
     (do NOT assume R8 stripped it; verify).
4. Internal-track AAB upload (`:app-salem:bundleRelease`).
5. End-to-end: hard-block no-internet first run → `BLOCKED_NO_INTERNET`; one activation → offline-forever
   (airplane-mode cold starts reach Main, **zero sockets** in logcat); tamper/lock/recovery; **parked-services
   regression** (weather/MBTA/aircraft stay blocked despite INTERNET now granted — the 14 gated clients +
   `V1_OFFLINE_ONLY` unchanged).

**Session 8 — disclosures (counsel/operator):**
1. Play Data Safety: integrity token in transit, **no retention**.
2. Privacy-policy line (LLC-bound, OMEN-021) + store-listing "one-time internet to validate".
3. Play target audience = adults.
4. **Register the new creds in OMEN credential inventory** (NOTE-L023 #5): `content-manifest.pem`,
   Play Integrity SA JSON, Worker secrets (`NONCE_SECRET`, `SA_*`, `ACTIVATION_SIGNING_KEY`).

---

## 14. Phase 2 / 3 roadmap (NOT in this build)

**Phase 2 — content encryption** (additive on the Phase-1 handshake; no rewrite):
1. Separate Keystore `PURPOSE_ENCRYPT|DECRYPT` **wrap key** in `DeviceKeyManager` (distinct from the
   signing key — the documented hook).
2. Build-time AES-256 encrypt of bundled content; ship ciphertext.
3. Worker releases the content key over the existing `/activate` call, **only** on passing verdict + matching manifest.
4. Decrypt to **app-internal** storage (closes the `adb pull` hole; tampered content fails to decrypt).
5. Migrate `unverifiedLarge` (the 354 MB tile DB) into the encrypted set; runtime verifies by decrypt, not per-launch hash.

**Phase 3 — residual hardening** (the last ~1%): native NDK gate; anti-Frida/anti-debug.

**Out of scope entirely:** per-install **tracing**/watermark — would require retention, breaking the retain-nothing posture.

---

## 15. Open items & OMEN hand-offs

- **Ready to hand to OMEN now:** the endpoint contract (`worker-activate/CONTRACT.md`) as the
  NOTE-L023 #2 response; confirm `~/keys/content-manifest.pem` pathed (cred inventory #5); confirm
  the `*.workers.dev` decision so OMEN withdraws NOTE-DA005; ack NOTE-L023 #1 (pin dropped) / #3
  (unit tests: 46 Android + 25 Worker) / #4 (anti-replay residual recorded).
- **Owed at S8:** register the three new credentials in the OMEN inventory.
- **Carry:** on-device `DeviceKeyManager` round-trip (StrongBox Pixel 8 / TEE Lenovo) + `verifyCheap`
  sub-second benchmark — fold into the S6 device-validation pass.

---

## 16. File & symbol index

| Path | What |
|---|---|
| `docs/plans/OMEN-025-anti-piracy-phase1.md` | The authoritative session-by-session plan. |
| `worker-activate/` | Cloudflare Worker (contract, nonce, integrity, router, tests). |
| `core/src/main/java/com/example/locationmapapp/activation/` | Android Layer-1/2 + handshake code. |
| `core/src/test/java/com/example/locationmapapp/activation/` | 46 JVM tests. |
| `cache-proxy/scripts/sign-content-manifest.js` | Build-time manifest signer (publish-chain step 6). |
| `cache-proxy/scripts/verify-bundled-assets.js` | Post-bake sig + hash check. |
| `app-salem/src/main/res/raw/content_manifest_pubkey.der` | Committed Layer-2 public key. |
| `app-salem/src/main/assets/content-manifest.json` + `.sig` | Shipped manifest + signature. |
| `app-salem/.../ui/BuildDefaults.kt` | `ACTIVATION_BYPASS` / `ACTIVATION_TRIPWIRES_LOG_ONLY`. |
| `core/.../util/FeatureFlags.kt` | `V1_OFFLINE_ONLY`, `ACTIVATION_HANDSHAKE_ENABLED`. |
| `app-salem/src/{main,debug}/AndroidManifest.xml` | Release (no INTERNET) + debug overlay (INTERNET + cleartext). |
| `~/Development/OMEN/architecture/lma-anti-piracy-assessment.md` | OMEN-025 full threat model. |

---

*Created Session 296 (2026-05-24) as the OMEN-025 Phase 1 continuity reference. Keep current with
the activation architecture, keys, and contracts. Per-session narrative lives in
`docs/session-logs/`; this is the standing reference.*
