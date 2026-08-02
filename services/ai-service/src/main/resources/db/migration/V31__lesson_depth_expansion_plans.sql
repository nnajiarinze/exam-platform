CREATE TABLE ai_lesson_depth_audit (
  id UUID PRIMARY KEY,
  corpus_id VARCHAR(120) NOT NULL,
  source_revision_id VARCHAR(120) NOT NULL,
  definition_checksum CHAR(64) NOT NULL,
  reading_words_per_minute INTEGER NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'AUDITED',
  created_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_lesson_depth_audit UNIQUE(corpus_id,source_revision_id,definition_checksum),
  CONSTRAINT ck_lesson_depth_checksum CHECK(definition_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_lesson_depth_status CHECK(status IN ('AUDITED','GENERATING','VALIDATED','RELEASED','PARTIAL','BLOCKED'))
);

CREATE TABLE ai_lesson_depth_topic_plan (
  id UUID PRIMARY KEY,
  depth_audit_id UUID NOT NULL REFERENCES ai_lesson_depth_audit(id),
  topic_id UUID NOT NULL,
  learning_objective_id UUID NOT NULL,
  current_lesson_id UUID NOT NULL,
  current_lesson_version INTEGER NOT NULL,
  classification VARCHAR(40) NOT NULL,
  current_page_count INTEGER NOT NULL,
  target_page_count INTEGER NOT NULL,
  current_word_count INTEGER NOT NULL,
  normalized_source_length INTEGER NOT NULL,
  estimated_reading_seconds INTEGER NOT NULL,
  maximum_supportable_page_count INTEGER NOT NULL,
  audit_snapshot JSONB NOT NULL,
  audit_checksum CHAR(64) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_lesson_depth_topic UNIQUE(depth_audit_id,topic_id),
  CONSTRAINT ck_lesson_depth_topic_counts CHECK(current_page_count BETWEEN 1 AND 6 AND target_page_count BETWEEN 1 AND 6
    AND maximum_supportable_page_count BETWEEN 1 AND 6 AND target_page_count<=maximum_supportable_page_count),
  CONSTRAINT ck_lesson_depth_topic_checksum CHECK(audit_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_lesson_depth_topic_status CHECK(status IN ('PLANNED','GENERATING','VALIDATED','UNCHANGED','LIMITED','BLOCKED'))
);

CREATE TABLE ai_lesson_depth_page_plan (
  id UUID PRIMARY KEY,
  topic_plan_id UUID NOT NULL REFERENCES ai_lesson_depth_topic_plan(id),
  plan_order INTEGER NOT NULL,
  action VARCHAR(30) NOT NULL,
  retained_page_id UUID,
  supersedes_page_id UUID,
  immutable_plan JSONB NOT NULL,
  plan_checksum CHAR(64) NOT NULL,
  status VARCHAR(30) NOT NULL DEFAULT 'PLANNED',
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_lesson_depth_page_order UNIQUE(topic_plan_id,plan_order),
  CONSTRAINT uq_lesson_depth_page_checksum UNIQUE(topic_plan_id,plan_checksum),
  CONSTRAINT ck_lesson_depth_page_order CHECK(plan_order>=0 AND plan_order<6),
  CONSTRAINT ck_lesson_depth_page_action CHECK(action IN ('REUSE_UNCHANGED','REUSE_WITH_REORDERING','ADD','REPLACE','MERGE','SPLIT')),
  CONSTRAINT ck_lesson_depth_page_checksum CHECK(plan_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_lesson_depth_page_status CHECK(status IN ('PLANNED','GENERATING','VALIDATED','REJECTED','REMOVED_SOURCE_LIMITED'))
);

CREATE TABLE ai_lesson_depth_page_revision (
  id UUID PRIMARY KEY,
  page_plan_id UUID NOT NULL REFERENCES ai_lesson_depth_page_plan(id),
  revision_number INTEGER NOT NULL,
  replaces_revision_id UUID REFERENCES ai_lesson_depth_page_revision(id),
  content JSONB NOT NULL,
  status VARCHAR(30) NOT NULL,
  diagnostics JSONB NOT NULL DEFAULT '[]'::jsonb,
  validator_version VARCHAR(80) NOT NULL,
  provider VARCHAR(80),
  model VARCHAR(200),
  provider_request_id VARCHAR(300),
  input_tokens INTEGER,
  output_tokens INTEGER,
  created_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_lesson_depth_page_revision UNIQUE(page_plan_id,revision_number),
  CONSTRAINT ck_lesson_depth_revision_number CHECK(revision_number>0),
  CONSTRAINT ck_lesson_depth_revision_status CHECK(status IN ('PENDING','VALIDATED','REJECTED','SUPERSEDED'))
);

CREATE OR REPLACE FUNCTION reject_lesson_depth_plan_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'lesson depth plans are immutable; create a versioned replacement plan';
END $$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION enforce_lesson_depth_topic_plan_immutability() RETURNS trigger AS $$
BEGIN
  IF ROW(OLD.depth_audit_id,OLD.topic_id,OLD.learning_objective_id,OLD.current_lesson_id,
      OLD.current_lesson_version,OLD.classification,OLD.current_page_count,OLD.target_page_count,
      OLD.current_word_count,OLD.normalized_source_length,OLD.estimated_reading_seconds,
      OLD.maximum_supportable_page_count,OLD.audit_snapshot,OLD.audit_checksum)
    IS DISTINCT FROM ROW(NEW.depth_audit_id,NEW.topic_id,NEW.learning_objective_id,NEW.current_lesson_id,
      NEW.current_lesson_version,NEW.classification,NEW.current_page_count,NEW.target_page_count,
      NEW.current_word_count,NEW.normalized_source_length,NEW.estimated_reading_seconds,
      NEW.maximum_supportable_page_count,NEW.audit_snapshot,NEW.audit_checksum) THEN
    RAISE EXCEPTION 'lesson depth topic plan is immutable';
  END IF;
  RETURN NEW;
END $$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lesson_depth_audit_immutable BEFORE DELETE ON ai_lesson_depth_audit
  FOR EACH ROW EXECUTE FUNCTION reject_lesson_depth_plan_mutation();
CREATE TRIGGER trg_lesson_depth_topic_plan_immutable BEFORE DELETE ON ai_lesson_depth_topic_plan
  FOR EACH ROW EXECUTE FUNCTION reject_lesson_depth_plan_mutation();
CREATE TRIGGER trg_lesson_depth_topic_fields_immutable BEFORE UPDATE ON ai_lesson_depth_topic_plan
  FOR EACH ROW EXECUTE FUNCTION enforce_lesson_depth_topic_plan_immutability();
CREATE TRIGGER trg_lesson_depth_page_plan_immutable BEFORE DELETE ON ai_lesson_depth_page_plan
  FOR EACH ROW EXECUTE FUNCTION reject_lesson_depth_plan_mutation();

CREATE INDEX idx_lesson_depth_topic_status ON ai_lesson_depth_topic_plan(depth_audit_id,status,topic_id);
CREATE INDEX idx_lesson_depth_page_status ON ai_lesson_depth_page_plan(topic_plan_id,status,plan_order);
