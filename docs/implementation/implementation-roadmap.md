# Implementation roadmap

Phases are ordered learning milestones, not fixed dates. Official examination rules remain unresolved and configurable.

## 1. Repository and CI foundation

- **Goal:** Establish enforceable independent builds.
- **Deliverables:** Gradle/TypeScript workspace conventions, contract generation, CI, Compose, formatting, security scanning.
- **Dependencies:** Accepted ADRs.
- **Exit criteria:** Clean checkout can build/test empty service skeletons and start isolated dependencies.
- **Out of scope:** Domain endpoints, cloud production deployment.

## 2. Content Service foundation

- **Goal:** Represent sourced, versioned civic knowledge.
- **Deliverables:** Service/database, exam taxonomy, sources, facts, questions, Flyway migrations, admin authorization baseline.
- **Dependencies:** Phase 1, source/editorial decisions.
- **Exit criteria:** Draft aggregate persists with traceability and integration tests.
- **Out of scope:** Publishing, learner serving, AI generation.

## 3. Admin application

- **Goal:** Let authorized staff manage the draft/review lifecycle.
- **Deliverables:** Generated client, author/reviewer screens, validation, audit-visible status, issue triage baseline.
- **Dependencies:** Phase 2 APIs and identity choice.
- **Exit criteria:** Separate author/reviewer can take original content to approved state.
- **Out of scope:** Public administration access, bulk AI publishing.

## 4. Content publishing

- **Goal:** Produce immutable learner snapshots.
- **Deliverables:** Release validation, manifest/schema, object storage, checksum, outbox/event, retirement/corrective release procedure.
- **Dependencies:** Phases 2–3, queue/object choices.
- **Exit criteria:** Approved-only snapshot publishes idempotently and failed builds never announce.
- **Out of scope:** Direct Content-to-mobile serving.

## 5. Learning Service foundation

- **Goal:** Establish learner authority and local content.
- **Deliverables:** User/preferences model, release importer, atomic projection, session/response foundations.
- **Dependencies:** Phases 1 and 4.
- **Exit criteria:** Duplicate import is harmless and a session pins a valid release.
- **Out of scope:** Advanced readiness, payments.

## 6. Mobile practice flow

- **Goal:** Complete the first learner feedback loop.
- **Deliverables:** Expo app, onboarding/language preferences, topic selection, question/answer/explanation, issue reporting, incorrect review.
- **Dependencies:** Phase 5 and approved seed content.
- **Exit criteria:** A learner completes and resumes a versioned practice session with accessible error states.
- **Out of scope:** Public web, mock exams, driving theory.

## 7. Mock examinations

- **Goal:** Provide reproducible timed simulations without asserting unknown official rules.
- **Deliverables:** Configurable blueprint, server timer, stable selection, idempotent completion, score/topic breakdown.
- **Dependencies:** Phase 6 and verified product configuration.
- **Exit criteria:** Deterministic tests reproduce an attempt from pinned versions.
- **Out of scope:** Pass guarantees or invented official settings.

## 8. Payments

- **Goal:** Enforce premium access from verified subscription state.
- **Deliverables:** Stripe sandbox flow, signed idempotent webhooks, entitlement policy, restore/reconcile, support audit view.
- **Dependencies:** Identity and product packaging decisions.
- **Exit criteria:** Purchase, renewal, expiry, cancellation, replay, and outage paths are tested.
- **Out of scope:** Storing card data, B2B billing.

## 9. Controlled AI

- **Goal:** Add measurable assistance without transferring truth ownership.
- **Deliverables:** AI Service, provider adapter, schemas/provenance, queued drafts, reviewed acceptance, safe personalized explanation fallback.
- **Dependencies:** Phases 2–6, privacy/provider review.
- **Exit criteria:** AI output cannot bypass review/publishing and failures degrade safely.
- **Out of scope:** Autonomous approval, canonical answer changes.

## 10. Retention and optimization

- **Goal:** Improve useful return and study selection from evidence.
- **Deliverables:** Explainable mastery/readiness versions, recommendations, notification experiments where consented, PostHog taxonomy.
- **Dependencies:** Sufficient real usage and analytics consent.
- **Exit criteria:** Metrics are documented, uncertainty shown, and experiments have guardrails.
- **Out of scope:** Opaque pass prediction, manipulative engagement.

## 11. Production hardening

- **Goal:** Meet launch reliability, security, privacy, and recovery needs.
- **Deliverables:** Chosen managed platform, environment isolation, threat review, load tests, alerts/SLOs, backup restore, GDPR workflows, runbooks.
- **Dependencies:** Prior production candidate and provider decisions.
- **Exit criteria:** Release rehearsal, restore exercise, incident exercise, privacy/security review, and clean deployment succeed.
- **Out of scope:** Kubernetes/Kafka migration and unvalidated second products.

