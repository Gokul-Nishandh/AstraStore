import React, { useState } from 'react';
import { X, UploadCloud, CheckCircle2, File } from 'lucide-react';
import { apiService } from '../services/api';

interface UploadModalProps {
  isOpen: boolean;
  bucketId: string | null;
  onClose: () => void;
  onSuccess: () => void;
}

export const UploadModal: React.FC<UploadModalProps> = ({ isOpen, bucketId, onClose, onSuccess }) => {
  const [file, setFile] = useState<File | null>(null);
  const [customKey, setCustomKey] = useState('');
  const [uploading, setUploading] = useState(false);
  const [progress, setProgress] = useState(0);
  const [completedResult, setCompletedResult] = useState<any>(null);

  if (!isOpen || !bucketId) return null;

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files[0]) {
      const selected = e.target.files[0];
      setFile(selected);
      setCustomKey(selected.name);
    }
  };

  const handleUpload = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!file) return;

    setUploading(true);
    setProgress(30);

    try {
      const result = await apiService.uploadObject(bucketId, customKey || file.name, file);
      setProgress(100);
      setCompletedResult(result);
      setTimeout(() => {
        onSuccess();
      }, 1200);
    } catch (err) {
      alert('Upload failed');
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-[#14161e] border border-[#222634] rounded-xl w-full max-w-lg p-6 shadow-2xl relative glow-blue">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-gray-400 hover:text-gray-200 transition"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center gap-3 mb-6">
          <div className="p-3 rounded-lg bg-sky-500/10 border border-sky-500/20 text-sky-400">
            <UploadCloud className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-xl font-bold text-white">Upload Object to AstraStore</h2>
            <p className="text-xs text-gray-400">Zero-Memory Chunker (O(1) Memory Footprint · 8KB Buffer)</p>
          </div>
        </div>

        {completedResult ? (
          <div className="p-6 text-center space-y-3 bg-[#0b0c10] border border-emerald-500/30 rounded-lg">
            <CheckCircle2 className="w-12 h-12 text-emerald-400 mx-auto animate-bounce" />
            <h3 className="text-base font-bold text-white">Upload Successful!</h3>
            <div className="text-xs font-mono text-gray-400 space-y-1">
              <div>Object ID: {completedResult.objectId}</div>
              <div>Checksum: {completedResult.checksum?.substring(0, 24)}...</div>
              <div className="text-emerald-400">Chunks Created: {completedResult.chunkCount}</div>
            </div>
          </div>
        ) : (
          <form onSubmit={handleUpload} className="space-y-4">
            {/* File Drop Zone */}
            <div className="border-2 border-dashed border-[#222634] hover:border-sky-500/50 rounded-xl p-6 text-center bg-[#0b0c10] transition relative cursor-pointer">
              <input
                type="file"
                onChange={handleFileChange}
                className="absolute inset-0 opacity-0 cursor-pointer"
              />
              {file ? (
                <div className="flex items-center justify-center gap-2 text-sky-400 font-mono text-sm">
                  <File className="w-5 h-5" />
                  <span>{file.name} ({Math.round(file.size / 1024)} KB)</span>
                </div>
              ) : (
                <div className="space-y-2">
                  <UploadCloud className="w-10 h-10 text-gray-500 mx-auto" />
                  <p className="text-sm font-medium text-gray-300">Click or drag file to upload</p>
                  <p className="text-xs text-gray-500">Supports files of any size (Streaming via Gateway :8080)</p>
                </div>
              )}
            </div>

            {file && (
              <div>
                <label className="block text-xs font-medium text-gray-300 mb-1">Target Key Name</label>
                <input
                  type="text"
                  value={customKey}
                  onChange={(e) => setCustomKey(e.target.value)}
                  className="w-full bg-[#0b0c10] border border-[#222634] rounded-lg px-3 py-2 text-sm text-gray-100 font-mono focus:outline-none focus:border-sky-500"
                  required
                />
              </div>
            )}

            {uploading && (
              <div className="space-y-2">
                <div className="flex justify-between text-xs font-mono text-sky-400">
                  <span>Streaming chunks to nodes...</span>
                  <span>{progress}%</span>
                </div>
                <div className="w-full bg-[#0b0c10] h-2 rounded-full overflow-hidden border border-[#222634]">
                  <div
                    className="bg-sky-400 h-full transition-all duration-300"
                    style={{ width: `${progress}%` }}
                  ></div>
                </div>
              </div>
            )}

            <button
              type="submit"
              disabled={!file || uploading}
              className="w-full py-2.5 rounded-lg bg-gradient-to-r from-sky-500 to-blue-600 hover:from-sky-400 hover:to-blue-500 disabled:opacity-50 text-white font-medium text-sm transition shadow-lg shadow-sky-500/25"
            >
              {uploading ? 'Streaming...' : 'Start Zero-Memory Upload'}
            </button>
          </form>
        )}
      </div>
    </div>
  );
};
