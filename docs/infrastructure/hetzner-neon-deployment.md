# Hetzner and Neon EU deployment

This runbook implements [ADR-011](../decisions/ADR-011-hetzner-neon-eu-deployment.md).
It is deliberately explicit about which steps are automated and which require
provider access. Never use it to imply that a resource exists before its provider
and application checks pass.

## 1. Target architecture

```text
Mobile and Render-hosted Admin
              |
              v HTTPS
 api.example.com -> Hetzner Nuremberg, Ubuntu LTS, 8 GB
              |
        Nginx gateway (ports 80/443)
        |             |             |
     Content       Learning      Keycloak
        |
       AI
              |
              v TLS
 Neon AWS Frankfurt project
 content | learning | ai | identity
```

All five containers share a Docker bridge. Only Nginx publishes host ports. The
bridge permits outbound traffic because the services need Neon, Gemini, and
Resend, but unsolicited inbound traffic cannot reach an internal container.

## 2. Hetzner resource selection

Select the smallest currently available x86-64 plan with at least 8 GB RAM and
two shared vCPUs. As of 25 July 2026, the preferred selection is:

| Property | Selection |
|---|---|
| Location | Nuremberg `nbg1`, Germany |
| Image | Current Ubuntu LTS x86-64 |
| Plan | CX33, subject to Console availability |
| CPU | 4 shared vCPU |
| Memory | 8 GB |
| Disk | 80 GB |
| Base price | €8.49/month excluding VAT and IPv4 |
| Primary IPv4 | €0.50/month excluding VAT |
| Hetzner backups | 20% of server price, approximately €1.70/month |

Plan names and prices change. Confirm all fields in the Hetzner Console before
creating the VM; select a different current 8 GB x86-64 plan if CX33 is
unavailable. Nuremberg is preferred because Neon provides AWS Frankfurt.

Hetzner VM backups are useful for host recovery but do not replace encrypted
logical database backups.

## 3. Neon EU project

In Neon:

1. Create a new dedicated project in AWS Frankfurt (`eu-central-1`).
2. Select Launch or another paid plan after reviewing the account's current
   billing configuration.
3. Configure a small autoscaling range. Disable scale-to-zero if predictable
   database availability is required.
4. Set a seven-day restore window for staging.
5. Create `content`, `learning`, `ai`, and `identity` using
   `scripts/provision-neon-databases.sql`.
6. Generate separate passwords for the four service roles.
7. Record pooled and direct endpoints.
8. Use pooled endpoints in the four runtime JDBC application URLs.
9. Configure the three Spring services' `*_MIGRATION_DATABASE_URL` values with
   their direct endpoints so Flyway does not run through PgBouncer.
10. Use direct endpoints for Keycloak's first database initialization and for
    provisioning, dumps, restores, and validation. Switch Keycloak to its
    generated pooled endpoint after initialization.
11. Test `sslmode=verify-full`; never disable certificate verification.

Invoke the provisioning SQL from a secure shell without adding passwords to
history:

```bash
read -rsp 'Content role password: ' CONTENT_ROLE_PASSWORD
printf '\n'
# Repeat for the other roles, then use psql variables from the protected process
# environment or a temporary script with mode 600.
```

Do not use the US-East project for the new hosted deployment. Do not delete it
during cutover.

## 4. VM creation

In Hetzner Cloud:

1. Create or select the project.
2. Upload the deployment operator's Ed25519 public SSH key.
3. Create the selected 8 GB x86-64 server in `nbg1`.
4. Use the current Ubuntu LTS image.
5. Allocate IPv4 and IPv6 as required.
6. Enable provider backups if desired.
7. Create a cloud firewall allowing:
   - TCP 22 only from the operator IP where practical
   - TCP 80 from everywhere
   - TCP 443 from everywhere
8. Deny other inbound traffic.
9. Confirm no database or Docker daemon port is allowed.

Record the server fingerprint before adding it to GitHub Actions.

## 5. VM bootstrap and SSH

Copy the repository to the VM and run:

