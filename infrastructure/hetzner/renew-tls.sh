#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

require_command certbot
certbot renew --quiet
if compose ps --status running --services | grep -qx api-gateway; then
  compose exec -T api-gateway nginx -s reload
fi
