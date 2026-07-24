CREATE TABLE ai_question_generation_batch (
  id uuid PRIMARY KEY,
  scope_type varchar(40) NOT NULL,
  scope_id uuid,
  scope_label varchar(300),
  language varchar(10) NOT NULL,
  configuration jsonb NOT NULL,
  definition_checksum char(64) NOT NULL,
  requested_by varchar(200) NOT NULL,
  idempotency_key varchar(200) NOT NULL,
  status varchar(30) NOT NULL,
  cancellation_requested boolean NOT NULL DEFAULT false,
  provider varchar(80) NOT NULL,
  model varchar(160) NOT NULL,
  created_at timestamptz NOT NULL,
  started_at timestamptz,
  completed_at timestamptz,
  cancelled_at timestamptz,
  version bigint NOT NULL DEFAULT 0,
  CONSTRAINT uq_ai_question_batch_idempotency UNIQUE(requested_by,idempotency_key),
  CONSTRAINT ck_ai_question_batch_status CHECK(status IN ('PENDING','RUNNING','PARTIALLY_COMPLETED','COMPLETED','FAILED','CANCELLING','CANCELLED'))
);

CREATE TABLE ai_question_generation_batch_item (
  id uuid PRIMARY KEY,
  batch_id uuid NOT NULL REFERENCES ai_question_generation_batch(id),
  sequence integer NOT NULL,
  target_snapshot jsonb NOT NULL,
  context_snapshot jsonb NOT NULL,
  knowledge_fact_id uuid NOT NULL,
  knowledge_fact_version_id uuid NOT NULL,
  knowledge_fact_version bigint NOT NULL,
  knowledge_fact_checksum char(64) NOT NULL,
  topic_id uuid,
  subject_id uuid,
  exam_version_id uuid,
  question_type varchar(30) NOT NULL,
  language varchar(10) NOT NULL,
  target_difficulty varchar(30),
  target_bloom_level varchar(30),
  status varchar(30) NOT NULL,
  generation_job_id uuid REFERENCES ai_generation_job(id),
  proposal_id uuid REFERENCES ai_question_proposal(id),
  attempt_count integer NOT NULL DEFAULT 0,
  rate_limit_count integer NOT NULL DEFAULT 0,
  failure_code varchar(120),
  failure_message varchar(500),
  last_failure_at timestamptz,
  next_retry_at timestamptz,
  lease_owner varchar(200),
  lease_until timestamptz,
  created_at timestamptz NOT NULL,
  started_at timestamptz,
  completed_at timestamptz,
  version bigint NOT NULL DEFAULT 0,
  CONSTRAINT uq_ai_question_batch_item_sequence UNIQUE(batch_id,sequence),
  CONSTRAINT uq_ai_question_batch_item_definition UNIQUE(batch_id,knowledge_fact_id,question_type,target_difficulty,target_bloom_level,sequence),
  CONSTRAINT uq_ai_question_batch_item_job UNIQUE(generation_job_id),
  CONSTRAINT uq_ai_question_batch_item_proposal UNIQUE(proposal_id),
  CONSTRAINT ck_ai_question_batch_item_status CHECK(status IN ('PENDING','PROCESSING','RETRY_SCHEDULED','GENERATED','FAILED','CANCELLED')),
  CONSTRAINT ck_ai_question_batch_item_attempt CHECK(attempt_count >= 0)
);

CREATE TABLE ai_question_proposal_review_assignment (
  proposal_id uuid PRIMARY KEY REFERENCES ai_question_proposal(id),
  assigned_reviewer_id varchar(200) NOT NULL,
  assigned_by varchar(200) NOT NULL,
  assigned_at timestamptz NOT NULL,
  review_deadline timestamptz,
  review_status varchar(20) NOT NULL DEFAULT 'ASSIGNED',
  version bigint NOT NULL DEFAULT 0,
  CONSTRAINT ck_ai_question_review_assignment_status CHECK(review_status IN ('ASSIGNED','IN_REVIEW','REVIEWED'))
);

CREATE INDEX ix_ai_question_batch_status_created ON ai_question_generation_batch(status,created_at DESC);
CREATE INDEX ix_ai_question_batch_scope ON ai_question_generation_batch(scope_type,scope_id,created_at DESC);
CREATE INDEX ix_ai_question_batch_item_claim ON ai_question_generation_batch_item(status,next_retry_at,lease_until,created_at);
CREATE INDEX ix_ai_question_batch_item_fact ON ai_question_generation_batch_item(knowledge_fact_id,batch_id);
CREATE INDEX ix_ai_question_batch_item_filters ON ai_question_generation_batch_item(batch_id,status,question_type,target_difficulty,target_bloom_level);
CREATE INDEX ix_ai_question_review_reviewer ON ai_question_proposal_review_assignment(assigned_reviewer_id,review_status,assigned_at);
