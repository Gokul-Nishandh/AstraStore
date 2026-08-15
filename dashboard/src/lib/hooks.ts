import { useCallback, useEffect, useRef, useState } from 'react'
import { api } from './api'
import { toUserMessage } from './errors'
import type {
  Bucket,
  ChunkPlacement,
  ClusterStatus,
  Incident,
  MonitoringServices,
  MonitoringSummary,
  MonitoringWindow,
  NodeChunk,
  ObjectRecord,
  Page,
  ReplicationStatus,
  StorageBreakdown,
  StorageNode,
  UserStats,
} from '../types/api'

export interface AsyncState<T> {
  data: T | null
  /** Already a human sentence — never a status code or an exception string. */
  error: string | null
  loading: boolean
  lastUpdated: Date | null
  refresh: () => void
}

/**
 * Fetch, optionally on a timer.
 *
 * Two behaviours worth knowing. A poll that fails does not clear the data it
 * already has — a dashboard that blanks every panel because one refresh
 * timed out is worse than one showing figures a few seconds stale, so the
 * error appears alongside the last good values. And `loading` is only true
 * for the first load; later refreshes are silent, because a spinner every
 * five seconds makes a live panel unreadable.
 */
export function usePolling<T>(
  fetcher: () => Promise<T>,
  intervalMs = 0,
  deps: unknown[] = [],
  enabled = true,
): AsyncState<T> {
  const [data, setData] = useState<T | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(enabled)
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null)
  const [tick, setTick] = useState(0)

  const fetcherRef = useRef(fetcher)
  fetcherRef.current = fetcher

  useEffect(() => {
    if (!enabled) {
      setLoading(false)
      return
    }

    let cancelled = false

    const run = async () => {
      try {
        const result = await fetcherRef.current()
        if (cancelled) return
        setData(result)
        setError(null)
        setLastUpdated(new Date())
      } catch (e) {
        if (cancelled) return
        setError(toUserMessage(e))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }

    run()

    if (intervalMs > 0) {
      const timer = setInterval(run, intervalMs)
      return () => {
        cancelled = true
        clearInterval(timer)
      }
    }
    return () => {
      cancelled = true
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, tick, enabled, ...deps])

  const refresh = useCallback(() => setTick((t) => t + 1), [])
  return { data, error, loading, lastUpdated, refresh }
}

/**
 * A one-shot action with its own pending and error state — the mutation
 * counterpart to `usePolling`. Keeps every "save" button from reimplementing
 * try / setBusy / setError.
 */
export function useAction<Args extends unknown[], T>(
  action: (...args: Args) => Promise<T>,
  options: { onSuccess?: (result: T) => void; fallbackMessage?: string } = {},
) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const optionsRef = useRef(options)
  optionsRef.current = options
  const actionRef = useRef(action)
  actionRef.current = action

  const run = useCallback(async (...args: Args): Promise<T | undefined> => {
    setBusy(true)
    setError(null)
    try {
      const result = await actionRef.current(...args)
      optionsRef.current.onSuccess?.(result)
      return result
    } catch (e) {
      setError(toUserMessage(e, optionsRef.current.fallbackMessage))
      return undefined
    } finally {
      setBusy(false)
    }
  }, [])

  return { run, busy, error, clearError: useCallback(() => setError(null), []) }
}

/* ------------------------------------------------------------------ *
 * Domain hooks
 * ------------------------------------------------------------------ */

export function useStats() {
  return usePolling<UserStats>(() => api.stats(), 0)
}

export function useStorageBreakdown(days = 30) {
  return usePolling<StorageBreakdown>(() => api.storageBreakdown(days), 0, [days])
}

export function useBuckets() {
  return usePolling<Page<Bucket>>(() => api.listBuckets(0, 100), 0)
}

export function useObjects(bucketId: string | null, page = 0, search = '') {
  return usePolling<Page<ObjectRecord> | null>(
    () => (bucketId ? api.listObjects(bucketId, { page, size: 50, search }) : Promise.resolve(null)),
    0,
    [bucketId, page, search],
  )
}

