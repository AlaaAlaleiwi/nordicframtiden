# Critical Fixes Roadmap

This roadmap turns the backend review into an ordered remediation plan. Address P0 items before production deployment. Each fix should follow red-green-refactor and include authorization, failure-path, and regression tests appropriate to the risk.

## Status Summary

| Priority | Work item | Status |
| --- | --- | --- |
| P0 | Rotate and externalize database/JWT secrets | Repository mitigation complete; external rotation pending |
| P0 | Close user and admin privilege escalation | Complete |
| P0 | Enforce salary ownership and reporting authorization | Complete |
| P0 | Protect SMTP credentials | Repository mitigation complete; deployment secret pending |
| P0 | Resolve cookie authentication and CSRF protection | Core mitigation complete; revocation/rate limiting pending |
| P1 | Isolate and expand the automated test suite | Not started |
| P1 | Correct and freeze payroll/tax business rules | Not started |
| P1 | Replace plaintext password delivery | Not started |
| P1 | Activate validation and consistent API errors | Not started |
| P1 | Limit PDF, email, and reporting abuse | Not started |
| P1 | Add database integrity and concurrency controls | Not started |

## P0.1 Rotate and Externalize Secrets

### Repository changes completed

- `application.yml` now requires `APP_JWT_SECRET` and all three `SPRING_DATASOURCE_*` variables.
- Docker Compose now fails when required database/JWT variables are missing.
- The bundled PostgreSQL password is required instead of committed.
- The local PostgreSQL port binds to loopback by default.
- `.env.example` documents configuration with blank secret values.

### External actions still required

1. Rotate the exposed database credential at the database provider.
2. Generate a new random JWT signing key with at least 32 bytes of entropy.
3. Store secrets independently in each deployment environment's secret manager.
4. Invalidate active sessions after changing the JWT key.
5. Remove exposed values from Git history if repository access warrants it.
6. Add secret scanning to local hooks and CI.

### Acceptance criteria

- No usable database, JWT, SMTP, or other secret exists in tracked files or Git history.
- The application fails startup when required secrets are absent or invalid.
- Development, test, staging, and production use distinct credentials.
- Deployment documentation identifies the secret owner and rotation process.

## P0.2 Close Privilege Escalation

### Completed

- Administrator endpoints now require the `ADMIN` role at both URL and method/service boundaries.
- Ordinary users can access only their own `/api/users/me` profile operations.
- Self-profile updates use a dedicated DTO that cannot change enabled state, hourly cost, roles, or permissions.
- Staff require `PEOPLE` to manage ordinary `USER` accounts.
- Staff cannot list, create, read, or update `STAFF` or `ADMIN` accounts through user management.
- Password reset and account deletion require `ADMIN`.
- Security regression tests cover the primary USER, STAFF/PEOPLE, and ADMIN boundaries.

Define an explicit authorization matrix before editing endpoints:

- `USER` can read and update only approved `/me` resources.
- `STAFF` actions require explicit permissions; a role alone does not grant user/admin management.
- `ADMIN` owns account, role, permission, and system configuration management.
- Services enforce high-risk authorization so alternate entry points cannot bypass controllers.

Add MockMvc security tests for unauthenticated, USER, STAFF with and without each permission, and ADMIN callers. Cover attempts to create staff/admin accounts, update roles/permissions, disable users, reset passwords, and access another user.

## P0.3 Protect Salary and Employee Data

### Completed

- `/api/salaries/payslip/me` derives the user ID from the authenticated username and does not accept a caller-selected user ID.
- All arbitrary-user payslip/history endpoints, company summaries, reports, and salary-email operations require `ADMIN` or `PERM_SALARIES` at both URL and controller-method boundaries.
- Salary PDF delivery resolves the recipient email and employee name from the stored employee profile; the API no longer accepts either value from the caller.
- The frontend no longer asks for or submits a salary-email recipient and displays the server-resolved recipient after delivery.
- Security regression tests cover self access, cross-user denial, report denial, staff with and without `PERM_SALARIES`, and server-owned email recipients.

### Follow-up

- Add audit events for payslip access, generation, and delivery as part of the observability work.

Tests must demonstrate that one user cannot access or send another user's payroll data.

## P0.4 Protect SMTP Configuration

### Repository changes completed

