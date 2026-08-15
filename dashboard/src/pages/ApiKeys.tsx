import { useEffect, useState } from 'react'
import { KeyRound, Plus, Trash2, Copy } from 'lucide-react'
import { PageHeader } from '../components/ui/PageHeader'
import { Button } from '../components/ui/Button'
import { Card, CardHeader, CardTitle, CardDescription } from '../components/ui/Card'
import { Input } from '../components/ui/Field'
import { Dialog } from '../components/ui/Dialog'
import { EmptyState } from '../components/ui/EmptyState'
import { SkeletonRows } from '../components/ui/Skeleton'
import { Badge } from '../components/ui/Badge'
import { Table, THead, TR, TH, TD } from '../components/ui/Table'
import { useToast } from '../components/ui/toast-context'
import { api } from '../lib/api'
import { formatDate } from '../lib/format'
import type { ApiKey, ApiKeyCreated } from '../types/api'

export function ApiKeysPage() {
  const { toast } = useToast()
  const [keys, setKeys] = useState<ApiKey[] | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [createOpen, setCreateOpen] = useState(false)
  const [newKeyName, setNewKeyName] = useState('')
  const [creating, setCreating] = useState(false)
  const [createdKey, setCreatedKey] = useState<ApiKeyCreated | null>(null)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    api
      .listApiKeys()
      .then((data) => {
        if (cancelled) return
        setKeys(data)
        setError(null)
      })
      .catch((e) => {
        if (cancelled) return
        setError(e instanceof Error ? e.message : 'Could not load API keys')
        setKeys([])
      })
      .finally(() => setLoading(false))
    return () => { cancelled = true }
  }, [])

  const createKey = async () => {
    const name = newKeyName.trim()
    if (!name) return
    setCreating(true)
    try {
      const created = await api.createApiKey({ name })
      setCreatedKey(created)
      setKeys((prev) => (prev ? [mapCreatedToKey(created), ...prev] : [mapCreatedToKey(created)]))
      setNewKeyName('')
      toast('API key created. Copy it now — it will not be shown again.', 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Could not create API key', 'error')
    } finally {
      setCreating(false)
    }
  }

  const deleteKey = async (id: number) => {
    try {
      await api.deleteApiKey(id)
      setKeys((prev) => prev?.filter((k) => k.id !== id) ?? null)
      toast('API key revoked', 'success')
    } catch (e) {
      toast(e instanceof Error ? e.message : 'Could not revoke API key', 'error')
    }
  }

  return (
    <div className="mx-auto max-w-5xl space-y-6">
      <PageHeader
        eyebrow="Access"
        title="API keys"
        description="Generate and revoke keys for programmatic access to AstraStore. Keys are shown only once after creation."
        actions={
          <Button onClick={() => setCreateOpen(true)} disabled={loading || !!error}>
            <Plus className="size-4" /> New key
          </Button>
        }
      />

      {error && (
        <div className="rounded-xl border border-danger-border bg-danger-subtle px-4 py-3 text-sm text-danger-text">
          {error}
        </div>
      )}

      <Card className="overflow-hidden p-0">
        <CardHeader className="border-b border-line px-5 py-4">
          <div>
            <CardTitle>Active keys</CardTitle>
            <CardDescription>Keys with access to the AstraStore API.</CardDescription>
          </div>
        </CardHeader>

        {loading ? (
          <div className="p-5">
            <SkeletonRows rows={4} />
          </div>
        ) : !keys || keys.length === 0 ? (
          <EmptyState
            icon={<KeyRound className="size-5" />}
            title="No API keys"
            description="Create a key to authenticate SDKs and scripts."
            action={
              !error && (
                <Button size="sm" onClick={() => setCreateOpen(true)}>
                  <Plus className="size-4" /> Create first key
                </Button>
              )
            }
          />
        ) : (
          // Wide on a phone: scroll the table inside its own box rather than
          // letting it push the whole page sideways.
          <div className="scroll-x">
          <Table>
            <THead>
              <TR>
                <TH>Name</TH>
                <TH>Key prefix</TH>
                <TH>Created</TH>
                <TH>Status</TH>
                <TH className="w-px text-right">Actions</TH>
              </TR>
            </THead>
            <tbody>
              {keys.map((k) => (
                <TR key={k.id}>
                  <TD className="font-medium text-ink">{k.name}</TD>
                  <TD className="tnum font-mono text-ink-2">
                    {k.keyPrefix}••••••••
                  </TD>
                  <TD className="tnum whitespace-nowrap text-ink-2">
                    {formatDate(k.createdAt)}
                    {k.lastUsedAt && (
                      <span className="block text-xs text-ink-4">Last used {formatDate(k.lastUsedAt)}</span>
                    )}
                  </TD>
                  <TD>
                    <Badge tone="success" dot>Active</Badge>
                  </TD>
                  <TD>
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label="Copy key prefix"
                        onClick={() => {
                          navigator.clipboard.writeText(k.keyPrefix)
                          toast('Key prefix copied', 'info')
                        }}
                        className="size-8"
                      >
                        <Copy className="size-4" />
                      </Button>
                      <Button
                        type="button"
                        variant="ghost"
                        size="icon"
                        aria-label={`Revoke key ${k.name}`}
                        onClick={() => deleteKey(k.id)}
                        className="size-8 text-ink-3 hover:bg-danger-subtle hover:text-danger-text"
                      >
                        <Trash2 className="size-4" />
                      </Button>
                    </div>
                  </TD>
                </TR>
              ))}
            </tbody>
          </Table>
          </div>
        )}
      </Card>

      <Dialog
        open={createOpen}
        onClose={() => {
          setCreateOpen(false)
          setNewKeyName('')
        }}
        title="Create API key"
        description="Give the key a descriptive name so you know where it's used."
        footer={
          <>
            <Button
              variant="secondary"
              onClick={() => {
                setCreateOpen(false)
                setNewKeyName('')
              }}
              disabled={creating}
            >
              Cancel
            </Button>
            <Button onClick={createKey} disabled={!newKeyName.trim()} loading={creating}>
              Create key
            </Button>
          </>
        }
      >
        <Input
          label="Key name"
          value={newKeyName}
          onChange={(e) => setNewKeyName(e.target.value)}
          placeholder="production-uploads"
          autoFocus
          onKeyDown={(e) => {
            if (e.key === 'Enter') createKey()
          }}
        />
      </Dialog>

      {createdKey && (
        <Dialog
          open
          onClose={() => setCreatedKey(null)}
          title="Copy your API key"
          description="This is the only time the full key is shown. Store it somewhere safe."
          footer={
            <>
              <Button variant="secondary" onClick={() => setCreatedKey(null)}>
                Done
              </Button>
              <Button
                onClick={() => {
                  navigator.clipboard.writeText(createdKey.key)
                  toast('API key copied', 'success')
                  setCreatedKey(null)
                }}
              >
                <Copy className="size-4" /> Copy to clipboard
              </Button>
            </>
          }
        >
          <div className="rounded-lg border border-accent-border bg-accent-subtle p-4">
            <code className="tnum block break-all font-mono text-sm text-ink">
              {createdKey.key}
            </code>
          </div>
        </Dialog>
      )}
    </div>
  )
}

function mapCreatedToKey(created: ApiKeyCreated): ApiKey {
  return {
    id: created.id,
    name: created.name,
    keyPrefix: created.keyPrefix,
    createdAt: created.createdAt,
    expiresAt: created.expiresAt,
    lastUsedAt: null,
  }
}
