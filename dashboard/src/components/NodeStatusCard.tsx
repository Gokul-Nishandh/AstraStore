import React, { useEffect, useState } from 'react';
import { Server, Activity, HardDrive, CheckCircle2, RefreshCw, Cpu } from 'lucide-react';
import { apiService } from '../services/api';
import { StorageNodeHealth } from '../types';

export const NodeStatusCard: React.FC = () => {
  const [nodes, setNodes] = useState<StorageNodeHealth[]>([]);
  const [loading, setLoading] = useState(true);

  const loadNodes = async () => {
    setLoading(true);
    const data = await apiService.getNodesHealth();
    setNodes(data);
    setLoading(false);
  };

  useEffect(() => {
    loadNodes();
  }, []);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-wide">Storage Node Topology</h1>
          <p className="text-xs text-gray-400">P2P Streaming Agents · Kafka Coordinated Replication · Self-Healing Loop @60s</p>
        </div>
        <button
          onClick={loadNodes}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-[#14161e] border border-[#222634] text-xs text-gray-300 hover:text-white transition"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Scan Topology
        </button>
      </div>

      {/* Node Cards Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {nodes.map((node) => (
          <div key={node.id} className="grafana-card p-5 space-y-4 glow-blue">
            <div className="flex items-center justify-between border-b border-[#222634] pb-3">
              <div className="flex items-center gap-3">
                <div className="p-2.5 rounded-lg bg-sky-500/10 border border-sky-500/20 text-sky-400">
                  <Server className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="text-base font-bold text-white">{node.name}</h3>
                  <p className="text-xs font-mono text-gray-400">Port :{node.port}</p>
                </div>
              </div>
              <span className="inline-flex items-center gap-1.5 px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs font-medium">
                <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse-subtle"></span>
                {node.status}
              </span>
            </div>

            <div className="space-y-3 text-xs">
              <div className="flex justify-between font-mono text-gray-300">
                <span>Active Chunks Staged</span>
                <span className="text-sky-400 font-bold">{node.activeChunks}</span>
              </div>

              <div className="space-y-1">
                <div className="flex justify-between font-mono text-gray-400">
                  <span>Disk Usage</span>
                  <span>{node.diskUsagePercent}%</span>
                </div>
                <div className="w-full bg-[#0b0c10] h-2 rounded-full overflow-hidden border border-[#222634]">
                  <div
                    className="bg-gradient-to-r from-sky-500 to-indigo-500 h-full"
                    style={{ width: `${node.diskUsagePercent}%` }}
                  ></div>
                </div>
              </div>

              <div className="p-2.5 bg-[#0b0c10] border border-[#222634] rounded font-mono text-[11px] text-gray-400 truncate">
                Disk Fan-Out Path: <span className="text-gray-200">{node.path}</span>
              </div>
            </div>

            <div className="pt-2 border-t border-[#222634] flex items-center justify-between text-[11px] text-gray-500 font-mono">
              <span>Heartbeat: /api/v1/health</span>
              <span className="text-emerald-400">200 OK</span>
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
