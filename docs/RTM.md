# Requirements Traceability Matrix

Status key: **Done** = implemented and exercised by a test run in this project; **Partial** = implemented in part (gap stated); **Not done** = not implemented. "Test" names are real test classes in the repo. Test totals at the time of writing: 154 automated tests, 0 failures (see TEST_PLAN_AND_RESULTS.md).

Abbreviations: ENG = `engine` module, JDG = `judge` module, BE = `backend`, FE = `frontend`. Tests: `AT` = `AcceptanceTest`, `JU` = `JudgeUnitTest`, `JD` = `JudgeDockerIT`, `ST` = `StorageTest`, `ES` = `EngineScenarioTest`, `AL` = `AlgorithmsTest`, `NH` = `NormalizationAndHashingTest`, `LX` = `LexerTest`, `QM` = `QualityMeasurementTest`, `BM` = `BenchmarkTest`, `FT` = `frontend.test.tsx`.

## Functional

| ID | Status | Implemented in | Verified by | Gap / note |
|---|---|---|---|---|
| FR-AUTH-01 | Done | `AuthService`, `CryptoConfig` (BCrypt cost 10) | AT (hash prefix `$2` check) | |
| FR-AUTH-02 | Done | `users.role`, `Principal`, `CurrentUser` | AT | |
| FR-AUTH-03 | Done | `Access`, per-endpoint checks, `SecurityConfig` (`/api/**` authenticated, else denyAll) | AT (401/403/404 cases) | Role is re-read from DB each request |
| FR-AUTH-04 | Done | DTO allowlists in `AssignmentController`, `SubmissionController` | AT `studentNeverReceivesHiddenTests…`, `studentsCannotReadEachOthers…` | Unauthorised resources answer 404 |
| FR-COURSE-01 | Done | `CourseController` create/edit/view/archive (`V2__archive_flags.sql`) | AT (archive hides course from students, blocks enrolment, restore) | — |
| FR-COURSE-02 | Done | enrollment code (`/enroll`, register) | AT | |
| FR-COURSE-03 | Done | `/students`, `DashboardController` | AT `dashboardsAreComputedFromData` | |
| FR-ASSIGN-01 | Done | create/patch/publish/unpublish/archive | AT | — |
| FR-ASSIGN-02 | Partial | `assignments` table | AT | One language per assignment (not a list); upload size/file-type limits are global, not per assignment; no creation-date field shown in UI (column exists) |
| FR-ASSIGN-03 | Done | `/assignments/{id}/files` kind STARTER | AT | |
| FR-ASSIGN-04 | Done | `/test-cases` (structured records) | AT | |
| FR-ASSIGN-05 | Done | student DTO omits hidden rows | AT, Playwright walk-through (ad hoc) | |
| FR-ASSIGN-06 | Partial | REFERENCE attachment, instructor-only | AT | Stored privately and never used as plagiarism evidence; **not executed against the tests** as a correctness check |
| FR-ASSIGN-07 | Partial | STARTER files fed to engine as starter set | ES, QM, AT | Whole starter files only; no per-region marking UI |
| FR-ASSIGN-08 | Done | deadline check in `SubmissionController.submit`, published check in `Access` | AT (draft invisible) | Dedicated past-deadline acceptance test added |
| FR-SUB-01 | Done | multipart single file or ZIP | AT | |
| FR-SUB-02 | Partial | extension allowlist, size, entries, extracted size, NUL-byte check | AT `hostileUploadsAreRejected`, ST | **No MIME/content sniffing** |
| FR-SUB-03 | Partial | `SafeZipExtractor` | ST (traversal, absolute, backslash, nested archive, bomb, depth, duplicates) | Symlink entries are written as plain files containing link text (never followed); not tested with a real symlink archive |
| FR-SUB-04 | Done | `submissions`, `submission_files` (attempt, status, sha256) | AT | |
| FR-SUB-05 | Done | attempts ≤ 20, deadline | AT | |
| FR-SUB-06 | Done | `/submissions/me`, FE polling | AT, FT | |
| FR-SUB-07 | Partial | keys are unique, `CREATE_NEW`, no update/delete API | — | Immutability by absence of an API, not enforced by DB or filesystem permissions; no retention/deletion operation |
| FR-JUDGE-01 | Done | upload inserts `evaluation_jobs`, `JobWorker` | AT | |
| FR-JUDGE-02 | Done | `DockerRunner`, compile in container | JD | |
| FR-JUDGE-03 | Done | `Judge`, CE path | JU, JD (C, Java, Python) | |
| FR-JUDGE-04 | Done | stdin-fed tests | JD, JU | |
| FR-JUDGE-05 | Done | `--memory`, `--cpus`, `--pids-limit`, `timeout`, wall guard, output cap, job timeout config | JU (command line), JD (TLE, MLE, fork bomb, output flood) | |
| FR-JUDGE-06 | Done | `VerdictClassifier` | JU, JD | MLE for non-OOM runtimes is heuristic (stderr text), documented |
| FR-JUDGE-07 | Done | `OutputComparator` | JU, JD | |
| FR-JUDGE-08 | Done | `evaluation_results`, `testcase_results` | AT | |
| FR-JUDGE-09 | Done | hidden data never in container filesystem or argv | JD `hiddenExpectedOutput…`, JU | Stdin carries hidden *input* during its own run, by design |
| FR-JUDGE-10 | Done | worker is separate from request threads; containers bounded | JD fork bomb/flood | |
| FR-JUDGE-11 | Done | `evaluation()` hides detail/excerpts/names for hidden tests | AT | |
| FR-PLAG-01 | Done | `LexerFactory`, `LanguageTokenizer` | LX | C, C++, Java, Python, JavaScript |
| FR-PLAG-02 | Done | hand-written scanners | LX | |
| FR-PLAG-03 | Done | token stream keeps keywords/operators/order | LX, NH | |
| FR-PLAG-04 | Done | `TokenNormalizer` first-occurrence IDs | NH, QM | Default scope is per *unit* rather than per file (documented deviation, measured) |
| FR-PLAG-05 | Done | `NormalizationConfig` literal modes (SMART default) | NH | |
| FR-PLAG-06 | Done | escapes, raw/multiline strings, char literals | LX | |
| FR-PLAG-07 | Done | preprocessor handled, common includes suppressed (configurable) | LX, NH | |
| FR-PLAG-08 | Done | `StarterCodeFilter` | ES, QM | |
| FR-PLAG-09 | Done | `KGramHasher`, k=10 | AL, NH | |
| FR-PLAG-10 | Done | FNV-1a + Rabin–Karp rolling, pinned test vector | NH | |
| FR-PLAG-11 | Done | winnowing w=5, rightmost-min ties | AL | |
| FR-PLAG-12 | Partial | `fingerprintVersion()` stored per run (`plagiarism_runs.config`) | AT | Fingerprints are **recomputed each run**, not persisted/cached |
| FR-PLAG-13 | Done | `InvertedIndex` | AL, BM | |
| FR-PLAG-14 | Done | orchestration loads one assignment's submissions | AT `plagiarismIsScopedToOneAssignment` | |
| FR-PLAG-15 | Done | language groups in engine | ES | |
| FR-PLAG-16 | Done | `Dice` | AL | |
| FR-PLAG-17 | Done | detailed stage only on candidates | ES, BM | |
| FR-PLAG-18 | Done | `GreedyStringTiling` (RKR-GST) with size cap | AL, BM | |
| FR-PLAG-19 | Done | `PairReport`, `PairDetail` | AT, QM | |
| FR-PLAG-20 | Done | `Fragment` line ranges | ES, AT | |
| FR-PLAG-21 | Done | starter and common-tile discounting | ES, QM | |
| FR-PLAG-22 | Done | rarity weighting (N ≥ 5) | AL, ES | |
| FR-PLAG-23 | Done | `PLAG_HIGH_PRIORITY_THRESHOLD` (0.80 default) | AT | Triage setting, not proof |
| FR-PLAG-24 | Done | `INSUFFICIENT_EVIDENCE`, min-token gates | ES, AT | |
| FR-PLAG-25 | Done | `PairPage`, `CodeCompare` | FT (line marks), Playwright (ad hoc) | |
| FR-PLAG-26 | Done | `/review` | AT | |
| FR-PLAG-27 | Done | no code path changes grades/accounts | AT (wording), code review | No grading feature exists at all |
| FR-PLAG-28 | Done | `plagiarism_reviews` (history), `audit_logs` | AT | |
| FR-STOR-01 | Done | `FileStorageProvider` | ST | |
| FR-STOR-02 | Done | `LocalFileStorageProvider`, `STORAGE_ROOT` | ST | |
| FR-STOR-03 | Partial | path/key, size, sha256, association via tables | AT | Provider name and MIME type are **not** stored |
| FR-STOR-04 | Removed | Google Drive stub cleanly removed | — | Local storage is the verified, production-ready provider |
| FR-STOR-05 | n/a | Local storage is the active provider | — | |
| FR-STOR-06 | Done | local is the primary verified provider | AT | |
| FR-STOR-07 | Done | all file reads go through authorised endpoints | AT | |
| FR-REPORT-01 | Done | `DashboardController`, submissions list | AT | |
| FR-REPORT-02 | Done | pair list/detail | AT | |
| FR-REPORT-03 | Done | `CodeCompare` | FT, Playwright (ad hoc) | |
| FR-REPORT-04 | Done | `PlagiarismExportController` & `DossierExportRegistry`: PDF (OpenPDF), HTML, JSON per pair, CSV per assignment (instructor-only, escaped, formula-neutralised) | AT | Full polymorphic dossier export |
| FR-AUDIT-01 | Partial | `AuditService` (login, create, publish, submission, run, review, rejudge, file view) | AT `auditTrailRecords…` | Evaluation state transitions are in `evaluation_jobs`, **not** in `audit_logs` |