```bash
sudo DEPLOY_USER=citizenship SSH_ALLOW_FROM=YOUR_PUBLIC_IP/32 \
  ./infrastructure/hetzner/bootstrap.sh
```

The script installs Docker, Compose, PostgreSQL tools, Certbot, age, AWS CLI,
UFW, fail2ban, and unattended upgrades. It creates the non-root deployment user
and application directories.

SSH password and root login are disabled only after the deployment user's
`authorized_keys` file is non-empty. Re-run bootstrap after installing the key.
Keep a second shell open while testing hardened SSH to avoid lockout.

Routine deployment must use `citizenship`, not root. Root is required only for
host administration and initial certificate issuance.

## 6. DNS and initial TLS

Lower the existing API DNS TTL to 300 seconds at least one normal TTL period
before cutover. Do not point production traffic to the VM yet.

Create a temporary validation record or the final API record only after the
containers and databases are ready for validation. Ensure port 80 reaches the
VM, then stop anything using ports 80/443 and run:

```bash
sudo API_DOMAIN=api.example.com TLS_EMAIL=operator@example.com \
  ./infrastructure/hetzner/provision-tls.sh
```

The production hosted gateway requires an issued certificate before startup.
Certificate renewal runs twice daily through systemd and reloads Nginx.

HTTP redirects to HTTPS. TLS 1.2 and 1.3 are enabled. HSTS is intentionally not
enabled during initial migration; add it only after HTTPS and rollback DNS have
been stable.

For an explicitly non-production, pre-DNS empty-stack check, set
`PUBLIC_SCHEME=http` and bind
`GATEWAY_CONFIG_TEMPLATE=./infrastructure/gateway/bootstrap-http.conf.template`.
This bootstrap configuration listens only for plain HTTP application
validation. Never use it for production traffic, credentials, mobile releases,
or the final cutover.

## 7. Hosted secrets

Copy `.env.hosted.example` to:

```text
/opt/citizenship-platform/.env
```

Set ownership to the deployment user and mode 600:

```bash
sudo chown citizenship:citizenship /opt/citizenship-platform/.env
sudo chmod 600 /opt/citizenship-platform/.env
```

Generate independent random internal keys and bootstrap credentials. Never use a
database owner or Neon administrative role for application traffic.

The file contains server secrets and must not be copied into an image, repository,
support ticket, or GitHub log. Public Expo and Vite values must never contain
database, Gemini, Resend, Keycloak bootstrap, or internal API credentials.

## 8. GHCR

The image workflow publishes:

```text
ghcr.io/OWNER/citizenship-content-service:<commit-sha>
ghcr.io/OWNER/citizenship-learning-service:<commit-sha>
ghcr.io/OWNER/citizenship-ai-service:<commit-sha>
ghcr.io/OWNER/citizenship-api-gateway:<commit-sha>
ghcr.io/OWNER/citizenship-keycloak:<commit-sha>
```

Deployment never uses `latest`. If packages are private, create a read-only
fine-grained token, store it on the VM in a root/deployment-user-readable file
with mode 600, and configure:

```text
GHCR_USERNAME
GHCR_TOKEN_FILE
```

Do not send the registry token through SSH command arguments.

## 9. Hosted Compose

`docker-compose.hosted.yml` contains no PostgreSQL container. Initial limits are:

| Container | Memory | CPU |
|---|---:|---:|
| Gateway | 256 MB | 0.25 |
| Content | 1,536 MB | 0.75 |
| Learning | 1,280 MB | 0.75 |
| AI | 1,280 MB | 0.75 |
| Keycloak | 1,536 MB | 0.75 |
| Total limits | 5,888 MB | 3.25 shared CPU |

The host retains more than 2 GB for Linux, Docker, page cache, Certbot, backup
tools, and deployment overlap. Java uses 65% maximum RAM percentage rather than
allocating the whole container limit to heap.

The three custom Java containers run non-root, read-only, without Linux
capabilities, and use tmpfs for `/tmp`. Nginx is also read-only. Keycloak is the
documented exception to the read-only root setting because its supported image
may write runtime data under `/opt/keycloak/data`; it still drops capabilities
and enables `no-new-privileges`.

