import { useMemo } from 'react'
import { Link } from 'react-router-dom'
import { Clock, Download, Trash2, Upload } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { SkeletonRows } from '../components/ui/Skeleton'
import { ObjectList } from '../components/objects/ObjectList'
import { useStarState } from '../components/objects/useStarState'
import { useSoftDelete } from '../components/objects/useSoftDelete'
import { objectName, READ_ONLY_REASON } from '../components/objects/helpers'
import { downloadObject } from '../lib/api'
import { useToast } from '../components/ui/toast-context'
import { toUserMessage } from '../lib/errors'
import type { ObjectRecord } from '../types/api'
import { useAuth } from '../lib/useAuth'
import { useBuckets, useRecentObjects } from '../lib/hooks'

export function RecentPage() {
  const { canWrite } = useAuth()
  const recent = useRecentObjects(50)
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
  const buckets = useBuckets()

  const bucketNames = useMemo(
    () => new Map((buckets.data?.content ?? []).map((b) => [b.id, b.name])),
    [buckets.data],
  )

  /* Starring here does not reorder or remove the row, so the optimistic
     toggle can stand on its own without a refetch. */
  const star = useStarState()
  const softDelete = useSoftDelete(() => recent.refresh())

  const objects = recent.data ?? []

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Drive"
        title="Recent"
        description="Your most recently uploaded objects, newest first, across every bucket you own."
      />

      <Card padded={false}>
        {recent.error && !recent.data ? (
          <div className="p-4">
            <ErrorState description={recent.error} onRetry={recent.refresh} />
          </div>
        ) : recent.loading && !recent.data ? (
          <table className="w-full">
            <tbody>
              <SkeletonRows rows={8} cols={4} />
            </tbody>
          </table>
        ) : objects.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={<Clock />}
              title="Nothing uploaded yet"
              description="Once you upload an object it appears here, so you can pick up where you left off without hunting through buckets."
              action={
                <Button asChild icon={<Upload />}>
                  <Link to="/dashboard/objects?upload=1">Upload an object</Link>
                </Button>
              }
            />
          </div>
        ) : (
          <ObjectList
            caption="Recently uploaded objects"
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
        )}
      </Card>
    </div>
  )
}
