import json
from pathlib import Path
import unittest
from rsem_public_validation import verify_public_workflow

ROOT = Path(__file__).resolve().parents[2]
DATA = ROOT / 'docs/literature/omics-2023-2026'

class PublicRsemEvidenceTest(unittest.TestCase):
    def proof(self):
        return json.loads((DATA / 'rsem-workflow-validation.json').read_bytes())

    def test_full_public_scientific_populations_and_source_links_are_verified(self):
        result = verify_public_workflow(ROOT, DATA, self.proof())
        self.assertEqual(12, result['public_fitting_cases'])
        self.assertEqual(193, result['complete_public_scientific_files'])
        self.assertEqual(778, result['public_numeric_cells'])

    def test_unavailable_workflow_cannot_receive_public_completion_credit(self):
        proof = self.proof(); proof['public_fitting_workflow_available'] = False
        with self.assertRaises(AssertionError): verify_public_workflow(ROOT, DATA, proof)

    def test_portable_runtime_ticket_claim_is_refused(self):
        proof = self.proof(); proof['reusable_runtime_admission_ticket'] = True
        with self.assertRaises(AssertionError): verify_public_workflow(ROOT, DATA, proof)

    def test_incorrect_public_numeric_population_claim_is_refused(self):
        proof = self.proof(); proof['public_numeric_cells'] += 1
        with self.assertRaises(AssertionError): verify_public_workflow(ROOT, DATA, proof)

if __name__ == '__main__':
    unittest.main()
