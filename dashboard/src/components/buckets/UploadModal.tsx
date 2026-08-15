import { useCallback, useRef, useState, type DragEvent } from 'react'
import { AlertCircle, CheckCircle2, UploadCloud, X, XCircle } from 'lucide-react'
import { Dialog } from '../ui/Dialog'
import { Button } from '../ui/Button'
import { Input } from '../ui/Field'
import { ProgressBar } from '../ui/ProgressBar'
import { Spinner } from '../ui/Spinner'
import { useToast } from '../ui/toast-context'
import { FileTile } from '../objects/FileIcon'
import { uploadObject } from '../../lib/api'
import { toUserMessage } from '../../lib/errors'
import { formatBytes } from '../../lib/format'
import { validateObjectKey } from '../../lib/validation'
import { cn } from '../../lib/cn'

type ItemStatus = 'queued' | 'uploading' | 'done' | 'failed' | 'cancelled'

interface UploadItem {
  id: string
  file: File
  key: string
  status: ItemStatus
  percent: number
  error?: string
}

export interface UploadModalProps {
  open: boolean
  bucketId: string
  bucketName: string
  onClose: () => void
  /** Fired after each successful file, so the listing behind stays honest. */
  onUploaded: () => void
}

/** Joins an optional folder prefix to a file name without doubling slashes. */
function buildKey(prefix: string, fileName: string): string {
  const clean = prefix.trim().replace(/^\/+|\/+$/g, '')
  return clean ? `${clean}/${fileName}` : fileName
}

