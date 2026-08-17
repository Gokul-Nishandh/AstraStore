import { useEffect, useState } from 'react'
import { NavLink, Outlet, Link, useLocation, useNavigate } from 'react-router-dom'
import {
  Clock,
  HardDrive,
  KeyRound,
  LayoutDashboard,
  LogOut,
  Menu,
  ScrollText,
  Server,
  Settings,
  Star,
  Trash2,
  Upload,
  Users,
  X,
} from 'lucide-react'
import { BrandMark } from '../ui/BrandMark'
import { ThemeToggle } from '../ui/ThemeToggle'
import { Button } from '../ui/Button'
import { StatusDot } from '../ui/Badge'
import { Tooltip } from '../ui/Tooltip'
import { cn } from '../../lib/cn'
import { useAuth } from '../../lib/useAuth'
import { useMonitoringSummary } from '../../lib/hooks'
import { formatUptime } from '../../lib/format'

interface NavItem {
  to: string
  label: string
  icon: typeof Clock
  end?: boolean
}

function useNavGroups(isAdmin: boolean): { label: string; items: NavItem[] }[] {
  const drive: NavItem[] = [
    { to: '/dashboard', label: 'My Drive', icon: HardDrive, end: true },
    { to: '/dashboard/recent', label: 'Recent', icon: Clock },
    { to: '/dashboard/starred', label: 'Starred', icon: Star },
    { to: '/dashboard/trash', label: 'Trash', icon: Trash2 },
  ]
  const tools: NavItem[] = [
    { to: '/dashboard/api-keys', label: 'API keys', icon: KeyRound },
    { to: '/dashboard/settings', label: 'Settings', icon: Settings },
  ]
  const admin: NavItem[] = [
    { to: '/dashboard/overview', label: 'Overview', icon: LayoutDashboard },
    { to: '/dashboard/cluster', label: 'Cluster', icon: Server },
    { to: '/dashboard/users', label: 'Users', icon: Users },
    { to: '/dashboard/audit-log', label: 'Audit log', icon: ScrollText },
  ]

  return [
    { label: 'Drive', items: drive },
    { label: 'Account', items: tools },
    ...(isAdmin ? [{ label: 'Operations', items: admin }] : []),
  ]
}

/**
 * Cluster health in the corner, for admins only.
 *
 * Reports "awaiting data" rather than a figure when the monitoring service
 * has not gathered enough samples — a badge that reads 100% on a cluster
 * nobody has measured yet is worse than one that admits it does not know.
 */
function ClusterPill() {
  /* The role gates the poll as well as the pill. Rendering nothing while a
     15-second timer kept asking an admin-only endpoint left every non-admin
     session generating a 403 in the console for as long as the tab was open,
     and filled their own audit trail with refusals. */
  const { isAdmin } = useAuth()
  const { data } = useMonitoringSummary('24h', isAdmin)

  if (!isAdmin) return null

  const down = data ? data.servicesDown : 0
  const tone = !data ? 'neutral' : down > 0 ? 'danger' : data.servicesDegraded > 0 ? 'warning' : 'success'
  const label = !data
    ? 'Checking'
    : data.insufficientData
      ? 'Awaiting data'
      : down > 0
        ? `${down} down`
        : formatUptime(data.clusterUptimePercent)

  return (
    <Tooltip content={data ? `${data.servicesUp}/${data.servicesTotal} services up over 24h` : 'Contacting monitoring'}>
      <Link
        to="/dashboard/overview"
        className="inline-flex h-9 items-center gap-2 rounded-full border border-line bg-surface px-3 text-xs font-medium text-ink-2 transition-colors hover:border-accent-border hover:text-ink"
      >
        <StatusDot tone={tone} pulse={tone === 'success'} />
        <span className="tnum">{label}</span>
      </Link>
    </Tooltip>
  )
}

