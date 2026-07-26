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

for command in age aws cmp jq sha256sum; do require_command "${command}"; done
PG_DUMP="$(postgres_tool pg_dump)"
PG_RESTORE="$(postgres_tool pg_restore)"
PSQL="$(postgres_tool psql)"
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
MANIFEST="${WORK_DIR}/backup-manifest.tsv"
METADATA="${WORK_DIR}/backup-metadata.json"
printf 'database\tobject\tbytes\tsha256\tflyway_version\n' >"${MANIFEST}"
if [[ -z "${IMAGE_TAG:-}" && -f "${PLATFORM_RELEASE_ENV_FILE:-/opt/citizenship-platform/.release.env}" ]]; then
  IMAGE_TAG="$(env_file_value IMAGE_TAG "${PLATFORM_RELEASE_ENV_FILE:-/opt/citizenship-platform/.release.env}")"
fi

dump_one() {
  local database="$1" prefix="$2" username password dump_file encrypted_file username_var password_var
  local flyway_version bytes checksum
  username_var="${prefix}_USERNAME"
  password_var="${prefix}_PASSWORD"
  username="${!username_var}"
  password="${!password_var}"
  dump_file="${WORK_DIR}/${database}-${TIMESTAMP}.dump"
  encrypted_file="${dump_file}.age"
  PGPASSWORD="${password}" "${PG_DUMP}" \
    --host="${BACKUP_DATABASE_HOST}" --port="${BACKUP_DATABASE_PORT}" \
    --username="${username}" --dbname="${database}" \
    --format=custom --compress=9 --no-password --file="${dump_file}"
  "${PG_RESTORE}" --list "${dump_file}" >/dev/null
  age --recipient "${BACKUP_AGE_RECIPIENT}" --output "${encrypted_file}" "${dump_file}"
  if [[ "${database}" == identity ]]; then
    flyway_version="keycloak-managed"
  else
    flyway_version="$(PGPASSWORD="${password}" "${PSQL}" \
      --host="${BACKUP_DATABASE_HOST}" --port="${BACKUP_DATABASE_PORT}" \
      --username="${username}" --dbname="${database}" --tuples-only --no-align \
      --command="SELECT COALESCE(MAX(version), 'none') FROM flyway_schema_history WHERE success" |
      tr -d '[:space:]')"
  fi
  bytes="$(wc -c <"${encrypted_file}" | tr -d '[:space:]')"
  checksum="$(sha256sum "${encrypted_file}" | awk '{print $1}')"
  printf '%s\t%s\t%s\t%s\t%s\n' "${database}" "$(basename "${encrypted_file}")" \
    "${bytes}" "${checksum}" "${flyway_version}" >>"${MANIFEST}"
  aws "${aws_args[@]}" s3 cp \
    "${encrypted_file}" "${BACKUP_S3_URI%/}/${TIMESTAMP}/$(basename "${encrypted_file}")" \
    --only-show-errors
}

dump_one content BACKUP_CONTENT
dump_one learning BACKUP_LEARNING
dump_one ai BACKUP_AI
dump_one identity BACKUP_IDENTITY

jq -n \
  --arg timestamp "${TIMESTAMP}" \
  --arg imageTag "${IMAGE_TAG:-unknown}" \
  --arg format "PostgreSQL custom archive encrypted with age" \
  '{backupTimestamp:$timestamp,applicationImageSha:$imageTag,archiveFormat:$format,
    databases:["content","learning","ai","identity"]}' >"${METADATA}"
aws "${aws_args[@]}" s3 cp "${MANIFEST}" \
  "${BACKUP_S3_URI%/}/${TIMESTAMP}/backup-manifest.tsv" --only-show-errors
aws "${aws_args[@]}" s3 cp "${METADATA}" \
  "${BACKUP_S3_URI%/}/${TIMESTAMP}/backup-metadata.json" --only-show-errors

VERIFY_DIR="${WORK_DIR}/verify"
mkdir -p "${VERIFY_DIR}"
aws "${aws_args[@]}" s3 cp \
  "${BACKUP_S3_URI%/}/${TIMESTAMP}/backup-manifest.tsv" "${VERIFY_DIR}/backup-manifest.tsv" \
  --only-show-errors
aws "${aws_args[@]}" s3 cp \
  "${BACKUP_S3_URI%/}/${TIMESTAMP}/backup-metadata.json" "${VERIFY_DIR}/backup-metadata.json" \
  --only-show-errors
cmp "${MANIFEST}" "${VERIFY_DIR}/backup-manifest.tsv"
cmp "${METADATA}" "${VERIFY_DIR}/backup-metadata.json"
while IFS=$'\t' read -r database object bytes checksum flyway_version; do
  [[ "${database}" == database ]] && continue
  remote_size="$(aws "${aws_args[@]}" s3api head-object \
    --bucket "$(sed -E 's#s3://([^/]+).*#\1#' <<<"${BACKUP_S3_URI}")" \
    --key "$(sed -E 's#s3://[^/]+/?##' <<<"${BACKUP_S3_URI%/}")/${TIMESTAMP}/${object}" \
    --query ContentLength --output text)"
  [[ "${remote_size}" == "${bytes}" ]] || die "Uploaded size mismatch for ${database}"
  aws "${aws_args[@]}" s3 cp \
    "${BACKUP_S3_URI%/}/${TIMESTAMP}/${object}" "${VERIFY_DIR}/${object}" --only-show-errors
  [[ "$(sha256sum "${VERIFY_DIR}/${object}" | awk '{print $1}')" == "${checksum}" ]] ||
    die "Uploaded checksum mismatch for ${database}"
  head -n 1 "${VERIFY_DIR}/${object}" | grep -q '^age-encryption.org/v1$' ||
    die "Uploaded archive for ${database} is not age-encrypted"
done <"${MANIFEST}"
printf 'Encrypted offsite backups for all four databases completed at %s.\n' "${TIMESTAMP}"
