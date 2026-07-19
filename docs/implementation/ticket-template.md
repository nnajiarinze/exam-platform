# Implementation ticket template

## Task

`<One outcome stated as an imperative>`

## Context

`<User/system problem, owning service, and why now>`

## Files to read

- `AGENTS.md`
- `<Relevant ADRs, architecture, product rules, contracts, and neighboring code>`

## Scope

- `<Required behavior and bounded files/components>`

## Out of scope

- `<Tempting adjacent work that must not be done>`

## Business rules

- `<Precise invariants, ownership, state transitions, idempotency, historical behavior>`

## API contract

`<OpenAPI/event changes, versioning and compatibility; “none” with reason if absent>`

## Data changes

`<Owning database, append-only Flyway migration, backfill/rollback and retention; “none” if absent>`

## Security considerations

`<Authentication, authorization, input validation, sensitive data, abuse/rate limits, audit>`

## Acceptance criteria

- [ ] `<Observable outcome, including failure behavior>`

## Required tests

- `<Unit, integration/Testcontainers, contract, migration, architecture, security, concurrency, or E2E cases>`

## Verification commands

```text
<Exact commands runnable from a clean checkout>
```

## Expected final report

List files changed, behavior delivered, migrations/contracts, commands and results, assumptions, unresolved risks, and any scoped follow-up. Confirm no unrelated changes.

