#!/usr/bin/env python3
"""Deterministically audit source-v2 evidence utilization before Fact generation."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import uuid
from collections import Counter
from pathlib import Path


CORPUS_ID = "sverige-i-fokus-v1"
SOURCE_REVISION = "sverige-i-fokus-source-v2"
ACTOR = "codex-fact-density-audit"
NAMESPACE = uuid.UUID("3a23e874-ef64-5a39-a54b-29c118e00537")
STOPWORDS = frozenset("och i att det som en ett är av för på till med om den de har eller sig från var så kan ska inte sin sina ett".split())
LEADING_CONTEXT = re.compile(
    r"^(detta|dessa|därför|dessutom|samtidigt|men|och|här|där|till exempel|på så sätt|"
    r"det|de|den|man|sådana|sådan|han|hon)\b",
    re.I,
)
NON_INSTRUCTIONAL = re.compile(
    r"^(?:(?i:foto|bild|källa|kapitel\s+\d+|sverige i fokus)|[A-ZÅÄÖ\s]{5,})[:\s]"
)
VERB = re.compile(r"\b(är|har|finns|innebär|gäller|kan|ska|får|måste|ansvarar|bestämmer|väljs|utses|styrs|betalar|arbetar|skyddar|förbjuder|blev|var|infördes|bildades|anslöt|bygger|finansieras|kallas|omfattar|består|reglerar|granskar|påverkar|ökar|minskar|ger|gör)\b", re.I)


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", value.replace("\xad", "")).strip()


def fingerprint(value: str) -> str:
    return hashlib.sha256(normalize(value).casefold().encode()).hexdigest()


def tokens(value: str) -> set[str]:
    def stem(word: str) -> str:
        for suffix in ("ernas", "arnas", "ernas", "ande", "arna", "erna", "ens", "ets", "nas", "ers", "ars", "en", "et", "ar", "er", "or", "s"):
            if len(word) > len(suffix) + 4 and word.endswith(suffix):
                return word[:-len(suffix)]
        return word
    return {stem(word) for word in re.findall(r"[0-9a-zåäö]+", normalize(value).casefold()) if len(word) > 2 and word not in STOPWORDS}


def similarity(left: str, right: str) -> float:
    a, b = tokens(left), tokens(right)
    return 0.0 if not a or not b else len(a & b) / len(a | b)


def evidence_quotes(fact: dict) -> list[str]:
    result = []
    for value in fact.get("evidence") or []:
        quote = value.get("quote") if isinstance(value, dict) else value
        if quote and normalize(str(quote)):
            result.append(normalize(str(quote)))
    return result


def covered_ranges(source: str, quotes: list[str]) -> list[tuple[int, int]]:
    folded = normalize(source).casefold()
    ranges = []
    for quote in quotes:
        needle = normalize(quote).casefold()
        start = folded.find(needle)
        if start >= 0:
            ranges.append((start, start + len(needle)))
    merged = []
    for start, end in sorted(ranges):
        if merged and start <= merged[-1][1]:
            merged[-1] = (merged[-1][0], max(end, merged[-1][1]))
        else:
            merged.append((start, end))
    return merged


def sentence_candidates(text: str) -> list[str]:
    paragraphs = [normalize(value) for value in re.split(r"\n\s*\n", text) if normalize(value)]
    candidates = []
    for paragraph in paragraphs:
        for sentence in re.split(r"(?<=[.!?])\s+(?=[A-ZÅÄÖ0-9])", paragraph):
            sentence = normalize(sentence).strip(" •–-")
            if 25 <= len(sentence) <= 420:
                candidates.append(sentence)
    return candidates


def concept_inventory(section: dict) -> list[dict]:
    facts = section["facts"]
    represented = [fact["text"] for fact in facts]
    quotes = [quote for fact in facts for quote in evidence_quotes(fact)]
    concepts = [{"classification": "REPRESENTED_BY_APPROVED_FACT", "conceptText": fact["text"],
                 "exactEvidence": evidence_quotes(fact)[0] if evidence_quotes(fact) else None,
                 "generationEligible": False, "diagnostic": "Existing approved active Fact."} for fact in facts]
    seen = set()
    for sentence in sentence_candidates(section["exactText"]):
        key = fingerprint(sentence)
        if key in seen:
            continue
        seen.add(key)
        best_fact = max((similarity(sentence, fact) for fact in represented), default=0.0)
        best_evidence = max((similarity(sentence, quote) for quote in quotes), default=0.0)
        if best_fact >= 0.48 or best_evidence >= 0.62:
            classification, eligible, diagnostic = "DUPLICATE", False, "Semantically represented by approved Fact evidence."
        elif NON_INSTRUCTIONAL.search(sentence) or len(tokens(sentence)) < 4:
            classification, eligible, diagnostic = "SUPPORTING_CONTEXT_ONLY", False, "Layout, heading, caption, or low-information context."
        elif LEADING_CONTEXT.search(sentence) or not VERB.search(sentence):
            classification, eligible, diagnostic = "SUPPORTING_CONTEXT_ONLY", False, "Not independently understandable as one teaching proposition."
        else:
            classification, eligible, diagnostic = "EXPLICIT_IN_SOURCE_BUT_NOT_FACT", True, "Explicit bounded-source claim not semantically represented by an approved Fact."
        concepts.append({"classification": classification, "conceptText": sentence,
                         "exactEvidence": sentence if eligible else None,
                         "generationEligible": eligible, "diagnostic": diagnostic})
    return concepts


def classify(section: dict, eligible: list[dict]) -> str:
    normalized_length = len(normalize(section["normalizedText"]))
    if not eligible:
        return "SOURCE_TOO_THIN" if normalized_length < 260 and len(section["facts"]) <= 1 else "FULLY_REPRESENTED"
    if len(eligible) == 1:
        return "UNDERUTILIZED_ONE_FACT"
    if len(eligible) == 2:
        return "UNDERUTILIZED_TWO_FACTS"
    if len(sentence_candidates(section["exactText"])) >= 14 and len(eligible) >= 5:
        return "SOURCE_STRUCTURALLY_COMPLEX"
    return "UNDERUTILIZED_THREE_OR_MORE_FACTS"


def safe_additional_count(section: dict, eligible: list[dict]) -> int:
    """Apply the source-driven diagnostic ceiling without turning it into a quota."""
    if not eligible:
        return 0
    source_sentences = len(sentence_candidates(section["exactText"]))
    final_ceiling = 2 if source_sentences <= 5 else 5 if source_sentences <= 13 else 8
    return min(len(eligible), max(0, final_ceiling - len(section["facts"])))


def lesson_readiness(approved_fact_count: int, remaining_uncovered: int) -> str:
    if approved_fact_count >= 4:
        return "READY_FOR_DEEPER_LESSON"
    if approved_fact_count >= 2 or remaining_uncovered > 0:
        return "LIMITED_BUT_USABLE"
    return "SOURCE_EXPANSION_REQUIRED"


def query_snapshot() -> dict:
    sql = """
    WITH active AS (
      SELECT f.id fact_id,f.current_version_id,v.canonical_statement,f.learning_objective_id
      FROM knowledge_fact f JOIN knowledge_fact_version v ON v.id=f.current_version_id
      WHERE f.status='ACTIVE' AND f.review_status='APPROVED' AND v.review_status='APPROVED'
    ), effective AS (
      SELECT a.*,coalesce(c.source_section_id,v2.id,p.source_section_id) source_section_id,
        coalesce(c.corrected_source_evidence,p.source_evidence) source_evidence
      FROM active a JOIN knowledge_fact_ai_provenance p ON p.knowledge_fact_version_id=a.current_version_id
      LEFT JOIN LATERAL (SELECT * FROM knowledge_fact_evidence_provenance_correction x
        WHERE x.knowledge_fact_version_id=a.current_version_id AND x.validation_status='PASS'
        ORDER BY x.revision_number DESC LIMIT 1)c ON true
      LEFT JOIN source_section old ON old.id=p.source_section_id
      LEFT JOIN source_section v2 ON v2.source_revision_id='sverige-i-fokus-source-v2'
        AND v2.logical_section_id=old.logical_section_id
    )
    SELECT jsonb_build_object('sections',(SELECT jsonb_agg(jsonb_build_object(
      'id',ss.id,'sourceReferenceId',ss.source_reference_id,'logicalSectionId',ss.logical_section_id,'title',ss.subsection_title,
      'checksum',ss.section_checksum,'pageStart',ss.page_start,'pageEnd',ss.page_end,
      'exactText',ss.exact_text,'normalizedText',ss.normalized_text,'topicId',t.id,'topic',t.name,
      'chapter',s.name,'objectiveId',lo.id,'objective',lo.title,'facts',coalesce((SELECT jsonb_agg(
        jsonb_build_object('factId',e.fact_id,'factVersionId',e.current_version_id,'text',e.canonical_statement,
          'evidence',e.source_evidence) ORDER BY e.canonical_statement)
        FROM effective e WHERE e.learning_objective_id=lo.id AND e.source_section_id=ss.id),'[]'::jsonb)
      ) ORDER BY ss.display_order)
      FROM source_section ss JOIN learning_objective_source_section los ON los.source_section_id=ss.id
      JOIN learning_objective lo ON lo.id=los.learning_objective_id JOIN topic t ON t.id=lo.topic_id
      JOIN subject s ON s.id=t.subject_id WHERE ss.source_revision_id='sverige-i-fokus-source-v2'))::text;
    """
    command = ["docker", "compose", "exec", "-T", "content-database", "psql", "-U", "content", "-d", "content", "-Atc", sql]
    return json.loads(subprocess.run(command, check=True, capture_output=True, text=True).stdout)


def build(snapshot: dict) -> dict:
    sections = []
    for section in snapshot["sections"]:
        concepts = concept_inventory(section)
        eligible = [item for item in concepts if item["generationEligible"]]
        source = normalize(section["normalizedText"])
        quotes = [quote for fact in section["facts"] for quote in evidence_quotes(fact)]
        ranges = covered_ranges(source, quotes)
        covered = sum(end - start for start, end in ranges)
        item = {key: section[key] for key in ("chapter", "topic", "topicId", "objective", "objectiveId", "id", "sourceReferenceId", "logicalSectionId", "title", "checksum", "pageStart", "pageEnd")}
        maximum_safe = safe_additional_count(section, eligible)
        item.update({"normalizedCharacterCount": len(source), "approvedFactCount": len(section["facts"]),
                     "approvedFacts": section["facts"], "usedEvidenceSpans": quotes,
                     "evidenceCoveredCharacters": covered,
                     "evidenceCoveragePercentage": round(100 * covered / len(source), 3),
                     "teachingConcepts": concepts,
                     "remainingUnusedClaims": [value["conceptText"] for value in eligible],
                     "importantConceptsNotRepresented": [value["conceptText"] for value in eligible],
                     "nonInstructionalOrRepeated": [value["conceptText"] for value in concepts if value["classification"] in {"DUPLICATE", "SUPPORTING_CONTEXT_ONLY"}],
                     "maximumSafeAdditionalFactCount": maximum_safe,
                     "generationTargets": [value["conceptText"] for value in eligible[:maximum_safe]],
                     "classification": classify(section, eligible)})
        sections.append(item)
    definition = {"corpusId": CORPUS_ID, "sourceRevision": SOURCE_REVISION, "algorithm": "fact-density-audit-v1", "sections": sections}
    checksum = hashlib.sha256(json.dumps(definition, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode()).hexdigest()
    return {"auditId": str(uuid.uuid5(NAMESPACE, checksum)), "definitionChecksum": checksum, **definition}


def sql_literal(value) -> str:
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, sort_keys=True)
    return "'" + str(value).replace("'", "''") + "'"


def persist(audit: dict) -> None:
    lines = ["BEGIN;", "SELECT pg_advisory_xact_lock(hashtext('sverige-i-fokus-fact-density-audit-v1'));",
             "INSERT INTO ai_fact_density_audit(id,corpus_id,source_revision_id,definition_checksum,status,source_section_count,existing_fact_count,created_by,created_at) VALUES(" + ",".join((sql_literal(audit["auditId"]),sql_literal(CORPUS_ID),sql_literal(SOURCE_REVISION),sql_literal(audit["definitionChecksum"]),sql_literal("AUDITED"),str(len(audit["sections"])),str(sum(s["approvedFactCount"] for s in audit["sections"])),sql_literal(ACTOR),"now()")) + ") ON CONFLICT DO NOTHING;"]
    for section in audit["sections"]:
        section_id = str(uuid.uuid5(uuid.UUID(audit["auditId"]), "section:" + section["id"]))
        section_checksum = fingerprint(json.dumps(section, ensure_ascii=False, sort_keys=True))
        snapshot = {key: value for key, value in section.items() if key not in {"approvedFacts", "teachingConcepts"}}
        lines.append("INSERT INTO ai_fact_density_section_audit(id,fact_density_audit_id,source_section_id,source_section_checksum,topic_id,learning_objective_id,classification,normalized_character_count,approved_fact_count,evidence_covered_characters,evidence_coverage_percentage,maximum_safe_additional_fact_count,audit_snapshot,audit_checksum,status,created_at) VALUES(" + ",".join((sql_literal(section_id),sql_literal(audit["auditId"]),sql_literal(section["id"]),sql_literal(section["checksum"]),sql_literal(section["topicId"]),sql_literal(section["objectiveId"]),sql_literal(section["classification"]),str(section["normalizedCharacterCount"]),str(section["approvedFactCount"]),str(section["evidenceCoveredCharacters"]),str(section["evidenceCoveragePercentage"]),str(section["maximumSafeAdditionalFactCount"]),sql_literal(snapshot),sql_literal(section_checksum),sql_literal("AUDITED"),"now()")) + ") ON CONFLICT DO NOTHING;")
        for order, concept in enumerate(section["teachingConcepts"]):
            concept_id = str(uuid.uuid5(uuid.UUID(section_id), "concept:" + str(order) + ":" + fingerprint(concept["conceptText"])))
            evidence = "NULL" if concept["exactEvidence"] is None else sql_literal(concept["exactEvidence"])
            lines.append("INSERT INTO ai_fact_density_teaching_concept(id,section_audit_id,concept_order,classification,concept_text,exact_evidence,semantic_fingerprint,generation_eligible,diagnostic,created_at) VALUES(" + ",".join((sql_literal(concept_id),sql_literal(section_id),str(order),sql_literal(concept["classification"]),sql_literal(concept["conceptText"]),evidence,sql_literal(fingerprint(concept["conceptText"])),str(concept["generationEligible"]).lower(),sql_literal(concept["diagnostic"]),"now()")) + ") ON CONFLICT DO NOTHING;")
    lines.extend(("COMMIT;", ""))
    subprocess.run(["docker", "compose", "exec", "-T", "ai-database", "psql", "-v", "ON_ERROR_STOP=1", "-U", "ai", "-d", "ai"], input="\n".join(lines), text=True, check=True)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--output", type=Path, default=Path("content/sverige-i-fokus/fact-density-audit-v1.json"))
    parser.add_argument("--persist", action="store_true")
    args = parser.parse_args()
    audit = build(query_snapshot())
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n")
    if args.persist:
        persist(audit)
    print(json.dumps({"auditId": audit["auditId"], "sections": len(audit["sections"]),
                      "existingFacts": sum(s["approvedFactCount"] for s in audit["sections"]),
                      "missingConcepts": sum(s["maximumSafeAdditionalFactCount"] for s in audit["sections"]),
                      "classifications": Counter(s["classification"] for s in audit["sections"])}, sort_keys=True))


if __name__ == "__main__":
    main()
