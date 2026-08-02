#!/usr/bin/env bash
set -Eeuo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib.sh
source "${SCRIPT_DIR}/lib.sh"

require_var API_DOMAIN
[[ "${API_DOMAIN}" != "api.example.com" ]] || die "Replace the placeholder API domain"

contact_args=(--register-unsafely-without-email)
if [[ -n "${TLS_EMAIL:-}" ]]; then contact_args=(--email "${TLS_EMAIL}"); fi
authenticator="${TLS_AUTHENTICATOR:-webroot}"
case "${authenticator}" in
  webroot) authenticator_args=(--webroot --webroot-path /var/www/certbot); docker_port_args=() ;;
  standalone) authenticator_args=(--standalone); docker_port_args=(-p 80:80) ;;
  *) die "TLS_AUTHENTICATOR must be webroot or standalone" ;;
esac
certbot_args=(certonly "${authenticator_args[@]}" --non-interactive --agree-tos "${contact_args[@]}" --domain "${API_DOMAIN}")
if [[ "${EUID}" -eq 0 ]] && command -v certbot >/dev/null 2>&1; then
  install -d -m 755 /var/www/certbot
  certbot "${certbot_args[@]}"
else
  require_command docker
  docker run --rm "${docker_port_args[@]}" \
    -v /etc/letsencrypt:/etc/letsencrypt \
    -v /var/www/certbot:/var/www/certbot \
    certbot/certbot:latest "${certbot_args[@]}"
fi

printf 'TLS certificate issued for %s. HSTS remains intentionally disabled.\n' "${API_DOMAIN}"
