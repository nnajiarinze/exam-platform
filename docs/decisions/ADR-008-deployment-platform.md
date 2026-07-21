# ADR-008: Deployment platform

## Status

Accepted (concrete provider selected 2026-07-21)

## Context

The initial team needs reliable container deployment without operating an orchestration control plane.

## Decision

Package services as independent containers and deploy them on Render in Frankfurt. A small Nginx gateway is the only public backend entry point; Content, Learning, AI, and Keycloak use Render private networking. Admin is a Render static site. Keep development and production environments separate and use Docker Compose locally. Do not use Kubernetes.

Use a dedicated Neon PostgreSQL project containing separately owned `content`, `learning`, `ai`, and `identity` databases. This preserves database ownership and independent Flyway histories while fitting the development free tier. The citizenship project does not reuse Gojo's database. Production may move each database to a separate Neon project without changing service configuration.

## Consequences

### Positive

Portable artifacts, low operational burden, and independent release/rollback.

### Negative

Platform constraints and some vendor-specific configuration; final provider remains unresolved.

## Alternatives considered

Kubernetes is flexible but operationally disproportionate. One shared host weakens isolation and deployments. Serverless functions do not fit every service workload.

## Revisit conditions

Choose and record a concrete provider before production. Revisit orchestration only with measured scale, networking, or compliance needs.
