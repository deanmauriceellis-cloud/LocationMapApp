#!/usr/bin/env node
/*
 * auto-gen-narration-subtopics.js — Session 220
 *
 * Synthesizes narration_subtopics candidates for POIs that don't yet
 * have any. Uses two local sources, no SalemIntelligence/Oracle calls
 * (provenance pause S214):
 *
 *   1. Figure detection — scans each POI's narration text for any of
 *      the 49 NPC names from salem_witch_trials_npc_bios. For each
 *      hit, emits a subtopic with source_kind='figure'.
 *
 *   2. Adjacency — finds 1 nearby active POI (≤200m, different
 *      category, has narration) and emits one subtopic with
 *      source_kind='adjacent_poi'. Capped at 1 per POI.
 *
 * The whole script is conservative:
 *   - Whole-word case-insensitive name matching with multi-variant
 *     lookups (split on em-dash for dual-named NPCs).
 *   - Skip variants shorter than 8 chars (prevents "Ann"/"Mary" false hits).
 *   - Cap 3 figure cards + 1 adjacency card = 4 cards max per POI.
 *   - By default, skips any POI that already has a non-empty
 *     narration_subtopics value. --overwrite forces re-run.
 *
 * Each emitted subtopic carries source_kind + source_ref so the admin
 * editor (web/src/admin/SubtopicEditor.tsx) shows the operator which
 * are auto-generated and which were hand-authored.
 *
 * Usage:
 *   node cache-proxy/scripts/auto-gen-narration-subtopics.js
 *   node cache-proxy/scripts/auto-gen-narration-subtopics.js --dry-run
 *   node cache-proxy/scripts/auto-gen-narration-subtopics.js --limit 50
 *   node cache-proxy/scripts/auto-gen-narration-subtopics.js --poi-id charter_street_cemetery
 *   node cache-proxy/scripts/auto-gen-narration-subtopics.js --overwrite
 */

// .env is loaded by `source cache-proxy/.env` from the shell. If DATABASE_URL
// isn't present, fall back to reading .env directly so the script works
// regardless of how it's invoked.
if (!process.env.DATABASE_URL) {
  const fs = require('fs');
  const path = require('path');
  const envPath = path.join(__dirname, '..', '.env');
  if (fs.existsSync(envPath)) {
    const envText = fs.readFileSync(envPath, 'utf8');
    for (const line of envText.split('\n')) {
      const m = line.match(/^\s*([A-Z0-9_]+)\s*=\s*(.*)\s*$/);
      if (m && !process.env[m[1]]) {
        let val = m[2];
        if (val.startsWith('"') && val.endsWith('"')) val = val.slice(1, -1);
        else if (val.startsWith("'") && val.endsWith("'")) val = val.slice(1, -1);
        process.env[m[1]] = val;
      }
    }
  }
}

const { Pool } = require('pg');

if (!process.env.DATABASE_URL) {
  console.error('Error: DATABASE_URL environment variable is required');
  process.exit(1);
}

const args = process.argv.slice(2);
const DRY_RUN = args.includes('--dry-run');
const OVERWRITE = args.includes('--overwrite');
const LIMIT = (() => {
  const i = args.indexOf('--limit');
  return i >= 0 ? parseInt(args[i + 1], 10) : null;
})();
const POI_ID_FILTER = (() => {
  const i = args.indexOf('--poi-id');
  return i >= 0 ? args[i + 1] : null;
})();

const ADJACENCY_RADIUS_M = 200;
const MIN_NARRATION_LEN = 200;       // adjacency candidate must have at least this much narration
const MAX_FIGURE_CARDS = 3;
const MAX_ADJACENT_CARDS = 1;
const MIN_VARIANT_LEN = 8;            // ignore variants shorter than this (prevents "Ann"/"Mary" hits)
const FIGURE_BODY_MAX = 800;          // chars
const ADJACENT_BODY_MAX = 280;        // chars

// FENCE — only Historical Buildings / Historical Landmarks / Civic / Religious
// (Worship) and explicitly-flagged historical / witch-trial / civic POIs
// receive auto-gen subtopics. Commercial categories (Shopping, Food & Drink,
// Lodging, Services, Healthcare, Auto, Offices, Witch Shop, Tour Companies,
// Education, etc.) are excluded — operator rule S220.
//
// Note: is_tour_poi is intentionally NOT in this fence. The flag is over-set
// in production (e.g. Young World Academy is_tour_poi=true), and the
// operator's spec is category-and-historical-flag based, not tour-curation
// based.
const ELIGIBLE_CATEGORIES = new Set([
  'HISTORICAL_LANDMARKS',
  'HISTORICAL_BUILDINGS',
  'CIVIC',
  'WORSHIP',
]);
function isEligible(p) {
  return ELIGIBLE_CATEGORIES.has(p.category) ||
         p.is_historical_property === true ||
         p.is_civic_poi === true ||
         p.is_witch_trial_site === true;
}

const pool = new Pool({ connectionString: process.env.DATABASE_URL });

