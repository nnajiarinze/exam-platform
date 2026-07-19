# Product vision

## Problem and users

People preparing for Swedish citizenship need a trustworthy way to learn relevant civic knowledge, practise recall, understand errors, and judge preparation progress. Available information may be fragmented, difficult to interpret in a learner's strongest language, or presented without traceable sources.

Initial users are adults preparing for Swedish citizenship requirements, including multilingual learners and people studying in short, repeated mobile sessions. Admin editors and reviewers are operational users, not the product's learner audience.

## Initial scope and value

The first product provides structured civic knowledge, topic practice, answer explanations, timed mock examinations, incorrect-answer review, progress and weak-topic views, readiness estimates, multiple interface/explanation languages, issue reporting, and subscription-based premium access through mobile. Admin users create, review, translate, publish, and retire content.

The core value is a traceable learning loop: learn a fact, answer original questions based on it, understand mistakes, and choose the next useful study activity. Readiness is a transparent study signal, not a forecast or guarantee.

## Platform opportunity

Reusable capabilities include versioned exam products, knowledge-to-question traceability, publishing, learner sessions, scoring, mastery, entitlements, localization, and controlled AI assistance. These boundaries allow a later exam product without placing driving-specific assumptions in the core. See [ADR-001](../decisions/ADR-001-service-boundaries.md) and the [future roadmap](future-roadmap.md).

## Explicitly out of scope

- A public web learner application in the first flow
- Driving-theory content or features
- Replication of official or leaked question banks
- Claims of affiliation with Swedish authorities
- Immigration legal advice or guaranteed outcomes
- Invented exam question counts, duration, scoring thresholds, or regulatory structure
- Kubernetes, Kafka, and service-per-entity deployment

## Assumptions requiring validation

- Learners value explanations and traceability enough to return regularly.
- Topic mastery and weak-topic recommendations improve study decisions.
- Users understand readiness estimates when limitations are clearly shown.
- The selected free/premium boundary supports conversion without blocking core trust.
- Required languages and translation-review capacity are commercially sustainable.
- Source licensing and editorial access support the intended content coverage.
- Official examination format, timing, scoring, and accessibility requirements remain unresolved product requirements until confirmed by an authoritative source.

