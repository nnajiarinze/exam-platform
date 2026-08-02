#!/usr/bin/env python3
"""Atomically assemble all validated Lesson Regeneration v2 pages."""

from __future__ import annotations

import argparse
import hashlib
import json
import subprocess
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from assemble_lesson_depth_lessons import current_lessons, execute, generated_pages, sql_literal

ACTOR = "codex-lesson-regeneration-v2"
REASON = "LESSON_DEPTH_EXPANSION"


def checksum(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def assemble(audit: dict, state: dict, existing: dict[str, dict]) -> tuple[str, dict]:
    lines = ["BEGIN;", "SELECT pg_advisory_xact_lock(hashtext('sverige-i-fokus-lesson-regeneration-v2'));" ]
    report = {"auditId": audit["auditId"], "definitionChecksum": audit["definitionChecksum"],
              "lessons": [], "lessonCount": 0, "pageCount": 0, "wordCount": 0,
              "estimatedReadingMinutes": 0, "readingSpeedWordsPerMinute": 180, "factAssignments": 0}
    global_facts: list[str] = []
    for topic in audit["topics"]:
        old = existing[topic["topicId"]]
        pages = generated_pages(topic, state["topics"].get(topic["topicId"], {}))
        plans = topic["candidatePagePlans"]
        if len(pages) != len(plans) or [p["plan"]["planChecksum"] for p in pages] != [p["planChecksum"] for p in plans]:
            raise RuntimeError(f"{topic['topicTitle']}: not every immutable page plan has one validated revision")
        facts = [fact for item in pages for fact in item["page"]["knowledgeFactVersionIds"]]
        expected = topic["assignedFactVersionIds"]
        if len(facts) != len(set(facts)) or set(facts) != set(expected):
            raise RuntimeError(f"{topic['topicTitle']}: Facts must be assigned exactly once")
        bodies = [" ".join(item["page"]["body"].casefold().split()) for item in pages]
        if len(bodies) != len(set(bodies)):
            raise RuntimeError(f"{topic['topicTitle']}: duplicate learner pages")
        word_counts = [len(item["page"]["body"].split()) for item in pages]
        if any(words < 70 or words > 160 for words in word_counts):
            raise RuntimeError(f"{topic['topicTitle']}: page outside 70-160 words")

        lesson_id = str(uuid.uuid5(uuid.UUID(audit["auditId"]), "lesson-v2:" + topic["topicId"]))
        plan_id = str(uuid.uuid5(uuid.UUID(audit["auditId"]), "topic-v2:" + topic["topicId"]))
        version = old["version"] + 1
        title = old["title"]
        introduction = f"I den här lektionen arbetar du med {topic['topicTitle'].lower()} utifrån källmaterialet Sverige i fokus."
        summary = f"Du har nu gått igenom {topic['topicTitle']}."
        important = []
        for plan in plans:
            for fact in plan["assignedFacts"]:
                if fact["statement"] not in important: important.append(fact["statement"])
        important = important[:3]
        source_checksum = checksum("|".join(section["checksum"] for section in topic["sourceSections"]))
        lines.append("INSERT INTO lesson_draft(id,topic_id,version_number,title,introduction,summary,important_points,review_status,source_checksum,created_by,reviewed_by,review_note,created_at,updated_at,reviewed_at,version,supersedes_lesson_draft_id,generation_plan_id,revision_reason,human_verified) VALUES(" + ",".join([
            sql_literal(lesson_id), sql_literal(topic["topicId"]), str(version), sql_literal(title),
            sql_literal(introduction), sql_literal(summary), sql_literal(important), sql_literal("REVIEWED"),
            sql_literal(source_checksum), sql_literal(ACTOR), sql_literal(ACTOR),
            sql_literal("Lesson Regeneration v2 automatic claim, template, coverage, and coherence gates passed; human_verified=false."),
            "now()", "now()", "now()", "0", sql_literal(old["id"]), sql_literal(plan_id), sql_literal(REASON), "false"
        ]) + ") ON CONFLICT(id) DO NOTHING;")
        old_pages = sorted(old["pages"], key=lambda page: page["displayOrder"])
        for order, item in enumerate(pages):
            page, plan, revision = item["page"], item["plan"], item["revision"]
            section_id = str(uuid.uuid5(uuid.UUID(lesson_id), f"page:{order}:{plan['planChecksum']}"))
            predecessor = old_pages[order]["id"] if order < len(old_pages) else None
            logical = old_pages[order]["logicalSectionId"] if predecessor else section_id
            section_checksum = checksum(page["title"].strip() + "\n" + page["body"].strip())
            lines.append("INSERT INTO lesson_draft_section(id,lesson_draft_id,source_section_id,title,explanation,key_terms,supported_examples,display_order,section_checksum,logical_section_id) VALUES(" + ",".join([
                sql_literal(section_id), sql_literal(lesson_id), sql_literal(plan["sourceSections"][0]["id"]),
                sql_literal(page["title"]), sql_literal(page["body"]), sql_literal(page.get("keyTerms", [])),
                sql_literal([]), str(order), sql_literal(section_checksum), sql_literal(logical)
            ]) + ") ON CONFLICT(id) DO NOTHING;")
            for fact in page["knowledgeFactVersionIds"]:
                lines.append("INSERT INTO lesson_draft_section_fact(lesson_draft_section_id,knowledge_fact_version_id) VALUES(" + sql_literal(section_id) + "," + sql_literal(fact) + ") ON CONFLICT DO NOTHING;")
            mapping_id = str(uuid.uuid5(uuid.UUID(lesson_id), f"mapping:{order}:{predecessor}"))
            snapshot = {"auditId": audit["auditId"], "definitionChecksum": audit["definitionChecksum"],
                        "planChecksum": plan["planChecksum"], "pageRevisionId": revision["id"]}
            lines.append("INSERT INTO lesson_draft_supersession_mapping(id,predecessor_lesson_id,successor_lesson_id,predecessor_section_id,successor_section_id,mapping_type,audit_snapshot,created_by,created_at) VALUES(" + ",".join([
                sql_literal(mapping_id), sql_literal(old["id"]), sql_literal(lesson_id), sql_literal(predecessor),
                sql_literal(section_id), sql_literal("SUPERSEDED" if predecessor else "ADDED"),
                sql_literal(snapshot), sql_literal(ACTOR), "now()"
            ]) + ") ON CONFLICT DO NOTHING;")
        words = sum(word_counts)
        old_words = sum(len(page["explanation"].split()) for page in old_pages)
        report["lessons"].append({"topicId": topic["topicId"], "topicTitle": topic["topicTitle"],
                                  "lessonId": lesson_id, "oldVersion": old["version"], "newVersion": version,
                                  "oldPages": len(old_pages), "newPages": len(pages),
                                  "oldWords": old_words, "newWords": words,
                                  "targetPages": topic["plannedPageCount"], "boundedSourceException": topic["boundedSourceException"]})
        report["lessonCount"] += 1; report["pageCount"] += len(pages); report["wordCount"] += words
        report["factAssignments"] += len(facts); global_facts.extend(facts)
    if len(global_facts) != 209 or len(set(global_facts)) != 209:
        raise RuntimeError("Corpus-wide immutable Fact coverage must be exactly 209/209 with no repetition")
    report["estimatedReadingMinutes"] = round(report["wordCount"] / report["readingSpeedWordsPerMinute"])
    lines.extend(["COMMIT;", ""])
    return "\n".join(lines), report


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--audit", type=Path, required=True); parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--report", type=Path, required=True); parser.add_argument("--apply", action="store_true")
    args = parser.parse_args(); audit = json.loads(args.audit.read_text()); state = json.loads(args.state.read_text())
    sql, report = assemble(audit, state, current_lessons())
    if args.apply: execute("exam-platform-content-database-1", "content", "content", sql)
    args.report.parent.mkdir(parents=True, exist_ok=True); args.report.write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({key: report[key] for key in ("lessonCount", "pageCount", "wordCount", "estimatedReadingMinutes", "factAssignments")}, sort_keys=True))


if __name__ == "__main__": main()
