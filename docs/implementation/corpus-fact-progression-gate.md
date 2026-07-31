# Corpus Fact progression gate

`scripts/corpusctl.py coverage` evaluates chapter readiness with policy version
`coverage-safety-v1`. The gate is an operational corpus-production decision; Content Service
validators and canonical approval transitions remain unchanged.

Progression requires complete Topic and Learning Objective coverage by approved active Facts,
an attempted generation job for every mapped Source Section, valid approved-Fact provenance and
checksums, zero unsupported approved Facts, and deterministic Topic-level Lesson sufficiency.
Source material of at least 500 characters can make a single distinct, reasonably testable Fact
`LIMITED_BUT_USABLE`; two or more distinct testable Facts are `SUFFICIENT`. Any Topic that does not
meet those conditions is `INSUFFICIENT`.

Gate outcomes are `PASS`, `PASS_WITH_WARNING`, `PARTIAL`, and `BLOCKED`. Only the first two permit
progression to Lesson planning. Approval, rejection, duplicate, classification, grounding, and
provider-failure rates remain present under `efficiency`; low approval efficiency produces a warning
instead of independently blocking a fully covered and safe chapter. Safety or integrity defects
always block, while coverage or Lesson-sufficiency gaps are resumable partial results.
The separate `warningLevel` is `HEALTHY`, `REVIEW_RECOMMENDED`,
`LOW_GENERATION_EFFICIENCY`, or `PIPELINE_DEFECT_SUSPECTED`.

The coverage event retains the legacy `uncoveredObjectives` field for existing consumers and adds
separate `uncoveredFactObjectives` and `uncoveredQuestionObjectives` fields. No checkpoint API or
Admin corpus dashboard currently exists, so this change does not add a UI or cross-service schema.
The emitted structured coverage event is the checkpoint record used by the corpus-production run.
