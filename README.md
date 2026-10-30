# SkillBridge — campus opportunities & placement portal

A Java 17, JDBC, relational-database and probability/statistics project for students and a campus placement office. Students maintain an academic/skills profile, explore internships and jobs, save roles, submit one application per role, receive decisions and interview details, and track application history. Placement staff publish roles and requirements, review applications, record decisions, arrange interviews, examine descriptive outcome statistics and export reports.

The interface is server-rendered HTML with local CSS. It intentionally uses no frontend framework, JavaScript bundle, external fonts, analytics, hosted services or CDN. The visual system is a restrained, responsive campus portal.

## Start here

**Requires Java 17 or later.** Extract the entire ZIP to a writable folder; the H2 2.3.232 JDBC driver is bundled, so a fresh demonstration runs offline without installing MySQL or Maven.

- Windows: double-click `run.bat`.
- macOS/Linux: from the extracted folder run `sh run.sh`.
- Open `http://127.0.0.1:8080` in your browser. Keep the server process open; Ctrl+C stops it.
- The first run creates local `data/` files and sample records. Set a unique admin password **before first startup**: PowerShell `$env:SB_ADMIN_PASSWORD='Your-unique-password'; .\run.bat`, or macOS/Linux `SB_ADMIN_PASSWORD='Your-unique-password' sh run.sh`. Passwords are hashed before persistence. Changing this variable does not reset an existing account.
- Rebuild with `sh build.sh` or `build.bat` using a full JDK (including `javac` and `jar`).

### Local demonstration accounts — synthetic data only

| Role | Email | Initial password |
|---|---|---|
| Student | `student@skillbridge.local` | `Student@12345` |
| Student | `rahul@skillbridge.local` | `Student@12345` |
| Placement office | `admin@skillbridge.local` | `Admin@12345` |

These public sample credentials are only for a local classroom demo. Change passwords before entering any information; do not expose the server to a network or use real student records. The seed admin password is read only when there are no accounts. A seed password cannot reset an existing account.

## What students can do

Register an account; maintain name, department, graduating year, CGPA, bio, optional portfolio and honest skill proficiency; search/filter/sort/page current opportunities; inspect eligibility, requirements, compensation and deadlines; save roles; submit one cover note per role; see a submission-time profile/skill snapshot, status timeline, interview venue and notifications; withdraw at permitted stages; accept/decline offers; download their own application CSV. Minimum CGPA, graduating batch, role status and deadline are server-enforced. Skill gaps are shown rather than silently used to exclude a student.

## What the placement office can do

View pipeline counts; create/edit/close roles and skill requirements; review/filter applications and a student directory; shortlist/reject and schedule an interview with future date/time and venue; issue offers; see full status history, notes and interview details; manage the shared skill catalogue; view an activity log; export the institution's application CSV. Invalid stage changes and stale concurrent updates are rejected.

## Probability lab — correct interpretation

A skill coverage score is an explainable prerequisite comparison—not an individual's chance of hiring:

$$
\mathrm{Coverage}(u,j)=\begin{cases}\dfrac{100}{m}\sum_{s=1}^{m}\min(1,\ell_{u,s}/r_{j,s}),&m>0\\0,&m=0\end{cases}
$$

For required skill $s$, its minimum level $r$ is 1–3, and a student's recorded level $\ell$ is 0–3 (0 means missing); each listed skill has equal weight and coverage is capped at 100%. No requirements yields 0 rather than an undefined value being advertised as a perfect fit. The score is not a calibrated hiring/interview probability or guarantee.

The sample space is all current student × open, non-expired opportunity pairs (or one student's profile × those opportunities for the student view). It includes academically ineligible pairs; CGPA/batch eligibility is a distinct application gate. The lab displays N, mean, median, **population** variance/standard deviation (denominator N because this query represents the complete selected database population), empirical relative frequency at a chosen inclusive threshold, and distribution bands. With N=0 it does not fabricate a rate.

The separate observed-outcome panel defines an offer event as `OFFERED`, `ACCEPTED` or `DECLINED`; the decided denominator additionally includes `REJECTED`. `APPLIED`, `SHORTLISTED`, `INTERVIEW` and `WITHDRAWN` are excluded. It reports raw observed rate, a Beta(1,1) posterior mean `(offers+1)/(decided+2)` and a 95% Wilson interval. With zero decisions the interval says no data; the posterior mean is explicitly called prior-only. These are historical, selection-biased, potentially dependent outcomes—not personal forecasts. The binomial/Monte Carlo lab takes hypothetical user-chosen $n$ and $p$; it never infers $p$ from skill coverage. See [`docs/PROBABILITY.md`](docs/PROBABILITY.md).

## Java / DBMS learning outcomes

Java 17 `HttpServer`, records, generics, lambdas/streams, `SecureRandom`, PBKDF2 password hashing, concurrent bounded executor, JDBC `PreparedStatement`, generated keys, explicit transaction/rollback, SQL constraints, optimistic version checks and server-side validation. Responsibilities are separated across `App`, `Database`, `Auth`, `Workflow`, `Probability` and `Views`.

Ten relations cover users/roles, skill catalogue, two many-to-many bridge tables, opportunities, applications, immutable application events, saved roles, notifications and audit records. Composite keys, FKs, `UNIQUE`, `CHECK` constraints and indexes backstop Java validation. A submission transaction snapshots profile/coverage and inserts application, first event, notification and audit row atomically. The intentionally historical profile snapshot does not change when the current profile changes. See [`docs/DATABASE.md`](docs/DATABASE.md), [`docs/ER_DIAGRAM.md`](docs/ER_DIAGRAM.md), [`docs/JAVA_CONCEPTS.md`](docs/JAVA_CONCEPTS.md).

## Verification

**58 Java/math/database tests and 84 direct HTTP-handler checks passed** on a Java 17 runtime. Coverage includes SQL integrity, simultaneous duplicate submissions, permissions, CSRF, application/interview/offer workflow, CSV safety, account changes and disk persistence. `tests/integration_test.py` additionally exercises actual TCP transport. It could not be run in the authoring sandbox because the sandbox has no loopback route; the real `App.handle()` was tested without a socket. See [`docs/TESTING.md`](docs/TESTING.md). These are observed checks, not a guarantee that defects are impossible on every OS/deployment.

## Settings

| Environment variable | Default | Meaning |
|---|---|---|
| `PORT` | 8080 | HTTP port |
| `SB_HOST` | `127.0.0.1` | Local-only listener |
| `SB_DB_URL` | `jdbc:h2:./data/skillbridge;DB_CLOSE_ON_EXIT=FALSE;LOCK_TIMEOUT=10000` | JDBC database URL |
| `SB_ADMIN_PASSWORD` | `Admin@12345` | Fresh-demo seed password only |
| `SB_COOKIE_SECURE` | unset | Set to `true` only when behind HTTPS |

## Limitations / deployment warning

Academic/demo use only, not production placement SaaS. Local H2, server-memory sessions, one demo-office role, no email, no resume storage, no email verification/account recovery, no SQL-layer pagination for large institutions, and plain HTTP on localhost. Interview time is server local timezone. Keep synthetic data only. Do not bind publicly; production needs independent security/privacy/legal review, HTTPS, managed DB, backups, access/retention controls and secure credential/session operations.

## Contents

`src/main/java/skillbridge/` Java sources; `src/main/resources/schema.sql` DDL; `src/main/resources/static/style.css`; `src/test/java/skillbridge/` Java test suites; `tests/integration_test.py`; `lib/h2-2.3.232.jar`; compiled `skillbridge.jar`; `build.*`, `run.*`, `test.*`; documentation in `docs/`.
