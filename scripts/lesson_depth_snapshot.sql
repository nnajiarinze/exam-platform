WITH latest_lesson AS (
  SELECT DISTINCT ON (topic_id) *
  FROM lesson_draft
  WHERE review_status='REVIEWED'
  ORDER BY topic_id,version_number DESC
), topic_rows AS (
  SELECT s.sort_order AS chapter_order,s.name AS chapter,t.sort_order,t.id AS topic_id,t.name AS topic_title,
    o.id AS objective_id,o.title AS objective_text,l.id AS lesson_id,l.version_number,
    coalesce((SELECT jsonb_agg(jsonb_build_object(
      'id',section.id,'title',section.title,'explanation',section.explanation,'displayOrder',section.display_order,
      'sourceSectionId',section.source_section_id,'sectionChecksum',section.section_checksum,
      'factVersionIds',coalesce((SELECT jsonb_agg(link.knowledge_fact_version_id ORDER BY link.knowledge_fact_version_id)
        FROM lesson_draft_section_fact link WHERE link.lesson_draft_section_id=section.id),'[]'::jsonb)) ORDER BY section.display_order)
      FROM lesson_draft_section section WHERE section.lesson_draft_id=l.id),'[]'::jsonb) AS pages,
    coalesce((SELECT jsonb_agg(jsonb_build_object('id',fact.id,'versionId',version.id,
      'statement',version.canonical_statement,'sourceSectionId',provenance.source_section_id) ORDER BY fact.id)
      FROM knowledge_fact fact JOIN knowledge_fact_version version ON version.id=fact.current_version_id
      JOIN knowledge_fact_ai_provenance provenance ON provenance.knowledge_fact_version_id=version.id
      WHERE fact.learning_objective_id=o.id AND fact.status='ACTIVE' AND version.review_status='APPROVED'),'[]'::jsonb) AS facts,
    coalesce((SELECT jsonb_agg(jsonb_build_object('id',section.id,'title',section.subsection_title,
      'checksum',section.section_checksum,'exactText',section.exact_text,'normalizedText',section.normalized_text)
      ORDER BY section.display_order)
      FROM learning_objective_source_section mapping JOIN source_section section ON section.id=mapping.source_section_id
      WHERE mapping.learning_objective_id=o.id AND section.source_revision_id='sverige-i-fokus-source-v2'),'[]'::jsonb) AS source_sections
  FROM latest_lesson l JOIN topic t ON t.id=l.topic_id JOIN subject s ON s.id=t.subject_id
  JOIN learning_objective o ON o.topic_id=t.id
)
SELECT jsonb_agg(jsonb_build_object(
  'chapter',chapter,'topicId',topic_id,'topicTitle',topic_title,'objectiveId',objective_id,
  'objectiveText',objective_text,'lessonId',lesson_id,'lessonVersion',version_number,'pages',pages,
  'facts',facts,'sourceSections',source_sections,'sourceRevision','sverige-i-fokus-source-v2')
  ORDER BY chapter_order,sort_order)::text FROM topic_rows;
