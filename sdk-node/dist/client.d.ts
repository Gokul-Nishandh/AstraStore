import { Readable, Writable } from 'stream';
import { AstraClientOptions, Bucket, ObjectRecord, UploadResult, AuthResponse, ApiKey } from './types.js';
export declare class AstraClient {
    private baseUrl;
    private apiKey?;
    private email?;
    private password?;
    private timeout;
    private accessToken?;
    private refreshToken?;
    constructor(options?: AstraClientOptions);
    private getValidToken;
    private request;
    private handleError;
    login(email: string, password: string): Promise<AuthResponse>;
    refresh(): Promise<AuthResponse>;
    createBucket(name: string): Promise<Bucket>;
    getBucket(bucketId: string): Promise<Bucket>;
    listBuckets(): Promise<Bucket[]>;
    deleteBucket(bucketId: string): Promise<void>;
    uploadObject(bucketId: string, key: string, content: Buffer | string | Readable, contentType?: string): Promise<UploadResult>;
    downloadObjectBuffer(bucketId: string, key: string): Promise<Buffer>;
    downloadObject(bucketId: string, key: string, target: string | Writable): Promise<void>;
    getObjectMetadata(objectId: string): Promise<ObjectRecord>;
    deleteObject(objectId: string): Promise<void>;
    createApiKey(name: string): Promise<ApiKey>;
    listApiKeys(): Promise<ApiKey[]>;
    revokeApiKey(keyId: number): Promise<void>;
}
