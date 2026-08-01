-- V23 attached every historical revision to the plan it actually followed.
-- Preserve the original deterministic job plan as a separate immutable plan
-- revision whenever provider output had previously drifted from it.
WITH desired AS (
  SELECT p.id proposal_id,j.id job_id,j.topic_id,j.learning_objective_id,j.source_section_id,
    j.input_snapshot->>'sourceSectionChecksum' source_checksum,j.requested_by,
    page.ordinality::integer-1 page_index,page.value page,
    coalesce((SELECT max(existing.plan_revision_number) FROM ai_lesson_page_plan_revision existing
      WHERE existing.lesson_proposal_id=p.id AND existing.page_index=page.ordinality::integer-1),0)+1 revision_number,
    (SELECT existing.id FROM ai_lesson_page_plan_revision existing
      WHERE existing.lesson_proposal_id=p.id AND existing.page_index=page.ordinality::integer-1
      ORDER BY existing.plan_revision_number DESC LIMIT 1) replaces_id
  FROM ai_lesson_proposal p
  JOIN ai_lesson_generation_job j ON j.id=p.generation_job_id
  CROSS JOIN LATERAL jsonb_array_elements(j.input_snapshot->'plan') WITH ORDINALITY page(value,ordinality)
)
INSERT INTO ai_lesson_page_plan_revision(
  id,lesson_proposal_id,page_index,plan_revision_number,replaces_plan_revision_id,page_type,title,
  knowledge_fact_version_ids,source_section_id,source_section_checksum,topic_id,learning_objective_id,
  plan_checksum,created_by,created_at)
SELECT gen_random_uuid(),d.proposal_id,d.page_index,d.revision_number,d.replaces_id,
  d.page->>'pageType',d.page->>'title',d.page->'knowledgeFactVersionIds',d.source_section_id,
  d.source_checksum,d.topic_id,d.learning_objective_id,
  encode(digest(convert_to(jsonb_build_object(
    'pageIndex',d.page_index,'pageType',d.page->>'pageType','title',d.page->>'title',
    'knowledgeFactVersionIds',d.page->'knowledgeFactVersionIds','sourceSectionId',d.source_section_id,
    'sourceSectionChecksum',d.source_checksum,'topicId',d.topic_id,
    'learningObjectiveId',d.learning_objective_id)::text,'UTF8'),'sha256'),'hex'),
  'migration-v24:'||d.requested_by,now()
FROM desired d
WHERE NOT EXISTS(
  SELECT 1 FROM ai_lesson_page_plan_revision existing
  WHERE existing.lesson_proposal_id=d.proposal_id AND existing.page_index=d.page_index
    AND existing.page_type=d.page->>'pageType' AND existing.title=d.page->>'title'
    AND existing.knowledge_fact_version_ids=d.page->'knowledgeFactVersionIds'
    AND existing.source_section_id=d.source_section_id
    AND existing.source_section_checksum=d.source_checksum
    AND existing.topic_id=d.topic_id AND existing.learning_objective_id=d.learning_objective_id
);
