# FinCore 360 — Web Portal Production Image
# Multi-stage build: Node 22 build environment -> Nginx 1.27 Alpine runtime

# ── STAGE 1: BUILD ENVIRONMENT ──────────────────────────────────
FROM node:22-alpine AS build

WORKDIR /app

# Cache dependencies layer
COPY web/package*.json ./
RUN npm ci

# Copy source code and build production bundle
COPY web/ ./
RUN npm run build

# ── STAGE 2: RUNTIME ENVIRONMENT ────────────────────────────────
FROM nginx:1.27-alpine AS runtime

# Remove default nginx static assets and configs
RUN rm -rf /usr/share/nginx/html/* /etc/nginx/conf.d/*

# Copy built SPA bundle from build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Copy production hardened Nginx configuration
COPY infra/docker/nginx.conf /etc/nginx/conf.d/default.conf

# Configure directory permissions for unprivileged execution
RUN chown -R nginx:nginx /usr/share/nginx/html /var/cache/nginx /var/log/nginx /etc/nginx/conf.d && \
    touch /var/run/nginx.pid && \
    chown -R nginx:nginx /var/run/nginx.pid

# Expose standard HTTP port
EXPOSE 80

# Image health check against local endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=10s --retries=3 \
    CMD wget --spider -q http://localhost:80/health || exit 1

# Run as non-root nginx user
USER nginx

CMD ["nginx", "-g", "daemon off;"]
