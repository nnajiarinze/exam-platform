#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths
require_command docker
require_command jq

normalize_url() {
  python3 -c 'import sys,urllib.parse
u=urllib.parse.urlsplit(sys.stdin.read().strip().removeprefix("jdbc:"))
q=dict(urllib.parse.parse_qsl(u.query,keep_blank_values=True))
q.pop("sslfactory",None)
q.pop("targetServerType",None)
if q.pop("ssl",None)=="true" and "sslmode" not in q:q["sslmode"]="require"
if "channelBinding" in q:q["channel_binding"]=q.pop("channelBinding")
print(urllib.parse.urlunsplit((u.scheme,u.netloc,u.path,urllib.parse.urlencode(q),"")))'
}
query() {
  local url_key="$1" user_key="$2" password_key="$3" sql="$4"
  local url user password
  url="$(env_file_value "${url_key}" "${PLATFORM_ENV_FILE}" | normalize_url)"
  user="$(env_file_value "${user_key}" "${PLATFORM_ENV_FILE}")"
  password="$(env_file_value "${password_key}" "${PLATFORM_ENV_FILE}")"
  docker run --rm --network host -e PGPASSWORD="${password}" postgres:18-alpine \
    psql -XqAt --no-psqlrc --set=ON_ERROR_STOP=1 --username="${user}" --dbname="${url}" \
    --command="SET default_transaction_read_only=on; ${sql}"
}

content="$(query CONTENT_MIGRATION_DATABASE_URL CONTENT_DATABASE_USERNAME CONTENT_DATABASE_PASSWORD "
SELECT json_build_object(
  'approvedActiveFacts',(SELECT count(*) FROM knowledge_fact WHERE review_status='APPROVED' AND status='ACTIVE'),
  'latestLessons',(SELECT count(*) FROM lesson_draft d WHERE d.review_status='REVIEWED' AND NOT EXISTS(SELECT 1 FROM lesson_draft n WHERE n.topic_id=d.topic_id AND n.review_status='REVIEWED' AND n.version_number>d.version_number)),
  'latestPages',(SELECT count(*) FROM lesson_draft_section s JOIN lesson_draft d ON d.id=s.lesson_draft_id WHERE d.review_status='REVIEWED' AND NOT EXISTS(SELECT 1 FROM lesson_draft n WHERE n.topic_id=d.topic_id AND n.review_status='REVIEWED' AND n.version_number>d.version_number)),
  'questions',(SELECT count(*) FROM question),
  'authoringAudits',(SELECT count(*) FROM audit_event),
  'activeRelease',(SELECT json_build_object('id',id,'key',release_number,'checksum',checksum) FROM content_release WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1)
)::text;")"
learning="$(query LEARNING_MIGRATION_DATABASE_URL LEARNING_DATABASE_USERNAME LEARNING_DATABASE_PASSWORD "
SELECT json_build_object(
  'activeReleaseId',(SELECT external_release_id FROM imported_content_release WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1),
  'activeChecksum',(SELECT checksum FROM imported_content_release WHERE status='ACTIVE' ORDER BY activated_at DESC LIMIT 1),
  'profiles',(SELECT count(*) FROM learner_profile),
  'profileIdentityHash',(SELECT md5(coalesce(string_agg(id::text,',' ORDER BY id::text),'')) FROM learner_profile),
  'settings',(SELECT count(*) FROM learner_settings),
  'practiceSessions',(SELECT count(*) FROM practice_session),
  'practiceResponses',(SELECT count(*) FROM practice_response),
  'progress',(SELECT count(*) FROM topic_progress),
  'mockAttempts',(SELECT count(*) FROM mock_exam_attempt),
  'mockResponses',(SELECT count(*) FROM mock_exam_response)
)::text;")"
identity="$(query KEYCLOAK_DATABASE_URL KEYCLOAK_DATABASE_USERNAME KEYCLOAK_DATABASE_PASSWORD "
SELECT json_build_object(
  'users',(SELECT count(*) FROM user_entity),
  'subjectHash',(SELECT md5(coalesce(string_agg(id,',' ORDER BY id),'')) FROM user_entity),
  'roleMappings',(SELECT count(*) FROM user_role_mapping)
)::text;")"

jq -cn --argjson content "${content}" --argjson learning "${learning}" --argjson identity "${identity}" \
  '{content:$content,learning:$learning,identity:$identity}'
