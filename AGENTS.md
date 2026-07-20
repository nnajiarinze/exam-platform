# UI / Design Workflow

## Mandatory: Use Stitch for all UI and UX design work

For any task involving user interfaces, user experience, visual design, layouts, wireframes, prototypes, or frontend screen planning, ALWAYS use Stitch as the primary design tool before writing implementation code.

This includes (but is not limited to):

- New pages
- New screens
- Dashboards
- Admin interfaces
- Forms
- Tables
- Navigation
- Mobile layouts
- Responsive layouts
- Empty states
- Loading states
- Error states
- Design system updates
- Component composition
- Information architecture
- UX improvements
- Visual redesigns

### Required workflow

1. Understand the product requirements.
2. Create or update the design in Stitch.
3. Review the generated design for usability and consistency.
4. Use the approved Stitch design as the source of truth.
5. Only then implement the UI in code.

Never skip the Stitch step unless explicitly instructed by the user.

### Existing designs

If a Stitch design already exists:

- Update the existing design instead of creating a new one whenever possible.
- Keep layouts consistent across the application.
- Reuse existing components and patterns.

### Backend-only work

Do NOT use Stitch for:

- Backend services
- APIs
- Database work
- Infrastructure
- Business logic
- Unit tests
- Integration tests
- DevOps
- Documentation that has no UI implications

### UI implementation

When implementing a UI:

- Follow the Stitch design closely.
- Do not invent layouts that differ significantly from the approved design.
- Maintain spacing, hierarchy, accessibility, responsiveness, and consistency with existing screens.

### If requirements are ambiguous

If a UI requirement is incomplete:

- Produce the best UX in Stitch first.
- Make reasonable assumptions.
- Clearly document assumptions.
- Do not block implementation waiting for perfect specifications unless critical information is missing.





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

