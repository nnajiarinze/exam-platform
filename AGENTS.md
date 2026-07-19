# Repository instructions

## Product scope

Build an independent Swedish citizenship preparation product: structured civic knowledge, practice, explanations, timed mock examinations, progress, weak-topic identification, readiness estimates, multilingual presentation, and premium subscriptions. The platform may support other examinations later, but driving theory and a public web application are not part of the current scope. Never invent unresolved official exam rules.

## Architecture and ownership

The backend has three independently deployable Java 21/Spring Boot services:

- Content Service owns canonical facts, sources, questions, translations, review, and immutable releases.
- Learning Service owns users, learner activity, scoring, progress, readiness, subscriptions, entitlements, and a local projection of published content.
- AI Service owns AI job execution and derived outputs; it does not own factual truth.

Each service owns a separate PostgreSQL database, migrations, build, tests, configuration, and deployment artifact. Database sharing and cross-service SQL are forbidden. Communicate through versioned REST APIs, immutable snapshots, explicit events, or asynchronous jobs. Do not share Java domain libraries between services.

Read relevant files in [docs/decisions](docs/decisions) before changing architecture. The system overview is [docs/architecture/system-overview.md](docs/architecture/system-overview.md), and the ownership matrix is [docs/architecture/data-ownership.md](docs/architecture/data-ownership.md).

## Backend standards

- Use Java 21, Spring Boot, Gradle, PostgreSQL, Flyway, OpenAPI, and Docker.
- Keep domain rules separate from transport and persistence code.
- Make boundaries, transaction scope, authorization, errors, and observability explicit.
- Use backward-compatible, versioned APIs. Maintain OpenAPI contracts in `contracts/openapi`; generate clients rather than hand-copying DTOs.
- Store event schemas in `contracts/events`; version events and make consumers idempotent.
- Add an append-only Flyway migration for every schema change. Never edit an applied migration.
- Published content releases and question versions are immutable. Historical attempts retain their release and question versions.

## Frontend standards

- Use TypeScript, React Native with Expo for mobile, and React for admin.
- Consume generated OpenAPI clients and keep product/domain decisions out of generated code.
- Handle loading, empty, offline/retry, authorization, and error states.
- Meet accessible interaction, semantic labeling, and localization requirements.
- Do not create the planned public web application without an explicit product decision.

## Testing expectations

Use JUnit 5 and AssertJ for unit tests, Testcontainers for database/integration tests, and ArchUnit for service-internal architecture rules. Test migrations from an empty database, API/event contracts, publishing/import idempotency, authorization, deterministic mock scoring, and important failure paths. Keep cross-service end-to-end tests small. See [testing-strategy.md](docs/implementation/testing-strategy.md).

## AI boundaries

AI may draft, translate, simplify, classify, detect duplication/ambiguity, and produce derived personalized explanations. It may not approve or publish content, alter approved facts or correct answers, or modify a published release. Treat model output as untrusted structured input, minimize personal data, record provenance, and require human review where content becomes canonical.

## Forbidden choices

Do not introduce Kubernetes, Kafka, service discovery, a service per entity, a shared database, cross-service ORM relationships, a giant backend deployment, or leaked/copied exam question banks. Do not imply government affiliation, guarantee a pass, or provide immigration legal advice.

## Change discipline and definition of done

Stay within the ticket scope and avoid unrelated refactoring. Before implementation, read this file, the ticket, relevant ADRs, contracts, and nearby conventions. A ticket is done only when acceptance criteria and business rules are satisfied; tests and a clean build pass; migrations and contracts are updated where applicable; security, errors, logs, and observability are addressed; documentation is current; the diff contains no unrelated changes; and reviewer findings are resolved. Report changed files, verification commands/results, and remaining risks. See the full [definition of done](docs/implementation/definition-of-done.md).

