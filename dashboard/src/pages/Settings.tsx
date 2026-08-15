import { useEffect, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import {
  AlertTriangle,
  KeyRound,
  Monitor,
  Moon,
  Palette,
  ShieldCheck,
  Sun,
  User,
  Users,
} from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card, CardSection } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Input } from '../components/ui/Field'
import { Dialog } from '../components/ui/Dialog'
import { SegmentedControl } from '../components/ui/Tabs'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { Skeleton } from '../components/ui/Skeleton'
import { useToast } from '../components/ui/toast-context'
import { api } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { formatDate } from '../lib/format'
import { usePolling } from '../lib/hooks'
import { useAuth } from '../lib/useAuth'
import { useTheme } from '../lib/useTheme'
import { validateEmail, validatePassword, validateUsername } from '../lib/validation'
import { ROLE_DESCRIPTIONS, ROLE_LABELS, type Role } from '../types/api'
import type { Theme } from '../lib/theme-context'

const THEMES: { value: Theme; label: string; icon: typeof Sun }[] = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'system', label: 'System', icon: Monitor },
]

export function SettingsPage() {
  const { user, isAdmin, applyTokens, refreshUser } = useAuth()
  const profile = usePolling(() => api.profile(), 0)

  return (
    <div className="mx-auto max-w-2xl space-y-6">
      <PageHeader
        eyebrow="Account"
        title="Settings"
        description="Your profile, your access, and how this console looks."
      />

      {profile.error && !profile.data ? (
        <ErrorState description={profile.error} onRetry={profile.refresh} />
      ) : (
        <>
          <ProfileSection
            loading={profile.loading && !profile.data}
            profile={profile.data}
            onSaved={() => {
              profile.refresh()
              void refreshUser()
            }}
            applyTokens={applyTokens}
          />

          <PasswordSection />

          <RolesSection roles={profile.data?.roles ?? user?.roles ?? []} isAdmin={isAdmin} />

          <AppearanceSection />

          <DangerSection />
        </>
      )}
    </div>
  )
}

/* ------------------------------------------------------------------ */