All containers have restart policies, graceful stop periods, health checks, and
rotated JSON logs.

## 10. Application networking

Hosted internal URLs are fixed to Docker DNS:

```text
Content -> http://learning-service:8080
Content -> http://ai-service:8080
Content/Learning JWK -> http://keycloak:8080/auth/...
Gateway -> service container names
```

AI has no gateway route and no host port. Internal API keys remain required.
Future improvements may use service-specific keys, mTLS, or workload identity;
a service mesh is not justified now.

## 11. Database migration

Use a documented maintenance window. A zero-downtime migration has not been
implemented.

1. Provision the EU project, four databases, and four roles.
2. Verify direct and pooled TLS endpoints.
3. Stop or block writes on the old application.
4. Run encrypted offsite backups of all source databases.
5. Record exact source row counts and latest timestamps.
6. Restore Content, Learning, AI, and Identity to the EU databases.
7. Confirm sequences, indexes, constraints, and ownership.
8. Confirm `flyway_schema_history` heads: Content V12, Learning V12, AI V12.
9. Confirm the Keycloak realm, users, clients, roles, and groups by counts.
10. Run `validate-migration.sh`.
11. Start exactly one instance of each service against the target.
12. Run integrity and application smoke tests.
13. If writes occurred after the first dump, repeat the final dump and restore
    during the maintenance window.

`pg_dump` custom format and `pg_restore` preserve schema objects and sequences.
Never put passwords in command-line URLs. Use the script's protected environment
variables.

`validate-migration.sh` performs exact `COUNT(*)` checks and prints table names
and counts only, never rows or personally identifiable data. Run it during the
maintenance window because exact counts can be expensive on large databases and
are meaningful only while source writes are frozen.

## 12. Backup and restore

`backup.sh`:

- dumps all four databases through direct endpoints
- uses custom compressed format
- validates every archive with `pg_restore --list`
- encrypts each archive with an age recipient
- uploads to external S3-compatible storage
- refuses to run unless bucket lifecycle retention exists
- removes local temporary plaintext and encrypted copies

By default it reads only its named backup variables from the protected hosted
environment file; it never evaluates that file as shell code. S3 credentials
must be limited to the backup prefix/bucket. When validating another environment,
set `BACKUP_ENV_FILE` to a different mode-600 file.

Recommended staging bucket lifecycle:

- expire daily backup objects after 7 days
- retain separately tagged weekly copies for 28 days

Production retention should be 14–30 days plus longer monthly copies according
to recovery and legal requirements.

Run backup manually before migration and schedule it afterward with a systemd
timer or external scheduler. Alert on non-zero exit. Do not store the only backup
on the VM.

`restore.sh` requires:

```text
CONFIRM_RESTORE=RESTORE_TO_EMPTY_EU_DATABASES
```

Use it only against verified empty target databases. It does not use `--clean`,
so accidental restoration over populated databases should fail rather than
silently replacing objects. Perform quarterly restore tests into disposable
databases and record results.

## 13. Keycloak

The hosted Keycloak container runs optimized production mode behind
`https://API_DOMAIN/auth`, with strict hostname validation and forwarded proxy
headers. It uses the current 26.7.0 release and only the `identity` database.
Version 26.3.5 was rejected after image scanning identified fixed High-severity
authentication and authorization vulnerabilities.

Do not import `exam-platform-realm.json` into hosted Keycloak. It contains
development users, redirect URIs, and Mailpit SMTP configuration. Migration of
the current identity database preserves the hosted realm.

After restoration verify:

- issuer and discovery URL
- mobile PKCE client and deep-link callback
- Admin callback and exact web origin
- realm roles and audience mappers
- secure cookies
- registration, login, refresh, and logout
- existing user login

The first 26.7.0 startup may upgrade the Keycloak schema. Take a target identity
backup first, run only one Keycloak instance, inspect the upgrade result, and
complete login/client/role validation. Application-image rollback does not
downgrade a Keycloak database schema. DNS rollback continues to use the untouched
US-East identity database during the migration window.

