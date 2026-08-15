import { useEffect, useState, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Menu, X } from 'lucide-react'
import { BrandMark } from '../ui/BrandMark'
import { ThemeToggle } from '../ui/ThemeToggle'
import { Button } from '../ui/Button'
import { cn } from '../../lib/cn'

interface NavItem {
  to: string
  label: string
}

/* Section anchors are written as absolute paths so the same header works on
   the legal pages, where the target section does not exist on this route. */
const NAV: NavItem[] = [
  { to: '/#how-it-works', label: 'How it works' },
  { to: '/#capabilities', label: 'Capabilities' },
  { to: '/#developers', label: 'Developers' },
  { to: '/support', label: 'Support' },
]

const FOOTER_GROUPS: { heading: string; items: NavItem[] }[] = [
  {
    heading: 'Platform',
    items: [
      { to: '/#how-it-works', label: 'How it works' },
      { to: '/#capabilities', label: 'Capabilities' },
      { to: '/#developers', label: 'Developer surface' },
    ],
  },
  {
    heading: 'Account',
    items: [
      { to: '/login', label: 'Sign in' },
      { to: '/register', label: 'Create an account' },
      { to: '/forgot-password', label: 'Reset your password' },
    ],
  },
  {
    heading: 'Legal',
    items: [
      { to: '/privacy', label: 'Privacy policy' },
      { to: '/terms', label: 'Terms of service' },
      { to: '/data-deletion', label: 'Account & data deletion' },
      { to: '/support', label: 'Support' },
    ],
  },
]

function PublicHeader() {
  const [scrolled, setScrolled] = useState(false)
  const [menuOpen, setMenuOpen] = useState(false)
  const { pathname, hash } = useLocation()

  useEffect(() => {
    const onScroll = () => setScrolled(window.scrollY > 8)
    onScroll()
    window.addEventListener('scroll', onScroll, { passive: true })
    return () => window.removeEventListener('scroll', onScroll)
  }, [])

  // Any navigation closes the sheet, including an in-page jump to a section,
  // which is why the hash is a dependency and not just the pathname.
  useEffect(() => setMenuOpen(false), [pathname, hash])

  return (
    <header
      className={cn(
        'sticky top-0 z-50 border-b transition-colors duration-300',
        scrolled || menuOpen ? 'border-line bg-bg/85 backdrop-blur-xl' : 'border-transparent',
      )}
    >
      <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-3 px-4 sm:px-6">
        <Link to="/" aria-label="AstraStore home" className="shrink-0 rounded-md">
          <BrandMark />
        </Link>

        <nav
          aria-label="Site"
          className="hidden items-center gap-0.5 rounded-full border border-line bg-surface/80 p-1 backdrop-blur md:flex"
        >
          {NAV.map((item) => (
            <Link
              key={item.to}
              to={item.to}
              className="rounded-full px-3.5 py-1.5 text-[13px] font-medium text-ink-3 transition-colors hover:bg-surface-3 hover:text-ink"
            >
              {item.label}
            </Link>
          ))}
        </nav>

        <div className="flex shrink-0 items-center gap-2">
          <ThemeToggle size="sm" />
          <Link
            to="/login"
            className="hidden h-8 items-center rounded-md px-3 text-[13px] font-medium text-ink-2 transition-colors hover:text-ink sm:inline-flex"
          >
            Sign in
          </Link>
          <Button asChild size="sm" className="hidden sm:inline-flex">
            <Link to="/register">Get started</Link>
          </Button>
          <button
            type="button"
            onClick={() => setMenuOpen((open) => !open)}
            aria-expanded={menuOpen}
            aria-controls="public-nav-sheet"
            aria-label={menuOpen ? 'Close menu' : 'Open menu'}
            className="grid size-8 place-items-center rounded-lg border border-line bg-surface text-ink-2 transition-colors hover:bg-surface-2 hover:text-ink md:hidden"
          >
            {menuOpen ? <X className="size-4" /> : <Menu className="size-4" />}
          </button>
        </div>
      </div>

      {menuOpen && (
        <div id="public-nav-sheet" className="border-t border-line bg-bg/95 backdrop-blur-xl md:hidden">
          <nav aria-label="Site" className="mx-auto flex max-w-6xl flex-col gap-1 px-4 py-3 sm:px-6">
            {NAV.map((item) => (
              <Link
                key={item.to}
                to={item.to}
                className="flex min-h-11 items-center rounded-lg px-3 text-sm font-medium text-ink-2 transition-colors hover:bg-surface-2 hover:text-ink"
              >
                {item.label}
              </Link>
            ))}
            <div className="mt-2 flex gap-2 border-t border-line pt-3 sm:hidden">
              <Button asChild variant="secondary" size="lg" className="flex-1">
                <Link to="/login">Sign in</Link>
              </Button>
              <Button asChild size="lg" className="flex-1">
                <Link to="/register">Get started</Link>
              </Button>
            </div>
          </nav>
        </div>
      )}
    </header>
  )
}

