# Admin Phase 2 validation

## Startup and roles

```bash
./gradlew :services:content-service:bootJar
docker compose up -d --build content-service
curl --fail http://localhost:8082/actuator/health
cd apps/admin && npm run dev
```

Local requests use `X-Admin-Identity: dev-content-admin` and
`X-Admin-Roles: ADMIN`. Reads accept any recognized content-admin role.
Structure/source authoring accepts `CONTENT_AUTHOR` or `ADMIN`; source review,
require-update, and retirement accept `CONTENT_REVIEWER` or `ADMIN`.

## Contract and sample

- Exams and versions: `/api/v1/admin/exams`, nested `/versions`, and
  `/api/v1/admin/exam-versions/{id}`
- Subjects/topics: nested collection endpoints plus item, `/archive`, and
  `/order` endpoints
- Sources: `/api/v1/admin/sources`, item endpoints, `/review`,
  `/require-update`, and `/retire`

```bash
curl -i http://localhost:8082/api/v1/admin/exams \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Identity: dev-content-admin' \
  -H 'X-Admin-Roles: ADMIN' \
  --data '{"code":"SWEDISH_CITIZENSHIP","name":"Swedish Citizenship","countryCode":"SE","status":"DRAFT"}'
```

Updates and actions send the latest returned `version`; stale versions must
return `409`. Reorder requests contain every direct child exactly once:
`{"ids":["<first-id>","<second-id>"]}`.

## Manual UI validation

1. Sign in and create/edit an exam under **Exam structure**.
2. Create a version, two subjects, and topics; reorder and refresh to verify
   persistence.
3. Archive a record and confirm it remains present with archived status.
4. Under **Sources**, create/edit a source and exercise review,
   require-update, and retirement using the appropriate role.
5. Exercise search, filters, and pagination; verify loading, empty, validation,
   authorization, unavailable-service, and stale-conflict states.
6. Sign out and confirm development identity headers are absent.

## Deferred behavior and production blockers

Production OIDC/OAuth, gateway policy, final CORS/CSRF policy,
tamper-resistant audit storage, bulk import, question/fact editing, review
queues, and publishing remain deferred. Development identity headers are
forgeable and must never be enabled in production. Phase 3 functionality is
intentionally absent.
