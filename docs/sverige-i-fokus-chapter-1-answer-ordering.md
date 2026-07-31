# Chapter 1 deterministic answer ordering

## Scope and decision

This Priority 1 change fixes learner-visible answer ordering without regenerating or editing any Knowledge Fact, Lesson, Question, option, explanation, evidence, or acceptance record. Chapters 2–13 remain absent. The platform now treats option correctness as semantic data identified by the stable option ID, while release-specific list position is presentation data.

The ordering boundary is Content Service release snapshot assembly. This is the narrowest boundary that keeps canonical editorial versions and proposal lineage unchanged, makes rollback release-specific, and gives Learning Service a checksummed immutable order to persist. Learning Service and mobile continue to render the supplied order without runtime randomization.

## Root cause and trace

All 11 accepted AI proposals stored the correct proposal option at `display_order=0` (`option_key=A`). Acceptance copied that ordered semantic content into canonical `question_option`; all 11 canonical correct options therefore also had `display_order=0`. The v2.1 release snapshot copied canonical display order unchanged. Learning projection persisted it as `sort_order`, the learner API selected by `sort_order`, and mobile mapped the ordered array directly while deriving A/B/C/D from its index.

The first source boundary containing the defect was the provider proposal. The first platform presentation boundary that failed to normalize it was release snapshot assembly. Neither Learning Service nor mobile reordered the options. Correctness was never inferred from index or label: practice and mock submissions use stable option IDs, and backend scoring resolves the `correct` relationship by option identity.

## Ordering policy

Policy version: `release-option-order-v1`.

The stable seed material is:

```text
corpusId | releaseVersion | orderingPolicyVersion
```

Question ID is appended for question ordering, target-position selection, and distractor ordering. SHA-256 supplies deterministic seed material. Questions receive a balanced correct-position assignment by choosing among the least-used valid positions, avoiding an immediately repeated position when a tied alternative exists, with deterministic hash tie-breaking. Remaining distractors use a seeded Fisher–Yates shuffle. Questions with fewer options participate only in their valid positions. Non-single-choice options receive a deterministic whole-list shuffle without single-correct balancing.

Ordering is generated once into the immutable published snapshot, included in its checksum, persisted by Learning projection, and stable across API retries, app reloads, and idempotent re-import. A future release version intentionally has independent seed material.

## Chapter 1 v2.2 results

Before: A=11, B=0, C=0, D=0.

After: A=2, B=3, C=3, D=3.

| Canonical Question ID | Correct option ID | Position | Options |
|---|---|---:|---:|
| `5687946b-2007-4878-93a1-7899b038d977` | `4afb2138-41fd-447d-9447-26859c667f45` | A | 4 |
| `7f0e759a-9327-495a-85e8-cbf643d97b2b` | `c0754888-2a8c-41ba-b8c9-891d96338d1e` | A | 4 |
| `bd50e9ea-2110-49cf-a95e-0d81c52d155c` | `5ae73276-0564-4687-b21b-fbdb6842a84b` | B | 4 |
| `b31e8883-02cb-4706-8378-4495bc838ac0` | `f514a7db-55f3-4d58-9ed9-f907b3e16665` | B | 4 |
| `9368bd68-e4b7-4439-9aec-daf2e553a67e` | `782870da-6cac-46c9-b493-a969fc496c63` | B | 4 |
| `71b14cf1-ecc5-4209-9e85-a6f47ff68b80` | `b4cca029-4f03-4d33-8d56-b841d2505ae5` | C | 3 |
| `c8c58b21-60a6-430a-a151-79301ef36141` | `c031da1d-a9dc-41ab-aba8-a8c6b29d917f` | C | 4 |
| `e568712f-e8e9-4f7b-b314-9ca7dbfd4b03` | `219a0ba6-6aa6-4c86-880b-80c97076469c` | C | 4 |
| `1e5746a6-c2ff-4009-b481-e3ea84761667` | `120d2de1-fee1-4395-8db0-fe7cd84d3990` | D | 4 |
| `5aa3a404-ec84-4532-857b-710773122f18` | `90c0439c-a5ca-426d-a501-47331abc1ccf` | D | 4 |
| `5d437558-3976-4420-b8aa-b2523dd436a8` | `58fe096e-0155-4862-ba85-66cc5c784324` | D | 4 |

Semantic hashes of v2.1 and v2.2 question IDs, prompts, explanations, correct-option IDs, and ID-sorted option text/correctness/feedback are identical. Lesson hashes are also identical.

## Identity and history compatibility

Canonical Question IDs, Question version IDs, option IDs, fact links, proposal IDs, and acceptance lineage are preserved. Existing responses already reference imported options associated with their pinned release; selected option IDs are also preserved in response-selection records. No migration or compatibility translation is required. v2.1 and its Learning projection remain available for rollback and historical sessions.

## Local release

- Content release: `6d6d22c9-62dc-48c4-b947-78536518ab37`
- Release identifier: `sverige-i-fokus-chapter-1-internal-v2.2`
- Status: `ACTIVE`
- Checksum: `07aa8cd8c76d9a2a0939aca6c210830beab29b17ac1ad1207683464febf35daf`
- Learning projection: `ece0f44c-6c93-491d-ad1f-98dbb709a788`
- Projection re-import: `imported=false`, `status=ACTIVE`

The learner API returned the persisted order for all 11 questions. Correct submissions succeeded at A, B, C, and D positions; incorrect submission returned `correct=false`, the stable correct option ID, option feedback, and the unchanged explanation. Repeated reads returned identical arrays.

## Hosted validation

Implementation commit `25946c3c9d3d596515d14ba0fc27e7754b1e356b` passed CI run `30642116981`, including backend, admin, mobile, Gitleaks, hosted Compose, image builds, and vulnerability scans. Immutable image run `30642329947` and hosted deployment run `30642529197` succeeded.

The v2.2 snapshot was transferred as a temporary encrypted GitHub hosted-environment secret and promoted through Learning Service's authenticated internal import and activation APIs in run `30643095790`. No raw database rows were copied. The temporary runner/host files and environment secret were removed. The importer validated external Content release `6d6d22c9-62dc-48c4-b947-78536518ab37` and checksum `07aa8cd8c76d9a2a0939aca6c210830beab29b17ac1ad1207683464febf35daf`; idempotent retries returned the same projection and activation remained `ACTIVE`. The internal hosted projection UUID was deliberately not copied out through a database query and is not exposed by the activation DTO.

The existing hosted iOS build was relaunched on the connected physical iPhone. Physical Chapter 1 Practice validation confirmed correct answers appear in different A/B/C/D positions under the active v2.2 release. The user completed the requested submission/feedback/reload check and reported success. Existing multi-page Study lessons remain unchanged by semantic hash. No mobile source or API contract changed, so no mobile rebuild was required.
