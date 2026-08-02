import unittest

from scripts.fact_density_report import learner_question, page_ceiling


class FactDensityReportTest(unittest.TestCase):
    def test_page_ceiling_is_bounded_by_fact_density(self):
        self.assertEqual([page_ceiling(value) for value in (1, 2, 3, 4, 5, 8)], [2, 3, 4, 5, 6, 6])

    def test_preview_question_is_derived_from_fact(self):
        self.assertEqual(learner_question("Sverige har fyra grundlagar."), "Hur skulle du förklara att sverige har fyra grundlagar?")


if __name__ == "__main__":
    unittest.main()
