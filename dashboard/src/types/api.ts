/**
 * Type contracts mirroring the AstraStore backend DTOs.
 *
 * One rule governs this file: a field is optional here only when the backend
 * can genuinely omit it. Telemetry in particular uses `null` to mean "not
 * enough data yet" rather than zero, so those fields are `number | null` and
 * every consumer has to decide what to render for the null case. That is
 * deliberate — it is what stops a fresh cluster from displaying a confident
 * "100% uptime" or a phantom "10 TB".
 */

/* ------------------------------------------------------------------ *
 * Pagination
 * ------------------------------------------------------------------ */

/** Spring Data `Page<T>`, as the metadata service serialises it. */
export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  size: number
  number: number
  first: boolean
  last: boolean
  empty: boolean
}

/** The auth service's leaner envelope (`PageResponse<T>`). */
export interface PageResponse<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
  sort: string
}

/* ------------------------------------------------------------------ *
 * Identity and access
 * ------------------------------------------------------------------ */

/** Assignable roles, mirroring `auth/security/Roles.java` (FR-004). */
export type Role = 'ADMIN' | 'DEVELOPER' | 'USER' | 'READ_ONLY'

export const ROLE_LABELS: Record<Role, string> = {
  ADMIN: 'Administrator',
  DEVELOPER: 'Developer',
  USER: 'User',
  READ_ONLY: 'Read only',
}

export const ROLE_DESCRIPTIONS: Record<Role, string> = {
  ADMIN: 'Full access, including cluster operations, the audit trail and user management.',
  DEVELOPER: 'Full access to their own data plus API key issuance.',
  USER: 'Upload, download and manage their own objects.',
  READ_ONLY: 'May read their own objects but cannot upload, delete or issue keys.',
}

export interface LoginRequest {
  email: string
  password: string
}

export interface RegisterRequest {
  username: string
  email: string
  password: string
}

export interface AuthResponse {
  token: string
  type: string
  refreshToken: string
  userId: number
  username: string
  email: string
  roles: string[]
}

export interface RefreshRequest {
  refreshToken: string
}

export interface AuthUser {
  userId: number
  username: string
  email: string
  roles: string[]
}

/** `GET /api/auth/account` */
export interface UserProfile {
  id: number
  username: string
  email: string
  roles: string[]
  enabled: boolean
  createdAt: string
  updatedAt: string | null
  lastLoginAt: string | null
}

/**
 * `PATCH /api/auth/account`.
 *
 * Changing the username or email invalidates the identity baked into the
 * existing JWT, so the server reissues the pair. When it does,
 * `tokensReissued` is true and the client must store the new tokens or the
 * next request will 401.
 */
export interface ProfileUpdateResult {
  user: UserProfile
  tokensReissued: boolean
  token: string | null
  refreshToken: string | null
}

