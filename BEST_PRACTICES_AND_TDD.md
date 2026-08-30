# Backend Best Practices and TDD Guide

This guide defines the recommended engineering workflow for the Nordicframtiden backend. It is tailored to the current Spring Boot 3.5, Java 25, Maven, PostgreSQL, Flyway, Spring Security, JWT, JPA, Redis, and mail stack.

## 1. Current Baseline and Immediate Priorities

The application already has useful foundations: constructor injection, service and repository layers, DTO records, transactions, Flyway migrations, Bean Validation, and JUnit 5/Mockito support.

Address these risks before expanding the feature set:

| Priority | Current risk | Recommended action |
| --- | --- | --- |
| Critical | Database credentials are committed in `application.yml` and `docker-compose.yml`. | Rotate the exposed credential, remove secrets from tracked files and Git history, and inject them through environment variables or a secret manager. Commit only safe placeholders. |
| Critical | `AdminServiceIT` uses `@SpringBootTest` without an isolated test datasource and can write to the configured remote database. | Use a PostgreSQL Testcontainer or a dedicated disposable test database. Never run integration tests against shared, staging, or production data. |
| High | The JWT secret has a committed fallback value. | Require `APP_JWT_SECRET` outside tests and fail startup when it is absent or too short. Use a separate test-only secret. |
| High | The `*IT` naming convention is not connected to Maven Failsafe. | Configure Failsafe for `verify`, or rename integration tests if they should run under Surefire. Keep unit tests in `test` and integration tests in `verify`. |
| High | The only application context test is disabled. | Replace it with an isolated context smoke test using test configuration and disposable infrastructure. |
| High | The project targets Java 25, but an older local or CI runtime cannot execute Java 25 test classes. | Pin Java 25 consistently in developer setup and CI, and enforce it with Maven Enforcer or Maven Toolchains. |
| High | Some APIs return newly generated raw passwords. | Prefer a one-time, expiring setup/reset token. Never log credentials, and limit any unavoidable plaintext password to a single protected response. |
| Medium | Validation and error handling rely heavily on `IllegalArgumentException`. | Add `@Valid` to request bodies, introduce domain-specific exceptions, and return a consistent RFC 9457 `ProblemDetail` response with correct status codes. |
| Medium | `System.out.println` and swallowed exceptions exist in application paths. | Use structured SLF4J logging without sensitive values; catch only expected exceptions and preserve the cause or handle it explicitly. |
| Medium | The Docker build skips tests. | Run `./mvnw verify` in CI before image creation. Build the image only from a verified commit/artifact. |

## 2. Definition of Done

A change is complete only when all applicable items are true:

- The behavior and acceptance examples are clear before implementation.
- A failing test demonstrates the missing behavior or defect.
- The smallest production change makes the test pass.
- Tests cover the happy path, boundary cases, invalid input, authorization, and persistence constraints relevant to the change.
- Public APIs use request/response DTOs and do not expose JPA entities, password hashes, tokens, or internal exception details.
- Database changes use a new immutable Flyway migration and are tested against PostgreSQL.
- Logs contain useful context but no credentials, JWTs, password reset values, personal data, or mail secrets.
- `./mvnw test` and `./mvnw verify` pass in an isolated environment.
- Formatting, compilation, and `git diff --check` pass.
- Documentation and configuration examples are updated when behavior or operations change.

## 3. TDD Workflow: Red, Green, Refactor

Use a short loop for every observable behavior.

### Red: describe one behavior with a failing test

Name the test as a sentence describing the result, for example:

```java
@Test
void rejectsShiftWhenEndIsNotAfterStart() {
    assertThatThrownBy(() -> service.create(
            10L,
            20L,
            OffsetDateTime.parse("2026-08-30T10:00:00Z"),
            OffsetDateTime.parse("2026-08-30T09:00:00Z"),
            "invalid"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Invalid time range");
}
```

Confirm that it fails for the expected reason, not because setup or infrastructure is broken:

