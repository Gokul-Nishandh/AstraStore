import { useMemo, useState, type ReactNode } from 'react'
import { Link } from 'react-router-dom'
import {
  ArrowRight,
  Clock,
  Database,
  Download,
  FolderPlus,
  HardDrive,
  KeyRound,
  Package,
  Sparkles,
  Star,
  Trash2,
  Upload,
} from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card, CardHeader, CardTitle, CardDescription } from '../components/ui/Card'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { Skeleton, SkeletonTile } from '../components/ui/Skeleton'
import { Tooltip } from '../components/ui/Tooltip'
import { useToast } from '../components/ui/toast-context'
import { CreateBucketDialog } from '../components/buckets/CreateBucketDialog'
import { ObjectList } from '../components/objects/ObjectList'
import { StorageByType, UploadActivity } from '../components/charts/StoragePanels'
import { useStarState } from '../components/objects/useStarState'
import { useSoftDelete } from '../components/objects/useSoftDelete'
import { objectName, READ_ONLY_REASON } from '../components/objects/helpers'
import { useOnboarding } from '../components/onboarding/onboarding-state'
import { api, downloadObject } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { formatBytes, formatCount, formatDate } from '../lib/format'
import { useBuckets, useRecentObjects, useStats, useStorageBreakdown, type AsyncState } from '../lib/hooks'
import { useAuth } from '../lib/useAuth'
import { cn } from '../lib/cn'
import type { Bucket, ObjectRecord, UserStats } from '../types/api'

