#!/usr/bin/env bash
set -euo pipefail

# Resolve the directory this script lives in
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# Image tag = short git SHA
export IMAGE_TAG
IMAGE_TAG=$(git rev-parse --short HEAD)

echo "==> Building images with tag: ${IMAGE_TAG}"

docker build \
  -f task-management-backend/Dockerfile \
  -t "task-management-backend:${IMAGE_TAG}" \
  .

docker build \
  -f task-management-frontend/Dockerfile \
  -t "task-management-frontend:${IMAGE_TAG}" \
  .

echo "==> Starting containers"

docker compose down --remove-orphans
docker compose up -d

echo ""
echo "  Backend:  http://localhost:8080"
echo "  Frontend: http://localhost:4200"
echo ""
echo "  Logs:     docker compose logs -f"
echo "  Stop:     docker compose down"
