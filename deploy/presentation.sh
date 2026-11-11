#!/usr/bin/env bash
# Run CodeSentinel with demo data on THIS computer and get a public https link (Cloudflare quick tunnel: free, no account).
# Needs: Docker running, Java 21, Maven, Node 20+, and `cloudflared` (https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/downloads/).
# The link works only while this script runs and this computer is on. Demo accounts are public: see README. Do not use real student data.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { cp .env.example .env; sed -i "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$(openssl rand -hex 16)|; s|^JWT_SECRET=.*|JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')|" .env; }
set -a; . ./.env; set +a
docker compose up -d
./scripts/build-sandbox-images.sh
mvn -B -q -DskipTests package
(cd frontend && npm ci && VITE_DEMO=true npm run build)
export STATIC_DIR="$PWD/frontend/dist" SEED_DEMO=true FORWARD_HEADERS=framework
java -jar backend/target/codesentinel-backend-0.1.0.jar & APP=$!
trap 'kill $APP 2>/dev/null' EXIT
until curl -fs http://127.0.0.1:8080/actuator/health >/dev/null; do sleep 2; done
echo "Local: http://127.0.0.1:8080   (login: demo-instructor@demo.invalid / demo-password-1)"
exec cloudflared tunnel --url http://127.0.0.1:8080
