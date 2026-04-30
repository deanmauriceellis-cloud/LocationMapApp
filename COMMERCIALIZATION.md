# Katrina's Mystic Visitors Guide — V1 Commercial Posture

**Status:** Live spec for V1 launch.
**Last updated:** 2026-04-29 (S201 V1 triage).
**Internal target ship:** **2026-08-01** (one month ahead of the Sept 1 publicly-stated anchor; see STATE.md).
**Salem 400+ peak:** October 2026.

> **Pre-pivot platform vision archived** at `docs/archive/COMMERCIALIZATION_removed_2026-04-29.md`. That document describes the abandoned generic-LMA freemium-with-ads / social / Overpass-scanner platform. Do not treat it as live.

---

## Product

**Katrina's Mystic Visitors Guide** — GPS-guided walking-tour app for Salem, Massachusetts. Fully self-contained: bundled tile pyramid, on-device walking router, geofence-triggered narration, 1692 Witch Trials Oracle (newspapers + bios + tile articles).

## Pricing

- **One-time purchase: $19.99** through Google Play Store.
- No subscriptions.
- No in-app purchases or microtransactions in V1.
- No advertisements.

## Posture

- **Fully offline** — manifest is network-incapable at the OS level (S180). Zero outside contact at runtime besides GPS satellite. Privacy claim: "What we do not collect cannot be lost, leaked, subpoenaed, or sold" (PRIVACY-POLICY-V1.md §2.4).
- **No LLM** at runtime. All narration is pre-baked content + on-device Android TTS.
- **No analytics, no crash reporting that includes personal data, no ad SDKs, no third-party SDKs that transmit identifiers.**
- **IARC Teen** rating (1692 witch-trial historical content).

## Platform

- **Android only** for V1. Google Play Store distribution.
- iOS scope to be evaluated post-V1 (S201 triage: "scope now, decide later").

## Channels

| Channel | V1 status | Notes |
|---|---|---|
| Google Play Store retail | **Primary** | Direct D2C at $19.99. |
| Salem Chamber of Commerce + local outreach | **Primary marketing channel** | Operator-owned; local-first launch posture. Asset packet drafted in a content-only session before Aug 1 (sell sheet, feature graphic, 3-5 hero screenshots, press blurb). |
| Hotel B2B wholesale | **Post-launch only** (S201 decision) | Hawthorne Hotel / Salem Inn / Hotel Salem etc. at ~$9.99 wholesale. Approached after V1 launch with retail metrics in hand. |
| Educator / school licensing | Deferred to V2 | Witch Trials content is curriculum-grade; revisit post-launch. |

## Merchant tier architecture (deferred for V1, infrastructure preserved)

`salem_pois.merchant_tier` (int) + `custom_description` / `custom_voice_asset` / `custom_icon_asset` columns are wired and gated. V1 ships with all 1,439 commercial POIs at `merchant_tier=0` (template-only render: name + category + sub-category + tappable phone + Visit Website button, no editorial prose, per S200 attorney-driven cleanup). Tier > 0 reveals merchant-authored custom fields. The plumbing is in place for a V2 paid-merchant tier; no merchant pitch in V1.

## Privacy / Data Safety form answers

V1 Posture A (per PRIVACY-POLICY-V1.md §3.7):

- Data collected: **None** (nothing transmitted off-device)
- Data shared: **None**
- Data security: **Not applicable** (no data to secure)
- Data deletion mechanism: **Uninstall** (removes all on-device data)
- Location precision: **Accessed but not collected** (on-device use without transmission)

## Open commercial / legal items

| Item | Owner | Status |
|---|---|---|
| Form TX copyright filing | Counsel | Hard deadline 2026-05-20 (per S201 confirmation: lawyer handling) |
| Operating entity formation | Counsel | C-corp formation discussed at 2026-04-20 meeting; outcome captured by operator, four privacy-policy [TBD] fields pending operator-relay |
| Play Console developer account | Operator | Starting this week (multi-week ID-verification clock) |
| Privacy policy public hosting | Operator + counsel | Planned subpage on DestructiveAIGurus.com once entity + contact resolved |
| Upload keystore second-medium backup | Operator | Covered (multiple secure methods per S201 confirmation) |
| Trademark "Katrina's Mystic Visitors Guide" | Deferred | Post-launch decision |

## What the live commercial spec is NOT

This document is NOT:
- A description of the abandoned freemium-with-ads / Overpass-scanner / Socket.IO-chat platform (archived).
- A multi-jurisdictional legal opinion (see GOVERNANCE.md for legal/compliance framework).
- A full marketing plan (asset packet drafted in a separate pre-launch session).

For full legal analysis — entity, IP, content liability, COPPA, privacy law detail — see `GOVERNANCE.md`. For session-by-session decision log and live state, see `STATE.md` + `docs/session-logs/`.
