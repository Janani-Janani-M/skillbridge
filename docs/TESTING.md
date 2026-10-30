# Verification and test plan

`skillbridge.SelfTest` covers pure probability, password hashes, demo SQL seed, application uniqueness/eligibility/state transitions, interview date validation, stale updates, transactional history, foreign-key and range checks, simultaneous duplicate applications, submission snapshots and HTML escaping.

`skillbridge.HandlerTest` calls the real `App.handle()` with constructed `HttpExchange` requests and a real H2 disk database. It exercises registration/login/logout, CSRF, role isolation, profile/opportunity edits, applications through acceptance, interviews, notifications, CSV scoping/formula protection, redirects/status codes, persistence across App recreation and HTML output. It does not replace Workflow or Views.

`tests/integration_test.py` is an optional Python standard-library real HTTP/TCP suite. Run `sh test.sh --http` or `test.bat --http` with Java 17+ and Python on a normal local-network-capable machine.

## Verified in the authoring environment

Java 17 compilation succeeded; **58 Java/math/database checks and 84 handler checks passed**. Sandbox network policy blocked loopback sockets, so the additional TCP suite could not run there; direct real handler tests did run. No browser network session, operating-system matrix or production deployment was tested. These observed tests are not a claim that defects are impossible under every machine, load or configuration.

Run Windows `test.bat` or macOS/Linux `sh test.sh`. Neither built-in Java test needs internet, JUnit or credentials.

Possible academic extensions: password recovery with audit and reset tokens; SQL-level pagination; managed DB/migrations/backups/TLS; authenticated notification delivery; modeling only with consented representative outcomes and held-out calibration/bias analysis.