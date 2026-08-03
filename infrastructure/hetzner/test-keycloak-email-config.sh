#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
harden="${root}/infrastructure/hetzner/harden-keycloak.sh"
theme="${root}/infrastructure/keycloak/themes/svea-study/email"

grep -q 'smtp.resend.com' "${harden}"
grep -q 'smtp_username.*RESEND' "${harden}" && { echo 'A duplicate Resend username mapping was introduced' >&2; exit 1; } || true
grep -q 'smtp_password=.*RESEND_API_KEY' "${harden}"
if grep --exclude=test-keycloak-email-config.sh -RqE 'KEYCLOAK_SMTP_PASSWORD|re_[A-Za-z0-9_]{16,}' "${root}/.env.example" "${root}/.env.hosted.example" "${root}/infrastructure" "${root}/apps/admin/src"; then
  echo 'A duplicate SMTP password variable or key-like value was found' >&2
  exit 1
fi
grep -q 'emailTheme=svea-study' "${harden}"
grep -q 'defaultLocale=sv' "${harden}"
grep -q 'starttls' "${harden}"
grep -q 'no-reply@tinkona.com' "${root}/.github/workflows/deploy-hosted-auth.yml"
grep -q 'support@tinkona.com' "${root}/.github/workflows/deploy-hosted-auth.yml"
grep -q 'Verifiera din e-post' "${theme}/messages/messages_sv.properties"
grep -q 'Verify your email' "${theme}/messages/messages_en.properties"
if grep -RqiE 'sslip\.io|46\.224\.221\.7|localhost|onrender\.com|http://' "${theme}"; then
  echo 'The email theme contains a forbidden production hostname' >&2
  exit 1
fi
printf 'Keycloak email provisioning and theme contract passed.\n'
