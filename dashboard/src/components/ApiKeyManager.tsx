import React, { useEffect, useState } from 'react';
import { Key, Plus, Trash2, Copy, CheckCircle2, ShieldAlert } from 'lucide-react';
import { apiService } from '../services/api';
import { ApiKey } from '../types';

export const ApiKeyManager: React.FC = () => {
  const [keys, setKeys] = useState<ApiKey[]>([]);
  const [keyName, setKeyName] = useState('');
  const [createdKey, setCreatedKey] = useState<ApiKey | null>(null);
  const [loading, setLoading] = useState(false);
  const [copied, setCopied] = useState(false);

  const loadKeys = async () => {
    const data = await apiService.listApiKeys();
    setKeys(data);
  };

  useEffect(() => {
    loadKeys();
  }, []);

  const handleCreate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!keyName.trim()) return;
    setLoading(true);
    const newKey = await apiService.createApiKey(keyName.trim());
    setKeyName('');
    setCreatedKey(newKey);
    setLoading(false);
    await loadKeys();
  };

  const handleRevoke = async (id: number) => {
    if (!confirm('Revoke this API Key permanently?')) return;
    await apiService.revokeApiKey(id);
    await loadKeys();
  };

  const handleCopy = (text?: string) => {
    if (!text) return;
    navigator.clipboard.writeText(text);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <h1 className="text-2xl font-bold text-white tracking-wide">API Keys & Tokens</h1>
        <p className="text-xs text-gray-400">Generate long-lived API keys for Java, Python, and Node.js SDK Clients</p>
      </div>

      {/* Created Key Banner */}
      {createdKey && createdKey.key && (
        <div className="grafana-card p-5 border-emerald-500/40 bg-emerald-500/10 space-y-3 glow-green">
          <div className="flex items-center gap-2 text-emerald-400 font-bold text-sm">
            <CheckCircle2 className="w-5 h-5" />
            API Key Created Successfully! (Copy it now, it won't be shown again)
          </div>
          <div className="flex items-center gap-2 bg-[#0b0c10] border border-emerald-500/30 rounded-lg p-3">
            <code className="flex-1 text-emerald-300 font-mono text-sm break-all">{createdKey.key}</code>
            <button
              onClick={() => handleCopy(createdKey.key)}
              className="px-3 py-1.5 rounded bg-emerald-500 text-white font-medium text-xs flex items-center gap-1 hover:bg-emerald-400 transition"
            >
              {copied ? <CheckCircle2 className="w-3.5 h-3.5" /> : <Copy className="w-3.5 h-3.5" />}
              {copied ? 'Copied!' : 'Copy Key'}
            </button>
          </div>
        </div>
      )}

      {/* Main Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Create Form */}
        <div className="grafana-card p-5 space-y-4">
          <h3 className="text-sm font-semibold text-white flex items-center gap-2 border-b border-[#222634] pb-3">
            <Key className="w-4 h-4 text-sky-400" />
            Create New API Key
          </h3>

          <form onSubmit={handleCreate} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-gray-300 mb-1">Key Description / Client Name</label>
              <input
                type="text"
                placeholder="e.g. Java Microservice Backend"
                value={keyName}
                onChange={(e) => setKeyName(e.target.value)}
                className="w-full bg-[#0b0c10] border border-[#222634] rounded-lg px-3 py-2 text-sm text-gray-100 focus:outline-none focus:border-sky-500"
                required
              />
            </div>

            <button
              type="submit"
              disabled={loading}
              className="w-full py-2 rounded-lg bg-sky-500 hover:bg-sky-400 text-white font-medium text-xs transition flex items-center justify-center gap-1.5"
            >
              <Plus className="w-4 h-4" />
              Generate API Key
            </button>
          </form>
        </div>

        {/* List of Keys */}
        <div className="lg:col-span-2 grafana-card p-5 space-y-4">
          <h3 className="text-sm font-semibold text-white border-b border-[#222634] pb-3">
            Active API Keys ({keys.length})
          </h3>

          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs text-gray-300">
              <thead className="text-gray-500 border-b border-[#222634] uppercase font-mono text-[10px]">
                <tr>
                  <th className="pb-2">Name</th>
                  <th className="pb-2">Key Prefix</th>
                  <th className="pb-2">Created At</th>
                  <th className="pb-2 text-right">Revoke</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-[#222634]/50">
                {keys.map((key) => (
                  <tr key={key.id} className="hover:bg-[#1a1e2b] transition">
                    <td className="py-3 font-medium text-white">{key.name}</td>
                    <td className="py-3 font-mono text-sky-400">{key.keyPrefix || 'ast_live_xxxx'}...</td>
                    <td className="py-3 font-mono text-gray-400">{key.createdAt?.split('T')[0] || '2026-08-11'}</td>
                    <td className="py-3 text-right">
                      <button
                        onClick={() => handleRevoke(key.id)}
                        className="p-1.5 rounded bg-rose-500/10 border border-rose-500/20 text-rose-400 hover:bg-rose-500/20 transition"
                        title="Revoke Key"
                      >
                        <Trash2 className="w-3.5 h-3.5" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
};
