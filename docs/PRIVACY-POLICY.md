# WickedSalemWitchCityTour — Privacy Policy

**Status:** DRAFT — pending OMEN review against OMEN-008 before Salem radio-environment ingest goes live.
**Draft version:** 0.1
**Drafted:** 2026-04-10 (Session 114)
**Scope of this draft:** satisfies OMEN-008 §8 (layered disclosure) for the LocationMapApp Salem module's participation in the RadioIntelligence Salem stream.
**Legal entity placeholders:** all references to "we," "us," and the operating entity are placeholders pending final company/trading-name decision. All contact addresses are placeholders.

---

## 1. Install-screen blurb (short form)

This is the text that appears in the install-time consent surface and in the app's in-settings privacy section. It is intentionally short; the linked Privacy Policy (section 2 onward) is the authoritative disclosure.

> **Privacy summary.** WickedSalemWitchCityTour collects location data to deliver the tour and, while the app is running in Salem, also collects anonymized information about nearby Bluetooth, Wi-Fi, and cellular devices to improve tour analytics and help respond to security incidents in the area. You are consenting to this collection by installing the app. See the full Privacy Policy for what is collected, how it is stored, how long it is kept, and how it may be used.
>
> [Read the full Privacy Policy]

The "Read the full Privacy Policy" link opens the text below (either as an in-app WebView or as a link to a hosted copy of the same text — either is acceptable so long as the content is identical).

---

## 2. Full Privacy Policy

### 2.1 Who we are and what this policy covers

WickedSalemWitchCityTour ("the app") is a GPS-guided walking-tour application for Salem, Massachusetts, produced by **[OPERATING ENTITY TBD]** ("we," "us"). This Privacy Policy explains every category of data the app collects, why we collect it, where it is stored, how long it is kept, and who can see it.

This policy covers two distinct collection activities:

1. **Tour operation** — the data the app needs to deliver the walking-tour product itself (your location on the map, which points of interest you are near, which narrations to play).
2. **Salem radio-environment analytics** — a secondary, anonymized data collection that only occurs while you are using the app inside the Salem tour area. This data feeds an aggregate heatmap and density analytics system and is cross-referenced against a security threat list operated by the same entity. This is the part of the policy that matters most for your informed consent and is explained in full in section 2.4 below.

If you are comfortable with GPS-based tour apps but want to understand the radio-environment collection specifically, read section 2.4.

### 2.2 Data we collect for tour operation

While you are actively using the app:

- **GPS location** — used to position you on the map, trigger point-of-interest narrations, and compute walking directions. Resolution is limited to what the device's GPS hardware provides. Location is used in-memory to drive the tour experience and is written to on-device logs for crash-recovery and debugging. Raw per-fix GPS data is not uploaded to our servers as part of tour operation.
- **Device model, OS version, and app version** — used to diagnose crashes and compatibility issues if you contact support.
- **Tour progress** — which stops you have visited, which narrations have played. Stored locally on your device in the app's private database. Not uploaded.
- **Cached map tiles and point-of-interest data** — bundled with the app or downloaded on demand. Not tied to your identity.

The app works fully offline for the Salem tour. Tour operation does not require a network connection and does not transmit your tour progress or location history to any server.

### 2.3 What the app does NOT collect for tour operation

- We do not collect your name, email address, phone number, or any account identifier. The app has no user accounts.
- We do not use third-party analytics SDKs (Google Analytics, Firebase Analytics, Facebook SDK, AppsFlyer, or similar) for tour operation.
- We do not sell your data to advertisers or data brokers.
- We do not serve personalized advertising.

### 2.4 Salem radio-environment analytics (OMEN-008 Salem stream)

**This is the section that matters most for your consent.** Please read it carefully.

#### 2.4.1 What is collected

While you are using the app inside the Salem tour area, the app also reads the radio environment around your phone. Specifically:

- **Bluetooth Low Energy (BLE) advertisements** broadcast by nearby devices (phones, wearables, tags, beacons, vehicles).
- **Wi-Fi probe requests and beacon frames** broadcast by nearby devices and access points.
- **Cellular tower information** observable by your phone in the normal course of its network operation.
- **Your GPS location at the time of each of those observations**, so the observations can be placed on a map.

These radio signals are broadcast into public space by the devices that emit them. Your phone is the device doing the listening. The app does not transmit anything itself as part of this collection — it only listens to signals that are already being broadcast by devices around you.

#### 2.4.2 The consenting-proxy architecture (the part you should understand)

**You are consenting to this collection on your own behalf.** The third-party devices the app observes are not — they did not agree to be observed, and we have no practical way to ask them.

This is a known limitation of any radio-environment collection by a mobile application, and we are telling you about it honestly rather than hiding it in legal language. The legal and practical justification is threefold:

