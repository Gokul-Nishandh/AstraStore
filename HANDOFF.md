# Handoff — next task: see "Known outstanding work"

Continuation notes for a fresh session. Everything below is verified against
the running stack unless marked otherwise.

---

## Done: the chunk placement view

Administrators can now see **where chunks actually live**, on both surfaces.

- `GET /api/v1/admin/objects/{objectId}/chunks` → `AdminChunkController` in
  `metadata/`. Every chunk of one object in index order; not paged, because an
  object's chunk count is bounded by its size.
- `GET /api/v1/admin/chunks?nodeId=…` → the same controller. Every chunk a
  node holds **in either role**, paged and sort-sanitised.
- Both are **ADMIN only**, enforced by a class-level
  `@PreAuthorize("hasRole('ADMIN')")`. Verified live: a signed-in non-admin
  gets 403 on both, anonymous gets 401, and the console fires no request at
  all without the role.
- UI: a "Chunk placement" panel on `ObjectDetail` (admins only) and a
  drill-down dialog from a clickable node row on the cluster page, in
  `dashboard/src/components/chunks/`.

### Two things worth knowing before touching this

1. **A node answers to two names.** `chunk_locations.node_id` holds the node's
   *base URL* (`http://storage-node-1:8088`) because upload writes
   `ChunkManifest.nodeIp()` there and the download service fetches the bytes
   straight from that column. The placement registry calls the same machine
   `storage-node-1`. Neither can change without touching the read path, so
   `?nodeId=` is **repeatable** and the console sends both forms. `nodeLabel()`
   in the dashboard renders either as "Node 1".
2. **`findByNodeId` matches the primary column only.** A node holding nothing
   but replicas looks empty through it — on the live cluster `storage-node-2`
   holds 17 chunks and every one is a replica. `findByNode` covers both
   columns; prefer it for anything user-visible.

Per-chunk byte sizes do not exist anywhere — `chunk_locations` records no
length — so the node drill-down shows the **object's** size and labels it as
such rather than inventing a per-chunk figure.

---

## Read these first

- `dashboard/DESIGN.md` — the design contract. Token vocabulary, the "never
  invent a number" rule, the four required async states, responsive floor at
  375px. It is not optional reading.
- `dashboard/src/index.css` — the Copper Nebula tokens. Never hardcode a hex
  or an `oklch()` outside this file, including in SVG `fill`/`stroke`.
- `astrastore-docs/src/app/architecture/` — the read path, write path,
  durability and failure-recovery pages describe the real mechanism.

---

## Conventions that will save you time

- **Tables** go inside a `.scroll-x` wrapper. A table that scrolls the page
  sideways is a bug.
- **Tailwind breakpoints track the viewport, not the container.** A panel
  narrowed by the sidebar will still apply `lg:` classes. `ObjectList` has a
  `compact` prop for exactly this reason.
- **Popovers must be portalled.** `Menu` renders into `document.body` because
  an `overflow` ancestor clips absolutely positioned children — that bug read
  as "the action button does nothing".
- **Never render a raw error.** Everything goes through `toUserMessage`.
- **Null means "no data", not zero.** `formatBytes`, `formatCount`,
  `formatUptime`, `formatDuration` all take null and return an em dash.

---

## Traps already hit — do not repeat them

1. **Multi-document YAML.** `auth/src/main/resources/application.yaml` has
   `---` separators. Appending a root key creates a duplicate and the service
   dies with "while constructing a mapping". Merge into the existing key.
2. **Compose env vars.** Several services expected variables that
   `docker-compose.yaml` never set, and each failed only when composed. If you
   add a config property, wire it in compose in the same change.
3. **Hibernate enum CHECK constraints.** `ddl-auto: update` creates them and
   never revises them, so adding an enum value breaks inserts at runtime. Set
   `columnDefinition = "varchar(n)"` — see the comment on `AuditLog.action`.
4. **`ddl-auto` does not always add columns to an existing table.** Adding
   fields to `User` took down every login until the entity change was
   reverted. If you add a column, plan the migration explicitly.
5. **Named parameters in a native query's `GROUP BY`** expand to different
   bind markers than the copy in `SELECT`; Postgres rejects it. Group by
   ordinal.
6. **The Postgres driver returns `Instant` for `timestamptz`**, not
   `Timestamp`.
