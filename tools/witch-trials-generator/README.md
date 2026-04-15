# witch-trials-generator

One-shot Python pipeline that generates the 16 Salem Witch Trials History articles
for Phase 9X.2 by calling the local Salem Oracle (`gemma3:27b` via Ollama) and
loading the results into the `salem_witch_trials_articles` PG table.

## GPU swap dance (READ FIRST)

The workstation has one RTX 3090. SalemIntelligence (`:8089`) and Salem Oracle
(`:8088`) cannot run at the same time — they fight over the GPU.

**Before running the generator:**
1. Operator stops SalemIntelligence (`:8089`).
2. Operator starts Salem Oracle (`:8088`) — wait for `available:true` from
   `curl http://localhost:8088/api/oracle/status`.
3. Run the generator (this directory).
4. When the run completes, operator stops Oracle and restarts SI so the rest
   of the LMA stack (admin tool editorial-AI, narration generation, etc.)
   keeps working.

The generator does not start or stop either service — it only consumes
Oracle's `/api/oracle/ask` endpoint.

## Layout

```
tools/witch-trials-generator/
├── README.md                  this file
├── requirements.txt
├── .gitignore
├── salem_corpus_loader.py     reads ~/Development/Salem/data/json/
├── generate_articles.py       calls Oracle, writes output/*.json
├── import_to_pg.py            loads output/*.json → salem_witch_trials_articles
├── prompts/                   Jinja templates per tile_kind
└── output/                    generated JSON (gitignored)
```

## Usage

```bash
cd tools/witch-trials-generator
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Smoke-test: generate just article #1 (Pre-1692 intro), inspect, abort if bad.
python generate_articles.py --only 1

# Full run: 16 articles sequentially, ~3-4 min each, ~50-60 min total.
# History is reset between articles. Each article is checkpointed to
# output/article-NN.json so the run can be resumed if it dies mid-way.
python generate_articles.py

# Load to PG (requires DATABASE_URL).
python import_to_pg.py
```

## The 16 articles

| # | tile_kind | period_label                        |
|--:|-----------|-------------------------------------|
|  1 | intro     | Before 1692 — The Village in Crisis |
|  2 | month     | January 1692                        |
|  3 | month     | February 1692                       |
|  4 | month     | March 1692                          |
|  5 | month     | April 1692                          |
|  6 | month     | May 1692                            |
|  7 | month     | June 1692                           |
|  8 | month     | July 1692                           |
|  9 | month     | August 1692                         |
| 10 | month     | September 1692                      |
| 11 | month     | October 1692                        |
| 12 | month     | November 1692                       |
| 13 | month     | December 1692                       |
| 14 | fallout   | 1693 — The Trials End               |
| 15 | closing   | The Aftermath (1694–1711)           |
| 16 | epilogue  | The Long Memory                     |

**November + December 1692:** Salem corpus has zero events for these months.
The corpus loader widens the prompt with adjacent facts and the prompt
template adds explicit "calm between executions" framing so the article
doesn't feel hollow.

## Provenance stamped on every row

| column | value |
|--------|-------|
| data_source | `salem_oracle` |
| confidence | `0.7` |
| verified_date | `NULL` (operator marks during admin review) |
| generator_model | `gemma3:27b` |
| generator_prompt_hash | sha256 of the rendered prompt |
| admin_dirty | `FALSE` initially |

Articles flagged for editorial review by setting `verified_date` to a real
date through the admin tool (Phase 9X.7).
