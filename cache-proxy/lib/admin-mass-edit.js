/*
 * LocationMapApp v1.5
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin Mass-Edit endpoints (S241).
 *
 * Round-trip workflow:
 *   1. Operator clicks "Export" in the admin tool's Mass Edit tab.
 *      GET /admin/salem/pois/export-spreadsheet streams an .xlsx file
 *      with frozen header row, per-column autofilter, hidden column A
 *      carrying POI UUIDs, and a hidden "Export Info" sheet with the
 *      export timestamp + column manifest.
 *
 *   2. Operator opens the .xlsx in LibreOffice Calc, mass-edits cells,
 *      saves (as .xlsx or .ods).
 *
 *   3. Operator uploads the edited file via the Mass Edit tab.
 *      POST /admin/salem/pois/import-spreadsheet (multipart) parses the
 *      file, diffs every cell against the live PG row, returns a JSON
 *      changeset { changeset: [{ poi_id, poi_name, stale, changes: [...] }] }.
 *
 *   4. Operator approves/rejects per-cell in the review UI, hits Apply.
 *      POST /admin/salem/pois/apply-mass-edit takes the approved cells,
 *      groups by poi_id, applies in a single PG transaction via the
 *      existing buildUpdateClause() helper (mirrors PUT /admin/salem/pois/:id
 *      validation + whitelist + JSONB serialization).
 *
 * Editable column set = admin-pois.UPDATABLE_FIELDS minus EXCLUDE_FROM_SPREADSHEET
 * (JSONB-complex / geometry / pipeline-managed columns kept in the per-POI
 * PoiEditDialog only). See EXCLUDE_FROM_SPREADSHEET below for the list.
 *
 * Routes (all gated by /admin Basic Auth — middleware applied at app level):
 *   GET  /admin/salem/pois/export-spreadsheet
 *   POST /admin/salem/pois/import-spreadsheet   (multipart, 'file' field)
 *   POST /admin/salem/pois/apply-mass-edit      (JSON { approvals: [...] })
 */
const MODULE_ID = '(C) Dean Maurice Ellis, 2026 - Module admin-mass-edit.js';

const fs = require('fs');
const path = require('path');
const XLSX = require('xlsx');
const JSZip = require('jszip');
const multer = require('multer');

const {
  UPDATABLE_FIELDS,
  JSONB_FIELDS,
  buildUpdateClause,
} = require('./admin-pois');

// ─── Editable / excluded column sets ─────────────────────────────────────────

// Columns intentionally kept out of the spreadsheet round-trip. Operators
// edit these via the per-POI dialog only.
//   narration_subtopics    — JSONB array of {header, body, source_kind?, source_ref?}
//                            (S219); nested objects don't survive a flat cell.
//   action_buttons         — JSONB array of {label, url} objects.
//   hours                  — JSONB structured weekly hours. hours_text mirror
//                            (plain string) stays editable in the spreadsheet.
//   corridor_points        — encoded geofence vertex string; fragile.
//   building_footprint_geojson — polygon geometry; large + nested.
//   intel_entity_id, source_id, stale_after — rarely-touched provenance refs.
const EXCLUDE_FROM_SPREADSHEET = new Set([
  'narration_subtopics',
  'action_buttons',
  'hours',
  'corridor_points',
  'building_footprint_geojson',
  'intel_entity_id',
  'source_id',
  'stale_after',
]);

// Editable columns in the spreadsheet, in a stable display order. Order matters
// — operator scrolls these columns left-to-right and Export Info embeds this
// exact list so the importer can verify the file structure on round-trip.
const EDITABLE_COLUMNS = UPDATABLE_FIELDS.filter(f => !EXCLUDE_FROM_SPREADSHEET.has(f));

// Read-only context columns shown in the spreadsheet for filtering / sorting
// but locked. id is hidden in column A so it never displays but is available
// to the importer for stable POI matching even if name/category changes.
const READONLY_HIDDEN = ['id'];

// Pinned to the FRONT of the visible columns (right after hidden id) so the
// operator can use them as the primary group/filter dimensions when scanning
// 2000+ rows. category + subcategory are normal editable cells.
const PINNED_EDITABLE_FRONT = ['category', 'subcategory'];

// S241.1: is_deleted is now a writable "intent" column. It looks like a normal
// boolean cell to the operator, but the apply path treats it specially — flips
// route to canonical soft-delete / restore SQL (mirroring admin-pois.js
// DELETE/restore endpoints) rather than going through buildUpdateClause
// (deleted_at is not in UPDATABLE_FIELDS by design). Position: column D, right
// after category/subcategory.
const IS_DELETED_INTENT_COLUMN = 'is_deleted';

// Pushed to the BACK of the visible columns — useful but not primary axes.
const READONLY_VISIBLE_BACK = ['created_at', 'updated_at'];

// Boolean and numeric subsets (mirror of admin-pois.js JSONB_FIELDS plus
// poiAdminFields.ts BOOLEAN/NUMERIC/DATE sets — kept in sync by hand).
const BOOLEAN_COLUMNS = new Set([
  'requires_transportation', 'wheelchair_accessible', 'seasonal',
  'is_tour_poi', 'is_civic_poi', 'is_narrated', 'default_visible',
  'has_announce_narration',
  'is_historical_property', 'is_witch_trial_site', 'is_free_admission',
  'is_indoor', 'is_family_friendly',
  'location_truth_of_record',
  'haunt_enabled',
]);

