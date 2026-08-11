import React from 'react';
import { Database, ShieldCheck, User, LogOut, Key } from 'lucide-react';
import { UserSession } from '../types';

interface NavbarProps {
  userSession: UserSession | null;
  onOpenLogin: () => void;
  onLogout: () => void;
}

export const Navbar: React.FC<NavbarProps> = ({ userSession, onOpenLogin, onLogout }) => {
  return (
    <header className="h-16 bg-[#14161e] border-b border-[#222634] px-6 flex items-center justify-between sticky top-0 z-30">
      {/* Brand logo */}
      <div className="flex items-center gap-3">
        <div className="w-9 h-9 rounded-lg bg-gradient-to-tr from-sky-500 to-indigo-600 flex items-center justify-center shadow-lg shadow-sky-500/20">
          <Database className="w-5 h-5 text-white" />
        </div>
        <div>
          <div className="flex items-center gap-2">
            <span className="font-bold text-lg text-white tracking-wide">AstraStore</span>
            <span className="text-[10px] uppercase font-mono px-2 py-0.5 rounded bg-sky-500/10 text-sky-400 border border-sky-500/20">
              v1.0 Distributed
            </span>
          </div>
          <p className="text-xs text-gray-400">Zero-Memory Chunker & P2P Engine</p>
        </div>
      </div>

      {/* Gateway Cluster Status Badge */}
      <div className="hidden md:flex items-center gap-6">
        <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-emerald-500/10 border border-emerald-500/20">
          <span className="w-2 h-2 rounded-full bg-emerald-400 animate-pulse-subtle"></span>
          <span className="text-xs font-medium text-emerald-400">Gateway :8080 Connected</span>
        </div>

        <div className="flex items-center gap-2 px-3 py-1.5 rounded-full bg-indigo-500/10 border border-indigo-500/20">
          <ShieldCheck className="w-3.5 h-3.5 text-indigo-400" />
          <span className="text-xs font-medium text-indigo-300">Auth :8081 Active</span>
        </div>
      </div>

      {/* User Actions */}
      <div className="flex items-center gap-3">
        {userSession ? (
          <div className="flex items-center gap-3">
            <div className="flex items-center gap-2 px-3 py-1.5 rounded-lg bg-[#1a1e2b] border border-[#2d3345]">
              <User className="w-4 h-4 text-sky-400" />
              <span className="text-sm font-medium text-gray-200">{userSession.username || userSession.email}</span>
            </div>
            <button
              onClick={onLogout}
              className="p-2 rounded-lg bg-rose-500/10 border border-rose-500/20 text-rose-400 hover:bg-rose-500/20 transition"
              title="Logout"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <button
            onClick={onOpenLogin}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 text-white font-medium text-sm transition shadow-lg shadow-sky-500/25"
          >
            <Key className="w-4 h-4" />
            Sign In / API Auth
          </button>
        )}
      </div>
    </header>
  );
};
