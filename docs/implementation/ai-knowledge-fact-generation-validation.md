# AI Knowledge Fact generation validation

1. Run `./gradlew clean test bootJar`, `npm --prefix apps/admin run generate:api`, and `docker compose up -d --build`.
2. Run the guarded deterministic reset/seed. Edit one active Source and add reusable factual text under **Source content for editorial generation**.
3. Sign in as a Content Author, open that Source, and choose **Generate Knowledge Facts**.
4. Select one Learning Objective, language `sv`, and several proposals. Confirm queued/running completes without a full reload.
5. Compare every proposal and visible evidence with the source. Edit one, reject one, accept one, and select then accept the remainder. Confirm the dialog says all resulting facts are drafts.
6. Open resulting facts. Confirm `DRAFT`/`UNREVIEWED`, Source and Learning Objective relationships, and stored provenance. Submit one.
7. Sign in as the same author and confirm approval is unavailable/forbidden. Sign in as the separate reviewer, review and approve it. Confirm only then can it become release-eligible.
8. Verify a URL-only Source disables generation; disabled provider returns `AI_FEATURE_DISABLED`; malformed fake output fails with `AI_STRUCTURED_OUTPUT_INVALID`; timeout retries are bounded; queued cancellation terminates; repeated idempotency/acceptance does not duplicate data; exact normalized fact duplicates are blocked.
9. Change Source text and confirm its checksum changes while old provenance does not. Restart both services and confirm jobs/proposals persist.
10. Confirm the UI uses the existing Admin shell, cards, forms, buttons, badges, errors, and responsive behavior. Stitch MCP was explicitly unavailable and unnecessary for this task.
