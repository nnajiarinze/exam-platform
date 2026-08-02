#!/bin/sh
set -eu

: "${API_DOMAIN:?API_DOMAIN is required}"
source_dir="/etc/letsencrypt/live/${API_DOMAIN}"
runtime_dir=/tmp/nginx-certs

for file in fullchain.pem privkey.pem; do
  if [ ! -s "${source_dir}/${file}" ]; then
    printf 'Required hosted TLS file is unavailable: %s\n' "${source_dir}/${file}" >&2
    exit 1
  fi
done

install -d -m 0750 "${runtime_dir}"
install -m 0440 "${source_dir}/fullchain.pem" "${runtime_dir}/fullchain.pem"
install -m 0440 "${source_dir}/privkey.pem" "${runtime_dir}/privkey.pem"
install -m 0660 /dev/null /tmp/nginx-error.log
install -m 0660 /dev/null /tmp/nginx-access.log

# Preserve container-native logging while ensuring Nginx itself never runs as root.
su-exec 101:101 tail -n 0 -F /tmp/nginx-access.log &
su-exec 101:101 tail -n 0 -F /tmp/nginx-error.log >&2 &

exec su-exec 101:101 /docker-entrypoint.sh "$@"