const NUMERIC_COLUMNS = new Set([
  'lat', 'lng', 'geofence_radius_m', 'priority', 'wave', 'rating',
  'confidence', 'ad_priority', 'merchant_tier', 'year_established',
  'mhc_year_built',
  'haunt_outer_range_m', 'haunt_outer_interval_s',
  'haunt_inner_range_m', 'haunt_inner_interval_s',
  'haunt_duration_s',
]);

const DATE_COLUMNS = new Set([
  'verified_date',
]);

// S241.2 — Data-validation pick-list bindings. Each editable column listed
// here gets an in-cell dropdown of the named list. allowBlank=1 + showError=0
// means operator can still type a value not in the list (overrides the pick).
const HAUNT_SPRITE_IDS  = ['witch', 'owl', 'black-cat', 'katrina-kitty', 'skeleton', 'mouse', 'rat'];
const PRICE_RANGES      = ['$', '$$', '$$$', '$$$$'];
const STATUS_VALUES     = ['open', 'temporarily_closed', 'permanently_closed', 'seasonal'];
const GEOFENCE_SHAPES   = ['circle', 'polygon'];
const LOCATION_STATUSES = ['unverified', 'accepted', 'no_address', 'needs_review', 'no_match'];
const MERCHANT_TIERS    = ['0', '1', '2', '3'];
const TRUE_FALSE        = ['TRUE', 'FALSE'];

// column → name of the list in the (hidden) "Validation Lists" sheet.
// Note: category + subcategory lists are populated dynamically from PG at
// export time; the others are static. Booleans are wired below the binding map.
const VALIDATION_BINDINGS = {
  'category':         'categories',
  'subcategory':      'subcategories',
  'price_range':      'price_range',
  'status':           'status',
  'geofence_shape':   'geofence_shape',
  'location_status':  'location_status',
  'haunt_sprite_id':  'haunt_sprite_id',
  'merchant_tier':    'merchant_tier',
};
// Every boolean column + the is_deleted intent gets a TRUE/FALSE pick.
for (const b of BOOLEAN_COLUMNS) VALIDATION_BINDINGS[b] = 'true_false';
VALIDATION_BINDINGS['is_deleted'] = 'true_false';

// Lat/lng safety bbox for proposed coordinate edits (Salem area, matches
// admin-lint VALIDATE_SALEM_* but slightly looser to allow Beverly/Peabody).
const SALEM_LAT_MIN = 42.4;
const SALEM_LAT_MAX = 42.6;
const SALEM_LNG_MIN = -71.0;
const SALEM_LNG_MAX = -70.7;

// ─── Cell normalization + diff ───────────────────────────────────────────────

function colKind(field) {
  if (BOOLEAN_COLUMNS.has(field)) return 'boolean';
  if (NUMERIC_COLUMNS.has(field)) return 'numeric';
  if (DATE_COLUMNS.has(field)) return 'date';
  if (JSONB_FIELDS.has(field)) return 'jsonb';
  return 'text';
}

function cellToText(cell) {
  if (cell === undefined || cell === null) return '';
  if (typeof cell === 'string') return cell;
  if (typeof cell === 'number') return String(cell);
  if (typeof cell === 'boolean') return cell ? 'TRUE' : 'FALSE';
  return String(cell);
}

function parseBoolean(raw) {
  if (raw === null || raw === undefined || raw === '') return { value: null, blank: true };
  if (typeof raw === 'boolean') return { value: raw };
  const s = String(raw).trim().toLowerCase();
  if (s === '' ) return { value: null, blank: true };
  if (s === 'true' || s === 't' || s === '1' || s === 'yes' || s === 'y') return { value: true };
  if (s === 'false' || s === 'f' || s === '0' || s === 'no' || s === 'n') return { value: false };
  return { error: `not a boolean: "${raw}" (expected TRUE/FALSE/1/0/yes/no)` };
}

function parseNumeric(raw) {
  if (raw === null || raw === undefined || raw === '') return { value: null, blank: true };
  if (typeof raw === 'number') {
    if (!Number.isFinite(raw)) return { error: `not a finite number: ${raw}` };
    return { value: raw };
  }
  const s = String(raw).trim();
  if (s === '') return { value: null, blank: true };
  const n = Number(s);
  if (!Number.isFinite(n)) return { error: `not a finite number: "${raw}"` };
  return { value: n };
}

function parseJsonbArray(raw) {
  if (raw === null || raw === undefined || raw === '') return { value: null, blank: true };
  const s = String(raw).trim();
  if (s === '') return { value: null, blank: true };
  try {
    const parsed = JSON.parse(s);
    return { value: parsed };
  } catch (e) {
    return { error: `not valid JSON: ${e.message}` };
  }
}

