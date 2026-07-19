# ADR-009: Monorepo

## Status

Accepted

## Context

Clients, contracts, services, infrastructure, and documentation evolve together, while runtime units must remain independent.

## Decision

Use one repository with independent service builds, migrations, tests, images, and deployment pipelines. Generate consumers from versioned contracts. Monorepo does not mean monolith: changes and CI are path-aware, and no service imports another's implementation.

## Consequences

### Positive

Atomic contract/client changes, shared visibility, simpler discovery, and consistent repository policy.

### Negative

CI needs change detection and boundaries need enforcement to prevent accidental coupling.

## Alternatives considered

Multiple repositories isolate permissions but make coordinated changes and discovery harder at current team size.

## Revisit conditions

Split only when access control, scale, or autonomous team release needs outweigh coordination cost.

