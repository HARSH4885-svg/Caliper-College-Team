# Software Requirements Specification — CodeSentinel

## 1. Purpose and scope
CodeSentinel is a local-first web platform for programming assignments in an academic course. Instructors publish assignments with sample and hidden tests; students submit source files or ZIPs; submissions are compiled and run in isolated Docker containers and judged; the platform also surfaces **candidate similar pairs within one assignment** for human review with explainable evidence. It is a teaching-project system, not a production service, and it does not decide academic misconduct.

Out of scope: cross-assignment or internet-wide plagiarism search, AI-generated-code detection, automatic grading/penalties, hosted deployment, external cloud drives (local storage is the sole verified provider).

## 2. Users
- **Instructor** (created by administrator via environment bootstrap; no public sign-up): manages courses, assignments, tests, reviews similarity.
- **Student**: self-registers only with a course enrollment code; submits and views own results.

## 3. Operating environment
Java 21, PostgreSQL 16, Docker (for the judge), Node 20+ for the UI build. Binds to 127.0.0.1 by default.

## 4. Functional requirements
Identified by the IDs used throughout the repository (FR-AUTH, FR-COURSE, FR-ASSIGN, FR-SUB, FR-JUDGE, FR-PLAG, FR-STOR, FR-REPORT, FR-AUDIT). The authoritative list with the implementation status of each is `RTM.md`; summary of what the system does:

1. Authentication with BCrypt-hashed passwords and signed bearer tokens; role-based authorization enforced on the backend.
2. Courses and enrollment by code; instructors see their students and summaries.
3. Assignments with statement, deadline, language, time/memory limits, starter files, private reference file, sample and hidden tests; publish.
4. Submissions as a single file or ZIP with safe extraction; attempts; history; checksum.
5. Asynchronous judging in containers with verdicts AC/WA/CE/RE/TLE/MLE/IE, per-test results, stated output-comparison policy.
6. Similarity review: language-aware normalization, fingerprints (k=10, w=5), candidate retrieval through an inverted index, structural verification, rarity/starter/common-idiom discounting, triage states, explanation, side-by-side view with matched lines, instructor decision (Confirmed concern / Dismissed / Inconclusive) with history, audit.
7. Dashboards computed from data; audit log of material events.

## 5. Output comparison policy (FR-JUDGE-07)
Line endings CRLF/LF are equivalent; trailing whitespace on each line is ignored; trailing blank lines at the end of output are ignored. Nothing else is ignored: token order, case, numeric formatting (`1` ≠ `1.0`), inner spacing, and blank lines in the middle all matter.

## 6. Fairness rules for similarity (non-negotiable)
Same assignment only; latest attempt per student; never an automatic penalty or accusation; states are "needs review", never "plagiarism"; short/common code yields "insufficient evidence"; every pair shows the metrics and reasons; a human decision is recorded separately from the engine state.

## 7. Non-functional requirements
Security (NFR-SEC-01…06), reliability (NFR-REL-01/02), performance (NFR-PERF-01/02: 100 × ~300 lines under 5 s on a documented machine — measured 0.6 s / 3.4 s), accuracy and explainability (NFR-ACC), maintainability (NFR-MAINT: engine and judge independent; tokenizers pluggable), usability, free operation. Status per requirement: `RTM.md`.

## 8. Constraints and assumptions
No paid or AI APIs. Untrusted code never runs on the host. If Docker is unavailable the judge reports "Judge unavailable" and does nothing else. Demo data is synthetic and labelled. Thresholds are triage settings and have been tuned only on synthetic data.

## 9. Known limitations
See README ("Known limitations"): no Drive adapter, no report export, no course/assignment archive or unpublish, one language per assignment, token revocation is client-side, similarity accuracy unvalidated on real data.
