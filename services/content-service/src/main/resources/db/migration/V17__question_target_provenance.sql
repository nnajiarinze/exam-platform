ALTER TABLE question_ai_provenance
  ADD COLUMN tested_target_type varchar(50),
  ADD COLUMN tested_proposition text,
  ADD COLUMN tested_proposition_checksum char(64),
  ADD COLUMN expansion_target_plan_id uuid;

CREATE UNIQUE INDEX uq_question_fact_tested_proposition
  ON question_ai_provenance(knowledge_fact_version_id,tested_proposition_checksum)
  WHERE tested_proposition_checksum IS NOT NULL;

CREATE INDEX ix_question_tested_proposition
  ON question_ai_provenance(tested_proposition_checksum)
  WHERE tested_proposition_checksum IS NOT NULL;