7. **Browser navigation carries no `Authorization` header.** Anything the
   browser fetches directly needs an authenticated `fetch`, not an `<a href>`.
   Testing an endpoint with curl proves the API works, not that the UI does.
8. **The images package `<service>/build/libs/*.jar`** — see `Dockerfile`. So
   `docker compose build <service>` ships whatever jar is on disk, and
   `./gradlew :<service>:test` does **not** produce one. Without a
   `:<service>:bootJar` (or full `build`) first, you are testing the previous
   jar and debugging a ghost. This cost an hour and a wrong diagnosis.
9. **`/api/v1/admin/**` at the gateway belongs to the replication service.**
   Anything under that prefix served by another service needs its own route
   declared *above* `admin-route`. Two such routes exist for metadata.

---

## Verifying your work

Bring the stack up and exercise the real thing — this codebase has produced
several bugs that only appear when composed:

```bash
docker compose up -d
```

Dashboard http://localhost:5173 · gateway http://localhost:8080 · docs
`cd ../astrastore-docs && npm run dev` (port 3000).

Build gates:

```bash
./gradlew build -x :cli:test -x :metadata:jacocoTestCoverageVerification
cd dashboard && npx tsc -b --noEmit && npm run build
```

Clean up any accounts, buckets or objects you create while testing. The only
account that should remain is the administrator.

---

## Known outstanding work, in rough priority order

1. **Two small defects found while building the placement view, both
   pre-existing and both left alone deliberately.** (a) The app shell overflows
   the viewport by ~23px at 375px on *every* page — the `ml-auto flex` cluster
   in the header — which breaks the "no horizontal scrolling on `<body>`" rule
   in `DESIGN.md` §8. (b) The shell polls
   `/api/v1/monitoring/summary` regardless of role, so every non-admin session
   generates a steady stream of 403s — the exact thing the `enabled` flag on
   `useClusterHealth` was introduced to prevent.
2. **Existing chunks are over-replicated.** `ReplicationOrchestrator` used to
   place two replicas regardless of the configured factor, so every chunk
   written before that fix sits on all three nodes. New writes are correct;
   old chunks need a reconciliation pass to drop the surplus copy.
3. **Downloads buffer in memory.** Fixed to use an authenticated fetch, which
   is correct but loads the whole object before the save dialog. The right
   answer is a short-lived pre-signed URL the browser can navigate to.
4. **Account deletion does not erase stored objects.** Credentials, keys and
   sessions go and the audit trail is anonymised, but buckets and objects
   remain. A `user_deletion_events` row is written and **nothing consumes it**.
   The data-deletion policy page says so honestly and is marked as blocking
   publication until this is resolved.
5. **Storage quotas and billing** — deferred by the owner. Free tier 100 MB,
   paid tier around ₹500/month, ADMIN unlimited for testing. Needs a payment
   provider; Razorpay suits ₹ pricing.
6. **`metadata` test coverage is 47% against a 70% gate** (SDD NFR-011). The
   gate is currently excluded from the build rather than met.
7. **OAuth was removed** after it took down login, and the implementation is
   **gone** — the commits that added and reverted it were squashed away before
   they ever reached GitHub, so there is no history to recover it from.
   Federated sign-in has to be rewritten from scratch. Whoever does: apply the
   schema migration *before* deploying, because adding these columns to a live
   `users` table is what broke login last time — `ALTER TABLE users ADD COLUMN
   provider varchar(32) NOT NULL DEFAULT 'LOCAL', ADD COLUMN provider_id
   varchar(191);`
8. **Almost nothing has been visually reviewed in a browser.** Every claim in
   this repo about the UI is backed by build, type check and API response —
   not by looking at it. The exceptions are the two chunk placement surfaces,
   which were driven in a real browser at 1280px and 375px.

---

## Verified facts

Confirmed against a running cluster, not read from a design document.

| Property | Value |
|---|---|
| Chunk size | 8 MiB (a 12 MB object produces 2 chunks) |
| Replication factor | 2 — primary `node_id` + `replica_node_id` |
| Object digest | SHA-256, matches byte-for-byte on round trip |
| Cross-account access | 404, never 403 |
| Admin bootstrap | `ASTRASTORE_ADMIN_EMAILS` promotes on startup |
| Internal token | `ASTRA_INTERNAL_TOKEN`; services refuse to start without it outside dev/test |
