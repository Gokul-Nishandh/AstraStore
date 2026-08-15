import { useEffect, useMemo, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Database, Download, FolderPlus, Package, Search, Trash2, Upload } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Select } from '../components/ui/Select'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Pagination } from '../components/ui/Pagination'
import { Tooltip } from '../components/ui/Tooltip'
import { useToast } from '../components/ui/toast-context'
import { CreateBucketDialog } from '../components/buckets/CreateBucketDialog'
import { UploadModal } from '../components/buckets/UploadModal'
import { ObjectList } from '../components/objects/ObjectList'
import { useStarState } from '../components/objects/useStarState'
import { useSoftDelete } from '../components/objects/useSoftDelete'
import { objectName, READ_ONLY_REASON } from '../components/objects/helpers'
import { api, downloadObject } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import type { ObjectRecord } from '../types/api'
import { useAuth } from '../lib/useAuth'
import { useBuckets, useDebounced, useObjects } from '../lib/hooks'

/**
 * Browse one bucket at a time.
 *
 * The selected bucket lives in the query string rather than component state,
 * so a particular bucket can be linked to and survives a reload. `?upload=1`
 * opens the upload dialog, which is what the Upload button in the app shell
 * points at.
 */
export function ObjectExplorerPage() {
  const { canWrite } = useAuth()
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
  const [params, setParams] = useSearchParams()

  const buckets = useBuckets()
  const bucketList = useMemo(() => buckets.data?.content ?? [], [buckets.data])

  const bucketId = params.get('bucket')
  const selected = bucketList.find((b) => b.id === bucketId) ?? bucketList[0] ?? null

  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const search = useDebounced(query, 300)

  const objects = useObjects(selected?.id ?? null, page, search)

  const [creating, setCreating] = useState(false)
  const [deletingBucket, setDeletingBucket] = useState(false)
  const [busy, setBusy] = useState(false)

  const uploadOpen = params.get('upload') === '1'
  const setUploadOpen = (open: boolean) => {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        if (open) next.set('upload', '1')
        else next.delete('upload')
        return next
      },
      { replace: true },
    )
  }

  // Once buckets load, pin the resolved selection into the URL so a shared
  // link points at the same bucket the sender was looking at.
  useEffect(() => {
    if (selected && !bucketId) {
      setParams(
        (current) => {
          const next = new URLSearchParams(current)
          next.set('bucket', selected.id)
          return next
        },
        { replace: true },
      )
    }
  }, [selected, bucketId, setParams])

  const star = useStarState()
  const softDelete = useSoftDelete(() => objects.refresh())

  const rows = objects.data?.content ?? []
  const isFiltered = search.trim().length > 0

  const deleteBucket = async () => {
    if (!selected) return
    setBusy(true)
    try {
      await api.deleteBucket(selected.id)
      toast(`Deleted bucket "${selected.name}".`, 'success')
      setDeletingBucket(false)
      setParams({}, { replace: true })
      buckets.refresh()
    } catch (error) {
      toast(toUserMessage(error, 'That bucket could not be deleted.'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Drive"
        title="Objects"
        description="Browse a bucket, upload new objects, and manage what is already stored."
        actions={
          <>
            <Tooltip content={canWrite ? 'Create a new bucket' : READ_ONLY_REASON}>
              <Button
                variant="secondary"
                icon={<FolderPlus />}
                disabled={!canWrite}
                onClick={() => setCreating(true)}
              >
                New bucket
              </Button>
            </Tooltip>
            <Tooltip content={canWrite ? 'Upload into this bucket' : READ_ONLY_REASON}>
              <Button
                icon={<Upload />}
                disabled={!canWrite || !selected}
                onClick={() => setUploadOpen(true)}
              >
                Upload
              </Button>
            </Tooltip>
          </>
        }
      />

      {buckets.error && !buckets.data ? (
        <ErrorState description={buckets.error} onRetry={buckets.refresh} />
      ) : buckets.loading && !buckets.data ? (
        <Card>
          <SkeletonRows rows={3} cols={3} />
        </Card>
      ) : bucketList.length === 0 ? (
        <Card>
          <EmptyState
            icon={<Database />}
            title="Create your first bucket"
            description="Buckets are the top-level containers your objects live in. You need one before you can upload anything."
            action={
              <Tooltip content={canWrite ? undefined : READ_ONLY_REASON}>
                <Button icon={<FolderPlus />} disabled={!canWrite} onClick={() => setCreating(true)}>
                  Create a bucket
                </Button>
              </Tooltip>
            }
          />
        </Card>
      ) : (
        <Card padded={false}>
          <div className="flex flex-wrap items-center gap-3 border-b border-line p-3">
            <div className="w-full shrink-0 sm:w-56">
              <Select
                aria-label="Bucket"
                value={selected?.id ?? ''}
                onChange={(e) => {
                  setPage(0)
                  setQuery('')
                  setParams({ bucket: e.target.value }, { replace: true })
                }}
                options={bucketList.map((b) => ({ value: b.id, label: b.name }))}
              />
            </div>

            <div className="relative min-w-0 flex-1 basis-48">
              <Search
                aria-hidden
                className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-4"
              />
              <input
                type="search"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value)
                  setPage(0)
                }}
                placeholder="Search this bucket"
                aria-label="Search objects in this bucket"
                className="h-9 w-full rounded-md border border-line bg-surface pl-9 pr-3 text-sm text-ink outline-none transition-colors placeholder:text-ink-4 hover:border-line-strong focus-visible:border-accent"
              />
            </div>

            {objects.data && (
              <p className="tnum shrink-0 text-[12px] text-ink-4">
                {objects.data.totalElements}{' '}
                {objects.data.totalElements === 1 ? 'object' : 'objects'}
              </p>
            )}

            <Tooltip content={canWrite ? 'Delete this bucket' : READ_ONLY_REASON}>
              <Button
                variant="ghost"
                size="icon-sm"
                aria-label="Delete this bucket"
                disabled={!canWrite}
                onClick={() => setDeletingBucket(true)}
              >
                <Trash2 className="size-4" />
              </Button>
            </Tooltip>
          </div>

          {objects.error && !objects.data ? (
            <div className="p-4">
              <ErrorState description={objects.error} onRetry={objects.refresh} />
            </div>
          ) : objects.loading && !objects.data ? (
            <table className="w-full">
              <tbody>
                <SkeletonRows rows={8} cols={4} />
              </tbody>
            </table>
          ) : rows.length === 0 ? (
            <div className="p-4">
              {isFiltered ? (
                <EmptyState
                  icon={<Search />}
                  size="sm"
                  title="Nothing matches that search"
                  description={`No object in ${selected?.name} has a name like that.`}
                  action={
                    <Button variant="secondary" size="sm" onClick={() => setQuery('')}>
                      Clear search
                    </Button>
                  }
                />
              ) : (
                <EmptyState
                  icon={<Package />}
                  title={`${selected?.name} is empty`}
                  description="Upload an object and it will be chunked, checksummed and replicated across the cluster as it arrives."
                  action={
                    <Tooltip content={canWrite ? undefined : READ_ONLY_REASON}>
                      <Button icon={<Upload />} disabled={!canWrite} onClick={() => setUploadOpen(true)}>
                        Upload an object
                      </Button>
                    </Tooltip>
                  }
                />
              )}
            </div>
          ) : (
            <>
              <ObjectList
                caption={`Objects in ${selected?.name}`}
                objects={rows}
                star={star}
                starDisabledReason={canWrite ? undefined : READ_ONLY_REASON}
                href={(object) => `/dashboard/objects/${object.id}`}
                actions={(object) => [
                  {
                    label: 'Download',
                    icon: <Download />,
                    onSelect: () =>
                      void download(object),
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
              <Pagination
                page={objects.data?.number ?? 0}
                totalPages={objects.data?.totalPages ?? 1}
                totalElements={objects.data?.totalElements ?? 0}
                pageSize={objects.data?.size ?? 50}
                onChange={setPage}
                className="border-t border-line p-3"
              />
            </>
          )}
        </Card>
      )}

      <CreateBucketDialog
        open={creating}
        onClose={() => setCreating(false)}
        onCreated={(bucket) => {
          setCreating(false)
          buckets.refresh()
          setParams({ bucket: bucket.id }, { replace: true })
        }}
      />

      {selected && (
        <UploadModal
          open={uploadOpen}
          bucketId={selected.id}
          bucketName={selected.name}
          onClose={() => setUploadOpen(false)}
          onUploaded={() => objects.refresh()}
        />
      )}

      <ConfirmDialog
        open={deletingBucket}
        onClose={() => setDeletingBucket(false)}
        onConfirm={deleteBucket}
        loading={busy}
        destructive
        title="Delete this bucket?"
        confirmLabel="Delete bucket"
        description={<span className="font-medium text-ink">{selected?.name}</span>}
        body="A bucket can only be deleted once it is empty. Move its objects to trash first if it still holds any."
      />

      <p className="text-center text-[12.5px] text-ink-4">
        Looking for something you deleted?{' '}
        <Link to="/dashboard/trash" className="text-accent-text underline underline-offset-2">
          Check the trash
        </Link>
        .
      </p>
    </div>
  )
}
