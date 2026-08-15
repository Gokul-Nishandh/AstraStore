import { useEffect, useMemo, useState } from 'react'
import { Search, ShieldAlert, Trash2, UserCog, UserX, Users as UsersIcon } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Select } from '../components/ui/Select'
import { Checkbox } from '../components/ui/Toggle'
import { Dialog } from '../components/ui/Dialog'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Pagination } from '../components/ui/Pagination'
import { Menu } from '../components/ui/Menu'
import { Table, THead, TR, TH, TD } from '../components/ui/Table'
import { Tooltip } from '../components/ui/Tooltip'
import { useToast } from '../components/ui/toast-context'
import { api } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { formatDate } from '../lib/format'
import { useAuth } from '../lib/useAuth'
import { usePolling, useDebounced } from '../lib/hooks'
import { ROLE_DESCRIPTIONS, ROLE_LABELS, type AdminUser, type Role } from '../types/api'

const LAST_ADMIN_HINT =
  'A deployment must keep at least one administrator, so this is unavailable.'
const SELF_HINT = 'You cannot change your own role. Ask another administrator.'

export function UsersPage() {
  const { user: me } = useAuth()
  const { toast } = useToast()

  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [role, setRole] = useState('')
  const search = useDebounced(query, 300)

  const users = usePolling(
    () => api.listUsers({ search, role, page, size: 25, sort: 'id,asc' }),
    0,
    [search, role, page],
  )
  const roles = usePolling(() => api.assignableRoles(), 0)

  const [editing, setEditing] = useState<AdminUser | null>(null)
  const [deleting, setDeleting] = useState<AdminUser | null>(null)
  const [busy, setBusy] = useState(false)

  const rows = users.data?.content ?? []

  /* How many administrators the current page can see. Used only to soften
     the UI — the server is the authority and refuses the last removal
     regardless of what this count says. */
  const adminCount = useMemo(
    () => (users.data?.content ?? []).filter((u) => u.roles.includes('ADMIN')).length,
    [users.data],
  )

  const guardReason = (target: AdminUser): string | null => {
    if (me && target.id === me.userId) return SELF_HINT
    if (target.roles.includes('ADMIN') && adminCount <= 1) return LAST_ADMIN_HINT
    return null
  }

  const act = async (label: string, run: () => Promise<unknown>) => {
    setBusy(true)
    try {
      await run()
      toast(label, 'success')
      users.refresh()
      return true
    } catch (error) {
      toast(toUserMessage(error, 'That change could not be applied.'), 'error')
      return false
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Operations"
        title="Users"
        description="Every account on this deployment, with the roles and access each one holds."
      />

      <Card padded={false}>
        <div className="flex flex-wrap items-center gap-3 border-b border-line p-3">
          <div className="relative min-w-0 flex-1 basis-56">
            <Search
              aria-hidden
              className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-4"
            />
            <input
              type="search"
              value={query}
              onChange={(e) => {
                setQuery(e.target.value)
                setPage(0)
              }}
              placeholder="Search by username or email"
              aria-label="Search users"
              className="h-9 w-full rounded-md border border-line bg-surface pl-9 pr-3 text-sm text-ink outline-none transition-colors placeholder:text-ink-4 hover:border-line-strong focus-visible:border-accent"
            />
          </div>
          <div className="w-44 shrink-0">
            <Select
              aria-label="Filter by role"
              size="sm"
              value={role}
              placeholder="Any role"
              onChange={(e) => {
                setRole(e.target.value)
                setPage(0)
              }}
              options={(roles.data ?? []).map((r) => ({
                value: r,
                label: ROLE_LABELS[r as Role] ?? r,
              }))}
            />
          </div>
          {users.data && (
            <p className="tnum shrink-0 text-[12px] text-ink-4">
              {users.data.totalElements} {users.data.totalElements === 1 ? 'account' : 'accounts'}
            </p>
          )}
        </div>

        {users.error && !users.data ? (
          <div className="p-4">
            <ErrorState description={users.error} onRetry={users.refresh} />
          </div>
        ) : users.loading && !users.data ? (
          <table className="w-full">
            <tbody>
              <SkeletonRows rows={8} cols={5} />
            </tbody>
          </table>
        ) : rows.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={<UsersIcon />}
              size="sm"
              title="No accounts match"
              description="Try a different search term or clear the role filter."
              action={
                <Button
                  variant="secondary"
                  size="sm"
                  onClick={() => {
                    setQuery('')
                    setRole('')
                  }}
                >
                  Clear filters
                </Button>
              }
            />
          </div>
        ) : (
          <>
            <div className="scroll-x">
              <Table>
                <THead>
                  <TR>
                    <TH>Account</TH>
                    <TH>Roles</TH>
                    <TH>Status</TH>
                    <TH>Created</TH>
                    <TH>Last sign-in</TH>
                    <TH numeric>Keys</TH>
                    <TH>
                      <span className="sr-only">Actions</span>
                    </TH>
                  </TR>
                </THead>
                <tbody>
                  {rows.map((row) => {
                    const reason = guardReason(row)
                    return (
                      <TR key={row.id} interactive={false}>
                        <TD>
                          <div className="min-w-0">
                            <p className="truncate font-medium text-ink">
                              {row.username}
                              {me?.userId === row.id && (
                                <span className="ml-2 text-[11px] font-normal text-ink-4">you</span>
                              )}
                            </p>
                            <p className="truncate text-[12px] text-ink-4">{row.email}</p>
                          </div>
                        </TD>
                        <TD>
                          <div className="flex flex-wrap gap-1">
                            {row.roles.map((r) => (
                              <Badge key={r} tone={r === 'ADMIN' ? 'accent' : 'neutral'} size="sm">
                                {ROLE_LABELS[r as Role] ?? r}
                              </Badge>
                            ))}
                          </div>
                        </TD>
                        <TD>
                          <Badge tone={row.enabled ? 'success' : 'warning'} dot>
                            {row.enabled ? 'Active' : 'Disabled'}
                          </Badge>
                        </TD>
                        <TD>{formatDate(row.createdAt)}</TD>
                        <TD>{row.lastLoginAt ? formatDate(row.lastLoginAt) : '—'}</TD>
                        <TD numeric>{row.apiKeyCount}</TD>
                        <TD>
                          <div className="flex justify-end">
                            {reason ? (
                              // Explain the refusal up front rather than letting
                              // the server reject the attempt after the fact.
                              <Tooltip content={reason}>
                                <span className="grid size-9 place-items-center text-ink-4">
                                  <ShieldAlert aria-hidden className="size-4" />
                                  <span className="sr-only">{reason}</span>
                                </span>
                              </Tooltip>
                            ) : (
                              <Menu
                                trigger={(props) => (
                                  <button
                                    {...props}
                                    type="button"
                                    aria-label={`Actions for ${row.username}`}
                                    className="grid size-9 place-items-center rounded-md text-ink-3 transition-colors hover:bg-surface-2 hover:text-ink"
                                  >
                                    <UserCog aria-hidden className="size-4" />
                                  </button>
                                )}
                                items={[
                                  {
                                    label: 'Change roles',
                                    icon: <UserCog />,
                                    onSelect: () => setEditing(row),
                                  },
                                  {
                                    label: row.enabled ? 'Disable account' : 'Enable account',
                                    icon: <UserX />,
                                    onSelect: () =>
                                      void act(
                                        row.enabled
                                          ? `Disabled ${row.username}.`
                                          : `Enabled ${row.username}.`,
                                        () =>
                                          api.updateUserStatus(row.id, { enabled: !row.enabled }),
                                      ),
                                  },
                                  {
                                    label: 'Delete account',
                                    icon: <Trash2 />,
                                    destructive: true,
                                    onSelect: () => setDeleting(row),
                                  },
                                ]}
                              />
                            )}
                          </div>
                        </TD>
                      </TR>
                    )
                  })}
                </tbody>
              </Table>
            </div>
            <Pagination
              page={users.data?.page ?? 0}
              totalPages={users.data?.totalPages ?? 1}
              totalElements={users.data?.totalElements ?? 0}
              pageSize={users.data?.size ?? 25}
              onChange={setPage}
              className="border-t border-line p-3"
            />
          </>
        )}
      </Card>

      <RoleDialog
        user={editing}
        available={roles.data ?? []}
        busy={busy}
        onClose={() => setEditing(null)}
        onSave={async (next) => {
          if (!editing) return
          const ok = await act(`Updated roles for ${editing.username}.`, () =>
            api.updateUserRoles(editing.id, { roles: next }),
          )
          if (ok) setEditing(null)
        }}
      />

      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        loading={busy}
        destructive
        title="Delete this account?"
        confirmLabel="Delete account"
        description={
          <>
            <span className="font-medium text-ink">{deleting?.username}</span> ({deleting?.email})
            will lose access immediately. Their API keys and sessions are revoked and their audit
            history is anonymised.
          </>
        }
        onConfirm={async () => {
          if (!deleting) return
          const ok = await act(`Deleted ${deleting.username}.`, () => api.deleteUser(deleting.id))
          if (ok) setDeleting(null)
        }}
      />
    </div>
  )
}

