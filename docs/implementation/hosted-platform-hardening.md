# Hosted platform hardening

This phase keeps the temporary HTTP endpoint usable while preparing the
configuration required after DNS and TLS cutover.

## Mock-exam result semantics

`correctAnswers`, `incorrectAnswers`, and `unansweredAnswers` are disjoint:

- correct is an answered question whose exact option set is correct;
- incorrect is an answered question whose exact option set is wrong;
- unanswered has no persisted response.

New attempts persist that definition. Result retrieval derives the incorrect
count from responses, so previously stored attempts are presented consistently
without rewriting historical rows. Percentage continues to use all questions
as the denominator. Subject breakdowns already used these semantics.

## AI validation modes

Normal hosted runtime remains Gemini and never falls back to fake output.
`docker-compose.ai-fake-validation.yml` is an explicit operator-only override.
It sets provider `FAKE`, model `deterministic-v1`, disables Gemini usage, and
requires `AI_ALLOW_FAKE_IN_PRODUCTION=true`. Use it only for a controlled
validation window, run test-prefixed fixtures, then redeploy the normal hosted
Compose configuration. The hosted profile rejects FAKE unless that deliberate
override is present.

Valid Gemini `FREE_ONLY` configuration additionally requires:

- expected billing tier `FREE`;
- a non-secret project label;
- positive operator-selected RPM, TPM, and RPD limits;
- paid usage disabled and monthly spend limit zero;
- ordered warning, critical, and stop thresholds.

No real Gemini request is part of automated validation.

## Dependency findings

- The Admin Vite SPA uses React Router 7.18.1. The reported RSC action CSRF
  issue is in React Server Components mode, which this client-only application
  does not use. The published fixed version is React Router 8.3.0, a major
  upgrade that is not forced during hardening.
- Mobile pins PostCSS 8.5.18 and the compatible `brace-expansion` 5.0.8 patch.
  Older `brace-expansion` 1.x and 2.x copies arrive through Jest, React Native,
  and Expo toolchains. Forcing a cross-major override risks breaking glob and
  minimatch; update those upstream toolchains instead.
- Keycloak 26.7.0 remains pinned for schema compatibility. Scanner findings in
  bundled Jackson, Netty, PostgreSQL JDBC, and optional admin/client drivers
  require an upstream Keycloak image refresh. Do not replace individual jars
  inside the optimized distribution. CI continues to block Critical findings;
  High findings are reviewed at every Keycloak release.

## Immutable deployment

Images are selected by a full Git commit SHA. Deployment records requested,
current, and previous image tags under `/opt/citizenship-platform/state`.
Compose pulls images from the registry and never builds from arbitrary changes
in the server checkout. Rollback selects the recorded previous immutable SHA.
The server checkout must remain clean; configuration changes are delivered by
a reviewed commit and new images, while secrets remain in the mode-600
environment file.
