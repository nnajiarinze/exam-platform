ALTER TABLE ai_generation_job DROP CONSTRAINT ck_ai_job_operation;
ALTER TABLE ai_generation_job ALTER COLUMN source_id DROP NOT NULL;
ALTER TABLE ai_generation_job ALTER COLUMN source_title DROP NOT NULL;
ALTER TABLE ai_generation_job ALTER COLUMN source_content DROP NOT NULL;
ALTER TABLE ai_generation_job ALTER COLUMN source_content_checksum DROP NOT NULL;
ALTER TABLE ai_generation_job ADD CONSTRAINT ck_ai_job_operation CHECK(operation_type IS NULL OR operation_type IN (
  'REWRITE_FOR_CLARITY','SIMPLIFY_LANGUAGE','MAKE_ATOMIC','SPLIT_FACT',
  'CHECK_SOURCE_SUPPORT','DETECT_AMBIGUITY','EDITORIAL_REVIEW_NOTES'
));

ALTER TABLE ai_editorial_proposal ADD COLUMN proposal_order INTEGER;
ALTER TABLE ai_editorial_proposal ADD COLUMN proposal_metadata JSONB NOT NULL DEFAULT '{}'::jsonb;
CREATE UNIQUE INDEX uq_ai_editorial_proposal_order
  ON ai_editorial_proposal(generation_job_id,proposal_order) WHERE proposal_order IS NOT NULL;

CREATE TABLE ai_editorial_finding (
  id UUID PRIMARY KEY,
  generation_job_id UUID NOT NULL REFERENCES ai_generation_job(id),
  operation_type VARCHAR(60) NOT NULL,
  target_fact_id UUID NOT NULL,
  target_fact_version_id UUID NOT NULL,
  finding_type VARCHAR(80) NOT NULL,
  severity VARCHAR(20) NOT NULL,
  title VARCHAR(300) NOT NULL,
  message VARCHAR(2000) NOT NULL,
  affected_text VARCHAR(500),
  source_evidence JSONB NOT NULL DEFAULT '[]'::jsonb,
  confidence VARCHAR(20),
  suggested_action VARCHAR(500),
  finding_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
  dismissed_at TIMESTAMPTZ,
  dismissed_by VARCHAR(200),
  dismissal_reason VARCHAR(500),
  created_at TIMESTAMPTZ NOT NULL,
  version BIGINT NOT NULL DEFAULT 0,
  CONSTRAINT ck_ai_editorial_finding_severity CHECK(severity IN ('INFO','WARNING','HIGH','BLOCKING')),
  CONSTRAINT ck_ai_editorial_finding_status CHECK(status IN ('OPEN','DISMISSED'))
);
CREATE INDEX idx_ai_editorial_finding_job ON ai_editorial_finding(generation_job_id,status,created_at);
CREATE INDEX idx_ai_editorial_finding_target ON ai_editorial_finding(target_fact_id,status,created_at DESC);
CREATE UNIQUE INDEX uq_ai_editorial_finding_identity ON ai_editorial_finding(
  generation_job_id,finding_type,target_fact_id,coalesce(affected_text,'')
);

CREATE OR REPLACE FUNCTION append_ai_editorial_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE before_state JSONB:=CASE WHEN TG_OP='INSERT' THEN NULL ELSE to_jsonb(OLD)-'source_content' END;after_state JSONB:=CASE WHEN TG_OP='DELETE' THEN NULL ELSE to_jsonb(NEW)-'source_content' END;state JSONB:=coalesce(after_state,before_state);action_name TEXT;entity_name TEXT;
BEGIN
 IF TG_TABLE_NAME='ai_generation_job' THEN action_name:=CASE WHEN TG_OP='INSERT' THEN 'GENERATION_REQUESTED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'GENERATION_'||NEW.status ELSE 'GENERATION_UPDATED' END;entity_name:='AI_GENERATION_JOB';
 ELSIF TG_TABLE_NAME='ai_editorial_finding' THEN action_name:=CASE WHEN TG_OP='INSERT' THEN 'FINDING_CREATED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'FINDING_'||NEW.status ELSE 'FINDING_UPDATED' END;entity_name:='AI_EDITORIAL_FINDING';
 ELSE action_name:=CASE WHEN TG_OP='INSERT' THEN 'PROPOSAL_CREATED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'PROPOSAL_'||NEW.status ELSE 'PROPOSAL_EDITED' END;entity_name:=CASE WHEN TG_TABLE_NAME='ai_editorial_proposal' THEN 'AI_EDITORIAL_PROPOSAL' ELSE 'AI_KNOWLEDGE_FACT_PROPOSAL' END;END IF;
 INSERT INTO ai_audit_event VALUES(gen_random_uuid(),clock_timestamp(),coalesce(state->>'dismissed_by',state->>'accepted_by',state->>'requested_by','system'),action_name,entity_name,(state->>'id')::uuid,before_state,after_state,jsonb_build_object('operation',state->>'operation_type','promptVersion',state->>'prompt_version','provider',state->>'provider','model',state->>'model'));
 RETURN coalesce(NEW,OLD);END $$;
CREATE TRIGGER audit_ai_editorial_finding AFTER INSERT OR UPDATE ON ai_editorial_finding FOR EACH ROW EXECUTE FUNCTION append_ai_editorial_audit();
