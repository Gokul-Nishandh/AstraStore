import React, { useState, useEffect } from 'react';
import { HardDrive, Plus, Trash2, Upload, Download, Eye, FileText, FileCode, FileImage, FileVideo, FileAudio, FolderOpen } from 'lucide-react';
import { apiService } from '../services/api';
import { Bucket, ObjectRecord } from '../types';

interface BucketExplorerProps {
  onOpenUpload: (bucketId: string) => void;
  onOpenViewer: (object: ObjectRecord) => void;
}

export const BucketExplorer: React.FC<BucketExplorerProps> = ({ onOpenUpload, onOpenViewer }) => {
  const [buckets, setBuckets] = useState<Bucket[]>([]);
  const [selectedBucket, setSelectedBucket] = useState<Bucket | null>(null);
  const [objects, setObjects] = useState<ObjectRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [newBucketName, setNewBucketName] = useState('');
  const [creating, setCreating] = useState(false);

  const loadBuckets = async () => {
    setLoading(true);
    const data = await apiService.listBuckets();
    setBuckets(data);
    if (data.length > 0 && !selectedBucket) {
      setSelectedBucket(data[0]);
    }
    setLoading(false);
  };

  const loadObjects = async (bucketId: string) => {
    const data = await apiService.listObjectsInBucket(bucketId);
    setObjects(data);
  };

  useEffect(() => {
    loadBuckets();
  }, []);

  useEffect(() => {
    if (selectedBucket) {
      loadObjects(selectedBucket.id);
    }
  }, [selectedBucket]);

  const handleCreateBucket = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newBucketName.trim()) return;
    setCreating(true);
    const bucket = await apiService.createBucket(newBucketName.trim());
    setNewBucketName('');
    setCreating(false);
    await loadBuckets();
    setSelectedBucket(bucket);
  };

  const handleDeleteBucket = async (id: string) => {
    if (!confirm('Are you sure you want to delete this bucket?')) return;
    await apiService.deleteBucket(id);
    if (selectedBucket?.id === id) {
      setSelectedBucket(null);
    }
    await loadBuckets();
  };

  const getFileIcon = (contentType?: string) => {
    if (!contentType) return FileText;
    if (contentType.includes('image')) return FileImage;
    if (contentType.includes('video')) return FileVideo;
    if (contentType.includes('audio')) return FileAudio;
    if (contentType.includes('json') || contentType.includes('javascript') || contentType.includes('code')) return FileCode;
    return FileText;
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-white tracking-wide">Buckets & Object Storage</h1>
          <p className="text-xs text-gray-400">Zero-Memory Chunker Uploads & Direct Reassembly Downloads</p>
        </div>
      </div>

      {/* Main Split Grid */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
        {/* Left Panel: Bucket List */}
        <div className="grafana-card p-4 space-y-4">
          <h3 className="text-sm font-semibold text-white flex items-center gap-2 border-b border-[#222634] pb-3">
            <HardDrive className="w-4 h-4 text-sky-400" />
            Buckets ({buckets.length})
          </h3>

          {/* Create Bucket Form */}
          <form onSubmit={handleCreateBucket} className="flex gap-2">
            <input
              type="text"
              placeholder="new-bucket-name"
              value={newBucketName}
              onChange={(e) => setNewBucketName(e.target.value)}
              className="flex-1 bg-[#0b0c10] border border-[#222634] rounded-lg px-3 py-1.5 text-xs text-gray-100 focus:outline-none focus:border-sky-500"
            />
            <button
              type="submit"
              disabled={creating}
              className="px-3 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-white text-xs font-medium transition flex items-center gap-1"
            >
              <Plus className="w-3.5 h-3.5" />
              Create
            </button>
          </form>

          {/* Bucket Items */}
          <div className="space-y-1 max-h-96 overflow-y-auto">
            {buckets.map((bucket) => {
              const isSelected = selectedBucket?.id === bucket.id;
              return (
                <div
                  key={bucket.id}
                  onClick={() => setSelectedBucket(bucket)}
                  className={`p-3 rounded-lg flex items-center justify-between cursor-pointer transition text-xs ${
                    isSelected
                      ? 'bg-sky-500/10 border border-sky-500/30 text-white'
                      : 'bg-[#0b0c10] border border-transparent text-gray-300 hover:bg-[#1a1e2b]'
                  }`}
                >
                  <div className="flex items-center gap-2 truncate">
                    <FolderOpen className={`w-4 h-4 ${isSelected ? 'text-sky-400' : 'text-gray-500'}`} />
                    <span className="font-mono font-medium truncate">{bucket.name}</span>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDeleteBucket(bucket.id);
                    }}
                    className="text-gray-500 hover:text-rose-400 transition"
                    title="Delete Bucket"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              );
            })}
          </div>
        </div>

        {/* Right Panel: Objects in Selected Bucket */}
        <div className="md:col-span-2 grafana-card p-5 space-y-4">
          {selectedBucket ? (
            <>
              <div className="flex items-center justify-between border-b border-[#222634] pb-3">
                <div>
                  <div className="flex items-center gap-2">
                    <h3 className="text-base font-bold text-white font-mono">{selectedBucket.name}</h3>
                    <span className="text-[10px] px-2 py-0.5 rounded bg-[#0b0c10] border border-[#222634] text-gray-400 font-mono">
                      ID: {selectedBucket.id}
                    </span>
                  </div>
                  <p className="text-xs text-gray-400">{objects.length} objects stored</p>
                </div>

                <button
                  onClick={() => onOpenUpload(selectedBucket.id)}
                  className="flex items-center gap-2 px-4 py-2 rounded-lg bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 text-white font-medium text-xs shadow-lg shadow-sky-500/20 transition"
                >
                  <Upload className="w-3.5 h-3.5" />
                  Upload File
                </button>
              </div>

              {/* Objects Table */}
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs text-gray-300">
                  <thead className="text-gray-500 border-b border-[#222634] uppercase font-mono text-[10px]">
                    <tr>
                      <th className="pb-2">Object Key</th>
                      <th className="pb-2">Size</th>
                      <th className="pb-2">Replication</th>
                      <th className="pb-2 text-right">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-[#222634]/50">
                    {objects.map((obj) => {
                      const Icon = getFileIcon(obj.contentType);
                      return (
                        <tr key={obj.id} className="hover:bg-[#1a1e2b] transition group">
                          <td className="py-3 pr-2">
                            <div className="flex items-center gap-2">
                              <Icon className="w-4 h-4 text-sky-400 shrink-0" />
                              <span className="font-mono text-gray-200 font-medium">{obj.key}</span>
                            </div>
                          </td>
                          <td className="py-3 font-mono text-gray-400">{formatBytes(obj.sizeBytes)}</td>
                          <td className="py-3">
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-[10px] font-mono">
                              3/3 Synced
                            </span>
                          </td>
                          <td className="py-3 text-right">
                            <div className="flex items-center justify-end gap-2">
                              <button
                                onClick={() => onOpenViewer(obj)}
                                className="p-1.5 rounded bg-[#0b0c10] border border-[#222634] text-gray-300 hover:text-sky-400 hover:border-sky-500/40 transition"
                                title="Preview File"
                              >
                                <Eye className="w-3.5 h-3.5" />
                              </button>
                              <button
                                onClick={() => apiService.downloadObject(obj.bucketId, obj.key, obj.key.split('/').pop() || 'file')}
                                className="p-1.5 rounded bg-[#0b0c10] border border-[#222634] text-gray-300 hover:text-emerald-400 hover:border-emerald-500/40 transition"
                                title="Download File"
                              >
                                <Download className="w-3.5 h-3.5" />
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </>
          ) : (
            <div className="p-12 text-center text-gray-500 text-sm">
              Select or create a bucket to view its object storage entries.
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