The 26.7.0 upstream image has no detected fixable Critical vulnerability in the
current Trivy database. It still reports High findings in bundled libraries for
which a newer supported Keycloak image was not available during implementation.
CI blocks Critical findings for Keycloak and blocks both High and Critical for
the project-owned images. Review upstream Keycloak releases and restore the
High-severity gate as soon as a clean supported image is available.

Keycloak does not use Learning Service's Resend HTTP adapter. Configure a
supported SMTP provider separately. Before relying on Resend SMTP from Hetzner,
verify Hetzner outbound SMTP policy and request any required port unblocking.
Until an actual Keycloak SMTP test and registration email pass, verification and
password-reset email remain an explicit cutover blocker.

## 14. Controlled deployment

Pull requests and pushes run `.github/workflows/ci.yml`. Successful `main` CI can
publish immutable images. Deployment is manual through the protected `hosted`
GitHub environment.

Required GitHub environment secrets:

```text
HOSTED_HOST
HOSTED_USER
HOSTED_SSH_PRIVATE_KEY
HOSTED_KNOWN_HOSTS
```

Configure required reviewers on the `hosted` environment. The VM repository
checkout must use read-only Git credentials if the repository is private.

`deploy.sh` validates the SHA, secret-file mode, Compose model, image availability,
container health, and smoke tests. It records image state and attempts
application-image rollback when health fails. It does not reverse Flyway
migrations or delete old images.

## 15. Rollback

Rollback triggers include:

- failed container readiness
- login or token refresh failure
- material row-count mismatch
- Content or Learning API failure
- release projection inconsistency
- sustained error/latency regression

Before cutover, keep DNS TTL at 300 seconds and retain:

- old Render backend services
- US-East Neon project
- source database backups
- previous Hetzner image tags

Application rollback on the VM:

```bash
./infrastructure/hetzner/rollback.sh PREVIOUS_FULL_COMMIT_SHA
```

Database migrations are forward-only. Correct them with a new migration or
restore a verified backup during maintenance.

Never leave old and new stacks writable indefinitely. DNS rollback to Render is
safe only when the old database remains authoritative or when new writes have
been reconciled. Define a maximum acceptable outage before cutover; a 30-minute
maintenance window is a reasonable initial proposal, not a guarantee.

## 16. Mobile

LOCAL mode remains unchanged. REMOTE now requires:

```text
EXPO_PUBLIC_APP_ENV=REMOTE
EXPO_PUBLIC_API_BASE_URL=https://api.example.com
EXPO_PUBLIC_OIDC_CLIENT_ID=mobile-app
```

The app derives `/learning` and `/auth`. It contains no Render fallback and no
service-warming calls. Do not build a production binary against a temporary
HTTP IP.

After cutover validate login, refresh, logout, topics, study, practice, progress,
and mock exams with the laptop off and the device on mobile data.

## 17. Admin Portal

Keep the Admin Portal on Render static hosting. Set and redeploy:

```text
VITE_API_BASE_URL=https://api.example.com
VITE_OIDC_AUTHORITY=https://api.example.com/auth/realms/exam-platform
VITE_OIDC_CLIENT_ID=admin-portal
VITE_DEV_ADMIN_AUTH_ENABLED=false
```

Update the Keycloak client's callback, logout URIs, and web origins to the exact
Admin URL. Set backend `CORS_ALLOWED_ORIGINS` to exact comma-separated Admin and
approved local-development origins. Never use `*` with credentials.

## 18. Smoke tests

Run:

```bash
API_DOMAIN=api.example.com ADMIN_PORTAL_URL=https://ADMIN_URL \
  ./infrastructure/hetzner/smoke-test.sh
```

The script checks gateway health, OIDC discovery, the protected Learning
response, and optionally the Admin page and authenticated endpoints. Supply
short-lived bearer tokens only through protected environment variables.

It never invokes paid Gemini generation or sends email. Those operations require
explicit manual validation.

## 19. Resend and Gemini

Learning's Resend HTTP sender uses bounded timeouts and retries and does not log
the API key or message body. Send one approved test email only after setting an
explicit manual validation procedure. Record the provider message ID without
recording the recipient or key.

