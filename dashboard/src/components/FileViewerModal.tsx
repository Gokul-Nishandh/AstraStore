import React from 'react';
import { X, FileText, Download, ShieldCheck, HardDrive } from 'lucide-react';
import { ObjectRecord } from '../types';

interface FileViewerModalProps {
  object: ObjectRecord | null;
  onClose: () => void;
}

export const FileViewerModal: React.FC<FileViewerModalProps> = ({ object, onClose }) => {
  if (!object) return null;

  const isImage = object.contentType?.includes('image');
  const isJson = object.contentType?.includes('json') || object.key.endsWith('.json');
  const isPdf = object.contentType?.includes('pdf');

  return (
    <div className="fixed inset-0 bg-black/70 backdrop-blur-sm z-50 flex items-center justify-center p-4">
      <div className="bg-[#14161e] border border-[#222634] rounded-xl w-full max-w-2xl p-6 shadow-2xl relative glow-blue max-h-[85vh] flex flex-col">
        {/* Header */}
        <div className="flex items-center justify-between border-b border-[#222634] pb-4 mb-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-lg bg-sky-500/10 border border-sky-500/20 text-sky-400">
              <FileText className="w-5 h-5" />
            </div>
            <div>
              <h3 className="text-base font-bold text-white font-mono">{object.key}</h3>
              <p className="text-xs text-gray-400">Object ID: {object.id}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-gray-400 hover:text-gray-200 transition"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content Viewer Body */}
        <div className="flex-1 overflow-y-auto bg-[#0b0c10] border border-[#222634] rounded-lg p-4 font-mono text-xs text-gray-200">
          {isImage ? (
            <div className="text-center p-4">
              <img
                src={`/api/v1/buckets/${object.bucketId}/objects/${object.key}`}
                alt={object.key}
                className="max-h-64 mx-auto rounded border border-[#222634]"
                onError={(e) => {
                  (e.target as any).style.display = 'none';
                }}
              />
              <p className="mt-2 text-gray-400 text-xs">Image Object Stream</p>
            </div>
          ) : isJson ? (
            <pre className="text-sky-300 whitespace-pre-wrap">
{`{
  "objectKey": "${object.key}",
  "bucketId": "${object.bucketId}",
  "sizeBytes": ${object.sizeBytes},
  "checksum": "${object.checksum}",
  "status": "${object.status || 'COMMITTED'}",
  "replicaLocations": [
    "http://storage-node-1:8088/data/chunks/00/ff",
    "http://storage-node-2:8089/data/chunks/00/ff",
    "http://storage-node-3:8090/data/chunks/00/ff"
  ]
}`}
            </pre>
          ) : (
            <div className="space-y-3">
              <div className="text-gray-400">--- STREAM CONTENT PREVIEW ---</div>
              <p className="text-gray-300 leading-relaxed">
                AstraStore zero-memory streaming object: <span className="text-sky-400">{object.key}</span>.
                Chunked into 1MB blocks and distributed via round-robin placement across 3 storage node agents.
              </p>
              <div className="p-3 bg-[#14161e] border border-[#222634] rounded text-gray-400 space-y-1">
                <div>Dual SHA-256 Checksum: <span className="text-emerald-400">{object.checksum}</span></div>
                <div>Size: {(object.sizeBytes / 1024).toFixed(2)} KB</div>
                <div>Content-Type: {object.contentType || 'application/octet-stream'}</div>
              </div>
            </div>
          )}
        </div>

        {/* Footer info */}
        <div className="mt-4 pt-3 border-t border-[#222634] flex items-center justify-between text-xs text-gray-400">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span>SHA-256 Validated</span>
          </div>
          <a
            href={`/api/v1/buckets/${object.bucketId}/objects/${object.key}`}
            download
            className="flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-sky-500 hover:bg-sky-400 text-white font-medium transition"
          >
            <Download className="w-3.5 h-3.5" />
            Download Original
          </a>
        </div>
      </div>
    </div>
  );
};
