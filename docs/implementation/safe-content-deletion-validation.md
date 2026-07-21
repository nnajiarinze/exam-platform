# Safe content deletion validation

## Policy

Physical deletion is limited to non-historical draft or equivalent unused content. The Content Service locks the target, evaluates lifecycle and dependency rules explicitly, and rejects unsafe deletion with a controlled conflict response. `ADMIN` does not bypass immutability.

Supported entities are exams, exam versions, subjects, topics, learning objectives, sources, knowledge facts, questions, and releases. Structure and sources require `ADMIN`; authors may delete only their own eligible facts and questions; publishers may delete only their own eligible draft releases.

The service never queries the Learning Service database. Learner-runtime safety follows the service boundary: content can reach Learning only through a published release, and any released entity is permanently ineligible for deletion. This preserves database ownership while protecting projected learner history.

## Owned temporary children

No broad cascade was added. The service explicitly removes these transient children only after all safety checks pass:

- The sole unreviewed draft fact version and its source links.
- The sole unreviewed draft question version; its options, tags, and fact links use their existing version-owned cascades.
- Draft release selections and validation runs.

Historical versions, reviews, snapshots, deliveries, activations, releases, and audit events are never cascaded.

## Manual validation

1. Create an empty draft topic and delete it as `ADMIN`; verify HTTP 204 and a DELETE audit entry.
2. Add an objective to a draft topic; verify deletion returns `ENTITY_HAS_DEPENDENCIES` and recommends archive.
3. Archive the referenced topic through the existing archive action.
4. Create and delete an empty draft exam version.
5. Add a subject or release to a draft exam version; verify deletion is blocked.
6. Create an unused fact as a content author and delete it with the same identity.
7. Submit or approve a fact; verify deletion is permanently blocked and retirement is recommended.
8. Repeat the owner and lifecycle checks for a question.
9. Create a draft release as a publisher and delete it with the same identity.
10. Publish a release and verify deletion is permanently blocked.
11. Search audit events by the deleted entity ID and verify actor, reason, final state, and timestamp remain available.
12. Attempt deletion without authentication, with reviewer-only authorization, and after adding a dependency; verify 401, 403, and controlled 409 responses respectively.

## Automated verification

```bash
./gradlew :services:content-service:test :services:content-service:build
npm --prefix apps/admin run generate:api
npm --prefix apps/admin run typecheck
```

Admin UI implementation and frontend tests require an approved Stitch design before they can be completed.
