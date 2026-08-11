import React, { useState } from 'react';
import { Navbar } from './components/Navbar';
import { Sidebar, DashboardTab } from './components/Sidebar';
import { MetricsPanel } from './components/MetricsPanel';
import { BucketExplorer } from './components/BucketExplorer';
import { NodeStatusCard } from './components/NodeStatusCard';
import { ApiKeyManager } from './components/ApiKeyManager';
import { LoginModal } from './components/LoginModal';
import { UploadModal } from './components/UploadModal';
import { FileViewerModal } from './components/FileViewerModal';
import { UserSession, ObjectRecord } from './types';

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<DashboardTab>('metrics');
  const [userSession, setUserSession] = useState<UserSession | null>({
    token: 'demo-jwt-token-2026',
    username: 'gokul_nishandh',
    email: 'gokul@astrastore.io',
    roles: ['ADMIN'],
  });

  const [isLoginOpen, setIsLoginOpen] = useState(false);
  const [uploadBucketId, setUploadBucketId] = useState<string | null>(null);
  const [viewingObject, setViewingObject] = useState<ObjectRecord | null>(null);

  return (
    <div className="min-h-screen bg-[#0b0c10] text-gray-100 flex flex-col font-sans">
      <Navbar
        userSession={userSession}
        onOpenLogin={() => setIsLoginOpen(true)}
        onLogout={() => setUserSession(null)}
      />

      <div className="flex-1 flex">
        <Sidebar activeTab={activeTab} setActiveTab={setActiveTab} />

        <main className="flex-1 p-6 overflow-y-auto max-w-7xl mx-auto w-full">
          {activeTab === 'metrics' && <MetricsPanel />}
          {activeTab === 'buckets' && (
            <BucketExplorer
              onOpenUpload={(bucketId) => setUploadBucketId(bucketId)}
              onOpenViewer={(obj) => setViewingObject(obj)}
            />
          )}
          {activeTab === 'nodes' && <NodeStatusCard />}
          {activeTab === 'apikeys' && <ApiKeyManager />}
        </main>
      </div>

      {/* Modals */}
      <LoginModal
        isOpen={isLoginOpen}
        onClose={() => setIsLoginOpen(false)}
        onSuccess={(session) => setUserSession(session)}
      />

      <UploadModal
        isOpen={!!uploadBucketId}
        bucketId={uploadBucketId}
        onClose={() => setUploadBucketId(null)}
        onSuccess={() => setUploadBucketId(null)}
      />

      <FileViewerModal
        object={viewingObject}
        onClose={() => setViewingObject(null)}
      />
    </div>
  );
};

export default App;
