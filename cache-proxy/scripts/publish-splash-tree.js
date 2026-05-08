#!/usr/bin/env node
/*
 * S231 — Publish step 5: bake the splash-tree JSON authored in the web admin
 * into the bundled APK asset.
 *
 *   src:  cache-proxy/data/splash-tree-v1.json
 *   dst:  app-salem/src/main/assets/splash_tree_v1.json
 *
 * Validates against the same schema rules the cache-proxy /admin/splash/tree
 * PUT endpoint uses, so a failed validation here means whatever the operator
 * saved last is corrupt — the publish chain refuses to ship it. Loud failure
 * is the right answer; silent fallback would hide bad data behind the splash.
 *
 * Hook into Gradle preBuild (publishSalemContent task) so every :app-salem
 * build runs this automatically. Skip with -PskipPublishChain if needed.
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module publish-splash-tree.js';
void MODULE_ID;

const fs = require('fs');
const path = require('path');
const { validateTree } = require('../lib/splash-tree-types');

const SRC_PATH = path.resolve(__dirname, '..', 'data', 'splash-tree-v1.json');
const DST_PATH = path.resolve(__dirname, '..', '..', 'app-salem', 'src', 'main', 'assets', 'splash_tree_v1.json');

function main() {
  if (!fs.existsSync(SRC_PATH)) {
    console.error(`ERROR: source not found: ${SRC_PATH}`);
    console.error('       seed cache-proxy/data/splash-tree-v1.json or run the admin tool first.');
    process.exit(1);
  }
  const raw = fs.readFileSync(SRC_PATH, 'utf8');
  let tree;
  try {
    tree = JSON.parse(raw);
  } catch (e) {
    console.error(`ERROR: ${SRC_PATH} is not valid JSON: ${e.message}`);
    process.exit(1);
  }

  const v = validateTree(tree);
  if (!v.ok) {
    console.error('ERROR: splash-tree validation failed:');
    for (const e of v.errors) console.error('  - ' + e);
    process.exit(1);
  }

  // Strip authoring-only fields if any creep in (none for now, but future-proof).
  // The runtime JSON is byte-identical to authoring JSON for v1.
  fs.writeFileSync(DST_PATH, JSON.stringify(tree) + '\n', 'utf8');
  const sizeKb = (fs.statSync(DST_PATH).size / 1024).toFixed(2);

  let totalVariants = 0;
  for (const b of tree.buckets) totalVariants += b.variants.length;
  totalVariants += tree.fallback.variants.length;

  console.log('PUBLISH SPLASH TREE COMPLETE');
  console.log(`  buckets:  ${tree.buckets.length} + 1 fallback`);
  console.log(`  variants: ${totalVariants}`);
  console.log(`  size:     ${sizeKb} KB`);
  console.log(`  asset:    ${DST_PATH}`);
}

main();
