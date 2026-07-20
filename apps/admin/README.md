# Admin Portal

React/Vite application for content administrators. Phase 2 adds exam,
exam-version, subject, topic, ordering, and source-reference management to the
Phase 1 authentication, permissions, navigation, and error foundation.

## Requirements

- Node.js 20.19+ or 22.12+
- npm

## Local setup

```bash
npm install
npm run dev
```

Vite loads `.env.development`, which enables a non-production local identity.
Copy `.env.example` to `.env.local` to override it. Supported values are:

- `VITE_CONTENT_SERVICE_BASE_URL`: externally reachable Content Service URL.
- `VITE_DEV_ADMIN_AUTH_ENABLED`: enables the local identity entry screen.
- `VITE_DEV_ADMIN_ID`: local administrator identifier.
- `VITE_DEV_ADMIN_NAME`: local display name.
- `VITE_DEV_ADMIN_ROLES`: comma-separated roles.
- `VITE_DEV_REVIEWER_ID`, `VITE_DEV_REVIEWER_NAME`, and
  `VITE_DEV_REVIEWER_ROLES`: the separate local reviewer identity used to
  validate separation of duties.

The local identity is stored in `sessionStorage` so it survives normal routing
but not browser restarts. The generated client sends its identifier and roles as
`X-Admin-Identity` and `X-Admin-Roles` only to the configured Content Service.
These headers are forgeable development credentials, not production tokens.
Never expose an internal service API key through a `VITE_` variable.

## Commands

```bash
npm run generate:api
npm run lint
npm run typecheck
npm test
npm run build
```

API generation reads `contracts/openapi/content-service-v1.yaml` and writes the
fetch-based client to `src/api/generated`. Generated files must not contain
application or authentication decisions.

Phase 2 routes are `/exam-structure` and `/sources`. Server state remains in
TanStack Query and editor state uses React Hook Form. Add API changes to the
OpenAPI contract and regenerate; never edit `src/api/generated` manually.

Phase 3 adds `/knowledge`, `/knowledge/objectives`, and their editor routes. The
fact editor selects active sources, supports validity dates, review actions,
retirement, and immutable version history. Backend permissions remain
authoritative.

From the repository root, build and start the Content Service before the portal:

```bash
./gradlew :services:content-service:bootJar
docker compose up -d --build content-service
curl http://localhost:8082/actuator/health
```

The dashboard calls contracted `GET /api/v1/status` on the configured service
(port 8082 locally; Expo Metro commonly occupies 8081). It displays connected,
unavailable, authentication-required, forbidden, or misconfigured states without
exposing raw responses.

## Production security blockers

Production must replace development identity with the managed OIDC/OAuth 2.1
flow described by the security architecture. The gateway and Content Service must
validate access tokens and enforce backend roles. If browser authentication uses
cookies, the design must add `Secure`, `HttpOnly`, and `SameSite` controls plus a
documented CSRF mechanism. Production CORS remains undecided. The backend enables
only explicit Vite origins while development-header authentication is enabled.

Phase 2 intentionally defers production identity, tamper-resistant audit
storage, bulk operations, publishing, and question/fact workflows.
