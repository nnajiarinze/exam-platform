ALTER TABLE ai_question_proposal DROP CONSTRAINT ck_ai_question_proposal_status;
ALTER TABLE ai_question_proposal
  ADD CONSTRAINT ck_ai_question_proposal_status CHECK (status IN ('PROPOSED','REJECTED','ACCEPTED','SUPERSEDED')),
  ADD COLUMN rejection_reason_code varchar(40),
  ADD COLUMN reviewer_comment varchar(1000),
  ADD COLUMN root_proposal_id uuid REFERENCES ai_question_proposal(id),
  ADD COLUMN parent_proposal_id uuid REFERENCES ai_question_proposal(id),
  ADD COLUMN superseded_by_proposal_id uuid REFERENCES ai_question_proposal(id),
  ADD COLUMN generation_attempt integer NOT NULL DEFAULT 1,
  ADD COLUMN regenerated_by varchar(200),
  ADD COLUMN regenerated_at timestamptz,
  ADD COLUMN regeneration_feedback varchar(1000),
  ADD CONSTRAINT uq_ai_question_proposal_parent UNIQUE(parent_proposal_id),
  ADD CONSTRAINT uq_ai_question_proposal_successor UNIQUE(superseded_by_proposal_id),
  ADD CONSTRAINT ck_ai_question_proposal_attempt CHECK(generation_attempt >= 1),
  ADD CONSTRAINT ck_ai_question_proposal_rejection_code CHECK(rejection_reason_code IS NULL OR rejection_reason_code IN (
    'FACTUALLY_INCORRECT','AMBIGUOUS','DUPLICATE','POOR_DISTRACTORS','WRONG_CORRECT_ANSWER',
    'WRONG_DIFFICULTY','WRONG_BLOOM_LEVEL','WRONG_QUESTION_TYPE','UNSUPPORTED_BY_KNOWLEDGE_FACT',
    'UNSUPPORTED_BY_SOURCE','LANGUAGE_QUALITY','READABILITY','BIAS_OR_SAFETY','FORMAT_INVALID','OTHER'
  ));

ALTER TABLE ai_question_generation_detail
  ADD COLUMN regeneration_parent_proposal_id uuid REFERENCES ai_question_proposal(id),
  ADD COLUMN regeneration_root_proposal_id uuid REFERENCES ai_question_proposal(id),
  ADD COLUMN regeneration_feedback varchar(1000),
  ADD COLUMN regeneration_reason_code varchar(40),
  ADD COLUMN regenerated_by varchar(200),
  ADD CONSTRAINT uq_ai_question_regeneration_parent UNIQUE(regeneration_parent_proposal_id);

CREATE INDEX ix_ai_question_proposal_lineage
  ON ai_question_proposal(root_proposal_id,generation_attempt,created_at);

CREATE INDEX ix_ai_question_regeneration_job
  ON ai_question_generation_detail(regeneration_parent_proposal_id,generation_job_id);