function escapeRegex(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function nameVariants(npc) {
  const raw = String(npc.name || '');
  const halves = raw.split(/\s+[—\-–]\s+/).map(s => s.trim()).filter(Boolean);
  const variants = new Set();
  for (const h of halves) variants.add(h);
  variants.add(raw);
  return [...variants].filter(v => v.length >= MIN_VARIANT_LEN);
}

function compileNpcMatcher(npcs) {
  return npcs.map(n => ({
    npc: n,
    variants: nameVariants(n),
    patterns: nameVariants(n).map(v => new RegExp(`\\b${escapeRegex(v)}\\b`, 'i')),
  })).filter(m => m.patterns.length > 0);
}

function findFigureMatches(text, matchers) {
  if (!text || !text.trim()) return [];
  const hits = [];
  for (const m of matchers) {
    if (m.patterns.some(p => p.test(text))) {
      hits.push(m.npc);
    }
  }
  // longer name = more specific = ranked higher
  hits.sort((a, b) => (b.name.length - a.name.length));
  return hits.slice(0, MAX_FIGURE_CARDS);
}

function trimToSentence(text, max) {
  if (!text) return '';
  if (text.length <= max) return text.trim();
  const head = text.substring(0, max);
  const lastPeriod = Math.max(head.lastIndexOf('. '), head.lastIndexOf('! '), head.lastIndexOf('? '));
  if (lastPeriod > max * 0.6) return head.substring(0, lastPeriod + 1).trim();
  return head.replace(/\s+\S*$/, '').trim() + '…';
}

function firstSentences(text, max) {
  if (!text) return '';
  const sentences = text.match(/[^.!?]+[.!?]+/g) || [];
  if (sentences.length === 0) return trimToSentence(text, max);
  let out = '';
  for (const s of sentences) {
    if ((out + s).length > max) break;
    out += s;
  }
  return (out || sentences[0]).trim() || trimToSentence(text, max);
}

function nameTokens(name) {
  return new Set(
    String(name || '')
      .toLowerCase()
      .replace(/[^\w\s]/g, ' ')
      .split(/\s+/)
      .filter(t => t.length >= 4)  // ignore "the", "of", "at", short prepositions/articles
  );
}

/** Returns true if two POI names look like dupes — substring containment
 *  or >50% overlap of significant tokens. Used to suppress adjacency
 *  cards that point at the source POI's own duplicate row. */
function looksLikeDupe(name1, name2) {
  const a = String(name1 || '').toLowerCase().trim();
  const b = String(name2 || '').toLowerCase().trim();
  if (!a || !b) return false;
  if (a === b) return true;
  if (a.includes(b) || b.includes(a)) return true;
  const t1 = nameTokens(name1);
  const t2 = nameTokens(name2);
  if (t1.size === 0 || t2.size === 0) return false;
  let shared = 0;
  for (const tok of t1) if (t2.has(tok)) shared++;
  const smaller = Math.min(t1.size, t2.size);
  return smaller > 0 && shared / smaller >= 0.5;
}

function haversineM(lat1, lng1, lat2, lng2) {
  const R = 6371000;
  const toRad = d => d * Math.PI / 180;
  const dLat = toRad(lat2 - lat1);
  const dLng = toRad(lng2 - lng1);
  const a = Math.sin(dLat / 2) ** 2 +
            Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) *
            Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(a));
}

function buildFigureSubtopic(npc) {
  const role = npc.role && npc.role.trim() ? npc.role.trim() : null;
  const lead = role ? `${role}. ` : '';
  const body = trimToSentence(lead + (npc.bio || ''), FIGURE_BODY_MAX);
  return {
    header: npc.name,
    body,
    source_kind: 'figure',
    source_ref: npc.id,
  };
}

function buildAdjacentSubtopic(nearby, distanceM) {
  const teaser = nearby.short_narration || nearby.description || '';
  const body = firstSentences(teaser, ADJACENT_BODY_MAX) || `${nearby.name} is nearby.`;
  const distNote = distanceM < 50 ? 'just steps away' :
                   distanceM < 100 ? 'a short walk away' :
                   `about ${Math.round(distanceM)} m away`;
  return {
    header: `Nearby: ${nearby.name}`,
    body: `${body}\n\n(${distNote})`,
    source_kind: 'adjacent_poi',
    source_ref: nearby.id,
  };
}

function poiNarrationText(p) {
  const parts = [p.long_narration, p.short_narration, p.historical_narration, p.description, p.name]
    .filter(s => typeof s === 'string' && s.trim());
  return parts.join('\n\n');
}

