# ADR-008: Deployment platform

## Status

Accepted

## Context

The initial team needs reliable container deployment without operating an orchestration control plane.

## Decision

Package services as containers and deploy independently on a managed platform such as ECS Fargate, App Runner, Render, Railway, or Fly.io. Keep development and production environments separate. Use Docker Compose locally. Do not use Kubernetes initially.

## Consequences

### Positive

Portable artifacts, low operational burden, and independent release/rollback.

### Negative

Platform constraints and some vendor-specific configuration; final provider remains unresolved.

## Alternatives considered

Kubernetes is flexible but operationally disproportionate. One shared host weakens isolation and deployments. Serverless functions do not fit every service workload.

## Revisit conditions

Choose and record a concrete provider before production. Revisit orchestration only with measured scale, networking, or compliance needs.

