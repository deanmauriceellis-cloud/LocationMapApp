# UltraCode Review — LocationMapApp v1.5 Workstream Survey

**Date:** 2026-05-29 · **Session:** 304 · **Branch:** master · **HEAD:** `232893c`
**Method:** 9-agent survey workflow (7 parallel readers over S301/S302/S303 logs + STATE.md + the 3 active plan docs → synthesis → completeness critic), reconciled against a first-hand read of all three session logs and STATE.
**Days to ship:** ~64 to the 2026-08-01 internal target · ~33 to Form TX (2026-07-01) · ~4 to Node 20→24 GH Actions deprecation (2026-06-02).

---

## The one thing that matters

**The Aug 1 ship is operator-blocked, not engineering-blocked.** The only declared ship-blocker — OMEN-025 anti-piracy — is code-complete through the S6 core (46 Android + 25 Worker tests green, runtime gate still `false`). No code-side progress is possible until three credentials arrive. Meanwhile **two clocks are slipping purely for want of an action only the operator can take:**

1. **Play Console developer-account registration** — the multi-week ID-verification clock **has not started.** It's the true long pole: it gates *both* the AAB upload *and* the OMEN-025 internal-testing track (the only place Play Integrity verdicts mean anything). Every day it sits is a day off the Aug 1 runway. This is the single highest-leverage item on the whole board and it costs one afternoon.
2. **Node 20 → Node 24 GitHub Actions bump** — hard deprecation **2026-06-02 (~4 days out)**, a one-line change in `.github/workflows/ci.yml`, currently unowned.

Everything else — tech-debt decomp, graphics follow-ups, portrait survey, i18n, historical maps — *feels* urgent but **does not gate Aug 1.**

---

## Full workstream inventory

### 🔴 Ship-blocker (TOP PRIORITY)

| Workstream | Status | Blocker |
|---|---|---|
| **OMEN-025 Phase 1 activation/anti-piracy** | Code-complete S1–S6 core; `ACTIVATION_HANDSHAKE_ENABLED=false` (no runtime change). Worker deploy → S6 wiring → S7 hardening → S8 disclosures all gated. | **3 operator creds:** Cloudflare API token · Google Play Integrity SA JSON · Play Console internal-testing track. |

### 🟢 Active / recently shipped (code-side)

| Workstream | Status |
|---|---|
| **S303 GPS-OBS heartbeat false-stale fix** | Shipped (`91277a6`), on Pixel 8. **Awaiting operator field drive** — the corrective path only fires under real lifecycle hiccups. After a drive: pull `debug-YYYYMMDD.log`, grep `observer-vs-source skew` (each = a rescued false-alarm) + `REACH-OUT SUPPRESSED` (should drop). |
| **Pixel 8 forensic pull / DebugLogger discovery** | Complete; FINDINGS committed, raw gitignored (home GPS = PII). |
| **Node 20→24 CI bump** | ⏰ 4 days. Trivial. Unowned. |

### 🟡 Carry-forward (queued / awaiting decision)

