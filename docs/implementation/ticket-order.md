# Recommended ticket order

Dependencies use ticket IDs. Every acceptance summary includes tests appropriate to the change; the repository [definition of done](definition-of-done.md) also applies.

| ID | Title | Purpose | Dependencies | Acceptance summary | Out of scope |
|---|---|---|---|---|---|
| EP-001 | Repository bootstrap | Establish independent project boundaries and toolchains. | None | Three backend builds and two client workspaces exist; boundaries are documented and path-addressable. | Domain behavior; public web. |
| EP-002 | CI setup | Verify builds, tests, formatting, contracts, and dependency/security checks. | EP-001 | PR and main workflows fail on violated gates and cache safely. | Deployment. |
| EP-003 | Docker Compose | Provide reproducible local dependencies. | EP-001 | Three isolated databases, queue substitute, and object storage start with health checks and documented cleanup. | Kubernetes; production topology. |
| EP-004 | Content Service skeleton | Create independently deployable Java/Spring unit. | EP-001–003 | Health endpoint, owned DB, Flyway baseline, unit/integration/ArchUnit tests, and image build pass. | Content entities. |
| EP-005 | Learning Service skeleton | Create independently deployable learner runtime unit. | EP-001–003 | Same isolation/build standards as EP-004 with its own database. | Sessions and projection. |
| EP-006 | AI Service skeleton | Create isolated AI job unit without committing a provider domain model. | EP-001–003 | Health, owned DB, job boundary, disabled/mock provider, tests, and image build pass. | Real prompts; autonomous content. |
| EP-007 | Exam and exam-version model | Represent product and version boundaries. | EP-004 | Immutable version identity/status APIs and migrations exist; no assumed official rules are hard-coded. | Topics/questions. |
| EP-008 | Subject and topic model | Build hierarchical content taxonomy. | EP-007 | Valid scoped hierarchy with ordering and cycle/duplicate tests is exposed by admin contract. | Learner progress. |
| EP-009 | Source-reference model | Capture evidence and licensing metadata. | EP-004 | Required provenance, effective/access dates, validation, audit fields, migration, and API exist. | Automated source truth verification. |
| EP-010 | Knowledge-fact model | Make sourced fact versions canonical. | EP-008–009 | Immutable fact versions link objective/source; edits version rather than overwrite; tests prove traceability. | Questions. |
| EP-011 | Question draft model | Add original versioned question/options/explanations. | EP-010 | One correct option, fact/objective links, draft state, localization hooks, migration/API/tests exist. | Review or publish. |
| EP-012 | Question review workflow | Enforce human approval. | EP-011 | Submit/approve/reject transitions, separate reviewer rule, concurrency control, audit log, authorization tests exist. | Publishing. |
| EP-013 | Content-release publishing | Produce immutable approved-only snapshots. | EP-012, EP-003 | Checksummed manifest, object artifact, outbox/event, idempotency, failure and corrective-release tests pass. | Learning import. |
| EP-014 | Admin question flow | Give editors/reviewers usable end-to-end tooling. | EP-011–013 | Generated client supports draft, source linking, validation, review, publish status, accessible errors. | Learner UI; AI auto-approval. |
| EP-015 | Learning content import | Build local runtime projection. | EP-005, EP-013 | Snapshot validation, duplicate delivery, atomic activation, schema incompatibility, and history retention tests pass. | Practice selection. |
| EP-016 | Practice-session creation | Pin a stable topic-based question set. | EP-015 | Authorized request creates idempotent session with release and exact versions; empty/entitlement cases defined. | Answer evaluation. |
| EP-017 | Answer evaluation | Record and evaluate submissions deterministically. | EP-016 | Idempotent submission stores immutable response/result and returns reviewed explanation; authorization/concurrency tested. | Mastery calculation. |
| EP-018 | Progress calculation | Derive explainable topic evidence. | EP-017 | Versioned calculation updates idempotently, exposes evidence and insufficient-data state, and handles taxonomy versions. | Pass prediction. |
| EP-019 | Mobile onboarding | Capture learner language/preferences and disclosures. | EP-005, API generation | Expo flow handles create/restore, localization, independence/readiness notices, accessibility, offline/retry states. | Practice UI; public web. |
| EP-020 | Mobile practice flow | Complete topic practice and error review. | EP-016–019 | Select, answer, explain, resume, bookmark/review incorrect, and report concern through generated client. | Mock exams. |
| EP-021 | Mock blueprint | Model configurable selection/timing/scoring rules. | EP-015, verified product input | Versioned blueprint validates coverage without invented official defaults; deterministic selection tests pass. | Attempt runtime. |
| EP-022 | Mock execution | Run and preserve a timed attempt. | EP-017, EP-021 | Server-authoritative timing, pinned selection, navigation, idempotent finish, interruption recovery, and exact reproduction pass. | Readiness. |
| EP-023 | Readiness estimate | Expose a limited, explainable study indicator. | EP-018, EP-022 | Versioned formula, evidence window, confidence/unknown state, limitations, and drift tests are documented. | Guarantee or official score prediction. |
| EP-024 | Subscription entitlements | Derive premium access from Stripe state. | EP-005, identity/packaging decisions | Signed idempotent webhooks, purchase/restore/reconcile, expiry/refund, audit and authorization tests pass. | Card storage; B2B invoicing. |
| EP-025 | AI draft generation | Generate traceable editorial proposals. | EP-006, EP-010–012, provider/privacy decision | Queued structured output records provenance, validates schema, stays draft, requires review, and handles timeout/replay safely. | Publishing or changing correct answers. |

Later tickets should cover translation review, content issue triage, personalized explanation fallback, GDPR export/deletion, analytics taxonomy, backup restoration, deployment, observability, and launch hardening; order them from measured product and operational needs.

