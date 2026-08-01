#!/usr/bin/env python3
"""Build the deterministic Sverige i fokus corpus artifacts from the repository PDF."""

from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import uuid
from dataclasses import dataclass
from pathlib import Path

CORPUS_ID = "sverige-i-fokus-v1"
SOURCE_REVISION_ID = "sverige-i-fokus-source-v2"
SOURCE_REVISION_VERSION = 2
PARSER_VERSION = "chapter-boundary-v2"
EXPECTED_SHA256 = "39a93261cc64af0122e186b7d67f57dffad573576570956a4754d22ce776aada"
NAMESPACE = uuid.UUID("418411a4-4a89-50d8-aeef-68e9c237d12b")


@dataclass(frozen=True)
class Chapter:
    title: str
    page: int
    subsections: tuple[tuple[str, int], ...]


CHAPTERS = (
    Chapter("Landet Sverige", 5, (("Geografi, klimat och natur", 5), ("Sveriges indelning", 6), ("Befolkning", 7), ("Naturresurser", 7), ("Klimatförändringar", 8))),
    Chapter("Sveriges demokratiska system", 10, (("Demokrati betyder folkstyre", 10), ("Hot mot demokratin", 11))),
    Chapter("Så här styrs Sverige", 12, (("Landet styrs på olika nivåer", 12), ("Sveriges statsskick", 13))),
    Chapter("Politiska val och partier", 14, (("Val och röstning", 14), ("Politiska partier", 15))),
    Chapter("Lag och rätt", 16, (("Grundlagarna", 16), ("Rättsväsendet", 17))),
    Chapter("Mediernas roll", 20, (("Fria medier", 20), ("Olika slags medier", 21), ("Källkritik", 21))),
    Chapter("Mänskliga rättigheter", 22, (("Mänskliga rättigheter gäller alla", 22), ("Jämställdhet mellan könen", 23), ("Barns rättigheter", 24), ("Minoriteters rättigheter", 25), ("Arbetet mot diskriminering", 26))),
    Chapter("Arbetsmarknad och privatekonomi", 27, (("Så fungerar arbetsmarknaden", 27), ("Arbetsmarknadens parter", 28), ("Lagar och regler på arbetsmarknaden", 29), ("Privatekonomi i Sverige", 29))),
    Chapter("Välfärdssamhället", 30, (("Skatter för Sveriges välfärd", 30), ("Stat, regioner och kommuner har olika ansvar", 30))),
    Chapter("Sveriges moderna historia", 32, (("Från jordbrukssamhälle till industrisamhälle", 32), ("Sveriges väg till demokrati", 33), ("Modernisering och folkhem", 34), ("Rekordåren", 36), ("Informationssamhället och globalisering", 37))),
    Chapter("Sverige och omvärlden", 39, (("Nordiskt och europeiskt samarbete", 39), ("Globalt samarbete", 39), ("Försvars- och säkerhetspolitik", 40))),
    Chapter("En sekulär stat och ett mångreligiöst land", 42, (("Religionsfrihet", 42), ("Religionens roll", 42))),
    Chapter("Traditioner och högtider", 45, (("Några traditionella högtider under året", 45),)),
)


def stable_id(kind: str, *parts: str) -> str:
    return str(uuid.uuid5(NAMESPACE, ":".join((CORPUS_ID, kind, *parts))))


def slug(value: str) -> str:
    replacements = str.maketrans({"å": "a", "ä": "a", "ö": "o", "Å": "a", "Ä": "a", "Ö": "o"})
    return re.sub(r"[^a-z0-9]+", "-", value.translate(replacements).lower()).strip("-")


def normalized(value: str) -> str:
    return re.sub(r"\s+", " ", value.replace("\xad", "")).strip()


