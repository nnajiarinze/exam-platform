# AI editorial workspace

Before any operation, the current Knowledge Fact passes deterministic input-quality validation described in [editorial-input-validation.md](editorial-input-validation.md). Invalid text creates no AI job. Suspicious text is limited to appropriate analysis operations, and a provider result that ignores known quality concerns is rejected rather than displayed as a clean positive result.

## Phase 1.5B boundary

The editorial workspace reuses the Phase 1/1.5A asynchronous job, worker, retry, idempotency, audit, provider, and proposal infrastructure. It supports exactly:

- `REWRITE_FOR_CLARITY` and `SIMPLIFY_LANGUAGE` for controlled draft updates;
- `MAKE_ATOMIC` and `SPLIT_FACT` for proposed atomic facts;
- `CHECK_SOURCE_SUPPORT`, `DETECT_AMBIGUITY`, and `EDITORIAL_REVIEW_NOTES` for advisory findings.

Merge, duplicate detection across multiple facts, missing-fact discovery, automated lifecycle transitions, bulk processing, and a global job centre remain out of scope.

## Ownership and trust boundaries

Content Service owns eligibility, stored Source retrieval, role checks, acceptance, lifecycle state, and provenance. AI Service owns prompts, asynchronous execution, provider isolation, output validation, proposals, and findings. The Admin Portal calls only Content Service through its generated client. Internal AI endpoints require the service API key and are never called by the browser.

Grounded operations use only `source_reference.content_text` with a checksum. `DETECT_AMBIGUITY` is wording-only and may run without stored Source text. Prompt instructions and Source text are untrusted provider input; the server selects the prompt template and validates every result. Findings and proposals are advisory and cannot submit, approve, publish, retire, reject, or otherwise change lifecycle state.

Rewrite and simplification first require plausible support from a linked Source. Evidence is bound to one Source snapshot and must be verbatim and plausibly relevant to its proposal. Identical normalized output becomes `ALREADY_CLEAR`, not an acceptable proposal. Human-edited final text is grounded again during acceptance. See [AI editorial grounding](ai-editorial-grounding.md).

Authors may run all operations on editable facts and accept changes. Reviewers may run the three analysis operations on reviewable facts and inspect their findings, but cannot mutate fact content. Publishers can inspect completed output. Authorization is enforced in Content Service, not only in the UI.

## Output and evidence rules

Rewrite operations produce one to three proposals. Split-style operations produce one to five ordered proposals. Analysis operations produce zero or more persistent findings. Outputs must use the operation-specific schema, contain no HTML, stay within configured length/count bounds, and use only target and Source IDs from the request.

Evidence contains the stored Source ID and an exact quote from the supplied Source text. Grounded findings and proposals require valid evidence; invented quotes and unknown Source IDs fail the job. Ambiguity findings may instead identify an affected phrase. Provider payloads cannot prescribe lifecycle actions.

Findings use controlled type and severity values and have an independent `OPEN`/`DISMISSED` lifecycle. Dismissal records actor, reason, and time; it never changes the Knowledge Fact.

## Safe acceptance

Normal rewrite acceptance locks and revalidates the target before updating its existing draft.

Split acceptance uses the single supported mode `CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL`. The author explicitly selects saved proposals and confirms that:

- the original fact remains unchanged;
- every selected proposal creates a separate `DRAFT`/`UNREVIEWED` fact;
- evidence-backed Source relationships are copied to each resulting fact;
- the new facts require normal human review.

Acceptance locks the original and verifies job ownership, proposal status, target version/checksum, Source checksums, evidence membership, duplicates, and an idempotency key. It is all-or-nothing in the Content Service transaction. Each resulting fact has immutable provenance and sibling lineage. No result is automatically submitted, approved, published, or included in a release.

The Content Service records successful local acceptance before marking the corresponding AI proposals accepted over the internal API. A provider-service outage rolls back local acceptance; callers may safely retry with the same idempotency key after a committed result.
# Provider runtime and safety

Real editorial operations use the configured Gemini adapter. Tests and explicit simulations use FAKE. Provider failure pauses AI rather than silently substituting fake output. Structured provider output remains subject to all grounding, evidence, checksum, lifecycle and human-review controls described here. See [Gemini AI provider](ai-provider-gemini.md) and [quota and billing safety](ai-quota-and-billing-safety.md).