Keep Gemini concurrency and spending controls conservative. Validate fake-provider
generation first. Run one real Gemini operation only after confirming the model,
quota, billing tier, and allowed data handling.

## 20. Monitoring and logs

At minimum configure an independent uptime provider to check:

- `https://API_DOMAIN/healthz`
- Admin Portal root
- Keycloak discovery endpoint

Alert on:

- container unhealthy/restarting
- disk above 75%
- sustained memory above 80%
- backup failure or missing daily backup
- TLS renewal failure
- elevated gateway 5xx

Docker logs rotate at 10 MB with five files. Inspect startup and error logs after
each deploy for credentials, URLs containing passwords, bearer tokens, cookies,
email authorization headers, and environment dumps.

No heavy Prometheus/Grafana stack is included because the initial 8 GB host needs
deployment headroom. Sentry or another hosted error tracker can be added later
through secret-free configuration placeholders.

## 21. Security checklist

Before cutover verify:

- deployment user is non-root
- SSH key authentication works before passwords/root are disabled
- Hetzner and UFW firewalls agree
- only ports 80, 443, and restricted 22 are reachable
- internal container ports and Docker daemon are unreachable
- `/opt/citizenship-platform/.env` is mode 600
- all credentials differ from local/demo credentials
- internal API keys were rotated
- TLS and exact CORS are active
- Keycloak uses production mode and secure cookies
- no broad Actuator exposure exists
- Neon TLS verification succeeds
- dependency, secret, and image checks pass
- backup and restore have been tested

## 22. Cost estimate

Indicative starting monthly cost, excluding VAT:

| Component | Estimate |
|---|---:|
| Hetzner CX33 | €8.49 |
| Primary IPv4 | €0.50 |
| Hetzner VM backups | €1.70 |
| Neon Launch | approximately $15–25 |
| Render Admin static | $0 |
| Resend Free | $0 initially |
| Gemini | $0 or usage-based |
| S3-compatible logical backups | provider-dependent |
| Domain | annual provider-dependent |

Expected initial total is approximately €25–40/month after currency conversion
and small backup storage, but only provider invoices establish actual cost.

## 23. Scaling

First measure container memory, CPU, JVM heap, gateway latency, database latency,
and AI queue depth. Vertical VM scaling is the first simple response. If one
service becomes noisy, move that unchanged image to a separate host before
considering application consolidation. Preserve database ownership.

## 24. Troubleshooting

- Certificate missing: issue it before starting hosted Compose.
- Gateway 502: inspect the target container health and Docker DNS, not a Render URL.
- JWT issuer mismatch: token `iss` must exactly equal the public HTTPS issuer.
- JWK failure: Content/Learning should use the private Keycloak JWK URL.
- Flyway failure: check the correct database/role and migration history; never
  repair production history casually.
- Neon connection failure: verify pooled versus direct endpoint, database name,
  role, and `verify-full`.
- Keycloak not ready: inspect memory, identity database, hostname, and management
  health path.
- Mobile fails only in release: rebuild with the stable HTTPS
  `EXPO_PUBLIC_API_BASE_URL`.

## 25. Render decommission checklist

Only after the rollback window and explicit approval:

1. Confirm DNS no longer resolves to Render gateway.
2. Confirm mobile and Admin use the new domain.
3. Confirm no service-to-service Render URL remains.
4. Confirm Hetzner restart recovery.
5. Confirm EU data and backup validation.
6. Export final Render logs and configuration inventory if needed.
7. Suspend, then later remove Render gateway, Content, Learning, AI, and Keycloak.
8. Keep the Render Admin static site.
9. Retain US-East Neon according to the agreed rollback/retention period.

Nothing in this repository automatically deletes Render or Neon resources.

## 26. Provisioning status

Repository support is implemented, but this document does not prove that the
Hetzner VM, Neon EU project, DNS record, TLS certificate, GHCR packages, offsite
bucket, or GitHub environment exists. Complete and record every external step and
smoke test before declaring cutover.
