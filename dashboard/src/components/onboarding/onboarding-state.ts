import { useCallback, useMemo, useState } from 'react'
import type { OnboardingState, OnboardingStep } from '../../types/api'

/**
 * Onboarding progress lives in localStorage, keyed per user id.
 *
 * There is no backend endpoint for it, and the alternative — inferring
 * "finished" from whether the account happens to own a bucket — would drag
 * a returning user back through the wizard the moment they deleted one.
 * Keying by id also stops two accounts on a shared machine from inheriting
 * each other's progress.
 */
const KEY_PREFIX = 'astrastore_onboarding_'

export const BLANK_ONBOARDING: OnboardingState = {
  completed: false,
  dismissed: false,
  steps: { bucket: false, upload: false, apiKey: false },
}

function storageKey(userId: number | undefined): string | null {
  return userId == null ? null : `${KEY_PREFIX}${userId}`
}

export function readOnboarding(userId: number | undefined): OnboardingState {
  const key = storageKey(userId)
  if (!key) return BLANK_ONBOARDING

  try {
    const raw = localStorage.getItem(key)
    if (!raw) return BLANK_ONBOARDING
    const parsed = JSON.parse(raw) as Partial<OnboardingState>
    return {
      completed: parsed.completed === true,
      dismissed: parsed.dismissed === true,
      steps: { ...BLANK_ONBOARDING.steps, ...(parsed.steps ?? {}) },
    }
  } catch {
    // Corrupt or blocked storage must not break the screen it gates.
    return BLANK_ONBOARDING
  }
}

export function writeOnboarding(userId: number | undefined, state: OnboardingState): void {
  const key = storageKey(userId)
  if (!key) return
  try {
    localStorage.setItem(key, JSON.stringify(state))
  } catch {
    /* storage blocked — progress is lost on reload, nothing else breaks */
  }
}

export interface OnboardingController {
  state: OnboardingState
  completeStep: (step: OnboardingStep) => void
  finish: () => void
  dismiss: () => void
}

export function useOnboarding(userId: number | undefined): OnboardingController {
  const [state, setState] = useState<OnboardingState>(() => readOnboarding(userId))

  const completeStep = useCallback(
    (step: OnboardingStep) => {
      setState((current) => {
        const next = { ...current, steps: { ...current.steps, [step]: true } }
        writeOnboarding(userId, next)
        return next
      })
    },
    [userId],
  )

  const finish = useCallback(() => {
    setState((current) => {
      const next = { ...current, completed: true }
      writeOnboarding(userId, next)
      return next
    })
  }, [userId])

  const dismiss = useCallback(() => {
    setState((current) => {
      const next = { ...current, dismissed: true }
      writeOnboarding(userId, next)
      return next
    })
  }, [userId])

  return useMemo(
    () => ({ state, completeStep, finish, dismiss }),
    [state, completeStep, finish, dismiss],
  )
}
