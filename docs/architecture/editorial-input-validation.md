# Editorial input validation

Phase 1.5B.1 adds deterministic quality gates before AI execution. Content Service is the authoritative first boundary; AI Service repeats a smaller defensive check for direct internal calls and rejects provider results that conflict with deterministic concerns.

Quality is calculated from the current canonical text and is never cached as authoritative state:

- `VALID`: a plausible declarative civic claim. All otherwise-authorized operations remain available.
- `SUSPICIOUS`: meaningful but vague or structurally weak. Analysis operations may run; transformations are blocked until wording or grounding improves.
- `INVALID`: placeholder, keyboard mash, HTML/script, prompt injection, URL/identifier-only, question/instruction-only, or content without meaningful words. No AI job is created.

Severe invalid input is blocked on draft save. Imperfect but meaningful drafts remain saveable. Submission for human review requires `VALID`; legacy invalid drafts remain readable but cannot be submitted or analyzed until edited.

Checks use Unicode NFC normalization, Swedish/English declarative verbs, token and alphabetic ratios, placeholder patterns, keyboard-mash/vowel checks, repetition checks, and fact-shape checks. They intentionally do not implement a grammar parser or require a dictionary, preserving Swedish compounds and names.

No full Source text or offensive text is logged; audit and metrics use fact IDs, operation, quality, and issue codes. Lexical validation cannot prove factual truth. Grounding, independent human review, separation of duties, and release validation remain mandatory.
