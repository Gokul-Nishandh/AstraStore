import { Spinner } from '../ui/Spinner'

/**
 * Shown while a lazily loaded route fetches its chunk.
 *
 * Deliberately quiet and delayed: on a fast connection a chunk arrives in
 * tens of milliseconds, and a spinner that flashes for one frame reads as a
 * glitch. The CSS delay means nothing is painted at all unless the wait is
 * long enough for a person to notice it.
 */
export function RouteFallback() {
  return (
    <div
      role="status"
      aria-live="polite"
      className="grid min-h-[60vh] place-items-center opacity-0 motion-safe:animate-fade-in [animation-delay:400ms] [animation-fill-mode:forwards]"
    >
      <span className="sr-only">Loading</span>
      <Spinner className="size-5" />
    </div>
  )
}
