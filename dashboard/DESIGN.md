# AstraStore console — design system

The contract every screen is built against. If a rule here conflicts with
something you were about to do, the rule wins.

## 1. Colour: "Copper Nebula"

Defined once, in `src/index.css`. **Never hardcode a hex or an `oklch()`
outside that file.** If a colour is missing, add a token there.

| Group | Tokens | Use |
|---|---|---|
| Surfaces | `bg`, `bg-elevated`, `surface`, `surface-2`, `surface-3`, `surface-4`, `sidebar` | Page → panel → raised. Warm graphite, hue 60 — not slate. |
| Ink | `ink`, `ink-2`, `ink-3`, `ink-4` | Primary → faintest. `ink-4` is the last step that still clears 4.5:1. |
| Structure | `line`, `line-strong`, `overlay`, `ring` | Hairline borders. |
| Accent | `accent`, `accent-hover`, `accent-active`, `accent-text`, `accent-subtle`, `accent-border`, `on-accent` | Burnished copper. |
| Status | `success`, `warning`, `danger` (+ `-text`, `-subtle`, `-border`, `on-*`) | State only. |
| Charts | `chart-1` … `chart-6` | Categorical series. |

Three rules this palette exists to enforce:

1. **Copper is identity and intent, never status.** Brand, primary action,
   active nav, focus, links. A healthy node is `success`, never `accent`.
2. **Every coloured fill ships its own `--on-*` foreground.** Writing
   `bg-accent` without `text-on-accent` is a bug — that exact pairing is what
   shipped invisible white-on-white button labels in the previous build.
3. **No raw hex, ever.** Including in SVG `fill`/`stroke` — use
   `fill="var(--chart-2)"`.

Dark is the default (`:root`); light is the `.light` class. Both are complete.
Never write a colour that only exists in one theme.

## 2. Typography

- `font-display` (Space Grotesk) — headings only, via `h1/h2/h3`.
- `font-sans` (Inter) — all UI text. **Hero numbers use this, not display.**
- `font-mono` (JetBrains Mono) — machine values only: object IDs, checksums,
  byte counts, IP addresses. Add `.tnum` to any figure that updates live so
  the digits do not jitter.

## 3. Components — use these, do not rebuild them

Everything in `src/components/ui/`:

`Button` (variants primary/secondary/subtle/ghost/danger; `asChild` for links)
· `Card`, `CardHeader`, `CardTitle`, `CardDescription`, `CardSection`
· `Table`, `THead`, `TR`, `TH`, `TD` · `Badge` + `StatusDot` (tones
neutral/accent/success/warning/danger/info) · `Dialog`, `ConfirmDialog`
· `Field` · `Select` · `Tabs`, `SegmentedControl` · `Menu` · `Tooltip`
· `Checkbox`, `Switch` · `Toast` (via `useToast`) · `EmptyState`, `ErrorState`
· `Skeleton`, `SkeletonRows`, `SkeletonTile`, `LoadingRegion` · `PageHeader`
· `Spinner` · `ProgressBar` · `CopyButton` · `BrandMark` · `ThemeToggle`

Charts in `src/components/charts/`: `Sparkline`, `Meter`, and `scale.ts`
(`linearScale`, `niceTicks`, `niceDomain`, `linePath`, `downsample`,
`seriesColor`).

Domain helpers: `nodeStateTone()` and `serviceStatusTone()` in `Badge.tsx` map
a backend state to a tone — use them so DEGRADED is amber on every screen.

## 4. Data rules — the ones that matter most

**Never invent a number.** The backend returns `null` to mean "not enough data
yet", never zero. Render an em dash or "Awaiting data" — never `0`, never
`100%`. A fresh cluster must not claim perfect uptime, and it must not claim
a capacity no hardware has. The previous build displayed a phantom
"10 TB across 3 nodes, 6% used" and it is the single thing the product owner
called out by name. `formatDuration`, `formatUptime`, `formatMillis`,
`formatCount` and `formatBytes` in `src/lib/format.ts` all take `null` and
return `—`; route every figure through them.

**Capacity has two truths.** `rawBytesStored` is what is physically on disk;
`logicalBytesStored` is what users uploaded. With replication factor 2 a 1 GB
object occupies 2 GB. Label which one you are showing.

## 5. Every async surface has four states

Loading (skeleton shaped like the content), empty (`EmptyState` — says what
belongs here and offers the action that creates it), error (`ErrorState` with
a working retry), and loaded. A bare "No data" is the thing `EmptyState`
exists to prevent. `usePolling` in `src/lib/hooks.ts` gives you
`{ data, error, loading, lastUpdated, refresh }` and keeps the last good data
on a failed refresh.

## 6. Errors

`toUserMessage(error, fallback)` from `src/lib/errors.ts` turns any thrown
value into one display-safe sentence. **Nothing else may ever be rendered.**
No status codes, no exception text, no `error.message` straight from a
response. Use a toast for the result of an action, an inline `ErrorState` for
a surface that failed to load.

## 7. Accessibility and interaction

- Focus is handled globally — do not remove outlines.
- Touch targets ≥ 44px on interactive rows and icon buttons (`min-h-11`).
- Colour is never the only signal: a status dot always sits beside a label.
- Icons that carry no meaning get `aria-hidden`; icon-only buttons get
  `aria-label`.
- Destructive actions go through `ConfirmDialog` and name what is being
  destroyed. Anything irreversible (purging an object, deleting an account)
  says so.
- Respect `prefers-reduced-motion` — use the `motion-safe:` prefix.

## 8. Responsive — every screen, from 375px

- No horizontal scrolling on `<body>`, ever. Wide content scrolls in its own
  `.scroll-x` container.
- Tables become card lists below `sm`, or scroll inside their own container.
  A table that forces the page sideways is a bug.
- Grids reflow to one column. Dialogs become bottom sheets on small screens.
- Test at 375, 768, 1280.

## 9. Visual direction

Dense, calm, engineered. Hairline borders over heavy shadows; generous
whitespace inside panels; one accent per view. Reference points: a soft
radial gradient wash behind hero sections, pill-shaped nav clusters, dense
stat grids with hairline dividers, and file-manager layouts with a quiet left
rail. Take the structure, not the palette — the colour identity is Copper
Nebula and nothing else.

The product must read as one application. A screen that looks like it came
from a different template is wrong even if it is attractive.
