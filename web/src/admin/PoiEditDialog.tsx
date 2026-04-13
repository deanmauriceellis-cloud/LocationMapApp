// PoiEditDialog — Phase 9P.B Step 9P.10
//
// Tabbed modal for editing a single POI across all three kinds. Opens when
// the operator clicks a marker on AdminMap; closes on Save (success) or Cancel.
// Mirrors the PUT /admin/salem/pois/:id endpoint's whitelist exactly
// (see poiAdminFields.ts and cache-proxy/lib/admin-pois.js).
//
// ─── Tab structure (per master plan §1296) ────────────────────────────────
//   General · Location · Hours & Contact · Narration · Provenance ·
//   Linked Historical Content (9P.10a stub) · Danger Zone
//
// ─── Field rendering rules ─────────────────────────────────────────────────
//   - JSONB columns → <textarea> with JSON.stringify(value, null, 2) default,
//     JSON.parse on submit. Empty textarea → []. Parse failure aborts save
//     with an inline error.
//   - Boolean columns (tour: requires_transportation, wheelchair_accessible,
//     seasonal) → <input type="checkbox">.
//   - Numeric columns (lat, lng, geofence_radius_m, etc.) → <input type="number">.
//   - Date columns (verified_date, stale_after) → <input type="date">; PG
//     returns ISO timestamps so we slice the leading 10 chars for the default.
//   - Category / business_type → free-text input + <datalist> of observed
//     values (passed in via knownCategories prop, computed in AdminLayout).
//   - Other strings → <input type="text"> or <textarea> for narration fields.
//
// ─── Dirty-tracking via react-hook-form ────────────────────────────────────
// formState.dirtyFields lets us send only the fields the operator actually
// touched. The PUT endpoint is partial-update friendly. This avoids:
//   1. Accidentally clobbering server-only or computed fields
//   2. Sending the full row over the wire on every save
//   3. Marking the row updated_at when nothing changed
//
// ─── Soft-delete (Danger Zone) ─────────────────────────────────────────────
// DELETE /admin/salem/pois/:id sets deleted_at = NOW(). On success the
// dialog closes and AdminLayout's onPoiDeleted patches the row in shared
// byKind so the tree immediately ghosts it.
//
// ─── Future work hooks ─────────────────────────────────────────────────────
//   - 9P.10a — Linked Historical Content tab is a stub; real impl after 9Q
//   - 9P.10b — "Generate with Salem Oracle" button slot in Narration tab

import { Fragment, useCallback, useMemo, useState } from 'react'
import {
  Dialog,
  DialogPanel,
  DialogTitle,
  TabGroup,
  TabList,
  Tab,
  TabPanels,
  TabPanel,
  Transition,
  TransitionChild,
} from '@headlessui/react'
import { useForm, type FieldValues, type SubmitHandler } from 'react-hook-form'
import type { PoiRow } from './PoiTree'
import {
  UPDATABLE_FIELDS,
  JSONB_FIELDS,
  BOOLEAN_FIELDS,
  NUMERIC_FIELDS,
  DATE_FIELDS,
} from './poiAdminFields'
import {
  ask as oracleAsk,
  isAskOk,
  type OracleAskOk,
  type OraclePrimarySource,
} from './oracleClient'

// ─── Oracle audit log (9P.10b) ────────────────────────────────────────────
//
// Per master plan §1366: "Capture the full Oracle response (text + primary_
// sources + question + timestamp) in a local audit log per accepted generation,
// so revisions are traceable. Stored client-side in localStorage; not pushed
// to PG until/unless we add an editorial-history feature."
//
// We log on INSERT (not on generate) — i.e. when the operator actually accepts
// a generation by pasting it into a form field. Generate-but-discard is not
// audited, since it's just thinking out loud.

const ORACLE_AUDIT_KEY = 'salem-oracle-audit'
const ORACLE_AUDIT_MAX = 500 // cap to keep localStorage manageable

interface OracleAuditEntry {
  ts: string
  poi_id: string
  poi_category: string
  field: string
  question: string
  text: string
  primary_sources: OraclePrimarySource[]
  history_turn_count: number
}

function appendOracleAudit(entry: OracleAuditEntry): void {
  try {
    const raw = localStorage.getItem(ORACLE_AUDIT_KEY)
    const arr: OracleAuditEntry[] = raw ? JSON.parse(raw) : []
    arr.push(entry)
    while (arr.length > ORACLE_AUDIT_MAX) arr.shift()
    localStorage.setItem(ORACLE_AUDIT_KEY, JSON.stringify(arr))
  } catch (e) {
    // localStorage quota or JSON parse — non-fatal, audit is best-effort
    console.warn('[oracle-audit] failed to append:', e)
  }
}

// ─── Props ────────────────────────────────────────────────────────────────────

