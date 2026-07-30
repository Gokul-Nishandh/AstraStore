# AstraStore — Infrastructure & Services Guide

> What was added, why, and how to use it.
> Last updated: 2026-07-30

---

## Table of Contents

1. [What Changed — High Level](#1-what-changed--high-level)
2. [Infrastructure Services](#2-infrastructure-services)
3. [How to Run the Full Stack](#3-how-to-run-the-full-stack)
4. [Auth Service](#4-auth-service)
5. [Metadata Service](#5-metadata-service)
6. [All Services — Observability](#6-all-services--observability)
7. [API Gateway Routes](#7-api-gateway-routes)
8. [Troubleshooting](#8-troubleshooting)

---

## 1. What Changed — High Level

Before this update, AstraStore was an **empty scaffold** — 8 Spring Boot services that each only had their `Application.java` main class and a "context loads" smoke test. Nothing was connected to anything.

After this update, the stack looks like this:

```
┌─────────────────────────────────────────────────────────────────┐
│                        Docker Network: astrastore                │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐        │
│  │ postgres │  │   redis  │  │   kafka  │  │zookeeper │        │
│  │  :5432   │  │  :6379   │  │  :9092   │  │  :2181   │        │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘        │
│                                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                      │
│  │prometheus│  │ grafana  │  │  jaeger  │                      │
│  │  :9090   │  │  :3000   │  │ :16686   │                      │
│  └──────────┘  └──────────┘  └──────────┘                      │
│                                                                  │
│  ┌─────────────────────────────────────────────────┐            │
│  │            Spring Boot Services (ports 8080-8087) │            │
│  │  api-gw │ auth │ upload │ download │ metadata     │            │
│  │  placement │ replication │ monitoring             │            │
│  └─────────────────────────────────────────────────┘            │
└─────────────────────────────────────────────────────────────────┘
```

**New files:**
- `infrastructure/` — Prometheus config, Grafana dashboards & datasource provisioning
- `docker-compose.yaml` — completely rewritten to include all infra
- `auth/src/main/java/com/astrastore/auth/` — JWT auth, DB-backed users
- `metadata/src/main/java/com/astrastore/metadata/` — file metadata CRUD
- Plus observability wiring in every service

---

## 2. Infrastructure Services

### PostgreSQL (port 5432)
**Image:** `postgres:16-alpine`
**Volume:** `postgres-data` (survives `docker compose down`)
**Creds:** `astrastore / astrastore_secret`

Stores:
- `users` table — user accounts for the auth service
- `file_metadata` table — all file metadata records

Schema is auto-created by Hibernate on first run (`ddl-auto: update`).

### Redis (port 6379)
**Image:** `redis:7-alpine`
**Creds:** none

Used for session and token caching in the auth service. In the current implementation it's configured but the JWT flow is stateless — Redis will be used for token blacklisting and rate-limiting in a future update.

### Kafka (ports 9092 internal / 29092 external)
**Image:** `confluentinc/cp-kafka:7.6.0`
**Volume:** `kafka-data`
**Dependency:** Zookeeper on port 2181

The async event bus. Future pipeline:
1. `upload` service receives a file → produces `file.uploaded` event to Kafka
2. `placement` service consumes it → decides where to store → produces `placement.decided`
3. `replication` service consumes `placement.decided` → creates N copies → produces `replication.complete`
4. `metadata` service updates the record

Currently wired but no producers/consumers are implemented yet — that's the next build step.

### Prometheus (port 9090)
**Image:** `prom/prometheus:v2.51.0`
**Volume:** `prometheus-data`
**Config:** `infrastructure/prometheus/prometheus.yml`

Scrapes all 8 Spring Boot services every 10s at `/actuator/prometheus`. No targets appear until the services are up and healthy.

### Grafana (port 3000)
**Image:** `grafana/grafana:10.4.0`
**Volume:** `grafana-data`
**Login:** `admin / admin123`

Pre-configured with:
- Prometheus as default datasource (auto-provisioned)
- One dashboard: **AstraStore — Service Overview** with 4 panels:
  - JVM CPU usage (per service)
  - HTTP request rate by endpoint
  - Response latency p99 / p95
  - HTTP 5xx error rate

### Jaeger (port 16686)
**Image:** `jaegertracing/all-in-one:1.56`
**Ports:** 16686 (UI), 4317 (OTLP gRPC), 4318 (OTLP HTTP)

Distributed tracing. Every request that crosses service boundaries generates a trace. To see traces:
1. Make an API call (e.g. login)
2. Open http://localhost:16686
3. Click **Search** — traces appear after a few seconds

---

## 3. How to Run the Full Stack

```bash
# Start everything (including building all 8 services)
docker compose up --build -d

# Wait ~30s for Postgres + Kafka to be healthy
docker compose ps

# Verify all services are up
curl http://localhost:8080/actuator/health   # → {"status":"UP"}
curl http://localhost:8081/actuator/health
curl http://localhost:9090/api/v1/status/config  # Prometheus is running

# Open the UIs
open http://localhost:3000   # Grafana  (admin / admin123)
open http://localhost:16686   # Jaeger
open http://localhost:9090    # Prometheus

# Stop everything (data volumes persist)
docker compose down

# Stop and wipe all data volumes
docker compose down -v
```

### Running locally without Docker

```bash
# You still need Postgres, Redis, Kafka running
# Then start individual services:
./gradlew :auth:bootRun          # runs on port 8081
./gradlew :metadata:bootRun      # runs on port 8084

# Or boot everything at once:
./gradlew runAll                 # all 8 in parallel
```

---

## 4. Auth Service

### What it does
- **Register** new users (`POST /api/auth/register`)
- **Login** and return a JWT (`POST /api/auth/login`)
- Every other request validates the JWT Bearer token

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/auth/register` | No | Create a new user account |
| `POST` | `/api/auth/login` | No | Returns a JWT token |
| `GET` | `/api/auth/**` | Yes | All other endpoints require a JWT |

### Registration
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "username": "alice",
    "email": "alice@example.com",
    "password": "password123"
  }'
# → 201 Created: { "message": "User registered successfully" }
```

### Login
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "alice@example.com",
    "password": "password123"
  }'
# → 200 OK:
# {
#   "token": "eyJhbGciOiJIUzI1NiJ9...",
#   "type": "Bearer",
#   "userId": 1,
#   "username": "alice",
#   "email": "alice@example.com",
#   "roles": ["USER"]
# }
```

### Using the token
Every subsequent request includes the token:
```bash
curl http://localhost:8080/api/metadata/files \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9..."
```

### How it works (code structure)

```
auth/src/main/java/com/astrastore/auth/
├── entity/User.java              # JPA entity — implements UserDetails
├── repository/UserRepository.java # findByEmail, existsByEmail
├── security/
│   ├── JwtService.java           # Generates & validates HS256 JWTs
│   └── JwtAuthenticationFilter.java  # Intercepts every request, validates token
├── config/
│   ├── PersistenceConfig.java   # UserDetailsService bean
│   └── SecurityConfig.java       # Filter chain — stateless JWT, public /auth/*
├── controller/AuthController.java # /register, /login endpoints
└── dto/
    ├── RegisterRequest.java      # Validated payload
    ├── LoginRequest.java
    └── AuthResponse.java         # Token + user info response
```

### Security notes
- Passwords are hashed with **BCrypt** before storage
- JWTs are signed with **HS256** — the secret is in `application.yaml` and overridden by `JWT_SECRET` env var in docker-compose
- The old hardcoded `admin/admin123` is gone — no default users exist until you register one
- Sessions are **stateless** — no server-side sessions, every request must carry a valid token

---

## 5. Metadata Service

### What it does
CRUD operations for file metadata records — the database of what files exist, who owns them, and where they're stored.

### Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/api/metadata/files` | Yes | List all files (paginated) |
| `GET` | `/api/metadata/files?owner=email` | Yes | List files by owner |
| `GET` | `/api/metadata/files/{id}` | Yes | Get one file's metadata |
| `POST` | `/api/metadata/files` | Yes | Create metadata record |
| `PUT` | `/api/metadata/files/{id}` | Yes | Update metadata |
| `DELETE` | `/api/metadata/files/{id}` | Yes | Delete metadata |

### Create a file metadata record
```bash
curl -X POST http://localhost:8080/api/metadata/files \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{
    "filename": "annual-report.pdf",
    "contentType": "application/pdf",
    "size": 15728640,
    "owner": "alice@example.com",
    "contentHash": "sha256:abc123...",
    "storageLocation": "node-1:/data/abc123",
    "replicaCount": 3
  }'
```

### Deduplication
If a `contentHash` matches an existing record, `POST` returns `409 Conflict`. This prevents duplicate uploads.

### Database schema
```
file_metadata
├── id              BIGSERIAL PRIMARY KEY
├── filename        VARCHAR NOT NULL
├── content_type    VARCHAR NOT NULL
├── size            BIGINT NOT NULL
├── owner           VARCHAR NOT NULL
├── content_hash    VARCHAR UNIQUE  (for deduplication)
├── storage_location VARCHAR NOT NULL  (where the file lives)
├── replica_count   INT NOT NULL DEFAULT 1
├── created_at      TIMESTAMP
└── updated_at      TIMESTAMP

indexes:
  idx_file_owner            ON (owner)
  idx_file_storage_location ON (storage_location)
```

### Code structure
```
metadata/src/main/java/com/astrastore/metadata/
├── entity/FileMetadata.java         # JPA entity
├── repository/FileMetadataRepository.java  # findByOwner, findByContentHash, paginated
├── controller/MetadataController.java  # Full CRUD at /api/metadata/files
└── dto/
    ├── FileMetadataRequest.java     # Validated input
    └── FileMetadataResponse.java    # API response
```

---

## 6. All Services — Observability

Every service now exposes:

| Endpoint | What it shows |
|----------|--------------|
| `GET /actuator/health` | Health status (UP/DOWN) |
| `GET /actuator/info` | App info |
| `GET /actuator/prometheus` | Prometheus metrics |

All metrics include:
- HTTP request count, latency, error rate (per endpoint)
- JVM memory and GC stats
- Custom business metrics (added as you build)

**Tracing:** Every request generates an OpenTelemetry trace, sent to Jaeger. To enable tracing, add the OTEL Java agent to the JVM:

```bash
java -javaagent:opentelemetry-javaagent.jar \
     -Dotel.service.name=auth-service \
     -Dotel.exporter.otlp.endpoint=http://localhost:4317 \
     -jar app.jar
```

This is already configured in the Dockerfile via `JAVA_TOOL_OPTIONS` env var.

---

## 7. API Gateway Routes

The gateway (port 8080) strips the `/api` prefix and forwards to downstream services:

```
Client
  │
  ▼
GET /api/auth/login          →  http://auth:8081/auth/login
GET /api/metadata/files      →  http://metadata:8084/metadata/files
GET /api/upload/something   →  http://upload:8082/upload/something
  (StripPrefix=1 removes the first path segment)
```

All route URIs use Docker DNS names (`auth`, `metadata`, etc.) — **not** `localhost`. Using `localhost` inside a container would resolve to the container itself, not the other services.

---

## 8. Troubleshooting

### Services fail to start with "connection refused to postgres"
```bash
# Check if postgres is healthy
docker compose ps postgres
# If not healthy, check logs
docker compose logs postgres
```
Wait longer — postgres takes ~10s on first start. The `depends_on` with `condition: service_healthy` should handle this automatically.

### Prometheus shows 0 targets
Services need to be up AND expose `/actuator/prometheus`. Check:
```bash
curl http://localhost:8081/actuator/prometheus | head -5
```
Also check Prometheus config reload: `http://localhost:9090/-/reload`

### Auth login returns 401 even after registration
- Registration returns `201` — if you see `409`, the email is already registered
- Make sure you're hitting the right endpoint: `/api/auth/login` (not `/api/auth/register`)
- The JWT token must be passed as `Authorization: Bearer <token>` (capital B)

### Gateway returns 404
The gateway may not be routing correctly. Check:
```bash
curl http://localhost:8080/actuator/gateway/routes
```
This lists all configured routes. If routes are missing, the `application.yaml` changes may not have been picked up — rebuild the image: `docker compose up --build api-gateway`

### Tests fail because they can't reach Postgres
Tests use H2 in-memory database, not Postgres. They also disable Kafka and Redis. If a test fails:
```bash
# Run with test profile explicitly
./gradlew :auth:test --tests "*" -Pspring.profiles.active=test
```

### Changing the JWT secret
In production, always set this via environment variable:
```yaml
# docker-compose.yaml — auth service
environment:
  JWT_SECRET: ${JWT_SECRET}   # set in your shell or .env file
```
