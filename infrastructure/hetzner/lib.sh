#!/usr/bin/env bash
set -Eeuo pipefail

die() {
  printf 'ERROR: %s\n' "$*" >&2
  exit 1
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || die "Required command is unavailable: $1"
}

require_file() {
  [[ -f "$1" ]] || die "Required file does not exist: $1"
}

require_var() {
  [[ -n "${!1:-}" ]] || die "Required environment variable is not set: $1"
}

require_sha() {
  [[ "$1" =~ ^[0-9a-f]{40}$ ]] || die "Image tag must be a full lowercase Git commit SHA"
}

env_file_value() {
  local key="$1" file="$2"
  sed -n "s/^${key}=//p" "${file}" | tail -n 1
}

compose() {
  docker compose \
    --env-file "${PLATFORM_ENV_FILE}" \
    --env-file "${PLATFORM_RELEASE_ENV_FILE}" \
    -f "${PLATFORM_COMPOSE_FILE}" "$@"
}

load_platform_paths() {
  PLATFORM_ROOT="${PLATFORM_ROOT:-/opt/citizenship-platform}"
  PLATFORM_REPOSITORY="${PLATFORM_REPOSITORY:-${PLATFORM_ROOT}/repo}"
  PLATFORM_ENV_FILE="${PLATFORM_ENV_FILE:-${PLATFORM_ROOT}/.env}"
  PLATFORM_RELEASE_ENV_FILE="${PLATFORM_RELEASE_ENV_FILE:-${PLATFORM_ROOT}/.release.env}"
  PLATFORM_COMPOSE_FILE="${PLATFORM_COMPOSE_FILE:-${PLATFORM_REPOSITORY}/docker-compose.hosted.yml}"
  PLATFORM_STATE_DIR="${PLATFORM_STATE_DIR:-${PLATFORM_ROOT}/state}"
}
