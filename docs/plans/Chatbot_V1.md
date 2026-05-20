# Chatbot V1 — POI Find Plan

**Status:** Scoped at S283 (2026-05-19), not yet started.
**Working title:** "Find" (drawer-entry name, user-facing).
**Internal codename:** chatbot_v1 — rule-based on-device POI finder.
**Owner:** Operator + Claude.
**Target window:** Post-S283 implementation, ahead of 2026-08-01 ship.

---

## Why this exists

Tourists need an answer to three question shapes:

1. **Name lookup** — "Where is the Phillips House?"
2. **Category browse** — "Where can I find pizza?"
3. **Proximity browse** — "What are pizza places close to me?"

V1 commercial posture (`feedback_v1_no_external_contact.md`, `project_v1_offline_only.md`) forbids hosted LLMs and external network. This is an on-device, rule-based search over the existing `salem_pois` content, surfaced through a natural-language-tolerant text input. It is not a chatbot in the conversational sense — it is structured POI search with a forgiving query layer.

---

## Locked scope (S283 operator decisions)

| Area | Decision |
|---|---|
| Surface | Dedicated full-screen "Find" entry via drawer menu |
| Index source | All 2,039 active POIs (`deleted_at IS NULL`) |
| Index columns | `name + category + subcategory + description + short_description` |
| Match engine | SQLite FTS5 virtual table, baked into asset DB |
| Fuzzy fallback | Levenshtein on `name` only when FTS returns 0 (threshold ~2-3) |
| Result sort (GPS known) | Distance ascending, optional radius chip |
| Result sort (GPS unknown) | Alphabetical, distance column hidden |
| Result row content | name · distance (if known) · first line of `short_description` |
| Direct-answer mode | Triggered when top result ≥ 80% name-token overlap with query; snap-map + compact answer card |
| Tap-through | Existing `PoiDetailSheet` |
| Empty-state | Top-category chips ("Restaurants · Coffee · Historical Sites · Witch Trials") |
| Synonyms | **Deferred** — revisit only if recall fails in field walks |
| Multi-turn context | **Not in v1** |
| Voice input | **Not in v1** |
| Narration-style answers ("Tell me about X") | **Not in v1** — mapped to "best name match → PoiDetailSheet" |

---

## Open items for operator pondering

Things I flagged that aren't fully decided. Worth thinking through before kickoff.

### 1. Recall sanity check before build

Does "pizza" actually return useful POIs? Depends on whether descriptions/categories contain the literal token. Risk: tourist-staple keywords (pizza, coffee, beer, ATM, restroom, parking, ice cream, bathroom) might have terrible recall if `salem_pois` descriptions use formal language ("Italian restaurant" instead of "pizza").

**Proposal:** 5-minute PG spike before any code — run a recall check on ~15 tourist keywords against the indexed columns; if recall is bad, synonyms move from "deferred" to "v1 must-have."

### 2. FTS bake hook placement

Two options for where the FTS table gets built:

- **A. Inside `align-asset-schema-to-room.js`** — extend the existing schema-aligning step to also drop+rebuild the FTS table at the end. Makes it un-skippable (same discipline as the rest of the chain), no new publish-chain entry.
- **B. New standalone script** (`publish-poi-fts.js`) — explicit chain step, easier to debug, but one more thing for operator to remember to run.

Recommend A. Single-point-of-failure pattern matches what already exists.

### 3. Room schema rotation discipline

Adding the FTS5 entity will bump Room v23 → v24, rotate `identity_hash`, and require a fresh `bundletool` install on both test devices. Same discipline as every prior schema bump. No new risk, just one more rotation to plan around field-walk schedules.

### 4. Direct-answer threshold tuning

The 80% name-token overlap heuristic for triggering direct-answer mode is a guess. Probably correct for "Phillips House" → `Phillips House` (100%) and wrong for "where can I find pizza" → top result might be `Bertini's Pizza` (50% overlap, bumps to list mode — desired). But there are edge cases:

- "Hawthorne" → matches both `Hawthorne Hotel` and `Hawthorne Statue` and `Nathaniel Hawthorne's Birthplace`. Direct-answer would arbitrarily pick one. **List mode is probably safer here.** Heuristic should require uniqueness — only direct-answer when top result's score is meaningfully higher than second.

**Proposal:** threshold rule is "top result FTS bm25 score is ≥ 2× the second result AND name-token overlap ≥ 80%." That avoids the Hawthorne case.

### 5. "Near me" radius chip default

What's the default radius when the chip is in "auto" mode and GPS is good?

- **0.5 mi** — most aggressive, fits the "walking tourist" model. Probably too tight if user is on the wharf and asks about Witch House.
- **1 mi** — covers downtown core. Probably the right v1 default.
- **anywhere** — distance-sorted but unfiltered. Most permissive, lets the list itself communicate distance.

