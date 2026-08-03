#!/bin/sh
set -eu

: "${API_DOMAIN:?API_DOMAIN is required}"
: "${ROLLBACK_HOSTNAME:?ROLLBACK_HOSTNAME is required during the permanent-domain transition}"
runtime_dir=/tmp/nginx-certs

install -d -m 0750 "${runtime_dir}"
for certificate in primary legacy; do
  if [ "${certificate}" = primary ]; then domain="${API_DOMAIN}"; else domain="${ROLLBACK_HOSTNAME}"; fi
  source_dir="/etc/letsencrypt/live/${domain}"
  install -d -m 0750 "${runtime_dir}/${certificate}"
  for file in fullchain.pem privkey.pem; do
    if [ ! -s "${source_dir}/${file}" ]; then
      printf 'Required %s hosted TLS file is unavailable for %s.\n' "${certificate}" "${domain}" >&2
      exit 1
    fi
    install -m 0440 "${source_dir}/${file}" "${runtime_dir}/${certificate}/${file}"
  done
done
install -m 0660 /dev/null /tmp/nginx-error.log
install -m 0660 /dev/null /tmp/nginx-access.log

# Preserve container-native logging while ensuring Nginx itself never runs as root.
su-exec 101:101 tail -n 0 -F /tmp/nginx-access.log &
su-exec 101:101 tail -n 0 -F /tmp/nginx-error.log >&2 &

exec su-exec 101:101 /docker-entrypoint.sh "$@"
