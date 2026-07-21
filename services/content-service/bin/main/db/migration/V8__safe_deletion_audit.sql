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
  actor_name TEXT;
  actor_roles TEXT;
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

  actor := COALESCE(NULLIF(current_setting('app.actor_id',true),''),state->>'updated_by',state->>'created_by',
                    state->>'published_by',state->>'validated_by',state->>'author_id',
                    state->>'reviewer_id',state->>'activated_by',state->>'requested_by','system');
  actor_name := COALESCE(NULLIF(current_setting('app.actor_name',true),''),actor);
  actor_roles := COALESCE(NULLIF(current_setting('app.actor_roles',true),''),'UNKNOWN');
  INSERT INTO audit_event(id,occurred_at,actor_id,actor_name,actor_role,action,entity_type,
                          entity_id,entity_version,previous_state,new_state,reason,metadata,request_id)
  VALUES(gen_random_uuid(),clock_timestamp(),actor,actor_name,actor_roles,event_action,TG_ARGV[0],
         (state->>'id')::uuid,NULLIF(state->>'version','')::bigint,before_state,after_state,
         NULLIF(current_setting('app.delete_reason',true),''),
         CASE WHEN TG_OP='DELETE' THEN jsonb_build_object('finalState',before_state) ELSE '{}'::jsonb END,
         COALESCE(NULLIF(current_setting('app.request_id',true),''),gen_random_uuid()::text));
  RETURN COALESCE(NEW,OLD);
END $$;

CREATE TRIGGER audit_subject AFTER INSERT OR UPDATE OR DELETE ON subject
FOR EACH ROW EXECUTE FUNCTION append_entity_audit('Subject');
CREATE TRIGGER audit_topic AFTER INSERT OR UPDATE OR DELETE ON topic
FOR EACH ROW EXECUTE FUNCTION append_entity_audit('Topic');
