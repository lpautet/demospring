# Spring Boot 4 migration plan

The application was upgraded from Spring Boot 3.5.7 to 3.5.16 before beginning the Spring Boot 4 migration. The target for the next migration is Spring Boot 4.1.1 on Java 21.

## Current status

The Spring Boot 3.5.16 baseline is complete. On Java 21, `mvn clean package` builds the React production bundle, runs all 25 backend tests, and packages the executable JAR successfully. Tests use an isolated in-memory H2 database, mock Redis in the application context, and load Mockito through an explicit Java agent.

## Migration sequence

1. Establish a reliable test baseline for Spring Boot 3.5.16.
   - [x] Give tests an isolated H2 test configuration.
   - [x] Configure Mockito's Java agent explicitly instead of relying on runtime self-attachment.
   - [x] Run the complete backend test suite and the React production build locally.
   - [x] Add the same Java 21 clean-package command to GitHub Actions CI.
   - [ ] Confirm the first GitHub Actions run succeeds on `main`.

2. Prepare the dependencies for Spring Boot 4.
   - Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc`.
   - Replace the generic and direct security test dependencies with the relevant Boot 4 test starters.
   - Review explicit dependency versions and confirm compatibility with Spring Framework 7, Spring Security 7, Hibernate 7, and Jakarta EE 11.
   - Temporarily add `spring-boot-properties-migrator` during the migration, then remove it when configuration updates are complete.

3. Migrate JSON handling.
   - Inventory the Jackson 2 usage in the Netatmo, Salesforce, API, and Redis cache code.
   - Prefer migrating `com.fasterxml.jackson.databind` usage to Jackson 3 rather than relying permanently on Boot's deprecated Jackson 2 compatibility module.
   - Keep the compatible `com.fasterxml.jackson.annotation` annotations where appropriate.
   - Verify API payloads, token responses, cached values, and Redis serialization before deployment.

4. Update framework APIs.
   - Replace Spring's legacy nullability annotations with JSpecify annotations where required.
   - Review the JWT security filter chain against Spring Security 7.
   - Compile with deprecation warnings enabled and replace APIs removed by Spring Boot 4 or Spring Framework 7.

5. Verify persistence and infrastructure.
   - Test PostgreSQL schema updates and repository behavior with Hibernate 7.
   - Test Redis repositories and cache serialization with existing production-shaped data.
   - Verify Actuator health, liveness, and readiness endpoints.
   - Confirm the generated executable JAR starts with Heroku's Java 21 runtime and binds to the assigned port.

6. Deploy safely.
   - Deploy Spring Boot 4.1.1 to a staging Heroku app with separate PostgreSQL and Redis instances.
   - Exercise authentication, Netatmo OAuth and data retrieval, Salesforce ingestion, dashboard loading, scheduled tasks, and Slack log forwarding.
   - Review startup logs, health checks, database changes, memory usage, and response latency.
   - Prepare a rollback to the last Spring Boot 3.5.16 release before promoting to production.

## Completion criteria

- The clean Maven package and complete automated test suite pass on Java 21.
- No deprecated Boot 3 compatibility modules remain.
- Existing Redis and PostgreSQL data are readable or have an explicit migration.
- The staging smoke-test checklist passes without regressions.
- Production deployment and rollback procedures have been tested.
