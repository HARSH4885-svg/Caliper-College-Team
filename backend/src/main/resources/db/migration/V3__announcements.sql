-- Course announcements: a short stream the instructor posts to and enrolled students read.
CREATE TABLE announcements (
    id         BIGSERIAL PRIMARY KEY,
    course_id  BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    author_id  BIGINT       NOT NULL REFERENCES users(id),
    title      VARCHAR(150) NOT NULL,
    body       TEXT         NOT NULL,
    pinned     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_announcements_course ON announcements (course_id, pinned DESC, created_at DESC);
