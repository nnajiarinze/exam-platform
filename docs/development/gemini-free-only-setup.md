# Gemini FREE_ONLY setup

Deterministic local simulation uses `AI_PROVIDER=FAKE` and is clearly reported as FAKE. Automated tests always use FAKE or a local mocked HTTP server.

For real free-only testing, create a dedicated Google AI project without paid billing, create a restricted API key, and confirm the model's current free-tier limits in Google AI Studio. Store the key only in an ignored local environment file, shell secret or secret manager. Configure conservative limits below the displayed maximum:

```text
AI_PROVIDER=GEMINI
AI_USAGE_MODE=FREE_ONLY
AI_EDITORIAL_ASSISTANT_ENABLED=true
AI_GEMINI_API_KEY=<managed local secret>
AI_GEMINI_MODEL=gemini-3.1-flash-lite
AI_GEMINI_EXPECTED_BILLING_TIER=FREE
AI_GEMINI_PROJECT_LABEL=<non-secret label>
AI_GEMINI_INTERNAL_RPM_LIMIT=<operator value>
AI_GEMINI_INTERNAL_TPM_LIMIT=<operator value>
AI_GEMINI_INTERNAL_RPD_LIMIT=<operator value>
AI_PAID_USAGE_ENABLED=false
AI_MONTHLY_SPEND_LIMIT_USD=0
```

Do not commit the key, bake it into an image, expose it through Vite/Expo variables, or paste it into Admin. Start the AI Service and inspect Platform health. It must show configuration valid before a call is attempted. “Application-tracked usage” is not authoritative; confirm tier and usage in Google AI Studio.

No live Gemini test runs during build or CI. A live smoke test remains an operator action and must use FREE_ONLY controls. This implementation did not access Stitch; the existing local Admin UI was the design source.
