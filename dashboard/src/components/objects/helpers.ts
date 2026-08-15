import type { ObjectRecord } from '../../types/api'

/**
 * One sentence for every control a READ_ONLY account cannot use. Written
 * once so the explanation never varies by screen, and so the reason is
 * always given rather than the request being left to 403.
 */
export const READ_ONLY_REASON = 'Your account is read only, so this is unavailable.'

/** The last path segment — what a person calls the file. */
export function objectName(key: string): string {
  const segments = key.split('/')
  return segments[segments.length - 1] || key
}

/** Everything before the file name, or null for a key at the bucket root. */
export function objectFolder(key: string): string | null {
  const index = key.lastIndexOf('/')
  return index > 0 ? key.slice(0, index) : null
}

export function downloadName(object: ObjectRecord): string {
  return objectName(object.key)
}
