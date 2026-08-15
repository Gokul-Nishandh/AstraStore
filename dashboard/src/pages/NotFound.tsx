import { Link, useLocation, useNavigate } from 'react-router-dom'
import { ArrowLeft, HardDrive, Home, LifeBuoy } from 'lucide-react'
import { PublicLayout } from '../components/layout/PublicLayout'
import { Button } from '../components/ui/Button'

const ROUTES = [
  { to: '/', icon: Home, label: 'Home', detail: 'What AstraStore is and how it works' },
  { to: '/dashboard', icon: HardDrive, label: 'My Drive', detail: 'Your buckets and objects' },
  { to: '/support', icon: LifeBuoy, label: 'Support', detail: 'Report a problem or ask a question' },
]

/**
 * `inShell` is set by the catch-all route under `/dashboard`, where the
 * signed-in chrome is already on screen. Rendering the public header and
 * footer there would stack a second navigation on top of the first.
 */
export function NotFoundPage({ inShell = false }: { inShell?: boolean }) {
  const navigate = useNavigate()
  const { pathname } = useLocation()

  return (
    <PublicLayout bare={inShell}>
      <div className={inShell ? 'py-10' : 'accent-wash px-4 py-20 sm:px-6 sm:py-28'}>
        <div className="mx-auto max-w-xl">
          <p className="font-mono text-[12.5px] font-medium uppercase tracking-widest text-accent-text">
            404 — not found
          </p>
          <h1 className="mt-3 text-balance text-[clamp(1.75rem,5vw,2.5rem)] font-semibold leading-tight text-ink">
            There is nothing at this address
          </h1>
          <p className="mt-4 text-pretty text-[15px] leading-relaxed text-ink-3">
            The page you asked for does not exist. If you followed a link from somewhere in the
            console, that is worth telling us about.
          </p>

          <p className="scroll-x mt-4 rounded-lg border border-line bg-surface-2 px-3 py-2">
            <code className="whitespace-nowrap text-[12.5px] text-ink-3">{pathname}</code>
          </p>

          <div className="mt-8 grid gap-px overflow-hidden rounded-xl border border-line bg-line">
            {ROUTES.map((route) => (
              <Link
                key={route.to}
                to={route.to}
                className="flex min-h-11 items-center gap-3 bg-surface px-4 py-3 transition-colors hover:bg-surface-2"
              >
                <span
                  aria-hidden
                  className="grid size-8 shrink-0 place-items-center rounded-lg border border-line bg-surface-2 text-ink-3"
                >
                  <route.icon className="size-4" />
                </span>
                <span className="min-w-0">
                  <span className="block text-[13.5px] font-medium text-ink">{route.label}</span>
                  <span className="block text-[12.5px] text-ink-4">{route.detail}</span>
                </span>
              </Link>
            ))}
          </div>

          <Button
            variant="ghost"
            size="md"
            className="mt-6 -ml-2"
            icon={<ArrowLeft />}
            onClick={() => navigate(-1)}
          >
            Go back
          </Button>
        </div>
      </div>
    </PublicLayout>
  )
}
