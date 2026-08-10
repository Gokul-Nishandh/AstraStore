# AstraStore CLI (`astra`)

A command-line tool for interacting with the AstraStore distributed storage system.

## Installation

The CLI is built as an executable JAR via Gradle:

```bash
cd cli
../gradlew installDist
```

The binary is produced at `cli/build/install/astra/bin/astra`.

Add it to your PATH:

```bash
export PATH="$PWD/cli/build/install/astra/bin:$PATH"
```

## Quick Start

```bash
# 1. Register (one-time) or use existing credentials
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"me","password":"MyP@ss123","email":"me@example.com"}'

# 2. Login — credentials are stored encrypted at ~/.astra/credentials.enc
astra auth login -u me@example.com --password=MyP@ss123

# 3. Create a bucket — copy the UUID from the output
astra mb -n my-bucket

# 4. Upload a file using the bucket UUID
astra upload ./README.md -b <bucket-uuid> -k readme.md

# 5. List objects
astra ls <bucket-uuid>

# 6. Download by object ID
astra download <object-uuid> -o readme-back.md

# 7. Check cluster health
astra cluster health
```

## Commands

### Authentication (`astra auth`)

| Command | Description |
|---------|-------------|
| `astra auth login -u <email> --password=<pwd>` | Log in with email + password |
| `astra auth login --api-key=<key>` | Log in with API key |
| `astra auth logout` | Revoke refresh token and clear local credentials |
| `astra auth status` | Show current authentication state |
| `astra auth create-key -n <name>` | Create new API key (raw key shown once) |
| `astra auth list-keys` | List your active API keys |
| `astra auth list-keys --output json` | Same, JSON output |
| `astra auth revoke-key --yes <id>` | Revoke an API key by ID |

### Bucket Management

| Command | Description |
|---------|-------------|
| `astra mb -n <name>` | Create a new bucket |
| `astra rb <bucket-uuid>` | Delete a bucket (confirms) |
| `astra rb <bucket-uuid> --yes` | Delete without confirmation |
| `astra ls-buckets` | List all buckets |
| `astra ls-buckets --output json` | List buckets as JSON |

### File Operations

| Command | Description |
|---------|-------------|
| `astra upload <file> -b <bucket-uuid> -k <key>` | Upload file to bucket |
| `astra upload <file> -b <uuid> -k <key> --no-progress` | Upload without progress bar |
| `astra download <object-uuid> -o <output>` | Download object by ID |
| `astra ls <bucket-uuid>` | List objects in a bucket |
| `astra ls <bucket-uuid> --output json` | List objects as JSON |
| `astra rm <object-uuid> --yes` | Delete object |

### Cluster Operations

| Command | Description |
|---------|-------------|
| `astra cluster health` | Show cluster health summary |
| `astra cluster health --output json` | Health summary as JSON |
| `astra cluster nodes` | List all storage nodes |
| `astra cluster healing status` | Show under-replicated chunk count |
| `astra cluster healing run` | Trigger self-healing scan immediately |

### Interactive Shell

```bash
astra shell
```

Starts a REPL where you can run commands without prefixing `astra`:

```
astra> login -u me@example.com --password=MyP@ss123
astra> mb -n test-bucket
astra> ls-buckets
astra> cluster health
astra> quit
```

## Global Options

Most commands support:
- `--output json` (where applicable) — JSON output for scripting
- `--help` / `-h` — show command help
- `--version` / `-V` — show version

## Configuration

The CLI reads `~/.astra/config.yaml`:

```yaml
gatewayUrl: http://localhost:8080
authUrl: http://localhost:8081
placementUrl: http://localhost:8085
outputFormat: table      # or json
timeoutSeconds: 30
```

Override defaults with environment variables:
- `ASTRA_GATEWAY_URL`
- `ASTRA_AUTH_URL`
- `ASTRA_PLACEMENT_URL`

## Security

- Credentials are encrypted at rest using **AES-256-GCM** with a PBKDF2-derived key from machine-specific entropy
- Credentials file is stored with `0600` permissions (owner read/write only)
- Raw API keys are shown **exactly once** at creation — store them immediately
- All HTTP requests automatically include Bearer token from stored credentials

## Troubleshooting

### "Not logged in"
Run `astra auth login` first.

### "Bucket not found: <uuid>"
Use `astra ls-buckets` to find valid bucket UUIDs (names won't work for upload).

### "Connection refused"
Ensure the AstraStore gateway is running at the configured `gatewayUrl` (default `http://localhost:8080`).

### Clear all local state
```bash
rm -rf ~/.astra/
```

## Examples

### Backup script
```bash
#!/bin/bash
BUCKET_ID=$(astra ls-buckets --output json | jq -r '.[0].id')
astra login -u backup@example.com --password=$BACKUP_PASSWORD
for f in /var/log/*.log; do
  astra upload "$f" -b $BUCKET_ID -k "logs/$(basename $f).$(date +%s)"
done
```

### CI/CD pipeline
```bash
export ASTRA_GATEWAY_URL=https://astra.company.com
astra auth login --api-key=$CI_API_KEY
BUCKET_ID=$(astra mb -n "build-$BUILD_ID" 2>/dev/null || astra ls-buckets --output json | jq -r '.[] | select(.name=="build-'$BUILD_ID'") | .id')
astra upload dist.tar.gz -b $BUCKET_ID -k "build-$BUILD_ID.tar.gz"
```

### Monitoring
```bash
while true; do
  clear
  astra cluster health
  sleep 30
done
```
