# Admin Phase 6 validation

## Start locally

```bash
./gradlew :services:learning-service:bootJar :services:content-service:bootJar
docker compose up -d --build learning-service content-service
npm --prefix apps/admin install
npm --prefix apps/admin run dev
```

Confirm ports 8080 and 8082 report `UP`. Open the Vite URL and sign in as the
development administrator (`dev-content-admin`, role `ADMIN`). For least
privilege tests, use `CONTENT_PUBLISHER`. The Content Service receives its
Learning Service URL and internal API key through server variables; never put the
internal key in a `VITE_` variable.

## Successful release

1. Create an exam version, subject, topic, and learning objective.
2. Create and review an active source. Create a fact using it, submit it, and
   approve it with a different reviewer identity.
3. Create a single-choice question with one correct and one incorrect option, an
   explanation, and the fact. Submit and approve it.
4. Create a release for the exam version. Select the question and save. Verify
   its exact fact version is automatically included.
5. Validate. A small-release warning is expected but does not block publication.
6. Inspect coverage and preview, publish, record the SHA-256 checksum, and verify
   the selection becomes read-only.
7. Deliver, inspect the successful attempt, and activate. Create a practice
   session and verify it references this release.
8. Restart both services and confirm release, snapshot, and activation persist.

## Failure and governance checks

- Validate a draft, change its selection, and verify it returns to DRAFT and
  publication is blocked until revalidation.
- Try draft, under-review, retired, cross-exam, and unsupported content. Expect a
  controlled 409 or 422 response.
- Stop Learning Service and deliver. Verify `DELIVERY_FAILED` and a sanitized
  attempt. Restart it and retry; snapshot and checksum must remain identical.
- Try to edit a published release. It must be rejected; corrections require a
  new release.
- Activate release 2, then reactivate delivered release 1. New sessions must use
  release 1, old sessions keep their original release, and activation history is
  retained.
- Try publishing as `CONTENT_AUTHOR` or `CONTENT_REVIEWER`; expect 403.
- Send Learning Service an unsupported schema or a changed snapshot with the old
  checksum using the internal test key; expect rejection and no partial import.

## Automated verification

```bash
npm --prefix apps/admin run generate:api
npm --prefix apps/admin run lint
npm --prefix apps/admin run typecheck
npm --prefix apps/admin test
npm --prefix apps/admin run build
./gradlew test
./gradlew build
docker compose config
```

## Known limitations

- The learner snapshot supports single-choice questions only.
- Question authoring does not yet persist language, so this Swedish v1 snapshot
  emits `sv`; localization is outside Phase 6.
- The learner projection stores one primary fact ID per question. Content Release
  retains every linked fact version for governance.
- Production OIDC, managed secrets, scheduling, media assets, automatic rollback,
  and multi-region delivery remain follow-up work.

## Exam identifier regression

An internal Content Service code such as `SWEDISH_CITIZENSHIP` must appear in a
newly published snapshot as `swedish-citizenship`. Both lowercase and legacy
uppercase Learning Service queries resolve that same active release. Database
inspection must show only canonical `exam_id` values and at most one active
release for the slug. Migration conflict resolution selects the latest explicit
activation, then latest publication/import as a stable fallback; it preserves
all releases, checksums, snapshots, activation history, and session references.
