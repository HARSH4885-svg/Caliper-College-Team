-- Classroom expansion: richer courses, co-instructors, assessment settings, multi-question assignments,
-- a reusable question bank, and quiz attempts with per-question grading.

ALTER TABLE courses
    ADD COLUMN description TEXT NOT NULL DEFAULT '',
    ADD COLUMN subject     VARCHAR(80) NOT NULL DEFAULT '',
    ADD COLUMN category    VARCHAR(60) NOT NULL DEFAULT '',
    ADD COLUMN starts_on   DATE,
    ADD COLUMN ends_on     DATE;

-- Co-instructors and teaching assistants have the same course permissions as the lead instructor, except deleting the course.
CREATE TABLE course_staff (
    course_id  BIGINT      NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id    BIGINT      NOT NULL REFERENCES users(id),
    role       VARCHAR(16) NOT NULL DEFAULT 'CO_INSTRUCTOR' CHECK (role IN ('CO_INSTRUCTOR','TA')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (course_id, user_id)
);

CREATE FUNCTION teaches(course BIGINT, uid BIGINT) RETURNS BOOLEAN LANGUAGE sql STABLE AS $$
    SELECT EXISTS (SELECT 1 FROM courses WHERE id = course AND instructor_id = uid)
        OR EXISTS (SELECT 1 FROM course_staff WHERE course_id = course AND user_id = uid)
$$;

ALTER TABLE assignments DROP CONSTRAINT assignments_language_check;
ALTER TABLE assignments ADD CONSTRAINT assignments_language_check CHECK (language IN ('c','cpp','java','python','javascript','none'));
ALTER TABLE assignments
    ADD COLUMN kind              VARCHAR(8)   NOT NULL DEFAULT 'CODING' CHECK (kind IN ('CODING','QUIZ')),
    ADD COLUMN category          VARCHAR(30)  NOT NULL DEFAULT 'ASSIGNMENT' CHECK (category IN ('ASSIGNMENT','QUIZ','LAB','EXAM','PRACTICE')),
    ADD COLUMN difficulty        VARCHAR(8)   NOT NULL DEFAULT 'MEDIUM' CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    ADD COLUMN max_marks         NUMERIC(7,2) NOT NULL DEFAULT 100 CHECK (max_marks > 0),
    ADD COLUMN passing_marks     NUMERIC(7,2),
    ADD COLUMN available_from    TIMESTAMPTZ,
    ADD COLUMN max_attempts      INT          NOT NULL DEFAULT 20 CHECK (max_attempts BETWEEN 1 AND 50),
    ADD COLUMN late_policy       VARCHAR(8)   NOT NULL DEFAULT 'BLOCK' CHECK (late_policy IN ('BLOCK','ALLOW','PENALTY')),
    ADD COLUMN late_penalty_pct  INT          NOT NULL DEFAULT 0 CHECK (late_penalty_pct BETWEEN 0 AND 100),
    ADD COLUMN late_until        TIMESTAMPTZ,
    ADD COLUMN quiz_minutes      INT          CHECK (quiz_minutes BETWEEN 1 AND 600),
    ADD COLUMN shuffle_questions BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN shuffle_options   BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN release_mode      VARCHAR(10)  NOT NULL DEFAULT 'IMMEDIATE' CHECK (release_mode IN ('IMMEDIATE','AFTER_DUE','MANUAL')),
    ADD COLUMN show_answers      BOOLEAN      NOT NULL DEFAULT TRUE;

CREATE TABLE question_bank (
    id          BIGSERIAL PRIMARY KEY,
    owner_id    BIGINT       NOT NULL REFERENCES users(id),
    type        VARCHAR(12)  NOT NULL,
    prompt      TEXT         NOT NULL,
    marks       NUMERIC(6,2) NOT NULL,
    difficulty  VARCHAR(8)   NOT NULL DEFAULT 'MEDIUM',
    subject     VARCHAR(80)  NOT NULL DEFAULT '',
    topic       VARCHAR(80)  NOT NULL DEFAULT '',
    options     JSONB        NOT NULL DEFAULT '[]',
    answer      JSONB        NOT NULL DEFAULT '{}',
    settings    JSONB        NOT NULL DEFAULT '{}',
    explanation TEXT         NOT NULL DEFAULT '',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_bank_owner ON question_bank (owner_id, subject, topic);

CREATE TABLE questions (
    id            BIGSERIAL PRIMARY KEY,
    assignment_id BIGINT       NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    ord           INT          NOT NULL DEFAULT 0,
    type          VARCHAR(12)  NOT NULL CHECK (type IN ('MCQ','MULTI','TRUE_FALSE','SHORT','LONG','FILL_BLANK','OUTPUT','FILE')),
    prompt        TEXT         NOT NULL,
    marks         NUMERIC(6,2) NOT NULL CHECK (marks >= 0),
    difficulty    VARCHAR(8)   NOT NULL DEFAULT 'MEDIUM' CHECK (difficulty IN ('EASY','MEDIUM','HARD')),
    required      BOOLEAN      NOT NULL DEFAULT TRUE,
    options       JSONB        NOT NULL DEFAULT '[]',
    answer        JSONB        NOT NULL DEFAULT '{}',
    settings      JSONB        NOT NULL DEFAULT '{}',
    explanation   TEXT         NOT NULL DEFAULT '',
    bank_id       BIGINT       REFERENCES question_bank(id) ON DELETE SET NULL
);
CREATE INDEX ix_questions_assignment ON questions (assignment_id, ord);

CREATE TABLE quiz_attempts (
    id             BIGSERIAL PRIMARY KEY,
    assignment_id  BIGINT       NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    student_id     BIGINT       NOT NULL REFERENCES users(id),
    attempt        INT          NOT NULL,
    status         VARCHAR(12)  NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS','SUBMITTED','GRADED','RETURNED')),
    started_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    submitted_at   TIMESTAMPTZ,
    late           BOOLEAN      NOT NULL DEFAULT FALSE,
    score          NUMERIC(7,2),
    released       BOOLEAN      NOT NULL DEFAULT FALSE,
    feedback       TEXT         NOT NULL DEFAULT '',
    private_note   TEXT         NOT NULL DEFAULT '',
    question_order JSONB        NOT NULL DEFAULT '[]',
    synthetic      BOOLEAN      NOT NULL DEFAULT FALSE,
    UNIQUE (assignment_id, student_id, attempt)
);
CREATE INDEX ix_attempts_assignment ON quiz_attempts (assignment_id, student_id);

CREATE TABLE question_responses (
    attempt_id   BIGINT       NOT NULL REFERENCES quiz_attempts(id) ON DELETE CASCADE,
    question_id  BIGINT       NOT NULL REFERENCES questions(id) ON DELETE CASCADE,
    response     JSONB        NOT NULL DEFAULT '{}',
    auto_score   NUMERIC(6,2),
    manual_score NUMERIC(6,2),
    feedback     TEXT         NOT NULL DEFAULT '',
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (attempt_id, question_id)
);

-- Instructor-entered marks and comments for coding submissions (the automatic score stays in evaluation_results).
ALTER TABLE submissions
    ADD COLUMN late            BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN override_score  NUMERIC(7,2),
    ADD COLUMN feedback        TEXT         NOT NULL DEFAULT '',
    ADD COLUMN private_note    TEXT         NOT NULL DEFAULT '';
