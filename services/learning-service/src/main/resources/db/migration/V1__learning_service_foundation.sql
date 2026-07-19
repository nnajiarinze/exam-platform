CREATE TABLE learner_profile (
    id UUID PRIMARY KEY,
    external_identity_id VARCHAR(200) NOT NULL UNIQUE,
    display_name VARCHAR(200),
    interface_language VARCHAR(35) NOT NULL,
    explanation_language VARCHAR(35) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE imported_content_release (
    id UUID PRIMARY KEY,
    external_release_id VARCHAR(200) NOT NULL UNIQUE,
    exam_id VARCHAR(200) NOT NULL,
    exam_version_id VARCHAR(200) NOT NULL,
    release_version VARCHAR(100) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'SUPERSEDED', 'FAILED')),
    published_at TIMESTAMPTZ NOT NULL,
    imported_at TIMESTAMPTZ NOT NULL,
    failure_reason VARCHAR(500),
    UNIQUE (exam_version_id, release_version)
);

CREATE UNIQUE INDEX uq_active_release_per_exam_version
    ON imported_content_release (exam_version_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_release_exam_active ON imported_content_release (exam_id, status);

CREATE TABLE imported_subject (
    id UUID PRIMARY KEY,
    external_subject_id VARCHAR(200) NOT NULL,
    content_release_id UUID NOT NULL REFERENCES imported_content_release(id),
    name VARCHAR(500) NOT NULL,
    sort_order INTEGER NOT NULL,
    UNIQUE (content_release_id, external_subject_id)
);

CREATE TABLE imported_topic (
    id UUID PRIMARY KEY,
    external_topic_id VARCHAR(200) NOT NULL,
    subject_id UUID NOT NULL REFERENCES imported_subject(id),
    content_release_id UUID NOT NULL REFERENCES imported_content_release(id),
    name VARCHAR(500) NOT NULL,
    description TEXT,
    sort_order INTEGER NOT NULL,
    UNIQUE (content_release_id, external_topic_id)
);
CREATE INDEX idx_topic_release ON imported_topic (content_release_id);

CREATE TABLE imported_question (
    id UUID PRIMARY KEY,
    external_question_id VARCHAR(200) NOT NULL,
    external_question_version_id VARCHAR(200) NOT NULL,
    content_release_id UUID NOT NULL REFERENCES imported_content_release(id),
    topic_id UUID NOT NULL REFERENCES imported_topic(id),
    knowledge_fact_id VARCHAR(200) NOT NULL,
    question_type VARCHAR(30) NOT NULL CHECK (question_type = 'SINGLE_CHOICE'),
    prompt TEXT NOT NULL,
    explanation TEXT NOT NULL,
    language VARCHAR(35) NOT NULL,
    difficulty VARCHAR(50),
    active BOOLEAN NOT NULL,
    UNIQUE (content_release_id, external_question_version_id),
    UNIQUE (content_release_id, external_question_id, external_question_version_id)
);
CREATE INDEX idx_question_release_topic_active ON imported_question (content_release_id, topic_id, active);

CREATE TABLE imported_answer_option (
    id UUID PRIMARY KEY,
    external_answer_option_id VARCHAR(200) NOT NULL,
    question_id UUID NOT NULL REFERENCES imported_question(id),
    text TEXT NOT NULL,
    correct BOOLEAN NOT NULL,
    sort_order INTEGER NOT NULL,
    UNIQUE (question_id, external_answer_option_id),
    UNIQUE (question_id, sort_order)
);
CREATE UNIQUE INDEX uq_one_correct_option ON imported_answer_option (question_id) WHERE correct;

CREATE TABLE practice_session (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner_profile(id),
    exam_id VARCHAR(200) NOT NULL,
    content_release_id UUID NOT NULL REFERENCES imported_content_release(id),
    topic_id UUID REFERENCES imported_topic(id),
    mode VARCHAR(30) NOT NULL CHECK (mode IN ('TOPIC', 'MIXED', 'INCORRECT_REVIEW')),
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'COMPLETED', 'ABANDONED')),
    started_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ
);
CREATE INDEX idx_session_learner ON practice_session (learner_id, started_at DESC);

CREATE TABLE practice_session_question (
    id UUID PRIMARY KEY,
    practice_session_id UUID NOT NULL REFERENCES practice_session(id),
    imported_question_id UUID NOT NULL REFERENCES imported_question(id),
    sequence_number INTEGER NOT NULL,
    answered BOOLEAN NOT NULL DEFAULT FALSE,
    UNIQUE (practice_session_id, imported_question_id),
    UNIQUE (practice_session_id, sequence_number)
);

CREATE TABLE practice_response (
    id UUID PRIMARY KEY,
    practice_session_id UUID NOT NULL REFERENCES practice_session(id),
    practice_session_question_id UUID NOT NULL REFERENCES practice_session_question(id),
    learner_id UUID NOT NULL REFERENCES learner_profile(id),
    selected_answer_option_id UUID NOT NULL REFERENCES imported_answer_option(id),
    correct BOOLEAN NOT NULL,
    answered_at TIMESTAMPTZ NOT NULL,
    response_time_millis BIGINT CHECK (response_time_millis IS NULL OR response_time_millis >= 0),
    UNIQUE (practice_session_question_id)
);

CREATE TABLE topic_progress (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner_profile(id),
    topic_id UUID NOT NULL REFERENCES imported_topic(id),
    questions_answered INTEGER NOT NULL CHECK (questions_answered >= 0),
    correct_answers INTEGER NOT NULL CHECK (correct_answers >= 0 AND correct_answers <= questions_answered),
    accuracy_percentage NUMERIC(5,2) NOT NULL CHECK (accuracy_percentage BETWEEN 0 AND 100),
    last_practised_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (learner_id, topic_id)
);

CREATE TABLE bookmark (
    id UUID PRIMARY KEY,
    learner_id UUID NOT NULL REFERENCES learner_profile(id),
    imported_question_id UUID NOT NULL REFERENCES imported_question(id),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (learner_id, imported_question_id)
);
