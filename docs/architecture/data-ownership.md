# Data ownership

Database sharing and direct cross-service queries are forbidden. “Projection” is a read-only, versioned copy imported through a snapshot or event, not shared ownership.

| Data | System of record | Other-service representation |
|---|---|---|
| Users | Learning Service | Minimal subject/reference in authorized AI requests only |
| Questions/options | Content Service | Immutable release projection in Learning; draft input/output in AI |
| Knowledge facts | Content Service | Release projection in Learning; bounded request context in AI |
| Practice sessions | Learning Service | None |
| Mock attempts | Learning Service | None |
| Progress/mastery/readiness | Learning Service | None unless an explicit reporting contract is added |
| Explanations | Content Service for reviewed canonical versions | Projection in Learning; derived output in AI |
| AI jobs/results | AI Service | Caller stores job reference/accepted derived result as its own data |
| Subscriptions/entitlements | Learning Service | Stripe is external evidence, not the internal authority |
| Content releases | Content Service | Imported immutable projection/status in Learning |
| Source references | Content Service | Traceability fields in release projection; request context in AI |

Each owner validates writes, applies authorization, defines retention, publishes contracts, and runs its migrations. Services exchange data only through versioned REST APIs, explicit events, immutable snapshots, and asynchronous jobs as established in [ADR-002](../decisions/ADR-002-database-ownership.md).

