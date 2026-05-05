// PoiCreateDialog — S225
//
// Minimal create-POI modal opened after the operator clicks the map in
// "+ New POI" place-mode (see AdminLayout / AdminMap wiring). Captures only
// the four fields needed to materialize the row in salem_pois:
//   - Name (required)
//   - Category (required; inline "+ Add new" mirrors PoiEditDialog)
//   - Subcategory (optional, drives off Category)
//   - Address (optional)
// Coords come in pre-filled from the map click and are shown read-only.
//
// On 201 the new row is handed back via onCreated; AdminLayout then opens the
// existing PoiEditDialog on it so the operator can fill narration, flags,
// hours, etc. — keeps this dialog small and ships the create flow without
// duplicating the 2800-LOC editor.

import { Dialog, DialogPanel, DialogTitle } from '@headlessui/react'
import { useCallback, useMemo, useState } from 'react'
import type { PoiRow, CategoryRow, SubcategoryRow } from './PoiTree'

export interface PoiCreateDialogProps {
  open: boolean
  /** Click coords from AdminMap. The dialog only opens once these are known. */
  lat: number | null
  lng: number | null
  categories: CategoryRow[]
  subcategories: SubcategoryRow[]
  /** Refetch taxonomy after a successful inline + Add new. */
  onTaxonomyChanged: () => void | Promise<void>
  /** POST returned 201; row is the freshly-inserted POI. */
  onCreated: (poi: PoiRow) => void
  onClose: () => void
}

