# Must-Have #11 — Pricing ($19.99 flat paid) + Age-Gate Posture for V1

**Purpose of this document:** Counsel-ready writeup of the pricing decision and the age-gate posture for V1 of *Katrina's Mystic Visitors Guide*. Prepared for the 2026-04-20 C-corp formation meeting. No code changes driven by this document — it is a decision record + a research verification.

**Parking lot reference:** item #11 in `docs/parking-lot-S138-master-review.md`.

**Related documents:**
- `docs/PRIVACY-POLICY-V1.md` — V1-minimal Privacy Policy (Posture A)
- `docs/lawyer-packet/10-legal-walkthrough.md` — consolidated legal walkthrough
- `GOVERNANCE.md` — the full legal/compliance framework
- OMEN NOTE-L017 — Play Store release checklist (`~/Development/OMEN/notes/locationmapapp.md`)

---

## 1. Operator's original question (verbatim, from parking lot)

> *"I need to know everything about how to place an application for commercial user in google. I want to make any user spend $19.99 to buy it and I want to avoid the crazy new validations we have to do on age. I figure if I make people on Google pay $19.99 for the application — Google will do the new age requirement nonsense."*

Two questions in one:

1. **What does it take to place a paid app on Google Play at $19.99?** (Commerce / payments / store listing.)
2. **Does charging $19.99 up front let us avoid age-verification requirements?** (Legal / compliance.)

The short answer to (2) — and the most important thing to know before the counsel meeting — is: **no, paid-vs-free is not what bypasses age verification. Content rating is.** The good news is that the V1 posture (IARC Teen / 13+ target, paid, fully offline, no data collection) lands us in a place where Google's age enforcement happens automatically without any developer-side age gate. The operator's instinct was directionally right even if the underlying mechanism is different from what was assumed.

---

## 2. Decision: $19.99 flat paid — one-time purchase

### 2.1 What V1 sells

One app: *Katrina's Mystic Visitors Guide*. One price: **$19.99 USD**. One transaction: one-time purchase via the Google Play Store. No subscriptions, no in-app purchases, no microtransactions, no advertising, no freemium tier in V1.

The purchase unlocks the full app — all five bundled tours, all ~1,837 points of interest, the Witch Trials historical content (Oracle newspaper, portraits, cross-linked NPC narratives), the walking-directions engine, the GPS narration system, and the on-device map tile cache. Once purchased and installed, the app works fully offline.

### 2.2 Why flat paid and not tiered

The pre-S138 plan was a four-tier pricing model (Free/ads, $4.99 Explorer, $9.99 Premium, $49.99/mo Salem LLM subscription). Operator pivoted to flat $19.99 at Session 138 for four reasons:

1. **Offline-first commits us to no servers.** V1 is fully offline. A subscription tier requires a server-side entitlement check; a flat-paid single purchase does not. Flat paid is the only pricing model that is compatible with Posture A Privacy Policy (zero data transmitted off-device).
2. **Ad-supported free tier requires an ad SDK.** Ad SDKs are third-party SDKs that transmit data. They are incompatible with the "nothing leaves your device" Privacy Policy posture. Removing the free tier removes the SDK.
3. **Tiered in-app purchases add regulatory surface area.** Each tier is a distinct product on the Play Store listing, each needs Data Safety answers, each can be refunded independently. A single product is simpler for the V1 ship.
4. **$19.99 is a plausible one-time price for a Salem tour app.** Operator product judgment: visitors spending $400 on flights + hotels to walk Salem at Halloween will pay $19.99 for a guided-tour app. A $4.99 price signals disposable; a $19.99 price signals sit-down dinner.

V2 can revisit tiered pricing if the commercial reality calls for it. V1 is deliberately the simplest possible commercial product.

### 2.3 What Google takes

Google Play's developer fee is **15% on the first $1M of annual revenue**, and **30% on revenue above $1M per year per developer account**. On a $19.99 sale, Google takes roughly $3.00 under the 15% bracket, leaving ~$16.99 to the operator per unit (before income tax).

