#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

[[ "${CONFIRM_RESTORE:-}" == "RESTORE_TO_EMPTY_EU_DATABASES" ]] || \
  die "Set CONFIRM_RESTORE=RESTORE_TO_EMPTY_EU_DATABASES after verifying backups and targets"
require_command age
PG_RESTORE="$(postgres_tool pg_restore)"
for variable in RESTORE_AGE_IDENTITY RESTORE_DATABASE_HOST RESTORE_DATABASE_PORT RESTORE_BACKUP_DIR; do require_var "${variable}"; done

restore_one() {
  local database="$1" prefix="$2" username password encrypted dump_file username_var password_var
  require_var "${prefix}_USERNAME"
  require_var "${prefix}_PASSWORD"
  username_var="${prefix}_USERNAME"
  password_var="${prefix}_PASSWORD"
  username="${!username_var}"
  password="${!password_var}"
  encrypted="$(find "${RESTORE_BACKUP_DIR}" -maxdepth 1 -type f -name "${database}-*.dump.age" | sort | tail -n 1)"
  [[ -n "${encrypted}" ]] || die "No encrypted backup found for ${database}"
  dump_file="$(mktemp)"
  trap 'rm -f -- "${dump_file}"' RETURN
  age --decrypt --identity "${RESTORE_AGE_IDENTITY}" --output "${dump_file}" "${encrypted}"
  "${PG_RESTORE}" --list "${dump_file}" >/dev/null
  PGPASSWORD="${password}" "${PG_RESTORE}" \
    --host="${RESTORE_DATABASE_HOST}" --port="${RESTORE_DATABASE_PORT}" \
    --username="${username}" --dbname="${database}" \
    --exit-on-error --no-owner --no-privileges "${dump_file}"
  rm -f -- "${dump_file}"
  trap - RETURN
  printf 'Restored %s without printing data.\n' "${database}"
}

restore_one content RESTORE_CONTENT
restore_one learning RESTORE_LEARNING
restore_one ai RESTORE_AI
restore_one identity RESTORE_IDENTITY
