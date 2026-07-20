# Content Service

## Responsibilities and owned data

The service owns exams and versions, subjects, topics, learning objectives, knowledge facts, source references, questions/options, reviewed explanations, translations, review state, releases, retirement, and admin audit records. Ownership means it is the only writer and authority for this data.

## Major modules and entities

- **Taxonomy:** `Exam`, `ExamVersion`, `Subject`, `Topic`, `LearningObjective`.
- **Knowledge:** `SourceReference`, immutable `KnowledgeFactVersion`.
- **Assessment:** `Question`, immutable `QuestionVersion`, `AnswerOption`, `ExplanationVersion`, translation versions.
- **Workflow:** review assignment, decision, comment, and issue report.
- **Publishing:** `ContentRelease`, manifest, snapshot artifact, checksum, import status visibility.
- **Admin API:** authenticated commands/queries for editorial work; never a learner content API.

Questions link to learning objectives, facts, sources, and their exact explanation/translation versions. Detailed principles are in [content strategy](../product/content-strategy.md).

## Review and release workflow

Authors create drafts against existing facts and sources. Submission freezes the candidate version for review. A qualified reviewer approves or rejects it; self-review is disallowed by policy for publishable content. Only approved versions can be selected into a release. Publishing validates references, translations, answer correctness, and review state, then creates an immutable manifest and snapshot with a checksum. Status progresses `building → published` or `failed`; a published release is never edited. Retirement affects future selection, not existing releases.

The admin API can create drafts, submit reviews, record decisions, inspect release validation, publish, retire, and triage reports. The publishing boundary writes artifacts and events only after the release transaction is durably recorded. An outbox or equivalent prevents database/event divergence.

## Failure scenarios

- Validation failure leaves a diagnostic failed build and no published event.
- Object-storage failure permits safe retry using release identity and checksum.
- Duplicate queue delivery is harmless to consumers.
- A conflicting edit creates a new version or returns an optimistic-concurrency error.
- A critical content issue retires affected versions from future releases; a corrective release replaces them without rewriting history.
- AI/provider unavailability blocks only optional drafting operations.

## Non-responsibilities

The service does not own users, sessions, scoring, mastery, readiness, payments, entitlements, AI execution, or the learner runtime projection. It does not serve a question on each learner interaction or access another database. See [ADR-004](../decisions/ADR-004-runtime-content-projection.md).

## Admin Phase 2 implementation

The first editorial slice is implemented as the `exam` and `source` modules. The
Content Service owns `exam`, `exam_version`, `subject`, `topic`, and
`source_reference` tables. Foreign keys do not cascade deletes; records are
archived or retired. Unique constraints protect hierarchy-local codes and
ordering, and mutable records carry an optimistic-lock `version`.

The admin API is rooted at `/api/v1/admin`. Reads require a recognized admin
role. Authoring requires `CONTENT_AUTHOR` or `ADMIN`; source workflow decisions
require `CONTENT_REVIEWER` or `ADMIN`. Writes are transactional. Reordering
requires the complete child set and uses a temporary ordering pass to preserve
unique constraints. Important actions emit structured logs without payloads or
internal notes.

## Admin Phase 3 knowledge base

Learning objectives belong to topics and describe learner understanding goals.
Knowledge facts belong to objectives and are the canonical, source-backed truth
from which later assessment content is derived. A stable `knowledge_fact` points
to its current `knowledge_fact_version`; source links belong to that exact
version. Approved versions are never updated in place. Editing an approved fact
creates a new draft version while approved history remains traceable.

Facts move through `UNREVIEWED → UNDER_REVIEW → APPROVED`, with rejection and
required-update paths returning work to draft. Approval requires an active
source and a reviewer other than the version author. Retirement retains history.

## Admin Phase 4 question management

`question` is the stable administrative aggregate and points to its current `question_version`. Options, tags, and knowledge-fact links belong to a version. `question_knowledge_fact` references an exact approved `knowledge_fact_version`, preserving provenance even when the fact later receives a new draft version.

Question creation and draft editing are transactional. Approved versions are never updated: editing an approved question creates a new draft version and atomically moves the aggregate's current-version pointer. Optimistic locking on the aggregate rejects concurrent administrative updates.

Question review follows the same author/reviewer separation as knowledge facts. Only approved active knowledge facts from the question's learning objective may be linked. Publishing and release assembly are outside the Phase 4 API.

## Admin Phase 5 review governance

`review_item` projects the active governance state for one submitted fact or
question version. `review_record` is an append-only chronological trail of
submissions, assignments, comments, priority changes, and decisions.
`review_comment` binds feedback to the exact content version under review.

The shared review layer owns queue queries, concurrency-safe assignment,
comments, priority, and history. Knowledge and question services continue to own
content-specific transitions and approval validation, then atomically record the
governance event in the same transaction. Review status, content lifecycle, and
future release status remain separate concepts.
