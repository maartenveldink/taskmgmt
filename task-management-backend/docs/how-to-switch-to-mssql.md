# How-To: Switch from H2 to Microsoft SQL Server

This guide describes how to switch the Task Management backend from the default H2 in-memory database to Microsoft SQL Server.

## Prerequisites

- Microsoft SQL Server instance (2019 or later recommended)
- Database created (e.g., `taskdb`)
- SQL Server credentials with sufficient permissions
- Docker (optional, for local development)

## Step 1: Start a MSSQL Instance (Optional - Local Development)

For local development, you can use Docker to run SQL Server:

```bash
docker run -e "ACCEPT_EULA=Y" \
  -e "SA_PASSWORD=YourStrong!Passw0rd" \
  -e "MSSQL_PID=Developer" \
  -p 1433:1433 \
  --name task-management-mssql \
  -d mcr.microsoft.com/mssql/server:2022-latest
```

Create the database:

```bash
docker exec -it task-management-mssql /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "YourStrong!Passw0rd" -C \
  -Q "CREATE DATABASE taskdb;"
```

## Step 2: Update Maven Dependencies

In `task-management-backend/pom.xml`, swap the JDBC drivers:

```xml
<!-- Comment out (or remove) H2 -->
<!--
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-h2</artifactId>
</dependency>
-->

<!-- Uncomment MSSQL -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-jdbc-mssql</artifactId>
</dependency>
```

## Step 3: Update Application Configuration

In `task-management-backend/src/main/resources/application.properties`, update the datasource and Axon configuration:

### Before (H2):
```properties
quarkus.datasource.db-kind=h2
quarkus.datasource.jdbc.url=jdbc:h2:mem:taskdb;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE
axon.event-store.sql-error-codes.database-product-name=H2
```

### After (MSSQL):
```properties
quarkus.datasource.db-kind=mssql
quarkus.datasource.jdbc.url=jdbc:sqlserver://localhost:1433;databaseName=taskdb;encrypt=false;trustServerCertificate=true
quarkus.datasource.username=sa
quarkus.datasource.password=YourStrong!Passw0rd
axon.event-store.sql-error-codes.database-product-name=Microsoft SQL Server
```

### Connection String Options

| Option | Description |
|--------|-------------|
| `encrypt=true` | Enable TLS encryption (recommended for production) |
| `trustServerCertificate=true` | Trust self-signed certificates (development only) |
| `loginTimeout=30` | Connection timeout in seconds |
| `integratedSecurity=true` | Use Windows Authentication (instead of username/password) |

### Production Example:
```properties
quarkus.datasource.jdbc.url=jdbc:sqlserver://sql-server.example.com:1433;databaseName=taskdb;encrypt=true
quarkus.datasource.username=${DB_USERNAME}
quarkus.datasource.password=${DB_PASSWORD}
```

## Step 4: Update Hibernate Dialect

In `task-management-backend/src/main/resources/META-INF/persistence.xml`, change the Hibernate dialect:

### Before (H2):
```xml
<property name="hibernate.dialect"
          value="org.hibernate.dialect.H2Dialect"/>
```

### After (MSSQL):
```xml
<property name="hibernate.dialect"
          value="org.hibernate.dialect.SQLServerDialect"/>
```

> **Note:** Quarkus requires an explicit dialect at build-time. Property substitution is not supported in persistence.xml.

## Step 5: Rebuild and Run

```bash
cd task-management-backend
mvn clean compile quarkus:dev
```

Flyway will automatically:
1. Load migrations from `db/migration/common/` (shared migrations)
2. Load migrations from `db/migration/mssql/` (MSSQL-specific migrations)
3. Create the required tables (`task_view`, `audit_trail`)

Hibernate will automatically:
1. Use the configured `SQLServerDialect`
2. Create Axon tables (`DomainEventEntry`, `SnapshotEventEntry`, `SagaEntry`, `AssociationValueEntry`)

## Step 6: Verify the Setup

Check that all tables are created:

