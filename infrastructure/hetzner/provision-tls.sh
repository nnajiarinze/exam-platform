#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

[[ "${EUID}" -eq 0 ]] || die "Run provision-tls.sh as root"
require_command certbot
require_var API_DOMAIN
require_var TLS_EMAIL
[[ "${API_DOMAIN}" != "api.example.com" ]] || die "Replace the placeholder API domain"

certbot certonly \
  --standalone \
  --non-interactive \
  --agree-tos \
  --email "${TLS_EMAIL}" \
  --domain "${API_DOMAIN}"

printf 'TLS certificate issued for %s. HSTS remains intentionally disabled.\n' "${API_DOMAIN}"
