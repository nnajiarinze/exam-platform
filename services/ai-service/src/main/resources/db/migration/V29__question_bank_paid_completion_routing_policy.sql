ALTER TABLE ai_provider_routing_decision DROP CONSTRAINT ck_ai_routing_policy;
ALTER TABLE ai_provider_routing_decision ADD CONSTRAINT ck_ai_routing_policy
  CHECK (billing_policy IN (
    'FREE_ONLY',
    'FREE_FIRST_CAPPED_PAID',
    'QUESTION_BANK_PAID_COMPLETION'
  ));
