#!/usr/bin/env bash
set -Eeuo pipefail

REPO_ROOT="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
SCRIPT_UNDER_TEST="${REPO_ROOT}/infrastructure/hetzner/deploy-hosted-auth.sh"

fail() { printf 'FAIL: %s\n' "$*" >&2; exit 1; }
assert_eq() { [[ "$1" == "$2" ]] || fail "$3 (expected '$1' got '$2')"; }
assert_file_contains() { grep -Fq -- "$2" "$1" || fail "$3"; }
assert_file_not_contains() { ! grep -Fq -- "$2" "$1" || fail "$3"; }

setup_fake_env() {
  WORK_DIR="$(mktemp -d /tmp/deploy-auth-test.XXXXXX)"
  ROOT_DIR="${WORK_DIR}/root"
  REPO_DIR="${WORK_DIR}/repo"
  STATE_DIR="${ROOT_DIR}/state"
  BIN_DIR="${WORK_DIR}/bin"
  mkdir -p "${ROOT_DIR}" "${REPO_DIR}/infrastructure/hetzner" "${STATE_DIR}" "${BIN_DIR}"

  ENV_FILE="${ROOT_DIR}/.env"
  RELEASE_FILE="${ROOT_DIR}/.release.env"
  COMPOSE_FILE="${REPO_DIR}/docker-compose.hosted.yml"

  cat >"${ENV_FILE}" <<'EOF'
RESEND_API_KEY=protected-resend
KEYCLOAK_GOOGLE_ENABLED=false
KEYCLOAK_GOOGLE_CLIENT_ID=placeholder-id
KEYCLOAK_GOOGLE_CLIENT_SECRET=placeholder-secret
EOF
  chmod 600 "${ENV_FILE}"

  cat >"${RELEASE_FILE}" <<'EOF'
IMAGE_REGISTRY=ghcr.io/nnajiarinze
IMAGE_TAG=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb
EOF
  chmod 600 "${RELEASE_FILE}"

  cat >"${COMPOSE_FILE}" <<'EOF'
services:
  api-gateway:
    image: ${IMAGE_REGISTRY}/citizenship-api-gateway:${GATEWAY_IMAGE_TAG:-${IMAGE_TAG}}
  learning-service:
    image: ${IMAGE_REGISTRY}/citizenship-learning-service:${LEARNING_IMAGE_TAG:-${IMAGE_TAG}}
  keycloak:
    image: ${IMAGE_REGISTRY}/citizenship-keycloak:${KEYCLOAK_IMAGE_TAG:-${IMAGE_TAG}}
EOF

  cat >"${REPO_DIR}/infrastructure/hetzner/harden-keycloak.sh" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${FAKE_HARDEN_FAIL:-false}" == true ]] && exit 1
exit 0
EOF
  chmod +x "${REPO_DIR}/infrastructure/hetzner/harden-keycloak.sh"

  cat >"${REPO_DIR}/infrastructure/hetzner/smoke-test.sh" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${FAKE_SMOKE_FAIL:-false}" == true ]] && exit 1
exit 0
EOF
  chmod +x "${REPO_DIR}/infrastructure/hetzner/smoke-test.sh"

  cat >"${BIN_DIR}/docker" <<'EOF'
#!/usr/bin/env bash
set -Eeuo pipefail
state="${FAKE_DOCKER_STATE_DIR:?}"
mkdir -p "${state}"

get_var() {
  local file="$1" key="$2"
  awk -F= -v key="${key}" '$1==key{print $2}' "${file}" | tail -n 1
}

service_image() {
  local service="$1" env_file="$2" release_file="$3"
  local registry image_tag specific
  registry="$(get_var "${release_file}" IMAGE_REGISTRY)"
  image_tag="$(get_var "${release_file}" IMAGE_TAG)"
  case "${service}" in
    api-gateway)
      specific="$(get_var "${release_file}" GATEWAY_IMAGE_TAG)"
      echo "${registry}/citizenship-api-gateway:${specific:-${image_tag}}"
      ;;
    learning-service)
      specific="$(get_var "${release_file}" LEARNING_IMAGE_TAG)"
      echo "${registry}/citizenship-learning-service:${specific:-${image_tag}}"
      ;;
    keycloak)
      specific="$(get_var "${release_file}" KEYCLOAK_IMAGE_TAG)"
      echo "${registry}/citizenship-keycloak:${specific:-${image_tag}}"
      ;;
    *)
      exit 1
      ;;
  esac
}

