# Must-Have #10 — Consolidated Legal Walkthrough for Counsel

**Purpose:** Single document the operator can walk through with counsel at the 2026-04-20 C-corp formation meeting. Consolidates the legal, commercial, and compliance decisions that need to be made before uploading the V1 build to the Google Play Store. Where a decision is captured in detail elsewhere, this document points there rather than duplicating.

**Target reading time for counsel:** 20-30 minutes.

**Lawyer packet contents:**
- `docs/lawyer-packet/10-legal-walkthrough.md` — this file (overview + open-decision menu)
- `docs/lawyer-packet/11-pricing-and-age-gate.md` — pricing and age-gate posture detail
- `docs/PRIVACY-POLICY-V1.md` — V1-minimal Privacy Policy draft (Posture A)

**Supporting reference documents (in the repo, available if counsel wants depth):**
- `GOVERNANCE.md` — 1,156-line legal/compliance framework (comprehensive background)
- `IP.md` — 14 patentable innovations + protection roadmap
- `COMMERCIALIZATION.md` — full commercial strategy
- `docs/PRIVACY-POLICY.md` — full OMEN-008-compliant future-state Privacy Policy (held in reserve for future versions that collect radio-environment data; **not** the V1 policy)
- OMEN NOTE-L017 — Google Play Store release checklist (`~/Development/OMEN/notes/locationmapapp.md`)

---

## 1. What the app is, in one paragraph

*Katrina's Mystic Visitors Guide* is a GPS-guided walking-tour application for Salem, Massachusetts, targeting the 1M+ visitor surge for the 2026 Salem 400+ quadricentennial. The app bundles a map of downtown Salem with ~1,837 points of interest, five pre-built walking tours, narration that plays automatically as the user walks past historical sites, and a historical Witch Trials content experience (Oracle-styled 1692 newspapers, portraits of 49 key figures, and cross-linked narratives). V1 is **fully offline** — all content is bundled inside the app at install time, and the app transmits no data during use. Intended ship window: Google Play Store before **2026-09-01** to capture October 2026 visitor traffic.

---

## 2. Open decisions counsel is being asked to drive on Monday

These are the items that require counsel sign-off or operator-with-counsel decision. Each has a full-detail section further down in this document.

