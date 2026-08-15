import { useEffect } from 'react'
import { useLocation } from 'react-router-dom'

/**
 * Resets scroll on navigation.
 *
 * A single-page app keeps the scroll position across route changes, so
 * following a link from halfway down a long table lands the next page
 * already scrolled past its own heading. Hash links are left alone — those
 * are in-page anchors that are supposed to jump.
 */
export function ScrollToTop() {
  const { pathname, hash } = useLocation()

  useEffect(() => {
    if (hash) return
    window.scrollTo({ top: 0, left: 0, behavior: 'instant' })
  }, [pathname, hash])

  return null
}
