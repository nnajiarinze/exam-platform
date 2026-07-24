ALTER TABLE question_version
  ADD COLUMN language varchar(8),
  ADD COLUMN blooms_level varchar(16),
  ADD COLUMN complexity varchar(16),
  ADD COLUMN generation_intent varchar(16),
  ADD COLUMN estimated_reading_seconds integer,
  ADD CONSTRAINT ck_question_version_blooms CHECK (blooms_level IS NULL OR blooms_level IN ('REMEMBER','UNDERSTAND','APPLY','ANALYZE','EVALUATE','CREATE')),
  ADD CONSTRAINT ck_question_version_complexity CHECK (complexity IS NULL OR complexity IN ('LOW','MEDIUM','HIGH')),
  ADD CONSTRAINT ck_question_version_intent CHECK (generation_intent IS NULL OR generation_intent IN ('PRACTICE','MOCK_EXAM','FINAL_EXAM','FLASHCARD','REVISION')),
  ADD CONSTRAINT ck_question_version_reading_time CHECK (estimated_reading_seconds IS NULL OR estimated_reading_seconds > 0);

CREATE TABLE question_source_reference (
  question_version_id uuid NOT NULL REFERENCES question_version(id) ON DELETE CASCADE,
  source_reference_id uuid NOT NULL REFERENCES source_reference(id),
  PRIMARY KEY (question_version_id, source_reference_id)
);

CREATE INDEX ix_question_source_reference_source ON question_source_reference(source_reference_id);

CREATE TABLE question_ai_provenance (
  question_id uuid PRIMARY KEY REFERENCES question(id),
  question_version_id uuid NOT NULL UNIQUE REFERENCES question_version(id),
  proposal_id uuid NOT NULL UNIQUE,
  generation_job_id uuid NOT NULL,
  knowledge_fact_id uuid NOT NULL REFERENCES knowledge_fact(id),
  knowledge_fact_version_id uuid NOT NULL REFERENCES knowledge_fact_version(id),
  knowledge_fact_checksum char(64) NOT NULL,
  source_checksums jsonb NOT NULL,
  proposal_checksum char(64) NOT NULL,
  provider varchar(80) NOT NULL,
  model varchar(160) NOT NULL,
  prompt_version varchar(100) NOT NULL,
  generated_at timestamptz NOT NULL,
  accepted_by varchar(200) NOT NULL,
  accepted_at timestamptz NOT NULL,
  intelligence_engine_version varchar(32) NOT NULL,
  CONSTRAINT ck_question_ai_source_checksums_array CHECK (jsonb_typeof(source_checksums)='array')
);

CREATE INDEX ix_question_ai_provenance_job ON question_ai_provenance(generation_job_id);
CREATE INDEX ix_question_ai_provenance_fact ON question_ai_provenance(knowledge_fact_id,knowledge_fact_version_id);
