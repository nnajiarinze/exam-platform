ALTER TABLE ai_generation_job DROP CONSTRAINT ck_ai_job_operation;
ALTER TABLE ai_generation_job ADD CONSTRAINT ck_ai_job_operation CHECK(operation_type IS NULL OR operation_type IN (
  'REWRITE_FOR_CLARITY','SIMPLIFY_LANGUAGE','MAKE_ATOMIC','SPLIT_FACT',
  'CHECK_SOURCE_SUPPORT','DETECT_AMBIGUITY','EDITORIAL_REVIEW_NOTES','GENERATE_QUESTIONS_FROM_FACT'
));
ALTER TABLE ai_generation_job DROP CONSTRAINT ck_ai_editorial_result_type;
ALTER TABLE ai_generation_job ADD CONSTRAINT ck_ai_editorial_result_type CHECK(result_type IS NULL OR result_type IN (
  'PROPOSALS_AVAILABLE','ALREADY_CLEAR','NO_MEANINGFUL_CHANGE','INSUFFICIENT_GROUNDED_EVIDENCE','FINDINGS_AVAILABLE',
  'QUESTIONS_PROPOSED','INSUFFICIENT_GROUNDED_INFORMATION','FACT_NOT_SUITABLE_FOR_QUESTION'
));

CREATE TABLE ai_question_generation_detail (
  generation_job_id UUID PRIMARY KEY REFERENCES ai_generation_job(id),
  target_fact_id UUID NOT NULL,
  target_fact_version_id UUID NOT NULL,
  target_version BIGINT NOT NULL,
  target_fact_checksum CHAR(64) NOT NULL,
  target_fact_snapshot TEXT NOT NULL,
  learning_objective_id UUID NOT NULL,
  topic_id UUID NOT NULL,
  subject_id UUID NOT NULL,
  exam_id UUID NOT NULL,
  exam_version_id UUID NOT NULL,
  requested_question_type VARCHAR(30),
  context_snapshot JSONB NOT NULL,
  original_output_checksum CHAR(64),
  validation_failures JSONB NOT NULL DEFAULT '[]'::jsonb,
  CONSTRAINT ck_question_generation_type CHECK(requested_question_type IS NULL OR requested_question_type IN ('SINGLE_CHOICE','TRUE_FALSE','MULTIPLE_CHOICE')),
  CONSTRAINT ck_question_generation_fact_checksum CHECK(target_fact_checksum ~ '^[a-f0-9]{64}$')
);
CREATE INDEX idx_question_generation_target ON ai_question_generation_detail(target_fact_id,target_fact_version_id);

CREATE TABLE ai_question_generation_source (
  generation_job_id UUID NOT NULL REFERENCES ai_generation_job(id),
  source_id UUID NOT NULL,
  source_title VARCHAR(500) NOT NULL,
  source_checksum CHAR(64) NOT NULL,
  content_excerpt TEXT NOT NULL,
  display_order INTEGER NOT NULL,
  PRIMARY KEY(generation_job_id,source_id),
  CONSTRAINT uq_question_generation_source_order UNIQUE(generation_job_id,display_order),
  CONSTRAINT ck_question_generation_source_checksum CHECK(source_checksum ~ '^[a-f0-9]{64}$')
);

CREATE TABLE ai_question_proposal (
  id UUID PRIMARY KEY,
  generation_job_id UUID NOT NULL REFERENCES ai_generation_job(id),
  proposal_order INTEGER NOT NULL,
  operation_type VARCHAR(60) NOT NULL DEFAULT 'GENERATE_QUESTIONS_FROM_FACT',
  question_type VARCHAR(30) NOT NULL,
  question_text TEXT NOT NULL,
  language VARCHAR(20) NOT NULL,
  explanation TEXT NOT NULL,
  rationale TEXT NOT NULL,
  target_fact_id UUID NOT NULL,
  target_fact_version_id UUID NOT NULL,
  target_fact_version BIGINT NOT NULL,
  target_fact_checksum CHAR(64) NOT NULL,
  target_fact_snapshot TEXT NOT NULL,
  learning_objective_id UUID NOT NULL,
  warnings JSONB NOT NULL DEFAULT '[]'::jsonb,
  confidence VARCHAR(20),
  validation_status VARCHAR(30) NOT NULL DEFAULT 'VALID',
  status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
  rejection_reason VARCHAR(500),
  rejected_by VARCHAR(200),
  rejected_at TIMESTAMPTZ,
  provider VARCHAR(60) NOT NULL,
  model VARCHAR(200) NOT NULL,
  prompt_version VARCHAR(100) NOT NULL,
  provider_request_id VARCHAR(300),
  input_tokens INTEGER,
  output_tokens INTEGER,
  total_tokens INTEGER,
  original_output_checksum CHAR(64) NOT NULL,
  proposal_text_checksum CHAR(64) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT uq_ai_question_proposal_order UNIQUE(generation_job_id,proposal_order),
  CONSTRAINT uq_ai_question_proposal_text UNIQUE(generation_job_id,proposal_text_checksum),
  CONSTRAINT ck_ai_question_proposal_type CHECK(question_type IN ('SINGLE_CHOICE','TRUE_FALSE','MULTIPLE_CHOICE')),
  CONSTRAINT ck_ai_question_proposal_status CHECK(status IN ('PROPOSED','REJECTED')),
  CONSTRAINT ck_ai_question_validation_status CHECK(validation_status='VALID')
);
CREATE INDEX idx_ai_question_proposal_job ON ai_question_proposal(generation_job_id,status,proposal_order);
CREATE INDEX idx_ai_question_proposal_target ON ai_question_proposal(target_fact_id,status,created_at DESC);

