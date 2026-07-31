# Sverige i fokus — Chapter 1 Knowledge Fact review

Generated for corpus `sverige-i-fokus-v1`. This is an internal human-review package. No proposal in this report is approved, canonical, released, or an official UHR question.

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

## Human review actions

1. Review the 13 v2 proposals in the existing Admin Knowledge Fact proposal queue.
2. Prefer the v2 Gulf-current, population-distribution, and atomic hydropower wording over their v1 counterparts.
3. Split or rewrite `a87d692a`, `5b5d9058`, and `89d27f36`.
4. Reject or supersede semantic v1/v2 duplicates only after human comparison.
5. Approve selected facts through the normal Admin acceptance flow; do not create lessons or questions until that review is complete.

## Decision

**NEEDS_ONE_MORE_SAMPLE**

Evidence validation is now strict and explainable, all five sections produced persisted grounded proposals, and quota behavior is bounded. One additional Chapter 1 rerun should confirm that partial-candidate rejection works against the real provider without requiring a section-level rerun, and prompt guidance should be tightened once more to reduce compound list facts before scaling Chapters 2–13.
