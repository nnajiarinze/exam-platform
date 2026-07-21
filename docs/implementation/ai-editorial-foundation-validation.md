# AI editorial foundation validation

## Implemented scope

Phase 1.5A adds persistent, asynchronous editorial jobs for `REWRITE_FOR_CLARITY` and `SIMPLIFY_LANGUAGE` on one editable draft Knowledge Fact. The deterministic fake provider supports local and CI validation without external credentials.

## Manual validation checklist

1. Start local PostgreSQL infrastructure.
2. Start AI Service and confirm its health endpoint is up.
3. Start Content Service and confirm its authenticated status endpoint is ready.
4. Start the Admin Portal using its local environment configuration.
5. Set `AI_EDITORIAL_ASSISTANT_ENABLED=true`, `AI_PROVIDER=FAKE`, and matching `AI_INTERNAL_API_KEY` values.
6. Sign in as a Content Author.
7. Open an unreviewed or requires-update draft Knowledge Fact owned by that author.
8. Choose Rewrite for Clarity and generate a suggestion.
9. Observe the persistent `QUEUED`, `RUNNING`, and `COMPLETED` states.
10. Inspect original/proposed text, rationale, warnings, and verbatim source evidence.
11. Edit one proposal and save it.
12. Accept it explicitly.
13. Confirm the same fact ID remains `DRAFT` with the same review state.
14. Verify editorial provenance contains original, proposed, final text, actors, prompt/model, and checksums.
15. Verify the normal content audit records the fact update and the AI audit records proposal acceptance.
16. Run Simplify Language.
17. Reject one proposal and confirm it cannot subsequently be accepted.
18. Generate and accept another valid proposal.
19. Generate a proposal, then modify the fact or linked Source before acceptance.
20. Confirm acceptance returns `409 AI_EDITORIAL_PROPOSAL_STALE` and leaves the newer human work intact.
21. Remove stored content from a linked Source and confirm generation is blocked with `AI_EDITORIAL_SOURCE_CONTENT_UNAVAILABLE`.
22. Disable the feature and confirm a controlled `AI_FEATURE_DISABLED` response.
23. Stop AI Service and confirm the Admin Portal shows the provider-unavailable error without crashing.
24. Use `[[SIMULATE_TIMEOUT]]` in local stored source content and confirm bounded retry followed by a terminal result.
25. Cancel a queued or running job and confirm it reaches `CANCELLED` without proposals being applied.
26. Restart AI and Content services and confirm jobs, proposals, and provenance persist.
27. Submit an accepted revision through the normal workflow and confirm an independent reviewer is still required.
28. Confirm the UI remains inside the existing Knowledge Fact editor and reuses local forms, cards, buttons, badges, loading, and error patterns.
29. Confirm no Stitch lookup, online design check, chatbot, job centre, or extra operation was introduced.

## Automated verification

- AI Service tests cover both operations, deterministic evidence, instruction-as-data behavior, controlled failures, and the existing structured-output validator.
- Content migration tests verify V10 upgrades and the immutable editorial provenance table.
- Admin component tests verify the two-operation boundary and truthful ineligible/source-unavailable states.
- OpenAPI generation, lint, typecheck, tests, and production build must all pass.

## Explicitly deferred

The other eight proposed editorial operations, findings/dismissal, multiple targets, bulk application, a global job centre, automated review decisions, and publishing are not part of Phase 1.5A.

## Known limitations

The fake provider is intentionally deterministic and does not represent the linguistic quality of a paid model. Cross-service acceptance uses idempotent proposal/provenance identifiers rather than distributed transactions. The Admin UI provides the focused fact-editor workflow only; operational job search and bulk processing are deliberately absent.

Stitch MCP was unavailable for this phase and was not accessed. No online Stitch lookup or connectivity check was performed; the existing local Admin Portal implementation was the visual source of truth.