| # | Decision | Section |
|---|---|---|
| 2.1 | Operating entity — C-corp formation (state, name, registered agent) | §3 |
| 2.2 | Final app name (*Katrina's Mystic Visitors Guide* is the current working title) | §4 |
| 2.3 | Domain / hosting URL strategy (DestructiveAIGurus.com subpages for V1) | §5 |
| 2.4 | Privacy Policy — approve V1-minimal Posture A (see `PRIVACY-POLICY-V1.md`) | §6 |
| 2.5 | Pricing + age gate — approve $19.99 flat paid, IARC Teen, no dev-side age gate (see `11-pricing-and-age-gate.md`) | §7 |
| 2.6 | Terms of Service stub — does V1 need a ToS at all? | §8 |
| 2.7 | Copyright filing — US Copyright Office registration ($65, overdue per IP.md) | §9.1 |
| 2.8 | Trademark filing — app name + "Katrina's Mystic Visitors Guide" brand | §9.2 |
| 2.9 | Patent filing strategy — 4 provisional patents recommended (~$1,280 total) | §9.3 |
| 2.10 | Third-party content licensing — OpenStreetMap attribution, Salem historical corpus provenance | §10 |
| 2.11 | Play Store operator setup — Google Play Console account under the C-corp | §11 |
| 2.12 | Insurance and indemnity — does the operating entity need E&O coverage before ship? | §12 |

---

## 3. Operating entity

### 3.1 The decision

Per Session 145 operator direction: **form a C-corp at the 2026-04-20 counsel meeting; the C-corp is the operating entity for the app.**

### 3.2 Counsel-level questions

- **State of incorporation.** Delaware is the default for C-corps intending to raise outside capital. Massachusetts (operator's home state) simplifies in-state tax and registered-agent logistics. Counsel should weigh the pros/cons and pick one.
- **Entity name.** Does the C-corp name match the app brand (e.g., *Katrina's Mystic Visitors Guide, Inc.*), or does it use a parent-holding name (e.g., *DestructiveAIGurus, Inc.* or *Ellis Ventures, Inc.*) with the app as a product? Parent-holding is common when multiple products will ship under one entity.
- **Registered agent.** Delaware requires an in-state registered agent if out-of-state incorporators form there. Counsel typically provides this as a service or points the operator to a standard provider.
- **Founders and cap table.** Single-founder is the simplest V1 posture. Counsel will likely produce a founder-shares issuance and initial 83(b) election paperwork. If any collaborators or early contributors exist, they need to be declared now.
- **Operating agreement / bylaws.** Counsel-standard for a new C-corp.

### 3.3 Timing risk

C-corp formation typically takes **1-3 weeks** from filing to receiving the incorporation certificate, and a further 1-2 weeks to open a business bank account and receive an EIN from the IRS. **The V1 Play Store launch target is 2026-09-01 (4.5 months from this document's date)**, so there is comfortable headroom. However, the Google Play Console merchant profile can only be set up once the entity + bank account + EIN exist, so counsel should treat the formation as the front-of-critical-path item.

### 3.4 Interim posture if formation takes longer than expected

If incorporation slips past July 2026, there are two fallback postures:

1. **Ship as sole proprietor (Dean Maurice Ellis).** V1 can legally ship under a sole-proprietor Google Play Console account, then transfer the listing to the C-corp post-formation. Google allows entity transfers but charges a new $25 registration and may lose review/install continuity.
2. **Delay ship to the next post-Halloween window.** Not recommended — the commercial window is specifically October 2026 Salem 400+ traffic.

Counsel should confirm the C-corp timeline is compatible with the Sept 1 target, and flag early if it is not.

---

## 4. App name — options

### 4.1 Current working title

**Katrina's Mystic Visitors Guide** — retitled at Session 142 (2026-04-17) from the earlier internal name *WickedSalemWitchCityTour*. The brand is centered on the operator's cat (Katrina), who appears as the splash illustration (12 randomized art variants) and the welcome-dialog mascot.

### 4.2 Naming considerations counsel will raise

- **Trademark search required** before locking the name. The USPTO's TESS database will surface any existing marks that conflict. The working title includes "Katrina" (a personal name) and "Mystic" (a common tourism term) — combinations may or may not be clear.
- **"Salem" in the name?** Several existing apps and trademarks use "Salem" in tourism-related contexts. The current working title avoids "Salem" in the primary name but references the Salem tour heavily in subtitles and store listing copy. Counsel may want to verify that avoiding "Salem" in the trademarked name is sufficient.
- **"Witch" / "Wicked" / "Witch City"** — the prior internal name used all three. "Witch City" is a widely used Salem tourism tagline but may be a registered regional mark. Worth a TESS check.
- **International considerations.** If the app ships outside the US, cultural appropriateness of "Mystic" and "Witch" in translated markets should be sanity-checked.

### 4.3 Decision menu for counsel

- [ ] Lock "Katrina's Mystic Visitors Guide" as the app name (after TESS clearance)
- [ ] Pick an alternative name (operator + counsel)
- [ ] Keep the working title for V1 ship and rename in V2 if issues emerge

---

## 5. Domain / URL strategy

### 5.1 Operator direction (Session 145)

*"This will be all hosted under DestructiveAIGurus.com — the whole website will need to be redeveloped but everything should be part there as subpages for now."*

### 5.2 V1 URL requirements (driven by Play Store submission)

- **Privacy Policy** — must be hosted at a public HTTPS URL. Suggested: `destructiveaigurus.com/katrinas-mystic-guide/privacy`.
- **Support contact** — Play Store requires a support email or URL. If a web-form support page is used: `destructiveaigurus.com/katrinas-mystic-guide/support`.
- **Store listing website field (optional but recommended)** — a product landing page: `destructiveaigurus.com/katrinas-mystic-guide/`.

### 5.3 Counsel-level questions

- **Domain ownership.** DestructiveAIGurus.com is currently registered under the operator's personal name. Post-C-corp formation, should the domain be assigned to the corporation? (Typical yes — corporate-held domains protect the brand.)
- **Hosting and terms of service for the site itself.** The DAG site will have its own separate (brief) terms covering the website experience — counsel should confirm whether the existing site has one and whether it needs updating to cover the new subpages.
- **Redirects if the app name changes.** If "Katrina's Mystic Visitors Guide" is renamed post-launch, the DAG subpaths must redirect cleanly to preserve SEO and link integrity.
- **Future dedicated domain.** A standalone app domain (e.g., `katrinasmysticguide.com`) would be cleaner commercially but is a post-launch enhancement. Counsel may note this but it is not a V1 blocker.

### 5.4 Decision menu for counsel

- [ ] Approve DAG-subpages-for-V1 hosting strategy
- [ ] Confirm the DAG domain ownership transfer timing (now, at C-corp funding, or later)
- [ ] Approve the subpage URL pattern (e.g., `/katrinas-mystic-guide/privacy`)

---

## 6. Privacy Policy — Posture A summary

### 6.1 The decision (Session 145)

**Posture A — V1-minimal Privacy Policy.** The policy describes only what the shipped V1 app does (GPS on-device, no transmission, no third-party SDKs, no ads, no data collection). Full details in `docs/PRIVACY-POLICY-V1.md`. The longer OMEN-008-compliant draft at `docs/PRIVACY-POLICY.md` covers future functionality (Salem radio-environment analytics via the RadioIntelligence project) and is **not** part of V1.

### 6.2 What counsel needs to review

- Read `docs/PRIVACY-POLICY-V1.md` end-to-end (180 lines, ~8 minutes).
- Confirm the four `[TBD]` placeholders (operating entity, contact email, mailing address, jurisdiction) will be filled in before public hosting.
- Approve the plain-English tone, especially §2.4 ("What we do not collect cannot be lost, leaked, subpoenaed, or sold"). This is a deliberate user-trust claim and is truthful for V1 — but counsel should confirm the framing is acceptable.
- Approve the change-notice mechanism (§2.10 — in-app notice on next launch after material change). This is the hook by which V2 can introduce server-side features without surprise.

### 6.3 Known pending item (NOTE-L014 / OMEN)

The full OMEN-008-compliant policy at `docs/PRIVACY-POLICY.md` (184 lines, pending OMEN review for 31 LocationMapApp sessions) is **not** required for V1. It is held in reserve for future versions. Counsel does not need to read it for the 2026-04-20 meeting unless they specifically want the depth; the V1 posture is fully contained in `PRIVACY-POLICY-V1.md`.

---

## 7. Pricing and age gate — summary

### 7.1 The decisions (Session 138, reconfirmed Session 145)

- **Pricing:** $19.99 flat paid, one-time purchase via Google Play. No subscriptions, no in-app purchases, no ads in V1.
- **Content rating:** IARC Teen (13+). Historical witch-trial content drives this.
- **Age gate:** no developer-side age gate. Google Family Link handles age enforcement automatically under Teen + 13+ target audience + all-None Data Safety.

Full detail in `docs/lawyer-packet/11-pricing-and-age-gate.md`.

### 7.2 Corrected mental model

The operator's original hypothesis was *"charge $19.99 and Google handles the age nonsense."* The research-verified reality is *"content rating + target audience + data-safety posture drives the age-enforcement behavior; pricing is incidental."* V1 lands in a clean posture because of the Teen + 13+ + no-data combination, not because of the $19.99 price.

### 7.3 What counsel needs to confirm

See checklists in `11-pricing-and-age-gate.md` §4. Summary:

- Approve $19.99 flat paid and 15% Google revenue share
- Confirm no state-specific sales-tax or paid-app registration gaps
- Approve IARC Teen + 13+ target audience
- Confirm no developer-side age gate is legally required given the V1 posture

---

## 8. Terms of Service

### 8.1 Question for counsel — does V1 need a ToS at all?

A traditional mobile-app Terms of Service typically covers: accepting the price, limitation of liability, no-warranty, user-generated-content rules, prohibited uses, license to the app, dispute resolution (arbitration clause, class-action waiver), and termination rights. Google Play provides its own Terms of Sale and distributes the app under Google's developer agreement, which gives the end user some baseline protections the operator does not need to re-specify.

For V1 (paid, fully offline, no user-generated content, no accounts), **the minimum-viable ToS is very short** — essentially:

- This app is licensed, not sold.
- The app is provided "as is" without warranty.
- The operator is not liable for anything the app does or does not do on your device (subject to whatever limitations Google Play's own policies allow).
- Governing law is [state of operating entity].

### 8.2 Decision menu for counsel

- [ ] No separate ToS for V1 — rely entirely on Google Play's default protections
- [ ] Minimal ToS (the 4-bullet posture above) hosted at `destructiveaigurus.com/katrinas-mystic-guide/terms`
- [ ] Full ToS (standard mobile-app template) — recommended if counsel has a template on hand; adds 1-2 days of drafting time

### 8.3 Recommendation

**Minimal ToS hosted on the DAG subpage.** It is a low-effort addition that closes a visible gap in the Play Store listing metadata (Google lets developers link to a ToS, and its absence is neutral but a present one is slightly more professional). Counsel likely has a template that fits the V1 scope.

---

## 9. Intellectual Property

### 9.1 Copyright registration — overdue per IP.md

Per `IP.md` §Legal Protection Roadmap, a **US Copyright Office registration** for the app source code has been on the roadmap as a "Week 1" item since March 2026. **It has not yet been filed.** Cost: $65. Benefit: statutory damages + attorney fees eligibility if infringement ever occurs.

- [ ] Counsel confirms whether to file now (pre-launch) or at launch
- [ ] Counsel confirms form selection (Form TX — literary work, software source code)
- [ ] Counsel confirms ownership assignment (author = C-corp, once formed)

### 9.2 Trademark registration

Per `IP.md` §9, USPTO trademark registration has been on the roadmap as a Month-6 item. With the rename to *Katrina's Mystic Visitors Guide* at Session 142, the trademark target shifts from the old internal name to the new brand name.

- [ ] Counsel runs a TESS search on *Katrina's Mystic Visitors Guide* (and variants)
- [ ] Counsel confirms filing basis (IC 009 — mobile application software)
- [ ] Counsel confirms timing (pre-launch is ideal to preserve first-to-use priority, but can be done post-launch without loss)
- [ ] Fee: $250-350 depending on TEAS Plus vs TEAS Standard

### 9.3 Patent strategy

Per `IP.md`, the codebase contains **14 patentable innovations**, documented with novelty analysis and claim-angle sketches. The recommended strategy is **4 provisional patent filings** at Month-1 priority, covering the highest-novelty items:

1. Adaptive Radius Cap-Retry Algorithm
2. Probe-Calibrate-Spiral Scanner
3. Overpass Fair-Queue with Rate Limiting
4. JTS R-Tree Geofence Engine

Cost: USPTO micro-entity rate ~$320 each, $1,280 total. Each provisional gives 12 months to file a full utility patent.

**Counsel-level questions:**

- [ ] Is the provisional-first strategy right for the C-corp's funding trajectory?
- [ ] Should the patent filings wait for the C-corp to be formed (so the corp, not the sole proprietor, owns them)?
- [ ] Which of the 14 innovations does counsel think has the strongest claim surface? (May differ from the IP.md priority ordering.)
- [ ] Trade-secret vs patent for server-side algorithms (relevant mostly for post-V1 versions that re-introduce a cache proxy).

### 9.4 Third-party content and open-source dependencies

The V1 app bundles or depends on:

- **OpenStreetMap map tile data** — permissive license (ODbL) with attribution requirement. Per OSM attribution policy, the store listing and an in-app "About" screen must credit "© OpenStreetMap contributors." Counsel should confirm the credit is displayed prominently enough.
- **osmdroid** — permissive Apache-2.0 licensed Android OSM library. License notice must be included in the app's license-notices screen.
- **OkHttp, Hilt, Kotlin standard library, Room, other Android libraries** — all permissive licenses (Apache-2.0, MIT). Standard license-notices screen covers these.
- **Salem historical corpus** — bundled content derived from primary-source records (newspapers, court transcripts, biographical material). The 1692 trial records are public-domain by age; the modern editorial arrangement and narration writing are operator-authored and copyright-owned.
- **Stable Diffusion / AI-generated imagery** — the splash illustrations (Katrina splashes, welcome-card icons, Find-menu tile illustrations) are generated using DreamShaper XL Turbo locally. Counsel should confirm the commercial-use posture of the chosen SD model weights; DreamShaper is Fair Use / permissive for commercial products in most interpretations, but AI-image copyright status is legally evolving.
- **TTS voice clips** — Piper TTS with sox post-processing for atmospheric effects. Piper voice models are permissively licensed.

### 9.5 Decision menu for counsel

- [ ] Approve the copyright-registration-before-launch posture
- [ ] Approve (or modify) the 4-provisional-patent strategy
- [ ] Confirm the trademark filing for the current app name
- [ ] Review third-party content licensing attribution screen for V1

---

## 10. Play Store release checklist summary

The full 20+ item Play Store release checklist is in **OMEN NOTE-L017** (`~/Development/OMEN/notes/locationmapapp.md`). Summary of the items counsel should be aware of for Monday:

### 10.1 Operator-setup items (counsel may advise on corporate flow)

- Google Play Developer Account ($25 one-time) — under the C-corp
- Identity verification — government ID or D-U-N-S number for the C-corp
- Merchant / payments profile — linked to C-corp bank account; Google's 15%/30% commission

### 10.2 Legal / policy items

- Privacy Policy hosted at public URL — addressed in §6 above
- Data Safety form in Play Console — all "None" answers for V1
- Content rating (IARC questionnaire) — Teen rating expected
- Ads declaration — "no ads" for V1

### 10.3 Technical items (operator-executed in future sessions)

- Target SDK level 34+
- AAB (Android App Bundle) build via Gradle
- Signing key — release keystore, enrolled in Google Play App Signing
- ProGuard/R8 shrinking
- Version code + version name

### 10.4 Store listing assets (operator-executed)

- App icon (512x512 PNG) — ready via Session 144 library-tea icon work
- Feature graphic (1024x500) — not yet produced
- Screenshots (2+ per form factor) — not yet produced
- Short description (80 chars)
- Full description (up to 4000 chars)
- App category: Travel & Local
- Contact email: [TBD — §2.1 Privacy Policy]

### 10.5 Pre-launch testing

- Internal testing track — validated with Lenovo TB305FU device
- Pre-launch report (Firebase Test Lab) — review before promoting to production

### 10.6 Pricing and availability

- Base price: $19.99 (§7)
- Country availability: default worldwide, operator-reviewable

---

## 11. Data Safety form — all-None posture

Google Play's Data Safety questionnaire is a required listing field. V1's answer across every question is one of:

- *Does this app collect any [category] data?* — **No.**
- *Does this app share any [category] data with third parties?* — **No.**
- *Is this data collected in encrypted transit?* — **N/A.**
- *Does the user have a way to request deletion of their data?* — **Yes — uninstall the app.**

The single mostly-maybe answer is **Location (precise)** — the app uses precise location but does not collect or transmit it. Google's form has a specific option for this: *"Accessed but not collected."* That is the correct answer.

**Counsel confirmation needed:** V1 Data Safety posture is all-None, with Location flagged as "Accessed but not collected."

---

## 12. Insurance and indemnity

### 12.1 The question

Should the C-corp carry any commercial insurance before shipping V1 to paying customers?

### 12.2 What a $19.99 GPS-tour app's liability surface actually is

Realistic loss scenarios:

- A user claims the walking directions sent them through a dangerous area and they were injured.
- A user claims the historical content contained a defamation or false statement about an identifiable living person.
- A user claims the app's GPS use drained their phone battery at a critical moment (vanishingly unlikely but possible).
- A third party claims the app infringes a patent, trademark, or copyright.

### 12.3 Standard coverage options counsel will know

- **General liability** — covers bodily injury and property damage. Very unlikely to apply to an app.
- **Errors & Omissions (E&O) / Professional liability** — covers financial harm from professional advice or software errors. The closest fit.
- **Cyber liability** — covers data breach and cybersecurity incidents. Not applicable to V1 (no data to breach) but worth re-evaluating for V2.
- **Patent infringement coverage** — usually a specialty policy; most small software startups do not carry it until revenue justifies it.

### 12.4 Decision menu for counsel

- [ ] Recommend E&O coverage at ship ($1M-$2M limit is typical for early-stage software)
- [ ] Defer insurance until V1 ships and revenue is flowing
- [ ] Self-insure via C-corp limited-liability protection only

The operator has not previously engaged with insurance for this product. Counsel may have a default recommendation based on the C-corp type and expected first-year revenue.

---

## 13. Timeline view — what decisions need to happen when

| When | Item | Driver |
|---|---|---|
| 2026-04-20 (Monday) | C-corp formation paperwork initiated | Counsel meeting |
| 2026-04-20 | Privacy Policy posture A approved | Counsel sign-off on `PRIVACY-POLICY-V1.md` |
| 2026-04-20 | App name approved or alternative picked | Counsel TESS search + operator direction |
| 2026-04-20 | Copyright filing greenlit | $65, ~1 week to file |
| 2026-04-20 | Trademark filing greenlit | ~$300, counsel-executed |
| Apr-May 2026 | C-corp incorporation completes + EIN + bank account | Paperwork timeline |
| May-June 2026 | Google Play Developer Account set up under C-corp | Requires EIN + bank account |
| June 2026 | Provisional patent filings (if counsel approves) | Paperwork timeline |
| July 2026 | DAG subpages go live (Privacy Policy, Support, Product) | Website redev work |
| July-Aug 2026 | V1 store listing assets produced (feature graphic, screenshots, descriptions) | Development sessions |
| Aug 2026 | V1 AAB uploaded to internal testing track | Development + testing |
| Early Sept 2026 | V1 production release | Target: 2026-09-01 |
| Oct 2026 | Salem 400+ visitor surge | Commercial objective |

**The C-corp formation is the front-of-critical-path item.** Every downstream Play Store operator action (merchant profile, store listing under the corp, copyright/trademark assignments) waits on the incorporation.

---

## 14. Single-page summary for counsel

- **What is it:** *Katrina's Mystic Visitors Guide*, a paid ($19.99) offline GPS walking-tour app for Salem, Massachusetts.
- **Who ships it:** C-corp to be formed at the 2026-04-20 counsel meeting.
- **Privacy posture:** V1 collects no data off-device — `docs/PRIVACY-POLICY-V1.md`.
- **Pricing + age gate:** $19.99 flat paid; IARC Teen; no dev-side age gate — `docs/lawyer-packet/11-pricing-and-age-gate.md`.
- **IP actions needed:** US copyright ($65), trademark (~$300), 4 provisional patents ($1,280).
- **Third-party content:** OpenStreetMap (attribution), open-source libraries (license screen), public-domain historical records.
- **Domain strategy:** DestructiveAIGurus.com subpages for V1; dedicated domain post-launch.
- **Ship target:** 2026-09-01 (before Oct Salem 400+ visitor surge).
- **Front-of-critical-path:** C-corp formation. Every other operator action downstream.

---

**Last updated:** 2026-04-17 (S145 draft, pre-counsel-meeting).
