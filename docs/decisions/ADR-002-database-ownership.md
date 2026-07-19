# ADR-002: Database ownership

## Status

Accepted

## Context

Independent deployments fail if services rely on another service's schema or transaction timing.

## Decision

Each service owns a separate database/schema credentials and migrations. Direct cross-service database access, shared tables, and cross-service ORM relationships are forbidden. Exchange data only through APIs, explicit events, asynchronous jobs, and versioned snapshots. See the [ownership matrix](../architecture/data-ownership.md).

## Consequences

### Positive

Schema evolution and recovery remain within the owner; authority and security are explicit.

### Negative

Useful data is duplicated in projections and cross-service workflows are eventually consistent.

## Alternatives considered

A shared database simplifies early joins but creates hidden coupling. Database views/replicas still expose private schemas and were rejected.

## Revisit conditions

The ownership of a domain may move through an explicit migration ADR; the no-direct-access rule remains unless the deployment model fundamentally changes.

