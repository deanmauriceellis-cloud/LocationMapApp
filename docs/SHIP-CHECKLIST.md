# V1 Ship Checklist — Katrina's Mystic Visitors Guide

Last updated: 2026-05-12 (S255 Wave 2).

Run through this in order before pushing a signed AAB to Play Console. Every step must complete successfully — if any fail, do not upload.

## 1. Signing keystore + secrets

The release keystore is the irrecoverable artifact. If you lose it, the app cannot be updated.

- [ ] **Keystore path:** `~/keys/wickedsalem-upload.jks` exists locally (mode 0600).
- [ ] **Off-machine USB copy:** at least one separate physical USB with `wickedsalem-upload-key-backup-<YYYY-MM-DD>/` directory containing `wickedsalem-upload.jks` + a copy of `~/.gradle/gradle.properties`. Stored at a different physical location than the dev box.
- [ ] **SECOND off-machine copy still owed** (operator carry-forward since S188). Encrypted cloud OR second USB at a different location than the first. Single-medium backups fail.
- [ ] **Signing properties:** `~/.gradle/gradle.properties` (mode 0600) contains:
  - `WICKED_SALEM_KEYSTORE_PATH=/home/<user>/keys/wickedsalem-upload.jks`
  - `WICKED_SALEM_KEYSTORE_PASSWORD=<password>`
  - `WICKED_SALEM_KEY_ALIAS=upload`
  - `WICKED_SALEM_KEY_PASSWORD=<password>`
- [ ] `keytool -list -v -keystore ~/keys/wickedsalem-upload.jks` runs clean and the SHA-1 / SHA-256 fingerprints match what Play Console expects (re-check on first upload; sticky after).

## 2. Operator-side legal / business

These are operator-owned. None of them is a Claude-side blocker but all are required before Play Console accepts the AAB.

