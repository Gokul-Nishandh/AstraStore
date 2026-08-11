import React from 'react';
import { LayoutDashboard, HardDrive, Server, Key, Activity } from 'lucide-react';

export type DashboardTab = 'metrics' | 'buckets' | 'nodes' | 'apikeys';

interface SidebarProps {
  activeTab: DashboardTab;
  setActiveTab: (tab: DashboardTab) => void;
}

export const Sidebar: React.FC<SidebarProps> = ({ activeTab, setActiveTab }) => {
  const menuItems = [
    { id: 'metrics' as DashboardTab, label: 'System Metrics', icon: Activity },
    { id: 'buckets' as DashboardTab, label: 'Buckets & Storage', icon: HardDrive },
    { id: 'nodes' as DashboardTab, label: 'Cluster Topology', icon: Server },
    { id: 'apikeys' as DashboardTab, label: 'API Keys & Tokens', icon: Key },
  ];

  return (
    <aside className="w-64 bg-[#14161e] border-r border-[#222634] p-4 flex flex-col justify-between hidden md:flex min-h-[calc(100vh-4rem)]">
      <div className="space-y-1">
        <div className="px-3 py-2 text-xs font-semibold text-gray-500 uppercase tracking-wider">
          Astra Engine Control
        </div>
        {menuItems.map((item) => {
          const Icon = item.icon;
          const isActive = activeTab === item.id;
          return (
            <button
              key={item.id}
              onClick={() => setActiveTab(item.id)}
              className={`w-full flex items-center gap-3 px-3 py-2.5 rounded-lg text-sm font-medium transition ${
                isActive
                  ? 'bg-sky-500/10 text-sky-400 border border-sky-500/30'
                  : 'text-gray-400 hover:text-gray-200 hover:bg-[#1a1e2b]'
              }`}
            >
              <Icon className={`w-4 h-4 ${isActive ? 'text-sky-400' : 'text-gray-400'}`} />
              {item.label}
            </button>
          );
        })}
      </div>

      <div className="p-3 rounded-lg bg-[#0b0c10] border border-[#222634] text-xs text-gray-400 space-y-1">
        <div className="flex justify-between font-mono">
          <span>Cluster ID</span>
          <span className="text-gray-200">astrastore-us-east</span>
        </div>
        <div className="flex justify-between font-mono">
          <span>Replication</span>
          <span className="text-emerald-400">3x P2P Push</span>
        </div>
      </div>
    </aside>
  );
};
