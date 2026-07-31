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

Gemini reuses the existing free-only quota reservation, usage reconciliation, and circuit implementation. Groq uses the explicit `openai/gpt-oss-120b` model through Groq's API and provider metadata; it is selected because it is account-available, multilingual, production-class, and supports Groq JSON Schema structured output. Groq requires `GROQ_ENABLED=true`, `GROQ_MODEL=openai/gpt-oss-120b`, `GROQ_FREE_REQUEST_LIMIT=1000`, and `GROQ_FREE_TOKEN_LIMIT=8000`. Without locally enforced limits its free status remains unverified. The local secret is `GROQ_API_KEY` in the ignored repository `.env`; hosted uses the same secret name in the mode-600 `/opt/citizenship-platform/.env` used by Gemini and other hosted secrets.

A bounded synthetic verification returned `{"country":"Sweden","capital":"Stockholm"}` with 148 input tokens, 48 completion tokens, and 23 of those completion tokens reported as reasoning tokens (196 total). Groq confirmed the configured 1,000-request and 8,000-token limits in response headers. Router tests verify that Gemini remains first when ready and Groq is selected when Gemini is simulated unavailable. No learner or Sverige i fokus content was used or persisted.

Cloudflare enforces `CLOUDFLARE_DAILY_NEURON_LIMIT` and resets its local ledger at 00:00 UTC. OpenRouter verifies every relevant pricing field from official model metadata and rejects missing or non-zero prices; `openrouter/free` is allowed only when `OPENROUTER_ALLOW_FREE_ROUTER=true`.

All provider base URLs are restricted to their official HTTPS hosts. Secrets are read only from environment variables and are never stored or returned by Admin APIs.

See `.env.example` and `.env.hosted.example` for the complete local and hosted settings. Keep optional providers disabled until their credentials, explicit models, and free limits have been configured.

## Persistence and operations

Flyway migration V16 adds independent provider attempts, explainable routing decisions, and provider/model capacity snapshots. Successful fallback updates the owning generation job's provider/model provenance while preserving its idempotency key.

Secured Admin endpoints:

- `GET /api/v1/admin/ai/providers`
- `GET /api/v1/admin/ai/provider/operations`

The Admin dashboard is available at `/ai/providers`. Refreshing it reads persisted/non-generating status only and consumes no inference quota.
