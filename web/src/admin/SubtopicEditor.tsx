// SubtopicEditor — S219
//
// Structured editor for the narration_subtopics JSONB array on salem_pois.
// Each subtopic = { header, body, source_kind?, source_ref? }.
//
// The editor receives the current value as a stringified JSON array (the
// form layer in PoiEditDialog stores all JSONB fields as strings — see
// poiAdminFields.ts JSONB_FIELDS), parses it once, and re-serializes on every
// edit so the parent form stays in sync with the canonical string.
//
// HARD RULE (feedback_narration_storytelling_with_subtopics): bodies are
// story-shaped prose, not bullet facts. Word-count badge nudges the operator
// toward 50–250 words per body.

import { useCallback, useEffect, useMemo, useState } from 'react'

const SOURCE_KINDS = [
  { value: '', label: '— none —' },
  { value: 'figure', label: 'figure' },
  { value: 'event', label: 'event' },
  { value: 'fact', label: 'fact' },
  { value: 'source', label: 'primary source' },
  { value: 'adjacent_poi', label: 'adjacent POI' },
  { value: 'manual', label: 'manual / authored' },
] as const

interface Subtopic {
  header: string
  body: string
  source_kind?: string
  source_ref?: string
}

interface SubtopicEditorProps {
  value: string // stringified JSON array (matches form-layer convention)
  onChange: (next: string) => void
  disabled?: boolean
}

function parseValue(value: string): { items: Subtopic[]; parseError: string | null } {
  const trimmed = (value ?? '').trim()
  if (trimmed === '' || trimmed === '[]' || trimmed === 'null') {
    return { items: [], parseError: null }
  }
  try {
    const parsed = JSON.parse(trimmed)
    if (!Array.isArray(parsed)) {
      return { items: [], parseError: 'Stored value is not a JSON array.' }
    }
    const items = parsed.map((row): Subtopic => {
      if (typeof row !== 'object' || row === null) {
        return { header: '', body: '' }
      }
      const r = row as Record<string, unknown>
      return {
        header: typeof r.header === 'string' ? r.header : '',
        body: typeof r.body === 'string' ? r.body : '',
        source_kind: typeof r.source_kind === 'string' ? r.source_kind : undefined,
        source_ref: typeof r.source_ref === 'string' ? r.source_ref : undefined,
      }
    })
    return { items, parseError: null }
  } catch (e) {
    return { items: [], parseError: e instanceof Error ? e.message : String(e) }
  }
}

function serializeItems(items: Subtopic[]): string {
  // Drop empty optional fields so the JSON stays clean.
  const cleaned = items.map((s) => {
    const out: Subtopic = { header: s.header, body: s.body }
    if (s.source_kind && s.source_kind.trim() !== '') out.source_kind = s.source_kind
    if (s.source_ref && s.source_ref.trim() !== '') out.source_ref = s.source_ref
    return out
  })
  return JSON.stringify(cleaned, null, 2)
}

function wordCount(text: string): number {
  const t = (text ?? '').trim()
  if (t === '') return 0
  return t.split(/\s+/).length
}

