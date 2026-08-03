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
  -s loginTheme=svea-study \
  -s internationalizationEnabled=true \
  -s 'supportedLocales=["sv","en"]' \
  -s defaultLocale=sv \
  -s registrationAllowed=true \
  -s verifyEmail=true \
  -s resetPasswordAllowed=true \
  -s loginWithEmailAllowed=true \
  -s duplicateEmailsAllowed=false \
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
  admin_redirects="$(jq -cn --arg hosted "${admin_portal_url}/oidc/callback" \
    '["http://localhost:5173/oidc/callback","http://127.0.0.1:5173/oidc/callback",$hosted]')"
  admin_origins="$(jq -cn --arg hosted "${admin_portal_url}" \
    '["http://localhost:5173","http://127.0.0.1:5173",$hosted]')"
  logout_redirects="http://localhost:5173##http://127.0.0.1:5173##${admin_portal_url}"
else
  kcadm update "clients/${mobile_id}" -r exam-platform \
    -s 'redirectUris=["sveastudy://auth/callback"]' \
    -s 'webOrigins=[]' >/dev/null
  admin_redirects="$(jq -cn --arg hosted "${admin_portal_url}/oidc/callback" '[$hosted]')"
  admin_origins="$(jq -cn --arg hosted "${admin_portal_url}" '[$hosted]')"
  logout_redirects="${admin_portal_url}"
fi
kcadm update "clients/${admin_id}" -r exam-platform \
  -s "redirectUris=${admin_redirects}" \
  -s "webOrigins=${admin_origins}" \
  -s "attributes.\"post.logout.redirect.uris\"=${logout_redirects}" >/dev/null

# Social provider secrets remain in the protected host environment. Providers
# are hidden from the generic realm page so privileged Admin authentication is
# not silently broadened; the mobile client selects them with kc_idp_hint.
google_client_id="${KEYCLOAK_GOOGLE_CLIENT_ID:-$(env_file_value KEYCLOAK_GOOGLE_CLIENT_ID "${PLATFORM_ENV_FILE}")}"
google_client_secret="${KEYCLOAK_GOOGLE_CLIENT_SECRET:-$(env_file_value KEYCLOAK_GOOGLE_CLIENT_SECRET "${PLATFORM_ENV_FILE}")}"
google_exists="$(kcadm get identity-provider/instances -r exam-platform | jq -r 'any(.alias=="google")')"
if [[ -n "${google_client_id}" && -n "${google_client_secret}" &&
      "${google_client_id}" != CHANGE_ME && "${google_client_secret}" != CHANGE_ME ]]; then
  google_json="$(jq -cn --arg client "${google_client_id}" --arg secret "${google_client_secret}" '{
    alias:"google",providerId:"google",enabled:true,trustEmail:false,storeToken:false,
    addReadTokenRoleOnCreate:false,authenticateByDefault:false,linkOnly:false,hideOnLogin:true,
    firstBrokerLoginFlowAlias:"first broker login",config:{clientId:$client,clientSecret:$secret,
    defaultScope:"openid profile email",syncMode:"IMPORT",useJwksUrl:"true"}}')"
  if [[ "${google_exists}" == true ]]; then
    printf '%s' "${google_json}" | docker exec -i "${container}" /opt/keycloak/bin/kcadm.sh update \
      identity-provider/instances/google -r exam-platform --config /tmp/kcadm-hardening.config -f - >/dev/null
  else
    printf '%s' "${google_json}" | docker exec -i "${container}" /opt/keycloak/bin/kcadm.sh create \
      identity-provider/instances -r exam-platform --config /tmp/kcadm-hardening.config -f - >/dev/null
  fi
elif [[ "${google_exists}" == true ]]; then
  # Removing protected credentials must fail closed, including after a restart.
  kcadm update identity-provider/instances/google -r exam-platform -s enabled=false >/dev/null
fi

kcadm get realms/exam-platform |
  jq -e --arg ssl "${ssl_required}" '
    .sslRequired == $ssl and .loginTheme == "svea-study" and
    .internationalizationEnabled == true and .defaultLocale == "sv" and
    (.supportedLocales | sort) == (["en","sv"] | sort) and
    .registrationAllowed == true and .verifyEmail == true and
    .resetPasswordAllowed == true and .duplicateEmailsAllowed == false and
    .bruteForceProtected == true and
    (.passwordPolicy | contains("length(10)"))' >/dev/null
printf 'Keycloak realm hardening applied in %s mode.\n' "${mode}"