function ProfileSection({
  profile,
  loading,
  onSaved,
  applyTokens,
}: {
  profile: Awaited<ReturnType<typeof api.profile>> | null
  loading: boolean
  onSaved: () => void
  applyTokens: ReturnType<typeof useAuth>['applyTokens']
}) {
  const { toast } = useToast()
  const [username, setUsername] = useState('')
  const [email, setEmail] = useState('')
  const [errors, setErrors] = useState<{ username?: string; email?: string }>({})
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    setUsername(profile?.username ?? '')
    setEmail(profile?.email ?? '')
  }, [profile])

  const dirty = profile && (username !== profile.username || email !== profile.email)

  const save = async (event: React.FormEvent) => {
    event.preventDefault()
    const next = {
      username: validateUsername(username) ?? undefined,
      email: validateEmail(email) ?? undefined,
    }
    if (next.username || next.email) {
      setErrors(next)
      return
    }
    setErrors({})
    setBusy(true)
    try {
      const result = await api.updateProfile({ username, email })
      /* Changing identity invalidates the JWT the server issued, so it hands
         back a fresh pair. Storing it here is not optional — the next request
         on the old token would 401 and bounce the user to sign-in. */
      if (result.tokensReissued && result.token && result.refreshToken) {
        applyTokens(result.token, result.refreshToken, {
          userId: result.user.id,
          username: result.user.username,
          email: result.user.email,
          roles: result.user.roles,
        })
      }
      toast('Profile updated.', 'success')
      onSaved()
    } catch (error) {
      toast(toUserMessage(error, 'Your profile could not be updated.'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <CardSection
      icon={<User />}
      title="Profile"
      description="How you appear across the console and in the audit trail."
    >
      {loading ? (
        <div className="space-y-4">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-10 w-full" />
        </div>
      ) : (
        <form onSubmit={save} className="space-y-4" noValidate>
          <div className="grid gap-4 sm:grid-cols-2">
            <Input
              label="Username"
              value={username}
              error={errors.username}
              onChange={(e) => setUsername(e.target.value)}
            />
            <Input
              label="Email"
              type="email"
              value={email}
              error={errors.email}
              hint="Changing this signs you in with the new address next time."
              onChange={(e) => setEmail(e.target.value)}
            />
          </div>

          {profile && (
            <dl className="flex flex-wrap gap-x-6 gap-y-1 text-[12.5px] text-ink-4">
              <div className="flex gap-1.5">
                <dt>Joined</dt>
                <dd className="text-ink-3">{formatDate(profile.createdAt)}</dd>
              </div>
              {profile.lastLoginAt && (
                <div className="flex gap-1.5">
                  <dt>Last sign-in</dt>
                  <dd className="text-ink-3">{formatDate(profile.lastLoginAt)}</dd>
                </div>
              )}
            </dl>
          )}

          <div className="flex justify-end">
            <Button type="submit" loading={busy} disabled={!dirty}>
              Save changes
            </Button>
          </div>
        </form>
      )}
    </CardSection>
  )
}

function PasswordSection() {
  const { toast } = useToast()
  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)

  const submit = async (event: React.FormEvent) => {
    event.preventDefault()
    const invalid = validatePassword(next)
    if (invalid) return setError(invalid)
    if (next !== confirm) return setError('The two new passwords do not match.')

    setError(null)
    setBusy(true)
    try {
      await api.changePassword({ currentPassword: current, newPassword: next })
      toast('Password changed.', 'success')
      setCurrent('')
      setNext('')
      setConfirm('')
    } catch (e) {
      toast(toUserMessage(e, 'Your password could not be changed.'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <CardSection
      icon={<KeyRound />}
      title="Password"
      description="You will stay signed in here, but other sessions are ended."
    >
      <form onSubmit={submit} className="space-y-4" noValidate>
        <Input
          label="Current password"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <Input
            label="New password"
            type="password"
            autoComplete="new-password"
            value={next}
            error={error ?? undefined}
            onChange={(e) => {
              setNext(e.target.value)
              setError(null)
            }}
          />
          <Input
            label="Confirm new password"
            type="password"
            autoComplete="new-password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
          />
        </div>
        <div className="flex justify-end">
          <Button type="submit" loading={busy} disabled={!current || !next || !confirm}>
            Change password
          </Button>
        </div>
      </form>
    </CardSection>
  )
}

/**
 * Roles, spelled out.
 *
 * The previous version showed a disabled text box and told the user to
 * contact an administrator, which meant a user could not even discover what
 * their own role permitted. Each role a person holds is now named and
 * explained.
 */
function RolesSection({ roles, isAdmin }: { roles: string[]; isAdmin: boolean }) {
  return (
    <CardSection
      icon={<ShieldCheck />}
      title="Roles and access"
      description="What this account is permitted to do. Roles are assigned by an administrator."
      actions={
        isAdmin && (
          <Button asChild variant="secondary" size="sm" icon={<Users />}>
            <Link to="/dashboard/users">Manage users</Link>
          </Button>
        )
      }
    >
      {roles.length === 0 ? (
        <EmptyState size="sm" title="No roles assigned" description="Ask an administrator to grant access." />
      ) : (
        <ul className="space-y-3">
          {roles.map((role) => (
            <li key={role} className="flex gap-3 rounded-lg border border-line bg-surface-2 p-3.5">
              <Badge tone={role === 'ADMIN' ? 'accent' : 'neutral'} className="mt-0.5 shrink-0">
                {ROLE_LABELS[role as Role] ?? role}
              </Badge>
              <p className="text-[13px] leading-relaxed text-ink-2">
                {ROLE_DESCRIPTIONS[role as Role] ??
                  'A custom role on this deployment. Ask an administrator what it grants.'}
              </p>
            </li>
          ))}
        </ul>
      )}
    </CardSection>
  )
}

function AppearanceSection() {
  const { theme, setTheme } = useTheme()

  return (
    <CardSection
      icon={<Palette />}
      title="Appearance"
      description="Applies immediately and is remembered on this device."
    >
      <SegmentedControl
        aria-label="Colour theme"
        value={theme}
        onChange={setTheme}
        items={THEMES.map((t) => ({
          value: t.value,
          label: (
            <>
              <t.icon aria-hidden className="size-3.5" />
              {t.label}
            </>
          ),
        }))}
      />
    </CardSection>
  )
}

function DangerSection() {
  const { logout } = useAuth()
  const { toast } = useToast()
  const navigate = useNavigate()

  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [acknowledgement, setAcknowledgement] = useState('')
  const [busy, setBusy] = useState(false)

  const CONFIRM_WORD = 'DELETE'
  const ready = password.length > 0 && acknowledgement === CONFIRM_WORD

  const destroy = async () => {
    setBusy(true)
    try {
      await api.deleteAccount({ password })
      // The account is gone; clear local state before navigating so no
      // request fires against a user that no longer exists.
      await logout()
      navigate('/', { replace: true })
      toast('Your account has been deleted.', 'success')
    } catch (error) {
      toast(toUserMessage(error, 'Your account could not be deleted.'), 'error')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <Card className="border-danger-border">
        <div className="flex items-start gap-3">
          <span
            aria-hidden
            className="grid size-9 shrink-0 place-items-center rounded-lg border border-danger-border bg-danger-subtle text-danger-text [&>svg]:size-4"
          >
            <AlertTriangle />
          </span>
          <div className="min-w-0 flex-1">
            <h3 className="font-display text-[15px] font-semibold tracking-tight text-ink">
              Delete this account
            </h3>
            <p className="mt-1 text-[13px] leading-relaxed text-ink-3">
              Your credentials, API keys and sessions are destroyed immediately, and your audit
              history is anonymised. This cannot be undone.{' '}
              <Link to="/data-deletion" className="text-accent-text underline underline-offset-2">
                What exactly is deleted
              </Link>
              .
            </p>
            <Button variant="danger" className="mt-4" onClick={() => setOpen(true)}>
              Delete account
            </Button>
          </div>
        </div>
      </Card>

      <Dialog
        open={open}
        onClose={() => setOpen(false)}
        title="Delete your account?"
        description="This is permanent. There is no recovery process and no grace period."
        footer={
          <>
            <Button variant="secondary" onClick={() => setOpen(false)} disabled={busy}>
              Keep my account
            </Button>
            <Button variant="danger" onClick={destroy} loading={busy} disabled={!ready}>
              Delete permanently
            </Button>
          </>
        }
      >
        <div className="space-y-4">
          <Input
            label="Confirm your password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          <Input
            label={`Type ${CONFIRM_WORD} to confirm`}
            value={acknowledgement}
            onChange={(e) => setAcknowledgement(e.target.value)}
            hint="Deliberately awkward, because this cannot be undone."
          />
        </div>
      </Dialog>
    </>
  )
}
