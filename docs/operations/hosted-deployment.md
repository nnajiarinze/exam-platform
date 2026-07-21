# Hosted deployment

## Architecture and providers

The temporary development deployment uses Render free web services in Frankfurt with Neon PostgreSQL. `render.yaml` creates one Nginx gateway, three Spring services, Keycloak, and the Admin static site. Free Render services are publicly addressable and may sleep when idle; application authorization and internal service keys remain mandatory. Upgrade Content, Learning, AI, and Keycloak to private services before production. The intended public routes are:

- `/content/*` → Content Service
- `/learning/*` → Learning Service
- `/auth/*` → Keycloak
- `/healthz` → gateway liveness

AI Service has no public route. Content calls Learning and AI over Render's private network with independent shared secrets. Mobile and Admin receive only the public HTTPS gateway URL.

## External resources to create

Create one Neon project dedicated to the citizenship platform. Run `scripts/provision-neon-databases.sql` as the Neon owner to create four independently owned databases: `content`, `learning`, `ai`, and `identity`. Pass four generated role passwords as psql variables; never put them in the SQL file. Do not select, link, or migrate the Gojo project.

The initial free development setup shares one Neon compute but not databases, roles, schemas, or Flyway histories. For production, use four Neon projects if stronger infrastructure isolation is required. Use Neon pooled connection strings and TLS. Keep the total configured Hikari pools within the provider connection limit.

Convert the provider connection details into JDBC form without embedding the password:

```text
jdbc:postgresql://HOST:PORT/DATABASE?sslmode=verify-full
```

Store URL, username, and password as separate Render secrets. Each URL must select its service database and role. The checked-in `.env.example` contains names and placeholders only.

## Deploy

1. Push this repository to the Git provider connected to Render.
2. In Render, create a Blueprint from `render.yaml`.
3. Enter every `sync: false` value in the dashboard. Generate independent random values for `LEARNING_INTERNAL_API_KEY`, `AI_INTERNAL_API_KEY`, and Keycloak bootstrap credentials.
4. Set `OIDC_ISSUER_URI` on Content and Learning to `https://PUBLIC_GATEWAY/auth/realms/exam-platform`.
5. Set `KC_HOSTNAME` to `https://PUBLIC_GATEWAY/auth` and its database variables to the Neon `identity` database and identity role.
6. Deploy private services, Keycloak, then the gateway and Admin site. Flyway runs automatically during each Spring service startup and fails startup if a migration fails.
7. Create the `exam-platform` realm in hosted Keycloak without importing the local demo users. Recreate the realm roles, audience mappers, PKCE clients, exact hosted redirect URIs, and web origins from the local realm. Never use the local demo passwords in hosted environments.
8. Set Admin `VITE_API_BASE_URL` to the gateway origin and `VITE_OIDC_AUTHORITY` to its hosted realm issuer. Rebuild Admin after changing either build-time value.

Docker builds use repository-root context:

```bash
docker build -f services/content-service/Dockerfile -t citizenship-content .
docker build -f services/learning-service/Dockerfile -t citizenship-learning .
docker build -f services/ai-service/Dockerfile -t citizenship-ai .
docker build -f infrastructure/gateway/Dockerfile -t citizenship-gateway .
```

## Flyway and database validation

Each service owns only its database and Flyway history. Current migration heads are Content V11, Learning V11, and AI V8. Never edit an applied migration or manually create application tables. For a clean validation, point one service at its empty non-production database and start it with `SPRING_PROFILES_ACTIVE=hosted`; then query `flyway_schema_history` using a database administrator connection. Do not run multiple services against the same database URL.

Rollback application code by redeploying the previous Render artifact. Database migrations are forward-only; use a new corrective migration. Take a Neon backup or branch before a schema-changing production deploy and follow `backup-and-recovery.md`.

## Resend and authentication mail

Keycloak owns account verification and password-reset delivery. Configure its realm SMTP settings to use Resend:

- host `smtp.resend.com`
- port `465`, SSL enabled, authentication enabled
- username `resend`
- password supplied from `RESEND_API_KEY`
- From address from `RESEND_FROM_EMAIL`
- From display name from `RESEND_FROM_NAME`
- Reply-To from `RESEND_REPLY_TO_EMAIL` when configured

Verify the sender domain in Resend first. Gojo currently sends from a Gojo-specific address and must not be reused as Svea Study. Use a neutral address on an actually verified domain until a Svea Study domain is verified. Use Keycloak's Admin Console test-connection action as the opt-in email test and then perform a real registration verification. Do not expose a test-mail HTTP endpoint.