```bash
docker exec -it task-management-mssql /opt/mssql-tools18/bin/sqlcmd \
  -S localhost -U sa -P "YourStrong!Passw0rd" -C \
  -Q "USE taskdb; SELECT name FROM sys.tables ORDER BY name;"
```

Expected tables:
- `AssociationValueEntry` (Axon saga associations)
- `audit_trail` (application audit log)
- `DomainEventEntry` (Axon event store)
- `flyway_schema_history` (Flyway metadata)
- `SagaEntry` (Axon saga state)
- `SnapshotEventEntry` (Axon snapshots)
- `task_view` (application read model)

## Docker Compose (Production-like Setup)

Add MSSQL to `docker-compose.yml`:

```yaml
services:
  mssql:
    image: mcr.microsoft.com/mssql/server:2022-latest
    container_name: task-management-mssql
    environment:
      - ACCEPT_EULA=Y
      - SA_PASSWORD=YourStrong!Passw0rd
      - MSSQL_PID=Developer
    ports:
      - "1433:1433"
    volumes:
      - mssql-data:/var/opt/mssql
    networks:
      - task-management
    healthcheck:
      test: /opt/mssql-tools18/bin/sqlcmd -S localhost -U sa -P "$$SA_PASSWORD" -C -Q "SELECT 1"
      interval: 10s
      timeout: 5s
      retries: 10

  backend:
    image: task-management-backend:${IMAGE_TAG}
    container_name: task-management-backend
    environment:
      - QUARKUS_DATASOURCE_DB_KIND=mssql
      - QUARKUS_DATASOURCE_JDBC_URL=jdbc:sqlserver://mssql:1433;databaseName=taskdb;encrypt=false;trustServerCertificate=true
      - QUARKUS_DATASOURCE_USERNAME=sa
      - QUARKUS_DATASOURCE_PASSWORD=YourStrong!Passw0rd
      - AXON_EVENT_STORE_SQL_ERROR_CODES_DATABASE_PRODUCT_NAME=Microsoft SQL Server
    ports:
      - "8080:8080"
    depends_on:
      mssql:
        condition: service_healthy
    networks:
      - task-management

  # ... frontend service ...

volumes:
  mssql-data:

networks:
  task-management:
    driver: bridge
```

## Configuration Summary

| File | Property | H2 Value | MSSQL Value |
|------|----------|----------|-------------|
| `application.properties` | `quarkus.datasource.db-kind` | `h2` | `mssql` |
| `application.properties` | `quarkus.datasource.jdbc.url` | `jdbc:h2:mem:taskdb;...` | `jdbc:sqlserver://host:1433;...` |
| `application.properties` | `quarkus.datasource.username` | _(not needed)_ | `sa` or service account |
| `application.properties` | `quarkus.datasource.password` | _(not needed)_ | _(your password)_ |
| `application.properties` | `axon.event-store...database-product-name` | `H2` | `Microsoft SQL Server` |
| `persistence.xml` | `hibernate.dialect` | `org.hibernate.dialect.H2Dialect` | `org.hibernate.dialect.SQLServerDialect` |

## Troubleshooting

### "Login failed for user"
- Verify username and password
- Check that SQL Server authentication is enabled (not just Windows Auth)

### "Cannot open database"
- Ensure the database exists: `CREATE DATABASE taskdb;`
- Check user permissions: `ALTER ROLE db_owner ADD MEMBER your_user;`

### "SSL/TLS handshake failed"
- For development: add `encrypt=false;trustServerCertificate=true`
- For production: configure proper TLS certificates

### Flyway migration errors
- Check that migration files exist in `db/migration/mssql/`
- Verify versioning matches between H2 and MSSQL folders
- Clear Flyway history if needed: `DELETE FROM flyway_schema_history;`

## Rolling Back to H2

To switch back to H2:
1. Revert the changes in `pom.xml` (uncomment H2, comment MSSQL)
2. Revert the changes in `application.properties`
3. Revert the dialect in `persistence.xml` to `org.hibernate.dialect.H2Dialect`
4. Rebuild: `mvn clean compile`

---

_Last updated: July 2026_

