-- Local demonstration configuration only. These values are not official exam rules.
BEGIN;

UPDATE mock_exam_blueprint
SET active = FALSE, updated_at = now()
WHERE exam_id = 'swedish-citizenship'
  AND id <> '70000000-0000-0000-0000-000000000001';

INSERT INTO mock_exam_blueprint
  (id, exam_id, name, total_questions, duration_minutes, passing_percentage,
   active, created_at, updated_at)
VALUES
  ('70000000-0000-0000-0000-000000000001', 'swedish-citizenship',
   'Local demonstration mock', 10, 15, 70.00, TRUE, now(), now())
ON CONFLICT (id) DO UPDATE SET
  name = EXCLUDED.name,
  total_questions = EXCLUDED.total_questions,
  duration_minutes = EXCLUDED.duration_minutes,
  passing_percentage = EXCLUDED.passing_percentage,
  active = TRUE,
  updated_at = now();

DELETE FROM mock_exam_topic_allocation
WHERE blueprint_id = '70000000-0000-0000-0000-000000000001';

INSERT INTO mock_exam_topic_allocation
  (id, blueprint_id, external_topic_id, question_count)
VALUES
  ('70000000-0000-0000-0000-000000000011',
   '70000000-0000-0000-0000-000000000001', 'topic-democracy', 2),
  ('70000000-0000-0000-0000-000000000012',
   '70000000-0000-0000-0000-000000000001', 'topic-sweden-basics', 8);

COMMIT;
