#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

NEW_TAG="${1:-}"
require_sha "${NEW_TAG}"
require_command docker
require_file "${PLATFORM_ENV_FILE}"
require_file "${PLATFORM_COMPOSE_FILE}"
[[ "$(stat -c '%a' "${PLATFORM_ENV_FILE}")" == "600" ]] || die "${PLATFORM_ENV_FILE} must have mode 600"

mkdir -p "${PLATFORM_STATE_DIR}"
PREVIOUS_TAG=""
if [[ -f "${PLATFORM_RELEASE_ENV_FILE}" ]]; then
  PREVIOUS_TAG="$(sed -n 's/^IMAGE_TAG=//p' "${PLATFORM_RELEASE_ENV_FILE}" | tail -n 1)"
fi

if [[ -n "${GHCR_TOKEN_FILE:-}" ]]; then
  require_file "${GHCR_TOKEN_FILE}"
  require_var GHCR_USERNAME
  docker login ghcr.io --username "${GHCR_USERNAME}" --password-stdin <"${GHCR_TOKEN_FILE}" >/dev/null
fi

umask 077
printf 'IMAGE_TAG=%s\n' "${NEW_TAG}" >"${PLATFORM_RELEASE_ENV_FILE}.new"
if [[ -n "${IMAGE_REGISTRY:-}" ]]; then
  printf 'IMAGE_REGISTRY=%s\n' "${IMAGE_REGISTRY}" >>"${PLATFORM_RELEASE_ENV_FILE}.new"
fi
mv "${PLATFORM_RELEASE_ENV_FILE}.new" "${PLATFORM_RELEASE_ENV_FILE}"

compose config --quiet
compose config --images >"${PLATFORM_STATE_DIR}/requested-images-${NEW_TAG}.txt"
compose images --format json >"${PLATFORM_STATE_DIR}/images-before-${NEW_TAG}.json" 2>/dev/null || true
compose pull

if ! compose up -d --remove-orphans --wait --wait-timeout 240; then
  printf 'Deployment health validation failed for %s.\n' "${NEW_TAG}" >&2
  if [[ -n "${PREVIOUS_TAG}" ]]; then
    printf 'IMAGE_TAG=%s\n' "${PREVIOUS_TAG}" >"${PLATFORM_RELEASE_ENV_FILE}"
    compose up -d --remove-orphans --wait --wait-timeout 240 || true
    printf 'Rollback to %s was attempted; inspect all services.\n' "${PREVIOUS_TAG}" >&2
  fi
  exit 1
fi

API_DOMAIN="$(env_file_value API_DOMAIN "${PLATFORM_ENV_FILE}")"
require_var API_DOMAIN
API_DOMAIN="${API_DOMAIN}" "${SCRIPT_DIR}/smoke-test.sh" --internal
printf '%s\n' "${NEW_TAG}" >"${PLATFORM_STATE_DIR}/current-image-tag"
if [[ -n "${PREVIOUS_TAG}" && "${PREVIOUS_TAG}" != "${NEW_TAG}" ]]; then
  printf '%s\n' "${PREVIOUS_TAG}" >"${PLATFORM_STATE_DIR}/previous-image-tag"
fi
date -u +'%Y-%m-%dT%H:%M:%SZ' >>"${PLATFORM_STATE_DIR}/deployment-audit.log"
printf 'Deployed immutable image tag %s successfully.\n' "${NEW_TAG}"
