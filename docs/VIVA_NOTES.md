# Viva notes

Short, honest answers. Where something is a limitation, say so — it scores better than being caught.

## What is it?
A platform where instructors publish programming assignments, students submit code, a Docker sandbox judges it, and an explainable similarity engine surfaces candidate pairs *within one assignment* for the instructor to review. Local, free, PostgreSQL + Spring Boot + React.

## Why two independent modules (engine, judge)?
Different failure modes and trust levels: the judge runs hostile code and needs process/Docker access; the engine is pure computation. Neither imports the other or Spring (NFR-MAINT-02), so each is testable alone (71 and 40 tests).

## Plagiarism pipeline — one paragraph
Tokenize with hand-written scanners (comments/strings handled correctly, not by regex) → normalise identifiers by first occurrence, keep keywords/operators/order → drop instructor starter code → hash every window of k=10 tokens with a Rabin–Karp rolling hash → winnowing (window 5) keeps a guaranteed-dense subset → inverted index finds only pairs that share fingerprints → Dice similarity plus a rarity weight (matches most students share count less) → on candidates only, Running Karp–Rabin Greedy String Tiling confirms structure and gives matched fragments → map tokens back to file/line → triage state with a plain-language explanation.

## Why winnowing guarantees detection?
Any shared run of at least w+k−1 = 14 tokens contains a window whose minimum hash is selected in both documents, so it contributes a common fingerprint. Shorter copies may be missed — by design, short matches are ambiguous.

## Why is the false-positive risk lower than "just Dice"?
Starter code is removed, common includes are dropped, tiles made of very common idioms are discounted, rarity weighting reduces credit for what everyone writes, HIGH needs ≥100 distinctive tokens *and* high Dice *and* score ≥0.80, and tiny submissions return "insufficient evidence". Measured on the synthetic set: 0 FP of 131 independent pairs. **Caveat:** synthetic, author-written copies; not proof on real data.

## What did measurement change in your design?
(1) File-global identifier numbering gave recall 2/5 on reordered/renamed copies; scoping numbering per unit gave 5/5 with 0 FP. (2) A padded-copy scenario test failed at candidate cutoff 0.50, so the default became 0.30 (on the 17-submission quality set both cutoffs give the same 5/0/131/0, so that choice rests on the scenario test, not on the quality table). (3) The first benchmark was 9.4 s because my generator made all programs alike; a realistic generator plus a rarity gate and a primitive GST table gave 0.6 s (the homogeneous worst case is reported too: 3.4 s). All in `IMPLEMENTATION_LOG.md`.

## Why is the copied pair NEEDS_REVIEW and not HIGH in the acceptance test?
Its score was 0.783 < 0.80. The threshold is a triage setting; I did not tune it to make the demo look better.

## How is untrusted code contained?
Only via Docker: no network, read-only root, `--cap-drop ALL`, no-new-privileges, non-root uid, pid/memory/CPU limits, `noexec` tmpfs, one mounted job directory (read-only while running tests), output cap, in-container `timeout` plus a host wall-clock guard. Tested with a fork bomb, memory hog, infinite loop, output flood, network attempt, write attempts and host-file probes. Limits: same kernel as host; not gVisor/Firecracker.

## How do hidden tests stay hidden?
Student endpoints never read hidden rows; the expected output is never written into the container; hidden test names appear as "Hidden test N"; details/excerpts are nulled for hidden tests. Asserted by tests on actual JSON.

## What if Docker is down?
The judge says "Judge unavailable" (verdict IE, explicitly not the student's fault) and executes nothing on the host. Instructor can re-run later.

## Why a database queue instead of Kafka/RabbitMQ?
One dependency fewer for a local demo; `SELECT … FOR UPDATE SKIP LOCKED` gives safe multi-consumer claiming; status is already persisted (recoverability). Trade-off: polling latency (~1.5 s) and throughput limits.

## Patterns used
Adapter (language adapters, storage providers, container runner port), Strategy/Factory (tokenizers), Repository-style data access, Producer–consumer (jobs), DTO allowlist, Template-ish pipeline in the engine.

## Security answers
BCrypt; JWT in sessionStorage (XSS would expose it — no cookies, hence no CSRF); role re-read from DB each request; 404 instead of 403 on foreign resources; lockout; audit log; secrets in env; localhost binding. Known weakness: no server-side revocation.

## What is NOT done (say it first)
Google Drive adapter (cleanly removed stub; local storage used); report export completed (PDF, HTML, JSON); course archive/unpublish; per-assignment multi-language/file policies; MIME sniffing; automated test for stuck-job recovery; E2E UI test suite; validation on real student data.

## COCOMO, cyclomatic complexity
Basic COCOMO organic 2.4·KLOC^1.05: 3.5 KLOC → 8.94 PM (spec planning figure); measured 6.295 physical KLOC → 16.56 PM — both theoretical. `VerdictClassifier.classify` V(G)=9 (13 counting `||`), by hand. Details in `PROJECT_PLAN.md`.

## Likely curveballs
- *"Is 0.80 a magic number?"* No: configurable, uncalibrated on real data, labelled triage.
- *"Can it detect AI-written code?"* No, and it doesn't claim to.
- *"Would it catch a student who rewrote the logic?"* No: structural similarity only; that is a limitation of all token-based tools.
- *"Why not compare across assignments/years?"* Out of scope in the MVP, and it raises fairness and privacy questions.
