-- Migration V8: AST Structural Similarity analysis support

ALTER TABLE similarity_pairs ADD COLUMN ast_structural_score DOUBLE PRECISION NOT NULL DEFAULT 0.0;

CREATE TABLE structural_fragments (
    id            BIGSERIAL PRIMARY KEY,
    pair_id       BIGINT       NOT NULL REFERENCES similarity_pairs(id) ON DELETE CASCADE,
    file_a        VARCHAR(200) NOT NULL,
    start_line_a  INT NOT NULL,
    end_line_a    INT NOT NULL,
    file_b        VARCHAR(200) NOT NULL,
    start_line_b  INT NOT NULL,
    end_line_b    INT NOT NULL,
    node_type     VARCHAR(80)  NOT NULL,
    node_count    INT NOT NULL
);

CREATE INDEX ix_structural_fragments_pair ON structural_fragments (pair_id);
