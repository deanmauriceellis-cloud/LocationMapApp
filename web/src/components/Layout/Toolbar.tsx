interface Props {
  dark: boolean
  onToggleDark: () => void
  findOpen?: boolean
  onToggleFind?: () => void
}

export function Toolbar({ dark, onToggleDark, findOpen, onToggleFind }: Props) {
  return (
    <div className="absolute top-0 left-0 right-0 z-[1000] h-12 bg-white/90 dark:bg-gray-900/90 backdrop-blur-sm border-b border-gray-200 dark:border-gray-700 flex items-center px-3 gap-1">
      <span className="font-semibold text-sm text-gray-800 dark:text-gray-100 mr-auto">
        LocationMapApp
      </span>

      {onToggleFind && (
        <ToolbarButton title="Find" onClick={onToggleFind} active={findOpen}>
          <svg viewBox="0 0 24 24" className="w-5 h-5" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8" />
            <path d="m21 21-4.35-4.35" strokeLinecap="round" />
          </svg>
        </ToolbarButton>
      )}

      <ToolbarButton title="Dark Mode" onClick={onToggleDark}>
        {dark ? (
          <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor">
            <path d="M12 3a9 9 0 109 9c0-.46-.04-.92-.1-1.36a5.39 5.39 0 01-4.4 2.26 5.4 5.4 0 01-3.14-9.8A9 9 0 0012 3z" />
          </svg>
        ) : (
          <svg viewBox="0 0 24 24" className="w-5 h-5" fill="currentColor">
            <circle cx="12" cy="12" r="5" />
            <path d="M12 1v2m0 18v2M4.22 4.22l1.42 1.42m12.72 12.72l1.42 1.42M1 12h2m18 0h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
          </svg>
        )}
      </ToolbarButton>
    </div>
  )
}

function ToolbarButton({ children, title, onClick, active }: { children: React.ReactNode; title: string; onClick: () => void; active?: boolean }) {
  return (
    <button
      onClick={onClick}
      title={title}
      className={`w-9 h-9 rounded-lg flex items-center justify-center transition-colors ${
        active
          ? 'bg-teal-100 dark:bg-teal-900/50 text-teal-700 dark:text-teal-300'
          : 'text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800'
      }`}
    >
      {children}
    </button>
  )
}
