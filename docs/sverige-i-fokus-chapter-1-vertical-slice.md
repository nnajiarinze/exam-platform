# Sverige i fokus — Chapter 1 vertical-slice report

Run date: 2026-07-31

Scope: local environment only; Chapter 1 (`Landet Sverige`)

Decision: `VERTICAL_SLICE_BLOCKED`

1. **Starting commit:** `77c2788d93c14ae5a1f764cfb298e3c145413fa5`.
2. **Files changed:** AI lesson job/provider/controller/model and migration; Gemini quota/client and batch null handling; Content provenance repair, lesson serialization, release validation/snapshot; Content OpenAPI; AI integration tests; this report. Pre-existing mobile and Learning test worktree changes were not part of this run.
3. **Existing capabilities reused:** lesson drafts and review transitions; persistent question batches/jobs/proposals; Question Intelligence and canonical acceptance; immutable release snapshots and publish/deliver/activate; Learning import, study, practice, progress, and Study Again.
4. **Missing capabilities implemented:** persistent Gemini lesson-generation jobs and proposals with exact-fact validation, acceptance linkage, audit and token accounting; guarded provenance repair from immutable accepted evidence; reviewed-lesson release serialization; corpus-scoped lesson release validation. Two blocking defects discovered during execution were also fixed: nullable multi-fact batch checksums and PostgreSQL timestamp conversion in the quota circuit.
5. **Approved facts used:** 11/11 approved active facts under `Landet Sverige`, grouped across five Topics. No pending, rejected, duplicate, or Chapter 2–13 facts were used. Four null Source Section provenance fields were repaired only after accepted proposal lineage, objective mapping, and exact stored evidence independently proved the link; fact text and approval state were unchanged.
6. **Lesson jobs executed:** five successful Gemini jobs, after five initial jobs failed closed before provider use because the quota reservation schema could not reference the new lesson aggregate.
7. **Lesson drafts generated:** five, one per existing Topic, with one ordered section each and exact fact coverage of `2 + 3 + 2 + 3 + 1 = 11`.
8. **Lessons automatically approved:** five through normal `DRAFT → UNDER_REVIEW → REVIEWED` transitions using distinct author and reviewer identities.
9. **Lesson items left for review:** zero.
10. **Question jobs executed:** batch `0637e70f-fcf2-4ad5-b1ad-0727bab5b8b3` requested 22 items. Twenty obtained generation-job IDs; two failed during item initialization.
11. **Question proposals generated:** zero persisted proposals. Fourteen provider results produced no proposal that passed grounding/Question Intelligence validation.
12. **Questions automatically accepted:** zero.
13. **Questions rejected:** zero proposal records; 22 batch items failed and remain fully auditable. Failure breakdown: 14 `AI_QUESTION_GENERATION_NO_VALID_PROPOSALS`, five `AI_QUOTA_RESERVATION_FAILED`, two `BATCH_ITEM_INITIALIZATION_FAILED`, one `AI_FREE_QUOTA_PAUSED`.
14. **Questions left for review:** zero.
15. **Canonical Question count:** zero for this Chapter 1 slice.
16. **Gemini model and quota mode:** `gemini-3.1-flash-lite`, `FREE_ONLY`; paid usage remained disabled.
17. **Token usage:** successful lesson jobs used 2,946 input and 538 output tokens. At the terminal safety stop, application-tracked daily provider usage was 58,649 input and 17,808 output tokens across all local activity; the question batch itself recorded zero accepted batch usage because no item reached `GENERATED`.
18. **Internal release ID and status:** not created. Publishing content without a useful accepted question set would violate the requested success policy.
19. **Release contents:** none.
20. **Learning projection result:** not run because no valid release was created.
21. **LOCAL learner endpoint results:** not run against Chapter 1 because no release was projected.
22. **Mobile rebuild required:** `NO` for the backend/content work in this run. No mobile source was changed by this task.
23. **Mobile validation results:** blocked before mobile validation by question acceptance/release creation.
24. **Study Again result:** existing implementation and tests were audited, but this Chapter 1 release could not be exercised.
25. **Practice and progress results:** blocked because there are no accepted Chapter 1 canonical questions.
26. **Disclaimer/attribution result:** all five reviewed lessons include `Baserat på Sverige i fokus — självständigt övningsmaterial. Innehållet är inte officiella provfrågor.` in learner-facing summary metadata.
27. **Legacy-content absence result:** the Content selection is Chapter 1-only, but learner-level absence cannot be proven until a release is projected.
28. **Tests run and results:** `./gradlew test --no-daemon` and `./gradlew build --no-daemon` passed; Admin generate/lint/typecheck passed, tests passed (87, one skipped live test), and production build passed; Mobile generate/typecheck and tests passed (37/37); corpus SHA-256 matched; `docker compose config --quiet`, Gitleaks, and `git diff --check` passed.
29. **Anything deployed:** no. Only local Docker services were rebuilt/restarted.
30. **Hosted result:** not executed; LOCAL did not pass.
31. **Final decision:** `VERTICAL_SLICE_BLOCKED`.
32. **Exact next action:** after the quota circuit’s recorded reset at `2026-08-01T07:00:00Z`, call the normal provider recheck operation, retry the failed batch items through `/api/v1/admin/ai/question-generation-batches/0637e70f-fcf2-4ad5-b1ad-0727bab5b8b3/retry-failed`, and inspect why grounded outputs are failing Question Intelligence. Accept only `GOOD` proposals through the normal acceptance/review path; then create, validate, publish, deliver and activate the Chapter 1-only internal release and execute the 19 learner checks. Do not reset quota accounting, enable paid use, generate Chapters 2–13, or publish an incomplete release.

## Resumability evidence

- Lesson drafts: `0510d6cd-b1af-4b4d-bfd9-76e789b4ea17`, `db2143fc-21dc-4712-a941-554b18de2f4e`, `13d682a4-f35c-43f1-9d0e-f1bbbae3ab3c`, `5402a006-e9b0-4f80-97df-8df9c1e1136d`, `3f54d6f7-a276-4be2-ab1b-36c58bfea852`.
- Lesson job prompt: `lesson-generation-v1`.
- Question batch state: `FAILED`, requested 22, failed 22, accepted 0.
- Quota circuit: `QUOTA_PAUSED`, reason `AI_FREE_QUOTA_PAUSED`, recorded resume time `2026-08-01T07:00:00Z`.
- No databases were cleared and no approved fact text was regenerated.
