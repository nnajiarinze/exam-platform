# Sverige i fokus source-v2 Fact-density audit

Status: `FACT_DENSITY_PARTIAL`

The deterministic audit completed for all 38 authoritative source-v2 sections before any provider request. Its immutable ID is `ac32ab62-70eb-513d-be74-31c5ff6e1a1e`. Generation stopped at the configured hourly application limit after 30 requests; the checkpoint is persisted and is safe to resume.

## Results

- Starting commit: `7d702d9c5d0cf7815bc447ae2054b80a36961361`
- Sections audited: 38/38
- Audit classifications: 26 `SOURCE_STRUCTURALLY_COMPLEX`, 9 `UNDERUTILIZED_THREE_OR_MORE_FACTS`, 2 `UNDERUTILIZED_ONE_FACT`, 1 `UNDERUTILIZED_TWO_FACTS`
- Missing teaching concepts identified: 179 safe candidate slots
- Existing approved Facts preserved: 85/85
- Provider proposals produced so far: 69
- Newly approved Facts: 49
- Pending Content validation: 2
- Preserved non-approved proposals: 15 `NEEDS_SPLIT`, 3 `DUPLICATE`
- Approved Fact total: 85 → 134
- Evidence-span utilization: 10.03% → 15.44%
- Lesson sufficiency: 14 `READY_FOR_DEEPER_LESSON`, 24 `LIMITED_BUT_USABLE`, 0 proven `SOURCE_EXPANSION_REQUIRED` at this partial checkpoint
- Preview: up to 26 useful additional pages, projected 8,742–9,652 words and 62.4–68.9 minutes at 140 Swedish words/minute

The full section inventory is in `content/sverige-i-fokus/fact-density-audit-v1.json`. Per-Topic before/after counts, page ceilings, Fact-derived learner questions, and reading-time projections are in `content/sverige-i-fokus/fact-density-impact-preview-v1.json`.

## Provider and budget accounting

- Groq FREE: 4 succeeded, 17 failed; 4,948 prompt and 2,721 completion tokens on successful attempts
- OpenRouter FREE (`google/gemma-4-26b-a4b-it:free`): 21 succeeded; 28,356 prompt and 2,874 completion tokens
- OpenRouter PAID (`openai/gpt-oss-120b`): 5 succeeded; 5,787 prompt, 3,059 completion, and 2,234 reasoning tokens
- Paid spend attributable to this run: $0.00104474
- Application budget state after the run: $14.00 configured, $0.05889758 spent corpus-wide, $0 reserved, $0.03236664 conservatively unknown, $13.90873578 available

## Preservation

No Lesson, Question, Mock Exam, release, projection, or learner-state mutation was performed. The active release remains `9f5a2207-1c8f-5325-95d9-afd935a8efe2` with checksum `2660dea5cb80b20ccbe36a2c44d93855a416d623c795625b18ebfcd13ecacae4`, 85 released Facts, and 139 Questions. The database still contains 139 canonical Questions, 25 Practice sessions, 37 Practice responses, one Mock Exam attempt, and one Mock Exam response.

## Validation

- Focused Python audit/generation/report/corpus tests: 28 passed
- Migration v32 applied successfully and its focused integration test passed
- Compose configuration: valid
- `git diff --check`: passed
- Full `./gradlew test --no-daemon`: assertions passed through AI and Content suites, then was bounded during the known Testcontainers/scheduled-worker shutdown hang
- `./gradlew build --no-daemon`: deferred at this partial checkpoint because it executes the same hanging test lifecycle

## Resume

Resume the same audit at `Olika slags medier`, batch 1, using the persisted idempotency key in `fact-density-generation-v1.json` after the hourly limit resets. Do not recreate the audit or regenerate completed jobs. Then approve only `GOOD` proposals, regenerate the deterministic impact preview, run the bounded verification suite, and make the final readiness decision.
