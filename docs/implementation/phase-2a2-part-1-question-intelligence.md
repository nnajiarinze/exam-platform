# Phase 2A.2 Part 1 — Question intelligence foundation

Question Intelligence runs only after the Phase 2A.1 structural, language, evidence, and grounding validation succeeds. Provider metadata is advisory; the application derives the authoritative difficulty, Bloom level, complexity, generation intent, and reading time deterministically.

## Processing and persistence

The `question-generation-intelligence-v1` prompt requests advisory pedagogical metadata and a concise quality rationale. `QuestionIntelligenceEngine` applies versioned validators, component penalties, centrally configured weights, and quality thresholds. Any blocking finding prevents the proposal from becoming reviewable. Mixed provider results remain partially complete when at least one proposal passes.

V9 adds nullable intelligence columns to `ai_question_proposal`, normalized finding and component-score tables, and filter indexes. Existing V8 rows retain `NOT_EVALUATED`; they are never assigned fabricated scores. Evaluated rows retain the engine version, evaluation time, provider metadata, authoritative metadata, findings, component scores, rationale, and the existing provider/model/prompt/token provenance.

Quality levels are `EXCELLENT`, `GOOD`, `ACCEPTABLE`, `NEEDS_REVIEW`, and `REJECTED`. Scores range from 0–100 and are internal editorial signals, not learner-facing claims or calibrated item difficulty.

## Admin validation

The existing fact-scoped proposal workspace shows compact quality, difficulty, and Bloom badges, an expandable finding summary, and quality/difficulty/question-type filters. Historical proposals clearly show “Quality not evaluated”. No raw provider JSON is displayed.

## Operational validation

1. Run `./gradlew :services:ai-service:test :services:content-service:test`.
2. Run `./gradlew test`.
3. In `apps/admin`, run `npm run generate:api`, `npm run lint`, `npm run typecheck`, `npm test`, and `npm run build`.
4. Generate proposals with the fake provider and verify deterministic scores across identical inputs, persisted findings, filters, partial completion, rejection, and historical `NOT_EVALUATED` responses.
5. Gemini validation is manual and opt-in; automated tests must not spend provider quota.

Part 2 distractor analysis, semantic duplicate detection, proposal editing/acceptance, canonical Question creation, and publication remain out of scope.
