# ADR-006: API contracts

## Status

Accepted

## Context

Independent services and TypeScript clients need evolvable, testable interfaces without sharing implementation models.

## Decision

Use versioned REST APIs with OpenAPI-first or continuously maintained specifications in `contracts/openapi`. Generate frontend clients. Use explicit compatibility rules and contract tests. Do not share a Java domain library between services.

## Consequences

### Positive

Language-neutral boundaries, discoverable APIs, generated clients, and compatibility checks.

### Negative

Contract maintenance and generation add build work; mappings between API and domain types are deliberate duplication.

## Alternatives considered

Shared Java DTO/domain libraries create release coupling. GraphQL and gRPC offer benefits but add unneeded initial client/operations complexity.

## Revisit conditions

Adopt an additional protocol only for a measured use case and document interoperability/versioning in a new ADR.

