#!/usr/bin/env python3
"""Emit idempotent structural-import SQL for the Sverige i fokus corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path


def sql(value: object) -> str:
    if value is None:
        return "NULL"
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    if isinstance(value, int):
        return str(value)
    return "'" + str(value).replace("'", "''") + "'"


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--manifest", type=Path, default=Path("content/sverige-i-fokus/curriculum-manifest.yaml"))
    parser.add_argument("--sections", type=Path, default=Path("content/sverige-i-fokus/source-sections.json"))
    parser.add_argument("--pdf", type=Path, default=Path("docs/sverige-i-fokus.pdf"))
    args = parser.parse_args()
    manifest = json.loads(args.manifest.read_text())
    sections = json.loads(args.sections.read_text())
    source = manifest["source"]
    pdf_checksum = hashlib.sha256(args.pdf.read_bytes()).hexdigest()
    if pdf_checksum != source["sha256"]:
        raise SystemExit("PDF and manifest checksums differ")
    extracted = (
        subprocess.run(["pdftotext", "-layout", str(args.pdf), "-"], check=True, capture_output=True, text=True).stdout
        if shutil.which("pdftotext")
        else "\n\n".join(section["exactText"] for section in sections)
    )
    content_checksum = hashlib.sha256(extracted.encode()).hexdigest()
    lines = [
        "BEGIN;",
        "SELECT set_config('app.actor_id','sverige-i-fokus-import',true);",
        "SELECT set_config('app.actor_name','Sverige i fokus deterministic importer',true);",
        "SELECT set_config('app.actor_roles','SYSTEM',true);",
        (
            "INSERT INTO source_reference(id,publisher,title,source_type,document_version,publication_date,accessed_at,"
            "copyright_notes,internal_notes,review_status,status,created_at,updated_at,content_text,content_checksum,"
            "original_filename,file_checksum,language_code,official_study_material,attribution,licensing_review_status,imported_at)"
            f" VALUES({sql(source['id'])},{sql(source['publisher'])},{sql(source['title'])},'GOVERNMENT_DOCUMENT',"
            f"{sql(source['edition'] + ', 2026')},NULL,CURRENT_DATE,"
            "'Rights and public-reuse scope require editorial/legal review before public reproduction.',"
            "'Authoritative internal corpus source. Independent practice material; no endorsement.',"
            f"'UNREVIEWED','ACTIVE',now(),now(),{sql(extracted)},{sql(content_checksum)},{sql(source['originalFilename'])},"
            f"{sql(source['sha256'])},{sql(source['language'])},TRUE,{sql(source['attribution'])},'PENDING',now())"
            " ON CONFLICT DO NOTHING;"
        ),
    ]
    for section in sections:
        section_code = f"SEC-{section['order']:03d}"
        lines.append(
            "INSERT INTO source_section(id,source_reference_id,code,chapter_title,subsection_title,structural_path,"
            "page_start,page_end,display_order,exact_text,normalized_text,section_checksum,created_at,updated_at)"
            f" VALUES({sql(section['id'])},{sql(source['id'])},{sql(section_code)},"
            f"{sql(section['chapter'])},{sql(section['subsection'])},{sql(section['structuralPath'])},"
            f"{section['startPage']},{section['endPage']},{section['order']},{sql(section['exactText'])},"
            f"{sql(section['normalizedText'])},{sql(section['checksum'])},now(),now())"
            " ON CONFLICT(source_reference_id,code) DO NOTHING;"
        )
    exam = manifest["exam"]
    lines.extend(
        [
            (
                "INSERT INTO exam(id,code,name,country_code,status,created_at,updated_at)"
                f" VALUES({sql(exam['id'])},{sql(exam['code'])},{sql(exam['name'])},'SE','DRAFT',now(),now())"
                " ON CONFLICT(code) DO NOTHING;"
            ),
            (
                "INSERT INTO exam_version(id,exam_id,version_code,display_name,status,created_at,updated_at)"
                f" VALUES({sql(exam['versionId'])},{sql(exam['id'])},{sql(exam['versionCode'])},"
                "'Sverige i fokus v1 – intern granskningsversion','DRAFT',now(),now())"
                " ON CONFLICT(exam_id,version_code) DO NOTHING;"
            ),
        ]
    )
    for subject in manifest["subjects"]:
        lines.append(
            "INSERT INTO subject(id,exam_version_id,code,name,description,sort_order,status,created_at,updated_at)"
            f" VALUES({sql(subject['id'])},{sql(exam['versionId'])},{sql(subject['code'])},{sql(subject['name'])},"
            "'Chapter-derived subject from Sverige i fokus.',"
            f"{subject['displayOrder']},'DRAFT',now(),now()) ON CONFLICT(exam_version_id,code) DO NOTHING;"
        )
        for topic in subject["topics"]:
            lines.append(
                "INSERT INTO topic(id,subject_id,code,name,description,sort_order,status,created_at,updated_at)"
                f" VALUES({sql(topic['id'])},{sql(subject['id'])},{sql(topic['code'])},{sql(topic['name'])},"
                "'Subsection-derived topic from Sverige i fokus.',"
                f"{topic['displayOrder']},'DRAFT',now(),now()) ON CONFLICT(subject_id,code) DO NOTHING;"
            )
            for objective in topic["learningObjectives"]:
                lines.append(
                    "INSERT INTO learning_objective(id,topic_id,code,title,description,status,created_at,updated_at)"
                    f" VALUES({sql(objective['id'])},{sql(topic['id'])},{sql(objective['code'])},{sql(objective['title'])},"
                    "'Grounded exclusively in the mapped source section.','DRAFT',now(),now())"
                    " ON CONFLICT(topic_id,code) DO NOTHING;"
                )
                for section_id in objective["sourceSectionIds"]:
                    lines.append(
                        "INSERT INTO learning_objective_source_section(learning_objective_id,source_section_id)"
                        f" VALUES({sql(objective['id'])},{sql(section_id)}) ON CONFLICT DO NOTHING;"
                    )
    lines.extend(
        [
            "DO $$ BEGIN IF (SELECT count(*) FROM source_section WHERE source_reference_id="
            + sql(source["id"])
            + ") <> 38 THEN RAISE EXCEPTION 'Expected 38 imported source sections'; END IF; END $$;",
            "COMMIT;",
        ]
    )
    print("\n".join(lines))


if __name__ == "__main__":
    main()
