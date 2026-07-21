# Authentication architecture

## Decision

The platform uses OpenID Connect and OAuth 2.0 Authorization Code flow with
PKCE. Keycloak is the local identity provider; domain services depend only on
standard JWT claims and can use a managed production OIDC provider without
changing their ownership model. Content Service and Learning Service do not
store passwords or issue user tokens.

Mobile and Admin are public clients and contain no client secret. Access tokens
are short lived. Mobile stores only refresh credentials in Expo SecureStore
(iOS Keychain/Android Keystore) and keeps access tokens in memory. Admin uses
`oidc-client-ts`; its user session is scoped to browser `sessionStorage`, never
`localStorage`, and is cleared together with the query cache at logout.

## Trust boundaries

- Learning Service validates signature, issuer, audience `learning-api`, token
  time claims and bearer type. `sub` is the only external identity key.
- Content Service validates the same properties with audience `content-api` and
  maps existing realm roles: `CONTENT_AUTHOR`, `CONTENT_REVIEWER`,
  `CONTENT_PUBLISHER`, and `ADMIN`.
- Learner tokens cannot authorize Admin routes because they lack both Content
  audience and roles.
- Release import and operational reporting retain separate internal service
  credentials. An ordinary user bearer token cannot replace those credentials.
- Development identity headers remain available only when their explicit local
  flags are enabled. They are disabled by default outside Compose development.

## Learner ownership and deletion

Learning Service owns the learner profile. First authenticated access inserts a
profile idempotently under the unique OIDC subject. Concurrent requests converge
on the same row. Practice, progress and mock records continue to reference the
internal learner UUID resolved by the server, never a learner ID supplied in a
request body or URL.

Account deletion is an authenticated `/api/v1/me` operation. It removes profile
email/name, marks the account `DELETED`, and prevents further access while
retaining pseudonymous learning records needed for integrity. Provider-account
removal is a separately privileged identity-provider operation in production.

Existing anonymous data is not silently attached to accounts. Deterministic
demo data is explicitly mapped to the fixed local Keycloak demo subject.

## Token lifecycle

Clients refresh shortly before expiry. Refresh is deduplicated, rotation is
persisted when the provider returns a new refresh token, and failure clears
credentials, learner state and query caches before showing session expiration.
403 responses are not refreshed. APIs remain authoritative for authorization.
