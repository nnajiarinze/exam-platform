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
for command in age age-keygen docker python3 sha256sum; do require_command "${command}"; done
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
PG_DUMP="$(postgres_tool pg_dump)"
PG_RESTORE="$(postgres_tool pg_restore)"

for prefix in CONTENT LEARNING AI; do
  url="$(env_file_value "${prefix}_MIGRATION_DATABASE_URL" "${PLATFORM_ENV_FILE}")"
  [[ -n "${url}" ]] || url="$(env_file_value "${prefix}_DATABASE_URL" "${PLATFORM_ENV_FILE}")"
  username="$(env_file_value "${prefix}_DATABASE_USERNAME" "${PLATFORM_ENV_FILE}")"
  password="$(env_file_value "${prefix}_DATABASE_PASSWORD" "${PLATFORM_ENV_FILE}")"
  printf -v "${prefix}_CORPUS_URL" '%s' "${url#jdbc:}"
  printf -v "${prefix}_CORPUS_USERNAME" '%s' "${username}"
  printf -v "${prefix}_CORPUS_PASSWORD" '%s' "${password}"
  export "${prefix}_CORPUS_URL" "${prefix}_CORPUS_USERNAME" "${prefix}_CORPUS_PASSWORD"
  require_var "${prefix}_CORPUS_URL"
  require_var "${prefix}_CORPUS_USERNAME"
  require_var "${prefix}_CORPUS_PASSWORD"
done

hosts=()
for prefix in CONTENT LEARNING AI; do
  url_var="${prefix}_CORPUS_URL"
  read -r host database < <(python3 -c 'import sys,urllib.parse; u=urllib.parse.urlparse(sys.argv[1]); print(u.hostname or "",u.path.lstrip("/"))' "${!url_var}")
  host="${host,,}"
  [[ "${database}" == "${prefix,,}" ]] || die "${prefix} URL targets unexpected database ${database}"
  [[ "${host}" == *.eu-central-1.aws.neon.tech ]] ||
    die "${prefix} database is not a verified Neon eu-central-1 endpoint"
  [[ "${host}" != *us-east* && "${host}" != *useast* ]] ||
    die "Retired US-East target is forbidden"
  hosts+=("${host}")
done
[[ "${hosts[0]}" == "${hosts[1]}" && "${hosts[0]}" == "${hosts[2]}" ]] ||
  die "Content, Learning, and AI do not resolve to the same verified direct endpoint"
HOST_SHA256="$(printf '%s' "${hosts[0]}" | sha256sum | awk '{print $1}')"
if [[ "${ACTION}" == execute ]]; then
  [[ "${EXPECTED_HOST_SHA256}" =~ ^[a-f0-9]{64}$ ]] ||
    die "Execute requires the fingerprint emitted by a separate inspect run"
  [[ "${HOST_SHA256}" == "${EXPECTED_HOST_SHA256}" ]] ||
    die "Hosted target fingerprint changed after inspection"
fi

db_value() {
  local database="$1" prefix="${2^^}" query="$3" username_var password_var url_var
  username_var="${prefix}_CORPUS_USERNAME"
  password_var="${prefix}_CORPUS_PASSWORD"
  url_var="${prefix}_CORPUS_URL"
  PGPASSWORD="${!password_var}" "${PSQL}" -XAt -v ON_ERROR_STOP=1 \
    --username="${!username_var}" --dbname="${!url_var}" --command="${query}"
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
BACKUP_ROOT="${PLATFORM_ROOT}/backups/sverige-i-fokus-$(date -u +'%Y%m%dT%H%M%SZ')"
install -d -m 700 "${BACKUP_ROOT}"
AGE_IDENTITY="${PLATFORM_ROOT}/backups/sverige-i-fokus-age-identity.txt"
if [[ ! -f "${AGE_IDENTITY}" ]]; then
  age-keygen -o "${AGE_IDENTITY}"
  chmod 600 "${AGE_IDENTITY}"
fi
AGE_RECIPIENT="$(age-keygen -y "${AGE_IDENTITY}")"
printf 'database\tarchive\tbytes\tsha256\tvalidated\n' >"${BACKUP_ROOT}/manifest.tsv"
for prefix in CONTENT LEARNING AI; do
  database="${prefix,,}"
  username_var="${prefix}_CORPUS_USERNAME"
  password_var="${prefix}_CORPUS_PASSWORD"
  url_var="${prefix}_CORPUS_URL"
  clear_archive="${BACKUP_ROOT}/${database}.dump"
  encrypted_archive="${clear_archive}.age"
  PGPASSWORD="${!password_var}" "${PG_DUMP}" --format=custom --compress=9 \
    --no-owner --no-acl --username="${!username_var}" --dbname="${!url_var}" --file="${clear_archive}"
  "${PG_RESTORE}" --list "${clear_archive}" >/dev/null
  age -r "${AGE_RECIPIENT}" -o "${encrypted_archive}" "${clear_archive}"
  rm -f -- "${clear_archive}"
  chmod 600 "${encrypted_archive}"
  printf '%s\t%s\t%s\t%s\ttrue\n' "${database}" "${encrypted_archive}" \
    "$(wc -c <"${encrypted_archive}" | tr -d '[:space:]')" \
    "$(sha256sum "${encrypted_archive}" | awk '{print $1}')" >>"${BACKUP_ROOT}/manifest.tsv"
done
chmod 600 "${BACKUP_ROOT}/manifest.tsv"
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
PGPASSWORD="${CONTENT_CORPUS_PASSWORD}" "${PSQL}" -X -v ON_ERROR_STOP=1 \
  --username="${CONTENT_CORPUS_USERNAME}" --dbname="${CONTENT_CORPUS_URL}" --file="${IMPORT_SQL}"
rm -f -- "${IMPORT_SQL}"

compose up -d content-service learning-service ai-service --wait --wait-timeout 240
writers_stopped=false
"${SCRIPT_DIR}/smoke-test.sh" --internal

printf '"after":{"content":%s,"learning":%s,"ai":%s},"identityReset":false,"oldUsEastTouched":false}\n' \
  "$(counts_json content content)" "$(counts_json learning learning)" "$(counts_json ai ai)" >>"${REPORT}"
chmod 600 "${REPORT}"
trap - EXIT
printf 'Hosted Sverige i fokus structural reset completed; report=%s\n' "${REPORT}"