function RoleDialog({
  user,
  available,
  busy,
  onClose,
  onSave,
}: {
  user: AdminUser | null
  available: string[]
  busy: boolean
  onClose: () => void
  onSave: (roles: string[]) => void
}) {
  const [selected, setSelected] = useState<string[]>([])

  useEffect(() => {
    setSelected(user?.roles ?? [])
  }, [user])

  const toggle = (role: string) =>
    setSelected((current) =>
      current.includes(role) ? current.filter((r) => r !== role) : [...current, role],
    )

  return (
    <Dialog
      open={user !== null}
      onClose={onClose}
      title={user ? `Roles for ${user.username}` : 'Roles'}
      description="Roles decide what this account can reach. Changes take effect on their next request."
      footer={
        <>
          <Button variant="secondary" onClick={onClose} disabled={busy}>
            Cancel
          </Button>
          <Button onClick={() => onSave(selected)} loading={busy} disabled={selected.length === 0}>
            Save roles
          </Button>
        </>
      }
    >
      <div className="space-y-3">
        {available.map((role) => (
          <Checkbox
            key={role}
            id={`role-${role}`}
            checked={selected.includes(role)}
            onChange={() => toggle(role)}
            label={ROLE_LABELS[role as Role] ?? role}
            description={ROLE_DESCRIPTIONS[role as Role]}
          />
        ))}
        {selected.length === 0 && (
          <p className="text-[12.5px] text-danger-text">An account must hold at least one role.</p>
        )}
      </div>
    </Dialog>
  )
}
