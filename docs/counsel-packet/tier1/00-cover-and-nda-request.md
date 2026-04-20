<a name="cover"></a>

# Katrina's Mystic Visitors Guide — Counsel Packet

**Tier 1 — Pre-NDA Disclosure Set**

**Prepared for:** The 2026-04-20 counsel engagement meeting
**Prepared by:** Dean Maurice Ellis, founder / sole proprietor (pending C-corp formation at this meeting)
**Date:** April 2026
**App name (working title):** *Katrina's Mystic Visitors Guide*
**Product:** Paid ($19.99) offline GPS walking-tour Android application for Salem, Massachusetts
**Ship target:** Google Play Store before **2026-09-01** (Salem 400+ quadricentennial window)

---

## 1. Purpose of this packet

This packet is assembled so that you — as prospective counsel — have, on paper, the legal and compliance work already done by the operator, and a clear set of open decisions for you to drive.

The goal of the 2026-04-20 meeting is twofold:

1. **Formally engage counsel** to represent the to-be-formed C-corp through the pre-launch and Play Store submission phases.
2. **Drive the twelve open decisions** enumerated in this packet so the operator can execute on the Play Store submission timeline.

This is **Tier 1** of a two-tier disclosure package. Tier 1 is the public-facing, non-IP-sensitive material. Tier 2 — which contains the operator's intellectual-property register, architecture, and full commercial strategy — is **held back until a mutual non-disclosure agreement or an engagement letter with explicit confidentiality provisions is in place**. See §3 below.

---

<a name="packet-toc"></a>

## 2. What's in this packet

