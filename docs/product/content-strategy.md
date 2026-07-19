# Content strategy

## Knowledge-first model

Canonical knowledge follows: reliable source → learning objective → knowledge fact → original question variants → reviewed explanations. Questions test facts; they are not factual truth. Every published question references a fact and internally traceable source. See [ADR-010](../decisions/ADR-010-knowledge-first-content-model.md).

## Sources and original writing

Editors record source title, publisher, URL or archival locator, access date, jurisdiction, effective dates where relevant, and licensing notes. Prefer authoritative primary sources, then reliable secondary sources when they add necessary interpretation. Editors write original prompts, options, and explanations from facts. Do not copy official/copyrighted question banks, commercial study materials, or leaked exam content. Facts may be restated; protected wording may not be reproduced without permission.

## Review requirements

A person other than the author reviews factual accuracy, source support, correct answer, distractor plausibility, clarity, bias, language, and learning-objective fit. High-impact corrections require the same review. AI output is never treated as review. Draft or unapproved content cannot enter a learner snapshot.

## Lifecycle and versioning

`draft → in_review → approved → released → retired` is the normal lifecycle; rejection returns an item to draft with a reason. Approval applies to a specific immutable version. Material edits create a new version. Publishing selects approved versions into a checksummed immutable release; retirement prevents new selection but does not rewrite historical attempts. See [content publishing](../architecture/content-publishing-flow.md).

## Translation handling

Canonical facts and assessment meaning are anchored to a designated source language. Each translation records source version, language, translator type, review state, and provenance. Material source changes mark translations stale. A missing approved translation falls back only according to explicit product rules; machine drafts must be labeled internally and reviewed before canonical learner use.

## User-reported issues

Reports include content, version, release, reason category, optional comment, and workflow status. Editors triage severity, reproduce the issue, compare the source, and either close with rationale or create a replacement version. Urgent misleading content may be retired from new sessions; published releases remain immutable.

## AI draft policy

AI may propose questions, distractors, explanations, simplifications, translations, and quality flags from supplied facts and sources. Store model/provider, prompt/template version, input references, output, and review decision. AI cannot create source authority, choose final correctness, approve, or publish. Never send unnecessary learner personal data.

## Quality criteria

Publishable content is source-supported, original, factually accurate, unambiguous, inclusive, appropriate to its objective and difficulty intent, localized without meaning drift, explainable, technically valid, and reviewed. Metrics such as report rate and option distribution trigger review; they do not automatically change correct answers.

