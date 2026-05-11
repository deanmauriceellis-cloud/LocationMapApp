# Archived cache-proxy scripts

These scripts were moved out of `cache-proxy/scripts/` as part of S242 tech-debt
reduction. They were one-shot migrations or fixtures that have already been
applied to the live PostgreSQL database (`locationmapapp`). They are preserved
here as an audit trail in case the operator needs to recreate columns,
re-investigate a past decision, or stand up a fresh DB from scratch.

**Do not run these blind on a live DB** — re-running an already-applied
migration may double-apply or corrupt data. Inspect the script first.

## Directories

- **`migrations-2026-04/`** — Date-prefixed one-shot SQL/JS migrations from the
  S174/S185/S214 schema-evolution sessions.
- **`one-shot-authoring/`** — Hand-authored POI content batches, narration
  upgrades, modern-attraction reclassification, audit-trigger install. The
  `install-audit-trigger.sql` script is idempotent and **safe to re-run on a
  fresh PG clone** to recreate the `salem_audit_log` trigger.
- **`historical-dedup/`** — S123/S135 dedup audit fixtures. Live dedup tooling
  (`dedup-live-clusters.js`, `dedup-merge-and-purge.js`) stays in
  `cache-proxy/scripts/` for ongoing use.
- **`one-shot-ingest/`** — MassGIS L3 parcel ingest, OSM pedestrian-edge ingest
  (S178 surgical allowlist), location-verify column scaffolding (S162), the
  launch-review ODT generator (S189).

## Load-bearing scripts NOT moved

The publish chain (auto-wired into Gradle preBuild) and ongoing admin tooling
stayed in `cache-proxy/scripts/`:

- `publish-salem-pois.js`
- `publish-tours.js`
- `publish-tour-legs.js`
- `align-asset-schema-to-room.js`
- `publish-splash-tree.js`
- `publish-witch-trials*.js` (3 variants)
- `bundle-witch-trials-*-into-db.js` (3 variants)
- `verify-bundled-assets.js`
- `dedup-live-clusters.js`, `dedup-merge-and-purge.js`
- `flag-narration-status.js`, `score-historical-narrations.js`, `auto-gen-narration-subtopics.js`
- `import-legacy-tours.js`, `build-heritage-trail-route.js`
- `sync-poi-taxonomy.js`
- `generate-npc-portraits.py`
