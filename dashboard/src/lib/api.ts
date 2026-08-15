/**
 * The single door to the backend.
 *
 * Everything here funnels non-2xx responses through one parser that produces
 * the `ApiError` in `./errors`, carrying the backend's stable `code`. No
 * caller ever sees a `Response`, a status number, or a raw body — which is
 * what makes it possible to guarantee that a bare "503" can never reach the
 * screen.
 */
import type {
  AdminUser,
  ApiKey,
  ApiKeyCreated,
  ApiKeyRequest,
  AuditEvent,
  AuditFilters,
  AuthResponse,
  Bucket,
  ChangePasswordRequest,
  ChunkPlacement,
  ClusterStatus,
  DeleteAccountRequest,
  ForgotPasswordRequest,
  Incident,
  LoginRequest,
  MonitoringServices,
  MonitoringSummary,
  MonitoringWindow,
  NodeChunk,
  ObjectRecord,
  Page,
  PageResponse,
  ProfileUpdateResult,
  RefreshRequest,
  RegisterRequest,
  ReplicationStatus,
  ResetPasswordRequest,
  ServiceUptime,
  UpdateProfileRequest,
  UpdateRolesRequest,
  UpdateStatusRequest,
  UploadResult,
  UserProfile,
  StorageBreakdown,
  UserStats,
} from '../types/api'
import { ApiError, NetworkError } from './errors'
import { encodeObjectKey } from './format'

/* ------------------------------------------------------------------ *
 * Token store
 * ------------------------------------------------------------------ */

/**
 * Persisted to localStorage so a full reload keeps the session. This is an
 * access token with a short lifetime plus a rotating refresh token, not a
 * long-lived credential — the API keys that *are* long-lived are never
 * stored here, they are shown once and then only ever held by the user.
 */
const ACCESS_KEY = 'astrastore_access'
const REFRESH_KEY = 'astrastore_refresh'

function read(key: string): string | null {
  try {
    return localStorage.getItem(key)
  } catch {
    return null
  }
}

let accessToken: string | null = read(ACCESS_KEY)
let refreshToken: string | null = read(REFRESH_KEY)

function persist() {
  try {
    if (accessToken) localStorage.setItem(ACCESS_KEY, accessToken)
    else localStorage.removeItem(ACCESS_KEY)
    if (refreshToken) localStorage.setItem(REFRESH_KEY, refreshToken)
    else localStorage.removeItem(REFRESH_KEY)
  } catch {
    /* storage blocked (private mode) — the session still works in memory */
  }
}

export function setAuthTokens(access: string | null, refresh: string | null) {
  accessToken = access
  refreshToken = refresh
  persist()
}

export function getAccessToken(): string | null {
  return accessToken
}

export function getRefreshToken(): string | null {
  return refreshToken
}

/**
 * Fired when the refresh token is spent and the session cannot be recovered.
 * The auth provider listens and sends the user to sign-in with one toast,
 * rather than every in-flight request racing to do the same.
 */
export const SESSION_EXPIRED_EVENT = 'astrastore:session-expired'

function announceSessionExpired() {
  setAuthTokens(null, null)
  window.dispatchEvent(new CustomEvent(SESSION_EXPIRED_EVENT))
}

/* ------------------------------------------------------------------ *
 * Request plumbing
 * ------------------------------------------------------------------ */

const JSON_TYPE = 'application/json'

/**
 * Reads the shared `ApiError` envelope `{code, message, requestId}`. Older
 * shapes are tolerated because not every path is behind the gateway yet, but
 * the status is always kept so `toUserMessage` can fall back on it.
 */
async function parseError(res: Response): Promise<ApiError> {
  let code = ''
  let message = ''
  let requestId: string | undefined

  try {
    const body = (await res.json()) as {
      code?: string
      message?: string
      error?: string
      detail?: string
      requestId?: string
    }
    code = body.code ?? ''
    message = body.message ?? body.error ?? body.detail ?? ''
    requestId = body.requestId
  } catch {
    /* empty or non-JSON body — status alone drives the message */
  }

  return new ApiError(res.status, code, message, requestId)
}

