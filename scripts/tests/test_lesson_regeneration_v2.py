import importlib.util
import pathlib
import unittest


PATH = pathlib.Path(__file__).parents[1] / "lesson_regeneration_v2.py"
SPEC = importlib.util.spec_from_file_location("lesson_regeneration_v2", PATH)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class LessonRegenerationV2Test(unittest.TestCase):
    def test_page_target_never_repeats_a_fact_to_hit_page_quota(self):
        self.assertEqual(MODULE.target_pages(3, 4), 3)
        self.assertEqual(MODULE.target_pages(5, 6), 5)
        self.assertEqual(MODULE.target_pages(8, 6), 6)

    def test_balanced_groups_assign_each_fact_exactly_once(self):
        facts = [{"id": value} for value in range(8)]
        groups = MODULE.balanced_groups(facts, 6)
        self.assertEqual([2, 2, 1, 1, 1, 1], [len(value) for value in groups])
        self.assertEqual(list(range(8)), [fact["id"] for group in groups for fact in group])

    def test_lesson_flow_has_introduction_development_and_summary(self):
        self.assertEqual("INTRODUCTION", MODULE.page_kind(0, 5, "text"))
        self.assertEqual("CORE_CONCEPT", MODULE.page_kind(2, 5, "Sverige har sjöar."))
        self.assertEqual("SUMMARY", MODULE.page_kind(4, 5, "text"))


if __name__ == "__main__":
    unittest.main()
