/** Formatting helpers — all data rendered with tabular numerals. */

export function formatBytes(bytes: number | null | undefined): string {
  if (bytes == null || Number.isNaN(bytes)) return '—'
  if (bytes === 0) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB', 'PB']
  const i = Math.min(Math.floor(Math.log(bytes) / Math.log(1024)), units.length - 1)
  const value = bytes / 1024 ** i
  const precision = value >= 100 ? 0 : value >= 10 ? 1 : 2
  return `${value.toFixed(precision)} ${units[i]}`
}

export function formatPercent(fraction: number | string | null | undefined): string {
  const n = typeof fraction === 'string' ? parseFloat(fraction) : fraction
  if (n == null || Number.isNaN(n)) return '—'
  return `${(n * 100).toFixed(n * 100 % 1 === 0 ? 0 : 1)}%`
}

export function formatDate(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleString(undefined, {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export function formatTime(iso: string | null | undefined): string {
  if (!iso) return '—'
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return '—'
  return d.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

export function timeAgo(iso: string | null | undefined): string {
  if (!iso) return 'never'
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return '—'
  const seconds = Math.max(0, Math.floor((Date.now() - then) / 1000))
  if (seconds < 5) return 'just now'
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

export function truncateChecksum(checksum: string, head = 8, tail = 8): string {
  if (!checksum) return '—'
  if (checksum.length <= head + tail + 1) return checksum
  return `${checksum.slice(0, head)}…${checksum.slice(-tail)}`
}

export function encodeObjectKey(key: string): string {
  return key
    .split('/')
    .map((seg) => encodeURIComponent(seg))
    .join('/')
}

export function fileTypeLabel(contentType: string | null | undefined): string {
  if (!contentType || contentType === 'application/octet-stream') return 'binary'
  return contentType
}

export type FileCategory = 'image' | 'video' | 'audio' | 'document' | 'spreadsheet' | 'presentation' | 'archive' | 'code' | 'pdf' | 'other'

function extCategory(key: string): FileCategory {
  const ext = key.split('.').pop()?.toLowerCase() ?? ''
  if (['jpg', 'jpeg', 'png', 'gif', 'webp', 'svg', 'bmp', 'ico'].includes(ext)) return 'image'
  if (['mp4', 'mov', 'avi', 'mkv', 'webm'].includes(ext)) return 'video'
  if (['mp3', 'wav', 'flac', 'aac', 'ogg'].includes(ext)) return 'audio'
  if (['doc', 'docx', 'txt', 'rtf', 'md'].includes(ext)) return 'document'
  if (['xls', 'xlsx', 'csv'].includes(ext)) return 'spreadsheet'
  if (['ppt', 'pptx', 'key'].includes(ext)) return 'presentation'
  if (['zip', 'rar', '7z', 'tar', 'gz'].includes(ext)) return 'archive'
  if (['js', 'ts', 'jsx', 'tsx', 'py', 'java', 'c', 'cpp', 'go', 'rs', 'html', 'css', 'json', 'xml'].includes(ext)) return 'code'
  if (ext === 'pdf') return 'pdf'
  return 'other'
}

export function fileCategory(contentType: string | undefined, key: string): FileCategory {
  if (!contentType) return extCategory(key)
  if (contentType.startsWith('image/')) return 'image'
  if (contentType.startsWith('video/')) return 'video'
  if (contentType.startsWith('audio/')) return 'audio'
  if (contentType.includes('pdf')) return 'pdf'
  if (contentType.includes('spreadsheet') || contentType.includes('csv')) return 'spreadsheet'
  if (contentType.includes('presentation')) return 'presentation'
  if (contentType.includes('word') || contentType.includes('text/plain') || contentType.includes('markdown')) return 'document'
  if (contentType.includes('zip') || contentType.includes('archive') || contentType.includes('compressed')) return 'archive'
  return extCategory(key)
}

/**
 * File-type tiles.
 *
 * File categories are categorical data, so they use the chart series — the
 * one token group designed to stay legible against each other and in both
 * themes. `chart-1` is deliberately skipped: it is the copper brand hue, and
 * a spreadsheet icon is not a brand moment.
 *
 * The tint is the same token at low alpha rather than a separate light-mode
 * shade, which is what keeps these readable on a near-black surface — the
 * previous fixed `bg-rose-100` tiles glowed white in dark mode.
 */
export function fileTypeColor(category: FileCategory): { bg: string; text: string } {
  switch (category) {
    case 'image': return { bg: 'bg-chart-4/12', text: 'text-chart-4' }
    case 'video': return { bg: 'bg-chart-6/12', text: 'text-chart-6' }
    case 'audio': return { bg: 'bg-chart-3/12', text: 'text-chart-3' }
    case 'document': return { bg: 'bg-chart-2/12', text: 'text-chart-2' }
    case 'spreadsheet': return { bg: 'bg-chart-5/12', text: 'text-chart-5' }
    case 'presentation': return { bg: 'bg-chart-3/12', text: 'text-chart-3' }
    case 'archive': return { bg: 'bg-neutral-subtle', text: 'text-ink-3' }
    case 'code': return { bg: 'bg-chart-6/12', text: 'text-chart-6' }
    case 'pdf': return { bg: 'bg-chart-4/12', text: 'text-chart-4' }
    default: return { bg: 'bg-neutral-subtle', text: 'text-ink-3' }
  }
}

/* ------------------------------------------------------------------ *
 * Telemetry formatting
 *
 * These all take `null` to mean "no data yet" and render an em dash. That
 * is the whole contract: a caller cannot accidentally turn missing
 * availability data into a confident "0%" by passing it through here.
 * ------------------------------------------------------------------ */

/** A duration in seconds as an operator would write it: `2d 4h`, `3m 12s`. */
export function formatDuration(seconds: number | null | undefined): string {
  if (seconds == null || Number.isNaN(seconds)) return '—'
  if (seconds <= 0) return 'none'

  const days = Math.floor(seconds / 86400)
  const hours = Math.floor((seconds % 86400) / 3600)
  const minutes = Math.floor((seconds % 3600) / 60)
  const secs = Math.floor(seconds % 60)

  if (days > 0) return hours > 0 ? `${days}d ${hours}h` : `${days}d`
  if (hours > 0) return minutes > 0 ? `${hours}h ${minutes}m` : `${hours}h`
  if (minutes > 0) return secs > 0 ? `${minutes}m ${secs}s` : `${minutes}m`
  return `${secs}s`
}

/**
 * An availability percentage at the precision operators actually read it:
 * the interesting part of 99.97% is the tail, so high values keep two
 * decimals while a service sitting at 60% does not need them.
 */
export function formatUptime(percent: number | null | undefined): string {
  if (percent == null || Number.isNaN(percent)) return '—'
  if (percent >= 99.99) return '100%'
  if (percent >= 99) return `${percent.toFixed(2)}%`
  if (percent >= 90) return `${percent.toFixed(1)}%`
  return `${Math.round(percent)}%`
}

export function formatMillis(ms: number | null | undefined): string {
  if (ms == null || Number.isNaN(ms)) return '—'
  if (ms < 1) return '<1 ms'
  if (ms < 1000) return `${Math.round(ms)} ms`
  return `${(ms / 1000).toFixed(2)} s`
}

/** Thousands separators, so a chunk count is scannable at a glance. */
export function formatCount(value: number | null | undefined): string {
  if (value == null || Number.isNaN(value)) return '—'
  return value.toLocaleString()
}
