#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

TARGET_TAG="${1:-}"
if [[ -z "${TARGET_TAG}" ]]; then
  require_file "${PLATFORM_STATE_DIR}/previous-image-tag"
  TARGET_TAG="$(<"${PLATFORM_STATE_DIR}/previous-image-tag")"
fi
require_sha "${TARGET_TAG}"

CURRENT_TAG=""
[[ -f "${PLATFORM_STATE_DIR}/current-image-tag" ]] && CURRENT_TAG="$(<"${PLATFORM_STATE_DIR}/current-image-tag")"
printf 'Rolling application containers back to %s. Database migrations are not reversed.\n' "${TARGET_TAG}"
"${SCRIPT_DIR}/deploy.sh" "${TARGET_TAG}"
if [[ -n "${CURRENT_TAG}" ]]; then
  printf '%s\n' "${CURRENT_TAG}" >"${PLATFORM_STATE_DIR}/previous-image-tag"
fi
