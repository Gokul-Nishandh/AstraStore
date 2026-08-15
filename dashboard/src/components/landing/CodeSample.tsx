import { useState } from 'react'
import { SegmentedControl } from '../ui/Tabs'
import { CopyButton } from '../ui/CopyButton'

type Surface = 'cli' | 'python' | 'node' | 'http'

interface Sample {
  value: Surface
  label: string
  /** Shown above the block so the reader knows what they are installing. */
  install: string
  code: string
}

/* Every sample is the syntax the shipped clients actually expose — the CLI
   verbs come from the Picocli commands, the SDK method names from their
   published type definitions, and the HTTP paths from the gateway routes. */
const SAMPLES: Sample[] = [
  {
    value: 'cli',
    label: 'CLI',
    install: './gradlew :cli:installDist',
    code: `# Credentials are encrypted at rest in ~/.astra/credentials.enc
astra auth login -u you@example.com --password="$ASTRA_PASSWORD"

# Buckets are addressed by UUID, not by name
astra mb -n release-artifacts
astra ls-buckets --output json

astra upload ./build.tar.gz -b "$BUCKET_ID" -k builds/2026-02-14.tar.gz
astra ls "$BUCKET_ID"
astra download "$OBJECT_ID" -o build.tar.gz

# Replica state, and an on-demand repair pass
astra cluster health
astra cluster healing status`,
  },
  {
    value: 'python',
    label: 'Python',
    install: 'pip install ./sdk-python',
    code: `import os
from astrastore import AstraClient

astra = AstraClient(
    base_url="https://astra.example.com",
    api_key=os.environ["ASTRA_API_KEY"],
)

bucket = astra.create_bucket("release-artifacts")

with open("build.tar.gz", "rb") as f:
    result = astra.upload_object(bucket.id, "builds/2026-02-14.tar.gz", f)

# chunkCount is how many pieces the object was split into;
# checksum is the whole-object SHA-256 computed during the stream.
print(result.objectId, result.chunkCount, result.checksum)

astra.download_object(bucket.id, "builds/2026-02-14.tar.gz", "restored.tar.gz")`,
  },
  {
    value: 'node',
    label: 'Node',
    install: 'npm install @astrastore/sdk',
    code: `import { createReadStream } from 'node:fs'
import { AstraClient } from '@astrastore/sdk'

const astra = new AstraClient({
  baseUrl: 'https://astra.example.com',
  apiKey: process.env.ASTRA_API_KEY,
})

const bucket = await astra.createBucket('release-artifacts')

const { objectId, chunkCount, checksum } = await astra.uploadObject(
  bucket.id,
  'builds/2026-02-14.tar.gz',
  createReadStream('build.tar.gz'),
)

await astra.downloadObject(bucket.id, 'builds/2026-02-14.tar.gz', 'restored.tar.gz')`,
  },
  {
    value: 'http',
    label: 'HTTP',
    install: 'no client required',
    code: `# Exchange a password for a JWT pair, or skip this and send an API key
curl -s -X POST https://astra.example.com/api/auth/login \\
  -H 'Content-Type: application/json' \\
  -d '{"email":"you@example.com","password":"'"$ASTRA_PASSWORD"'"}'

# The object key is the remainder of the path, slashes and all
curl -X PUT \\
  "https://astra.example.com/api/v1/buckets/$BUCKET_ID/objects/builds/2026-02-14.tar.gz" \\
  -H "Authorization: Bearer $TOKEN" \\
  --data-binary @build.tar.gz

curl -H "Authorization: Bearer $TOKEN" \\
  "https://astra.example.com/api/v1/objects/$OBJECT_ID" -o build.tar.gz`,
  },
]

export function CodeSample() {
  const [surface, setSurface] = useState<Surface>('cli')
  const active = SAMPLES.find((s) => s.value === surface) ?? SAMPLES[0]

  return (
    <div className="overflow-hidden rounded-xl border border-line bg-surface shadow-xs">
      <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line px-3 py-2.5 sm:px-4">
        <SegmentedControl
          items={SAMPLES.map((s) => ({ value: s.value, label: s.label }))}
          value={surface}
          onChange={setSurface}
          size="sm"
          aria-label="Client"
        />
        <div className="flex items-center gap-2">
          <span className="hidden font-mono text-[11.5px] text-ink-4 sm:inline">{active.install}</span>
          <CopyButton value={active.code} label="Copy" />
        </div>
      </div>

      <div className="scroll-x bg-bg-elevated">
        <pre className="min-w-full p-4 text-[12.5px] leading-relaxed text-ink-2 sm:p-5">
          <code>{active.code}</code>
        </pre>
      </div>
    </div>
  )
}
