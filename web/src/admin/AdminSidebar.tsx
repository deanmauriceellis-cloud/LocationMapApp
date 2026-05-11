// AdminSidebar — S231
//
// Left-rail navigation for the admin tool. Replaces the 8-button view-toggle
// strip that lived in the header. Groups views into Content / Quality /
// Tooling categories so adding new tabs (e.g. Splash Tree, Halloween Layer,
// Time Slider) doesn't crowd the header.
//
// Persists in localStorage:
//   - admin.sidebar.collapsed  : "1" | "0"
//   - admin.sidebar.groups     : JSON of { Content: bool, Quality: bool, Tooling: bool }
//
// Each row is a button; the selected view gets indigo highlight. The group
// headers are buttons that flip the section open/closed. A collapse handle
// at the bottom shrinks the rail to icon-only width.

import { useCallback, useEffect, useMemo, useState } from 'react'

export type AdminView =
  | 'pois'
  | 'tours'
  | 'witch-trials'
  | 'lint'
  | 'geocodes'
  | 'audit'
  | 'field-edits'
  | 'mass-edit'
  | 'splash-tree'

interface SidebarItem {
  id: AdminView
  label: string
  icon: string         // single-glyph emoji or unicode char (visible in collapsed mode)
  title?: string       // hover tooltip
}

interface SidebarGroup {
  name: 'Content' | 'Quality' | 'Tooling'
  items: SidebarItem[]
}

const GROUPS: SidebarGroup[] = [
  {
    name: 'Content',
    items: [
      { id: 'pois',         label: 'POIs',         icon: '📍', title: 'Salem POIs — tree + map editor' },
      { id: 'tours',        label: 'Tours',        icon: '🗺', title: 'Tours, stops, walking legs' },
      { id: 'witch-trials', label: 'Witch Trials', icon: '🪦', title: '1692 figures, articles, newspapers' },
    ],
  },
  {
    name: 'Quality',
    items: [
      { id: 'lint',         label: 'Lint',         icon: '🔍', title: 'Data-quality lint scan' },
      { id: 'geocodes',     label: 'Geocodes',     icon: '📐', title: 'Address ↔ stored coords deep verification' },
      { id: 'field-edits',  label: 'Field Edits',  icon: '📨', title: 'Field-edit inbox — atomic POI changes from the Lenovo' },
      { id: 'audit',        label: 'Audit',        icon: '📋', title: 'Change audit log — every admin/automation edit, with revert' },
    ],
  },
  {
    name: 'Tooling',
    items: [
      { id: 'splash-tree',  label: 'Splash Tree',  icon: '🌳', title: 'Splash announcement decision tree authoring' },
      { id: 'mass-edit',    label: 'Mass Edit',    icon: '📊', title: 'Bulk POI edits via .xlsx round-trip with per-cell review' },
    ],
  },
]

const LS_COLLAPSED = 'admin.sidebar.collapsed'
const LS_GROUPS    = 'admin.sidebar.groups'

interface AdminSidebarProps {
  view: AdminView
  onViewChange: (v: AdminView) => void
}

export function AdminSidebar({ view, onViewChange }: AdminSidebarProps) {
  const [collapsed, setCollapsed] = useState<boolean>(() => {
    try { return localStorage.getItem(LS_COLLAPSED) === '1' } catch { return false }
  })
  const [openGroups, setOpenGroups] = useState<Record<string, boolean>>(() => {
    try {
      const raw = localStorage.getItem(LS_GROUPS)
      if (raw) return JSON.parse(raw)
    } catch { /* fallthrough */ }
    return { Content: true, Quality: true, Tooling: true }
  })

  useEffect(() => {
    try { localStorage.setItem(LS_COLLAPSED, collapsed ? '1' : '0') } catch { /* ignore */ }
  }, [collapsed])
  useEffect(() => {
    try { localStorage.setItem(LS_GROUPS, JSON.stringify(openGroups)) } catch { /* ignore */ }
  }, [openGroups])

  const toggleGroup = useCallback((name: string) => {
    setOpenGroups(prev => ({ ...prev, [name]: !prev[name] }))
  }, [])

  // When collapsed, every item shows regardless of group expand state — the
  // group concept only matters in the expanded view as a visual divider.
  const flatItems = useMemo(
    () => GROUPS.flatMap(g => g.items),
    [],
  )

  return (
    <aside
      className={
        'shrink-0 bg-slate-900 text-slate-100 border-r border-slate-800 ' +
        'flex flex-col transition-[width] duration-150 ' +
        (collapsed ? 'w-12' : 'w-52')
      }
    >
      <nav className="flex-1 overflow-y-auto py-2">
        {collapsed ? (
          // Icon-only flat list.
          <ul className="flex flex-col gap-0.5">
            {flatItems.map(item => (
              <li key={item.id}>
                <button
                  type="button"
                  onClick={() => onViewChange(item.id)}
                  className={
                    'w-full h-9 flex items-center justify-center text-base ' +
                    (view === item.id
                      ? 'bg-indigo-600 text-white'
                      : 'text-slate-300 hover:bg-slate-800 hover:text-white')
                  }
                  title={`${item.label}${item.title ? ' — ' + item.title : ''}`}
                  aria-label={item.label}
                  aria-current={view === item.id ? 'page' : undefined}
                >
                  <span aria-hidden>{item.icon}</span>
                </button>
              </li>
            ))}
          </ul>
        ) : (
          GROUPS.map(group => (
            <div key={group.name} className="mb-1.5">
              <button
                type="button"
                onClick={() => toggleGroup(group.name)}
                className="w-full px-3 py-1 flex items-center text-[11px] uppercase tracking-wider text-slate-500 hover:text-slate-300"
              >
                <span className="flex-1 text-left">{group.name}</span>
                <span aria-hidden className="text-xs">
                  {openGroups[group.name] ? '▾' : '▸'}
                </span>
              </button>
              {openGroups[group.name] && (
                <ul className="flex flex-col">
                  {group.items.map(item => (
                    <li key={item.id}>
                      <button
                        type="button"
                        onClick={() => onViewChange(item.id)}
                        className={
                          'w-full h-8 px-3 flex items-center gap-2 text-sm transition-colors ' +
                          (view === item.id
                            ? 'bg-indigo-600 text-white'
                            : 'text-slate-300 hover:bg-slate-800 hover:text-white')
                        }
                        title={item.title}
                        aria-current={view === item.id ? 'page' : undefined}
                      >
                        <span aria-hidden className="w-5 text-center">{item.icon}</span>
                        <span className="truncate">{item.label}</span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          ))
        )}
      </nav>
      <button
        type="button"
        onClick={() => setCollapsed(c => !c)}
        className="h-8 border-t border-slate-800 text-slate-500 hover:text-slate-200 hover:bg-slate-800 text-xs flex items-center justify-center gap-1"
        title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
      >
        <span aria-hidden>{collapsed ? '»' : '«'}</span>
        {!collapsed && <span>collapse</span>}
      </button>
    </aside>
  )
}