export function PoiCreateDialog({
  open,
  lat,
  lng,
  categories,
  subcategories,
  onTaxonomyChanged,
  onCreated,
  onClose,
}: PoiCreateDialogProps) {
  const [name, setName] = useState('')
  const [category, setCategory] = useState('')
  const [subcategory, setSubcategory] = useState('')
  const [address, setAddress] = useState('')
  const [submitBusy, setSubmitBusy] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  // Inline + Add new category panel (mirrors PoiEditDialog pattern).
  const [addCatOpen, setAddCatOpen] = useState(false)
  const [newCatLabel, setNewCatLabel] = useState('')
  const [newCatColor, setNewCatColor] = useState('#607D8B')
  const [addCatBusy, setAddCatBusy] = useState(false)
  const [addCatError, setAddCatError] = useState<string | null>(null)

  // Filter subcategories to the currently-selected category. Same shape as
  // PoiEditDialog's currentCategory-driven filter.
  const filteredSubs = useMemo(
    () => subcategories.filter((s) => s.category_id === category),
    [subcategories, category],
  )

  const reset = useCallback(() => {
    setName('')
    setCategory('')
    setSubcategory('')
    setAddress('')
    setSubmitBusy(false)
    setSubmitError(null)
    setAddCatOpen(false)
    setNewCatLabel('')
    setNewCatColor('#607D8B')
    setAddCatBusy(false)
    setAddCatError(null)
  }, [])

  const handleClose = useCallback(() => {
    if (submitBusy) return
    reset()
    onClose()
  }, [submitBusy, reset, onClose])

  const submitAddCategory = useCallback(async () => {
    const label = newCatLabel.trim()
    if (!label) {
      setAddCatError('Label is required')
      return
    }
    setAddCatBusy(true)
    setAddCatError(null)
    try {
      const res = await fetch('/api/admin/salem/categories', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ label, color: newCatColor }),
      })
      const body = await res.json().catch(() => ({}))
      if (!res.ok) {
        setAddCatError(body.error || `HTTP ${res.status}`)
        return
      }
      const created = body.category as CategoryRow
      await onTaxonomyChanged()
      setCategory(created.id)
      setSubcategory('')
      setAddCatOpen(false)
      setNewCatLabel('')
      setNewCatColor('#607D8B')
    } catch (e) {
      setAddCatError(e instanceof Error ? e.message : String(e))
    } finally {
      setAddCatBusy(false)
    }
  }, [newCatLabel, newCatColor, onTaxonomyChanged])

  const submit = useCallback(async () => {
    if (lat == null || lng == null) return
    const trimmedName = name.trim()
    if (!trimmedName) {
      setSubmitError('Name is required')
      return
    }
    if (!category) {
      setSubmitError('Category is required')
      return
    }
    setSubmitBusy(true)
    setSubmitError(null)
    try {
      const payload: Record<string, unknown> = {
        name: trimmedName,
        lat,
        lng,
        category,
      }
      if (subcategory) payload.subcategory = subcategory
      const trimmedAddr = address.trim()
      if (trimmedAddr) payload.address = trimmedAddr

      const res = await fetch('/api/admin/salem/pois', {
        method: 'POST',
        credentials: 'same-origin',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload),
      })
      const body = await res.json().catch(() => ({}))
      if (!res.ok) {
        setSubmitError(body.error || `HTTP ${res.status}`)
        return
      }
      // Server returns the row with lat/lng as strings/numbers; normalize
      // to numbers so AdminMap renders the marker without parseFloat juggling.
      const created: PoiRow = {
        ...body,
        lat: typeof body.lat === 'string' ? parseFloat(body.lat) : body.lat,
        lng: typeof body.lng === 'string' ? parseFloat(body.lng) : body.lng,
      }
      onCreated(created)
      reset()
    } catch (e) {
      setSubmitError(e instanceof Error ? e.message : String(e))
    } finally {
      setSubmitBusy(false)
    }
  }, [lat, lng, name, category, subcategory, address, onCreated, reset])

  const submitDisabled = submitBusy || !name.trim() || !category || lat == null || lng == null

  return (
    <Dialog open={open} onClose={handleClose} className="relative z-[1100]">
      <div className="fixed inset-0 bg-black/40" aria-hidden="true" />
      <div className="fixed inset-0 flex items-center justify-center p-4">
        <DialogPanel className="w-full max-w-md rounded-lg bg-white shadow-xl">
          <div className="px-5 py-4 border-b border-slate-200">
            <DialogTitle className="text-base font-semibold text-slate-800">
              New POI
            </DialogTitle>
            <p className="mt-1 text-xs text-slate-500">
              Coordinates from your map click. Fill in the basics; you can edit
              narration, flags, hours, and the rest after this saves.
            </p>
          </div>

          <div className="px-5 py-4 space-y-4">
            {/* Coords readonly */}
            <div className="grid grid-cols-2 gap-3">
              <div>
                <label className="block text-xs font-medium text-slate-700">
                  Latitude
                </label>
                <input
                  readOnly
                  value={lat != null ? lat.toFixed(6) : ''}
                  className="mt-1 w-full px-2 py-1.5 text-sm rounded border border-slate-300 bg-slate-50 text-slate-600"
                />
              </div>
              <div>
                <label className="block text-xs font-medium text-slate-700">
                  Longitude
                </label>
                <input
                  readOnly
                  value={lng != null ? lng.toFixed(6) : ''}
                  className="mt-1 w-full px-2 py-1.5 text-sm rounded border border-slate-300 bg-slate-50 text-slate-600"
                />
              </div>
            </div>

            {/* Name */}
            <div>
              <label className="block text-xs font-medium text-slate-700">
                Name <span className="text-rose-600">*</span>
              </label>
              <input
                autoFocus
                type="text"
                value={name}
                onChange={(e) => setName(e.target.value)}
                disabled={submitBusy}
                className="mt-1 w-full px-2 py-1.5 text-sm rounded border border-slate-300 focus:border-sky-500 focus:ring-1 focus:ring-sky-500 outline-none"
                placeholder="e.g. Old Witch Trial Plaque"
              />
            </div>

            {/* Category */}
            <div>
              <label className="block text-xs font-medium text-slate-700">
                Category <span className="text-rose-600">*</span>
              </label>
              <select
                value={category}
                onChange={(e) => {
                  setCategory(e.target.value)
                  setSubcategory('')
                }}
                disabled={submitBusy}
                className="mt-1 w-full px-2 py-1.5 text-sm rounded border border-slate-300 focus:border-sky-500 focus:ring-1 focus:ring-sky-500 outline-none bg-white"
              >
                <option value="">— pick a category —</option>
                {[...categories]
                  .sort((a, b) => a.display_order - b.display_order || a.label.localeCompare(b.label))
                  .map((c) => (
                    <option key={c.id} value={c.id}>
                      {c.label}
                    </option>
                  ))}
              </select>
              {!addCatOpen && (
                <button
                  type="button"
                  onClick={() => setAddCatOpen(true)}
                  disabled={submitBusy}
                  className="mt-1 text-xs text-sky-700 hover:underline"
                >
                  + Add new category…
                </button>
              )}
              {addCatOpen && (
                <div className="mt-2 p-3 rounded border border-slate-200 bg-slate-50 space-y-2">
                  <div className="flex items-center gap-2">
                    <input
                      type="text"
                      value={newCatLabel}
                      onChange={(e) => setNewCatLabel(e.target.value)}
                      placeholder="Label (e.g. Cemeteries)"
                      disabled={addCatBusy}
                      className="flex-1 px-2 py-1 text-sm rounded border border-slate-300 outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500"
                    />
                    <input
                      type="color"
                      value={newCatColor}
                      onChange={(e) => setNewCatColor(e.target.value)}
                      disabled={addCatBusy}
                      className="h-7 w-10 rounded border border-slate-300"
                      title="Marker color"
                    />
                  </div>
                  {addCatError && (
                    <p className="text-xs text-rose-600">{addCatError}</p>
                  )}
                  <div className="flex justify-end gap-2">
                    <button
                      type="button"
                      onClick={() => {
                        setAddCatOpen(false)
                        setNewCatLabel('')
                        setAddCatError(null)
                      }}
                      disabled={addCatBusy}
                      className="px-2 py-1 text-xs rounded text-slate-700 hover:bg-slate-200"
                    >
                      Cancel
                    </button>
                    <button
                      type="button"
                      onClick={submitAddCategory}
                      disabled={addCatBusy || !newCatLabel.trim()}
                      className="px-2 py-1 text-xs rounded bg-sky-600 hover:bg-sky-500 text-white disabled:opacity-40 disabled:cursor-not-allowed"
                    >
                      {addCatBusy ? 'Saving…' : 'Save category'}
                    </button>
                  </div>
                </div>
              )}
            </div>

            {/* Subcategory (optional) */}
            <div>
              <label className="block text-xs font-medium text-slate-700">
                Subcategory <span className="text-slate-400">(optional)</span>
              </label>
              <select
                value={subcategory}
                onChange={(e) => setSubcategory(e.target.value)}
                disabled={submitBusy || !category}
                className="mt-1 w-full px-2 py-1.5 text-sm rounded border border-slate-300 focus:border-sky-500 focus:ring-1 focus:ring-sky-500 outline-none bg-white disabled:bg-slate-50 disabled:text-slate-400"
              >
                <option value="">— none —</option>
                {filteredSubs
                  .slice()
                  .sort((a, b) => a.display_order - b.display_order || a.label.localeCompare(b.label))
                  .map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.label}
                    </option>
                  ))}
              </select>
            </div>

            {/* Address (optional) */}
            <div>
              <label className="block text-xs font-medium text-slate-700">
                Address <span className="text-slate-400">(optional)</span>
              </label>
              <input
                type="text"
                value={address}
                onChange={(e) => setAddress(e.target.value)}
                disabled={submitBusy}
                className="mt-1 w-full px-2 py-1.5 text-sm rounded border border-slate-300 focus:border-sky-500 focus:ring-1 focus:ring-sky-500 outline-none"
                placeholder="e.g. 174 Derby St, Salem, MA 01970"
              />
            </div>

            {submitError && (
              <p className="text-xs text-rose-600">{submitError}</p>
            )}
          </div>

          <div className="px-5 py-3 border-t border-slate-200 flex justify-end gap-2 bg-slate-50 rounded-b-lg">
            <button
              type="button"
              onClick={handleClose}
              disabled={submitBusy}
              className="px-3 py-1.5 text-sm rounded text-slate-700 hover:bg-slate-200 disabled:opacity-40"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={submit}
              disabled={submitDisabled}
              className="px-3 py-1.5 text-sm rounded bg-sky-600 hover:bg-sky-500 text-white disabled:opacity-40 disabled:cursor-not-allowed"
            >
              {submitBusy ? 'Creating…' : 'Create POI'}
            </button>
          </div>
        </DialogPanel>
      </div>
    </Dialog>
  )
}
