# Content publishing flow

Content approval, release validation, publication, delivery, and activation are
separate decisions. Approval makes an exact version eligible. Validation checks
a frozen selection. Publication stores immutable canonical JSON plus SHA-256.
Delivery transfers that stored snapshot over authenticated internal HTTP.
Activation selects the imported release used by new learner sessions.

The cross-service exam identifier is a canonical lowercase kebab-case slug.
Content Service maps its internal editorial code through this boundary; for
example `SWEDISH_CITIZENSHIP` and `Swedish Citizenship` both produce
`swedish-citizenship`. Learning Service accepts legacy case/separator variants
at inbound boundaries, but persists and returns only the canonical identifier.

```text
DRAFT -> VALIDATED -> PUBLISHED -> DELIVERED -> ACTIVE -> RETIRED
                         |             ^
                         +-> DELIVERY_FAILED -- retry
```

Editing invalidates prior validation. Corrections to published content require a
new release. Reactivating a previously delivered release is manual rollback.

```mermaid
sequenceDiagram
  actor Admin
  actor Reviewer
  participant CS as Content Service
  participant DB as Content DB
  participant OS as Object storage
  participant Q as Queue
  participant LS as Learning Service
  participant LP as Learning projection
  participant Learner
  Admin->>CS: Create knowledge fact draft
  CS->>DB: Store version + source link
  Admin->>CS: Create question draft
  CS->>DB: Store question version + fact link
  Reviewer->>CS: Approve specific versions
  CS->>DB: Record review decisions
  Admin->>CS: Create release from approved versions
  CS->>DB: Status = building; freeze manifest
  CS->>OS: Publish snapshot(checksum)
  CS->>DB: Status = published; outbox event
  CS->>Q: ContentReleasePublished
  Q-->>LS: Deliver event (possibly more than once)
  LS->>OS: Download snapshot
  LS->>LS: Verify schema, manifest, checksum
  LS->>LP: Import and atomically activate release
  Learner->>LS: Start practice/mock
  LS-->>Learner: Questions pinned to active release
```

## Integrity and retry rules

Release identity is globally unique and its manifest, members, and checksum become immutable at publish. Status is `building`, `published`, or `failed`; only `published` may be announced/imported. The event includes release ID, product/exam version, snapshot schema version, object locator, checksum algorithm/value, and creation time.

Publishing uses an idempotency key and transactional outbox (or equivalent). Repeating a command returns the same release or fails on different payload. The importer records release/checksum and treats duplicate delivery as success. It downloads to staging, validates all references, and activates atomically; partial projections never serve learners.

## Rollback and history

Rollback means publish/activate a new corrective release or reactivate a previously valid release for new sessions; never mutate or delete a published release to simulate rollback. Every practice session and mock attempt stores its release ID and exact question/version selection. Referenced projections are retained according to policy so an active or historical attempt remains reproducible. See [ADR-003](../decisions/ADR-003-content-versioning.md) and [ADR-004](../decisions/ADR-004-runtime-content-projection.md).
