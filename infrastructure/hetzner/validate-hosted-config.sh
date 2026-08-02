#!/usr/bin/env bash
set -Eeuo pipefail
REPOSITORY_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${REPOSITORY_ROOT}/docker-compose.hosted.yml"
ENV_FILE="${1:-${REPOSITORY_ROOT}/.env.hosted.example}"

docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config --quiet
rendered="$(docker compose --env-file "${ENV_FILE}" -f "${COMPOSE_FILE}" config)"

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
grep -q "connect-src 'self'" "${REPOSITORY_ROOT}/infrastructure/gateway/hosted.conf.template" || {
  printf 'Hosted Admin CSP does not restrict browser connections to the gateway origin.\n' >&2; exit 1;
}
grep -q 'try_files \$uri \$uri/ /index.html' "${REPOSITORY_ROOT}/infrastructure/gateway/hosted.conf.template" || {
  printf 'Hosted gateway does not serve the Admin SPA callback routes.\n' >&2; exit 1;
}
printf 'Hosted Compose security and routing validation passed.\n'
