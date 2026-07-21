ALTER TABLE mock_exam_blueprint
    ADD COLUMN description TEXT NOT NULL DEFAULT 'Timed mock examination',
    ADD COLUMN randomize_questions BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN randomize_options BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN max_attempts_per_day INTEGER CHECK (max_attempts_per_day IS NULL OR max_attempts_per_day > 0),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE mock_exam_attempt
    ADD COLUMN exam_id VARCHAR(200),
    ADD COLUMN expires_at TIMESTAMPTZ,
    ADD COLUMN auto_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN incorrect_count INTEGER CHECK (incorrect_count IS NULL OR incorrect_count >= 0),
    ADD COLUMN unanswered_count INTEGER CHECK (unanswered_count IS NULL OR unanswered_count >= 0),
    ADD COLUMN created_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE mock_exam_attempt attempt SET
    exam_id = release.exam_id,
    expires_at = attempt.started_at + attempt.duration_minutes * interval '1 minute',
    created_at = attempt.started_at,
    updated_at = COALESCE(attempt.completed_at, attempt.started_at),
    auto_submitted = attempt.status = 'EXPIRED',
    incorrect_count = CASE WHEN attempt.score IS NULL THEN NULL ELSE attempt.total_questions - attempt.score END,
    unanswered_count = CASE WHEN attempt.score IS NULL THEN NULL ELSE (
        SELECT count(*) FROM mock_exam_question q
        LEFT JOIN mock_exam_response r ON r.attempt_question_id = q.id
        WHERE q.attempt_id = attempt.id AND r.id IS NULL) END
FROM imported_content_release release
WHERE release.id = attempt.content_release_id;

ALTER TABLE mock_exam_attempt
    ALTER COLUMN exam_id SET NOT NULL,
    ALTER COLUMN expires_at SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

WITH ranked AS (
    SELECT id, row_number() OVER (PARTITION BY learner_id, exam_id ORDER BY started_at DESC, id) AS position
    FROM mock_exam_attempt WHERE status = 'ACTIVE'
)
UPDATE mock_exam_attempt attempt SET
    status = 'ABANDONED',
    completed_at = attempt.updated_at,
    updated_at = attempt.updated_at,
    version = attempt.version + 1
FROM ranked WHERE ranked.id = attempt.id AND ranked.position > 1;

CREATE UNIQUE INDEX uq_mock_exam_one_active_per_learner_exam
    ON mock_exam_attempt (learner_id, exam_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_mock_attempt_release ON mock_exam_attempt (content_release_id);
CREATE INDEX idx_mock_attempt_status_submitted ON mock_exam_attempt (status, submitted_at DESC);

ALTER TABLE mock_exam_question
    ADD COLUMN option_order JSONB,
    ADD COLUMN created_at TIMESTAMPTZ,
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

UPDATE mock_exam_question q SET
    option_order = (SELECT jsonb_agg(o.external_answer_option_id ORDER BY o.sort_order)
                    FROM imported_answer_option o WHERE o.question_id = q.imported_question_id),
    created_at = a.started_at,
    updated_at = a.started_at
FROM mock_exam_attempt a WHERE a.id = q.attempt_id;

ALTER TABLE mock_exam_question
    ALTER COLUMN option_order SET NOT NULL,
    ALTER COLUMN created_at SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

ALTER TABLE mock_exam_response
    ADD COLUMN updated_at TIMESTAMPTZ,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
UPDATE mock_exam_response SET updated_at = answered_at;
ALTER TABLE mock_exam_response ALTER COLUMN updated_at SET NOT NULL;

CREATE TABLE mock_exam_subject_result (
    attempt_id UUID NOT NULL REFERENCES mock_exam_attempt(id),
    subject_id VARCHAR(200) NOT NULL,
    subject_name VARCHAR(500) NOT NULL,
    question_count INTEGER NOT NULL CHECK (question_count > 0),
    correct_count INTEGER NOT NULL CHECK (correct_count >= 0),
    incorrect_count INTEGER NOT NULL CHECK (incorrect_count >= 0),
    unanswered_count INTEGER NOT NULL CHECK (unanswered_count >= 0),
    percentage NUMERIC(5,2) NOT NULL CHECK (percentage BETWEEN 0 AND 100),
    PRIMARY KEY (attempt_id, subject_id)
);

CREATE TABLE mock_exam_topic_result (
    attempt_id UUID NOT NULL REFERENCES mock_exam_attempt(id),
    topic_id VARCHAR(200) NOT NULL,
    topic_name VARCHAR(500) NOT NULL,
    question_count INTEGER NOT NULL CHECK (question_count > 0),
    correct_count INTEGER NOT NULL CHECK (correct_count >= 0),
    incorrect_count INTEGER NOT NULL CHECK (incorrect_count >= 0),
    unanswered_count INTEGER NOT NULL CHECK (unanswered_count >= 0),
    percentage NUMERIC(5,2) NOT NULL CHECK (percentage BETWEEN 0 AND 100),
    PRIMARY KEY (attempt_id, topic_id)
);
