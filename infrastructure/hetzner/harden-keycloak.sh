#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths
require_command docker
require_command jq
require_command curl
require_command python3

mode="${KEYCLOAK_SECURITY_MODE:-$(env_file_value KEYCLOAK_SECURITY_MODE "${PLATFORM_ENV_FILE}")}"
mode="${mode:-BOOTSTRAP_HTTP}"
admin_portal_url="${ADMIN_PORTAL_URL:-$(env_file_value ADMIN_PORTAL_URL "${PLATFORM_ENV_FILE}")}"
if [[ -z "${admin_portal_url}" ]]; then
  api_domain="$(env_file_value API_DOMAIN "${PLATFORM_ENV_FILE}")"
  public_scheme="$(env_file_value PUBLIC_SCHEME "${PLATFORM_ENV_FILE}")"
  admin_portal_url="${public_scheme:-https}://${api_domain}"
fi
[[ "${admin_portal_url}" == https://* && "${admin_portal_url}" != *onrender.com* ]] || die "Hosted Admin URL must be the non-Render HTTPS gateway"
legacy_admin_portal_url="${LEGACY_ADMIN_PORTAL_URL:-$(env_file_value LEGACY_ADMIN_PORTAL_URL "${PLATFORM_ENV_FILE}")}"
if [[ -n "${legacy_admin_portal_url}" ]]; then
  [[ "${legacy_admin_portal_url}" == https://* && "${legacy_admin_portal_url}" != *onrender.com* ]] || die "Legacy Admin rollback URL must use HTTPS"
fi
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
  -s emailTheme=svea-study \
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
account_id="$(client_id account)"
if [[ "${mode}" == "BOOTSTRAP_HTTP" ]]; then
  kcadm update "clients/${mobile_id}" -r exam-platform \
    -s 'redirectUris=["sveastudy://auth/callback","exp://192.168.1.213:8081/--/auth/callback"]' \
    -s 'webOrigins=[]' >/dev/null
  admin_redirects="$(jq -cn --arg hosted "${admin_portal_url}/oidc/callback" --arg legacy "${legacy_admin_portal_url:+${legacy_admin_portal_url}/oidc/callback}" \
    '["http://localhost:5173/oidc/callback","http://127.0.0.1:5173/oidc/callback",$hosted,$legacy] | map(select(length>0)) | unique')"
  admin_origins="$(jq -cn --arg hosted "${admin_portal_url}" --arg legacy "${legacy_admin_portal_url}" \
    '["http://localhost:5173","http://127.0.0.1:5173",$hosted,$legacy] | map(select(length>0)) | unique')"
  logout_redirects="http://localhost:5173##http://127.0.0.1:5173##${admin_portal_url}${legacy_admin_portal_url:+##${legacy_admin_portal_url}}"
else
  kcadm update "clients/${mobile_id}" -r exam-platform \
    -s 'redirectUris=["sveastudy://auth/callback"]' \
    -s 'webOrigins=[]' >/dev/null
  admin_redirects="$(jq -cn --arg hosted "${admin_portal_url}/oidc/callback" --arg legacy "${legacy_admin_portal_url:+${legacy_admin_portal_url}/oidc/callback}" '[$hosted,$legacy] | map(select(length>0)) | unique')"
  admin_origins="$(jq -cn --arg hosted "${admin_portal_url}" --arg legacy "${legacy_admin_portal_url}" '[$hosted,$legacy] | map(select(length>0)) | unique')"
  logout_redirects="${admin_portal_url}${legacy_admin_portal_url:+##${legacy_admin_portal_url}}"
fi
kcadm update "clients/${admin_id}" -r exam-platform \
  -s "redirectUris=${admin_redirects}" \
  -s "webOrigins=${admin_origins}" \
  -s "attributes.\"post.logout.redirect.uris\"=${logout_redirects}" >/dev/null

# Keycloak's supported application-initiated action for account linking needs
# only the narrow account.manage-account-links client role in mobile tokens.
manage_links_role="$(kcadm get "clients/${account_id}/roles/manage-account-links" -r exam-platform)"
if ! kcadm get "clients/${mobile_id}/scope-mappings/clients/${account_id}" -r exam-platform | jq -e 'any(.name=="manage-account-links")' >/dev/null; then
  printf '[%s]' "${manage_links_role}" | docker exec -i "${container}" /opt/keycloak/bin/kcadm.sh create \
    "clients/${mobile_id}/scope-mappings/clients/${account_id}" -r exam-platform \
    --config /tmp/kcadm-hardening.config -f - >/dev/null
fi

# Provision the least-privilege confidential client used only by Learning
# Service's identity-management BFF. It is disabled when protected inputs are
# absent; no Admin credential is ever exposed to a public client.
identity_management_enabled="${IDENTITY_MANAGEMENT_ENABLED:-$(env_file_value IDENTITY_MANAGEMENT_ENABLED "${PLATFORM_ENV_FILE}")}"
identity_bff_client_id="${IDENTITY_BFF_CLIENT_ID:-$(env_file_value IDENTITY_BFF_CLIENT_ID "${PLATFORM_ENV_FILE}")}"
identity_bff_client_secret="${IDENTITY_BFF_CLIENT_SECRET:-$(env_file_value IDENTITY_BFF_CLIENT_SECRET "${PLATFORM_ENV_FILE}")}"
identity_bff_client_id="${identity_bff_client_id:-identity-management-bff}"
identity_bff_matches="$(kcadm get clients -r exam-platform -q "clientId=${identity_bff_client_id}")"
identity_bff_exists="$(jq -r 'length==1' <<<"${identity_bff_matches}")"
if [[ "${identity_management_enabled}" == true && -n "${identity_bff_client_secret}" && "${identity_bff_client_secret}" != CHANGE_ME ]]; then
  identity_bff_json="$(jq -cn --arg id "${identity_bff_client_id}" --arg secret "${identity_bff_client_secret}" '{clientId:$id,name:"Svea Study identity management BFF",enabled:true,publicClient:false,serviceAccountsEnabled:true,standardFlowEnabled:false,directAccessGrantsEnabled:false,implicitFlowEnabled:false,bearerOnly:false,secret:$secret,protocol:"openid-connect",attributes:{"client.secret.creation.time":"0"}}')"
  if [[ "${identity_bff_exists}" == true ]]; then
    identity_bff_id="$(jq -er '.[0].id' <<<"${identity_bff_matches}")"
    printf '%s' "${identity_bff_json}" | docker exec -i "${container}" /opt/keycloak/bin/kcadm.sh update \
      "clients/${identity_bff_id}" -r exam-platform --config /tmp/kcadm-hardening.config -f - >/dev/null
  else
    printf '%s' "${identity_bff_json}" | docker exec -i "${container}" /opt/keycloak/bin/kcadm.sh create \
      clients -r exam-platform --config /tmp/kcadm-hardening.config -f - >/dev/null
    identity_bff_id="$(client_id "${identity_bff_client_id}")"
  fi
  service_account_user="$(kcadm get "clients/${identity_bff_id}/service-account-user" -r exam-platform | jq -er .id)"
  realm_management_id="$(client_id realm-management)"
  for role in view-users manage-users view-realm; do
    role_json="$(kcadm get "clients/${realm_management_id}/roles/${role}" -r exam-platform)"
    if ! kcadm get "users/${service_account_user}/role-mappings/clients/${realm_management_id}" -r exam-platform | jq -e --arg role "${role}" 'any(.name==$role)' >/dev/null; then
      printf '[%s]' "${role_json}" | docker exec -i "${container}" /opt/keycloak/bin/kcadm.sh create \
        "users/${service_account_user}/role-mappings/clients/${realm_management_id}" -r exam-platform \
        --config /tmp/kcadm-hardening.config -f - >/dev/null
    fi
  done
elif [[ "${identity_bff_exists}" == true ]]; then
  identity_bff_id="$(jq -er '.[0].id' <<<"${identity_bff_matches}")"
  kcadm update "clients/${identity_bff_id}" -r exam-platform -s enabled=false >/dev/null
fi

# Social provider secrets remain in the protected host environment. Providers
# are hidden from the generic realm page so privileged Admin authentication is
# not silently broadened; the mobile client selects them with kc_idp_hint.
google_client_id="${KEYCLOAK_GOOGLE_CLIENT_ID:-$(env_file_value KEYCLOAK_GOOGLE_CLIENT_ID "${PLATFORM_ENV_FILE}")}"
google_client_secret="${KEYCLOAK_GOOGLE_CLIENT_SECRET:-$(env_file_value KEYCLOAK_GOOGLE_CLIENT_SECRET "${PLATFORM_ENV_FILE}")}"
google_enabled="${KEYCLOAK_GOOGLE_ENABLED:-$(env_file_value KEYCLOAK_GOOGLE_ENABLED "${PLATFORM_ENV_FILE}")}"
google_exists="$(kcadm get identity-provider/instances -r exam-platform | jq -r 'any(.alias=="google")')"
if [[ "${google_enabled}" == true && -n "${google_client_id}" && -n "${google_client_secret}" &&
      "${google_client_id}" != CHANGE_ME && "${google_client_secret}" != CHANGE_ME ]]; then
  google_json="$(jq -cn --arg client "${google_client_id}" --arg secret "${google_client_secret}" '{
    alias:"google",displayName:"Fortsätt med Google",providerId:"google",enabled:true,trustEmail:true,storeToken:false,
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

apple_enabled="${KEYCLOAK_APPLE_ENABLED:-$(env_file_value KEYCLOAK_APPLE_ENABLED "${PLATFORM_ENV_FILE}")}"
apple_services_id="${KEYCLOAK_APPLE_SERVICES_ID:-$(env_file_value KEYCLOAK_APPLE_SERVICES_ID "${PLATFORM_ENV_FILE}")}"
apple_team_id="${KEYCLOAK_APPLE_TEAM_ID:-$(env_file_value KEYCLOAK_APPLE_TEAM_ID "${PLATFORM_ENV_FILE}")}"
apple_key_id="${KEYCLOAK_APPLE_KEY_ID:-$(env_file_value KEYCLOAK_APPLE_KEY_ID "${PLATFORM_ENV_FILE}")}"
apple_private_key_base64="${KEYCLOAK_APPLE_PRIVATE_KEY_BASE64:-$(env_file_value KEYCLOAK_APPLE_PRIVATE_KEY_BASE64 "${PLATFORM_ENV_FILE}")}"
apple_exists="$(kcadm get identity-provider/instances -r exam-platform | jq -r 'any(.alias=="apple")')"
if [[ "${apple_enabled}" == true && -n "${apple_services_id}" && -n "${apple_team_id}" && -n "${apple_key_id}" &&
      -n "${apple_private_key_base64}" && "${apple_private_key_base64}" != CHANGE_ME ]]; then
  apple_private_key="$(printf '%s' "${apple_private_key_base64}" | base64 -d)"
  grep -q '^-----BEGIN PRIVATE KEY-----$' <<<"${apple_private_key}"
  grep -q '^-----END PRIVATE KEY-----$' <<<"${apple_private_key}"
  apple_json="$(jq -cn --arg client "${apple_services_id}" --arg secret "${apple_private_key}" --arg team "${apple_team_id}" --arg key "${apple_key_id}" '{
    alias:"apple",displayName:"Fortsätt med Apple",providerId:"apple",enabled:true,trustEmail:true,storeToken:false,
    addReadTokenRoleOnCreate:false,authenticateByDefault:false,linkOnly:false,hideOnLogin:true,
    firstBrokerLoginFlowAlias:"first broker login",config:{clientId:$client,clientSecret:$secret,teamId:$team,keyId:$key,
    defaultScope:"name%20email",syncMode:"IMPORT",tokenExchangeAccountLinkingEnabled:"false"}}')"
  if [[ "${apple_exists}" == true ]]; then
    printf '%s' "${apple_json}" | docker exec -i "${container}" /opt/keycloak/bin/kcadm.sh update \
      identity-provider/instances/apple -r exam-platform --config /tmp/kcadm-hardening.config -f - >/dev/null
  else
    printf '%s' "${apple_json}" | docker exec -i "${container}" /opt/keycloak/bin/kcadm.sh create \
      identity-provider/instances -r exam-platform --config /tmp/kcadm-hardening.config -f - >/dev/null
  fi
  unset apple_private_key apple_json
elif [[ "${apple_exists}" == true ]]; then
  kcadm update identity-provider/instances/apple -r exam-platform -s enabled=false >/dev/null
fi

# Configure realm SMTP only when a complete protected sender configuration is
# present. Password and action-link bodies are never printed by this script.
smtp_host="${KEYCLOAK_SMTP_HOST:-$(env_file_value KEYCLOAK_SMTP_HOST "${PLATFORM_ENV_FILE}")}"
smtp_port="${KEYCLOAK_SMTP_PORT:-$(env_file_value KEYCLOAK_SMTP_PORT "${PLATFORM_ENV_FILE}")}"
smtp_username="${KEYCLOAK_SMTP_USERNAME:-$(env_file_value KEYCLOAK_SMTP_USERNAME "${PLATFORM_ENV_FILE}")}"
smtp_password="${RESEND_API_KEY:-$(env_file_value RESEND_API_KEY "${PLATFORM_ENV_FILE}")}"
smtp_from="${KEYCLOAK_SMTP_FROM:-$(env_file_value KEYCLOAK_SMTP_FROM "${PLATFORM_ENV_FILE}")}"
smtp_from_name="${KEYCLOAK_SMTP_FROM_DISPLAY_NAME:-$(env_file_value KEYCLOAK_SMTP_FROM_DISPLAY_NAME "${PLATFORM_ENV_FILE}")}"
smtp_reply_to="${KEYCLOAK_SMTP_REPLY_TO:-$(env_file_value KEYCLOAK_SMTP_REPLY_TO "${PLATFORM_ENV_FILE}")}"
smtp_reply_to_name="${KEYCLOAK_SMTP_REPLY_TO_DISPLAY_NAME:-$(env_file_value KEYCLOAK_SMTP_REPLY_TO_DISPLAY_NAME "${PLATFORM_ENV_FILE}")}"
smtp_starttls="${KEYCLOAK_SMTP_STARTTLS:-$(env_file_value KEYCLOAK_SMTP_STARTTLS "${PLATFORM_ENV_FILE}")}"
smtp_ssl="${KEYCLOAK_SMTP_SSL:-$(env_file_value KEYCLOAK_SMTP_SSL "${PLATFORM_ENV_FILE}")}"
smtp_configured=false
if [[ "${smtp_host}" == smtp.resend.com && "${smtp_port:-587}" == 587 && "${smtp_username}" == resend &&
      -n "${smtp_password}" && "${smtp_password}" != CHANGE_ME && "${smtp_from}" == no-reply@tinkona.com &&
      "${smtp_reply_to}" == support@tinkona.com && "${smtp_starttls:-true}" == true && "${smtp_ssl:-false}" == false ]]; then
  smtp_json="$(jq -cn --arg host "${smtp_host}" --arg port "${smtp_port:-587}" --arg user "${smtp_username}" --arg password "${smtp_password}" --arg from "${smtp_from}" --arg fromName "${smtp_from_name:-Svea Study}" --arg replyTo "${smtp_reply_to}" --arg replyToName "${smtp_reply_to_name}" --arg starttls "${smtp_starttls:-true}" --arg ssl "${smtp_ssl:-false}" '{host:$host,port:$port,from:$from,fromDisplayName:$fromName,replyTo:$replyTo,replyToDisplayName:$replyToName,starttls:$starttls,ssl:$ssl,auth:"true",user:$user,password:$password}')"
  kcadm update realms/exam-platform -s "smtpServer=${smtp_json}" >/dev/null
  resend_domains="$(printf 'header = "Authorization: Bearer %s"\nheader = "User-Agent: SveaStudy-Keycloak-Provisioning/1.0"\n' "${smtp_password}" | curl --config - --fail --silent --show-error --max-time 15 https://api.resend.com/domains 2>/dev/null || printf '{"data":[]}')"
  resend_domain="$(jq -c '[.data[]? | select(.name=="tinkona.com")][0] // {}' <<<"${resend_domains}")"
  resend_domain_id="$(jq -r '.id // empty' <<<"${resend_domain}")"
  if [[ -n "${resend_domain_id}" ]]; then
    resend_domain="$(printf 'header = "Authorization: Bearer %s"\nheader = "User-Agent: SveaStudy-Keycloak-Provisioning/1.0"\n' "${smtp_password}" | curl --config - --fail --silent --show-error --max-time 15 "https://api.resend.com/domains/${resend_domain_id}" 2>/dev/null || printf '{}')"
  fi
  resend_domain_status="$(jq -r '.status // "unknown"' <<<"${resend_domain}")"
  resend_spf_status="$(jq -r '[.records[]? | select((.record // .type // "")|ascii_downcase|contains("spf")) | .status][0] // "unknown"' <<<"${resend_domain}")"
  resend_dkim_status="$(jq -r '[.records[]? | select((.record // .type // "")|ascii_downcase|contains("dkim")) | .status][0] // "unknown"' <<<"${resend_domain}")"
  email_dmarc_status="${KEYCLOAK_EMAIL_DMARC_STATUS:-$(env_file_value KEYCLOAK_EMAIL_DMARC_STATUS "${PLATFORM_ENV_FILE}")}"
  if ! SMTP_PASSWORD="${smtp_password}" python3 - <<'PY'
import os
import smtplib
import ssl

try:
    smtp = smtplib.SMTP("smtp.resend.com", 587, timeout=15)
    smtp.ehlo()
    smtp.starttls(context=ssl.create_default_context())
    smtp.ehlo()
    smtp.login("resend", os.environ["SMTP_PASSWORD"])
    smtp.quit()
except Exception:
    raise SystemExit(1)
PY
  then
    die "Resend SMTP STARTTLS authentication failed"
  fi
  smtp_test_at="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"
  realm_attributes="$(kcadm get realms/exam-platform | jq -c --arg domain "${resend_domain_status}" --arg spf "${resend_spf_status}" --arg dkim "${resend_dkim_status}" --arg dmarc "${email_dmarc_status:-present}" --arg smtpTestAt "${smtp_test_at}" '(.attributes // {}) + {resendDomainStatus:$domain,resendSpfStatus:$spf,resendDkimStatus:$dkim,emailDmarcStatus:$dmarc,lastSmtpTestAt:$smtpTestAt}')"
  kcadm update realms/exam-platform -s "attributes=${realm_attributes}" >/dev/null
  smtp_configured=true
  unset smtp_password smtp_json smtp_test_at resend_domains resend_domain resend_domain_id
fi
if [[ "${mode}" == HTTPS_HOSTED && "${smtp_configured}" != true ]]; then
  die "Hosted Resend SMTP configuration is incomplete"
fi

# Fail closed if the realm's first-broker flow contains the unsafe automatic
# email-link authenticator.
first_broker_flow_id="$(kcadm get authentication/flows -r exam-platform | jq -er '.[]|select(.alias=="first broker login")|.id')"
if kcadm get "authentication/flows/${first_broker_flow_id}/executions" -r exam-platform | jq -e 'any(.providerId=="idp-auto-link")' >/dev/null; then
  die "Unsafe automatic first-broker email linking is configured"
fi

realm_configuration="$(kcadm get realms/exam-platform)"
jq -e --arg ssl "${ssl_required}" '
    .sslRequired == $ssl and .loginTheme == "svea-study" and .emailTheme == "svea-study" and
    .internationalizationEnabled == true and .defaultLocale == "sv" and
    (.supportedLocales | sort) == (["en","sv"] | sort) and
    .registrationAllowed == true and .verifyEmail == true and
    .resetPasswordAllowed == true and .duplicateEmailsAllowed == false and
    .bruteForceProtected == true and
    (.passwordPolicy | contains("length(10)"))' <<<"${realm_configuration}" >/dev/null
if [[ "${mode}" == HTTPS_HOSTED ]]; then
  jq -e '.smtpServer.host=="smtp.resend.com" and .smtpServer.port=="587" and
    .smtpServer.starttls=="true" and .smtpServer.ssl=="false" and .smtpServer.auth=="true" and
    .smtpServer.user=="resend" and .smtpServer.from=="no-reply@tinkona.com" and
    .smtpServer.replyTo=="support@tinkona.com" and (.smtpServer.password|length)>0' \
    <<<"${realm_configuration}" >/dev/null
fi
unset realm_configuration
printf 'Keycloak realm hardening applied in %s mode.\n' "${mode}"
