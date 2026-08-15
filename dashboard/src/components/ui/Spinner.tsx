import { cn } from '../../lib/cn'

export function Spinner({ className }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'inline-block motion-safe:animate-spin-slow rounded-full border-[1.5px] border-line-strong border-t-accent',
        'size-4',
        className,
      )}
    />
  )
}
