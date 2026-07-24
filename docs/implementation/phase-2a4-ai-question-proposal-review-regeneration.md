# Phase 2A.4 — AI question proposal review, regeneration, and lineage

Phase 2A.4 adds a governed review loop without changing canonical Question ownership or the existing human acceptance boundary.

## Lifecycle and invariants

`PROPOSED` is the initial review state retained from earlier phases. A reviewer may reject it with one structured reason and an optional comment; `OTHER` requires a comment. A rejected proposal remains immutable and auditable. An accepted proposal cannot be rejected or regenerated.

Regeneration queues the existing asynchronous question-generation pipeline for exactly one replacement. It is not an in-place edit:

1. the original immutable Knowledge Fact and Source snapshots are reconstructed;
2. reviewer feedback, prior rejection reason, prior question, and prior options are added to the provider request;
3. provider grounding validation and Question Intelligence run again;
4. the replacement is stored as a new `PROPOSED` proposal;
5. the predecessor becomes `SUPERSEDED` and links to its successor in the same transaction.

Every lineage is a single ordered chain. Database uniqueness constraints prohibit branches and multiple successors. A regeneration request is idempotent by its caller-supplied key, and optimistic proposal versions reject stale decisions.

## API

Content Service exposes:

- `POST /api/v1/admin/ai/question-proposals/{proposalId}/reject`
- `POST /api/v1/admin/ai/question-proposals/{proposalId}/regenerate`
- `GET /api/v1/admin/ai/question-proposals/{proposalId}/lineage`

The corresponding AI Service endpoints use the `/internal/v1/question-generation` prefix and require internal service authentication. Content Service derives the reviewer identity from the authenticated admin session; the public request cannot supply an actor.

## Admin workflow

Proposal cards show lifecycle state and generation attempt. Eligible reviewers can:

- choose a structured rejection reason and record review comments;
- provide mandatory concrete feedback and queue regeneration;
- follow the new generation job through the existing polling workflow;
- inspect the immutable ordered attempt history and compare predecessor and replacement question text.

Accepted and superseded proposals display history but no mutation actions. Rejected proposals retain their reason, comment, reviewer, and timestamp.

## Audit and provenance

Audit records cover rejection, regeneration request, regenerated proposal creation, superseding, and lineage linking. Each replacement retains the root and parent IDs, attempt number, reviewer feedback and actor, provider/model/prompt metadata, grounding evidence, token usage, validation result, and Question Intelligence assessment.

## Validation

Automated validation covers:

- migration and lineage schema;
- structured rejection and optimistic versioning;
- asynchronous regeneration and one-successor enforcement;
- ordered lineage retrieval;
- Admin rejection validation, regeneration feedback, polling handoff, and lineage comparison;
- OpenAPI generation, lint, type checking, tests, and production builds.

No automatic acceptance, canonical Question mutation, publication, or learner-facing behavior is introduced. Phase 2A.5 reuses these lifecycle operations independently inside its governed batch review workflow.
