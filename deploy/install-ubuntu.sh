#!/usr/bin/env bash
# One-shot installer for a fresh Ubuntu 22.04/24.04 VM (x86_64 or arm64), run as root:
#   git clone https://github.com/HARSH4885-svg/CodeSentinel /opt/codesentinel && cd /opt/codesentinel && sudo bash deploy/install-ubuntu.sh [your.domain.com]
# Without a domain it serves on http://localhost only (use a Cloudflare Tunnel, see docs/DEPLOYMENT.md).
set -euo pipefail
[ "$(id -u)" = 0 ] || { echo "run as root (sudo)"; exit 1; }
DOMAIN="${1:-}"; APP=/opt/codesentinel
cd "$APP"
export DEBIAN_FRONTEND=noninteractive
apt-get update -y
apt-get install -y ca-certificates curl gnupg openssl openjdk-21-jdk-headless maven docker.io docker-compose-v2 ufw nodejs npm || apt-get install -y ca-certificates curl gnupg openssl openjdk-21-jdk-headless maven docker.io docker-compose nodejs npm ufw
systemctl enable --now docker
id codesentinel >/dev/null 2>&1 || useradd --system --home "$APP" --shell /usr/sbin/nologin codesentinel
usermod -aG docker codesentinel
mkdir -p /etc/codesentinel /var/lib/codesentinel/storage /var/backups/codesentinel
if [ ! -f /etc/codesentinel/prod.env ]; then
  sed -e "s|POSTGRES_PASSWORD=__GENERATED__|POSTGRES_PASSWORD=$(openssl rand -hex 24)|" -e "s|JWT_SECRET=__GENERATED__|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" deploy/prod.env.example > /etc/codesentinel/prod.env
  [ -n "$DOMAIN" ] && sed -i "s|https://example.invalid|https://$DOMAIN|" /etc/codesentinel/prod.env
  read -rp "Instructor email for first login: " IE; read -rsp "Instructor password (min 10 chars): " IP; echo
  sed -i -e "s|^BOOTSTRAP_INSTRUCTOR_EMAIL=.*|BOOTSTRAP_INSTRUCTOR_EMAIL=$IE|" -e "s|^BOOTSTRAP_INSTRUCTOR_PASSWORD=.*|BOOTSTRAP_INSTRUCTOR_PASSWORD=$IP|" /etc/codesentinel/prod.env
  chmod 600 /etc/codesentinel/prod.env
fi
set -a; . /etc/codesentinel/prod.env; set +a
# database
docker compose --env-file /etc/codesentinel/prod.env -f compose.yaml up -d
# sandbox images + app build
./scripts/build-sandbox-images.sh
mvn -B -q -DskipTests package
(cd frontend && npm ci && npm run build)
chown -R codesentinel:codesentinel "$APP" /var/lib/codesentinel
install -m 644 deploy/codesentinel.service /etc/systemd/system/codesentinel.service
systemctl daemon-reload && systemctl enable --now codesentinel
# TLS reverse proxy (only when a domain was given)
if [ -n "$DOMAIN" ]; then
  apt-get install -y caddy || { echo "install caddy manually: https://caddyserver.com/docs/install"; exit 1; }
  SITE_ADDRESS="$DOMAIN" envsubst < deploy/Caddyfile > /etc/caddy/Caddyfile || sed "s|{\$SITE_ADDRESS:localhost}|$DOMAIN|" deploy/Caddyfile > /etc/caddy/Caddyfile
  systemctl restart caddy
  ufw allow OpenSSH && ufw allow 80,443/tcp && ufw --force enable
fi
echo "0 2 * * * root $APP/deploy/backup.sh" > /etc/cron.d/codesentinel-backup
sleep 15; curl -fsS http://127.0.0.1:8080/actuator/health && echo && echo "OK. Open https://${DOMAIN:-<your tunnel url>}"