def page_text(pdf: Path, page: int) -> str:
    result = subprocess.run(
        ["pdftotext", "-f", str(page), "-l", str(page), "-layout", str(pdf), "-"],
        check=True,
        capture_output=True,
        text=True,
    )
    lines = []
    for line in result.stdout.replace("\f", "").splitlines():
        clean = normalized(line)
        if not clean:
            lines.append("")
        elif re.match(r"^SVERIGE I FOKUS\b", clean) or clean == str(page):
            continue
        else:
            lines.append(clean)
    return "\n".join(lines).strip()


def find_heading(text: str, heading: str) -> int:
    candidates = (heading, heading + ".", heading.rstrip("."))
    folded = text.casefold()
    for candidate in candidates:
        pos = folded.find(candidate.casefold())
        if pos >= 0:
            return pos
    raise ValueError(f"Heading not found in extracted page: {heading!r}")


def find_chapter_heading(text: str, chapter_number: int) -> int:
    match = re.search(rf"(?im)^kapitel\s+{chapter_number}\s*[–-]", text)
    if match:
        return match.start()
    raise ValueError(f"Chapter heading not found in extracted page: {chapter_number}")


def extract_sections(pdf: Path) -> list[dict]:
    flattened = [(chapter, title, page) for chapter in CHAPTERS for title, page in chapter.subsections]
    pages = {page: page_text(pdf, page) for page in range(4, 49)}
    sections = []
    for index, (chapter, title, start_page) in enumerate(flattened):
        next_item = flattened[index + 1] if index + 1 < len(flattened) else None
        next_page = next_item[2] if next_item else 48
        start_offset = find_heading(pages[start_page], title)
        pieces = []
        end_page = start_page
        end_offset = len(pages[start_page])
        boundary_reason = "END_OF_DOCUMENT"
        # Page 48 is the publisher contact colophon, not curriculum content.
        last_page = next_page if next_item else 47
        for page in range(start_page, last_page + 1):
            page_start = start_offset if page == start_page else 0
            candidates: list[tuple[int, str]] = []
            if next_item and page == next_page:
                candidates.append((find_heading(pages[page], next_item[1]), "NEXT_SUBSECTION"))
            next_chapter = CHAPTERS[CHAPTERS.index(chapter) + 1] if chapter != CHAPTERS[-1] else None
            if next_chapter and page == next_chapter.page:
                candidates.append((find_chapter_heading(pages[page], CHAPTERS.index(chapter) + 2), "NEXT_CHAPTER"))
            page_end, reason = min(candidates, default=(len(pages[page]), "END_OF_DOCUMENT"), key=lambda item: item[0])
            piece = pages[page][page_start:page_end].strip()
            if piece:
                pieces.append(piece)
                end_page = page
                end_offset = page_end
            if candidates:
                boundary_reason = reason
                break
        exact = "\n\n".join(piece.strip() for piece in pieces if piece.strip())
        if len(exact) < len(title) + 40:
            raise ValueError(f"Section extraction is unexpectedly short: {title}")
        section_id = stable_id("section", SOURCE_REVISION_ID, chapter.title, title)
        sections.append(
            {
                "id": section_id,
                "logicalSectionId": stable_id("section", chapter.title, title),
                "sourceRevisionId": SOURCE_REVISION_ID,
                "sourceRevisionVersion": SOURCE_REVISION_VERSION,
                "parserVersion": PARSER_VERSION,
                "chapter": chapter.title,
                "subsection": title,
                "structuralPath": f"{chapter.title} / {title}",
                "startPage": start_page,
                "endPage": end_page,
                "extractionStart": {"page": start_page, "offset": start_offset},
                "extractionEnd": {"page": end_page, "offset": end_offset},
                "boundaryReason": boundary_reason,
                "order": index + 1,
                "exactText": exact,
                "normalizedText": normalized(exact),
                "checksum": hashlib.sha256(exact.encode()).hexdigest(),
            }
        )
    return sections


