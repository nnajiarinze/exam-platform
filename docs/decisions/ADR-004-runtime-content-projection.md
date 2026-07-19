# ADR-004: Runtime content projection

## Status

Accepted

## Context

Learner interactions must continue when editorial systems are unavailable and must pin internally consistent content.

## Decision

Learning Service maintains a local learner-facing projection imported idempotently from checksummed, published snapshots. It does not call Content Service for every question interaction. Activation is atomic and only validated releases serve new sessions. See the [publishing flow](../architecture/content-publishing-flow.md).

## Consequences

### Positive

Low latency, runtime resilience, consistent releases, and bounded Content Service load.

### Negative

Import lag, duplicated data, importer/schema compatibility, and artifact retention must be managed.

## Alternatives considered

Synchronous per-question calls couple availability. Replicating the Content database violates ownership. Event-per-entity reconstruction complicates atomic release consistency.

## Revisit conditions

Revisit snapshot format and distribution when size or import latency breaches agreed objectives.

