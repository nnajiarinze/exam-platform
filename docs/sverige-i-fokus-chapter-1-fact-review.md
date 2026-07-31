# Sverige i fokus — Chapter 1 Knowledge Fact review

Generated for corpus `sverige-i-fokus-v1`. This is an internal calibration record; approved
Knowledge Facts remain product content rather than official UHR questions.

Source: *Sverige i fokus*, “Landet Sverige”, PDF pages 5–10. Evidence excerpts below are deliberately short; reviewers should use the full immutable Source Section in Admin.

## Failure analysis

The seven v1 failures all ended with `AI_STRUCTURED_OUTPUT_INVALID` / “Proposal evidence was not found in the supplied source”. The provider returned a schema-valid response, but at least one submitted quote did not match its immutable Source Section. The v1 implementation rejected the entire response and did not retain failed candidate payloads, so candidate text and quotes cannot be reconstructed safely.

The five initial failures affected every Chapter 1 section. A second sample succeeded for four sections after whitespace normalization; “Sveriges indelning” failed twice more. The source contains PDF layout artifacts such as mid-word newlines, spaces, and line-break hyphens. This, together with a four-fact request and an underspecified “verbatim” instruction, is the supported root cause. There is no evidence of page-boundary, section-mapping, schema, provider-status, truncation, or source-support defects.

| Attempt | Section | Pages | Result | Diagnostic | Classification |
|---|---|---:|---|---|---|
| v1-1 | Geografi, klimat och natur | 5–6 | Failed | Evidence absent after v1 comparison | PDF whitespace/evidence-span mismatch |
| v1-1 | Sveriges indelning | 6–7 | Failed | Evidence absent after v1 comparison | PDF word-break mismatch |
| v1-1 | Befolkning | 7 | Failed | Evidence absent after v1 comparison | PDF whitespace/evidence-span mismatch |
| v1-1 | Naturresurser | 7–8 | Failed | Evidence absent after v1 comparison | PDF whitespace/evidence-span mismatch |
| v1-1 | Klimatförändringar | 8–10 | Failed | Evidence absent after v1 comparison | PDF hyphenation/whitespace mismatch |
| v1-2 | Geografi, klimat och natur | 5–6 | Completed | 4 proposals persisted | — |
| v1-2 | Sveriges indelning | 6–7 | Failed | Evidence absent after normalized comparison | Provider did not copy exact PDF artifact |
| v1-2 | Befolkning | 7 | Completed | 4 proposals persisted | — |
| v1-2 | Naturresurser | 7–8 | Completed | 4 proposals persisted | — |
| v1-2 | Klimatförändringar | 8–10 | Completed | 4 proposals persisted | — |
| v1-3 | Sveriges indelning | 6–7 | Failed | Evidence absent after normalized comparison | Provider did not copy exact PDF artifact |

## Existing v1 proposals

| ID | Topic | Proposed fact (abridged) | Classification | Reviewer note |
|---|---|---|---|---|
| `5ea9f89d` | Geografi | Mild climate due to Gulf/North Atlantic currents | AMBIGUOUS | Evidence begins with “Det”; retain the wider causal span or rewrite. |
| `688afde7` | Geografi | Sweden is the largest Nordic country | GOOD | Atomic, clear, stable, exact support. |
| `a0b309a2` | Geografi | Sweden has about 250,000 islands | GOOD | Atomic and supported; preserve “cirka”. |
| `c358c9ca` | Geografi | More than half of Sweden is forest | GOOD | Atomic, stable, directly supported. |
| `1edb9690` | Befolkning | Population is unevenly distributed | GOOD | Clear and supported, though less informative than the v2 wording. |
| `4e91e90f` | Befolkning | Stockholm, Gothenburg and Malmö are the three largest cities | GOOD | Atomic and directly supported. |
| `9f735e46` | Befolkning | About 85% live in cities | GOOD | Supported; percentage is edition-bound context. |
| `cb92b659` | Befolkning | Population is almost 11 million | NEEDS_REWRITE | Time-sensitive value needs explicit source-edition context. |
| `0f6367cf` | Naturresurser | Forestry/export/products statement | TOO_BROAD | Combines industry importance, products, and exports; evidence does not support every detail in one span. |
| `2d51a728` | Naturresurser | Hydropower uses flowing water and supplies much electricity | NEEDS_SPLIT | Two independently testable ideas. |
| `9a0b80cb` | Naturresurser | List of five resource categories | TOO_BROAD | Supported list, but weak as one testable fact. |
| `e31ab2b6` | Naturresurser | Agriculture is concentrated in the south because the north is colder | GOOD | One causal relationship with direct support. |
| `73743cc0` | Climate | Flood, drought, and wildfire risks | NEEDS_SPLIT | Multiple consequences in one proposal. |
| `75fd894c` | Climate | Several individual sustainability actions | TOO_BROAD | List should be narrowed or split into independently useful facts. |
| `9073f9b3` | Climate | Near-zero greenhouse-gas target for 2045 | GOOD | Supported; retain explicit target framing and year. |
| `ec8bd043` | Climate | Human greenhouse-gas emissions are the main cause of rapid warming | GOOD | Directly supported and learner-useful. |

