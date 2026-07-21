ALTER TABLE imported_question DROP CONSTRAINT imported_question_question_type_check;
ALTER TABLE imported_question ADD CONSTRAINT imported_question_question_type_check
    CHECK (question_type IN ('SINGLE_CHOICE', 'MULTIPLE_CHOICE', 'TRUE_FALSE'));
DROP INDEX uq_one_correct_option;
ALTER TABLE imported_answer_option ADD COLUMN feedback TEXT;

ALTER TABLE practice_response ALTER COLUMN selected_answer_option_id DROP NOT NULL;
ALTER TABLE practice_response ADD CONSTRAINT uq_practice_response_id_question UNIQUE(id, imported_question_id);
CREATE TABLE practice_response_selection (
    practice_response_id UUID NOT NULL,
    imported_question_id UUID NOT NULL,
    answer_option_id UUID NOT NULL,
    PRIMARY KEY (practice_response_id, answer_option_id),
    FOREIGN KEY (practice_response_id, imported_question_id)
        REFERENCES practice_response(id, imported_question_id) ON DELETE CASCADE,
    FOREIGN KEY (answer_option_id, imported_question_id)
        REFERENCES imported_answer_option(id, question_id)
);
INSERT INTO practice_response_selection(practice_response_id, imported_question_id, answer_option_id)
SELECT id, imported_question_id, selected_answer_option_id FROM practice_response WHERE selected_answer_option_id IS NOT NULL;

ALTER TABLE mock_exam_response ALTER COLUMN selected_answer_option_id DROP NOT NULL;
ALTER TABLE mock_exam_response ADD CONSTRAINT uq_mock_response_id_question UNIQUE(id, imported_question_id);
CREATE TABLE mock_exam_response_selection (
    mock_exam_response_id UUID NOT NULL,
    imported_question_id UUID NOT NULL,
    answer_option_id UUID NOT NULL,
    PRIMARY KEY (mock_exam_response_id, answer_option_id),
    FOREIGN KEY (mock_exam_response_id, imported_question_id)
        REFERENCES mock_exam_response(id, imported_question_id) ON DELETE CASCADE,
    FOREIGN KEY (answer_option_id, imported_question_id)
        REFERENCES imported_answer_option(id, question_id)
);
INSERT INTO mock_exam_response_selection(mock_exam_response_id, imported_question_id, answer_option_id)
SELECT id, imported_question_id, selected_answer_option_id FROM mock_exam_response WHERE selected_answer_option_id IS NOT NULL;
