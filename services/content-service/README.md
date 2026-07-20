# Content Service

Independently deployable canonical content owner. It implements authenticated
exam structure, sources, knowledge facts, questions, immutable content versions,
and centralized review governance APIs. Flyway migrations are append-only and
the Content Service is the only writer to its PostgreSQL database.

Development requests require `CONTENT_DEV_IDENTITY_ENABLED=true` and both:

```text
X-Admin-Identity: dev-content-admin
X-Admin-Roles: ADMIN
```

These headers are non-production and independently forgeable. Production must
replace them with validated OIDC/OAuth access tokens. Infrastructure health is
available at `/actuator/health`; admin integration readiness is contracted at
`GET /api/v1/status` and verifies database connectivity.

The Phase 5 review API is rooted at `/api/v1/admin/reviews`. Reads are available
to recognized content roles. Claiming, unclaiming, and comments require
`CONTENT_REVIEWER` or `ADMIN`; assignment and priority changes require `ADMIN`.
Content-specific fact and question services remain responsible for approval
validation and self-review prevention.

## Phase 6 releases

Release APIs are rooted at `/api/v1/admin/releases`. They freeze explicit
question and fact versions. Publication stores one immutable JSONB snapshot and
SHA-256 checksum. Delivery uses the server-side Learning Service URL and internal
API key, records every attempt, and retries the same stored snapshot. Activation
is explicit after import. Mutations require `CONTENT_PUBLISHER` or `ADMIN`.

Operational reports use `/api/v1/admin/reports`; immutable audit search uses `/api/v1/admin/audit-events`. Learner health is fetched through authenticated service-to-service HTTP and contains aggregates only. Sensitive writes have configurable per-instance rate limits; production also requires gateway limits and tamper-resistant audit export.
