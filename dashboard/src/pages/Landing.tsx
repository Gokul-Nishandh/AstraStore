import { useEffect, type ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import {
  Activity,
  ArrowRight,
  Boxes,
  Copy,
  Gauge,
  HardDrive,
  KeyRound,
  Layers,
  ScrollText,
  Terminal,
} from 'lucide-react'
import { Button } from '../components/ui/Button'
import { PublicLayout } from '../components/layout/PublicLayout'
import { PipelineDiagram } from '../components/landing/PipelineDiagram'
import { CodeSample } from '../components/landing/CodeSample'

/* Configuration defaults read from the services themselves, not performance
   claims. Nothing here is a measurement, so nothing here can go stale in a
   way that misleads. */
const DEFAULTS = [
  { value: '8 KB', label: 'Streaming buffer', detail: 'Memory does not grow with file size' },
  { value: '2', label: 'Extra replicas', detail: 'Beyond the node that took the write' },
  { value: '60 s', label: 'Repair scan', detail: 'Interval of the under-replication scanner' },
  { value: '10 s', label: 'Node heartbeat', detail: 'Health poll behind placement decisions' },
]

const STAGES = [
  {
    title: 'Chunk',
    body:
      'The upload service reads the request body through a fixed 8 KB buffer, hashing each chunk and the whole object with SHA-256 as the bytes go past. Nothing is staged to local disk first, so a 5 GB object costs the same memory as a 5 KB one.',
  },
  {
    title: 'Place',
    body:
      'Placement keeps a live state machine per storage node — healthy, degraded, down, recovering — driven by a heartbeat every ten seconds. A chunk is only ever handed to a node that is currently answering.',
  },
  {
    title: 'Replicate',
    body:
      'Each written chunk publishes an event. The replication service consumes it and has the chunk pushed node-to-node to two further nodes, with a per-node concurrency limit and exponential backoff so a slow peer cannot stall the queue.',
  },
  {
    title: 'Heal',
    body:
      'A scanner sweeps for chunks below their replica target every sixty seconds and republishes them onto the same replication path, rate-limited so a recovering cluster is not flooded by its own repairs.',
  },
]

const CAPABILITIES = [
  {
    icon: Boxes,
    title: 'Content-addressed chunking',
    body:
      'Per-chunk and whole-object SHA-256 digests are computed during the stream and verified again on the way out, so a silently corrupted chunk fails the read instead of the restore.',
  },
  {
    icon: Copy,
    title: 'Configurable replication',
    body:
      'Every chunk lands on a primary node and is copied to two more by default. The replica target travels with the chunk, so raising it means new writes fan out further.',
  },
  {
    icon: Activity,
    title: 'Self-healing placement',
    body:
      'Node health is a state machine, not a boolean: three consecutive failures demote a node, two consecutive successes bring it back. Repairs are queued, throttled and retried.',
  },
  {
    icon: Layers,
    title: 'S3-style object API',
    body:
      'Buckets and keys, addressed over plain HTTP through one gateway. PUT to write, GET by bucket and key or by object id, DELETE to move an object to trash and restore it later.',
  },
  {
    icon: KeyRound,
    title: 'JWT and API keys',
    body:
      'Interactive sessions use short-lived access tokens with rotating refresh tokens. Machines use API keys, which are shown exactly once at creation and stored only as a hash.',
  },
  {
    icon: ScrollText,
    title: 'Audit trail',
    body:
      'Sign-ins, failed sign-ins, key creation, role changes and account deletion are recorded with actor, IP address, user agent and outcome, and kept for 90 days.',
  },
]

const SUPPORTING_FACTS = [
  { icon: HardDrive, text: 'Chunks land via temp file, fsync, then atomic rename — no torn writes' },
  { icon: Terminal, text: 'CLI, Python, Node and Java clients, plus the raw HTTP API' },
  { icon: Gauge, text: 'Prometheus metrics, Grafana dashboards and distributed traces' },
  { icon: Boxes, text: 'Runs as a Docker Compose stack you host yourself' },
]

/**
 * Section anchors are reachable as `/#capabilities` from other routes. A
 * client-side navigation does not trigger the browser's own fragment scroll,
 * so the landing page performs it once the sections exist.
 */
function useHashScroll() {
  const { hash } = useLocation()

  useEffect(() => {
    if (!hash) return
    const target = document.querySelector(hash)
    if (target) target.scrollIntoView({ block: 'start' })
  }, [hash])
}

function SectionHeading({
  eyebrow,
  title,
  children,
}: {
  eyebrow: string
  title: string
  children?: ReactNode
}) {
  return (
    <div className="max-w-2xl">
      <p className="font-sans text-[11.5px] font-semibold uppercase tracking-wider text-accent-text">
        {eyebrow}
      </p>
      <h2 className="mt-3 text-balance text-[clamp(1.65rem,4vw,2.35rem)] font-semibold leading-[1.15] text-ink">
        {title}
      </h2>
      {children && <p className="mt-4 text-pretty text-[15px] leading-relaxed text-ink-3">{children}</p>}
    </div>
  )
}

function Hero() {
  return (
    <section className="accent-wash relative border-b border-line px-4 pb-16 pt-14 sm:px-6 sm:pb-20 sm:pt-20">
      <div className="mx-auto max-w-4xl text-center">
        <span className="inline-flex items-center gap-2 rounded-full border border-line bg-surface px-3 py-1 text-[12px] font-medium text-ink-3">
          Distributed object storage · self-hosted
        </span>

        <h1 className="mt-6 text-balance text-[clamp(2.1rem,6.5vw,3.75rem)] font-semibold leading-[1.05] tracking-[-0.03em] text-ink">
          Object storage that keeps repairing itself
        </h1>

        <p className="mx-auto mt-6 max-w-2xl text-pretty text-[16px] leading-relaxed text-ink-2 sm:text-[17px]">
          AstraStore splits every upload into SHA-256 addressed chunks, streams them to storage nodes
          that are still answering their heartbeat, copies each chunk to two more, and re-replicates
          anything that falls short — without anyone being paged.
        </p>

        <div className="mt-9 flex flex-wrap items-center justify-center gap-3">
          <Button asChild size="lg">
            <Link to="/register">
              Create an account <ArrowRight className="size-4" aria-hidden />
            </Link>
          </Button>
          <Button asChild size="lg" variant="secondary">
            <Link to="/#how-it-works">See the write path</Link>
          </Button>
        </div>
      </div>

      <div className="mx-auto mt-14 max-w-5xl">
        <h2 className="sr-only">Pipeline defaults</h2>
        <dl className="grid grid-cols-2 overflow-hidden rounded-xl border border-line bg-surface sm:grid-cols-4">
          {DEFAULTS.map((item, i) => (
            <div
              key={item.label}
              className={[
                'p-4 sm:p-5',
                i % 2 === 1 ? 'border-l border-line' : '',
                i >= 2 ? 'border-t border-line sm:border-t-0' : '',
                i > 0 ? 'sm:border-l sm:border-line' : '',
              ].join(' ')}
            >
              <dd className="tnum font-sans text-[22px] font-semibold leading-none text-ink sm:text-[26px]">
                {item.value}
              </dd>
              <dt className="mt-2 text-[13px] font-medium text-ink-2">{item.label}</dt>
              <p className="mt-1 text-[12px] leading-relaxed text-ink-4">{item.detail}</p>
            </div>
          ))}
        </dl>
      </div>
    </section>
  )
}

function HowItWorks() {
  return (
    <section id="how-it-works" className="scroll-mt-20 border-b border-line px-4 py-20 sm:px-6 sm:py-24">
      <div className="mx-auto max-w-6xl">
        <SectionHeading eyebrow="How it works" title="Four stages, and a loop that closes itself">
          The write path is deliberately boring in the happy case. What makes it durable is the
          fourth stage, which assumes the first three will eventually fail.
        </SectionHeading>

        <div className="mt-12 rounded-2xl border border-line bg-surface/50 p-4 sm:p-6">
          <PipelineDiagram />
        </div>

        <ol className="mt-10 grid gap-px overflow-hidden rounded-xl border border-line bg-line md:grid-cols-2 xl:grid-cols-4">
          {STAGES.map((stage, i) => (
            <li key={stage.title} className="bg-surface p-5 sm:p-6">
              <div className="flex items-center gap-2.5">
                <span
                  aria-hidden
                  className="tnum grid size-6 shrink-0 place-items-center rounded-full border border-accent-border bg-accent-subtle font-mono text-[11px] font-medium text-accent-text"
                >
                  {i + 1}
                </span>
                <h3 className="text-[15px] font-semibold text-ink">{stage.title}</h3>
              </div>
              <p className="mt-3 text-[13.5px] leading-relaxed text-ink-3">{stage.body}</p>
            </li>
          ))}
        </ol>
      </div>
    </section>
  )
}

function Capabilities() {
  return (
    <section id="capabilities" className="scroll-mt-20 border-b border-line px-4 py-20 sm:px-6 sm:py-24">
      <div className="mx-auto max-w-6xl">
        <SectionHeading eyebrow="Capabilities" title="What you get when you run it">
          A console, an API and a cluster that agree with each other. Everything below is in the
          shipped services — nothing here is on a roadmap.
        </SectionHeading>

        <div className="mt-12 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {CAPABILITIES.map((cap) => (
            <div
              key={cap.title}
              className="rounded-xl border border-line bg-surface p-5 transition-colors duration-150 hover:border-accent-border"
            >
              <span
                aria-hidden
                className="grid size-9 place-items-center rounded-lg border border-accent-border bg-accent-subtle text-accent-text"
              >
                <cap.icon className="size-4" />
              </span>
              <h3 className="mt-4 text-[15px] font-semibold text-ink">{cap.title}</h3>
              <p className="mt-2 text-[13.5px] leading-relaxed text-ink-3">{cap.body}</p>
            </div>
          ))}
        </div>

        <ul className="mt-4 grid gap-px overflow-hidden rounded-xl border border-line bg-line sm:grid-cols-2 xl:grid-cols-4">
          {SUPPORTING_FACTS.map((fact) => (
            <li key={fact.text} className="flex items-start gap-2.5 bg-surface p-4">
              <fact.icon aria-hidden className="mt-0.5 size-4 shrink-0 text-ink-4" />
              <span className="text-[13px] leading-relaxed text-ink-3">{fact.text}</span>
            </li>
          ))}
        </ul>
      </div>
    </section>
  )
}

