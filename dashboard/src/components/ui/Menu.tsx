import {
  useCallback,
  useEffect,
  useId,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { createPortal } from 'react-dom'
import { cn } from '../../lib/cn'

export interface MenuItem {
  label: string
  icon?: ReactNode
  onSelect: () => void
  /** Renders in the danger tone and sits below a divider. */
  destructive?: boolean
  disabled?: boolean
}

export interface MenuProps {
  /** The control that opens the menu. Receives the props it must spread. */
  trigger: (props: {
    ref: React.Ref<HTMLButtonElement>
    onClick: () => void
    'aria-expanded': boolean
    'aria-haspopup': 'menu'
    'aria-controls': string
  }) => ReactNode
  items: MenuItem[]
  align?: 'start' | 'end'
  className?: string
}

const MENU_WIDTH = 200
const GAP = 6

/**
 * Row-level actions menu.
 *
 * Rendered into a portal and positioned against the viewport rather than
 * absolutely inside its parent. That is not over-engineering: every table in
 * this product sits in a `.scroll-x` wrapper so wide content scrolls instead
 * of breaking the page, and `overflow` establishes a clipping context — an
 * absolutely positioned menu inside one opens correctly and is then clipped
 * to nothing, which reads to a user as a button that does not work.
 *
 * Destructive items are separated by a rule and always placed last, so
 * "Delete" is never adjacent to the item a user reaches for by muscle memory.
 */
export function Menu({ trigger, items, align = 'end', className }: MenuProps) {
  const [open, setOpen] = useState(false)
  const [position, setPosition] = useState<{ top: number; left: number } | null>(null)
  const menuId = useId()
  const triggerRef = useRef<HTMLButtonElement>(null)
  const menuRef = useRef<HTMLDivElement>(null)

  const place = useCallback(() => {
    const trigger = triggerRef.current
    if (!trigger) return

    const rect = trigger.getBoundingClientRect()
    const height = menuRef.current?.offsetHeight ?? 0

    // Flip above the trigger when there is not enough room below, so a menu
    // on the last row of a long table is not pinned off-screen.
    const below = window.innerHeight - rect.bottom
    const flip = height > 0 && below < height + GAP && rect.top > below

    const left = align === 'end' ? rect.right - MENU_WIDTH : rect.left
    setPosition({
      top: flip ? rect.top - height - GAP : rect.bottom + GAP,
      // Never let the menu leave the viewport on a narrow screen.
      left: Math.max(8, Math.min(left, window.innerWidth - MENU_WIDTH - 8)),
    })
  }, [align])

  useLayoutEffect(() => {
    if (open) place()
  }, [open, place])

  useEffect(() => {
    if (!open) return

    const close = () => setOpen(false)
    const onPointerDown = (event: PointerEvent) => {
      const target = event.target as Node
      if (triggerRef.current?.contains(target) || menuRef.current?.contains(target)) return
      close()
    }
    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        close()
        triggerRef.current?.focus()
      }
    }

    document.addEventListener('pointerdown', onPointerDown)
    document.addEventListener('keydown', onKeyDown)
    // The menu is positioned against the viewport, so anything that moves the
    // trigger has to either reposition it or dismiss it. Scrolling dismisses:
    // chasing a scrolling row is more distracting than closing.
    window.addEventListener('scroll', close, true)
    window.addEventListener('resize', place)

    return () => {
      document.removeEventListener('pointerdown', onPointerDown)
      document.removeEventListener('keydown', onKeyDown)
      window.removeEventListener('scroll', close, true)
      window.removeEventListener('resize', place)
    }
  }, [open, place])

  const ordered = [...items.filter((i) => !i.destructive), ...items.filter((i) => i.destructive)]
  const firstDestructive = ordered.findIndex((i) => i.destructive)

  return (
    <span className={cn('inline-flex', className)}>
      {trigger({
        ref: triggerRef,
        onClick: () => setOpen((v) => !v),
        'aria-expanded': open,
        'aria-haspopup': 'menu',
        'aria-controls': menuId,
      })}

      {open &&
        createPortal(
          <div
            ref={menuRef}
            id={menuId}
            role="menu"
            style={{
              position: 'fixed',
              top: position?.top ?? -9999,
              left: position?.left ?? -9999,
              width: MENU_WIDTH,
              // Hidden until measured, so the first paint does not flash in
              // the wrong place before the flip calculation lands.
              visibility: position ? 'visible' : 'hidden',
            }}
            className="z-50 overflow-hidden rounded-lg border border-line bg-bg-elevated p-1 shadow-lg motion-safe:animate-fade-in"
          >
            {ordered.map((item, index) => (
              <div key={item.label}>
                {index === firstDestructive && index > 0 && (
                  <div role="separator" className="my-1 h-px bg-line" />
                )}
                <button
                  type="button"
                  role="menuitem"
                  disabled={item.disabled}
                  onClick={() => {
                    setOpen(false)
                    item.onSelect()
                  }}
                  className={cn(
                    'flex w-full items-center gap-2.5 rounded-md px-2.5 py-2 text-left text-[13px] font-medium',
                    'transition-colors duration-100 disabled:cursor-not-allowed disabled:text-ink-4',
                    item.destructive
                      ? 'text-danger-text hover:bg-danger-subtle'
                      : 'text-ink-2 hover:bg-surface-2 hover:text-ink',
                  )}
                >
                  {item.icon && (
                    <span aria-hidden className="grid shrink-0 place-items-center [&>svg]:size-4">
                      {item.icon}
                    </span>
                  )}
                  {item.label}
                </button>
              </div>
            ))}
          </div>,
          document.body,
        )}
    </span>
  )
}
