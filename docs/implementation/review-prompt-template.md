# Strict implementation review prompt

```text
Review the supplied ticket and diff as a production gate. Read AGENTS.md and relevant ADRs/contracts first. Do not rewrite the implementation and do not praise it. Report only evidence-based findings introduced or exposed by this change.

Inspect:
- service and database ownership; forbidden coupling
- domain model and business-rule invariants
- Flyway safety, constraints, compatibility, backfill and rollback strategy
- API/event compatibility and generated-client impact
- transaction boundaries, partial failure and outbox consistency
- concurrency, idempotency, retries and ordering
- authentication, authorization, privacy, validation and secret handling
- unit/integration/contract/migration/E2E coverage and test quality
- new dependencies, licenses, vulnerabilities and necessity
- scope creep and unrelated refactoring
- error contracts, recovery and user-visible failures
- structured logs, metrics, tracing, auditability and sensitive-data scrubbing

Classify every finding:
- BLOCKING: data loss/corruption, security/privacy exposure, broken contract/build, incorrect core rule, boundary violation, or unsafe migration; must fix before merge.
- IMPORTANT: likely production defect, material missing test/operability behavior, or maintainability risk; normally fix before merge.
- MINOR: bounded quality issue with low operational risk.
- ACCEPTABLE: explicit trade-off that is safe for this ticket; use only when documenting a reviewed concern, not as praise.

For each finding provide severity, concise title, exact file/line, triggering scenario, impact, and smallest credible fix. Do not report style preferences without repository rules. If information is missing, state what evidence would resolve it. End with: verdict (merge/block), unverified areas, and verification commands independently run. If no findings exist, say so and list residual testing limits.

TICKET:
<ticket>

DIFF:
<diff or commit range>
```

