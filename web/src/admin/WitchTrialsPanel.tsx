/*
 * WitchTrialsPanel — Phase 9X.7 (Session 133)
 * Copyright (c) 2026 Dean Maurice Ellis. All rights reserved.
 *
 * Admin panel for browsing and editing Witch Trials content: articles (16),
 * NPC bios (49), and newspapers (202). Three-tab layout with list + inline
 * edit dialog.
 */

import { useCallback, useEffect, useState } from 'react'

// ─── Types ──────────────────────────────────────────────────────────────────

interface WtArticle {
  id: string
  tile_order: number
  tile_kind: string
  title: string
  period_label: string | null
  teaser: string
  body: string
  related_npc_ids: string
  related_event_ids: string
  related_newspaper_dates: string
  data_source: string
  confidence: number
  verified_date: string | null
  generator_model: string | null
}

interface WtBio {
  id: string
  name: string
  display_name: string | null
  tier: number
  role: string
  faction: string | null
  born_year: number | null
  died_year: number | null
  age_in_1692: number | null
  historical_outcome: string | null
  bio: string
  related_npc_ids: string
  related_event_ids: string
  related_newspaper_dates: string
  portrait_asset: string | null
  data_source: string
  confidence: number
  verified_date: string | null
  generator_model: string | null
}

interface WtNewspaper {
  id: string
  date: string
  day_of_week: string | null
  long_date: string | null
  crisis_phase: number
  summary: string | null
  lede: string | null
  tts_full_text: string
  headline: string | null
  headline_summary: string | null
  data_source: string
  confidence: number
  verified_date: string | null
  generator_model: string | null
}

type TabKey = 'articles' | 'bios' | 'newspapers'

const API_BASE = '/api/admin/salem/witch-trials'

// ─── Component ──────────────────────────────────────────────────────────────

