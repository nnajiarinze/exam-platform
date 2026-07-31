# Reviewed lesson page repair

Lesson proposals remain immutable editorial inputs. A page is first copied into
`ai_lesson_page_revision`, split into sentence-level claims, and checked against
the proposal's exact source snapshot and approved Fact texts. Instructional and
transition text is recorded as `NON_FACTUAL_TEXT`; it is not forced into the
Knowledge Fact model.

The previous validator treated page prose as opaque JSON. Its gates verified
structure, IDs, coverage, checksums, and whether a supplied evidence quotation
occurred in the source. They did not validate each assertion, so prose could
connect two supported concepts with an unsupported causal conclusion.

## Workflow

The internal page endpoints support inspection, validation, explicit rejection,
and repair. A rejected revision is retained. Repair sends only the topic,
objective, assigned approved Facts, bounded source section, existing page plan,
sibling titles, and concise failure codes through FREE_ONLY routing. The new
revision links to the revision it replaces. Sibling pages and deterministic page
order are not changed.

Every replacement is revalidated before it can update the current proposal.
The proposal's `pageClaimValidationPassed` gate becomes true only when each page
has a validated revision. Repair calls carry a persisted idempotency key: a
repeated key returns the existing result, while a new key can retry a rejected
replacement without losing history.

Validator `lesson-page-claim-v1` reports `SUPPORTED`, `NON_FACTUAL_TEXT`, and
structured failures including `UNSUPPORTED_CLAIM`, `UNSUPPORTED_CAUSALITY`,
`UNSUPPORTED_GENERALIZATION`, `MISSING_EVIDENCE`, `CONTRADICTION`, and
`DUPLICATE_CLAIM`. Existing proposal gates continue to own source identity,
checksum, topic/objective mapping, ordering, Swedish text, and structural checks.

Admin support is intentionally limited to the authenticated internal API. Its
inspection response includes page and claim status, diagnostics, short evidence
references, provider/model/prompt metadata, and replacement lineage. No source
document or chain-of-thought is exposed.
