-- Code-writing questions inside quizzes: judged asynchronously by the sandbox, like coding assignments.
ALTER TABLE questions DROP CONSTRAINT questions_type_check;
ALTER TABLE questions ADD CONSTRAINT questions_type_check CHECK (type IN ('MCQ','MULTI','TRUE_FALSE','SHORT','LONG','FILL_BLANK','OUTPUT','FILE','CODE'));
ALTER TABLE question_responses
    ADD COLUMN judge_status VARCHAR(8) CHECK (judge_status IN ('QUEUED','RUNNING','DONE')),
    ADD COLUMN judge_result JSONB;
CREATE INDEX ix_responses_judge ON question_responses (judge_status) WHERE judge_status IN ('QUEUED','RUNNING');
