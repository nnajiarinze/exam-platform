# Platform Infrastructure Review Summary

**Review date:** 2026-07-30  
**Evidence labels:** **Verified** means observed through a provider API, public endpoint, or current repository file. **Repository-recorded** means documented as completed but not independently confirmed in the provider. **Unverified** means provider or host access was unavailable.

## 1. Architecture at a glance

```text
Mobile clients ───────────────┐
Render-hosted Admin (static) ─┴→ HTTP Nginx gateway, Hetzner CX33
                                 ├→ Content ─→ Learning / AI ─→ Gemini
                                 ├→ Learning ────────────────→ Resend
                                 └→ Keycloak
                                      │
                                      └→ Neon, Frankfurt
                                          content / learning / ai / identity

GitHub Actions → SHA-tagged images in GHCR → gated SSH deployment to Hetzner
Rollback: previous GHCR SHA; suspended Render backends + old US-East Neon (temporary)
Backups: Neon recovery features + age-encrypted logical-dump tooling → S3-compatible store
```

All five runtime containers share one VM and one private Docker bridge. Only Nginx publishes application ports. This is operationally simple, but it is not application-node high availability.

## 2. Current infrastructure

| Component | Provider / purpose | Sizing | Exposure / persistence | Approx. monthly cost |
|---|---|---|---|---:|
| VM | Hetzner; all backend runtime | **Verified:** CX33, Nuremberg, 4 shared vCPU, 8 GB RAM, 80 GB local disk, Ubuntu 24.04 image | Public IPv4; local disk is a single failure domain | €8.49 excl. VAT |
| Primary IPv4 | Hetzner | One | Ports 22/80/443 allowed by attached cloud firewall | ~€0.50; invoice unverified |
| VM backups | Hetzner image backups | **Verified disabled** (`backup_window` absent) | No Hetzner recovery copy currently | €0 current; ~€1.70 if enabled |
| PostgreSQL | Neon; four logical DBs | **Unverified:** plan, min/max CU, scale-to-zero, storage and PostgreSQL 18 runtime | Repo records one EU project in Frankfurt; managed persistence | $0–25 likely at low load; actual bill unverified |
| Admin | Render static site | **Verified reachable** | Public HTTPS static site | $0 within free allowances |
| Registry / CI | GHCR and GitHub Actions | Five SHA-tagged images; public repository | Image persistence and CI logs | $0 at current public-repo pricing |
| Logical-backup objects | Intended Cloudflare R2/S3-compatible store | **Unverified / incomplete** | Script requires encrypted objects and lifecycle policy | Usually $0 at low volume if within R2 free tier |
| Gemini | External AI API | Configured for `FREE_ONLY`, paid use off by default | Usage-based external dependency | $0 intended; account/quota unverified |
| Resend | Transactional email | Hosted default configured | External dependency; sender/domain status unverified | $0 up to free allowance; then from $20 |
| Domain / DNS | Provider unknown | **Unverified** | No working public HTTPS API hostname was evidenced | Annual/provider-dependent |

Current Hetzner API pricing and current public provider price pages were used, not invoices. Hetzner’s server price excludes VAT. Neon’s current Launch rate is usage-based, so a monthly figure cannot be derived without compute/storage settings and usage.

## 3. Deployment model

`docker-compose.hosted.yml` defines Nginx, Content, Learning, AI and Keycloak on one bridge network. Nginx maps host 80/443; no service database or internal application port is published. Images are built after CI and tagged with a full commit SHA in GHCR. Deployment is a manual `workflow_dispatch` in the protected `hosted` GitHub environment and uses pinned SSH known-host data.

The deployment script pulls the requested SHA, waits for Compose health, applies Keycloak hardening, runs an internal smoke test, records current/previous tags and automatically attempts the prior tag if readiness fails. Rollback is therefore strong for application artifacts, but not for forward-only schema changes. Flyway runs at service startup; the repository documents a maintenance window rather than zero-downtime migration.

The repository is on `main` at `8b70947953d1a406e26d8d17ae01022629e60c63`, with an unrelated local modification to `.env.hosted.example`. The latest CI for that SHA failed (mobile and Keycloak image-scan jobs); its image-build workflow was skipped. The actual server checkout, deployed SHA, image tags and digests could not be read because deployment SSH credentials were unavailable locally. No GitHub-hosted deployment run was visible, so the current release path may also include operator-local deployment.

## 4. Database model

The intended and repository-recorded layout is one Neon EU project/compute with four logical databases (`content`, `learning`, `ai`, `identity`) and separate service roles. Runtime uses pooled TLS endpoints; Flyway, dump and restore operations use direct TLS endpoints. Configured maximum pools are Content 5, Learning 5, AI 4 and Keycloak 5 (19 total), with minimum idle 1 each.

The backup script dumps all four databases in PostgreSQL custom format, validates each dump, encrypts it with `age`, uploads archives plus checksums/metadata, downloads and byte-compares manifests, verifies each remote object and refuses to run without bucket lifecycle configuration. Restore requires an explicit confirmation and empty targets. A restore rehearsal is repository-recorded as completed, but its date/evidence and resulting RPO/RTO are not formalized in the reviewed files.