async function main() {
  console.log(`auto-gen-narration-subtopics — ${DRY_RUN ? 'DRY RUN' : 'LIVE'}, overwrite=${OVERWRITE}, limit=${LIMIT || 'none'}, poiFilter=${POI_ID_FILTER || 'none'}`);
  console.log('');

  const npcRes = await pool.query(`
    SELECT id, name, display_name, role, faction, bio
    FROM salem_witch_trials_npc_bios
    WHERE deleted_at IS NULL
    ORDER BY tier, name
  `);
  const npcs = npcRes.rows;
  console.log(`Loaded ${npcs.length} active NPCs.`);
  const matchers = compileNpcMatcher(npcs);
  console.log(`Compiled ${matchers.length} name matchers.`);
  console.log('');

  const allPoiRes = await pool.query(`
    SELECT id, name, lat, lng, category,
           short_narration, long_narration, historical_narration, description,
           narration_subtopics, deleted_at,
           is_historical_property, is_tour_poi, is_witch_trial_site, is_civic_poi
    FROM salem_pois
    WHERE deleted_at IS NULL
    ORDER BY id
  `);
  const allPois = allPoiRes.rows;
  console.log(`Loaded ${allPois.length} active POIs.`);
  console.log('');

  // Source-POI fence: only eligible categories + flagged POIs receive
  // auto-gen. Commercial POIs are skipped entirely.
  let targets = allPois.filter(p => isEligible(p));
  console.log(`${targets.length} POIs pass eligibility fence (HISTORICAL/CIVIC/WORSHIP categories + flagged).`);

  targets = targets.filter(p => OVERWRITE || !p.narration_subtopics || p.narration_subtopics.length === 0);
  if (POI_ID_FILTER) targets = targets.filter(p => p.id === POI_ID_FILTER);
  if (LIMIT) targets = targets.slice(0, LIMIT);
  console.log(`${targets.length} POIs after overwrite/filter/limit guards.`);
  console.log('');

  // Adjacency lookup pool: same eligibility fence — never link to a
  // commercial POI as "Nearby". Plus a substantive-narration cutoff so
  // adjacent cards have something to teaser.
  const adjacencyPool = allPois.filter(p =>
    p.lat != null && p.lng != null &&
    typeof p.long_narration === 'string' && p.long_narration.length >= MIN_NARRATION_LEN &&
    isEligible(p)
  );
  console.log(`Adjacency pool: ${adjacencyPool.length} eligible POIs with substantive narration.`);
  console.log('');

  let updated = 0;
  let skippedNoCards = 0;
  const summary = { figureOnly: 0, figurePlusAdjacent: 0, adjacentOnly: 0 };

  for (const poi of targets) {
    const text = poiNarrationText(poi);
    const figures = findFigureMatches(text, matchers);
    const figureCards = figures.map(buildFigureSubtopic);

    let adjacentCard = null;
    if (figureCards.length < MAX_FIGURE_CARDS + MAX_ADJACENT_CARDS && poi.lat != null && poi.lng != null) {
      const candidates = [];
      for (const cand of adjacencyPool) {
        if (cand.id === poi.id) continue;
        if (looksLikeDupe(poi.name, cand.name)) continue;
        const d = haversineM(poi.lat, poi.lng, cand.lat, cand.lng);
        if (d > ADJACENCY_RADIUS_M) continue;
        candidates.push({ cand, d });
      }
      candidates.sort((a, b) => a.d - b.d);
      if (candidates.length > 0) {
        adjacentCard = buildAdjacentSubtopic(candidates[0].cand, candidates[0].d);
      }
    }

    const cards = [...figureCards];
    if (adjacentCard) cards.push(adjacentCard);

    if (cards.length === 0) {
      skippedNoCards++;
      continue;
    }

    if (figureCards.length > 0 && adjacentCard) summary.figurePlusAdjacent++;
    else if (figureCards.length > 0) summary.figureOnly++;
    else summary.adjacentOnly++;

    if (DRY_RUN) {
      console.log(`[DRY] ${poi.id} → ${cards.length} card(s):`);
      for (const c of cards) console.log(`        ${c.source_kind}: ${c.header}`);
    } else {
      await pool.query(
        `UPDATE salem_pois SET narration_subtopics = $1, updated_at = now() WHERE id = $2`,
        [JSON.stringify(cards), poi.id]
      );
      updated++;
      if (updated % 50 === 0) console.log(`  …updated ${updated}`);
    }
  }

  console.log('');
  console.log('— Summary —');
  console.log(`  Eligible POIs           : ${targets.length}`);
  console.log(`  POIs updated            : ${DRY_RUN ? '(dry run)' : updated}`);
  console.log(`  Skipped (no cards)      : ${skippedNoCards}`);
  console.log(`  figure-only POIs        : ${summary.figureOnly}`);
  console.log(`  figure + adjacent POIs  : ${summary.figurePlusAdjacent}`);
  console.log(`  adjacent-only POIs      : ${summary.adjacentOnly}`);
  console.log('');
  console.log('Next steps after a live run:');
  console.log('  node cache-proxy/scripts/publish-salem-pois.js');
  console.log('  node cache-proxy/scripts/align-asset-schema-to-room.js');
  console.log('  ./gradlew :app-salem:assembleDebug');
  console.log('  adb -s HNY0CY0W uninstall com.destructiveaigurus.katrinasmysticvisitorsguide && adb -s HNY0CY0W install <apk>');

  await pool.end();
}

main().catch(err => {
  console.error('FATAL:', err);
  process.exit(1);
});