I'd start with **anywhere as default** and let users tighten via chip. Fewer surprising empty lists.

### 6. Drawer-menu placement

Where in the drawer does "Find" sit? Above Tours? Below Settings? This is small but worth a moment — Find is going to be a top-3 entry point for first-time tourists once it ships. Probably belongs near the top of the drawer.

### 7. What happens when user types nothing yet

Empty-input state on the Find screen — show what? Three options:
- Just the input field with placeholder text ("Search Salem POIs…").
- Top-category chips (same as the empty-results state).
- Recent searches list (would require new persistence).

Probably (b) — category chips give first-time users a way to browse without typing.

---

## Implementation phases

Each phase is a session-scale chunk. None depend on session-overlapping infrastructure.

### Phase 0 — Recall spike (pre-implementation)

- 5-min PG query over ~15 tourist-staple keywords across the proposed FTS columns.
- Decide: synonyms in v1 or deferred?
- Output: short table in this doc + go/no-go on synonyms.

### Phase 1 — FTS bake

- Add `@Fts5` entity `SalemPoiFts` content-tabled to `SalemPoi`.
- Bump Room schema v23 → v24.
- Extend `align-asset-schema-to-room.js` to populate FTS after schema stamp.
- Verify in-IDE schema export, identity_hash rotation, asset DB size delta.
- Smoke: `sqlite3 salem_content.db "SELECT id, name FROM salem_poi_fts WHERE salem_poi_fts MATCH 'pizza'"`.

### Phase 2 — Search DAO

- `PoiSearchDao.kt`: FTS MATCH query returning `Flow<List<SalemPoi>>` with bm25 ranking.
- Kotlin-side Levenshtein implementation (~30 LOC) for the 0-results name-fallback.
- Distance computation reuses existing Haversine helper (find it in `LocationUtils` / `MapMath` / wherever it currently lives).
- Unit tests on the DAO against an in-memory Room build of the asset schema.

### Phase 3 — Find screen

- `FindActivity` (or `FindFragment` under existing nav graph if one exists).
- ViewModel: query StateFlow → debounce 200 ms → DAO call → results StateFlow.
- Layout: input field (top), radius chip row, results RecyclerView, empty-state with category chips.
- Result row layout: name + distance + short-description first line.
- Direct-answer card composable for the high-confidence-name case.

### Phase 4 — Drawer wiring

- Add "Find" entry to drawer in `AppBarMenuManager` (or wherever the current drawer is wired).
- Place near top of drawer.
- Add icon — magnifying glass; check if `@drawable/ic_search` already exists or if we need a witch-themed variant for v1.

### Phase 5 — Field smoke + portrait check

- Lenovo + Pixel 8 install via `bundletool`.
- Smoke: 10 representative queries (name lookups, category queries, proximity queries, typos, no-GPS state via airplane-mode toggle).
- Portrait + landscape check on Pixel 8.
- Document recall failures in this doc; trigger synonyms work if needed.

### Phase 6 — Post-ship deferrals (NOT v1)

- Synonyms table.
- Multi-turn context.
- Voice input (on-device STT only; no network).
- Recent searches persistence.
- "Tell me about" mapping to narration playback.

---

## Risks

1. **Recall on tourist staples** — biggest single risk. Phase 0 spike de-risks.
2. **FTS bake forgotten in publish chain** — mitigated by folding into `align-asset-schema-to-room.js`.
3. **Direct-answer misfires** — mitigated by stricter threshold (uniqueness + overlap).
4. **APK size growth** — estimated 1-2 MB for FTS index at 2K rows; negligible against the 207 MB tile pack.
5. **Drawer entry pushes portrait survey** — adds one more surface to validate, but tiny.
6. **Off-Salem tourist edge case** — operator confirmed they may not be in Salem; alpha-sort fallback handles it, but result-list usefulness drops without distance context. Acceptable for v1.

---

## Out of scope (explicit)

- Hosted LLM of any kind.
- Network calls of any kind at runtime.
- New POI content authoring (search runs over what's already there).
- Synonyms (deferred, see Phase 0).
- Voice input.
- Multi-turn conversational memory.
- Direct narration playback from the Find screen.
- iOS port.

---

## References

- `feedback_v1_no_external_contact.md` — V1 zero outside contact rule.
- `project_v1_offline_only.md` — V1 commercial offline posture.
- `feedback_leverage_existing_assets.md` — `PoiDetailSheet`, distance-format, drawer system, location pipeline are reused.
- `feedback_adb_install_after_db_rebake.md` — Phase 5 install discipline.
- `CLAUDE.md` Key Paths — publish chain + Room schema export discipline.
- `STATE.md` S283 opener — operator declaration that scoped this work.