export function DrivePage() {
  const { user, canWrite } = useAuth()
  const { toast } = useToast()

  /* Downloads go through an authenticated fetch: a plain navigation carries
     no Authorization header and comes back 401. */
  const download = async (object: ObjectRecord) => {
    try {
      await downloadObject(object.bucketId, object.key, objectName(object.key))
    } catch (error) {
      toast(toUserMessage(error, 'That file could not be downloaded.'), 'error')
    }
  }

  const stats = useStats()
  const buckets = useBuckets()
  const recent = useRecentObjects(6)
  const breakdown = useStorageBreakdown(30)
  const onboarding = useOnboarding(user?.userId)

  const [createOpen, setCreateOpen] = useState(false)
  const [pendingDelete, setPendingDelete] = useState<Bucket | null>(null)

  const refreshAll = () => {
    stats.refresh()
    buckets.refresh()
    recent.refresh()
  }

  const star = useStarState({ onSettled: () => stats.refresh() })
  const softDelete = useSoftDelete(refreshAll)

  const bucketList = useMemo(() => buckets.data?.content ?? [], [buckets.data])
  const bucketNames = useMemo(
    () => new Map(bucketList.map((bucket) => [bucket.id, bucket.name])),
    [bucketList],
  )

  const deleteBucket = async () => {
    if (!pendingDelete) return
    try {
      await api.deleteBucket(pendingDelete.id)
      toast(`Bucket “${pendingDelete.name}” deleted`, 'success')
      setPendingDelete(null)
      refreshAll()
    } catch (error) {
      toast(toUserMessage(error, 'Could not delete that bucket.'), 'error')
    }
  }

  const firstRun = !buckets.loading && !buckets.error && bucketList.length === 0
  const showResume =
    !onboarding.state.completed &&
    !onboarding.state.dismissed &&
    Object.values(onboarding.state.steps).some(Boolean)

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Storage"
        title="My Drive"
        description="Everything you have stored in AstraStore, and the fastest routes back to it."
        actions={
          <>
            <WriteGuard canWrite={canWrite}>
              <Button
                variant="secondary"
                icon={<FolderPlus />}
                disabled={!canWrite}
                onClick={() => setCreateOpen(true)}
              >
                New bucket
              </Button>
            </WriteGuard>
            {canWrite ? (
              <Button asChild>
                <Link to="/dashboard/objects?upload=1">
                  <Upload aria-hidden className="size-4" />
                  Upload
                </Link>
              </Button>
            ) : (
              <WriteGuard canWrite={canWrite}>
                <Button icon={<Upload />} disabled>
                  Upload
                </Button>
              </WriteGuard>
            )}
          </>
        }
      />

      {showResume && (
        <Card className="flex flex-wrap items-center justify-between gap-4 border-accent-border">
          <div className="flex min-w-0 items-start gap-3">
            <span
              aria-hidden
              className="grid size-9 shrink-0 place-items-center rounded-lg border border-accent-border bg-accent-subtle text-accent-text"
            >
              <Sparkles className="size-4" />
            </span>
            <div className="min-w-0">
              <p className="text-[13.5px] font-medium text-ink">Finish setting up your account</p>
              <p className="mt-0.5 text-[12.5px] text-ink-3">
                You started the guided setup — pick up where you left off.
              </p>
            </div>
          </div>
          <div className="flex items-center gap-2">
            <Button variant="ghost" size="sm" onClick={onboarding.dismiss}>
              Not now
            </Button>
            <Button asChild variant="secondary" size="sm">
              <Link to="/onboarding">Resume setup</Link>
            </Button>
          </div>
        </Card>
      )}

      <StatsRow state={stats} />

      {firstRun ? (
        <EmptyState
          icon={<Database className="size-6" />}
          title="Your drive is empty"
          description="Objects live inside buckets. Create your first one and you can upload straight away — replication across the cluster is automatic."
          action={
            <WriteGuard canWrite={canWrite}>
              <Button icon={<FolderPlus />} disabled={!canWrite} onClick={() => setCreateOpen(true)}>
                Create your first bucket
              </Button>
            </WriteGuard>
          }
          secondaryAction={
            canWrite ? (
              <Button asChild variant="ghost">
                <Link to="/onboarding">Take the guided setup</Link>
              </Button>
            ) : null
          }
        />
      ) : (
        <div className="grid items-start gap-5 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.7fr)]">
          <BucketsPanel
            buckets={bucketList}
            loading={buckets.loading}
            error={buckets.error}
            onRetry={buckets.refresh}
            onCreate={() => setCreateOpen(true)}
            onDelete={setPendingDelete}
            canWrite={canWrite}
          />

          <Card className="overflow-hidden p-0">
            <CardHeader className="border-b border-line px-5 py-4">
              <div className="min-w-0">
                <CardTitle>Recent objects</CardTitle>
                <CardDescription>The latest uploads across every bucket.</CardDescription>
              </div>
              <Button asChild variant="ghost" size="sm">
                <Link to="/dashboard/recent">
                  View all
                  <ArrowRight aria-hidden className="size-3.5" />
                </Link>
              </Button>
            </CardHeader>

            {recent.loading ? (
              <div className="space-y-3 p-5">
                {Array.from({ length: 4 }).map((_, index) => (
                  <Skeleton key={index} className="h-11 w-full" />
                ))}
              </div>
            ) : recent.error && !recent.data ? (
              <div className="p-5">
                <ErrorState description={recent.error} onRetry={recent.refresh} />
              </div>
            ) : (recent.data?.length ?? 0) === 0 ? (
              <div className="p-5">
                <EmptyState
                  size="sm"
                  icon={<Package className="size-5" />}
                  title="Nothing uploaded yet"
                  description="Objects you upload appear here, newest first."
                  action={
                    canWrite ? (
                      <Button asChild size="sm">
                        <Link to="/dashboard/objects?upload=1">
                          <Upload aria-hidden className="size-3.5" />
                          Upload a file
                        </Link>
                      </Button>
                    ) : null
                  }
                />
              </div>
            ) : (
              <div className="px-3 sm:px-0">
                <ObjectList
                  caption="Recently uploaded objects"
                  objects={recent.data ?? []}
                  bucketNames={bucketNames}
                  // Name and size only in this panel; the Recent page carries
                  // the full detail without fighting the sidebar for width.
                  compact
                  star={star}
                  starDisabledReason={canWrite ? undefined : READ_ONLY_REASON}
                  href={(object) => `/dashboard/objects/${object.id}`}
                  actions={(object) => [
                    {
                      label: 'Download',
                      icon: <Download />,
                      onSelect: () => {
                        void download(object)
                      },
                    },
                    {
                      label: 'Move to trash',
                      icon: <Trash2 />,
                      destructive: true,
                      disabled: !canWrite,
                      onSelect: () => void softDelete(object),
                    },
                  ]}
                />
              </div>
            )}
          </Card>
        </div>
      )}

      {!firstRun && (
        <div className="grid gap-5 lg:grid-cols-2">
          <StorageByType state={breakdown} />
          <UploadActivity state={breakdown} />
        </div>
      )}

      <QuickActions canWrite={canWrite} />

      <CreateBucketDialog
        open={createOpen}
        onClose={() => setCreateOpen(false)}
        onCreated={() => {
          setCreateOpen(false)
          refreshAll()
        }}
      />

      <ConfirmDialog
        open={pendingDelete !== null}
        destructive
        title="Delete this bucket?"
        description={pendingDelete ? `“${pendingDelete.name}”` : undefined}
        body="The bucket and its listing are removed from your drive. This cannot be undone."
        confirmLabel="Delete bucket"
        onConfirm={deleteBucket}
        onClose={() => setPendingDelete(null)}
      />
    </div>
  )
}

