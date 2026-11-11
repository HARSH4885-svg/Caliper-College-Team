# Caliper: Presenter Playbook & Operational Demonstration Guide

> **The Precision Instrument for Code Evaluation & Academic Integrity**  
> *A Complete, Presenter-Ready Guide for Demonstrations, Evaluator Panels, and Technical Defenses*

---

## Executive Summary & Guide Structure

This playbook provides a complete, self-contained guide for presenting **Caliper** without needing the original developer to explain every section. It is organized into five operational sections:

1. **[Slide-by-Slide Presentation Guide (8–10 Minutes)](#section-1-slide-by-slide-presentation-guide)**: Complete slide outlines, visual layouts, detailed spoken scripts, and natural transitions.
2. **[Practical Live Demonstration Script](#section-2-practical-live-demonstration-script)**: Pre-demo checklist, screen-by-screen actions, what to click, what to say, expected results, and emergency fallback procedures.
3. **[Evaluator & Audience Q&A Preparation](#section-3-evaluator--audience-qa-preparation)**: Technically rigorous answers to critical evaluator questions grounded directly in the Java source code.
4. **[Presenter Quick-Reference Fact Sheet](#section-4-presenter-quick-reference-fact-sheet)**: Key technical facts, ports, credentials, test counts, and architecture summaries for rapid reference.
5. **[Final Closing Statement](#section-5-final-closing-statement)**: A concise, impactful closing address for academic evaluators.

---

# Section 1: Slide-by-Slide Presentation Guide

*Recommended Total Speaking Time: 8 to 10 Minutes.*
*Can be delivered by a solo presenter or divided among a multi-person team.*

---

### Slide 1: Title & Purpose — What is Caliper?

- **Recommended Duration**: 45 seconds
- **Slide Title**: Caliper: Precision Code Evaluation & Academic Integrity
- **Subtitle**: A Self-Hosted, Local-First Platform for Computer Science Education
- **What Appears on the Slide**:
  - **Core Tagline**: The Precision Instrument for Code Evaluation & Academic Integrity.
  - **Key System Capabilities**:
    - Multi-language in-browser coding workspace (C, C++, Java, Python, JavaScript/Node.js).
    - Pluggable container execution sandboxing (Docker `runc` and Google gVisor `runsc`).
    - Explainable dual-engine similarity detection (Lexical Winnowing + Tree-sitter AST structural parsing).
    - Strategy-pattern polymorphic quiz evaluation.
    - 1EdTech LTI 1.3 Advantage Core integration for Canvas LMS.
    - Formal Academic Integrity Hearing Dossiers in printable PDF format via OpenPDF.
  - **Visual Suggestion**: Caliper logo banner or a screenshot of the clean student coding workspace alongside the instructor similarity review desk.

#### Speaker Notes
> "Good morning, respected evaluators and faculty. Today, I am proud to present **Caliper** — *The Precision Instrument for Code Evaluation and Academic Integrity*.
>
> In simple terms, Caliper is an open, self-hosted platform designed specifically for computer science education. It replaces the fragmented toolchains that universities currently use by bringing together three critical functions into one cohesive system: first, a low-latency in-browser coding workspace for students; second, an isolated, rootless container sandbox that executes untrusted code safely; and third, an explainable, dual-engine plagiarism investigation desk that pairs lexical fingerprinting with deep Abstract Syntax Tree structural parsing.
>
> Caliper is built entirely on open, auditable software. It requires zero commercial cloud autograder subscriptions, zero invasive student surveillance extensions, and zero third-party AI APIs. Institutional code never leaves campus servers."

#### Transition Sentence
> *"To understand why we built Caliper, let's examine the friction and breakdown that computer science departments face every semester."*

---

### Slide 2: The Core Problem — The Broken CS Assignment Lifecycle

- **Recommended Duration**: 45 seconds
- **Slide Title**: The Core Problem: Fragmentation and the "Black Box" Trap
- **What Appears on the Slide**:
  - **The Student Dilemma**:
    - Environment mismatches ("works on my machine, fails in the autograder").
    - Complex command-line upload workflows that distract from algorithm design.
    - Anxiety from blind submissions without immediate intermediate feedback.
  - **The Instructor Dilemma**:
    - Juggling generic LMS platforms (Canvas, Moodle) that cannot compile or execute code.
    - High per-submission costs from commercial cloud autograders.
  - **The Plagiarism "Black Box" Trap**:
    - Commercial tools spit out an arbitrary percentage (e.g., "78% similar") with zero explainable evidence.
    - Common starter code and textbook algorithms trigger rampant false positives.
    - Accusatory scores cannot be defended before academic misconduct review boards.
  - **Visual Suggestion**: Split comparison diagram showing a student struggling with disjointed tools on the left versus an instructor drowning in unexplainable similarity percentages on the right.

#### Speaker Notes
> "If you have ever taught or taken a computer science programming course, you know the assignment lifecycle is fraught with friction.
>
> On the student side, learners wrestle with environment mismatches, confusing autograders, and disconnected upload portals. They submit code blindly and hope for the best, with no way to test edge cases interactively.
>
> On the faculty side, instructors are forced to juggle multiple disjointed tools: a general LMS like Canvas that cannot compile or run code, external commercial autograders that charge per-student fees, and commercial similarity tools that present an arbitrary percentage score with zero insight into how that number was calculated.
>
> When an algorithm accuses a student of plagiarism based on an opaque percentage, everyone loses. Students feel unfairly targeted, instructors cannot substantiate claims before an academic integrity hearing, and countless hours are lost untangling common boilerplate starter code. Caliper was engineered to resolve both challenges directly."

#### Transition Sentence
> *"Let's look at how Caliper structures its platform to solve these problems for both students and instructors."*

---

### Slide 3: Target Users & Core Platform Workflows

- **Recommended Duration**: 45 seconds
- **Slide Title**: Two Workflows, One Unified Platform
- **What Appears on the Slide**:
  - **Student Experience (Learning-Centric)**:
    - Centralized course and workspace hub (`/workspace`).
    - Multi-language in-browser editor with persistent local drafts.
    - Instant custom-input scratchpad testing.
    - Automated public testcase validation before final submission.
  - **Instructor Experience (Pedagogical Control)**:
    - Course creation, assignment authoring, and testcase management.
    - Multi-course plagiarism triage desk (`/reviews`) with risk tiers.
    - Side-by-side synchronized diff viewer with AST node alignment.
    - One-click formal PDF hearing dossier generation.
  - **Security & Authorization**:
    - Strict Role-Based Access Control (`STUDENT`, `INSTRUCTOR`, `ADMIN`).
    - Stateless JSON Web Token authentication with BCrypt password hashing.
  - **Visual Suggestion**: Architectural workflow flowchart showing the dual user journeys converging on the central evaluation and storage layers.

#### Speaker Notes
> "Caliper organizes the educational experience around the two people who matter most: the student and the instructor.
>
> For students, the platform provides a clean, low-friction Coding Workspace. Students log in, select an active assignment, see the problem description, constraints, and starter code, and begin coding immediately in their browser. They can test hypotheses in a scratchpad with arbitrary inputs, run their solution against official public test cases, and submit their work with confidence.
>
> For instructors, Caliper offers an operational command center. Faculty can create courses, publish coding assignments, configure hidden and public test cases, and author interactive quizzes. When submissions arrive, Caliper automatically runs similarity triage across all submissions, categorizing candidate pairs into clear risk tiers. Instructors can inspect matches side-by-side, toggle between lexical and syntax-tree alignments, and export printable hearing dossiers.
>
> Every action is guarded by role-based access control, ensuring students only see what is pedagogically appropriate, while instructors maintain complete administrative oversight."

#### Transition Sentence
> *"Let's dive deeper into the student coding workspace, where code evaluation begins."*

---

### Slide 4: Student Experience — In-Browser Workspace & Scratchpad Testing

- **Recommended Duration**: 60 seconds
- **Slide Title**: The Coding Workspace: Low Latency & Safe Exploration
- **What Appears on the Slide**:
  - **CodeMirror 6 Editor**:
    - Full syntax highlighting, bracket matching, line numbers, and auto-indentation.
    - Multi-language support: C, C++, Java 21, Python 3.12, Node.js 20.
  - **Custom Input Scratchpad**:
    - Executes untrusted code in an ephemeral container in under 50 milliseconds.
    - Allows students to test edge cases and verify algorithm logic without grade penalty.
  - **Public Test Runner**:
    - Real-time comparison table showing expected versus actual stdout.
    - Visual diffs highlighting whitespace, formatting, or logic discrepancies.
  - **Persistent Local Storage**:
    - Every keystroke is saved to browser `localStorage` under assignment-scoped keys.
    - Zero work lost if the browser crashes, tab closes, or connection drops.
  - **Visual Suggestion**: High-resolution screenshot of `/assignments/1/code` showing the problem description on the left, editor on the right, and scratchpad output below.

#### Speaker Notes
> "Let's examine the student coding experience. In traditional setups, students write code in an external IDE, compile it locally, export a zip file, and upload it to a portal. If their local compiler version differs from the grading server, their submission fails.
>
> In Caliper, students code directly in an optimized browser workspace powered by CodeMirror 6. The instructor's starter code is automatically loaded, and problem constraints are visible right alongside the editor.
>
> Pedagogically, students learn through hypothesis testing. That's why Caliper includes a dedicated **Custom Input Scratchpad**. A student can enter custom test data—like an empty array or negative numbers—and press `Ctrl+Enter`. In under 50 milliseconds, our backend spins up an ephemeral container, runs the code with strict resource limits, and prints the exact stdout and runtime metrics.
>
> When ready, the student clicks 'Run Tests' to validate their solution against public test cases. If their browser crashes or their laptop loses power, nothing is lost—every keystroke is automatically saved to browser local storage. Once satisfied, they click 'Submit Assignment', and their code is enqueued for formal grading against hidden test cases."

#### Transition Sentence
> *"Now, executing untrusted student code in the browser is dangerous if not properly contained. Let's look at how Caliper secures its execution pipeline."*

---

### Slide 5: The Execution Engine — Pluggable Container Sandboxing

- **Recommended Duration**: 60 seconds
- **Slide Title**: Execution Sandboxing: Multi-Layered Defense-in-Depth
- **What Appears on the Slide**:
  - **Core Rule**: Student code NEVER executes directly on the host operating system.
  - **Pluggable Execution Architecture**:
    - `ContainerRunner` interface abstracts container mechanics from grading logic.
    - `DockerSandboxProvider` (`runc`): Default high-performance Linux container isolation.
    - `GVisorSandboxProvider` (`runsc`): User-space kernel virtualization in Go; blocks host kernel exploit vectors.
  - **Defense-in-Depth Isolation Boundaries**:
    - Rootless execution: User `65534:65534` (`nobody:nogroup`).
    - Read-only root filesystem (`--read-only`).
    - Network disabled: `--network none` (zero socket egress or port scanning).
    - Cgroup limits: `--cpus=1.0`, `--memory=256m`, `--pids-limit=64` (completely neutralizes fork bombs).
    - Memory-backed scratchpad: `/tmp` tmpfs capped at 64MB with `noexec` and `nosuid`.
  - **Host Watchdog**: Host Java process enforces hard wall-clock timeouts with `SIGKILL`.
  - **Visual Suggestion**: Diagram of the host process communicating across the Docker socket to an isolated container with all security boundaries labeled.

#### Speaker Notes
> "When evaluating student code, we are executing completely untrusted programs. A submission might contain an accidental infinite loop, an intentional fork bomb, an attempt to scan local network ports, or malicious code probing `/etc/shadow`.
>
> Caliper enforces a non-negotiable rule: **student code never runs directly on the host machine**. All executions take place inside disposable containers governed by our `ContainerRunner` abstraction.
>
> We employ a multi-layered defense-in-depth model:
> 1. First, containers run rootless as user `nobody:65534`.
> 2. Second, the container root filesystem is mounted strictly read-only. Scratch files can only be written to an ephemeral 64MB memory-backed tmpfs mounted with `noexec` and `nosuid` flags.
> 3. Third, networking is completely severed with `--network none`. The container cannot ping internal services or reach external servers.
> 4. Fourth, hardware cgroups restrict the container to 1.0 CPU core, 256 megabytes of RAM, and a hard cap of 64 processes. A fork bomb immediately hits the process ceiling and crashes safely without degrading the host.
> 5. Finally, if code hangs, a host-level Java watchdog thread terminates the container with `SIGKILL` after 10 seconds.
>
> Furthermore, for hardened environments, Caliper supports Google gVisor via `CALIPER_SANDBOX_RUNTIME=gvisor`. This runs code inside a user-space guest kernel, eliminating shared kernel escape risks entirely."

#### Transition Sentence
> *"Beyond programming assignments, modern curricula require diverse assessments. Let's explore how Caliper structures question evaluation."*

---

### Slide 6: Assessment Architecture — Strategy Pattern & Quiz Grading

- **Recommended Duration**: 45 seconds
- **Slide Title**: Polymorphic Assessments: The Strategy Pattern in Action
- **What Appears on the Slide**:
  - **Ten Supported Question Formats**:
    - Fully Autograded: `MCQ`, `MULTI`, `TRUE_FALSE`, `SHORT`, `OUTPUT`, `BLANK`, `FILL_BLANK`.
    - Instructor & Sandboxed Review: `LONG`, `FILE`, `CODE`.
  - **Clean Object-Oriented Hierarchy**:
    - `QuestionGradingStrategy` interface defines polymorphic evaluation contracts.
    - `AbstractQuestionGradingStrategy` provides shared text normalization and decimal score scaling (`RoundingMode.HALF_UP`).
    - `ManualReviewGradingStrategy` encapsulates `LONG`, `FILE`, and `CODE` types cleanly.
  - **Spring-Managed Registry (`QuestionGraderRegistry`)**:
    - Discovers strategy beans automatically via Spring component scanning.
    - Validates and rejects duplicate strategy identifiers during initialization.
    - Injected directly into `QuizGrader` via constructor injection (Open/Closed Principle).
  - **Sandboxed Code Questions**: Code questions in quizzes are asynchronously queued and executed by `QuizCodeMarker` against test cases.
  - **Visual Suggestion**: Mermaid UML class diagram showing `QuizGrader` delegating to `QuestionGraderRegistry` and concrete strategies.

#### Speaker Notes
> "In computer science courses, assessments rarely consist of coding assignments alone. Instructors frequently mix conceptual multiple-choice questions, fill-in-the-blank tracing problems, program output predictions, and small coding questions.
>
> In earlier software architectures, grading multiple question formats led to fragile, monolithic `switch` statements. In Caliper, we solved this using the **Strategy Pattern**.
>
> We defined an explicit `QuestionGradingStrategy` interface, backed by an `AbstractQuestionGradingStrategy` base class that provides shared text normalization and mathematical score scaling with `HALF_UP` rounding.
>
> Each format—such as `McqGradingStrategy` or `MultiSelectGradingStrategy`—is an independent Spring component. Manual grading types (`LONG` essays, `FILE` uploads, and `CODE` solutions) are handled by `ManualReviewGradingStrategy`.
>
> All strategies are auto-discovered by our Spring-managed `QuestionGraderRegistry`, which rejects duplicate type registrations and injects directly into `QuizGrader`. When a university wants to add a new question type—such as Parsons problem blocks—they simply write a new strategy bean without touching a single line of `QuizGrader` or `QuizService`. That is the Open/Closed Principle implemented cleanly."

#### Transition Sentence
> *"Now, once submissions are graded, instructors face their most sensitive challenge: evaluating academic integrity with explainable evidence."*

---

### Slide 7: Plagiarism Detection — Dual-Engine Algorithmic Pipeline

- **Recommended Duration**: 60 seconds
- **Slide Title**: Explainable Similarity: The Dual-Engine Pipeline
- **What Appears on the Slide**:
  - **Core Philosophy**: Zero opaque AI/LLM models, zero cloud APIs, zero arbitrary percentages. Grounded in peer-reviewed computer science literature.
  - **Step 1: Starter Code Subtraction**:
    - Instructor boilerplate is tokenized and its $k$-grams are subtracted *before* indexing, preventing class-wide false positives.
  - **Step 2: Lexical Winnowing ($k=10, w=5$)**:
    - Sequential identifier normalization (`v0`, `v1`, `v2`...) strips cosmetic variable renames.
    - Rolling Karp-Rabin hashing guarantees that any shared sequence $\ge 14$ tokens is mathematically guaranteed to be detected.
  - **Step 3: Fast Inverted Indexing**:
    - Pre-filters candidate pairs using Dice similarity ($\ge 0.30$), pruning non-matching pairs in sub-second time.
  - **Step 4: Running Karp-Rabin Greedy String Tiling (RKR-GST, $m=8$)**:
    - Discovers maximal non-overlapping tiles of length $\ge 8$ tokens and maps them to 1-based source coordinates with rarity weighting.
  - **Step 5: Tree-sitter AST Structural Analysis**:
    - Native grammar parsers across 5 languages parse source into abstract syntax subtrees.
    - Erases variable names, formatting, and dummy functions to detect pure structural clones.
  - **Visual Suggestion**: 5-stage pipeline diagram showing source code flowing through Lexer/AST -> Starter Subtraction -> Winnowing -> Tiling -> Review Desk.

#### Speaker Notes
> "Let's turn to Caliper's plagiarism engine. Commercial similarity checkers often operate as black boxes, spitting out an opaque number that faculty cannot explain before an academic integrity appeals committee. Worse, generative AI checkers introduce hallucinations and non-deterministic results.
>
> Caliper is built on deterministic, peer-reviewed computer science algorithms. It combines two complementary engines:
>
> First, our **Lexical Winnowing Pipeline**. We start with **Starter Code Subtraction**: Caliper tokenizes the instructor's template and mathematically subtracts those $k$-grams before fingerprinting. This eliminates the artificial 40% similarity baseline caused by shared headers and boilerplate. Next, we normalize all identifiers sequentially (`v0`, `v1`), strip comments, and apply rolling Karp-Rabin hashing with $k=10$ and window $w=5$. By the Winnowing theorem, any shared sequence of 14 tokens or greater is mathematically guaranteed to be captured. Candidate pairs passing our 0.30 Dice cutoff undergo deep Greedy String Tiling to extract exact, non-overlapping matching tiles.
>
> Second, our **AST Structural Pipeline**. Using native Java bindings for Tree-sitter, Caliper parses source code into abstract syntax trees across C, C++, Java, Python, and JavaScript. This engine abstracts away all variable names, comments, and formatting to hash syntax subtrees. Even if a student renames every variable, wraps statements in helper functions, or inverts loops, the underlying syntactic structure remains detectable.
>
> Both scores are surfaced side-by-side, giving faculty comprehensive forensic evidence."

#### Transition Sentence
> *"Let's see how instructors interact with this forensic evidence on the Review Desk."*

---

### Slide 8: The Review Desk — Explainable Evidence & Academic Ethics

- **Recommended Duration**: 60 seconds
- **Slide Title**: The Review Desk: Forensic Evidence, Not Accusations
- **What Appears on the Slide**:
  - **Global Triage Queue (`/reviews`)**:
    - Categorizes candidate pairs into actionable risk tiers: `HIGH_PRIORITY_REVIEW`, `NEEDS_REVIEW`, and `LOW_SIMILARITY`.
  - **Side-by-Side Synchronized Inspection (`/pairs/:id`)**:
    - Synchronized line scrolling: clicking a match in Student A automatically highlights and scrolls to the corresponding block in Student B.
    - Toggle between Lexical Token Highlights and Tree-sitter AST Structural Node alignments.
  - **Formal PDF Hearing Dossiers**:
    - One-click export of formal academic integrity hearing dossiers generated via OpenPDF.
    - Includes case metadata, dual scores, side-by-side code excerpts, and institutional disclaimers.
  - **The Core Ethical Boundary**:
    - An algorithm must never convict a student of academic misconduct.
    - Caliper surfaces evidence for human faculty evaluation; instructors record formal determinations (`CLEARED`, `FALSE_POSITIVE`, `INVESTIGATING`, `CONFIRMED_VIOLATION`).
  - **Visual Suggestion**: Screenshot of the side-by-side pair comparison view with highlighted matching blocks and the 'PDF Dossier' export button highlighted.

#### Speaker Notes
> "Here is the Instructor Review Desk in action.
>
> When an instructor opens the similarity review for an assignment, they don't see an unranked list of hundreds of files. Caliper triages candidate pairs into explicit risk tiers: High Priority Review, Needs Review, and Low Similarity.
>
> Clicking 'Inspect Pair' opens our side-by-side inspection view. On the left is Student A; on the right is Student B. Shared tiles are visually highlighted. When the instructor clicks a highlighted tile in Student A's code, Student B's pane smoothly scrolls to the exact matching logic block, displaying the tile length and rarity score.
>
> In the top bar, the instructor can toggle between **Lexical Tokens** and **AST Structural Nodes** to see how the syntax trees align.
>
> When a case must go before an academic integrity committee, the instructor clicks **PDF Dossier**. In one click, Caliper generates a formal, printable PDF document complete with student IDs, timestamped audit metadata, dual similarity metrics, side-by-side code excerpts, and formal university disclaimers.
>
> Crucially, Caliper respects a strict ethical boundary: **an algorithm must never convict a student**. High similarity can occur naturally from standard problem constraints or authorized collaboration. Caliper is an investigation signal that empowers qualified faculty to make informed, defensible decisions."

#### Transition Sentence
> *"Behind this responsive interface lies a disciplined, resilient backend architecture. Let's look at how the system is engineered."*

---

### Slide 9: Systems Engineering — Modular Monolith & Concurrency Safety

- **Recommended Duration**: 45 seconds
- **Slide Title**: Systems Architecture: Resilient, Lock-Free & Scalable
- **What Appears on the Slide**:
  - **Clean Multi-Module Monolith**:
    - `engine`: Pure Java algorithmic library (zero Spring, zero database dependencies).
    - `judge`: Container orchestration & sandbox lifecycle (zero Spring, zero database dependencies).
    - `backend`: Spring Boot 3.3.5 REST API, security filters, and background worker queue.
    - `frontend`: React 18, TypeScript 5.6, and Vite Single Page Application.
  - **Lock-Free Job Queue (PostgreSQL `SKIP LOCKED`)**:
    - `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1`: Atomic, contention-free task claiming across worker instances.
    - Zero external message broker dependencies (no RabbitMQ, Redis, or Kafka overhead).
    - Automatic orphan recovery: detects and recovers stale jobs if a worker crashes.
  - **Database Evolution**:
    - 8 versioned Flyway migrations managing 17 relational tables with zero Hibernate overhead.
  - **LMS Interoperability**:
    - 1EdTech LTI 1.3 Advantage Core: OIDC login initiation, RSA-2048 JWKS distribution, anti-replay nonces, and automated AGS grade passback.
  - **Visual Suggestion**: Systems topology diagram showing the React frontend, Spring Boot API, PostgreSQL `SKIP LOCKED` task queue, and worker nodes.

#### Speaker Notes
> "Let's look under the hood at our systems engineering.
>
> Caliper is architected as a modular monolith with strict Maven boundary enforcement:
> - The `engine` module is pure computational Java. It contains all tokenizers, Winnowing, and AST algorithms, with zero dependencies on Spring or databases.
> - The `judge` module handles container execution and language adapters, also completely free of Spring dependencies.
> - The `backend` module coordinates REST routing, security, and persistence.
> - And the `frontend` is a modern React and TypeScript Single Page Application.
>
> For background job processing, rather than introducing heavyweight external message brokers like RabbitMQ or Kafka, Caliper implements a lock-free distributed task queue directly inside PostgreSQL using `SELECT ... FOR UPDATE SKIP LOCKED`.
>
> When a student submits code, a job row is inserted. Worker threads poll the database and atomically claim jobs without lock contention. If a worker process is abruptly killed, Caliper's heartbeat recovery mechanism automatically re-queues stale jobs after 10 minutes. This guarantees ACID transactional safety, exactly-once execution, and horizontal worker scalability with zero external broker maintenance.
>
> Furthermore, database persistence uses Spring's `JdbcClient` over 8 versioned Flyway migrations, avoiding the memory leaks and N+1 query traps common in heavyweight ORM frameworks."

#### Transition Sentence
> *"Throughout this codebase, software engineering best practices and object-oriented principles are strictly observed. Let's examine our design patterns."*

---

### Slide 10: Object-Oriented Design & SOLID in Practice

- **Recommended Duration**: 45 seconds
- **Slide Title**: Object-Oriented Architecture: Genuine SOLID Principles
- **What Appears on the Slide**:
  - **Encapsulation**:
    - Shallowly immutable Java 21 `record` constructs for domain data transfer objects (`AssignmentDto`, `CourseDto`, `StudentDto`, `StaffDto`).
    - Repository boundaries encapsulating relational queries (`CourseRepository`, `AssignmentRepository`).
  - **Abstraction & Decoupling**:
    - Explicit interfaces isolate core ports: `ContainerRunner`, `FileStorageProvider`, `DossierExporter`, `LanguageAdapter`.
  - **Inheritance**:
    - Behavioral reuse in `AbstractQuestionGradingStrategy` (`#scale()`, `+normalize()`).
    - Interface inheritance in `SandboxExecutionProvider extends ContainerRunner`.
  - **Polymorphism & Strategy Registries**:
    - `QuestionGraderRegistry` and `DossierExportRegistry` dispatch runtime strategies dynamically via Spring Dependency Injection.
  - **Accurate Language Adapter Architecture**:
    - `CompiledNativeAdapter` and `ScriptAdapter` are parameterized `final` concrete classes with static factories (`c()`, `cpp()`, `python()`, `javascript()`), avoiding invented class hierarchies.
  - **Visual Suggestion**: High-level UML diagram illustrating key abstraction interfaces and concrete implementing classes.

#### Speaker Notes
> "Caliper was engineered as a reference implementation of object-oriented architecture and SOLID design principles.
>
> Let's look at how the four pillars are genuinely demonstrated in the code:
> - **Encapsulation**: We utilize Java 21 `record` constructs for all data transfer objects, request commands, and projections. Records provide shallowly immutable data carriers with generated accessors. At the storage layer, repository interfaces like `CourseRepository` and `AssignmentRepository` decouple controllers from SQL queries.
> - **Abstraction**: Subsystems interact across cohesive interfaces: `FileStorageProvider` abstracts physical disk writes; `ContainerRunner` abstracts Docker execution; and `DossierExporter` abstracts document serialization across PDF, HTML, and JSON.
> - **Inheritance**: We apply inheritance where genuine behavioral reuse exists, such as `AbstractQuestionGradingStrategy` providing shared normalization and rounding methods to concrete grading classes. We deliberately avoided inventing fictitious subclass hierarchies: for example, C and C++ are instances of `CompiledNativeAdapter` parameterized via static factory methods.
> - **Polymorphism & Dependency Inversion**: `QuizGrader` receives `QuestionGraderRegistry` through constructor injection, resolving question strategies dynamically at runtime without procedural branching.
>
> Every pattern in Caliper solves an authentic architectural requirement."

#### Transition Sentence
> *"How do we verify that these systems function correctly? Let's inspect our test suite and platform coverage."*

---

### Slide 11: Testing, Verification & Platform Support

- **Recommended Duration**: 45 seconds
- **Slide Title**: Verification: 238 Passing Automated Tests & Cross-Platform Support
- **What Appears on the Slide**:
  - **238 Passing Automated Tests Across Four Suites**:
    - `engine` (75 tests): Lexical tokenizers, identifier normalizers, Winnowing, RKR-GST tiling, AST parsing, and synthetic accuracy benchmarks.
    - `judge` (50 tests): Docker `runc` execution, gVisor provider, cross-platform path translation, timeout enforcement, and memory caps.
    - `backend` (74 tests): End-to-end acceptance scenarios, Spring application-context DI wiring, LTI 1.3 Advantage, rate limiting, and quiz strategy execution.
    - `frontend` (39 tests): Vitest unit tests covering editor helpers, quiz runner, charts, and role-based navigation.
  - **Multi-Platform Engineering**:
    - Linux (Ubuntu 24.04): Full automated CI matrix running PostgreSQL, container builds, and the full test suite.
    - Windows 11/10: 8 production PowerShell scripts in `scripts/windows/`, path normalization for Docker Desktop WSL2, and automated engine/judge/frontend CI validation.
  - **Visual Suggestion**: Test suite breakdown diagram showing the four test suites totaling 238 green tests alongside the GitHub Actions CI badge.

#### Speaker Notes
> "Software quality cannot be asserted through claims; it must be proven through automated testing.
>
> Caliper is verified by **238 automated tests** passing across our entire multi-module codebase:
> - **75 tests in `engine`** validate our lexical tokenizers, identifier normalization, Winnowing math, and Tree-sitter AST structural analysis.
> - **50 tests in `judge`** exercise container execution, path normalization, timeout handling, and memory cap enforcement.
> - **74 tests in `backend`** test end-to-end acceptance workflows against a live PostgreSQL database, LTI 1.3 grade passback, OpenPDF report generation, rate limiting, and our newly refactored `QuizGrader` dependency injection.
> - **39 tests in `frontend`** run under Vitest to verify editor behavior, draft persistence, and role-aware navigation.
>
> Caliper also features first-class cross-platform engineering. On Windows, we provide 8 dedicated PowerShell scripts with syntax validation and automatic path translation in `DockerRunner` for Docker Desktop on WSL2. Our GitHub Actions CI matrix continuously validates both Linux and Windows runners."

#### Transition Sentence
> *"To conclude, let's review our known operational boundaries and our vision for the future."*

---

### Slide 12: Technical Boundaries, Future Roadmap & Conclusion

- **Recommended Duration**: 45 seconds
- **Slide Title**: Real Boundaries, Future Horizon & Conclusion
- **What Appears on the Slide**:
  - **Honest Technical Boundaries**:
    - Single-host storage: `LocalFileStorageProvider` stores files on local worker disk; multi-node clusters require shared NFS or S3 integration.
    - Heuristic limits: Syntax-level comparison detects reordering and renaming, but cannot detect algorithmic paradigm rewrites (e.g., iterative loop converted to recursive memoization).
    - Ancillary services: While course and assignment persistence is decoupled, services like `QuizService` and `PlagiarismService` still execute SQL queries via `JdbcClient`.
  - **Future Engineering Roadmap**:
    - S3 / MinIO distributed object storage adapter.
    - Hardware-enforced microVM isolation using AWS Firecracker.
    - Live Webhook push notifications for LMS gradebooks.
  - **Closing Principle**: Restoring trust to computer science education through precision, transparency, and human academic governance.
  - **Visual Suggestion**: Summary table contrasting current implemented capabilities with future roadmap extensions.

#### Speaker Notes
> "We believe technical credibility requires complete honesty about operational boundaries.
>
> Today, Caliper operates with three documented boundaries:
> 1. First, file storage uses `LocalFileStorageProvider` on local disk. A distributed multi-node cluster would require mounting an NFS volume or adding an S3-compatible object storage provider.
> 2. Second, while our dual-engine similarity analysis catches variable renaming, statement padding, and syntax restructuring, it operates at the lexical and syntactic level. It does not detect profound semantic paradigm shifts, such as rewriting an iterative algorithm into recursive dynamic programming.
> 3. Third, while `CourseRepository` and `AssignmentRepository` decouple persistence cleanly, several ancillary services still interact directly with `JdbcClient`.
>
> In our future roadmap, we plan to introduce S3 object storage adapters, explore AWS Firecracker microVM execution, and add real-time webhook gradebook notifications.
>
> In conclusion, Caliper restores trust to computer science education. It gives students a responsive, empowering workspace to build and test their ideas, and it equips instructors with an explainable, deterministic precision instrument to evaluate code with fairness and integrity.
>
> Thank you, and we welcome your questions."

---

# Section 2: Practical Live Demonstration Script

*Recommended Demo Duration: 4 to 5 Minutes total (2 min Student, 2 min Instructor, 1 min Q&A buffer).*
*Follow this exact sequence of screens and actions.*

---

### Pre-Demo Checklist (T-Minus 15 Minutes)

Before beginning the demonstration, verify each item:

- [ ] **Docker Engine**: Verify Docker daemon is running (`docker info >/dev/null`).
- [ ] **Runner Images**: Confirm all 4 runner images exist:
  ```bash
  docker images | grep codesentinel/runner
  # Expected: runner-native:1, runner-java:1, runner-python:1, runner-node:1
  ```
- [ ] **Database & Backend**: Confirm PostgreSQL is active and backend is running on `http://127.0.0.1:8080`:
  ```bash
  curl -fs http://127.0.0.1:8080/actuator/health
  # Expected: {"status":"UP"}
  ```
- [ ] **Frontend Dev Server**: Running on `http://localhost:5173`.
- [ ] **Browser Windows**:
  - **Window 1 (Standard Window)**: Pre-loaded at `http://localhost:5173/login` for the Instructor demo.
  - **Window 2 (Incognito Window)**: Pre-loaded at `http://localhost:5173/login` for the Student demo.
  - Zoom set to **110%** for high projection visibility.

---

### Part A: Student Workflow Walkthrough (2 Minutes)

| Step | Route / Action | What Presenter Does | Spoken Script | What Audience Observes |
|:---:|:---|:---|:---|:---|
| **1** | `/login` | Click the **Student** helper button (or type `student1@demo.invalid` / `demo-password-1`) and click **Sign In**. | *"I'm logging in as Aarav, a student in CS201. Notice our clean, role-aware dashboard."* | Instant redirect to Student Dashboard showing enrolled courses. |
| **2** | Top Navigation | Click **Coding Workspace** (`/workspace`). | *"In Caliper, students don't need to configure local compilers. The Workspace Hub shows all active assignments and persistent drafts."* | Workspace hub displays active assignments: *Array Statistics*, *Binary Search*, etc. |
| **3** | `/workspace` | Locate **Array Statistics** and click **Open Workspace** (`/assignments/1/code`). | *"Let's open Array Statistics. The problem description and constraints are on the left, CodeMirror editor on the right with starter code pre-loaded."* | Multi-language editor loads with C++ starter code. |
| **4** | `/assignments/1/code` | In **Custom Input**, enter `12 45 7 89 23` and press `Ctrl+Enter` (or click **Run Scratchpad**). | *"Students learn through hypothesis testing. Watch as I run custom input through our scratchpad."* | Console executes in ~40ms. Stdout prints: `Min: 7, Max: 89, Mean: 35.2`. |
| **5** | `/assignments/1/code` | Click **Run Tests** button. | *"Now let's validate against official public test cases before submitting."* | Test comparison table renders with green checkmarks for Test 1 and Test 2. |
| **6** | `/assignments/1/code` | Click **Submit Assignment**. | *"Confident in our solution, we submit. The job is queued in PostgreSQL and evaluated against hidden test cases in an isolated container."* | Badge transitions to `ACCEPTED` with score `100/100`. |

---

### Part B: Instructor Workflow Walkthrough (2 Minutes)

| Step | Route / Action | What Presenter Does | Spoken Script | What Audience Observes |
|:---:|:---|:---|:---|:---|
| **1** | `/login` | In Window 1, log in as `demo-instructor@demo.invalid` / `demo-password-1`. | *"Now switching to Dr. Ananya Sharma, the course instructor. Notice the elevated navigation bar with Similarity Review and Question Bank."* | Redirects to Instructor Dashboard with 'Urgent Similarity Reviews' banner. |
| **2** | Top Navigation | Click **Similarity Review** (`/reviews`). | *"The Global Similarity Desk triages all course assignments into risk tiers rather than dumping raw file lists."* | Triage table displays comparison runs with risk badges (`HIGH_PRIORITY_REVIEW`). |
| **3** | `/reviews` | Point to **Starter Code Subtracted** badge and click **Review Candidates** on *Array Statistics*. | *"Notice this badge: Starter Code Subtracted. Caliper stripped the instructor's template $k$-grams before fingerprinting, stopping boilerplate false alarms."* | Navigates to `/assignments/1/plagiarism`. Candidate pairs listed with Dice scores. |
| **4** | `/assignments/1/plagiarism` | Click **Inspect Pair** on the top flagged pair (`/pairs/:id`). | *"Let's inspect the top candidate pair. Student B renamed variables and reorganized loops, but let's see what Caliper uncovers."* | Side-by-side inspection view loads: Student A on the left, Student B on the right. |
| **5** | `/pairs/:id` | Point to matched token blocks. Click the **AST Structural Nodes** toggle. | *"Sequential identifier normalization matched the lexical tiles. When I switch to AST mode, Tree-sitter reveals identical syntax tree subtrees."* | Highlighting shifts from lexical tokens to syntactic subtree blocks. |
| **6** | `/pairs/:id` | Click on line 24 of Student A's code. | *"Watch synchronized line mapping: clicking a block in Student A immediately scrolls and highlights the matching block in Student B."* | Both panes scroll synchronously to the shared logic block. |
| **7** | `/pairs/:id` | Click the **PDF Dossier** button in the top right. | *"If this case must be defended before an academic hearing board, one click generates a formal, printable PDF audit dossier."* | Browser downloads `caliper-audit-report-pair-:id.pdf`. Opens high-res PDF with metadata and evidence. |
| **8** | `/pairs/:id` | Select `INVESTIGATING` from the determination dropdown, enter a faculty note, and click **Save Determination**. | *"Finally, our ethical rule: algorithms never convict students. The instructor records a human academic determination and note."* | Green confirmation toast appears. Determination is saved permanently to PostgreSQL. |

---

### Emergency Fallback Procedures (Failure Recovery Matrix)

If an unexpected failure occurs during the live demonstration, **execute these immediate recovery protocols**:

| Failure Scenario | Root Cause | What Presenter Should Say & Do |
|:---|:---|:---|
| **Docker execution times out or hangs** | Host under heavy CPU load or container queue stalled. | **Say**: *"The container has hit its execution timeout. Notice how our host watchdog enforces strict execution bounds to prevent infinite loops from hanging the server."*<br/>**Action**: Re-run with a smaller input or proceed to public tests. |
| **Docker daemon becomes unavailable** | Docker Desktop crashed or socket permission dropped. | **Say**: *"The backend detected that Docker is unreachable. Notice that Caliper fails safely: the submission receives a graceful 'Judge Problem' status rather than executing insecurely on the host."*<br/>**Action**: Open the pre-generated screenshot at `docs/screenshots/caliper-student-workspace-hub.png`. |
| **Similarity run takes longer than expected** | Background worker queue is currently processing jobs. | **Say**: *"The similarity engine processes comparisons asynchronously via our PostgreSQL task queue. Let's inspect an already-completed run on Array Statistics."*<br/>**Action**: Click an existing completed assignment comparison row. |
| **Complete machine or presentation crash** | Hardware freeze or display disconnect. | **Say**: *"While our local instance reboots, let's open the architectural specification and inspect the genuine high-resolution system captures in our documentation."*<br/>**Action**: Open `docs/screenshots/` and walk through the captured UI views. |

---

# Section 3: Evaluator & Audience Q&A Preparation

*Technically rigorous answers to critical evaluator questions, verified against the source code.*

---

### 1. Why was Caliper built when commercial tools like Gradescope and Turnitin already exist?
> **Answer**: Commercial platforms suffer from three severe flaws in computer science education:
> 1. **Fragmented toolchains**: Universities must pay for an LMS, a separate autograder charging per-submission fees, and a disconnected plagiarism checker. Caliper unifies coding, scratchpad testing, sandboxed grading, and similarity analysis in one platform.
> 2. **The "Black Box" plagiarism problem**: Commercial checkers output an arbitrary percentage (e.g., "82%") without showing token alignment or subtracting boilerplate starter code. This leads to false accusations that cannot be legally or academically defended.
> 3. **Data sovereignty & privacy**: Caliper requires zero external AI APIs or cloud tracking. Student source code stays strictly on institutional servers, complying with FERPA and GDPR.

### 2. How does code execution work from a systems perspective?
> **Answer**: Untrusted code is managed by the `judge` module via our `ContainerRunner` abstraction. When code is submitted:
> 1. The backend writes the source files and test inputs into a job-scoped temporary directory (`/tmp/cs-job-XXXX/`).
> 2. `DockerRunner` spawns an ephemeral container using the appropriate language runner image (`codesentinel/runner-{native,java,python,node}:1`).
> 3. The container executes with `--network none`, `--read-only` root filesystem, `--user 65534:65534`, and strict cgroups limits (1.0 CPU, 256MB RAM, `--pids-limit=64`).
> 4. Stdout and stderr are captured with a 64KB excerpt cap.
> 5. A host-level Java thread monitors wall-clock time; if the timeout expires, it issues a `SIGKILL`.
> 6. The container is automatically deleted (`--rm`), and the temporary host directory is cleaned up in a `finally` block.

### 3. How are test cases evaluated, and how do hidden tests stay hidden?
> **Answer**: In `Judge.java`, testcases are evaluated sequentially:
> 1. For each test, the container is invoked with the test input fed via stdin.
> 2. The program stdout is captured, normalized (CRLF and trailing whitespace trimmed), and compared against the expected output using `VerdictClassifier`.
> 3. If outputs match, it records `ACCEPTED`; otherwise `WRONG_ANSWER`, `TIME_LIMIT_EXCEEDED`, `MEMORY_LIMIT_EXCEEDED`, or `RUNTIME_ERROR`.
> 4. **Hidden test isolation**: When student APIs return submission evaluations (`SubmissionController`), student JSON projections explicitly null out the inputs, expected outputs, and program excerpts for hidden test cases. Hidden test names are obfuscated as "Hidden test N". Hidden test data is never leaked to the client.

### 4. How does the plagiarism detector identify similarity?
> **Answer**: Caliper operates a hybrid dual-engine pipeline:
> - **Lexical Winnowing**: Normalizes all identifiers sequentially (`v0`, `v1`...), strips whitespace and comments, and hashes consecutive tokens ($k=10$). A sliding window of size $w=5$ selects minimum hashes. By the Winnowing theorem, any shared sequence $\ge 14$ tokens is guaranteed to be detected. Candidate pairs passing our 0.30 Dice threshold undergo Running Karp-Rabin Greedy String Tiling ($m=8$) to extract exact, non-overlapping matching tiles.
> - **Tree-sitter AST Structural Analysis**: Uses native Java Tree-sitter parsers to construct abstract syntax trees across C, C++, Java, Python, and JavaScript. It erases variable names, comments, and literals, hashing syntax subtrees of size $\ge 3$ to detect structural clones and statement reordering.

### 5. How do you avoid treating two correct solutions as plagiarism merely because they are similar?
> **Answer**: Through three complementary mechanisms:
> 1. **Starter Code Subtraction**: The instructor's template code is tokenized first, and all its $k$-grams are subtracted prior to candidate indexing. Shared headers and boilerplate loops are completely eliminated.
> 2. **Rarity Weighting**: In `RkrGst.java`, tiles that occur frequently across the entire class (such as standard loop increments or swap idioms) receive lower weight than rare, idiosyncratic logic blocks.
> 3. **Candidate Cutoffs & Human Review**: Pairs below 0.30 Dice similarity are discarded. Most importantly, Caliper never assigns automatic penalties—similarity is strictly an investigation signal for qualified faculty review.

### 6. Which Object-Oriented Programming concepts are demonstrated, and where?
> **Answer**: Real OOP principles are implemented across all modules:
> - **Encapsulation**: Domain records (`AssignmentDto`, `CourseDto`, `StudentDto`, `StaffDto`) provide shallowly immutable data carriers with private final fields and compiler-generated accessors. Persistence logic is encapsulated in `CourseRepository` and `AssignmentRepository`.
> - **Abstraction**: Ports isolate infrastructure mechanics: `ContainerRunner` abstracts container execution; `FileStorageProvider` abstracts physical disk writes; `DossierExporter` abstracts document serialization; `LanguageAdapter` abstracts compiler commands.
> - **Inheritance**: Behavioral reuse in `AbstractQuestionGradingStrategy` (`#scale()` rounding and `+normalize()` methods) extended by concrete grading strategies. Interface inheritance in `SandboxExecutionProvider extends ContainerRunner`.
> - **Polymorphism**: `QuestionGraderRegistry` and `DossierExportRegistry` dispatch runtime strategies dynamically via Spring Dependency Injection without procedural branching.

### 7. Why use strategy-based grading instead of simple switch statements?
> **Answer**: In `QuizGrader`, using switch statements violated the Open/Closed Principle: every time a new question type was added, the core grader and service classes had to be modified. By introducing `QuestionGradingStrategy`, each question type is an independent `@Component` bean. Adding a new question type (e.g., Parsons problems) requires only writing a new strategy bean; Spring automatically registers it into `QuestionGraderRegistry`, requiring zero modifications to existing grading logic.

### 8. How are execution risks and sandbox security handled?
> **Answer**: Untrusted code execution is secured via multi-layered defense-in-depth:
> - Cgroups enforce strict resource caps (1 CPU core, 256MB RAM, 64 PIDs) to prevent denial of service and fork bombs.
> - The root filesystem is read-only, preventing system tampering.
> - Networking is disabled (`--network none`) to prevent reverse shells and data exfiltration.
> - Rootless execution (`nobody:65534`) and dropped Linux capabilities prevent privilege escalation.
> - For maximum hardening, Google gVisor (`runsc`) runs submissions in a user-space guest kernel written in Go, eliminating shared kernel escape risks.

### 9. What are the current limitations of the platform?
> **Answer**: We document three genuine technical boundaries:
> 1. **Single-host local storage**: Submissions are stored on local worker disk via `LocalFileStorageProvider`. A distributed multi-node cluster requires shared NFS or S3 integration.
> 2. **Heuristic code comparison**: While Tree-sitter AST structural analysis catches variable renamings and function reordering, semantic paradigm shifts (e.g. converting an iterative loop into recursive memoization) cannot be detected by syntax-level comparison.
> 3. **Direct SQL access in ancillary services**: While course and assignment persistence is decoupled via repositories, services like `QuizService` and `PlagiarismService` still execute SQL queries directly via `JdbcClient`.

### 10. What would you improve next?
> **Answer**: Our immediate engineering roadmap focuses on:
> 1. Adding an S3/MinIO distributed object storage provider to support multi-node worker clusters.
> 2. Implementing AWS Firecracker microVM execution for hardware-isolated sub-millisecond evaluation.
> 3. Completing repository decoupling for remaining domain services (`QuizService`, `PlagiarismService`).
> 4. Adding real-time Webhook push notifications for LMS gradebook synchronization.

---

# Section 4: Presenter Quick-Reference Fact Sheet

*Keep this summary accessible during the presentation for rapid technical reference.*

| Category | Key Fact / Metric | Source Code Location |
|:---|:---|:---|
| **Automated Tests** | **238 total passing**: Engine 75, Judge 50, Backend 74, Frontend 39 | `mvn test` & `npm test` |
| **Java Platform** | Java 21 LTS / Java 25 compatible, Maven 3.9+ | `pom.xml` |
| **Backend Framework** | Spring Boot 3.3.5, Spring Security JWT, Spring `JdbcClient` | `backend/pom.xml` |
| **Frontend Stack** | React 18.3, TypeScript 5.6, Vite 5.4, CodeMirror 6 | `frontend/package.json` |
| **Database** | PostgreSQL 16+, 8 Flyway migrations, 17 tables | `backend/.../db/migration/` |
| **Job Queue** | PostgreSQL `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 1` | `JobWorker.java` |
| **Supported Languages** | C (gcc C11), C++ (g++ C++17), Java 21, Python 3.12, Node.js 20 | `Adapters.java` |
| **Sandbox Runtimes** | Docker `runc` (default) & Google gVisor `runsc` | `judge/.../sandbox/` |
| **Sandbox Limits** | `--network none`, `--read-only`, `--cpus 1.0`, `--memory 256m`, `--pids-limit 64`, `--user 65534:65534` | `DockerRunner.java` |
| **Lexical Similarity** | Winnowing ($k=10, w=5$, min match $L=14$), Dice cutoff $\ge 0.30$, RKR-GST ($m=8$) | `engine/.../` |
| **AST Similarity** | Tree-sitter native bindings, syntax subtree size $\ge 3$ | `AstStructuralSimilarity.java` |
| **Report Export** | OpenPDF 2.2.2 (PDF), HTML, JSON via `DossierExportRegistry` | `plagiarism/export/` |
| **LMS Integration** | 1EdTech LTI 1.3 Advantage Core, RSA-2048 JWKS, AGS Grade Passback | `lti/` |
| **Instructor Account** | `demo-instructor@demo.invalid` / `demo-password-1` | `DemoSeedRunner.java` |
| **Student Account** | `student1@demo.invalid` / `demo-password-1` | `DemoCatalogSeeder.java` |
| **Default Ports** | Backend: `http://127.0.0.1:8080`, Frontend: `http://localhost:5173` | `.env.example` |

---

# Section 5: Final Closing Statement

*(To be delivered by the lead presenter with quiet confidence)*

> "Evaluators,
>
> At its core, computer science education is built on trust: a student's trust that their effort is evaluated fairly, and an instructor's trust that submitted work represents genuine understanding.
>
> For too long, educational software has eroded that trust with fragmented tools, opaque autograders, and black-box plagiarism algorithms that confuse boilerplate code with academic dishonesty.
>
> Caliper restores that trust.
>
> It gives students a clean, responsive, in-browser workspace to build and test their ideas with confidence. And it equips instructors with a precision instrument—transparent, deterministic, and grounded in rigorous computer science—to evaluate code with evidence, speed, and fairness.
>
> Thank you."
