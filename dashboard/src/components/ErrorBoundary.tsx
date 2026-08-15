import { Component, type ErrorInfo, type ReactNode } from 'react'

interface Props {
  children: ReactNode
}

interface State {
  hasError: boolean
}

/**
 * The last line of defence against a white screen.
 *
 * A render-time exception in React unmounts the whole tree, leaving a blank
 * page with the real error only in the console. This catches that and shows
 * something a person can act on.
 *
 * Note what it does NOT do: it never renders `error.message`. A React error
 * is a programming fault, and its text routinely contains component stacks
 * and internal identifiers — precisely the sort of thing that must not reach
 * a user. The details go to the console for us; the user gets a sentence.
 */
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }

  static getDerivedStateFromError(): State {
    return { hasError: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('Unhandled render error:', error, info.componentStack)
  }

  render() {
    if (!this.state.hasError) return this.props.children

    return (
      <div className="grid min-h-dvh place-items-center bg-bg px-6 text-center">
        <div className="max-w-md">
          <p className="text-[11px] font-semibold uppercase tracking-[0.09em] text-accent-text">
            Something broke
          </p>
          <h1 className="mt-2 font-display text-2xl font-semibold tracking-tight text-ink">
            This page stopped responding
          </h1>
          <p className="mt-3 text-pretty text-sm leading-relaxed text-ink-3">
            The error has been logged. Reloading usually clears it — your files and
            data are unaffected.
          </p>
          <div className="mt-6 flex flex-wrap items-center justify-center gap-2">
            <button
              type="button"
              onClick={() => window.location.reload()}
              className="inline-flex h-9 items-center rounded-md bg-accent px-4 text-sm font-medium text-on-accent transition-colors hover:bg-accent-hover"
            >
              Reload the page
            </button>
            <a
              href="/dashboard"
              className="inline-flex h-9 items-center rounded-md border border-line-strong bg-surface-2 px-4 text-sm font-medium text-ink transition-colors hover:bg-surface-3"
            >
              Back to my drive
            </a>
          </div>
        </div>
      </div>
    )
  }
}