function parseDate(raw) {
  if (raw === null || raw === undefined || raw === '') return { value: null, blank: true };
  // SheetJS may give us a JS Date object if the cell was formatted as a date.
  if (raw instanceof Date) {
    const iso = raw.toISOString().slice(0, 10);
    return { value: iso };
  }
  const s = String(raw).trim();
  if (s === '') return { value: null, blank: true };
  // Accept YYYY-MM-DD plain or longer ISO (just trim).
  const m = s.match(/^(\d{4})-(\d{2})-(\d{2})/);
  if (m) return { value: `${m[1]}-${m[2]}-${m[3]}` };
  return { error: `not a YYYY-MM-DD date: "${raw}"` };
}

// Compare a parsed cell value against the live PG value. Returns null if
// no change is proposed, or { old, new, kind } for the changeset.
function diffCell(field, kind, parsed, pgRow) {
  const pgVal = pgRow[field];

  // For blank cells: numeric/boolean/date preserve PG (no change). For text
  // and jsonb, blank means "clear to NULL".
  if (parsed.blank) {
    if (kind === 'text') {
      if (pgVal === null || pgVal === undefined || pgVal === '') return null;
      return { old: cellToText(pgVal), new: null, kind };
    }
    if (kind === 'jsonb') {
      if (pgVal === null || pgVal === undefined) return null;
      // Treat empty array / empty object as "no value".
      if (Array.isArray(pgVal) && pgVal.length === 0) return null;
      if (typeof pgVal === 'object' && pgVal !== null && Object.keys(pgVal).length === 0) return null;
      return { old: JSON.stringify(pgVal), new: null, kind };
    }
    // numeric / boolean / date — blank means no change
    return null;
  }

  if (kind === 'text') {
    const newStr = String(parsed.value);
    const oldStr = pgVal === null || pgVal === undefined ? '' : String(pgVal);
    if (newStr === oldStr) return null;
    return { old: oldStr, new: newStr, kind };
  }

  if (kind === 'boolean') {
    const newBool = parsed.value;
    const oldBool = pgVal === null || pgVal === undefined ? null : Boolean(pgVal);
    if (newBool === oldBool) return null;
    return { old: oldBool === null ? '' : (oldBool ? 'TRUE' : 'FALSE'),
             new: newBool ? 'TRUE' : 'FALSE',
             kind };
  }

  if (kind === 'numeric') {
    const newNum = parsed.value;
    const oldNum = pgVal === null || pgVal === undefined ? null : Number(pgVal);
    if (oldNum === newNum) return null;
    // Floating-point near-equality (lat/lng): treat values within 1e-7 as equal.
    if (typeof oldNum === 'number' && typeof newNum === 'number'
        && Math.abs(oldNum - newNum) < 1e-7) {
      return null;
    }
    return { old: oldNum === null ? '' : String(oldNum), new: String(newNum), kind };
  }

  if (kind === 'date') {
    const newDate = parsed.value;
    const oldDate = pgVal === null || pgVal === undefined ? '' :
                    (pgVal instanceof Date ? pgVal.toISOString().slice(0, 10) : String(pgVal).slice(0, 10));
    if (newDate === oldDate) return null;
    return { old: oldDate, new: newDate, kind };
  }

  if (kind === 'jsonb') {
    // Compare canonical-JSON forms.
    const newCanon = JSON.stringify(parsed.value);
    const oldCanon = pgVal === null || pgVal === undefined ? null : JSON.stringify(pgVal);
    if (newCanon === oldCanon) return null;
    return { old: oldCanon || '', new: newCanon, kind };
  }

  return null;
}

// Lat/lng bbox sanity for proposed coordinate changes — surfaces a warning
// but does NOT block (operator may legitimately move a POI just outside Salem
// if a row was misplaced). The frontend uses the warning flag to leave the
// approve checkbox unchecked by default.
function latLngWarning(field, parsedValue) {
  if (field === 'lat') {
    if (parsedValue < SALEM_LAT_MIN || parsedValue > SALEM_LAT_MAX) {
      return `lat outside Salem bbox [${SALEM_LAT_MIN}, ${SALEM_LAT_MAX}]`;
    }
  }
  if (field === 'lng') {
    if (parsedValue < SALEM_LNG_MIN || parsedValue > SALEM_LNG_MAX) {
      return `lng outside Salem bbox [${SALEM_LNG_MIN}, ${SALEM_LNG_MAX}]`;
    }
  }
  return null;
}

// ─── Workbook construction (export) ──────────────────────────────────────────