Summary: 10 GOOD, 1 AMBIGUOUS, 2 NEEDS_SPLIT, 3 TOO_BROAD/NEEDS_REWRITE. No v1 proposal was auto-approved or rejected.

## Prompt v2 sample

Prompt: `knowledge-fact-generation-v2`; provider: configured Gemini; maximum three candidates per Source Section; one concurrent provider call; `FREE_ONLY`.

The first v2 pass persisted ten candidates. “Sveriges indelning” initially failed because candidate 3 supplied a non-matching quote. The validator now rejects an invalid candidate independently instead of discarding other grounded candidates. A bounded rerun of that section completed with three valid proposals. All five Source Sections therefore produced reviewable output.

| ID | Topic / objective | Proposed fact (abridged) | Evidence reference | Automated recommendation |
|---|---|---|---|---|
| `412f7f7c` | Geografi / geography | Sweden has more islands than any other country | pp. 5–6, exact island sentence | GOOD; semantic overlap with v1 `a0b309a2` |
| `96e43b2c` | Geografi / geography | Mild climate relative to latitude is caused by two currents | pp. 5–6, two-sentence causal span | GOOD; preferable to ambiguous v1 `5ea9f89d` |
| `8343d63a` | Indelning / division | Norrland covers more than half of Sweden | pp. 6–7, exact sentence | GOOD |
| `8521115a` | Indelning / division | Three regions are Götaland, Svealand and Norrland | pp. 6–7, exact sentence | GOOD |
| `d7706cbb` | Indelning / division | Sweden has 21 counties and 290 municipalities | pp. 6–7, exact sentence | GOOD |
| `bc934ad1` | Befolkning / population | About 85% of the population lives in cities | p. 7, exact sentence | GOOD; semantic duplicate of v1 `9f735e46` |
| `f9d78eef` | Befolkning / population | Most people live in the south and along coasts | p. 7, exact sentence | GOOD; more useful than v1 `1edb9690` |
| `39ba1b47` | Naturresurser / resources | Hydropower plants use flowing water to generate electricity | pp. 7–8, exact sentence | GOOD; atomic replacement for v1 `2d51a728` |
| `90464f6e` | Naturresurser / resources | Agriculture is concentrated in southern Sweden because of climate | pp. 7–8, exact causal span | GOOD; semantic overlap with v1 `e31ab2b6` |
| `a87d692a` | Naturresurser / resources | Sweden has five named resource categories | pp. 7–8, exact list | NEEDS_SPLIT; list remains broad |
| `5b5d9058` | Climate / climate change | Several Swedish flood/drought/fire risks | pp. 8–10, exact two-sentence span | NEEDS_SPLIT; compound consequence list |
| `89d27f36` | Climate / climate change | Warming causes melting ice, sea-level rise and more extreme weather | pp. 8–10, exact span | NEEDS_SPLIT; three consequences |
| `a40f540c` | Climate / climate change | Human emissions are the main cause of current rapid climate change | pp. 8–10, exact span | GOOD; semantic overlap with v1 `ec8bd043` |

V2 assessment: 10 GOOD and 3 NEEDS_SPLIT. There are no exact duplicates within v2. Nine proposals intentionally overlap semantically with preserved v1 proposals because this is a comparison regeneration; reviewers should choose one wording and reject/supersede the alternative.

## Final prompt v3 calibration

Prompt: `knowledge-fact-generation-v3`; provider: `GEMINI`; model:
`gemini-3.1-flash-lite`; usage mode: `FREE_ONLY`. The prompt retains the v2 grounding
and exact-evidence rules and strengthens atomicity: exactly one independently testable
proposition, one subject, one predicate, no unrelated comma lists, combined facts,
multiple consequences, duties, rights, or compressed historical sequences. It directs
the provider to split a candidate whenever it could reasonably become two exam
questions and to prefer fewer excellent facts.

The final run used one persistent job per Chapter 1 Source Section and requested the
maximum three candidates per section. No Chapter 2–13 content, Lessons, Questions, or
learner release was created.

| Section | Job | Requested | Generated | Result | Retries | Input / output tokens |
|---|---|---:|---:|---|---:|---:|
| Geografi, klimat och natur | `f1e56801` | 3 | 3 | COMPLETED | 0 | 1,120 / 271 |
| Sveriges indelning | `c8187692` | 3 | 3 | COMPLETED | 0 | 542 / 267 |
| Befolkning | `ba9352ae` | 3 | 3 | COMPLETED | 0 | 423 / 269 |
| Naturresurser | `68235703` | 3 | 3 | COMPLETED | 0 | 761 / 268 |
| Klimatförändringar | `046d6125` | 3 | 3 | COMPLETED | 0 | 984 / 369 |
| **Total** |  | **15** | **15** | **5/5 completed** | **0** | **3,830 / 1,444** |

Total token usage was 5,274. There were no failed or partially completed jobs and no
provider retries.

### Candidate disposition

