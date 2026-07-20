# Local development

## Topology

Docker Compose should run three service containers (or allow services on the host), three separate PostgreSQL databases/users, object-storage emulation, and a queue substitute with semantics close to SQS. Mobile and admin run through their normal Expo/React tooling. External AI, Stripe, PostHog, Sentry, and identity providers are mocked by default; opt-in sandbox integration uses developer-specific credentials.

Kubernetes, Kafka, service discovery, and a shared database are not local dependencies. Compose DNS and explicit URLs are sufficient.

## Configuration and startup

Commit a non-secret `.env.example` documenting ports, database URLs, queue/object endpoints, issuer/audience, and provider feature flags. Real secrets stay outside Git. Each service runs its own Flyway migrations against its database on controlled startup or via an explicit migration task; no service migrates another database.

A clean-start workflow must:

1. Remove only named project containers/volumes through a documented Compose command.
2. Start dependencies and wait on health checks.
3. Build services and apply migrations from empty databases.
4. Load small, clearly non-production seed data through owned interfaces.
5. Publish/import one sample release so learner flows are testable.
6. Start clients and expose health/readiness endpoints.

## Tests and substitutes

Gradle tasks run unit, integration, ArchUnit, and contract checks per service. Testcontainers supplies isolated PostgreSQL and compatible dependencies in CI; tests must not rely on persistent Compose state. Contract fixtures simulate queue duplicates, corrupt snapshots, provider timeouts, payment webhooks, and identity claims. Seed content uses invented civic examples and source metadata, never leaked or copied question banks. Document exact commands when build files are introduced; until then, command names remain an implementation decision.

See [testing strategy](../implementation/testing-strategy.md) and [ADR-008](../decisions/ADR-008-deployment-platform.md).

## Content administration foundation

The Content Service has its own PostgreSQL database and runs on local port 8082;
port 8081 is left available for Expo Metro:

```bash
./gradlew :services:content-service:bootJar
docker compose up -d --build content-service
curl --fail http://localhost:8082/actuator/health
curl --fail http://localhost:8082/api/v1/status \
  -H 'X-Admin-Identity: dev-content-admin' \
  -H 'X-Admin-Roles: ADMIN'
```

Start the Admin Portal separately with `npm run dev` from `apps/admin`. Its
development environment targets `http://localhost:8082`. The two admin headers
are accepted only when `CONTENT_DEV_IDENTITY_ENABLED=true`; they are a local
adapter and are not suitable for deployment. Local CORS is limited to the Vite
origins configured by `CONTENT_DEV_ALLOWED_ORIGINS`.
The local default accepts `localhost` and `127.0.0.1` on any port so Vite's
automatic fallback ports work. This pattern is registered only when development
identity is enabled; deployed environments must supply explicit origins.

After changing the Content Service contract, run `npm run generate:api` and
`npm run typecheck` from `apps/admin`. Phase 2 migrations run automatically via
Flyway. Follow [the Phase 2 checklist](../implementation/admin-phase-2-validation.md)
for API and UI validation.
