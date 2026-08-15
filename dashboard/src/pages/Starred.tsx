import { useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { Download, Search, Star, Trash2 } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Pagination } from '../components/ui/Pagination'
import { ObjectList } from '../components/objects/ObjectList'
import { useStarState } from '../components/objects/useStarState'
import { useSoftDelete } from '../components/objects/useSoftDelete'
import { objectName, READ_ONLY_REASON } from '../components/objects/helpers'
import { downloadObject } from '../lib/api'
import { useToast } from '../components/ui/toast-context'
import { toUserMessage } from '../lib/errors'
import type { ObjectRecord } from '../types/api'
import { useAuth } from '../lib/useAuth'
import { useBuckets, useDebounced, useStarred } from '../lib/hooks'

export function StarredPage() {
  const { canWrite } = useAuth()
  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const search = useDebounced(query, 300)
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

  const starred = useStarred(page, search)
  const buckets = useBuckets()

  const bucketNames = useMemo(
    () => new Map((buckets.data?.content ?? []).map((b) => [b.id, b.name])),
    [buckets.data],
  )

  /* Unstarring here removes the row it was fired from, so the listing is
     refetched once the toggle settles rather than optimistically — a row
     vanishing before the server agreed would be a lie if the call failed. */
  const star = useStarState({ onSettled: () => starred.refresh() })
  const softDelete = useSoftDelete(() => starred.refresh())

  const objects = starred.data?.content ?? []
  const isFiltered = search.trim().length > 0

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Drive"
        title="Starred"
        description="Objects you have marked to find again quickly. Stars are private to your account."
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
              placeholder="Search starred objects"
              aria-label="Search starred objects"
              className="h-9 w-full rounded-md border border-line bg-surface pl-9 pr-3 text-sm text-ink outline-none transition-colors placeholder:text-ink-4 hover:border-line-strong focus-visible:border-accent"
            />
          </div>
          {starred.data && (
            <p className="tnum shrink-0 text-[12px] text-ink-4">
              {starred.data.totalElements} {starred.data.totalElements === 1 ? 'object' : 'objects'}
            </p>
          )}
        </div>

        {starred.error && !starred.data ? (
          <div className="p-4">
            <ErrorState description={starred.error} onRetry={starred.refresh} />
          </div>
        ) : starred.loading && !starred.data ? (
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
                description="No starred object has a name like that."
                action={
                  <Button variant="secondary" size="sm" onClick={() => setQuery('')}>
                    Clear search
                  </Button>
                }
              />
            ) : (
              <EmptyState
                icon={<Star />}
                title="No starred objects yet"
                description="Star an object anywhere in your drive and it appears here, so the files you keep returning to stay one click away."
                action={
                  <Button asChild>
                    <Link to="/dashboard/objects">Browse your objects</Link>
                  </Button>
                }
              />
            )}
          </div>
        ) : (
          <>
            <ObjectList
              caption="Starred objects"
              objects={objects}
              bucketNames={bucketNames}
              showBucket
              star={star}
              starDisabledReason={canWrite ? undefined : READ_ONLY_REASON}
              href={(object) => `/dashboard/objects/${object.id}`}
              actions={(object) => [
                {
                  label: 'Download',
                  icon: <Download />,
                  onSelect: () => void download(object),
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
              page={starred.data?.number ?? 0}
              totalPages={starred.data?.totalPages ?? 1}
              totalElements={starred.data?.totalElements ?? 0}
              pageSize={starred.data?.size ?? 50}
              onChange={setPage}
              className="border-t border-line p-3"
            />
          </>
        )}
      </Card>
    </div>
  )
}
