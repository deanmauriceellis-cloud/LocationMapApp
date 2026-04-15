# Archived cache-proxy scripts (2026-04-15, S126)

These three scripts were one-shot Phase 9P / 9U migrators that targeted the
legacy `salem_tour_pois`, `salem_businesses`, and `salem_narration_points`
PG tables. Those tables were dropped in S126 once the unified `salem_pois`
table fully replaced them; the scripts will not run against current PG.

Preserved for historical reference only. Do not re-run.

- `import-narration-points.js` — bulk import of bundled narration_points (S98 / Phase 9P.1).
- `import-tour-pois-and-businesses.js` — bulk import of legacy tour POIs + businesses.
- `migrate-to-unified-pois.js` — three-table → salem_pois merge (S117 / Phase 9U).
