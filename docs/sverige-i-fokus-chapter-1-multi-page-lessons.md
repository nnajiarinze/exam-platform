# Chapter 1 multi-page Study lessons

Date: 2026-07-31
Starting commit: `1577cea438742701c355c6ae400d1b74edd9f99f`

## Diagnosis

The one-page experience crossed the API and mobile layers faithfully. The defect began in generation and persistence: prompt `lesson-generation-v1` explicitly requested one lesson section, each reviewed lesson persisted one `lesson_draft_section`, and the active v1 projection therefore exposed one page. The mobile screen already supports an ordered section list, Previous/Next, resume, scrolling, final-page-only completion, and Study Again.

A second defect appeared only when the richer snapshot was projected: all sections in a lesson used the lesson ID as `external_section_version_id`. Learning correctly rejected those duplicates. Section checksums now provide distinct immutable page-version identities.

## Grounding and plans

Corpus: `docs/sverige-i-fokus.pdf`
Corpus SHA-256: `39a93261acbb55ae5498ccefe16a2e146f39aacfe19c3622ee2529e9bd41aada`

| Topic | Source Section | Approved facts | v1 pages | Planned/final pages | Final titles |
|---|---|---:|---:|---:|---|
| Geografi, klimat och natur | `2b1c8785-beb1-54ad-b268-c2004e45d76d` | 1 | 1 | 4/4 | Sverige på kartan; Landets geografi; Berg, sjöar och landskap; Det här ska du minnas |
| Sveriges indelning | `94b7294f-2711-52c1-867b-585dfed77ee5` | 3 | 1 | 3/3 | Så delas Sverige in; Landsdelar, län och kommuner; Det här ska du minnas |
| Befolkning | `936fc9dd-e35a-5844-9287-d5e2ae7f2af9` | 2 | 1 | 3/3 | Var människor bor; Städer och storstadsområden; Det här ska du minnas |
| Naturresurser | `2d97d182-7464-5a7a-83f6-b4c6115c93c3` | 3 | 1 | 4/4 | Sveriges naturresurser; Skog, malm och gruvor; Jordbruk, vatten och energi; Det här ska du minnas |
| Klimatförändringar | `77e35d6a-24af-51ae-924b-4d28e0d647a7` | 2 | 1 | 4/4 | Klimatet förändras; Varför jorden blir varmare; Följder av uppvärmningen; Det här ska du minnas |

Every accepted page retains its Source Section identity/checksum, mapped approved fact versions, and at least one validated source excerpt. PDF whitespace artifacts are normalized only for comparison; invalid extra excerpts are removed during revalidation. Page bodies are never repaired or accepted when structure, coverage, ordering, repetition, language, or grounding gates fail.

## Generation and review

Provider policy remained `FREE_ONLY`: Gemini `gemini-3.1-flash-lite` first, Groq `openai/gpt-oss-120b` second; Cloudflare and OpenRouter remained disabled and paid fallback remained forbidden. Gemini free quota was exhausted, so all successful jobs used Groq.

Successful final jobs and token usage:

| Topic | Job | Input/output tokens | Pages |
|---|---|---:|---:|
| Geografi | `66683d2b-0ce8-4453-b741-614af586063e` | 1,759 / 2,713 | 4 |
| Indelning | `46fcd317-512f-49d6-8692-e3bda1dfec75` | 1,522 / 1,847 | 3 |
| Befolkning | `ca94e1e2-8987-4cfa-80ea-1854edb07559` | 1,203 / 2,893 | 3 |
| Naturresurser | `c83e6df6-1885-43df-acbe-1d7e57a534ed` | 1,829 / 3,761 | 4 |
| Klimat | `51be9e04-b8a9-4752-8e08-835d5c131134` | 1,905 / 2,270 | 4 |

Two independent Naturresurser proposals were rejected after Groq reached its 4,096-token ceiling and returned only two of four planned pages. The bounded retry produced all four pages. Initial evidence formatting findings were repaired by retaining only excerpts that match the exact source after Unicode/whitespace normalization. No unsupported-content or material-repetition finding remained in an accepted proposal. All 18 final pages passed all mandatory gates and followed the existing Draft → Under Review → Reviewed audit flow with `human_verified=false`.

Final lesson IDs (version 2):

- `376bf1db-c4f4-45e9-a9a8-fdc34dcee2ba`
- `436693d5-88b2-48a2-8200-4f1bcdfbd0ac`
- `11934831-4d36-4912-8540-7a84c835a66c`
- `7080e805-6658-4cb9-acfc-955195b7cda8`
- `0f710b8e-246a-43bb-9e9f-c59f028224e9`

## Release and progress policy

The original active v1 release `835a50eb-6e45-4fac-a8ef-269c992311c7` and its five one-page lessons remain immutable and available for rollback. The first v2 snapshot `e9786b1f-0f09-4972-934f-6684ebe8a454` remains immutable with failed delivery diagnostics from the duplicate page-version defect. The corrected LOCAL release is:

- Content release: `ee96d2e0-9539-47c8-8c71-fd55684d7d67`
- Release key: `sverige-i-fokus-chapter-1-internal-v2.1`
- Status: `ACTIVE`
- Checksum: `1d42c244bcfe652e244b11cd1f73088c79db8ca675d021a75e1eec7023e44fbc`
- Learning projection: `8858888c-1b7e-45b1-8615-48e79fc13dcf`
- Counts: 1 subject, 5 topics, 18 pages, 11 questions, 43 options

Progress migration preserves an earned completion marker by stable external Topic ID but initializes the new release's page count at zero and page position at page 1. It does not claim newly added pages were read, revoke completion, erase profiles, or copy/erase practice history. Completing the final v2.1 page records a new-version completion. Study Again starts at page 1.

## Validation status

LOCAL API validation confirms five topics, page counts 4/3/3/4/4, deterministic ordering, 11 unchanged questions, no Chapters 2–13, and no legacy content. Mobile source was unchanged because its existing multi-page flow already meets the navigation behavior. Automated tests and hosted/device results are recorded in the task handoff when completed.
