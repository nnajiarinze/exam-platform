# Testing strategy

## Test layers

- **Unit:** Domain invariants, state transitions, selection/scoring/readiness functions, validation, and error mapping using JUnit 5/AssertJ (and the client test stack). Keep them deterministic and free of framework boot where unnecessary.
- **Integration:** Spring adapters, PostgreSQL constraints/transactions, repositories, security filters, queue/object/provider adapters using Testcontainers or faithful local substitutes.
- **Contract:** Validate provider and consumer behavior against versioned OpenAPI/event/snapshot schemas; generated clients compile in CI. Test additive evolution and rejected incompatible versions.
- **End-to-end:** A small set across admin approval → release → import → mobile practice/mock, plus subscription entitlement. Do not make E2E the main source of domain coverage.

## Required risk coverage

- Run every Flyway chain against an empty database and supported previous schema; verify constraints and representative backfills. Applied migrations are immutable.
- Prove release immutability, approved-only membership, checksums, failure-before-event behavior, corrective release, and historical retention.
- Deliver duplicate/out-of-order events and repeated API idempotency keys; assert one logical result and observable poison-message handling.
- Freeze clock and randomness for mock selection/timing/scoring; reproduce results from release, question versions, blueprint, rule version, responses, and seed where used.
- Test authorization by role/owner, tenant/object reference abuse if introduced, invalid tokens, rate limits on risky paths, webhook signatures/replay, log redaction, export/deletion authorization, and AI input minimization.
- Use ArchUnit to prevent cross-layer leakage and imports of another service's implementation; CI also checks each service builds independently.

## Avoid excessive testing

Do not test Spring/framework internals, generated client line-by-line behavior, trivial getters, implementation-private call order, third-party SDK behavior already covered by its contract, or every permutation in slow E2E tests. Prefer property/parameterized unit tests for combinatorial scoring and selection rules, contract fixtures for provider edges, and focused integration tests for owned behavior.

## Initial quality gates

Every change must compile, format/lint, pass unit and relevant integration/contract tests, apply migrations from empty state, and introduce no high-severity dependency/security finding. Boundary and API compatibility checks are blocking. Coverage percentage is a trend signal, not a substitute for risk cases; establish a baseline once source exists and prevent unexplained regression. Release/publishing, scoring, entitlement, and authorization paths require explicit positive, negative, retry, and concurrency coverage before production.

Local expectations are in [local development](../architecture/local-development.md); completion criteria are in the [definition of done](definition-of-done.md).
