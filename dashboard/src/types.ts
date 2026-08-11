export interface UserSession {
  token: string;
  refreshToken?: string;
  userId?: string;
  username?: string;
  email?: string;
  roles?: string[];
}

export interface Bucket {
  id: string;
  name: string;
  ownerId?: string;
  createdAt?: string;
}

export interface ObjectRecord {
  id: string;
  bucketId: string;
  key: string;
  sizeBytes: number;
  checksum: string;
  contentType?: string;
  status?: string;
  createdAt?: string;
  chunksReplicated?: number;
  chunksTotal?: number;
}

export interface UploadResult {
  objectId: string;
  bucketId: string;
  key: string;
  sizeBytes: number;
  checksum: string;
  chunkCount: number;
  status: string;
}

export interface ApiKey {
  id: number;
  name: string;
  key?: string;
  keyPrefix?: string;
  expiresAt?: string;
  createdAt?: string;
}

export interface StorageNodeHealth {
  id: string;
  name: string;
  port: number;
  status: 'ONLINE' | 'OFFLINE' | 'DEGRADED';
  activeChunks: number;
  diskUsagePercent: number;
  path: string;
}

export interface SystemMetrics {
  cpuUsage: number;
  memoryUsageMb: number;
  uploadThroughputMbps: number;
  downloadThroughputMbps: number;
  totalObjectsCount: number;
  totalStorageUsedBytes: number;
  activeReplicas: number;
  errorRate5xxPercent: number;
}
