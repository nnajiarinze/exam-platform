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

postgres_tool() {
  local tool="$1" candidate major
  if [[ -n "${POSTGRES_TOOL_DIR:-}" ]]; then
    candidate="${POSTGRES_TOOL_DIR}/${tool}"
  elif [[ -x "/opt/homebrew/opt/postgresql@18/bin/${tool}" ]]; then
    candidate="/opt/homebrew/opt/postgresql@18/bin/${tool}"
  else
    candidate="$(command -v "${tool}" || true)"
  fi
  [[ -x "${candidate}" ]] || die "PostgreSQL 18 ${tool} is required; set POSTGRES_TOOL_DIR to its bin directory"
  major="$("${candidate}" --version | sed -E 's/.* ([0-9]+)(\..*)?$/\1/')"
  [[ "${major}" =~ ^[0-9]+$ && "${major}" -ge 18 ]] || \
    die "${candidate} is PostgreSQL ${major:-unknown}; PostgreSQL 18 or newer is required"
  printf '%s\n' "${candidate}"
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
