#!/usr/bin/env python3
"""Deterministically audit and plan source-bounded lesson depth expansion.

The provider never chooses immutable lesson structure. This module turns an
authoritative content snapshot into an auditable plan before any provider call.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import uuid
from collections import Counter
from pathlib import Path

READING_WORDS_PER_MINUTE = 140
MIN_SUBSTANTIVE_WORDS = 70
MAX_PAGES = 6


def normalize(value: str) -> str:
    return re.sub(r"\s+", " ", (value or "").strip()).casefold()


def words(value: str) -> list[str]:
    return re.findall(r"[0-9A-Za-zÅÄÖåäö]+", value or "")


def reading_seconds(value: str) -> int:
    count = len(words(value))
    return 0 if count == 0 else max(1, round(count * 60 / READING_WORDS_PER_MINUTE))


def checksum(value: object) -> str:
    encoded = json.dumps(value, ensure_ascii=False, sort_keys=True, separators=(",", ":"))
    return hashlib.sha256(encoded.encode()).hexdigest()


def sentence_units(value: str) -> list[str]:
    result: list[str] = []
    for sentence in re.split(r"(?<=[.!?])\s+|\n+", value or ""):
        sentence = re.sub(r"\s+", " ", sentence).strip()
        if len(words(sentence)) >= 8 and normalize(sentence) not in {normalize(v) for v in result}:
            result.append(sentence)
    return result


def overlap(left: str, right: str) -> float:
    a = {token for token in words(normalize(left)) if len(token) > 3}
    b = {token for token in words(normalize(right)) if len(token) > 3}
    return 0.0 if not a or not b else len(a & b) / len(a | b)


def page_type(evidence: str) -> str:
    low = normalize(evidence)
    if re.search(r"\b(ansvar|ansvarar|myndighet|kommun|region|riksdag|regering)\b", low):
        return "ACTOR_OR_INSTITUTION"
    if re.search(r"\b(först|sedan|därefter|process|mellan \d{4}|\d{4})\b", low):
        return "PROCESS_OR_SEQUENCE"
    if re.search(r"\b(skillnad|medan|jämfört|däremot)\b", low):
        return "COMPARISON"
    if re.search(r"\b(betyder|innebär|är en|kallas)\b", low):
        return "KEY_TERMS"
    return "CORE_CONCEPT"


def learner_question(kind: str, topic: str, evidence: str) -> str:
    if kind == "ACTOR_OR_INSTITUTION":
        return f"Vem ansvarar för eller påverkar {topic.lower()}?"
    if kind == "PROCESS_OR_SEQUENCE":
        return f"Hur hänger utvecklingen eller processen inom {topic.lower()} ihop?"
    if kind == "COMPARISON":
        return f"Vilken uttrycklig skillnad behöver du förstå inom {topic.lower()}?"
    if kind == "KEY_TERMS":
        return f"Vad betyder de centrala begreppen inom {topic.lower()}?"
    subject = " ".join(words(evidence)[:7]).rstrip(".,:;")
    return f"Vad behöver du förstå om {subject.lower()}?"


def repeated_claims(pages: list[dict]) -> list[dict]:
    repeated = []
    for left in range(len(pages)):
        for right in range(left + 1, len(pages)):
            score = overlap(pages[left]["explanation"], pages[right]["explanation"])
            if score >= 0.72:
                repeated.append({"pageIds": [pages[left]["id"], pages[right]["id"]], "overlap": round(score, 3)})
    return repeated


def audit_topic(row: dict) -> dict:
    pages = sorted(row["pages"], key=lambda value: value["displayOrder"])
    page_count = len(pages)
    source_units = sentence_units("\n".join(section["exactText"] for section in row["sourceSections"]))
    existing_text = " ".join(page["explanation"] for page in pages)
    missing = [unit for unit in source_units if overlap(unit, existing_text) < 0.48]
    fact_count = len(row["facts"])
    source_words = len(words(" ".join(section["normalizedText"] for section in row["sourceSections"])))
    support_units = max(fact_count, min(len(source_units), 8))
    support_ceiling = min(MAX_PAGES, max(page_count, 3, 3 + min(3, (support_units - 1) // 2)))
    if source_words < 120:
        support_ceiling = min(support_ceiling, 3)
    elif source_words < 220:
        support_ceiling = min(support_ceiling, 4)
    elif source_words < 380:
        support_ceiling = min(support_ceiling, 5)
    support_ceiling = max(page_count, support_ceiling)

    repeats = repeated_claims(pages)
    current_words = sum(len(words(page["explanation"])) for page in pages)
    if repeats:
        classification = "RESTRUCTURE_REQUIRED"
    elif page_count >= min(4, support_ceiling) and current_words >= 220:
        classification = "DEPTH_SUFFICIENT"
    elif support_ceiling < 4:
        classification = "LIMITED_BUT_USABLE"
    else:
        classification = f"EXPANDABLE_TO_{['ZERO','ONE','TWO','THREE','FOUR','FIVE','SIX'][support_ceiling].upper()}_PAGES"

    target = page_count if classification == "DEPTH_SUFFICIENT" else max(page_count, support_ceiling)
    target = min(MAX_PAGES, target)
    duplicate_page_ids = {pair["pageIds"][1] for pair in repeats}
    page_actions = []
    for page in pages:
        action = "SUPERSEDE_WITH_DEEPER_PAGE" if page["id"] in duplicate_page_ids else "REUSE_UNCHANGED"
        page_actions.append({"pageId": page["id"], "action": action, "targetOrder": page["displayOrder"]})

    candidates = sorted(missing, key=lambda item: (-len(words(item)), normalize(item)))
    plans = []
    used_types: Counter[str] = Counter()
    retained_count = page_count - len(duplicate_page_ids)
    required_generated = max(0, target - retained_count)
    for order, evidence in enumerate(candidates[:required_generated], start=retained_count):
        kind = page_type(evidence)
        used_types[kind] += 1
        section = max(row["sourceSections"], key=lambda value: overlap(value["exactText"], evidence))
        section_facts = [fact for fact in row["facts"] if fact.get("sourceSectionId") == section["id"]]
        if not section_facts and all(fact.get("sourceSectionId") is None for fact in row["facts"]):
            section_facts = row["facts"]
        assigned = sorted(
            (fact for fact in section_facts if overlap(fact["statement"], evidence) >= 0.25),
            key=lambda fact: fact["versionId"],
        )
        if not assigned:
            assigned = sorted(section_facts, key=lambda fact: (-overlap(fact["statement"], evidence), fact["versionId"]))[:1]
        if not assigned:
            continue
        question = learner_question(kind, row["topicTitle"], evidence)
        immutable = {
            "pageType": kind,
            "title": question.rstrip("?"),
            "pageOrder": order,
            "learnerQuestion": question,
            "pagePurpose": f"Besvara learner-frågan med enbart den tilldelade evidensen för {row['topicTitle']}.",
            "knowledgeFactVersionIds": [fact["versionId"] for fact in assigned],
            "assignedFacts": assigned,
            "sourceSections": [{"id": section["id"], "checksum": section["checksum"]}],
            "exactSupportingEvidence": [evidence],
            "allowedConcepts": sorted({token for token in words(evidence) if len(token) >= 7}, key=str.casefold)[:12],
            "forbiddenConcepts": ["outside knowledge", "unstated causality", "invented example", "generic importance claim"],
            "relationshipToExistingPages": "Adds a source-supported learner question not materially taught by the retained pages.",
            "estimatedWordRange": {"minimum": MIN_SUBSTANTIVE_WORDS, "maximum": 160},
            "estimatedReadingSecondsRange": {"minimum": 30, "maximum": 69},
        }
        immutable["planChecksum"] = checksum(immutable)
        plans.append(immutable)

    # Never pretend a page target is supportable when distinct evidence ran out.
    safe_target = retained_count + len(plans)
    if safe_target < 4 and classification.startswith("EXPANDABLE"):
        classification = "LIMITED_BUT_USABLE"
    result = {
        "chapter": row["chapter"],
        "topicId": row["topicId"],
        "topicTitle": row["topicTitle"],
        "objectiveId": row["objectiveId"],
        "objectiveText": row["objectiveText"],
        "currentLessonId": row["lessonId"],
        "currentLessonVersion": row["lessonVersion"],
        "currentPageCount": page_count,
        "currentPageIds": [page["id"] for page in pages],
        "currentPages": pages,
        "currentTitles": [page["title"] for page in pages],
        "currentPagePurposes": [page.get("purpose") or page["title"] for page in pages],
        "assignedFactVersionIds": sorted({fid for page in pages for fid in page["factVersionIds"]}),
        "assignedSourceSectionIds": sorted({page["sourceSectionId"] for page in pages}),
        "sourceSections": row["sourceSections"],
        "sourceRevision": row["sourceRevision"],
        "normalizedSourceLength": source_words,
        "currentWordCount": current_words,
        "estimatedReadingSeconds": reading_seconds(existing_text),
        "factCoverage": {"assigned": len({fid for page in pages for fid in page["factVersionIds"]}), "approved": fact_count},
        "repeatedClaims": repeats,
        "missingTeachableConcepts": missing,
        "maximumSafelySupportablePageCount": support_ceiling,
        "classification": classification,
        "pageActions": page_actions,
        "candidatePagePlans": plans,
        "plannedPageCount": safe_target,
    }
    result["auditChecksum"] = checksum(result)
    return result


def build_audit(snapshot: list[dict]) -> dict:
    topics = [audit_topic(row) for row in snapshot]
    body = {
        "corpusId": "sverige-i-fokus-v1",
        "sourceRevision": "sverige-i-fokus-source-v2",
        "readingWordsPerMinute": READING_WORDS_PER_MINUTE,
        "topics": topics,
    }
    body["auditId"] = str(uuid.uuid5(uuid.NAMESPACE_URL, "lesson-depth:" + checksum(body)))
    body["definitionChecksum"] = checksum(body)
    return body


def sql_literal(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, (dict, list)):
        value = json.dumps(value, ensure_ascii=False, sort_keys=True)
    return "'" + str(value).replace("'", "''") + "'"


def persistence_sql(audit: dict) -> str:
    audit_id = audit["auditId"]
    lines = ["BEGIN;", (
        "INSERT INTO ai_lesson_depth_audit(id,corpus_id,source_revision_id,definition_checksum,"
        "reading_words_per_minute,status,created_by,created_at) VALUES(" + ",".join([
            sql_literal(audit_id), sql_literal(audit["corpusId"]), sql_literal(audit["sourceRevision"]),
            sql_literal(audit["definitionChecksum"]), str(audit["readingWordsPerMinute"]),
            sql_literal("AUDITED"), sql_literal("codex-lesson-depth-expansion"), "now()",
        ]) + ") ON CONFLICT(corpus_id,source_revision_id,definition_checksum) DO NOTHING;"
    )]
    for topic in audit["topics"]:
        topic_plan_id = str(uuid.uuid5(uuid.UUID(audit_id), "topic:" + topic["topicId"]))
        values = [topic_plan_id, audit_id, topic["topicId"], topic["objectiveId"], topic["currentLessonId"],
                  topic["currentLessonVersion"], topic["classification"], topic["currentPageCount"],
                  topic["plannedPageCount"], topic["currentWordCount"], topic["normalizedSourceLength"],
                  topic["estimatedReadingSeconds"], topic["maximumSafelySupportablePageCount"],
                  topic, topic["auditChecksum"]]
        encoded = [str(value) if isinstance(value, int) else sql_literal(value) for value in values]
        lines.append("INSERT INTO ai_lesson_depth_topic_plan(id,depth_audit_id,topic_id,learning_objective_id,current_lesson_id,current_lesson_version,classification,current_page_count,target_page_count,current_word_count,normalized_source_length,estimated_reading_seconds,maximum_supportable_page_count,audit_snapshot,audit_checksum,status,created_at,updated_at) VALUES(" + ",".join(encoded + [sql_literal("PLANNED"), "now()", "now()"]) + ") ON CONFLICT(depth_audit_id,topic_id) DO NOTHING;")
        retained_order = 0
        action_by_id = {item["pageId"]: item["action"] for item in topic["pageActions"]}
        for page in topic["currentPages"]:
            if action_by_id[page["id"]] != "REUSE_UNCHANGED":
                continue
            immutable = {"pageType": "RETAINED", "title": page["title"], "pageOrder": retained_order,
                         "retainedPageId": page["id"], "sourceSectionId": page["sourceSectionId"],
                         "sourceSectionChecksum": page["sectionChecksum"],
                         "knowledgeFactVersionIds": page["factVersionIds"]}
            immutable["planChecksum"] = checksum(immutable)
            page_plan_id = str(uuid.uuid5(uuid.UUID(topic_plan_id), "page:" + immutable["planChecksum"]))
            lines.append("INSERT INTO ai_lesson_depth_page_plan(id,topic_plan_id,plan_order,action,retained_page_id,immutable_plan,plan_checksum,status,created_at) VALUES(" + ",".join([
                sql_literal(page_plan_id), sql_literal(topic_plan_id), str(retained_order), sql_literal("REUSE_UNCHANGED"),
                sql_literal(page["id"]), sql_literal(immutable), sql_literal(immutable["planChecksum"]),
                sql_literal("VALIDATED"), "now()",
            ]) + ") ON CONFLICT(topic_plan_id,plan_checksum) DO NOTHING;")
            retained_order += 1
        replaced_ids = [item["pageId"] for item in topic["pageActions"] if item["action"] != "REUSE_UNCHANGED"]
        for index, immutable in enumerate(topic["candidatePagePlans"]):
            immutable = dict(immutable)
            immutable["pageOrder"] = retained_order + index
            immutable["planChecksum"] = checksum({k: v for k, v in immutable.items() if k != "planChecksum"})
            page_plan_id = str(uuid.uuid5(uuid.UUID(topic_plan_id), "page:" + immutable["planChecksum"]))
            predecessor = replaced_ids[index] if index < len(replaced_ids) else None
            action = "REPLACE" if predecessor else "ADD"
            lines.append("INSERT INTO ai_lesson_depth_page_plan(id,topic_plan_id,plan_order,action,supersedes_page_id,immutable_plan,plan_checksum,status,created_at) VALUES(" + ",".join([
                sql_literal(page_plan_id), sql_literal(topic_plan_id), str(retained_order + index), sql_literal(action),
                sql_literal(predecessor), sql_literal(immutable), sql_literal(immutable["planChecksum"]),
                sql_literal("PLANNED"), "now()",
            ]) + ") ON CONFLICT(topic_plan_id,plan_checksum) DO NOTHING;")
    lines.extend(["COMMIT;", ""])
    return "\n".join(lines)


def persist_audit(audit: dict, container: str) -> None:
    subprocess.run(["docker", "exec", "-i", container, "psql", "-v", "ON_ERROR_STOP=1",
                    "-U", "ai", "-d", "ai"], input=persistence_sql(audit), check=True, text=True)


def load_snapshot_from_container(container: str) -> list[dict]:
    sql = Path(__file__).with_name("lesson_depth_snapshot.sql").read_text()
    result = subprocess.run(
        ["docker", "exec", container, "psql", "-U", "content", "-d", "content", "-Atc", sql],
        check=True, capture_output=True, text=True,
    )
    return json.loads(result.stdout)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path)
    parser.add_argument("--database-container", default="exam-platform-content-database-1")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--persist", action="store_true")
    args = parser.parse_args()
    snapshot = json.loads(args.input.read_text()) if args.input else load_snapshot_from_container(args.database_container)
    audit = build_audit(snapshot)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(audit, ensure_ascii=False, indent=2) + "\n")
    if args.persist:
        persist_audit(audit, "exam-platform-ai-database-1")
    counts = Counter(topic["classification"] for topic in audit["topics"])
    print(json.dumps({"topics": len(audit["topics"]), "classifications": counts,
                      "currentPages": sum(t["currentPageCount"] for t in audit["topics"]),
                      "plannedPages": sum(t["plannedPageCount"] for t in audit["topics"]),
                      "checksum": audit["definitionChecksum"]}, sort_keys=True))


if __name__ == "__main__":
    main()