export interface PoiEditDialogProps {
  /** True when the dialog should render. AdminLayout owns this state. */
  open: boolean
  /** The POI being edited (or null when the dialog is closed). */
  poi: PoiRow | null
  /** @deprecated kind removed in Phase 9U — unified table */
  /**
   * Observed category / business_type values from the loaded dataset.
   * Used to populate the <datalist> autocomplete on the category field.
   * Operator can type new values; this is just hints.
   */
  knownCategories: string[]
  /**
   * 9P.10b: whether the Salem Oracle is currently reachable, mirrored from
   * AdminLayout's polled status. When false, the "Generate with Salem Oracle"
   * buttons are disabled with a tooltip pointing at the start script.
   */
  oracleAvailable: boolean
  /**
   * 9P.10b: ask AdminLayout to re-poll Oracle status. Wired to a "Re-check"
   * action in the Generate sub-dialog so the operator can recover after
   * starting the testapp without closing the edit dialog.
   */
  onOracleRefresh: () => void | Promise<void>
  /** Called after a successful PUT, with the full updated row from the API. */
  onSaved: (updated: PoiRow) => void
  /** Called after a successful soft-delete, so AdminLayout can patch the row. */
  onDeleted: (id: string, deletedAt: string) => void
  /** Called when the dialog should close (Cancel, ESC, backdrop, post-save). */
  onClose: () => void
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** True if value is meaningful enough to roundtrip as a defaultValue. */
function notNullish(v: unknown): boolean {
  return v !== null && v !== undefined
}

/**
 * Build the form's defaultValues for a given POI + kind. JSONB columns are
 * pretty-printed; date columns are sliced to YYYY-MM-DD; everything else
 * passes through, with `null`/`undefined` collapsed to '' so inputs stay
 * controlled.
 */
function buildDefaults(poi: PoiRow): Record<string, unknown> {
  const fields = UPDATABLE_FIELDS
  const out: Record<string, unknown> = {}
  for (const field of fields) {
    const raw = poi[field]
    if (JSONB_FIELDS.has(field)) {
      try {
        out[field] = notNullish(raw) ? JSON.stringify(raw, null, 2) : '[]'
      } catch {
        out[field] = '[]'
      }
    } else if (BOOLEAN_FIELDS.has(field)) {
      out[field] = !!raw
    } else if (DATE_FIELDS.has(field)) {
      // PG timestamps come back as ISO strings; HTML date input wants YYYY-MM-DD.
      if (typeof raw === 'string' && raw.length >= 10) {
        out[field] = raw.slice(0, 10)
      } else {
        out[field] = ''
      }
    } else if (NUMERIC_FIELDS.has(field)) {
      // Keep as number when present, '' when null. react-hook-form's number
      // inputs work either way as long as we coerce on submit.
      out[field] = notNullish(raw) ? raw : ''
    } else {
      out[field] = notNullish(raw) ? String(raw) : ''
    }
  }
  return out
}

/**
 * Build the PUT payload. Iterates only the dirty fields (so we ship a partial
 * update), parses JSONB textareas, coerces numeric strings, and trims empty
 * strings to null where the column allows it.
 *
 * Returns either { payload } or { error } — error is the inline message to
 * show in the dialog if a JSONB textarea didn't parse.
 */
function buildPayload(
  values: FieldValues,
  dirtyFields: Record<string, unknown>,
): { payload: Record<string, unknown> } | { error: string } {
  const payload: Record<string, unknown> = {}
  for (const field of Object.keys(dirtyFields)) {
    const raw = values[field]
    if (JSONB_FIELDS.has(field)) {
      try {
        const trimmed = (raw ?? '').toString().trim()
        payload[field] = trimmed === '' ? [] : JSON.parse(trimmed)
      } catch (e) {
        return {
          error: `JSON parse error in field "${field}": ${
            e instanceof Error ? e.message : String(e)
          }`,
        }
      }
    } else if (NUMERIC_FIELDS.has(field)) {
      if (raw === '' || raw === null || raw === undefined) {
        payload[field] = null
      } else {
        const n = typeof raw === 'number' ? raw : parseFloat(raw)
        if (Number.isFinite(n)) {
          payload[field] = n
        } else {
          return { error: `"${field}" must be a number` }
        }
      }
    } else if (BOOLEAN_FIELDS.has(field)) {
      payload[field] = !!raw
    } else if (DATE_FIELDS.has(field)) {
      payload[field] = raw === '' ? null : raw
    } else {
      payload[field] = raw === '' ? null : raw
    }
  }
  return { payload }
}

// ─── Tab styling helper ──────────────────────────────────────────────────────

function tabClass({ selected }: { selected: boolean }): string {
  return [
    'px-3 py-1.5 text-xs font-medium border-b-2 -mb-px transition-colors',
    selected
      ? 'border-sky-500 text-sky-700 bg-white'
      : 'border-transparent text-slate-500 hover:text-slate-700 hover:border-slate-300',
  ].join(' ')
}

// ─── Field row primitives ────────────────────────────────────────────────────

interface FieldRowProps {
  label: string
  htmlFor: string
  hint?: string
  children: React.ReactNode
}

function FieldRow({ label, htmlFor, hint, children }: FieldRowProps) {
  return (
    <div className="space-y-1">
      <label
        htmlFor={htmlFor}
        className="block text-xs font-medium text-slate-700"
      >
        {label}
      </label>
      {children}
      {hint && <p className="text-[11px] text-slate-500">{hint}</p>}
    </div>
  )
}

// ─── Component ───────────────────────────────────────────────────────────────

export function PoiEditDialog({
  open,
  poi,
  knownCategories,
  oracleAvailable,
  onOracleRefresh,
  onSaved,
  onDeleted,
  onClose,
}: PoiEditDialogProps) {
  // ─── Form state ────────────────────────────────────────────────────────────
  // We rebuild defaults from scratch each time the dialog opens for a different
  // POI. The `key` on Dialog forces a fresh form mount per POI; that combined
  // with `defaultValues` is the simplest way to keep state isolated per POI.
  const defaultValues = useMemo(() => {
    if (!poi) return {}
    return buildDefaults(poi)
  }, [poi])

  const {
    register,
    handleSubmit,
    formState: { dirtyFields, isDirty, isSubmitting },
    reset,
    setValue,
  } = useForm<FieldValues>({
    defaultValues,
    // Re-init when defaults change (POI swap)
    values: defaultValues,
  })

  const [saveError, setSaveError] = useState<string | null>(null)
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [deleteBusy, setDeleteBusy] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  // ─── Oracle sub-dialog state (9P.10b) ──────────────────────────────────────
  // The "Generate with Salem Oracle" button on the Narration tab (and General
  // tab, for description) opens a nested modal where the operator types a
  // prompt, hits Generate, waits 5-15s, then either pastes the result into
  // a target field or iterates with a follow-up.
  const [oracleOpen, setOracleOpen] = useState(false)
  const [oraclePrompt, setOraclePrompt] = useState('')
  const [oracleReset, setOracleReset] = useState(true) // default: fresh history
  const [oracleBusy, setOracleBusy] = useState(false)
  const [oracleError, setOracleError] = useState<string | null>(null)
  const [oracleResult, setOracleResult] = useState<OracleAskOk | null>(null)
  // The question that produced `oracleResult`, kept separately so the audit
  // log records the *posed* question even if the operator clears the textarea
  // before clicking Insert.
  const [oracleResultQuestion, setOracleResultQuestion] = useState<string>('')

  const openOracleDialog = useCallback(() => {
    // Fresh open: clear last result so the operator isn't looking at stale
    // text from a prior POI's session. Default to reset=true so the first
    // call after opening doesn't carry context from another POI.
    setOraclePrompt('')
    setOracleError(null)
    setOracleResult(null)
    setOracleResultQuestion('')
    setOracleReset(true)
    setOracleOpen(true)
  }, [])

  const closeOracleDialog = useCallback(() => {
    if (oracleBusy) return // ignore close while a request is in flight
    setOracleOpen(false)
  }, [oracleBusy])

  // Run a single Oracle ask. Used by both the initial Generate button and
  // the Iterate follow-up button. The only behavioral difference is the
  // `reset` flag: initial Generate uses the operator-controlled checkbox;
  // Iterate always passes false to keep history rolling.
  const runOracleAsk = useCallback(
    async (forceReset: boolean | null) => {
      if (!poi) return
      const question = oraclePrompt.trim()
      if (!question) {
        setOracleError('Prompt is empty')
        return
      }
      if (oracleBusy) return // serialize per master plan §1383
      setOracleBusy(true)
      setOracleError(null)
      try {
        const resp = await oracleAsk({
          question,
          current_poi_id: poi.id,
          reset: forceReset === null ? oracleReset : forceReset,
        })
        if (!isAskOk(resp)) {
          setOracleError(resp.error)
          return
        }
        setOracleResult(resp)
        setOracleResultQuestion(question)
        // Subsequent calls in this session should iterate by default — flip
        // reset off so the rolling history accumulates without the operator
        // having to remember to uncheck it.
        setOracleReset(false)
      } catch (e) {
        setOracleError(e instanceof Error ? e.message : String(e))
      } finally {
        setOracleBusy(false)
      }
    },
    [poi, oraclePrompt, oracleReset, oracleBusy],
  )

  // Paste the latest Oracle text into a form field, mark it dirty so the
  // existing Save flow picks it up, and append an audit log entry. The
  // sub-dialog stays open so the operator can iterate or insert into a
  // second field if they want — they close it manually when done.
  const insertOracleInto = useCallback(
    (field: string) => {
      if (!oracleResult || !poi) return
      setValue(field, oracleResult.text, {
        shouldDirty: true,
        shouldValidate: false,
        shouldTouch: true,
      })
      appendOracleAudit({
        ts: new Date().toISOString(),
        poi_id: poi.id,
        poi_category: (poi.category as string) || '',
        field,
        question: oracleResultQuestion,
        text: oracleResult.text,
        primary_sources: oracleResult.primary_sources ?? [],
        history_turn_count: oracleResult.history_turn_count,
      })
    },
    [oracleResult, oracleResultQuestion, poi, setValue],
  )

  // ─── Save flow ─────────────────────────────────────────────────────────────

  const onSubmit: SubmitHandler<FieldValues> = useCallback(
    async (values) => {
      if (!poi) return
      setSaveError(null)
      const built = buildPayload(values, dirtyFields)
      if ('error' in built) {
        setSaveError(built.error)
        return
      }
      if (Object.keys(built.payload).length === 0) {
        // Nothing changed — close without API call.
        onClose()
        return
      }
      try {
        const res = await fetch(
          `/api/admin/salem/pois/${encodeURIComponent(poi.id)}`,
          {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            credentials: 'same-origin',
            body: JSON.stringify(built.payload),
          },
        )
        if (!res.ok) {
          let msg = `${res.status} ${res.statusText}`
          try {
            const body = await res.json()
            if (body?.error) msg = `${res.status} ${body.error}`
          } catch {
            // body wasn't JSON; keep the status text
          }
          throw new Error(msg)
        }
        const updated = (await res.json()) as PoiRow
        onSaved(updated)
        // Reset the form to the new server state so dirty tracking restarts
        // from the freshly-saved baseline (only matters if we re-open without
        // unmounting; harmless either way).
        reset(buildDefaults(updated))
        onClose()
      } catch (e) {
        setSaveError(e instanceof Error ? e.message : String(e))
      }
    },
    [poi, dirtyFields, onSaved, onClose, reset],
  )

  // ─── Cancel / close flow ───────────────────────────────────────────────────

  const handleClose = useCallback(() => {
    if (isDirty) {
      const ok = window.confirm(
        'You have unsaved changes. Discard and close?',
      )
      if (!ok) return
    }
    setSaveError(null)
    setConfirmDelete(false)
    setDeleteError(null)
    onClose()
  }, [isDirty, onClose])

  // ─── Delete flow ───────────────────────────────────────────────────────────

  const handleDelete = useCallback(async () => {
    if (!poi) return
    setDeleteBusy(true)
    setDeleteError(null)
    try {
      const res = await fetch(
        `/api/admin/salem/pois/${encodeURIComponent(poi.id)}`,
        { method: 'DELETE', credentials: 'same-origin' },
      )
      if (!res.ok) {
        let msg = `${res.status} ${res.statusText}`
        try {
          const body = await res.json()
          if (body?.error) msg = `${res.status} ${body.error}`
        } catch {
          // body wasn't JSON
        }
        throw new Error(msg)
      }
      const body = (await res.json()) as { id: string; deleted_at: string }
      onDeleted(poi.id, body.deleted_at)
      setConfirmDelete(false)
      onClose()
    } catch (e) {
      setDeleteError(e instanceof Error ? e.message : String(e))
    } finally {
      setDeleteBusy(false)
    }
  }, [poi, onDeleted, onClose])

  // ─── Render guards ─────────────────────────────────────────────────────────
  if (!poi) return null

  // Convenience: unified field whitelist
  const allowed = new Set<string>(UPDATABLE_FIELDS)
  const has = (f: string) => allowed.has(f)
  const reg = (f: string) => register(f)

  // List of insertable target fields the current POI kind actually has, in
  // the order the operator most likely wants them. Filtered through `has()`
  // so a tour POI doesn't see fields it doesn't have.
  const insertableFields = [
    { field: 'short_narration', label: 'Insert into short_narration' },
    { field: 'long_narration', label: 'Insert into long_narration' },
    { field: 'description', label: 'Insert into description' },
  ].filter((f) => has(f.field))

  // ─── Render ────────────────────────────────────────────────────────────────
  return (
    <Fragment>
    <Transition show={open} as={Fragment}>
      <Dialog onClose={handleClose} className="relative z-[1000]">
        {/* Backdrop */}
        <TransitionChild
          as={Fragment}
          enter="ease-out duration-150"
          enterFrom="opacity-0"
          enterTo="opacity-100"
          leave="ease-in duration-100"
          leaveFrom="opacity-100"
          leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-black/65" aria-hidden="true" />
        </TransitionChild>

        {/* Panel */}
        <div className="fixed inset-0 flex items-center justify-center p-4">
          <TransitionChild
            as={Fragment}
            enter="ease-out duration-150"
            enterFrom="opacity-0 scale-95"
            enterTo="opacity-100 scale-100"
            leave="ease-in duration-100"
            leaveFrom="opacity-100 scale-100"
            leaveTo="opacity-0 scale-95"
          >
            <DialogPanel className="w-[800px] max-w-[95vw] max-h-[90vh] flex flex-col bg-white rounded-lg shadow-xl text-slate-800">
              {/* Header */}
              <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
                <div className="min-w-0">
                  <DialogTitle className="text-base font-semibold truncate">
                    {poi.name || '(unnamed)'}
                  </DialogTitle>
                  <p className="text-xs text-slate-500 font-mono truncate">
                    {poi.category || '—'} · {poi.id}
                    {poi.deleted_at && (
                      <span className="ml-2 text-rose-600">
                        soft-deleted at {String(poi.deleted_at).slice(0, 19)}
                      </span>
                    )}
                  </p>
                </div>
                <button
                  type="button"
                  onClick={handleClose}
                  className="text-slate-400 hover:text-slate-700 text-xl leading-none"
                  aria-label="Close"
                >
                  ×
                </button>
              </div>

              {poi.deleted_at && (
                <div className="mx-4 mt-3 p-2 text-xs text-amber-800 bg-amber-50 border border-amber-200 rounded">
                  This POI is soft-deleted. Saving will fail with a 409 — you
                  must restore it from the backend (no UI yet) before editing.
                  You can still inspect the fields here.
                </div>
              )}

              {/* Tabs + form */}
              <form
                onSubmit={handleSubmit(onSubmit)}
                className="flex-1 min-h-0 flex flex-col"
              >
                <TabGroup as="div" className="flex-1 min-h-0 flex flex-col">
                  <TabList className="px-4 pt-2 border-b border-slate-200 flex flex-wrap gap-1">
                    <Tab className={tabClass}>General</Tab>
                    <Tab className={tabClass}>Location</Tab>
                    <Tab className={tabClass}>Hours &amp; Contact</Tab>
                    <Tab className={tabClass}>Narration</Tab>
                    <Tab className={tabClass}>Provenance</Tab>
                    <Tab className={tabClass}>Linked Historical</Tab>
                    <Tab className={tabClass}>Danger Zone</Tab>
                  </TabList>

                  <TabPanels className="flex-1 min-h-0 overflow-y-auto px-4 py-4">
                    {/* ─── General ─────────────────────────────────────────── */}
                    <TabPanel className="space-y-4">
                      <FieldRow label="Name" htmlFor="name">
                        <input
                          id="name"
                          type="text"
                          {...reg('name')}
                          className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                        />
                      </FieldRow>

                      {has('address') && (
                        <FieldRow label="Address" htmlFor="address">
                          <input
                            id="address"
                            type="text"
                            {...reg('address')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}

                      {/* Category — tour & narration use `category`, business uses `business_type` */}
                      {has('category') && (
                        <FieldRow
                          label="Category"
                          htmlFor="category"
                          hint="Free text. Suggestions are existing values from this kind."
                        >
                          <input
                            id="category"
                            type="text"
                            list="poi-edit-category-list"
                            {...reg('category')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                          <datalist id="poi-edit-category-list">
                            {knownCategories.map((c) => (
                              <option key={c} value={c} />
                            ))}
                          </datalist>
                        </FieldRow>
                      )}
                      {has('business_type') && (
                        <FieldRow
                          label="Business type"
                          htmlFor="business_type"
                          hint="Free text. Suggestions are existing values from this kind."
                        >
                          <input
                            id="business_type"
                            type="text"
                            list="poi-edit-category-list"
                            {...reg('business_type')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                          <datalist id="poi-edit-category-list">
                            {knownCategories.map((c) => (
                              <option key={c} value={c} />
                            ))}
                          </datalist>
                        </FieldRow>
                      )}
                      {has('subcategory') && (
                        <FieldRow label="Subcategory" htmlFor="subcategory">
                          <input
                            id="subcategory"
                            type="text"
                            {...reg('subcategory')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('cuisine_type') && (
                        <FieldRow label="Cuisine type" htmlFor="cuisine_type">
                          <input
                            id="cuisine_type"
                            type="text"
                            {...reg('cuisine_type')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('historical_period') && (
                        <FieldRow label="Historical period" htmlFor="historical_period">
                          <input
                            id="historical_period"
                            type="text"
                            {...reg('historical_period')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('description') && (
                        <FieldRow label="Description" htmlFor="description">
                          <textarea
                            id="description"
                            rows={4}
                            {...reg('description')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded font-mono"
                          />
                          {/* 9P.10b — Oracle launcher mirrored on General tab */}
                          <OracleLauncher
                            oracleAvailable={oracleAvailable}
                            onClick={openOracleDialog}
                            label="Generate description with Salem Oracle"
                            compact
                          />
                        </FieldRow>
                      )}
                      {has('historical_note') && (
                        <FieldRow label="Historical note" htmlFor="historical_note">
                          <textarea
                            id="historical_note"
                            rows={3}
                            {...reg('historical_note')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}
                      {has('image_asset') && (
                        <FieldRow label="Image asset" htmlFor="image_asset">
                          <input
                            id="image_asset"
                            type="text"
                            {...reg('image_asset')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('admission_info') && (
                        <FieldRow label="Admission info" htmlFor="admission_info">
                          <input
                            id="admission_info"
                            type="text"
                            {...reg('admission_info')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('price_range') && (
                        <FieldRow label="Price range" htmlFor="price_range">
                          <input
                            id="price_range"
                            type="text"
                            {...reg('price_range')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('rating') && (
                        <FieldRow label="Rating" htmlFor="rating">
                          <input
                            id="rating"
                            type="number"
                            step="0.1"
                            {...reg('rating')}
                            className="w-32 px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}

                      {/* Boolean tour-only flags */}
                      {(has('requires_transportation') ||
                        has('wheelchair_accessible') ||
                        has('seasonal')) && (
                        <div className="flex flex-wrap gap-4 pt-2">
                          {has('requires_transportation') && (
                            <label className="flex items-center gap-2 text-xs text-slate-700">
                              <input
                                type="checkbox"
                                {...reg('requires_transportation')}
                              />
                              Requires transportation
                            </label>
                          )}
                          {has('wheelchair_accessible') && (
                            <label className="flex items-center gap-2 text-xs text-slate-700">
                              <input
                                type="checkbox"
                                {...reg('wheelchair_accessible')}
                              />
                              Wheelchair accessible
                            </label>
                          )}
                          {has('seasonal') && (
                            <label className="flex items-center gap-2 text-xs text-slate-700">
                              <input type="checkbox" {...reg('seasonal')} />
                              Seasonal
                            </label>
                          )}
                        </div>
                      )}

                      {/* JSONB collections in General */}
                      {has('subcategories') && (
                        <FieldRow
                          label="Subcategories (JSONB array)"
                          htmlFor="subcategories"
                          hint="JSON array of strings — e.g. [&quot;witch_trials&quot;, &quot;haunted&quot;]"
                        >
                          <textarea
                            id="subcategories"
                            rows={3}
                            {...reg('subcategories')}
                            className="w-full px-2 py-1 text-xs border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}
                      {has('tags') && (
                        <FieldRow
                          label="Tags (JSONB array)"
                          htmlFor="tags"
                          hint="JSON array of strings"
                        >
                          <textarea
                            id="tags"
                            rows={3}
                            {...reg('tags')}
                            className="w-full px-2 py-1 text-xs border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}
                    </TabPanel>

                    {/* ─── Location ────────────────────────────────────────── */}
                    <TabPanel className="space-y-4">
                      <div className="grid grid-cols-2 gap-4">
                        <FieldRow label="Latitude" htmlFor="lat">
                          <input
                            id="lat"
                            type="number"
                            step="any"
                            {...reg('lat')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                        <FieldRow label="Longitude" htmlFor="lng">
                          <input
                            id="lng"
                            type="number"
                            step="any"
                            {...reg('lng')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      </div>
                      <p className="text-[11px] text-slate-500 -mt-2">
                        Tip: drag the marker on the map for fine adjustments
                        instead of typing coordinates.
                      </p>

                      {has('geofence_radius_m') && (
                        <FieldRow
                          label="Geofence radius (m)"
                          htmlFor="geofence_radius_m"
                        >
                          <input
                            id="geofence_radius_m"
                            type="number"
                            step="1"
                            {...reg('geofence_radius_m')}
                            className="w-32 px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('geofence_shape') && (
                        <FieldRow label="Geofence shape" htmlFor="geofence_shape">
                          <input
                            id="geofence_shape"
                            type="text"
                            {...reg('geofence_shape')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('corridor_points') && (
                        <FieldRow
                          label="Corridor points"
                          htmlFor="corridor_points"
                          hint="Raw text — corridor polyline data"
                        >
                          <textarea
                            id="corridor_points"
                            rows={3}
                            {...reg('corridor_points')}
                            className="w-full px-2 py-1 text-xs border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}
                      {has('priority') && (
                        <FieldRow label="Priority" htmlFor="priority">
                          <input
                            id="priority"
                            type="number"
                            step="1"
                            {...reg('priority')}
                            className="w-32 px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('wave') && (
                        <FieldRow
                          label="Wave"
                          htmlFor="wave"
                          hint="Narration wave bucket — 1-4"
                        >
                          <input
                            id="wave"
                            type="number"
                            step="1"
                            {...reg('wave')}
                            className="w-32 px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                    </TabPanel>

                    {/* ─── Hours & Contact ─────────────────────────────────── */}
                    <TabPanel className="space-y-4">
                      {has('hours') && (
                        <FieldRow label="Hours" htmlFor="hours">
                          <input
                            id="hours"
                            type="text"
                            {...reg('hours')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('phone') && (
                        <FieldRow label="Phone" htmlFor="phone">
                          <input
                            id="phone"
                            type="text"
                            {...reg('phone')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('website') && (
                        <FieldRow label="Website" htmlFor="website">
                          <input
                            id="website"
                            type="url"
                            {...reg('website')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {!has('hours') && !has('phone') && !has('website') && (
                        <p className="text-xs italic text-slate-500">
                          No hours/contact fields for this POI kind.
                        </p>
                      )}
                    </TabPanel>

                    {/* ─── Narration ───────────────────────────────────────── */}
                    <TabPanel className="space-y-4">
                      {/* 9P.10b — "Generate with Salem Oracle" launcher */}
                      <OracleLauncher
                        oracleAvailable={oracleAvailable}
                        onClick={openOracleDialog}
                        label="Generate narration with Salem Oracle"
                      />

                      {has('short_narration') && (
                        <FieldRow label="Short narration" htmlFor="short_narration">
                          <textarea
                            id="short_narration"
                            rows={3}
                            {...reg('short_narration')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('long_narration') && (
                        <FieldRow label="Long narration" htmlFor="long_narration">
                          <textarea
                            id="long_narration"
                            rows={6}
                            {...reg('long_narration')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('voice_clip_asset') && (
                        <FieldRow label="Voice clip asset" htmlFor="voice_clip_asset">
                          <input
                            id="voice_clip_asset"
                            type="text"
                            {...reg('voice_clip_asset')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('custom_description') && (
                        <FieldRow
                          label="Custom description"
                          htmlFor="custom_description"
                          hint="Merchant-supplied override"
                        >
                          <textarea
                            id="custom_description"
                            rows={3}
                            {...reg('custom_description')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('custom_icon_asset') && (
                        <FieldRow label="Custom icon asset" htmlFor="custom_icon_asset">
                          <input
                            id="custom_icon_asset"
                            type="text"
                            {...reg('custom_icon_asset')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('custom_voice_asset') && (
                        <FieldRow label="Custom voice asset" htmlFor="custom_voice_asset">
                          <input
                            id="custom_voice_asset"
                            type="text"
                            {...reg('custom_voice_asset')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}

                      {/* JSONB linked-content arrays — narration only */}
                      {has('related_figure_ids') && (
                        <FieldRow
                          label="Related figure IDs (JSONB array)"
                          htmlFor="related_figure_ids"
                        >
                          <textarea
                            id="related_figure_ids"
                            rows={2}
                            {...reg('related_figure_ids')}
                            className="w-full px-2 py-1 text-xs border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}
                      {has('related_fact_ids') && (
                        <FieldRow
                          label="Related fact IDs (JSONB array)"
                          htmlFor="related_fact_ids"
                        >
                          <textarea
                            id="related_fact_ids"
                            rows={2}
                            {...reg('related_fact_ids')}
                            className="w-full px-2 py-1 text-xs border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}
                      {has('related_source_ids') && (
                        <FieldRow
                          label="Related source IDs (JSONB array)"
                          htmlFor="related_source_ids"
                        >
                          <textarea
                            id="related_source_ids"
                            rows={2}
                            {...reg('related_source_ids')}
                            className="w-full px-2 py-1 text-xs border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}
                      {has('action_buttons') && (
                        <FieldRow
                          label="Action buttons (JSONB)"
                          htmlFor="action_buttons"
                        >
                          <textarea
                            id="action_buttons"
                            rows={3}
                            {...reg('action_buttons')}
                            className="w-full px-2 py-1 text-xs border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}
                      {has('source_id') && (
                        <FieldRow label="Source ID" htmlFor="source_id">
                          <input
                            id="source_id"
                            type="text"
                            {...reg('source_id')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('source_categories') && (
                        <FieldRow
                          label="Source categories (JSONB array)"
                          htmlFor="source_categories"
                        >
                          <textarea
                            id="source_categories"
                            rows={2}
                            {...reg('source_categories')}
                            className="w-full px-2 py-1 text-xs border border-slate-300 rounded font-mono"
                          />
                        </FieldRow>
                      )}

                      {/* Merchant fields — narration only */}
                      {has('merchant_tier') && (
                        <FieldRow label="Merchant tier" htmlFor="merchant_tier">
                          <input
                            id="merchant_tier"
                            type="text"
                            {...reg('merchant_tier')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('ad_priority') && (
                        <FieldRow label="Ad priority" htmlFor="ad_priority">
                          <input
                            id="ad_priority"
                            type="number"
                            step="1"
                            {...reg('ad_priority')}
                            className="w-32 px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                    </TabPanel>

                    {/* ─── Provenance ──────────────────────────────────────── */}
                    <TabPanel className="space-y-4">
                      {has('data_source') && (
                        <FieldRow label="Data source" htmlFor="data_source">
                          <input
                            id="data_source"
                            type="text"
                            {...reg('data_source')}
                            className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('confidence') && (
                        <FieldRow
                          label="Confidence"
                          htmlFor="confidence"
                          hint="0.0 - 1.0"
                        >
                          <input
                            id="confidence"
                            type="number"
                            step="0.01"
                            min="0"
                            max="1"
                            {...reg('confidence')}
                            className="w-32 px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('verified_date') && (
                        <FieldRow label="Verified date" htmlFor="verified_date">
                          <input
                            id="verified_date"
                            type="date"
                            {...reg('verified_date')}
                            className="w-44 px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {has('stale_after') && (
                        <FieldRow label="Stale after" htmlFor="stale_after">
                          <input
                            id="stale_after"
                            type="date"
                            {...reg('stale_after')}
                            className="w-44 px-2 py-1 text-sm border border-slate-300 rounded"
                          />
                        </FieldRow>
                      )}
                      {/* Read-only metadata */}
                      <div className="pt-2 border-t border-slate-200 text-xs text-slate-500 space-y-1">
                        {(poi.created_at as string | undefined) && (
                          <div>
                            <span className="font-medium text-slate-600">Created:</span>{' '}
                            <span className="font-mono">
                              {String(poi.created_at).slice(0, 19)}
                            </span>
                          </div>
                        )}
                        {(poi.updated_at as string | undefined) && (
                          <div>
                            <span className="font-medium text-slate-600">Updated:</span>{' '}
                            <span className="font-mono">
                              {String(poi.updated_at).slice(0, 19)}
                            </span>
                          </div>
                        )}
                      </div>
                    </TabPanel>

                    {/* ─── Linked Historical Content (9P.10a stub) ─────────── */}
                    <TabPanel className="space-y-4">
                      <div className="p-4 text-sm text-slate-600 bg-slate-50 border border-slate-200 rounded">
                        <p className="font-medium text-slate-700 mb-1">
                          No links yet
                        </p>
                        <p className="text-xs">
                          This tab will show historical figures, facts,
                          timeline events, primary sources, and newspaper
                          articles tied to this POI via the building→POI
                          bridge built in <strong>Phase 9Q</strong> (Salem
                          Domain Content Bridge). Until 9Q lands, no rows are
                          available.
                        </p>
                        <p className="text-xs mt-2 text-slate-500">
                          Editing the linkages happens in Phase 9Q.6 (Building
                          Map admin panel), not here. This tab will always be
                          read-only.
                        </p>
                      </div>
                    </TabPanel>

                    {/* ─── Danger Zone ─────────────────────────────────────── */}
                    <TabPanel className="space-y-4">
                      <div className="p-4 border border-rose-300 bg-rose-50 rounded">
                        <h3 className="text-sm font-semibold text-rose-800 mb-1">
                          Soft delete this POI
                        </h3>
                        <p className="text-xs text-rose-700 mb-3">
                          The POI is marked deleted but kept in the database.
                          It disappears from public reads and from the admin
                          map; the tree still shows it (greyed out) when
                          &quot;Show soft-deleted POIs&quot; is on. Restoring
                          requires an API call (no UI yet).
                        </p>

                        {!confirmDelete ? (
                          <button
                            type="button"
                            onClick={() => setConfirmDelete(true)}
                            disabled={!!poi.deleted_at}
                            className="px-3 py-1 text-sm rounded bg-rose-600 text-white hover:bg-rose-500 disabled:opacity-50 disabled:cursor-not-allowed"
                          >
                            {poi.deleted_at
                              ? 'Already deleted'
                              : 'Delete POI…'}
                          </button>
                        ) : (
                          <div className="space-y-2">
                            <p className="text-xs font-medium text-rose-800">
                              Are you sure? This soft-deletes the POI
                              immediately.
                            </p>
                            {deleteError && (
                              <p className="text-xs font-mono text-rose-800 bg-white p-2 border border-rose-300 rounded">
                                {deleteError}
                              </p>
                            )}
                            <div className="flex gap-2">
                              <button
                                type="button"
                                onClick={() => {
                                  setConfirmDelete(false)
                                  setDeleteError(null)
                                }}
                                disabled={deleteBusy}
                                className="px-3 py-1 text-xs rounded bg-slate-200 hover:bg-slate-300 disabled:opacity-50"
                              >
                                Cancel
                              </button>
                              <button
                                type="button"
                                onClick={handleDelete}
                                disabled={deleteBusy}
                                className="px-3 py-1 text-xs rounded bg-rose-700 text-white hover:bg-rose-600 disabled:opacity-50"
                              >
                                {deleteBusy ? 'Deleting…' : 'Yes, soft delete'}
                              </button>
                            </div>
                          </div>
                        )}
                      </div>
                    </TabPanel>
                  </TabPanels>
                </TabGroup>

                {/* Footer — save bar */}
                <div className="px-4 py-3 border-t border-slate-200 flex items-center gap-3">
                  {saveError && (
                    <div className="flex-1 text-xs font-mono text-rose-700 bg-rose-50 border border-rose-200 rounded px-2 py-1 truncate">
                      {saveError}
                    </div>
                  )}
                  {!saveError && (
                    <div className="flex-1 text-xs text-slate-500">
                      {isDirty
                        ? `${Object.keys(dirtyFields).length} field(s) modified`
                        : 'No changes'}
                    </div>
                  )}
                  <button
                    type="button"
                    onClick={handleClose}
                    disabled={isSubmitting}
                    className="px-3 py-1 text-sm rounded bg-slate-200 hover:bg-slate-300 disabled:opacity-50"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting || !isDirty}
                    className="px-3 py-1 text-sm rounded bg-emerald-600 text-white hover:bg-emerald-500 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    {isSubmitting ? 'Saving…' : 'Save'}
                  </button>
                </div>
              </form>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </Transition>

    {/* ─── Oracle "Generate" sub-dialog (9P.10b) ──────────────────────────── */}
    <Transition show={oracleOpen} as={Fragment}>
      <Dialog
        onClose={closeOracleDialog}
        className="relative z-[1100]"
      >
        <TransitionChild
          as={Fragment}
          enter="ease-out duration-150"
          enterFrom="opacity-0"
          enterTo="opacity-100"
          leave="ease-in duration-100"
          leaveFrom="opacity-100"
          leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-black/50" aria-hidden="true" />
        </TransitionChild>

        <div className="fixed inset-0 flex items-center justify-center p-4">
          <TransitionChild
            as={Fragment}
            enter="ease-out duration-150"
            enterFrom="opacity-0 scale-95"
            enterTo="opacity-100 scale-100"
            leave="ease-in duration-100"
            leaveFrom="opacity-100 scale-100"
            leaveTo="opacity-0 scale-95"
          >
            <DialogPanel className="w-[720px] max-w-[95vw] max-h-[88vh] flex flex-col bg-white rounded-lg shadow-2xl text-slate-800">
              {/* Header */}
              <div className="flex items-start justify-between px-4 py-3 border-b border-slate-200">
                <div className="min-w-0">
                  <DialogTitle className="text-base font-semibold flex items-center gap-2">
                    <span className="text-violet-700">✦</span>
                    Generate with Salem Oracle
                  </DialogTitle>
                  <p className="text-xs text-slate-500 truncate">
                    Editing <span className="font-mono">{poi.id}</span> · context
                    pinned to this POI ({poi.category || '—'})
                  </p>
                </div>
                <button
                  type="button"
                  onClick={closeOracleDialog}
                  disabled={oracleBusy}
                  className="text-slate-400 hover:text-slate-700 text-xl leading-none disabled:opacity-30"
                  aria-label="Close"
                >
                  ×
                </button>
              </div>

              {/* Body */}
              <div className="flex-1 min-h-0 overflow-y-auto px-4 py-4 space-y-4">
                {/* Prompt input */}
                <div className="space-y-1">
                  <label
                    htmlFor="oracle-prompt"
                    className="block text-xs font-medium text-slate-700"
                  >
                    Prompt
                  </label>
                  <textarea
                    id="oracle-prompt"
                    rows={4}
                    value={oraclePrompt}
                    onChange={(e) => setOraclePrompt(e.target.value)}
                    disabled={oracleBusy}
                    placeholder='e.g. "Rewrite this description as a single 90-word tour-guide paragraph for spoken audio. Lead with what makes this place haunted, not its founding date."'
                    className="w-full px-2 py-1 text-sm border border-slate-300 rounded resize-y disabled:bg-slate-50"
                  />
                  <p className="text-[11px] text-slate-500">
                    The Oracle is told this POI is the immediate referent —
                    use &quot;this&quot;, &quot;the description&quot;,
                    &quot;him/her&quot;, etc. freely.
                  </p>
                </div>

                {/* Options row */}
                <div className="flex items-center justify-between gap-3">
                  <label className="flex items-center gap-2 text-xs text-slate-700">
                    <input
                      type="checkbox"
                      checked={oracleReset}
                      onChange={(e) => setOracleReset(e.target.checked)}
                      disabled={oracleBusy}
                    />
                    Reset Oracle conversation history
                    <span
                      className="text-slate-400"
                      title="The Oracle keeps a single shared 6-turn rolling history across all callers. Reset clears it before this turn."
                    >
                      (?)
                    </span>
                  </label>

                  <button
                    type="button"
                    onClick={() => void runOracleAsk(null)}
                    disabled={oracleBusy || !oracleAvailable || !oraclePrompt.trim()}
                    title={
                      !oracleAvailable
                        ? 'Oracle is unavailable — start ~/Development/Salem/scripts/start-testapp.sh and click the header pill'
                        : 'Asks the Salem LLM to compose content. Typically 5-15s per call.'
                    }
                    className="px-4 py-1.5 text-sm rounded bg-violet-700 text-white hover:bg-violet-600 disabled:bg-slate-300 disabled:cursor-not-allowed"
                  >
                    {oracleBusy ? 'Generating…' : 'Generate'}
                  </button>
                </div>

                {/* Oracle unavailable warning */}
                {!oracleAvailable && (
                  <div className="p-3 text-xs text-rose-800 bg-rose-50 border border-rose-200 rounded space-y-2">
                    <p className="font-medium">
                      Salem Oracle is unavailable.
                    </p>
                    <p>
                      Start it with:{' '}
                      <code className="px-1 py-0.5 bg-white border border-rose-200 rounded font-mono">
                        bash ~/Development/Salem/scripts/start-testapp.sh
                      </code>
                    </p>
                    <button
                      type="button"
                      onClick={() => void onOracleRefresh()}
                      className="px-2 py-1 text-xs rounded bg-rose-700 text-white hover:bg-rose-600"
                    >
                      Re-check status
                    </button>
                  </div>
                )}

                {/* Spinner */}
                {oracleBusy && (
                  <div className="flex items-center gap-3 px-3 py-3 bg-violet-50 border border-violet-200 rounded">
                    <div className="w-4 h-4 border-2 border-violet-600 border-t-transparent rounded-full animate-spin" />
                    <p className="text-xs text-violet-800">
                      Asking the Oracle… typically 5-15s. The LLM is gemma3:27b
                      on the workstation GPU; concurrent calls would queue, so
                      this is sequential.
                    </p>
                  </div>
                )}

                {/* Error */}
                {oracleError && !oracleBusy && (
                  <div className="p-3 text-xs font-mono text-rose-800 bg-rose-50 border border-rose-200 rounded break-words">
                    {oracleError}
                  </div>
                )}

                {/* Result */}
                {oracleResult && !oracleBusy && (
                  <div className="space-y-3">
                    <div className="p-3 bg-slate-50 border border-slate-200 rounded">
                      <p className="text-[11px] uppercase tracking-wide text-slate-500 font-semibold mb-1">
                        Oracle response · turn {oracleResult.history_turn_count}
                      </p>
                      <p className="text-sm text-slate-800 whitespace-pre-wrap">
                        {oracleResult.text}
                      </p>
                    </div>

                    {/* Insert buttons */}
                    {insertableFields.length > 0 && (
                      <div className="flex flex-wrap gap-2">
                        {insertableFields.map(({ field, label }) => (
                          <button
                            key={field}
                            type="button"
                            onClick={() => insertOracleInto(field)}
                            className="px-3 py-1 text-xs rounded bg-emerald-600 text-white hover:bg-emerald-500"
                          >
                            {label}
                          </button>
                        ))}
                      </div>
                    )}
                    {insertableFields.length === 0 && (
                      <p className="text-xs italic text-slate-500">
                        This POI kind has no narration / description field to
                        insert into. Copy the text manually if you need it.
                      </p>
                    )}

                    {/* Iterate */}
                    <div className="flex items-center gap-2 pt-1 border-t border-slate-200">
                      <p className="text-[11px] text-slate-500 flex-1">
                        To iterate, type a follow-up above (e.g. &quot;make
                        that two sentences shorter&quot;) and hit Iterate. The
                        Oracle&apos;s 6-turn rolling history carries forward.
                      </p>
                      <button
                        type="button"
                        onClick={() => void runOracleAsk(false)}
                        disabled={oracleBusy || !oracleAvailable || !oraclePrompt.trim()}
                        className="px-3 py-1 text-xs rounded bg-violet-600 text-white hover:bg-violet-500 disabled:bg-slate-300"
                      >
                        Iterate
                      </button>
                    </div>

                    {/* Primary sources */}
                    {oracleResult.primary_sources?.length > 0 && (
                      <details className="text-xs">
                        <summary className="cursor-pointer text-slate-600 hover:text-slate-800 font-medium">
                          Primary sources ({oracleResult.primary_sources.length})
                        </summary>
                        <ul className="mt-2 space-y-2">
                          {oracleResult.primary_sources.map((src, i) => (
                            <li
                              key={i}
                              className="p-2 bg-amber-50 border border-amber-200 rounded"
                            >
                              <p className="font-medium text-amber-900">
                                {src.attribution}
                                {typeof src.score === 'number' && (
                                  <span className="ml-2 text-amber-700 font-mono">
                                    (score {src.score.toFixed(2)})
                                  </span>
                                )}
                              </p>
                              <p className="mt-1 text-amber-800 italic whitespace-pre-wrap">
                                {src.verbatim_text}
                              </p>
                              {src.modern_gloss && (
                                <p className="mt-1 text-[11px] text-amber-700">
                                  Gloss: {src.modern_gloss}
                                </p>
                              )}
                            </li>
                          ))}
                        </ul>
                      </details>
                    )}
                  </div>
                )}
              </div>

              {/* Footer */}
              <div className="px-4 py-3 border-t border-slate-200 flex items-center justify-end">
                <button
                  type="button"
                  onClick={closeOracleDialog}
                  disabled={oracleBusy}
                  className="px-3 py-1 text-sm rounded bg-slate-200 hover:bg-slate-300 disabled:opacity-50"
                >
                  Done
                </button>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </Transition>
    </Fragment>
  )
}

// ─── Oracle launcher button (9P.10b) ────────────────────────────────────────
//
// Small inline button that opens the Oracle sub-dialog. Lives on the
// Narration tab as a prominent banner-style action and on the General tab
// (compact variant) under the description field.

interface OracleLauncherProps {
  oracleAvailable: boolean
  onClick: () => void
  label: string
  compact?: boolean
}

function OracleLauncher({
  oracleAvailable,
  onClick,
  label,
  compact,
}: OracleLauncherProps) {
  const tooltip = oracleAvailable
    ? 'Asks the Salem LLM to compose content. 5-15 seconds per call.'
    : 'Salem Oracle unavailable — start the testapp and reload.'

  if (compact) {
    return (
      <button
        type="button"
        onClick={onClick}
        title={tooltip}
        className={[
          'mt-1 inline-flex items-center gap-1 px-2 py-1 text-[11px] rounded transition-colors',
          oracleAvailable
            ? 'bg-violet-100 text-violet-800 hover:bg-violet-200'
            : 'bg-slate-100 text-slate-500',
        ].join(' ')}
      >
        <span aria-hidden>✦</span>
        {label}
      </button>
    )
  }

  return (
    <div
      className={[
        'flex items-center justify-between gap-3 p-3 rounded border',
        oracleAvailable
          ? 'bg-violet-50 border-violet-200'
          : 'bg-slate-50 border-slate-200',
      ].join(' ')}
    >
      <div className="min-w-0">
        <p className="text-xs font-semibold text-violet-900 flex items-center gap-1">
          <span aria-hidden>✦</span> Salem Oracle
        </p>
        <p className="text-[11px] text-slate-600">
          {oracleAvailable
            ? 'Ask the LLM to compose, rewrite, or expand narration content. Pinned to this POI for context.'
            : 'Currently unavailable. Start the Salem testapp to enable generation.'}
        </p>
      </div>
      <button
        type="button"
        onClick={onClick}
        title={tooltip}
        className={[
          'px-3 py-1.5 text-xs rounded transition-colors whitespace-nowrap',
          oracleAvailable
            ? 'bg-violet-700 text-white hover:bg-violet-600'
            : 'bg-slate-300 text-slate-700 hover:bg-slate-400',
        ].join(' ')}
      >
        {label}
      </button>
    </div>
  )
}
