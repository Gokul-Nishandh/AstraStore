import { Moon, Sun } from 'lucide-react'
import { useTheme } from '../../lib/useTheme'
import { cn } from '../../lib/cn'

interface ThemeToggleProps {
  className?: string
  size?: 'sm' | 'md'
}

export function ThemeToggle({ className, size = 'md' }: ThemeToggleProps) {
  const { resolvedTheme, toggleTheme } = useTheme()
  const isDark = resolvedTheme === 'dark'

  return (
    <button
      type="button"
      onClick={toggleTheme}
      aria-label={isDark ? 'Switch to light mode' : 'Switch to dark mode'}
      className={cn(
        'relative inline-flex items-center justify-center rounded-lg border border-line bg-surface text-ink-2 transition-colors hover:bg-surface-2 hover:text-ink focus-visible:ring-2 focus-visible:ring-ring',
        size === 'sm' ? 'size-8' : 'size-9',
        className,
      )}
    >
      <Sun
        className={cn(
          'absolute size-[1.1rem] transition-all duration-200',
          isDark ? 'rotate-90 scale-0 opacity-0' : 'rotate-0 scale-100 opacity-100',
        )}
      />
      <Moon
        className={cn(
          'absolute size-[1.1rem] transition-all duration-200',
          isDark ? 'rotate-0 scale-100 opacity-100' : '-rotate-90 scale-0 opacity-0',
        )}
      />
    </button>
  )
}
