# ADR-003: Content versioning

## Status

Accepted

## Context

Content is corrected over time, while completed and active attempts must remain explainable and reproducible.

## Decision

Question versions and published content releases are immutable. Material edits create new versions. Sessions and mock attempts retain release ID and exact question versions selected at start. Retirement affects future use, not history.

## Consequences

### Positive

Auditable publishing, deterministic scoring, safe correction, and historical explanation.

### Negative

Storage grows and retention/deletion rules must distinguish personal records from referenced content artifacts.

## Alternatives considered

In-place edits are simpler but corrupt history. Copying question text into every response duplicates data and weakens traceability.

## Revisit conditions

Revisit storage format or archival tier when scale requires it, without weakening logical immutability.