Offsite operation is **not complete**: no uploaded object, timer state or failure alert could be checked. Hetzner backups are disabled, leaving Neon’s documented provider recovery features as the only remaining backup class; the actual Neon plan and retention window are unverified. The old US-East Neon project is described as temporary rollback infrastructure; its existence, cost, write state and deletion deadline are unverified.

## 5. Security posture

- **Verified/configured:** Hetzner firewall attached to the VM and exposing only TCP 22, 80 and 443; only Nginx publishes application ports.
- **Repository-recorded:** root/password SSH disabled, a key-only deploy user, Fail2ban, unattended upgrades and UFW. Host-level state could not be rechecked.
- **Configured:** Content/Learning/AI run as UID 65532; Nginx as 101; Keycloak as 1000. All drop capabilities and enable `no-new-privileges`. Java/Nginx roots are read-only with tmpfs; Keycloak is the documented writable-root exception.
- **Configured:** exact CORS inputs, private JWK/service routes, separate internal API keys, mode-600 environment checks, Keycloak brute-force/password-policy hardening and no secrets in image tags or workflow arguments.
- **CI:** Gitleaks, dependency review and Trivy exist. The current Keycloak 26.7.0 image is known to carry High dependency findings; CI blocks Critical findings. The latest Keycloak scan failed, so the current `main` commit is not releasable under its own gate.
- **TLS is incomplete:** the public HTTP health endpoint returned 200, while port 443 did not accept a connection. Do not treat the temporary HTTP/IP mobile exception as production-safe. Admin-to-API browser use remains blocked by mixed-content rules.
- Backup encryption is well designed in code, but provides no protection until remote objects and restoreability are independently monitored.

## 6. Reliability and recovery

Every container has `unless-stopped`, health checks and bounded JSON logs (10 MB × 5). Java services have 40-second graceful stops; Keycloak has 45 seconds. Compose limits total 5,888 MB and 3.25 shared CPU, leaving host headroom. Java and Keycloak heaps use a 65% RAM ceiling and exit on OOM. The three services and gateway are read-only; Keycloak is not. Swap is repository-recorded as intentionally absent, but live state is unverified.

Docker should recover containers after a VM reboot, and the deployment script waits for health. A repository-recorded reboot/restore rehearsal and immutable rollback images are useful safeguards. However, VM, local disk, Nginx and all application services fail together. Recovery from VM loss is a manual rebuild/bootstrap, config/secret restoration, image redeploy and database validation. There is no tested application-node failover, load balancer or formal RPO/RTO. Render rollback is currently unavailable without resuming services: Admin returned 200, while gateway, Content, Learning, AI and Keycloak each returned 503. Database rollback is unsafe once writes diverge.

## 7. Observability

**Exists:** Docker stdout/stderr logs with rotation, container/readiness health checks, deploy audit/log output, internal/public smoke scripts, basic host inspection commands, GitHub Actions logs and Neon console metrics expected from the plan.

**Not evidenced:** independent uptime alerts, host CPU/memory/disk alerts, container restart alerts, gateway latency/5xx metrics, centralized/searchable logs, Sentry, TLS-expiry alerts, backup success/missing-backup alerts, Gemini/Resend operational alerts, or an incident dashboard. Neon metrics retention and quota alerts are unverified. The absence of a heavy self-hosted stack is appropriate for an 8 GB VM; external low-cost checks are still required.

## 8. Current monthly cost

Assumption: €1 = $1.14 (ECB reference-rate order of magnitude on review date); figures exclude VAT and invoices were unavailable.

| Cost class | EUR/month | USD/month | Assumption |
|---|---:|---:|---|
| Fixed current | ~€9 | ~$10 | CX33 + IPv4; no Hetzner backups |
| Usage-based current | €0–22 | $0–25 | Neon Free/low-load Launch; Gemini/Resend within free controls |
| Optional safeguards | €2–7 | $2–8 | Hetzner backups and low-volume object/uptime services |
| Unused rollback | €0–22+ | $0–25+ | Render free services; old Neon plan/usage unknown |
| **Possible current total** | **~€9–53** | **~$10–60** | Includes zero-to-paid estimates for both current and retained Neon projects |

The repository’s older €25–40 estimate assumed paid Neon and enabled Hetzner backups. Current API evidence shows backups disabled. Domain renewal is annual and excluded. Paid Gemini, Resend, CI overages or sustained Neon compute can raise the total.

## 9. Main strengths

- Low fixed cost and a small operational surface.
- Clear service/database ownership and private runtime networking.
- EU application/database placement is intended and partly provider-verified.
- Immutable SHA-tagged builds with health-gated deployment and artifact rollback.
- Sensible container limits, non-root execution, read-only roots and log rotation.
- Backup/restore tooling validates encryption, manifests and remote object integrity.

## 10. Main risks and limitations

