#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths
require_command curl

INTERNAL=false
[[ "${1:-}" == "--internal" ]] && INTERNAL=true
require_var API_DOMAIN
PUBLIC_SCHEME="${PUBLIC_SCHEME:-$(env_file_value PUBLIC_SCHEME "${PLATFORM_ENV_FILE}")}"
PUBLIC_SCHEME="${PUBLIC_SCHEME:-https}"
[[ "${PUBLIC_SCHEME}" == "http" || "${PUBLIC_SCHEME}" == "https" ]] ||
  die "PUBLIC_SCHEME must be http or https"
PUBLIC_BASE="${PUBLIC_SCHEME}://${API_DOMAIN}"

health="$(curl --fail --silent --show-error "${PUBLIC_BASE}/healthz")"
if [[ "${PUBLIC_SCHEME}" == "https" ]]; then
  [[ "${health}" == '{"status":"UP","mode":"hosted-https"}' ]] ||
    die "Hosted HTTPS gateway reported an unexpected runtime mode: ${health}"
  curl --fail --silent --show-error --proto '=https' --tlsv1.2 "${PUBLIC_BASE}/healthz" >/dev/null
fi
curl --fail --silent --show-error \
  "${PUBLIC_BASE}/auth/realms/exam-platform/.well-known/openid-configuration" >/dev/null

status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  "${PUBLIC_BASE}/learning/api/v1/content/subjects?examId=swedish-citizenship")"
[[ "${status}" == "401" ]] || die "Protected Learning endpoint returned ${status}, expected 401"

if [[ -n "${ADMIN_PORTAL_URL:-}" ]]; then
  curl --fail --silent --show-error "${ADMIN_PORTAL_URL}" >/dev/null
fi

if "${INTERNAL}"; then
  if [[ "${PUBLIC_SCHEME}" == "https" ]]; then
    internal_https_health="$(compose exec -T api-gateway wget --no-check-certificate -q -O - https://127.0.0.1:8443/healthz)"
    [[ "${internal_https_health}" == '{"status":"UP","mode":"hosted-https"}' ]] ||
      die "Gateway has no valid internal HTTPS listener"
  fi
  compose exec -T api-gateway wget -q -O /dev/null http://content-service:8080/actuator/health/readiness
  compose exec -T api-gateway wget -q -O /dev/null http://learning-service:8080/actuator/health/readiness
  compose exec -T api-gateway wget -q -O /dev/null http://ai-service:8080/actuator/health/readiness
  compose exec -T api-gateway wget -q -O /dev/null http://keycloak:9000/auth/health/ready
fi

if [[ -n "${LEARNER_BEARER_TOKEN:-}" ]]; then
  curl --fail --silent --show-error \
    -H "Authorization: Bearer ${LEARNER_BEARER_TOKEN}" \
    "${PUBLIC_BASE}/learning/api/v1/content/subjects?examId=swedish-citizenship" >/dev/null
fi
if [[ -n "${ADMIN_BEARER_TOKEN:-}" ]]; then
  curl --fail --silent --show-error \
    -H "Authorization: Bearer ${ADMIN_BEARER_TOKEN}" \
    "${PUBLIC_BASE}/content/api/v1/status" >/dev/null
fi

printf 'Hosted smoke tests passed. Paid Gemini and email sends were not invoked.\n'
