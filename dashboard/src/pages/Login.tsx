import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Eye, EyeOff } from 'lucide-react'
import { AuthShell } from '../components/layout/PublicLayout'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Field'
import { useToast } from '../components/ui/toast-context'
import { useAuth } from '../lib/useAuth'
import { toUserMessage } from '../lib/errors'
import { hasErrors, validateEmail, validateForm, validatePassword } from '../lib/validation'

interface FromLocation {
  pathname?: string
  search?: string
  hash?: string
}

/**
 * Rebuilds the path AuthGuard stashed before bouncing an anonymous visitor.
 *
 * The value is only ever written by our own router, but it is still read back
 * out of history state, so it is checked before being navigated to: anything
 * that is not a single-slash-rooted path falls back to the dashboard rather
 * than becoming a redirect someone else controls.
 */
function returnPath(from: FromLocation | undefined): string {
  const pathname = from?.pathname
  if (!pathname || !pathname.startsWith('/') || pathname.startsWith('//')) return '/dashboard'
  return `${pathname}${from?.search ?? ''}${from?.hash ?? ''}`
}

export function LoginPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { login, isAuthenticated } = useAuth()
  const { toast } = useToast()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [reveal, setReveal] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const destination = returnPath((location.state as { from?: FromLocation } | null)?.from)

  const validate = () =>
    validateForm({
      email: () => validateEmail(email),
      password: () => validatePassword(password),
    })

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found = validate()
    setErrors(found)
    if (hasErrors(found)) return

    setSubmitting(true)
    try {
      await login({ email: email.trim(), password })
      navigate(destination, { replace: true })
    } catch (error) {
      toast(toUserMessage(error, "We couldn't sign you in. Check your details and try again."), 'error')
    } finally {
      setSubmitting(false)
    }
  }

  // Someone who still has a session should never be shown a sign-in form; they
  // should land where they were heading.
  if (isAuthenticated) return <Navigate to={destination} replace />

  return (
    <AuthShell
      title="Sign in"
      description="Use the account you created on this deployment."
      footer={
        <>
          No account yet?{' '}
          <Link to="/register" className="font-medium text-accent-text underline-offset-2 hover:underline">
            Create one
          </Link>
        </>
      }
    >
      {/* Above the form, because for someone who has a provider account this
          is the shorter path — and it renders nothing at all on a deployment
          with no provider configured. */}

      <form onSubmit={submit} noValidate className="space-y-4">
        <Input
          label="Email"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoFocus
          placeholder="you@example.com"
          value={email}
          error={errors.email}
          onChange={(e) => {
            setEmail(e.target.value)
            if (errors.email) setErrors((prev) => ({ ...prev, email: '' }))
          }}
          onBlur={() => {
            const message = validateEmail(email)
            if (message) setErrors((prev) => ({ ...prev, email: message }))
          }}
        />

        <div>
          <Input
            label="Password"
            type={reveal ? 'text' : 'password'}
            autoComplete="current-password"
            placeholder="Your password"
            value={password}
            error={errors.password}
            onChange={(e) => {
              setPassword(e.target.value)
              if (errors.password) setErrors((prev) => ({ ...prev, password: '' }))
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
          <div className="mt-2 text-right">
            <Link
              to="/forgot-password"
              className="text-[12.5px] font-medium text-accent-text underline-offset-2 hover:underline"
            >
              Forgot your password?
            </Link>
          </div>
        </div>

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          Sign in
        </Button>
      </form>
    </AuthShell>
  )
}
