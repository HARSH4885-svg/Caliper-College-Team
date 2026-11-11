# Project plan

## Process model: incremental

Work was delivered as increments, each ending in a tested, committed, runnable slice (see `IMPLEMENTATION_LOG.md` for what each increment actually contained and its verification).

| Increment | Content | Exit check (what was run) |
|---|---|---|
| 0 | Skeleton: Maven modules, Compose Postgres, Vite app, env template | build compiles |
| 1 | Plagiarism engine: tokenizers, normalization, k-grams, winnowing, index, GST, triage, CLI | 71 engine tests; quality report; benchmark |
| 2 | Docker judge, storage port, safe ZIP extraction | 40 judge tests (22 against real containers); 11 storage tests |
| 3 | Backend (schema, JWT, REST, worker, plagiarism orchestration), React UI, demo seed | 13 acceptance tests; 10 UI tests; live walk-through |
| Final | Documentation from measured results | this document set |

Commits are real and in the repository history (`git log`), made in one working session on 2026-11-09 between about 22:35 and 23:30 local time (IST). They are **not** a record of a multi-week schedule and the plan must not be presented as one. Planned calendar milestones for the course (viva/evaluation 17 Nov 2026) are the student's to fill in; none are invented here.

## Effort estimate (theoretical)

Basic COCOMO, organic mode: Effort = 2.4 × (KLOC)^1.05 person-months.

| Input | KLOC | Effort (PM) | Status |
|---|---|---|---|
| Spec's planning figure | 3.5 | **8.94** | Theoretical planning estimate only |
| Measured size of this repository | 6.295 | **16.56** | Theoretical; model output for the measured size |

Measured size = physical lines (`wc -l`, including blanks and comments) of production code: Java main 5,464 (engine 2,514, judge 944, backend 2,006) + TypeScript/TSX 831. Excluded: tests (Java 2,363; TS 95), CSS (70), SQL (195). These are **not** SLOC and not logical-statement counts. COCOMO's output is a model prediction for a human team; it is **not** the actual effort spent here and the actual effort is not claimed or measured in person-months.

## Cyclomatic complexity (one implemented method)

`VerdictClassifier.classify(RunResult, String)` (`judge/.../verdict/VerdictClassifier.java`), counted by hand from the source:

- Decision statements: 6 `if` (OOM; timed-out; output-limit; exit 124/137; exit ≠ 0; runtime-OOM text) + 2 conditional expressions (`exitCode() > 128`; `matches(...)`) = 8 → **V(G) = 8 + 1 = 9**.
- Counting short-circuit operators as predicates as well (`||` ×1 in the 124/137 test, ×3 in the OOM-text test): 8 + 4 + 1 = **13**.

Both numbers are reported because tools differ. Coverage by `JudgeUnitTest.verdictRules` exercises 9 distinct outcome paths (AC, WA, RE×2, TLE×2, MLE×2, output-limit→RE). Not produced by an automated tool.

## Roles and schedule

Single developer (the student) with an AI pair; no team roles are claimed. Remaining work before evaluation is listed in the README "Known limitations".
