# ADR-011: Hetzner VM and Neon EU deployment

## Status

Accepted

## Context

The five Render Free backend services sleep independently, share a limited
instance-hour allowance, and communicate over public URLs. Their cold-start time
is longer than several application-to-application timeouts. The existing Neon
compute is in US East while the application services and primary users are in
Europe.

The three application services must remain independent deployment artifacts and
must continue to own separate logical databases.

## Decision

Run Nginx, Content Service, Learning Service, AI Service, and Keycloak as five
containers on one always-on 8 GB Hetzner Cloud VM in Nuremberg. Nginx is the only
container that publishes host ports. Internal calls use Docker DNS and retain
service API-key authentication.

Use one paid Neon project in AWS Frankfurt (`eu-central-1`) with separate
`content`, `learning`, `ai`, and `identity` databases and service roles. Normal
application traffic uses the pooled TLS endpoint. Administrative migration and
logical backup operations use the direct TLS endpoint.

Build immutable commit-SHA images in GitHub Actions, publish them to GHCR, and
deploy through a manually approved workflow. Keep the Admin Portal on Render
static hosting. Keep the old Render backend and US-East Neon project during the
cutover rollback window.

## Consequences

### Positive

- Backend services no longer sleep.
- Service traffic stays on a private Docker bridge.
- Application and database traffic remains in the EU.
- The existing service and database ownership boundaries are preserved.
- Compute cost is predictable and substantially lower than five managed JVM
  instances.
- Deployment and rollback operate on immutable images.

### Negative

- The single VM is a shared failure domain.
- OS patching, firewalling, Docker operation, capacity monitoring, and deployment
  recovery are now project responsibilities.
- Container deployment is briefly overlapping and constrained by 8 GB RAM.
- Neon remains an external network dependency.

## Alternatives considered

Paying for five always-on managed Render instances preserves infrastructure
isolation but costs materially more. Combining the Java services would violate
accepted service boundaries. Running PostgreSQL on the VM would reduce managed
cost but weaken persistence and recovery and is rejected.

## Revisit conditions

Move individual services to separate hosts or managed containers when measured
resource contention, availability targets, or team ownership justify it. Move
databases to separate projects when the shared Neon compute becomes an
unacceptable failure or performance boundary.
