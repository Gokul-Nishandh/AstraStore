import { useEffect, type ReactNode } from 'react'
import { PublicLayout } from '../layout/PublicLayout'

export interface LegalSection {
  id: string
  title: string
  body: ReactNode
}

export interface LegalPageProps {
  title: string
  /** One sentence saying what this document is for, in plain language. */
  summary: ReactNode
  /** ISO date. Rendered in the reader's locale. */
  updated: string
  sections: LegalSection[]
}

/**
 * The frame all four policy documents share.
 *
 * Long-form prose has different typographic needs from the console: the
 * measure is capped near 70 characters because a policy read edge-to-edge on
 * a wide monitor is genuinely harder to follow, and the heading scale is
 * flatter because these documents are navigated by their contents list
 * rather than skimmed for one number.
 */
export function LegalPage({ title, summary, updated, sections }: LegalPageProps) {
  useEffect(() => {
    document.title = `${title} — AstraStore`
    return () => {
      document.title = 'AstraStore — Distributed object storage'
    }
  }, [title])

  const formattedDate = new Date(updated).toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
  })

  return (
    <PublicLayout>
      <div className="accent-wash border-b border-line">
        <div className="mx-auto w-full max-w-3xl px-4 py-12 sm:px-6 sm:py-16">
          <p className="text-[11px] font-semibold uppercase tracking-[0.09em] text-accent-text">Legal</p>
          <h1 className="mt-2 font-display text-3xl font-semibold tracking-tight text-balance text-ink sm:text-4xl">
            {title}
          </h1>
          <p className="mt-4 max-w-2xl text-pretty text-[15px] leading-relaxed text-ink-2">{summary}</p>
          <p className="mt-6 text-[12.5px] text-ink-4">
            Last updated <time dateTime={updated}>{formattedDate}</time>
          </p>
        </div>
      </div>

      <div className="mx-auto w-full max-w-3xl px-4 py-10 sm:px-6 sm:py-14">
        <nav aria-labelledby="contents" className="mb-10 rounded-xl border border-line bg-surface p-5">
          <h2 id="contents" className="font-display text-[13px] font-semibold uppercase tracking-wide text-ink-3">
            Contents
          </h2>
          <ol className="mt-3 space-y-1.5">
            {sections.map((section, index) => (
              <li key={section.id} className="flex gap-3 text-[13.5px]">
                <span aria-hidden className="tnum shrink-0 text-ink-4">
                  {String(index + 1).padStart(2, '0')}
                </span>
                <a
                  href={`#${section.id}`}
                  className="text-ink-2 underline-offset-2 transition-colors hover:text-accent-text hover:underline"
                >
                  {section.title}
                </a>
              </li>
            ))}
          </ol>
        </nav>

        <div className="space-y-10">
          {sections.map((section, index) => (
            <section key={section.id} id={section.id} className="scroll-mt-24">
              <h2 className="font-display text-lg font-semibold tracking-tight text-ink">
                <span aria-hidden className="tnum mr-2.5 text-ink-4">
                  {String(index + 1).padStart(2, '0')}
                </span>
                {section.title}
              </h2>
              <div className="prose-legal mt-3 space-y-3 text-[14.5px] leading-relaxed text-ink-2">
                {section.body}
              </div>
            </section>
          ))}
        </div>
      </div>
    </PublicLayout>
  )
}

/**
 * Marks a value the operator must supply before this document is published.
 *
 * Rendered as a visible, high-contrast chip rather than plain text on
 * purpose: a placeholder that blends into the prose is one that ships.
 */
export function Placeholder({ children }: { children: ReactNode }) {
  return (
    <mark className="rounded border border-warning-border bg-warning-subtle px-1.5 py-0.5 font-mono text-[12.5px] font-medium text-warning-text">
      {children}
    </mark>
  )
}
