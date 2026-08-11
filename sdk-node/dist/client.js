"use strict";
var __createBinding = (this && this.__createBinding) || (Object.create ? (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    var desc = Object.getOwnPropertyDescriptor(m, k);
    if (!desc || ("get" in desc ? !m.__esModule : desc.writable || desc.configurable)) {
      desc = { enumerable: true, get: function() { return m[k]; } };
    }
    Object.defineProperty(o, k2, desc);
}) : (function(o, m, k, k2) {
    if (k2 === undefined) k2 = k;
    o[k2] = m[k];
}));
var __setModuleDefault = (this && this.__setModuleDefault) || (Object.create ? (function(o, v) {
    Object.defineProperty(o, "default", { enumerable: true, value: v });
}) : function(o, v) {
    o["default"] = v;
});
var __importStar = (this && this.__importStar) || (function () {
    var ownKeys = function(o) {
        ownKeys = Object.getOwnPropertyNames || function (o) {
            var ar = [];
            for (var k in o) if (Object.prototype.hasOwnProperty.call(o, k)) ar[ar.length] = k;
            return ar;
        };
        return ownKeys(o);
    };
    return function (mod) {
        if (mod && mod.__esModule) return mod;
        var result = {};
        if (mod != null) for (var k = ownKeys(mod), i = 0; i < k.length; i++) if (k[i] !== "default") __createBinding(result, mod, k[i]);
        __setModuleDefault(result, mod);
        return result;
    };
})();
Object.defineProperty(exports, "__esModule", { value: true });
exports.AstraClient = void 0;
const http = __importStar(require("http"));
const https = __importStar(require("https"));
const fs = __importStar(require("fs"));
const stream_1 = require("stream");
const errors_js_1 = require("./errors.js");
class AstraClient {
    baseUrl;
    apiKey;
    email;
    password;
    timeout;
    accessToken;
    refreshToken;
    constructor(options = {}) {
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
    async getValidToken() {
        if (this.accessToken)
            return this.accessToken;
        if (this.refreshToken) {
            try {
                await this.refresh();
                return this.accessToken;
            }
            catch (err) {
                // Fallback to credentials
            }
        }
        if (this.email && this.password) {
            await this.login(this.email, this.password);
            return this.accessToken;
        }
        return undefined;
    }
    async request(method, path, body, contentType = 'application/json') {
        const token = await this.getValidToken();
        const headers = {};
        if (contentType) {
            headers['Content-Type'] = contentType;
        }
        if (token) {
            if (this.apiKey) {
                headers['X-API-Key'] = token;
                headers['Authorization'] = `Bearer ${token}`;
            }
            else {
                headers['Authorization'] = `Bearer ${token}`;
            }
        }
        const targetUrl = new URL(`${this.baseUrl}${path}`);
        const isHttps = targetUrl.protocol === 'https:';
        const requestModule = isHttps ? https : http;
        return new Promise((resolve, reject) => {
            const reqOptions = {
                hostname: targetUrl.hostname,
                port: targetUrl.port || (isHttps ? 443 : 80),
                path: `${targetUrl.pathname}${targetUrl.search}`,
                method: method,
                headers: headers,
                timeout: this.timeout,
            };
            const req = requestModule.request(reqOptions, (res) => {
                const chunks = [];
                res.on('data', (chunk) => chunks.push(Buffer.from(chunk)));
                res.on('end', () => {
                    const rawBuffer = Buffer.concat(chunks);
                    const statusCode = res.statusCode || 500;
                    const bodyStr = rawBuffer.toString('utf-8');
                    if (statusCode >= 200 && statusCode < 300) {
                        let data = bodyStr;
                        if (res.headers['content-type']?.includes('application/json') || bodyStr.trim().startsWith('{') || bodyStr.trim().startsWith('[')) {
                            try {
                                data = JSON.parse(bodyStr);
                            }
                            catch (ignored) { }
                        }
                        resolve({ statusCode, headers: res.headers, data, buffer: rawBuffer });
                    }
                    else {
                        const err = this.handleError(statusCode, bodyStr);
                        reject(err);
                    }
                });
            });
            req.on('error', (err) => reject(new errors_js_1.AstraError(`Network error: ${err.message}`)));
            req.on('timeout', () => {
                req.destroy();
                reject(new errors_js_1.AstraError('Request timed out'));
            });
            if (body) {
                if (Buffer.isBuffer(body) || typeof body === 'string') {
                    req.write(body);
                    req.end();
                }
                else if (body instanceof stream_1.Readable) {
                    body.pipe(req);
                }
                else {
                    req.end();
                }
            }
            else {
                req.end();
            }
        });
    }
    handleError(statusCode, message) {
        const msg = `AstraStore request failed [HTTP ${statusCode}]: ${message}`;
        if (statusCode === 401 || statusCode === 403)
            return new errors_js_1.AstraAuthError(msg, statusCode);
        if (statusCode === 404)
            return new errors_js_1.AstraNotFoundError(msg, statusCode);
        if (statusCode === 400 || statusCode === 409)
            return new errors_js_1.AstraValidationError(msg, statusCode);
        return new errors_js_1.AstraServerError(msg, statusCode);
    }
    // =========================================
    // Auth Operations
    // =========================================
    async login(email, password) {
        const payload = JSON.stringify({ email, password });
        const res = await this.request('POST', '/api/auth/login', payload);
        this.accessToken = res.data.token;
        this.refreshToken = res.data.refreshToken;
        return res.data;
    }
    async refresh() {
        if (!this.refreshToken)
            throw new errors_js_1.AstraAuthError('No refresh token available');
        const payload = JSON.stringify({ refreshToken: this.refreshToken });
        const res = await this.request('POST', '/api/auth/refresh', payload);
        this.accessToken = res.data.token;
        this.refreshToken = res.data.refreshToken;
        return res.data;
    }
    // =========================================
    // Bucket Operations
    // =========================================
    async createBucket(name) {
        const payload = JSON.stringify({ name });
        const res = await this.request('POST', '/api/v1/buckets', payload);
        return res.data;
    }
    async getBucket(bucketId) {
        const res = await this.request('GET', `/api/v1/buckets/${bucketId}`);
        return res.data;
    }
    async listBuckets() {
        const res = await this.request('GET', '/api/v1/buckets');
        if (res.data && Array.isArray(res.data.content)) {
            return res.data.content;
        }
        return Array.isArray(res.data) ? res.data : [];
    }
    async deleteBucket(bucketId) {
        await this.request('DELETE', `/api/v1/buckets/${bucketId}`);
    }
    // =========================================
    // Object Upload & Download
    // =========================================
    async uploadObject(bucketId, key, content, contentType = 'application/octet-stream') {
        const sanitizedKey = key.replace(/^\/+/, '');
        const path = `/api/v1/buckets/${bucketId}/objects/${sanitizedKey}`;
        let payload;
        if (typeof content === 'string' && fs.existsSync(content)) {
            payload = fs.createReadStream(content);
        }
        else if (typeof content === 'string') {
            payload = Buffer.from(content, 'utf-8');
        }
        else {
            payload = content;
        }
        const res = await this.request('PUT', path, payload, contentType);
        return res.data;
    }
    async downloadObjectBuffer(bucketId, key) {
        const sanitizedKey = key.replace(/^\/+/, '');
        const path = `/api/v1/buckets/${bucketId}/objects/${sanitizedKey}`;
        const res = await this.request('GET', path, undefined, '');
        return res.buffer;
    }
    async downloadObject(bucketId, key, target) {
        const buffer = await this.downloadObjectBuffer(bucketId, key);
        if (typeof target === 'string') {
            await fs.promises.writeFile(target, buffer);
        }
        else if (target instanceof stream_1.Writable) {
            target.write(buffer);
            target.end();
        }
    }
    async getObjectMetadata(objectId) {
        const res = await this.request('GET', `/api/v1/objects/${objectId}`);
        return res.data;
    }
    async deleteObject(objectId) {
        await this.request('DELETE', `/api/v1/objects/${objectId}`);
    }
    // =========================================
    // API Keys
    // =========================================
    async createApiKey(name) {
        const payload = JSON.stringify({ name });
        const res = await this.request('POST', '/api/auth/keys', payload);
        return res.data;
    }
    async listApiKeys() {
        const res = await this.request('GET', '/api/auth/keys');
        return Array.isArray(res.data) ? res.data : [];
    }
    async revokeApiKey(keyId) {
        await this.request('DELETE', `/api/auth/keys/${keyId}`);
    }
}
exports.AstraClient = AstraClient;
