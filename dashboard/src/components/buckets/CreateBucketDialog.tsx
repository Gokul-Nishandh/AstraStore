import { useState, type FormEvent } from 'react'
import { Dialog } from '../ui/Dialog'
import { Button } from '../ui/Button'
import { Input } from '../ui/Field'
import { useToast } from '../ui/toast-context'
import { api } from '../../lib/api'
import { toUserMessage } from '../../lib/errors'
import { validateBucketName } from '../../lib/validation'
import type { Bucket } from '../../types/api'

export interface CreateBucketDialogProps {
  open: boolean
  onClose: () => void
  onCreated: (bucket: Bucket) => void
  /** Suppresses the toast where the caller already reports success itself. */
  silent?: boolean
}

/**
 * Bucket creation, shared by the drive, the explorer and onboarding — three
 * places a first bucket can be born, one set of rules for what it may be
 * called.
 */
export function CreateBucketDialog({ open, onClose, onCreated, silent = false }: CreateBucketDialogProps) {
  const { toast } = useToast()
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const close = () => {
    if (busy) return
    setName('')
    setError(null)
    onClose()
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const invalid = validateBucketName(name)
    if (invalid) {
      setError(invalid)
      return
    }

    setBusy(true)
    setError(null)
    try {
      const bucket = await api.createBucket(name.trim())
      if (!silent) toast(`Bucket “${bucket.name}” created`, 'success')
      setName('')
      onCreated(bucket)
    } catch (failure) {
      setError(toUserMessage(failure, 'Could not create that bucket.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <Dialog
      open={open}
      onClose={close}
      title="New bucket"
      description="Buckets group your objects. Names are permanent and appear in every object URL."
      size="sm"
      dismissOnBackdrop={!busy}
      footer={
        <>
          <Button variant="secondary" onClick={close} disabled={busy}>
            Cancel
          </Button>
          <Button onClick={submit} loading={busy} disabled={busy || name.trim().length === 0}>
            Create bucket
          </Button>
        </>
      }
    >
      <form onSubmit={submit}>
        <Input
          label="Bucket name"
          value={name}
          autoFocus
          disabled={busy}
          error={error ?? undefined}
          hint="Lowercase letters, numbers, dots and hyphens. 3–63 characters."
          placeholder="project-archive"
          onChange={(event) => {
            setName(event.target.value)
            if (error) setError(null)
          }}
        />
        {/* Submits on Enter without a visible duplicate of the footer button. */}
        <button type="submit" className="sr-only" tabIndex={-1} aria-hidden>
          Create bucket
        </button>
      </form>
    </Dialog>
  )
}
