# CodeSentinel — Implementation Log

This log records what was actually done, in order, with real command outcomes. Nothing here is back-filled:
the whole prototype was implemented in a single working session starting 2026-11-09 (IST), as sequential increments.

## Environment (measured, Increment 0)

| Item | Value |
|---|---|
| OS | Linux x86_64 (cloud sandbox VM), 2 vCPU, ~8 GB RAM |
| Java / Maven | OpenJDK 21.0.12 / Maven 3.9.11 |
| Node / npm | 22.23.4 / 10.9.9 |
| Docker | Engine 29.9.2 (daemon had to be started manually: `dockerd`), Compose v5.7.0 |
| Git | 2.43.0 |
| Pre-existing project files | none (empty working directory) — no conflicts |

## Increment 0 — Skeleton (done)

- Created multi-module Maven build: `engine` (plagiarism, pure Java), `judge` (Docker runner, pure Java), `backend` (Spring Boot 3.3.5).
  Separate modules make the engine/judge decoupling (NFR-MAINT-02) a compile-time fact: neither depends on the other.
- Added `.env.example`, `.gitignore`, `compose.yaml` (PostgreSQL 16, bound to 127.0.0.1), Vite + React + TypeScript frontend skeleton.
- Verified: `docker compose up -d` → postgres healthy; `mvn install -DskipTests` succeeds;
  backend starts in ~6 s, `GET /actuator/health` → `{"status":"UP"}` with Flyway connected to PostgreSQL 16.15;
  `npm run build` succeeds.

## Increment 1 — Plagiarism engine (done, verified)

Module `engine` (pure Java 21; no Spring/JDBC/HTTP/process APIs). Frozen contract: `api.PlagiarismEngine` +
`api.AnalysisRequest/EngineConfig` + `reporting.*` records.

Implemented: tokenizers for C, C++, Java, JavaScript (shared `CFamilyTokenizer` state machine) and Python
(`PythonTokenizer`, indentation-aware); `LexerFactory`; `TokenNormalizer` (identifier modes, literal modes, common-include
policy); `StarterCodeFilter` (verified-window suppression); FNV-1a token hash + Rabin–Karp rolling k-gram hash
(`KGramHasher`, mod 2^64 + SplitMix64 finalizer); `Winnower` (rightmost-minimum ties); `InvertedIndex`; `Dice`;
`GreedyStringTiling` (RKR-GST); `PairAnalyzer` (rarity weighting, fragments with line mapping, triage states);
`SimilarityEngine` (orchestration); `EngineCli` + `QualityEvaluator`.

Decisions made along the way (all measured, see `docs/results/`):
1. **Identifier numbering scope.** Strict file-wide first-occurrence numbering (FR-PLAG-04 literal reading) caught only
   3/5 deliberate copies, because renaming/adding one variable shifts every later `ID_n`. Default is now first-occurrence
   numbering that restarts per top-level unit (`FIRST_OCCURRENCE_UNIT`); strict (`FIRST_OCCURRENCE_FILE`) and `BLIND` remain
   selectable. On the 17-submission synthetic set: UNIT = 5/5 surfaced, 0 false positives.
2. **Candidate cutoff** default lowered from the spec's ~0.50 to 0.30: a heavily padded copy had Dice 0.43 and was never
   verified at 0.50. The matrix showed no precision cost. Still only a retrieval setting, not an accusation threshold.
3. **Rarity gate** (weighted Dice >= cutoff/2) keeps pairs that share only common fingerprints away from the expensive stage.
4. First benchmark run (homogeneous generator) took 9.4 s and found nothing; this exposed that my generator, not the detector,
   was unrealistic. Replaced by a structurally diverse generator; the homogeneous one is kept and reported as a labelled worst case.
   GST scan table rewritten with primitive arrays and pair analysis made parallel.

Verification: `mvn -pl engine test` → 71 tests, 0 failures (lexers 19, normalization/hashing 13, algorithms 16,
scenarios 20, quality 1, benchmark 2). Measured benchmark (2 vCPU sandbox): realistic workload 599 ms, homogeneous
worst case 3376 ms (target < 5000 ms). CLI verified on the fixtures.

