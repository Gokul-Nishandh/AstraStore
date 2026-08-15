import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import tailwindcss from '@tailwindcss/vite'

/**
 * In production the dashboard is served by nginx, which reverse-proxies
 * everything under /api to the API gateway and nothing else (see
 * `nginx.conf`). The dev server mirrors that exactly: one upstream, the
 * gateway, and no direct line to any individual service.
 *
 * That symmetry is the point. An earlier version proxied /api/v1/objects to
 * metadata, /api/v1/cluster to placement and /api/v1/admin to replication,
 * plus a middleware that rerouted object listings around the gateway
 * entirely. It worked, but it meant edge authentication, rate limiting and
 * error normalisation were never exercised during development — so every
 * bug in them could only surface in a deployed environment. The gateway now
 * routes all of it, including the Accept-header split between an object's
 * metadata and its bytes, so the workarounds are gone.
 */
const GATEWAY = 'http://localhost:8080'

export default defineConfig({
  plugins: [react(), tailwindcss()],
  server: {
    port: 5173,
    proxy: {
      '/api': GATEWAY,
      // The gateway rewrites this onto the monitoring service.
      '/health': GATEWAY,
    },
  },
})
