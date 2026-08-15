# ============================================================
#  AstraStore Dashboard — production image
#
#  Stage 1 builds the Vite/React bundle.
#  Stage 2 serves it from nginx and reverse-proxies /api to the
#  API Gateway, so the browser only ever talks to one origin.
# ============================================================

# ---- Stage 1: build ----------------------------------------
FROM node:22-alpine AS build

WORKDIR /app

# Install dependencies from the lockfile first so this layer is
# cached whenever only application source changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .

# Type-check and bundle. Fails the image build on a type error,
# which keeps a broken frontend from ever reaching a container.
RUN npm run build

# ---- Stage 2: serve ----------------------------------------
FROM nginx:1.27-alpine AS runtime

# Unprivileged runtime user; nginx only needs to read the bundle.
RUN addgroup -S astra && adduser -S astra -G astra

COPY --from=build /app/dist /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Writable paths for an unprivileged nginx. /run is named explicitly rather
# than via /var/run: on Alpine the latter is a symlink, and `chown -R` does
# not follow symlinks, so chowning it leaves the real directory root-owned
# and nginx dies with "open() /run/nginx.pid failed (13: Permission denied)".
RUN mkdir -p /var/cache/nginx /run \
    && chown -R astra:astra /var/cache/nginx /run /usr/share/nginx/html

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/healthz || exit 1

USER astra

# The pid path is left to the base image's nginx.conf; overriding it via -g
# collides with the directive already declared there ("pid directive is
# duplicate"). Ownership of /run above is what makes the default path work.
CMD ["nginx", "-g", "daemon off;"]
