# Content Service

Phase 1.5 foundation for the independently deployable canonical content owner.
It currently implements only authenticated service readiness; editorial domain
APIs and tables are intentionally deferred.

Development requests require `CONTENT_DEV_IDENTITY_ENABLED=true` and both:

```text
X-Admin-Identity: dev-content-admin
X-Admin-Roles: ADMIN
```

These headers are non-production and independently forgeable. Production must
replace them with validated OIDC/OAuth access tokens. Infrastructure health is
available at `/actuator/health`; admin integration readiness is contracted at
`GET /api/v1/status` and verifies database connectivity.
