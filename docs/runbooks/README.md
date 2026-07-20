# Operational runbooks

Every incident records UTC time, environment, request ID, affected entity, operator, and safe error code. Never copy secrets, learner identity, question text, or answer keys into tickets.

## Release, delivery, activation, and rollback

- Release failed: stop duplicate attempts, inspect validation and audit events, correct content through a new reviewed version, then revalidate. Never mutate a published snapshot.
- Delivery failed: check both service health endpoints and the delivery attempt; verify server-side URL/key configuration and the stored checksum; retry the existing delivery.
- Activation failed: verify delivery, restore Learning Service, and retry. To roll back, activate a previously delivered release; pinned sessions remain unchanged.

## Review queue stuck

Check pending age, assignment, request IDs, and audit history. Reassign only with authorization and never bypass author/reviewer separation.

## Database migration failed

Stop traffic, preserve logs and Flyway history, reproduce against an isolated restore, and ship a forward corrective migration. Never edit an applied migration or mark a failure successful manually.

## Service outages

- Learning unavailable: check health, database, migrations, capacity, and sanitized logs. After recovery verify active release, pinned session resume, and learner reporting.
- Admin unavailable: check static hosting, Content health, browser origin/CORS, and identity provider. Never enable development headers in production.
- Mock exam failure: capture error code/exam ID, check configuration allocations, eligible counts, expiry, and rate limiting. Do not mutate active attempts.
