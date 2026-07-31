#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

ACTION="${1:-inspect}"
EXPECTED_HOST_SHA256="${2:-}"
[[ "${ACTION}" == inspect || "${ACTION}" == execute ]] || die "Action must be inspect or execute"
require_file "${PLATFORM_ENV_FILE}"
for command in docker python3 sha256sum; do require_command "${command}"; done
if ! command -v psql >/dev/null 2>&1 || [[ "$(psql --version 2>/dev/null | sed -E 's/.* ([0-9]+)(\\..*)?$/\\1/')" -lt 18 ]]; then
  POSTGRES_TOOL_DIR="${PLATFORM_STATE_DIR}/postgres18-client"
  install -d -m 700 "${POSTGRES_TOOL_DIR}"
  for postgres_command in psql pg_dump pg_restore; do
    wrapper="${POSTGRES_TOOL_DIR}/${postgres_command}"
    printf '%s\n' \
      '#!/usr/bin/env bash' \
      "exec docker run --rm --network host -e PGPASSWORD -v /tmp:/tmp postgres:18-alpine ${postgres_command} \"\$@\"" \
      >"${wrapper}"
    chmod 700 "${wrapper}"
  done
  export POSTGRES_TOOL_DIR
fi
PSQL="$(postgres_tool psql)"

for variable in BACKUP_DATABASE_HOST BACKUP_DATABASE_PORT \
  BACKUP_CONTENT_USERNAME BACKUP_CONTENT_PASSWORD \
  BACKUP_LEARNING_USERNAME BACKUP_LEARNING_PASSWORD \
  BACKUP_AI_USERNAME BACKUP_AI_PASSWORD; do
  printf -v "${variable}" '%s' "$(env_file_value "${variable}" "${PLATFORM_ENV_FILE}")"
  declare -gx "${variable}=${!variable}"
  require_var "${variable}"
done

HOST="${BACKUP_DATABASE_HOST,,}"
[[ "${HOST}" == *.eu-central-1.aws.neon.tech ]] ||
  die "Hosted database is not a verified Neon eu-central-1 endpoint"
[[ "${HOST}" != *us-east* && "${HOST}" != *useast* ]] ||
  die "Retired US-East target is forbidden"
HOST_SHA256="$(printf '%s' "${HOST}" | sha256sum | awk '{print $1}')"
if [[ "${ACTION}" == execute ]]; then
  [[ "${EXPECTED_HOST_SHA256}" =~ ^[a-f0-9]{64}$ ]] ||
    die "Execute requires the fingerprint emitted by a separate inspect run"
  [[ "${HOST_SHA256}" == "${EXPECTED_HOST_SHA256}" ]] ||
    die "Hosted target fingerprint changed after inspection"
fi

db_value() {
  local database="$1" prefix="${2^^}" query="$3" username_var password_var
  username_var="BACKUP_${prefix}_USERNAME"
  password_var="BACKUP_${prefix}_PASSWORD"
  PGPASSWORD="${!password_var}" "${PSQL}" -XAt -v ON_ERROR_STOP=1 \
    --host="${BACKUP_DATABASE_HOST}" --port="${BACKUP_DATABASE_PORT}" \
    --username="${!username_var}" --dbname="${database}" --command="${query}"
}

counts_json() {
  local database="$1" prefix="$2"
  db_value "${database}" "${prefix}" \
    "SELECT coalesce(json_object_agg(tablename,n),'{}')::text FROM (
       SELECT tablename,(xpath('/row/c/text()',query_to_xml(format('SELECT count(*) c FROM %I',tablename),false,true,'')))[1]::text::bigint n
       FROM pg_tables WHERE schemaname='public' AND tablename<>'flyway_schema_history' ORDER BY tablename
     ) x;"
}

printf '{"event":"hosted_target_inspected","region":"eu-central-1","hostSha256":"%s","counts":{"content":%s,"learning":%s,"ai":%s}}\n' \
  "${HOST_SHA256}" "$(counts_json content content)" "$(counts_json learning learning)" "$(counts_json ai ai)"
[[ "${ACTION}" == inspect ]] && exit 0

cd "${PLATFORM_REPOSITORY}"
"${SCRIPT_DIR}/backup.sh"
BACKUP_COMPLETED_AT="$(date -u +'%Y-%m-%dT%H:%M:%SZ')"

mkdir -p "${PLATFORM_STATE_DIR}"
REPORT="${PLATFORM_STATE_DIR}/sverige-i-fokus-reset-$(date -u +'%Y%m%dT%H%M%SZ').json"
umask 077
printf '{"corpus":"sverige-i-fokus-v1","targetHostSha256":"%s","backupCompletedAt":"%s","before":{"content":%s,"learning":%s,"ai":%s},' \
  "${HOST_SHA256}" "${BACKUP_COMPLETED_AT}" "$(counts_json content content)" "$(counts_json learning learning)" "$(counts_json ai ai)" >"${REPORT}"

writers_stopped=false
restart_writers() {
  if [[ "${writers_stopped}" == true ]]; then
    compose up -d content-service learning-service ai-service --wait --wait-timeout 240 || true
  fi
}
trap restart_writers EXIT
compose stop content-service learning-service ai-service
writers_stopped=true

reset_database() {
  local database="$1" prefix="$2" preserve="$3"
  db_value "${database}" "${prefix}" "DO \\\$\\\$
    DECLARE names text;
    BEGIN
      SELECT string_agg(format('%I.%I',schemaname,tablename),',') INTO names
      FROM pg_tables WHERE schemaname='public' AND tablename NOT IN (${preserve});
      IF names IS NOT NULL THEN EXECUTE 'TRUNCATE TABLE ' || names || ' CASCADE'; END IF;
    END \\\$\\\$;
    DO \\\$\\\$ BEGIN
      IF (SELECT count(*) FROM flyway_schema_history)=0 THEN RAISE EXCEPTION 'Flyway history was cleared'; END IF;
    END \\\$\\\$;"
}

reset_database content content "'flyway_schema_history','audit_event'"
reset_database learning learning "'flyway_schema_history','learner_profile','learner_settings'"
reset_database ai ai "'flyway_schema_history','ai_audit_event','ai_quota_profile','ai_model_price_profile','ai_provider_circuit'"

IMPORT_SQL="$(mktemp)"
trap 'rm -f -- "${IMPORT_SQL}"; restart_writers' EXIT
python3 scripts/sverige_i_fokus_sql.py >"${IMPORT_SQL}"
PGPASSWORD="${BACKUP_CONTENT_PASSWORD}" "${PSQL}" -X -v ON_ERROR_STOP=1 \
  --host="${BACKUP_DATABASE_HOST}" --port="${BACKUP_DATABASE_PORT}" \
  --username="${BACKUP_CONTENT_USERNAME}" --dbname=content --file="${IMPORT_SQL}"
rm -f -- "${IMPORT_SQL}"

compose up -d content-service learning-service ai-service --wait --wait-timeout 240
writers_stopped=false
"${SCRIPT_DIR}/smoke-test.sh" --internal

printf '"after":{"content":%s,"learning":%s,"ai":%s},"identityReset":false,"oldUsEastTouched":false}\n' \
  "$(counts_json content content)" "$(counts_json learning learning)" "$(counts_json ai ai)" >>"${REPORT}"
chmod 600 "${REPORT}"
trap - EXIT
printf 'Hosted Sverige i fokus structural reset completed; report=%s\n' "${REPORT}"