## Increment 2 (part 1) - Docker-sandboxed judge
- Added `judge` module: language adapters (C, C++, Java, Python, JavaScript), `DockerRunner` (no network, read-only rootfs, cap-drop ALL, no-new-privileges, pids/memory/cpu limits, non-root uid, single mount of the job dir, tmpfs /tmp noexec), verdict classifier, output comparator.
- Runner images built locally via `scripts/build-sandbox-images.sh`.
- Verified (executed this session): `mvn -pl judge test` -> 40 tests, 0 failures: 18 unit tests (no Docker) + 22 real-container tests (AC for 5 languages, WA, CE x3, RE x2, TLE, MLE, fork bomb, output flood, network blocked, read-only fs, non-root, no docker.sock, hidden input not on disk, CRLF handling).
- Test-defect fixes found while running: two of my own container tests were wrong (searched for a marker that appeared in the source itself; probed `/proc/1/root`, which is the container's own root); corrected the tests, not the sandbox.
- Behaviour change: unsupported language is now `IE` (judge limitation), not `CE` (student fault).
- If Docker or the images are missing, the container tests are skipped by Assumptions and the judge reports itself unavailable; no host execution path exists.
- Not yet done in Increment 2: storage provider, safe ZIP extraction, separate worker.

## Increment 2 (part 2) - storage and safe archive extraction
- `FileStorageProvider` port, `LocalFileStorageProvider` (validated keys, atomic write, SHA-256), `GoogleDriveStorageProvider` (NOT implemented: fails with a clear error; app never depends on it).
- `SafeZipExtractor`: streaming, byte-budgeted extraction; rejects traversal, absolute/backslash/drive paths, nested archives, duplicates (case-insensitive), depth/entry/size limits, shell-metacharacter names; removes partial output on rejection.
- Verified: `StorageTest` 11 tests, 0 failures (executed this session).
- Known limit: `ZipInputStream` exposes symlink entries as ordinary files whose content is the link target text, so they are written as plain files and never followed. Not separately tested with a real symlink archive.
- The runner worker (persisted job queue consumer) is deferred to Increment 3 because it needs the database schema.

## Increment 3 (part 1) - backend: schema, security, APIs, worker, plagiarism orchestration
- Flyway `V1__core_schema.sql` (17 tables incl. users, courses, enrollments, assignments, assignment_files, test_cases, submissions, submission_files, evaluation_jobs/results, testcase_results, plagiarism_runs, similarity_pairs, matched_fragments, plagiarism_reviews, audit_logs). Canonical pair order enforced by a CHECK constraint. Deviation: no separate `fingerprints` or `storage_objects` tables - the engine is fast enough to recompute fingerprints per run, and file hashes live on the file rows.
- Spring Security + JWT (stateless, role re-read from DB per request), BCrypt, login lockout (5 failures / 5 min, in memory), instructor bootstrap from env, student registration only with a course enrollment code. Logout is client-side token discard (documented limitation).
- REST API for auth, courses, assignments (+starter/reference files, test cases), submissions (upload, ZIP, status, file view, evaluation, rejudge), dashboards, plagiarism (run, list, detail, review). DTO allowlists; hidden tests, reference files and other students' data never serialised for students.
- `JobWorker`: persisted queue consumer (`FOR UPDATE SKIP LOCKED`, retry, stuck-job recovery), one job at a time; `worker` profile runs it without a web server (profile file present, not yet exercised standalone).
- Verified (executed this session): `AcceptanceTest` 13 tests, 0 failures, against real PostgreSQL and the real Docker judge: role/ownership checks, hidden-data absence in student JSON, hostile uploads rejected, AC/WA judged end to end, cross-student access 404, plagiarism triage on a synthetic set, review persistence across re-run, audit trail.
- Measured in that scenario: the renamed/reordered copy pair scored 0.783 -> NEEDS_REVIEW (below the 0.80 HIGH_PRIORITY threshold, so not escalated); 4 independent solutions produced no candidate pair. One synthetic scenario - not a general accuracy claim.
- Test-quality fix: one assertion originally accepted either 403 or 404; tightened to 403.

## Increment 3 (part 2) - demo seed and React UI
- `DemoSeedRunner` (SEED_DEMO=true): labelled-synthetic instructor, 8 students, one course/assignment, 8 submissions (renamed copy pair, 4 independent solutions, 1 wrong output, 1 syntax error). Fixed public dev credentials, exist only while the flag is on.
- React UI: login/student sign-up, role dashboards (all numbers from API), courses, assignment (student: statement/samples/starter/upload/attempts with polling; instructor: tests editor, attachments, publish, submissions with verdict filter, rejudge), evaluation page, similarity list, pair page with side-by-side viewer (highlighted fragments, jump-to-fragment, synchronised scrolling), review actions with history. Verdict and similarity states use different badges; similarity never red.
- Verified (executed): live app on the real DB judged the 8 seeded submissions in real containers (6 AC, 1 WA, 1 CE); plagiarism run gave one candidate pair (Student 1 vs 2, NEEDS_REVIEW, 0.783); 2 submissions flagged as insufficient evidence (too short). `tsc` clean, `vite build` OK, vitest 10 tests pass. Playwright (Chromium) walk-through as instructor and as a student: no console errors; student cannot see hidden test text, is redirected from /pairs/1, and gets "Not found" for another student's submission.
- Responsive fix found by screenshot: horizontal overflow at 420px width on several pages; fixed (grid minmax, min-width, code wrapping); re-measured scrollWidth == viewport on 8 pages.
- Not done / limits: no automated browser (E2E) test suite checked in - the Playwright walk-through was ad hoc; accessibility checked only by semantic markup/labels, no audit tool run; (update) the standalone `worker` profile was run: it started without Tomcat, and consumed a job queued by a web process that had its worker disabled (sub 3 QUEUED -> DONE/AC). Found and fixed a bug doing so: the PasswordEncoder bean was web-only (moved to CryptoConfig). Retry and stuck-job recovery paths have no automated test.

## Final increment - documentation
- Wrote README, SRS, ARCHITECTURE, DESIGN_DIAGRAMS (Mermaid, not rendered/checked in a Mermaid tool), RTM, TEST_PLAN_AND_RESULTS, RMMM, PROJECT_PLAN, VIVA_NOTES, API from the measured results above.
- Totals from the final full `mvn test` + vitest: engine 71, judge 40, backend 24, frontend 10 = 145 tests, 0 failures, 0 skipped.
- Size: production Java 5,464 physical lines + TS/TSX 831 = 6.295 KLOC (physical, not SLOC); COCOMO 16.56 PM theoretical vs 8.94 PM for the spec's 3.5 KLOC planning figure. Cyclomatic V(G) of `VerdictClassifier.classify` = 9 (13 counting `||`), by hand.
- RTM honesty: Done/Partial/Not done per requirement; Not done = FR-STOR-04 (Drive), FR-REPORT-04 (export); Partial includes FR-COURSE-01 (archive), FR-ASSIGN-01/02/06/07, FR-SUB-02/03/07, FR-PLAG-12, FR-STOR-03, FR-AUDIT-01, NFR-REL-02 (untested), NFR-SEC-04, NFR-ACC-01, NFR-USE-01.
- Mermaid diagrams were written by hand and not validated with a renderer.

## Follow-up day (2026-11-09): closing requirement gaps
- Added course archive/restore and assignment unpublish/archive (Flyway V2), with UI buttons and acceptance tests.
- Added dedicated tests: past-deadline rejection; worker retry to FAILED/IE; stuck-job recovery.
- Added instructor-only evidence export (HTML per pair, CSV per assignment) with a UI download button; test checks 403/404/401, neutral wording and absence of hidden-test material. CSV cells are quoted and formula-neutralised.
- Full re-run: engine 71, judge 40, backend 29, frontend 10 = 150 tests, 0 failures (executed this session).
- Still not done: Google Drive adapter (stub), FR-STOR-03 provider/MIME metadata, per-assignment multi-language, PDF export, checked-in E2E suite, accessibility audit, Mermaid validation.

## Deploy-readiness branch (2026-11-10)
- Backend can serve the built UI (`app.static-dir`), with SPA fallback, public GET outside /api, CSP/Referrer/HSTS headers, and a 404 (not 500) for missing static files.
- Per-IP sliding-window rate limit on login/register/upload (429, default 30/min). Unit tests (3) + acceptance test for static serving/headers (order 19).
- Added deploy/ (installer, systemd unit, Caddyfile, backup, presentation script, prod env template) and GitHub Actions CI. Executed: production-mode server with real Chromium login + report download, 0 console errors; 33rd rapid login returned 429. Not executed: installer on a real VM, Caddy, cloudflared, GitHub CI run.
- Fixed while testing: GET / returned 500 (resource resolver), fixed with a view forward; NoResourceFoundException now maps to 404.

## Judge reliability pass (after the first live demo)
- Docker failing to start a container (exit before the container exists) is now an internal "Judge problem" with Docker's message, not a student-facing compile error.
- UTF-8 byte-order marks are stripped from Python/Java/JS/C/C++ sources before judging (Windows editors add them; javac and Python's syntax check rejected them).
- Start-up judge self-test for all five languages, logged per language.
- ZIP uploads silently skip build junk (`__pycache__`, `*.pyc`, `*.class`, `*.o`, `node_modules`, `.git`, `.idea`, `.vscode`, venvs) instead of rejecting the whole upload.
- Students join a class with the code alone (`POST /api/courses/join`); the separate "course number" field was removed.
- CI: `build-sandbox-images.sh` no longer fails on its cosmetic image listing.
- Browser-tested end to end (Playwright): register, join, create course/assignment/tests, publish, submit AC/WA/CE, empty/oversized/wrong-type uploads, mobile layout. 17 language/zip variants through the real API, plus 10 ZIP layouts (macOS junk, folders, multi-file).

## Brand and capability pass
- New mark (pennant + lookout lens), bunting masthead, chart-paper dot grid, dark theme.
- Instructor: Insights tab (verdict bar, score histogram, per-test pass rates), course Gradebook with CSV, similarity Network view.
- Student: in-browser "Write code" editor (submitted as the language's entry file), visible evaluation progress.
- Backend: `InsightsController` (analytics, gradebook, CSV). Acceptance test 21; 4 chart tests in vitest.

## Product evolution pass (2026-11-10)
- Gaps found in the audit: assignments could not be edited from the UI although the API supports it; no password change or profile; no announcements; no search, sorting or bulk actions; students could not see their own progress in a class; unknown URLs silently redirected Home; the theme toggle kept per-component state.
- Instructor: edit assignment details and extend the deadline (+1 day / 3 days / 1 week); duplicate an assignment; submissions search, sort, "latest attempt only", CSV export, bulk re-run by verdict (e.g. Judge problems only); course announcements (post, pin, delete); class-work filter.
- Student: per-assignment result chips and an "accepted of N" progress bar on each class; announcements.
- Everyone: command palette (Ctrl/Cmd+K or `/`) with server-side search, `?` shortcuts dialog, `g h` / `g c` / `g a` navigation, account page (display name, password, theme: match device / light / dark), proper "not found" page, shared theme store that follows the OS setting.
- Backend: Flyway V3 (announcements); `AccountController`, `AnnouncementController`, `SearchController`, `BulkSubmissionController`, `/my-progress` and `/duplicate`; password endpoint added to the rate limiter. Acceptance tests 22-25, 11 new vitest tests.
- Fixed on the way: unselected radio cards were white-on-white in dark mode (also affected the similarity decision picker).
- Not done: no password reset by email (needs a mail service), tokens still cannot be revoked server-side, no automated accessibility audit, announcements are plain text.

## Classroom and assessments (PR 1 of the LMS expansion, 2026-11-10)
- Flyway V4: course description/subject/category/dates, `course_staff` + SQL `teaches()`, assessment settings on assignments, `questions`, `question_bank`, `quiz_attempts`, `question_responses`, coding override/feedback.
- `QuizGrader` (pure, unit-tested) and `QuizService`: multi-question assignments with 8 question types, attempts with timer/attempt limit/late policy/availability window, autosave, auto + manual marking, re-marking when the key changes, release modes, return for revision.
- Question bank (private per instructor) with import and random sets. Course overview, analytics, gradebook with marks/late/missing, student "My grades". Co-instructors/TAs, bulk enrolment, course clone and safe delete.
- Demo campus (`DemoCatalogSeeder`, SEED_DEMO only, one transaction): 12 courses, 5 instructors, 72 students, 8 coding problems with real solutions and flawed variants queued for the real judge (about 230 jobs, a few minutes to drain on first start), 14 quizzes answered and marked through `QuizService`. All rows flagged synthetic, all emails `.invalid`. The eight original demo students and the demo instructor keep their logins but get realistic names.
- Not done in this PR: coding questions inside quizzes, the full-size IDE with "Run" (PR 2), discussions/notifications/calendar/materials (PR 3), new languages (C#, Go, Rust, SQL need new sandbox images), AI features (no AI APIs by project rule).

## Coding workspace (PR 2 of the LMS expansion)
- `CodeRunService` + `RunController`: interactive runs through the existing sandboxed judge (samples, custom input, staff-only all tests), bounded by a 2-slot semaphore and a per-user rate limit, audited as CODE_RUN.
- Workspace page (`/assignments/:id/code`): CodeMirror 6 (syntax highlighting, bracket matching, completion, search, folding), draggable and keyboard-resizable split panes, problem/submission-history tabs, console with test table, custom input, per-test re-run, draft autosave, font size, editor theme, tidy whitespace, reset, full screen, Ctrl+Enter / Ctrl+Shift+Enter. The editor is lazy-loaded (main bundle 375 kB, editor chunk 692 kB).
- Code questions in quizzes (V5, `QuizCodeMarker`), with run-examples inside the attempt and judged results in the review.
- Not done: language switching per assignment (each assignment has one language), C#/Go/Rust/SQL runners, memory usage reporting, true code formatting (only whitespace tidy).

## Materials, discussions, notifications, calendar (PR 3 of the LMS expansion)
- Flyway V6; `MaterialsController` (links, notes, files with safe preview types, modules, reorder), `DiscussionController` (threads, replies, staff answers, endorse, pin/lock/hide, reports), `NotificationService`/`NotificationController` (event hooks, deduplication, per-kind mutes, deadline reminders), `CalendarController`.
- UI: course Materials and Discussions tabs, thread page, notification bell and page, Calendar (month/week/agenda, course filter, event dialog). CSP now allows `blob:` frames and images for file previews.
- Demo: `DemoCommunitySeeder` adds modules, discussion threads and a few notifications to the demo campus (runs once).
- Not done: email delivery (no mail service), announcement scheduling and comments, groups/peer review, certificates, an administrator role, AI features.