export interface UpdateProfileRequest {
  username?: string
  email?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export interface ForgotPasswordRequest {
  email: string
}

export interface ResetPasswordRequest {
  token: string
  newPassword: string
}

export interface DeleteAccountRequest {
  password: string
}

/** `GET /api/auth/admin/users` */
export interface AdminUser {
  id: number
  username: string
  email: string
  roles: string[]
  enabled: boolean
  createdAt: string
  updatedAt: string | null
  lastLoginAt: string | null
  apiKeyCount: number
}

export interface UpdateRolesRequest {
  roles: string[]
}

export interface UpdateStatusRequest {
  enabled: boolean
  reason?: string
}

/* ------------------------------------------------------------------ *
 * Buckets and objects
 * ------------------------------------------------------------------ */

export interface Bucket {
  id: string
  name: string
  ownerId: string
  createdAt: string
}

export type ObjectStatus = 'ACTIVE' | 'DELETED'

/**
 * The one object shape every listing returns — bucket contents, recent,
 * starred and trash all use it, which is why a single table component serves
 * all four. `starred` is resolved for the calling user, never globally.
 */
export interface ObjectRecord {
  id: string
  bucketId: string
  key: string
  sizeBytes: number
  checksum: string
  contentType: string
  status: ObjectStatus
  createdAt: string
  chunksReplicated: number
  chunksTotal: number
  starred: boolean
  deletedAt: string | null
}

export interface UploadResult {
  objectId: string
  bucketId: string
  key: string
  sizeBytes: number
  checksum: string
  chunkCount: number
  createdAt: string
}

/** `GET /api/v1/stats` — the caller's own totals, counted in the database. */
export interface UserStats {
  objectCount: number
  totalBytes: number
  bucketCount: number
  starredCount: number
  trashedCount: number
}


/** `GET /api/v1/stats/breakdown` — aggregated in the database, not client-side. */
export interface StorageBreakdown {
  byCategory: { category: string; objectCount: number; totalBytes: number }[]
  /** Days with no uploads are ABSENT, not zero. Fill gaps client-side. */
  daily: { date: string; objectCount: number; totalBytes: number }[]
}

/* ------------------------------------------------------------------ *
 * API keys
 * ------------------------------------------------------------------ */

export interface ApiKey {
  id: number
  name: string
  keyPrefix: string
  expiresAt: string | null
  createdAt: string
  lastUsedAt: string | null
}

/** The only response that ever carries the full secret. Shown once. */
export interface ApiKeyCreated {
  id: number
  name: string
  key: string
  keyPrefix: string
  expiresAt: string | null
  createdAt: string
}

export interface ApiKeyRequest {
  name: string
  expiresAt?: string | null
}

/* ------------------------------------------------------------------ *
 * Audit trail
 * ------------------------------------------------------------------ */

/**
 * `username` is null for events recorded before the caller was identified —
 * a failed login against an address that does not exist — and for events
 * whose subject has since deleted their account. Fall back to `actorEmail`.
 */
export interface AuditEvent {
  id: number
  userId: number | null
  username: string | null
  email: string | null
  actorEmail: string | null
  action: string
  ipAddress: string | null
  userAgent: string | null
  success: boolean
  failureReason: string | null
  detail: string | null
  timestamp: string
}

export type AuditOutcome = 'all' | 'success' | 'failure'

export interface AuditFilters {
  userId?: number | null
  action?: string | null
  outcome?: AuditOutcome
  from?: string | null
  to?: string | null
  search?: string | null
  page?: number
  size?: number
  sort?: string
}

/* ------------------------------------------------------------------ *
 * Cluster capacity and placement
 * ------------------------------------------------------------------ */

export type NodeState = 'HEALTHY' | 'DEGRADED' | 'DOWN' | 'RECOVERING'

export interface StorageNode {
  nodeId: string
  baseUrl: string
  state: NodeState
  lastSeen: string | null
  lastChecked: string | null
  consecutiveFailures: number
  /** False until the node has told us its quota; every figure below is null. */
  capacityReported: boolean
  /** Configured quota for this node — not the size of the host disk. */
  capacityBytes: number | null
  usedBytes: number | null
  availableBytes: number | null
  chunkCount: number | null
  usedRatio: number | null
  /**
   * Advisory only. Every container on one host reports the same figure, so
   * summing this across nodes is what produced the phantom cluster total.
   */
  hostDiskFreeBytes: number | null
  eligibleForPlacement: boolean
}

/**
 * Cluster capacity as measured, not assumed.
 *
 * Every figure is a sum of quotas nodes reported about themselves. Host
 * filesystem sizes are deliberately absent: three containers sharing one
 * disk used to be summed into a total no hardware could satisfy, which is
 * where the phantom "10 TB across 3 nodes" came from.
 *
 * `raw` is what is physically stored; `logical` is what users uploaded.
 * With a replication factor of 2 a 1 GB object occupies 2 GB of cluster.
 * Both are true and they answer different questions.
 */
export interface ClusterStatus {
  totalNodes: number
  healthyNodes: number
  degradedNodes: number
  downNodes: number
  recoveringNodes: number
  eligibleNodes: number

  /** Nodes that have reported a quota at least once. */
  reportingNodes: number
  /** True when no node has reported yet. Everything below is then null. */
  insufficientData: boolean
  totalCapacityBytes: number | null
  usedBytes: number | null
  availableBytes: number | null
  usedRatio: number | null
  totalChunkCount: number | null

  replicationFactor: number
  rawBytesStored: number | null
  logicalBytesStored: number | null
  logicalBytesAvailable: number | null
  replicationOverheadBytes: number | null
  /** Logical bytes are derived from raw and the factor — label them as such. */
  logicalBytesIsEstimate: boolean

