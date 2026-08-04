#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

COMMIT_SHA="${1:-}"
PUBLIC_DOMAIN="${2:-}"
AUTHORITATIVE_IMAGE_REGISTRY="${3:-${IMAGE_REGISTRY:-}}"
require_sha "${COMMIT_SHA}"
[[ "${PUBLIC_DOMAIN}" == "api.tinkona.com" ]] || die "PUBLIC_DOMAIN must be api.tinkona.com"

require_command docker
require_command jq
require_file "${PLATFORM_ENV_FILE}"
require_file "${PLATFORM_RELEASE_ENV_FILE}"
require_file "${PLATFORM_COMPOSE_FILE}"
file_mode() {
  local path="$1"
  stat -c '%a' "${path}" 2>/dev/null || stat -f '%Lp' "${path}"
}
[[ "$(file_mode "${PLATFORM_ENV_FILE}")" == "600" ]] || die "${PLATFORM_ENV_FILE} must have mode 600"

ROOT="${PLATFORM_ROOT:-/opt/citizenship-platform}"
BACKUP_DIR="${PLATFORM_STATE_DIR}/backups/auth-deploy"
HARDEN_KEYCLOAK_SCRIPT="${HARDEN_KEYCLOAK_SCRIPT:-${SCRIPT_DIR}/harden-keycloak.sh}"
SMOKE_TEST_SCRIPT="${SMOKE_TEST_SCRIPT:-${SCRIPT_DIR}/smoke-test.sh}"
mkdir -p "${PLATFORM_STATE_DIR}" "${BACKUP_DIR}"
chmod 700 "${BACKUP_DIR}"

payload_file="$(mktemp "${PLATFORM_STATE_DIR}/auth-payload.XXXXXX")"
cleanup_payload() {
  rm -f "${payload_file}" >/dev/null 2>&1 || true
}
trap cleanup_payload EXIT
cat >"${payload_file}"
chmod 600 "${payload_file}"

google_client_id="$(jq -er '.keycloak_google_client_id' "${payload_file}")"
google_client_secret="$(jq -er '.keycloak_google_client_secret' "${payload_file}")"
google_enabled="$(jq -er '.keycloak_google_enabled' "${payload_file}")"
[[ -n "${google_client_id}" ]] || die "Protected KEYCLOAK_GOOGLE_CLIENT_ID is not provisioned"
[[ -n "${google_client_secret}" ]] || die "Protected KEYCLOAK_GOOGLE_CLIENT_SECRET is not provisioned"
[[ "${google_enabled}" == "true" ]] || die "KEYCLOAK_GOOGLE_ENABLED must be true in hosted vars/secrets"

env_file="${PLATFORM_ENV_FILE}"
release_env="${PLATFORM_RELEASE_ENV_FILE}"
compose_file="${PLATFORM_COMPOSE_FILE}"
AUTH_SERVICES=(api-gateway learning-service keycloak)

pending_env="$(mktemp "${env_file}.pending.XXXXXX")"
pending_release="$(mktemp "${release_env}.pending.XXXXXX")"
cp "${env_file}" "${pending_env}"
cp "${release_env}" "${pending_release}"
chmod 600 "${pending_env}" "${pending_release}"

update_file_env() {
  local target="$1" key="$2" value="$3" temporary
  temporary="$(mktemp "${target}.XXXXXX")"
  awk -F= -v key="${key}" -v value="${value}" '
    $1==key { next }
    { print }
    END { print key "=" value }
  ' "${target}" >"${temporary}"
  chmod 600 "${temporary}"
  mv "${temporary}" "${target}"
}

require_single_key() {
  local file="$1" key="$2" count
  count="$(awk -F= -v key="${key}" '$1==key{count++} END{print count+0}' "${file}")"
  [[ "${count}" == "1" ]] || die "${file} must contain exactly one ${key} entry"
}

validate_image_registry() {
  local registry="$1" owner
  [[ -n "${registry}" ]] || die "INVALID_IMAGE_REGISTRY: value is empty"
  [[ "${registry}" =~ ^ghcr\.io/[A-Za-z0-9][A-Za-z0-9-]*$ ]] || \
    die "INVALID_IMAGE_REGISTRY: '${registry}' must match ghcr.io/<owner>"
  owner="${registry#ghcr.io/}"
  [[ "${owner}" != "owner" ]] || die "INVALID_IMAGE_REGISTRY: placeholder registry '${registry}' is not allowed"
}

update_release() { update_file_env "${pending_release}" "$1" "$2"; }
update_env() { update_file_env "${pending_env}" "$1" "$2"; }

release_registry="$(awk -F= '$1=="IMAGE_REGISTRY"{print $2}' "${pending_release}" | tail -n 1)"
AUTHORITATIVE_IMAGE_REGISTRY="${AUTHORITATIVE_IMAGE_REGISTRY:-${release_registry}}"
validate_image_registry "${AUTHORITATIVE_IMAGE_REGISTRY}"

