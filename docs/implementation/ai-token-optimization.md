# Sverige i fokus AI token optimization

Date: 2026-07-31  
Baseline commit: `35ae4c08c043aebafbab1a9951f13decf9f5c031`

No provider was called and no corpus job, proposal, release, or database row was changed. Historical values below are provider-reported usage from the local AI audit tables. Post-change values are deterministic estimates obtained from the same stored snapshot sizes and the compact renderer; the first future production attempts must reconcile these estimates with provider-reported usage.

## Profile and budgets

| Operation | Samples | Baseline prompt | Baseline completion | Baseline total | Estimated optimized prompt | Prompt reduction | Budget |
|---|---:|---:|---:|---:|---:|---:|---:|
| Knowledge Fact (current V3) | 2 | 1,106 | 1,031 | 2,137 | 720 | 34.9% | <=1,500 |
| Lesson generation | 16 | 1,684 | 3,403 | 5,087 | 1,170 | 30.5% | <=1,800 |
| Lesson repair | 4 | 1,439 | 1,922 | 3,361 | 980 | 31.9% | <=1,000 |
| Question generation | 16 | 2,325 | 1,284 | 3,609 | 690 | 70.3% | <=1,200 |
| Question repair | 0 repair outputs | no observed sample | no observed sample | no observed sample | 860 fixture estimate | n/a | <=1,000 |

Weighted over the 38 observed calls, prompt usage falls from 72,102 provider-reported tokens to an estimated 35,180 tokens, a **51.2% reduction**. Completion-token savings are deliberately excluded from that success calculation. Question completions should also shrink because models no longer echo immutable Fact, Source, checksum, language, confidence, warning, or per-option-rationale fields.

The estimator is intentionally conservative and is not represented as provider billing data. If the first five reconciled calls exceed an operation budget, stop that operation and inspect the stored prompt/input-token telemetry rather than weakening evidence.

## Ranked inflation findings

The approximate contributor values below use provider totals plus serialized UTF-8 size attribution; provider tokenizers do not expose field-level accounting.

1. **Question Source-section duplication and full workflow context: about 1,050 prompt tokens/call.** The old request serialized the full Context record (about 5,080 characters on average), including subject, exam, corpus, chapter/page metadata and a 3,375-character Source excerpt. Generation only needs the approved Fact and its already accepted exact evidence. The compact request sends Fact, Topic, Objective, Source/Section identity and checksum, and exact evidence. The full immutable excerpt stays server-side for validation.
2. **Question output/provenance schema: about 430 prompt tokens/call.** Fact and Source evidence, IDs, checksums, language, confidence, warnings, and option rationales were requested back from an untrusted provider even though the application already owns them. They are now restored from the immutable request snapshot. The unchanged validator checks those restored values against that same snapshot.
3. **Lesson and repair envelope metadata: about 260 prompt tokens/call.** Aggregate IDs, duplicate Source titles, record property names, XML-like tags, evidence/key-term echoes from the original page, and verbose instructions were repeated. Compact JSON keeps Fact version/text, Source identity/checksum/text, plan/page purpose, original body, neighboring titles and failed diagnostics.
4. **Knowledge Fact instructions and schema: about 385 prompt tokens/call.** V3 repeated splitting, workflow, disclaimer and evidence language and requested unused confidence/notes/location/warnings. A static rule set retains single-proposition, alignment, exact-quote, extraction-artifact and no-inference constraints. The schema now contains only parsed Fact text and exact quote.
5. **Repeated static prompt construction: 130-300 tokens/call depending on operation.** Static system text is held in immutable templates. User input is structured JSON containing variables only.

## Safety and semantic equivalence

- Fact generation still receives Topic, Objective, bounded Source text and language, and still must return an exact quote.
- Question generation receives only the approved Fact, Topic, Objective, immutable Source/Section identity and checksum, and the exact accepted evidence. It receives no lesson, chapter, unrelated Fact, previous question, or unrelated Source content.
- Lesson generation receives only approved Fact version/text pairs, linked exact Source, deterministic page plan and language.
- Repair receives the same grounding plus the failed page, nearby titles and validation diagnostics.
- No acceptance, grounding, evidence, Question Intelligence, lesson, checksum, or publication validator was changed.
- Provider-supplied immutable provenance was removed from the question response boundary. The adapter reconstructs it from the request snapshot before existing validation, preventing both token waste and provider mutation of IDs/checksums.
- Question repair uses the same compact base context and adds only its parent proposal, reason, reviewer feedback and attempt data. There was no completed historical repair call, so its value is a fixture estimate rather than a claimed observed reduction.

## Quota benchmark

Using the locally configured Groq 8,000-token rolling allowance and observed average completions:

| Work | Before | Estimated after | Change |
|---|---:|---:|---:|
| Questions per token window | 2.2 | 4.1-5.7 | 1.9-2.6x |
| Lessons per token window | 1.6 | 1.7 | about 1.1x |

Question throughput benefits most because both prompt context and echoed completion provenance shrink. Lesson completion content is intentionally unchanged, so its total-token throughput improves only modestly even though its prompt is smaller. For the remaining mixed corpus, a conservative quota-bound elapsed-time estimate is **30-40% shorter**; actual wall time also depends on requests/day, provider cooldowns, review time and the final question/lesson mix.

## Verification

The focused adapter test captures the provider request, verifies irrelevant chapter/exam/corpus/Source text is absent, verifies the compact schema, and proves exact Fact/Source identity and evidence are restored. Full tests exercise the unchanged Fact, Question Intelligence, question proposal, lesson proposal, repair, acceptance and checksum validators. Corpus regression uses the existing Chapter 1 and Chapter 2 deterministic fixtures and makes no provider calls.

- `python3 -m unittest scripts.tests.test_sverige_i_fokus_corpus scripts.tests.test_demo_data`: 18 passed.
- `./gradlew test --no-daemon`: passed all services.
- `./gradlew build --no-daemon`: passed.
- Focused `GeminiAiProviderClientTest`: passed.
- `git diff --check`: passed.
- Gitleaks on every changed file: passed. Repository-history scanning still reports the pre-existing finding at commit `06e7fdf5dcae93fcc4fb96ed3eb2e1d051b467a2` in `docs/sverige-i-fokus-chapter-1-multi-page-lessons.md`; it is unrelated to this diff.
- Existing queued job `ab41a055-58a4-41e4-93e8-8bb1b0d2b6aa` remained `QUEUED`; no generation was resumed.

Recommendation: deploy the code before resuming Chapter 2, then use only the existing queued parent batch. Audit the first five provider-reported input-token values against these budgets. Do not regenerate accepted content merely to benchmark.
