import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  ArrowLeft,
  Boxes,
  ChevronDown,
  Download,
  FileQuestion,
  RefreshCw,
  RotateCcw,
  ShieldCheck,
  Trash2,
} from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card, CardSection } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { CopyButton } from '../components/ui/CopyButton'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { Skeleton } from '../components/ui/Skeleton'
import { Tooltip } from '../components/ui/Tooltip'
import { useToast } from '../components/ui/toast-context'
import { StarButton } from '../components/objects/StarButton'
import { useStarState } from '../components/objects/useStarState'
import { ReplicationBadge } from '../components/objects/ReplicationBadge'
import { ObjectChunkTable } from '../components/chunks/ObjectChunkTable'
import { objectFolder, objectName, READ_ONLY_REASON } from '../components/objects/helpers'
import { api, downloadObject } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { formatBytes, formatDate } from '../lib/format'
import { useAuth } from '../lib/useAuth'
import { useObjectChunks, usePolling } from '../lib/hooks'

/**
 * Everything recorded about one object.
 *
 * Fetched directly by id. An earlier version had to be handed the record
 * through router state because the gateway sent every GET on this path to the
 * download service; it now splits metadata from bytes on the Accept header,
 * so a deep link and a page reload both work.
 */
export function ObjectDetailPage() {
  const { objectId } = useParams<{ objectId: string }>()
  const navigate = useNavigate()
  const { toast } = useToast()
  const { canWrite, isAdmin } = useAuth()

  const object = usePolling(
    () => (objectId ? api.getObject(objectId) : Promise.reject(new Error('missing id'))),
    0,
    [objectId],
  )

  /* Placement is admin-only and enforced as such server-side, so the poll is
     gated on the role too — a non-admin never fires a request that would come
     back 403. */
  const chunks = useObjectChunks(objectId, isAdmin)

  const star = useStarState({ onSettled: () => object.refresh() })
  const [confirming, setConfirming] = useState(false)
  const [downloading, setDownloading] = useState(false)
  const [busy, setBusy] = useState(false)

  const data = object.data

  if (object.loading && !data) {
    return (
      <div className="mx-auto max-w-3xl space-y-4">
        <Skeleton className="h-8 w-64" />
        <Skeleton className="h-48 w-full" />
        <Skeleton className="h-32 w-full" />
      </div>
    )
  }

  if (!data) {
    return (
      <div className="mx-auto max-w-3xl">
        {object.error ? (
          <ErrorState
            title="This object could not be loaded"
            description={object.error}
            onRetry={object.refresh}
          />
        ) : (
          <EmptyState
            icon={<FileQuestion />}
            title="Object not found"
            description="It may have been permanently deleted, or the link may be wrong."
            action={
              <Button asChild>
                <Link to="/dashboard/objects">Browse objects</Link>
              </Button>
            }
          />
        )}
      </div>
    )
  }

  const trashed = data.status === 'DELETED'

  /* An authenticated fetch rather than a link: browser navigation sends no
     Authorization header, so a plain anchor comes back 401. */
  const download = async () => {
    setDownloading(true)
    try {
      await downloadObject(data.bucketId, data.key, objectName(data.key))
    } catch (error) {
      toast(toUserMessage(error, 'That file could not be downloaded.'), 'error')
    } finally {
      setDownloading(false)
    }
  }

  const softDelete = async () => {
    setBusy(true)
    try {
      await api.deleteObject(data.id)
      toast(`Moved "${objectName(data.key)}" to trash.`, 'success', {
        action: {
          label: 'Undo',
          onClick: () => {
            void api.restoreObject(data.id).then(() => object.refresh())
          },
        },
      })
      setConfirming(false)
      navigate('/dashboard/objects')
    } catch (error) {
      toast(toUserMessage(error, 'That object could not be deleted.'), 'error')
    } finally {
      setBusy(false)
    }
  }

  const restore = async () => {
    try {
      await api.restoreObject(data.id)
      toast('Restored from trash.', 'success')
      object.refresh()
    } catch (error) {
      toast(toUserMessage(error, 'That object could not be restored.'), 'error')
    }
  }

  const folder = objectFolder(data.key)

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Button asChild variant="ghost" size="sm" icon={<ArrowLeft />}>
        <Link to="/dashboard/objects">Back to objects</Link>
      </Button>

      <PageHeader
        eyebrow={folder ?? 'Object'}
        title={objectName(data.key)}
        meta={
          <>
            <Badge tone={trashed ? 'warning' : 'success'} dot>
              {trashed ? 'In trash' : 'Active'}
            </Badge>
            <ReplicationBadge object={data} />
            <span className="tnum text-[12px] text-ink-4">{formatBytes(data.sizeBytes)}</span>
          </>
        }
        actions={
          <>
            <StarButton
              starred={star.isStarred(data)}
              busy={star.isBusy(data)}
              onToggle={() => star.toggle(data)}
              name={objectName(data.key)}
              disabledReason={canWrite ? undefined : READ_ONLY_REASON}
            />
            <Button
              variant="secondary"
              icon={<Download />}
              loading={downloading}
              onClick={download}
            >
              Download
            </Button>
            {trashed ? (
              <Button variant="secondary" icon={<RotateCcw />} disabled={!canWrite} onClick={restore}>
                Restore
              </Button>
            ) : (
              <Tooltip content={canWrite ? 'Move to trash' : READ_ONLY_REASON}>
                <Button
                  variant="danger"
                  icon={<Trash2 />}
                  disabled={!canWrite}
                  onClick={() => setConfirming(true)}
                >
                  Delete
                </Button>
              </Tooltip>
            )}
          </>
        }
      />

      <CardSection title="Details" description="Recorded when the object was written.">
        <dl className="grid grid-cols-1 gap-x-6 gap-y-4 sm:grid-cols-2">
          <Detail label="Full key" value={<span className="break-all font-mono text-[12.5px]">{data.key}</span>} />
          <Detail label="Content type" value={data.contentType || 'application/octet-stream'} />
          <Detail label="Size" value={formatBytes(data.sizeBytes)} />
          <Detail label="Uploaded" value={formatDate(data.createdAt)} />
          {trashed && data.deletedAt && (
            <Detail label="Moved to trash" value={formatDate(data.deletedAt)} />
          )}
          <Detail
            label="Replication"
            value={`${data.chunksReplicated} of ${data.chunksTotal} chunks replicated`}
          />
        </dl>
      </CardSection>

      {/* Placement is cluster operations, not file management: it names other
          users' infrastructure and is admin-only on the server. Hidden rather
          than disabled, because for everyone else it may as well not exist. */}
      {isAdmin && (
        <CardSection
          icon={<Boxes />}
          title="Chunk placement"
          description="Which nodes hold each chunk of this object. Administrators only."
          actions={
            <Button
              variant="ghost"
              size="sm"
              icon={<RefreshCw />}
              onClick={chunks.refresh}
              aria-label="Refresh chunk placement"
            >
              Refresh
            </Button>
          }
        >
          <ObjectChunkTable
            chunks={chunks.data}
            loading={chunks.loading}
            error={chunks.error}
            onRetry={chunks.refresh}
          />
        </CardSection>
      )}

      {/* A 64-character hash is noise to almost everyone who opens this page,
          but it is exactly what someone verifying an archived copy needs. It
          is folded away rather than removed. */}
      <Card padded={false}>
        <details className="group">
          <summary className="flex cursor-pointer list-none items-center justify-between gap-3 p-5 text-[13px] font-medium text-ink-2 transition-colors hover:text-ink">
            <span className="flex items-center gap-2.5">
              <ShieldCheck aria-hidden className="size-4 text-ink-4" />
              Integrity
              <Badge tone="success" size="sm" dot>
                Verified on every read
              </Badge>
            </span>
            <ChevronDown
              aria-hidden
              className="size-4 shrink-0 text-ink-4 transition-transform group-open:rotate-180"
            />
          </summary>

          <div className="border-t border-line p-5">
            <p className="text-[12.5px] leading-relaxed text-ink-3">
              This SHA-256 was computed while the object was being written and is checked again
              every time it is read. If the stored bytes ever drift from it, the read fails rather
              than returning corrupt data.
            </p>
            <div className="mt-3 flex items-center gap-2 rounded-lg border border-line bg-surface-2 p-2.5">
              <code className="min-w-0 flex-1 break-all font-mono text-[12px] text-ink-2">
                {data.checksum}
              </code>
              <CopyButton value={data.checksum} />
            </div>
          </div>
        </details>
      </Card>

      <ConfirmDialog
        open={confirming}
        onClose={() => setConfirming(false)}
        onConfirm={softDelete}
        loading={busy}
        destructive
        title="Move this object to trash?"
        confirmLabel="Move to trash"
        body="It will be recoverable from Trash until you empty it."
        description={<span className="font-medium text-ink">{objectName(data.key)}</span>}
      />
    </div>
  )
}

function Detail({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="min-w-0">
      <dt className="text-[11px] font-semibold uppercase tracking-[0.08em] text-ink-4">{label}</dt>
      <dd className="mt-1 text-[13.5px] text-ink-2">{value}</dd>
    </div>
  )
}
