#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=infrastructure/hetzner/lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

MODE="${1:-}"
SNAPSHOT_ARCHIVE="${2:-}"
EXPECTED_SEMANTIC="${3:-}"
EXPECTED_HOST_SHA256="${4:-}"
EXPECTED_CONTENT_MIGRATION="${5:-20}"
EXPECTED_AI_MIGRATION="${6:-32}"
EXPECTED_RELEASE_ID="${7:-be07a3f5-a80c-42c8-bf1c-02541755f178}"
SOURCE_COMMIT="${8:-}"
[[ "${MODE}" == "DRY_RUN" || "${MODE}" == "IMPORT" ]] || die "Mode must be DRY_RUN or IMPORT"
[[ "${EXPECTED_SEMANTIC}" =~ ^[a-f0-9]{64}$ && "${EXPECTED_HOST_SHA256}" =~ ^[a-f0-9]{64}$ ]] || die "Expected checksums are required"
[[ "${EXPECTED_RELEASE_ID}" =~ ^[a-f0-9-]{36}$ ]] || die "Expected release ID is invalid"
[[ "${SOURCE_COMMIT}" =~ ^[a-f0-9]{40}$ ]] || die "Source commit is invalid"
require_file "${PLATFORM_ENV_FILE}"
require_file "${SNAPSHOT_ARCHIVE}"
for command in docker jq python3 sha256sum tar; do require_command "${command}"; done

if [[ "${MODE}" == "IMPORT" ]]; then
  [[ "${AUTHORING_IMPORT_CONFIRMATION:-}" == "IMPORT_VERIFIED_AUTHORING_STATE" ]] || die "IMPORT requires explicit confirmation"
  [[ "${AUTHORING_BACKUP_CONFIRMED:-}" == "true" ]] || die "IMPORT requires verified Content and AI backups"
  die "IMPORT execution is intentionally disabled until a successful DRY_RUN is separately approved"
fi

if ! command -v psql >/dev/null 2>&1 || [[ "$(psql --version 2>/dev/null | sed -E 's/.* ([0-9]+)(\..*)?$/\1/')" -lt 18 ]]; then
  POSTGRES_TOOL_DIR="${PLATFORM_STATE_DIR}/postgres18-client"
  install -d -m 700 "${POSTGRES_TOOL_DIR}"
  for tool in psql pg_dump pg_restore; do
    wrapper="${POSTGRES_TOOL_DIR}/${tool}"
    printf '%s\n' '#!/usr/bin/env bash' "exec docker run --rm --network host -e PGPASSWORD -v /tmp:/tmp postgres:18-alpine ${tool} \"\$@\"" >"${wrapper}"
    chmod 700 "${wrapper}"
  done
  export POSTGRES_TOOL_DIR
fi
PSQL="$(postgres_tool psql)"; PG_DUMP="$(postgres_tool pg_dump)"; PG_RESTORE="$(postgres_tool pg_restore)"

normalize_url() {
  python3 -c 'import sys,urllib.parse
u=urllib.parse.urlsplit(sys.stdin.read().strip().removeprefix("jdbc:")); q=dict(urllib.parse.parse_qsl(u.query,keep_blank_values=True)); q.pop("sslfactory",None)
if q.pop("ssl",None)=="true" and "sslmode" not in q:q["sslmode"]="require"
if "channelBinding" in q:q["channel_binding"]=q.pop("channelBinding")
print(urllib.parse.urlunsplit((u.scheme,u.netloc,u.path,urllib.parse.urlencode(q),"")))'
}
url_metadata() {
  python3 -c 'import sys,urllib.parse,json
u=urllib.parse.urlsplit(sys.stdin.read().strip()); print(json.dumps({"host":u.hostname or "","port":u.port or 5432,"database":u.path.lstrip("/"),"sslmode":dict(urllib.parse.parse_qsl(u.query)).get("sslmode","")}))'
}

for prefix in CONTENT AI LEARNING; do
  runtime="$(env_file_value "${prefix}_DATABASE_URL" "${PLATFORM_ENV_FILE}" | normalize_url)"
  migration_raw="$(env_file_value "${prefix}_MIGRATION_DATABASE_URL" "${PLATFORM_ENV_FILE}")"
  if [[ -n "${migration_raw}" ]]; then migration="$(printf '%s' "${migration_raw}" | normalize_url)"; else migration="${runtime}"; fi
  username="$(env_file_value "${prefix}_DATABASE_USERNAME" "${PLATFORM_ENV_FILE}")"
  password="$(env_file_value "${prefix}_DATABASE_PASSWORD" "${PLATFORM_ENV_FILE}")"
  printf -v "${prefix}_RUNTIME_URL" '%s' "${runtime}"; printf -v "${prefix}_MIGRATION_URL" '%s' "${migration}"
  printf -v "${prefix}_USERNAME" '%s' "${username}"; printf -v "${prefix}_PASSWORD" '%s' "${password}"
