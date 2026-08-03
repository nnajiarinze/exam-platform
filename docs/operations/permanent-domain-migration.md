# Permanent hosted domain

The authoritative hosted API, Admin and identity origin is
`https://api.tinkona.com`. The Keycloak realm issuer is
`https://api.tinkona.com/auth/realms/exam-platform` and the native callback
remains `sveastudy://auth/callback`.

During the controlled rollback window, `api.46-224-221-7.sslip.io` retains its
own certificate and SNI server block. It serves health, Admin and API routes,
but Keycloak remains authoritative for the permanent issuer. The legacy Admin
origin and callback are retained exactly in the Admin client and CORS list.
Their removal requires separate authorization after physical validation.

Future provider-console values are:

- Google authorized domain: `tinkona.com`
- Google/Keycloak broker callback: `https://api.tinkona.com/auth/realms/exam-platform/broker/google/endpoint`
- Apple web return URL: `https://api.tinkona.com/auth/realms/exam-platform/broker/apple/endpoint`
- Hosted origin: `https://api.tinkona.com`
- Native redirect URI: `sveastudy://auth/callback`
- Admin callback: `https://api.tinkona.com/oidc/callback`
- Admin logout URI: `https://api.tinkona.com`

Certificates renew through the existing twice-daily systemd timer. A successful
Certbot renewal recreates only the stateless gateway so protected keys are
recopied into its tmpfs before Nginx validation. Certificate files and private
keys never enter Git or application environment variables.