Every candidate passed schema parsing and exact-evidence validation. The automated
review stored the outcome of all eight mandatory gates on each proposal. `GOOD`
proposals were accepted into normal Knowledge Facts, submitted by
`automated-fact-author`, and approved by the distinct
`automated-fact-reviewer`. The normal review records contain 11 `SUBMITTED` and 11
`APPROVED` actions. All approved facts are active and preserve proposal lineage,
source evidence, generation metadata, timestamps, provenance, and review history.

| Proposal | Section | Generated fact | Classification | Final disposition |
|---|---|---|---|---|
| `130badbd` | Geografi | Kebnekaise är Sveriges högsta berg. | GOOD | APPROVED |
| `42a6d40b` | Geografi | Sverige har cirka 250 000 öar. | DUPLICATE | REJECTED |
| `5c318640` | Geografi | Sverige är det största landet i Norden. | DUPLICATE | REJECTED |
| `9b4f2d06` | Indelning | Sverige är indelat i de tre landsdelarna Götaland, Svealand och Norrland. | GOOD | APPROVED |
| `a2d6875c` | Indelning | Sverige är indelat i 21 län och 290 kommuner. | GOOD | APPROVED |
| `ad62c56e` | Indelning | Norrland utgör mer än hälften av Sveriges yta. | GOOD | APPROVED |
| `565d41c6` | Befolkning | Stockholm, Göteborg och Malmö är Sveriges tre största städer. | DUPLICATE | REJECTED |
| `60426208` | Befolkning | Ungefär 85 procent av Sveriges befolkning bor i städer. | GOOD | APPROVED |
| `6f947d3c` | Befolkning | Ungefär fyra miljoner människor bor i och runt städerna Stockholm, Göteborg och Malmö. | GOOD | APPROVED |
| `32ea9c24` | Naturresurser | Sveriges största gruvor finns i Norrbottens län. | GOOD | APPROVED |
| `7a5b28e0` | Naturresurser | Vattenkraft utgör en stor del av Sveriges elproduktion. | GOOD | APPROVED |
| `dc5db52c` | Naturresurser | Det mesta av det svenska jordbruket finns i södra Sverige. | GOOD | APPROVED |
| `08782c37` | Klimat | Människors utsläpp av växthusgaser från transporter, industrier och jordbruk är den största orsaken till den snabba uppvärmningen av jordens klimat. | GOOD | APPROVED |
| `2ca4d77d` | Klimat | Smältande isar vid polerna bidrar till att havsnivån höjs. | NEEDS_SPLIT | PROPOSED |
| `884f307d` | Klimat | En varmare jord medför en ökad frekvens av extremt väder som värmeböljor, kraftiga regn och torka. | GOOD | APPROVED |

Summary:

- GOOD and automatically approved: 11/15 (73.3%)
- Automatically rejected exact duplicates: 3/15 (20.0%)
- Remaining proposal-only `NEEDS_SPLIT`: 1/15 (6.7%)
- `NEEDS_REWRITE`, `TOO_BROAD`, `AMBIGUOUS`, and `UNSUPPORTED`: 0
- Compound-fact frequency: 1/15 (6.7%)
- Grounding/evidence failures: 0

The duplicate gate correctly detected three exact facts already retained from the v1
sample and rejected them with persisted diagnostics. The only compound candidate
contains two independently testable predicates: polar ice melts and sea level rises.
It remains in Proposal state as required. This is isolated rather than a systematic
compound pattern.

During the final audit, the deterministic Swedish predicate checks were corrected to
use Unicode word boundaries. This prevents the `är` suffix in “Ungefär” from being
counted as another predicate and allows concrete noun phrases such as “Det mesta av
det svenska jordbruket” without treating them as vague pronouns. The Content service's
independent declarative-claim vocabulary was aligned with the same supported Swedish
predicates. Regression tests cover both cases.

## Verification

- `./gradlew test --no-daemon` — passed
- `./gradlew build --no-daemon` — passed
- Focused prompt, evidence/grounding, atomicity, and Content text-quality tests — passed
- Approval policy and resumable workflow script unit tests — passed
- Corpus checksum — matched
  `39a93261cc64af0122e186b7d67f57dffad573576570956a4754d22ce776aada`
- Gitleaks repository scan — no leaks found
- `git diff --check` — passed

The full Gradle test run includes the repository's corpus, evidence validator,
duplicate, retry, prompt, and fake-provider coverage.

## Decision

**NEEDS_PROMPT_ADJUSTMENT**

All five sections succeeded, exact evidence and duplicate detection passed, retries and
failed jobs were zero, `NEEDS_SPLIT` is below 15%, and there is no systematic compound
pattern. However, only 73.3% of all generated proposals became automatically approved,
below the required 85% threshold. The shortfall is caused by three exact duplicates
against preserved v1 calibration proposals, not by grounding failures. Under the
stated success formula those duplicates remain in the generated denominator, so this
run cannot be marked `READY_TO_SCALE`. Chapters 2–13 must not be generated until the
scale criterion or its duplicate-denominator policy is explicitly adjusted and
revalidated.