if [[ "$1" == "manifest" && "$2" == "inspect" ]]; then
  image="$3"
  grep -Fxq "${image}" "${state}/available-images.txt"
  echo '{}'
  exit 0
fi

if [[ "$1" == "pull" ]]; then
  image="$2"
  grep -Fxq "${image}" "${state}/available-images.txt"
  printf '%s\n' "${image}" >>"${state}/pulls.log"
  exit 0
fi

if [[ "$1" == "inspect" ]]; then
  cid="$4"
  service="${cid#cid_}"
  cat "${state}/running-${service}.txt"
  exit 0
fi

if [[ "$1" == "compose" ]]; then
  shift
  env_file=""
  release_file=""
  while [[ $# -gt 0 ]]; do
    case "$1" in
      --env-file)
        if [[ -z "${env_file}" ]]; then env_file="$2"; else release_file="$2"; fi
        shift 2
        ;;
      -f)
        shift 2
        ;;
      *)
        break
        ;;
    esac
  done
  cmd="$1"
  shift
  case "${cmd}" in
    config)
      if [[ "${1:-}" == "--images" ]]; then
        shift
        while [[ $# -gt 0 ]]; do
          service="$1"
          service_image "${service}" "${env_file}" "${release_file}"
          shift
        done
        exit 0
      fi
      [[ "${FAKE_CONFIG_FAIL:-false}" != true ]]
      exit 0
      ;;
    ps)
      [[ "${1:-}" == "-q" ]]
      service="$2"
      if [[ -f "${state}/running-${service}.txt" ]]; then
        printf 'cid_%s\n' "${service}"
      fi
      exit 0
      ;;
    up)
      services=()
      for token in "$@"; do
        case "${token}" in
          api-gateway|learning-service|keycloak)
            services+=("${token}")
            ;;
        esac
      done
      for service in "${services[@]}"; do
        service_image "${service}" "${env_file}" "${release_file}" >"${state}/running-${service}.txt"
      done
      [[ "${FAKE_UP_FAIL:-false}" != true ]]
      exit 0
      ;;
    exec)
      [[ "${FAKE_EXEC_FAIL:-false}" != true ]]
      exit 0
      ;;
    *)
      exit 0
      ;;
  esac
fi

echo "Unsupported fake docker command: $*" >&2
exit 1
EOF
  chmod +x "${BIN_DIR}/docker"

  export PATH="${BIN_DIR}:$PATH"
  export FAKE_DOCKER_STATE_DIR="${STATE_DIR}/fake-docker"
  mkdir -p "${FAKE_DOCKER_STATE_DIR}"
}

teardown_fake_env() {
  rm -rf "${WORK_DIR}"
}

run_script() {
  local commit_sha="$1" payload="$2"
  printf '%s' "${payload}" | \
    PLATFORM_ROOT="${ROOT_DIR}" \
    PLATFORM_REPOSITORY="${REPO_DIR}" \
    PLATFORM_ENV_FILE="${ENV_FILE}" \
    PLATFORM_RELEASE_ENV_FILE="${RELEASE_FILE}" \
    PLATFORM_COMPOSE_FILE="${COMPOSE_FILE}" \
    PLATFORM_STATE_DIR="${STATE_DIR}" \
    HARDEN_KEYCLOAK_SCRIPT="${REPO_DIR}/infrastructure/hetzner/harden-keycloak.sh" \
    SMOKE_TEST_SCRIPT="${REPO_DIR}/infrastructure/hetzner/smoke-test.sh" \
    bash "${SCRIPT_UNDER_TEST}" "${commit_sha}" api.tinkona.com
}

seed_running_images_old_sha() {
  local sha="bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
  printf 'ghcr.io/nnajiarinze/citizenship-api-gateway:%s\n' "${sha}" >"${FAKE_DOCKER_STATE_DIR}/running-api-gateway.txt"
  printf 'ghcr.io/nnajiarinze/citizenship-learning-service:%s\n' "${sha}" >"${FAKE_DOCKER_STATE_DIR}/running-learning-service.txt"
  printf 'ghcr.io/nnajiarinze/citizenship-keycloak:%s\n' "${sha}" >"${FAKE_DOCKER_STATE_DIR}/running-keycloak.txt"
}

