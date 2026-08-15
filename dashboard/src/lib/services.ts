export interface ServiceDef {
  id: string
  name: string
  kind: 'service' | 'node'
}

export const SERVICES: ServiceDef[] = [
  { id: 'gateway', name: 'API Gateway', kind: 'service' },
  { id: 'auth', name: 'Auth', kind: 'service' },
  { id: 'upload', name: 'Upload', kind: 'service' },
  { id: 'download', name: 'Download', kind: 'service' },
  { id: 'metadata', name: 'Metadata', kind: 'service' },
  { id: 'placement', name: 'Placement', kind: 'service' },
  { id: 'replication', name: 'Replication', kind: 'service' },
  { id: 'monitoring', name: 'Monitoring', kind: 'service' },
  { id: 'node-1', name: 'Node 1', kind: 'node' },
  { id: 'node-2', name: 'Node 2', kind: 'node' },
  { id: 'node-3', name: 'Node 3', kind: 'node' },
]
