import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ThemeContext, type Theme } from './theme-context'

/** Shared with the inline bootstrap script in index.html. If this key
 *  changes, change it there too or the page will flash the wrong theme
 *  on first paint. */
const STORAGE_KEY = 'astrastore-theme'

function getSystemTheme(): 'light' | 'dark' {
  return window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

/**
 * The stylesheet is dark-first: `:root` holds the dark palette and the
 * `.light` class overrides it. So applying a theme means adding or
 * removing exactly one class.
 */
function applyTheme(theme: Theme): 'light' | 'dark' {
  const resolved = theme === 'system' ? getSystemTheme() : theme
  const root = document.documentElement

  root.classList.toggle('light', resolved === 'light')
  root.style.colorScheme = resolved

  // Keep the mobile browser chrome in step with the page. These two values
  // are the sRGB form of the `--bg` token in each theme; if that token
  // changes, change these (and public/theme-init.js) or the address bar will
  // sit a shade off the page it frames.
  const meta = document.querySelector('meta[name="theme-color"]')
  if (meta) {
    meta.setAttribute('content', resolved === 'light' ? '#fcfaf7' : '#0b0907')
  }

  return resolved
}

function readStoredTheme(): Theme {
  if (typeof window === 'undefined') return 'dark'
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (stored === 'light' || stored === 'dark' || stored === 'system') return stored
  } catch {
    /* storage blocked (private mode) — fall through to the default */
  }
  // Dark-first: an unknown visitor gets the dark theme, not the OS default.
  return 'dark'
}

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>(readStoredTheme)
  const [resolvedTheme, setResolvedTheme] = useState<'light' | 'dark'>(() =>
    typeof window === 'undefined'
      ? 'dark'
      : readStoredTheme() === 'system'
        ? getSystemTheme()
        : (readStoredTheme() as 'light' | 'dark'),
  )

  useEffect(() => {
    setResolvedTheme(applyTheme(theme))
    try {
      localStorage.setItem(STORAGE_KEY, theme)
    } catch {
      /* non-fatal: the theme still applies for this session */
    }
  }, [theme])

  // Only track the OS while the user has explicitly chosen "system".
  useEffect(() => {
    if (theme !== 'system') return
    const media = window.matchMedia('(prefers-color-scheme: light)')
    const handler = () => setResolvedTheme(applyTheme('system'))
    media.addEventListener('change', handler)
    return () => media.removeEventListener('change', handler)
  }, [theme])

  const setTheme = useCallback((next: Theme) => setThemeState(next), [])

  /** Toggle flips whatever is currently on screen, which means it also
   *  gives a sensible result while the theme is set to "system". */
  const toggleTheme = useCallback(() => {
    setThemeState(resolvedTheme === 'dark' ? 'light' : 'dark')
  }, [resolvedTheme])

  const value = useMemo(
    () => ({ theme, resolvedTheme, setTheme, toggleTheme }),
    [theme, resolvedTheme, setTheme, toggleTheme],
  )

  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
