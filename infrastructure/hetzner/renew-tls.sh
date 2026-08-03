#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"
load_platform_paths

require_command certbot
certbot renew --quiet --deploy-hook "${SCRIPT_DIR}/reload-gateway-certificates.sh"
