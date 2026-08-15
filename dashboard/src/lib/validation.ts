/**
 * Client-side validation and sanitisation.
 *
 * This is a usability layer, not a security boundary — the server validates
 * everything again, and must, because anything here can be bypassed. What
 * it buys us is telling the user what is wrong *before* a round trip, and
 * refusing to send obviously malformed input.
 *
 * Each validator returns an error string, or null when the value is fine.
 */

export type Validator<T = string> = (value: T) => string | null

/* ------------------------------------------------------------------
   Primitives
   ------------------------------------------------------------------ */

export const required =
  (label = 'This field'): Validator =>
  (value) =>
    value.trim().length === 0 ? `${label} is required.` : null

export const minLength =
  (min: number, label = 'This field'): Validator =>
  (value) =>
    value.length < min ? `${label} must be at least ${min} characters.` : null

export const maxLength =
  (max: number, label = 'This field'): Validator =>
  (value) =>
    value.length > max ? `${label} must be ${max} characters or fewer.` : null

/* ------------------------------------------------------------------
   Domain rules — kept in step with the backend's jakarta constraints.
   If one of these disagrees with the server, the server wins and the
   user sees a confusing double validation; keep them aligned.
   ------------------------------------------------------------------ */

/** Deliberately permissive. Over-strict email regexes reject valid addresses. */
export function validateEmail(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return 'Email is required.'
  if (trimmed.length > 254) return 'That email address is too long.'
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(trimmed)) return 'Enter a valid email address.'
  return null
}

export function validateUsername(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return 'Username is required.'
  if (trimmed.length < 3) return 'Username must be at least 3 characters.'
  if (trimmed.length > 40) return 'Username must be 40 characters or fewer.'
  if (!/^[a-zA-Z0-9._-]+$/.test(trimmed)) {
    return 'Use only letters, numbers, dots, underscores and hyphens.'
  }
  return null
}

export function validatePassword(value: string): string | null {
  if (!value) return 'Password is required.'
  if (value.length < 8) return 'Password must be at least 8 characters.'
  if (value.length > 128) return 'Password must be 128 characters or fewer.'
  return null
}

export function validatePasswordConfirmation(password: string, confirmation: string): string | null {
  if (!confirmation) return 'Please confirm your password.'
  if (password !== confirmation) return "Those passwords don't match."
  return null
}

/**
 * Bucket names follow S3-ish conventions: lowercase, DNS-safe. Being strict
 * here is deliberate — a bucket name ends up in a URL path.
 */
export function validateBucketName(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return 'Bucket name is required.'
  if (trimmed.length < 3) return 'Bucket name must be at least 3 characters.'
  if (trimmed.length > 63) return 'Bucket name must be 63 characters or fewer.'
  if (!/^[a-z0-9][a-z0-9.-]*[a-z0-9]$/.test(trimmed)) {
    return 'Use lowercase letters, numbers, dots and hyphens. Must start and end with a letter or number.'
  }
  if (/\.\./.test(trimmed) || /-\.|\.-/.test(trimmed)) {
    return 'Bucket name cannot contain consecutive dots or mix dots and hyphens.'
  }
  if (/^\d+\.\d+\.\d+\.\d+$/.test(trimmed)) {
    return 'Bucket name cannot look like an IP address.'
  }
  return null
}

/**
 * Object keys are close to free-form, but a few things are genuinely
 * dangerous: path traversal, control characters, and leading slashes that
 * would change how the key resolves in a URL.
 */
export function validateObjectKey(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return 'File name is required.'
  if (trimmed.length > 1024) return 'File name is too long.'
  if (trimmed.startsWith('/')) return 'File name cannot start with a slash.'
  if (trimmed.split('/').some((segment) => segment === '..' || segment === '.')) {
    return 'File name cannot contain "." or ".." path segments.'
  }
  // eslint-disable-next-line no-control-regex
  if (/[\x00-\x1f\x7f]/.test(trimmed)) return 'File name contains invalid characters.'
  if (/[\\<>:"|?*]/.test(trimmed)) return 'File name cannot contain \\ < > : " | ? or *.'
  return null
}

export function validateApiKeyName(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return 'Give the key a name so you can recognise it later.'
  if (trimmed.length > 64) return 'Name must be 64 characters or fewer.'
  return null
}

/* ------------------------------------------------------------------
   Sanitisation
   ------------------------------------------------------------------ */

/**
 * Strips control characters and collapses whitespace.
 *
 * Note this is NOT an XSS defence — React escapes interpolated text, and we
 * never use `dangerouslySetInnerHTML`. This is about preventing invisible
 * characters from ending up in stored names where they cause confusing
 * lookups and unreadable log lines.
 */
export function sanitizeText(value: string): string {
  return value
    // eslint-disable-next-line no-control-regex
    .replace(/[\x00-\x1f\x7f]/g, '')
    .replace(/\s+/g, ' ')
    .trim()
}

/**
 * Guards a URL before it is used in `href`. Blocks `javascript:` and `data:`
 * schemes, which are the two that turn a link into script execution.
 */
export function safeExternalUrl(value: string): string | null {
  try {
    const url = new URL(value, window.location.origin)
    if (url.protocol !== 'http:' && url.protocol !== 'https:' && url.protocol !== 'mailto:') {
      return null
    }
    return url.toString()
  } catch {
    return null
  }
}

/* ------------------------------------------------------------------
   Form helper
   ------------------------------------------------------------------ */

/**
 * Runs a map of field validators and returns the errors found.
 *
 * ```ts
 * const errors = validateForm({
 *   email: () => validateEmail(email),
 *   password: () => validatePassword(password),
 * })
 * if (hasErrors(errors)) { setErrors(errors); return }
 * ```
 */
export function validateForm(
  validators: Record<string, () => string | null>,
): Record<string, string> {
  const errors: Record<string, string> = {}
  for (const [field, run] of Object.entries(validators)) {
    const message = run()
    if (message) errors[field] = message
  }
  return errors
}

export function hasErrors(errors: Record<string, string>): boolean {
  return Object.keys(errors).length > 0
}
