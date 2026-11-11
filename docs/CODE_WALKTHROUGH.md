# Code walkthrough (read this before the viva)

Written for someone who knows C++ and is still learning Java. Paths are relative to the repo root; package prefix `dev.codesentinel`. Read in the order below; each stop says what to look at and the one-line point you should be able to explain.

## 0. The big picture (2 minutes)
```
Browser (React)  --HTTP+JWT-->  backend (Spring Boot)  --SQL-->  PostgreSQL
                                   |  \--> storage (local files)
                                   |-- JobWorker --> judge --> Docker container (student code runs here only)
                                   \-- PlagiarismService --> engine (pure Java library)
```
Three Maven modules: `engine` (similarity maths, no Spring), `judge` (runs code in Docker, no Spring), `backend` (glues them to HTTP, DB, security). `frontend/` is separate (React + TypeScript).

Java ideas you will meet: `record` (immutable data class, like a C++ struct with auto constructor/getters), `interface` (pure virtual class), `Optional` (maybe-value instead of null), `@RestController` / `@GetMapping` (annotations that register URL handlers), constructor injection (Spring passes the objects a class needs into its constructor).

## 1. Flow A: a student uploads code and gets a verdict

1. **Upload** - `backend/.../submissions/SubmissionController.java`, `@PostMapping("/assignments/{assignmentId}/submissions")`.
   Checks, in order: caller is a student, enrolled, assignment visible (`common/Access.java`), deadline, attempt cap (20), file size/type. A `.zip` goes through `storage/SafeZipExtractor.java` (rejects `../` paths, absolute paths, symlinks, too many files, zip bombs). Files are saved through the `FileStorageProvider` interface (`storage/LocalFileStorageProvider.java`), rows go into `submissions`, `submission_files`, and one row into `evaluation_jobs` (status QUEUED). The HTTP request returns immediately - judging is asynchronous.
   *Point:* upload never runs code; it only stores and enqueues.
