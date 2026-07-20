# Admin Phase 5 validation

## Local startup

```bash
./gradlew :services:content-service:build
docker compose up -d --build content-service
cd apps/admin && npm run dev
```

Development identities:

- Author/admin: `dev-content-admin`, role `ADMIN`
- Reviewer: `dev-content-reviewer`, role `CONTENT_REVIEWER`

These headers and identities are development-only.

## Permission matrix

| Capability | CONTENT_AUTHOR | CONTENT_REVIEWER | ADMIN |
|---|---:|---:|---:|
| Read relevant review state/history | Yes | Yes | Yes |
| Revise and resubmit returned content | Yes | No | Yes |
| Claim/unclaim and comment | No | Yes | Yes |
| Approve/reject/require update | No | Yes | Yes |
| Assign/reassign or change priority | No | No | Yes |
| Approve own authored version | No | No | No |

## Manual scenarios

### Fact approval

1. Sign in as the administrator, create a sourced fact, and submit it.
2. Sign in as the reviewer and open **Reviews**. Confirm the fact appears in the
   default `UNDER_REVIEW` queue with subject, topic, author, and submitted time.
3. Claim it, inspect source status and URL, add a comment, and approve.
4. Confirm the item leaves the default queue immediately and history contains
   `SUBMITTED`, `CLAIMED`, `REVIEW_COMMENT_ADDED`, and `APPROVED`.

### Question return and resubmission

1. Submit a valid question as the administrator.
2. As reviewer, claim it and select `AMBIGUOUS_WORDING`, enter actionable
   feedback, and choose **Require update**.
3. As author, open the question, read the retained feedback/history, revise the
   editable draft, and resubmit.
4. Verify `RESUBMITTED` is appended without changing earlier records or moving
   comments to another content version. Approve as the reviewer.

### Governance and concurrency

1. Open one unassigned item in two reviewer sessions. Claim in session A, then
   claim using the stale version in B; B must receive `409
   REVIEW_ITEM_ALREADY_CLAIMED` and the first assignment remains.
2. Open an item, mutate it in another session, then act from the stale screen;
   expect `409` and reload guidance.
3. Submit with identity A and attempt direct approval with identity A, even with
   `ADMIN`; expect self-review rejection. Identity B can approve.
4. As admin, assign/reassign a reviewer and change priority; verify both events
   appear in history. A non-admin reviewer receives `403` for those operations.
5. Restart the Content Service and verify queue assignment, comments, priority,
   and history persist.

## Automated verification

```bash
./gradlew :services:content-service:test :services:content-service:build --no-daemon
cd apps/admin
npm run generate:api
npm run lint
npm run typecheck
npm test -- --run
npm run build
```

## Status distinctions

- Review status describes editorial assessment: `UNDER_REVIEW`, `APPROVED`,
  `REJECTED`, or `REQUIRES_UPDATE`.
- Lifecycle status describes whether content is draft, active, or retired.
- Release/publishing status will describe immutable learner delivery artifacts
  in Phase 6 and is not implemented here.

## Known limitations

- No WebSockets; TanStack Query cache updates and scoped invalidation provide UI
  consistency.
- Display names currently fall back to stable development identity IDs because
  there is no production identity directory integration.
- Comments are append-only; editing, deletion, resolution, and threading are
  intentionally deferred.
- Impact warnings are deterministic database checks, not semantic or AI review.
- Stitch was unavailable, so the workspace follows existing portal patterns.
- Publishing, releases, learner delivery changes, and AI review are absent.
