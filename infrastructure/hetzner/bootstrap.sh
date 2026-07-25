#!/usr/bin/env bash
set -Eeuo pipefail

[[ "${EUID}" -eq 0 ]] || { printf 'Run bootstrap.sh as root.\n' >&2; exit 1; }

DEPLOY_USER="${DEPLOY_USER:-citizenship}"
PLATFORM_ROOT="${PLATFORM_ROOT:-/opt/citizenship-platform}"
SSH_ALLOW_FROM="${SSH_ALLOW_FROM:-}"

apt-get update
DEBIAN_FRONTEND=noninteractive apt-get install -y \
  ca-certificates certbot curl docker.io docker-compose-v2 fail2ban git jq \
  postgresql-client age awscli ufw unattended-upgrades

if ! id "${DEPLOY_USER}" >/dev/null 2>&1; then
  useradd --create-home --shell /bin/bash "${DEPLOY_USER}"
fi
usermod -aG docker "${DEPLOY_USER}"

install -d -m 0750 -o "${DEPLOY_USER}" -g "${DEPLOY_USER}" \
  "${PLATFORM_ROOT}" "${PLATFORM_ROOT}/repo" "${PLATFORM_ROOT}/state" \
  "${PLATFORM_ROOT}/backups" /var/www/certbot

ufw default deny incoming
ufw default allow outgoing
if [[ -n "${SSH_ALLOW_FROM}" ]]; then
  ufw allow from "${SSH_ALLOW_FROM}" to any port 22 proto tcp
else
  ufw allow 22/tcp
  printf 'WARNING: SSH is open globally. Set SSH_ALLOW_FROM and rerun to restrict it.\n' >&2
fi
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

systemctl enable --now docker fail2ban unattended-upgrades

AUTHORIZED_KEYS="/home/${DEPLOY_USER}/.ssh/authorized_keys"
if [[ -s "${AUTHORIZED_KEYS}" ]]; then
  install -d -m 0755 /etc/ssh/sshd_config.d
  cat >/etc/ssh/sshd_config.d/99-citizenship-hardening.conf <<'EOF'
PasswordAuthentication no
KbdInteractiveAuthentication no
PermitRootLogin no
PubkeyAuthentication yes
EOF
  sshd -t
  systemctl reload ssh
else
  printf 'WARNING: SSH hardening was not enabled because %s has no key.\n' "${AUTHORIZED_KEYS}" >&2
fi

cat >/etc/systemd/system/citizenship-certbot-renew.service <<EOF
[Unit]
Description=Renew Svea Study TLS certificate and reload gateway
After=docker.service

[Service]
Type=oneshot
ExecStart=${PLATFORM_ROOT}/repo/infrastructure/hetzner/renew-tls.sh
EOF

cat >/etc/systemd/system/citizenship-certbot-renew.timer <<'EOF'
[Unit]
Description=Twice-daily Svea Study certificate renewal check

[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=3600
Persistent=true

[Install]
WantedBy=timers.target
EOF
systemctl daemon-reload
systemctl enable citizenship-certbot-renew.timer

printf 'Bootstrap complete. Add the deployment SSH key before relying on hardened SSH.\n'
printf 'Create %s/.env with mode 600 and provision TLS before the first deployment.\n' "${PLATFORM_ROOT}"
