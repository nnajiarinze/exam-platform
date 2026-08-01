#!/usr/bin/env python3
"""Plan a guarded Sverige i fokus question-bank expansion before provider use.

The planner is deliberately deterministic. It consumes immutable content snapshots,
classifies each Fact, and emits at most one generation target per distinct learner
outcome. Provider execution is a later, explicit phase.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import uuid
from dataclasses import dataclass
from pathlib import Path

CLASS_BY_TOTAL = {
    0: "NOT_SAFELY_EXPANDABLE",
    1: "ONE_QUESTION_ONLY",
    2: "TWO_DISTINCT_QUESTIONS",
    3: "THREE_DISTINCT_QUESTIONS",
    4: "FOUR_OR_MORE_DISTINCT_QUESTIONS",
}


@dataclass(frozen=True)
class Target:
    target_type: str
    tested_proposition: str
    correct_answer_boundary: str


def normalized(value: str) -> str:
    return re.sub(r"\s+", " ", value.strip()).casefold()


def normalized_pdf_evidence(value: str) -> str:
    """Match the content-service PDF evidence normalization contract."""
    value = value.replace("\u00a0", " ").replace("\u00ad", "")
    return re.sub(r"\s+", " ", value.strip())


def checksum(value: str) -> str:
    return hashlib.sha256(normalized(value).encode()).hexdigest()


def candidate_targets(text: str) -> list[Target]:
    """Return semantic targets implied directly by an atomic Swedish Fact."""
    low = normalized(text)
    targets: list[Target] = []

    def add(kind: str, proposition: str) -> None:
        if kind not in {item.target_type for item in targets}:
            targets.append(Target(kind, proposition, text))

    if re.search(r"\b(18|19|20)\d{2}\b|\b\d{1,2} [a-zåäö]+\b|vart fjärde år|sedan \d{4}|mellan \d{4}", low):
        add("CHRONOLOGY", f"Identifiera den uttryckligen angivna tiden eller tidsperioden: {text}")
    if re.search(r"\b(ansvarar|finansierar|betalar|reglerar|arbetar för|representerar|bestäms|fastställer)\b", low):
        add("RESPONSIBILITY", f"Identifiera den uttryckligen angivna aktörens ansvar eller uppgift: {text}")
    if re.search(r"\b(betyder|handlar om|är en|består av)\b", low):
        add("DEFINITION", f"Identifiera den uttryckliga definitionen eller klassificeringen: {text}")
    if re.search(r"\b(orsak|för att|medför|gjorde det möjligt|gav fler|leder till)\b", low):
        kind = "CAUSE" if re.search(r"\b(orsak|för att)\b", low) else "CONSEQUENCE"
        add(kind, f"Identifiera det uttryckligen angivna {('orsakssambandet' if kind == 'CAUSE' else 'resultatet')}: {text}")
    if re.search(r"[,;].*\boch\b|\bbland annat\b|\bsom till exempel\b|\bde tre\b|\bfyra grundlag", low):
        add("LIST_MEMBERSHIP", f"Avgör vad som uttryckligen ingår i den angivna uppräkningen: {text}")
    if re.search(r"\b(i|på|till) (sverige|norrland|södra sverige|norrbottens län|ådalen|saltsjöbaden|usa|eu-parlamentet|staten|regionen|kommunen)\b", low):
        add("LOCATION", f"Identifiera den uttryckligen angivna platsen eller nivån: {text}")
    if re.search(r"\b(inte|mer än|största|näst största|de största|stor del|cirka|ungefär)\b", low):
        add("COMPARISON", f"Identifiera den uttryckliga jämförelsen, omfattningen eller avgränsningen: {text}")
    return targets


def audit_fact(row: dict) -> dict:
    existing = row.get("questions") or []
    candidates = candidate_targets(row["fact_text"])
    # Existing content is conservatively treated as direct recognition. New plans
    # may use only distinct structural targets detected in the immutable Fact.
    safe_total = min(4, len(existing) + len(candidates))
    if not existing and not candidates:
        safe_total = 0
    classification = CLASS_BY_TOTAL[safe_total]
    planned = candidates[: max(0, safe_total - len(existing))]
    evidence = normalized_pdf_evidence(row.get("exact_evidence") or row["fact_text"])
    result = {
        **{key: row[key] for key in (
            "fact_id", "fact_version_id", "fact_version", "fact_text", "chapter",
            "topic_id", "topic_name", "objective_id", "objective", "source_section_id",
            "source_section", "section_checksum")},
        "fact_checksum": hashlib.sha256(row["fact_text"].encode()).hexdigest(),
        "existing_question_count": len(existing),
        "existing_question_ids": [item["id"] for item in existing],
        "existing_questions": existing,
        "existing_tested_targets": ["DIRECT_RECOGNITION"] if existing else [],
        "semantic_overlap": "NONE" if len(existing) < 2 else "REVIEWED_DISTINCT",
        "safe_total_question_count": safe_total,
        "density_classification": classification,
        "reason": (
            "No additional learner outcome is explicit in the bounded Fact."
            if not planned else
            f"The bounded Fact explicitly supports {len(planned)} additional learner outcome(s): "
            + ", ".join(item.target_type for item in planned)
        ),
        "targets": [],
    }
    forbidden = result["existing_tested_targets"] + [item.target_type for item in planned]
    for order, item in enumerate(planned, 1):
        target_checksum = checksum(f"{row['fact_version_id']}|{item.target_type}|{item.tested_proposition}")
        result["targets"].append({
            "id": None,
            "target_order": order,
            "target_type": item.target_type,
            "tested_proposition": item.tested_proposition,
            "allowed_question_form": "SINGLE_CHOICE",
            "exact_evidence": evidence,
            "correct_answer_boundary": item.correct_answer_boundary,
            "forbidden_duplicate_target_labels": forbidden,
            "forbidden_interpretations": ["outside knowledge", "unstated inference", "trivial inversion"],
            "distractor_constraints": ["plausible but explicitly false within the evidence", "same semantic category as the answer", "no partial truths"],
            "explanation_scope": "Explain only the planned proposition using the exact evidence.",
            "target_checksum": target_checksum,
        })
    return result


def build_plan(rows: list[dict]) -> dict:
    audits = [audit_fact(row) for row in rows]
    canonical = json.dumps(audits, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    definition_checksum = hashlib.sha256(canonical.encode()).hexdigest()
    plan_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"sverige-i-fokus-question-expansion:{definition_checksum}"))
    for fact in audits:
        for target in fact["targets"]:
            target["id"] = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{plan_id}:target:{target['target_checksum']}"))
    return {
        "id": plan_id,
        "corpus_id": "sverige-i-fokus-v1",
        "source_revision_id": "sverige-i-fokus-source-v2",
        "starting_question_count": sum(len(row.get("questions") or []) for row in rows),
        "target_minimum": 200,
        "target_maximum": 250,
        "definition_checksum": definition_checksum,
        "facts": audits,
    }


def sql_literal(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, sort_keys=True)
    return "'" + str(value).replace("'", "''") + "'"


def persistence_sql(plan: dict) -> str:
    plan_id = plan["id"]
    lines = ["BEGIN;", (
        "INSERT INTO ai_question_bank_expansion_plan(id,corpus_id,source_revision_id,starting_question_count,"
        "target_minimum,target_maximum,status,definition_checksum,created_by,created_at) VALUES("+
        ",".join((sql_literal(plan_id),sql_literal(plan["corpus_id"]),sql_literal(plan["source_revision_id"]),
                  str(plan["starting_question_count"]),str(plan["target_minimum"]),str(plan["target_maximum"]),
                  sql_literal("PLANNED"),sql_literal(plan["definition_checksum"]),sql_literal("codex-question-bank-expansion"),"now()"))+
        ") ON CONFLICT(corpus_id,source_revision_id,definition_checksum) DO NOTHING;"
    )]
    for fact in plan["facts"]:
        audit_id = str(uuid.uuid5(uuid.NAMESPACE_URL, f"{plan_id}:fact:{fact['fact_id']}"))
        values = (audit_id,plan_id,fact["fact_id"],fact["fact_version_id"],fact["fact_version"],fact["fact_checksum"],
                  fact["fact_text"],fact["chapter"],fact["topic_id"],fact["topic_name"],fact["objective_id"],
                  fact["objective"],fact["source_section_id"],fact["section_checksum"],fact["existing_question_ids"],
                  fact["existing_questions"],fact["existing_tested_targets"],fact["semantic_overlap"],
                  fact["density_classification"],fact["safe_total_question_count"],fact["reason"])
        encoded = [str(value) if isinstance(value,int) else sql_literal(value) for value in values]
        lines.append("INSERT INTO ai_question_fact_density_audit(id,expansion_plan_id,knowledge_fact_id,knowledge_fact_version_id,knowledge_fact_version,fact_checksum,fact_snapshot,chapter_label,topic_id,topic_label,objective_id,objective_label,source_section_id,source_section_checksum,existing_question_ids,existing_question_snapshots,existing_target_labels,semantic_overlap,density_classification,safe_total_question_count,reason,audited_at) VALUES("+",".join(encoded+["now()"]) + ") ON CONFLICT(expansion_plan_id,knowledge_fact_id) DO NOTHING;")
        for target in fact["targets"]:
            target_values = (target["id"],audit_id,target["target_order"],target["target_type"],target["tested_proposition"],
                             target["allowed_question_form"],target["exact_evidence"],target["correct_answer_boundary"],
                             target["forbidden_duplicate_target_labels"],target["forbidden_interpretations"],
                             target["distractor_constraints"],target["explanation_scope"],target["target_checksum"])
            target_encoded = [str(value) if isinstance(value,int) else sql_literal(value) for value in target_values]
            lines.append("INSERT INTO ai_question_target_plan(id,density_audit_id,target_order,target_type,tested_proposition,allowed_question_form,exact_evidence,correct_answer_boundary,forbidden_duplicate_target_labels,forbidden_interpretations,distractor_constraints,explanation_scope,target_checksum,status,created_at,updated_at) VALUES("+",".join(target_encoded+[sql_literal("PLANNED"),"now()","now()"]) + ") ON CONFLICT(density_audit_id,target_checksum) DO NOTHING;")
    lines.extend(("COMMIT;", ""))
    return "\n".join(lines)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--sql-output", type=Path)
    args = parser.parse_args()
    plan = build_plan(json.loads(args.input.read_text()))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(plan, ensure_ascii=False, indent=2) + "\n")
    if args.sql_output:
        args.sql_output.parent.mkdir(parents=True, exist_ok=True)
        args.sql_output.write_text(persistence_sql(plan))
    counts: dict[str, int] = {}
    for fact in plan["facts"]:
        key = fact["density_classification"]
        counts[key] = counts.get(key, 0) + 1
    print(json.dumps({"facts": len(plan["facts"]), "existing": plan["starting_question_count"],
                      "targets": sum(len(f["targets"]) for f in plan["facts"]),
                      "projectedTotal": plan["starting_question_count"] + sum(len(f["targets"]) for f in plan["facts"]),
                      "classifications": counts, "checksum": plan["definition_checksum"]}, sort_keys=True))


if __name__ == "__main__":
    main()
