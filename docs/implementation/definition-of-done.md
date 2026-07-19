# Definition of done

A ticket is complete only when all applicable items are satisfied:

- [ ] Acceptance criteria and documented business rules are satisfied, including failure behavior.
- [ ] Relevant unit, integration, contract, migration, architecture, security, concurrency/idempotency, and E2E tests pass.
- [ ] Each schema change has an append-only owned Flyway migration tested from an empty database and supported upgrade path.
- [ ] OpenAPI/event contracts and generated consumers are updated compatibly.
- [ ] No unrelated refactoring, formatting churn, source code, dependency changes, or cross-service coupling is included.
- [ ] Authentication, authorization, privacy, input validation, abuse cases, and secrets were reviewed.
- [ ] Errors are actionable and stable; logs/metrics/traces/audit events are sufficient and contain no sensitive data.
- [ ] Relevant product, architecture, ADR, operational, and user-facing documentation is current.
- [ ] A clean checkout can build and run the required verification without undeclared local state.
- [ ] The author reviewed the final diff and reported changed files, migrations/contracts, commands/results, risks, and assumptions.
- [ ] All BLOCKING and IMPORTANT reviewer findings are resolved or an accountable owner explicitly accepts a documented exception.

“Not applicable” requires a short ticket-specific reason; it is not a silent omission.

