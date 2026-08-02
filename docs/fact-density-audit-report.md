# Sverige i fokus source-v2 Fact-density report

Status: `FACT_DENSITY_READY`

The immutable audit `ac32ab62-70eb-513d-be74-31c5ff6e1a1e` and all 72 planned batches are complete. Two failed jobs were recovered through new idempotent retry keys; one exact-evidence failure repeated and was deterministically excluded without weakening validation.

## Final outcomes

- Starting approved Facts: 85
- Final approved Facts: 209
- Planned teaching concepts processed: 179/179
- New approved Facts: 124
- Duplicate: 6
- Non-atomic, too broad, or invalid declarative form: 39
- Unsupported or outside the exact bounded-source evidence: 10
- Existing approved Facts modified: 0
- Provider proposals retained: 170
- Evidence-span utilization: 10.03% → 22.97%
- Topic sufficiency: 33 `READY_FOR_DEEPER_LESSON`, 5 `LIMITED_BUT_USABLE`, 0 proven `SOURCE_EXPANSION_REQUIRED`

The 39 non-atomic outcomes comprise 32 `NEEDS_SPLIT`, one `TOO_BROAD`, and six proposals stopped by Content text validation. The ten source-boundary outcomes comprise one automated `UNSUPPORTED`, eight structured candidates rejected for non-exact evidence, and one batch excluded after two evidence-boundary failures. Nothing was automatically rewritten.

The full section inventory is in `content/sverige-i-fokus/fact-density-audit-v1.json`. Per-Topic before/after counts, Fact-derived learner questions, page ceilings, and reading-time projections are in `content/sverige-i-fokus/fact-density-impact-preview-v1.json`.

## Lesson-readiness preview

- Topics safely supporting 5–6 pages: 33
- Topics safely supporting 4 pages: 5
- Potential additional pages over the four-page baseline: 61
- Projected corpus size: 10,667–12,802 words
- Projected reading time: 76.2–91.4 minutes at 140 Swedish words/minute
- Decision: deeper Lesson regeneration is justified, but it was not started by this task

## Provider and budget accounting

- Groq FREE (`openai/gpt-oss-120b`): 16 succeeded, 35 failed; 19,707 prompt and 12,339 completion tokens on successful attempts
- OpenRouter FREE (`google/gemma-4-26b-a4b-it:free`): 21 succeeded; 28,356 prompt and 2,874 completion tokens
- OpenRouter PAID (`openai/gpt-oss-120b`): 37 succeeded; 46,619 prompt, 26,736 completion, and 21,231 reasoning tokens
- Paid spend attributable to the complete expansion: $0.00864577
- Application budget after completion: $14.00 configured, $0.06649861 spent corpus-wide, $0 reserved, $0.03236664 conservatively unknown, $13.90113475 available

Paid fallback remained application-budgeted. Execution was sequential. Provider hard deadlines and recovery were preserved. The temporary local request ceiling used to finish the recoverable backlog was restored to the default 30 requests/hour.

## Preservation

No Lesson, Question, Mock Exam, release, projection, or learner-state mutation was performed. The active release remains `9f5a2207-1c8f-5325-95d9-afd935a8efe2` with checksum `2660dea5cb80b20ccbe36a2c44d93855a416d623c795625b18ebfcd13ecacae4`, 85 released Facts, and 139 Questions. The database still contains 139 canonical Questions, 25 Practice sessions, 37 Practice responses, one Mock Exam attempt, and one Mock Exam response.

## Validation

- Focused audit, generation, report, approval, and corpus tests: passed
- Migration v32 integration test: passed
- Compose validation: passed
- JSON validation and `git diff --check`: passed
- Gradle build reached the known Content/Testcontainers shutdown hang and was stopped by the configured five-minute bound; no assertion failure preceded the bound

## Next action

Begin a separate reviewed Lesson-regeneration task from the immutable 209-Fact corpus. Use the Topic-specific page ceilings in the impact preview, retain shorter four-page Lessons for the five `LIMITED_BUT_USABLE` Topics, and do not create or activate a release until Lesson validation completes.
