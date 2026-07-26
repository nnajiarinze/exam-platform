#!/usr/bin/env bash
set -Eeuo pipefail
[[ "${EUID}" -eq 0 ]] || { printf 'This read-only inspection must run as root.\n' >&2; exit 1; }

printf '%s\n' '--- UFW ---'
ufw status verbose
printf '%s\n' '--- Public TCP listeners ---'
ss -lntp | awk 'NR == 1 || $4 ~ /(^|:)(22|80|443)$/'
