# Modular architecture

The backend is a modular monolith. It remains one Spring Boot application and one
Heroku deployment, while Spring Modulith verifies the boundaries between its
business capabilities during every test run.

## Modules

| Module | Responsibility | Direct dependencies |
| --- | --- | --- |
| `foundation` | Shared application and cache configuration | None |
| `identity` | Users, JWT authentication, and API security | `foundation` |
| `operations` | Persistent application messages and Slack delivery | None |
| `weather` | Netatmo OAuth, API access, token management, and weather measurements | `foundation`, `identity`, `operations` |
| `datacloud` | Salesforce authentication, queries, and Data Cloud ingestion | `operations`, `weather` |
| `automation` | Scheduled orchestration of weather collection and Data Cloud ingestion | `datacloud`, `operations`, `weather` |

The intended dependency flow is acyclic:

```text
automation --> datacloud --> weather --> identity --> foundation
     |             |           |
     +-------------+-----------+--> operations
```

## Package rules

Each direct package below `net.pautet.softs.demospring` is an application module.
Types in a module's root package form its public API. Controllers, persistence,
configuration, vendor response models, and other implementation details belong
under that module's `internal` package and cannot be referenced by another module.

Add new functionality to the capability that owns it rather than recreating
technical top-level packages such as `controller`, `service`, `entity`, or
`repository`. A future trading implementation should begin as its own `trading`
module with an intentionally small root-package API.

## Verification

`ModularityTest` calls `ApplicationModules.verify()` and rejects cycles or access
to another module's internal types. `OperationsModuleTest` uses
`@ApplicationModuleTest` to prove that the operations capability boots in
isolation. Both run as part of the normal Maven build:

```bash
mvn clean verify
```

Module interactions are synchronous for now. Introduce Spring application events
only when a real workflow benefits from decoupling; the architecture does not use
the persistent event publication registry or add event tables to PostgreSQL.
