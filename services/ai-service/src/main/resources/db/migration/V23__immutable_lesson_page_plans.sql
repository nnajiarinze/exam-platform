CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE ai_lesson_page_plan_revision (
  id UUID PRIMARY KEY,
  lesson_proposal_id UUID NOT NULL REFERENCES ai_lesson_proposal(id),
  page_index INTEGER NOT NULL,
  plan_revision_number INTEGER NOT NULL,
  replaces_plan_revision_id UUID REFERENCES ai_lesson_page_plan_revision(id),
  page_type VARCHAR(80) NOT NULL,
  title VARCHAR(500) NOT NULL,
  knowledge_fact_version_ids JSONB NOT NULL,
  source_section_id UUID NOT NULL,
  source_section_checksum CHAR(64) NOT NULL,
  topic_id UUID NOT NULL,
  learning_objective_id UUID NOT NULL,
  plan_checksum CHAR(64) NOT NULL,
  created_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_lesson_page_plan_revision UNIQUE(lesson_proposal_id,page_index,plan_revision_number),
  CONSTRAINT uq_lesson_page_plan_checksum UNIQUE(lesson_proposal_id,page_index,plan_checksum),
  CONSTRAINT ck_lesson_page_plan_position CHECK(page_index>=0 AND plan_revision_number>=1),
  CONSTRAINT ck_lesson_page_plan_facts CHECK(jsonb_typeof(knowledge_fact_version_ids)='array' AND jsonb_array_length(knowledge_fact_version_ids)>0),
  CONSTRAINT ck_lesson_page_plan_source_checksum CHECK(source_section_checksum ~ '^[a-f0-9]{64}$'),
  CONSTRAINT ck_lesson_page_plan_checksum CHECK(plan_checksum ~ '^[a-f0-9]{64}$')
);

CREATE INDEX idx_lesson_page_plan_proposal
  ON ai_lesson_page_plan_revision(lesson_proposal_id,page_index,plan_revision_number DESC);

ALTER TABLE ai_lesson_page_revision ADD COLUMN page_plan_revision_id UUID;

-- Preserve the plans that actually governed all historical page revisions. A
-- provider page that diverged from the job snapshot becomes an explicit plan
-- revision instead of being silently compared with an incompatible snapshot.
INSERT INTO ai_lesson_page_plan_revision(
  id,lesson_proposal_id,page_index,plan_revision_number,replaces_plan_revision_id,
  page_type,title,knowledge_fact_version_ids,source_section_id,source_section_checksum,
  topic_id,learning_objective_id,plan_checksum,created_by,created_at)
SELECT gen_random_uuid(),r.lesson_proposal_id,r.page_index,1,NULL,
  r.page->>'pageType',r.page->>'title',r.page->'knowledgeFactVersionIds',j.source_section_id,
  j.input_snapshot->>'sourceSectionChecksum',j.topic_id,j.learning_objective_id,
  encode(digest(convert_to(jsonb_build_object(
    'pageIndex',r.page_index,'pageType',r.page->>'pageType','title',r.page->>'title',
    'knowledgeFactVersionIds',r.page->'knowledgeFactVersionIds','sourceSectionId',j.source_section_id,
    'sourceSectionChecksum',j.input_snapshot->>'sourceSectionChecksum','topicId',j.topic_id,
    'learningObjectiveId',j.learning_objective_id)::text,'UTF8'),'sha256'),'hex'),
  'migration-v23',min(r.created_at)
FROM ai_lesson_page_revision r
JOIN ai_lesson_proposal p ON p.id=r.lesson_proposal_id
JOIN ai_lesson_generation_job j ON j.id=p.generation_job_id
GROUP BY r.lesson_proposal_id,r.page_index,r.page->>'pageType',r.page->>'title',
  r.page->'knowledgeFactVersionIds',j.source_section_id,j.input_snapshot->>'sourceSectionChecksum',
  j.topic_id,j.learning_objective_id;

UPDATE ai_lesson_page_revision r SET page_plan_revision_id=plan.id
FROM ai_lesson_page_plan_revision plan
WHERE plan.lesson_proposal_id=r.lesson_proposal_id AND plan.page_index=r.page_index
  AND plan.page_type=r.page->>'pageType' AND plan.title=r.page->>'title'
  AND plan.knowledge_fact_version_ids=r.page->'knowledgeFactVersionIds';

ALTER TABLE ai_lesson_page_revision ALTER COLUMN page_plan_revision_id SET NOT NULL;
ALTER TABLE ai_lesson_page_revision ADD CONSTRAINT fk_lesson_page_revision_plan
  FOREIGN KEY(page_plan_revision_id) REFERENCES ai_lesson_page_plan_revision(id);

CREATE OR REPLACE FUNCTION reject_lesson_page_plan_mutation() RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'lesson page plans are immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lesson_page_plan_immutable
  BEFORE UPDATE OR DELETE ON ai_lesson_page_plan_revision
  FOR EACH ROW EXECUTE FUNCTION reject_lesson_page_plan_mutation();

CREATE OR REPLACE FUNCTION enforce_lesson_page_revision_plan() RETURNS trigger AS $$
DECLARE plan ai_lesson_page_plan_revision%ROWTYPE;
BEGIN
  SELECT * INTO plan FROM ai_lesson_page_plan_revision WHERE id=NEW.page_plan_revision_id;
  IF plan.id IS NULL OR plan.lesson_proposal_id<>NEW.lesson_proposal_id OR plan.page_index<>NEW.page_index
    OR plan.page_type<>NEW.page->>'pageType' OR plan.title<>NEW.page->>'title'
    OR plan.knowledge_fact_version_ids<>NEW.page->'knowledgeFactVersionIds' THEN
    RAISE EXCEPTION 'lesson page revision does not match immutable plan %',NEW.page_plan_revision_id;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_lesson_page_revision_plan
  BEFORE INSERT OR UPDATE OF page_plan_revision_id,page,lesson_proposal_id,page_index
  ON ai_lesson_page_revision FOR EACH ROW EXECUTE FUNCTION enforce_lesson_page_revision_plan();
