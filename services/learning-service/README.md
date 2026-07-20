# Learning Service

Independent learner-runtime service for imported published content, practice sessions, answer evaluation, and basic topic progress.

## Build and test

From the repository root:

```bash
./gradlew :services:learning-service:clean
./gradlew :services:learning-service:test
./gradlew :services:learning-service:check
./gradlew build
```

Integration tests use Testcontainers with PostgreSQL. Start the local database with `docker compose up -d learning-database`, then run the service with the database environment variables from `application.yml`. `docker compose config` validates the local topology; build the boot jar before building the service image.

## Local identity and import security

`X-Learner-Identity` is accepted only when `LEARNING_DEV_IDENTITY_ENABLED=true`, and the referenced learner must already exist. This is a local/test adapter, not production authentication. The import endpoint is disabled unless `LEARNING_INTERNAL_API_KEY` is configured and the caller supplies the same value in `X-Internal-Api-Key`.

The content snapshot and HTTP contracts are in `contracts/openapi`. No seed mechanism is included because the repository does not yet define one.

## Stage 5 import validation

The registered import route is:

```text
POST /internal/v1/content-releases/import
```

There is no servlet context path and no unversioned `/internal/content-releases/import` alias. The endpoint requires `X-Internal-Api-Key` matching `LEARNING_INTERNAL_API_KEY` and a snapshot conforming to `contracts/openapi/content-snapshot-v1.schema.json` with a valid checksum.

The Dockerfile copies the already-built boot JAR. After changing service code or migrations, rebuild both artifacts before testing Compose:

```bash
./gradlew :services:learning-service:bootJar
docker compose up -d --build learning-service
curl --fail http://localhost:8080/actuator/health
```

Import and activation are separate. Import leaves a verified projection in
`IMPORTED`; Content Service then calls
`POST /internal/v1/content-releases/{externalReleaseId}/activate`. Only one
release per exam is active, and existing sessions retain their original release.

An HTTP integration test imports, explicitly activates, and verifies the active
release and projected questions in PostgreSQL. OpenAPI validation should use:

```bash
npx -y @redocly/cli lint contracts/openapi/learning-service-v1.yaml
```
