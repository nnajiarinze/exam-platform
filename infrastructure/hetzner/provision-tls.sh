#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

[[ "${EUID}" -eq 0 ]] || die "Run provision-tls.sh as root"
require_command certbot
require_var API_DOMAIN
[[ "${API_DOMAIN}" != "api.example.com" ]] || die "Replace the placeholder API domain"

install -d -m 755 /var/www/certbot
contact_args=(--register-unsafely-without-email)
if [[ -n "${TLS_EMAIL:-}" ]]; then contact_args=(--email "${TLS_EMAIL}"); fi
certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --non-interactive \
  --agree-tos \
  "${contact_args[@]}" \
  --domain "${API_DOMAIN}"

printf 'TLS certificate issued for %s. HSTS remains intentionally disabled.\n' "${API_DOMAIN}"