| Workstream | Where it stands |
|---|---|
| **Tech-debt cleanup (7-phase P0–P6)** | Plan-of-record committed (`7fd0e02`), tag-gated, interruptible. **Awaiting go/no-go + 7 NEXTSTEPS questions.** P0 (publish orchestrator) = the only pre-ship-relevant phase. |
| **Graphics follow-ups** | Core shipped (124 heroes, 40 scenes, ghost badges, church glyph). Pending: field-walk re-roll list; rest of `poi-circle-icons/` still pre-redo witch sigils; ~5 hard monuments; splash 12→4 ("at the end"); **retire dead `hero/` system (art-bible §7)** — overlaps tech-debt P2. |
| **TcpLogStreamer back-off-when-cellular** | ~60 W-lines/min off-WiFi. Folds into tech-debt P3. |
| **Historical-map FAB (KEPT V1 feature #2)** | ⚠️ *Partially* shipped, not done: 4 maps live on Lenovo, but **precision GCP pass for 1874/1906/1911 still owed** (S286 used coarse ±50–100m corners), plus publish-chain docs, ASSETS-MANIFEST update, 4-map field-walk. |
| **OMEN hand-offs owed** | Worker `CONTRACT.md` (NOTE-L023 #2), `*.workers.dev` decision to withdraw NOTE-DA005, acks #1/#3/#4, manifest.pem path #5, git-maintenance request. |
| **Other KEPT V1 features** | Halloween/October layer · tour-completion certificate · Heritage Trail yellow polyline. |
| **Field-validation backlog (stacked on operator drives)** | S303 GPS cure · S206 post-fix POI/tour-mode · graphics eyeball · Phase A portrait survey · asset-pack tilt/SuperAdmin. **Drive time is the scarce resource — these need sequencing.** |
| **Historian browser smoke** (S290/S291) · **Phase A portrait survey** (owed since S264, *gates* i18n+content+admin-UX queue) · **Wave 2/3 leftovers** (cache-proxy MBTA route, asset-pack walk). | Open. |

### ⚪ Parked / deferred (gated or post-V1)

Full Explore + Beverly demo (`/tmp/expanded-map-demo.aab`, Pixel-8-only — Lenovo OOMs) · **Salem Oracle/SalemIntelligence provenance gate** (paused S214; *grandfathered POI data may need re-attribution before AAB upload*) · **1692/historical-maps docket** (10 sub-threads, untouched ~15 sessions; cheapest win = per-layer zoom clamps, ~30 min) · **Chatbot V1** (Phase 0 recall spike) · TigerBase Android wiring (lawyer-gated) · Wave 4 i18n / content authoring / admin polish (all behind portrait) · aged S215/S216 backlog + 4.4 GB disk reclaim · tour-mode S206-mirror UX ("we're good for this").

### Standing OMEN open items

NOTE-L014/OMEN-008 full Privacy Policy draft pending · NOTE-L015 SalemCommercial cutover (post-V1) · ANOM-001/002 SI relay · NOTE-L019 restrooms_zombie regen (LOW).

---

## Critical path to Aug 1 (dependency-ordered)

1. **Operator registers Play Console under Destructive AI Gurus, LLC** ← *start the multi-week clock now; nothing else completes without it.*
2. **Operator gets the Cloudflare token + Google Play Integrity SA JSON** (+ enable Play Integrity API in the GCP project).
3. **Worker deploy** (~1 session, code-side).
4. **Play internal-testing track live** (from step 1) → unlocks live verdicts.
5. **S6 launch-flow wiring** → flip `ACTIVATION_HANDSHAKE_ENABLED=true` (~1 session).
6. **S7** re-add INTERNET to release manifest, HTTPS-only config, *prove* a tampered release LOCKS via apkanalyzer (~1 session).
7. **S8** Play Data Safety + disclosures.
8. **Form TX filed by 2026-07-01** (counsel; parallel; must name the LLC).

---

## Time-sensitive items

| Item | Hard date | Days out | Risk |
|---|---|---|---|
| **Node 20 → Node 24 GH Actions** | 2026-06-02 | ~4 | At risk by neglect. Trivial one-liner. Do it now. |
| **Play Console ID-verification clock** | multi-week, not started | clock unstarted | **Most dangerous slip.** Gates AAB upload + OMEN-025 verdicts. |
| **Form TX copyright** | 2026-07-01 | ~33 | Counsel-handled; deferred once (5/20→7/01). Must name the LLC. |
| **Aug 1 internal ship** | 2026-08-01 | ~64 | Feasible IF creds land in ~2 weeks; tight otherwise. |
| **Salem 400+ peak** | Oct 2026 | ~5 mo | The real commercial deadline; ~2 mo buffer behind Aug 1. |

---

## Footguns to respect (these have bitten us)

- **`salem_tiles.sqlite` is gitignored** — `git checkout` can't restore it. Back up before *any* asset swap. (S302 incident, recovered from device runtime copy; exact V1 at `tools/tile-bake/dist/salem_tiles.sqlite.V1-EXACT-20260527`.)
- **Publish chain is manual & order-locked:** pois → tours → tour-legs → poi-collection → **align** → **sign-content-manifest (LAST)**. Skip align → destructive migration wipes the asset; skip sign-after-align → the live gate LOCKS genuine buyers.
- **preBuild auto-bake re-stamps the DB** → sha256 drift → `verifyBundledAssets` FAIL. Build `-PskipPublishChain` + `git checkout` the DB if it drifts (re-hit S301).
- **Install discipline:** `adb uninstall` + bundletool, never `install -r`. On `DELETE_FAILED_INTERNAL_ERROR`: `am force-stop` → `pm clear` → retry uninstall first.
- **Motion-sensor gate now confirmed on BOTH devices** — narration suppression is app-wide until the S303 derived-speed escape is field-proven. The fix is unproven until the next drive.

### Two corrections to the first-pass synthesis

- **Don't pre-stage `EXPECTED_MANIFEST_HASHES` expecting it to be operator-free** — the authoritative *signed* manifestHash only exists after the operator's offline release bake with `~/keys/content-manifest.pem` (CI emits it unsigned).
- **STATE's Wave-2 "hero/ dir parked per feedback_hero_revisit_later.md" is stale** — that memory was superseded at S298; art-bible §7 now wants the dead `hero/` system retired. The reconcile-before-delete footgun (live PG shows **949** `hero/%` refs vs art-bible's **803**) still stands — count against live POIs before any DB delete.

---

## Recommendations

### Operator, this week (gates everything)

1. **Register the Play Console account today.** This is the whole ballgame for Aug 1.
2. Grab the **Cloudflare token + Google SA JSON** (enable Play Integrity API in the linked GCP project).
3. Confirm with counsel: Form TX names the LLC, and whether a written operating agreement is needed before AAB upload.

### Agent, right now (no operator dependency, ship-relevant, low risk)

1. **Node 20→24 bump** — clear the 4-day deadline. Cheapest win on the board.
2. **Tech-debt Phase 0 only** (publish-chain orchestrator + shared `.env` parser + txn-wrap the 3 DROP+CREATE scripts + characterization tests asserting the align→sign ordering). The one cleanup phase with real pre-ship value — it directly **de-risks the brittle OMEN-025 release bake** — touches no app code, off-device verifiable.
3. **Write the OMEN hand-offs** owed in the next report.
4. Optional 30-min win: **historical-maps per-layer zoom clamps.**

### Explicitly NOT now

Do not start the P1–P5 monolith decomp on spec. It's the long pole against a 2-test safety net and would compete with the OMEN-025 runway for no ship benefit. Hold it until credentials force OMEN-025 to the top, or until after ship.

### Decisions needed from the operator

- **Tech-debt go/no-go:** recommendation is *P0 now, P1–P5 deferred to post-credential/post-ship.* Good enough, or commit the full decomp before Aug 1?
- **Next drive:** run the **S303 GPS-cure validation + graphics hero/marker eyeball** in the same outing? They share a drive and unblock the highest-value follow-ups.

---

**Bottom line:** the Aug 1 ship hinges on one operator afternoon (Play Console registration) plus 2 credentials. Engineering should clear the 4-day Node deadline, harden the release bake via Phase 0, pre-stage what's safely pre-stageable on the Worker, and otherwise stay out of the monolith until OMEN-025 credentials force it to the top of the queue.

_Generated S304, 2026-05-29. Source workflow: `workstream-survey` (9 agents). Full detail in `docs/session-logs/session-304-2026-05-29.md`._