done

hosts=(); migration_hosts=()
for prefix in CONTENT AI LEARNING; do
  for connection in RUNTIME MIGRATION; do
    url_var="${prefix}_${connection}_URL"; metadata="$(printf '%s' "${!url_var}" | url_metadata)"
    host="$(jq -r .host <<<"${metadata}")"; database="$(jq -r .database <<<"${metadata}")"
    [[ "${database}" == "${prefix,,}" ]] || die "${prefix} ${connection} targets unexpected database"
    [[ "${host,,}" == *.eu-central-1.aws.neon.tech ]] || die "${prefix} ${connection} is not a Neon eu-central-1 endpoint"
    [[ "${host,,}" != *us-east* && "${host,,}" != *render* ]] || die "Legacy US or Render target rejected"
    canonical_host="${host,,}"; canonical_host="${canonical_host/-pooler./.}"
    hosts+=("${canonical_host}")
    if [[ "${connection}" == "MIGRATION" ]]; then migration_hosts+=("${host,,}"); fi
  done
done
for host in "${hosts[@]}"; do [[ "${host}" == "${hosts[0]}" ]] || die "Service database endpoints differ unexpectedly"; done
[[ "${migration_hosts[0]}" == "${migration_hosts[1]}" && "${migration_hosts[0]}" == "${migration_hosts[2]}" ]] || die "Migration database endpoints differ unexpectedly"
HOST_SHA256="$(printf '%s' "${migration_hosts[0]}" | sha256sum | awk '{print $1}')"
[[ "${HOST_SHA256}" == "${EXPECTED_HOST_SHA256}" ]] || die "Authoritative endpoint fingerprint mismatch"

db_value() {
  local prefix="${1^^}" query="$2" username_var="${1^^}_USERNAME" password_var="${1^^}_PASSWORD" url_var="${1^^}_MIGRATION_URL"
  PGPASSWORD="${!password_var}" "${PSQL}" -XAt -v ON_ERROR_STOP=1 --username="${!username_var}" --dbname="${!url_var}" --command="BEGIN TRANSACTION READ ONLY; ${query}; ROLLBACK;" | grep -vE '^(BEGIN|ROLLBACK)$'
}
content_migration="$(db_value content "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
ai_migration="$(db_value ai "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
[[ "${content_migration}" == "${EXPECTED_CONTENT_MIGRATION}" ]] || die "Content migration mismatch"
[[ "${ai_migration}" == "${EXPECTED_AI_MIGRATION}" ]] || die "AI migration mismatch"

read_only_transaction_proof() {
  local prefix="${1^^}" username_var="${1^^}_USERNAME" password_var="${1^^}_PASSWORD" url_var="${1^^}_MIGRATION_URL"
  local output
  output="$(PGPASSWORD="${!password_var}" "${PSQL}" -XAt -v ON_ERROR_STOP=1 --username="${!username_var}" --dbname="${!url_var}" --command="BEGIN TRANSACTION READ ONLY; SHOW transaction_read_only; ROLLBACK;" | grep -vE '^(BEGIN|ROLLBACK)$')"
  [[ "${output}" == "on" ]] || die "${prefix} read-only transaction proof failed"
}
for role in content ai learning; do read_only_transaction_proof "${role}"; done

learning_state() {
  db_value learning "SELECT json_build_object(
   'activeReleaseId',(SELECT external_release_id FROM imported_content_release WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1),
   'activeChecksum',(SELECT checksum FROM imported_content_release WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1),
   'importedReleases',(SELECT count(*) FROM imported_content_release),'profiles',(SELECT count(*) FROM learner_profile),'settings',(SELECT count(*) FROM learner_settings),
   'practiceSessions',(SELECT count(*) FROM practice_session),'practiceResponses',(SELECT count(*) FROM practice_response),'progress',(SELECT count(*) FROM topic_progress),
   'mockAttempts',(SELECT count(*) FROM mock_exam_attempt),'mockResponses',(SELECT count(*) FROM mock_exam_response))::text"
}
learning_before="$(learning_state)"
[[ "$(jq -r .activeReleaseId <<<"${learning_before}")" == "${EXPECTED_RELEASE_ID}" ]] || die "Learning active release mismatch"

work="$(mktemp -d /tmp/authoring-dry-run.XXXXXX)"; chmod 700 "${work}"
cleanup(){ rm -rf -- "${work}"; rm -f -- "${SNAPSHOT_ARCHIVE}"; }
trap cleanup EXIT
tar -xzf "${SNAPSHOT_ARCHIVE}" -C "${work}"
snapshot="${work}/authoring-snapshot"
python3 scripts/authoring_snapshot.py verify --snapshot "${snapshot}" --expected-semantic "${EXPECTED_SEMANTIC}" >/dev/null

