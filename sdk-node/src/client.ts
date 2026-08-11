import * as http from 'http';
import * as https from 'https';
import * as fs from 'fs';
import { Readable, Writable } from 'stream';
import {
  AstraClientOptions,
  Bucket,
  ObjectRecord,
  UploadResult,
  AuthResponse,
  ApiKey,
} from './types.js';
import {
  AstraError,
  AstraAuthError,
  AstraNotFoundError,
  AstraValidationError,
  AstraServerError,
} from './errors.js';

export class AstraClient {
  private baseUrl: string;
  private apiKey?: string;
  private email?: string;
  private password?: string;
  private timeout: number;
  private accessToken?: string;
  private refreshToken?: string;

  constructor(options: AstraClientOptions = {}) {
    this.baseUrl = (options.baseUrl || 'http://localhost:8080').replace(/\/+$/, '');
    this.apiKey = options.apiKey;
    this.email = options.email;
    this.password = options.password;
    this.timeout = options.timeout || 30000;
    this.accessToken = options.apiKey;

    if (this.email && this.password && !this.apiKey) {
      // Lazy or initial login can be triggered
    }
  }

  private async getValidToken(): Promise<string | undefined> {
    if (this.accessToken) return this.accessToken;
    if (this.refreshToken) {
      try {
        await this.refresh();
        return this.accessToken;
      } catch (err) {
        // Fallback to credentials
      }
    }
    if (this.email && this.password) {
      await this.login(this.email, this.password);
      return this.accessToken;
    }
    return undefined;
  }

  private async request<T = any>(
    method: string,
    path: string,
    body?: Buffer | string | Readable,
    contentType: string = 'application/json'
  ): Promise<{ statusCode: number; headers: http.IncomingHttpHeaders; data: T; buffer: Buffer }> {
    const token = await this.getValidToken();
    const headers: Record<string, string> = {};

    if (contentType) {
      headers['Content-Type'] = contentType;
    }

    if (token) {
      if (this.apiKey) {
        headers['X-API-Key'] = token;
        headers['Authorization'] = `Bearer ${token}`;
      } else {
        headers['Authorization'] = `Bearer ${token}`;
      }
    }

    const targetUrl = new URL(`${this.baseUrl}${path}`);
    const isHttps = targetUrl.protocol === 'https:';
    const requestModule = isHttps ? https : http;

    return new Promise((resolve, reject) => {
      const reqOptions: http.RequestOptions = {
        hostname: targetUrl.hostname,
        port: targetUrl.port || (isHttps ? 443 : 80),
        path: `${targetUrl.pathname}${targetUrl.search}`,
        method: method,
        headers: headers,
        timeout: this.timeout,
      };

      const req = requestModule.request(reqOptions, (res) => {
        const chunks: Buffer[] = [];
        res.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
        res.on('end', () => {
          const rawBuffer = Buffer.concat(chunks);
          const statusCode = res.statusCode || 500;
          const bodyStr = rawBuffer.toString('utf-8');

          if (statusCode >= 200 && statusCode < 300) {
            let data: any = bodyStr;
            if (res.headers['content-type']?.includes('application/json') || bodyStr.trim().startsWith('{') || bodyStr.trim().startsWith('[')) {
              try {
                data = JSON.parse(bodyStr);
              } catch (ignored) {}
            }
            resolve({ statusCode, headers: res.headers, data, buffer: rawBuffer });
          } else {
            const err = this.handleError(statusCode, bodyStr);
            reject(err);
          }
        });
      });

      req.on('error', (err) => reject(new AstraError(`Network error: ${err.message}`)));
      req.on('timeout', () => {
        req.destroy();
        reject(new AstraError('Request timed out'));
      });

      if (body) {
        if (Buffer.isBuffer(body) || typeof body === 'string') {
          req.write(body);
          req.end();
        } else if (body instanceof Readable) {
          body.pipe(req);
        } else {
          req.end();
        }
      } else {
        req.end();
      }
    });
  }

  private handleError(statusCode: number, message: string): AstraError {
    const msg = `AstraStore request failed [HTTP ${statusCode}]: ${message}`;
    if (statusCode === 401 || statusCode === 403) return new AstraAuthError(msg, statusCode);
    if (statusCode === 404) return new AstraNotFoundError(msg, statusCode);
    if (statusCode === 400 || statusCode === 409) return new AstraValidationError(msg, statusCode);
    return new AstraServerError(msg, statusCode);
  }