There is no monthly platform fee, no upfront listing fee, no per-download charge. The one-time developer-account registration is $25 and is already tracked in NOTE-L017.

### 2.4 Payment flow — what V1 does not see

Because the entire payment flow is handled inside Google Play, the operating entity never receives or stores the user's payment method, billing address, or identity. Google pays out to a merchant account periodically (configurable as monthly or on a threshold). The operator sees aggregated sales data in Google Play Console; individual customer identities are not exposed to the app or the developer.

**Consequence for the Privacy Policy:** V1 Posture A can honestly state *"We do not collect payment information — Google handles billing entirely"* (see `docs/PRIVACY-POLICY-V1.md` §2.3 and §2.5). This is one of the reasons V1 lands in a clean privacy posture.

### 2.5 Refunds, chargebacks, and disputes

Google Play has a 48-hour automatic refund window for paid apps. After 48 hours, refunds are at Google's discretion unless the developer grants them. Chargebacks pass through Google's dispute system — the developer can be asked to respond with sale records, but does not handle the cardholder directly.

**Operator consequence:** V1 should expect some percentage of $19.99 purchases to be refunded within 48 hours. Budget for a small refund rate (2-5% of installs is a reasonable assumption for a new commercial app). This is a financial planning item rather than a technical one.

---

## 3. Age-gate posture: why $19.99 "works" without a dev-side age gate

This is where the operator's intuition was directionally right but mechanically different from the assumption.

### 3.1 The operator's hypothesis, stated plainly

*"If I charge $19.99 up front, Google handles the age nonsense."*

The reasoning: a paid app is not going to be impulse-installed by a random child; the payment friction itself acts as a filter; therefore Google should treat the app as if the audience were adults.

### 3.2 What actually drives Google's age-verification behavior

Google's age-assurance framework (rolled out gradually since 2024) is driven primarily by **three signals**, none of which are "is the app paid":

1. **IARC content rating** — the questionnaire the developer fills out in Play Console, which produces a global rating (Everyone / Everyone 10+ / Teen / Mature 17+ / Adults Only 18+).
2. **Target Audience** — the developer's declared intended audience age range, filled out separately in Play Console.
3. **Data Safety declarations** — whether the app collects, shares, or processes personal data, and what kinds.

The combination of those three fields determines what Google shows to different users at install time:

- Apps **rated for children or mixed audiences (Everyone, Everyone 10+)** with **Target Audience including children under 13** fall under Google Play's Designed-for-Families program, which inherits COPPA requirements — developer-side age-gating or parental-consent flows often needed.
- Apps **rated Teen or higher** with **Target Audience 13+** do NOT fall under Designed-for-Families. Google Family Link handles the install restriction automatically for child-supervised accounts; no developer-side age gate is required.
- Apps that collect personal data trigger extra questions regardless of rating, especially if that data could identify minors.

### 3.3 Where V1 lands

The V1 posture (S138 decision) makes all three signals clean:

| Signal | V1 value | Effect |
|---|---|---|
| IARC content rating | **Teen (13+)** | Not in Designed-for-Families. No child-audience inheritance. |
| Target Audience | **13+** | Aligns with IARC rating. Not in Designed-for-Families. |
| Data Safety | **All "None"** (offline V1) | No personal data collected → no child-data concerns. |

**Consequence:** Google's automatic age enforcement (Family Link for supervised child accounts; age-of-account checks for self-managed accounts) handles the restriction. The developer does **not** need to build an in-app age gate, age attestation, parental-consent flow, or COPPA verifiable-parental-consent mechanism.

### 3.4 The correct mental model

The correct mental model for why V1 avoids "age nonsense" is **not**:

> *"We charge $19.99, so Google handles it."*

It is:

> *"We are rated Teen, we target 13+, and we collect no data — so Google's built-in age enforcement is sufficient and no developer-side gating is required."*

The paid-$19.99 part is incidental to this posture. A free Teen-rated app with all-None Data Safety would land in the same posture. What flat-paid-$19.99 buys us is a different thing — it keeps us out of the ad-supported + analytics-SDK ecosystem that would contaminate Data Safety and push us into child-audience questions.

