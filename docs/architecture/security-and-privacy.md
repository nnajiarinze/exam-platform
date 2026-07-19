# Security and privacy

## Identity and authorization

Use an OIDC/OAuth 2.1-capable managed identity provider unless a later ADR decides otherwise. Clients send short-lived access tokens; services validate issuer, audience, signature, expiry, and scopes. Authorization is enforced inside each service, not only at the gateway.

Admin access uses least-privilege roles such as author, reviewer, publisher, support-readonly, and security administrator. Separate approval from authorship for publishable content and protect publishing/payment operations with stronger authentication. Service-to-service calls use workload identity or rotated short-lived credentials and explicit audience/scope; never reuse end-user secrets.

## Operational controls

- Store secrets in the deployment platform's secret manager; never source control or images.
- Encrypt traffic in transit and databases, backups, snapshots, and sensitive object storage at rest.
- Rate-limit by route, identity, and risk; apply stricter limits to authentication, reports, AI, and payment endpoints.
- Record tamper-resistant audit events for admin changes, review/publish actions, entitlement changes, exports/deletions, and privileged access.
- Scrub tokens, prompts, answers where unnecessary, payment data, and personal identifiers from logs/errors.
- Back up each owned database independently; encrypt, test restoration, define RPO/RTO before production, and retain immutable content artifacts needed for historical attempts.

## GDPR and minimization

Maintain a data inventory, lawful purpose, retention period, and processor list. Support authenticated export and deletion workflows across service-owned data; use a coordinated request ID rather than cross-database access. Deletion must reconcile legal/financial retention and de-identify learning analytics. Collect no immigration case files, identity documents, national identity numbers, precise location, contacts, or unrelated sensitive demographics for the initial product.

Analytics is consent/configuration aware, pseudonymous where possible, and excludes question free text or sensitive profile content. Stripe handles payment instruments; the platform stores provider/customer references and entitlement evidence, not card data.

## AI privacy boundary

Send only operation-required content. Do not send learner identity, immigration details, subscription/payment data, raw analytics history, or special-category data to AI providers. Contractually disable provider training/retention where available, set bounded retention, redact logs, validate output, and record provider/model provenance. A new personal-data AI use requires privacy/security review and a documented lawful basis. See [AI Service](ai-service.md) and the [legal boundaries](../product/legal-and-content-boundaries.md).

