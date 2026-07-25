#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
require_command psql

for side in SOURCE TARGET; do
  require_var "${side}_DATABASE_HOST"
  require_var "${side}_DATABASE_PORT"
  for database in CONTENT LEARNING AI IDENTITY; do
    require_var "${side}_${database}_USERNAME"
    require_var "${side}_${database}_PASSWORD"
  done
done

WORK_DIR="$(mktemp -d)"
trap 'rm -rf -- "${WORK_DIR}"' EXIT

counts() {
  local side="$1" database="$2" upper="${2^^}" host port username password output
  local host_var port_var username_var password_var
  host_var="${side}_DATABASE_HOST"
  port_var="${side}_DATABASE_PORT"
  username_var="${side}_${upper}_USERNAME"
  password_var="${side}_${upper}_PASSWORD"
  host="${!host_var}"
  port="${!port_var}"
  username="${!username_var}"
  password="${!password_var}"
  output="${WORK_DIR}/${side}-${database}.txt"
  PGPASSWORD="${password}" psql -X --no-psqlrc --set=ON_ERROR_STOP=1 \
    --host="${host}" --port="${port}" --username="${username}" --dbname="${database}" \
    --tuples-only --no-align <<'SQL' | sort >"${output}"
SELECT format(
  'SELECT %L || count(*) FROM %I.%I;',
  schemaname || '.' || relname || '|',
  schemaname,
  relname
)
FROM pg_stat_user_tables
ORDER BY schemaname, relname
\gexec
SQL
}

status=0
for database in content learning ai identity; do
  counts SOURCE "${database}"
  counts TARGET "${database}"
  if diff --unified=0 "${WORK_DIR}/SOURCE-${database}.txt" "${WORK_DIR}/TARGET-${database}.txt"; then
    printf '%s exact row counts match.\n' "${database}"
  else
    printf '%s exact row counts differ.\n' "${database}" >&2
    status=1
  fi
done
exit "${status}"
