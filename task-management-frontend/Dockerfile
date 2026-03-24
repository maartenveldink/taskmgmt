# =============================================================================
# Multi-stage build for Task Management PoC — Angular frontend
#
#   docker build -f task-management-frontend/Dockerfile -t task-management-frontend .
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — build
# -----------------------------------------------------------------------------
FROM node:22-alpine AS build

WORKDIR /build

# Copy package files first for layer caching
COPY task-management-frontend/package.json task-management-frontend/package-lock.json ./
RUN npm ci --quiet

# Copy source and build
COPY task-management-frontend/ ./
RUN npm run build -- --configuration production

# -----------------------------------------------------------------------------
# Stage 2 — runtime (nginx)
# -----------------------------------------------------------------------------
FROM nginx:alpine

# Angular 19 outputs to dist/<name>/browser/
COPY --from=build /build/dist/task-management-frontend/browser /usr/share/nginx/html

# nginx config: serve SPA with HTML5 routing support
COPY task-management-frontend/nginx.conf /etc/nginx/conf.d/default.conf

EXPOSE 80
