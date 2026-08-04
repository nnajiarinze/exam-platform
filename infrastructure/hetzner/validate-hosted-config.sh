#!/usr/bin/env bash
set -Eeuo pipefail
REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${REPOSITORY_ROOT}/docker-compose.hosted.yml"
ENV_FILE="${1:-${REPOSITORY_ROOT}/.env.hosted.example}"

compose_config() {
  env -i PATH="${PATH}" HOME="${HOME}" COMPOSE_DISABLE_ENV_FILE=1 "$@"
}
compose_config docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config --quiet
rendered="$(compose_config docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config)"
override_rendered="$(compose_config env GATEWAY_CONFIG_TEMPLATE=./infrastructure/gateway/bootstrap-http.conf.template \
  docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config)"

[[ "$(grep -cE '^[[:space:]]+published:' <<<"${rendered}")" -eq 2 ]] || {
  printf 'Expected exactly two published gateway ports.\n' >&2; exit 1;
}
for service in content-service learning-service ai-service keycloak; do
  section="$(awk -v service="${service}:" '$0=="  " service{show=1;next} show&&/^  [a-zA-Z]/{exit} show{print}' <<<"${rendered}")"
  ! grep -qE '^[[:space:]]+ports:' <<<"${section}" || {
    printf '%s unexpectedly publishes a host port.\n' "${service}" >&2; exit 1;
  }
  grep -q 'restart: unless-stopped' <<<"${section}" || {
    printf '%s has no restart policy.\n' "${service}" >&2; exit 1;
  }
  grep -q 'mem_limit:' <<<"${section}" || {
    printf '%s has no memory limit.\n' "${service}" >&2; exit 1;
  }
done

if grep -R -nE 'citizenship-.*\\.onrender\\.com|http://46\\.224\\.221\\.7' \
  "${COMPOSE_FILE}" "${REPOSITORY_ROOT}/infrastructure/gateway/hosted.conf.template" \
  "${REPOSITORY_ROOT}/apps/mobile/src/config/environment.ts"; then
  printf 'Hosted runtime configuration still contains a Render backend URL.\n' >&2
  exit 1
fi
GATEWAY_LOCATIONS="${REPOSITORY_ROOT}/infrastructure/gateway/hosted-locations.conf"
grep -q "connect-src 'self'" "${GATEWAY_LOCATIONS}" || {
  printf 'Hosted Admin CSP does not restrict browser connections to the gateway origin.\n' >&2; exit 1;
}
grep -q 'try_files \$uri \$uri/ /index.html' "${GATEWAY_LOCATIONS}" || {
  printf 'Hosted gateway does not serve the Admin SPA callback routes.\n' >&2; exit 1;
}
grep -qE '^[[:space:]]+source: .*infrastructure/gateway/hosted\.conf\.template$' <<<"${rendered}" || {
  printf 'Hosted gateway is not pinned to the HTTPS template.\n' >&2; exit 1;
}
! grep -q 'GATEWAY_CONFIG_TEMPLATE' "${COMPOSE_FILE}" || {
  printf 'Hosted Compose still permits runtime gateway template overrides.\n' >&2; exit 1;
}
grep -qE 'source: .*infrastructure/gateway/hosted\.conf\.template$' <<<"${override_rendered}" || {
  printf 'A stale environment value can still select bootstrap HTTP.\n' >&2; exit 1;
}
! grep -qE 'source: .*bootstrap-http\.conf\.template$' <<<"${override_rendered}" || exit 1
grep -q 'listen 8443 ssl;' "${REPOSITORY_ROOT}/infrastructure/gateway/hosted.conf.template" || {
  printf 'Hosted gateway has no TLS listener.\n' >&2; exit 1;
}
grep -q 'X-Forwarded-Port 443' "${GATEWAY_LOCATIONS}" || exit 1
grep -q 'X-Forwarded-Proto https' "${GATEWAY_LOCATIONS}" || exit 1
grep -q 'mode.*hosted-https' "${REPOSITORY_ROOT}/infrastructure/gateway/hosted.conf.template" || exit 1
grep -q 'API_DOMAIN: api.tinkona.com' <<<"${rendered}" || exit 1
grep -q 'ROLLBACK_HOSTNAME: api.46-224-221-7.sslip.io' <<<"${rendered}" || exit 1
grep -q "https://api.tinkona.com" "${REPOSITORY_ROOT}/apps/mobile/src/config/environment.ts" || exit 1
grep -q '/tmp/nginx-certs/primary/fullchain.pem' "${REPOSITORY_ROOT}/infrastructure/gateway/hosted.conf.template" || exit 1
grep -q '/tmp/nginx-certs/legacy/fullchain.pem' "${REPOSITORY_ROOT}/infrastructure/gateway/hosted.conf.template" || exit 1
SNAPSHOT_SCRIPT="${REPOSITORY_ROOT}/infrastructure/hetzner/snapshot-hosted-domain-state.sh"
bash -n "${SNAPSHOT_SCRIPT}"
! grep -qF 'postgres:18-alpine +' "${SNAPSHOT_SCRIPT}" || {
  printf 'Hosted state snapshot contains an invalid PostgreSQL container command.\n' >&2; exit 1;
}
! grep -qE 'docker run .* -i([[:space:]]|$)' "${SNAPSHOT_SCRIPT}" || {
  printf 'Hosted state snapshot may not consume the parent deployment stdin.\n' >&2; exit 1;
}
grep -qF 'default_transaction_read_only=on' "${SNAPSHOT_SCRIPT}" || {
  printf 'Hosted state snapshot does not enforce read-only SQL transactions.\n' >&2; exit 1;
}
printf 'Hosted Compose security and routing validation passed.\n'
