#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths
require_file "${PLATFORM_ENV_FILE}"

if ! command -v psql >/dev/null 2>&1 || [[ "$(psql --version 2>/dev/null | sed -E 's/.* ([0-9]+)(\..*)?$/\1/')" -lt 18 ]]; then
  POSTGRES_TOOL_DIR="${PLATFORM_STATE_DIR}/postgres18-client"
  install -d -m 700 "${POSTGRES_TOOL_DIR}"
  wrapper="${POSTGRES_TOOL_DIR}/psql"
  printf '%s\n' '#!/usr/bin/env bash' \
    'exec docker run --rm --network host -e PGPASSWORD postgres:18-alpine psql "$@"' >"${wrapper}"
  chmod 700 "${wrapper}"
  export POSTGRES_TOOL_DIR
fi
PSQL="$(postgres_tool psql)"
database_url="$(env_file_value CONTENT_MIGRATION_DATABASE_URL "${PLATFORM_ENV_FILE}")"
[[ -n "${database_url}" ]] || database_url="$(env_file_value CONTENT_DATABASE_URL "${PLATFORM_ENV_FILE}")"
database_user="$(env_file_value CONTENT_DATABASE_USERNAME "${PLATFORM_ENV_FILE}")"
database_password="$(env_file_value CONTENT_DATABASE_PASSWORD "${PLATFORM_ENV_FILE}")"
database_url="$(python3 -c '
import sys,urllib.parse
u=urllib.parse.urlsplit(sys.argv[1].removeprefix("jdbc:")); q=dict(urllib.parse.parse_qsl(u.query,keep_blank_values=True))
q.pop("sslfactory",None)
if q.pop("ssl",None)=="true" and "sslmode" not in q: q["sslmode"]="require"
if "channelBinding" in q: q["channel_binding"]=q.pop("channelBinding")
print(urllib.parse.urlunsplit((u.scheme,u.netloc,u.path,urllib.parse.urlencode(q),"")))
' "${database_url}")"
read -r database_host database_name < <(python3 -c 'import sys,urllib.parse; u=urllib.parse.urlparse(sys.argv[1]); print(u.hostname or "",u.path.lstrip("/"))' "${database_url}")
[[ "${database_host,,}" == *.eu-central-1.aws.neon.tech && "${database_name}" == content ]] ||
  die "Refusing unverified hosted Content database target"

cd "${PLATFORM_REPOSITORY}"
printf '%s  %s\n' \
  '39a93261cc64af0122e186b7d67f57dffad573576570956a4754d22ce776aada' \
  'docs/sverige-i-fokus.pdf' | sha256sum --check --status
sql_file="$(mktemp)"
trap 'rm -f "${sql_file}"' EXIT
python3 scripts/sverige_i_fokus_sql.py >"${sql_file}"
PGPASSWORD="${database_password}" "${PSQL}" -X -v ON_ERROR_STOP=1 \
  --username="${database_user}" --dbname="${database_url}" <"${sql_file}"

verification="$(PGPASSWORD="${database_password}" "${PSQL}" -XAt -v ON_ERROR_STOP=1 \
  --username="${database_user}" --dbname="${database_url}" --command="SELECT json_build_object('activeRevision',(SELECT id FROM source_revision WHERE status='ACTIVE'),'v1Sections',(SELECT count(*) FROM source_section WHERE source_revision_id='sverige-i-fokus-source-v1'),'v2Sections',(SELECT count(*) FROM source_section WHERE source_revision_id='sverige-i-fokus-source-v2'),'affectedItems',(SELECT count(*) FROM source_revision_revalidation WHERE classification IN ('AFFECTED_REQUIRES_REPAIR','INVALID_AFTER_BOUNDARY_CORRECTION')))::text")"
python3 -c 'import json,sys; value=json.loads(sys.argv[1]); assert value == {"activeRevision":"sverige-i-fokus-source-v2","v1Sections":38,"v2Sections":38,"affectedItems":0}, value' "${verification}"
printf 'Activated Sverige i fokus source revision v2 without affected generated content.\n'
