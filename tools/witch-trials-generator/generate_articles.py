"""
Generates the 16 Salem Witch Trials History articles by calling the local
Ollama instance directly at http://localhost:11434/api/chat with the
`salem-village:latest` model (Gemma3:27B Q4_K_M baked with Salem knowledge —
this is the same model the Salem Oracle uses internally).

We bypass Oracle's `/api/oracle/ask` because the Oracle wrapper has a hard
30-second client-side timeout to Ollama, and our 17k-char prompts + 600-1000
word generation routinely take 60-180 seconds. Bypassing also lets us drop
Oracle's RAG augmentation, which we don't need — we already inline a curated
events + facts bucket per tile.

Each article is checkpointed to output/article-NN.json on disk so a partial
run can be resumed by re-invoking with --resume (skips articles whose JSON
already exists).

Usage:
  python generate_articles.py                  # all 16
  python generate_articles.py --only 1         # smoke-test article 1
  python generate_articles.py --only 1,4,12    # subset
  python generate_articles.py --resume         # skip already-generated
"""

import argparse
import hashlib
import json
import re
import sys
import time
from pathlib import Path

import requests
from jinja2 import Environment, FileSystemLoader, select_autoescape

from salem_corpus_loader import bucket_articles, load_corpus

OLLAMA_CHAT_URL = "http://localhost:11434/api/chat"
OLLAMA_PS_URL = "http://localhost:11434/api/ps"
OLLAMA_TIMEOUT_SECONDS = 600  # 10 min per article — generous; typical run is 60-180s
OLLAMA_MODEL = "salem-village:latest"  # Gemma3:27B Q4_K_M with baked-in Salem knowledge
PROVENANCE_MODEL_LABEL = "ollama_direct_salem_village_gemma3_27b_q4km"

# Generation parameters. num_ctx must accommodate prompt (~5-7k tokens) + output
# (~1.5k tokens) with headroom. salem-village's modelfile sets 32k.
GEN_OPTIONS = {
    "temperature": 0.7,
    "top_p": 0.9,
    "num_ctx": 16384,
    "num_predict": 2048,  # cap output at ~2k tokens (well over our 1000-word target)
}

THIS_DIR = Path(__file__).resolve().parent
PROMPTS_DIR = THIS_DIR / "prompts"
OUTPUT_DIR = THIS_DIR / "output"


def render_prompt(article):
    """Pick the right template for the tile_kind and render the prompt string."""
    env = Environment(
        loader=FileSystemLoader(str(PROMPTS_DIR)),
        autoescape=select_autoescape(disabled_extensions=("j2",)),
        trim_blocks=False,
        lstrip_blocks=False,
    )

    kind = article["tile_kind"]
    if kind == "month" and article.get("is_quiet_month"):
        template_name = "quiet_month.j2"
    elif kind == "month":
        template_name = "monthly.j2"
    elif kind in ("intro", "fallout", "closing", "epilogue"):
        template_name = f"{kind}.j2"
    else:
        raise ValueError(f"unknown tile_kind: {kind!r}")

    tpl = env.get_template(template_name)
    return tpl.render(**article)


def check_ollama_ready():
    """Confirm Ollama is up and the salem-village model is in VRAM (or at
    least listed). Pre-warm with a tiny ping if needed."""
    try:
        r = requests.get(OLLAMA_PS_URL, timeout=5)
        r.raise_for_status()
        ps = r.json()
    except Exception as e:
        sys.exit(f"FATAL: Ollama ps check failed: {e}")

    loaded = [m["name"] for m in ps.get("models", [])]
    print(f"[ollama] currently loaded: {loaded if loaded else '(nothing)'}")
    if OLLAMA_MODEL not in loaded:
        print(f"[ollama] {OLLAMA_MODEL} not yet loaded — pre-warming with a 1-token ping...")
        warmup_payload = {
            "model": OLLAMA_MODEL,
            "messages": [{"role": "user", "content": "ok"}],
            "stream": False,
            "options": {"num_predict": 1},
        }
        t0 = time.time()
        try:
            requests.post(OLLAMA_CHAT_URL, json=warmup_payload, timeout=300).raise_for_status()
        except Exception as e:
            sys.exit(f"FATAL: pre-warm of {OLLAMA_MODEL} failed: {e}")
        print(f"[ollama] pre-warm complete in {time.time() - t0:.1f}s")


def call_llm(prompt):
    """One direct Ollama chat call. Returns the assistant message content."""
    payload = {
        "model": OLLAMA_MODEL,
        "messages": [{"role": "user", "content": prompt}],
        "stream": False,
        "options": GEN_OPTIONS,
    }
    r = requests.post(OLLAMA_CHAT_URL, json=payload, timeout=OLLAMA_TIMEOUT_SECONDS)
    r.raise_for_status()
    body = r.json()
    if "message" not in body:
        raise RuntimeError(f"unexpected Ollama response shape: {list(body.keys())}")
    return body["message"]["content"], body


