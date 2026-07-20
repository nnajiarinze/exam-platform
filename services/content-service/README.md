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