def manifest(sections: list[dict]) -> dict:
    by_title = {section["subsection"]: section for section in sections}
    subjects = []
    for subject_order, chapter in enumerate(CHAPTERS, 1):
        topics = []
        for topic_order, (title, _) in enumerate(chapter.subsections, 1):
            section = by_title[title]
            objective_id = stable_id("objective", chapter.title, title)
            topics.append(
                {
                    "id": stable_id("topic", chapter.title, title),
                    "code": f"{subject_order:02d}-{topic_order:02d}-{slug(title)}",
                    "name": title,
                    "displayOrder": topic_order,
                    "learningObjectives": [
                        {
                            "id": objective_id,
                            "code": f"LO-{subject_order:02d}-{topic_order:02d}",
                            "title": f"Förstå huvuddragen i {title.lower()}",
                            "sourceSectionIds": [section["id"]],
                            "factGenerationTarget": 8,
                            "lessonTarget": 1,
                            "questionTargetPerApprovedFact": 2,
                            "mockExamCoverageGroup": slug(chapter.title),
                        }
                    ],
                }
            )
        subjects.append(
            {
                "id": stable_id("subject", chapter.title),
                "code": f"{subject_order:02d}-{slug(chapter.title)}",
                "name": chapter.title,
                "displayOrder": subject_order,
                "topics": topics,
            }
        )
    return {
        "corpusId": CORPUS_ID,
        "sourceRevision": {
            "id": SOURCE_REVISION_ID,
            "version": SOURCE_REVISION_VERSION,
            "parentRevisionId": "sverige-i-fokus-source-v1",
            "parserVersion": PARSER_VERSION,
            "createdAt": "2026-08-01T00:00:00Z",
            "reviewStatus": "REVIEWED",
            "reviewerActor": "sverige-i-fokus-boundary-correction",
            "correctionReason": "Exclude following chapter headings and introductions from terminal Source Sections.",
        },
        "source": {
            "id": stable_id("source", EXPECTED_SHA256),
            "title": "Sverige i fokus – Utbildningsmaterial till medborgarskapsprov: Grundläggande kunskaper om det svenska samhället",
            "publisher": "Universitets- och högskolerådet (UHR)",
            "production": "Skolverket",
            "edition": "Första upplagan",
            "publicationYear": 2026,
            "language": "sv",
            "originalFilename": "sverige-i-fokus.pdf",
            "sha256": EXPECTED_SHA256,
            "pageCount": 48,
            "officialStudyMaterial": True,
            "licensingReviewStatus": "PENDING",
            "attribution": "Baserat på Sverige i fokus — självständigt övningsmaterial.",
        },
        "exam": {
            "id": stable_id("exam", CORPUS_ID),
            "code": CORPUS_ID,
            "name": "Sverige i fokus – självständigt studiematerial",
            "versionId": stable_id("exam-version", CORPUS_ID),
            "versionCode": CORPUS_ID,
            "releaseVersion": CORPUS_ID,
            "lifecycle": "SOURCE_IMPORTED",
        },
        "subjects": subjects,
    }


def validate(pdf: Path) -> str:
    if not pdf.is_file():
        raise FileNotFoundError(pdf)
    checksum = hashlib.sha256(pdf.read_bytes()).hexdigest()
    if checksum != EXPECTED_SHA256:
        raise ValueError(f"Unexpected PDF checksum: {checksum}")
    return checksum


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--pdf", type=Path, default=Path("docs/sverige-i-fokus.pdf"))
    parser.add_argument("--output", type=Path, default=Path("content/sverige-i-fokus"))
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()
    validate(args.pdf)
    sections = extract_sections(args.pdf)
    data = manifest(sections)
    if args.check:
        print(json.dumps({"corpusId": CORPUS_ID, "sections": len(sections), "sha256": EXPECTED_SHA256}, sort_keys=True))
        return
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "source-sections.json").write_text(json.dumps(sections, ensure_ascii=False, indent=2) + "\n")
    (args.output / "curriculum-manifest.yaml").write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n")
    print(json.dumps({"manifest": str(args.output / "curriculum-manifest.yaml"), "sections": len(sections)}, sort_keys=True))


if __name__ == "__main__":
    main()