function buildHeaders(init: RequestInit | undefined, token: string | null): Headers {
  const headers = new Headers(init?.headers)
  if (!headers.has('Content-Type') && typeof init?.body === 'string') {
    headers.set('Content-Type', JSON_TYPE)
  }
  if (!headers.has('Accept')) headers.set('Accept', JSON_TYPE)
  if (token) headers.set('Authorization', `Bearer ${token}`)
  return headers
}

/** Single-flight: a burst of 401s triggers one refresh, not one each. */
let refreshInFlight: Promise<string | null> | null = null

async function doRefresh(): Promise<string | null> {
  const current = refreshToken
  if (!current) return null

  try {
    const res = await fetch('/api/auth/refresh', {
      method: 'POST',
      headers: { 'Content-Type': JSON_TYPE },
      body: JSON.stringify({ refreshToken: current } satisfies RefreshRequest),
    })
    if (!res.ok) {
      announceSessionExpired()
      return null
    }
    const body = (await res.json()) as AuthResponse
    setAuthTokens(body.token, body.refreshToken)
    return body.token
  } catch {
    announceSessionExpired()
    return null
  }
}

function refreshAccessToken(): Promise<string | null> {
  refreshInFlight ??= doRefresh().finally(() => {
    refreshInFlight = null
  })
  return refreshInFlight
}

async function send(path: string, init: RequestInit | undefined, token: string | null) {
  try {
    return await fetch(path, { ...init, headers: buildHeaders(init, token) })
  } catch {
    // fetch only rejects when the request never completed — DNS, offline,
    // CORS. A 500 resolves normally, so this really is a network failure.
    throw new NetworkError()
  }
}

async function decode<T>(res: Response): Promise<T> {
  if (res.status === 204 || res.headers.get('Content-Length') === '0') {
    return undefined as T
  }
  const text = await res.text()
  if (!text) return undefined as T
  return JSON.parse(text) as T
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  let res = await send(path, init, accessToken)

  // Only 401 means "this token is stale". A 403 is a genuine permission
  // decision — retrying it with a fresh token just repeats the refusal, and
  // the old code's retry-on-403 turned every forbidden action into two.
  if (res.status === 401 && refreshToken) {
    const renewed = await refreshAccessToken()
    if (renewed) {
      res = await send(path, init, renewed)
    } else {
      throw await parseError(res)
    }
  }

  if (!res.ok) throw await parseError(res)
  return decode<T>(res)
}

function query(params: Record<string, string | number | boolean | null | undefined>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value === null || value === undefined || value === '') continue
    search.set(key, String(value))
  }
  const qs = search.toString()
  return qs ? `?${qs}` : ''
}

function body(payload: unknown): RequestInit {
  return { headers: { 'Content-Type': JSON_TYPE }, body: JSON.stringify(payload) }
}

/* ------------------------------------------------------------------ *
 * Endpoints
 * ------------------------------------------------------------------ */

