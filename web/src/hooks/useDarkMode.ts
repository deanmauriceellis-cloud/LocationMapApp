import { useState, useEffect, useCallback } from 'react'

export function useDarkMode() {
  const [dark, setDark] = useState(() => {
    const saved = localStorage.getItem('darkMode')
    return saved ? saved === 'true' : false
  })

  useEffect(() => {
    localStorage.setItem('darkMode', String(dark))
    document.documentElement.classList.toggle('dark', dark)
  }, [dark])

  const toggle = useCallback(() => setDark(d => !d), [])

  return { dark, toggle }
}
