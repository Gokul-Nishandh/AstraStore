import { useState } from 'react'
import { Download, Filter, ScrollText, Search, X } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card } from '../components/ui/Card'
import { Badge } from '../components/ui/Badge'
import { Select } from '../components/ui/Select'
import { Input } from '../components/ui/Field'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Pagination } from '../components/ui/Pagination'
import { Table, THead, TR, TH, TD } from '../components/ui/Table'
import { Tooltip } from '../components/ui/Tooltip'
import { useToast } from '../components/ui/toast-context'
import { api, downloadAuditCsv } from '../lib/api'
import { toUserMessage } from '../lib/errors'
import { formatDate } from '../lib/format'
import { useDebounced, usePolling } from '../lib/hooks'
import type { AuditEvent, AuditOutcome } from '../types/api'

const OUTCOMES: { value: AuditOutcome; label: string }[] = [
  { value: 'all', label: 'Any outcome' },
  { value: 'success', label: 'Succeeded' },
  { value: 'failure', label: 'Failed' },
]

/** `LOGIN_SUCCESS` reads as "Login success" without a hand-maintained map. */
function humanAction(action: string): string {
  const words = action.toLowerCase().replace(/_/g, ' ')
  return words.charAt(0).toUpperCase() + words.slice(1)
}

/**
 * Who did what, when, and whether it worked.
 *
 * Scope is decided server-side: an administrator sees the whole deployment
 * and may narrow to one account, while everyone else is pinned to their own
 * rows no matter what this page asks for. The filters here are a convenience,
 * never the access control.
 */
export function AuditLogPage() {
  const { toast } = useToast()

  const [page, setPage] = useState(0)
  const [query, setQuery] = useState('')
  const [action, setAction] = useState('')
  const [outcome, setOutcome] = useState<AuditOutcome>('all')
  const [from, setFrom] = useState('')
  const [to, setTo] = useState('')
  const [exporting, setExporting] = useState(false)

  const search = useDebounced(query, 300)

  const filters = {
    search: search || null,
    action: action || null,
    outcome,
    from: from ? new Date(from).toISOString() : null,
    to: to ? new Date(to).toISOString() : null,
  }

  const events = usePolling(
    () => api.auditLog({ ...filters, page, size: 50, sort: 'timestamp,desc' }),
    0,
    [search, action, outcome, from, to, page],
  )
  const actions = usePolling(() => api.auditActions(), 0)

  const rows = events.data?.content ?? []
  const hasFilters = Boolean(search || action || from || to || outcome !== 'all')

  const clearFilters = () => {
    setQuery('')
    setAction('')
    setOutcome('all')
    setFrom('')
    setTo('')
    setPage(0)
  }

  /* The CSV travels through the authenticated client because a plain anchor
     cannot carry the bearer token. */
  const exportCsv = async () => {
    setExporting(true)
    try {
      const blob = await downloadAuditCsv(filters)
      const url = URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `astrastore-audit-${new Date().toISOString().slice(0, 10)}.csv`
      anchor.click()
      URL.revokeObjectURL(url)
      toast('Audit trail exported.', 'success')
    } catch (error) {
      toast(toUserMessage(error, 'The audit trail could not be exported.'), 'error')
    } finally {
      setExporting(false)
    }
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Operations"
        title="Audit log"
        description="Security-relevant events across the deployment: sign-ins, key issuance, permission changes and account deletion."
        actions={
          <Button
            variant="secondary"
            icon={<Download />}
            loading={exporting}
            onClick={exportCsv}
            disabled={rows.length === 0}
          >
            Export CSV
          </Button>
        }
      />

      <Card padded={false}>
        <div className="space-y-3 border-b border-line p-3">
          <div className="flex flex-wrap items-end gap-3">
            <div className="relative min-w-0 flex-1 basis-56">
              <Search
                aria-hidden
                className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-ink-4"
              />
              <input
                type="search"
                value={query}
                onChange={(e) => {
                  setQuery(e.target.value)
                  setPage(0)
                }}
                placeholder="Search by user, email or IP address"
                aria-label="Search the audit trail"
                className="h-9 w-full rounded-md border border-line bg-surface pl-9 pr-3 text-sm text-ink outline-none transition-colors placeholder:text-ink-4 hover:border-line-strong focus-visible:border-accent"
              />
            </div>

            <div className="w-44 shrink-0">
              <Select
                aria-label="Filter by action"
                size="sm"
                value={action}
                placeholder="Any action"
                onChange={(e) => {
                  setAction(e.target.value)
                  setPage(0)
                }}
                options={(actions.data ?? []).map((a) => ({ value: a, label: humanAction(a) }))}
              />
            </div>

            <div className="w-36 shrink-0">
              <Select
                aria-label="Filter by outcome"
                size="sm"
                value={outcome}
                onChange={(e) => {
                  setOutcome(e.target.value as AuditOutcome)
                  setPage(0)
                }}
                options={OUTCOMES}
              />
            </div>
          </div>

          <div className="flex flex-wrap items-end gap-3">
            <Input
              type="date"
              label="From"
              value={from}
              onChange={(e) => {
                setFrom(e.target.value)
                setPage(0)
              }}
              wrapperClassName="w-40 shrink-0"
            />
            <Input
              type="date"
              label="To"
              value={to}
              onChange={(e) => {
                setTo(e.target.value)
                setPage(0)
              }}
              wrapperClassName="w-40 shrink-0"
            />

            {hasFilters && (
              <Button variant="ghost" size="sm" icon={<X />} onClick={clearFilters}>
                Clear filters
              </Button>
            )}

            {events.data && (
              <p className="tnum ml-auto text-[12px] text-ink-4">
                {events.data.totalElements} {events.data.totalElements === 1 ? 'event' : 'events'}
              </p>
            )}
          </div>
        </div>

        {events.error && !events.data ? (
          <div className="p-4">
            <ErrorState description={events.error} onRetry={events.refresh} />
          </div>
        ) : events.loading && !events.data ? (
          <table className="w-full">
            <tbody>
              <SkeletonRows rows={10} cols={5} />
            </tbody>
          </table>
        ) : rows.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={hasFilters ? <Filter /> : <ScrollText />}
              size="sm"
              title={hasFilters ? 'No events match those filters' : 'No events recorded yet'}
              description={
                hasFilters
                  ? 'Try widening the date range or clearing the action filter.'
                  : 'Security events appear here as soon as they happen — sign-ins, key issuance and permission changes.'
              }
              action={
                hasFilters ? (
                  <Button variant="secondary" size="sm" onClick={clearFilters}>
                    Clear filters
                  </Button>
                ) : undefined
              }
            />
          </div>
        ) : (
          <>
            <div className="scroll-x">
              <Table>
                <THead>
                  <TR>
                    <TH>When</TH>
                    <TH>User</TH>
                    <TH>Action</TH>
                    <TH>Outcome</TH>
                    <TH>Source</TH>
                  </TR>
                </THead>
                <tbody>
                  {rows.map((event) => (
                    <AuditRow key={event.id} event={event} />
                  ))}
                </tbody>
              </Table>
            </div>
            <Pagination
              page={events.data?.page ?? 0}
              totalPages={events.data?.totalPages ?? 1}
              totalElements={events.data?.totalElements ?? 0}
              pageSize={events.data?.size ?? 50}
              onChange={setPage}
              className="border-t border-line p-3"
            />
          </>
        )}
      </Card>
    </div>
  )
}