- [ ] **Form TX copyright** filed at copyright.gov. Hard deadline **2026-05-20**. Filing must name **Destructive AI Gurus, LLC** (Filing # 202626416630) as claimant, not Dean Ellis personally.
- [ ] **Google Play Developer Account** registered under **Destructive AI Gurus, LLC**. Multi-week ID-verification clock — start ASAP.
- [ ] **Privacy Policy** publicly hosted at a stable URL + in-app link present. Source: `docs/PRIVACY-POLICY-V1.md` v1.1 (all 4 placeholders backfilled to DAG LLC in S245). Counsel approval pending.
- [ ] **Operating agreement** filed if counsel recommends it (single-member MA LLC doesn't require, but veil protection improves with an OA on file).
- [ ] **Merchant / payments profile** set up in Play Console for paid distribution. Pricing locked at **$19.99 flat paid** (S138 / project_business_model.md).
- [ ] **IARC questionnaire** completed in Play Console targeting **Teen** rating (operator memory: PG-13 / IARC Teen — `feedback_pg13_content_rule.md`).

## 3. Build verification (pre-bundle)

Run from the repo root.

- [ ] Fresh PG admin edits flushed via the publish chain (per `feedback_publish_chain_manual_after_admin_edits.md`):
  ```
  node cache-proxy/scripts/publish-salem-pois.js
  node cache-proxy/scripts/publish-tours.js
  node cache-proxy/scripts/publish-tour-legs.js
  node cache-proxy/scripts/align-asset-schema-to-room.js
  ```
- [ ] `node cache-proxy/scripts/verify-bundled-assets.js` exits 0 (every required table present, every required dir non-empty).
- [ ] `./gradlew :app-salem:assembleDebug -PskipPublishChain` — sanity build, expect clean.
- [ ] `./gradlew :app-salem:lintRelease` — clean against the committed baseline (`app-salem/lint-baseline.xml`).
- [ ] `./gradlew :routing-jvm:test` — 10/10 pass (parity fixtures aligned with bundled `salem-routing-graph.sqlite`).

## 4. Release build

- [ ] `./gradlew :app-salem:bundleRelease` — signed AAB lands at `app-salem/build/outputs/bundle/release/app-salem-release.aab`. Track size against the **200 MB compressed download ceiling** (or, with Asset Pack wired, the **150 MB base + 1 GB Asset Pack** install-time delivery).
- [ ] `apkanalyzer manifest permissions app-salem-release.aab` (or extract base APK first) returns **zero INTERNET / ACCESS_NETWORK_STATE** entries. V1 ship-gate.
- [ ] `apkanalyzer manifest print` confirms `android:largeHeap="true"` + `android:allowBackup="false"` on release.
- [ ] `apkanalyzer dex code --class com.example.locationmapapp.util.SuperAdminMode <aab>` returns "class not found". S249 R8-strip paranoia satisfied.
- [ ] (Optional, recommended) `bundletool build-apks --bundle <aab> --output app-salem.apks --mode universal` produces a universal APK that installs and launches on Lenovo HNY0CY0W.

## 5. Eyes-on smoke on Lenovo HNY0CY0W

Install via `adb uninstall <pkg> && adb install <apk>` — NEVER `install -r` (per `feedback_adb_install_after_db_rebake.md`).

- [ ] App launches without crash, splash → SalemMainActivity.
- [ ] WickedMap basemap renders at startup zoom; pan + pinch work cleanly.
- [ ] Tilt slider to 60° + zoom to 19 — no OOM, no chunked frames. (Wave 1 `largeHeap` should clear this.)
- [ ] Walk-sim a complete tour (e.g., `tour_essentials`) end-to-end with TTS narration audible at each tour-POI fence.
- [ ] Find dialog → searches a POI → POI detail opens with hero image (heroes/<slug> or hero/<uuid> tier resolves).
- [ ] Layers menu toggles a category off and on; markers reflect the change.
- [ ] About dialog shows **Katrina's Mystic Visitors Guide v1.5.55** (or current versionName), copyright Destructive AI Gurus, LLC, email contact@destructiveaigurus.com.
- [ ] App in airplane mode — full tour walk-sim still works, no toast errors about network.
- [ ] No `Log.e` or `FATAL EXCEPTION` lines in `adb logcat` during a 10-minute session.

## 6. Play Console upload

- [ ] Upload `app-salem-release.aab` to the internal testing track first. Wait for processing.
- [ ] Review the Play Console-generated pre-launch report. No crashes, no security warnings.
- [ ] Internal testers (including operator) install via the internal-testing opt-in link and verify section 5 again on retail-channel install.
- [ ] Promote internal → closed-alpha (or production) only after the internal pass.

## 7. Post-upload paperwork

- [ ] Add the Play Console SHA-1 / SHA-256 signing-key fingerprints (Play Console → App signing) to `IP.md` for future reference.
- [ ] Confirm Play App Signing is enabled (Google manages the actual release-signing key after upload key handoff).
- [ ] Tag the commit: `git tag v1.5.<patch>-play-internal && git push origin v1.5.<patch>-play-internal`.

---

## Reference paths

- Upload keystore: `~/keys/wickedsalem-upload.jks`
- Signing properties: `~/.gradle/gradle.properties` (mode 0600)
- AAB output: `app-salem/build/outputs/bundle/release/app-salem-release.aab`
- APK output: `app-salem/build/outputs/apk/release/app-salem-release.apk`
- Room schemas: `app-salem/schemas/com.example.wickedsalemwitchcitytour.content.db.SalemContentDatabase/<v>.json`
- Bundled-asset verifier: `cache-proxy/scripts/verify-bundled-assets.js`
- Publish chain: `cache-proxy/scripts/publish-salem-pois.js` → `publish-tours.js` → `publish-tour-legs.js` → `align-asset-schema-to-room.js`

## See also

- `docs/PRIVACY-POLICY-V1.md` — V1 privacy policy (counsel review pending)
- `IP.md` — copyright + Form TX status, V1-novel patentable angles
- `COMMERCIALIZATION.md` — V1 pricing + channels (Salem Chamber + local), merchant tier
- `feedback_publish_chain_manual_after_admin_edits.md` — why the manual publish step matters
- `feedback_adb_install_after_db_rebake.md` — why never `install -r`
