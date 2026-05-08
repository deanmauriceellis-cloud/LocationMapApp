# Splash Announcements — V1 Authoring Doc

**Last updated:** 2026-05-07 (S231)
**For your eyes:** edit anything below. The runtime will use whatever's here at the next build. Lines flagged `// TODO` are stubs — replace with your copy.

This file is the source of truth. The runtime engine reads templates verbatim and substitutes named slots at speak-time.

---

## How the engine picks a line

On every cold start, the splash:

1. Calls `FusedLocationProvider.getLastLocation()` (instant — uses the system's most recent cached fix; may be `null` on a fresh install).
2. Looks the (lat, lon) up against the bundled polygon asset (`us_places_v1.sqlite`, 102 top-100 cities + 102 buffer rings + 10 Salem-adjacent towns + 3,235 US counties).
3. Computes:
   - `distance_mi` — great-circle miles to Salem common (42.5224, -70.8961).
   - `bearing_to_salem` — degrees, 0 = north.
   - `compass` — N / NE / E / SE / S / SW / W / NW based on bearing FROM Salem to user.
   - `movement` — `STATIONARY` (`speed < 0.5 m/s`) / `APPROACHING` (within ±60° of bearing-to-Salem) / `DEPARTING` (within ±60° of opposite) / `LATERAL` (otherwise) / `UNKNOWN` (no speed).
   - `place_kind` and `place_name` — what `us_places_v1.sqlite` resolved to: IN_CITY / NEAR_CITY / IN_TOWN_ADJACENT_TO_SALEM / IN_COUNTY / OFFGRID.
4. Picks a **bucket** based on `distance_mi`.
5. Picks a random line from that bucket's pool.
6. Fills slots in `{curly}` form.
7. Speaks via Android TTS.

If the GPS lookup fails (no permission, no last-known fix, asset error), the engine falls back to the `UNKNOWN` bucket.

---

## Slot vocabulary

You can use any of these `{slot}` names inside any template. The engine fills each before speaking. Slots not present in the resolved context expand to a sensible empty string (no "in undefined, undefined" surprises).

| Slot | Example value | Notes |
|---|---|---|
| `{miles}` | `47.3` | Distance to Salem in miles (1 decimal). |
| `{miles_int}` | `47` | Same, rounded to int. |
| `{city}` | `Detroit` | Resolved IN_CITY name (top-100). |
| `{near_city}` | `Boston` | Resolved NEAR_CITY name (within 30 mi buffer). |
| `{town}` | `Beverly` | Resolved IN_TOWN_ADJACENT_TO_SALEM name. |
| `{county}` | `Wayne County` | Resolved IN_COUNTY name (with " County" suffix). |
| `{state}` | `MI` | State postal code. |
| `{state_long}` | `Michigan` | State full name. |
| `{compass}` | `southwest` | Direction FROM Salem to user (N, NE, E, SE, S, SW, W, NW expanded to lowercase words). |
| `{compass_short}` | `SW` | Same, abbreviated. |
| `{movement}` | `approaching` / `stationary` / `departing` / `crossing` | Lowercase. |
| `{place}` | `Detroit, MI` / `Wayne County, MI` / `Beverly, MA` | Best-resolved place + state. Falls back gracefully when nothing resolved. |

**Authoring tip:** always assume `{near_city}` / `{town}` / `{county}` may be missing. The engine will quietly drop a sentence fragment if a slot is empty, but you'll get cleaner copy if you write 2–3 variants per bucket so we can pick one whose slots all resolved.

---

## Bucket structure

| Bucket | Trigger condition | Use |
|---|---|---|
| **NEAR** | `distance_mi < 30` | "You're close to Salem — in *<town/city/place>*, about *<miles>* miles away." |
| **APPROACHING** | `30 ≤ distance_mi ≤ 100` AND `movement = APPROACHING` | "You're heading our way — about *<miles>* to go. Want a tour while we go there?" |
| **MID_RANGE** | `30 ≤ distance_mi ≤ 100` AND `movement ≠ APPROACHING` (stationary, lateral, departing, unknown) | "You're *<miles>* miles from Salem in *<place>*. Want a tour from your chair?" |
| **FAR** | `distance_mi > 100` | "You're *<miles>* miles from Salem near *<place>*. Want a tour and let us walk for you?" |
| **DANVERS** | resolved IN_TOWN_ADJACENT_TO_SALEM AND `town = Danvers` | "You're in Danvers — historic Salem Village, where the 1692 trials really began..." (special tribute pool, see notes) |
| **UNKNOWN** | no GPS / no last-known fix / asset miss | Generic legacy welcome. |

---

## NEAR — distance < 30 mi

You're close to Salem (downtown's polygon, 30-mile buffer ring around it, or any of the 10 Salem-adjacent town polygons). Tone: "you've nearly made it / you're already here."