### 3.5 Honest caveat — the Teen rating is content-gated, not audience-gated

The app is rated Teen because the historical content covers the 1692 Salem witch trials, which include references to persecution, imprisonment, and execution of accused witches. The IARC questionnaire flags this as appropriate for 13+ ("Mild violence; reference to real historical persecution").

If the app were rated Everyone with Target Audience including children, we would be in Designed-for-Families territory even at $19.99. The rating drives the outcome; the price does not. Counsel should understand this in case future product decisions (e.g., a children's version of the Salem tour) would shift the rating downward.

### 3.6 What operator-level actions the age-gate posture requires

- **In Play Console:**
  - Complete the IARC questionnaire truthfully — the "persecution / imprisonment / execution" context goes into the relevant mild-violence boxes. Result: Teen rating.
  - Set Target Audience to 13+ explicitly.
  - Complete the Data Safety form with all "None" answers. This requires linking to the V1 Privacy Policy.
  - Do not enroll in the Designed-for-Families program.
- **In the app:**
  - Do not build an in-app age gate. It is redundant with Google Family Link and would create a UX drag on paying adult users.
  - Do not collect age, birthdate, or any age-related self-attestation.
  - Reference the Teen rating in the store listing description.
- **In the Privacy Policy:**
  - V1 Posture A already handles this — see `docs/PRIVACY-POLICY-V1.md` §2.6 ("Children").

### 3.7 State-law exposure — none known for V1

Several US states (California, Texas, Utah) have introduced age-verification laws in 2024-2025, but these target apps that either (a) collect data from minors, (b) serve user-generated content, or (c) contain explicit material. V1 is none of the above:

- No data collection — §2.3 of the Privacy Policy states this explicitly.
- No user-generated content — V1 is read-only historical content.
- No explicit material — IARC Teen rating excludes the categories these state laws target.

**Counsel should still verify** there is no state-specific paid-app registration or tax-nexus requirement that this research missed, but the age-law surface area for V1 is very small.

---

## 4. What counsel needs to confirm on Monday

### 4.1 Pricing / commerce

- [ ] Confirm the operator's plan to ship V1 at **$19.99 flat paid, one-time**, through Google Play Store.
- [ ] Confirm the C-corp to be formed will be the Google Play merchant account holder. (See `10-legal-walkthrough.md` for entity considerations.)
- [ ] Confirm acceptance of **Google's 15% / 30% revenue share**.
- [ ] Confirm there are no state-level sales-tax or paid-app registration requirements the operator needs to register for. (Google handles most sales tax automatically for paid apps, but some states have supplemental requirements.)
- [ ] Confirm the **48-hour Google refund window** is acceptable (it is standard and not negotiable).

### 4.2 Age gate / content rating

- [ ] Confirm the **IARC Teen / 13+ content rating** is correct for the witch-trials historical content.
- [ ] Confirm that **no developer-side age gate** is needed and the Google Family Link posture is acceptable.
- [ ] Confirm the "paid-app → Google handles age" mental model is now correctly understood as "Teen-rated + 13+ target audience + all-None Data Safety → Google handles age."
- [ ] Confirm there is **no state-law age-verification requirement** the operator needs to comply with under V1's offline-no-data-collection posture.

### 4.3 Related confirmations (surface from the Privacy Policy review)

- [ ] V1 Privacy Policy Posture A (minimal, on-device only) is the right posture to ship.
- [ ] DestructiveAIGurus.com is the right hosting domain for the public Privacy Policy.
- [ ] The Data Safety form answers will all be **None** for V1.

---

## 5. Summary — one sentence each

- **Pricing:** $19.99 flat paid one-time purchase via Google Play; Google takes 15%; no tiers in V1.
- **Age gate:** not required — the combination of Teen content rating, 13+ target audience, and zero data collection puts V1 in a posture where Google's built-in age enforcement is sufficient.
- **Counsel task:** validate the pricing and rating decisions; confirm no state-law or payment-registration gaps; review the Privacy Policy separately.

---

**Last updated:** 2026-04-17 (S145 draft, pre-counsel-meeting).
