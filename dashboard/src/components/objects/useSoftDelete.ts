import { useCallback, useRef } from 'react'
import { api } from '../../lib/api'
import { toUserMessage } from '../../lib/errors'
import { useToast } from '../ui/toast-context'
import { objectName } from './helpers'
import type { ObjectRecord } from '../../types/api'

/**
 * Deleting an object anywhere in the app is a soft delete: it lands in trash
 * and stays restorable. The toast says so and carries the Undo, because a
 * recovery path the user has to go looking for is one most people never find.
 */
export function useSoftDelete(onChanged: () => void) {
  const { toast } = useToast()
  const changedRef = useRef(onChanged)
  changedRef.current = onChanged

  return useCallback(
    async (object: ObjectRecord) => {
      try {
        await api.deleteObject(object.id)
        changedRef.current()
        toast('Moved to trash', 'success', {
          description: `“${objectName(object.key)}” can be restored from Trash.`,
          action: {
            label: 'Undo',
            onClick: () => {
              api
                .restoreObject(object.id)
                .then(() => {
                  changedRef.current()
                  toast(`Restored “${objectName(object.key)}”`, 'success')
                })
                .catch((error: unknown) => {
                  toast(toUserMessage(error, 'Could not restore that object.'), 'error')
                })
            },
          },
        })
        return true
      } catch (error) {
        toast(toUserMessage(error, 'Could not move that object to trash.'), 'error')
        return false
      }
    },
    [toast],
  )
}
