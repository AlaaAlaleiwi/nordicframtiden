---
name: nordicframtiden-backend
description: Implement or review changes in the Nordicframtiden Spring Boot backend using its project-specific architecture, security safeguards, database practices, and test-driven workflow. Use for Java backend features, bug fixes, APIs, services, security, persistence, Flyway migrations, and tests in this repository.
---

# Nordicframtiden Backend

Work within the user's requested scope and preserve unrelated changes. Inspect the relevant production code, tests, configuration, and migrations before editing.

For detailed project conventions, priorities, examples, and the Definition of Done, read [BEST_PRACTICES_AND_TDD.md](../../BEST_PRACTICES_AND_TDD.md) when the task involves implementation, design, review, or testing.

## Protect External Systems and Secrets

The default application configuration may point to a remote PostgreSQL database. Before running any Spring context or integration test, prove that it uses disposable infrastructure. Prefer PostgreSQL Testcontainers or an explicitly isolated test datasource. Never let a test write to a shared, staging, or production database.

Do not expose secret values in output. If tracked credentials are relevant to the task, identify their file locations without repeating their values and recommend immediate rotation. Do not rotate, delete, or rewrite Git history unless the user requests it.

Unit tests must mock or fake mail, Redis, and other external adapters. Do not send real mail or depend on developer-machine services.

## Use TDD for Behavior Changes

For each behavior change:

1. Add a focused test that fails for the intended behavioral reason.
2. Make the smallest production change that passes it.
3. Refactor while keeping the focused test green.
4. Run the relevant enclosing test layer.

Match the test to the change:

- Use JUnit 5, AssertJ, and Mockito for domain and service behavior.
- Use `@WebMvcTest`, MockMvc, and Spring Security test support for JSON contracts, validation, status codes, roles, and ownership.
- Use PostgreSQL Testcontainers for JPA queries, constraints, Flyway, and application integration.
- Use `*Test` for Surefire tests and `*IT` only when Maven Failsafe is configured to run them during `verify`.

Prioritize boundary coverage for payroll arithmetic, Swedish business-time rules, rounding, schedule ranges, authorization, uniqueness, password flows, JWT validation, and migration compatibility.

## Preserve Application Boundaries

- Keep controllers focused on HTTP translation and delegate use cases to services.
- Keep transaction boundaries in application services; mark read operations `readOnly = true`.
- Use validated request/response DTOs and add `@Valid` where Bean Validation must run.
- Do not expose JPA entities, password hashes, JWTs, raw secrets, or internal exceptions through APIs.
- Prefer domain-specific exceptions and consistent `ProblemDetail` responses over generic `IllegalArgumentException` maps.
- Enforce invariants in both application logic and PostgreSQL constraints where concurrency matters.
- Add a new immutable Flyway migration for schema changes; do not edit an already-applied migration.
- Use `BigDecimal` with explicit rounding for money, an injected `Clock` for current time, and an explicit business timezone for calendar rules.
- Use structured SLF4J logging. Never log credentials, tokens, password reset values, or unnecessary personal data.

## Verify Safely

Check the toolchain before Java verification:

```bash
java -version
./mvnw -version
```

This project targets Java 25. If Maven runs on an older JDK, report the mismatch instead of treating class-version failures as product failures. Do not use stale compiled files to claim success.

Run the narrowest safe check first, followed by broader checks proportional to the change:

```bash
./mvnw -Dtest=RelevantTest test
./mvnw test
./mvnw verify
git diff --check
```

Do not run database-backed tests until isolation is established. For documentation-only changes, validate formatting, links or references, and diff cleanliness; Java tests are unnecessary unless the documentation change affects executable examples or build behavior.

Before finishing, report exactly which checks passed, failed, or were skipped and why. Call out residual risk without overstating verification.
