CREATE TABLE mock_exam_blueprint (
    id UUID PRIMARY KEY,
    exam_id VARCHAR(200) NOT NULL,
    name VARCHAR(500) NOT NULL,
    total_questions INTEGER NOT NULL CHECK (total_questions > 0),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    passing_percentage NUMERIC(5,2) NOT NULL CHECK (passing_percentage BETWEEN 0 AND 100),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX uq_active_mock_blueprint_per_exam
    ON mock_exam_blueprint (exam_id) WHERE active;

CREATE TABLE mock_exam_topic_allocation (
    id UUID PRIMARY KEY,
    blueprint_id UUID NOT NULL REFERENCES mock_exam_blueprint(id),
    external_topic_id VARCHAR(200) NOT NULL,
    question_count INTEGER NOT NULL CHECK (question_count > 0),
    UNIQUE (blueprint_id, external_topic_id)
);
CREATE INDEX idx_mock_allocation_blueprint ON mock_exam_topic_allocation (blueprint_id);

CREATE TABLE mock_exam_attempt (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner_profile(id),
    blueprint_id UUID NOT NULL REFERENCES mock_exam_blueprint(id),
    content_release_id UUID NOT NULL REFERENCES imported_content_release(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUBMITTED', 'EXPIRED', 'ABANDONED')),
    started_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    duration_seconds INTEGER CHECK (duration_seconds IS NULL OR duration_seconds >= 0),
    score INTEGER CHECK (score IS NULL OR score >= 0),
    percentage NUMERIC(5,2) CHECK (percentage IS NULL OR percentage BETWEEN 0 AND 100),
    passed BOOLEAN,
    blueprint_name VARCHAR(500) NOT NULL,
    total_questions INTEGER NOT NULL CHECK (total_questions > 0),
    duration_minutes INTEGER NOT NULL CHECK (duration_minutes > 0),
    passing_percentage NUMERIC(5,2) NOT NULL CHECK (passing_percentage BETWEEN 0 AND 100),
    UNIQUE (id, learner_id),
    UNIQUE (id, content_release_id)
);
CREATE INDEX idx_mock_attempt_learner_history
    ON mock_exam_attempt (learner_id, started_at DESC);
CREATE INDEX idx_mock_attempt_active_expiry
    ON mock_exam_attempt (started_at)
    WHERE status = 'ACTIVE';

CREATE TABLE mock_exam_question (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL REFERENCES mock_exam_attempt(id),
    imported_question_id UUID NOT NULL REFERENCES imported_question(id),
    content_release_id UUID NOT NULL,
    sequence_number INTEGER NOT NULL CHECK (sequence_number > 0),
    flagged BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (attempt_id, imported_question_id),
    UNIQUE (attempt_id, sequence_number),
    UNIQUE (id, attempt_id),
    UNIQUE (id, imported_question_id),
    FOREIGN KEY (attempt_id, content_release_id)
        REFERENCES mock_exam_attempt(id, content_release_id),
    FOREIGN KEY (imported_question_id, content_release_id)
        REFERENCES imported_question(id, content_release_id)
);

CREATE TABLE mock_exam_response (
    id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    attempt_question_id UUID NOT NULL REFERENCES mock_exam_question(id),
    imported_question_id UUID NOT NULL,
    selected_answer_option_id UUID NOT NULL REFERENCES imported_answer_option(id),
    correct BOOLEAN NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL,
    UNIQUE (attempt_question_id),
    FOREIGN KEY (attempt_question_id, attempt_id)
        REFERENCES mock_exam_question(id, attempt_id),
    FOREIGN KEY (attempt_question_id, imported_question_id)
        REFERENCES mock_exam_question(id, imported_question_id),
    FOREIGN KEY (selected_answer_option_id, imported_question_id)
        REFERENCES imported_answer_option(id, question_id)
);
CREATE INDEX idx_mock_response_attempt ON mock_exam_response (attempt_id);
