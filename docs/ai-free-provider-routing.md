# FREE_ONLY AI provider routing

The AI service routes structured editorial requests deterministically through `GEMINI`, `GROQ`, `CLOUDFLARE_WORKERS_AI`, then `OPENROUTER_FREE`. The priority is configurable with `AI_PROVIDER_PRIORITY`.

## Safety policy

Startup fails unless all of these invariants hold:

- `AI_BILLING_POLICY=FREE_ONLY`
- `AI_ALLOW_PAID_FALLBACK=false`
- `AI_REQUIRE_ZERO_COST_PROVIDER=true`
- `AI_ALLOW_AUTOMATIC_BILLING_UPGRADE=false`

Missing credentials, unknown pricing, unknown free capacity, unsupported structured output, open circuits, and exhausted quota cause a provider to be skipped. If no confirmed-free provider is eligible, the routing decision is persisted and the job remains queued until the earliest known reset time without consuming its retry budget.

Provider output still passes through the existing grounding, validation, proposal, review, and approval workflows. Adapters cannot write canonical learner content.

## Provider configuration

Gemini reuses the existing free-only quota reservation, usage reconciliation, and circuit implementation. Groq additionally requires explicit non-zero `GROQ_FREE_REQUEST_LIMIT` and `GROQ_FREE_TOKEN_LIMIT`; without locally enforced limits its free status remains unverified. Cloudflare enforces `CLOUDFLARE_DAILY_NEURON_LIMIT` and resets its local ledger at 00:00 UTC. OpenRouter verifies every relevant pricing field from official model metadata and rejects missing or non-zero prices; `openrouter/free` is allowed only when `OPENROUTER_ALLOW_FREE_ROUTER=true`.

All provider base URLs are restricted to their official HTTPS hosts. Secrets are read only from environment variables and are never stored or returned by Admin APIs.

See `.env.example` and `.env.hosted.example` for the complete local and hosted settings. Keep optional providers disabled until their credentials, explicit models, and free limits have been configured.

## Persistence and operations

Flyway migration V16 adds independent provider attempts, explainable routing decisions, and provider/model capacity snapshots. Successful fallback updates the owning generation job's provider/model provenance while preserving its idempotency key.

Secured Admin endpoints:

- `GET /api/v1/admin/ai/providers`
- `GET /api/v1/admin/ai/provider/operations`

The Admin dashboard is available at `/ai/providers`. Refreshing it reads persisted/non-generating status only and consumes no inference quota.
