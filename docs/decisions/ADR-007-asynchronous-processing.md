# ADR-007: Asynchronous processing

## Status

Accepted

## Context

Publishing, imports, AI generation, and provider callbacks can be slow or retried and should not hold request transactions open.

## Decision

Use a managed queue, preferring AWS SQS initially, for slow AI and publishing jobs. Consumers must be idempotent, retries bounded, and poison messages observable through dead-letter handling. Do not introduce Kafka initially.

## Consequences

### Positive

Workload buffering, managed operations, retry isolation, and independent scaling.

### Negative

At-least-once delivery requires deduplication; ordering and immediate consistency are limited.

## Alternatives considered

Synchronous processing has fragile latency. Kafka provides replay/streaming power not required by current flows. Database polling is acceptable only as a local/outbox implementation detail.

## Revisit conditions

Revisit when measured throughput, ordering, replay, or multi-consumer stream requirements exceed the managed queue model.

