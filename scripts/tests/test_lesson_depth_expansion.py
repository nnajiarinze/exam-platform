import importlib.util
import pathlib
import unittest


PATH = pathlib.Path(__file__).parents[1] / "lesson_depth_expansion.py"
SPEC = importlib.util.spec_from_file_location("lesson_depth_expansion", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


def topic(source: str, pages: int = 3):
    vocabulary = ["geografi", "lagstiftning", "sjukvård", "industrialisering", "religionsfrihet", "högtider"]
    return {
        "chapter": "Kapitel", "topicId": "topic", "topicTitle": "Demokrati",
        "objectiveId": "objective", "objectiveText": "Förstå demokrati", "lessonId": "lesson",
        "lessonVersion": 1, "sourceRevision": "sverige-i-fokus-source-v2",
        "facts": [{"id": "fact", "versionId": "fact-v1", "statement": "Riksdagen beslutar om lagar i Sverige."}],
        "sourceSections": [{"id": "source", "title": "Demokrati", "checksum": "a" * 64,
                            "exactText": source, "normalizedText": source}],
        "pages": [{"id": f"page-{index}", "title": f"Sida {index}",
                   "explanation": f"Detta är en särskild befintlig förklaring om {vocabulary[index]}.",
                   "displayOrder": index, "sourceSectionId": "source", "sectionChecksum": "a" * 64,
                   "factVersionIds": ["fact-v1"]} for index in range(pages)],
    }


class LessonDepthExpansionTest(unittest.TestCase):
    def test_reading_time_uses_documented_swedish_speed(self):
        self.assertEqual(60, MODULE.reading_seconds("ord " * 140))

    def test_source_limited_topic_is_not_padded(self):
        audit = MODULE.audit_topic(topic("Riksdagen beslutar om lagar i Sverige."))
        self.assertEqual("LIMITED_BUT_USABLE", audit["classification"])
        self.assertEqual(3, audit["plannedPageCount"])

    def test_rich_topic_has_at_most_six_pages_with_immutable_checksum(self):
        evidence = " ".join([
            "Riksdagen beslutar om lagar i Sverige och väljs av folket.",
            "Regeringen styr landet och genomför riksdagens beslut.",
            "Kommunerna ansvarar för flera lokala verksamheter.",
            "Regionerna ansvarar för uppgifter på regional nivå.",
            "Val hålls vart fjärde år och rösten är hemlig.",
            "Demokrati betyder att folket har möjlighet att påverka.",
            "Grundlagarna har en särskild ställning bland lagarna.",
            "Myndigheter genomför beslut inom sina ansvarsområden.",
        ])
        audit = MODULE.audit_topic(topic(evidence))
        self.assertLessEqual(audit["plannedPageCount"], 6)
        self.assertTrue(all(len(plan["planChecksum"]) == 64 for plan in audit["candidatePagePlans"]))

    def test_repeated_pages_require_restructure(self):
        row = topic("Riksdagen beslutar om lagar i Sverige. " * 20)
        row["pages"][1]["explanation"] = row["pages"][0]["explanation"]
        self.assertEqual("RESTRUCTURE_REQUIRED", MODULE.audit_topic(row)["classification"])

    def test_existing_four_page_lesson_can_remain_unchanged(self):
        row = topic("Riksdagen beslutar om lagar i Sverige. " * 50, pages=4)
        vocabularies = ["kartor sjöar fjäll kust", "riksdag regering lagar beslut",
                        "kommun region sjukvård skola", "historia industri rösträtt reform"]
        for index, page in enumerate(row["pages"]):
            page["explanation"] = ((vocabularies[index] + " ger tydlig förklaring med naturligt språk. ") * 15)
        audit = MODULE.audit_topic(row)
        self.assertEqual("DEPTH_SUFFICIENT", audit["classification"])
        self.assertTrue(all(item["action"] == "REUSE_UNCHANGED" for item in audit["pageActions"]))


if __name__ == "__main__":
    unittest.main()