```bash
./mvnw -Dtest=ScheduleServiceTest#rejectsShiftWhenEndIsNotAfterStart test
```

### Green: implement only what the failing test requires

Keep the patch narrow. Avoid unrelated cleanup while the test is red. Run the focused test until it passes, then run the enclosing test class:

```bash
./mvnw -Dtest=ScheduleServiceTest test
```

### Refactor: improve design while tests stay green

Remove duplication, improve names, isolate pure calculations, and clarify responsibilities. Do not change behavior during this step. Then run the full relevant test layer.

### Repeat and integrate

Add the next boundary or failure case and repeat the loop. Before committing, run:

```bash
./mvnw test
./mvnw verify
git diff --check
```

`verify` should include integration tests once Maven Failsafe is configured.

## 4. Test Strategy for This Application

Prefer many fast unit tests, a focused set of Spring slice tests, and fewer full integration tests.

| Test type | What it should cover here | Recommended tools | Naming |
| --- | --- | --- | --- |
| Pure unit | Payroll arithmetic, weekend multipliers, date ranges, input rules, username generation, service branching | JUnit 5, AssertJ, Mockito only at boundaries | `*Test` |
| Service unit | Repository calls, transactions at the behavioral level, mail interactions, missing entity cases | JUnit 5, Mockito, AssertJ | `*Test` |
| MVC/security slice | JSON contracts, validation, status codes, role rules, unauthenticated behavior | `@WebMvcTest`, MockMvc, Spring Security test support | `*ControllerTest` |
| JPA slice | Queries, mappings, unique constraints, cascade behavior | `@DataJpaTest` with PostgreSQL Testcontainers | `*RepositoryIT` |
| Application integration | Flyway migrations, real repository/service wiring, transaction behavior | `@SpringBootTest` plus PostgreSQL Testcontainers | `*IT` |
| External adapter | Mail configuration and message mapping without sending real email | Mock/fake `JavaMailSender`; optional local SMTP container | `*Test` or `*IT` |

### Unit-test priorities

Start with logic that carries financial, time, security, or state-transition risk:

- `PayrollService`: weekday/Saturday/Sunday rates, shifts crossing midnight, zero/negative duration, rounding, month/year boundaries, missing hourly cost, and USER versus STAFF shift sources.
- `TaxService`: table/column resolution boundaries, missing tax rows, invalid columns, and rounding inputs.
- `ScheduleService` and `StaffScheduleService`: invalid ranges, missing user/pharmacy, updates that create an invalid final range, and hourly-rate snapshots.
- `AvailabilityService`: 62-day boundary, status transitions, authorization/ownership, and inclusive versus exclusive time ranges.
- `AdminService` and `UserService`: uniqueness, roles, password encoding, reset behavior, and mail failure policy.
- `JwtService` and `JwtAuthenticationFilter`: issuer, expiry, malformed tokens, disabled users, and absence of secrets/tokens in logs.

Financial and time calculations should be extracted into small package-private or dedicated domain components. Pure functions are easier to test exhaustively than private methods reached indirectly through a large service.

### Controller and security tests

For each endpoint, test the contract rather than its implementation:

- expected status and JSON body;
- malformed JSON and invalid DTO fields;
- unauthenticated access;
- each allowed and denied role;
- ownership rules for `/me` endpoints;
- stable error shape and status;
- absence of password hashes and other internal fields.

Add `spring-security-test` as a test dependency and use `@WithMockUser` or request post-processors. Use `@Valid` on every validated `@RequestBody`; validation annotations on a record do nothing at the MVC boundary unless validation is triggered.

Example shape:

```java
@WebMvcTest(PharmacyController.class)
class PharmacyControllerTest {

    @Autowired MockMvc mvc;
    @MockBean PharmacyService pharmacyService;

    @Test
    @WithMockUser(roles = "USER")
    void forbidsUserFromListingPharmacies() throws Exception {
        mvc.perform(get("/api/pharmacies"))
            .andExpect(status().isForbidden());
    }
}
```

