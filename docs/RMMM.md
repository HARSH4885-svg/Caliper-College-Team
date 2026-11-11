# Risk Mitigation, Monitoring and Management (RMMM)

Probability (L/M/H) and impact (1 low – 4 catastrophic) are judgement, not measured values. "Status" says what was actually done.

| # | Risk | P | Impact | Mitigation | Monitoring | Status |
|---|---|---|---|---|---|---|
| R1 | Student code escapes or harms the host | M | 4 | Docker only; no host execution path exists; no network, read-only FS, non-root, cap-drop ALL, no-new-privileges, pid/memory/CPU limits, private job dir. Docker down ⇒ judge reports IE | JD tests; `DockerRunner` flags asserted in JU | Implemented + tested. Residual: shared kernel; not hardened beyond Docker defaults |
| R2 | Hidden tests / reference solution leak | M | 4 | Student DTO allowlists; hidden test text never mounted; 404 for others' resources | AT leak assertions | Implemented + tested |
| R3 | False accusation from similarity output | M | 4 | Neutral wording, triage states only, human decision, no automatic penalty, rarity/starter/common-idiom discounting, insufficient-evidence state | wording test (AT, FT); quality report | Implemented; accuracy only validated on synthetic data |
| R4 | Detector too weak (misses disguised copies) | M | 3 | Structural GST stage after fingerprints; measured ablations | quality report | Recall 5/5 on synthetic set; heavy refactoring/semantic rewrites are not detected by design |
| R5 | Detector slow at class scale | L | 2 | Inverted index, rarity gate, capped GST, parallel pair analysis | BM | 599 ms / 3,376 ms for 100×~314 lines on 2 CPUs |
| R6 | Malicious upload (ZIP bomb, traversal) | M | 3 | Streaming extractor with byte budget, limits | ST, AT | Implemented + tested; no MIME sniffing |
| R7 | Docker unavailable at demo time | M | 3 | Judge degrades to explicit IE "Judge unavailable"; rejudge button; docs list start-up commands (`dockerd` may need manual start) | `/api` shows IE; worker log | Handled; demo rehearsal is the student's task |
| R8 | Jobs lost or stuck on worker crash | L | 3 | Persisted queue, `SKIP LOCKED`, stuck-job recovery, attempt cap | — | Implemented, **not tested** |
| R9 | Secret leakage | L | 3 | Env only; `.env` ignored; app refuses short JWT secret; placeholders in `.env.example` | `git ls-files` review | Done; demo credentials are fixed and public, only exist with `SEED_DEMO=true` |
| R10 | Brute-force login | M | 2 | In-memory lockout 5/5 min (resets on restart; per email, not per IP) | AT | Implemented + tested |
| R11 | Stateless JWT cannot be revoked | M | 2 | 8 h TTL, role/enabled re-read from DB each request; sessionStorage | — | Logout is client-side only (limitation) |
| R12 | Google Drive adapter expected | L | 1 | Stub cleanly removed; local storage is the complete, verified provider | ST | **Removed cleanly**; local storage is active |
| R13 | Overclaiming results at evaluation | M | 3 | Every metric labelled synthetic/measured; RTM marks gaps | this repo's docs | Ongoing |
| R14 | Schedule: little time before 17 Nov 2026 | M | 3 | Prioritised the core pipeline; list of known gaps in README | — | Student to manage |
