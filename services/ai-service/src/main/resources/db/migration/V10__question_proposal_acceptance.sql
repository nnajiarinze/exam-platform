ALTER TABLE ai_question_proposal DROP CONSTRAINT ck_ai_question_proposal_status;
ALTER TABLE ai_question_proposal
  ADD CONSTRAINT ck_ai_question_proposal_status CHECK (status IN ('PROPOSED','REJECTED','ACCEPTED')),
  ADD COLUMN accepted_question_id uuid,
  ADD COLUMN accepted_by varchar(200),
  ADD COLUMN accepted_at timestamptz,
  ADD COLUMN acceptance_validation_checksum char(64),
  ADD CONSTRAINT uq_ai_question_proposal_accepted_question UNIQUE (accepted_question_id),
  ADD CONSTRAINT ck_ai_question_proposal_acceptance CHECK (
    (status='ACCEPTED' AND accepted_question_id IS NOT NULL AND accepted_by IS NOT NULL AND accepted_at IS NOT NULL)
    OR (status<>'ACCEPTED' AND accepted_question_id IS NULL AND accepted_by IS NULL AND accepted_at IS NULL)
  );

CREATE INDEX ix_ai_question_proposal_acceptance
  ON ai_question_proposal(status, accepted_question_id);
