# Phase 1.5B.1 editorial input validation

The reproduced defect allowed `random shit to test asdfsdfas sffasdfsdfwwas safdsaf ds` to reach `DETECT_AMBIGUITY`. The deterministic fake provider interpreted the absence of known ambiguity patterns as clarity and persisted `NO_AMBIGUITY_FOUND` / `KEEP_AS_IS`.

The corrected pipeline validates at draft save, review submission, Content-to-AI job eligibility, the AI internal boundary, provider-output consistency, and proposal acceptance. Invalid Content requests return `KNOWLEDGE_FACT_TEXT_INVALID` or `AI_EDITORIAL_INPUT_INVALID` with user-safe issue codes and `EDIT_FACT`; direct invalid AI requests create no job and consume no tokens. Suspicious inputs cannot receive an unqualified positive result.

The Admin editor and editorial workspace reuse existing form, alert, warning, button, and accessibility patterns. Invalid drafts show an announced error and disable job creation. Suspicious drafts show a warning. Validation is dynamically tied to current text, so correcting a legacy draft restores eligibility. No schema migration is needed.

Known limitation: structural and lexical heuristics identify obvious garbage but cannot establish factual correctness. Real-provider semantic validation remains future work.

Stitch MCP was not accessed, its connectivity was not checked, and no online Stitch lookup was performed. Existing local Admin patterns were the UI source of truth.
