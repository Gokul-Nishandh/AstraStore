import { useId } from 'react'

/**
 * The write path, drawn to scale rather than described in four icon boxes.
 *
 * Everything on it is a real stage of the pipeline: the upload service chunks
 * and hashes, placement chooses a node that is answering heartbeats, the
 * replication service pushes the chunk node-to-node, and the scanner closes
 * the loop by republishing anything that is short of its replica target.
 *
 * Colours come from tokens via Tailwind's `fill-*`/`stroke-*` utilities so the
 * drawing follows the theme instead of pinning a hex that only works in dark.
 */
export function PipelineDiagram() {
  const id = useId()
  const arrow = `${id}-arrow`
  const arrowUp = `${id}-arrow-up`

  return (
    <div className="scroll-x -mx-4 px-4 sm:mx-0 sm:px-0">
      <svg
        viewBox="0 0 920 320"
        role="img"
        aria-labelledby={`${id}-title ${id}-desc`}
        className="w-full min-w-[760px]"
      >
        <title id={`${id}-title`}>The AstraStore write path</title>
        <desc id={`${id}-desc`}>
          An object is split into hashed chunks; placement selects a storage node that is passing its
          heartbeat; the replication service pushes each chunk node-to-node to two further replicas;
          and a scanner republishes any chunk left below its replica target.
        </desc>

        <defs>
          <marker
            id={arrow}
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M0 0 L10 5 L0 10 z" className="fill-ink-4" />
          </marker>
          <marker
            id={arrowUp}
            viewBox="0 0 10 10"
            refX="9"
            refY="5"
            markerWidth="6"
            markerHeight="6"
            orient="auto-start-reverse"
          >
            <path d="M0 0 L10 5 L0 10 z" className="fill-accent" />
          </marker>
        </defs>

        {[
          { x: 15, n: '1', label: 'Chunk' },
          { x: 245, n: '2', label: 'Place' },
          { x: 475, n: '3', label: 'Replicate' },
          { x: 705, n: '4', label: 'Heal' },
        ].map((panel) => (
          <g key={panel.n}>
            <rect
              x={panel.x}
              y={44}
              width={200}
              height={216}
              rx={12}
              className="fill-surface stroke-line"
              strokeWidth={1}
            />
            <circle
              cx={panel.x + 22}
              cy={70}
              r={11}
              className="fill-accent-subtle stroke-accent-border"
              strokeWidth={1}
            />
            <text
              x={panel.x + 22}
              y={74}
              textAnchor="middle"
              className="fill-accent-text font-mono text-[11px] font-medium"
            >
              {panel.n}
            </text>
            <text x={panel.x + 42} y={75} className="fill-ink font-sans text-[14px] font-semibold">
              {panel.label}
            </text>
          </g>
        ))}

        {[215, 445, 675].map((x) => (
          <line
            key={x}
            x1={x + 4}
            y1={152}
            x2={x + 26}
            y2={152}
            className="stroke-line-strong"
            strokeWidth={1.5}
            markerEnd={`url(#${arrow})`}
          />
        ))}

        {/* 1 — chunk */}
        <g>
          <rect
            x={53}
            y={104}
            width={124}
            height={26}
            rx={6}
            className="fill-surface-2 stroke-line-strong"
            strokeWidth={1}
          />
          <text x={115} y={121} textAnchor="middle" className="fill-ink-2 font-mono text-[10.5px]">
            object
          </text>
          {[67, 115, 163].map((cx) => (
            <path
              key={cx}
              d={`M115 130 C115 156 ${cx} 152 ${cx} 172`}
              fill="none"
              className="stroke-line-strong"
              strokeWidth={1.25}
              markerEnd={`url(#${arrow})`}
            />
          ))}
          {[52, 100, 148].map((x) => (
            <rect
              key={x}
              x={x}
              y={178}
              width={30}
              height={30}
              rx={6}
              className="fill-accent-subtle stroke-accent-border"
              strokeWidth={1}
            />
          ))}
          <text x={115} y={230} textAnchor="middle" className="fill-ink-3 font-sans text-[11px]">
            8 KB streaming buffer
          </text>
          <text x={115} y={245} textAnchor="middle" className="fill-ink-3 font-sans text-[11px]">
            SHA-256 per chunk and object
          </text>
        </g>

        {/* 2 — place */}
        <g>
          <rect
            x={330}
            y={104}
            width={30}
            height={30}
            rx={6}
            className="fill-accent-subtle stroke-accent-border"
            strokeWidth={1}
          />
          <line
            x1={345}
            y1={138}
            x2={345}
            y2={174}
            className="stroke-accent"
            strokeWidth={1.5}
            strokeDasharray="4 3"
            markerEnd={`url(#${arrowUp})`}
          />
          {[
            { x: 262, chosen: false },
            { x: 322, chosen: true },
            { x: 382, chosen: false },
          ].map((node) => (
            <g key={node.x}>
              <rect
                x={node.x}
                y={180}
                width={46}
                height={34}
                rx={7}
                strokeWidth={node.chosen ? 1.5 : 1}
                className={
                  node.chosen
                    ? 'fill-accent-subtle stroke-accent'
                    : 'fill-surface-2 stroke-line-strong'
                }
              />
              <line
                x1={node.x + 10}
                y1={197}
                x2={node.x + 36}
                y2={197}
                className={node.chosen ? 'stroke-accent-border' : 'stroke-line-strong'}
                strokeWidth={1}
              />
            </g>
          ))}
          <text x={345} y={230} textAnchor="middle" className="fill-ink-3 font-sans text-[11px]">
            a node still answering its
          </text>
          <text x={345} y={245} textAnchor="middle" className="fill-ink-3 font-sans text-[11px]">
            10-second heartbeat
          </text>
        </g>

        {/* 3 — replicate */}
        <g>
          <rect
            x={552}
            y={104}
            width={46}
            height={34}
            rx={7}
            className="fill-accent-subtle stroke-accent"
            strokeWidth={1.5}
          />
          {[
            { to: 528, from: 566 },
            { to: 620, from: 584 },
          ].map((leg) => (
            <path
              key={leg.to}
              d={`M${leg.from} 138 C${leg.from} 162 ${leg.to} 156 ${leg.to} 174`}
              fill="none"
              className="stroke-line-strong"
              strokeWidth={1.25}
              markerEnd={`url(#${arrow})`}
            />
          ))}
          {[505, 597].map((x) => (
            <g key={x}>
              <rect
                x={x}
                y={180}
                width={46}
                height={34}
                rx={7}
                className="fill-surface-2 stroke-line-strong"
                strokeWidth={1}
              />
              <line
                x1={x + 10}
                y1={197}
                x2={x + 36}
                y2={197}
                className="stroke-line-strong"
                strokeWidth={1}
              />
            </g>
          ))}
          <text x={575} y={230} textAnchor="middle" className="fill-ink-3 font-sans text-[11px]">
            node-to-node push to two
          </text>
          <text x={575} y={245} textAnchor="middle" className="fill-ink-3 font-sans text-[11px]">
            further replicas
          </text>
        </g>

        {/* 4 — heal */}
        <g>
          <circle
            cx={805}
            cy={122}
            r={24}
            fill="none"
            className="stroke-accent-border"
            strokeWidth={1.5}
            strokeDasharray="5 4"
          />
          <text
            x={805}
            y={126}
            textAnchor="middle"
            className="fill-accent-text font-mono text-[11px] font-medium"
          >
            60s
          </text>
          <path
            d="M814 143 C838 156 848 160 852 172"
            fill="none"
            className="stroke-accent"
            strokeWidth={1.5}
            strokeDasharray="4 3"
            markerEnd={`url(#${arrowUp})`}
          />
          {[730, 784, 838].map((x, i) => (
            <g key={x}>
              <rect
                x={x}
                y={180}
                width={42}
                height={34}
                rx={7}
                strokeWidth={i === 2 ? 1.5 : 1}
                strokeDasharray={i === 2 ? '4 3' : undefined}
                className={i === 2 ? 'fill-none stroke-danger' : 'fill-surface-2 stroke-line-strong'}
              />
              {i !== 2 && (
                <line
                  x1={x + 9}
                  y1={197}
                  x2={x + 33}
                  y2={197}
                  className="stroke-line-strong"
                  strokeWidth={1}
                />
              )}
            </g>
          ))}
          <text x={805} y={230} textAnchor="middle" className="fill-ink-3 font-sans text-[11px]">
            a chunk below its replica
          </text>
          <text x={805} y={245} textAnchor="middle" className="fill-ink-3 font-sans text-[11px]">
            target is re-queued
          </text>
        </g>

        {/* The loop back into stage 3: healing does not copy chunks itself, it
            republishes the same event the normal write path consumes. */}
        <path
          d="M805 260 L805 286 Q805 296 795 296 L585 296 Q575 296 575 286 L575 268"
          fill="none"
          className="stroke-accent-border"
          strokeWidth={1.5}
          markerEnd={`url(#${arrowUp})`}
        />
        <text x={690} y={282} textAnchor="middle" className="fill-ink-4 font-sans text-[11px]">
          recovery event republished to the replication queue
        </text>
      </svg>
    </div>
  )
}