function PublicFooter() {
  return (
    <footer className="border-t border-line bg-surface/40">
      <div className="mx-auto max-w-6xl px-4 py-12 sm:px-6 sm:py-14">
        <div className="grid gap-10 sm:grid-cols-2 lg:grid-cols-[1.4fr_repeat(3,1fr)]">
          <div className="max-w-xs">
            <BrandMark />
            <p className="mt-3 text-[13px] leading-relaxed text-ink-3">
              Self-hosted distributed object storage. Chunked, replicated across storage nodes, and
              repaired without an operator in the loop.
            </p>
          </div>

          {FOOTER_GROUPS.map((group) => (
            <div key={group.heading}>
              <h2 className="font-sans text-[11px] font-semibold uppercase tracking-wider text-ink-4">
                {group.heading}
              </h2>
              <ul className="mt-3 space-y-2">
                {group.items.map((item) => (
                  <li key={item.to}>
                    <Link
                      to={item.to}
                      className="text-[13px] text-ink-3 transition-colors hover:text-ink"
                    >
                      {item.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          ))}
        </div>

        <div className="mt-10 flex flex-col gap-3 border-t border-line pt-6 sm:flex-row sm:items-center sm:justify-between">
          <p className="text-[12px] text-ink-4">
            © {new Date().getFullYear()} [LEGAL ENTITY NAME]. AstraStore is distributed object
            storage software.
          </p>
          <p className="text-[12px] text-ink-4">
            Questions about your data?{' '}
            <Link to="/data-deletion" className="text-accent-text underline-offset-2 hover:underline">
              Deleting your account
            </Link>
          </p>
        </div>
      </div>
    </footer>
  )
}

/**
 * Chrome shared by every signed-out route.
 *
 * `bare` exists for the 404, which renders inside the signed-in shell on
 * `/dashboard/*` and must not stack a second header and footer on top of it.
 */
export function PublicLayout({ children, bare = false }: { children: ReactNode; bare?: boolean }) {
  if (bare) return <>{children}</>

  return (
    <div className="flex min-h-screen flex-col overflow-x-clip bg-bg text-ink">
      <PublicHeader />
      <main className="flex-1">{children}</main>
      <PublicFooter />
    </div>
  )
}

/**
 * The single-column frame every credential screen sits in.
 *
 * Sign-in, registration and both halves of a password reset are one flow as
 * far as the user is concerned, so they share a frame rather than each
 * inventing a layout — moving between them should not feel like moving
 * between products.
 */
export function AuthShell({
  title,
  description,
  children,
  footer,
}: {
  title: string
  description?: ReactNode
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <div className="accent-wash flex min-h-screen flex-col overflow-x-clip bg-bg text-ink">
      <div className="mx-auto flex h-16 w-full max-w-5xl shrink-0 items-center justify-between px-4 sm:px-6">
        <Link to="/" aria-label="AstraStore home" className="rounded-md">
          <BrandMark />
        </Link>
        <ThemeToggle size="sm" />
      </div>

      <main className="flex flex-1 items-start justify-center px-4 pb-16 pt-4 sm:items-center sm:px-6 sm:pb-20 sm:pt-0">
        <div className="w-full max-w-[26rem]">
          <div className="rounded-xl border border-line bg-surface p-6 shadow-sm sm:p-8">
            <h1 className="text-[22px] font-semibold leading-tight text-ink">{title}</h1>
            {description && (
              <p className="mt-2 text-[13.5px] leading-relaxed text-ink-3">{description}</p>
            )}
            <div className="mt-6">{children}</div>
          </div>

          {footer && <div className="mt-5 text-center text-[13px] text-ink-3">{footer}</div>}

          <p className="mt-8 flex flex-wrap items-center justify-center gap-x-4 gap-y-1 text-[12px] text-ink-4">
            <Link to="/privacy" className="transition-colors hover:text-ink-2">
              Privacy
            </Link>
            <Link to="/terms" className="transition-colors hover:text-ink-2">
              Terms
            </Link>
            <Link to="/support" className="transition-colors hover:text-ink-2">
              Support
            </Link>
          </p>
        </div>
      </main>
    </div>
  )
}
