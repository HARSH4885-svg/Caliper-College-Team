# CodeSentinel - what is left before it is deploy-ready

Date: 2026-11-10. Written from the repository state at the last commit plus a web check of free-tier terms (listed in "Sources"). Items marked **Not verified** were not tested in this project.

## 1. Where the project stands

| Level | Status |
|---|---|
| Runs on one developer machine, demo + all 150 automated tests passing | **Yes** (executed) |
| Suitable for a supervised classroom/viva demo on a laptop or lab PC | **Yes**, if run as in the README |
| Suitable for real students over the internet | **No** - the blockers in section 2 must be closed first |

The app is deliberately "localhost-only": it binds to `127.0.0.1`, the UI is served by the Vite *development* server, there is no TLS, and the judge assumes a Docker daemon on the same machine. Those are good defaults for development and the main work for deployment.

## 2. Blockers (must be done before any public/real use)

**Update 2026-11-10 (branch `deploy-ready`):** B2 (production UI serving + security headers), B4 (per-IP rate limit; lockout still in memory), B6 (backup script, restore drill documented, not yet run on a server), B7 (CI workflow, systemd unit, installer, no Docker image for the backend) and B8 (production env template, generated secrets) are addressed in code/scripts - see `DEPLOYMENT.md` for what was verified. B1 is addressed by the Caddy/tunnel setup but unverified on a real host. **Still open:** B3, B5, B9, B10.