function SidebarContent({ onNavigate }: { onNavigate?: () => void }) {
  const { user, logout, isAdmin } = useAuth()
  const groups = useNavGroups(isAdmin)
  const navigate = useNavigate()

  const initials = user?.username.slice(0, 2).toUpperCase() ?? '—'

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <>
      <div className="flex h-16 shrink-0 items-center gap-2 px-5">
        <Link to="/" className="min-w-0" onClick={onNavigate}>
          <BrandMark />
        </Link>
        {onNavigate && (
          <button
            type="button"
            onClick={onNavigate}
            aria-label="Close navigation"
            className="ml-auto grid size-9 place-items-center rounded-md text-ink-3 transition-colors hover:bg-surface-2 hover:text-ink"
          >
            <X className="size-4" />
          </button>
        )}
      </div>

      <nav className="flex-1 space-y-6 overflow-y-auto px-3 pb-4" aria-label="Primary">
        {groups.map((group) => (
          <div key={group.label}>
            <p className="mb-1.5 px-3 text-[10.5px] font-semibold uppercase tracking-[0.09em] text-ink-4">
              {group.label}
            </p>
            <ul className="space-y-0.5">
              {group.items.map((item) => (
                <li key={item.to}>
                  <NavLink
                    to={item.to}
                    end={item.end}
                    onClick={onNavigate}
                    className={({ isActive }) =>
                      cn(
                        // min-h-11 keeps every row a comfortable touch target
                        // on a phone without loosening the desktop rhythm.
                        'flex min-h-11 items-center gap-3 rounded-lg px-3 py-2 text-[13.5px] font-medium',
                        'transition-colors duration-150',
                        isActive
                          ? 'bg-accent-subtle text-accent-text'
                          : 'text-ink-2 hover:bg-surface-2 hover:text-ink',
                      )
                    }
                  >
                    {({ isActive }) => (
                      <>
                        <item.icon
                          aria-hidden
                          className={cn('size-[17px] shrink-0', isActive ? 'text-accent-text' : 'text-ink-4')}
                        />
                        <span className="truncate">{item.label}</span>
                      </>
                    )}
                  </NavLink>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </nav>

      <div className="shrink-0 border-t border-line p-3">
        <div className="flex items-center gap-2.5 rounded-lg p-2">
          <span
            aria-hidden
            className="grid size-9 shrink-0 place-items-center rounded-full border border-accent-border bg-accent-subtle text-[11px] font-semibold text-accent-text"
          >
            {initials}
          </span>
          <div className="min-w-0 flex-1">
            <p className="truncate text-[13px] font-medium text-ink">{user?.username}</p>
            <p className="truncate text-[11px] text-ink-4">{user?.email}</p>
          </div>
          <Tooltip content="Sign out">
            <button
              type="button"
              onClick={handleLogout}
              aria-label="Sign out"
              className="grid size-9 shrink-0 place-items-center rounded-md text-ink-3 transition-colors hover:bg-surface-2 hover:text-ink"
            >
              <LogOut className="size-4" />
            </button>
          </Tooltip>
        </div>
      </div>
    </>
  )
}

export function Shell() {
  const [mobileOpen, setMobileOpen] = useState(false)
  const { canWrite } = useAuth()
  const location = useLocation()

  // Close the drawer on navigation. Without this, tapping a link on a phone
  // leaves the overlay covering the page it just opened.
  useEffect(() => setMobileOpen(false), [location.pathname])

  // A drawer over a scrolling page lets the body scroll behind it on iOS.
  useEffect(() => {
    if (!mobileOpen) return
    const previous = document.body.style.overflow
    document.body.style.overflow = 'hidden'
    return () => {
      document.body.style.overflow = previous
    }
  }, [mobileOpen])

  useEffect(() => {
    if (!mobileOpen) return
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setMobileOpen(false)
    document.addEventListener('keydown', onKey)
    return () => document.removeEventListener('keydown', onKey)
  }, [mobileOpen])

  return (
    <div className="flex min-h-dvh bg-bg text-ink">
      {/* Visible only once focused, so a keyboard user can jump the whole
          navigation rail instead of tabbing through it on every page. */}
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-[60] focus:inline-flex focus:h-10 focus:items-center focus:rounded-md focus:bg-accent focus:px-4 focus:text-sm focus:font-medium focus:text-on-accent"
      >
        Skip to content
      </a>

      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r border-line bg-sidebar lg:flex">
        <SidebarContent />
      </aside>

      {mobileOpen && (
        <div className="lg:hidden">
          <button
            type="button"
            aria-label="Close navigation"
            onClick={() => setMobileOpen(false)}
            className="fixed inset-0 z-40 bg-overlay backdrop-blur-[2px] motion-safe:animate-fade-in"
          />
          <aside
            role="dialog"
            aria-modal="true"
            aria-label="Navigation"
            className="fixed inset-y-0 left-0 z-50 flex w-[min(18rem,85vw)] flex-col border-r border-line bg-sidebar shadow-xl"
          >
            <SidebarContent onNavigate={() => setMobileOpen(false)} />
          </aside>
        </div>
      )}

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-16 shrink-0 items-center gap-3 border-b border-line bg-bg/85 px-4 backdrop-blur-md sm:px-6">
          <button
            type="button"
            onClick={() => setMobileOpen(true)}
            aria-label="Open navigation"
            className="grid size-9 shrink-0 place-items-center rounded-lg border border-line bg-surface text-ink-3 transition-colors hover:text-ink lg:hidden"
          >
            <Menu className="size-4" />
          </button>

          <Link to="/" className="lg:hidden">
            <BrandMark />
          </Link>

          <div className="ml-auto flex items-center gap-2">
            {/* Intent in the URL rather than a global event, so the upload
                dialog survives a reload and can be linked to directly. */}
            {canWrite && (
              <Button asChild size="sm" icon={<Upload />} className="hidden sm:inline-flex">
                <Link to="/dashboard/objects?upload=1">Upload</Link>
              </Button>
            )}
            <ClusterPill />
            <ThemeToggle size="sm" />
          </div>
        </header>

        <main id="main" className="mx-auto w-full max-w-[1440px] flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <Outlet />
        </main>
      </div>
    </div>
  )
}
