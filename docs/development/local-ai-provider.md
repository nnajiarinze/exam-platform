# Local AI provider

The FAKE provider is downstream of deterministic input validation and is not the primary gibberish detector. It nevertheless rejects clearly invalid direct requests and never returns `KEEP_AS_IS` or `No material ambiguity found` for obvious meaningless input.

Local development uses the deterministic, unpaid fake provider:

```env
AI_EDITORIAL_ASSISTANT_ENABLED=true
AI_PROVIDER=FAKE
AI_MODEL=deterministic-v1
AI_INTERNAL_API_KEY=local-ai-development-only
AI_SERVICE_BASE_URL=http://ai-service:8080
```

`docker compose up -d --build ai-database ai-service content-service` starts it. Normal Content Service workflows start and work when AI is disabled or credentials are absent; generation returns a controlled unavailable response.

The fake provider creates one proposal per source-text statement. Special test markers are `[[SIMULATE_TIMEOUT]]`, `[[SIMULATE_RATE_LIMIT]]`, and `[[SIMULATE_MALFORMED]]`. Automated tests never call a paid provider. Real operation uses the isolated Gemini adapter and requires an operator-managed credential, approved model, quota profile and FREE_ONLY controls.
# Phase 1.5B editorial operations

The fake provider implements all seven supported editorial operations. Split output is deterministic and ordered, Source-support and review operations persist structured findings, and ambiguity analysis can run without Source text. Evidence is copied verbatim from the request's stored Source text.

Rewrite evidence comes from the individual Source sentence with the strongest conservative key-term alignment to that proposal. The provider never falls back to the first Source sentence. If it cannot make a meaningful grounded rewrite, it returns `ALREADY_CLEAR`; it does not manufacture evidence or an unchanged usable proposal. Concurrent executions share no mutable evidence state.

Useful deterministic markers include `[[ALREADY_ATOMIC]]`, `[[THREE_WAY_SPLIT]]`, `[[PARTIALLY_SUPPORTED]]`, `[[UNSUPPORTED]]`, `[[NO_AMBIGUITY]]`, `[[SIMULATE_TIMEOUT]]`, `[[SIMULATE_UNAVAILABLE]]`, `[[SIMULATE_MALFORMED]]`, `[[SIMULATE_EMPTY]]`, `[[SIMULATE_MISSING_EVIDENCE]]`, and `[[SIMULATE_INVENTED_EVIDENCE]]`. Automated tests use only this fake provider and never call a paid API.

FAKE is limited to tests, deterministic fixtures and explicit local simulation. Normal runtime defaults to GEMINI and never falls back to FAKE. Production profile startup rejects FAKE unless the deliberate emergency development override is set. See [Gemini FREE_ONLY setup](gemini-free-only-setup.md).
