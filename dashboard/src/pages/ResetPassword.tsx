import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { Eye, EyeOff, LinkIcon } from 'lucide-react'
import { AuthShell } from '../components/layout/PublicLayout'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Field'
import { useToast } from '../components/ui/toast-context'
import { PasswordStrength } from './Register'
import { api } from '../lib/api'
import { isRetryable, toUserMessage } from '../lib/errors'
import { hasErrors, validateForm, validatePassword, validatePasswordConfirmation } from '../lib/validation'

/**
 * Shown when the link is missing its token, or the server refused the one it
 * carried. Both cases mean the same thing to the person holding the link, and
 * both need the same way out — so they get one screen with a real action on
 * it rather than a dead end.
 */
function BrokenLink({ reason }: { reason: string }) {
  return (
    <AuthShell
      title="That link no longer works"
      footer={
        <Link to="/login" className="font-medium text-accent-text underline-offset-2 hover:underline">
          Back to sign in
        </Link>
      }
    >
      <div className="text-center">
        <span
          aria-hidden
          className="mx-auto grid size-12 place-items-center rounded-xl border border-line bg-surface-2 text-ink-3"
        >
          <LinkIcon className="size-5" />
        </span>
        <p className="mt-4 text-[13.5px] leading-relaxed text-ink-2">{reason}</p>
        <p className="mt-3 text-[12.5px] leading-relaxed text-ink-4">
          Reset links are single use and expire a short while after they are issued. Requesting a
          new one takes a moment and retires any older link.
        </p>
      </div>

      <Button asChild size="lg" className="mt-6 w-full">
        <Link to="/forgot-password">Request a new link</Link>
      </Button>
    </AuthShell>
  )
}

export function ResetPasswordPage() {
  const [params] = useSearchParams()
  const navigate = useNavigate()
  const { toast } = useToast()

  const token = params.get('token')?.trim() ?? ''

  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [reveal, setReveal] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [rejected, setRejected] = useState(false)

  if (!token) {
    return <BrokenLink reason="This address is missing the reset token, so we cannot tell which account it belongs to." />
  }

  if (rejected) {
    return <BrokenLink reason="The server would not accept this reset link. It has either expired or already been used." />
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found = validateForm({
      password: () => validatePassword(password),
      confirmation: () => validatePasswordConfirmation(password, confirmation),
    })
    setErrors(found)
    if (hasErrors(found)) return

    setSubmitting(true)
    try {
      await api.resetPassword({ token, newPassword: password })
      toast('Your password has been changed. Sign in with it now.', 'success')
      navigate('/login', { replace: true })
    } catch (error) {
      // A network blip is not a spent token, so only a definitive refusal
      // burns the form and sends the user back to request a new link.
      if (isRetryable(error)) {
        toast(toUserMessage(error, "We couldn't change your password. Please try again."), 'error')
      } else {
        setRejected(true)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthShell
      title="Choose a new password"
      description="Setting a new password signs out every other session on this account."
      footer={
        <Link to="/login" className="font-medium text-accent-text underline-offset-2 hover:underline">
          Back to sign in
        </Link>
      }
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <div>
          <Input
            label="New password"
            type={reveal ? 'text' : 'password'}
            autoComplete="new-password"
            autoFocus
            placeholder="At least 8 characters"
            value={password}
            error={errors.password}
            onChange={(e) => {
              setPassword(e.target.value)
              if (errors.password) setErrors((prev) => ({ ...prev, password: '' }))
            }}
            onBlur={() => {
              const message = validatePassword(password)
              if (message) setErrors((prev) => ({ ...prev, password: message }))
            }}
            trailing={
              <button
                type="button"
                onClick={() => setReveal((on) => !on)}
                aria-label={reveal ? 'Hide password' : 'Show password'}
                className="grid size-8 place-items-center rounded-md text-ink-3 transition-colors hover:bg-surface-2 hover:text-ink"
              >
                {reveal ? <EyeOff className="size-4" /> : <Eye className="size-4" />}
              </button>
            }
          />
          <PasswordStrength value={password} />
        </div>

        <Input
          label="Confirm new password"
          type={reveal ? 'text' : 'password'}
          autoComplete="new-password"
          placeholder="Type it again"
          value={confirmation}
          error={errors.confirmation}
          onChange={(e) => {
            setConfirmation(e.target.value)
            if (errors.confirmation) setErrors((prev) => ({ ...prev, confirmation: '' }))
          }}
          onBlur={() => {
            const message = validatePasswordConfirmation(password, confirmation)
            if (message) setErrors((prev) => ({ ...prev, confirmation: message }))
          }}
        />

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          Change password
        </Button>
      </form>
    </AuthShell>
  )
}
