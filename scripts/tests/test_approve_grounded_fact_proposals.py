import unittest

from scripts.approve_grounded_fact_proposals import (
    MANDATORY_GATES,
    approval_eligible,
    gates_pass,
)


class ApproveGroundedFactProposalsTest(unittest.TestCase):
    def test_only_good_proposals_with_every_gate_pass(self):
        proposal = {
            "status": "PROPOSED",
            "automatedClassification": "GOOD",
            "validationGates": {gate: True for gate in MANDATORY_GATES},
        }
        self.assertTrue(approval_eligible(proposal))
        proposal["validationGates"]["atomic"] = False
        self.assertFalse(approval_eligible(proposal))

    def test_accepted_good_proposal_remains_safe_to_resume(self):
        proposal = {
            "status": "ACCEPTED",
            "automatedClassification": "GOOD",
            "validationGates": {gate: True for gate in MANDATORY_GATES},
            "resultingKnowledgeFactId": "fact-id",
        }
        self.assertTrue(gates_pass(proposal))
        self.assertFalse(approval_eligible(proposal))

    def test_non_good_and_rejected_proposals_never_qualify(self):
        gates = {gate: True for gate in MANDATORY_GATES}
        self.assertFalse(
            approval_eligible(
                {
                    "status": "PROPOSED",
                    "automatedClassification": "NEEDS_SPLIT",
                    "validationGates": gates,
                }
            )
        )
        self.assertFalse(
            approval_eligible(
                {
                    "status": "REJECTED",
                    "automatedClassification": "DUPLICATE",
                    "validationGates": gates,
                }
            )
        )


if __name__ == "__main__":
    unittest.main()
