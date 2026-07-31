ALTER TABLE ai_lesson_page_revision ADD COLUMN idempotency_key VARCHAR(200);

CREATE UNIQUE INDEX uq_lesson_page_revision_idempotency
  ON ai_lesson_page_revision(lesson_proposal_id,page_index,idempotency_key)
  WHERE idempotency_key IS NOT NULL;
