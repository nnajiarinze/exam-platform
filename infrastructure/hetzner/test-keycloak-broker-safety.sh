#!/usr/bin/env bash
set -Eeuo pipefail

root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"
harden="${root}/infrastructure/hetzner/harden-keycloak.sh"

grep -q 'firstBrokerLoginFlowAlias:"first broker login"' "${harden}"
grep -q 'providerId=="idp-auto-link"' "${harden}"
grep -q 'Unsafe automatic first-broker email linking is configured' "${harden}"
grep -q 'tokenExchangeAccountLinkingEnabled:"false"' "${harden}"
grep -q 'hideOnLogin:true' "${harden}"

if grep -q 'idp-auto-link.*create' "${harden}"; then
  echo 'Unsafe idp-auto-link provisioning detected' >&2
  exit 1
fi

printf 'Keycloak first-broker and link safety contract passed.\n'