- One VM/gateway is a complete application failure domain; no node failover exists.
- TLS/DNS cutover is incomplete; the reachable backend is HTTP-only.
- Offsite backups and backup-failure monitoring are not operationally verified; Hetzner backups are disabled.
- Current `main` fails CI, including the Keycloak Critical scan gate; known High Keycloak findings remain.
- Monitoring is insufficient for external users: no verified uptime, resource, restart, 5xx or TLS alerts.
- Deployed SHA, container state/digests, host hardening and swap could not be independently checked.
- Suspended Render and old Neon rollback resources have unclear expiry, cost and data-divergence controls.
- Recovery depends on manual operator steps, and RPO/RTO are not defined or measured.

## 11. Candidate simplifications or removals

| Candidate | Potential benefit | Tradeoff / reason to retain |
|---|---|---|
| Consolidate Content/Learning/AI runtime | Fewer images, JVMs, health checks and deploy units; lower memory | Weakens explicit ownership and independent scaling; preserve database/API boundaries if tried |
| Replace self-hosted Keycloak | Removes the heaviest operational/security dependency | Managed identity adds recurring/MAU cost, migration work and vendor dependency |
| Replace Nginx + Certbot with Caddy | Smaller TLS configuration and automatic renewal | Migration/testing effort; Nginx is already small and familiar |
| Revisit four logical databases | Simpler role/migration/backup operations | Separate ownership prevents accidental cross-service coupling; identity isolation has security value |
| Remove Render backend + old Neon | Eliminates stale attack surface, ambiguity and possible Neon cost | Only after TLS cutover, reconciliation and a time-bounded rollback window |
| Enable Hetzner backups alongside logical dumps | Fast whole-VM recovery plus portable data recovery are complementary | Costs ~20% of VM; VM images do not replace verified offsite database dumps |
| Reduce duplicate CI image builds | Lower workflow time/cache churn | Pull-request scans and post-CI publish provide different assurance; optimize caching before removing gates |
| Keep Admin on Render | Free CDN/TLS and no VM contention | Another provider/deploy path; moving it to object/CDN hosting may simplify ownership but not necessarily cost |
| Reduce pre-production backup retention | Lower storage/operational noise | Retention is cheap at current volume; production/audit requirements may justify it |

## 12. Candidate improvements

**Now**

1. Complete DNS/TLS, remove the temporary HTTP/IP client exception and verify Admin/mobile/OIDC end to end.
2. Configure and prove an offsite backup object, lifecycle, scheduled run, missing-backup alert and restore from that object; decide whether to enable Hetzner backups.
3. Resolve the failed `main` CI/Keycloak Critical scan and add external checks for API health, Admin and OIDC.
4. Time-box and then remove or formally retain the suspended Render backends and old Neon project.

**Soon**

1. Add lightweight host/container resource, restart, disk, 5xx, latency and TLS-expiry alerts; add Neon quota/compute alerts.
2. Write and rehearse a VM-loss runbook; set measured RPO/RTO and incident ownership.
3. Inventory provider plans, billing alerts, secret rotation dates and the actual deployed image digests.

**Later**

1. Add a second node/load balancer only when uptime requirements justify the additional state, rollout and cost complexity.
2. Introduce reproducible host provisioning (focused Ansible/Terraform) and a production/staging split when release frequency or team size makes manual state risky.
3. Consider a managed container platform only if on-call burden or scaling exceeds the single-VM model; Kubernetes has no current justification.

## 13. Questions for the DevOps reviewer

1. Is single-VM Compose acceptable for the next 12 months and the intended external-user SLO?
2. What measured RPO/RTO are required, and does the proposed Neon + encrypted logical + optional VM-backup strategy meet them?
3. Which controls must be operational before any production client can use the platform?
4. Should Keycloak remain self-hosted given its memory footprint and recurring dependency findings?
5. Do three application containers and four logical databases still earn their operational cost at current scale?
6. Is Neon’s selected plan, scale-to-zero policy, restore window and connection ceiling appropriate for authentication and production traffic?
7. Should Nginx/Certbot remain, or would Caddy reduce TLS operational risk enough to justify migration?
8. When exactly should the Render backends and old US-East Neon project be deleted, and how will write divergence be ruled out?
9. Are both Hetzner image backups and encrypted logical dumps warranted, or which recovery scenario can safely be dropped?
10. Which CI jobs can be consolidated without weakening the release gate, especially the duplicated image builds?
11. Should Admin remain on Render static hosting or move to the same DNS/CDN account as other public assets?
12. What traffic, SLO or on-call threshold would justify a second VM, load balancer or managed container service?

---

**Evidence inspected:** `docker-compose.hosted.yml`; service, gateway and Keycloak Dockerfiles; CI/build/deploy workflows; Hetzner deploy/rollback/backup/restore/TLS/bootstrap scripts; hosted deployment, hardening, backup and ADR documentation; Render blueprint; current Git/GitHub state; Hetzner server/firewall API; public HTTP/HTTPS endpoint checks; and official Hetzner, Neon, Render, GitHub, Cloudflare R2, Gemini, Resend and ECB pricing/reference pages.
