#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

cd "${PLATFORM_REPOSITORY}"
python3 scripts/sverige_i_fokus_corpus.py --check >/dev/null
python3 scripts/sverige_i_fokus_sql.py | compose exec -T content-database \
  sh -c 'psql -X -v ON_ERROR_STOP=1 -U "$POSTGRES_USER" -d "$POSTGRES_DB"'

verification="$(compose exec -T content-database sh -c \
  'psql -XAt -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "SELECT json_build_object('"'"'activeRevision'"'"',(SELECT id FROM source_revision WHERE status='"'"'ACTIVE'"'"'),'"'"'v1Sections'"'"',(SELECT count(*) FROM source_section WHERE source_revision_id='"'"'sverige-i-fokus-source-v1'"'"'),'"'"'v2Sections'"'"',(SELECT count(*) FROM source_section WHERE source_revision_id='"'"'sverige-i-fokus-source-v2'"'"'),'"'"'affectedItems'"'"',(SELECT count(*) FROM source_revision_revalidation WHERE classification IN ('"'"'AFFECTED_REQUIRES_REPAIR'"'"','"'"'INVALID_AFTER_BOUNDARY_CORRECTION'"'"')))::text"')"
python3 -c 'import json,sys; value=json.loads(sys.argv[1]); assert value == {"activeRevision":"sverige-i-fokus-source-v2","v1Sections":38,"v2Sections":38,"affectedItems":0}, value' "${verification}"
printf 'Activated Sverige i fokus source revision v2 without affected generated content.\n'