```
// TODO 01: NEAR — author 4–6 variants. Templates can use {miles}, {town}, {city}, {compass}, {place}.

// Example seed (replace freely):
"You're close to discovering Salem — about {miles} miles away in {place}."
```

```
// TODO 02:
```

```
// TODO 03:
```

```
// TODO 04:
```

```
// TODO 05:
```

```
// TODO 06:
```

---

## APPROACHING — 30–100 mi AND moving toward Salem

You're driving up. Tone: "we're getting ready for you / want a tour while we go there?"

```
// TODO 07: APPROACHING — author 4–6 variants. Templates can use {miles}, {place}, {compass}.

// Example seed (replace freely):
"Approaching Salem from {compass} — about {miles} to go. Would you like a tour while we head there?"
```

```
// TODO 08:
```

```
// TODO 09:
```

```
// TODO 10:
```

```
// TODO 11:
```

```
// TODO 12:
```

---

## MID_RANGE — 30–100 mi AND not moving toward Salem

Stationary, lateral, departing, or no-speed-data. Tone: "we know you're not on the road right now — fancy a tour from your chair?"

```
// TODO 13: MID_RANGE — author 4–6 variants. Templates can use {miles}, {place}, {compass}.

// Example seed (replace freely):
"You're {miles} miles from Salem, in {place}. Want a tour of Salem from your chair?"
```

```
// TODO 14:
```

```
// TODO 15:
```

```
// TODO 16:
```

```
// TODO 17:
```

```
// TODO 18:
```

---

## FAR — > 100 mi

You're far. Tone: warmer, more invitation, maybe playful — "let us walk for you."

```
// TODO 19: FAR — author 4–6 variants. Templates can use {miles}, {place}, {compass}, {state_long}.

// Example seed (replace freely):
"You're {miles} miles from Salem, near {place}. Would you like a tour of Salem and let us walk for you?"
```

```
// TODO 20:
```

```
// TODO 21:
```

```
// TODO 22:
```

```
// TODO 23:
```

```
// TODO 24:
```

---

## DANVERS — special pool when resolved town = Danvers

Modern Danvers was historic **Salem Village** — the actual courthouse-and-accusers center of the 1692 witch trials. Anyone the engine resolves as "in Danvers" deserves a different welcome than someone in Lynn or Marblehead. Override pool, not additive: when this fires the engine prefers it over generic NEAR.

```
// TODO 25: DANVERS — author 2–4 variants. Templates can use {miles}, {compass}.

// Example seed (replace freely):
"You're in Danvers — historic Salem Village, where the 1692 trials began. About {miles} miles from modern Salem, but the story really starts where you're standing."
```

```
// TODO 26:
```

```
// TODO 27:
```

```
// TODO 28:
```

---

## UNKNOWN — no GPS / no fix / fresh install

GPS permission not yet granted, no last-known location, or the polygon asset returned nothing. Fall back to the legacy welcome and a couple of generic alternates so even fresh installs feel varied.

```
"Welcome to Katrina's Mystic Visitors Guide, Historic Salem Tour App."
```

```
// TODO 29: UNKNOWN — author 2–4 generic welcomes that don't reference location.
```

```
// TODO 30:
```

```
// TODO 31:
```

```
// TODO 32:
```

---

## Authoring guardrails (V1)

- **PG-13 / IARC Teen** content rule applies. No profanity, no sexually explicit material, no graphic violence. (Memory: `feedback_pg13_content_rule.md`.)
- **Storytelling tone, not fact dumps.** Warm, witchy, inviting; the splash is the user's first contact. (Memory: `feedback_narration_storytelling_with_subtopics.md`.)
- **Don't narrate the gap.** No "we don't know much about" or "details remain scarce." Say what we know about Salem confidently. (Memory: `feedback_narration_no_meta_gaps.md`.)
- **Heritage Trail is yellow, not red.** If you reference it. (Memory: `reference_salem_heritage_trail_yellow.md`.)
- **Stay short.** TTS adds ~180ms per word in the Lenovo voice profile. 12-word lines = ~2.2 s; 20-word lines = ~3.6 s. The splash budget is forgiving (waits for `onDone`), but long lines drag the launch perception. Aim for 10–18 words per template.

---

## Future expansion (not authoring tasks for now)

- **Sunset/dusk pool** — the engine already knows civil twilight from lat/lon + system clock, with no extra data needed. Want a seasonal "the witching hour is yours" line that fires when within 30 min of sunset? Just say so and I'll add a SUNSET bucket schema.
- **Holiday / Halloween-week pool** — date-checked. Probably worth a separate bucket for Oct 28-Nov 1.
- **First-launch onboarding tie-in** — the same `LocationContext` will inform the 3-option screen (Tour / Witch Trials / Explore) when that screen lands. No authoring needed yet; this doc captures the input layer for that future work.
