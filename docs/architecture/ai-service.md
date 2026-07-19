# AI Service

## Responsibilities and boundaries

AI Service executes controlled draft and derived-content operations: draft questions/explanations/translations, simplification, duplicate/ambiguity flags, and personalized explanations. It owns AI job state, provider requests, provenance, policy decisions, cached derived outputs, and operational metadata.

It may not own facts, approve content, select canonical correct answers, publish, modify an approved version, or write a published release. Callers must treat results as untrusted proposals. See [ADR-005](../decisions/ADR-005-ai-boundaries.md).

## Execution modes

Use synchronous calls only for bounded, latency-capped learner derivations with a safe reviewed fallback. Use queued jobs for content generation, translation drafts, batch duplicate detection, and other slow/retriable work. Requests contain an operation type, schema version, authorized source material, locale, constraints, correlation/idempotency key, and no unnecessary personal data.

Outputs must validate against operation-specific JSON schemas and include provider/model, prompt-template version, input references, timestamps, safety flags, and confidence/limitations where meaningful. Invalid output is rejected or retried within a bounded policy; it is never coerced into canonical content.

## Provider abstraction and caching

A narrow provider adapter maps internal operations to vendor APIs, preserving vendor-neutral request/result records. Avoid lowest-common-denominator domain abstractions. Cache only deterministic-enough derived results keyed by operation, normalized inputs, source/content version, locale, template version, model configuration, and policy version. Encrypt cached content and apply retention; never cache secrets or unrestricted personal prompts.

## Failure handling and review

Timeouts, rate limits, malformed output, policy failures, and provider outages yield explicit states and metrics. Queue retries use backoff, attempt limits, idempotency, and dead-letter handling. Personalized explanation failure falls back to an approved explanation. Content-operation results remain drafts and require human review under the [content strategy](../product/content-strategy.md). Log provenance and decisions without logging raw sensitive prompts.