export const api = {
  /* --- Authentication ------------------------------------------- */
  login: (payload: LoginRequest) =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', ...body(payload) }),

  register: (payload: RegisterRequest) =>
    request<{ message: string }>('/api/auth/register', { method: 'POST', ...body(payload) }),

  refresh: (payload: RefreshRequest) =>
    request<AuthResponse>('/api/auth/refresh', { method: 'POST', ...body(payload) }),

  logout: (payload: RefreshRequest) =>
    request<{ message: string }>('/api/auth/logout', { method: 'POST', ...body(payload) }),

  /**
   * Always succeeds, whether or not the address exists — the response must
   * not become an account-existence oracle. `resetToken` is null unless the
   * server was explicitly configured to expose it for local testing.
   */
  forgotPassword: (payload: ForgotPasswordRequest) =>
    request<{ message: string; resetToken: string | null }>('/api/auth/forgot-password', {
      method: 'POST',
      ...body(payload),
    }),

  resetPassword: (payload: ResetPasswordRequest) =>
    request<{ message: string }>('/api/auth/reset-password', { method: 'POST', ...body(payload) }),

  /* --- The caller's own account --------------------------------- */
  profile: () => request<UserProfile>('/api/auth/account'),

  updateProfile: (payload: UpdateProfileRequest) =>
    request<ProfileUpdateResult>('/api/auth/account', { method: 'PATCH', ...body(payload) }),

  changePassword: (payload: ChangePasswordRequest) =>
    request<void>('/api/auth/account/password', { method: 'POST', ...body(payload) }),

  deleteAccount: (payload: DeleteAccountRequest) =>
    request<void>('/api/auth/account', { method: 'DELETE', ...body(payload) }),

  /* --- User administration (ADMIN) ------------------------------ */
  listUsers: (params: { search?: string; role?: string; page?: number; size?: number; sort?: string } = {}) =>
    request<PageResponse<AdminUser>>(`/api/auth/admin/users${query(params)}`),

  assignableRoles: () => request<string[]>('/api/auth/admin/roles'),

  updateUserRoles: (userId: number, payload: UpdateRolesRequest) =>
    request<AdminUser>(`/api/auth/admin/users/${userId}/roles`, { method: 'PATCH', ...body(payload) }),

  updateUserStatus: (userId: number, payload: UpdateStatusRequest) =>
    request<AdminUser>(`/api/auth/admin/users/${userId}/status`, { method: 'PATCH', ...body(payload) }),

  deleteUser: (userId: number) =>
    request<void>(`/api/auth/admin/users/${userId}`, { method: 'DELETE' }),

  /* --- Buckets --------------------------------------------------- */
  listBuckets: (page = 0, size = 50) =>
    request<Page<Bucket>>(`/api/v1/buckets${query({ page, size })}`),

  createBucket: (name: string) =>
    request<Bucket>('/api/v1/buckets', { method: 'POST', ...body({ name }) }),

  deleteBucket: (bucketId: string) =>
    request<void>(`/api/v1/buckets/${bucketId}`, { method: 'DELETE' }),

  /* --- Objects --------------------------------------------------- */
  listObjects: (bucketId: string, params: { page?: number; size?: number; search?: string } = {}) =>
    request<Page<ObjectRecord>>(`/api/v1/buckets/${bucketId}/objects${query(params)}`),

  recentObjects: (limit = 12) =>
    request<ObjectRecord[]>(`/api/v1/objects/recent${query({ limit })}`),

  getObject: (objectId: string) => request<ObjectRecord>(`/api/v1/objects/${objectId}`),

  /** Soft delete — the object moves to trash and can be restored. */
  deleteObject: (objectId: string) =>
    request<void>(`/api/v1/objects/${objectId}`, { method: 'DELETE' }),

  restoreObject: (objectId: string) =>
    request<ObjectRecord>(`/api/v1/objects/${objectId}/restore`, { method: 'POST' }),

  /** Irreversible. Callers must confirm before reaching this. */
  purgeObject: (objectId: string) =>
    request<void>(`/api/v1/objects/${objectId}/permanent`, { method: 'DELETE' }),

  /**
   * The raw path. Useful for display, but do NOT navigate to it directly:
   * a browser navigation carries no Authorization header, and the session
   * token lives in localStorage rather than a cookie, so the gateway sees an
   * anonymous request. Use `downloadObject` instead.
   */
  downloadUrl: (bucketId: string, key: string) =>
    `/api/v1/buckets/${bucketId}/objects/${encodeObjectKey(key)}`,

  /* --- Starred (idempotent both ways) ---------------------------- */
  star: (objectId: string) => request<void>(`/api/v1/objects/${objectId}/star`, { method: 'PUT' }),

  unstar: (objectId: string) =>
    request<void>(`/api/v1/objects/${objectId}/star`, { method: 'DELETE' }),

  listStarred: (params: { page?: number; size?: number; search?: string } = {}) =>
    request<Page<ObjectRecord>>(`/api/v1/starred${query(params)}`),

  /* --- Trash ------------------------------------------------------ */
  listTrash: (params: { page?: number; size?: number; search?: string } = {}) =>
    request<Page<ObjectRecord>>(`/api/v1/trash${query(params)}`),

  emptyTrash: () => request<{ purged: number }>('/api/v1/trash/empty', { method: 'POST' }),

  /* --- Totals ----------------------------------------------------- */
  stats: () => request<UserStats>('/api/v1/stats'),

  storageBreakdown: (days = 30) =>
    request<StorageBreakdown>(`/api/v1/stats/breakdown${query({ days })}`),

  /* --- Cluster ---------------------------------------------------- */
  clusterStatus: () => request<ClusterStatus>('/api/v1/cluster/status'),

  replicationStatus: () => request<ReplicationStatus>('/api/v1/admin/metadata/status'),

  triggerHeal: () =>
    request<{ message: string; underReplicatedChunksFound: number }>('/api/v1/admin/heal/run', {
      method: 'POST',
    }),

  /* --- Chunk placement (ADMIN) ------------------------------------ *
   * Both are refused with a 403 for a non-admin. The role is checked
   * server-side; the AdminGuard in the console only hides navigation.
   * ---------------------------------------------------------------- */

  /** Where each chunk of one object lives. Not paged — chunk counts are small. */
  objectChunks: (objectId: string) =>
    request<ChunkPlacement[]>(`/api/v1/admin/objects/${objectId}/chunks`),

  /**
   * Every chunk one node holds, primary or replica. Paged — a node holds many.
   *
   * `nodeIds` is every name the node answers to, and all of them are sent.
   * A chunk row records the node's base URL (`http://storage-node-1:8088`),
   * because the download service fetches the bytes straight from that column;
   * the cluster status calls the same machine `storage-node-1`. Sending only
   * one of the two silently under-reports what the node is holding.
   */
  nodeChunks: (nodeIds: string[], params: { page?: number; size?: number } = {}) => {
    const search = new URLSearchParams()
    for (const id of nodeIds) if (id) search.append('nodeId', id)
    if (params.page != null) search.set('page', String(params.page))
    if (params.size != null) search.set('size', String(params.size))
    return request<Page<NodeChunk>>(`/api/v1/admin/chunks?${search.toString()}`)
  },

  /* --- Availability history --------------------------------------- */
  monitoringSummary: (window: MonitoringWindow = '24h') =>
    request<MonitoringSummary>(`/api/v1/monitoring/summary${query({ window })}`),

  monitoringServices: (window: MonitoringWindow = '24h') =>
    request<MonitoringServices>(`/api/v1/monitoring/services${query({ window })}`),

  monitoringService: (id: string, window: MonitoringWindow = '24h') =>
    request<ServiceUptime>(`/api/v1/monitoring/services/${id}${query({ window })}`),

  incidents: (window: MonitoringWindow = '7d', limit = 50) =>
    request<{ incidents: Incident[] }>(`/api/v1/monitoring/incidents${query({ window, limit })}`),

  /* --- API keys ---------------------------------------------------- */
  listApiKeys: () => request<ApiKey[]>('/api/auth/keys'),

  createApiKey: (payload: ApiKeyRequest) =>
    request<ApiKeyCreated>('/api/auth/keys', { method: 'POST', ...body(payload) }),

  deleteApiKey: (id: number) => request<void>(`/api/auth/keys/${id}`, { method: 'DELETE' }),

  /* --- Audit trail -------------------------------------------------- */
  auditLog: (filters: AuditFilters = {}) =>
    request<PageResponse<AuditEvent>>(`/api/auth/audit${query({ ...filters })}`),

  auditActions: () => request<string[]>('/api/auth/audit/actions'),
}

