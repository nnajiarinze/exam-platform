CREATE TABLE ai_question_target_generation_attempt (
  id UUID PRIMARY KEY,
  target_id UUID NOT NULL REFERENCES ai_question_target_plan(id),
  generation_job_id UUID NOT NULL UNIQUE REFERENCES ai_generation_job(id),
  replaces_generation_job_id UUID REFERENCES ai_generation_job(id),
  attempt_number INTEGER NOT NULL CHECK (attempt_number > 0),
  reason_code VARCHAR(100) NOT NULL,
  requested_by VARCHAR(200) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  UNIQUE(target_id,attempt_number)
);

INSERT INTO ai_question_target_generation_attempt(
  id,target_id,generation_job_id,replaces_generation_job_id,attempt_number,reason_code,requested_by,created_at
)
SELECT gen_random_uuid(),t.id,t.generation_job_id,NULL,1,'INITIAL',j.requested_by,j.created_at
FROM ai_question_target_plan t
JOIN ai_generation_job j ON j.id=t.generation_job_id
ON CONFLICT(generation_job_id) DO NOTHING;
