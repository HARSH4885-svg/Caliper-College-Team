# Design diagrams (Mermaid)

## 1. Use cases
```mermaid
flowchart LR
  I([Instructor]) --> UC1[Manage courses and enrollment code]
  I --> UC2[Create assignment, tests, starter/reference files]
  I --> UC3[View submissions and evaluations]
  I --> UC4[Run similarity comparison]
  I --> UC5[Review pair side by side and record decision]
  S([Student]) --> UC6[Register with enrollment code]
  S --> UC7[View assignment and sample tests]
  S --> UC8[Submit file or ZIP]
  S --> UC9[View own verdict and history]
  W([Worker]) --> UC10[Judge queued submissions in containers]
```

## 2. Component view
```mermaid
flowchart TB
  FE[React SPA] -->|REST + Bearer JWT| API[Spring Boot API]
  API --> DB[(PostgreSQL)]
  API --> FS[[FileStorageProvider: Local]]
  API --> ENG[engine library]
  W[JobWorker] --> DB
  W --> FS
  W --> JDG[judge library]
  JDG -->|docker run, one job dir| C[(Ephemeral runner container)]
```

## 3. Submission and judging (sequence)
```mermaid
sequenceDiagram
  participant S as Student UI
  participant A as API
  participant D as PostgreSQL
  participant W as JobWorker
  participant J as Judge
  participant C as Container
  S->>A: POST /assignments/{id}/submissions (file|zip)
  A->>A: authorise, deadline, validate, safe extract
  A->>D: submission + files + evaluation_job(QUEUED)
  A-->>S: 200 (status QUEUED)
  W->>D: claim job (FOR UPDATE SKIP LOCKED)
  W->>J: judge(request)
  J->>C: compile (job dir rw, no network)
  alt compile fails
    J-->>W: CE + compiler log
  else ok
    loop each test
      J->>C: run (job dir ro, stdin = test input)
      C-->>J: exit, stdout, stderr, OOM flag
    end
  end
  W->>D: save results, job DONE
  S->>A: GET /submissions/{id}/evaluation (polling)
```

## 4. Similarity pipeline
```mermaid
flowchart LR
  A[Source files of latest attempts] --> B[Tokenize per language]
  B --> C[Normalize: IDs, literals, includes]
  C --> D[Remove starter-code windows]
  D --> E[k-gram Rabin-Karp hashes]
  E --> F[Winnowing fingerprints]
  F --> G[Inverted index: candidate pairs]
  G --> H[Dice + rarity gate]
  H --> I[RKR-GST on candidates only]
  I --> J[Fragments to line ranges]
  J --> K[Triage state + explanation]
  K --> L[(Persist run, pairs, fragments)]
  L --> M[Instructor reviews, decision recorded]
```

## 5. Triage states
```mermaid
stateDiagram-v2
  [*] --> INSUFFICIENT_EVIDENCE: too few informative tokens
  [*] --> NO_MEANINGFUL_MATCH
  [*] --> LOW_SIMILARITY
  [*] --> NEEDS_REVIEW: distinctive overlap >= review threshold
  NEEDS_REVIEW --> HIGH_PRIORITY_REVIEW: score, Dice, tokens and rarity gates all met
  note right of NEEDS_REVIEW: Engine state is never edited. A human decision is stored separately.
```

## 6. Key classes
```mermaid
classDiagram
  class PlagiarismEngine { <<interface>> +analyze(AnalysisRequest) AnalysisResult }
  class SimilarityEngine
  class LanguageTokenizer { <<interface>> +tokenize(source,fileName) }
  class CFamilyTokenizer
  class PythonTokenizer
  PlagiarismEngine <|.. SimilarityEngine
  LanguageTokenizer <|.. CFamilyTokenizer
  LanguageTokenizer <|.. PythonTokenizer
  SimilarityEngine --> LanguageTokenizer
  class ContainerRunner { <<interface>> +run(ContainerSpec) RunResult }
  class DockerRunner
  class LanguageAdapter { <<interface>> +plan(files,dir,mem,ms) BuildPlan }
  class Judge
  ContainerRunner <|.. DockerRunner
  Judge --> ContainerRunner
  Judge --> LanguageAdapter
  class FileStorageProvider { <<interface>> }
  class LocalFileStorageProvider
  FileStorageProvider <|.. LocalFileStorageProvider
```

## 7. Entity-relationship
```mermaid
erDiagram
  users ||--o{ courses : instructs
  courses ||--o{ enrollments : has
  users ||--o{ enrollments : joins
  courses ||--o{ assignments : has
  assignments ||--o{ assignment_files : has
  assignments ||--o{ test_cases : has
  assignments ||--o{ submissions : receives
  users ||--o{ submissions : makes
  submissions ||--o{ submission_files : has
  submissions ||--o{ evaluation_jobs : queued_as
  submissions ||--o| evaluation_results : yields
  evaluation_results ||--o{ testcase_results : has
  assignments ||--o{ plagiarism_runs : analysed_by
  plagiarism_runs ||--o{ similarity_pairs : produces
  similarity_pairs ||--o{ matched_fragments : evidenced_by
  similarity_pairs ||--o{ plagiarism_reviews : reviewed_in
  users ||--o{ plagiarism_reviews : records
```
