#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

BACKUP_ENV_FILE="${BACKUP_ENV_FILE:-/opt/citizenship-platform/.env}"
if [[ -f "${BACKUP_ENV_FILE}" ]]; then
  for variable in \
    BACKUP_DATABASE_HOST BACKUP_DATABASE_PORT BACKUP_S3_URI BACKUP_S3_ENDPOINT_URL \
    BACKUP_S3_REGION BACKUP_S3_ACCESS_KEY_ID BACKUP_S3_SECRET_ACCESS_KEY \
    BACKUP_AGE_RECIPIENT \
    BACKUP_CONTENT_USERNAME BACKUP_CONTENT_PASSWORD \
    BACKUP_LEARNING_USERNAME BACKUP_LEARNING_PASSWORD \
    BACKUP_AI_USERNAME BACKUP_AI_PASSWORD \
    BACKUP_IDENTITY_USERNAME BACKUP_IDENTITY_PASSWORD; do
    if [[ -z "${!variable:-}" ]]; then
      printf -v "${variable}" '%s' "$(env_file_value "${variable}" "${BACKUP_ENV_FILE}")"
      declare -gx "${variable}=${!variable}"
    fi
  done
fi

for command in pg_dump pg_restore age aws; do require_command "${command}"; done
for variable in BACKUP_DATABASE_HOST BACKUP_DATABASE_PORT BACKUP_S3_URI BACKUP_AGE_RECIPIENT; do require_var "${variable}"; done
for database in CONTENT LEARNING AI IDENTITY; do
  require_var "BACKUP_${database}_USERNAME"
  require_var "BACKUP_${database}_PASSWORD"
done

aws_args=()
[[ -n "${BACKUP_S3_ENDPOINT_URL:-}" ]] && aws_args+=(--endpoint-url "${BACKUP_S3_ENDPOINT_URL}")
[[ -n "${BACKUP_S3_REGION:-}" ]] && aws_args+=(--region "${BACKUP_S3_REGION}")
if [[ -n "${BACKUP_S3_ACCESS_KEY_ID:-}" ]]; then
  export AWS_ACCESS_KEY_ID="${BACKUP_S3_ACCESS_KEY_ID}"
fi
if [[ -n "${BACKUP_S3_SECRET_ACCESS_KEY:-}" ]]; then
  export AWS_SECRET_ACCESS_KEY="${BACKUP_S3_SECRET_ACCESS_KEY}"
fi
if ! aws "${aws_args[@]}" s3api get-bucket-lifecycle-configuration \
  --bucket "$(sed -E 's#s3://([^/]+).*#\1#' <<<"${BACKUP_S3_URI}")" >/dev/null 2>&1; then
  die "Offsite bucket lifecycle is not configured; configure retention before backups"
fi

TIMESTAMP="$(date -u +'%Y%m%dT%H%M%SZ')"
WORK_DIR="$(mktemp -d)"
trap 'rm -rf -- "${WORK_DIR}"' EXIT

dump_one() {
  local database="$1" prefix="$2" username password dump_file encrypted_file username_var password_var
  username_var="${prefix}_USERNAME"
  password_var="${prefix}_PASSWORD"
  username="${!username_var}"
  password="${!password_var}"
  dump_file="${WORK_DIR}/${database}-${TIMESTAMP}.dump"
  encrypted_file="${dump_file}.age"
  PGPASSWORD="${password}" pg_dump \
    --host="${BACKUP_DATABASE_HOST}" --port="${BACKUP_DATABASE_PORT}" \
    --username="${username}" --dbname="${database}" \
    --format=custom --compress=9 --no-password --file="${dump_file}"
  pg_restore --list "${dump_file}" >/dev/null
  age --recipient "${BACKUP_AGE_RECIPIENT}" --output "${encrypted_file}" "${dump_file}"
  aws "${aws_args[@]}" s3 cp \
    "${encrypted_file}" "${BACKUP_S3_URI%/}/${TIMESTAMP}/$(basename "${encrypted_file}")" \
    --only-show-errors
}

dump_one content BACKUP_CONTENT
dump_one learning BACKUP_LEARNING
dump_one ai BACKUP_AI
dump_one identity BACKUP_IDENTITY
printf 'Encrypted offsite backups for all four databases completed at %s.\n' "${TIMESTAMP}"
