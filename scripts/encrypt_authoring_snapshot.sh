#!/usr/bin/env bash
set -Eeuo pipefail
SNAPSHOT_DIR="${1:-}"
OUTPUT="${2:-}"
[[ -d "${SNAPSHOT_DIR}" && -n "${OUTPUT}" ]] || { echo "Usage: $0 SNAPSHOT_DIR OUTPUT.age" >&2; exit 2; }
[[ -n "${AGE_PASSPHRASE:-}" ]] || { echo "AGE_PASSPHRASE must come from a protected environment" >&2; exit 2; }
command -v age >/dev/null
command -v expect >/dev/null
command -v python3 >/dev/null
[[ "$(basename "${SNAPSHOT_DIR}")" == authoring-snapshot ]]
work="$(mktemp -d /tmp/authoring-encryption.XXXXXX)"; chmod 700 "${work}"
cleanup(){ rm -rf -- "${work}"; }
trap cleanup EXIT
python3 scripts/authoring_snapshot.py verify --snapshot "${SNAPSHOT_DIR}" >/dev/null
COPYFILE_DISABLE=1 tar --no-xattrs -czf "${work}/snapshot.tar.gz" -C "$(dirname "${SNAPSHOT_DIR}")" authoring-snapshot
plaintext_sha="$(shasum -a 256 "${work}/snapshot.tar.gz"|awk '{print $1}')"
scripts/age_with_passphrase.exp encrypt "${work}/snapshot.tar.gz" "${OUTPUT}"
encrypted_sha="$(shasum -a 256 "${OUTPUT}"|awk '{print $1}')"
printf '{"plaintextSha256":"%s","encryptedSha256":"%s","bytes":%s}\n' "${plaintext_sha}" "${encrypted_sha}" "$(wc -c <"${OUTPUT}"|tr -d ' ')"
