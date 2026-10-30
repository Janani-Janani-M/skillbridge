# Java design and concepts

## Responsibilities

- `App`: JDK `HttpServer`, request dispatch, session/CSRF checks, role authorization, bounded form validation, security headers, redirects, CSV, lifecycle.
- `Database`: JDBC connections, prepared queries, generated-key inserts, transaction commit/rollback, schema initialization and first-run seeding.
- `Auth`: salted PBKDF2-HMAC-SHA-256, constant-time hash comparison, random session/CSRF tokens, expiry, revocation and per-IP throttling.
- `Workflow`: eligibility, application state machine, profile coverage, transactional application/event/notification/audit writes.
- `Probability`: pure Java descriptive and inferential calculations, binomial probability and seeded simulation.
- `Views`: escaped, server-rendered HTML. Browser validation assists but does not replace server-side validation.
- `SelfTest`, `HandlerTest`: standalone suites, no JUnit or external service required.

The code illustrates Java 17 records, generics, functional interfaces, lambdas, streams, `SecureRandom`, `MessageDigest.isEqual`, JDBC resource safety, `HttpServer`, executor concurrency and optimistic versions.

## Apply request walk-through

1. Resolve random session; limit POST content type/size; validate CSRF; authorize STUDENT; parse ID.
2. Open one SQL transaction; lock student and role; verify eligibility and deadline; reject duplicate; read proficiency requirements.
3. Calculate explainable coverage and capture submission-time profile snapshot. Insert application, initial event, notification and audit row in the same transaction.
4. Commit all or roll back all. SQL uniqueness handles a racing duplicate. Redirect-after-POST opens the new application.

## Status state machine

```text
APPLIED → SHORTLISTED → INTERVIEW → OFFERED → ACCEPTED
     └→ REJECTED        └→ REJECTED        └→ DECLINED

Student withdrawal allowed in APPLIED / SHORTLISTED / INTERVIEW.
Only student can accept/decline a pending offer. Terminal states stay terminal.
```

`version` increments on each valid change; `WHERE id=? AND version=?` rejects an outdated tab. Event log records actor, note and timestamp.

## Security scope

The project illustrates defensive techniques; it is not a production security framework. Local demo uses memory-backed sessions, public demo credentials, no email verification/recovery and localhost HTTP. Review the deployment warning in [`../README.md`](../README.md) before considering any external exposure.