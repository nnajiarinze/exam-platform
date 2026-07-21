# AI editorial grounding

## Release-blocking defect

Phase 1.5 live validation found that the deterministic fake provider copied the first sentence of the first Source into every rewrite proposal. Validation proved only that the quote existed in that Source. It did not establish that the quote plausibly supported the proposal, and identical rewrites were accepted. Persistence and serialization preserved the invalid output while Admin labelled it changed.

## Source identity and validation

Each request preserves target fact/version/checksum, Learning Objective, individual Source ID/title/text/checksum, operation, job, and proposal identity. Evidence carries its exact Source ID and title. Validation resolves evidence against that job's Source snapshot and rejects unknown Sources, quotes absent from that exact Source, and obvious proposal/evidence topic mismatch. Quotes are never validated against combined Source text.

Before rewrite or simplification, Content Service and AI Service independently require conservative lexical support between the target and a linked Source. After generation, every proposal needs Source-specific evidence with key-term overlap. Acceptance repeats Source relationship, checksum, quote, no-op, and final-text grounding checks. An unsupported human edit returns `AI_EDITORIAL_FINAL_TEXT_NOT_GROUNDED`.

The deterministic overlap check blocks obvious mismatches such as municipality content supported only by a Riksdag/lawmaking quote. It is not semantic proof, fact checking, or a substitute for independent review. A production provider must supply structured claim-to-evidence mappings and may require stronger approved semantic validation.

## No-op and isolation

Unicode NFC normalization, whitespace collapsing, trimming, and terminal punctuation normalization identify harmless no-ops while preserving Swedish characters and meaningful internal punctuation. An all-no-op rewrite completes with `ALREADY_CLEAR`, persists a finding instead of a proposal, and has no acceptance action. Mixed output retains only meaningful proposals.

Provider output is request-local with no static evidence cache. Repository queries remain scoped by job/proposal. Admin query keys include fact, operation, and job; the workspace remounts for a new fact and clears when operation/job changes. Metrics count no-ops, grounding rejections, and accepted proposals without Source text. Rejection audit events use an independent transaction so an acceptance rollback cannot erase them.