Import only the security configuration needed by the slice, or provide a documented test security configuration. Do not disable security merely to make controller tests pass.

### Database and Flyway tests

Use the same database engine in tests as production. H2 can accept SQL and type behavior that PostgreSQL rejects.

Recommended pattern:

1. Start a reusable PostgreSQL Testcontainer for the test suite.
2. Supply its JDBC URL, username, and password with `@DynamicPropertySource` or Spring Boot Testcontainers service connections.
3. Let Flyway create the schema from version `V1` on a clean database.
4. Run repository and service assertions.
5. Roll back test data where practical; recreate the container/schema when migration isolation is required.

For every migration:

- add a new `V{next}__descriptive_name.sql`; never edit a migration already applied outside local disposable databases;
- test migration from an empty database and from the previous released schema when the change transforms data;
- include constraints and indexes that enforce domain invariants;
- make destructive changes backward-compatible with the deployed application sequence;
- document any irreversible data transformation and backup requirement.

### External systems

Tests must not depend on Aiven, real SMTP, Redis on a developer machine, or any shared service.

- Mock mail sending in unit tests and verify recipient, subject, and non-sensitive content with an argument captor.
- Use a container or fake server only for adapter integration tests.
- Disable or replace Redis caching in unit/slice profiles; use a Redis container only where cache behavior itself is under test.
- Set timeouts and test failure behavior for every network dependency.

## 5. Maven Test Lifecycle

Keep the phases predictable:

```bash
# Fast tests used throughout development
./mvnw test

# Unit tests plus integration checks and packaging
./mvnw verify

# One test class or method during TDD
./mvnw -Dtest=PayrollServiceTest test
./mvnw -Dtest=PayrollServiceTest#calculatesSundayRate test
```

Before running Maven, confirm that both the launcher and compiler use the project's Java 25 target:

```bash
java -version
./mvnw -version
```

Do not rely on previously compiled files in `target/` when changing JDKs. A class-file-version error means the runtime JDK is older than the JDK that compiled the class; align the toolchain and run a clean build.

Configure Maven Surefire to run `*Test` during `test` and Maven Failsafe to run `*IT` during `integration-test` and `verify`. Until Failsafe is added, a file such as `AdminServiceIT.java` is not guaranteed to run with the normal Maven test command.

CI should run on every pull request:

1. compile;
2. unit and slice tests;
3. PostgreSQL/Flyway integration tests;
4. package verification;
5. dependency and secret scanning;
6. container build after all tests pass.

Never use `-DskipTests` as the CI quality gate. It is acceptable only for a local diagnostic build after a verified artifact already exists.

## 6. Application Design Practices

### Package and layer boundaries

The project currently mixes feature packages (`admin`, `availability`, `pharmacy`) with a shared `api` package. Prefer consistent feature-oriented packages as the code grows:

```text
com.nordicframtiden.pharmacy
├── api
│   ├── PharmacyController
│   └── PharmacyDto
├── application
│   └── PharmacyService
├── domain
│   └── Pharmacy
└── persistence
    └── PharmacyRepository
```

Keep dependencies directed inward:

- controllers translate HTTP to application calls;
- services own use cases and transaction boundaries;
- domain code owns business rules and calculations;
- repositories own persistence access;
- adapters own mail, cache, PDF, and other infrastructure.

Controllers should not query repositories directly. For example, admin statistics should be exposed through an application service so authorization, business rules, and testing have one seam.

### API contracts and validation

- Use immutable request/response records, but place them in named files when reused or substantial.
- Add `@Valid` before validated request bodies.
- Use `@NotNull`, `@Positive`, `@Size`, `@Pattern`, and custom validators where they express the contract better than manual checks.
- Return `201 Created` plus a resource location for creation, `204 No Content` for successful deletion without a body, `404` for missing resources, `409` for uniqueness/state conflicts, and `422` or `400` consistently for invalid business input.
- Use a common `ProblemDetail` response containing a stable error code, safe message, path, and field errors. Do not expose stack traces or database messages.
- Version or preserve backward compatibility for externally consumed contracts.

