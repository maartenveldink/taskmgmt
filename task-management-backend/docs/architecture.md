# Architecture

This project uses a CQRS/Event Sourcing backend with a thin HTTP boundary.

## C4 Context

```mermaid
flowchart LR
    classDef person fill:#fff,stroke:#333,stroke-width:1px;
    classDef system fill:#eef6ff,stroke:#2563eb,stroke-width:1px;
    classDef external fill:#fff7ed,stroke:#f97316,stroke-width:1px;

    user[Task user / admin]:::person
    dev[Developer]:::person
    ui[Angular frontend]:::system
    backend[Quarkus backend]:::system
    directory[External user directory]:::external

    user --> ui
    dev --> backend
    ui --> backend
    backend --> directory
```

## C4 Containers

```mermaid
flowchart LR
    classDef container fill:#eef6ff,stroke:#2563eb,stroke-width:1px;
    classDef datastore fill:#ecfccb,stroke:#16a34a,stroke-width:1px;
    classDef external fill:#fff7ed,stroke:#f97316,stroke-width:1px;

    frontend[task-management-frontend\nAngular SPA]:::container
    api[task-management-backend\nQuarkus + Axon API]:::container
    db[(H2 / JPA event store + read model)]:::datastore
    directory[External user directory]:::external

    frontend --> api
    api --> db
    api --> directory
```

## C4 Backend components

```mermaid
flowchart LR
    classDef boundary fill:#f8fafc,stroke:#64748b,stroke-width:1px;
    classDef app fill:#dbeafe,stroke:#2563eb,stroke-width:1px;
    classDef domain fill:#dcfce7,stroke:#16a34a,stroke-width:1px;
    classDef infra fill:#fff7ed,stroke:#f97316,stroke-width:1px;

    subgraph http[HTTP boundary]
        resource[TasksHttpResource]:::boundary
        mapper[TasksHttpMapper]:::boundary
    end

    subgraph application[Application layer]
        cmd[TaskCommandApplicationService]:::app
        qry[TaskQueryApplicationService]:::app
    end

    subgraph domain[Domain layer]
        aggregate[TaskAggregate]:::domain
        proj[TaskProjection]:::domain
        saga1[TaskDeadlineSaga]:::domain
        saga2[UserProvisioningCompletionSaga]:::domain
    end

    subgraph infra[Infrastructure]
        directory[HttpExternalUserDirectoryClient]:::infra
        eventstore[(JPA event store / saga store)]:::infra
        readmodel[(TaskView / audit trail tables)]:::infra
    end

    resource --> mapper
    resource --> cmd
    resource --> qry
    cmd --> aggregate
    aggregate --> eventstore
    proj --> readmodel
    saga1 --> eventstore
    saga2 --> directory
    saga2 --> cmd
    qry --> readmodel
```

## Roles

- **Task user / admin**: works through the Angular UI.
- **Developer**: runs backend/frontend locally, changes code, and uses the docs.
- **External system**: user directory queried by the provisioning saga.
