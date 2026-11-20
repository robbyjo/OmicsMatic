"""Source-review guard controls; no original edgeR fitting is performed."""
from pathlib import Path
import copy
import json
import unittest
from edger_catalog.primary import validate
from edger_catalog.coverage import build

ROOT = Path(__file__).resolve().parents[3]


class EdgeRSourceRoleControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.primary = json.loads((ROOT / 'docs/literature/edger-role-crosswalk/PRIMARY.json').read_bytes())

    def altered(self, change):
        value = copy.deepcopy(self.primary)
        change(value)
        with self.assertRaises(ValueError):
            validate(value)

    def test_p088_has_no_invented_operation_or_function(self):
        row = next(r for r in build(self.primary)['roles'] if r['paper_id'] == 'P088')
        self.assertTrue(row['operation_unresolved'])
        self.assertEqual([], row['explicit_edgeR_functions'])
        self.assertEqual([], row['native_matrix_counterparts'])
        self.assertEqual([], row['original_prepared_count_counterparts'])

    def test_p108_edition_stays_at_developing_brain_anchor(self):
        row = next(r for r in build(self.primary)['roles'] if r['paper_id'] == 'P108')
        self.assertEqual(44, row['primary_evidence']['paragraph_index'])
        self.assertEqual([], row['primary_anchor_reported_versions'])
        self.assertEqual(['3.40.2'], row['matching_reported_native_editions'])
        self.assertEqual([], row['matching_reported_model_source_profiles'])

    def test_p237_limma_tests_are_not_assigned_an_edger_nb_function(self):
        row = next(r for r in build(self.primary)['roles'] if r['paper_id'] == 'P237')
        self.assertEqual(['sumTechReps', 'cpm'], [w['function'] for w in row['explicit_edgeR_functions']])
        self.assertEqual([], row['original_prepared_count_counterparts'])
        self.assertIn('not exposed', row['remaining_author_roles'][0])

    def test_p053_methods_anchor_and_unsupported_old_inference_are_explicit(self):
        row = next(r for r in build(self.primary)['roles'] if r['paper_id'] == 'P053')
        self.assertEqual(73, row['primary_evidence']['paragraph_index'])
        self.assertEqual(['3.36.0'], row['matching_reported_native_editions'])
        self.assertEqual(['3.36.0'], row['unsupported_reported_model_editions'])
        self.assertEqual([], row['matching_reported_model_source_profiles'])

    def test_p037_power_trend_is_an_open_gap(self):
        row = next(r for r in build(self.primary)['roles'] if r['paper_id'] == 'P037')
        self.assertEqual(['glmQLFTest'], [w['function'] for w in row['explicit_edgeR_functions']])
        self.assertIn('power dispersion trend is not offered', row['remaining_author_roles'][0])
        self.assertEqual(['3.32.1'], row['unsupported_reported_model_editions'])

    def test_crosswalk_does_not_admit_itself_or_reproduce_authors(self):
        result = build(self.primary)
        self.assertEqual(17, result['review_records_to_append'])
        self.assertEqual(['P088'], result['observations_to_add'])
        self.assertEqual(0, result['actual_new_family_credit'])
        self.assertEqual(0, result['canonical_reviews_appended'])
        for row in result['roles']:
            self.assertFalse(row['canonical_review_appended'])
            self.assertFalse(row['full_reported_role_qualified'])
            self.assertFalse(row['author_R_and_dependency_profile_recovered'])

    def test_missing_literal_function_is_refused(self):
        self.altered(lambda v: v['explicit_functions'].pop())

    def test_invented_p088_function_is_refused(self):
        def change(v):
            witness = copy.deepcopy(v['explicit_functions'][0])
            witness['paper_id'] = 'P088'
            v['explicit_functions'].append(witness)
        self.altered(change)

    def test_altered_source_identity_is_refused(self):
        self.altered(lambda v: v['frozen_census']['source_identities'][0].update(doi='10.invalid/replacement'))

    def test_wrong_function_anchor_is_refused(self):
        self.altered(lambda v: v['explicit_functions'][0].update(paragraph_index=48))

    def test_replaced_function_hash_is_refused(self):
        self.altered(lambda v: v['explicit_functions'][0].update(paragraph_sha256='0' * 64))

    def test_duplicate_function_is_refused(self):
        self.altered(lambda v: v['explicit_functions'].append(copy.deepcopy(v['explicit_functions'][0])))

    def test_negative_literal_position_is_refused(self):
        self.altered(lambda v: v['explicit_functions'][0].update(character_offsets=[-1]))

    def test_unadmitted_credit_is_refused(self):
        self.altered(lambda v: v.update(new_family_credit=1))

    def test_fabricated_success_field_is_refused(self):
        self.altered(lambda v: v['explicit_functions'][0].update(author_workflow_reproduced=True))


if __name__ == '__main__':
    unittest.main()