Learning Service also has a typed Resend gateway for server-owned transactional workflows. `EMAIL_PROVIDER=logging` is the local/test default; hosted defaults to `resend`. It validates addresses and bounds, sends text and HTML, applies timeouts and bounded transient retries, preserves an idempotency key, captures the provider message ID, records low-cardinality metrics, and never logs the API key or email body. Ordinary tests use mocked transport only.

## Mobile and Admin configuration

For an EAS preview or production environment set public build-time values only:

```text
EXPO_PUBLIC_APP_ENV=production
EXPO_PUBLIC_API_BASE_URL=https://PUBLIC_GATEWAY
EXPO_PUBLIC_OIDC_ISSUER=https://PUBLIC_GATEWAY/auth/realms/exam-platform
```

The app derives Learning as `/learning` and rejects localhost in production. A physical phone therefore needs no laptop or LAN access. Expo public variables are embedded in the bundle; never store keys or database credentials in them. Local development can keep `EXPO_PUBLIC_LEARNING_BASE_URL` as a compatibility override. Reload Expo after changing environment values.

Admin uses `VITE_API_BASE_URL=https://PUBLIC_GATEWAY`, deriving Content as `/content`; production rejects localhost. Vite variables are also public bundle values. Admin must never receive private service URLs.

## Health and smoke checks

```bash
curl -fsS https://PUBLIC_GATEWAY/healthz
curl -fsS https://PUBLIC_GATEWAY/content/actuator/health/readiness
curl -fsS https://PUBLIC_GATEWAY/learning/actuator/health/readiness
curl -i https://PUBLIC_GATEWAY/learning/api/v1/content/subjects
```

The last request should return `401` without a bearer token. AI health is checked from Render private networking, not exposed publicly. Health details are disabled and Resend outages do not fail readiness.

After login, validate one mobile topics request and one Admin status request. Publish/deliver a non-production release to verify Content → Learning private communication. Run one explicitly initiated AI request to verify Content → AI. Inspect logs for migration versions and sanitized provider outcomes, never secret values.

## Local development

`compose.yaml` remains the local path: three local PostgreSQL containers, Mailpit, Keycloak development realm, and fake AI by default. Local/test email defaults to logging and makes no Resend call. Start with:

```bash
docker compose up -d --build
```

The hosted profile is not required locally. Never copy hosted environment files into an image.

## Secret rotation and troubleshooting

Rotate a leaked credential in its provider first, update Render secrets, and redeploy only consumers. Rotating internal API keys requires coordinated Content/Learning or Content/AI updates. Rotating Keycloak signing keys invalidates affected tokens; schedule and communicate it. Rotate each Neon service role independently and verify each service separately.

Common failures:

- `Flyway`/connection errors: confirm the service-specific JDBC URL, credentials, CA trust, and pooler port.
- JWT issuer errors: the token `iss` must exactly equal `OIDC_ISSUER_URI`; JWK retrieval uses the private Keycloak host.
- CORS errors: set `CORS_ALLOWED_ORIGINS` to the exact Admin HTTPS origin, without a wildcard credential policy.
- Resend 4xx: verify the domain/from address and request fields; permanent validation errors are not retried.
- Resend 429/5xx: bounded retries occur; inspect provider status and quotas.
- Mobile still calls LAN/localhost: rebuild or reload using the intended EAS environment.

## Environment variable classification

Server secrets: all database usernames/passwords, `RESEND_API_KEY`, `AI_GEMINI_API_KEY`, internal API keys, Keycloak bootstrap credentials, and any signing secret. Server/private values: database JDBC URLs, private hostports, and JWK private URL. Public server values: gateway/admin origins and OIDC issuer. Browser build-time public values: `VITE_API_BASE_URL`, `VITE_OIDC_AUTHORITY`, `VITE_OIDC_CLIENT_ID`. Mobile build-time public values: `EXPO_PUBLIC_API_BASE_URL`, `EXPO_PUBLIC_OIDC_ISSUER`, `EXPO_PUBLIC_OIDC_CLIENT_ID`, and `EXPO_PUBLIC_APP_ENV`.

The complete secret-free variable list is in `.env.example`. External provisioning, DNS, hosted realm creation, sender verification, and real email delivery are manual steps and must be verified before production traffic.
