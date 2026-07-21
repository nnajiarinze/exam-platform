# Gemini provider validation

Automated validation uses synthetic responses only. It covers structured JSON and schemas, token metadata with missing-metadata tolerance, request IDs, Source delimiters, no tools/search, missing credentials, malformed output, minute/daily 429 classification, persistent migrations, atomic quota reservations, reconciliation and concurrent stop-threshold enforcement. Existing Phase 1/1.5 suites remain deterministic under FAKE.

No live key was supplied and no live Gemini request was made. Live validation is pending operator execution: configure the dedicated free-only project, verify status, run one grounded rewrite and one Source Support job, confirm GEMINI provenance and human-only acceptance, lower test limits to exercise warning/critical/pause, simulate 429s with the mocked suite, restart AI Service to confirm persistence, and confirm no FAKE fallback or paid enablement.

The existing Admin Portal was the visual source. Stitch MCP was not used, and no online Stitch lookup or connectivity check was performed.