| § | Document | Purpose |
|---|---|---|
| [A1](#a1-legal-walkthrough) | Legal Walkthrough (redacted) | The twelve open decisions, with recommendations |
| [A2](#a2-pricing-and-age-gate) | Pricing + Age-Gate Posture | $19.99 flat paid, IARC Teen, no dev-side age gate |
| [A3](#a3-privacy-policy-v1) | V1 Privacy Policy Draft (Posture A) | Public-URL Privacy Policy — for your review before hosting |
| [A4](#a4-terms-of-service-stub) | V1 Terms of Service Stub | Minimal ToS draft for your review |
| [A5](#a5-data-safety-answers) | Google Play Data Safety — Pre-Filled Answers | Every Play Console questionnaire question, pre-answered |
| [A6](#a6-play-store-checklist) | Google Play Store Release Checklist | Operator-action checklist by lead-time |
| [A7](#a7-decision-checklist) | Counsel Decision Checklist | Single-page sign-off sheet for Monday |
| [B1](#b1-tier2-holdback-manifest) | Tier-2 Holdback Manifest | What's in Tier 2 and why it's gated |
| [B2](#b2-mutual-nda-template) | Mutual NDA Template (Draft) | Optional template if you don't have one ready |

---

<a name="nda-request"></a>

## 3. Non-Disclosure Request — Read First

### 3.1 What we are protecting

The operator has, over roughly 150 development sessions, produced substantial intellectual-property-sensitive work product in addition to this pre-NDA packet:

- An **intellectual-property register** documenting **14 patentable innovations** across GPS / geospatial algorithms, content pipelines, and narration systems. Each entry carries novelty analysis, claim-angle sketches, and protection-roadmap recommendations.
- A **comprehensive governance and compliance framework** (~1,150 lines of lawyer-ready material).
- A **full commercialization strategy** including tier architecture, market analysis, and financial projections.
- A **technical design-review record** of architectural decisions with algorithm-level specificity.
- A **future-state Privacy Policy** describing functionality not yet shipped in V1, tied to a cross-project radio-observation data architecture that is itself a candidate for IP protection.

Disclosing this material before a confidentiality agreement is in place creates two specific risks:

1. **Patent novelty risk.** Several of the 14 innovations have not yet been filed as provisional patents. Public disclosure, or disclosure to a party not bound by confidentiality, starts the 12-month US bar clock running (35 U.S.C. § 102(b)) and may eliminate foreign-jurisdiction filing rights entirely. The operator is aware of this and is deliberately not disclosing novelty analysis pre-engagement.
2. **Competitive risk.** The Salem tourism market is small, local, and has multiple existing apps. Leak of the tier architecture or the specific feature roadmap would meaningfully advantage a competitor.

### 3.2 What we are asking

Before the operator discloses Tier 2 material, we ask counsel to either:

- **Option A (preferred):** sign a mutual NDA. A draft mutual-NDA template is included at [§B2](#b2-mutual-nda-template) for your convenience. You are, of course, welcome to substitute your own.
- **Option B:** execute an engagement letter with explicit confidentiality provisions covering the attorney-client relationship, including confidentiality obligations in respect of technical and business-strategy material shared during the engagement. (We are aware that ABA Model Rule 1.18 extends a confidentiality duty to prospective-client disclosures, but we want the protection to be express and written.)

Upon execution of either, the operator will immediately deliver Tier 2 of this packet (see [§B1](#b1-tier2-holdback-manifest) for the manifest).

### 3.3 What's NOT gated by the NDA

Everything in this Tier 1 packet is either already public-facing (pricing, Privacy Policy intended for public URL hosting, Terms of Service intended for public URL hosting, Play Store submission metadata) or is a decision list suitable for an initial counsel consultation. **You can engage with this Tier 1 material without any confidentiality agreement.** It is safe to read, discuss, and annotate.

---

<a name="exec-summary"></a>

## 4. One-page executive summary

**What the app is.** A paid ($19.99 one-time) offline Android walking-tour app for Salem, Massachusetts. Bundles a map, ~1,830 points of interest, five pre-built walking tours, automatic GPS-triggered narration, and a historical 1692 witch-trials experience. V1 is fully offline — no data leaves the user's phone. Target: the October 2026 Salem 400+ visitor surge.

**Who will ship it.** A Massachusetts or Delaware C-corp, to be formed at this meeting. Interim fallback: sole-proprietor filing under the operator's personal name, transferred to the C-corp post-formation.

**Privacy posture.** "Posture A" — the app collects no personal data, transmits nothing off-device, contains no ads or third-party SDKs, and handles payment entirely through Google Play. The Privacy Policy is drafted and ready for your review ([§A3](#a3-privacy-policy-v1)).

**Pricing + age gate.** $19.99 flat paid via Google Play. IARC Teen (13+) content rating. Target audience 13+. Data Safety form answers all "None" (with Location flagged "Accessed but not collected"). In this posture, no developer-side age gate is required — Google Family Link and account age-of-account checks handle the restriction automatically ([§A2](#a2-pricing-and-age-gate)).

**IP actions needed.** Held for Tier 2, but at headline level: a US Copyright Office registration ($65) is overdue, a USPTO trademark filing is recommended for the app name (~$300 depending on TEAS path), and a **patent-filing strategy** needs to be discussed post-NDA. Budget estimate for patent provisionals at USPTO micro-entity rate is in the Tier 2 packet.

**Third-party content.** OpenStreetMap (attribution required in app and store listing), open-source Android libraries (standard license-notices screen), public-domain 1692 primary-source historical records, and locally AI-generated imagery (Stable Diffusion, DreamShaper XL Turbo). AI-image copyright status is legally evolving — counsel input welcome.

**Domain.** Privacy Policy and support pages to be hosted as subpages of `destructiveaigurus.com` (operator-owned domain). Domain ownership transfer to the C-corp is on the decision list.

**Front-of-critical-path item.** C-corp formation. Every downstream Play Store operator action — merchant profile, store listing, copyright/trademark assignments, bank account — waits on the incorporation certificate and EIN.

**Calendar runway.** 4.5 months from today (2026-04-19) to the 2026-09-01 ship target. Comfortable for V1 if no scope creep.

---

[Back to top](#cover) | [Legal walkthrough →](#a1-legal-walkthrough)
