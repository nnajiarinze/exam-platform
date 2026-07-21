# Exam Platform

Monorepo for the exam platform's first learner flow.

## Repository layout

- `apps/mobile`: learner-facing mobile application
- `apps/admin`: internal administration application
- `services/content-service`: independently deployable content service
- `services/learning-service`: independently deployable learning service
- `services/ai-service`: independently deployable AI service
- `contracts/openapi`: shared HTTP API contracts
- `contracts/events`: shared event contracts
- `docs`: architecture, product, decision, and implementation documentation
- `infrastructure`: deployment and environment configuration
- `scripts`: repository automation

The services share this repository for coordinated development, but each service
must own its runtime, dependencies, configuration, data boundaries, build, tests,
and deployment lifecycle. A public web application is intentionally out of scope
for the first learner flow.

## Local demonstration data

The guarded, deterministic development reset and seed workflow is documented in
[docs/development/demo-data.md](docs/development/demo-data.md). It creates
clearly labelled preparation content and never represents itself as an official
Swedish citizenship examination.
