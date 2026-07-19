# ADR-001: Service boundaries

## Status

Accepted

## Context

Canonical content has an editorial lifecycle, learner runtime needs low-latency stateful behavior, and AI workloads have distinct reliability, privacy, and provider concerns. One deployment couples these concerns; a service per entity creates distributed transactions and excessive operations.

## Decision

Use exactly three initial backend deployment units: Content Service, Learning Service, and AI Service, with ownership defined in the [system overview](../architecture/system-overview.md). Reject one giant backend and service-per-entity microservices.

## Consequences

### Positive

Clear authority, independent scaling/deployment, and isolation of AI/provider failures.

### Negative

Contracts, eventual consistency, tracing, and local multi-service operation are required.

## Alternatives considered

A modular monolith reduces initial operations but cannot isolate deployments. Fine-grained microservices appear flexible but add coordination without demonstrated need.

## Revisit conditions

Revisit only when measured load, team ownership, compliance, or failure isolation cannot be addressed inside a current service boundary.

