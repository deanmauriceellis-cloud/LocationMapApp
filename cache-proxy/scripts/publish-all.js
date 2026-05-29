#!/usr/bin/env node
/*
 * cache-proxy/scripts/publish-all.js — Android content publish-chain
 * orchestrator (S304 tech-debt Phase 0a).
 *
 * Runs the 6 canonical publish scripts in the ONE correct order, fail-fast:
 * any non-zero exit aborts the chain immediately, so a stale or partial asset
 * never reaches the align/sign steps. Replaces the error-prone "remember to
 * run these six commands in this exact order" ritual in CLAUDE.md.
 *
 *   node cache-proxy/scripts/publish-all.js            # full real run
 *   node cache-proxy/scripts/publish-all.js --dry-run  # SAFE check: runs the 4
 *       PG->SQLite publish scripts in --dry-run (count-only, no writes) to
 *       verify PG connectivity + chain wiring, then SKIPS align + sign (those
 *       rewrite / sign the committed asset and can't be meaningfully dry-run).
 *   node cache-proxy/scripts/publish-all.js --from=5   # resume at step N (1-6)
 *
 * Canonical order (see CLAUDE.md "Android publish chain"):
 *   1 publish-salem-pois.js
 *   2 publish-tours.js
 *   3 publish-tour-legs.js
 *   4 publish-poi-collection.js
 *   5 align-asset-schema-to-room.js   (rewrites every Room table; MUST precede sign)
 *   6 sign-content-manifest.js        (MUST run last, AFTER align; prints manifestHash)
 */
const path = require('path');
const { spawnSync } = require('child_process');

// Load cache-proxy/.env so DATABASE_URL is in the environment the child
// scripts inherit (each also loads it via lib/env; this is belt-and-suspenders).
require('../lib/env').loadEnv();

const DRY_RUN = process.argv.includes('--dry-run');
const fromArg = process.argv.find((a) => a.startsWith('--from='));
const FROM = fromArg ? Math.max(1, parseInt(fromArg.split('=')[1], 10) || 1) : 1;

// dryRunnable: the 4 PG->SQLite publish scripts honour --dry-run (count-only,
// no writes). align + sign mutate / sign the committed asset, so they are
// skipped entirely in --dry-run rather than run.
const STEPS = [
  { n: 1, script: 'publish-salem-pois.js', dryRunnable: true },
  { n: 2, script: 'publish-tours.js', dryRunnable: true },
  { n: 3, script: 'publish-tour-legs.js', dryRunnable: true },
  { n: 4, script: 'publish-poi-collection.js', dryRunnable: true },
  { n: 5, script: 'align-asset-schema-to-room.js', dryRunnable: false },
  { n: 6, script: 'sign-content-manifest.js', dryRunnable: false },
];

function runStep(step, captureStdout) {
  const scriptPath = path.resolve(__dirname, step.script);
  const args = [scriptPath];
  if (DRY_RUN && step.dryRunnable) args.push('--dry-run');
  console.log(
    `\n==============================================================\n` +
    ` [${step.n}/6] ${step.script}${DRY_RUN && step.dryRunnable ? '  (--dry-run)' : ''}\n` +
    `==============================================================`
  );
  const res = spawnSync('node', args, {
    cwd: path.resolve(__dirname, '..'),
    stdio: captureStdout ? ['inherit', 'pipe', 'inherit'] : 'inherit',
    encoding: 'utf8',
  });
  if (captureStdout && res.stdout) process.stdout.write(res.stdout);
  if (res.error) {
    console.error(`\nx Chain ABORTED at step ${step.n} (${step.script}): ${res.error.message}`);
    process.exit(1);
  }
  if (res.status !== 0) {
    console.error(
      `\nx Chain ABORTED at step ${step.n} (${step.script}) — exit ${res.status}. ` +
      `Asset left as-is; downstream steps NOT run.`
    );
    process.exit(res.status || 1);
  }
  return res.stdout || '';
}

(function main() {
  console.log(
    `\n=== Android content publish chain ${DRY_RUN ? '(DRY RUN — align/sign skipped)' : '(LIVE)'} ===`
  );
  if (FROM > 1) console.log(`Resuming at step ${FROM}.`);

  let manifestHash = null;
  for (const step of STEPS) {
    if (step.n < FROM) {
      console.log(`\n- skipping step ${step.n} (${step.script}) [--from=${FROM}]`);
      continue;
    }
    if (DRY_RUN && !step.dryRunnable) {
      console.log(
        `\n- skipping step ${step.n} (${step.script}) in --dry-run (mutates/signs the committed asset)`
      );
      continue;
    }
    const out = runStep(step, step.n === 6);
    if (step.n === 6) {
      const m = out.match(/manifestHash\s*=\s*([0-9a-f]{64})/i);
      if (m) manifestHash = m[1];
    }
  }

  console.log(`\n=== Publish chain ${DRY_RUN ? 'dry-run ' : ''}complete. ===`);
  if (manifestHash) {
    console.log(`\n  manifestHash = ${manifestHash}`);
    console.log(`  -> paste into the activation Worker EXPECTED_MANIFEST_HASHES on deploy.`);
  } else if (!DRY_RUN) {
    console.log(`  (no manifestHash captured — check the sign-content-manifest output above.)`);
  }
})();
