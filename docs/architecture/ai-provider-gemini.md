# Gemini AI provider

The AI Service owns the Google Gemini integration. Content Service and Admin Portal continue to use provider-neutral contracts; the API key never leaves AI Service. `GeminiAiProviderClient` implements both existing provider interfaces so Knowledge Fact generation and all Phase 1.5 operations share one isolated REST adapter. There is no automatic fallback to FAKE.

The adapter uses the official `generateContent` REST API because editorial jobs are non-interactive, stateless requests and the API supports JSON-schema structured output. The model and API version are configuration driven. The development default is the stable, free-tier-capable `gemini-3.1-flash-lite` model. Responses request `application/json` with an operation-specific `responseJsonSchema`; existing server validators remain authoritative. Google may restrict older models such as Gemini 2.5 Flash for new projects even while listing them, so availability must be validated for the configured project.

Prompts preserve system/user separation and wrap Sources and targets in explicit untrusted-data delimiters. Search grounding, browsing, tools, function calling and code execution are not enabled. Only content required for the operation is sent. Provider JSON, evidence, Source identity/checksums, no-op protection, stale targets, human acceptance and independent review retain their existing validation.

Token usage comes from `usageMetadata.promptTokenCount` and `candidatesTokenCount`. A provider request ID is retained from response headers or `responseId` when available. Full prompts, Source bodies, credentials and authorization headers are not written to the usage ledger or logs.

References: [generateContent API](https://ai.google.dev/api/generate-content), [structured output](https://ai.google.dev/gemini-api/docs/structured-output), [API keys](https://ai.google.dev/gemini-api/docs/api-key), and the [current model catalogue](https://ai.google.dev/gemini-api/docs/models).

Provider-specific data-use terms depend on the selected service tier. Do not assume free-tier submissions are confidential or excluded from provider improvement. Do not submit confidential or restricted material.
