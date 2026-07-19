# Codex implementation prompt template

```text
Implement the ticket below in this repository.

Before changing files:
1. Read AGENTS.md completely.
2. Read the ticket's referenced ADRs, architecture/product documents, and contracts.
3. Inspect nearby implementation and test conventions.
4. Restate any blocking conflict; otherwise work within the ticket's assumptions.

Stay strictly within scope. Avoid unrelated refactoring, dependency upgrades, formatting churn, and new architecture. Preserve independent service/database ownership. Do not edit applied migrations or generated files by hand.

Implement the smallest complete change. Update append-only Flyway migrations, OpenAPI/event contracts, generated clients/artifacts through their generator, tests, security/authorization, errors/logging, and relevant documentation whenever applicable. Add no application or technology excluded by AGENTS.md.

Run the ticket verification commands and the narrowest relevant tests first, then repository quality gates. Review your own diff for boundary violations, compatibility, accidental secrets, missing failure/concurrency cases, and unrelated changes. Fix issues you find.

Final response:
- outcome and key behavior
- files changed
- migrations and contracts changed
- exact commands/tests run with results
- assumptions, risks, and scoped follow-ups
- confirmation that the diff was reviewed and no unrelated refactoring was included

TICKET:
<paste completed ticket using ticket-template.md>
```

