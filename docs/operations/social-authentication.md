# Social authentication operations

## Production addresses

- Issuer: `https://api.tinkona.com/auth/realms/exam-platform`
- Mobile callback: `sveastudy://auth/callback`
- Google callback: `https://api.tinkona.com/auth/realms/exam-platform/broker/google/endpoint`
- Apple callback: `https://api.tinkona.com/auth/realms/exam-platform/broker/apple/endpoint`
- iOS App ID: `se.medbo.sveastudy`

Provider flags must remain false until the corresponding protected values and external callback are verified. Values belong only in `/opt/citizenship-platform/.env` on the hosted server (owner-readable mode), using the variable names documented in `.env.hosted.example`. Never put them in Git, mobile configuration, frontend configuration, an image, or diagnostics.

## Google Cloud Console

1. In the production Google Cloud project, configure the OAuth consent screen for the Tinkona-owned application and add the authorized domain `tinkona.com`.
2. Request only `openid`, `email`, and `profile`; do not request Gmail, contacts, Drive, or Calendar.
3. Create an OAuth 2.0 client of type **Web application**, separate from local development.
4. Add the exact Google callback above under Authorized redirect URIs. No raw IP, HTTP, sslip.io, or wildcard is allowed.
5. Put its client ID and client secret into `KEYCLOAK_GOOGLE_CLIENT_ID` and `KEYCLOAK_GOOGLE_CLIENT_SECRET`, then set `KEYCLOAK_GOOGLE_ENABLED=true`.
6. Run the hardening workflow and confirm Admin readiness reports Google READY before testing. Google broker configuration trusts Google's verified-email assertion, stores no provider token, and uses `openid email profile`.

## Apple Developer

1. Enable Sign in with Apple for primary App ID `se.medbo.sveastudy`.
2. Create a production Services ID for the web/OIDC broker and associate it with that primary App ID. The Services ID is the Keycloak client ID; do not invent or reuse the App ID value.
3. Configure website domain `api.tinkona.com` and the exact Apple callback above.
4. Create a Sign in with Apple key and record its Team ID and Key ID. Download the `.p8` once and protect it.
5. Base64-encode the complete PEM outside logs and provision `KEYCLOAK_APPLE_PRIVATE_KEY_BASE64`, plus `KEYCLOAK_APPLE_TEAM_ID`, `KEYCLOAK_APPLE_KEY_ID`, and `KEYCLOAK_APPLE_SERVICES_ID`. Set `KEYCLOAK_APPLE_ENABLED=true` only after all values exist.
6. The provider creates short-lived Apple client assertions on demand; no static client secret is committed. Validate first authorization with real email, Hide My Email/private relay, and a later authorization where Apple omits name/email. Stable Apple subject is authoritative.

The image contains `klausbetz/apple-identity-provider-keycloak` 1.17.0 from its [upstream repository](https://github.com/klausbetz/apple-identity-provider-keycloak), Apache-2.0, pinned to SHA-256 `4091dee2a1ec9e0771bef4bd46005197d86b0a2b1f25198c41738476b1d102bb`. The release declares Keycloak 26.5+ compatibility. Source and JAR contents were reviewed; automatic token-exchange linking is explicitly disabled. The build downloads only in its builder stage, verifies the digest, runs Keycloak augmentation, emits OCI SBOM/provenance, and never downloads at container startup. Review upstream release notes, source diff, license, checksum, dependency/SBOM scan, and Keycloak compatibility before pinning any update.

## SMTP

Use a transactional sender verified for `tinkona.com`, preferably `Svea Study <no-reply@tinkona.com>`. Provision `KEYCLOAK_SMTP_HOST`, port, TLS/auth settings, username, password/API key, and from address in protected hosted configuration. Publish and verify the provider's SPF and DKIM records and a DMARC policy before setting `KEYCLOAK_SMTP_CONFIGURED=true`. Exercise registration verification, resend, reset, expired reset, and required-action mail. Never log message bodies or action URLs. Do not use personal mailbox credentials.

## Linking and first login

Keycloak's safe first-broker flow is retained: a matching email never silently merges users. The user confirms linking and authenticates the existing account. The hardening script refuses a first-broker flow containing `idp-auto-link`. Authenticated linking starts through the BFF, uses Keycloak AIA and PKCE, and mobile rejects a callback whose subject differs from the starting subject. Apple private-relay addresses receive no special merge privilege.

## Deletion and retention

Confirmed deletion is immediate: active sessions and Keycloak identity are removed; email, display name, verification state, and external identity subject in Learning are anonymised. Study progress, practice, and mock-exam records remain pseudonymous under an internal UUID for result integrity. Correlation-only, subject-hashed operational audit is retained. Shared curriculum is untouched. There is no restoration window.

## Deployment and verification

Deploy Learning Service/BFF first, then the checksum-pinned Keycloak image/theme and safe broker flow, then enable only credential-complete providers and SMTP, then Admin, then mobile Release. Snapshot learner/profile/history counts and active content checksum before and after. Validate issuer discovery, exact callbacks, Admin login, email flow, dedicated non-production Google/Apple test identities, linking/unlinking, global logout, and deletion of a disposable account. Local provider credentials, if used, must belong to separate OAuth applications.

If credentials are absent, this is the intended safe status: email/existing OIDC continues, Google and Apple buttons are disabled, Admin reports DISABLED, and deployment must not synthesize placeholder values.