/**
 * Streams the audit CSV through the authenticated client rather than a bare
 * link, because a plain `<a href>` cannot carry the bearer token.
 */
export async function downloadAuditCsv(filters: AuditFilters = {}): Promise<Blob> {
  const res = await send(`/api/auth/audit/export.csv${query({ ...filters })}`, undefined, accessToken)
  if (!res.ok) throw await parseError(res)
  return res.blob()
}

/**
 * Upload with real byte progress.
 *
 * `fetch` cannot report upload progress, so this stays on XMLHttpRequest. It
 * is the one place that talks to the network without going through
 * `request`, and it reproduces the same error contract by hand.
 */
export function uploadObject(
  bucketId: string,
  key: string,
  file: File,
  options: {
    onProgress?: (percent: number) => void
    signal?: AbortSignal
  } = {},
): Promise<UploadResult> {
  const { onProgress, signal } = options

  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('Upload cancelled', 'AbortError'))
      return
    }

    const xhr = new XMLHttpRequest()
    xhr.open('PUT', `/api/v1/buckets/${bucketId}/objects/${encodeObjectKey(key)}`)
    xhr.setRequestHeader('Content-Type', file.type || 'application/octet-stream')
    if (accessToken) xhr.setRequestHeader('Authorization', `Bearer ${accessToken}`)

    const onAbort = () => xhr.abort()
    signal?.addEventListener('abort', onAbort, { once: true })
    const cleanup = () => signal?.removeEventListener('abort', onAbort)

    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable) onProgress?.(Math.round((e.loaded / e.total) * 100))
    }

    xhr.onload = () => {
      cleanup()
      if (xhr.status >= 200 && xhr.status < 300) {
        try {
          resolve(JSON.parse(xhr.responseText) as UploadResult)
        } catch {
          reject(new ApiError(xhr.status, 'INTERNAL_ERROR', ''))
        }
        return
      }
      let code = ''
      let message = ''
      try {
        const parsed = JSON.parse(xhr.responseText) as { code?: string; message?: string; error?: string }
        code = parsed.code ?? ''
        message = parsed.message ?? parsed.error ?? ''
      } catch {
        /* non-JSON error body */
      }
      reject(new ApiError(xhr.status, code, message))
    }

    xhr.onerror = () => {
      cleanup()
      reject(new NetworkError('The upload could not reach AstraStore.'))
    }

    xhr.onabort = () => {
      cleanup()
      reject(new DOMException('Upload cancelled', 'AbortError'))
    }

    xhr.send(file)
  })
}

/**
 * Downloads an object and hands it to the browser's save flow.
 *
 * A plain `<a href download>` cannot be used: the request would carry no
 * Authorization header and come back 401. So the bytes are fetched with the
 * session token and handed over as an object URL.
 *
 * The cost is that the whole object is buffered in memory before the save
 * dialog appears — acceptable for a console, wrong for very large objects.
 * The proper fix is a short-lived pre-signed download URL the browser can
 * navigate to directly, which is how object stores normally solve this.
 */
export async function downloadObject(
  bucketId: string,
  key: string,
  fileName: string,
): Promise<void> {
  const res = await send(api.downloadUrl(bucketId, key), undefined, accessToken)
  if (!res.ok) throw await parseError(res)

  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  try {
    const anchor = document.createElement('a')
    anchor.href = url
    anchor.download = fileName
    anchor.rel = 'noopener'
    document.body.appendChild(anchor)
    anchor.click()
    anchor.remove()
  } finally {
    // Revoking immediately can cancel the save in some browsers, so the URL
    // is released on the next tick once the click has been handled.
    setTimeout(() => URL.revokeObjectURL(url), 0)
  }
}
