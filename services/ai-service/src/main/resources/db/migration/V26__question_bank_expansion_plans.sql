CREATE TABLE ai_question_bank_expansion_plan (
  id uuid PRIMARY KEY,
  corpus_id varchar(120) NOT NULL,
  source_revision_id varchar(120) NOT NULL,
  starting_question_count integer NOT NULL,
  target_minimum integer NOT NULL,
  target_maximum integer NOT NULL,
  status varchar(30) NOT NULL,
  definition_checksum char(64) NOT NULL,
  created_by varchar(200) NOT NULL,
  created_at timestamptz NOT NULL,
  completed_at timestamptz,
  CONSTRAINT uq_question_bank_expansion_definition UNIQUE(corpus_id,source_revision_id,definition_checksum),
  CONSTRAINT ck_question_bank_expansion_status CHECK(status IN ('AUDITING','PLANNED','GENERATING','VALIDATING','COMPLETED','BLOCKED')),
  CONSTRAINT ck_question_bank_expansion_targets CHECK(starting_question_count>=0 AND target_minimum>=starting_question_count AND target_maximum>=target_minimum)
);

ALTER TABLE ai_question_generation_batch_item
  ADD COLUMN narrow_target_snapshot jsonb;

CREATE TABLE ai_question_fact_density_audit (
  id uuid PRIMARY KEY,
  expansion_plan_id uuid NOT NULL REFERENCES ai_question_bank_expansion_plan(id),
  knowledge_fact_id uuid NOT NULL,
  knowledge_fact_version_id uuid NOT NULL,
  knowledge_fact_version bigint NOT NULL,
  fact_checksum char(64) NOT NULL,
  fact_snapshot text NOT NULL,
  chapter_label varchar(300) NOT NULL,
  topic_id uuid NOT NULL,
  topic_label varchar(300) NOT NULL,
  objective_id uuid NOT NULL,
  objective_label varchar(500) NOT NULL,
  source_section_id uuid NOT NULL,
  source_section_checksum char(64) NOT NULL,
  existing_question_ids jsonb NOT NULL,
  existing_question_snapshots jsonb NOT NULL,
  existing_target_labels jsonb NOT NULL,
  semantic_overlap varchar(30) NOT NULL,
  density_classification varchar(40) NOT NULL,
  safe_total_question_count integer NOT NULL,
  reason text NOT NULL,
  audited_at timestamptz NOT NULL,
  CONSTRAINT uq_question_fact_density_audit UNIQUE(expansion_plan_id,knowledge_fact_id),
  CONSTRAINT ck_question_fact_density_classification CHECK(density_classification IN ('ONE_QUESTION_ONLY','TWO_DISTINCT_QUESTIONS','THREE_DISTINCT_QUESTIONS','FOUR_OR_MORE_DISTINCT_QUESTIONS','NOT_SAFELY_EXPANDABLE')),
  CONSTRAINT ck_question_fact_density_total CHECK(safe_total_question_count BETWEEN 0 AND 4)
);

CREATE TABLE ai_question_target_plan (
  id uuid PRIMARY KEY,
  density_audit_id uuid NOT NULL REFERENCES ai_question_fact_density_audit(id),
  target_order integer NOT NULL,
  target_type varchar(50) NOT NULL,
  tested_proposition text NOT NULL,
  allowed_question_form varchar(40) NOT NULL,
  exact_evidence text NOT NULL,
  correct_answer_boundary text NOT NULL,
  forbidden_duplicate_target_labels jsonb NOT NULL,
  forbidden_interpretations jsonb NOT NULL,
  distractor_constraints jsonb NOT NULL,
  explanation_scope text NOT NULL,
  target_checksum char(64) NOT NULL,
  status varchar(30) NOT NULL DEFAULT 'PLANNED',
  generation_job_id uuid REFERENCES ai_generation_job(id),
  proposal_id uuid REFERENCES ai_question_proposal(id),
  created_at timestamptz NOT NULL,
  updated_at timestamptz NOT NULL,
  CONSTRAINT uq_question_target_order UNIQUE(density_audit_id,target_order),
  CONSTRAINT uq_question_target_semantics UNIQUE(density_audit_id,target_checksum),
  CONSTRAINT uq_question_target_job UNIQUE(generation_job_id),
  CONSTRAINT uq_question_target_proposal UNIQUE(proposal_id),
  CONSTRAINT ck_question_target_status CHECK(status IN ('PLANNED','QUEUED','GENERATED','ACCEPTED','REJECTED','BLOCKED')),
  CONSTRAINT ck_question_target_order CHECK(target_order>=1)
);

CREATE INDEX ix_question_target_plan_status ON ai_question_target_plan(status,created_at);
CREATE INDEX ix_question_density_fact ON ai_question_fact_density_audit(knowledge_fact_id,knowledge_fact_version_id);