## Non-functional

| ID | Status | Evidence |
|---|---|---|
| NFR-SEC-01 | Done | BCrypt; AT asserts only `$2` hashes stored |
| NFR-SEC-02 | Done | `Access` + AT cross-user cases |
| NFR-SEC-03 | Done | `DockerRunner` flags asserted in JU; behaviour in JD (no network, read-only FS, non-root, no docker.sock). Not a claim of production-grade isolation: containers share the host kernel |
| NFR-SEC-04 | Partial | see FR-SUB-02/03 |
| NFR-SEC-05 | Done | secrets only via env; `.env` git-ignored; `.env.example` has placeholders; app refuses to start with a short `JWT_SECRET` |
| NFR-SEC-06 | Done | `server.address=127.0.0.1`, Postgres bound to 127.0.0.1 |
| NFR-REL-01 | Done | JD (fork bomb/flood/crash do not hurt the judge); IE path in JU |
| NFR-REL-02 | Done | retry cap + `recoverStuck()` | AT order 17 (retry then FAILED/IE; stale RUNNING re-queued or failed) |
| NFR-PERF-01 | Done | inverted index; BM shows 3 candidate pairs of 4,950 on the realistic workload |
| NFR-PERF-02 | Done (synthetic) | 100 × ~314 lines: 599 ms realistic, 3,376 ms homogeneous worst case, 2-CPU machine |
| NFR-ACC-01 | Partial | 0 FP / 131 independent pairs on a **synthetic** set; not validated on real student work |
| NFR-ACC-02 | Done | explanation + metrics in every `PairReport` |
| NFR-MAINT-01 | Done | `LanguageTokenizer` interface; tokenizer added without touching hashing/winnowing/index |
| NFR-MAINT-02 | Done | engine and judge are separate Maven modules; neither depends on the other or on Spring |
| NFR-USE-01 | Partial | separate navs, empty/loading/error states, no horizontal overflow at 420 px on 8 pages (measured). No accessibility audit tool was run |
| NFR-COST-01 | Done | no paid service or API anywhere |
