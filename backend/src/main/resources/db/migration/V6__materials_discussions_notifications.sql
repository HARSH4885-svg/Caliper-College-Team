-- Course materials grouped into modules, course discussions, and in-app notifications.
CREATE TABLE course_materials (
    id          BIGSERIAL PRIMARY KEY,
    course_id   BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    author_id   BIGINT       NOT NULL REFERENCES users(id),
    module      VARCHAR(100) NOT NULL DEFAULT '',
    title       VARCHAR(150) NOT NULL,
    kind        VARCHAR(8)   NOT NULL CHECK (kind IN ('LINK','FILE','NOTE')),
    url         VARCHAR(1000),
    body        TEXT         NOT NULL DEFAULT '',
    storage_key VARCHAR(300),
    file_name   VARCHAR(160),
    size_bytes  BIGINT,
    ord         INT          NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_materials_course ON course_materials (course_id, module, ord);

CREATE TABLE discussion_threads (
    id            BIGSERIAL PRIMARY KEY,
    course_id     BIGINT       NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    author_id     BIGINT       NOT NULL REFERENCES users(id),
    title         VARCHAR(150) NOT NULL,
    body          TEXT         NOT NULL,
    pinned        BOOLEAN      NOT NULL DEFAULT FALSE,
    locked        BOOLEAN      NOT NULL DEFAULT FALSE,
    hidden        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    last_activity TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_threads_course ON discussion_threads (course_id, pinned DESC, last_activity DESC);

CREATE TABLE discussion_posts (
    id         BIGSERIAL PRIMARY KEY,
    thread_id  BIGINT      NOT NULL REFERENCES discussion_threads(id) ON DELETE CASCADE,
    author_id  BIGINT      NOT NULL REFERENCES users(id),
    body       TEXT        NOT NULL,
    endorsed   BOOLEAN     NOT NULL DEFAULT FALSE,
    hidden     BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_posts_thread ON discussion_posts (thread_id, created_at);

CREATE TABLE discussion_reports (
    id          BIGSERIAL PRIMARY KEY,
    thread_id   BIGINT       NOT NULL REFERENCES discussion_threads(id) ON DELETE CASCADE,
    post_id     BIGINT       REFERENCES discussion_posts(id) ON DELETE CASCADE,
    reporter_id BIGINT       NOT NULL REFERENCES users(id),
    reason      VARCHAR(300) NOT NULL,
    resolved    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind       VARCHAR(20)  NOT NULL,
    title      VARCHAR(200) NOT NULL,
    body       VARCHAR(500) NOT NULL DEFAULT '',
    link       VARCHAR(300) NOT NULL DEFAULT '',
    dedupe     VARCHAR(100),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    read_at    TIMESTAMPTZ
);
CREATE INDEX ix_notifications_user ON notifications (user_id, created_at DESC);
CREATE UNIQUE INDEX ux_notifications_dedupe ON notifications (user_id, dedupe) WHERE dedupe IS NOT NULL;

-- Kinds a user has switched off. Absent row = on.
CREATE TABLE notification_mutes (
    user_id BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    kind    VARCHAR(20) NOT NULL,
    PRIMARY KEY (user_id, kind)
);
