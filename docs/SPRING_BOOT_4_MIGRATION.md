# Spring Boot 4 migration

The application was upgraded from Spring Boot 3.5.7 to 3.5.16, then migrated to Spring Boot 4.1.1 on Java 21.

## Current status

The local Spring Boot 4.1.1 migration is complete. The application uses Boot 4's focused MVC, REST client, and test starters; application JSON handling uses Jackson 3; and Redis caching uses the Jackson 3 serializer. On Java 21, `mvn clean package` rebuilds the React production bundle, runs all 25 backend tests (including the full application context), and packages the executable JAR successfully. Tests use an isolated in-memory H2 database, mock Redis in the application context, and load Mockito through an explicit Java agent.

## Migration sequence

1. Establish a reliable test baseline for Spring Boot 3.5.16.
   - [x] Give tests an isolated H2 test configuration.
   - [x] Configure Mockito's Java agent explicitly instead of relying on runtime self-attachment.
   - [x] Run the complete backend test suite and the React production build locally.
   - [x] Add the same Java 21 clean-package command to GitHub Actions CI.
   - [x] Confirm the first GitHub Actions run succeeds on `main`.

2. Prepare the dependencies for Spring Boot 4.
   - [x] Replace `spring-boot-starter-web` with `spring-boot-starter-webmvc` and add the focused REST client starter.
   - [x] Replace the direct security test dependency with Boot 4's MVC and security test starters.
   - [x] Compile and run the application context against Spring Framework 7, Spring Security 7, Hibernate 7, and Jakarta EE 11.
   - [x] Run `spring-boot-properties-migrator`, confirm no property migrations are reported, and remove it.

3. Migrate JSON handling.
   - [x] Inventory Jackson 2 usage in the Netatmo, Salesforce, API, and Redis cache code.
   - [x] Migrate application-owned databind/core usage to Jackson 3 without the deprecated Boot compatibility module.
   - [x] Keep the compatible `com.fasterxml.jackson.annotation` annotations.
   - [x] Migrate Redis caches to `GenericJacksonJsonRedisSerializer`.
   - [ ] Verify live API payloads, token responses, and Redis serialization in the Heroku environment.

4. Update framework APIs.
   - [x] Replace Spring's legacy nullability annotations with JSpecify annotations.
   - [x] Compile and exercise the JWT security filter chain against Spring Security 7 through the application-context test.
   - [x] Replace APIs removed by Spring Boot 4, including the moved Lettuce customizer and Jackson 2 Redis serializer.

5. Next: verify persistence and infrastructure.
   - Test PostgreSQL schema updates and repository behavior with Hibernate 7.
   - Test Redis repositories and cache serialization with existing production-shaped data.
   - Verify Actuator health, liveness, and readiness endpoints.
   - Confirm the generated executable JAR starts with Heroku's Java 21 runtime and binds to the assigned port.

6. Then deploy safely.
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
