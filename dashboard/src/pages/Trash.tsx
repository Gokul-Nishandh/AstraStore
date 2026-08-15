import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { RotateCcw, Search, Trash2, XCircle } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Pagination } from '../components/ui/Pagination'
import { Tooltip } from '../components/ui/Tooltip'
import { useToast } from '../components/ui/toast-context'
import { ObjectList } from '../components/objects/ObjectList'
import { objectName, READ_ONLY_REASON } from '../components/objects/helpers'
import { api } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { useAuth } from '../lib/useAuth'
import { useBuckets, useDebounced, useTrash } from '../lib/hooks'
import type { ObjectRecord } from '../types/api'

export function TrashPage() {
  const { canWrite } = useAuth()
  const { toast } = useToast()

  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const search = useDebounced(query, 300)

  const trash = useTrash(page, search)
  const buckets = useBuckets()

  const [purging, setPurging] = useState<ObjectRecord | null>(null)
  const [emptying, setEmptying] = useState(false)
  const [busy, setBusy] = useState(false)

  const bucketNames = useMemo(
    () => new Map((buckets.data?.content ?? []).map((b) => [b.id, b.name])),
    [buckets.data],
  )

  const objects = trash.data?.content ?? []
  const total = trash.data?.totalElements ?? 0
  const isFiltered = search.trim().length > 0

  const restore = async (object: ObjectRecord) => {
    try {
      await api.restoreObject(object.id)
      toast(`Restored "${objectName(object.key)}".`, 'success')
      trash.refresh()
    } catch (error) {
      toast(toUserMessage(error, 'That object could not be restored.'), 'error')
    }
  }

  const purge = async () => {
    if (!purging) return
    setBusy(true)
    try {
      await api.purgeObject(purging.id)
      toast(`Permanently deleted "${objectName(purging.key)}".`, 'success')
      setPurging(null)
      trash.refresh()
    } catch (error) {
      toast(toUserMessage(error, 'That object could not be deleted.'), 'error')
    } finally {
      setBusy(false)
    }
  }

  const emptyTrash = async () => {
    setBusy(true)
    try {
      const result = await api.emptyTrash()
      toast(`Permanently deleted ${result.purged} ${result.purged === 1 ? 'object' : 'objects'}.`, 'success')
      setEmptying(false)
      setPage(0)
      trash.refresh()
    } catch (error) {
      toast(toUserMessage(error, 'Trash could not be emptied.'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Drive"
        title="Trash"
        description="Deleted objects are kept here so you can restore them. Emptying the trash destroys the stored bytes and cannot be undone."
        actions={
          total > 0 && (
            <Tooltip content={canWrite ? 'Permanently delete everything here' : READ_ONLY_REASON}>
              <Button
                variant="danger"
                icon={<XCircle />}
                disabled={!canWrite}
                onClick={() => setEmptying(true)}
              >
                Empty trash
              </Button>
            </Tooltip>
          )
        }
      />

      <Card padded={false}>
        <div className="flex flex-wrap items-center gap-3 border-b border-line p-3">
          <div className="relative min-w-0 flex-1 basis-56">
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
              placeholder="Search trashed objects"
              aria-label="Search trashed objects"
              className="h-9 w-full rounded-md border border-line bg-surface pl-9 pr-3 text-sm text-ink outline-none transition-colors placeholder:text-ink-4 hover:border-line-strong focus-visible:border-accent"
            />
          </div>
          {trash.data && (
            <p className="tnum shrink-0 text-[12px] text-ink-4">
              {total} {total === 1 ? 'object' : 'objects'}
            </p>
          )}
        </div>

        {trash.error && !trash.data ? (
          <div className="p-4">
            <ErrorState description={trash.error} onRetry={trash.refresh} />
          </div>
        ) : trash.loading && !trash.data ? (
          <table className="w-full">
            <tbody>
              <SkeletonRows rows={6} cols={4} />
            </tbody>
          </table>
        ) : objects.length === 0 ? (
          <div className="p-4">
            {isFiltered ? (
              <EmptyState
                icon={<Search />}
                size="sm"
                title="Nothing matches that search"
                description="No object in the trash has a name like that."
                action={
                  <Button variant="secondary" size="sm" onClick={() => setQuery('')}>
                    Clear search
                  </Button>
                }
              />
            ) : (
              <EmptyState
                icon={<Trash2 />}
                title="Trash is empty"
                description="Objects you delete land here first. Nothing is destroyed until you empty the trash or delete an item permanently."
                action={
                  <Button asChild variant="secondary">
                    <Link to="/dashboard">Back to my drive</Link>
                  </Button>
                }
              />
            )}
          </div>
        ) : (
          <>
            {/* Struck-through names and the deletion timestamp, so a trashed
                object never reads like a live one at a glance. */}
            <ObjectList
              caption="Objects in the trash"
              objects={objects}
              bucketNames={bucketNames}
              showBucket
              struck
              timestamp="deletedAt"
              timestampLabel="Deleted"
              showReplication={false}
              actions={(object) => [
                {
                  label: 'Restore',
                  icon: <RotateCcw />,
                  disabled: !canWrite,
                  onSelect: () => void restore(object),
                },
                {
                  label: 'Delete permanently',
                  icon: <XCircle />,
                  destructive: true,
                  disabled: !canWrite,
                  onSelect: () => setPurging(object),
                },
              ]}
            />
            <Pagination
              page={trash.data?.number ?? 0}
              totalPages={trash.data?.totalPages ?? 1}
              totalElements={total}
              pageSize={trash.data?.size ?? 50}
              onChange={setPage}
              className="border-t border-line p-3"
            />
          </>
        )}
      </Card>

      <ConfirmDialog
        open={purging !== null}
        onClose={() => setPurging(null)}
        onConfirm={purge}
        loading={busy}
        destructive
        title="Delete this object permanently?"
        confirmLabel="Delete permanently"
        description={
          <>
            <span className="font-medium text-ink">{purging ? objectName(purging.key) : ''}</span> and
            every stored copy of its data will be destroyed. This cannot be undone.
          </>
        }
      />

      <ConfirmDialog
        open={emptying}
        onClose={() => setEmptying(false)}
        onConfirm={emptyTrash}
        loading={busy}
        destructive
        title="Empty the trash?"
        confirmLabel={`Delete ${total} ${total === 1 ? 'object' : 'objects'}`}
        description={
          <>
            All {total} {total === 1 ? 'object' : 'objects'} in the trash will be destroyed, along with
            every stored copy of their data. This cannot be undone.
          </>
        }
      />
    </div>
  )
}
