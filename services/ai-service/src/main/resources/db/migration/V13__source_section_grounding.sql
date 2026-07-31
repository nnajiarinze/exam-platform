ALTER TABLE ai_generation_job
  ADD COLUMN source_section_id UUID;

ALTER TABLE ai_knowledge_fact_proposal
  ADD COLUMN source_section_id UUID;

ALTER TABLE ai_question_generation_source
  ADD COLUMN source_section_id UUID;

ALTER TABLE ai_question_proposal_evidence
  ADD COLUMN source_section_id UUID;

CREATE INDEX idx_ai_job_source_section
  ON ai_generation_job(source_section_id)
  WHERE source_section_id IS NOT NULL;
CREATE INDEX idx_ai_fact_proposal_source_section
  ON ai_knowledge_fact_proposal(source_section_id)
  WHERE source_section_id IS NOT NULL;
CREATE INDEX idx_ai_question_source_section
  ON ai_question_generation_source(source_section_id)
  WHERE source_section_id IS NOT NULL;