/**
 * Explains a control a READ_ONLY account cannot use instead of letting the
 * request come back 403. The pointer-events reset is what lets the tooltip
 * fire at all — a disabled button dispatches no events of its own.
 */
function WriteGuard({ canWrite, children }: { canWrite: boolean; children: ReactNode }) {
  if (canWrite) return <>{children}</>
  return (
    <Tooltip content={READ_ONLY_REASON}>
      <span className="[&_button:disabled]:pointer-events-none">{children}</span>
    </Tooltip>
  )
}

/**
 * Totals come from `api.stats()`, counted in the database.
 *
 * Deliberately not derived from a page of results: summing the six objects
 * on screen and calling it "stored" is how a drive holding gigabytes ends up
 * reporting a few megabytes.
 */
function StatsRow({ state }: { state: AsyncState<UserStats> }) {
  if (state.loading) {
    return (
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
        {Array.from({ length: 5 }).map((_, index) => (
          <SkeletonTile key={index} />
        ))}
      </div>
    )
  }

  if (!state.data) {
    return (
      <ErrorState
        title="Totals unavailable"
        description={state.error ?? 'Your storage totals could not be loaded.'}
        onRetry={state.refresh}
      />
    )
  }

  const stats = state.data
  const tiles = [
    { icon: Package, label: 'Objects', value: formatCount(stats.objectCount), to: '/dashboard/objects' },
    { icon: HardDrive, label: 'Stored', value: formatBytes(stats.totalBytes), hint: 'What you uploaded' },
    { icon: Database, label: 'Buckets', value: formatCount(stats.bucketCount) },
    { icon: Star, label: 'Starred', value: formatCount(stats.starredCount), to: '/dashboard/starred' },
    { icon: Trash2, label: 'In trash', value: formatCount(stats.trashedCount), to: '/dashboard/trash' },
  ]

  return (
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-5">
      {tiles.map((tile) => {
        const body = (
          <>
            <div className="flex items-center gap-2">
              <tile.icon aria-hidden className="size-3.5 shrink-0 text-ink-4" />
              <p className="truncate text-[11px] font-semibold uppercase tracking-wide text-ink-4">
                {tile.label}
              </p>
            </div>
            <p className="tnum mt-2 truncate text-[22px] font-semibold leading-none text-ink">{tile.value}</p>
            <p className="mt-1.5 text-[11.5px] text-ink-4">{tile.hint ?? ' '}</p>
          </>
        )

        const shell = cn(
          'block rounded-xl border border-line bg-surface p-4 shadow-xs transition-colors',
          tile.to && 'hover:border-accent-border',
        )

        return tile.to ? (
          <Link key={tile.label} to={tile.to} className={shell}>
            {body}
          </Link>
        ) : (
          <div key={tile.label} className={shell}>
            {body}
          </div>
        )
      })}
    </div>
  )
}