  // =========================================
  // Auth Operations
  // =========================================

  public async login(email: string, password: string): Promise<AuthResponse> {
    const payload = JSON.stringify({ email, password });
    const res = await this.request<AuthResponse>('POST', '/api/auth/login', payload);
    this.accessToken = res.data.token;
    this.refreshToken = res.data.refreshToken;
    return res.data;
  }

  public async refresh(): Promise<AuthResponse> {
    if (!this.refreshToken) throw new AstraAuthError('No refresh token available');
    const payload = JSON.stringify({ refreshToken: this.refreshToken });
    const res = await this.request<AuthResponse>('POST', '/api/auth/refresh', payload);
    this.accessToken = res.data.token;
    this.refreshToken = res.data.refreshToken;
    return res.data;
  }

  // =========================================
  // Bucket Operations
  // =========================================

  public async createBucket(name: string): Promise<Bucket> {
    const payload = JSON.stringify({ name });
    const res = await this.request<Bucket>('POST', '/api/v1/buckets', payload);
    return res.data;
  }

  public async getBucket(bucketId: string): Promise<Bucket> {
    const res = await this.request<Bucket>('GET', `/api/v1/buckets/${bucketId}`);
    return res.data;
  }

  public async listBuckets(): Promise<Bucket[]> {
    const res = await this.request<any>('GET', '/api/v1/buckets');
    if (res.data && Array.isArray(res.data.content)) {
      return res.data.content;
    }
    return Array.isArray(res.data) ? res.data : [];
  }

  public async deleteBucket(bucketId: string): Promise<void> {
    await this.request('DELETE', `/api/v1/buckets/${bucketId}`);
  }

  // =========================================
  // Object Upload & Download
  // =========================================

  public async uploadObject(
    bucketId: string,
    key: string,
    content: Buffer | string | Readable,
    contentType: string = 'application/octet-stream'
  ): Promise<UploadResult> {
    const sanitizedKey = key.replace(/^\/+/, '');
    const path = `/api/v1/buckets/${bucketId}/objects/${sanitizedKey}`;

    let payload: Buffer | Readable;
    if (typeof content === 'string' && fs.existsSync(content)) {
      payload = fs.createReadStream(content);
    } else if (typeof content === 'string') {
      payload = Buffer.from(content, 'utf-8');
    } else {
      payload = content;
    }

    const res = await this.request<UploadResult>('PUT', path, payload, contentType);
    return res.data;
  }

  public async downloadObjectBuffer(bucketId: string, key: string): Promise<Buffer> {
    const sanitizedKey = key.replace(/^\/+/, '');
    const path = `/api/v1/buckets/${bucketId}/objects/${sanitizedKey}`;
    const res = await this.request('GET', path, undefined, '');
    return res.buffer;
  }

  public async downloadObject(
    bucketId: string,
    key: string,
    target: string | Writable
  ): Promise<void> {
    const buffer = await this.downloadObjectBuffer(bucketId, key);
    if (typeof target === 'string') {
      await fs.promises.writeFile(target, buffer);
    } else if (target instanceof Writable) {
      target.write(buffer);
      target.end();
    }
  }

  public async getObjectMetadata(objectId: string): Promise<ObjectRecord> {
    const res = await this.request<ObjectRecord>('GET', `/api/v1/objects/${objectId}`);
    return res.data;
  }

  public async deleteObject(objectId: string): Promise<void> {
    await this.request('DELETE', `/api/v1/objects/${objectId}`);
  }

  // =========================================
  // API Keys
  // =========================================

  public async createApiKey(name: string): Promise<ApiKey> {
    const payload = JSON.stringify({ name });
    const res = await this.request<ApiKey>('POST', '/api/auth/keys', payload);
    return res.data;
  }

  public async listApiKeys(): Promise<ApiKey[]> {
    const res = await this.request<ApiKey[]>('GET', '/api/auth/keys');
    return Array.isArray(res.data) ? res.data : [];
  }

  public async revokeApiKey(keyId: number): Promise<void> {
    await this.request('DELETE', `/api/auth/keys/${keyId}`);
  }
}
