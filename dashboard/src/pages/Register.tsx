import { useMemo, useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { Check, Eye, EyeOff } from 'lucide-react'
import { AuthShell } from '../components/layout/PublicLayout'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Field'
import { useToast } from '../components/ui/toast-context'
import { useAuth } from '../lib/useAuth'
import { toUserMessage } from '../lib/errors'
import {
  hasErrors,
  validateEmail,
  validateForm,
  validatePassword,
  validatePasswordConfirmation,
  validateUsername,
} from '../lib/validation'
import { cn } from '../lib/cn'

interface Rule {
  label: string
  met: (value: string) => boolean
}

/* Length is weighted first because it is the only one of these that reliably
   costs an attacker anything; the character-class rules are here to nudge,
   not to gate. The server enforces the real minimum. */
const RULES: Rule[] = [
  { label: 'At least 12 characters', met: (v) => v.length >= 12 },
  { label: 'Upper and lower case', met: (v) => /[a-z]/.test(v) && /[A-Z]/.test(v) },
  { label: 'A number', met: (v) => /\d/.test(v) },
  { label: 'A symbol', met: (v) => /[^A-Za-z0-9]/.test(v) },
]

const STRENGTH = [
  { label: 'Too short', bar: 'bg-danger', text: 'text-danger-text' },
  { label: 'Weak', bar: 'bg-danger', text: 'text-danger-text' },
  { label: 'Fair', bar: 'bg-warning', text: 'text-warning-text' },
  { label: 'Good', bar: 'bg-success', text: 'text-success-text' },
  { label: 'Strong', bar: 'bg-success', text: 'text-success-text' },
] as const

function scorePassword(value: string): number {
  if (value.length < 8) return 0
  return RULES.reduce((total, rule) => total + (rule.met(value) ? 1 : 0), 0)
}

/** Shared with the reset-password screen so both agree on what "strong" means. */
export function PasswordStrength({ value }: { value: string }) {
  const score = useMemo(() => scorePassword(value), [value])
  const strength = STRENGTH[score]

  return (
    <div className="mt-3">
      <div className="flex items-center justify-between gap-3">
        <div aria-hidden className="flex flex-1 gap-1">
          {[0, 1, 2, 3].map((segment) => (
            <span
              key={segment}
              className={cn(
                'h-1 flex-1 rounded-full transition-colors duration-200',
                segment < score ? strength.bar : 'bg-surface-3',
              )}
            />
          ))}
        </div>
        <span className={cn('text-[11.5px] font-medium', value ? strength.text : 'text-ink-4')}>
          {value ? strength.label : 'Not set'}
        </span>
      </div>

      <ul className="mt-2.5 grid gap-1 sm:grid-cols-2">
        {RULES.map((rule) => {
          const met = rule.met(value)
          return (
            <li
              key={rule.label}
              className={cn('flex items-center gap-1.5 text-[11.5px]', met ? 'text-ink-2' : 'text-ink-4')}
            >
              <Check aria-hidden className={cn('size-3.5 shrink-0', met ? 'text-success-text' : 'text-ink-4')} />
              {rule.label}
            </li>
          )
        })}
      </ul>
      {/* The bar itself is decorative; this is what a screen reader hears. */}
      <p className="sr-only" aria-live="polite">
        Password strength: {value ? strength.label : 'not set'}
      </p>
    </div>
  )
}

export function RegisterPage() {
  const navigate = useNavigate()
  const { register, login, isAuthenticated } = useAuth()
  const { toast } = useToast()

  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirmation, setConfirmation] = useState('')
  const [reveal, setReveal] = useState(false)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  const clear = (field: string) => setErrors((prev) => (prev[field] ? { ...prev, [field]: '' } : prev))
  const mark = (field: string, message: string | null) => {
    if (message) setErrors((prev) => ({ ...prev, [field]: message }))
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const found = validateForm({
      username: () => validateUsername(username),
      email: () => validateEmail(email),
      password: () => validatePassword(password),
      confirmation: () => validatePasswordConfirmation(password, confirmation),
    })
    setErrors(found)
    if (hasErrors(found)) return

    setSubmitting(true)
    try {
      await register({ username: username.trim(), email: email.trim(), password })
    } catch (error) {
      setSubmitting(false)
      toast(toUserMessage(error, "We couldn't create that account. Please try again."), 'error')
      return
    }

    // Registration does not issue tokens, so the account is signed in here.
    // If that second call fails the account still exists — say so, and send
    // the user somewhere they can act on it rather than looping the form.
    try {
      await login({ email: email.trim(), password })
      navigate('/onboarding', { replace: true })
    } catch {
      toast('Your account was created. Please sign in to continue.', 'info')
      navigate('/login', { replace: true })
    } finally {
      setSubmitting(false)
    }
  }

  if (isAuthenticated) return <Navigate to="/dashboard" replace />

  return (
    <AuthShell
      title="Create an account"
      description="You get a bucket namespace of your own, API keys, and the console."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-accent-text underline-offset-2 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      {/* Signing up with a provider skips the password entirely — and the
          account it creates has none, by design. */}

      <form onSubmit={submit} noValidate className="space-y-4">
        <Input
          label="Username"
          autoComplete="username"
          autoFocus
          placeholder="your-handle"
          hint="Letters, numbers, dots, underscores and hyphens."
          value={username}
          error={errors.username}
          onChange={(e) => {
            setUsername(e.target.value)
            clear('username')
          }}
          onBlur={() => mark('username', validateUsername(username))}
        />

        <Input
          label="Email"
          type="email"
          inputMode="email"
          autoComplete="email"
          placeholder="you@example.com"
          value={email}
          error={errors.email}
          onChange={(e) => {
            setEmail(e.target.value)
            clear('email')
          }}
          onBlur={() => mark('email', validateEmail(email))}
        />

        <div>
          <Input
            label="Password"
            type={reveal ? 'text' : 'password'}
            autoComplete="new-password"
            placeholder="At least 8 characters"
            value={password}
            error={errors.password}
            onChange={(e) => {
              setPassword(e.target.value)
              clear('password')
            }}
            onBlur={() => mark('password', validatePassword(password))}
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
          label="Confirm password"
          type={reveal ? 'text' : 'password'}
          autoComplete="new-password"
          placeholder="Type it again"
          value={confirmation}
          error={errors.confirmation}
          onChange={(e) => {
            setConfirmation(e.target.value)
            clear('confirmation')
          }}
          onBlur={() => mark('confirmation', validatePasswordConfirmation(password, confirmation))}
        />

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          Create account
        </Button>

        <p className="text-[12px] leading-relaxed text-ink-4">
          By creating an account you agree to the{' '}
          <Link to="/terms" className="text-accent-text underline-offset-2 hover:underline">
            Terms of Service
          </Link>{' '}
          and the{' '}
          <Link to="/privacy" className="text-accent-text underline-offset-2 hover:underline">
            Privacy Policy
          </Link>
          .
        </p>
      </form>
    </AuthShell>
  )
}
