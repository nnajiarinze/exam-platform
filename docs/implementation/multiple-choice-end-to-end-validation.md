# Multiple-choice end-to-end validation

## Decision

Published snapshots containing the explicit `correctOptionIds` set use schema version `1.1`. The Learning Service continues to accept legacy `1.0` snapshots and derives their correct set from each option's `correct` flag. New `1.1` imports require the explicit set and flags to match exactly.

Both `SINGLE_CHOICE` and `MULTIPLE_CHOICE` use the same request shape: `selectedOptionIds`. Multiple-choice scoring is exact-set scoring: the selected identifiers must equal the complete correct identifier set, independent of order. There is no partial credit.

## Validation matrix

- Authoring accepts 2–6 nonblank, uniquely ordered, nonduplicate options.
- `SINGLE_CHOICE` and `TRUE_FALSE` require exactly one correct option.
- `MULTIPLE_CHOICE` requires at least one correct option.
- Release validation applies the same rules and no longer rejects `MULTIPLE_CHOICE`.
- Snapshot `1.1` includes stable option IDs, `correctOptionIds`, per-option correctness, optional feedback, and deterministic ordering.
- Learning import accepts all three supported types and rejects duplicate or inconsistent correct-option identifiers.
- Practice and mock responses persist every selection in normalized join tables; legacy scalar selection columns remain for backward-compatible historical data.
- Practice reveals correctness, the complete correct set, explanation, and option feedback only after submission.
- Mock exam delivery returns saved selections for resume but does not return correctness before final submission.
- Mock review identifies selected, correct, incorrect-selected, and missed-correct options.

## Automated validation

Run from the repository root:

```bash
./gradlew test build
npm --prefix apps/mobile run generate:api
npm --prefix apps/mobile run typecheck
npm --prefix apps/mobile test -- --runInBand
npm --prefix apps/mobile run build
npm --prefix apps/admin run generate:api
npm --prefix apps/admin run lint
npm --prefix apps/admin run typecheck
npm --prefix apps/admin test -- --runInBand
npm --prefix apps/admin run build
```

## Manual end-to-end check

1. Create a `MULTIPLE_CHOICE` question with at least two correct options and optional feedback.
2. Review and approve it with a different development reviewer.
3. Add the approved version and its knowledge facts to a release, validate, publish, deliver, and activate it.
4. Confirm the stored snapshot is schema `1.1` and contains the same option IDs and `correctOptionIds`.
5. Start practice and submit: all correct; a subset; a superset; and an incorrect set. Only the exact set must score correct.
6. Start a mock exam, save multiple selections, leave and resume, and confirm all selections return without correctness data.
7. Submit the mock exam and confirm review marks selected wrong answers and missed correct answers distinctly.

## Rollback

Application rollback remains compatible with existing schema `1.0` releases. Migration `V9` is additive apart from widening checks and nullable legacy scalar columns; selection rows are preserved. Do not delete the new join tables when rolling application code back. New `1.1` releases should not be published while an older Learning Service that only accepts `1.0` is active.
