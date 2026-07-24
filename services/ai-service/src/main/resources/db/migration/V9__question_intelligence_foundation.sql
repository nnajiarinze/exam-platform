ALTER TABLE ai_question_proposal
  ADD COLUMN intelligence_evaluation_status varchar(24) NOT NULL DEFAULT 'NOT_EVALUATED',
  ADD COLUMN difficulty varchar(16),
  ADD COLUMN blooms_level varchar(16),
  ADD COLUMN complexity varchar(16),
  ADD COLUMN generation_intent varchar(16),
  ADD COLUMN estimated_reading_seconds integer,
  ADD COLUMN overall_quality_score integer,
  ADD COLUMN quality_level varchar(24),
  ADD COLUMN passed_intelligence_validation boolean,
  ADD COLUMN intelligence_engine_version varchar(32),
  ADD COLUMN quality_rationale varchar(500),
  ADD COLUMN provider_pedagogical_metadata jsonb,
  ADD COLUMN intelligence_evaluated_at timestamptz;

ALTER TABLE ai_question_proposal
  ADD CONSTRAINT ck_ai_question_quality_score CHECK (overall_quality_score BETWEEN 0 AND 100),
  ADD CONSTRAINT ck_ai_question_reading_seconds CHECK (estimated_reading_seconds > 0),
  ADD CONSTRAINT ck_ai_question_intelligence_status CHECK (intelligence_evaluation_status IN ('NOT_EVALUATED','EVALUATED'));

CREATE TABLE ai_question_intelligence_finding (
  id uuid PRIMARY KEY,
  proposal_id uuid NOT NULL REFERENCES ai_question_proposal(id) ON DELETE CASCADE,
  display_order integer NOT NULL,
  code varchar(80) NOT NULL,
  severity varchar(16) NOT NULL,
  category varchar(32) NOT NULL,
  message varchar(500) NOT NULL,
  field_path varchar(160),
  blocking boolean NOT NULL,
  details jsonb NOT NULL DEFAULT '{}'::jsonb,
  validator_name varchar(100) NOT NULL,
  validator_version varchar(32) NOT NULL,
  UNIQUE (proposal_id, display_order)
);

CREATE TABLE ai_question_intelligence_component_score (
  proposal_id uuid NOT NULL REFERENCES ai_question_proposal(id) ON DELETE CASCADE,
  component varchar(40) NOT NULL,
  score integer NOT NULL CHECK (score BETWEEN 0 AND 100),
  PRIMARY KEY (proposal_id, component)
);

CREATE INDEX ix_ai_question_proposal_intelligence_filters
  ON ai_question_proposal(quality_level, difficulty, question_type, created_at DESC)
  WHERE intelligence_evaluation_status = 'EVALUATED';
CREATE INDEX ix_ai_question_intelligence_finding_proposal
  ON ai_question_intelligence_finding(proposal_id, severity, display_order);
