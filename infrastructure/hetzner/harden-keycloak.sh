#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths
require_command docker
require_command jq

mode="${KEYCLOAK_SECURITY_MODE:-$(env_file_value KEYCLOAK_SECURITY_MODE "${PLATFORM_ENV_FILE}")}"
mode="${mode:-BOOTSTRAP_HTTP}"
admin_portal_url="${ADMIN_PORTAL_URL:-$(env_file_value ADMIN_PORTAL_URL "${PLATFORM_ENV_FILE}")}"
if [[ -z "${admin_portal_url}" ]]; then
  api_domain="$(env_file_value API_DOMAIN "${PLATFORM_ENV_FILE}")"
  public_scheme="$(env_file_value PUBLIC_SCHEME "${PLATFORM_ENV_FILE}")"
  admin_portal_url="${public_scheme:-https}://${api_domain}"
fi
[[ "${admin_portal_url}" == https://* && "${admin_portal_url}" != *onrender.com* ]] || die "Hosted Admin URL must be the non-Render HTTPS gateway"
container="${KEYCLOAK_CONTAINER:-citizenship-platform-keycloak-1}"
internal_url="${KEYCLOAK_INTERNAL_URL:-http://127.0.0.1:8080/auth}"

case "${mode}" in
  BOOTSTRAP_HTTP) ssl_required="none" ;;
  HTTPS_HOSTED) ssl_required="external" ;;
  *) die "KEYCLOAK_SECURITY_MODE must be BOOTSTRAP_HTTP or HTTPS_HOSTED" ;;
esac

docker inspect "${container}" >/dev/null
docker exec -e KEYCLOAK_HARDENING_INTERNAL_URL="${internal_url}" "${container}" sh -ec '
  /opt/keycloak/bin/kcadm.sh config credentials \
    --config /tmp/kcadm-hardening.config \
    --server "$KEYCLOAK_HARDENING_INTERNAL_URL" \
    --realm master \
    --user "$KC_BOOTSTRAP_ADMIN_USERNAME" \
    --password "$KC_BOOTSTRAP_ADMIN_PASSWORD" >/dev/null
'
cleanup() {
  docker exec "${container}" unlink /tmp/kcadm-hardening.config >/dev/null 2>&1 || true
}
trap cleanup EXIT

kcadm() {
  local command="$1"
  shift
  docker exec "${container}" /opt/keycloak/bin/kcadm.sh \
    "${command}" --config /tmp/kcadm-hardening.config "$@"
}

kcadm update realms/exam-platform \
  -s "sslRequired=${ssl_required}" \
  -s bruteForceProtected=true \
  -s permanentLockout=false \
  -s failureFactor=5 \
  -s waitIncrementSeconds=60 \
  -s maxFailureWaitSeconds=900 \
  -s minimumQuickLoginWaitSeconds=60 \
  -s quickLoginCheckMilliSeconds=1000 \
  -s maxDeltaTimeSeconds=43200 \
  -s 'passwordPolicy=length(10) and upperCase(1) and lowerCase(1) and digits(1) and notUsername(undefined) and passwordHistory(5)' \
  >/dev/null

client_id() {
  kcadm get clients -r exam-platform -q "clientId=$1" |
    jq -er 'if length == 1 then .[0].id else error("expected exactly one client") end'
}

mobile_id="$(client_id mobile-app)"
admin_id="$(client_id admin-portal)"
if [[ "${mode}" == "BOOTSTRAP_HTTP" ]]; then
  kcadm update "clients/${mobile_id}" -r exam-platform \
    -s 'redirectUris=["sveastudy://auth/callback","exp://192.168.1.213:8081/--/auth/callback"]' \
    -s 'webOrigins=[]' >/dev/null
  admin_redirects="$(jq -cn --arg hosted "${admin_portal_url}/auth/callback" \
    '["http://localhost:5173/auth/callback","http://127.0.0.1:5173/auth/callback",$hosted]')"
  admin_origins="$(jq -cn --arg hosted "${admin_portal_url}" \
    '["http://localhost:5173","http://127.0.0.1:5173",$hosted]')"
  logout_redirects="http://localhost:5173##http://127.0.0.1:5173##${admin_portal_url}"
else
  kcadm update "clients/${mobile_id}" -r exam-platform \
    -s 'redirectUris=["sveastudy://auth/callback"]' \
    -s 'webOrigins=[]' >/dev/null
  admin_redirects="$(jq -cn --arg hosted "${admin_portal_url}/auth/callback" '[$hosted]')"
  admin_origins="$(jq -cn --arg hosted "${admin_portal_url}" '[$hosted]')"
  logout_redirects="${admin_portal_url}"
fi
kcadm update "clients/${admin_id}" -r exam-platform \
  -s "redirectUris=${admin_redirects}" \
  -s "webOrigins=${admin_origins}" \
  -s "attributes.\"post.logout.redirect.uris\"=${logout_redirects}" >/dev/null

kcadm get realms/exam-platform |
  jq -e --arg ssl "${ssl_required}" '
    .sslRequired == $ssl and
    .bruteForceProtected == true and
    (.passwordPolicy | contains("length(10)"))' >/dev/null
printf 'Keycloak realm hardening applied in %s mode.\n' "${mode}"