update_release IMAGE_REGISTRY "${AUTHORITATIVE_IMAGE_REGISTRY}"
update_release IMAGE_TAG "${COMMIT_SHA}"
update_release GATEWAY_IMAGE_TAG "${COMMIT_SHA}"
update_release KEYCLOAK_IMAGE_TAG "${COMMIT_SHA}"
update_release LEARNING_IMAGE_TAG "${COMMIT_SHA}"
update_release KEYCLOAK_SMTP_HOST smtp.resend.com
update_release KEYCLOAK_SMTP_PORT 587
update_release KEYCLOAK_SMTP_USERNAME resend
update_release KEYCLOAK_SMTP_FROM no-reply@tinkona.com
update_release KEYCLOAK_SMTP_FROM_DISPLAY_NAME 'Svea Study'
update_release KEYCLOAK_SMTP_REPLY_TO support@tinkona.com
update_release KEYCLOAK_SMTP_REPLY_TO_DISPLAY_NAME 'Svea Study Support'
update_release KEYCLOAK_SMTP_STARTTLS true
update_release KEYCLOAK_SMTP_SSL false
update_release KEYCLOAK_SMTP_CONFIGURED true
update_release KEYCLOAK_EMAIL_DMARC_STATUS present

update_env KEYCLOAK_GOOGLE_CLIENT_ID "${google_client_id}"
update_env KEYCLOAK_GOOGLE_CLIENT_SECRET "${google_client_secret}"
update_env KEYCLOAK_GOOGLE_ENABLED "${google_enabled}"

require_single_key "${pending_release}" IMAGE_TAG
require_single_key "${pending_release}" GATEWAY_IMAGE_TAG
require_single_key "${pending_release}" LEARNING_IMAGE_TAG
require_single_key "${pending_release}" KEYCLOAK_IMAGE_TAG
require_single_key "${pending_release}" IMAGE_REGISTRY

candidate_compose=(docker compose --env-file "${pending_env}" --env-file "${pending_release}" -f "${compose_file}")
current_compose=(docker compose --env-file "${env_file}" --env-file "${release_env}" -f "${compose_file}")

"${candidate_compose[@]}" config >/dev/null
candidate_images_file="${PLATFORM_STATE_DIR}/candidate-auth-images-${COMMIT_SHA}.txt"
candidate_services_file="${PLATFORM_STATE_DIR}/candidate-auth-services-${COMMIT_SHA}.txt"
printf '%s\n' "${AUTH_SERVICES[@]}" >"${candidate_services_file}"
"${candidate_compose[@]}" config --images "${AUTH_SERVICES[@]}" |
  awk 'NF>0' |
  grep -E '/citizenship-(api-gateway|learning-service|keycloak):' |
  sort -u >"${candidate_images_file}"
[[ -s "${candidate_images_file}" ]] || die "Candidate image list is empty"
[[ "$(awk 'END{print NR+0}' "${candidate_images_file}")" == "3" ]] || \
  die "Auth preflight must resolve exactly three unique auth images"

while IFS= read -r image; do
  docker manifest inspect "${image}" >/dev/null 2>&1 || die "Required immutable image missing: ${image}"
done <"${candidate_images_file}"

pulled_images_file="${PLATFORM_STATE_DIR}/candidate-auth-pulled-images-${COMMIT_SHA}.txt"
: >"${pulled_images_file}"
while IFS= read -r image; do
  docker pull "${image}" >/dev/null
  printf '%s\n' "${image}" >>"${pulled_images_file}"
done <"${candidate_images_file}"
sort -u "${pulled_images_file}" -o "${pulled_images_file}"
cmp -s "${candidate_images_file}" "${pulled_images_file}" || \
  die "Rendered candidate images differ from pulled images"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
env_backup="${BACKUP_DIR}/.env.${timestamp}.bak"
release_backup="${BACKUP_DIR}/.release.env.${timestamp}.bak"
cp "${env_file}" "${env_backup}"
cp "${release_env}" "${release_backup}"
chmod 600 "${env_backup}" "${release_backup}"

previous_gateway_image="$("${current_compose[@]}" ps -q api-gateway | xargs -r docker inspect -f '{{.Config.Image}}' 2>/dev/null || true)"
previous_learning_image="$("${current_compose[@]}" ps -q learning-service | xargs -r docker inspect -f '{{.Config.Image}}' 2>/dev/null || true)"
previous_keycloak_image="$("${current_compose[@]}" ps -q keycloak | xargs -r docker inspect -f '{{.Config.Image}}' 2>/dev/null || true)"