function BucketsPanel({
  buckets,
  loading,
  error,
  onRetry,
  onCreate,
  onDelete,
  canWrite,
}: {
  buckets: Bucket[]
  loading: boolean
  error: string | null
  onRetry: () => void
  onCreate: () => void
  onDelete: (bucket: Bucket) => void
  canWrite: boolean
}) {
  return (
    <Card className="p-0">
      <CardHeader className="border-b border-line px-5 py-4">
        <div className="min-w-0">
          <CardTitle>Buckets</CardTitle>
          <CardDescription>Where your objects are grouped.</CardDescription>
        </div>
        {canWrite && (
          <Button variant="ghost" size="sm" icon={<FolderPlus />} onClick={onCreate}>
            New
          </Button>
        )}
      </CardHeader>

      <div className="p-2">
        {loading ? (
          <div className="space-y-2 p-3">
            {Array.from({ length: 3 }).map((_, index) => (
              <Skeleton key={index} className="h-11 w-full" />
            ))}
          </div>
        ) : error && buckets.length === 0 ? (
          <div className="p-3">
            <ErrorState description={error} onRetry={onRetry} />
          </div>
        ) : buckets.length === 0 ? (
          <div className="p-3">
            <EmptyState
              size="sm"
              icon={<Database className="size-5" />}
              title="No buckets"
              description="Create one to start uploading."
              action={
                canWrite ? (
                  <Button size="sm" icon={<FolderPlus />} onClick={onCreate}>
                    New bucket
                  </Button>
                ) : null
              }
            />
          </div>
        ) : (
          <ul>
            {buckets.map((bucket) => (
              <li key={bucket.id} className="flex items-center gap-1">
                <Link
                  to={`/dashboard/objects?bucket=${encodeURIComponent(bucket.id)}`}
                  className="flex min-h-11 min-w-0 flex-1 items-center gap-3 rounded-lg px-3 py-1.5 transition-colors hover:bg-surface-2"
                >
                  <span
                    aria-hidden
                    className="grid size-8 shrink-0 place-items-center rounded-lg border border-accent-border bg-accent-subtle text-accent-text"
                  >
                    <Database className="size-4" />
                  </span>
                  <span className="min-w-0">
                    <span className="block truncate text-[13.5px] font-medium text-ink">{bucket.name}</span>
                    <span className="tnum block truncate text-[11.5px] text-ink-4">
                      Created {formatDate(bucket.createdAt)}
                    </span>
                  </span>
                </Link>
                {canWrite && (
                  <button
                    type="button"
                    onClick={() => onDelete(bucket)}
                    aria-label={`Delete bucket ${bucket.name}`}
                    className="grid size-11 shrink-0 place-items-center rounded-md text-ink-4 transition-colors hover:bg-danger-subtle hover:text-danger-text sm:size-9"
                  >
                    <Trash2 aria-hidden className="size-4" />
                  </button>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Card>
  )
}

function QuickActions({ canWrite }: { canWrite: boolean }) {
  const actions = [
    {
      to: '/dashboard/objects',
      icon: Package,
      title: 'Browse objects',
      body: 'Search a bucket and inspect any object.',
    },
    {
      to: '/dashboard/recent',
      icon: Clock,
      title: 'Recent activity',
      body: 'Your newest uploads, in one list.',
    },
    {
      to: '/dashboard/api-keys',
      icon: KeyRound,
      title: canWrite ? 'Issue an API key' : 'Your API keys',
      body: 'Authenticate scripts and SDKs.',
    },
  ]

  /* One panel divided by hairlines rather than three separate cards. Three
     equally weighted boxes read as filler; a single divided strip reads as
     one piece of furniture and costs a third of the vertical space. The
     divider flips with the axis, since a vertical rule between stacked rows
     is meaningless. */
  return (
    <div className="overflow-hidden rounded-xl border border-line bg-surface shadow-xs">
      <div className="grid divide-y divide-line sm:grid-cols-3 sm:divide-x sm:divide-y-0">
        {actions.map((action) => (
          <Link
            key={action.to}
            to={action.to}
            className="group flex items-center gap-3.5 p-4 transition-colors hover:bg-surface-2"
          >
            <span
              aria-hidden
              className="grid size-10 shrink-0 place-items-center rounded-lg border border-line bg-surface-2 text-ink-3 transition-colors group-hover:border-accent-border group-hover:bg-accent-subtle group-hover:text-accent-text"
            >
              <action.icon className="size-[18px]" />
            </span>

            <span className="min-w-0 flex-1">
              <span className="block text-[13.5px] font-medium text-ink">{action.title}</span>
              <span className="mt-0.5 block text-pretty text-[12px] leading-relaxed text-ink-4">
                {action.body}
              </span>
            </span>

            <ArrowRight
              aria-hidden
              className="size-4 shrink-0 text-ink-4 transition-all group-hover:translate-x-0.5 group-hover:text-accent-text"
            />
          </Link>
        ))}
      </div>
    </div>
  )
}