payload_for() {
  local secret="$1"
  jq -cn --arg cid 'client-id-value' --arg sec "${secret}" --arg enabled true \
    '{keycloak_google_client_id:$cid,keycloak_google_client_secret:$sec,keycloak_google_enabled:$enabled}'
}

run_test_candidate_images_exist_old_release_points_old_sha() {
  setup_fake_env
  local commit_sha='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  cat >"${FAKE_DOCKER_STATE_DIR}/available-images.txt" <<EOF
ghcr.io/nnajiarinze/citizenship-api-gateway:${commit_sha}
ghcr.io/nnajiarinze/citizenship-learning-service:${commit_sha}
ghcr.io/nnajiarinze/citizenship-keycloak:${commit_sha}
EOF
  seed_running_images_old_sha
  run_script "${commit_sha}" "$(payload_for 'safe-secret')" >/dev/null
  assert_file_contains "${RELEASE_FILE}" "IMAGE_TAG=${commit_sha}" "release IMAGE_TAG must move to candidate SHA"
  assert_file_contains "${FAKE_DOCKER_STATE_DIR}/pulls.log" "${commit_sha}" "candidate SHA images must be pulled"
  teardown_fake_env
}

run_test_duplicate_image_tag_old_value_cannot_win() {
  setup_fake_env
  local commit_sha='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  cat >>"${RELEASE_FILE}" <<'EOF'
IMAGE_TAG=cccccccccccccccccccccccccccccccccccccccc
IMAGE_TAG=dddddddddddddddddddddddddddddddddddddddd
EOF
  cat >"${FAKE_DOCKER_STATE_DIR}/available-images.txt" <<EOF
ghcr.io/nnajiarinze/citizenship-api-gateway:${commit_sha}
ghcr.io/nnajiarinze/citizenship-learning-service:${commit_sha}
ghcr.io/nnajiarinze/citizenship-keycloak:${commit_sha}
EOF
  seed_running_images_old_sha
  run_script "${commit_sha}" "$(payload_for 'safe-secret')" >/dev/null
  image_tag_count="$(awk -F= '$1=="IMAGE_TAG"{count++} END{print count+0}' "${RELEASE_FILE}")"
  assert_eq "1" "${image_tag_count}" "release file must be normalized to one IMAGE_TAG"
  assert_file_contains "${RELEASE_FILE}" "IMAGE_TAG=${commit_sha}" "normalized IMAGE_TAG must use candidate SHA"
  assert_file_not_contains "${RELEASE_FILE}" "IMAGE_TAG=cccccccccccccccccccccccccccccccccccccccc" "stale IMAGE_TAG must be removed"
  assert_file_not_contains "${RELEASE_FILE}" "IMAGE_TAG=dddddddddddddddddddddddddddddddddddddddd" "duplicate IMAGE_TAG must be removed"
  teardown_fake_env
}

run_test_missing_candidate_image_zero_mutation() {
  setup_fake_env
  local commit_sha='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  local env_before release_before
  env_before="$(cat "${ENV_FILE}")"
  release_before="$(cat "${RELEASE_FILE}")"
  cat >"${FAKE_DOCKER_STATE_DIR}/available-images.txt" <<EOF
ghcr.io/nnajiarinze/citizenship-api-gateway:${commit_sha}
ghcr.io/nnajiarinze/citizenship-learning-service:${commit_sha}
EOF
  seed_running_images_old_sha
  if run_script "${commit_sha}" "$(payload_for 'safe-secret')" >/dev/null 2>&1; then
    fail "script should fail when candidate image is missing"
  fi
  assert_eq "${env_before}" "$(cat "${ENV_FILE}")" "env must not mutate when preflight fails"
  assert_eq "${release_before}" "$(cat "${RELEASE_FILE}")" "release must not mutate when preflight fails"
  [[ ! -d "${STATE_DIR}/backups/auth-deploy" || -z "$(ls -A "${STATE_DIR}/backups/auth-deploy" 2>/dev/null)" ]] || fail "no backups should be created on preflight failure"
  teardown_fake_env
}

