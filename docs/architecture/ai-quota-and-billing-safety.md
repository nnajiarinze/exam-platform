# AI quota and billing safety

`FREE_ONLY` is the default. It requires provider GEMINI, an API key, expected tier `FREE`, a non-secret project label, positive operator-configured RPM/TPM/RPD limits, paid usage disabled, valid thresholds, and a reservable persistent circuit. Missing or ambiguous configuration fails closed before HTTP.

Use a dedicated Gemini project with no paid billing enabled and verify its tier in Google AI Studio. The application cannot infer billing status from an API key. Internal counters are conservative application-side controls, not Google's authoritative quota or billing counters. Free limits can change; quota values are never embedded in business logic and must be set below the displayed project limits with a safety margin.

Each attempt atomically locks the persistent circuit, evaluates rolling-minute and quota-timezone daily windows, reserves a request and estimated input tokens, then reconciles actual input/output usage. The most constrained dimension determines NORMAL, WARNING, CRITICAL or PAUSED behaviour; dimensions are never averaged. Reservations and circuit state survive restart and serialize concurrent workers across instances.

Minute 429 responses open a short temporary circuit. Recognizable daily exhaustion pauses until a conservative next-reset recheck. Unknown resource exhaustion is treated as a cautious daily pause. Manual and billing-safety pauses never auto-resume. Normal authoring, review, release, Practice and Mock Exam functionality remains available while AI is paused.

`PAID_ALLOWED` is restart/configuration driven and requires `AI_PAID_USAGE_ENABLED=true`, a positive monthly limit, a versioned model price profile and conservative maximum-output reservation. Application-tracked estimated/actual cost stops before the configured limit. Pricing may change and provider billing data may be delayed, so operators must maintain the price profile and provider-side budgets. No Admin UI bypasses hard limits.

Persistent alerts are deduplicated and acknowledgement is audited. Metrics cover reservation outcomes, thresholds and categorized 429s. No API keys or prompt bodies are recorded.

See Google's [rate limit model](https://ai.google.dev/gemini-api/docs/rate-limits), [pricing](https://ai.google.dev/gemini-api/docs/pricing), [billing guidance](https://ai.google.dev/gemini-api/docs/billing), and [troubleshooting](https://ai.google.dev/gemini-api/docs/troubleshooting).

