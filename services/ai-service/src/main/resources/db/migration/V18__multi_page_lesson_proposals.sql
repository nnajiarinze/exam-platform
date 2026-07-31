ALTER TABLE ai_lesson_proposal
  ADD COLUMN introduction TEXT,
  ADD COLUMN summary TEXT,
  ADD COLUMN important_points JSONB NOT NULL DEFAULT '[]'::jsonb,
  ADD COLUMN pages JSONB NOT NULL DEFAULT '[]'::jsonb;

ALTER TABLE ai_lesson_generation_job
  ADD COLUMN actual_provider VARCHAR(50),
  ADD COLUMN actual_model VARCHAR(120);
