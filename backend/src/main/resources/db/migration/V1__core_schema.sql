-- CodeSentinel core schema. Pair ordering is canonical (submission_a < submission_b) so a pair is stored once.
CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    email         VARCHAR(254) NOT NULL,
    display_name  VARCHAR(100) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(16)  NOT NULL CHECK (role IN ('INSTRUCTOR','STUDENT')),
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    synthetic     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

CREATE TABLE courses (
    id              BIGSERIAL PRIMARY KEY,
    code            VARCHAR(30)  NOT NULL,
    title           VARCHAR(150) NOT NULL,
    enrollment_code VARCHAR(40)  NOT NULL UNIQUE,
    instructor_id   BIGINT       NOT NULL REFERENCES users(id),
    synthetic       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE enrollments (
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    student_id BIGINT      NOT NULL REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (course_id, student_id)
);

CREATE TABLE assignments (
    id              BIGSERIAL PRIMARY KEY,
    course_id       BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title           VARCHAR(150) NOT NULL,
    description     TEXT         NOT NULL,
    language        VARCHAR(16)  NOT NULL CHECK (language IN ('c','cpp','java','python','javascript')),
    deadline        TIMESTAMPTZ  NOT NULL,
    time_limit_ms   INT          NOT NULL CHECK (time_limit_ms BETWEEN 100 AND 20000),
    memory_limit_mb INT          NOT NULL CHECK (memory_limit_mb BETWEEN 16 AND 1024),
    published       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- kind STARTER is visible to enrolled students; REFERENCE is instructor-only and is never returned by any student API.
CREATE TABLE assignment_files (
    id            BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT       NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    kind          VARCHAR(12)  NOT NULL CHECK (kind IN ('STARTER','REFERENCE')),
    name          VARCHAR(160) NOT NULL,
    storage_key   VARCHAR(300) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE test_cases (
    id              BIGSERIAL PRIMARY KEY,
    assignment_id   BIGINT       NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    name            VARCHAR(80)  NOT NULL,
    input           TEXT         NOT NULL DEFAULT '',
    expected_output TEXT         NOT NULL,
    sample          BOOLEAN      NOT NULL DEFAULT FALSE,
    ord             INT          NOT NULL DEFAULT 0
);

CREATE TABLE submissions (
    id            BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT      NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    student_id    BIGINT      NOT NULL REFERENCES users(id),
    attempt       INT         NOT NULL,
    storage_key   VARCHAR(300),
    upload_sha256 CHAR(64),
    status        VARCHAR(16) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
    synthetic     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (assignment_id, student_id, attempt)
);
CREATE INDEX ix_submissions_assignment ON submissions (assignment_id);

CREATE TABLE submission_files (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT       NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    path          VARCHAR(200) NOT NULL,
    storage_key   VARCHAR(300) NOT NULL,
    size_bytes    BIGINT       NOT NULL,
    sha256        CHAR(64)     NOT NULL
);
CREATE INDEX ix_submission_files_sub ON submission_files (submission_id);

CREATE TABLE evaluation_jobs (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    status        VARCHAR(10) NOT NULL DEFAULT 'QUEUED' CHECK (status IN ('QUEUED','RUNNING','DONE','FAILED')),
    attempts      INT         NOT NULL DEFAULT 0,
    locked_at     TIMESTAMPTZ,
    error         TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    finished_at   TIMESTAMPTZ
);
CREATE INDEX ix_jobs_status ON evaluation_jobs (status, id);

CREATE TABLE evaluation_results (
    id            BIGSERIAL PRIMARY KEY,
    submission_id BIGINT      NOT NULL UNIQUE REFERENCES submissions(id) ON DELETE CASCADE,
    verdict       VARCHAR(4)  NOT NULL,
    passed        INT         NOT NULL,
    total         INT         NOT NULL,
    message       TEXT,
    compile_log   TEXT,
    millis        BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE testcase_results (
    id            BIGSERIAL PRIMARY KEY,
    result_id     BIGINT      NOT NULL REFERENCES evaluation_results(id) ON DELETE CASCADE,
    name          VARCHAR(80) NOT NULL,
    sample        BOOLEAN     NOT NULL,
    verdict       VARCHAR(4)  NOT NULL,
    millis        BIGINT      NOT NULL,
    detail        TEXT,
    stdout_excerpt TEXT,
    stderr_excerpt TEXT
);

CREATE TABLE plagiarism_runs (
    id                BIGSERIAL PRIMARY KEY,
    assignment_id     BIGINT      NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    algorithm_version VARCHAR(200) NOT NULL,
    config            TEXT        NOT NULL,
    submissions       INT         NOT NULL,
    candidate_pairs   INT         NOT NULL,
    elapsed_ms        BIGINT      NOT NULL,
    notes             TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE similarity_pairs (
    id                 BIGSERIAL PRIMARY KEY,
    run_id             BIGINT      NOT NULL REFERENCES plagiarism_runs(id) ON DELETE CASCADE,
    assignment_id      BIGINT      NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    submission_a       BIGINT      NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    submission_b       BIGINT      NOT NULL REFERENCES submissions(id) ON DELETE CASCADE,
    state              VARCHAR(30) NOT NULL,
    distinctive_score  DOUBLE PRECISION NOT NULL,
    fingerprint_dice   DOUBLE PRECISION NOT NULL,
    weighted_dice      DOUBLE PRECISION NOT NULL,
    coverage_a         DOUBLE PRECISION NOT NULL,
    coverage_b         DOUBLE PRECISION NOT NULL,
    matched_tokens     INT         NOT NULL,
    distinctive_tokens INT         NOT NULL,
    longest_tile       INT         NOT NULL,
    suppressed_a       INT         NOT NULL,
    suppressed_b       INT         NOT NULL,
    rarity_active      BOOLEAN     NOT NULL,
    explanation        TEXT        NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (submission_a < submission_b)
);
CREATE INDEX ix_pairs_assignment ON similarity_pairs (assignment_id, run_id);

CREATE TABLE matched_fragments (
    id            BIGSERIAL PRIMARY KEY,
    pair_id       BIGINT       NOT NULL REFERENCES similarity_pairs(id) ON DELETE CASCADE,
    file_a        VARCHAR(200) NOT NULL,
    start_line_a  INT NOT NULL,
    end_line_a    INT NOT NULL,
    file_b        VARCHAR(200) NOT NULL,
    start_line_b  INT NOT NULL,
    end_line_b    INT NOT NULL,
    token_count   INT NOT NULL,
    rarity_weight DOUBLE PRECISION NOT NULL,
    distinctive   BOOLEAN NOT NULL
);

-- Instructor decision. Neutral wording; the engine state is never changed by a review.
CREATE TABLE plagiarism_reviews (
    id          BIGSERIAL PRIMARY KEY,
    pair_id     BIGINT      NOT NULL REFERENCES similarity_pairs(id) ON DELETE CASCADE,
    reviewer_id BIGINT      NOT NULL REFERENCES users(id),
    decision    VARCHAR(20) NOT NULL CHECK (decision IN ('CONFIRMED_CONCERN','DISMISSED','INCONCLUSIVE')),
    note        TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id          BIGSERIAL PRIMARY KEY,
    actor_id    BIGINT,
    action      VARCHAR(60) NOT NULL,
    entity_type VARCHAR(40),
    entity_id   BIGINT,
    detail      TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
