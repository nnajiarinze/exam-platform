#!/usr/bin/env bash
set -Eeuo pipefail

image="${1:-citizenship-gateway:ci}"
suffix="$$"
network="gateway-runtime-test-${suffix}"
gateway="gateway-runtime-test-${suffix}"
missing_gateway="gateway-missing-cert-test-${suffix}"
content="content-service-test-${suffix}"
learning="learning-service-test-${suffix}"
keycloak="keycloak-test-${suffix}"
test_root="$(mktemp -d)"

cleanup() {
  docker rm -f "${gateway}" "${missing_gateway}" "${content}" "${learning}" "${keycloak}" >/dev/null 2>&1 || true
  docker network rm "${network}" >/dev/null 2>&1 || true
  rm -rf -- "${test_root}"
}
trap cleanup EXIT

mkdir -p "${test_root}/certs/live/test.local" "${test_root}/certs/live/legacy.test.local" "${test_root}/empty-certs" "${test_root}/www"
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=test.local \
  -keyout "${test_root}/certs/live/test.local/privkey.pem" \
  -out "${test_root}/certs/live/test.local/fullchain.pem" >/dev/null 2>&1
openssl req -x509 -newkey rsa:2048 -nodes -days 1 -subj /CN=legacy.test.local \
  -addext 'subjectAltName=DNS:legacy.test.local' \
  -keyout "${test_root}/certs/live/legacy.test.local/privkey.pem" \
  -out "${test_root}/certs/live/legacy.test.local/fullchain.pem" >/dev/null 2>&1

docker network create "${network}" >/dev/null
docker run -d --name "${content}" --network "${network}" --network-alias content-service nginx:alpine >/dev/null
docker run -d --name "${learning}" --network "${network}" --network-alias learning-service nginx:alpine >/dev/null
docker run -d --name "${keycloak}" --network "${network}" --network-alias keycloak nginx:alpine >/dev/null

gateway_args=(
  --network "${network}"
  --cap-drop ALL
  --cap-add SETGID
  --cap-add SETUID
  --security-opt no-new-privileges
  -e API_DOMAIN=test.local
  -e LEGACY_API_DOMAIN=legacy.test.local
  -e 'NGINX_ENVSUBST_FILTER=^(API_DOMAIN|LEGACY_API_DOMAIN)$'
  -v "${test_root}/www:/var/www/certbot:ro"
  -v "${PWD}/infrastructure/gateway/hosted.conf.template:/etc/nginx/templates/default.conf.template:ro"
)
docker run -d --name "${gateway}" -p 127.0.0.1::8443 "${gateway_args[@]}" \
  -v "${test_root}/certs:/etc/letsencrypt:ro" "${image}" >/dev/null

for _ in {1..60}; do
  gateway_health="$(docker inspect -f '{{.State.Health.Status}}' "${gateway}")"
  [[ "${gateway_health}" == healthy || "${gateway_health}" == unhealthy ]] && break
  sleep 1
done
if [[ "$(docker inspect -f '{{.State.Health.Status}}' "${gateway}")" != healthy ]]; then
  docker logs "${gateway}" >&2
  docker inspect -f '{{json .State.Health}}' "${gateway}" >&2
  exit 1
fi
docker exec --user 101:101 "${gateway}" nginx -t
listeners="$(docker exec "${gateway}" netstat -lnt)"
grep -qE '0\.0\.0\.0:8080[[:space:]]' <<<"${listeners}"
grep -qE '0\.0\.0\.0:8443[[:space:]]' <<<"${listeners}"
[[ "$(docker exec "${gateway}" wget --no-check-certificate -q -O - https://127.0.0.1:8443/healthz)" == \
  '{"status":"UP","mode":"hosted-https"}' ]]
primary_subject="$(openssl s_client -connect 127.0.0.1:"$(docker port "${gateway}" 8443/tcp | sed 's/.*://')" -servername test.local </dev/null 2>/dev/null | openssl x509 -noout -subject)"
legacy_subject="$(openssl s_client -connect 127.0.0.1:"$(docker port "${gateway}" 8443/tcp | sed 's/.*://')" -servername legacy.test.local </dev/null 2>/dev/null | openssl x509 -noout -subject)"
[[ "${primary_subject}" == *CN\ =\ test.local* ]]
[[ "${legacy_subject}" == *CN\ =\ legacy.test.local* ]]

set +e
docker run --name "${missing_gateway}" "${gateway_args[@]}" \
  -v "${test_root}/empty-certs:/etc/letsencrypt:ro" "${image}" >/dev/null 2>&1
missing_status=$?
set -e
[[ "${missing_status}" -ne 0 ]] || {
  printf 'Gateway unexpectedly started without its hosted certificate.\n' >&2
  exit 1
}

printf 'Hosted gateway runtime selection tests passed.\n'