function AuditRow({ event }: { event: AuditEvent }) {
  /* A numeric id is useless to a reader, and some rows have no user at all —
     a failed sign-in against an address that does not exist, or an account
     since deleted. The attempted address is the useful identity there. */
  const displayName = event.username ?? event.actorEmail ?? 'Unknown'
  const secondary = event.username ? (event.email ?? event.actorEmail) : null
  const anonymised = !event.username && !event.actorEmail

  return (
    <TR interactive={false}>
      <TD className="whitespace-nowrap">{formatDate(event.timestamp)}</TD>
      <TD>
        <div className="min-w-0">
          <p className={anonymised ? 'truncate italic text-ink-4' : 'truncate font-medium text-ink'}>
            {anonymised ? 'Anonymised' : displayName}
          </p>
          {secondary && <p className="truncate text-[12px] text-ink-4">{secondary}</p>}
        </div>
      </TD>
      <TD>
        <span className="text-ink-2">{humanAction(event.action)}</span>
        {event.detail && <p className="mt-0.5 text-[12px] text-ink-4">{event.detail}</p>}
      </TD>
      <TD>
        {event.success ? (
          <Badge tone="success" dot>
            Succeeded
          </Badge>
        ) : (
          <Tooltip content={event.failureReason ?? 'No reason recorded'}>
            <Badge tone="danger" dot>
              Failed
            </Badge>
          </Tooltip>
        )}
      </TD>
      <TD>
        <span className="font-mono text-[12px] text-ink-3">{event.ipAddress ?? '—'}</span>
        {event.userAgent && (
          <p className="max-w-[220px] truncate text-[11.5px] text-ink-4" title={event.userAgent}>
            {event.userAgent}
          </p>
        )}
      </TD>
    </TR>
  )
}
