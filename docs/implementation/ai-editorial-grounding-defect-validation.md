# AI editorial grounding defect validation

## Reproduction

Job `c23beb16-60ab-471e-bd4f-0105de1cc583`, proposal `64f29915-cc38-4282-af8c-bccb4da1c982`, and fact `3f3c692b-9e99-411d-88ea-7192bbde6bee` reproduced the defect. The unchanged budget statement was paired with the first, unrelated lawmaking sentence in Source `215c6e3f-b5d1-59dd-ab22-4a7e55726277`.

## Automated validation

- Unicode/whitespace/punctuation no-op classification.
- Municipality/Riksdag relevance matching and mismatch rejection.
- Request-specific Source ID/title/quote selection.
- Concurrent municipality and Riksdag executions with isolated evidence.
- Typed already-clear output with zero proposals.
- Pre-generation rejection for unsupported linked Sources.
- Acceptance rejection for unsupported human-edited text.
- Persistent rejection audits and validation counters.
- Full Phase 1, 1.5A, 1.5B, Knowledge Fact, review, audit, and release regression build.

## Demo data

The deterministic seed constructs each Source text from facts linked to that Source, preventing cross-topic relationships by construction. Its local-government Source explicitly supports municipal school, elderly-care, and library responsibilities. Healthcare remains correctly regional. Run `python3 scripts/demo_data.py --help` to inspect guarded options; no destructive reset runs automatically.

## UI

Existing local Admin components and layout are reused. No Stitch MCP call, endpoint call, online search, or connectivity check was performed. Browser and direct-API results are reported separately without overstating coverage.
