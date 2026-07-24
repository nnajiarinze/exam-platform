# Phase 2A.5 — Bulk AI question generation and batch review

## Architecture and ownership

Content Service resolves canonical hierarchy scopes and remains the only service that can create canonical Questions. AI Service owns persistent batches, items, generation jobs, proposals, provider execution, review assignment metadata, progress, usage, and audit history. No service reads another service's database.

A batch item invokes the existing single-question generation service with `proposalCount=1`. Consequently every item uses the existing immutable snapshot checks, provider schema, grounding validator, Question Intelligence engine, proposal persistence, provenance, and retry behavior. Bulk acceptance calls the Phase 2A.3 acceptance service once per proposal. Bulk regeneration calls the Phase 2A.4 lineage service once per proposal.

## Scope resolution and preview

Supported scopes are `KNOWLEDGE_FACT`, `MULTIPLE_KNOWLEDGE_FACTS`, `TOPIC`, `SUBJECT`, and `EXAM_VERSION`. Content Service resolves the scope through the canonical hierarchy, removes duplicate fact IDs, evaluates each fact with the existing generation eligibility rules, and creates bounded target/context snapshots.

Preview is non-persistent. It returns eligible and excluded fact counts, exclusion reason counts, exact proposal and provider-call totals, deterministic resolved distributions, the configured maximum of 200, and warnings. Monetary cost is deliberately omitted because no versioned pricing configuration exists.

## Deterministic planning

Question types are assigned by stable round-robin order. Difficulty and Bloom percentages must be non-negative, use supported values, and total 100. Counts use largest-remainder allocation with stable enum-order tie breaking, after which assignments are interleaved deterministically. The exact assignment is persisted on each item.

## Persistence and lifecycle

`ai_question_generation_batch` stores immutable scope/configuration, a definition checksum, actor-scoped idempotency key, provider/model, lifecycle, and timestamps.

Batch states:

- `PENDING`: no item started.
- `RUNNING`: items remain active.
- `PARTIALLY_COMPLETED`: terminal with generated and failed/cancelled items.
- `COMPLETED`: every item generated a proposal.
- `FAILED`: no item generated successfully.
- `CANCELLING`: no new claims; running jobs may finish.
- `CANCELLED`: no active items remain.

Item states are `PENDING`, `PROCESSING`, `RETRY_SCHEDULED`, `GENERATED`, `FAILED`, and `CANCELLED`. Items retain their immutable snapshots, target configuration, job/proposal links, attempts, failures, retry time, lease, and timestamps.

## Processing, concurrency, retry, and cancellation

Workers claim items with PostgreSQL `FOR UPDATE SKIP LOCKED`. Global active leases enforce configurable concurrency (`ai.question-batches.concurrency`, default 2, hard maximum 8). Claims and item transitions are transactional. A processing lease is renewed while its generation job is active; an item without a job is safely recovered after lease expiry.

Transient provider timeout, unavailability, and rate-limit errors retry with bounded exponential delay. The default maximum is three attempts and the hard maximum is five. Permanent validation, grounding, intelligence, and duplicate failures do not retry. One failed item never rolls back another.

Cancellation changes the batch to `CANCELLING`, atomically cancels pending/retry-scheduled items, prevents new claims, and allows already-running jobs to reach a safe terminal state. Generated proposals are retained. Repeated cancellation is idempotent.

## Duplicate protection

Each item passes through existing proposal validation. Existing proposal and lineage constraints remain active. Canonical exact-duplicate validation still executes during every individual acceptance. Successful batch items and canonical Questions are never recreated when failed items are retried.

## Review assignment and bulk actions

Assignments are workflow metadata in `ai_question_proposal_review_assignment`; they do not change `PROPOSED`, `REJECTED`, `ACCEPTED`, or `SUPERSEDED`. Assign and unassign operations are itemized.

Bulk reject, regenerate, and accept operations deliberately execute each proposal independently:

- rejection uses Phase 2A.4 structured reason/comment validation;
- regeneration creates one independent successor lineage per proposal;
- acceptance performs fresh grounding, source, intelligence, stale-input, authorization, and exact-duplicate checks before creating one `DRAFT / UNREVIEWED` Question.

There is no blind accept-all path and no transaction spans unrelated proposals. Responses report per-proposal success or typed failure.

## Progress, usage, and export

Generation progress counts pending, processing, retry-scheduled, generated, failed, and cancelled items. Review progress derives from current proposal lifecycle and is distinct from generation completion. Acceptance progress counts accepted proposals only. Counts are queried from source rows to avoid counter drift.

Input, output, and total token usage are aggregated from linked generation jobs while retaining item-level job attribution. Monetary cost is omitted. The bounded JSON export includes batch and review results but excludes prompts, secrets, and hidden reasoning.

## APIs

Public endpoints use `/api/v1/admin/ai/question-generation-batches`; internal endpoints use `/internal/v1/question-generation-batches`. They cover preview, create, list/detail, paginated items/proposals, cancel, retry failed, export, reviewer assignment, and itemized bulk reject/regenerate/accept.

## Admin workflow

The Admin navigation contains an AI Question Batches workspace. Administrators can configure and preview a scope, see the exact count before creation, monitor separate generation/review/acceptance progress, inspect failures and usage, filter items/proposals, review through the reused Phase 2A.3/2A.4 proposal card, select visible proposals, run guarded bulk dialogs, assign reviewers, retry failures, cancel active batches, and export results.

## Audit

AI Service records batch creation, item start/generation/failure/retry, cancellation, reviewer assignment/unassignment, and related proposal lineage events in the existing append-only audit store. Polling itself is not audited.

## Operational limits and known limitations

- Maximum 200 items per batch.
- One to three questions per Knowledge Fact.
- Concurrency maximum 8 and attempts maximum 5.
- Database polling is the local/recoverable implementation; managed queue adoption remains governed by ADR-007.
- No monetary estimate is returned without versioned pricing data.
- Export is intentionally bounded by the batch maximum.
- Semantic vector similarity is not introduced; existing exact duplicate and Question Intelligence signals remain authoritative.
- Running provider calls are not force-killed during cancellation.

## Validation

Integration tests cover persistence migrations, actor/payload idempotency, conflict detection, deterministic planning, asynchronous multi-item processing, progress, usage, cancellation, assignment, invalid distributions, and regression through the existing proposal lifecycle. Admin tests cover preview-before-create, persisted batch rows, separate progress, item failure rendering, loading, and filtering.