  checkedAt: string
  nodes: StorageNode[]
}

export interface ReplicationStatus {
  totalTrackedChunks: number
  underReplicatedChunks: number
}

/* ------------------------------------------------------------------ *
 * Chunk placement (ADMIN)
 *
 * Where the bytes physically are. Both endpoints are admin-only and
 * enforced as such server-side — a node's chunk listing spans every
 * account in the cluster, so there is no owner to scope it to.
 * ------------------------------------------------------------------ */

/** `metadata.chunk_locations.replication_status`, verbatim. */
export type ChunkReplicationStatus =
  | 'PENDING'
  | 'REPLICATING'
  | 'REPLICATED'
  | 'UNDER_REPLICATED'
  | 'REPAIRING'
  | 'FAILED'
  | 'COMPLETE'

/**
 * `GET /api/v1/admin/objects/{objectId}/chunks` — one row per chunk of one
 * object, in index order.
 *
 * `replicaNodeId` is null until replication has placed the second copy. That
 * is "not yet", not "nowhere", and it renders as an em dash.
 */
export interface ChunkPlacement {
  id: string
  chunkIndex: number
  nodeId: string
  replicaNodeId: string | null
  replicationStatus: ChunkReplicationStatus
  checksum: string
  createdAt: string
}

/** Which copy of a chunk the node being inspected holds. */
export type ChunkRole = 'PRIMARY' | 'REPLICA'

/**
 * `GET /api/v1/admin/chunks?nodeId=…` — one row per chunk held by one node.
 *
 * The object fields are nullable because a chunk row can outlive its object
 * (a cleanup event that never landed). Such a row is reported rather than
 * hidden — an orphaned chunk is exactly what this view is for.
 *
 * `objectSizeBytes` is the size of the whole object, not of this chunk:
 * per-chunk lengths are not recorded anywhere, so labelling it as the
 * chunk's size would be inventing a number.
 */
export interface NodeChunk {
  id: string
  objectId: string
  objectKey: string | null
  bucketId: string | null
  bucketName: string | null
  objectSizeBytes: number | null
  chunkIndex: number
  role: ChunkRole
  /** The node holding the other copy, or null when there is not one yet. */
  peerNodeId: string | null
  replicationStatus: ChunkReplicationStatus
  checksum: string
  createdAt: string
}

/* ------------------------------------------------------------------ *
 * Monitoring — availability history
 * ------------------------------------------------------------------ */

export type ServiceStatus = 'UP' | 'DOWN' | 'DEGRADED' | 'UNKNOWN'

export type MonitoringWindow = '1h' | '24h' | '7d' | '30d'

export const MONITORING_WINDOWS: { value: MonitoringWindow; label: string }[] = [
  { value: '1h', label: 'Last hour' },
  { value: '24h', label: 'Last 24 hours' },
  { value: '7d', label: 'Last 7 days' },
  { value: '30d', label: 'Last 30 days' },
]

export interface ResponseTimes {
  p50: number | null
  p95: number | null
  p99: number | null
  last: number | null
}

/** One downsampled bucket of a service's availability sparkline. */
export interface UptimePoint {
  t: string
  up: boolean
  ms: number | null
}

export interface Incident {
  id: number
  serviceId: string
  serviceName: string
  startedAt: string
  endedAt: string | null
  durationSeconds: number | null
  ongoing: boolean
  lastError: string | null
}

export interface ServiceUptime {
  id: string
  name: string
  kind: 'service' | 'node'
  status: ServiceStatus
  /** Null when the window holds no samples. Never assume 100. */
  uptimePercent: number | null
  lastStateChange: string | null
  downtimeSeconds: number | null
  incidentCount: number
  responseTimeMs: ResponseTimes
  sparkline: UptimePoint[]
  incidents?: Incident[]
}

export interface MonitoringServices {
  window: MonitoringWindow
  generatedAt: string
  services: ServiceUptime[]
}

export interface MonitoringSummary {
  window: MonitoringWindow
  generatedAt: string
  servicesTotal: number
  servicesUp: number
  servicesDown: number
  servicesDegraded: number
  clusterUptimePercent: number | null
  openIncidents: number
  incidentsInWindow: number
  insufficientData: boolean
}

/* ------------------------------------------------------------------ *
 * Onboarding
 * ------------------------------------------------------------------ */

export type OnboardingStep = 'bucket' | 'upload' | 'apiKey'

export interface OnboardingState {
  completed: boolean
  dismissed: boolean
  steps: Record<OnboardingStep, boolean>
}
