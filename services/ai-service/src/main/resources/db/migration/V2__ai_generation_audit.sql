CREATE TABLE ai_audit_event (
  id UUID PRIMARY KEY, occurred_at TIMESTAMPTZ NOT NULL, actor_id VARCHAR(200) NOT NULL,
  action VARCHAR(60) NOT NULL, entity_type VARCHAR(60) NOT NULL, entity_id UUID NOT NULL,
  previous_state JSONB, new_state JSONB, metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_ai_audit_entity ON ai_audit_event(entity_type,entity_id,occurred_at DESC);
CREATE INDEX idx_ai_audit_actor ON ai_audit_event(actor_id,occurred_at DESC);
CREATE OR REPLACE FUNCTION reject_ai_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'AI audit events are immutable'; END $$;
CREATE TRIGGER ai_audit_immutable BEFORE UPDATE OR DELETE ON ai_audit_event FOR EACH ROW EXECUTE FUNCTION reject_ai_audit_mutation();
CREATE OR REPLACE FUNCTION append_ai_editorial_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE before_state JSONB:=CASE WHEN TG_OP='INSERT' THEN NULL ELSE to_jsonb(OLD)-'source_content' END;after_state JSONB:=CASE WHEN TG_OP='DELETE' THEN NULL ELSE to_jsonb(NEW)-'source_content' END;state JSONB:=coalesce(after_state,before_state);action_name TEXT;
BEGIN
 IF TG_TABLE_NAME='ai_generation_job' THEN action_name:=CASE WHEN TG_OP='INSERT' THEN 'GENERATION_REQUESTED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'GENERATION_'||NEW.status ELSE 'GENERATION_UPDATED' END;
 ELSE action_name:=CASE WHEN TG_OP='INSERT' THEN 'PROPOSAL_CREATED' WHEN OLD.status IS DISTINCT FROM NEW.status THEN 'PROPOSAL_'||NEW.status ELSE 'PROPOSAL_EDITED' END;END IF;
 INSERT INTO ai_audit_event VALUES(gen_random_uuid(),clock_timestamp(),coalesce(state->>'accepted_by',state->>'requested_by','system'),action_name,CASE WHEN TG_TABLE_NAME='ai_generation_job' THEN 'AI_GENERATION_JOB' ELSE 'AI_KNOWLEDGE_FACT_PROPOSAL' END,(state->>'id')::uuid,before_state,after_state,jsonb_build_object('promptVersion',state->>'prompt_version','provider',state->>'provider','model',state->>'model'));
 RETURN coalesce(NEW,OLD);END $$;
CREATE TRIGGER audit_ai_job AFTER INSERT OR UPDATE ON ai_generation_job FOR EACH ROW EXECUTE FUNCTION append_ai_editorial_audit();
CREATE TRIGGER audit_ai_proposal AFTER INSERT OR UPDATE ON ai_knowledge_fact_proposal FOR EACH ROW EXECUTE FUNCTION append_ai_editorial_audit();
