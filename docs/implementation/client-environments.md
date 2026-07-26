# Client environments

Both clients use the typed environments `LOCAL` and `HOSTED`. API and OIDC endpoints are resolved as one configuration so they cannot be mixed.

## Mobile

Ordinary Expo development defaults to `LOCAL`. Use the `development-local` EAS profile for laptop services and `development-hosted` or `preview-hosted` for internal testing against `http://46.224.221.7`. The raw-IP HTTP endpoint is temporary and internal-only. The `production` profile is guarded: it rejects `LOCAL` and any non-HTTPS hosted endpoint.

Android permits cleartext only in debug manifests. iOS retains `NSAllowsArbitraryLoads=false` and local-network access; no global ATS exception is added. Replace `EXPO_PUBLIC_API_BASE_URL` with the future HTTPS gateway without changing source.

For `HOSTED` plus `EXPO_PUBLIC_BUILD_KIND=internal`, Expo config adds an exact-host ATS exception for `46.224.221.7`; it is absent from local and production configurations. Remove this conditional exception when the hosted gateway moves to HTTPS. Apple documents ATS exception-domain keys as domains, so raw-IP handling must be verified on the physical device; a hostname with HTTPS is the durable solution.

## Admin Portal

Set `VITE_ENABLE_ENVIRONMENT_SWITCHER=true` to show the compact header selector. `VITE_DEFAULT_APP_ENV` sets the build default. Local and hosted API/issuer pairs are configured with `VITE_LOCAL_*` and `VITE_HOSTED_*` variables shown in `.env.example`.

The selection is persisted in localStorage only when switching is enabled. Switching removes the current OIDC user and development identity, clears query data, then reloads. OIDC session storage is environment-prefixed. Disable the selector for public builds; stored overrides are then ignored.

Local Admin callback origins remain `http://localhost:5173` and `http://127.0.0.1:5173`; this client change does not broaden CORS or Keycloak redirect rules.
