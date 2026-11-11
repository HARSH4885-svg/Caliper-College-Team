-- Caliper LTI 1.3 Advantage & Canvas LMS Integration Schema

CREATE TABLE lti_platforms (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    issuer        VARCHAR(512) NOT NULL UNIQUE,
    client_id     VARCHAR(255) NOT NULL,
    deployment_id VARCHAR(255),
    auth_url      VARCHAR(1024) NOT NULL,
    token_url     VARCHAR(1024) NOT NULL,
    jwks_url      VARCHAR(1024) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE lti_nonces (
    nonce      VARCHAR(255) PRIMARY KEY,
    state      VARCHAR(255) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_lti_nonces_expires ON lti_nonces (expires_at);

CREATE TABLE lti_course_mappings (
    id             BIGSERIAL PRIMARY KEY,
    platform_id    BIGINT NOT NULL REFERENCES lti_platforms(id) ON DELETE CASCADE,
    lti_context_id VARCHAR(255) NOT NULL,
    course_id      BIGINT NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, lti_context_id)
);

CREATE TABLE lti_resource_links (
    id               BIGSERIAL PRIMARY KEY,
    platform_id      BIGINT NOT NULL REFERENCES lti_platforms(id) ON DELETE CASCADE,
    resource_link_id VARCHAR(255) NOT NULL,
    assignment_id    BIGINT NOT NULL REFERENCES assignments(id) ON DELETE CASCADE,
    lineitem_url     VARCHAR(1024),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (platform_id, resource_link_id)
);
