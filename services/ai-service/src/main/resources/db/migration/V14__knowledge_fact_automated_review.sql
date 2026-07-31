ALTER TABLE ai_knowledge_fact_proposal
  ADD COLUMN automated_classification VARCHAR(30),
  ADD COLUMN validation_gates JSONB NOT NULL DEFAULT '{}'::jsonb,
  ADD COLUMN automated_reviewed_at TIMESTAMPTZ;

ALTER TABLE ai_knowledge_fact_proposal
  ADD CONSTRAINT ck_ai_knowledge_fact_automated_classification CHECK (
    automated_classification IS NULL OR automated_classification IN (
      'GOOD',
      'NEEDS_SPLIT',
      'NEEDS_REWRITE',
      'TOO_BROAD',
      'AMBIGUOUS',
      'UNSUPPORTED',
      'DUPLICATE'
    )
  );

CREATE INDEX idx_ai_knowledge_fact_automated_review
  ON ai_knowledge_fact_proposal(automated_classification, status, automated_reviewed_at);