function buildWorkbook(pois, exportedAt, validationListsAoa) {
  // Column order:
  //   [id (hidden)] [category, subcategory] [is_deleted (intent)] [name, lat, lng, ...remaining editable] [created_at, updated_at]
  const trailingEditable = EDITABLE_COLUMNS.filter(c => !PINNED_EDITABLE_FRONT.includes(c));
  const headerRow = [
    ...READONLY_HIDDEN,
    ...PINNED_EDITABLE_FRONT,
    IS_DELETED_INTENT_COLUMN,
    ...trailingEditable,
    ...READONLY_VISIBLE_BACK,
  ];

  // Build data rows. JSONB columns serialized as JSON strings. Booleans as
  // TRUE/FALSE. Dates as YYYY-MM-DD. Nulls as empty cells.
  const dataRows = pois.map(p => headerRow.map(col => {
    // is_deleted is a synthetic read-only boolean derived from deleted_at.
    if (col === 'is_deleted') return p.deleted_at ? 'TRUE' : 'FALSE';

    const v = p[col];
    if (v === null || v === undefined) return '';
    if (BOOLEAN_COLUMNS.has(col)) return v ? 'TRUE' : 'FALSE';
    if (DATE_COLUMNS.has(col)) {
      if (v instanceof Date) return v.toISOString().slice(0, 10);
      return String(v).slice(0, 10);
    }
    if (JSONB_FIELDS.has(col)) {
      try { return JSON.stringify(v); } catch (e) { return ''; }
    }
    if (col === 'created_at' || col === 'updated_at') {
      // PG returns timestamp as JS Date when pg is configured with date types;
      // fall back to string slice.
      if (v instanceof Date) return v.toISOString();
      return String(v);
    }
    return v;
  }));

  const sheet = XLSX.utils.aoa_to_sheet([headerRow, ...dataRows]);

  // ── Sheet metadata ────────────────────────────────────────────────────────
  // Autofilter across the entire header row.
  const lastColLetter = XLSX.utils.encode_col(headerRow.length - 1);
  sheet['!autofilter'] = { ref: `A1:${lastColLetter}1` };

  // Frozen pane: top row stays put when scrolling.
  sheet['!views'] = [{ state: 'frozen', ySplit: 1 }];

  // Column widths + hidden flags. Column A (id) hidden so the operator
  // never sees the UUID; importer still reads it.
  sheet['!cols'] = headerRow.map((col, i) => {
    const hidden = READONLY_HIDDEN.includes(col);
    const isWide = ['short_narration', 'long_narration', 'historical_narration',
                    'description', 'short_description', 'custom_description',
                    'origin_story', 'mhc_narrative', 'hours_text', 'address'].includes(col);
    return {
      hidden,
      wch: hidden ? 30 : (isWide ? 50 : Math.max(12, col.length + 2)),
    };
  });

  const workbook = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(workbook, sheet, 'POIs');

  // ── Export Info sheet (hidden) ────────────────────────────────────────────
  // Carries the export timestamp + column manifest so the importer can detect
  // (a) which version this file was generated against, and (b) stale rows
  // (any PG row whose updated_at > exportedAt).
  const infoRows = [
    ['key', 'value'],
    ['exportedAt', exportedAt],
    ['schemaVersion', 's241-v2'],
    ['poiCount', String(pois.length)],
    ['readOnlyHiddenColumns', READONLY_HIDDEN.join(',')],
    ['readOnlyVisibleColumns', READONLY_VISIBLE_BACK.join(',')],
    ['editableColumns', EDITABLE_COLUMNS.join(',')],
    ['pinnedEditableFront', PINNED_EDITABLE_FRONT.join(',')],
    ['isDeletedIntentColumn', IS_DELETED_INTENT_COLUMN],
  ];
  const infoSheet = XLSX.utils.aoa_to_sheet(infoRows);
  XLSX.utils.book_append_sheet(workbook, infoSheet, 'Export Info');

  // ── Validation Lists sheet (hidden) — backs the in-cell pick-lists ──────
  if (validationListsAoa) {
    const validationSheet = XLSX.utils.aoa_to_sheet(validationListsAoa);
    XLSX.utils.book_append_sheet(workbook, validationSheet, 'Validation Lists');
  }

  // Mark non-POIs sheets as hidden so operator doesn't accidentally edit them.
  workbook.Workbook = workbook.Workbook || { Sheets: [] };
  workbook.Workbook.Sheets = [
    { name: 'POIs', Hidden: 0 },
    { name: 'Export Info', Hidden: 1 },
    ...(validationListsAoa ? [{ name: 'Validation Lists', Hidden: 1 }] : []),
  ];

  return workbook;
}

// SheetJS community writer skips frozen-pane info AND has no API for
// in-cell data-validation pick-lists. Re-zip the buffer with patched XML so
// LibreOffice / Excel freeze the top row AND show enum dropdowns on
// category / subcategory / boolean / etc. cells.
async function patchWorksheetXml(xlsxBuffer, sheetXmlPath, opts) {
  const zip = await JSZip.loadAsync(xlsxBuffer);
  const entry = zip.file(sheetXmlPath);
  if (!entry) return xlsxBuffer;
  let xml = await entry.async('string');

  // ── Frozen pane (top header row) ────────────────────────────────────────
  if (opts.frozenPane) {
    const FROZEN_VIEW =
      '<sheetViews>' +
        '<sheetView workbookViewId="0">' +
          '<pane ySplit="1" topLeftCell="A2" activePane="bottomLeft" state="frozen"/>' +
          '<selection pane="bottomLeft" activeCell="A2" sqref="A2"/>' +
        '</sheetView>' +
      '</sheetViews>';

    let patched = xml.replace(/<sheetViews>[\s\S]*?<\/sheetViews>/, FROZEN_VIEW);
    if (patched === xml) patched = xml.replace(/<sheetViews[^>]*\/>/, FROZEN_VIEW);
    if (patched === xml) patched = xml.replace(/<dimension[^>]*\/>/, m => m + FROZEN_VIEW);
    xml = patched;
  }

  // ── Data validations (in-cell enum pick-lists) ──────────────────────────
  if (opts.dataValidationsXml) {
    // Per OOXML schema, dataValidations comes AFTER autoFilter and BEFORE
    // pageMargins. Try those anchors in order; fall back to before </worksheet>.
    // Use function-form replacement so $-digit sequences in the XML (e.g.
    // "$B$171" inside a formula1 range reference) aren't misinterpreted as
    // backreferences.
    const dv = opts.dataValidationsXml;
    let injected = false;
    if (/<autoFilter[^>]*\/>/.test(xml)) {
      xml = xml.replace(/<autoFilter[^>]*\/>/, m => m + dv);
      injected = true;
    }
    if (!injected && /<\/sheetData>/.test(xml)) {
      xml = xml.replace(/<\/sheetData>/, () => '</sheetData>' + dv);
      injected = true;
    }
    if (!injected) {
      xml = xml.replace(/<\/worksheet>/, () => dv + '</worksheet>');
    }
  }

  zip.file(sheetXmlPath, xml);
  return zip.generateAsync({ type: 'nodebuffer', compression: 'DEFLATE' });
}