- SMTP passwords are read only from the `MAIL_PASSWORD` deployment environment variable.
- Public settings responses omit the password and return only `passwordConfigured`.
- Settings reads and changes require `ADMIN` at both URL and controller-method boundaries.
- The settings request and frontend no longer accept, display, or submit an SMTP password.
- Flyway migration `V20` deletes legacy `mail.password` values from `app_setting`.
- Security and service tests cover staff denial, response secrecy, environment-only runtime credentials, and prevention of database password writes.

### Deployment action required

1. Rotate the SMTP password because the previous value may have been stored or exposed.
2. Store the new value as `MAIL_PASSWORD` in the deployment secret manager.
3. Restart the application after changing SMTP credentials or mail connection settings; the shared mail sender is created at startup.
4. Confirm delivery in each environment without logging credential values.

## P0.5 Resolve CSRF and Token Lifecycle

### Core mitigation completed

- Authentication now uses one model: bearer-only access tokens in the `Authorization` header.
- The backend no longer creates or accepts access/refresh cookies, and the refresh-token endpoint was removed.
- CSRF remains disabled because browsers cannot attach bearer authentication ambiently to cross-site requests.
- Access JWTs carry an explicit `type=access` claim; tokens with another type are rejected.
- Every authenticated request reloads the account and its current roles/permissions, rejecting disabled or deleted accounts and stale privilege claims.
- CORS credentials and `Set-Cookie` exposure were disabled.
- The frontend stores the access token in `sessionStorage`, clears the former persistent token, and removes it on logout.
- The default access-token lifetime was reduced from 60 to 15 minutes.
- Regression tests cover cookie rejection, cookie-free login/logout, token type enforcement, disabled accounts, and current database authorities.

### Remaining hardening

1. Add distributed authentication rate limiting.
2. Add server-side emergency token revocation if immediate invalidation before the 15-minute expiry is required.

## P1.1 Establish a Safe Test Foundation

- Add PostgreSQL Testcontainers and a test-only Spring profile.
- Guarantee tests never use shared or remote databases.
- Configure Surefire for `*Test` and Failsafe for `*IT`.
- Enable an isolated context smoke test.
- Add `spring-security-test` and role/permission matrix tests.
- Require `./mvnw verify` in CI before container packaging.
- Pin Java 25 consistently in development and CI.

## P1.2 Correct Payroll and Tax Rules

Confirm the business rules with a payroll owner, then add tests before changing implementation:

- Use the hourly-rate snapshot associated with each historical shift.
- Add snapshots for staff shifts if historical rates must remain stable.
- Use the agreed Swedish business timezone, including DST boundaries.
- Prevent accidental mixing or double-counting of USER and STAFF shifts.
- Include the required `days_count` dimension in tax-table lookup.
- Confirm tax-column age rules for each tax year.
- Define rounding at every calculation boundary.

## P1.3 Replace Plaintext Password Delivery

Use short-lived, single-use setup/reset tokens. Store only token hashes, expire and invalidate tokens after use, rate-limit requests, and return generic responses. Do not return or email generated plaintext passwords.

## P1.4 Validation and API Errors

- Add `@Valid` to validated request bodies.
- Add size, range, positivity, enum, and email constraints.
- Replace generic exception maps with stable `ProblemDetail` responses.
- Return appropriate `400`, `401`, `403`, `404`, `409`, and `422` statuses.
- Do not expose internal exception messages in production.

## P1.5 Resource and Abuse Limits

- Cap request and decoded PDF size.
- Validate PDF type and content before sending.
- Rate-limit authentication, reset, contact, PDF, and mail endpoints.
- Bound report date ranges and add pagination.
- Move mail delivery to a bounded asynchronous worker with controlled retries.

## P1.6 Database Integrity

Add PostgreSQL constraints and tests for valid time ranges, non-negative monetary values, required uniqueness, delete behavior, and any forbidden shift overlap. Add optimistic locking where concurrent updates could overwrite schedules or profiles. Review migrations that inserted synthetic municipality and birth-year data before using those values for payroll.

## Required Verification for Every Fix

1. Observe a focused regression/security test fail for the intended reason.
2. Implement the narrowest fix.
3. Run the focused test and its enclosing test layer.
4. Run isolated integration tests with PostgreSQL/Flyway when persistence changes.
5. Run `./mvnw verify` and `git diff --check` before merging.
6. Record skipped checks and residual risk explicitly.