run_test_secret_metacharacters_safe_transfer() {
  setup_fake_env
  local commit_sha='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  cat >"${FAKE_DOCKER_STATE_DIR}/available-images.txt" <<EOF
ghcr.io/nnajiarinze/citizenship-api-gateway:${commit_sha}
ghcr.io/nnajiarinze/citizenship-learning-service:${commit_sha}
ghcr.io/nnajiarinze/citizenship-keycloak:${commit_sha}
EOF
  seed_running_images_old_sha
  secret='x$y"z'
  secret+="'"
  secret+=";()[]{}<>!"
  run_script "${commit_sha}" "$(payload_for "${secret}")" >/dev/null
  assert_file_contains "${ENV_FILE}" "KEYCLOAK_GOOGLE_CLIENT_SECRET=${secret}" "secret with shell metacharacters must persist exactly"
  teardown_fake_env
}

run_test_failure_after_replacement_restores_files_and_images() {
  setup_fake_env
  local commit_sha='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  cat >"${FAKE_DOCKER_STATE_DIR}/available-images.txt" <<EOF
ghcr.io/nnajiarinze/citizenship-api-gateway:${commit_sha}
ghcr.io/nnajiarinze/citizenship-learning-service:${commit_sha}
ghcr.io/nnajiarinze/citizenship-keycloak:${commit_sha}
EOF
  seed_running_images_old_sha
  local env_before release_before
  env_before="$(cat "${ENV_FILE}")"
  release_before="$(cat "${RELEASE_FILE}")"
  export FAKE_HARDEN_FAIL=true
  if run_script "${commit_sha}" "$(payload_for 'safe-secret')" >/dev/null 2>&1; then
    fail "script should fail when harden-keycloak fails"
  fi
  unset FAKE_HARDEN_FAIL
  assert_eq "${env_before}" "$(cat "${ENV_FILE}")" "env must rollback after post-replacement failure"
  assert_eq "${release_before}" "$(cat "${RELEASE_FILE}")" "release must rollback after post-replacement failure"
  assert_eq "ghcr.io/nnajiarinze/citizenship-api-gateway:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" "$(cat "${FAKE_DOCKER_STATE_DIR}/running-api-gateway.txt")" "gateway image must rollback"
  assert_eq "ghcr.io/nnajiarinze/citizenship-learning-service:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" "$(cat "${FAKE_DOCKER_STATE_DIR}/running-learning-service.txt")" "learning image must rollback"
  assert_eq "ghcr.io/nnajiarinze/citizenship-keycloak:bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb" "$(cat "${FAKE_DOCKER_STATE_DIR}/running-keycloak.txt")" "keycloak image must rollback"
  teardown_fake_env
}

run_test_success_retains_backups_and_idempotent_second_run() {
  setup_fake_env
  local commit_sha='aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa'
  cat >"${FAKE_DOCKER_STATE_DIR}/available-images.txt" <<EOF
ghcr.io/nnajiarinze/citizenship-api-gateway:${commit_sha}
ghcr.io/nnajiarinze/citizenship-learning-service:${commit_sha}
ghcr.io/nnajiarinze/citizenship-keycloak:${commit_sha}
EOF
  seed_running_images_old_sha

  run_script "${commit_sha}" "$(payload_for 'safe-secret')" >/dev/null
  first_env="$(cat "${ENV_FILE}")"
  first_release="$(cat "${RELEASE_FILE}")"
  backups_after_first="$(find "${STATE_DIR}/backups/auth-deploy" -type f | wc -l | tr -d ' ')"
  [[ "${backups_after_first}" -ge 2 ]] || fail "successful deploy must retain protected backups"

  run_script "${commit_sha}" "$(payload_for 'safe-secret')" >/dev/null
  assert_eq "${first_env}" "$(cat "${ENV_FILE}")" "second run must keep env idempotent"
  assert_eq "${first_release}" "$(cat "${RELEASE_FILE}")" "second run must keep release idempotent"
  teardown_fake_env
}

run_test_candidate_images_exist_old_release_points_old_sha
run_test_duplicate_image_tag_old_value_cannot_win
run_test_missing_candidate_image_zero_mutation
run_test_secret_metacharacters_safe_transfer
run_test_failure_after_replacement_restores_files_and_images
run_test_success_retains_backups_and_idempotent_second_run
printf 'deploy-hosted-auth transactional tests passed.\n'
