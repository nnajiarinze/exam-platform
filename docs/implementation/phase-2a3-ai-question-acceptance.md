# Phase 2A.3 — AI question proposal acceptance

Phase 2A.3 adds a human-only bridge from an AI Question Proposal to one canonical Content Service Question. AI Service still owns jobs and proposals; Content Service remains the sole owner of canonical Questions.

## Acceptance transaction

An authorised `CONTENT_AUTHOR` or `ADMIN` requests acceptance with the proposal version. Content Service:

1. retrieves the proposal through the internal AI API;
2. requires `PROPOSED` and a passing evaluated intelligence assessment;
3. asks AI Service to rerun grounding, proposal checksum, and Question Intelligence validation;
4. verifies the current approved Knowledge Fact version and checksum;
5. recreates the bounded Source excerpts and verifies every Source checksum;
6. checks exact question text, ordered options, and correct-answer flags under the Learning Objective;
7. creates one `DRAFT` / `UNREVIEWED` canonical Question;
8. stores immutable provenance and acceptance audit events;
9. links the Question in AI Service and transitions the proposal to `ACCEPTED` using optimistic locking.

Accepted and rejected proposals are read-only. The unique provenance constraints and AI proposal linkage prevent one proposal from creating more than one Question.

## Canonical data

Question text, type, ordered options, correct answers, explanation, language, Knowledge Fact links, Source links, mapped canonical difficulty, Bloom level, complexity, generation intent, and deterministic reading time are retained. Provider self-assessment, tokens, request IDs, and quality findings remain proposal-only.

The canonical Question domain supports `EASY`, `MEDIUM`, and `HARD`; `VERY_EASY` maps to `EASY` and `VERY_HARD` maps to `HARD`. The full authoritative intelligence difficulty remains in immutable proposal provenance.

Acceptance does not submit, approve, activate, publish, release, or expose learner content. The existing Question review workflow remains unchanged.

## Deferred

Proposal editing, batch acceptance, semantic duplicate detection, translation, merging, automatic approval, publication, release, and learner visibility remain out of scope.
