ALTER TABLE lesson_progress ADD COLUMN carried_completion_at TIMESTAMPTZ;

-- Backfill the currently active richer release when it was imported before this migration.
INSERT INTO lesson_progress(id,learner_id,content_release_id,topic_id,last_section_id,
  completed_section_count,started_at,last_accessed_at,completed_at,carried_completion_at)
SELECT gen_random_uuid(),old_progress.learner_id,new_release.id,new_topic.id,first_section.id,
  0,now(),now(),NULL,old_progress.completed_at
FROM imported_content_release new_release
JOIN imported_topic new_topic ON new_topic.content_release_id=new_release.id
JOIN LATERAL (
  SELECT old_release.id FROM imported_content_release old_release
  WHERE old_release.exam_id=new_release.exam_id AND old_release.id<>new_release.id
    AND old_release.published_at<new_release.published_at
  ORDER BY old_release.published_at DESC LIMIT 1
) previous ON true
JOIN imported_topic old_topic ON old_topic.content_release_id=previous.id
  AND old_topic.external_topic_id=new_topic.external_topic_id
JOIN lesson_progress old_progress ON old_progress.topic_id=old_topic.id
  AND old_progress.completed_at IS NOT NULL
JOIN LATERAL (
  SELECT section.id FROM imported_lesson_section section WHERE section.topic_id=new_topic.id
  ORDER BY section.display_order,section.id LIMIT 1
) first_section ON true
WHERE new_release.status='ACTIVE'
ON CONFLICT(learner_id,content_release_id,topic_id) DO NOTHING;
