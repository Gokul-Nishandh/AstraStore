/**
 * Turning failures into something a person can act on.
 *
 * Nothing in this app may show a user a raw status code, a stack trace, or
 * a backend exception string. Every failure goes through `toUserMessage`
 * and comes out as a sentence written for a human.
 *
 * The backend returns a single envelope (see the Java `ApiError` record):
 *   { code, message, requestId, timestamp }
 * `code` is a stable token we switch on. `message` is already written to be
 * display-safe, so we prefer it — but we always have a fallback, because a
 * message we did not write is not a message we can vouch for.
 */

/** Error thrown by the API client for any non-2xx response. */
export class ApiError extends Error {
  readonly status: number
  readonly code: string
  /** Correlation id from the gateway, shown in support-facing copy only. */
  readonly requestId?: string

  constructor(status: number, code: string, message: string, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.requestId = requestId
  }
}

/** Thrown when the request never reached the server at all. */
export class NetworkError extends Error {
  constructor(message = 'Network request failed') {
    super(message)
    this.name = 'NetworkError'
  }
}

/**
 * Copy for each backend error code. Written in second person, says what
 * happened and — where there is one — what to do about it.
 */
const CODE_MESSAGES: Record<string, string> = {
  UNAUTHENTICATED: 'Your session has expired. Please sign in again.',
  FORBIDDEN: "You don't have permission to do that.",
  NOT_FOUND: "We couldn't find what you were looking for.",
  VALIDATION_FAILED: 'Some of the details you entered need fixing.',
  CONFLICT: 'That conflicts with something that already exists.',
  RATE_LIMITED: "You're going a little fast. Wait a moment and try again.",
  PAYLOAD_TOO_LARGE: 'That file is too large to upload.',
  SERVICE_UNAVAILABLE: 'AstraStore is temporarily unavailable. Please try again in a moment.',
  INTERNAL_ERROR: 'Something went wrong on our end. We’ve logged it — please try again.',
}

/**
 * Fallbacks by HTTP status, for the rare response that arrives without a
 * recognised code. Note there is no entry that surfaces the number itself:
 * "503" is not a message.
 */
const STATUS_MESSAGES: Record<number, string> = {
  400: "That request wasn't quite right. Check the details and try again.",
  401: 'Your session has expired. Please sign in again.',
  403: "You don't have permission to do that.",
  404: "We couldn't find what you were looking for.",
  405: "That action isn't available here.",
  408: 'That took too long. Please try again.',
  409: 'That conflicts with something that already exists.',
  413: 'That file is too large to upload.',
  415: "That file type isn't supported.",
  422: 'Some of the details you entered need fixing.',
  429: "You're going a little fast. Wait a moment and try again.",
  500: 'Something went wrong on our end. We’ve logged it — please try again.',
  502: 'AstraStore is temporarily unavailable. Please try again in a moment.',
  503: 'AstraStore is temporarily unavailable. Please try again in a moment.',
  504: 'The server took too long to respond. Please try again.',
}

const GENERIC = 'Something went wrong. Please try again.'

/**
 * Any thrown value in, one display-safe sentence out.
 *
 * @param error    the caught value — may be anything
 * @param fallback context-specific default, e.g. "Could not delete the bucket."
 */
export function toUserMessage(error: unknown, fallback = GENERIC): string {
  if (error instanceof NetworkError) {
    return "We couldn't reach AstraStore. Check your connection and try again."
  }

  if (error instanceof ApiError) {
    // A server message is preferred, but only when it is actually readable.
    if (error.message && isDisplaySafe(error.message)) return error.message
    return CODE_MESSAGES[error.code] ?? STATUS_MESSAGES[error.status] ?? fallback
  }

  // An unrecognised throw is a bug on our side. Log it for ourselves and
  // show the caller the neutral fallback rather than an internal string.
  if (import.meta.env.DEV) console.error('Unhandled error:', error)
  return fallback
}

/**
 * Guards against a backend leaking something that should never be rendered.
 *
 * Rejects stack traces, Java/SQL exception class names, internal hostnames
 * and bare status codes. If the string trips any of these, we fall back to
 * our own copy instead.
 */
function isDisplaySafe(message: string): boolean {
  if (message.length > 200) return false

  const unsafe = [
    /\bat\s+[\w.$]+\([\w.]+:\d+\)/, // stack frame
    /\b(?:java|org|com|jakarta|springframework)\.[\w.]+(?:Exception|Error)\b/,
    /Exception\b|Throwable\b|NullPointer|StackTrace/i,
    /\b(?:SELECT|INSERT|UPDATE|DELETE)\s+.*\b(?:FROM|INTO|SET)\b/i,
    /jdbc:|postgresql:\/\/|redis:\/\/|kafka:/i,
    /https?:\/\/(?:localhost|127\.0\.0\.1|[\w-]+:\d{4,5})/i, // internal URL
    /^\s*\d{3}\s*$/, // a bare status code such as "503"
    /^(?:Request failed|Internal Server Error|Bad Gateway|Service Unavailable)/i,
  ]

  return !unsafe.some((pattern) => pattern.test(message))
}

/** Field-level validation errors, keyed by field name, if the server sent them. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (error instanceof ApiError && error.code === 'VALIDATION_FAILED') {
    const details = (error as ApiError & { details?: Record<string, string> }).details
    if (details && typeof details === 'object') return details
  }
  return {}
}

/** True when the failure means the session is gone and we should sign out. */
export function isAuthExpired(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 401 || error.code === 'UNAUTHENTICATED')
}

/** True when retrying the same request might reasonably succeed. */
export function isRetryable(error: unknown): boolean {
  if (error instanceof NetworkError) return true
  if (error instanceof ApiError) return error.status >= 500 || error.status === 408 || error.status === 429
  return false
}
