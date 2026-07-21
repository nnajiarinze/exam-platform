# Local authentication

Start the identity provider and services:

```bash
docker compose up -d --build identity-provider learning-service content-service
```

Keycloak is available at `http://localhost:8090`; the realm is
`exam-platform`. The local Keycloak administration account is `admin` /
`local-keycloak-admin` and must never be reused outside local development.

Demo users:

| Username | Password | Role |
|---|---|---|
| `demo.learner` | `DemoLearner2026!` | `LEARNER` |
| `demo.author` | `DemoAuthor2026!` | `CONTENT_AUTHOR` |
| `demo.reviewer` | `DemoReviewer2026!` | `CONTENT_REVIEWER` |
| `demo.publisher` | `DemoPublisher2026!` | `CONTENT_PUBLISHER` |
| `demo.admin` | `DemoAdmin2026!` | `ADMIN` |

Run deterministic data seeding after migrations. Its learner profile subject is
the fixed Keycloak demo learner ID.

Mobile variables:

```env
EXPO_PUBLIC_OIDC_ISSUER=http://YOUR-LAN-IP:8090/realms/exam-platform
EXPO_PUBLIC_OIDC_CLIENT_ID=mobile-app
EXPO_PUBLIC_LEARNING_BASE_URL=http://YOUR-LAN-IP:8080
```

Admin variables:

```env
VITE_OIDC_AUTHORITY=http://localhost:8090/realms/exam-platform
VITE_OIDC_CLIENT_ID=admin-portal
VITE_CONTENT_SERVICE_BASE_URL=http://localhost:8082
VITE_DEV_ADMIN_AUTH_ENABLED=false
```

For physical mobile devices, configure Keycloak and the issuer consistently
with the LAN hostname. Issuer strings must match exactly. Production requires
HTTPS, production redirect/logout URI allowlists, SMTP for verification/reset,
managed secrets, provider backups, MFA policy for administrators, and removal
of all development-header flags and local credentials.
