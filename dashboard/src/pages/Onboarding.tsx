import { useEffect, useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { ArrowRight, Check, Database, KeyRound, Upload } from 'lucide-react'
import { BrandMark } from '../components/ui/BrandMark'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Field'
import { CopyButton } from '../components/ui/CopyButton'
import { ProgressBar } from '../components/ui/ProgressBar'
import { useToast } from '../components/ui/toast-context'
import { useOnboarding } from '../components/onboarding/onboarding-state'
import { api, uploadObject } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { formatBytes } from '../lib/format'
import { validateBucketName } from '../lib/validation'
import { useAuth } from '../lib/useAuth'
import type { Bucket, OnboardingStep } from '../types/api'

interface StepDef {
  id: OnboardingStep
  title: string
  blurb: string
  icon: typeof Database
}

const STEPS: StepDef[] = [
  {
    id: 'bucket',
    title: 'Create a bucket',
    blurb: 'Buckets are the top-level containers your objects live in. Most people start with one.',
    icon: Database,
  },
  {
    id: 'upload',
    title: 'Upload your first object',
    blurb: 'It is split into chunks, checksummed, and replicated across storage nodes as it arrives.',
    icon: Upload,
  },
  {
    id: 'apiKey',
    title: 'Create an API key',
    blurb: 'Keys let the CLI and SDKs act on your behalf. You will see it once, so keep it somewhere safe.',
    icon: KeyRound,
  },
]

/**
 * First-run setup, shown once after registration.
 *
 * Every step performs the real action against the real API — this is not a
 * tour. A new account that finishes here genuinely has a bucket, an object
 * and a key, which is a far better starting position than an empty drive and
 * a "get started" button.
 */
export function OnboardingPage() {
  const { user } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()
  const onboarding = useOnboarding(user?.userId)

  const [index, setIndex] = useState(0)
  const [bucket, setBucket] = useState<Bucket | null>(null)
  const [createdKey, setCreatedKey] = useState<string | null>(null)

  // Someone who already finished should not be able to land back here by
  // typing the URL — send them where they were going.
  useEffect(() => {
    if (onboarding.state.completed || onboarding.state.dismissed) {
      navigate('/dashboard', { replace: true })
    }
  }, [onboarding.state.completed, onboarding.state.dismissed, navigate])

  const step = STEPS[index]
  const completedCount = STEPS.filter((s) => onboarding.state.steps[s.id]).length

  const advance = () => {
    if (index < STEPS.length - 1) setIndex(index + 1)
  }

  const finish = () => {
    onboarding.finish()
    navigate('/dashboard', { replace: true })
  }

  const skip = () => {
    onboarding.dismiss()
    navigate('/dashboard', { replace: true })
  }

  return (
    <div className="flex min-h-dvh flex-col bg-bg">
      <header className="flex items-center justify-between gap-4 px-5 py-5 sm:px-8">
        <BrandMark />
        <Button variant="ghost" size="sm" onClick={skip}>
          Skip setup
        </Button>
      </header>

      <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col justify-center px-5 pb-16 sm:px-8">
        <div className="accent-wash rounded-2xl border border-line bg-surface p-6 shadow-lg sm:p-8">
          <div className="mb-6">
            <div className="flex items-baseline justify-between gap-3">
              <p className="text-[11px] font-semibold uppercase tracking-[0.09em] text-accent-text">
                Step {index + 1} of {STEPS.length}
              </p>
              <p className="tnum text-[12px] text-ink-4">{completedCount} done</p>
            </div>
            <ProgressBar
              percent={(completedCount / STEPS.length) * 100}
              aria-label="Setup progress"
              className="mt-2.5"
            />
          </div>

          <div className="flex items-start gap-3.5">
            <span
              aria-hidden
              className="grid size-10 shrink-0 place-items-center rounded-xl border border-accent-border bg-accent-subtle text-accent-text [&>svg]:size-5"
            >
              <step.icon />
            </span>
            <div className="min-w-0">
              <h1 className="font-display text-xl font-semibold tracking-tight text-ink">
                {step.title}
              </h1>
              <p className="mt-1.5 text-pretty text-[13.5px] leading-relaxed text-ink-3">
                {step.blurb}
              </p>
            </div>
          </div>

          <div className="mt-6">
            {step.id === 'bucket' && (
              <BucketStep
                existing={bucket}
                onDone={(created) => {
                  setBucket(created)
                  onboarding.completeStep('bucket')
                  toast(`Created bucket "${created.name}".`, 'success')
                  advance()
                }}
              />
            )}

            {step.id === 'upload' && (
              <UploadStep
                bucket={bucket}
                onDone={() => {
                  onboarding.completeStep('upload')
                  toast('Your first object is stored and replicating.', 'success')
                  advance()
                }}
                onSkip={advance}
              />
            )}

            {step.id === 'apiKey' && (
              <ApiKeyStep
                createdKey={createdKey}
                onDone={(key) => {
                  setCreatedKey(key)
                  onboarding.completeStep('apiKey')
                }}
              />
            )}
          </div>

          <div className="mt-7 flex flex-wrap items-center justify-between gap-3 border-t border-line pt-5">
            <button
              type="button"
              onClick={index === 0 ? skip : () => setIndex(index - 1)}
              className="text-[13px] font-medium text-ink-3 transition-colors hover:text-ink"
            >
              {index === 0 ? 'Skip for now' : 'Back'}
            </button>

            {index === STEPS.length - 1 ? (
              <Button onClick={finish} icon={<ArrowRight />}>
                Go to my drive
              </Button>
            ) : (
              <button
                type="button"
                onClick={advance}
                className="text-[13px] font-medium text-ink-3 transition-colors hover:text-ink"
              >
                Skip this step
              </button>
            )}
          </div>
        </div>

        <ol className="mt-6 flex flex-wrap justify-center gap-x-6 gap-y-2">
          {STEPS.map((s, i) => {
            const done = onboarding.state.steps[s.id]
            return (
              <li key={s.id} className="flex items-center gap-2 text-[12.5px]">
                <span
                  aria-hidden
                  className={
                    done
                      ? 'grid size-4 place-items-center rounded-full bg-success text-on-success'
                      : i === index
                        ? 'size-4 rounded-full border-2 border-accent'
                        : 'size-4 rounded-full border border-line-strong'
                  }
                >
                  {done && <Check className="size-2.5" />}
                </span>
                <span className={done || i === index ? 'text-ink-2' : 'text-ink-4'}>{s.title}</span>
              </li>
            )
          })}
        </ol>
      </main>
    </div>
  )
}

function BucketStep({
  existing,
  onDone,
}: {
  existing: Bucket | null
  onDone: (bucket: Bucket) => void
}) {
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  if (existing) {
    return (
      <p className="rounded-lg border border-success-border bg-success-subtle px-4 py-3 text-[13.5px] text-ink-2">
        Bucket <span className="font-medium text-ink">{existing.name}</span> is ready.
      </p>
    )
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    const invalid = validateBucketName(name)
    if (invalid) {
      setError(invalid)
      return
    }
    setBusy(true)
    setError(null)
    try {
      onDone(await api.createBucket(name.trim()))
    } catch (e) {
      setError(toUserMessage(e, 'That bucket could not be created.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Input
        id="bucket-name"
        label="Bucket name"
        error={error ?? undefined}
        hint="Lowercase letters, numbers and hyphens. This cannot be changed later."
        value={name}
        onChange={(e) => {
          setName(e.target.value)
          setError(null)
        }}
        autoFocus
        placeholder="my-first-bucket"
      />
      <Button type="submit" loading={busy} icon={<Database />}>
        Create bucket
      </Button>
    </form>
  )
}

function UploadStep({
  bucket,
  onDone,
  onSkip,
}: {
  bucket: Bucket | null
  onDone: () => void
  onSkip: () => void
}) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [progress, setProgress] = useState<number | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [file, setFile] = useState<File | null>(null)

  if (!bucket) {
    return (
      <div className="rounded-lg border border-line bg-surface-2 px-4 py-3">
        <p className="text-[13.5px] text-ink-2">
          You need a bucket before you can upload. Go back a step, or skip ahead and upload later.
        </p>
        <Button variant="secondary" size="sm" className="mt-3" onClick={onSkip}>
          Skip this step
        </Button>
      </div>
    )
  }

  const start = async (chosen: File) => {
    setFile(chosen)
    setError(null)
    setProgress(0)
    try {
      await uploadObject(bucket.id, chosen.name, chosen, {
        onProgress: setProgress,
      })
      onDone()
    } catch (e) {
      setError(toUserMessage(e, 'That upload did not complete.'))
      setProgress(null)
    }
  }

  return (
    <div className="space-y-4">
      <input
        ref={inputRef}
        type="file"
        className="sr-only"
        onChange={(e) => {
          const chosen = e.target.files?.[0]
          if (chosen) void start(chosen)
        }}
      />

      <button
        type="button"
        onClick={() => inputRef.current?.click()}
        disabled={progress !== null}
        className="flex w-full flex-col items-center justify-center rounded-xl border border-dashed border-line-strong bg-surface-2 px-6 py-10 text-center transition-colors hover:border-accent-border hover:bg-surface-3 disabled:cursor-not-allowed"
      >
        <Upload aria-hidden className="size-6 text-ink-4" />
        <span className="mt-3 text-[13.5px] font-medium text-ink">
          {file ? file.name : 'Choose a file to upload'}
        </span>
        <span className="mt-1 text-[12px] text-ink-4">
          {file ? formatBytes(file.size) : `It will land in ${bucket.name}`}
        </span>
      </button>

      {progress !== null && (
        <ProgressBar percent={progress} aria-label="Upload progress" />
      )}

      {error && (
        <p className="rounded-lg border border-danger-border bg-danger-subtle px-3.5 py-2.5 text-[13px] text-ink-2">
          {error}
        </p>
      )}
    </div>
  )
}

function ApiKeyStep({
  createdKey,
  onDone,
}: {
  createdKey: string | null
  onDone: (key: string) => void
}) {
  const [name, setName] = useState('My first key')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (createdKey) {
    return (
      <div className="space-y-3">
        <div className="rounded-lg border border-warning-border bg-warning-subtle px-4 py-3">
          <p className="text-[13px] font-medium text-ink">
            Copy this now — it will never be shown again.
          </p>
          <p className="mt-1 text-[12.5px] leading-relaxed text-ink-2">
            We store only a hash of it. If you lose it, revoke the key and issue another.
          </p>
        </div>
        <div className="flex items-center gap-2 rounded-lg border border-line bg-surface-2 p-2.5">
          <code className="min-w-0 flex-1 truncate font-mono text-[12.5px] text-ink">
            {createdKey}
          </code>
          <CopyButton value={createdKey} />
        </div>
      </div>
    )
  }

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    setBusy(true)
    setError(null)
    try {
      const created = await api.createApiKey({ name: name.trim() || 'My first key' })
      onDone(created.key)
    } catch (e) {
      setError(toUserMessage(e, 'That key could not be created.'))
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} className="space-y-4" noValidate>
      <Input
        id="key-name"
        label="Key name"
        error={error ?? undefined}
        hint="A label so you can recognise it later."
        value={name}
        onChange={(e) => setName(e.target.value)}
      />
      <Button type="submit" loading={busy} icon={<KeyRound />}>
        Create API key
      </Button>
    </form>
  )
}