export default function SubtopicEditor({ value, onChange, disabled }: SubtopicEditorProps) {
  // Keep local state for fluent editing; sync to parent via onChange.
  const initial = useMemo(() => parseValue(value), [value])
  const [items, setItems] = useState<Subtopic[]>(initial.items)
  const [parseError, setParseError] = useState<string | null>(initial.parseError)
  const [collapsed, setCollapsed] = useState<Record<number, boolean>>({})

  // If the parent value changes from outside (e.g., reset, refetch), re-sync.
  useEffect(() => {
    const next = parseValue(value)
    // Only refresh if the parent's serialized value differs from ours — avoids
    // losing focus mid-edit when the parent re-emits the same string.
    if (serializeItems(next.items) !== serializeItems(items)) {
      setItems(next.items)
      setParseError(next.parseError)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value])

  const emit = useCallback(
    (next: Subtopic[]) => {
      setItems(next)
      onChange(serializeItems(next))
    },
    [onChange],
  )

  const updateAt = useCallback(
    (index: number, patch: Partial<Subtopic>) => {
      const next = items.map((s, i) => (i === index ? { ...s, ...patch } : s))
      emit(next)
    },
    [items, emit],
  )

  const addNew = useCallback(() => {
    const next = [...items, { header: '', body: '', source_kind: 'manual' }]
    emit(next)
  }, [items, emit])

  const removeAt = useCallback(
    (index: number) => {
      const next = items.filter((_, i) => i !== index)
      emit(next)
    },
    [items, emit],
  )

  const moveBy = useCallback(
    (index: number, delta: number) => {
      const target = index + delta
      if (target < 0 || target >= items.length) return
      const next = [...items]
      const [row] = next.splice(index, 1)
      next.splice(target, 0, row)
      emit(next)
    },
    [items, emit],
  )

  const totalWords = items.reduce((acc, s) => acc + wordCount(s.body), 0)

  return (
    <div className="space-y-2 border border-slate-300 rounded p-2 bg-slate-50">
      <div className="flex items-center justify-between text-xs text-slate-700">
        <div>
          <strong>Subtopics</strong> — overflow facts as scrollable cards on the POI detail sheet.
          Story-shaped bodies, not bullet facts. ({items.length} card{items.length === 1 ? '' : 's'},{' '}
          {totalWords} body words total)
        </div>
        <button
          type="button"
          onClick={addNew}
          disabled={disabled}
          className="px-2 py-0.5 bg-fuchsia-600 hover:bg-fuchsia-700 text-white rounded text-xs font-medium disabled:opacity-40"
        >
          + Add subtopic
        </button>
      </div>

      {parseError && (
        <div className="text-xs text-rose-700 bg-rose-50 border border-rose-300 rounded px-2 py-1">
          Could not parse stored value as JSON array: {parseError}. Editor started empty; saving
          will overwrite the malformed value.
        </div>
      )}

      {items.length === 0 && (
        <div className="text-xs text-slate-500 italic px-2 py-3">
          No subtopics yet. Use <em>+ Add subtopic</em> to add a header and a story-shaped body.
        </div>
      )}

      {items.map((sub, index) => {
        const wc = wordCount(sub.body)
        const wcColor =
          wc < 30 ? 'text-amber-700' : wc > 300 ? 'text-amber-700' : 'text-slate-500'
        const isCollapsed = collapsed[index] ?? false
        return (
          <div
            key={index}
            className="border border-slate-300 bg-white rounded p-2 space-y-1.5"
          >
            <div className="flex items-center gap-1">
              <span className="text-xs text-slate-400 w-6 text-center">{index + 1}.</span>
              <input
                type="text"
                value={sub.header}
                onChange={(e) => updateAt(index, { header: e.target.value })}
                placeholder="Topic header — becomes the chip label (≤ 80 chars)"
                maxLength={80}
                disabled={disabled}
                className="flex-1 px-2 py-1 text-sm border border-slate-300 rounded font-medium"
              />
              <button
                type="button"
                onClick={() => moveBy(index, -1)}
                disabled={disabled || index === 0}
                title="Move up"
                className="px-1.5 py-1 text-xs bg-slate-200 hover:bg-slate-300 rounded disabled:opacity-30"
              >
                ↑
              </button>
              <button
                type="button"
                onClick={() => moveBy(index, 1)}
                disabled={disabled || index === items.length - 1}
                title="Move down"
                className="px-1.5 py-1 text-xs bg-slate-200 hover:bg-slate-300 rounded disabled:opacity-30"
              >
                ↓
              </button>
              <button
                type="button"
                onClick={() =>
                  setCollapsed((prev) => ({ ...prev, [index]: !isCollapsed }))
                }
                title={isCollapsed ? 'Expand' : 'Collapse'}
                className="px-1.5 py-1 text-xs bg-slate-200 hover:bg-slate-300 rounded"
              >
                {isCollapsed ? '▸' : '▾'}
              </button>
              <button
                type="button"
                onClick={() => {
                  if (window.confirm(`Delete subtopic "${sub.header || '(untitled)'}"?`)) {
                    removeAt(index)
                  }
                }}
                disabled={disabled}
                title="Delete"
                className="px-1.5 py-1 text-xs bg-rose-100 hover:bg-rose-200 text-rose-700 rounded disabled:opacity-30"
              >
                ✕
              </button>
            </div>

            {!isCollapsed && (
              <>
                <div>
                  <textarea
                    value={sub.body}
                    onChange={(e) => updateAt(index, { body: e.target.value })}
                    placeholder="Story-shaped expansion — 50–250 words. Warm voice, embedded facts. No bullet lists."
                    rows={4}
                    disabled={disabled}
                    className="w-full px-2 py-1 text-sm border border-slate-300 rounded"
                  />
                  <div className={`text-xs mt-0.5 ${wcColor}`}>
                    {wc} word{wc === 1 ? '' : 's'}
                    {wc < 30 && wc > 0 && ' — quite short, expand the story'}
                    {wc > 300 && ' — over 300 words, consider splitting into two subtopics'}
                  </div>
                </div>

                <div className="flex items-center gap-1 text-xs">
                  <label className="text-slate-600">Source:</label>
                  <select
                    value={sub.source_kind ?? ''}
                    onChange={(e) =>
                      updateAt(index, { source_kind: e.target.value || undefined })
                    }
                    disabled={disabled}
                    className="px-1 py-0.5 border border-slate-300 rounded"
                  >
                    {SOURCE_KINDS.map((sk) => (
                      <option key={sk.value} value={sk.value}>
                        {sk.label}
                      </option>
                    ))}
                  </select>
                  <input
                    type="text"
                    value={sub.source_ref ?? ''}
                    onChange={(e) =>
                      updateAt(index, { source_ref: e.target.value || undefined })
                    }
                    placeholder="ref id (e.g. john_hathorne)"
                    disabled={disabled || !sub.source_kind}
                    className="flex-1 px-1.5 py-0.5 border border-slate-300 rounded font-mono disabled:opacity-50 disabled:bg-slate-100"
                  />
                </div>
              </>
            )}
          </div>
        )
      })}
    </div>
  )
}