// Build the hidden "Validation Lists" sheet AoA + named-range map. Each list
// becomes one column; row 1 is the list name, row 2+ is the values.
async function buildValidationLists(pgPool) {
  const cats = (await pgPool.query(`SELECT id FROM salem_poi_categories ORDER BY id`)).rows.map(r => r.id);
  const subs = (await pgPool.query(`SELECT id FROM salem_poi_subcategories ORDER BY category_id, id`)).rows.map(r => r.id);

  const lists = {
    categories: cats,
    subcategories: subs,
    true_false: TRUE_FALSE,
    merchant_tier: MERCHANT_TIERS,
    haunt_sprite_id: HAUNT_SPRITE_IDS,
    price_range: PRICE_RANGES,
    status: STATUS_VALUES,
    geofence_shape: GEOFENCE_SHAPES,
    location_status: LOCATION_STATUSES,
  };

  const names = Object.keys(lists);
  const maxLen = Math.max(...names.map(n => lists[n].length));
  const aoa = [names.slice()];  // header row
  for (let i = 0; i < maxLen; i++) {
    aoa.push(names.map(n => lists[n][i] != null ? String(lists[n][i]) : ''));
  }

  const ranges = {};
  names.forEach((name, colIdx) => {
    const colLetter = XLSX.utils.encode_col(colIdx);
    ranges[name] = `'Validation Lists'!$${colLetter}$2:$${colLetter}$${1 + lists[name].length}`;
  });

  return { aoa, ranges };
}

