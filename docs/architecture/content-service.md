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

