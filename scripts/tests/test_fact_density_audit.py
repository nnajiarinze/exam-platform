import unittest

from scripts.fact_density_audit import covered_ranges, concept_inventory, classify, lesson_readiness, similarity


def section(text, facts=()):
    return {"exactText": text, "normalizedText": text, "facts": list(facts)}


class FactDensityAuditTest(unittest.TestCase):
    def test_evidence_span_coverage_merges_overlap(self):
        self.assertEqual(covered_ranges("Sverige har fyra grundlagar och fria val.",
                                        ["Sverige har fyra grundlagar", "fyra grundlagar och fria val"]), [(0, 40)])

    def test_semantic_fact_coverage_rejects_duplicate(self):
        fact = {"text": "Sverige har fyra grundlagar.", "evidence": [{"quote": "Sverige har fyra grundlagar."}]}
        inventory = concept_inventory(section("Sverige har fyra grundlagar. Kommunerna ansvarar för skolan och äldreomsorgen.", [fact]))
        self.assertIn("DUPLICATE", {item["classification"] for item in inventory})
        self.assertIn("EXPLICIT_IN_SOURCE_BUT_NOT_FACT", {item["classification"] for item in inventory})

    def test_missing_concept_requires_atomic_independent_sentence(self):
        inventory = concept_inventory(section("Därför är detta viktigt. Regionerna ansvarar för sjukvården i Sverige."))
        eligible = [item["conceptText"] for item in inventory if item["generationEligible"]]
        self.assertEqual(eligible, ["Regionerna ansvarar för sjukvården i Sverige."])

    def test_source_thin_classification(self):
        self.assertEqual(classify({"normalizedText": "Kort stödtext.", "facts": []}, []), "SOURCE_TOO_THIN")

    def test_underutilized_classification_is_source_driven(self):
        eligible = [{"generationEligible": True}] * 2
        self.assertEqual(classify({"normalizedText": "x" * 500, "exactText": "x.", "facts": []}, eligible),
                         "UNDERUTILIZED_TWO_FACTS")

    def test_similarity_handles_semantic_token_overlap(self):
        self.assertGreater(similarity("Kommunerna ansvarar för skolan.", "Skolan är kommunernas ansvar."), 0.45)

    def test_lesson_depth_readiness_is_fact_driven(self):
        self.assertEqual(lesson_readiness(4, 2), "READY_FOR_DEEPER_LESSON")
        self.assertEqual(lesson_readiness(2, 1), "LIMITED_BUT_USABLE")
        self.assertEqual(lesson_readiness(1, 2), "LIMITED_BUT_USABLE")
        self.assertEqual(lesson_readiness(1, 0), "SOURCE_EXPANSION_REQUIRED")


if __name__ == "__main__":
    unittest.main()
