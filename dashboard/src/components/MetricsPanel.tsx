import React, { useEffect, useState } from 'react';
import { Cpu, HardDrive, ArrowUpRight, ArrowDownRight, Layers, AlertCircle, RefreshCw } from 'lucide-react';
import { apiService } from '../services/api';
import { SystemMetrics } from '../types';

export const MetricsPanel: React.FC = () => {
  const [metrics, setMetrics] = useState<SystemMetrics | null>(null);
  const [loading, setLoading] = useState(true);

  const fetchMetrics = async () => {
    setLoading(true);
    const data = await apiService.getMetrics();
    setMetrics(data);
    setLoading(false);
  };

  useEffect(() => {
    fetchMetrics();
    const interval = setInterval(fetchMetrics, 10000);
    return () => clearInterval(interval);
  }, []);

  if (loading && !metrics) {
    return (
      <div className="p-8 text-center text-gray-400">Loading live Grafana system metrics...</div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Top Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-wide">System Metrics & Dashboard</h1>
          <p className="text-xs text-gray-400">Prometheus Scrape Interval: @10s · API Gateway Route: :8080</p>
        </div>
        <button
          onClick={fetchMetrics}
          className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-[#14161e] border border-[#222634] text-xs text-gray-300 hover:text-white hover:border-[#31374a] transition"
        >
          <RefreshCw className="w-3.5 h-3.5" />
          Refresh
        </button>
      </div>

      {/* 4 Primary Grafana Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="grafana-card p-4 space-y-2 border-l-4 border-l-sky-500">
          <div className="flex justify-between text-xs text-gray-400">
            <span>JVM CPU USAGE</span>
            <Cpu className="w-4 h-4 text-sky-400" />
          </div>
          <div className="text-2xl font-bold text-white font-mono">{metrics?.cpuUsage.toFixed(1)}%</div>
          <div className="w-full bg-[#0b0c10] h-2 rounded-full overflow-hidden">
            <div
              className="bg-sky-400 h-full transition-all duration-500"
              style={{ width: `${metrics?.cpuUsage}%` }}
            ></div>
          </div>
        </div>

        <div className="grafana-card p-4 space-y-2 border-l-4 border-l-emerald-500">
          <div className="flex justify-between text-xs text-gray-400">
            <span>UPLOAD THROUGHPUT</span>
            <ArrowUpRight className="w-4 h-4 text-emerald-400" />
          </div>
          <div className="text-2xl font-bold text-white font-mono">{metrics?.uploadThroughputMbps} MB/s</div>
          <div className="text-xs text-emerald-400 font-mono">O(1) Fixed 8KB Buffer</div>
        </div>

        <div className="grafana-card p-4 space-y-2 border-l-4 border-l-indigo-500">
          <div className="flex justify-between text-xs text-gray-400">
            <span>DOWNLOAD THROUGHPUT</span>
            <ArrowDownRight className="w-4 h-4 text-indigo-400" />
          </div>
          <div className="text-2xl font-bold text-white font-mono">{metrics?.downloadThroughputMbps} MB/s</div>
          <div className="text-xs text-indigo-400 font-mono">P2P Chunk Reassembly</div>
        </div>

        <div className="grafana-card p-4 space-y-2 border-l-4 border-l-orange-500">
          <div className="flex justify-between text-xs text-gray-400">
            <span>TOTAL OBJECTS STORED</span>
            <Layers className="w-4 h-4 text-orange-400" />
          </div>
          <div className="text-2xl font-bold text-white font-mono">{metrics?.totalObjectsCount}</div>
          <div className="text-xs text-gray-400 font-mono">3x Replication Factor</div>
        </div>
      </div>

      {/* Visual Chart Simulators (Grafana Dark Aesthetic) */}
      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Storage Capacity Gauge Panel */}
        <div className="grafana-card p-5 space-y-4">
          <div className="flex justify-between items-center border-b border-[#222634] pb-3">
            <div className="flex items-center gap-2">
              <HardDrive className="w-4 h-4 text-sky-400" />
              <h3 className="text-sm font-semibold text-white">Cluster Disk Usage & Directory Fan-Out</h3>
            </div>
            <span className="text-xs font-mono text-gray-400">/data/chunks (256-way)</span>
          </div>

          <div className="space-y-3">
            <div className="flex justify-between text-xs text-gray-300 font-mono">
              <span>Total Storage Used</span>
              <span className="text-sky-400">18.45 GB / 50.00 GB</span>
            </div>
            <div className="w-full bg-[#0b0c10] h-4 rounded-full overflow-hidden p-0.5 border border-[#222634]">
              <div
                className="bg-gradient-to-r from-sky-500 via-indigo-500 to-emerald-400 h-full rounded-full transition-all duration-700"
                style={{ width: '36.9%' }}
              ></div>
            </div>

            <div className="grid grid-cols-3 gap-2 pt-2 text-center text-xs">
              <div className="bg-[#0b0c10] p-2 rounded border border-[#222634]">
                <div className="text-gray-400">Primary Chunks</div>
                <div className="text-white font-mono font-semibold">1,420</div>
              </div>
              <div className="bg-[#0b0c10] p-2 rounded border border-[#222634]">
                <div className="text-gray-400">Replicas</div>
                <div className="text-emerald-400 font-mono font-semibold">2,840</div>
              </div>
              <div className="bg-[#0b0c10] p-2 rounded border border-[#222634]">
                <div className="text-gray-400">Self-Healing</div>
                <div className="text-sky-400 font-mono font-semibold">0 Under-replicated</div>
              </div>
            </div>
          </div>
        </div>

        {/* HTTP Health & Latency Panel */}
        <div className="grafana-card p-5 space-y-4">
          <div className="flex justify-between items-center border-b border-[#222634] pb-3">
            <div className="flex items-center gap-2">
              <AlertCircle className="w-4 h-4 text-emerald-400" />
              <h3 className="text-sm font-semibold text-white">HTTP Request Latency & 5xx Error Rate</h3>
            </div>
            <span className="text-xs font-mono text-emerald-400">0.0% 5xx Errors</span>
          </div>

          <div className="space-y-4">
            <div className="flex justify-between items-center text-xs">
              <span className="text-gray-400">API Gateway Response (p99)</span>
              <span className="font-mono text-white">12.4 ms</span>
            </div>
            <div className="w-full bg-[#0b0c10] h-2 rounded-full overflow-hidden">
              <div className="bg-emerald-400 h-full" style={{ width: '15%' }}></div>
            </div>

            <div className="flex justify-between items-center text-xs">
              <span className="text-gray-400">Zero-Memory Chunker (p95)</span>
              <span className="font-mono text-white">4.8 ms</span>
            </div>
            <div className="w-full bg-[#0b0c10] h-2 rounded-full overflow-hidden">
              <div className="bg-sky-400 h-full" style={{ width: '8%' }}></div>
            </div>

            <div className="flex justify-between items-center text-xs">
              <span className="text-gray-400">P2P Replication Kafka Event Propagation</span>
              <span className="font-mono text-white">2.1 ms</span>
            </div>
            <div className="w-full bg-[#0b0c10] h-2 rounded-full overflow-hidden">
              <div className="bg-indigo-400 h-full" style={{ width: '4%' }}></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};
