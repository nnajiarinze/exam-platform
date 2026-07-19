CREATE INDEX idx_active_release_exam_published
    ON imported_content_release (exam_id, published_at DESC)
    WHERE status = 'ACTIVE';

CREATE INDEX idx_subject_release_sort
    ON imported_subject (content_release_id, sort_order, external_subject_id);

CREATE INDEX idx_topic_subject_sort
    ON imported_topic (subject_id, sort_order, external_topic_id);

CREATE INDEX idx_active_question_release
    ON imported_question (content_release_id, id)
    WHERE active;

ALTER TABLE imported_subject
    ADD CONSTRAINT uq_imported_subject_id_release UNIQUE (id, content_release_id);

ALTER TABLE imported_topic
    ADD CONSTRAINT uq_imported_topic_id_release UNIQUE (id, content_release_id),
    ADD CONSTRAINT fk_imported_topic_subject_release
        FOREIGN KEY (subject_id, content_release_id)
        REFERENCES imported_subject (id, content_release_id);

ALTER TABLE imported_question
    ADD CONSTRAINT uq_imported_question_id_release UNIQUE (id, content_release_id),
    ADD CONSTRAINT fk_imported_question_topic_release
        FOREIGN KEY (topic_id, content_release_id)
        REFERENCES imported_topic (id, content_release_id);

ALTER TABLE imported_answer_option
    ADD CONSTRAINT uq_imported_answer_option_id_question UNIQUE (id, question_id),
    ADD CONSTRAINT fk_imported_answer_option_question_release
        FOREIGN KEY (question_id, content_release_id)
        REFERENCES imported_question (id, content_release_id);

ALTER TABLE practice_session
    ADD CONSTRAINT uq_practice_session_id_release UNIQUE (id, content_release_id),
    ADD CONSTRAINT uq_practice_session_id_learner UNIQUE (id, learner_id),
    ADD CONSTRAINT fk_practice_session_topic_release
        FOREIGN KEY (topic_id, content_release_id)
        REFERENCES imported_topic (id, content_release_id);

ALTER TABLE practice_session_question
    ADD COLUMN content_release_id UUID;

UPDATE practice_session_question question
SET content_release_id = session.content_release_id
FROM practice_session session
WHERE session.id = question.practice_session_id;

ALTER TABLE practice_session_question
    ALTER COLUMN content_release_id SET NOT NULL,
    ADD CONSTRAINT uq_session_question_id_session UNIQUE (id, practice_session_id),
    ADD CONSTRAINT uq_session_question_id_question UNIQUE (id, imported_question_id),
    ADD CONSTRAINT fk_session_question_session_release
        FOREIGN KEY (practice_session_id, content_release_id)
        REFERENCES practice_session (id, content_release_id),
    ADD CONSTRAINT fk_session_question_question_release
        FOREIGN KEY (imported_question_id, content_release_id)
        REFERENCES imported_question (id, content_release_id);

ALTER TABLE practice_response
    ADD COLUMN imported_question_id UUID;

UPDATE practice_response response
SET imported_question_id = question.imported_question_id
FROM practice_session_question question
WHERE question.id = response.practice_session_question_id;

ALTER TABLE practice_response
    ALTER COLUMN imported_question_id SET NOT NULL,
    ADD CONSTRAINT fk_practice_response_session_learner
        FOREIGN KEY (practice_session_id, learner_id)
        REFERENCES practice_session (id, learner_id),
    ADD CONSTRAINT fk_practice_response_question_session
        FOREIGN KEY (practice_session_question_id, practice_session_id)
        REFERENCES practice_session_question (id, practice_session_id),
    ADD CONSTRAINT fk_practice_response_selected_question
        FOREIGN KEY (selected_answer_option_id, imported_question_id)
        REFERENCES imported_answer_option (id, question_id),
    ADD CONSTRAINT fk_practice_response_session_question
        FOREIGN KEY (practice_session_question_id, imported_question_id)
        REFERENCES practice_session_question (id, imported_question_id);
