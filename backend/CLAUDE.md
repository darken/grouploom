# Backend

Spring Boot 4.x, Java 25, Gradle, package `io.grouploom.backend`.

## Commands
```bash
./gradlew bootRun                     # run the app (starts compose.yaml Postgres; needs Docker)
./gradlew build                       # compile + test
./gradlew test                        # all tests (Testcontainers; needs Docker)
./gradlew test --tests 'io.grouploom.backend.BackendApplicationTests'   # single test class
./gradlew test --tests '*SomeTest.someMethod'                           # single method
```

## Architecture
- Layers: `controller` → `service` → `repository`. Controllers never
  touch repositories; services never return entities to controllers.
- Package by feature (`io.grouploom.backend.group`, `.member`, …), each with
  its own controller/service/repository/dto subpackages. Cross-cutting
  configuration lives in `io.grouploom.backend.config`.
- `spring.mvc.servlet.path` is `/api`, so controller mappings must **not**
  include it: `@RequestMapping("/groups")` is served at `/api/groups`.
  Security request matchers are relative to the servlet path too.
- Entities are never exposed in the API. Use DTOs (records) with Bean
  Validation annotations; map explicitly (or MapStruct, if adopted).
- Constructor injection only; no field `@Autowired`.
- Transactions at the service layer: `@Transactional` on writes,
  `readOnly = true` on reads.
- Errors: one `@RestControllerAdvice` returning RFC 9457 `ProblemDetail`.
  Throw domain exceptions from services; don't catch-and-swallow.
- Paginated list endpoints use `Pageable`; never return unbounded lists.
- Config is `src/main/resources/application.yaml`. springdoc serves the spec at
  `/api/v3/api-docs` and Swagger UI at `/api/swagger-ui/index.html`.

## Database
- PostgreSQL 17. `spring-boot-docker-compose` (developmentOnly) starts
  `compose.yaml` automatically on `bootRun`. Postgres is exposed on a random
  host port and Spring wires the connection; don't hardcode a datasource URL
  for local dev.
- Schema is managed by Flyway; don't rely on Hibernate DDL auto-generation.
  Migrations go in `src/main/resources/db/migration`, are forward-only, and
  are named `V<n>__snake_case_description.sql`. Add indexes and constraints
  (FKs, NOT NULL, unique) in SQL, not just in JPA.
- Watch for N+1 queries: use `@EntityGraph` or fetch joins for known
  access patterns. Prefer lazy associations.

## Security
- `config/SecurityConfig` is a placeholder: the OpenAPI docs and Swagger UI
  are public (so `pnpm gen:api` works) and everything else uses the default
  form/basic login.
- JWT auth for users is planned. It needs a new dependency (e.g. the OAuth2
  resource server starter), so ask before adding it.
- Member endpoints (preferences, attendance confirmation) are reached from
  email links: `permitAll` at the URL level, authorized by validating the
  link token in the service.
- Don't weaken security config (CSRF, CORS, auth rules) to make a test or
  the frontend work; ask first.
- Authorization checks live in services or method security, not just URL rules.

## Testing
- Unit: JUnit 5 + Mockito for services. Web layer: `@WebMvcTest` +
  MockMvc. Persistence/integration: `@Import(TestcontainersConfiguration.class)`,
  which starts Postgres 17 via `@ServiceConnection`, so tests don't depend on
  the compose database.
- Name tests `methodName_condition_expectedResult`.
- Every new endpoint gets: happy path, validation failure, and
  unauthorized/forbidden cases.

## Gotchas
- This project is on Spring Boot 4 / Spring Security 7 / Jackson 3.
  Much online example code targets Boot 3 (old security DSL, `com.fasterxml`
  Jackson packages, old starter names). Check current docs when unsure.
- Testcontainers is 2.x: artifacts are `testcontainers-postgresql` /
  `testcontainers-junit-jupiter` and the class is
  `org.testcontainers.postgresql.PostgreSQLContainer`. Older examples using
  `org.testcontainers.containers.*` won't compile.