| # | Gap | Why it matters | What to do |
|---|---|---|---|
| B1 | **No HTTPS / reverse proxy.** Passwords and JWTs would cross the network in clear text. | Credentials theft | Put Caddy (free, automatic TLS) or nginx in front; terminate TLS; set `SERVER_ADDRESS` back to localhost behind it. |
| B2 | **Frontend is served by the Vite dev server only.** No production serving. | Dev server is not for production | `npm run build` (already works) and serve `frontend/dist` from the reverse proxy, with `/api` proxied to the backend (same origin, so no CORS). Add security headers (CSP, `X-Content-Type-Options`, frame denial) at the proxy - **not currently set or tested**. |
| B3 | **Judge needs a Docker daemon on the worker host; giving the app access to Docker is root-equivalent on that host.** | If the backend were compromised, so is the machine | Run the judge worker on a dedicated VM/user with nothing else on it (or rootless Docker), keep the web process and DB separate, never expose the Docker socket to the network. Containers still share the host kernel (documented limitation). |
| B4 | **No rate limiting by IP; lockout is in-memory (per account, lost on restart).** Registration, login and upload are open to abuse. | Brute force / resource exhaustion | Proxy-level rate limits (Caddy/nginx), `REGISTRATION_ENABLED=false` unless a course needs open sign-up, per-user upload throttling. |
| B5 | **JWT cannot be revoked; it lives in `sessionStorage`** (readable by any XSS). | Stolen token valid until expiry (default 480 min) | Shorten `JWT_TTL_MINUTES`, add a token-version column checked per request, or move to HttpOnly cookies + CSRF protection. Needs code + tests. |
| B6 | **No backups.** Postgres data and `./storage` (submissions, hidden tests) live on one disk. | One failure loses all work | Nightly `pg_dump` + copy of `storage/` to a second location; do one restore drill. Not set up. |
| B7 | **No deployment packaging.** No Dockerfile for backend/frontend, `compose.yaml` only starts PostgreSQL, no CI, no systemd unit. | Deployment is manual and unrepeatable | Add a backend Dockerfile (or systemd service), a production compose file, and a GitHub Actions workflow running `mvn test` and `npm test`. |
| B8 | **Secrets and data hygiene:** demo seed uses public credentials (only with `SEED_DEMO=true`); real `.env` must be created per server. | Known-password accounts | Never set `SEED_DEMO` in production; generate `JWT_SECRET` and DB password per environment; restrict file permissions on `.env`. |
| B9 | **Privacy/legal not addressed.** The system stores student names, emails, code and similarity evidence. There is no account deletion, retention schedule or data-export for students, and no consent text. | Institutional/legal compliance (e.g. IIITA policy; India's DPDP Act - **not legally reviewed here**) | Get the instructor/institution's approval; define retention and deletion; add a privacy notice. Needs a decision by you/your faculty, then code for deletion. |
| B10 | **No password reset or change-password flow** (no email service). | Locked-out users need an admin | Add admin-initiated reset (no email needed) or a free SMTP provider flow. |

## 3. Should-do (quality and operations)

- **Load and capacity are unmeasured.** Each worker process judges one submission at a time (a sequential drain loop); throughput under a class-wide deadline rush is **Not verified**. Measure with ~50 submissions at once; run extra `worker` profile processes if needed (the queue is already multi-worker safe).
- **Architecture:** all testing was on x86_64 Linux. ARM (e.g. Oracle Ampere) is **Not verified**; the runner images are built from local Dockerfiles, so they probably build, but test them.
- **Monitoring:** only `/actuator/health`; no metrics, alerting or log shipping. Use free uptime pings and `journalctl`/log rotation.
- **Security assurance:** no penetration test, no dependency vulnerability scan (try OWASP Dependency-Check or `npm audit`, both free), no formal threat model review beyond RMMM.
- **Accessibility:** no audit tool run (use axe/Lighthouse). **Browser E2E tests:** none checked in (Playwright is free).
- **Accuracy:** similarity thresholds validated only on synthetic data; run on real, anonymised past assignments with instructor review before trusting the triage ordering.
- **Requirements Scope Clarifications** (from the RTM): Incomplete Google Drive adapter stub removed (local storage verified), one language per assignment, no MIME sniffing, symlink entries in ZIPs written as plain files, provider/MIME metadata not stored, fingerprints recomputed each run, PDF/HTML/JSON export completed.
- **Docs:** a short operations runbook (start/stop, backup/restore, rotating secrets, adding an instructor) does not exist yet.

## 4. Free ways to run it (with honest limits)

The deciding constraint: **the judge needs a real Docker daemon next to the worker.** Most free "platform-as-a-service" tiers (which run your app inside their own container) do not let you start containers from your app, so the judge would report "unavailable". That leaves a free **virtual machine / your own computer**, with the other parts optionally split off.

### Option A - A machine you already control (college lab PC/server, spare laptop, home PC) - *most realistic for a student project*
- Cost: free. Run Docker + Postgres + backend + Caddy on it.
- To reach it from the internet without opening router ports: **Cloudflare Tunnel** (free) or only use it on the campus LAN.
- Limits: the machine must stay on; uptime depends on you and the network; your college may block tunnels or hosting on its network (**ask**); home IP/ISP issues; you are the sysadmin (patching, backups). Check Cloudflare's terms for your use.

### Option B - Oracle Cloud "Always Free" VM
- What the docs say: Always Free Ampere A1 compute, and as of a June 2026 change reported by InfoQ the allowance is **2 OCPU / 12 GB** (it was 4 / 24); older articles still show the old figure, so check Oracle's page when you sign up.
- Good fit: a real always-on Linux VM where Docker works - the only free option here that fits the whole architecture in one place.
- Limits: needs a credit/debit card at sign-up; regions often report "out of capacity" for the free ARM shapes; Oracle's **idle-instance reclamation** can stop VMs with very low CPU/network/memory use; if a trial account exceeds the Always Free size when the trial ends, **all** A1 instances are disabled and later deleted (stay within limits); ARM build is unverified for this project; free terms can change without much notice.

### Option C - Split deployment (free tiers for the parts that do not execute student code)
- **Frontend:** Cloudflare Pages (static; unlimited static requests, 500 builds/month on Free) or GitHub Pages/Netlify. Limits: some hosts restrict commercial use; you need `/api` to point at your backend (CORS setting `CORS_ORIGIN` or a proxy rule).
- **Database:** Neon free Postgres - per its docs: 0.5-1 GB storage per project (figures differ across its pages; the latest FAQ says 1 GB), 100 compute-hours/month per project, **compute auto-suspends after 5 minutes idle** (first request after idle is slower), hitting a limit suspends compute until the next month. Fine for a small class; storage of submissions stays on the worker host, not in the DB, but the DB holds test cases/results/audit.
- **Judge worker + API:** still need a Docker-capable host (Option A or B). So Option C reduces little unless the frontend/DB must live elsewhere.

### Not recommended
- Free PaaS tiers that sleep after inactivity or offer no Docker access for the app (judge cannot run; sleeping hosts also stop the queue worker).
- GitHub Codespaces / Colab-style environments as "hosting": they are development sessions, not stable public services.
- Running student code on any host without Docker - the app refuses by design.

### Free tools that close gaps
Caddy (TLS + reverse proxy + headers), Let's Encrypt (via Caddy), `ufw` firewall, `fail2ban`, `pg_dump` + cron, `restic`/`rclone` to cloud storage for backups (local filesystem storage is used by the app), UptimeRobot-style free pings, GitHub Actions free minutes for CI (limits apply, more for public repos), OWASP Dependency-Check, `npm audit`, Playwright, axe/Lighthouse.

## 5. Recommended plan (smallest path that is honest and safe)

1. **For your submission/viva:** run locally (README) - already ready.
2. **For a real class pilot (say one course, one semester):** Option A on a lab machine or Option B VM; Caddy TLS; `REGISTRATION_ENABLED` per need; blockers B1-B8 closed; B9 approved by your instructor; backup cron + one restore test.
3. Suggested order of work (rough effort is my estimate, not measured): B7 packaging and CI (0.5-1 day) -> B1/B2 proxy + production frontend (0.5 day) -> B6 backups (0.25 day) -> B4/B8 limits and secrets (0.5 day) -> B5 token revocation/cookies (1 day with tests) -> B10 admin reset (0.5 day) -> load test and ARM check (0.5 day) -> B9 policy/deletion (depends on approvals).

## 6. Deploy checklist
- [ ] TLS reachable, HTTP redirects to HTTPS, security headers present
- [ ] `SEED_DEMO` unset; strong `JWT_SECRET`/DB password; `.env` not in git (checked) and permissions 600
- [ ] Postgres not exposed publicly (bind localhost or firewall)
- [ ] Docker socket not exposed; worker on dedicated host/user
- [ ] Runner images built on the target machine; submit a test program in each language
- [ ] Hostile-program tests (fork bomb, network) re-run on the target
- [ ] Backup job running; restore tested
- [ ] Rate limits on login/register/upload
- [ ] Privacy notice + retention rule agreed
- [ ] Load test with a class-sized burst; judge queue drains
- [ ] Runbook written; an owner named for patching and incidents

## Sources (checked 2026-11-10; terms change - re-check before relying)
- Oracle: docs.oracle.com Always Free Resources and Free Tier pages; InfoQ, "Oracle Quietly Halves Free Tier Ampere A1 Compute Limits" (July 2026).
- Neon: neon.com Free plan limits FAQ and plans page (idle suspend after 5 minutes; storage/compute caps).
- Cloudflare: developers.cloudflare.com Pages limits (500 builds/month on Free; static requests unlimited per third-party summary).