### Persistence and transactions

- Put transaction boundaries on application service methods, using `readOnly = true` for reads.
- Avoid N+1 queries such as loading a profile separately for every user; use a fetch join, projection, or batch query.
- Enforce uniqueness and referential integrity in PostgreSQL even when services validate first; service checks alone have race conditions.
- Add optimistic locking with `@Version` where concurrent schedule/profile updates can overwrite each other.
- Keep entities out of API responses to prevent lazy-loading surprises and accidental field exposure.
- Define pagination for endpoints that can grow instead of returning every row.

### Time and money

- Use `BigDecimal` for money, with named scales and rounding rules agreed with the business.
- Store instants/timestamps in UTC, but make the business timezone explicit for payroll rules. Swedish weekend and midnight rules may need `Europe/Stockholm`, including daylight-saving transitions, rather than UTC calendar days.
- Inject a `Clock` instead of calling the system clock directly so date-dependent behavior is deterministic in tests.
- Define whether intervals are `[start, end)` and use the same convention in queries, validation, reports, and tests.
- Snapshot values, such as an hourly rate, when historical payroll must not change after a profile update; test that invariant.

### Security and privacy

- Keep secrets out of source code, Docker Compose, logs, test reports, and API responses.
- Rotate any credential that has ever been committed; deleting it from the latest file is not sufficient.
- Keep JWT access tokens short-lived, validate algorithm/issuer/expiry, and define refresh-token rotation and revocation.
- Apply least privilege at both URL and method level, and test both layers.
- Treat `/me` and ID-based endpoints differently: ID-based access requires explicit role or ownership checks.
- Rate-limit login, password reset, contact, and email-sending endpoints.
- Do not reveal whether an account exists in public authentication/reset responses.
- Review CORS origins per environment and never combine wildcard origins with credentials.
- Minimize personal data in logs and establish retention/deletion rules for profiles and contact requests.

### Errors, logging, and observability

- Use SLF4J parameterized logging rather than `System.out`.
- Log a request/correlation ID, operation, safe entity ID, duration, and outcome.
- Do not catch `Exception` and ignore it. Catch an expected type and handle it, or let it propagate to a centralized boundary.
- Publish health/readiness information carefully; do not expose configuration or credentials through Actuator.
- Add metrics for authentication failures, mail failures, request latency, and scheduled/payroll operations without high-cardinality personal labels.

## 7. Pull Request Checklist

- [ ] Acceptance examples are recorded.
- [ ] The first new test was observed failing for the intended reason.
- [ ] Each behavior change has focused automated coverage.
- [ ] Authorization and validation paths are covered.
- [ ] PostgreSQL/Flyway changes are tested on a disposable database.
- [ ] No test uses remote/shared infrastructure.
- [ ] No secret, token, raw password, or personal data was added to code or logs.
- [ ] API and migration compatibility were considered.
- [ ] `./mvnw test` passed.
- [ ] `./mvnw verify` passed.
- [ ] `git diff --check` passed.
- [ ] Operational or API documentation was updated.

## 8. Suggested First TDD Improvements

Apply these as small, separate pull requests:

1. Rotate and externalize all committed secrets; add a safe `application-test.yml`.
2. Add PostgreSQL Testcontainers and Maven Failsafe, then isolate and enable the context/integration tests.
3. Add focused `PayrollServiceTest` coverage and extract the time/pay calculation into a pure component with an explicit business timezone.
4. Add `spring-security-test` and MVC tests for every role-protected controller.
5. Add `@Valid` consistently and replace the generic exception map with typed exceptions plus `ProblemDetail`.
6. Remove direct repository access from controllers, swallowed exceptions, and `System.out` calls under test coverage.
7. Make the Docker/CI pipeline require `./mvnw verify` before packaging or deployment.

The goal is not maximum test count. The goal is fast, deterministic evidence that business rules, security boundaries, database evolution, and API contracts remain correct as the system changes.
