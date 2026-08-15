import { useId, useState } from 'react'
import { AlertTriangle, HardDrive, RefreshCw, Server } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card, CardSection } from '../components/ui/Card'
import { SegmentedControl } from '../components/ui/Tabs'
import { EmptyState, ErrorState } from '../components/ui/EmptyState'
import { Skeleton } from '../components/ui/Skeleton'
import { HealthBand } from '../components/cluster/HealthBand'
import { ServicesStrip } from '../components/cluster/ServicesStrip'
import { ServiceTable } from '../components/cluster/ServiceTable'
import { ServiceDetail } from '../components/cluster/ServiceDetail'
import { CapacityPanel } from '../components/cluster/CapacityPanel'
import { IncidentTimeline } from '../components/cluster/IncidentTimeline'
import {
  useClusterHealth,
  useIncidents,
  useMonitoringServices,
  useMonitoringSummary,
  useReplicationStatus,
} from '../lib/hooks'
import { formatDate } from '../lib/format'
import { MONITORING_WINDOWS, type MonitoringWindow } from '../types/api'

/**
 * The operations console.
 *
 * Everything on this page is measured. Where a window holds no samples the
 * backend sends null and the panels say so — "awaiting data", never a
 * confident zero or a reassuring 100%. A dashboard that invents a figure is
 * worse than one that admits ignorance, because an operator acts on it.
 */
export function OverviewPage() {
  const [window, setWindow] = useState<MonitoringWindow>('24h')
  const [selected, setSelected] = useState<string | null>(null)
  const detailPanelId = useId()

  const summary = useMonitoringSummary(window)
  const services = useMonitoringServices(window)
  // A one-hour incident list is nearly always empty and reads as "nothing
  // ever happens"; the shortest useful history here is a day.
  const incidents = useIncidents(window === '1h' ? '24h' : window, 25)
  const cluster = useClusterHealth()
  const replication = useReplicationStatus()

  const serviceList = services.data?.services ?? []
  const windowLabel = MONITORING_WINDOWS.find((w) => w.value === window)?.label ?? window

  const refreshAll = () => {
    summary.refresh()
    services.refresh()
    incidents.refresh()
    cluster.refresh()
    replication.refresh()
  }

  const toggle = (id: string) => setSelected((current) => (current === id ? null : id))

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow="Operations"
        title="Overview"
        description="Live availability, capacity and replication across the whole cluster."
        meta={
          summary.lastUpdated && (
            <span className="text-[12px] text-ink-4">
              Updated {formatDate(summary.lastUpdated.toISOString())}
            </span>
          )
        }
        actions={
          <>
            <SegmentedControl
              aria-label="Time window"
              size="sm"
              value={window}
              onChange={setWindow}
              items={MONITORING_WINDOWS.map((w) => ({
                value: w.value,
                label: w.label.replace('Last ', ''),
              }))}
            />
            <Button variant="secondary" size="sm" icon={<RefreshCw />} onClick={refreshAll}>
              Refresh
            </Button>
          </>
        }
      />

      {summary.error && !summary.data && (
        <ErrorState
          title="Monitoring is unreachable"
          description={summary.error}
          onRetry={summary.refresh}
        />
      )}

      <HealthBand
        summary={summary.data}
        cluster={cluster.data}
        replication={replication.data}
        windowLabel={windowLabel}
        loading={summary.loading && !summary.data}
      />

      <ServicesStrip
        services={serviceList}
        loading={services.loading && !services.data}
        selectedId={selected}
        onSelect={toggle}
      />

      <Card padded={false}>
        <div className="border-b border-line p-4">
          <h2 className="font-display text-[15px] font-semibold tracking-tight text-ink">
            Service availability
          </h2>
          <p className="mt-0.5 text-[12.5px] text-ink-3">
            Uptime, cumulative downtime and response times over {windowLabel.toLowerCase()}. Select a
            row for its incident history.
          </p>
        </div>

        {services.error && !services.data ? (
          <div className="p-4">
            <ErrorState description={services.error} onRetry={services.refresh} />
          </div>
        ) : services.loading && !services.data ? (
          <div className="space-y-2 p-4">
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} className="h-10 w-full" />
            ))}
          </div>
        ) : serviceList.length === 0 ? (
          <div className="p-4">
            <EmptyState
              icon={<Server />}
              size="sm"
              title="No services are being monitored"
              description="The monitoring service has not registered any probe targets yet."
            />
          </div>
        ) : (
          <div className="scroll-x">
            <ServiceTable
              services={serviceList}
              selectedId={selected}
              onSelect={toggle}
              detailPanelId={detailPanelId}
            />
          </div>
        )}
      </Card>

      {selected && (
        <div id={detailPanelId}>
          <ServiceDetail
            serviceId={selected}
            window={window}
            fallback={serviceList.find((s) => s.id === selected)}
          />
        </div>
      )}

      <div className="grid grid-cols-1 gap-4 xl:grid-cols-2">
        <CardSection
          icon={<HardDrive />}
          title="Cluster capacity"
          description="Summed from the quotas storage nodes report about themselves — never from host filesystems."
        >
          {cluster.error && !cluster.data ? (
            <ErrorState description={cluster.error} onRetry={cluster.refresh} />
          ) : cluster.loading && !cluster.data ? (
            <Skeleton className="h-40 w-full" />
          ) : cluster.data ? (
            <CapacityPanel cluster={cluster.data} />
          ) : null}
        </CardSection>

        <CardSection
          icon={<AlertTriangle />}
          title="Incident history"
          description="Every transition to unavailable, and how long it lasted."
        >
          {incidents.error && !incidents.data ? (
            <ErrorState description={incidents.error} onRetry={incidents.refresh} />
          ) : incidents.loading && !incidents.data ? (
            <div className="space-y-2">
              {Array.from({ length: 3 }).map((_, i) => (
                <Skeleton key={i} className="h-12 w-full" />
              ))}
            </div>
          ) : (
            <IncidentTimeline incidents={incidents.data?.incidents ?? []} />
          )}
        </CardSection>
      </div>
    </div>
  )
}
