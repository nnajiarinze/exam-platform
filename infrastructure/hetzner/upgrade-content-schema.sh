#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

EXPECTED_ENDPOINT="${1:-}"; EXPECTED_CURRENT="${2:-}"; EXPECTED_TARGET="${3:-}"
EXPECTED_BACKUP_CHECKSUM="${4:-}"; CONFIRMATION="${5:-}"; IMAGE_TAG="${6:-}"
[[ "${EXPECTED_ENDPOINT}" =~ ^[a-f0-9]{64}$ && "${EXPECTED_CURRENT}" == 20 && "${EXPECTED_TARGET}" == 24 ]]
[[ "${CONFIRMATION}" == UPGRADE_CONTENT_V20_TO_V24 ]] || die "Explicit Content upgrade confirmation is missing"
require_sha "${IMAGE_TAG}"; require_file "${PLATFORM_ENV_FILE}"; require_file "${PLATFORM_COMPOSE_FILE}"
for tool in psql pg_dump pg_restore; do postgres_tool "${tool}" >/dev/null; done
require_command docker; require_command jq; require_command sha256sum
PSQL="$(postgres_tool psql)"; PG_DUMP="$(postgres_tool pg_dump)"; PG_RESTORE="$(postgres_tool pg_restore)"

normalize_url(){ python3 -c 'import sys,urllib.parse
u=urllib.parse.urlsplit(sys.stdin.read().strip().removeprefix("jdbc:")); q=dict(urllib.parse.parse_qsl(u.query,keep_blank_values=True)); q.pop("sslfactory",None)
if q.pop("ssl",None)=="true" and "sslmode" not in q:q["sslmode"]="require"
print(urllib.parse.urlunsplit((u.scheme,u.netloc,u.path,urllib.parse.urlencode(q),"")))'; }
url_metadata(){ python3 -c 'import sys,urllib.parse,json
u=urllib.parse.urlsplit(sys.stdin.read().strip()); print(json.dumps({"host":u.hostname or "","database":u.path.lstrip("/")}))'; }
db_url(){ env_file_value "$1" "${PLATFORM_ENV_FILE}" | normalize_url; }
CONTENT_URL="$(db_url CONTENT_MIGRATION_DATABASE_URL)"; LEARNING_URL="$(db_url LEARNING_MIGRATION_DATABASE_URL)"
CONTENT_USER="$(env_file_value CONTENT_DATABASE_USERNAME "${PLATFORM_ENV_FILE}")"; CONTENT_PASSWORD="$(env_file_value CONTENT_DATABASE_PASSWORD "${PLATFORM_ENV_FILE}")"
LEARNING_USER="$(env_file_value LEARNING_DATABASE_USERNAME "${PLATFORM_ENV_FILE}")"; LEARNING_PASSWORD="$(env_file_value LEARNING_DATABASE_PASSWORD "${PLATFORM_ENV_FILE}")"
metadata="$(printf %s "${CONTENT_URL}"|url_metadata)"; host="$(jq -r .host <<<"${metadata}")"; database="$(jq -r .database <<<"${metadata}")"
[[ "${database}" == content && "${host,,}" == *.eu-central-1.aws.neon.tech && "${host,,}" != *render* && "${host,,}" != *us-* ]] || die "Rejected non-authoritative Content target"
canonical_host="${host,,}"; canonical_host="${canonical_host/-pooler./.}"
host_sha="$(printf %s "${canonical_host}"|sha256sum|awk '{print $1}')"; [[ "${host_sha}" == "${EXPECTED_ENDPOINT}" ]] || die "Endpoint fingerprint mismatch"

query(){ local url="$1" user="$2" password="$3" sql="$4"; PGPASSWORD="${password}" "${PSQL}" -XAt -v ON_ERROR_STOP=1 --username="${user}" --dbname="${url}" --command="${sql}"; }
content_version(){ query "${CONTENT_URL}" "${CONTENT_USER}" "${CONTENT_PASSWORD}" "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1"; }
learning_state(){ query "${LEARNING_URL}" "${LEARNING_USER}" "${LEARNING_PASSWORD}" "SELECT json_build_object('activeReleaseId',(SELECT external_release_id FROM imported_content_release WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1),'activeChecksum',(SELECT checksum FROM imported_content_release WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1),'importedReleases',(SELECT count(*) FROM imported_content_release),'profiles',(SELECT count(*) FROM learner_profile),'settings',(SELECT count(*) FROM learner_settings),'practiceSessions',(SELECT count(*) FROM practice_session),'practiceResponses',(SELECT count(*) FROM practice_response),'progress',(SELECT count(*) FROM topic_progress),'mockAttempts',(SELECT count(*) FROM mock_exam_attempt),'mockResponses',(SELECT count(*) FROM mock_exam_response))::text"; }
[[ "$(content_version)" == "${EXPECTED_CURRENT}" ]] || die "Content is not at expected V20"
failed="$(query "${CONTENT_URL}" "${CONTENT_USER}" "${CONTENT_PASSWORD}" "SELECT count(*) FROM flyway_schema_history WHERE NOT success")"; [[ "${failed}" == 0 ]] || die "Failed or partial Flyway history exists"
conflicts="$(query "${CONTENT_URL}" "${CONTENT_USER}" "${CONTENT_PASSWORD}" "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('source_payload_revision','source_payload_identity_reconciliation','source_dependency_reconciliation','source_payload_reconciliation_revision')")"; [[ "${conflicts}" == 0 ]] || die "V21-V24 objects already exist unexpectedly"
before_counts="$(query "${CONTENT_URL}" "${CONTENT_USER}" "${CONTENT_PASSWORD}" "SELECT json_build_object('sourceReference',(SELECT count(*) FROM source_reference),'sourceRevision',(SELECT count(*) FROM source_revision),'sourceSection',(SELECT count(*) FROM source_section),'objectives',(SELECT count(*) FROM learning_objective),'facts',(SELECT count(*) FROM knowledge_fact),'questions',(SELECT count(*) FROM question),'releases',(SELECT count(*) FROM content_release),'audits',(SELECT count(*) FROM audit_event))::text")"
[[ "$(jq -r '.facts==0 and .questions==0 and .releases==0' <<<"${before_counts}")" == true ]] || die "Unexpected hosted authoring content"
learning_before="$(learning_state)"

timestamp="$(date -u +%Y%m%dT%H%M%SZ)"; backup_id="content-v20-${timestamp}"; backup_dir="${PLATFORM_ROOT}/backups/${backup_id}"
install -d -m 700 "${backup_dir}"; backup="${backup_dir}/content-public.dump"
PGPASSWORD="${CONTENT_PASSWORD}" "${PG_DUMP}" --schema=public --format=custom --no-owner --no-acl --username="${CONTENT_USER}" --dbname="${CONTENT_URL}" --file="${backup}"
"${PG_RESTORE}" --list "${backup}" >"${backup_dir}/restore-list.txt"; backup_checksum="$(sha256sum "${backup}"|awk '{print $1}')"
if [[ -n "${EXPECTED_BACKUP_CHECKSUM}" && "${EXPECTED_BACKUP_CHECKSUM}" != AUTO ]]; then [[ "${backup_checksum}" == "${EXPECTED_BACKUP_CHECKSUM}" ]] || die "Backup checksum mismatch"; fi
chmod 600 "${backup}" "${backup_dir}/restore-list.txt"

stage_container="content-v24-stage-${timestamp,,}"; stage_service="content-v24-service-${timestamp,,}"; stage_network="content-v24-network-${timestamp,,}"
cleanup(){ docker rm -f "${stage_service}" "${stage_container}" >/dev/null 2>&1 || true; docker network rm "${stage_network}" >/dev/null 2>&1 || true; }
trap cleanup EXIT
docker network create "${stage_network}" >/dev/null
docker run -d --name "${stage_container}" --network "${stage_network}" -e POSTGRES_USER=content -e POSTGRES_PASSWORD=stage-only -e POSTGRES_DB=content postgres:16-alpine >/dev/null
for _ in {1..30}; do docker exec "${stage_container}" pg_isready -U content >/dev/null 2>&1 && break; sleep 1; done
docker exec -i "${stage_container}" pg_restore --exit-on-error --no-owner --no-acl -U content -d content <"${backup}"

registry="$(sed -n 's/^IMAGE_REGISTRY=//p' "${PLATFORM_RELEASE_ENV_FILE}"|tail -1)"; require_var registry
image="${registry}/citizenship-content-service:${IMAGE_TAG}"; docker pull "${image}" >/dev/null
docker run -d --name "${stage_service}" --network "${stage_network}" -e CONTENT_DATABASE_URL="jdbc:postgresql://${stage_container}:5432/content" -e CONTENT_MIGRATION_DATABASE_URL="jdbc:postgresql://${stage_container}:5432/content" -e CONTENT_DATABASE_USERNAME=content -e CONTENT_DATABASE_PASSWORD=stage-only -e OIDC_JWK_SET_URI=http://127.0.0.1/unused -e LEARNING_INTERNAL_API_KEY=stage -e AI_INTERNAL_API_KEY=stage "${image}" >/dev/null
for _ in {1..90}; do status="$(docker inspect -f '{{.State.Health.Status}}' "${stage_service}" 2>/dev/null || true)"; [[ "${status}" == healthy ]] && break; [[ "$(docker inspect -f '{{.State.Status}}' "${stage_service}")" == exited ]] && { docker logs "${stage_service}" >&2; die "Staging service exited"; }; sleep 2; done
[[ "$(docker inspect -f '{{.State.Health.Status}}' "${stage_service}")" == healthy ]] || die "Staging V24 service failed readiness"
stage_version="$(docker exec "${stage_container}" psql -U content -d content -Atc "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"; [[ "${stage_version}" == 24 ]] || die "Staging did not reach V24"
stage_counts="$(docker exec "${stage_container}" psql -U content -d content -Atc "SELECT json_build_object('sourceReference',(SELECT count(*) FROM source_reference),'sourceRevision',(SELECT count(*) FROM source_revision),'sourceSection',(SELECT count(*) FROM source_section),'objectives',(SELECT count(*) FROM learning_objective),'facts',(SELECT count(*) FROM knowledge_fact),'questions',(SELECT count(*) FROM question),'releases',(SELECT count(*) FROM content_release),'audits',(SELECT count(*) FROM audit_event))::text")"
[[ "${stage_counts}" == "${before_counts}" ]] || die "Staging migration changed semantic counts"
objects="$(docker exec "${stage_container}" psql -U content -d content -Atc "SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('source_payload_revision','source_payload_identity_reconciliation','source_dependency_reconciliation','source_payload_reconciliation_revision')")"; [[ "${objects}" == 4 ]] || die "Staging V24 objects missing"
docker stop "${stage_service}" >/dev/null

previous_image="$(compose ps -q content-service | xargs docker inspect -f '{{.Config.Image}}')"
temporary_release="$(mktemp "${PLATFORM_STATE_DIR}/content-v24-release.XXXXXX")"; printf 'IMAGE_TAG=%s\nIMAGE_REGISTRY=%s\n' "${IMAGE_TAG}" "${registry}" >"${temporary_release}"
docker compose --env-file "${PLATFORM_ENV_FILE}" --env-file "${temporary_release}" -f "${PLATFORM_COMPOSE_FILE}" up -d --no-deps --wait --wait-timeout 240 content-service
new_container="$(docker compose --env-file "${PLATFORM_ENV_FILE}" --env-file "${temporary_release}" -f "${PLATFORM_COMPOSE_FILE}" ps -q content-service)"
new_image="$(docker inspect -f '{{.Config.Image}}' "${new_container}")"; [[ "${new_image}" == "${image}" ]] || die "Content image mismatch"
[[ "$(content_version)" == "${EXPECTED_TARGET}" ]] || die "Production Content did not reach V24"
after_counts="$(query "${CONTENT_URL}" "${CONTENT_USER}" "${CONTENT_PASSWORD}" "SELECT json_build_object('sourceReference',(SELECT count(*) FROM source_reference),'sourceRevision',(SELECT count(*) FROM source_revision),'sourceSection',(SELECT count(*) FROM source_section),'objectives',(SELECT count(*) FROM learning_objective),'facts',(SELECT count(*) FROM knowledge_fact),'questions',(SELECT count(*) FROM question),'releases',(SELECT count(*) FROM content_release),'audits',(SELECT count(*) FROM audit_event))::text")"
[[ "${after_counts}" == "${before_counts}" ]] || die "Production migration changed semantic counts"
learning_after="$(learning_state)"; [[ "${learning_after}" == "${learning_before}" ]] || die "Learning state changed"
mutation_trigger="$(query "${CONTENT_URL}" "${CONTENT_USER}" "${CONTENT_PASSWORD}" "SELECT count(*) FROM pg_trigger WHERE tgname='reconciled_source_payload_immutable' AND NOT tgisinternal")"; [[ "${mutation_trigger}" == 1 ]] || die "Immutable payload trigger missing"
report="${PLATFORM_STATE_DIR}/content-v24-upgrade-${timestamp}.json"
jq -n --arg event content_v24_upgrade --arg endpoint "${host_sha}" --arg backupId "${backup_id}" --arg backupChecksum "${backup_checksum}" --arg beforeVersion "${EXPECTED_CURRENT}" --arg afterVersion "${EXPECTED_TARGET}" --arg previousImage "${previous_image}" --arg newImage "${new_image}" --argjson contentBefore "${before_counts}" --argjson contentAfter "${after_counts}" --argjson learningBefore "${learning_before}" --argjson learningAfter "${learning_after}" '{event:$event,region:"eu-central-1",endpointFingerprint:$endpoint,backup:{id:$backupId,checksum:$backupChecksum,pgRestoreList:"PASS",retained:true},staging:{restore:"PASS",migration:"V20_TO_V24_PASS",serviceReadiness:"UP"},migration:{before:$beforeVersion,after:$afterVersion},images:{before:$previousImage,after:$newImage},content:{before:$contentBefore,after:$contentAfter,equal:($contentBefore==$contentAfter)},learning:{before:$learningBefore,after:$learningAfter,equal:($learningBefore==$learningAfter)},importExecuted:false}' >"${report}"
cp "${report}" "${PLATFORM_STATE_DIR}/content-v24-upgrade-last.json"
printf '%s\n' "${report}"
