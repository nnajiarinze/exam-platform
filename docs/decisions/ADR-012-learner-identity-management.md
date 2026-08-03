# ADR-012: Learner identity management

Status: Accepted — 2026-08-03

## Decision

Learning Service owns a minimal learner identity-management BFF. Mobile continues to use Keycloak Authorization Code + PKCE in the system browser. It never receives Keycloak Admin credentials, provider secrets, or passwords.

The BFF maps the validated access-token subject to the caller's learner and Keycloak user, permits only self-service operations, requires an authentication age of at most five minutes for link removal, global logout, and deletion, and appends a subject-hashed audit event. Its service account receives only `view-users` and `manage-users`. Linking uses Keycloak's client-initiated `idp_link:<alias>` action; unsafe automatic email linking is forbidden.

Account deletion is a persisted two-step operation. Following recent authentication and the literal `DELETE` confirmation, Keycloak identity and sessions are deleted, direct learner PII and the external subject are anonymised, and pseudonymous learning history remains attached to the internal learner UUID. Interrupted operations are retried idempotently. Shared curriculum is never affected.

## Consequences

- The final usable login method cannot be unlinked.
- Provider identity does not change the stable Keycloak subject or learner UUID.
- Provider configuration is fail-closed when protected credentials are absent.
- Audit records contain correlation IDs and subject hashes, never tokens, emails, credentials, or provider secrets.
- There is no restoration window after confirmed deletion.
