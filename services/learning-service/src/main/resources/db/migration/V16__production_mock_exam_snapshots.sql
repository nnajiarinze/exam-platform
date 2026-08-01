-- Mock attempts own immutable copies of every learner-visible/scoring field.
-- Imported release rows remain the source used at creation time only.
ALTER TABLE mock_exam_blueprint
    ALTER COLUMN duration_minutes DROP NOT NULL,
    DROP CONSTRAINT IF EXISTS mock_exam_blueprint_duration_minutes_check,
    ADD CONSTRAINT ck_mock_blueprint_duration
        CHECK (duration_minutes IS NULL OR duration_minutes > 0);

ALTER TABLE mock_exam_attempt
    ALTER COLUMN duration_minutes DROP NOT NULL,
    ALTER COLUMN expires_at DROP NOT NULL;

ALTER TABLE mock_exam_question
    ADD COLUMN question_external_id VARCHAR(200),
    ADD COLUMN prompt_snapshot TEXT,
    ADD COLUMN question_type_snapshot VARCHAR(30),
    ADD COLUMN explanation_snapshot TEXT,
    ADD COLUMN topic_external_id VARCHAR(200),
    ADD COLUMN topic_name_snapshot VARCHAR(500),
    ADD COLUMN objective_external_id VARCHAR(200),
    ADD COLUMN objective_name_snapshot VARCHAR(500),
    ADD COLUMN lesson_topic_external_id VARCHAR(200);

UPDATE mock_exam_question q SET
    question_external_id = iq.external_question_version_id,
    prompt_snapshot = iq.prompt,
    question_type_snapshot = iq.question_type,
    explanation_snapshot = iq.explanation,
    topic_external_id = t.external_topic_id,
    topic_name_snapshot = t.name,
    -- The v1 learning projection does not expose objective IDs separately.
    -- The immutable grounded Fact ID is the stable objective coverage key.
    objective_external_id = iq.knowledge_fact_id,
    objective_name_snapshot = t.name,
    lesson_topic_external_id = t.external_topic_id
FROM imported_question iq
JOIN imported_topic t ON t.id = iq.topic_id
WHERE iq.id = q.imported_question_id;

ALTER TABLE mock_exam_question
    ALTER COLUMN question_external_id SET NOT NULL,
    ALTER COLUMN prompt_snapshot SET NOT NULL,
    ALTER COLUMN question_type_snapshot SET NOT NULL,
    ALTER COLUMN explanation_snapshot SET NOT NULL,
    ALTER COLUMN topic_external_id SET NOT NULL,
    ALTER COLUMN topic_name_snapshot SET NOT NULL,
    ALTER COLUMN objective_external_id SET NOT NULL,
    ALTER COLUMN objective_name_snapshot SET NOT NULL,
    ALTER COLUMN lesson_topic_external_id SET NOT NULL;

CREATE TABLE mock_exam_option_snapshot (
    attempt_question_id UUID NOT NULL REFERENCES mock_exam_question(id),
    option_id VARCHAR(200) NOT NULL,
    option_text TEXT NOT NULL,
    correct BOOLEAN NOT NULL,
    feedback TEXT,
    display_order INTEGER NOT NULL CHECK (display_order > 0),
    PRIMARY KEY (attempt_question_id, option_id),
    UNIQUE (attempt_question_id, display_order)
);

INSERT INTO mock_exam_option_snapshot
    (attempt_question_id, option_id, option_text, correct, feedback, display_order)
SELECT q.id, o.external_answer_option_id, o.text, o.correct, o.feedback, ordering.position::integer
FROM mock_exam_question q
JOIN LATERAL jsonb_array_elements_text(q.option_order)
     WITH ORDINALITY ordering(option_id, position) ON TRUE
JOIN imported_answer_option o
  ON o.question_id = q.imported_question_id
 AND o.external_answer_option_id = ordering.option_id;

CREATE TABLE mock_exam_objective_result (
    attempt_id UUID NOT NULL REFERENCES mock_exam_attempt(id),
    objective_id VARCHAR(200) NOT NULL,
    objective_name VARCHAR(500) NOT NULL,
    question_count INTEGER NOT NULL CHECK (question_count > 0),
    correct_count INTEGER NOT NULL CHECK (correct_count >= 0),
    incorrect_count INTEGER NOT NULL CHECK (incorrect_count >= 0),
    unanswered_count INTEGER NOT NULL CHECK (unanswered_count >= 0),
    percentage NUMERIC(5,2) NOT NULL CHECK (percentage BETWEEN 0 AND 100),
    PRIMARY KEY (attempt_id, objective_id)
);

CREATE INDEX idx_mock_question_topic_snapshot
    ON mock_exam_question (attempt_id, topic_external_id);
CREATE INDEX idx_mock_question_objective_snapshot
    ON mock_exam_question (attempt_id, objective_external_id);

-- The active definition is runtime configuration, not curriculum content. Topic
-- allocations are derived from each active release so release upgrades need no
-- definition rewrite.
INSERT INTO mock_exam_blueprint
    (id, exam_id, name, total_questions, duration_minutes, passing_percentage,
     active, created_at, updated_at, description, randomize_questions,
     randomize_options, version)
VALUES
    ('6d2c2328-b5cc-4fe1-8904-e150b699242e', 'sverige-i-fokus-v1',
     'Citizenship Exam v1', 30, NULL, 70.00, TRUE, now(), now(),
     'A complete untimed mock examination covering every major curriculum area.',
     TRUE, TRUE, 1)
ON CONFLICT DO NOTHING;
