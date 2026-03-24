# =============================================================================
# Multi-stage build for Task Management PoC — Quarkus backend
#
# Build context must be the PARENT directory (taskmgmt/), not this module:
#
#   docker build -f task-management-backend/Dockerfile -t task-management .
#   docker run --rm -p 8080:8080 task-management
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1 — build
# -----------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21-alpine AS build

WORKDIR /build

# Copy parent pom first (needed by the child module)
COPY pom.xml ./pom.xml

# Copy module pom separately to allow Docker layer caching of dependencies
COPY task-management-backend/pom.xml ./task-management-backend/pom.xml

# Download dependencies before copying source (cached unless pom changes)
RUN mvn -pl task-management-backend -am dependency:go-offline -q

# Copy source and build
COPY task-management-backend/src ./task-management-backend/src
RUN mvn -pl task-management-backend -am package -DskipTests -q

# -----------------------------------------------------------------------------
# Stage 2 — runtime
# -----------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine

WORKDIR /deployments

# Run as non-root
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

COPY --from=build --chown=appuser:appgroup /build/task-management-backend/target/quarkus-app/lib/ ./lib/
COPY --from=build --chown=appuser:appgroup /build/task-management-backend/target/quarkus-app/*.jar ./
COPY --from=build --chown=appuser:appgroup /build/task-management-backend/target/quarkus-app/app/ ./app/
COPY --from=build --chown=appuser:appgroup /build/task-management-backend/target/quarkus-app/quarkus/ ./quarkus/

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
