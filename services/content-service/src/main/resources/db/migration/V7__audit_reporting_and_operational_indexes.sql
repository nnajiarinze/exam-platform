CREATE TABLE audit_event (
  id UUID PRIMARY KEY,
  occurred_at TIMESTAMPTZ NOT NULL,
  actor_id VARCHAR(200) NOT NULL,
  actor_name VARCHAR(300) NOT NULL,
  actor_role VARCHAR(500) NOT NULL,
  action VARCHAR(40) NOT NULL,
  entity_type VARCHAR(60) NOT NULL,
  entity_id UUID NOT NULL,
  entity_version BIGINT,
  previous_state JSONB,
  new_state JSONB,
  reason TEXT,
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  ip_address INET,
  request_id VARCHAR(100) NOT NULL,
  CONSTRAINT ck_audit_action CHECK (action IN ('CREATE','UPDATE','DELETE','ARCHIVE','RESTORE','APPROVE','REJECT','REQUIRES_UPDATE','SUBMIT','PUBLISH','DELIVER','ACTIVATE','RETIRE','LOGIN','LOGOUT','CONFIG_CHANGE'))
);

CREATE INDEX idx_audit_occurred ON audit_event(occurred_at DESC, id DESC);
CREATE INDEX idx_audit_actor ON audit_event(actor_id, occurred_at DESC);
CREATE INDEX idx_audit_entity ON audit_event(entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_action ON audit_event(action, occurred_at DESC);
CREATE INDEX idx_audit_request ON audit_event(request_id);

CREATE OR REPLACE FUNCTION reject_audit_mutation() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'audit events are immutable';
END $$;
CREATE TRIGGER audit_event_immutable BEFORE UPDATE OR DELETE ON audit_event
FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();

CREATE OR REPLACE FUNCTION append_entity_audit() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
  before_state JSONB := CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE to_jsonb(OLD) END;
  after_state JSONB := CASE WHEN TG_OP = 'DELETE' THEN NULL ELSE to_jsonb(NEW) END;
  state JSONB := COALESCE(after_state, before_state);
  status_before TEXT := before_state->>'status';
  status_after TEXT := after_state->>'status';
  review_before TEXT := before_state->>'review_status';
  review_after TEXT := after_state->>'review_status';
  event_action TEXT;
  actor TEXT;
BEGIN
  IF TG_OP = 'INSERT' THEN event_action := 'CREATE';
  ELSIF TG_OP = 'DELETE' THEN event_action := 'DELETE';
  ELSIF status_before IS DISTINCT FROM status_after AND status_after IN ('ARCHIVED','RETIRED') THEN event_action := CASE WHEN status_after='ARCHIVED' THEN 'ARCHIVE' ELSE 'RETIRE' END;
  ELSIF status_before IS DISTINCT FROM status_after AND status_after='PUBLISHED' THEN event_action := 'PUBLISH';
  ELSIF status_before IS DISTINCT FROM status_after AND status_after='DELIVERED' THEN event_action := 'DELIVER';
  ELSIF status_before IS DISTINCT FROM status_after AND status_after='ACTIVE' AND TG_TABLE_NAME='content_release' THEN event_action := 'ACTIVATE';
  ELSIF review_before IS DISTINCT FROM review_after AND review_after='APPROVED' THEN event_action := 'APPROVE';
  ELSIF review_before IS DISTINCT FROM review_after AND review_after='REJECTED' THEN event_action := 'REJECT';
  ELSIF review_before IS DISTINCT FROM review_after AND review_after='REQUIRES_UPDATE' THEN event_action := 'REQUIRES_UPDATE';
  ELSIF review_before IS DISTINCT FROM review_after AND review_after='UNDER_REVIEW' THEN event_action := 'SUBMIT';
  ELSE event_action := 'UPDATE'; END IF;

  actor := COALESCE(state->>'updated_by', state->>'created_by', state->>'published_by',
                    state->>'validated_by', state->>'author_id', state->>'reviewer_id',
                    state->>'activated_by', state->>'requested_by', 'system');
  INSERT INTO audit_event(id,occurred_at,actor_id,actor_name,actor_role,action,entity_type,
                          entity_id,entity_version,previous_state,new_state,request_id)
  VALUES(gen_random_uuid(),clock_timestamp(),actor,actor,'UNKNOWN',event_action,TG_ARGV[0],
         (state->>'id')::uuid,NULLIF(state->>'version','')::bigint,before_state,after_state,
         COALESCE(current_setting('app.request_id',true),gen_random_uuid()::text));
  RETURN COALESCE(NEW,OLD);
END $$;

CREATE TRIGGER audit_exam AFTER INSERT OR UPDATE OR DELETE ON exam FOR EACH ROW EXECUTE FUNCTION append_entity_audit('Exam');
CREATE TRIGGER audit_exam_version AFTER INSERT OR UPDATE OR DELETE ON exam_version FOR EACH ROW EXECUTE FUNCTION append_entity_audit('ExamVersion');
CREATE TRIGGER audit_source AFTER INSERT OR UPDATE OR DELETE ON source_reference FOR EACH ROW EXECUTE FUNCTION append_entity_audit('Source');
CREATE TRIGGER audit_objective AFTER INSERT OR UPDATE OR DELETE ON learning_objective FOR EACH ROW EXECUTE FUNCTION append_entity_audit('LearningObjective');
CREATE TRIGGER audit_fact AFTER INSERT OR UPDATE OR DELETE ON knowledge_fact FOR EACH ROW EXECUTE FUNCTION append_entity_audit('KnowledgeFact');
CREATE TRIGGER audit_question AFTER INSERT OR UPDATE OR DELETE ON question FOR EACH ROW EXECUTE FUNCTION append_entity_audit('Question');
CREATE TRIGGER audit_release AFTER INSERT OR UPDATE OR DELETE ON content_release FOR EACH ROW EXECUTE FUNCTION append_entity_audit('Release');
CREATE TRIGGER audit_review AFTER INSERT OR UPDATE OR DELETE ON review_item FOR EACH ROW EXECUTE FUNCTION append_entity_audit('Review');

CREATE INDEX idx_question_health ON question(status,review_status,learning_objective_id);
CREATE INDEX idx_fact_health ON knowledge_fact(status,review_status,learning_objective_id);
CREATE INDEX idx_source_health_accessed ON source_reference(status,review_status,accessed_at);
CREATE INDEX idx_review_health ON review_item(review_status,assigned_reviewer_id,submitted_at);
CREATE INDEX idx_delivery_failures ON release_delivery_attempt(status,started_at DESC) WHERE status='FAILED';