function escapeXmlAttr(s) {
  return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
                  .replace(/"/g, '&quot;').replace(/'/g, '&apos;');
}

// Emit a <dataValidations> XML block for every column in headerRow that has
// a binding in VALIDATION_BINDINGS. The sqref covers all data rows.
function buildDataValidationsXml(headerRow, dataRowCount, ranges) {
  const parts = [];
  for (let i = 0; i < headerRow.length; i++) {
    const col = headerRow[i];
    const listName = VALIDATION_BINDINGS[col];
    if (!listName) continue;
    const range = ranges[listName];
    if (!range) continue;
    const colLetter = XLSX.utils.encode_col(i);
    const sqref = `${colLetter}2:${colLetter}${1 + dataRowCount}`;
    // showErrorMessage="0" — operator can type values not in the list and
    // LibreOffice / Excel won't reject. Matches operator's "I should be able
    // to write my own and have it override" requirement.
    parts.push(
      `<dataValidation type="list" allowBlank="1" showInputMessage="0" showErrorMessage="0" sqref="${sqref}">` +
        `<formula1>${escapeXmlAttr(range)}</formula1>` +
      `</dataValidation>`
    );
  }
  if (parts.length === 0) return '';
  return `<dataValidations count="${parts.length}">${parts.join('')}</dataValidations>`;
}

// ─── Workbook parsing (import) ───────────────────────────────────────────────

function parseWorkbook(workbookPath) {
  const wb = XLSX.readFile(workbookPath, { cellDates: true });
  if (!wb.SheetNames.includes('POIs')) {
    throw new Error('Spreadsheet missing required "POIs" sheet');
  }
  const sheet = wb.Sheets['POIs'];
  const rows = XLSX.utils.sheet_to_json(sheet, { header: 1, defval: '' });
  if (rows.length < 1) throw new Error('Spreadsheet has no rows');
  const headerRow = rows[0].map(h => String(h).trim());
  const dataRows = rows.slice(1).filter(r => r && r.length > 0 && r.some(c => c !== ''));

  // Pull export metadata from the hidden Info sheet (best-effort).
  let exportedAt = null;
  if (wb.SheetNames.includes('Export Info')) {
    const infoRows = XLSX.utils.sheet_to_json(wb.Sheets['Export Info'], { header: 1, defval: '' });
    for (const r of infoRows) {
      if (r[0] === 'exportedAt') exportedAt = String(r[1]);
    }
  }

  // Map header column name → column index for fast cell lookup.
  const colIdx = new Map();
  headerRow.forEach((h, i) => colIdx.set(h, i));
  if (!colIdx.has('id')) {
    throw new Error('Spreadsheet header missing required hidden column "id"');
  }

  return { headerRow, dataRows, colIdx, exportedAt };
}

// S241.1 — is_deleted is a writable "intent" column with bespoke apply
// semantics. Operator flips it TRUE/FALSE in the spreadsheet to drive bulk
// soft-delete / restore. Returns null (no change), a change object with
// kind 'soft_delete' | 'restore', or an error object if the cell isn't a
// recognizable boolean.
function diffIsDeleted(rawCell, pgRow) {
  const parsed = parseBoolean(rawCell);
  if (parsed.error) {
    return { column: 'is_deleted', kind: 'boolean', old: pgRow.deleted_at ? 'TRUE' : 'FALSE',
             new: cellToText(rawCell), error: parsed.error };
  }
  if (parsed.blank) return null;        // operator left it untouched
  const pgIsDeleted = !!pgRow.deleted_at;
  if (parsed.value === pgIsDeleted) return null;  // matches PG, no change
  if (parsed.value === true) {
    return { column: 'is_deleted', kind: 'soft_delete', old: 'FALSE', new: 'TRUE' };
  }
  return   { column: 'is_deleted', kind: 'restore',     old: 'TRUE',  new: 'FALSE' };
}

// Compute the per-row changeset by diffing each editable cell against pgRow.
function rowChangeset(dataRow, colIdx, pgRow, exportedAt) {
  const changes = [];
  for (const field of EDITABLE_COLUMNS) {
    const i = colIdx.get(field);
    if (i === undefined) continue;   // operator deleted the column header
    const rawCell = dataRow[i];
    const kind = colKind(field);

    let parsed;
    switch (kind) {
      case 'boolean': parsed = parseBoolean(rawCell); break;
      case 'numeric': parsed = parseNumeric(rawCell); break;
      case 'date':    parsed = parseDate(rawCell); break;
      case 'jsonb':   parsed = parseJsonbArray(rawCell); break;
      default:        parsed = { value: rawCell === '' ? '' : String(rawCell),
                                 blank: rawCell === '' || rawCell === null || rawCell === undefined };
    }

    if (parsed.error) {
      changes.push({ column: field, kind, old: '', new: cellToText(rawCell), error: parsed.error });
      continue;
    }

    const diff = diffCell(field, kind, parsed, pgRow);
    if (!diff) continue;

    const warning = (field === 'lat' || field === 'lng') && parsed.value !== null
      ? latLngWarning(field, parsed.value) : null;

    changes.push({
      column: field,
      kind: diff.kind,
      old: diff.old,
      new: diff.new,
      ...(warning ? { warning } : {}),
    });
  }

  // is_deleted intent column (S241.1 — handled outside the editable loop because
  // it routes to canonical soft-delete / restore SQL, not buildUpdateClause).
  const isDeletedIdx = colIdx.get(IS_DELETED_INTENT_COLUMN);
  if (isDeletedIdx !== undefined) {
    const intent = diffIsDeleted(dataRow[isDeletedIdx], pgRow);
    if (intent) changes.push(intent);
  }

  // Stale-row detection.
  let stale = false;
  if (exportedAt && pgRow.updated_at) {
    const pgUpdated = pgRow.updated_at instanceof Date
      ? pgRow.updated_at.toISOString()
      : String(pgRow.updated_at);
    if (pgUpdated > exportedAt) stale = true;
  }

  return { changes, stale };
}

// ─── multer setup ────────────────────────────────────────────────────────────

const UPLOAD_DIR = path.join(__dirname, '..', 'tmp', 'mass-edits');
try { fs.mkdirSync(UPLOAD_DIR, { recursive: true }); } catch (e) { /* ignore */ }

const upload = multer({
  dest: UPLOAD_DIR,
  limits: { fileSize: 50 * 1024 * 1024 },  // 50 MB cap
  fileFilter: (req, file, cb) => {
    const ok = /\.(xlsx|ods)$/i.test(file.originalname);
    if (!ok) return cb(new Error('only .xlsx or .ods files accepted'));
    cb(null, true);
  },
});

// ─── Module export ───────────────────────────────────────────────────────────

module.exports = function(app, deps) {
  const { pgPool, requirePg } = deps;

  // ─── GET /admin/salem/pois/export-spreadsheet ────────────────────────────
  app.get('/admin/salem/pois/export-spreadsheet', requirePg, async (req, res) => {
    try {
      // S241 update: include soft-deleted rows so operator can filter/group
      // by is_deleted in the spreadsheet. Apply path still refuses to update
      // soft-deleted POIs (see WHERE deleted_at IS NULL on the UPDATE below).
      const { rows } = await pgPool.query(
        `SELECT * FROM salem_pois
          ORDER BY category ASC, name ASC`
      );
      const exportedAt = new Date().toISOString();
      const { aoa: validationAoa, ranges: validationRanges } = await buildValidationLists(pgPool);
      const workbook = buildWorkbook(rows, exportedAt, validationAoa);
      const buf = XLSX.write(workbook, { type: 'buffer', bookType: 'xlsx' });

      // SheetJS community writer doesn't emit frozen-pane XML OR in-cell
      // data-validation pick-lists. Patch the xlsx zip after the fact to
      // inject both: <pane state="frozen"/> for the header row and a
      // <dataValidations> block for the enum columns (category, subcategory,
      // booleans, merchant_tier, etc.).
      // Header row matches buildWorkbook's layout exactly.
      const trailingEditable = EDITABLE_COLUMNS.filter(c => !PINNED_EDITABLE_FRONT.includes(c));
      const headerRow = [
        ...READONLY_HIDDEN, ...PINNED_EDITABLE_FRONT, IS_DELETED_INTENT_COLUMN,
        ...trailingEditable, ...READONLY_VISIBLE_BACK,
      ];
      const dataValidationsXml = buildDataValidationsXml(headerRow, rows.length, validationRanges);

      const patched = await patchWorksheetXml(buf, 'xl/worksheets/sheet1.xml', {
        frozenPane: true,
        dataValidationsXml,
      });

      const stamp = exportedAt.replace(/[:.]/g, '-');
      const filename = `salem-pois-${stamp}.xlsx`;
      res.setHeader('Content-Type',
        'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet');
      res.setHeader('Content-Disposition', `attachment; filename="${filename}"`);
      res.setHeader('X-Export-Timestamp', exportedAt);
      res.setHeader('X-POI-Count', String(rows.length));
      res.end(patched);
    } catch (err) {
      console.error('[AdminMassEdit] export error:', err.message);
      res.status(500).json({ error: err.message });
    }
  });

  // ─── POST /admin/salem/pois/import-spreadsheet (multipart) ───────────────
  app.post('/admin/salem/pois/import-spreadsheet',
    requirePg,
    upload.single('file'),
    async (req, res) => {
      let cleanupPath = null;
      try {
        if (!req.file) {
          return res.status(400).json({ error: 'no file uploaded (form field "file" required)' });
        }
        cleanupPath = req.file.path;

        const { dataRows, colIdx, exportedAt } = parseWorkbook(req.file.path);

        // Pull all POIs (including soft-deleted) so changesets on soft-deleted
        // rows surface clearly rather than as "not found". The apply path
        // still refuses to write to soft-deleted rows.
        const { rows: pgRows } = await pgPool.query(
          `SELECT * FROM salem_pois`
        );
        const pgById = new Map();
        for (const r of pgRows) pgById.set(r.id, r);

        const changeset = [];
        const skipped = { missingId: 0, notFound: 0, noChanges: 0 };

        for (const row of dataRows) {
          const id = String(row[colIdx.get('id')] || '').trim();
          if (!id) { skipped.missingId++; continue; }
          const pgRow = pgById.get(id);
          if (!pgRow) { skipped.notFound++; continue; }

          const { changes, stale } = rowChangeset(row, colIdx, pgRow, exportedAt);
          if (changes.length === 0) { skipped.noChanges++; continue; }

          changeset.push({
            poi_id: id,
            poi_name: pgRow.name,
            poi_category: pgRow.category,
            stale,
            soft_deleted: !!pgRow.deleted_at,
            pg_updated_at: pgRow.updated_at instanceof Date
              ? pgRow.updated_at.toISOString() : String(pgRow.updated_at || ''),
            changes,
          });
        }

        // Sort by name for stable UI rendering.
        changeset.sort((a, b) => (a.poi_name || '').localeCompare(b.poi_name || ''));

        res.json({
          exported_at: exportedAt,
          imported_at: new Date().toISOString(),
          poi_count_changed: changeset.length,
          cell_count_changed: changeset.reduce((s, c) => s + c.changes.length, 0),
          skipped,
          changeset,
        });
      } catch (err) {
        console.error('[AdminMassEdit] import error:', err.message);
        res.status(500).json({ error: err.message });
      } finally {
        if (cleanupPath) {
          fs.unlink(cleanupPath, () => { /* best-effort */ });
        }
      }
    }
  );

  // ─── POST /admin/salem/pois/apply-mass-edit ──────────────────────────────
  // Body: { approvals: [{ poi_id, column, new }] }
  //   `new` matches the shape emitted by the import endpoint — strings for
  //   text/boolean/numeric/date (parsed server-side), null to clear.
  app.post('/admin/salem/pois/apply-mass-edit', requirePg, async (req, res) => {
    const body = req.body || {};
    const approvals = Array.isArray(body.approvals) ? body.approvals : null;
    if (!approvals) {
      return res.status(400).json({ error: 'body.approvals must be an array' });
    }
    if (approvals.length === 0) {
      return res.json({ applied_pois: 0, applied_cells: 0, results: [] });
    }

    // Group approvals by poi_id. Field-update approvals (whitelisted editable
    // columns) feed buildUpdateClause; is_deleted approvals are siphoned off
    // into a separate intents Map and routed to soft-delete / restore SQL.
    const grouped = new Map();
    const intents = new Map();  // poi_id → 'soft_delete' | 'restore'
    for (const a of approvals) {
      if (!a || typeof a !== 'object') continue;
      const { poi_id, column, new: rawNew } = a;
      if (typeof poi_id !== 'string' || typeof column !== 'string') continue;

      // ── is_deleted intent — bypass the whitelist + buildUpdateClause path.
      if (column === IS_DELETED_INTENT_COLUMN) {
        const p = parseBoolean(rawNew);
        if (p.error || p.blank) {
          return res.status(400).json({
            error: `approval for ${poi_id}.is_deleted: ${p.error || 'value cannot be blank'}`,
          });
        }
        const want = p.value ? 'soft_delete' : 'restore';
        const prior = intents.get(poi_id);
        if (prior && prior !== want) {
          return res.status(400).json({
            error: `POI ${poi_id} has conflicting is_deleted intents in the same batch (both soft_delete and restore)`,
          });
        }
        intents.set(poi_id, want);
        continue;
      }

      if (!EDITABLE_COLUMNS.includes(column)) continue;   // server-side whitelist

      const kind = colKind(column);
      let coerced;
      try {
        if (rawNew === null || rawNew === undefined || rawNew === '') {
          coerced = null;
        } else if (kind === 'boolean') {
          const p = parseBoolean(rawNew);
          if (p.error) throw new Error(p.error);
          coerced = p.value;
        } else if (kind === 'numeric') {
          const p = parseNumeric(rawNew);
          if (p.error) throw new Error(p.error);
          coerced = p.value;
        } else if (kind === 'date') {
          const p = parseDate(rawNew);
          if (p.error) throw new Error(p.error);
          coerced = p.value;
        } else if (kind === 'jsonb') {
          const p = parseJsonbArray(rawNew);
          if (p.error) throw new Error(p.error);
          coerced = p.value;
        } else {
          coerced = String(rawNew);
        }
      } catch (e) {
        return res.status(400).json({
          error: `approval for ${poi_id}.${column}: ${e.message}`,
        });
      }

      if (!grouped.has(poi_id)) grouped.set(poi_id, {});
      grouped.get(poi_id)[column] = coerced;
    }

    // Apply in a single transaction. Reuse buildUpdateClause for whitelist +
    // JSONB serialization + auto-set updated_at (mirrors PUT semantics).
    // Transaction ordering: field updates → restores → soft-deletes. Restoring
    // before deleting is symmetric; field updates running first means edits
    // hit live rows before any soft-delete in the same batch retires them.
    const client = await pgPool.connect();
    const results = [];
    let softDeleted = 0;
    let restored = 0;
    const touchedPois = new Set();
    try {
      await client.query('BEGIN');
      let appliedCells = 0;
      for (const [poiId, fields] of grouped) {
        const { setSql, values, error } = buildUpdateClause(fields);
        if (error) {
          throw new Error(`POI ${poiId}: ${error}`);
        }
        values.push(poiId);
        const { rows } = await client.query(
          `UPDATE salem_pois
              SET ${setSql},
                  admin_dirty = TRUE,
                  admin_dirty_at = NOW()
            WHERE id = $${values.length}
              AND deleted_at IS NULL
          RETURNING id, name`,
          values
        );
        if (!rows.length) {
          throw new Error(`POI ${poiId}: not found or soft-deleted`);
        }
        const cellCount = Object.keys(fields).length;
        appliedCells += cellCount;
        touchedPois.add(poiId);
        results.push({ poi_id: poiId, poi_name: rows[0].name, applied_cells: cellCount });
      }

      // Restores first (mirrors admin-pois.js POST /:id/restore).
      for (const [poiId, kind] of intents) {
        if (kind !== 'restore') continue;
        const { rows } = await client.query(
          `UPDATE salem_pois
              SET deleted_at = NULL,
                  admin_dirty = TRUE,
                  admin_dirty_at = NOW(),
                  updated_at = NOW()
            WHERE id = $1 AND deleted_at IS NOT NULL
          RETURNING id, name`,
          [poiId]
        );
        if (!rows.length) {
          throw new Error(`POI ${poiId}: cannot restore — not soft-deleted (or missing)`);
        }
        restored++;
        touchedPois.add(poiId);
      }

      // Soft-deletes last (mirrors admin-pois.js DELETE /:id, with
      // admin_dirty stamp added for batch consistency).
      for (const [poiId, kind] of intents) {
        if (kind !== 'soft_delete') continue;
        const { rows } = await client.query(
          `UPDATE salem_pois
              SET deleted_at = NOW(),
                  admin_dirty = TRUE,
                  admin_dirty_at = NOW(),
                  updated_at = NOW()
            WHERE id = $1 AND deleted_at IS NULL
          RETURNING id, name`,
          [poiId]
        );
        if (!rows.length) {
          throw new Error(`POI ${poiId}: cannot soft-delete — already deleted (or missing)`);
        }
        softDeleted++;
        touchedPois.add(poiId);
      }

      await client.query('COMMIT');
      res.json({
        applied_pois: touchedPois.size,
        applied_cells: appliedCells,
        soft_deleted: softDeleted,
        restored,
        results,
      });
    } catch (err) {
      await client.query('ROLLBACK').catch(() => {});
      console.error('[AdminMassEdit] apply error:', err.message);
      res.status(400).json({ error: err.message });
    } finally {
      client.release();
    }
  });
};

module.exports.EDITABLE_COLUMNS = EDITABLE_COLUMNS;
module.exports.READONLY_HIDDEN = READONLY_HIDDEN;
module.exports.READONLY_VISIBLE_BACK = READONLY_VISIBLE_BACK;
module.exports.PINNED_EDITABLE_FRONT = PINNED_EDITABLE_FRONT;
module.exports.IS_DELETED_INTENT_COLUMN = IS_DELETED_INTENT_COLUMN;
