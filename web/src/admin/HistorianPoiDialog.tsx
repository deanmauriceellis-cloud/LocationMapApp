// HistorianPoiDialog — S290
//
// A compact, restricted POI editor for the 'historian' admin role (operator's
// wife authoring historical content). It deliberately renders ONLY the fields a
// historian may edit — narrations, descriptions, category/subcategory, and the
// ten boolean flags — and saves through the same partial-update endpoint the
// full admin dialog uses (PUT /api/admin/salem/pois/:id?force=true).
//
// The backend is the authoritative boundary: cache-proxy/lib/admin-pois.js
// rejects (403) any out-of-scope field for a historian regardless of what the
// UI sends. This dialog mirrors that scope so a historian only ever sees the
// fields she can actually save. AdminLayout renders this instead of the big
// PoiEditDialog when the authenticated role is 'historian'; the admin dialog is
// left completely untouched.

import { Fragment, useCallback, useMemo, useState } from 'react'
import {
  Dialog,
  DialogPanel,
  DialogTitle,
  Transition,
  TransitionChild,
} from '@headlessui/react'
import { useForm, type FieldValues } from 'react-hook-form'
import type { PoiRow, CategoryRow, SubcategoryRow } from './PoiTree'
import SubtopicEditor from './SubtopicEditor'

// ─── Field scope (mirror of HISTORIAN_EDITABLE_FIELDS) ──────────────────────

// Boolean flag columns, with UI labels + hints. Order = render order.
const FLAGS: { field: string; label: string; hint: string }[] = [
  { field: 'is_tour_poi',            label: 'Tour POI',             hint: 'Always narrates during tour mode' },
  { field: 'is_civic_poi',           label: 'Civic POI',            hint: 'Narrates during tour only when the Civic layer is on' },
  { field: 'is_narrated',            label: 'Narrated',             hint: 'Has geofence narration audio' },
  { field: 'default_visible',        label: 'Visible by default',   hint: 'Shown on the map without filters' },
  { field: 'seasonal',               label: 'Seasonal',             hint: 'Only available certain times of year' },
  { field: 'requires_transportation',label: 'Requires transport',   hint: 'Not walkable from downtown' },
  { field: 'wheelchair_accessible',  label: 'Wheelchair accessible',hint: 'ADA accessible' },
  { field: 'location_truth_of_record', label: 'Location truth-of-record', hint: 'Coordinates are operator-confirmed and authoritative' },
  { field: 'haunt_enabled',          label: 'Haunt effect enabled', hint: 'Sprite peek effect fires near this POI' },
]
const FLAG_FIELDS = FLAGS.map((f) => f.field).concat('no_overwrite')

// All fields this dialog reads/writes (defaults are built over these).
const ALL_FIELDS = [
  'category', 'subcategory',
  'description', 'short_description', 'custom_description', 'origin_story',
  'short_narration', 'long_narration', 'historical_narration', 'narration_subtopics',
  ...FLAG_FIELDS,
]

const JSONB_FIELDS = new Set(['narration_subtopics'])
const BOOLEAN_FIELDS = new Set(FLAG_FIELDS)

function notNullish(v: unknown): boolean {
  return v !== null && v !== undefined
}

// Build react-hook-form defaults for the historian field set from a POI row.
function buildDefaults(poi: PoiRow): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const field of ALL_FIELDS) {
    const raw = (poi as Record<string, unknown>)[field]
    if (JSONB_FIELDS.has(field)) {
      try {
        out[field] = notNullish(raw) ? JSON.stringify(raw, null, 2) : '[]'
      } catch {
        out[field] = '[]'
      }
    } else if (BOOLEAN_FIELDS.has(field)) {
      out[field] = !!raw
    } else {
      out[field] = notNullish(raw) ? raw : ''
    }
  }
  return out
}

// Build the partial-update payload from only the fields the user touched,
// mirroring PoiEditDialog.buildPayload for this field subset.
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
        return { error: `JSON parse error in "${field}": ${e instanceof Error ? e.message : String(e)}` }
      }
    } else if (BOOLEAN_FIELDS.has(field)) {
      payload[field] = !!raw
    } else {
      payload[field] = raw === '' ? null : raw
    }
  }
  return { payload }
}

