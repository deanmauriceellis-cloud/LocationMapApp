<a name="a6-play-store-checklist"></a>

# A6. Google Play Store Release Checklist

**Purpose:** Operational checklist of everything that needs to happen between today (2026-04-19) and the V1 ship target (2026-09-01). Counsel is asked to advise on the **legal / corporate** items (§1, §2) and to acknowledge the technical / asset items (§3, §4, §5) for scheduling purposes.

**Ordering:** Items are grouped by category, then by lead time within each category (longest-lead first).

---

<a name="a6-1-operator-setup"></a>

## 1. Operator setup — one-time corporate items (longest lead time)

These items have multi-week lead times and several chain together (cannot open a bank account without the EIN; cannot set up the Play Console merchant profile without the bank account).

- [ ] **C-corp formation** — the 2026-04-20 counsel meeting initiates. Paperwork typically 1–3 weeks to receive the incorporation certificate.
- [ ] **EIN from IRS** — apply online once the corp is formed. Typically 1–2 days, up to 4 weeks if mail-filed.
- [ ] **Business bank account** — requires incorporation certificate + EIN. Typically 1–2 weeks after EIN.
- [ ] **Google Play Developer Account registration** — $25 one-time at [play.google.com/console](https://play.google.com/console). Must be registered under the C-corp, not the operator's personal Google account.
- [ ] **Identity verification** — Google requires government-issued ID (individual) or D-U-N-S number (organization). For the C-corp path, request a D-U-N-S number from Dun & Bradstreet (free but can take 30 days).
- [ ] **Merchant / payments profile** — required to receive revenue from paid-app sales. Set up in Play Console → Payments profile. Link to the C-corp bank account. Google commission: 15% on the first $1M/yr, 30% thereafter.

---

<a name="a6-2-legal-policy"></a>

## 2. Legal / policy — counsel-reviewable items

- [ ] **Privacy Policy** — V1 draft at [§A3](#a3-privacy-policy-v1). Must be hosted at a public HTTPS URL and linked in Play Console + in-app.
- [ ] **Terms of Service** — V1 stub at [§A4](#a4-terms-of-service-stub). Optional but recommended.
- [ ] **Data Safety form** — Play Console questionnaire. Pre-filled answers at [§A5](#a5-data-safety-answers). All-None posture for V1.
- [ ] **Content rating — IARC questionnaire** — ~5 min, completed in Play Console. "Teen" rating expected based on witch-trials historical content.
- [ ] **Target audience + content declaration** — Play Console → App content → Target audience and content. Set to 13+.
- [ ] **Ads declaration** — "no ads" for V1.
- [ ] **Government apps declaration** — N/A.
- [ ] **COVID-19 apps declaration** — N/A.
- [ ] **News apps declaration** — N/A.
- [ ] **Financial features declaration** — N/A.
- [ ] **Health apps declaration** — N/A.

---

<a name="a6-3-technical"></a>

## 3. Technical build items (operator development, non-counsel)

- [ ] **Target SDK level** — verify `targetSdk` in `app-salem/build.gradle` meets current Play Store minimum (API 34+ for new apps as of 2025; check for 2026 bump). Compile with latest SDK.
- [ ] **AAB build** — verify `./gradlew bundleRelease` produces a signed `.aab`. Mandatory for Play Store; APK-only uploads rejected.
- [ ] **App signing key** — generate a release keystore if one doesn't exist. Store securely, never in git. Register credential in operator inventory. Enroll in Google Play App Signing (mandatory for new apps).
- [ ] **64-bit support** — verify no native libraries are 32-bit-only. Kotlin-only apps are fine; audit NDK dependencies.
- [ ] **ProGuard / R8 shrinking** — verify release build has `minifyEnabled true` with working ProGuard rules (no runtime crashes from stripped code).
- [ ] **Version code + version name** — set release version (e.g., `versionName "1.5.0"`, monotonically increasing `versionCode`).
- [ ] **Permissions audit** — confirm the `AndroidManifest.xml` requests only: `ACCESS_FINE_LOCATION`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_LOCATION`, `POST_NOTIFICATIONS` (for narration), possibly `WAKE_LOCK`. No `INTERNET` permission beyond what Google Play Services mandates.
- [ ] **Network-security config** — confirm TLS 1.2+, no cleartext traffic policy for any residual network activity.
- [ ] **APK size audit** — current debug APK is 780 MB, dominated by POI icon assets. Pre-release AAB must prune / compress before Play Store upload (Google imposes a 150 MB base APK limit; AAB delivery model handles some of this, but asset optimization is still required).
- [ ] **ProGuard / R8 + asset encryption** — all assets encrypted and obfuscated before first Play Store submission (operator's pre-V1 hard blocker).

---

<a name="a6-4-store-listing"></a>

## 4. Store listing assets (operator production)

- [ ] **App icon** — 512×512 PNG, no transparency, no rounded corners (Play Store applies its own mask). Library-tea-scene Katrina icon is ready.
- [ ] **Feature graphic** — 1024×500 PNG/JPEG (appears at top of store listing). Not yet produced.
- [ ] **Phone screenshots** — minimum 2, recommended 4–8. Show: (1) splash / Katrina illustration, (2) map view with GPS marker, (3) POI detail sheet with narration, (4) tour selection / Take-a-Tour card, (5) Witch Trials feature, (6) Find menu. Landscape or portrait; 1080×1920 typical.
- [ ] **Tablet screenshots** — optional but recommended. 1920×1080 landscape.
- [ ] **Short description** — 80 chars max. Punchy commercial hook. Draft: *"GPS-guided walking tours of historic Salem, narrated. Works fully offline."*
- [ ] **Full description** — up to 4000 chars. Feature list, historical content pitch, what makes it unique. Draft pending.
- [ ] **App category** — Travel & Local (most natural fit for GPS-guided Salem tours).
- [ ] **Contact email** — required. Operator's public-facing support address (same as Privacy Policy §2.9).
- [ ] **External market URL** — optional. Target: `destructiveaigurus.com/katrinas-mystic-guide/`.
- [ ] **Promo video** — optional. YouTube URL. Likely V2 content.

---

<a name="a6-5-pre-launch"></a>

## 5. Pre-launch testing tracks

- [ ] **Internal testing track** — upload AAB to internal track first; install on test devices (Lenovo TB305FU tablet + one Samsung phone). Verify paid-app license check doesn't block sideloaded test installs.
- [ ] **Pre-launch report (Firebase Test Lab)** — Play Console runs automated Robo testing on FTL devices. Review crash/ANR output before promoting to production.
- [ ] **Closed testing** — optional, 20-tester minimum for 14 days to unlock production release (Google 2024 rule).
- [ ] **Open testing** — optional.
- [ ] **Production release** — the final step, 2026-09-01 target.

---

<a name="a6-6-pricing-availability"></a>

## 6. Pricing and availability

- [ ] **Set base price** — $19.99 USD per operator decision.
- [ ] **Country availability** — default worldwide recommended. Operator can restrict if state tax or regulatory exposure drives that decision.
- [ ] **Regional pricing** — Google offers auto-conversion; operator can accept the default or set per-country prices.
- [ ] **Pre-order** — optional, can enable pre-order state ahead of launch.

---

<a name="a6-7-counsel-ack"></a>

## 7. Counsel acknowledgment

Counsel does not need to "approve" every item on this checklist — most are operator-executed development and asset work. The items counsel does need to own or advise on are:

- [ ] §1 Operator setup — corporate formation sequence and timeline.
- [ ] §2 Legal / policy — Privacy Policy, ToS, Data Safety, content rating.
- [ ] §6 Pricing and availability — state tax / regulatory exposure for the chosen country set.

[Back to top](#cover) | [Decision checklist →](#a7-decision-checklist)
