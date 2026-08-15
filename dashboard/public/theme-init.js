/**
 * Theme bootstrap — runs before first paint.
 *
 * The stylesheet is dark-first: :root holds the dark palette and `.light`
 * overrides it. This script reads the stored preference and applies the
 * class synchronously, so a returning visitor on the light theme never
 * sees a frame of dark.
 *
 * Kept as a real file rather than an inline <script> so the production
 * Content-Security-Policy can stay at `script-src 'self'` with no hash or
 * nonce to keep in sync.
 *
 * STORAGE_KEY must match src/lib/theme.tsx.
 */
;(function () {
  try {
    var stored = localStorage.getItem('astrastore-theme')
    var resolved

    if (stored === 'light' || stored === 'dark') {
      resolved = stored
    } else if (stored === 'system') {
      resolved = window.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
    } else {
      // Dark-first: an unknown visitor gets the dark theme.
      resolved = 'dark'
    }

    if (resolved === 'light') {
      document.documentElement.classList.add('light')
    }
    document.documentElement.style.colorScheme = resolved

    var meta = document.querySelector('meta[name="theme-color"]')
    if (meta) {
      meta.setAttribute('content', resolved === 'light' ? '#fcfaf7' : '#0b0907')
    }
  } catch {
    /* Storage blocked (private mode). The dark-first default already applies. */
  }
})()
