# Controlled OpenRouter paid fallback

Editorial routing is free-first and fixed in this order:

`GEMINI -> GROQ -> OPENROUTER_FREE -> OPENROUTER_PAID -> PAUSE`

`OPENROUTER_PAID` is eligible only when `OPENROUTER_ALLOW_PAID=true`. The global
automatic billing upgrade remains disabled. The application discovers paid models
from the authenticated OpenRouter catalog, requires JSON Schema structured output,
temperature control, output-token limits, and at least 32K context, and then pins
one immutable catalog selection in `ai_openrouter_paid_model`. The already-validated
Groq editorial model is preferred when the same model is available and eligible;
otherwise the lowest blended-price eligible catalog entry is selected. Restarting
the service reloads the pinned row rather than silently switching models.

## Budget safety

`OPENROUTER_BUDGET_USD` is an application lifetime budget for the active database,
not an OpenRouter account limit. Before a request, one atomic SQL statement reserves
the model's maximum cost using the full context window, requested maximum completion,
reasoning price, and fixed request price. If the reservation cannot fit, routing
stops with `PAID_BUDGET_EXHAUSTED`. The database constraint also prevents spent plus
reserved cost from exceeding the configured budget under concurrent workers.

OpenRouter's returned `usage.cost` is authoritative for reconciliation. Prompt,
completion, and reasoning tokens, latency, estimated and actual cost, budget before
and after, request ID, and routing reason are retained in
`ai_paid_request_accounting`. Failed requests release the reservation unless the
provider returned billable usage. Neither APIs nor the dashboard serialize provider
credentials.

Required runtime configuration:

```text
OPENROUTER_API_KEY=<existing secret>
OPENROUTER_ALLOW_PAID=true
OPENROUTER_BUDGET_USD=14.00
```

Turning `OPENROUTER_ALLOW_PAID` off prevents every paid reservation regardless of
the persisted model or remaining budget.
