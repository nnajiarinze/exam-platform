WITH latest_lesson AS (
  SELECT DISTINCT ON (topic_id) *
  FROM lesson_draft
  WHERE review_status='REVIEWED'
  ORDER BY topic_id,version_number DESC
), active_fact AS (
  SELECT f.id,v.id version_id,v.canonical_statement,f.learning_objective_id
  FROM knowledge_fact f JOIN knowledge_fact_version v ON v.id=f.current_version_id
  WHERE f.status='ACTIVE' AND f.review_status='APPROVED' AND v.review_status='APPROVED'
), effective_fact AS (
  SELECT f.*,coalesce(c.source_section_id,v2.id,p.source_section_id) source_section_id,
    coalesce(c.corrected_source_evidence,p.source_evidence) source_evidence
  FROM active_fact f JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=f.version_id
  LEFT JOIN LATERAL (
    SELECT * FROM knowledge_fact_evidence_provenance_correction x
    WHERE x.knowledge_fact_version_id=f.version_id AND x.validation_status='PASS'
    ORDER BY x.revision_number DESC LIMIT 1
  ) c ON true
  LEFT JOIN source_section old ON old.id=p.source_section_id
  LEFT JOIN source_section v2 ON v2.source_revision_id='sverige-i-fokus-source-v2'
    AND v2.logical_section_id=old.logical_section_id
), topic_rows AS (
  SELECT s.sort_order chapter_order,s.name chapter,t.sort_order,t.id topic_id,t.name topic_title,
    o.id objective_id,o.title objective_text,l.id lesson_id,l.version_number,l.title lesson_title,
    l.introduction,l.summary,l.important_points,
    coalesce((SELECT jsonb_agg(jsonb_build_object(
      'id',section.id,'title',section.title,'explanation',section.explanation,
      'displayOrder',section.display_order,'sourceSectionId',section.source_section_id,
      'sectionChecksum',section.section_checksum,
      'factVersionIds',coalesce((SELECT jsonb_agg(link.knowledge_fact_version_id ORDER BY link.knowledge_fact_version_id)
        FROM lesson_draft_section_fact link WHERE link.lesson_draft_section_id=section.id),'[]'::jsonb))
      ORDER BY section.display_order)
      FROM lesson_draft_section section WHERE section.lesson_draft_id=l.id),'[]'::jsonb) pages,
    coalesce((SELECT jsonb_agg(jsonb_build_object('id',fact.id,'versionId',fact.version_id,
      'statement',fact.canonical_statement,'sourceSectionId',fact.source_section_id,
      'sourceEvidence',fact.source_evidence) ORDER BY fact.source_section_id,fact.canonical_statement)
      FROM effective_fact fact WHERE fact.learning_objective_id=o.id),'[]'::jsonb) facts,
    coalesce((SELECT jsonb_agg(jsonb_build_object('id',section.id,'title',section.subsection_title,
      'checksum',section.section_checksum,'exactText',section.exact_text,'normalizedText',section.normalized_text)
      ORDER BY section.display_order)
      FROM learning_objective_source_section mapping JOIN source_section section ON section.id=mapping.source_section_id
      WHERE mapping.learning_objective_id=o.id AND section.source_revision_id='sverige-i-fokus-source-v2'),'[]'::jsonb) source_sections
  FROM latest_lesson l JOIN topic t ON t.id=l.topic_id JOIN subject s ON s.id=t.subject_id
  JOIN learning_objective o ON o.topic_id=t.id
)
SELECT jsonb_agg(jsonb_build_object(
  'chapter',chapter,'topicId',topic_id,'topicTitle',topic_title,'objectiveId',objective_id,
  'objectiveText',objective_text,'lessonId',lesson_id,'lessonVersion',version_number,
  'lessonTitle',lesson_title,'introduction',introduction,'summary',summary,
  'importantPoints',important_points,'pages',pages,'facts',facts,'sourceSections',source_sections,
  'sourceRevision','sverige-i-fokus-source-v2') ORDER BY chapter_order,sort_order)::text
FROM topic_rows;
