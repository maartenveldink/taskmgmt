# Task Management PoC

Task management app built with a Quarkus/Axon backend and an Angular frontend.

## Prerequisites

- Java 21
- Maven 3.9+
- Node.js 20+ with npm
- Docker and Docker Compose (optional, for full-stack local runs)

## Repository layout

- `task-management-backend/` — Quarkus + Axon backend
- `task-management-frontend/` — Angular frontend
- `docs/` — architecture and feature documentation

## Quick start

1. Clone the repository.
2. Install frontend dependencies:
   ```bash
   cd task-management-frontend
   npm install
   ```
3. Start the backend in dev mode:
   ```bash
   cd task-management-backend
   mvn quarkus:dev
   ```
4. Start the frontend in a second terminal:
   ```bash
   cd task-management-frontend
   npm start
   ```

The frontend runs on `http://localhost:4200` and the backend on `http://localhost:8080`.

## Build and test

- Backend + frontend tests:
  ```bash
  mvn test
  ```
- Backend only:
  ```bash
  cd task-management-backend
  mvn test
  ```
- Frontend only:
  ```bash
  cd task-management-frontend
  npm test
  ```

## Docker

Build and start both containers:

```bash
./build-run.sh
```

This builds both images and starts them with Docker Compose.

## Documentation

- `docs/README.md` — documentation index
- `docs/architecture.md` — C4-style architecture overview
- `docs/features/user-provisioning.md` — external user provisioning feature