export function UploadModal({ open, bucketId, bucketName, onClose, onUploaded }: UploadModalProps) {
  const { toast } = useToast()
  const inputRef = useRef<HTMLInputElement>(null)
  const nextId = useRef(0)
  // Live handles on the in-flight request, so Cancel actually stops bytes
  // leaving the machine rather than only hiding the row.
  const controllers = useRef(new Map<string, AbortController>())

  const [items, setItems] = useState<UploadItem[]>([])
  const [prefix, setPrefix] = useState('')
  const [dragging, setDragging] = useState(false)
  const [running, setRunning] = useState(false)

  const patch = useCallback((id: string, changes: Partial<UploadItem>) => {
    setItems((current) => current.map((item) => (item.id === id ? { ...item, ...changes } : item)))
  }, [])

  const addFiles = useCallback((files: FileList | File[] | null) => {
    if (!files) return
    const added = Array.from(files).map<UploadItem>((file) => ({
      id: `upload-${nextId.current++}`,
      file,
      key: file.name,
      status: 'queued',
      percent: 0,
    }))
    if (added.length > 0) setItems((current) => [...current, ...added])
  }, [])

  const removeItem = (id: string) => {
    controllers.current.get(id)?.abort()
    setItems((current) => current.filter((item) => item.id !== id))
  }

  const close = () => {
    if (running) return
    setItems([])
    setPrefix('')
    onClose()
  }

  const run = async () => {
    const pending = items.filter((item) => item.status === 'queued' || item.status === 'failed')
    if (pending.length === 0) return

    setRunning(true)
    let succeeded = 0
    let failed = 0

    // Sequential on purpose: a browser will happily open six sockets and
    // starve them all, which makes every per-file progress bar meaningless.
    for (const item of pending) {
      const key = buildKey(prefix, item.file.name)
      const invalid = validateObjectKey(key)
      if (invalid) {
        patch(item.id, { status: 'failed', error: invalid, key })
        failed += 1
        continue
      }

      const controller = new AbortController()
      controllers.current.set(item.id, controller)
      patch(item.id, { status: 'uploading', percent: 0, error: undefined, key })

      try {
        await uploadObject(bucketId, key, item.file, {
          onProgress: (percent) => patch(item.id, { percent }),
          signal: controller.signal,
        })
        patch(item.id, { status: 'done', percent: 100 })
        succeeded += 1
        onUploaded()
      } catch (error) {
        // One refusal must not take the batch with it: the loop carries on
        // and the row keeps its own reason.
        if (error instanceof DOMException && error.name === 'AbortError') {
          patch(item.id, { status: 'cancelled' })
        } else {
          patch(item.id, {
            status: 'failed',
            error: toUserMessage(error, 'That file could not be uploaded.'),
          })
          failed += 1
        }
      } finally {
        controllers.current.delete(item.id)
      }
    }

    setRunning(false)

    if (succeeded > 0 && failed === 0) {
      toast(succeeded === 1 ? 'File uploaded' : `${succeeded} files uploaded`, 'success', {
        description: `Stored in “${bucketName}”.`,
      })
    } else if (succeeded > 0) {
      toast(`${succeeded} uploaded, ${failed} failed`, 'warning', {
        description: 'The files that failed are still listed, with the reason.',
      })
    } else if (failed > 0) {
      toast(failed === 1 ? 'Upload failed' : `${failed} uploads failed`, 'error')
    }
  }

  const onDrop = (event: DragEvent) => {
    event.preventDefault()
    setDragging(false)
    if (!running) addFiles(event.dataTransfer.files)
  }

  const pending = items.filter((item) => item.status === 'queued' || item.status === 'failed')
  const uploaded = items.filter((item) => item.status === 'done')
  const retrying = pending.some((item) => item.status === 'failed') && !running

  return (
    <Dialog
      open={open}
      onClose={close}
      size="lg"
      hideClose={running}
      dismissOnBackdrop={!running}
      title="Upload files"
      description={`Into “${bucketName}”`}
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={running}>
            {uploaded.length > 0 && pending.length === 0 ? 'Done' : 'Cancel'}
          </Button>
          <Button onClick={run} disabled={pending.length === 0 || running} loading={running}>
            {retrying ? 'Retry failed' : pending.length > 1 ? `Upload ${pending.length} files` : 'Upload'}
          </Button>
        </>
      }
    >
      <div className="space-y-4">
        <div
          onDragOver={(event) => {
            event.preventDefault()
            if (!running) setDragging(true)
          }}
          onDragLeave={() => setDragging(false)}
          onDrop={onDrop}
          className={cn(
            'rounded-xl border border-dashed px-6 py-8 text-center transition-colors duration-150',
            dragging ? 'border-accent bg-accent-subtle' : 'border-line-strong',
            running && 'opacity-60',
          )}
        >
          <UploadCloud aria-hidden className="mx-auto mb-2 size-6 text-accent-text" />
          <p className="text-sm font-medium text-ink">Drop files here</p>
          <p className="mt-0.5 text-[12.5px] text-ink-3">
            Objects are streamed to the cluster in chunks, so size is not a problem.
          </p>
          <input
            ref={inputRef}
            type="file"
            multiple
            className="sr-only"
            disabled={running}
            onChange={(event) => {
              addFiles(event.target.files)
              // Lets the same file be picked again after it was removed.
              event.target.value = ''
            }}
          />
          <Button
            variant="secondary"
            size="sm"
            className="mt-4"
            disabled={running}
            onClick={() => inputRef.current?.click()}
          >
            Choose files
          </Button>
        </div>

        <Input
          label="Folder"
          value={prefix}
          onChange={(event) => setPrefix(event.target.value)}
          placeholder="reports/2026"
          hint="Optional. Prefixed to every file in this batch; leave empty for the bucket root."
          disabled={running}
        />

        {items.length > 0 && (
          <ul className="max-h-64 space-y-1.5 overflow-y-auto" aria-label="Files in this upload">
            {items.map((item) => (
              <li key={item.id} className="rounded-lg border border-line bg-surface p-2.5">
                <div className="flex items-center gap-2.5">
                  <FileTile contentType={item.file.type} objectKey={item.file.name} size="sm" />

                  <div className="min-w-0 flex-1">
                    <p className="truncate text-[13px] font-medium text-ink">{item.file.name}</p>
                    <p className="tnum text-[11.5px] text-ink-4">
                      {formatBytes(item.file.size)}
                      {item.status === 'done' && ' · uploaded'}
                      {item.status === 'cancelled' && ' · cancelled'}
                    </p>
                  </div>

                  {item.status === 'done' && (
                    <CheckCircle2 aria-label="Uploaded" className="size-4 shrink-0 text-success-text" />
                  )}
                  {item.status === 'failed' && (
                    <XCircle aria-label="Failed" className="size-4 shrink-0 text-danger-text" />
                  )}
                  {item.status === 'uploading' && <Spinner className="shrink-0" />}

                  {item.status === 'uploading' ? (
                    <button
                      type="button"
                      onClick={() => controllers.current.get(item.id)?.abort()}
                      className="rounded-md px-2 py-2 text-[12px] font-medium text-ink-3 transition-colors hover:bg-surface-2 hover:text-ink"
                    >
                      Cancel
                    </button>
                  ) : (
                    <button
                      type="button"
                      onClick={() => removeItem(item.id)}
                      aria-label={`Remove ${item.file.name}`}
                      disabled={running}
                      className="grid size-9 shrink-0 place-items-center rounded-md text-ink-4 transition-colors hover:bg-surface-2 hover:text-ink disabled:opacity-40"
                    >
                      <X aria-hidden className="size-4" />
                    </button>
                  )}
                </div>

                {item.status === 'uploading' && (
                  <div className="mt-2">
                    <ProgressBar percent={item.percent} label={`Uploading ${item.file.name}`} showValue />
                  </div>
                )}

                {item.error && (
                  <p className="mt-1.5 flex items-start gap-1.5 text-[12px] text-danger-text">
                    <AlertCircle aria-hidden className="mt-px size-3.5 shrink-0" />
                    {item.error}
                  </p>
                )}
              </li>
            ))}
          </ul>
        )}
      </div>
    </Dialog>
  )
}
