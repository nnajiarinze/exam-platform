# ADR-005: AI boundaries

## Status

Accepted

## Context

Generative models can accelerate editorial and personalized output but are probabilistic and may be incorrect, ambiguous, or privacy-sensitive.

## Decision

AI assists with drafts and derived explanations but never owns factual truth, approves or publishes content, changes approved facts/correct answers, or modifies releases. Canonical use requires the defined human review. Inputs are minimized and outputs schema-validated with provenance.

## Consequences

### Positive

AI failures cannot silently alter assessment truth; provider changes remain contained.

### Negative

Review costs remain and some low-risk automation is intentionally constrained.

## Alternatives considered

Automatic publishing is faster but unacceptable for trust and traceability. Avoiding AI entirely loses useful drafting and personalization.

## Revisit conditions

Individual operations may gain risk-based acceptance rules after measured quality, privacy, and legal review; factual authority and publishing separation remain.