def extract_article_json(llm_text):
    """The LLM is asked to return strict JSON. Sometimes a model wraps it
    in fences or trailing prose. Find the first {...} object that parses."""
    text = llm_text.strip()

    # Strip markdown fences if the model added them.
    fence = re.match(r"^```(?:json)?\s*(.*?)\s*```$", text, re.DOTALL)
    if fence:
        text = fence.group(1).strip()

    # Try the whole thing first.
    try:
        return json.loads(text)
    except json.JSONDecodeError:
        pass

    # Fall back: scan for the first balanced {...} block.
    start = text.find("{")
    while start != -1:
        depth = 0
        in_string = False
        escape = False
        for i in range(start, len(text)):
            ch = text[i]
            if escape:
                escape = False
                continue
            if ch == "\\":
                escape = True
                continue
            if ch == '"' and not escape:
                in_string = not in_string
                continue
            if in_string:
                continue
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    candidate = text[start : i + 1]
                    try:
                        return json.loads(candidate)
                    except json.JSONDecodeError:
                        break
        start = text.find("{", start + 1)

    raise ValueError(f"could not extract JSON from Oracle response (first 400 chars): {text[:400]!r}")


def parse_only(spec):
    """--only "1,4,12" -> set([1, 4, 12]). --only "1" -> set([1])."""
    if not spec:
        return None
    out = set()
    for piece in spec.split(","):
        piece = piece.strip()
        if not piece:
            continue
        out.add(int(piece))
    return out


def generate_one(article, output_path):
    """Render prompt, hash it, call the LLM, write checkpoint JSON."""
    prompt = render_prompt(article)
    prompt_hash = hashlib.sha256(prompt.encode("utf-8")).hexdigest()

    print(f"[gen] tile {article['tile_order']:2d} ({article['tile_kind']}) — {article['period_label']}")
    print(f"      prompt: {len(prompt):,} chars  events={article['event_count']} facts={article['fact_count']}  hash={prompt_hash[:12]}")

    t0 = time.time()
    raw_text, llm_meta = call_llm(prompt)
    elapsed = time.time() - t0
    eval_count = llm_meta.get("eval_count")
    eval_dur_ns = llm_meta.get("eval_duration") or 1
    tps = (eval_count / (eval_dur_ns / 1e9)) if eval_count else None
    print(f"      llm returned in {elapsed:.1f}s — eval_count={eval_count} tok/s={tps:.1f}" if tps else f"      llm returned in {elapsed:.1f}s")

    parsed = extract_article_json(raw_text)
    body = parsed.get("body", "")
    word_count = len(body.split())
    print(f"      parsed: title={parsed.get('title')!r}  body_words={word_count}  body_chars={len(body)}")

    record = {
        "tile_order":            article["tile_order"],
        "tile_kind":             article["tile_kind"],
        "period_label":          article["period_label"],
        "title":                 parsed.get("title", article["title"]),
        "teaser":                parsed.get("teaser", ""),
        "body":                  body,
        "related_event_ids":     [e["id"] for e in article["events_for_period"]],
        "related_fact_ids":      [f["id"] for f in article["facts_for_period"]],
        "generator_model":       PROVENANCE_MODEL_LABEL,
        "generator_prompt_hash": prompt_hash,
        "llm_elapsed_seconds":   round(elapsed, 1),
        "llm_eval_count":        eval_count,
        "llm_tokens_per_second": round(tps, 1) if tps else None,
        "_raw_llm_text":         raw_text,  # kept for debugging; importer drops this
    }

    output_path.write_text(json.dumps(record, indent=2, ensure_ascii=False))
    print(f"      wrote {output_path}")
    return record


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--only", help="Generate only these tile_orders, comma-separated (e.g. '1' or '1,4,12').")
    parser.add_argument("--resume", action="store_true", help="Skip articles whose output JSON already exists.")
    args = parser.parse_args()

    only = parse_only(args.only)

    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    check_ollama_ready()

    corpus = load_corpus()
    print(f"[corpus] events={len(corpus['events'])} facts={len(corpus['facts'])} primary_sources={len(corpus['primary_sources'])}")
    enriched = bucket_articles(corpus)

    selected = [a for a in enriched if (only is None or a["tile_order"] in only)]
    print(f"[plan] generating {len(selected)} of {len(enriched)} articles")

    failed = []
    t_total = time.time()
    for art in selected:
        out_path = OUTPUT_DIR / f"article-{art['tile_order']:02d}.json"
        if args.resume and out_path.exists():
            print(f"[skip] tile {art['tile_order']:2d} — checkpoint exists at {out_path.name}")
            continue
        try:
            generate_one(art, out_path)
        except Exception as e:
            print(f"[FAIL] tile {art['tile_order']:2d}: {e}")
            failed.append((art["tile_order"], str(e)))

    elapsed_total = time.time() - t_total
    print(f"\n[done] {len(selected) - len(failed)}/{len(selected)} articles in {elapsed_total/60:.1f} min")
    if failed:
        print("[failures]")
        for order, msg in failed:
            print(f"  tile {order}: {msg}")
        sys.exit(1)


if __name__ == "__main__":
    main()