# Prove full future backup tooling against both authoring databases without retaining a production backup in DRY_RUN.
backup_report='[]'
for prefix in CONTENT AI; do
  username_var="${prefix}_USERNAME"; password_var="${prefix}_PASSWORD"; url_var="${prefix}_MIGRATION_URL"
  archive="${work}/${prefix,,}-backup-capability.dump"
  backup_timestamp="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  backup_identifier="dry-run-${prefix,,}-${backup_timestamp//[:]/-}"
  PGPASSWORD="${!password_var}" "${PG_DUMP}" --schema=public --format=custom --no-owner --no-acl --username="${!username_var}" --dbname="${!url_var}" --file="${archive}"
  "${PG_RESTORE}" --list "${archive}" >/dev/null
  checksum="$(sha256sum "${archive}"|awk '{print $1}')"
  if [[ "${prefix}" == "CONTENT" ]]; then
    backup_migration="${content_migration}"
  else
    backup_migration="${ai_migration}"
  fi
  backup_report="$(jq -c --arg db "${prefix,,}" --arg checksum "${checksum}" --arg migration "${backup_migration}" --arg fingerprint "${HOST_SHA256}" --arg timestamp "${backup_timestamp}" --arg identifier "${backup_identifier}" '.+[{database:$db,schema:"public",targetFingerprint:$fingerprint,migration:$migration,backupTimestamp:$timestamp,backupIdentifier:$identifier,artifactChecksum:$checksum,validation:"PG_RESTORE_LIST_OK",kind:"FULL_PUBLIC_SCHEMA_CAPABILITY_PROOF",retained:false,restoration:"Restore only to an isolated empty database using pg_restore --clean --if-exists --no-owner --no-acl; never restore over production."}]' <<<"${backup_report}")"
done

# Export authoritative Content and AI targets using read-only, repeatable transactions and environment-only credentials.
for role in content ai; do
  prefix="${role^^}"; url_var="${prefix}_MIGRATION_URL"; username_var="${prefix}_USERNAME"; password_var="${prefix}_PASSWORD"
  metadata="$(printf '%s' "${!url_var}" | url_metadata)"
  export "AUTHORING_${prefix}_PGHOST=$(jq -r .host <<<"${metadata}")" "AUTHORING_${prefix}_PGPORT=$(jq -r .port <<<"${metadata}")" "AUTHORING_${prefix}_PGUSER=${!username_var}" "AUTHORING_${prefix}_PGPASSWORD=${!password_var}" "AUTHORING_${prefix}_PGSSLMODE=$(jq -r 'if .sslmode=="" then "require" else .sslmode end' <<<"${metadata}")"
done
python3 scripts/authoring_snapshot.py export --output "${work}/target-before" --source-commit "${SOURCE_COMMIT}" --allow-noncanonical >/dev/null
python3 scripts/authoring_snapshot.py plan --source "${snapshot}" --target "${work}/target-before" --output "${work}/dry-run-report.json"
python3 scripts/authoring_snapshot.py export --output "${work}/target-after" --source-commit "${SOURCE_COMMIT}" --allow-noncanonical >/dev/null
before_semantic="$(jq -r .semanticChecksum "${work}/target-before/manifest.json")"; after_semantic="$(jq -r .semanticChecksum "${work}/target-after/manifest.json")"
[[ "${before_semantic}" == "${after_semantic}" ]] || die "Content or AI changed during DRY_RUN"
learning_after="$(learning_state)"; [[ "${learning_before}" == "${learning_after}" ]] || die "Learning changed during DRY_RUN"

jq -n --arg event authoring_transfer_dry_run --arg hostSha256 "${HOST_SHA256}" --arg contentMigration "${content_migration}" --arg aiMigration "${ai_migration}" --arg targetBefore "${before_semantic}" --arg targetAfter "${after_semantic}" --argjson learningBefore "${learning_before}" --argjson learningAfter "${learning_after}" --argjson backups "${backup_report}" --slurpfile plan "${work}/dry-run-report.json" '{event:$event,region:"eu-central-1",hostSha256:$hostSha256,migrations:{content:$contentMigration,ai:$aiMigration},readOnlyTransactions:{content:true,ai:true,learning:true},zeroWrite:{targetBefore:$targetBefore,targetAfter:$targetAfter,equal:($targetBefore==$targetAfter)},learning:{before:$learningBefore,after:$learningAfter,equal:($learningBefore==$learningAfter)},backupControls:$backups,plan:$plan[0]}' >"${work}/sanitized-dry-run-report.json"
cp "${work}/sanitized-dry-run-report.json" "${PLATFORM_STATE_DIR}/authoring-transfer-last-dry-run.json"
cat "${work}/sanitized-dry-run-report.json"
