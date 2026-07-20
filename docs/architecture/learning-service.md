# Learning Service

## Responsibilities and owned data

The service owns user/account-related state, profiles, language preferences, practice sessions, mock attempts, responses, scoring results, mastery evidence, readiness estimates, recommendations, bookmarks, incorrect-answer review state, subscriptions, entitlements, and the learner-facing content projection.

## Major modules

- Identity profile and preferences
- Content import/projection
- Practice selection and sessions
- Mock blueprint/execution
- Response evaluation and scoring
- Progress, mastery, readiness, and recommendations
- Bookmarks/review
- Billing webhook ingestion and entitlements

## Runtime flows

For practice, the service selects eligible question versions from one imported release, creates a session with a stable selection, evaluates idempotent submissions, and records evidence. For a mock, it validates a configurable blueprint, snapshots selected question/version identifiers and server timing, accepts responses, and finalizes exactly once. Official counts, duration, and thresholds are unresolved configuration requirements and must not be hard-coded.

Scoring is deterministic for the pinned versions and records rule/configuration version. Progress aggregates explainable evidence by objective/topic. Readiness includes its model version, evidence window, minimum-evidence state, and limitations; it is not a pass prediction. Entitlements derive from verified subscription/provider events, not client claims.

## Local content projection

The importer consumes published release events, downloads the checksummed snapshot, validates schema/checksum/references, and atomically activates a complete projection. Re-import of the same release is idempotent. Previous releases remain available while referenced by historical attempts. Learner reads do not depend on Content Service availability. See [publishing flow](content-publishing-flow.md).

## Failure scenarios

- Missing/corrupt release remains inactive and is retried/alerted.
- Duplicate answers and completion calls return the original result by idempotency key.
- Network loss does not move server-authoritative mock timing.
- Delayed subscription webhook leaves last verified entitlement and reconciles later.
- Insufficient mastery evidence produces an explicit unknown/low-confidence state.
- Projection taxonomy changes require a versioned mapping, not silent historical rewrites.

## Non-responsibilities

The service does not author, approve, mutate, or publish canonical content; call the Content Service for every question; execute general AI workloads; decide official exam rules; or access other service databases.

## Stage 5 implementation notes

The initial implementation uses Java 21, Spring Boot, JDBC, PostgreSQL, and Flyway in `services/learning-service`. Content snapshot schema `1.0` is defined in `contracts/openapi/content-snapshot-v1.schema.json`; its SHA-256 covers canonical JSON with the checksum field omitted. Import activation uses a transaction, a PostgreSQL advisory lock per exam version, and a partial unique index enforcing one active release per exam version. A delayed release older than the active release is retained as `SUPERSEDED` and cannot roll runtime traffic backward.

Practice supports `TOPIC` and `MIXED`. Selection is injectable for deterministic tests, prefers distinct knowledge facts, and persists the complete question set before returning. Composite database constraints ensure sessions, selected questions, responses, learners, answer options, and content releases cannot be cross-wired. Answer submission locks the learner session and session question, relies on a unique response constraint, updates topic progress atomically, and completes the session with the last accepted answer.

`X-Learner-Identity` is a temporary development/test adapter and is disabled by default. It is not production authentication. The internal import endpoint requires a configured temporary service key. Both must be replaced by the identity/workload-identity decisions described in [security and privacy](security-and-privacy.md) before production.

## Stage 7 mock examination notes

Mock examination blueprints are database configuration, with topic allocations, duration, total questions, and pass threshold kept out of application constants. Starting an attempt pins the active content release, selected question order, and a snapshot of the scoring/timing configuration. Selection requires distinct questions and knowledge facts. Answers remain editable and correctness remains hidden until final submission.

Server timestamps determine remaining time. Request-time checks and a transactional scheduled expiry scan finalize expired attempts. Submission calculates and permanently stores the deterministic score, configured pass/fail result, topic breakdown inputs, and version-pinned incorrect-question review.
