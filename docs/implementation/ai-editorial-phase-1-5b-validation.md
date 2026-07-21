# AI editorial Phase 1.5B validation

## Automated coverage

- AI migrations upgrade through V4 and preserve prior job data.
- All five Phase 1.5B operations have deterministic fake-provider scenarios.
- Operation-specific validation rejects missing, invented, or unknown evidence and unsafe lifecycle instructions.
- Split acceptance is exercised against PostgreSQL, including multiple drafts, retained original, Source links, lineage, audit records, stale Source rejection, duplicate rejection, and idempotent replay.
- Admin component tests cover author/reviewer operation visibility, missing-Source behavior, ordered proposal selection, explicit keep-original confirmation, evidence/coverage rendering, findings, and dismissal.

## Manual validation

1. Start `ai-database`, `content-database`, `ai-service`, `content-service`, and `admin` with the local fake provider enabled.
2. Sign in as a content author and open an editable Knowledge Fact with stored Source text.
3. Run **Make atomic** or **Split fact**. Confirm the job queues, progresses, and shows ordered proposal cards with evidence and coverage.
4. Select saved proposals. Confirm the dialog states that the original is retained and new facts are `DRAFT`/`UNREVIEWED`.
5. Accept once and repeat with the same idempotency key. Confirm one result set and no duplicate drafts.
6. Confirm the original text/status is unchanged; each result has Source relationships and requires normal review.
7. Run **Check source support**, **Detect ambiguity**, and **Editorial review notes**. Confirm structured findings appear inline and dismissing one records a reason without altering the fact.
8. Sign in as reviewer. Confirm only analysis operations are available and mutation/acceptance controls are absent.
9. Use a fact without stored Source text. Confirm grounded operations are disabled with a useful explanation while ambiguity detection remains available.
10. Change target or Source content after generation and confirm acceptance returns the specific stale error without partial writes.

## Expected controlled failures

- Provider timeout/unavailability follows the shared bounded retry policy.
- Malformed, empty, over-count, ungrounded, invented-evidence, and lifecycle-action output fails validation and persists only sanitized error metadata.
- Missing Source content blocks grounded generation.
- Unauthorized authors/reviewers/publishers receive the existing structured authorization response.

## Production readiness boundary

The production provider adapter, secret provisioning, cost/rate policy, operational dashboards, retention policy, and distributed reconciliation for an AI-service outage during acceptance remain deployment work. Phase 1.5B intentionally provides no automated editorial decision, merge operation, bulk workflow, or release transition.