rollback_required=true
rollback() {
  if [[ "${rollback_required}" != true ]]; then
    return 0
  fi
  cp "${env_backup}" "${env_file}" >/dev/null 2>&1 || true
  cp "${release_backup}" "${release_env}" >/dev/null 2>&1 || true
  chmod 600 "${env_file}" "${release_env}" >/dev/null 2>&1 || true
  "${current_compose[@]}" up -d --no-deps api-gateway learning-service keycloak >/dev/null 2>&1 || true

  current_gateway_image="$("${current_compose[@]}" ps -q api-gateway | xargs -r docker inspect -f '{{.Config.Image}}' 2>/dev/null || true)"
  current_learning_image="$("${current_compose[@]}" ps -q learning-service | xargs -r docker inspect -f '{{.Config.Image}}' 2>/dev/null || true)"
  current_keycloak_image="$("${current_compose[@]}" ps -q keycloak | xargs -r docker inspect -f '{{.Config.Image}}' 2>/dev/null || true)"
  [[ -z "${previous_gateway_image}" || "${current_gateway_image}" == "${previous_gateway_image}" ]] || die "Rollback failed: api-gateway image mismatch"
  [[ -z "${previous_learning_image}" || "${current_learning_image}" == "${previous_learning_image}" ]] || die "Rollback failed: learning-service image mismatch"
  [[ -z "${previous_keycloak_image}" || "${current_keycloak_image}" == "${previous_keycloak_image}" ]] || die "Rollback failed: keycloak image mismatch"
}
trap rollback ERR

mv "${pending_env}" "${env_file}"
mv "${pending_release}" "${release_env}"
chmod 600 "${env_file}" "${release_env}"

candidate_active_compose=(docker compose --env-file "${env_file}" --env-file "${release_env}" -f "${compose_file}")
"${candidate_active_compose[@]}" up -d --no-deps --wait --wait-timeout 120 keycloak
KEYCLOAK_SECURITY_MODE=HTTPS_HOSTED ADMIN_PORTAL_URL="https://${PUBLIC_DOMAIN}" \
  KEYCLOAK_SMTP_HOST=smtp.resend.com KEYCLOAK_SMTP_PORT=587 KEYCLOAK_SMTP_USERNAME=resend \
  KEYCLOAK_SMTP_FROM=no-reply@tinkona.com KEYCLOAK_SMTP_FROM_DISPLAY_NAME='Svea Study' \
  KEYCLOAK_SMTP_REPLY_TO=support@tinkona.com KEYCLOAK_SMTP_REPLY_TO_DISPLAY_NAME='Svea Study Support' \
  KEYCLOAK_SMTP_STARTTLS=true KEYCLOAK_SMTP_SSL=false KEYCLOAK_EMAIL_DMARC_STATUS=present \
  "${HARDEN_KEYCLOAK_SCRIPT}"
"${candidate_active_compose[@]}" up -d --no-deps --wait --wait-timeout 120 learning-service
"${candidate_active_compose[@]}" up -d --no-deps --wait --wait-timeout 60 api-gateway
"${candidate_active_compose[@]}" exec -T --user 101:101 api-gateway nginx -t
API_DOMAIN="${PUBLIC_DOMAIN}" "${SMOKE_TEST_SCRIPT}" --internal

gateway_image="$("${candidate_active_compose[@]}" ps -q api-gateway | xargs -r docker inspect -f '{{.Config.Image}}')"
keycloak_image="$("${candidate_active_compose[@]}" ps -q keycloak | xargs -r docker inspect -f '{{.Config.Image}}')"
learning_image="$("${candidate_active_compose[@]}" ps -q learning-service | xargs -r docker inspect -f '{{.Config.Image}}')"
[[ "${gateway_image}" == */citizenship-api-gateway:"${COMMIT_SHA}" ]]
[[ "${keycloak_image}" == */citizenship-keycloak:"${COMMIT_SHA}" ]]
[[ "${learning_image}" == */citizenship-learning-service:"${COMMIT_SHA}" ]]

rollback_required=false
trap - ERR

cleanup_backups() {
  local pattern="$1"
  local backups=()
  while IFS= read -r entry; do
    backups+=("${entry}")
  done < <(find "${BACKUP_DIR}" -type f -name "${pattern}" -print | sort)
  local keep=20
  local count="${#backups[@]}"
  if (( count > keep )); then
    local remove_count=$((count - keep))
    local index
    for ((index=0; index<remove_count; index++)); do
      rm -f "${backups[index]}"
    done
  fi
}
cleanup_backups '.env.*.bak'
cleanup_backups '.release.env.*.bak'
rm -f "${pending_env}" "${pending_release}"
printf 'Hosted auth deployment to %s completed with immutable SHA %s.\n' "${PUBLIC_DOMAIN}" "${COMMIT_SHA}"
