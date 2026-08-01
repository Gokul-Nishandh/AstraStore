# AstraStore - Distributed Storage Engine

A production-grade distributed storage system inspired by Amazon S3, implementing zero-memory streaming, P2P replication, and self-healing data repair mechanisms.

---

## 🌟 Features

- **Zero-Memory Streaming** — O(1) memory footprint using 8KB fixed buffer, handles files of any size
- **Dual SHA-256 Digests** — Per-chunk and whole-object hashing without staging to disk
- **Atomic Writes** — Temp file → fsync → atomic move for crash-safe storage
- **P2P Replication** — Direct node-to-node streaming with Kafka coordination
- **Self-Healing** — Automatic detection and repair of under-replicated chunks
- **Exponential Backoff** — Network resilience with retry logic (1s → 2s → 4s)
- **Concurrency Limits** — Semaphore-based throttling (10 concurrent streams per node)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                          API Gateway (:8080)                         │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
          ┌───────────────┼───────────────┐
          ▼               ▼               ▼
    ┌──────────┐   ┌──────────┐   ┌──────────┐
    │  Upload  │   │ Download │   │   Auth   │
    │ (:8082)  │   │ (:8083) │   │ (:8081) │
    └────┬─────┘   └──────────┘   └──────────┘
         │
         │ HTTP Streaming (8KB buffer)
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│                 Storage Node 1  (:8088)                              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │  POST /chunks/{id}  │  Atomic write → fsync → move          │   │
│  │  GET  /chunks/{id}  │  Hex fan-out (00-ff directories)     │   │
│  │  GET  /health       │  Disk capacity + status               │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
         │
         │ Kafka: astrastore.chunks.written
         ▼
┌─────────────────────────────────────────────────────────────────────┐
│               Replication Service (:8086)                            │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────────┐  │
│  │  Kafka      │  │  P2P Push   │  │  Self-Healing Engine       │  │
│  │  Listener   │──│  Client     │  │  • UnderReplicationScanner  │  │
│  │             │  │  (exponential│  │  • RepairRateLimiter (2/s) │  │
│  │             │  │   backoff)  │  │  • RecoveryPublisher       │  │
│  └─────────────┘  └──────┬──────┘  └─────────────────────────────┘  │
└──────────────────────────┼──────────────────────────────────────────┘
                           │ P2P Streaming
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
   ┌──────────┐     ┌──────────┐     ┌──────────┐
   │ Storage  │     │ Storage  │     │ Storage  │
   │ Node 2   │     │ Node 3   │     │ Node 1   │
   │ (:8089)  │     │ (:8090)  │     │ (:8088)  │
   └──────────┘     └──────────┘     └──────────┘
```

---

## 👥 Team

| Name | Role | Module |
|------|------|--------|
| **B T Senthan Amuthan** | Infrastructure Lead | Module 3: Distributed Storage Engine |
| **Pranaav A** | Security & DX Lead | Module 4: Auth, SDKs & Monitoring |
| **Pranav Surya** | Data Flow Lead | Module 1: Metadata Engine & Gateway |
| **Gokul Nishandh** | System State Lead | Module 2: Placement & Cluster Health |

---

## 📦 Module Breakdown

| Module | Owner | Description |
|--------|-------|-------------|
| **Module 1: Metadata Engine** | Pranav Surya | PostgreSQL schema, Upload/Download Orchestrator, API Gateway |
| **Module 2: Placement & Health** | Gokul Nishandh | Placement Strategy, Node Health State Machine, Monitoring |
| **Module 3: Storage Engine** | B T Senthan Amuthan | Zero-Memory Chunker, Storage Node Agent, Replication, Self-Healing |
| **Module 4: Auth & DX** | Pranaav A | Auth Service, Client SDKs, Monitoring Dashboard |

---

## 🚀 Quick Start

### Prerequisites

- Docker & Docker Compose
- Java 21+
- Gradle 9+

### 1. Clone & Build

```bash
git clone https://github.com/Gokul-Nishandh/AstraStore.git
cd AstraStore
./gradlew clean build -x test
```

### 2. Start All Services

```bash
docker compose up -d
```

### 3. Verify Services

```bash
# Check all services
docker compose ps