function DesignStatement() {
  return (
    <section className="border-b border-line px-4 py-20 sm:px-6 sm:py-24">
      <figure className="mx-auto max-w-3xl text-center">
        <blockquote className="text-balance font-display text-[clamp(1.35rem,3.5vw,1.9rem)] font-medium leading-[1.35] tracking-tight text-ink">
          “A chunk that exists in exactly one place is a chunk you are already losing. Every stage of
          the write path is arranged around noticing that before you do.”
        </blockquote>
        <figcaption className="mt-6 text-[12.5px] font-medium uppercase tracking-wider text-ink-4">
          AstraStore design principle
        </figcaption>
      </figure>
    </section>
  )
}

function Developers() {
  return (
    <section id="developers" className="scroll-mt-20 border-b border-line px-4 py-20 sm:px-6 sm:py-24">
      <div className="mx-auto grid max-w-6xl gap-10 lg:grid-cols-[minmax(0,0.85fr)_minmax(0,1.15fr)] lg:gap-14">
        <div>
          <SectionHeading eyebrow="Developer surface" title="Talk to it the way you already work">
            One gateway, one object model, four clients. Authenticate with an email and password for
            an interactive session, or with an API key for anything that runs unattended.
          </SectionHeading>

          <dl className="mt-8 space-y-4 border-t border-line pt-6">
            {[
              {
                term: 'Sessions',
                detail: 'Short-lived access token plus a rotating refresh token, revoked on sign-out.',
              },
              {
                term: 'API keys',
                detail: 'Created from the console or the CLI. The raw key is displayed once and never again.',
              },
              {
                term: 'Object addressing',
                detail: 'Buckets are UUIDs; keys are free-form paths. Both routes resolve to the same object.',
              },
            ].map((row) => (
              <div key={row.term} className="grid gap-1 sm:grid-cols-[8rem_minmax(0,1fr)] sm:gap-4">
                <dt className="text-[13px] font-semibold text-ink">{row.term}</dt>
                <dd className="text-[13px] leading-relaxed text-ink-3">{row.detail}</dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="min-w-0">
          <CodeSample />
        </div>
      </div>
    </section>
  )
}

function ClosingCta() {
  return (
    <section className="accent-wash px-4 py-20 sm:px-6 sm:py-24">
      <div className="mx-auto max-w-3xl rounded-2xl border border-line bg-surface p-8 text-center shadow-sm sm:p-12">
        <h2 className="text-balance text-[clamp(1.6rem,4.5vw,2.25rem)] font-semibold leading-tight text-ink">
          Bring up a cluster and put something in it
        </h2>
        <p className="mx-auto mt-4 max-w-lg text-pretty text-[15px] leading-relaxed text-ink-3">
          Create an account on this deployment to use the console, the CLI and the SDKs against it.
          Running your own is a Docker Compose stack away.
        </p>
        <div className="mt-8 flex flex-wrap items-center justify-center gap-3">
          <Button asChild size="lg">
            <Link to="/register">
              Create an account <ArrowRight className="size-4" aria-hidden />
            </Link>
          </Button>
          <Button asChild size="lg" variant="secondary">
            <Link to="/login">Sign in</Link>
          </Button>
        </div>
      </div>
    </section>
  )
}

export function LandingPage() {
  useHashScroll()

  return (
    <PublicLayout>
      <Hero />
      <HowItWorks />
      <Capabilities />
      <DesignStatement />
      <Developers />
      <ClosingCta />
    </PublicLayout>
  )
}