// ─── Props ──────────────────────────────────────────────────────────────────

export interface HistorianPoiDialogProps {
  open: boolean
  poi: PoiRow | null
  categories: CategoryRow[]
  subcategories: SubcategoryRow[]
  onSaved: (updated: PoiRow) => void
  onClose: () => void
}

// ─── Component ────────────────────────────────────────────────────────────────

export function HistorianPoiDialog({
  open,
  poi,
  categories,
  subcategories,
  onSaved,
  onClose,
}: HistorianPoiDialogProps) {
  const defaultValues = useMemo(
    () => (poi ? buildDefaults(poi) : {}),
    [poi],
  )

  const {
    register,
    handleSubmit,
    formState: { dirtyFields, isDirty, isSubmitting },
    reset,
    setValue,
    watch,
  } = useForm<FieldValues>({ defaultValues, values: defaultValues })

  const [saveError, setSaveError] = useState<string | null>(null)

  const currentCategory = (watch('category') as string | undefined) ?? ''
  const currentSubcategory = (watch('subcategory') as string | undefined) ?? ''

  const sortedCategories = useMemo(
    () => [...categories].sort((a, b) => a.display_order - b.display_order),
    [categories],
  )
  const subcategoryOptions = useMemo(() => {
    const cat = currentCategory.trim()
    if (!cat) return [] as SubcategoryRow[]
    return subcategories
      .filter((s) => s.category_id === cat)
      .sort((a, b) => a.display_order - b.display_order)
  }, [currentCategory, subcategories])

  const onSubmit = useCallback(
    async (values: FieldValues) => {
      if (!poi) return
      setSaveError(null)
      const built = buildPayload(values, dirtyFields)
      if ('error' in built) {
        setSaveError(built.error)
        return
      }
      if (Object.keys(built.payload).length === 0) {
        onClose()
        return
      }
      try {
        const res = await fetch(
          `/api/admin/salem/pois/${encodeURIComponent(poi.id)}?force=true`,
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
          } catch { /* non-JSON body */ }
          throw new Error(msg)
        }
        const updated = (await res.json()) as PoiRow
        onSaved(updated)
        reset(buildDefaults(updated))
        onClose()
      } catch (e) {
        setSaveError(e instanceof Error ? e.message : String(e))
      }
    },
    [poi, dirtyFields, onSaved, onClose, reset],
  )

  const handleClose = useCallback(() => {
    if (isDirty && !window.confirm('You have unsaved changes. Discard and close?')) return
    setSaveError(null)
    onClose()
  }, [isDirty, onClose])

  if (!poi) return null

  const inputCls = 'w-full px-2 py-1 text-sm border border-slate-300 rounded'
  const labelCls = 'block text-xs font-medium text-slate-700'

  return (
    <Transition show={open} as={Fragment}>
      <Dialog onClose={handleClose} className="relative z-[1000]">
        <TransitionChild
          as={Fragment}
          enter="ease-out duration-150" enterFrom="opacity-0" enterTo="opacity-100"
          leave="ease-in duration-100" leaveFrom="opacity-100" leaveTo="opacity-0"
        >
          <div className="fixed inset-0 bg-black/65" aria-hidden="true" />
        </TransitionChild>

        <div className="fixed inset-0 flex items-center justify-center p-4">
          <TransitionChild
            as={Fragment}
            enter="ease-out duration-150" enterFrom="opacity-0 scale-95" enterTo="opacity-100 scale-100"
            leave="ease-in duration-100" leaveFrom="opacity-100 scale-100" leaveTo="opacity-0 scale-95"
          >
            <DialogPanel className="w-[820px] max-w-[98vw] h-[92vh] max-h-[92vh] flex flex-col bg-white rounded-lg shadow-xl text-slate-800">
              {/* Header */}
              <div className="flex items-center justify-between gap-3 px-4 py-3 border-b border-slate-200">
                <div className="min-w-0 flex-1">
                  <DialogTitle className="text-base font-semibold truncate">
                    {poi.name || '(unnamed)'}
                  </DialogTitle>
                  <p className="text-xs text-slate-500 font-mono truncate">
                    {poi.category || '—'} · {poi.id}
                  </p>
                </div>
                <span className="shrink-0 px-2 py-0.5 rounded text-[10px] font-semibold uppercase tracking-wide bg-amber-100 text-amber-800 border border-amber-300">
                  Historian
                </span>
                <button
                  type="button"
                  onClick={handleClose}
                  className="shrink-0 text-slate-400 hover:text-slate-700 text-xl leading-none"
                  aria-label="Close"
                >
                  ×
                </button>
              </div>

              <form onSubmit={handleSubmit(onSubmit)} className="flex-1 min-h-0 flex flex-col">
                <div className="flex-1 min-h-0 overflow-y-auto px-4 py-4 space-y-5">
                  {/* ── Category ────────────────────────────────────────── */}
                  <section className="space-y-3">
                    <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Category</h3>
                    <div className="space-y-1">
                      <label htmlFor="hist-category" className={labelCls}>Category</label>
                      <select id="hist-category" {...register('category')} className={inputCls + ' bg-white'}>
                        <option value="">— none —</option>
                        {sortedCategories.map((c) => (
                          <option key={c.id} value={c.id}>{c.label} ({c.id})</option>
                        ))}
                        {currentCategory && !sortedCategories.some((c) => c.id === currentCategory) && (
                          <option value={currentCategory}>{currentCategory} (legacy — not in canonical list)</option>
                        )}
                      </select>
                    </div>
                    <div className="space-y-1">
                      <label htmlFor="hist-subcategory" className={labelCls}>Subcategory</label>
                      <select
                        id="hist-subcategory"
                        {...register('subcategory')}
                        disabled={!currentCategory}
                        className={inputCls + ' bg-white disabled:bg-slate-100 disabled:text-slate-400'}
                      >
                        <option value="">— none —</option>
                        {subcategoryOptions.map((s) => (
                          <option key={s.id} value={s.id}>{s.label}</option>
                        ))}
                        {currentSubcategory && !subcategoryOptions.some((s) => s.id === currentSubcategory) && (
                          <option value={currentSubcategory}>{currentSubcategory} (legacy — outside current category)</option>
                        )}
                      </select>
                      {!currentCategory && <p className="text-[11px] text-slate-500">Pick a category first.</p>}
                    </div>
                  </section>

                  {/* ── Descriptions ────────────────────────────────────── */}
                  <section className="space-y-3">
                    <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Descriptions</h3>
                    <div className="space-y-1">
                      <label htmlFor="hist-description" className={labelCls}>Description</label>
                      <textarea id="hist-description" rows={4} {...register('description')} className={inputCls} />
                    </div>
                    <div className="space-y-1">
                      <label htmlFor="hist-short_description" className={labelCls}>Short description</label>
                      <textarea id="hist-short_description" rows={2} {...register('short_description')} className={inputCls} />
                    </div>
                    <div className="space-y-1">
                      <label htmlFor="hist-custom_description" className={labelCls}>Custom description</label>
                      <textarea id="hist-custom_description" rows={2} {...register('custom_description')} className={inputCls} />
                    </div>
                    <div className="space-y-1">
                      <label htmlFor="hist-origin_story" className={labelCls}>Origin story</label>
                      <textarea id="hist-origin_story" rows={3} {...register('origin_story')} className={inputCls} />
                    </div>
                  </section>

                  {/* ── Narration ───────────────────────────────────────── */}
                  <section className="space-y-3">
                    <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Narration</h3>
                    <div className="space-y-1">
                      <label htmlFor="hist-short_narration" className={labelCls}>Short narration</label>
                      <textarea id="hist-short_narration" rows={3} {...register('short_narration')} className={inputCls} />
                    </div>
                    <div className="space-y-1">
                      <label htmlFor="hist-long_narration" className={labelCls}>Long narration</label>
                      <textarea id="hist-long_narration" rows={6} {...register('long_narration')} className={inputCls} />
                    </div>
                    <div className="space-y-1">
                      <label htmlFor="hist-historical_narration" className={labelCls}>Historical narration (pre-1860 only)</label>
                      <textarea
                        id="hist-historical_narration"
                        rows={10}
                        {...register('historical_narration')}
                        className={inputCls + ' font-serif'}
                        placeholder="Strict pre-1860 narration. Powers Historical Tour mode. Modern context belongs in short/long narration."
                      />
                    </div>
                    <div className="space-y-1">
                      <label className={labelCls}>Subtopics</label>
                      <SubtopicEditor
                        value={(watch('narration_subtopics') as string) ?? ''}
                        onChange={(next) =>
                          setValue('narration_subtopics', next, {
                            shouldDirty: true,
                            shouldValidate: false,
                            shouldTouch: true,
                          })
                        }
                      />
                      <p className="text-[11px] text-slate-500">Story-shaped overflow cards shown on the POI detail sheet.</p>
                    </div>
                  </section>

                  {/* ── Flags ───────────────────────────────────────────── */}
                  <section className="space-y-3">
                    <h3 className="text-xs font-semibold text-slate-500 uppercase tracking-wide">Flags</h3>
                    <div className="grid grid-cols-2 gap-x-8 gap-y-3">
                      {FLAGS.map(({ field, label, hint }) => (
                        <label key={field} className="flex items-start gap-3 cursor-pointer group">
                          <div className="relative mt-0.5 flex-shrink-0">
                            <input type="checkbox" {...register(field)} className="sr-only peer" />
                            <div className="w-9 h-5 rounded-full border border-slate-300 bg-slate-100 peer-checked:bg-teal-500 peer-checked:border-teal-500 transition-colors" />
                            <div className="absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white shadow transition-transform peer-checked:translate-x-4" />
                          </div>
                          <div>
                            <div className="text-sm font-medium text-slate-700 group-hover:text-slate-900">{label}</div>
                            <div className="text-xs text-slate-400">{hint}</div>
                          </div>
                        </label>
                      ))}
                    </div>

                    {/* Authoring lock — its own emphasized row */}
                    <label className="flex items-start gap-3 cursor-pointer group rounded border border-red-200 bg-red-50/50 p-3">
                      <div className="relative mt-0.5 flex-shrink-0">
                        <input type="checkbox" {...register('no_overwrite')} className="sr-only peer" />
                        <div className="w-9 h-5 rounded-full border border-slate-300 bg-slate-200 peer-checked:bg-red-600 peer-checked:border-red-600 transition-colors" />
                        <div className="absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white shadow ring-1 ring-slate-400 peer-checked:ring-red-700 transition-transform peer-checked:translate-x-4" />
                      </div>
                      <div>
                        <div className="text-sm font-medium text-slate-800">Admin Authored — Do Not Overwrite</div>
                        <div className="text-xs text-slate-500">Blocks AI / unattended automation from changing narration, description, or year. Your hand-edits here always go through.</div>
                      </div>
                    </label>
                  </section>
                </div>

                {/* Footer */}
                <div className="border-t border-slate-200 px-4 py-3 flex items-center gap-3">
                  {saveError && (
                    <div className="flex-1 text-xs text-rose-700 bg-rose-50 border border-rose-200 rounded px-2 py-1 truncate" title={saveError}>
                      {saveError}
                    </div>
                  )}
                  <div className="flex-1" />
                  <button
                    type="button"
                    onClick={handleClose}
                    className="px-3 py-1.5 text-sm rounded border border-slate-300 bg-white hover:bg-slate-50"
                  >
                    Cancel
                  </button>
                  <button
                    type="submit"
                    disabled={isSubmitting || !isDirty}
                    className="px-4 py-1.5 text-sm rounded bg-indigo-600 text-white hover:bg-indigo-500 disabled:opacity-50 disabled:cursor-not-allowed"
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
  )
}

export default HistorianPoiDialog
