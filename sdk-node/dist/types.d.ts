export interface AstraClientOptions {
    baseUrl?: string;
    apiKey?: string;
    email?: string;
    password?: string;
    timeout?: number;
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
export interface AuthResponse {
    token: string;
    type: string;
    refreshToken?: string;
    userId?: string;
    username?: string;
    email?: string;
    roles?: string[];
}
export interface ApiKey {
    id: number;
    name: string;
    key?: string;
    keyPrefix?: string;
    expiresAt?: string;
    createdAt?: string;
}