1. BLE, Wi-Fi, and cellular identifiers are broadcast in public radio space. Receiving them does not require any special access.
2. Your phone is your own private device. You, the app user, are the one deciding to run software that listens to that public radio space.
3. The collected observations are hashed (see 2.4.3) before being stored or transmitted, so third-party devices are not identifiable by name or raw address in the system of record.

We acknowledge this consent asymmetry in this policy rather than pretending it doesn't exist. If this model is not acceptable to you, do not install the app.

#### 2.4.3 How the data is stored

Every device identifier observed by the app (BLE address, Wi-Fi MAC, cellular identifier) is passed through a **one-way cryptographic hash** before it is stored or transmitted. The hash is produced with a keyed hash function ("salted hash") and the salt lives on the operator's own server hardware, not on the phone and not in the app.

This means:

- The system of record stores **hashes**, not raw MAC addresses or raw device identifiers.
- The same physical device produces the same hash in every observation, so density and heatmap analytics (how many distinct devices passed through an area) are still possible — but the identity of any individual device is not directly visible in the stored data.
- Turning a hash back into a raw identifier requires access to a separate, more-restricted lookup table that is gated behind manual operator action (see 2.4.6).

The hashed observations are transmitted from your phone through a minimal cloud relay to a single **PostgreSQL/PostGIS database on operator-owned hardware**. The relay itself does not retain any of the data on disk — it receives the upload and forwards it directly to the operator's server. The system of record is the operator's server; the cloud relay exists only because phones can be anywhere in the world and need a reachable endpoint.

#### 2.4.4 Retention windows

Data collected under 2.4.1 is retained on a tiered schedule:

| Data type | Retention |
|---|---|
| Aggregate heatmaps and density metrics (no per-device records) | Indefinite |
| Hashed per-device observations (raw records the aggregates are computed from) | Rolling 30-day window; older records are purged automatically |
| Hash-to-raw-identifier lookup table (the table needed to de-anonymize a hash) | Rolling 90-day window; older entries are purged automatically |

If a specific device is promoted from the Salem dataset into the operator's separate security threat-awareness system (see 2.4.5), the records relevant to that specific device may be copied into the security system before Salem's retention schedule purges them. Promotion is rare, intentional, and audited (see 2.4.6).

#### 2.4.5 Cross-reference with the operator's security systems

This is the second fact you need to understand for informed consent.

The operator of this app also operates a separate defensive security system ("the security system") that maintains a list of device identifiers of interest for threat-awareness reasons unrelated to the Salem tour. The Salem radio-environment data and the security system **share a unified hash function and salt**, meaning that any physical device observed in both contexts produces the same hash in both datasets.

**The practical consequence:** any device that has ever been observed by the operator's separate security system is directly identifiable in the Salem dataset on first contact, with no hash-table escalation required. The Salem hashed-by-default model protects only devices that have never been seen by the security system.

We are telling you this honestly rather than claiming Salem data is fully anonymized, because it is not fully anonymized for devices that already exist in the security system's records. This is a deliberate design decision made in service of the security system's threat-awareness mission. If this model is not acceptable to you, do not install the app.

When Salem observations arrive at the operator's server, they are automatically cross-referenced against the security system's list of devices of interest. If a match occurs, an alert is produced and delivered to the security system's operator. This matching happens within seconds of upload.

#### 2.4.6 The criminal-exception escalation

The hash-to-raw-identifier lookup table described in 2.4.3 is not accessible by any automated code path. It can only be unlocked by a **manual action taken by a specific human operator**, accompanied by a **typed justification** describing the specific incident that requires the lookup (for example: "vandalism reported at [location] at [time]").

Every such escalation — including the operator identity, the typed justification, the date and time, and the scope of the lookup — is written to an **append-only audit log** that cannot be edited or deleted by any code path in the system. The audit log is preserved indefinitely and is reviewed periodically by an independent governance process.

The escalation path exists so that the operator can respond to actual criminal incidents (vandalism, assault, theft) reported at or near the Salem tour area when the responsible device may have been observed in the dataset. It is not used for marketing, advertising, analytics, curiosity, or any non-criminal purpose.

#### 2.4.7 Who can see the data

- **Aggregate heatmap data** — visible to the operator and potentially published in aggregated form (density statistics, popular-area heatmaps).
- **Hashed per-device observations** — visible only to the operator, on operator-owned hardware, and only during their 30-day retention window.
- **Raw device identifiers (post-escalation)** — visible only to a specific human operator who has performed the manual escalation described in 2.4.6, and only for the specific incident described in the typed justification.
- **Third parties (advertisers, data brokers, marketing platforms)** — never, by design, for any tier of this data.

#### 2.4.8 Data sovereignty and legal jurisdiction

The system of record for the Salem radio-environment dataset is physical hardware in **[JURISDICTION TBD]**, owned and operated by **[OPERATING ENTITY TBD]**. The cloud relay that phones upload through contains no data at rest and is not the system of record.