# Health checks
curl http://localhost:8080/actuator/health   # API Gateway
curl http://localhost:8081/actuator/health  # Auth
curl http://localhost:8082/actuator/health  # Upload
curl http://localhost:8086/actuator/health  # Replication
curl http://localhost:8088/api/v1/health/heartbeat   # Storage Node 1
curl http://localhost:8089/api/v1/health/heartbeat   # Storage Node 2
curl http://localhost:8090/api/v1/health/heartbeat   # Storage Node 3
```

---

## 🧪 Testing APIs

### Upload a File

```bash
# Via API Gateway
curl -X POST http://localhost:8080/api/v1/upload \
  -F "file=@/path/to/file.bin"

# Response
{
  "globalHash": "abc123...",
  "totalChunks": 2,
  "chunks": [
    {"chunkId": "...", "nodeIp": "http://storage-node-1:8088", "checksum": "..."}
  ]
}
```

### Verify Replication (All 3 Nodes)

```bash
curl http://localhost:8088/api/v1/chunks/{chunkId}
curl http://localhost:8089/api/v1/chunks/{chunkId}
curl http://localhost:8090/api/v1/chunks/{chunkId}
```

### Self-Healing Test

```bash
# 1. Register chunk for tracking
curl -X POST http://localhost:8086/api/v1/admin/metadata/register \
  -H "Content-Type: application/json" \
  -d '{
    "chunkId": "YOUR-CHUNK-ID",
    "sizeBytes": 1024,
    "checksum": "abc123...",
    "targetReplicas": 3,
    "replicaNodes": [
      "http://storage-node-1:8088",
      "http://storage-node-2:8088",
      "http://storage-node-3:8088"
    ]
  }'

# 2. Simulate node failure (drop 1 replica)
curl -X POST http://localhost:8086/api/v1/admin/chaos/kill-node \
  -H "Content-Type: application/json" \
  -d '{"chunkId": "YOUR-CHUNK-ID", "replicasToDrop": 1}'

# 3. Trigger healing (or wait 60s for auto-scan)
curl -X POST http://localhost:8086/api/v1/admin/heal/run

# 4. Verify healed
curl http://localhost:8086/api/v1/admin/metadata/status
# Expected: {"underReplicatedChunks":0, ...}
```

### Auth Service

```bash
# Register
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"password123","email":"user@test.com"}'

# Login
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"user","password":"password123","email":"user@test.com"}'
```

---

## 🔑 Key Technical Decisions

### Zero-Memory Streaming

```java
// 8KB fixed buffer - memory never grows regardless of file size
byte[] buffer = new byte[8192];
while ((bytesRead = inputStream.read(buffer)) != -1) {
    chunkDigest.update(buffer, 0, bytesRead);
    objectDigest.update(buffer, 0, bytesRead);
    outputStream.write(buffer, 0, bytesRead);
}
```

### Atomic Writes

```java
// 1. Write to temp file
// 2. fsync to disk
// 3. Atomic rename to final location
Files.write(tempPath, data);
tempPath.toFile().setWritable(true);
FileOutputStream fos = new FileOutputStream(tempPath.toFile());
fos.getFD().sync();
Files.move(tempPath, finalPath, StandardCopyOption.ATOMIC_MOVE);
```

### Self-Healing Loop

```
UnderReplicationScanner (every 60s)
         ↓
MockChunkDatabase.findUnderReplicated()
         ↓
RepairRateLimiter.throttle(2/sec)
         ↓
RecoveryPublisher → Kafka (ChunkWrittenEvent)
         ↓
KafkaChunkListener → ReplicationOrchestrator
         ↓
P2P Push to target nodes
         ↓
MockMetadataClient → MockChunkDatabase (updated)
```

---

## 📊 Kafka Topics

| Topic | Producer | Consumer | Purpose |
|-------|----------|----------|---------|
| `astrastore.chunks.written` | Upload Service, Recovery Publisher | Replication Service | Trigger P2P replication |

---

## 🎯 Interview Impact

This project demonstrates:

| Concept | Company Focus | Implementation |
|---------|---------------|----------------|
| Zero-copy streaming | Amazon S3, Stripe | 8KB buffer, O(1) memory |
| Distributed replication | DynamoDB, Cassandra | P2P with Kafka coordination |
| Self-healing | AWS, Azure | 60s scanner + rate limiter |
| Exponential backoff | Amazon, Google | 1s → 2s → 4s with jitter |
| Atomic file I/O | Databases, firmware | fsync + atomic move |
| Event-driven architecture | LinkedIn, Netflix | Kafka-based async replication |

---

## 📄 License

MIT License - See LICENSE file for details.
