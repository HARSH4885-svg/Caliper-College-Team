# Caliper — Security Architecture & Threat Model

This document specifies the security architecture, threat model, sandbox isolation boundaries, and data access controls implemented in Caliper.

---

## 1. Threat Model

Caliper executes untrusted user-submitted source code in multi-tenant academic environments. The application explicitly addresses the following threat vectors:

| Threat Vector | Potential Impact | Mitigations Implemented in Caliper |
| :--- | :--- | :--- |
| **Resource Exhaustion (Denial of Service)** | Infinite loops, fork bombs, memory allocation attacks causing host exhaustion. | Strict cgroups limits: `--cpus=1.0`, `--memory=256m`, `--pids-limit=64`. Double-layer timeouts (in-container `timeout` command + host watchdog process). |
| **Host Filesystem Tampering** | Malicious code attempting to write outside the sandbox directory or modify system libraries. | Read-only container root filesystem (`--read-only`), read-only testcase mounts (`:ro`), non-executable tmpfs (`/tmp:rw,noexec,nosuid`). |
| **Sandbox Breakout & Privilege Escalation** | Kernel exploit attempting to escape container namespaces or gain root on the host. | Non-root container execution (`--user 65534:65534`), dropping all Linux capabilities (`--cap-drop ALL`), forbidding privilege escalation (`--security-opt no-new-privileges:true`). Optional gVisor (`runsc`) user-space kernel runtime. |
| **Unauthorized Network Egress** | Reverse shells, port scanning, botnet participation, or reaching cloud metadata endpoints (`169.254.169.254`). | Complete network namespace isolation (`--network none`). Submissions cannot make any socket connections. |
| **Malicious Archive Uploads (Zip Bombs & Traversal)** | Zip slip path traversal (`../../etc/passwd`) or decompression bombs designed to saturate disk space. | `SafeZipExtractor` enforcing path canonicalization guards, maximum uncompressed size limits (50 MB), file count caps (250 files), and compression ratio bounds. |
| **Insecure Direct Object Reference (IDOR)** | Students accessing submissions, evaluations, or similarity dossiers belonging to other students or courses. | Server-side `Access` control service validating user roles (`ADMIN`, `INSTRUCTOR`, `STUDENT`) and verifying ownership on every sensitive entity. |
| **Output Flood & Memory Overflow** | Submissions printing gigabytes of output to crash the host runner or browser. | Hard output excerpt caps (64 KB maximum captured stdout/stderr) with automatic truncation and notification. |
| **Cross-Site Scripting (XSS) in Dossiers** | Malicious student source code containing `<script>` tags executed in instructor audit reports. | Contextual HTML escaping in `HtmlDossierExporter` and programmatic document model rendering in OpenPDF `PdfReportService`. |

---

## 2. Sandbox Architecture & Hardening

Caliper executes student code inside isolated Linux container environments using `DockerRunner` and pluggable execution providers.

```
┌─────────────────────────────────────────────────────────────┐
│                       Host System                          │
│                                                             │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │  Backend / JobWorker  │ ────> │  Docker Daemon Socket │  │
│  └───────────────────────┘       └───────────┬───────────┘  │
│                                              │              │
│                          Spawns Container    ▼              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │ Container Sandbox (Isolated Namespace & Cgroups)      │  │
│  │                                                       │  │
│  │  • Network: NONE (unroutable, no egress)              │  │
│  │  • Filesystem: Read-Only (except /tmp tmpfs)          │  │
│  │  • UID / GID: 65534:65534 (nobody)                    │  │
│  │  • Capabilities: ALL DROPPED                          │  │
│  │  • No New Privileges: TRUE                            │  │
│  │  • PIDs Limit: 64 processes max                       │  │
│  │  • CPU Limit: 1.0 core                                │  │
│  │  • RAM Limit: 256 MB (hard capped)                    │  │
│  │  • Execution Timeout: Wall-clock watchdog             │  │
│  │                                                       │  │
│  │  ┌─────────────────────────────────────────────────┐  │  │
│  │  │ Runtime Option: Standard runc OR gVisor (runsc) │  │  │
│  │  └─────────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

### Container Invocation Security Flags
Every container spawned by Caliper is configured with:
```bash
docker run --rm \
  --network none \
  --read-only \
  --cap-drop ALL \
  --security-opt no-new-privileges:true \
  --user 65534:65534 \
  --cpus 1.0 \
  --memory 256m \
  --memory-swap 256m \
  --pids-limit 64 \
  --tmpfs /tmp:rw,noexec,nosuid,size=64m \
  -v <host_work_dir>:/work:ro \
  <runner_image>
