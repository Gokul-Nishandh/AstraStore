import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { MailCheck, TriangleAlert } from 'lucide-react'
import { AuthShell } from '../components/layout/PublicLayout'
import { Button } from '../components/ui/Button'
import { Input } from '../components/ui/Field'
import { CopyButton } from '../components/ui/CopyButton'
import { useToast } from '../components/ui/toast-context'
import { api } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { validateEmail } from '../lib/validation'

/**
 * The token the server hands back when it has been configured to expose one
 * for local testing. In every other deployment this is null and this whole
 * block never renders — but when it does render it has to be unmistakably a
 * development affordance, not a normal part of the flow.
 */
function DevTokenPanel({ token }: { token: string }) {
  return (
    <div className="mt-5 rounded-lg border border-warning-border bg-warning-subtle p-4 text-left">
      <div className="flex items-center gap-2">
        <TriangleAlert aria-hidden className="size-4 shrink-0 text-warning-text" />
        <p className="text-[12.5px] font-semibold text-warning-text">
          Development mode — this server returned the reset token
        </p>
      </div>
      <p className="mt-2 text-[12.5px] leading-relaxed text-ink-2">
        A production deployment emails this and never puts it on screen. Seeing it here means the
        server is configured to expose reset tokens.
      </p>

      <div className="mt-3 flex items-center gap-2 rounded-md border border-line bg-bg-elevated p-2">
        <code className="scroll-x min-w-0 flex-1 whitespace-nowrap text-[12px] text-ink-2">{token}</code>
        <CopyButton value={token} label="Copy" className="shrink-0" />
      </div>

      <Button asChild size="sm" variant="secondary" className="mt-3 w-full">
        <Link to={`/reset-password?token=${encodeURIComponent(token)}`}>Set a new password</Link>
      </Button>
    </div>
  )
}

export function ForgotPasswordPage() {
  const { toast } = useToast()
  const [email, setEmail] = useState('')
  const [error, setError] = useState<string>()
  const [submitting, setSubmitting] = useState(false)
  const [sent, setSent] = useState(false)
  const [devToken, setDevToken] = useState<string | null>(null)

  const submit = async (event: FormEvent) => {
    event.preventDefault()
    const message = validateEmail(email)
    setError(message ?? undefined)
    if (message) return

    setSubmitting(true)
    try {
      const response = await api.forgotPassword({ email: email.trim() })
      // The confirmation below is identical no matter what the server found,
      // so this screen can never be used to test whether an address has an
      // account on this deployment.
      setDevToken(response?.resetToken ?? null)
      setSent(true)
    } catch (err) {
      toast(toUserMessage(err, "We couldn't send that reset link. Please try again."), 'error')
    } finally {
      setSubmitting(false)
    }
  }

  if (sent) {
    return (
      <AuthShell
        title="Check your inbox"
        footer={
          <Link to="/login" className="font-medium text-accent-text underline-offset-2 hover:underline">
            Back to sign in
          </Link>
        }
      >
        <div className="text-center">
          <span
            aria-hidden
            className="mx-auto grid size-12 place-items-center rounded-xl border border-accent-border bg-accent-subtle text-accent-text"
          >
            <MailCheck className="size-5" />
          </span>
          <p className="mt-4 text-[13.5px] leading-relaxed text-ink-2">
            If <span className="font-medium text-ink">{email.trim()}</span> has an account here, a
            link to set a new password is on its way. The link expires, so use it soon.
          </p>
          <p className="mt-3 text-[12.5px] leading-relaxed text-ink-4">
            Nothing arrived? Check spam, then try again — we send the same reply whether or not the
            address is registered.
          </p>
        </div>

        {devToken && <DevTokenPanel token={devToken} />}

        <Button
          variant="secondary"
          size="lg"
          className="mt-5 w-full"
          onClick={() => {
            setSent(false)
            setDevToken(null)
          }}
        >
          Use a different address
        </Button>
      </AuthShell>
    )
  }

  return (
    <AuthShell
      title="Reset your password"
      description="Tell us the address on the account and we will send a link to set a new password."
      footer={
        <>
          Remembered it?{' '}
          <Link to="/login" className="font-medium text-accent-text underline-offset-2 hover:underline">
            Sign in
          </Link>
        </>
      }
    >
      <form onSubmit={submit} noValidate className="space-y-4">
        <Input
          label="Email"
          type="email"
          inputMode="email"
          autoComplete="email"
          autoFocus
          placeholder="you@example.com"
          value={email}
          error={error}
          onChange={(e) => {
            setEmail(e.target.value)
            if (error) setError(undefined)
          }}
          onBlur={() => setError(validateEmail(email) ?? undefined)}
        />

        <Button type="submit" size="lg" className="w-full" loading={submitting}>
          Send reset link
        </Button>
      </form>
    </AuthShell>
  )
}