### 2.5 Your choices

- **You can decline this collection by not installing the app.** Because the consent model is install-time and the radio-environment collection is part of the Salem tour experience, there is no in-app toggle to disable it while keeping the rest of the app functional. If you do not consent to 2.4, do not install.
- **You can uninstall at any time.** Uninstalling the app stops all future collection immediately. Data already collected before uninstall remains subject to the retention windows in 2.4.4.
- **You can contact us** at the address in section 2.7 to ask a question about this policy or about data related to a device you own.

### 2.6 Children

The app is not directed at children under 13 and does not knowingly collect information from them. Historical content in the tour references witch-trial history and may not be suitable for all ages.

### 2.7 Contact

Questions about this Privacy Policy, or requests related to data about a device you own, can be sent to:

**[CONTACT EMAIL TBD]**
**[MAILING ADDRESS TBD]**

### 2.8 Changes to this policy

Material changes to this policy (changes that expand what is collected, how it is stored, or who can see it) will be accompanied by an in-app notice on the next app launch after the change is deployed, and the revised policy will be dated at the top.

---

## 3. Draft notes for OMEN review

This section is not part of the policy text — it is drafting commentary for the OMEN review pass and will be removed before the policy is wired into the app.

1. **Legal entity name is TBD.** The current company/trading-name structure for the shipped app is not yet decided. Every `[OPERATING ENTITY TBD]` placeholder collapses to one name once that decision is made.
2. **Jurisdiction is TBD.** The policy lists "[JURISDICTION TBD]" for the home-box location. This is OMEN-008 §6 material and needs to match the physical reality of where the operator's PostgreSQL/PostGIS box actually lives.
3. **Contact email and mailing address are TBD.** These should route to an operator address that can receive privacy inquiries.
4. **The honest-acknowledgment sections (2.4.2, 2.4.5) are written in plain English by design.** OMEN-008 §5 and §4 specifically required honest disclosure of the consent asymmetry and the cross-stream identity limitation, rather than papering them over in legal language. If OMEN review asks for softer wording, the directive reference should be cited so the softening is an explicit deviation from OMEN-008 rather than an accidental drift.
5. **Retention table matches OMEN-008 §7** exactly for the three Salem tiers. WWWatcher/security-system retention is referenced indirectly (2.4.5 talks about promotion to the security system but doesn't claim to set that system's retention policy, because OMEN-008 §7 gives WWWatcher indefinite retention independent of this policy).
6. **The policy does not mention "RadioIntelligence" by product name.** Per OMEN-008 the back-end product name is internal; the user-facing disclosure talks about "the operator's separate security system" and "aggregate heatmap and density analytics." If OMEN prefers explicit product naming in the user-facing text, that's a one-word change in sections 2.4.5 and 2.4.7.
7. **No in-app toggle for opt-out.** Per OMEN-008 §1, consent is install-time only and the collection is not surfaced in active app UI. Section 2.5 honors this by saying "decline by not installing." If OMEN wants a kill-switch toggle added, that is a change to OMEN-008 §1 and should be issued as an amendment, not taken in the Privacy Policy alone.
8. **Coverage of the nine OMEN-008 constitutional decisions in this draft:**
   - §1 ingress model → 2.4.1, 2.5 (install-time consent, no stationary sensors mentioned because they aren't part of the Salem stream)
   - §2 hashed-default ceiling → 2.4.3
   - §3 criminal-exception escalation → 2.4.6
   - §4 consenting proxy → 2.4.2
   - §5 cross-stream identity space → 2.4.5 (the honest-limitation paragraph)
   - §6 data sovereignty → 2.4.3 (hardware), 2.4.8 (jurisdiction)
   - §7 retention tiers → 2.4.4
   - §8 layered disclosure → the document structure itself (section 1 = install-screen blurb, section 2 = linked full policy)
   - §9 real-time pipeline → 2.4.5 ("within seconds of upload")
9. **Things deliberately NOT promised:** the policy does not promise a specific hash algorithm, a specific salt-rotation cadence, a specific p95 latency figure, or specific audit-log review cadence. Those are internal engineering commitments that live in the RadioIntelligence architecture doc and can change without a user-facing policy rev. Naming them in the user-facing policy would bake engineering choices into a legal document.
10. **This draft is written before any collection code exists.** The EULA gate in OMEN-008 §8 means the policy exists before the feature — that's the correct sequencing. When the Salem radio-environment collection code is eventually written, it must match what this document promises; any divergence is a bug in the code, not a problem with the policy.

**Next step:** surface this file path (`docs/PRIVACY-POLICY.md`) in the Session 114 OMEN report and request OMEN review against OMEN-008 §8. No collection code to be written until OMEN signs off.