```

### Runtime Environments
- **`docker` (Default)**: Leverages standard Linux `runc` cgroups v2 and namespace isolation. Suitable for trusted and semi-trusted academic environments.
- **`gvisor`**: Pluggable provider invoking Google's `runsc` OCI runtime. Intercepts application system calls in user space, providing an architectural defense-in-depth boundary against host kernel privilege escalation. Configured via `CALIPER_SANDBOX_RUNTIME=gvisor`.

---

## 3. Storage & Upload Validation

All file ingestion points enforce strict server-side validation:
1. **Local Storage (`LocalFileStorageProvider`)**:
   - Storage keys are validated against strict regex (`^[a-zA-Z0-9_-]+(/[a-zA-Z0-9_-]+)*$`).
   - Relative path navigation (`..`, `.`, leading `/`) is immediately rejected with `StorageException`.
   - Files are written atomically using temporary files and verified with SHA-256 integrity hashes before directory moves.
2. **Archive Ingestion (`SafeZipExtractor`)**:
   - Rejects entries with path traversal sequences.
   - Rejects directory entries or symlinks pointing outside the target extraction path.
   - Enforces decompression quotas: maximum 50 MB total uncompressed volume, maximum 250 individual files, and checks compression ratios to prevent Zip Bombs.

---

## 4. Role-Based Access Control (RBAC) & Data Isolation

Caliper defines three distinct principal roles:
1. **`ADMIN`**: System administration, course management, and server metrics.
2. **`INSTRUCTOR`**: Course authoring, assignment management, gradebook access, and plagiarism investigations.
3. **`STUDENT`**: Course viewing, testcase execution in workspace, assignment submissions, and score inspection.

### Service-Level Authorization Enforcement
Authorization is enforced server-side in the `Access` service and repository layers:
- `requireCourseStaff(courseId, principal)`: Ensures only lead instructors and assigned co-instructors/TAs can modify courses, author assignments, or grade work.
- `requireAssignmentOwner(assignmentId, principal)`: Enforces ownership boundaries for plagiarism pair inspection and manual score adjustments.
- `requireSubmissionOwnerOrStaff(submissionId, principal)`: Ensures students can only retrieve their own submissions, code files, and feedback.
- `requireJobOwnerOrStaff(jobId, principal)`: Prevents unauthorized inspection of live worker jobs.

---

## 5. Known Operational Precautions & Realistic Limitations

1. **Host Kernel Sharing in Default Container Mode**: Standard Docker containers share the host Linux kernel. While namespaces, cgroups, dropped capabilities, and non-root execution prevent standard breakout vectors, institutions handling untrusted external code submissions are advised to enable the `gvisor` runtime provider (`CALIPER_SANDBOX_RUNTIME=gvisor`).
2. **Local Demo Credentials**: The demo environment includes well-known synthetic credentials (`demo-instructor@demo.invalid`, `student1@demo.invalid`). **These credentials must never be deployed to production environments.** Production deployments must configure strong JWT secrets and unique instructor passwords via environment variables.
3. **No Automatic Academic Penalties**: Similarity detection scores and similarity triage states are investigative evidence for human academic review. Caliper never applies automatic penalties based solely on statistical score thresholds.
