#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

# The unprivileged gateway copies protected private keys into its tmpfs at
# startup. Recreating only this stateless container atomically loads renewed
# certificates without exposing key material or restarting backend services.
if compose ps --status running --services | grep -qx api-gateway; then
  compose up -d --no-deps --force-recreate --wait --wait-timeout 60 api-gateway
  compose exec -T --user 101:101 api-gateway nginx -t
fi
