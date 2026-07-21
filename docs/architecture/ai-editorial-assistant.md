# AI editorial assistant — Phase 1

Phase 1 proposes atomic Knowledge Facts from text explicitly stored on a Source. It does not scrape URLs, generate questions, serve learners, submit facts, approve facts, publish releases, or activate releases.

Content Service remains the public Admin boundary and owns Sources and canonical facts. AI Service owns persistent generation jobs, provider calls, structured proposals, retry state, and usage. Calls between them use an internal API key; provider credentials never reach the browser. This follows ADR-001, ADR-005, and ADR-007 without cross-database queries.

`AiProviderClient` isolates providers. `FakeAiProviderClient` is the deterministic local/test implementation. `PromptTemplateRegistry` identifies `knowledge-fact-generation-v1`; `StructuredOutputValidator` requires supported evidence, bounded atomic text, no HTML, and no response duplicates. Source material is delimited conceptually as untrusted data and instructions found inside it are never commands.

Jobs move through `QUEUED`, `RUNNING`, and a terminal state. Transient fake/provider failures use bounded exponential retry; malformed structured output is terminal. Queued jobs cancel immediately; running jobs set cancellation requested and discard eventual output. Request count, source size, hourly usage, idempotency, and internal authentication are server-enforced.

Proposals remain separate from Knowledge Facts. Acceptance creates `UNREVIEWED`/`DRAFT` facts using the existing source and learning-objective relationships. Exact normalized duplicates and repeated proposal acceptance are blocked. Submission, independent review, approval, and release eligibility are unchanged.

The database retains structured job/proposal records and accepted-fact provenance. Raw provider payloads are not stored. Failed jobs and rejected proposals are retained for editorial traceability; an operational retention period must be approved before production.