export function WitchTrialsPanel() {
  const [tab, setTab] = useState<TabKey>('articles')
  const [articles, setArticles] = useState<WtArticle[]>([])
  const [bios, setBios] = useState<WtBio[]>([])
  const [newspapers, setNewspapers] = useState<WtNewspaper[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Edit state
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editFields, setEditFields] = useState<Record<string, string>>({})
  const [saving, setSaving] = useState(false)

  const fetchTab = useCallback(async (t: TabKey) => {
    setLoading(true)
    setError(null)
    try {
      const res = await fetch(`${API_BASE}/${t}`)
      if (!res.ok) throw new Error(`${res.status} ${res.statusText}`)
      const data = await res.json()
      if (t === 'articles') setArticles(data.articles)
      else if (t === 'bios') setBios(data.bios)
      else setNewspapers(data.newspapers)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    void fetchTab(tab)
  }, [tab, fetchTab])

  const handleEdit = (id: string, fields: Record<string, string>) => {
    setEditingId(id)
    setEditFields(fields)
  }

  const handleSave = async () => {
    if (!editingId) return
    setSaving(true)
    try {
      const res = await fetch(`${API_BASE}/${tab}/${editingId}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(editFields),
      })
      if (!res.ok) {
        const err = await res.json()
        throw new Error(err.error || `${res.status}`)
      }
      setEditingId(null)
      void fetchTab(tab)
    } catch (e) {
      setError(e instanceof Error ? e.message : String(e))
    } finally {
      setSaving(false)
    }
  }

  const handleCancel = () => {
    setEditingId(null)
    setEditFields({})
  }

  return (
    <div className="flex flex-col h-full bg-white">
      {/* Tab bar */}
      <div className="flex gap-1 px-3 py-2 border-b border-slate-200 bg-slate-50">
        {(['articles', 'bios', 'newspapers'] as TabKey[]).map((t) => (
          <button
            key={t}
            onClick={() => { setEditingId(null); setTab(t) }}
            className={`px-3 py-1.5 text-sm rounded font-medium transition-colors ${
              tab === t
                ? 'bg-indigo-600 text-white'
                : 'bg-slate-200 text-slate-700 hover:bg-slate-300'
            }`}
          >
            {t === 'articles' ? `Articles (${articles.length || '…'})` :
             t === 'bios' ? `Bios (${bios.length || '…'})` :
             `Newspapers (${newspapers.length || '…'})`}
          </button>
        ))}
      </div>

      {error && (
        <div className="px-3 py-2 bg-red-50 text-red-700 text-sm border-b border-red-200">
          {error}
          <button className="ml-2 underline" onClick={() => setError(null)}>dismiss</button>
        </div>
      )}

      {loading ? (
        <div className="flex-1 flex items-center justify-center text-slate-400">Loading…</div>
      ) : (
        <div className="flex-1 overflow-auto min-h-0">
          {tab === 'articles' && (
            <ArticleList
              articles={articles}
              editingId={editingId}
              editFields={editFields}
              onEdit={handleEdit}
              onFieldChange={(k, v) => setEditFields((p) => ({ ...p, [k]: v }))}
              onSave={handleSave}
              onCancel={handleCancel}
              saving={saving}
            />
          )}
          {tab === 'bios' && (
            <BioList
              bios={bios}
              editingId={editingId}
              editFields={editFields}
              onEdit={handleEdit}
              onFieldChange={(k, v) => setEditFields((p) => ({ ...p, [k]: v }))}
              onSave={handleSave}
              onCancel={handleCancel}
              saving={saving}
            />
          )}
          {tab === 'newspapers' && (
            <NewspaperList
              newspapers={newspapers}
              editingId={editingId}
              editFields={editFields}
              onEdit={handleEdit}
              onFieldChange={(k, v) => setEditFields((p) => ({ ...p, [k]: v }))}
              onSave={handleSave}
              onCancel={handleCancel}
              saving={saving}
            />
          )}
        </div>
      )}
    </div>
  )
}

// ─── Shared edit row component ──────────────────────────────────────────────

interface EditRowProps {
  label: string
  fieldKey: string
  value: string
  multiline?: boolean
  onChange: (key: string, value: string) => void
}

function EditRow({ label, fieldKey, value, multiline, onChange }: EditRowProps) {
  return (
    <div className="flex flex-col gap-1 py-1">
      <label className="text-xs font-semibold text-slate-500 uppercase tracking-wide">{label}</label>
      {multiline ? (
        <textarea
          className="w-full border border-slate-300 rounded px-2 py-1 text-sm font-mono min-h-[120px] resize-y"
          value={value}
          onChange={(e) => onChange(fieldKey, e.target.value)}
        />
      ) : (
        <input
          className="w-full border border-slate-300 rounded px-2 py-1 text-sm"
          value={value}
          onChange={(e) => onChange(fieldKey, e.target.value)}
        />
      )}
    </div>
  )
}

function SaveCancelBar({ onSave, onCancel, saving }: { onSave: () => void; onCancel: () => void; saving: boolean }) {
  return (
    <div className="flex gap-2 py-2">
      <button
        onClick={onSave}
        disabled={saving}
        className="px-4 py-1.5 text-sm rounded bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-50"
      >
        {saving ? 'Saving…' : 'Save'}
      </button>
      <button
        onClick={onCancel}
        className="px-4 py-1.5 text-sm rounded bg-slate-200 text-slate-700 hover:bg-slate-300"
      >
        Cancel
      </button>
    </div>
  )
}

// ─── Article list ───────────────────────────────────────────────────────────

interface ListProps<T> {
  editingId: string | null
  editFields: Record<string, string>
  onEdit: (id: string, fields: Record<string, string>) => void
  onFieldChange: (key: string, value: string) => void
  onSave: () => void
  onCancel: () => void
  saving: boolean
}

function ArticleList({
  articles, editingId, editFields, onEdit, onFieldChange, onSave, onCancel, saving,
}: ListProps<WtArticle> & { articles: WtArticle[] }) {
  return (
    <table className="w-full text-sm">
      <thead className="bg-slate-50 sticky top-0 z-10">
        <tr>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">#</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Title</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Period</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Body</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Source</th>
          <th className="px-3 py-2"></th>
        </tr>
      </thead>
      <tbody>
        {articles.map((a) => (
          <tr key={a.id} className={`border-b border-slate-100 ${editingId === a.id ? 'bg-indigo-50' : 'hover:bg-slate-50'}`}>
            {editingId === a.id ? (
              <td colSpan={6} className="px-3 py-2">
                <div className="text-xs font-semibold text-indigo-600 mb-2">Editing: {a.id} (tile #{a.tile_order})</div>
                <EditRow label="Title" fieldKey="title" value={editFields.title ?? ''} onChange={onFieldChange} />
                <EditRow label="Period Label" fieldKey="period_label" value={editFields.period_label ?? ''} onChange={onFieldChange} />
                <EditRow label="Teaser" fieldKey="teaser" value={editFields.teaser ?? ''} multiline onChange={onFieldChange} />
                <EditRow label="Body" fieldKey="body" value={editFields.body ?? ''} multiline onChange={onFieldChange} />
                <SaveCancelBar onSave={onSave} onCancel={onCancel} saving={saving} />
              </td>
            ) : (
              <>
                <td className="px-3 py-2 text-slate-400">{a.tile_order}</td>
                <td className="px-3 py-2 font-medium">{a.title}</td>
                <td className="px-3 py-2 text-slate-500">{a.period_label ?? '—'}</td>
                <td className="px-3 py-2 text-slate-400 truncate max-w-[300px]">{a.body.slice(0, 80)}…</td>
                <td className="px-3 py-2 text-slate-400 text-xs">{a.data_source}</td>
                <td className="px-3 py-2">
                  <button
                    onClick={() => onEdit(a.id, { title: a.title, period_label: a.period_label ?? '', teaser: a.teaser, body: a.body })}
                    className="text-indigo-600 hover:underline text-xs"
                  >
                    Edit
                  </button>
                </td>
              </>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// ─── Bio list ───────────────────────────────────────────────────────────────

function BioList({
  bios, editingId, editFields, onEdit, onFieldChange, onSave, onCancel, saving,
}: ListProps<WtBio> & { bios: WtBio[] }) {
  return (
    <table className="w-full text-sm">
      <thead className="bg-slate-50 sticky top-0 z-10">
        <tr>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Name</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Role</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Tier</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Faction</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Bio</th>
          <th className="px-3 py-2"></th>
        </tr>
      </thead>
      <tbody>
        {bios.map((b) => (
          <tr key={b.id} className={`border-b border-slate-100 ${editingId === b.id ? 'bg-indigo-50' : 'hover:bg-slate-50'}`}>
            {editingId === b.id ? (
              <td colSpan={6} className="px-3 py-2">
                <div className="text-xs font-semibold text-indigo-600 mb-2">Editing: {b.id}</div>
                <EditRow label="Name" fieldKey="name" value={editFields.name ?? ''} onChange={onFieldChange} />
                <EditRow label="Display Name" fieldKey="display_name" value={editFields.display_name ?? ''} onChange={onFieldChange} />
                <EditRow label="Role" fieldKey="role" value={editFields.role ?? ''} onChange={onFieldChange} />
                <EditRow label="Historical Outcome" fieldKey="historical_outcome" value={editFields.historical_outcome ?? ''} onChange={onFieldChange} />
                <EditRow label="Bio" fieldKey="bio" value={editFields.bio ?? ''} multiline onChange={onFieldChange} />
                <SaveCancelBar onSave={onSave} onCancel={onCancel} saving={saving} />
              </td>
            ) : (
              <>
                <td className="px-3 py-2 font-medium">{b.name}</td>
                <td className="px-3 py-2 text-slate-500">{b.role}</td>
                <td className="px-3 py-2 text-slate-400">{b.tier}</td>
                <td className="px-3 py-2 text-slate-400">{b.faction ?? '—'}</td>
                <td className="px-3 py-2 text-slate-400 truncate max-w-[300px]">{b.bio.slice(0, 80)}…</td>
                <td className="px-3 py-2">
                  <button
                    onClick={() => onEdit(b.id, {
                      name: b.name,
                      display_name: b.display_name ?? '',
                      role: b.role,
                      historical_outcome: b.historical_outcome ?? '',
                      bio: b.bio,
                    })}
                    className="text-indigo-600 hover:underline text-xs"
                  >
                    Edit
                  </button>
                </td>
              </>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  )
}

// ─── Newspaper list ─────────────────────────────────────────────────────────

const PHASE_LABELS: Record<number, string> = {
  0: 'Pre-crisis',
  1: 'Ignition',
  2: 'Accusation',
  3: 'Examinations',
  4: 'Court of O&T',
  5: 'Mass trials',
  6: 'Aftermath',
}

function NewspaperList({
  newspapers, editingId, editFields, onEdit, onFieldChange, onSave, onCancel, saving,
}: ListProps<WtNewspaper> & { newspapers: WtNewspaper[] }) {
  return (
    <table className="w-full text-sm">
      <thead className="bg-slate-50 sticky top-0 z-10">
        <tr>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Date</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Headline</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Phase</th>
          <th className="text-left px-3 py-2 text-xs font-semibold text-slate-500">Summary</th>
          <th className="px-3 py-2"></th>
        </tr>
      </thead>
      <tbody>
        {newspapers.map((n) => (
          <tr key={n.id} className={`border-b border-slate-100 ${editingId === n.id ? 'bg-indigo-50' : 'hover:bg-slate-50'}`}>
            {editingId === n.id ? (
              <td colSpan={5} className="px-3 py-2">
                <div className="text-xs font-semibold text-indigo-600 mb-2">Editing: {n.date}</div>
                <EditRow label="Headline" fieldKey="headline" value={editFields.headline ?? ''} onChange={onFieldChange} />
                <EditRow label="Headline Summary" fieldKey="headline_summary" value={editFields.headline_summary ?? ''} onChange={onFieldChange} />
                <EditRow label="Summary" fieldKey="summary" value={editFields.summary ?? ''} multiline onChange={onFieldChange} />
                <EditRow label="TTS Full Text" fieldKey="tts_full_text" value={editFields.tts_full_text ?? ''} multiline onChange={onFieldChange} />
                <SaveCancelBar onSave={onSave} onCancel={onCancel} saving={saving} />
              </td>
            ) : (
              <>
                <td className="px-3 py-2 font-mono text-slate-500">{n.date}</td>
                <td className="px-3 py-2 font-medium">{n.headline ?? '—'}</td>
                <td className="px-3 py-2 text-slate-400">{PHASE_LABELS[n.crisis_phase] ?? `Phase ${n.crisis_phase}`}</td>
                <td className="px-3 py-2 text-slate-400 truncate max-w-[300px]">{(n.headline_summary ?? n.summary ?? '').slice(0, 80)}</td>
                <td className="px-3 py-2">
                  <button
                    onClick={() => onEdit(n.id, {
                      headline: n.headline ?? '',
                      headline_summary: n.headline_summary ?? '',
                      summary: n.summary ?? '',
                      tts_full_text: n.tts_full_text,
                    })}
                    className="text-indigo-600 hover:underline text-xs"
                  >
                    Edit
                  </button>
                </td>
              </>
            )}
          </tr>
        ))}
      </tbody>
    </table>
  )
}