CREATE TABLE ai_question_proposal_option (
  id UUID PRIMARY KEY,
  proposal_id UUID NOT NULL REFERENCES ai_question_proposal(id),
  option_key VARCHAR(20) NOT NULL,
  display_order INTEGER NOT NULL,
  text TEXT NOT NULL,
  correct BOOLEAN NOT NULL,
  rationale TEXT,
  CONSTRAINT uq_ai_question_option_key UNIQUE(proposal_id,option_key),
  CONSTRAINT uq_ai_question_option_order UNIQUE(proposal_id,display_order)
);
CREATE INDEX idx_ai_question_option_proposal ON ai_question_proposal_option(proposal_id,display_order);

CREATE TABLE ai_question_proposal_evidence (
  id UUID PRIMARY KEY,
  proposal_id UUID NOT NULL REFERENCES ai_question_proposal(id),
  evidence_type VARCHAR(30) NOT NULL,
  source_id UUID,
  source_title VARCHAR(500),
  source_checksum CHAR(64),
  supported_claim TEXT NOT NULL,
  quote TEXT,
  display_order INTEGER NOT NULL,
  CONSTRAINT uq_ai_question_evidence_order UNIQUE(proposal_id,display_order),
  CONSTRAINT ck_ai_question_evidence_type CHECK(evidence_type IN ('KNOWLEDGE_FACT','SOURCE')),
  CONSTRAINT ck_ai_question_source_evidence CHECK(
    (evidence_type='KNOWLEDGE_FACT' AND source_id IS NULL AND source_checksum IS NULL) OR
    (evidence_type='SOURCE' AND source_id IS NOT NULL AND source_checksum IS NOT NULL AND quote IS NOT NULL)
  )
);
CREATE INDEX idx_ai_question_evidence_proposal ON ai_question_proposal_evidence(proposal_id,display_order);

CREATE OR REPLACE FUNCTION append_ai_editorial_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE before_state JSONB:=CASE WHEN TG_OP='INSERT' THEN NULL ELSE to_jsonb(OLD)-'source_content'-'target_fact_snapshot'-'context_snapshot' END;after_state JSONB:=CASE WHEN TG_OP='DELETE' THEN NULL ELSE to_jsonb(NEW)-'source_content'-'target_fact_snapshot'-'context_snapshot' END;state JSONB:=coalesce(after_state,before_state);action_name TEXT;entity_name TEXT;
BEGIN
 IF TG_TABLE_NAME='ai_generation_job' THEN action_name:=CASE WHEN TG_OP='INSERT' THEN 'GENERATION_REQUESTED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'GENERATION_'||NEW.status ELSE 'GENERATION_UPDATED' END;entity_name:='AI_GENERATION_JOB';
 ELSIF TG_TABLE_NAME='ai_editorial_finding' THEN action_name:=CASE WHEN TG_OP='INSERT' THEN 'FINDING_CREATED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'FINDING_'||NEW.status ELSE 'FINDING_UPDATED' END;entity_name:='AI_EDITORIAL_FINDING';
 ELSIF TG_TABLE_NAME='ai_question_proposal' THEN action_name:=CASE WHEN TG_OP='INSERT' THEN 'QUESTION_PROPOSAL_CREATED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'QUESTION_PROPOSAL_'||NEW.status ELSE 'QUESTION_PROPOSAL_UPDATED' END;entity_name:='AI_QUESTION_PROPOSAL';
 ELSE action_name:=CASE WHEN TG_OP='INSERT' THEN 'PROPOSAL_CREATED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'PROPOSAL_'||NEW.status ELSE 'PROPOSAL_EDITED' END;entity_name:=CASE WHEN TG_TABLE_NAME='ai_editorial_proposal' THEN 'AI_EDITORIAL_PROPOSAL' ELSE 'AI_KNOWLEDGE_FACT_PROPOSAL' END;END IF;
 INSERT INTO ai_audit_event VALUES(gen_random_uuid(),clock_timestamp(),coalesce(state->>'rejected_by',state->>'dismissed_by',state->>'accepted_by',state->>'requested_by','system'),action_name,entity_name,(state->>'id')::uuid,before_state,after_state,jsonb_build_object('operation',state->>'operation_type','promptVersion',state->>'prompt_version','provider',state->>'provider','model',state->>'model'));
 RETURN coalesce(NEW,OLD);END $$;
CREATE TRIGGER audit_ai_question_proposal AFTER INSERT OR UPDATE ON ai_question_proposal FOR EACH ROW EXECUTE FUNCTION append_ai_editorial_audit();
