ALTER TABLE imported_question
    DROP CONSTRAINT imported_question_content_release_id_external_question_id_e_key;

ALTER TABLE imported_question
    ADD CONSTRAINT uq_imported_question_release_external_id
    UNIQUE (content_release_id, external_question_id);

ALTER TABLE imported_answer_option
    ADD COLUMN content_release_id UUID;

UPDATE imported_answer_option option
SET content_release_id = question.content_release_id
FROM imported_question question
WHERE question.id = option.question_id;

ALTER TABLE imported_answer_option
    ALTER COLUMN content_release_id SET NOT NULL,
    ADD CONSTRAINT fk_imported_answer_option_release
        FOREIGN KEY (content_release_id) REFERENCES imported_content_release(id),
    ADD CONSTRAINT uq_imported_answer_option_release_external_id
        UNIQUE (content_release_id, external_answer_option_id);
