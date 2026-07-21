# AI provenance

AI lineage binds an output to the immutable generation snapshot: operation, job/proposal, provider/model, prompt version, generation time, target fact/version/checksum, Source IDs/checksums, requesting actor, evidence, warnings, provider request ID, and token usage when available. Secrets and credentials are never persisted or returned.

Evidence entries retain the exact Source ID and meaningful Source title used by the job. Failed grounding and no-op outcomes remain observable through typed results, finding/audit metadata, and aggregate counters; full Source content is not logged.

Accepted Phase 1 fact generation records lineage on the created fact version. Accepted Phase 1.5A revisions record the original, proposed, edited, and final content and update only the existing eligible draft.

Phase 1.5B split acceptance records one provenance row for each resulting fact version. Rows include the `CREATE_SELECTED_DRAFTS_KEEP_ORIGINAL` action, original target snapshot, selected proposal, final checksum, accepting actor, evidence, and all sibling resulting fact IDs. The separate idempotency record links the job, original fact, selected proposals, and complete result set. The original fact is not rewritten.

Persistent findings are audit-friendly AI Service records with type, severity, target, explanation, affected phrase, evidence, confidence, suggested action, provider metadata, and lifecycle timestamps. Dismissal appends actor/reason metadata and does not erase the finding.

Database audit triggers cover job, proposal, finding, Knowledge Fact, and fact-version mutations. Provenance tables are application-write-once and exposed read-only. AI lineage does not grant review status, publish content, or appear in learner release snapshots.
