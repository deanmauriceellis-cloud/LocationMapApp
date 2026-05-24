# OMEN-025 Phase 1 — Purchase Validation + Content Integrity

## Context

**Why:** OMEN directive **OMEN-025** (issued 2026-05-23, LMA-specific, HIGH, pre-V1 gate) requires the paid Salem Tour app to protect against piracy via a **mandatory, one-time, first-run online validation that retains nothing**, layering purchase validation (Google Play Integrity) + content integrity (signed manifest), with content encryption deferred to Phase 2. Operator framing: kill ~99% of casual piracy, accept the skilled-RE 1%. Full threat model: `~/Development/OMEN/architecture/lma-anti-piracy-assessment.md`; relay `NOTE-L022`.

**Framing correction (operator, S293):** This does **not** reverse the offline product promise. The tour still runs fully offline — GPS, tiles, narration, POIs, zero network. OMEN-025 adds a *one-time licensing handshake in front of* that experience; after one successful activation the app is offline-forever. The only literal change is that `INTERNET` returns to the release manifest for that single handshake.

**Operator decisions (S293, revised S293 post-OMEN-S058 / NOTE-L023):** **hard-block** on no-signal first run (no grace window). Verifier = stateless Cloudflare Worker **owned in the LMA repo**, deployed to a plain `*.workers.dev` hostname (NOT on destructiveaigurus.com — operator declined entangling the marketing site; this also removes the OMEN cross-project relay NOTE-DA005). **Cert pinning DROPPED** (OMEN/operator, NOTE-L023 #1) — plain HTTPS + standard cert validation; the stale-pin-bricks-every-new-buyer risk outweighed a low-value MITM threat. Keep `ActivationHostGuard` (exact-host/no-redirect/no-cleartext) + HTTPS-only network config + runtime signing-cert self-check.

**Outcome:** a Play-licensed install on a genuine device activates once online, then runs offline forever; sideloaded/tampered copies fail to activate; tampered content is detectable (and cryptographically unusable in Phase 2).

---

## Current architecture (what we build on)

- **Offline lockdown = 4 coordinated pieces:** `FeatureFlags.V1_OFFLINE_ONLY` const (`core/.../util/FeatureFlags.kt`), `OfflineModeInterceptor` (`core/.../util/network/OfflineModeInterceptor.kt`, throws on any request), manifest INTERNET-strip (`app-salem/src/main/AndroidManifest.xml`; debug overlay re-adds), ~15 call-site gates (parked services — **leave intact**).
- **14 OkHttp clients** all inline `OfflineModeInterceptor`, all hit LAN cache-proxy `http://10.0.0.229:4300`. No `CertificatePinner`, no `EncryptedSharedPreferences`/Keystore usage anywhere yet.
- **First-run:** `SplashActivity` → `SalemMainActivity` (via `EXTRA_FROM_SPLASH`). No existing onboarding/consent gate. `minSdk 26`, R8 on in release, signing from `~/keys/wickedsalem-upload.jks`.
- **Content/manifest infra:** publish chain ends at `align-asset-schema-to-room.js` (rewrites `salem_content.db`); `verify-bundled-assets.js` already does dynamic Room-schema reads + `CI_MODE` — **extend, don't rebuild**. `salem_content.db` 5.3MB (committed); `salem_tiles.sqlite` 354MB (install-time asset pack); `heroes/`/`hero/`/`poi-icons/` ~27MB.
- **Verifier host:** destructiveaigurus.com is **static GitHub Pages + Cloudflare** — no server runtime → a **Cloudflare Worker** is the mechanism (stateless by design, matches retain-nothing).

---

## Operator prerequisites (Session 0 — partly GATING)

1. **[BLOCKING for live test] Play Console** app (`com.destructiveaigurus.katrinasmysticvisitorsguide`) on an internal-testing track — Play Integrity verdicts are only meaningful for Play-distributed builds. *Per project state the Play Console account is still in setup → this is the critical-path blocker for live verification.* All unit-level work proceeds in parallel without it.
2. **Google Cloud project** linked to the Play Console app; enable **Play Integrity API**.
3. **Service account** with Play Integrity access; SA JSON stored offline at `~/keys/` (gitignored, OMEN-002).
4. **Cloudflare:** a scoped `wrangler` API token on the operator's Cloudflare account (a `*.workers.dev` deploy needs no custom DNS record); token stored offline, not in any repo.
5. **Manifest signing keypair (one-time):** `openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out ~/keys/content-manifest.pem` + extract public key. *(RSA-2048, not Ed25519 — Android `java.security` Ed25519 verify is API 33+ and minSdk is 26; RSA verifies on all levels with no extra dep.)* Private key offline at `~/keys/` (0600, gitignored); public key committed to `app-salem/src/main/res/raw/content_manifest_pubkey`.

---

## Architecture decisions

- **D1 — Dedicated `ActivationHttpClient`, NOT a host-exception in `OfflineModeInterceptor`.** A physically separate OkHttpClient that never installs the offline gate, talks only to the activation host (HTTPS + an `ActivationHostGuard` exact-host interceptor rejecting redirects/non-HTTPS; no `CertificatePinner` per NOTE-L023 #1). Preserves the default-deny invariant for the 14 gated clients; parked services stay blocked.
- **D2 — Gate lives in a new `ActivationActivity`** between Splash and Main (not in `WickedSalemApp.onCreate` → ANR/no-UI risk; not in Splash → TTS-timing coupling). Already-activated launches verify the local receipt only (no network, few ms) and forward immediately.
- **D3 — Revalidate on VIOLATION, never on a timer** (operator). `ACTIVATED ⇄ LOCKED-pending-revalidation`. Normal activated launches do zero network.
- **D4 — Tiered hashing.** Manifest-signature verify every launch (cheap). `salem_content.db` (~5MB) hashed every launch. The 354MB tile DB + big image dirs are **out of Phase-1 runtime scope** (multi-second hash on low-end phones) — recorded in the manifest as `unverifiedLarge` (size only) so Phase 2 can opt into chunked hashing without a format change. Rationale: `salem_content.db` is the high-value tamper target; tiles/images are bulk, low-tamper-value.
- **D5 — Debug bypass reuses the proven `BuildConfig.RECON_DEFAULTS` pattern** (`BuildDefaults.ACTIVATION_BYPASS = BuildConfig.RECON_DEFAULTS`) — const-folds to false + R8-strips in release, identical safety story to `SuperAdminMode`. Lets dev devices (sideloaded, no real Play verdict) test the flow.
- **D6 — Stateless Cloudflare Worker, LMA-owned, on `*.workers.dev`** (revised: NOT destructiveaigurus.com). Code lives in the LMA repo (`worker-activate/`), deploys to the operator's Cloudflare account at e.g. `activate-salem.<account>.workers.dev`. No marketing-site entanglement, no custom domain, no cross-project relay. **Stateless HMAC time-boxed nonce** (no KV) — anti-replay leans on Play Integrity token freshness, recorded as a residual (NOTE-L023 #4). **Standard `decodeIntegrityToken` API call** (SA-JWT minted via Web Crypto), reads `appLicensingVerdict`=LICENSED + `appRecognitionVerdict`=PLAY_RECOGNIZED + `deviceIntegrity`. **Retain-nothing:** no KV/D1/logging of token/IP/device; rate-limit via Cloudflare native rules. Worker knows the expected manifest hash via a deploy-time `EXPECTED_MANIFEST_HASHES` var (current+previous, for staged rollout). *(Alternative if consolidating on Google: Cloud Run in the Play Integrity GCP project — `*.run.app`.)*
- **D-pin — DROPPED** (NOTE-L023 #1). No `CertificatePinner`. Plain HTTPS + standard cert validation; defense kept at `ActivationHostGuard` (exact-host/no-redirect/no-cleartext) + HTTPS-only `network_security_config` + runtime signing-cert self-check. Removes the highest-consequence failure mode (stale pin bricking all new buyers).

---

## Implementation — files

### Android (new — `core` `com.example.locationmapapp.activation.*`)
- `DeviceKeyManager.kt` — Keystore EC P-256 signing key, StrongBox if `API>=28 && FEATURE_STRONGBOX_KEYSTORE` else TEE (catch `StrongBoxUnavailableException` → retry without), `setUserAuthenticationRequired(false)`. Phase-1 use: sign the receipt. KDoc the Phase-2 (separate encrypt/decrypt wrap key) hook.
- `ActivationReceipt.kt` / `ActivationStore.kt` — receipt model (device-key fingerprint, androidId hash, installer pkg, app signing-cert SHA-256, manifest hash, verdict, activatedAt) persisted in `EncryptedSharedPreferences`, **additionally detached-signed by `DeviceKeyManager`** (tamper-evident even if prefs lifted).
- `ActivationManager.kt` — state machine + orchestration. `evaluateLocal()` (offline: verify receipt sig + tripwires) / `runHandshake()` (online). Hard violations (sig invalid / device-key or androidId mismatch) + soft tripwires (installer≠`com.android.vending`, signing-cert self-check, debugger/emulator/Frida heuristics — debug=log-only) → LOCKED.
- `PlayIntegrityClient.kt` — `StandardIntegrityManager`; `requestHash = SHA256(serverNonce || contentManifestHash)`.
- `ActivationHttpClientFactory.kt` / `ActivationApi.kt` — D1 client; `GET /nonce`, `POST /activate {integrity_token, content_manifest_hash}`. **No `CertificatePinner`** (NOTE-L023 #1) — plain HTTPS + `ActivationHostGuard` (exact-host/no-redirect/no-cleartext).
- `ContentManifestVerifier.kt` — embedded RSA pubkey, manifest-sig verify, tiered hashing (D4); stream large files via `DigestInputStream`.

### Android (new — JVM/Robolectric unit tests, OMEN-004 / NOTE-L023 #3)
- `ActivationManagerTest` (ACTIVATED⇄LOCKED transitions, hard vs soft violation paths), `ContentManifestVerifierTest` (sig-valid / sig-invalid / byte-flipped-asset / stale-manifest), receipt sign→verify round-trip. Device-only bits (StrongBox, Play Integrity) stay in the instrumented/manual tier.

### Android (new — `app-salem`)
- `ActivationActivity.kt` + `ActivationViewModel.kt` + `activity_activation.xml` + strings — screen states: CHECKING / SUCCESS / **BLOCKED_NO_INTERNET (hard-block, Retry only)** / UNLICENSED / LOCKED_REVALIDATE. Back-button no-op.
- `res/raw/content_manifest_pubkey`.

### Android (modify)
- `core/.../util/FeatureFlags.kt` — add `ACTIVATION_HANDSHAKE_ENABLED`; KDoc update. **Do not change `V1_OFFLINE_ONLY`.**
- `app-salem/src/main/AndroidManifest.xml` — re-add `INTERNET` + `ACCESS_NETWORK_STATE`; register `ActivationActivity`; add release `res/xml/network_security_config.xml` (HTTPS-only, no pinning). Debug overlay stays (LAN cleartext).
- `SplashActivity.kt` — retarget `launchMainActivity()` → `ActivationActivity` (pass `EXTRA_FROM_SPLASH` through).
- `SalemMainActivity.kt` — defensive: if state≠ACTIVATED and not via activation, redirect + finish (anti-`am start` bypass).
- `BuildDefaults.kt` — `ACTIVATION_BYPASS` + `ACTIVATION_TRIPWIRES_LOG_ONLY` (= `BuildConfig.RECON_DEFAULTS`).
- `core/build.gradle` + `app-salem/build.gradle` — add `com.google.android.play:integrity` + `androidx.security:security-crypto`.
- `app-salem/proguard-rules.pro` — keep rules for Play integrity, `androidx.security.crypto`/Tink, and Gson-serialized `activation.**` models.

### Build pipeline (cache-proxy)
- **NEW** `cache-proxy/scripts/sign-content-manifest.js` — runs **after** `align-asset-schema-to-room.js`; globs in-scope assets, SHA-256 each, asserts manifest's Room id/version == what align stamped, signs with `~/keys/content-manifest.pem`, emits `content-manifest.json` + `.sig` into `app-salem/src/main/assets/`, prints `manifestHash`. Mirror `verify-bundled-assets.js` dynamic-schema-read + `CI_MODE` (CI emits manifest unsigned — no private key on CI by design).
- **MODIFY** `verify-bundled-assets.js` — verify manifest sig + in-scope hashes match (catches stale manifest).
- **MODIFY** `CLAUDE.md` — publish-chain step 6 (sign-content-manifest, MUST-run-after-align note) + `~/keys/content-manifest.pem` in Key Paths.
- **MODIFY** `app-salem/src/main/assets/ASSETS-MANIFEST.md` — document new shipped assets + D4 scope rationale.

### Worker (LMA-owned, in the LMA repo — `worker-activate/`)
- Revised (NOTE-L023 #2 + operator declined DAG.com): the Worker is an LMA artifact, NOT in the DestructiveAIGurus.com repo, deployed to `*.workers.dev`. `wrangler.toml` (`workers.dev` route, `EXPECTED_MANIFEST_HASHES` var), `src/index.js` (router), `src/integrity.js` (SA-JWT + `decodeIntegrityToken`), `src/nonce.js` (HMAC), `README.md`. Secrets via `wrangler secret put` (`SA_PRIVATE_KEY`, `SA_CLIENT_EMAIL`, `NONCE_SECRET`, `ACTIVATION_SIGNING_KEY`). Retain-nothing banner; no PII logging. No cross-project relay (NOTE-DA005 withdrawn). *(Alt: Cloud Run in the Play Integrity GCP project.)*

### Anti-replay residual (NOTE-L023 #4 — recorded decision)
The stateless HMAC nonce cannot enforce single-use; anti-replay leans on Play Integrity token freshness. Acceptable here: the receipt is device-key-bound and the casual-piracy threat is sideload-of-a-copy (fails `PLAY_RECOGNIZED`), not token replay. Recorded so it's a decision, not an omission.

---

## Phase 1 — sessions & steps

> This whole document is **OMEN-025 Phase 1** (Layers 1+2 + handshake + disclosures). Phase 2 (content encryption) and Phase 3 (native gate) are in **Out of scope** below.
> Status legend: ✅ done · ◻ todo · ⏸ blocked (gated on Play Console / external). Per OMEN NOTE-L023, Sessions 1–3 are cleared; 6–7 gate on the Play Console internal track (← operator P.O. Box / D-U-N-S).

### Session 1 — Build-time signed manifest (Layer 2) — ✅ DONE (S293)
1. ✅ Generate RSA-2048 keypair → `~/keys/content-manifest.pem` (0600, gitignored); commit public DER → `app-salem/src/main/res/raw/content_manifest_pubkey.der`.
2. ✅ `cache-proxy/scripts/sign-content-manifest.js` — hash in-scope assets, assert `roomIdentityHash`==`room_master_table` id=42, record `unverifiedLarge`, sign → `content-manifest.json` + `.sig`, print `manifestHash`. CI-mode = unsigned + warn.
3. ✅ Extend `verify-bundled-assets.js` — manifestHash self-consistency + per-file disk-hash match + sig verifies against committed pubkey.
4. ✅ Docs — `CLAUDE.md` publish-chain step 6 + Key Paths; `ASSETS-MANIFEST.md` content-manifest section + D4 rationale.
5. ✅ Test — `openssl` verifies sig; byte-flip fails (3 failures, exit 1); CI unsigned path; restore PASS.

### Session 2 — Android foundations (device key + receipt store) — ✅ DONE (S293)
1. ✅ `core/build.gradle` — add `play:integrity:1.4.0` + `security-crypto:1.1.0-alpha06`.
2. ✅ `core/.../activation/DeviceKeyManager.kt` — EC P-256 Keystore key, StrongBox→TEE fallback, device-bound not user-auth-bound; `ensureKey`/`sign`/`verify`/`publicKeyFingerprint`.
3. ✅ `ActivationReceipt.kt` — Gson model (fingerprint, androidId hash, installer, signing-cert sha256, manifest hash, verdict, securityLevel, bigAssetsVerified, schemaVersion).
4. ✅ `ActivationStore.kt` — EncryptedSharedPreferences + detached device-key signature; `load()` rejects copied/tampered receipts.
5. ✅ `app-salem/proguard-rules.pro` — keep `activation.**` (Gson), Play integrity, security-crypto/Tink.
6. ✅ `./gradlew :core:compileDebugKotlin` SUCCESSFUL.
7. ◻ (carry) on-device DeviceKeyManager round-trip — StrongBox (Pixel 8) / TEE (Lenovo); instrumented tier.
8. ◻ (carry) full `:app-salem:bundleRelease` assemble-check.

### Session 3 — Content manifest verifier (Android) — ✅ DONE (S295, code+tests; on-device benchmark carried)
1. ✅ Public-key load path — `KeyFactory("RSA")` + `X509EncodedKeySpec` over the SPKI DER; production read via `forAndroidAssets(context, R.raw.content_manifest_pubkey)` (app passes the resId; `:core` stays decoupled from `:app-salem`'s R).
2. ✅ `core/.../activation/ContentManifestVerifier.kt` — `Signature("SHA256withRSA")` verifies `.sig` over the EXACT `content-manifest.json` bytes (read verbatim, never reserialized), then parses the now-trusted manifest.
3. ✅ Tiered hashing (D4) — `verifyCheap()` (sig + recompute in-scope file SHA-256 + canonical `manifestHash` self-consistency, every launch) + `verifyFull()` (adds bulk-asset presence/size via injectable `largeAssetStat`; tile DB in the asset pack stays Phase-2-deferred → `largeVerified=false` but pass). Large files streamed via `DigestInputStream`. Pure-JVM-testable: all inputs are injected lambdas/bytes, no `Context`/`AssetManager`/`android.util.Log` on the verified path.
4. ✅ JVM unit tests (OMEN-004 #3, part 1) — `core/src/test/.../ContentManifestVerifierTest.kt`, **9 tests, 0 failures**: sig-valid / sig-invalid / byte-flipped-asset / stale-manifestHash / tampered-manifest-body / empty-sig / verifyFull size-match (+tile-DB-deferred) / verifyFull size-drift / **canonical-hash == real build value `978937…`** (the contract that the on-device `manifestHash` equals the Worker's `EXPECTED_MANIFEST_HASHES`).
5. ◻ (carry, instrumented tier) Benchmark `verifyCheap` on Pixel 8 + Lenovo — confirm sub-second (5 MB SHA-256; best done when Session 6 wires it into the activation flow, alongside S2's deferred on-device key round-trip).

### Session 4 — Activation Worker (LMA repo, `*.workers.dev`) — ✅ steps 1–6 DONE (S296); deploy ⏸
1. ✅ **Endpoint contract** → `worker-activate/CONTRACT.md` (`/nonce`, `/activate {integrity_token, content_manifest_hash, nonce}`, request/response shapes, error-code table, `EXPECTED_MANIFEST_HASHES` current+previous, `package_name` is Worker-config not client-supplied). Ready to hand to OMEN as the NOTE-L023 #2 response.
2. ✅ `worker-activate/` scaffold — `wrangler.toml` (workers.dev, `EXPECTED_MANIFEST_HASHES`=live `978937…`/`PACKAGE_NAME`/`NONCE_TTL_MS`/`TOKEN_MAX_AGE_MS` vars), `src/index.js` router, `package.json`, `.gitignore`, `README.md`.
3. ✅ `src/nonce.js` — 40-byte HMAC time-boxed stateless nonce (`ts‖rand‖HMAC[:16]`), Web Crypto, timing-safe compare + future-skew guard.
4. ✅ `src/integrity.js` — SA-JWT mint (Web Crypto RSASSA-PKCS1-v1_5 → OAuth2 exchange) → `decodeIntegrityToken`; pure `evaluateVerdict` (license/recognition/device/requestHash/package/freshness); `requestHash` byte-matched to the Android client; optional `signAssertion`.
5. ✅ Retain-nothing (no KV/D1/DO, no PII logging); Cloudflare native rate-limit documented (`wrangler.toml`).
6. ✅ `node --test` — **25 tests** (nonce round-trip/expiry/forge/skew; requestHash + full verdict matrix; router via `TEST_MODE` incl. fails-closed-when-TEST_MODE-off→502). `wrangler deploy --dry-run` bundles clean.
7. ⏸ Deploy to `*.workers.dev` — needs Cloudflare token + Google SA JSON (operator/OMEN).

### Session 5 — Activation network path (Android) — ✅ steps 1–3 DONE (S296); 4 → S6, 5 deploy-gated
1. ✅ `ActivationHostGuard.kt` — exact-host / HTTPS-only / no-redirect interceptor, pure `assertAllowed(HttpUrl)`, no `CertificatePinner`. **6 JVM tests.**
2. ✅ `ActivationHttpClientFactory.kt` — dedicated client, **no** `OfflineModeInterceptor` (D1), `followRedirects(false)`, placeholder `WORKER_HOST`.
3. ✅ `ActivationApi.kt` — `fetchNonce()`/`activate()` + models; pure `parseActivateResponse` (Activated/Denied/Retryable matrix). **9 JVM tests.**
4. ✅ (delivered as part of S6 — `ActivationManagerTest`, since the manager is S6) — state transitions, **19 JVM tests**.
5. ⏸ HTTPS + host-guard fails-closed test against the *deployed* Worker — deploy-gated.

### Session 6 — Play Integrity + state machine + gate UI — ✅ core DONE (S296); UI/wiring deferred ⏸
1. ✅ `PlayIntegrityClient.kt` — `StandardIntegrityManager` token request (Android tier) + pure `computeRequestHash = base64url(SHA256(nonce‖manifestHash))`, **byte-matched to the Worker value** (`8DORFCkZ…`, 3 JVM tests). `cloudProjectNumber` placeholder (gated).
2. ✅ `ActivationManager.kt` — pure `decideLocal` (ACTIVATED/NEEDS_ACTIVATION + hard violations sig/device-key/androidId + soft tripwires installer/cert, log-only-aware) + `decideHandshake` + `buildReceipt`; injected orchestration `evaluateLocal()`/`runHandshake()`. **19 JVM tests.**
3. ✅ `BuildDefaults.ACTIVATION_BYPASS` + `ACTIVATION_TRIPWIRES_LOG_ONLY` (= `BuildConfig.RECON_DEFAULTS`); `FeatureFlags.ACTIVATION_HANDSHAKE_ENABLED=false` (ships OFF until Worker deployed).
4. ◻ **(DEFERRED)** `ActivationActivity` + `ActivationViewModel` + `activity_activation.xml` + strings (5 states; hard-block, Retry-only).
5. ◻ **(DEFERRED)** Wiring — `SplashActivity` → `ActivationActivity` → Main; `SalemMainActivity` defensive redirect; flip `FeatureFlags.ACTIVATION_HANDSHAKE_ENABLED`.
6. ⏸ Real verdict validation on internal track.

> **S296 note:** the deferred S6 items (4, 5) alter the launch flow and are only meaningfully testable against a deployed Worker on the internal Play track, so they were intentionally held with S7. All S6 logic (decision state machine, requestHash binding, flags) is implemented + unit-tested now. Activation JVM tier = **46 tests** (9 ContentManifestVerifier + 6 HostGuard + 9 ApiParse + 3 PlayIntegrityRequestHash + 19 ActivationManager); Worker = **25 tests**.

### Session 7 — Offline re-scope + hardening + rollout — ⏸ (Play Console)
1. ◻ Re-add `INTERNET` + `ACCESS_NETWORK_STATE` to `src/main/AndroidManifest.xml`.
2. ◻ Release `res/xml/network_security_config.xml` — HTTPS-only (debug LAN-cleartext overlay stays).
3. ◻ `apkanalyzer` proof — INTERNET present, no cleartext, and a tampered release **actually LOCKS** (bypass const-folded out).
4. ◻ Internal-track AAB upload.
5. ◻ End-to-end — hard-block no-internet, offline-forever, tamper/lock/recovery, parked-services regression (weather/MBTA/aircraft stay blocked).

### Session 8 — Disclosures (counsel/operator) — ◻
1. ◻ Draft Play Data Safety entry (integrity token in transit, **no retention**).
2. ◻ Draft privacy-policy line (LLC-bound, OMEN-021) + store-listing "one-time internet to validate".
3. ◻ Set Play Console target audience = adults.
4. ◻ Register new creds (`content-manifest.pem`, Play Integrity SA JSON, Worker secrets) in OMEN credential inventory (NOTE-L023 #5).

---

## Verification (mapped to the session that closes it)

- **Signer (S1 ✅):** post-bake, `.sig` verifies via `openssl`; byte-flip an asset → hash/sig mismatch caught by `verify-bundled-assets.js`.
- **Device key (S2/S3):** `DeviceKeyManager` sign→verify round-trip on device (StrongBox Pixel 8 / TEE Lenovo); receipt copied to another device fails `ActivationStore.load()`.
- **Manifest verifier (S3):** `verifyCheap` sub-second on both devices; sig-invalid / byte-flipped / stale-manifest all rejected (JVM tests).
- **Worker (S4):** `wrangler dev` returns deny on a tampered/sample token, ok on a genuine one; nonce expiry honored; no PII in logs.
- **Network path (S5):** HTTPS + host-guard fails closed on a wrong/off-host/cleartext URL.
- **Hard-block (S6/S7):** airplane mode on first run → BLOCKED_NO_INTERNET, no tour access; Retry works on reconnect.
- **Offline-forever (S7):** after one activation, airplane-mode cold starts repeatedly reach Main with zero sockets (logcat).
- **Tamper/lock (S7):** re-sign APK (cert mismatch) / change ANDROID_ID / attach debugger / emulator → LOCKED → re-handshake recovers; byte-flip `salem_content.db` → manifest verify fails.
- **Parked-services regression (S7):** weather/MBTA/aircraft stay blocked in release despite INTERNET now granted (14 gated clients + `V1_OFFLINE_ONLY` unchanged).
- **Release-strip proof (S7):** `apkanalyzer manifest print` shows INTERNET + HTTPS-only + no cleartext; a tampered release build actually **LOCKS** (proves `ACTIVATION_BYPASS` const-folded out — do not assume R8 stripped it).

## Phase 2 — content encryption (roadmap, additive — NOT in this build)
Builds on the Phase-1 handshake; no rewrite. High level:
1. ◻ Separate Keystore `PURPOSE_ENCRYPT|DECRYPT` wrap key in `DeviceKeyManager` (distinct from the signing key — the documented hook).
2. ◻ Build-time AES-256 encrypt of bundled content; ship ciphertext.
3. ◻ Worker releases the content key over the existing `/activate` call, **only** on passing verdict + matching manifest.
4. ◻ Decrypt to **app-internal** storage (closes the `adb pull` hole; makes Layer-2 integrity cryptographically enforced — tampered content simply fails to decrypt).
5. ◻ Migrate `unverifiedLarge` (the 354 MB tile DB) into the encrypted set; runtime verifies by decrypt, not per-launch hash.

## Phase 3 — residual hardening (optional, the last ~1%)
1. ◻ Move the critical gate into native NDK code.
2. ◻ Anti-Frida / anti-debug checks.

*Out of scope entirely:* **tracing** fingerprinting (per-install watermark) — would require retention and break the retain-nothing posture.
