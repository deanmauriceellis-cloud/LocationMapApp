// Salem Oracle / SalemIntelligence variant picker (S194).
//
// Operator opens this from PoiEditDialog. It generates up to 15 candidate
// narrations — 5 short, 5 long, 5 historic — by firing parallel chat calls
// at SalemIntelligence (oracleClient.ask) with different style prompts.
// Operator clicks one card per type to copy that text into the form field.
//
// "Smart enough to know if it's not historic": the historic column is
// hidden when the POI is not HISTORICAL_BUILDINGS and lacks a witch-trial
// signal in its name.
//
// Historic variants run through historicalNarrationValidator. Variants
// that hit a critical pattern are shown disabled with the reason — the
// operator can still see them but can't accidentally pick a refusal /
// modern-leak / subject-absent generation.

import { Fragment, useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { Dialog, DialogPanel, DialogTitle, Transition, TransitionChild } from '@headlessui/react'
import { ask as oracleAsk, isAskOk } from './oracleClient'
import { validateHistoricalNarration } from './historicalNarrationValidator'

type FieldKey = 'short_narration' | 'long_narration' | 'historical_narration'

interface VariantSpec {
  id: string
  field: FieldKey
  label: string
  styleHint: string
  prompt: string
}

interface VariantState {
  spec: VariantSpec
  status: 'pending' | 'running' | 'done' | 'error' | 'invalid'
  text?: string
  error?: string
  invalidReasons?: string[]
}

export interface OracleVariantPickerProps {
  open: boolean
  poi: {
    id: string
    name: string
    category?: string
    year_established?: number | null
    address?: string | null
  } | null
  onSelect: (field: FieldKey, text: string) => void
  onClose: () => void
}

// Heuristic: is this POI eligible for the historic column?
function isHistoricEligible(poi: OracleVariantPickerProps['poi']): boolean {
  if (!poi) return false
  if (poi.category === 'HISTORICAL_BUILDINGS') return true
  // Witch-trial signal in the name → eligible regardless of category.
  return /\b(witch|coven|magistrate|gallows|examination|accuser|accused|hathorne|corwin|bishop|nurse|proctor|tituba)\b/i.test(
    poi.name ?? '',
  )
}

function buildVariantSpecs(
  poi: NonNullable<OracleVariantPickerProps['poi']>,
  includeHistoric: boolean,
): VariantSpec[] {
  const namePart = poi.name ?? '(unnamed POI)'
  const addressPart = poi.address ? ` at ${poi.address}` : ''
  const yearKnown = poi.year_established ? ` (established ${poi.year_established})` : ''
  const subject = `"${namePart}"${addressPart}${yearKnown} in Salem, Massachusetts`

  const SHORT: Array<[string, string, string]> = [
    [
      'factual',
      'Plain facts',
      `Write a concise, factual short narration for ${subject}. State what it is, when it was established, and one or two notable historical facts. 60-120 words. Plain declarative sentences. No filler, no hedging, no "records indicate" or "may have been". Begin with the subject's name.`,
    ],
    [
      'orientational',
      'Tour-guide voice',
      `Write a tour-guide-style short narration for ${subject}, oriented to a visitor standing here today. What they are looking at, how to recognize it, why it matters. 60-120 words. Authoritative, specific. Begin with the subject's name.`,
    ],
    [
      'concise',
      'Punchy lead',
      `Write a tight 60-80 word short narration for ${subject}. Lead with the single most distinctive fact about it — the one a visitor would most want to know. No throat-clearing. Begin with the subject's name.`,
    ],
    [
      'sensory',
      'Sensory anchor',
      `Write a sensory-rich short narration for ${subject} — what a visitor sees, hears, or notices first when standing here — anchored to one or two concrete historical details. 80-120 words. Begin with the subject's name.`,
    ],
    [
      'human-interest',
      'People-first',
      `Write a short narration for ${subject} centered on the people most associated with it — owners, builders, occupants, visitors. 80-120 words. Specific names where possible. Begin with the subject's name.`,
    ],
  ]

  const LONG: Array<[string, string, string]> = [
    [
      'scholarly',
      'Scholarly',
      `Write a historically-grounded long narration for ${subject}. Cite established events, dates, people, and architectural facts associated with it. 200-300 words. Authoritative scholarly tone. No hedging language. Begin with the subject's name.`,
    ],
    [
      'atmospheric',
      'Atmospheric',
      `Write an atmospheric long narration for ${subject} that captures what this place felt like across its history — sensory + historical concrete details. 250-350 words. Begin with the subject's name.`,
    ],
    [
      'story',
      'Story-driven',
      `Write a long narration for ${subject} structured as a brief story — choose one defining episode in its history and tell it in narrative form. 200-300 words. Begin with the subject's name.`,
    ],
    [
      'chronological',
      'Chronological',
      `Write a long narration tracing the history of ${subject} chronologically, from establishment forward. Date markers throughout. 250-350 words. Begin with the subject's name.`,
    ],
    [
      'human-interest',
      'People-driven',
      `Write a long narration for ${subject} centered on the people connected to it — original owner, builder, notable family, professional or institutional uses. 200-300 words. Specific names where possible. Begin with the subject's name.`,
    ],
  ]

  const PRE1860_RULES = [
    `Write a strictly pre-1860 historical narration for ${subject}.`,
    `Use ONLY verifiable historical facts you have specific knowledge of about this site. If you do not have specific historical knowledge of this site beyond its name and location, output the literal string EMPTY and nothing else.`,
    `Speak with authority. Do NOT use hedging language: no "records indicate", "it is noted", "thought to have", "appears to have", "may have been", "is understood to be", "likely", "perhaps", "possibly", "suggesting".`,
    `Do NOT use formulaic filler: no "stands as a testament", "speaks to", "fabric of the town", "enduring spirit", "those who first raised its walls", "building traditions", "in proximity to", "shadow of those proceedings".`,
    `Do NOT mention modern designations or post-1860 events: no NRHP, National Register, National Historic Landmark, historic district, museum operations, visitors, admission, today, currently, presently, "now operates", century names ("19th century", "nineteenth century", etc.).`,
    `Do NOT reference yourself, your memory, the prompt, the model, or "the records I possess".`,
    `Temporal framing: pre-1776 use "Massachusetts Bay Colony" or "the colony"; 1776-1779 use "the colony"; 1780+ use "Commonwealth of Massachusetts". Pre-1776 do NOT use "United States" or "America". Pre-1780 do NOT use "Commonwealth of Massachusetts".`,
    `Begin the narration with the subject's name as the first noun phrase.`,
  ].join(' ')

  const HISTORIC: Array<[string, string, string]> = [
    [
      'factual',
      'Strict facts (pre-1860)',
      `${PRE1860_RULES} Focus on verifiable facts: year, original owner, builder, architect if known, distinctive features. 80-200 words.`,
    ],
    [
      'witch-trial',
      'Witch-trial context',
      `${PRE1860_RULES} Emphasize this site's connections to the 1692 Salem witch trials specifically — accused, accusers, magistrates, examinations, executions, named figures connected to the trials and how they relate to this place. If no such connection is documented, output EMPTY. 80-200 words.`,
    ],
    [
      'occupants',
      'People who lived/worked here',
      `${PRE1860_RULES} Center the narration on the people who lived or worked at this site before 1860 — original owner, builder, notable family, professional use. Specific names. 80-200 words.`,
    ],
    [
      'architectural',
      'Architectural detail',
      `${PRE1860_RULES} Center the narration on the building itself — period style, construction date, materials, builders, architect, distinctive features that survive from before 1860. 80-200 words.`,
    ],
    [
      'narrative-arc',
      'Brief narrative arc',
      `${PRE1860_RULES} Use a brief narrative arc: establishment → defining pre-1860 episode or change → status as of 1859. 100-220 words.`,
    ],
  ]

  const specs: VariantSpec[] = []
  for (const [id, label, prompt] of SHORT) {
    specs.push({ id: `short:${id}`, field: 'short_narration', label, styleHint: id, prompt })
  }
  for (const [id, label, prompt] of LONG) {
    specs.push({ id: `long:${id}`, field: 'long_narration', label, styleHint: id, prompt })
  }
  if (includeHistoric) {
    for (const [id, label, prompt] of HISTORIC) {
      specs.push({ id: `historic:${id}`, field: 'historical_narration', label, styleHint: id, prompt })
    }
  }
  return specs
}

const CONCURRENCY = 3

export function OracleVariantPicker({ open, poi, onSelect, onClose }: OracleVariantPickerProps) {
  const includeHistoric = useMemo(() => isHistoricEligible(poi), [poi])
  const [variants, setVariants] = useState<VariantState[]>([])
  const cancelledRef = useRef(false)

  // Initialise variants when the picker opens. Reset on close so reopening
  // re-runs generation against the current POI.
  useEffect(() => {
    if (!open || !poi) return
    cancelledRef.current = false
    const specs = buildVariantSpecs(poi, includeHistoric)
    setVariants(specs.map((spec) => ({ spec, status: 'pending' })))
    return () => {
      cancelledRef.current = true
    }
  }, [open, poi, includeHistoric])

  // Run pending variants with bounded concurrency. Each completion
  // schedules the next pending one.
  useEffect(() => {
    if (!open || !poi) return
    let active = 0

    function runOne(idx: number) {
      active++
      setVariants((prev) => {
        const next = [...prev]
        next[idx] = { ...next[idx], status: 'running' }
        return next
      })
      const spec = variants[idx].spec
      const poiIdAtRunTime = poi?.id
      void oracleAsk({ question: spec.prompt, current_poi_id: poiIdAtRunTime })
        .then((res) => {
          if (cancelledRef.current) return
          if (!isAskOk(res)) {
            setVariants((prev) => {
              const next = [...prev]
              next[idx] = { ...next[idx], status: 'error', error: res.error }
              return next
            })
            return
          }
          let status: VariantState['status'] = 'done'
          let invalidReasons: string[] | undefined
          let text = (res.text ?? '').trim()

          // Strip surrounding quotes / triple-backtick fences gemma sometimes
          // wraps responses in.
          text = text.replace(/^```[a-z]*\s*/i, '').replace(/\s*```$/, '').trim()
          if (text === 'EMPTY' || text.length === 0) {
            status = 'invalid'
            invalidReasons = ['model returned EMPTY (no grounded knowledge of this site)']
          } else if (spec.field === 'historical_narration') {
            const v = validateHistoricalNarration(text, poi?.name ?? '')
            if (v.critical.length > 0) {
              status = 'invalid'
              invalidReasons = v.critical
            }
          }
          setVariants((prev) => {
            const next = [...prev]
            next[idx] = { ...next[idx], status, text, invalidReasons }
            return next
          })
        })
        .catch((e: unknown) => {
          if (cancelledRef.current) return
          setVariants((prev) => {
            const next = [...prev]
            next[idx] = {
              ...next[idx],
              status: 'error',
              error: e instanceof Error ? e.message : String(e),
            }
            return next
          })
        })
        .finally(() => {
          active--
          // Schedule the next pending variant if any.
          if (cancelledRef.current) return
          setVariants((cur) => {
            const nextIdx = cur.findIndex((v) => v.status === 'pending')
            if (nextIdx >= 0 && active < CONCURRENCY) {
              // Defer to escape the setState batch so runOne reads the
              // updated variants array on its next read.
              queueMicrotask(() => runOne(nextIdx))
            }
            return cur
          })
        })
    }

    // Kick off up to CONCURRENCY initial workers.
    const pendingIndices = variants
      .map((v, i) => (v.status === 'pending' ? i : -1))
      .filter((i) => i >= 0)
      .slice(0, CONCURRENCY)
    for (const i of pendingIndices) runOne(i)

    return () => {
      cancelledRef.current = true
    }
    // We intentionally only re-run when the variants array identity changes
    // (i.e. a new picker opening with new specs). The ref guards against
    // late completions firing after close.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [variants.length === 0 ? null : variants[0]?.spec.id])

  const handlePick = useCallback(
    (v: VariantState) => {
      if (v.status !== 'done' || !v.text) return
      onSelect(v.spec.field, v.text)
      onClose()
    },
    [onSelect, onClose],
  )

  const groups: Record<FieldKey, VariantState[]> = {
    short_narration: variants.filter((v) => v.spec.field === 'short_narration'),
    long_narration: variants.filter((v) => v.spec.field === 'long_narration'),
    historical_narration: variants.filter((v) => v.spec.field === 'historical_narration'),
  }

  return (
    <Transition show={open} as={Fragment}>
      <Dialog onClose={onClose} className="relative z-[1100]">
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
            <DialogPanel className="w-[1280px] max-w-[98vw] max-h-[92vh] flex flex-col bg-white rounded-lg shadow-xl text-slate-800">
              <div className="flex items-center justify-between px-4 py-3 border-b border-slate-200">
                <div className="min-w-0">
                  <DialogTitle className="text-base font-semibold truncate">
                    Generate narration variants — {poi?.name}
                  </DialogTitle>
                  <p className="text-xs text-slate-500">
                    5 short · 5 long{includeHistoric ? ' · 5 historic (pre-1860)' : ' · historic skipped (POI not flagged historical)'}.
                    Click a card to copy it into the form. Variants generate via SalemIntelligence on the workstation GPU — expect ~10s each.
                  </p>
                </div>
                <button
                  type="button"
                  onClick={onClose}
                  className="text-slate-500 hover:text-slate-800 px-2 py-1 rounded text-sm"
                >
                  Close
                </button>
              </div>

              <div className="flex-1 overflow-auto p-4">
                <div className={`grid gap-4 ${includeHistoric ? 'grid-cols-3' : 'grid-cols-2'}`}>
                  <Column title="Short narration" group={groups.short_narration} onPick={handlePick} />
                  <Column title="Long narration" group={groups.long_narration} onPick={handlePick} />
                  {includeHistoric && (
                    <Column
                      title="Historical narration (pre-1860)"
                      group={groups.historical_narration}
                      onPick={handlePick}
                    />
                  )}
                </div>
              </div>
            </DialogPanel>
          </TransitionChild>
        </div>
      </Dialog>
    </Transition>
  )
}

function Column({
  title,
  group,
  onPick,
}: {
  title: string
  group: VariantState[]
  onPick: (v: VariantState) => void
}) {
  return (
    <div className="flex flex-col gap-2">
      <div className="text-xs font-semibold uppercase tracking-wide text-slate-600 px-1">{title}</div>
      {group.map((v) => (
        <Card key={v.spec.id} v={v} onPick={onPick} />
      ))}
    </div>
  )
}

function Card({ v, onPick }: { v: VariantState; onPick: (v: VariantState) => void }) {
  const statusClasses: Record<VariantState['status'], string> = {
    pending: 'border-slate-200 bg-slate-50',
    running: 'border-sky-300 bg-sky-50 animate-pulse',
    done: 'border-emerald-300 bg-white hover:bg-emerald-50',
    invalid: 'border-amber-300 bg-amber-50 opacity-70',
    error: 'border-rose-300 bg-rose-50 opacity-70',
  }
  const isPickable = v.status === 'done'
  return (
    <button
      type="button"
      onClick={() => isPickable && onPick(v)}
      disabled={!isPickable}
      className={`text-left text-xs border rounded p-2 transition ${statusClasses[v.status]} ${isPickable ? 'cursor-pointer' : 'cursor-default'}`}
    >
      <div className="flex items-center justify-between mb-1">
        <span className="font-semibold text-slate-700">{v.spec.label}</span>
        <span className="text-[10px] text-slate-400">{v.spec.styleHint}</span>
      </div>
      {v.status === 'pending' && <div className="text-slate-400">Queued…</div>}
      {v.status === 'running' && <div className="text-sky-700">Generating…</div>}
      {v.status === 'done' && v.text && (
        <div className="whitespace-pre-wrap break-words leading-snug text-slate-700">
          {v.text}
        </div>
      )}
      {v.status === 'invalid' && (
        <>
          <div className="text-amber-800 font-semibold mb-1">
            Rejected by validator — not selectable
          </div>
          {v.invalidReasons && (
            <ul className="text-amber-800 list-disc pl-4 space-y-0.5">
              {v.invalidReasons.slice(0, 4).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}
          {v.text && (
            <details className="mt-2">
              <summary className="cursor-pointer text-slate-500">Show text anyway</summary>
              <div className="whitespace-pre-wrap break-words leading-snug text-slate-600 mt-1">
                {v.text}
              </div>
            </details>
          )}
        </>
      )}
      {v.status === 'error' && (
        <div className="text-rose-700">Error: {v.error}</div>
      )}
    </button>
  )
}
