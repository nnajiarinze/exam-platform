# Client environments

Both clients use the typed environments `LOCAL` and `HOSTED`. API and OIDC endpoints are resolved as one configuration so they cannot be mixed.

## Mobile

Ordinary Expo development defaults to `LOCAL`. Use the `development-local` EAS profile for laptop services and `development-hosted` or `preview-hosted` for `https://api.tinkona.com`. The `production` profile is guarded: it rejects `LOCAL` and every non-HTTPS hosted endpoint.

Android permits cleartext only in debug manifests. iOS retains `NSAllowsArbitraryLoads=false` and local-network access; no global ATS exception is added. Replace `EXPO_PUBLIC_API_BASE_URL` with the future HTTPS gateway without changing source.

All hosted profiles use the permanent HTTPS hostname. No raw-IP or broad ATS exception is present; local-network permission remains limited to LOCAL development.

## Admin Portal

Set `VITE_ENABLE_ENVIRONMENT_SWITCHER=true` to show the compact header selector. `VITE_DEFAULT_APP_ENV` sets the build default. Local and hosted API/issuer pairs are configured with `VITE_LOCAL_*` and `VITE_HOSTED_*` variables shown in `.env.example`.

The selection is persisted in localStorage only when switching is enabled. Switching removes the current OIDC user and development identity, clears query data, then reloads. OIDC session storage is environment-prefixed. Disable the selector for public builds; stored overrides are then ignored.

Local Admin callback origins remain `http://localhost:5173` and `http://127.0.0.1:5173`; this client change does not broaden CORS or Keycloak redirect rules.
