import { useCallback, useRef, useState } from 'react'
import { api } from '../../lib/api'
import { toUserMessage } from '../../lib/errors'
import { useToast } from '../ui/toast-context'
import type { ObjectRecord } from '../../types/api'

export interface StarState {
  /** The value to render — the pending optimistic one when there is one. */
  isStarred: (object: ObjectRecord) => boolean
  isBusy: (object: ObjectRecord) => boolean
  toggle: (object: ObjectRecord) => void
}

/**
 * Optimistic starring, shared by every surface that shows an object.
 *
 * The lists behind these rows come from `usePolling`, which owns its data and
 * hands back an immutable snapshot — so instead of editing the row we keep a
 * thin overlay of pending values keyed by object id and read through it. The
 * star flips on click; on failure the overlay entry is set back to the value
 * that was on screen before the click, which is also what the server still
 * believes, so the rollback and the next refresh agree.
 */
export function useStarState(options: { onSettled?: (object: ObjectRecord, starred: boolean) => void } = {}): StarState {
  const { toast } = useToast()
  const [overrides, setOverrides] = useState<Record<string, boolean>>({})
  const [busy, setBusy] = useState<Record<string, boolean>>({})

  const settledRef = useRef(options.onSettled)
  settledRef.current = options.onSettled

  const isStarred = useCallback(
    (object: ObjectRecord) => overrides[object.id] ?? object.starred,
    [overrides],
  )

  const isBusy = useCallback((object: ObjectRecord) => busy[object.id] === true, [busy])

  const toggle = useCallback(
    (object: ObjectRecord) => {
      if (busy[object.id]) return

      const previous = overrides[object.id] ?? object.starred
      const next = !previous

      setOverrides((current) => ({ ...current, [object.id]: next }))
      setBusy((current) => ({ ...current, [object.id]: true }))

      const call = next ? api.star(object.id) : api.unstar(object.id)

      call
        .then(() => settledRef.current?.(object, next))
        .catch((error: unknown) => {
          setOverrides((current) => ({ ...current, [object.id]: previous }))
          toast(
            toUserMessage(error, next ? 'Could not star that object.' : 'Could not remove that star.'),
            'error',
          )
        })
        .finally(() => {
          setBusy((current) => {
            const { [object.id]: _removed, ...rest } = current
            return rest
          })
        })
    },
    [busy, overrides, toast],
  )

  return { isStarred, isBusy, toggle }
}
