# Local AI provider

Local development uses the deterministic, unpaid fake provider:

```env
AI_EDITORIAL_ASSISTANT_ENABLED=true
AI_PROVIDER=FAKE
AI_MODEL=deterministic-v1
AI_INTERNAL_API_KEY=local-ai-development-only
AI_SERVICE_BASE_URL=http://ai-service:8080
```

`docker compose up -d --build ai-database ai-service content-service` starts it. Normal Content Service workflows start and work when AI is disabled or credentials are absent; generation returns a controlled unavailable response.

The fake provider creates one proposal per source-text statement. Special test markers are `[[SIMULATE_TIMEOUT]]`, `[[SIMULATE_RATE_LIMIT]]`, and `[[SIMULATE_MALFORMED]]`. Automated tests never call a paid provider. A production adapter, credential secret, approved model, pricing policy, gateway rate limits, metrics export, and retention schedule remain required.
# Phase 1.5B editorial operations

The fake provider implements all seven supported editorial operations. Split output is deterministic and ordered, Source-support and review operations persist structured findings, and ambiguity analysis can run without Source text. Evidence is copied verbatim from the request's stored Source text.

Useful deterministic markers include `[[ALREADY_ATOMIC]]`, `[[THREE_WAY_SPLIT]]`, `[[PARTIALLY_SUPPORTED]]`, `[[UNSUPPORTED]]`, `[[NO_AMBIGUITY]]`, `[[SIMULATE_TIMEOUT]]`, `[[SIMULATE_UNAVAILABLE]]`, `[[SIMULATE_MALFORMED]]`, `[[SIMULATE_EMPTY]]`, `[[SIMULATE_MISSING_EVIDENCE]]`, and `[[SIMULATE_INVENTED_EVIDENCE]]`. Automated tests use only this fake provider and never call a paid API.