export function useRecentObjects(limit = 12) {
  return usePolling<ObjectRecord[]>(() => api.recentObjects(limit), 0, [limit])
}

export function useStarred(page = 0, search = '') {
  return usePolling<Page<ObjectRecord>>(() => api.listStarred({ page, size: 50, search }), 0, [page, search])
}

export function useTrash(page = 0, search = '') {
  return usePolling<Page<ObjectRecord>>(() => api.listTrash({ page, size: 50, search }), 0, [page, search])
}

/**
 * Admin only. The route guard already blocks the page, but the poll is gated
 * too so a non-admin session never fires a request it will be refused —
 * which would otherwise fill their own audit trail with 403s.
 */
export function useClusterHealth(enabled = true) {
  return usePolling<ClusterStatus>(() => api.clusterStatus(), 10000, [], enabled)
}

export function useReplicationStatus(enabled = true) {
  return usePolling<ReplicationStatus>(() => api.replicationStatus(), 10000, [], enabled)
}

/**
 * Chunk placement for one object. Admin only, so `enabled` is not optional
 * in practice: pass `isAdmin` and a non-admin viewing their own object never
 * fires a request the server would refuse.
 *
 * Polled slowly rather than not at all — a freshly uploaded object's chunks
 * move from PENDING to REPLICATED within seconds, and watching that happen is
 * half the point of the panel.
 */
export function useObjectChunks(objectId: string | undefined, enabled = true) {
  return usePolling<ChunkPlacement[]>(
    () => (objectId ? api.objectChunks(objectId) : Promise.resolve([])),
    15000,
    [objectId],
    enabled && Boolean(objectId),
  )
}

/**
 * The chunks one node holds. Admin only. `node` is null while the drill-down
 * is closed, which keeps the request from firing until it opens.
 *
 * Both of the node's identifiers go to the server. Chunk rows are written
 * with the node's base URL and the cluster registry uses its short name, so
 * asking by one name alone finds only the rows recorded under it. This hook
 * is the single place that knows that, which is why it takes the whole node
 * rather than an id.
 */
export function useNodeChunks(node: StorageNode | null, page = 0, size = 50) {
  const identities = node ? [node.nodeId, node.baseUrl].filter(Boolean) : []

  return usePolling<Page<NodeChunk>>(
    () =>
      identities.length > 0
        ? api.nodeChunks(identities, { page, size })
        : Promise.reject(new Error('no node selected')),
    0,
    [identities.join('|'), page, size],
    identities.length > 0,
  )
}

export function useMonitoringSummary(window: MonitoringWindow = '24h', enabled = true) {
  return usePolling<MonitoringSummary>(() => api.monitoringSummary(window), 15000, [window], enabled)
}

export function useMonitoringServices(window: MonitoringWindow = '24h', enabled = true) {
  return usePolling<MonitoringServices>(() => api.monitoringServices(window), 15000, [window], enabled)
}

export function useIncidents(window: MonitoringWindow = '7d', limit = 50, enabled = true) {
  return usePolling<{ incidents: Incident[] }>(
    () => api.incidents(window, limit),
    30000,
    [window, limit],
    enabled,
  )
}

/**
 * Debounces a value — used by every search box so typing does not fire one
 * request per keystroke against a paginated endpoint.
 */
export function useDebounced<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)

  useEffect(() => {
    const timer = setTimeout(() => setDebounced(value), delayMs)
    return () => clearTimeout(timer)
  }, [value, delayMs])

  return debounced
}

/** Tracks a media query, for the layout decisions CSS alone cannot make. */
export function useMediaQuery(query: string): boolean {
  const [matches, setMatches] = useState(() =>
    typeof window === 'undefined' ? false : window.matchMedia(query).matches,
  )

  useEffect(() => {
    const media = window.matchMedia(query)
    const handler = () => setMatches(media.matches)
    handler()
    media.addEventListener('change', handler)
    return () => media.removeEventListener('change', handler)
  }, [query])

  return matches
}