2. **Queue** - table `evaluation_jobs` (`db/migration/V1__core_schema.sql`). It is a persisted queue: it survives a restart.
3. **Worker** - `jobs/JobWorker.java`. `poll()` runs on a timer and calls `processOne()`. The key SQL is `UPDATE ... WHERE id = (SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1)`: two workers can never take the same job. On an exception the job goes back to QUEUED until `maxAttempts`, then FAILED with verdict IE (judge problem, not the student's fault). `recoverStuck()` re-queues jobs left RUNNING by a crash.
   *Point:* at-least-once processing with an attempt cap; results are replaced, not duplicated (`save()` deletes old results first).
4. **Judge** - `judge/.../Judge.java`. Picks a `LanguageAdapter` (`compiler/`: native C/C++, Java, scripts). Builds (compile step in a container), then runs each test case via `sandbox/DockerRunner.java`, compares output with `testcase/OutputComparator.java` and decides the verdict with `verdict/VerdictClassifier.java` (AC, WA, CE, TLE, MLE, RE, IE; an output-limit overflow is classified as RE).
5. **Sandbox** - `DockerRunner` builds a `docker run` command: `--network none`, read-only root, `--cap-drop ALL`, `no-new-privileges`, non-root user, pid/memory/CPU limits, `noexec` tmpfs, one mounted job directory, output cap, in-container `timeout` plus a host-side wall-clock guard. If Docker is missing the judge reports unavailable; it never falls back to running on the host.
   *Point:* the isolation is Docker defaults plus these restrictions; containers share the host kernel (stated limitation).
6. **Result to student** - `SubmissionController` GET endpoints. For hidden tests the student sees only "Hidden test N", pass/fail; expected output, input and stdout excerpts are nulled. Tests assert this on the real JSON.
7. **UI** - `frontend/src/pages/SubmissionPage.tsx` polls until status is DONE/FAILED.

Verdict vs similarity: a verdict says "does it produce the right output"; it says nothing about copying.

## 2. Flow B: the similarity pipeline

Trigger: instructor clicks *Run comparison* -> `plagiarism/PlagiarismController` -> `PlagiarismService.run(assignmentId)`. The service loads the latest submission of each student **of that one assignment** (never across assignments), builds `SubmissionInput`s and calls `PlagiarismEngine` (`engine/api/`). Then it stores pairs, fragments and the run settings.

Inside the engine (`engine/`), in pipeline order:
1. **Lexing** - `lexer/CFamilyTokenizer`, `PythonTokenizer`: hand-written scanners so comments and string contents are handled properly (a regex would get this wrong).
2. **Normalisation** - `normalization/TokenNormalizer`: identifiers are replaced by their first-occurrence number inside a unit, so renaming variables does not hide a copy; keywords, operators and order are kept. `StarterCodeFilter` removes code the instructor supplied.
3. **K-gram hashing** - `fingerprint/KGramHasher`: rolling hash over windows of k=10 tokens.
4. **Winnowing** - `winnowing/Winnower`: in each window of w=5 hashes keep the minimum. Guarantee: any shared run of at least w+k-1 = 14 tokens produces a common fingerprint.
5. **Inverted index** - `indexing/InvertedIndex`: fingerprint -> submissions containing it, so only pairs that share something are examined (avoids comparing all n^2 pairs in full).
6. **Scoring** - `similarity/Dice` (set overlap) plus rarity weighting: fingerprints that most students share count less.
7. **Confirmation** - `similarity/GreedyStringTiling` (Running Karp-Rabin GST) on candidate pairs only: finds the actual matching token runs, which become the highlighted fragments (mapped back to file/line).
8. **Triage state** - `similarity/PairAnalyzer` and `reporting/ReportState`: NO_MEANINGFUL_MATCH / LOW_SIMILARITY / NEEDS_REVIEW / HIGH_PRIORITY_REVIEW / INSUFFICIENT_EVIDENCE, each with a plain-language explanation. These are review priorities, not verdicts.
9. **Human decision** - instructor records CONFIRMED_CONCERN / DISMISSED / INCONCLUSIVE with a note on the pair page; nothing happens automatically and nothing is shown to students.

Evidence you can quote (all from this project's own runs, synthetic data): quality set TP 5 / FP 0 / TN 131 / FN 0 (`docs/results/engine-quality-report.md`); the demo copied pair scores 0.783 -> NEEDS_REVIEW; benchmark 599 ms realistic, 3376 ms worst case.

## 3. Access control in one place
- JWT is checked in `auth/JwtAuthFilter`; role and enabled flag are re-read from the DB each request.
- `common/Access.java` is the single place that decides "may this user see this course/assignment/submission". Foreign resources return **404** (do not reveal existence); wrong role returns **403**.
- Students never get: hidden tests, reference solutions, other students' code, similarity data.
- Export (`plagiarism/PlagiarismExportController`) is instructor-owner only; CSV cells are quoted and formula-neutralised; HTML is escaped.

## 4. Where the tests are
| What | Where |
|---|---|
| Engine algorithms, quality set, benchmark | `engine/src/test` |
| Judge verdicts + real containers (fork bomb, memory hog, network, writes) | `judge/src/test` (`JudgeDockerIT` needs Docker) |
| ZIP safety, storage | `backend/.../storage/StorageTest` |
| End-to-end API with real PostgreSQL: RBAC, hidden tests, deadline, archive, worker retry/recovery, plagiarism, export | `backend/.../acceptance/AcceptanceTest` |
| UI components/routing | `frontend/src/frontend.test.tsx` |

## 5. Likely viva questions - one-line answers
- *Why async judging?* A slow or hostile program must not block HTTP threads; the queue also survives restarts.
- *Why `FOR UPDATE SKIP LOCKED`?* Safe parallel workers without double-processing.
- *Why compare only within one assignment?* Different problems share no meaningful code; cross-assignment matching causes false alarms and raises privacy concerns.
- *Why not accuse automatically?* Similarity can arise by chance or from shared starter code; the system only prioritises review and explains why.
- *What does 0 FP mean?* 0 false positives on 131 independent pairs of a synthetic set I wrote - not a general guarantee.
- *Is the sandbox secure?* Defence in depth with Docker restrictions, tested against several abuse programs, but containers share the host kernel; it is not production-hardened.
- *What was removed/clarified?* Incomplete Google Drive storage stub removed (local storage is fully implemented and tested), PDF/HTML/JSON dossier export is completed, multi-language per assignment and checked-in browser E2E tests. See README "Implemented System Capabilities".
- *What is Git for here?* See `docs/GIT_GUIDE.md`; the history is real, never backdated.

## 6. Suggested 20-minute self-study order
`V1__core_schema.sql` (10 min skim) -> `SubmissionController.submit` -> `JobWorker.processOne` -> `Judge` + `DockerRunner` -> `PlagiarismService.run` -> `PairAnalyzer` -> `AcceptanceTest` test names (they read like a requirement list).
