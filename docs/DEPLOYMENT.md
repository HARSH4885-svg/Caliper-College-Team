# Deploying Caliper

Caliper can be deployed either locally for demonstrations and evaluation, or on a dedicated server (Ubuntu 22.04/24.04 VM, AWS, GCP, or bare metal). Both deployment topologies place the complete application (Web UI, REST API, and Background Judge Worker) behind a single unified port (8080 by default), where the Spring Boot backend directly serves the pre-compiled frontend Single Page Application (`STATIC_DIR`).

---

## 1. What Has Been Verified

- **Full Local & CI Verification**:
  - Backend serving `frontend/dist` with client-side SPA routing (`/dashboard`, `/courses`, `/similarity`, `/workspace`).
  - Strict security headers (`Content-Security-Policy`, `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`).
  - Per-IP rate limiting returning HTTP `429 Too Many Requests` on abuse.
  - Multi-language sandboxed execution inside Docker runner containers across Linux and Windows.
  - Plagiarism dual-engine benchmark (100% precision, 100% recall on synthetic ground-truth).
  - 199 backend/engine/judge unit & integration tests passing (`mvn test`).
  - 39 frontend unit & component tests passing (`npm test -- --run`) — 238 passing tests total.
  - GitHub Actions CI workflow passing with green checks on Ubuntu and Windows matrix runners.

---

## 2. Deployment Option A: Local Demonstration & Viva

Ideal for presentations, demonstrations, or academic evaluations:

### Step 1: Ensure Prerequisites Are Installed
- Docker Engine / Docker Desktop
- Java JDK 21+
- Apache Maven 3.9+
- Node.js 20+

### Step 2: Build Sandbox Runner Images
```bash
./scripts/build-sandbox-images.sh
```
*(On Windows PowerShell: `.\scripts\build-sandbox-images.ps1`)*

### Step 3: Launch Local Demo
```bash
./deploy/presentation.sh
```
This builds both frontend and backend, sets `APP_SEED_DEMO=true`, initializes PostgreSQL (via Docker Compose), applies Flyway migrations, and starts the platform on `http://localhost:8080`.

Demo Accounts:
- **Instructor**: `instructor@example.test` / `Instructor123!`
- **Students**: `alice@example.test`, `bob@example.test`, `carol@example.test` / `Student123!`

---

## 3. Deployment Option B: Production Server (Ubuntu 22.04 / 24.04 LTS)

For institutional deployments hosting live courses:

### Step 1: Provision Server & Open Ports
Open inbound ports `22` (SSH), `80` (HTTP), and `443` (HTTPS).

### Step 2: Clone Repository & Execute Installer
```bash
git clone https://github.com/HARSH4885-svg/Caliper.git /opt/caliper
cd /opt/caliper
sudo bash deploy/install-ubuntu.sh your-institution-domain.edu
```
The installer script:
1. Provisions system dependencies (Java 21, Docker, PostgreSQL 16, Caddy web server).
2. Generates cryptographically random JWT secrets.
3. Builds the production runner containers (`codesentinel/runner-*`).
4. Compiles the backend and packages the production frontend.
5. Installs and enables the `caliper.service` systemd daemon.
6. Configures automated nightly database backups via cron.

---

## 4. Production Environment Configuration (`/etc/caliper/prod.env`)

```ini
# Application Secrets
JWT_SECRET=<64-char-cryptographically-random-string>
SERVER_PORT=8080

# Relational Database
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/caliper_prod
SPRING_DATASOURCE_USERNAME=caliper_app
SPRING_DATASOURCE_PASSWORD=<secure-database-password>

# File Storage
STORAGE_PROVIDER=local
STORAGE_ROOT=/var/lib/caliper/storage

# Container Sandbox Execution
CALIPER_DOCKER_BIN=docker
CALIPER_SANDBOX_RUNTIME=docker
CALIPER_JUDGE_WORK_ROOT=/var/lib/caliper/judge

# Production Security Flags
STATIC_DIR=/opt/caliper/frontend/dist
RATE_LIMIT_PER_MINUTE=60
REGISTRATION_ENABLED=false
APP_SEED_DEMO=false
```

---

## 5. Operations & Maintenance

### Service Management
```bash
# Check service status
sudo systemctl status caliper

# View live application and worker logs
sudo journalctl -u caliper -f

# Restart application
sudo systemctl restart caliper
```

### Health Inspection
```bash
curl http://127.0.0.1:8080/actuator/health
```

### Updating to Latest Release
```bash
cd /opt/caliper
git pull origin main
mvn clean package -DskipTests
cd frontend && npm install && npm run build && cd ..
sudo systemctl restart caliper
```

### Automated Backups
Automated database backups are created daily at 02:00 UTC and stored in `/var/backups/caliper/` with 14-day retention.
To trigger an immediate manual snapshot:
```bash
sudo bash deploy/backup.sh
```
