"""Refuse changes beyond the reviewed edgeR transition and bounded family credit."""
from pathlib import Path
import copy
import json
import unittest
import zipfile
from edger_catalog import model as m
from verify_edger_dispatch import verify

ROOT = Path(__file__).resolve().parents[3]


class EdgeRAdmissionModelControls(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.primary = json.loads((ROOT / 'docs/literature/edger-role-crosswalk/PRIMARY.json').read_bytes())
        cls.q = verify()[0]
        cls.q.pop('global_dispatcher_currently_installed')
        archived = ROOT / 'docs/literature/edger-catalog-admission/metadata-before.zip'
        if archived.exists():
            with zipfile.ZipFile(archived) as z:
                cls.before = {n: z.read(n) for n in m.META}
        else:
            cls.before = {n: (ROOT / n).read_bytes() for n in m.META}
        cls.after = m.transition(cls.before, cls.primary, cls.q)

    def altered(self, name, change):
        after = dict(self.after)
        value = json.loads(after[name])
        change(value)
        after[name] = m.encoded(value)
        with self.assertRaises(ValueError):
            m.check(self.before, after, self.primary, self.q)

    def test_exact_proposed_population_and_credit(self):
        result = m.check(self.before, self.after, self.primary, self.q)
        self.assertEqual((2705, 1520, 1185), (result['observations'], result['reviewed_uses'], result['pending_uses']))
        self.assertEqual((139, 25, 114), (result['integrations'], result['native_integrations'], result['upstream_integrations']))
        self.assertEqual(0, result['native_family_credit_added'])
        self.assertFalse(result['whole_goal_complete'])

    def test_prior_review_edit_is_refused(self):
        self.altered(m.META[5], lambda v: v['reviews'][0].update(use_summary='replacement'))

    def test_prior_review_reordering_is_refused(self):
        def change(v):
            v['reviews'][0], v['reviews'][1] = v['reviews'][1], v['reviews'][0]
        self.altered(m.META[5], change)

    def test_duplicate_added_review_is_refused(self):
        self.altered(m.META[5], lambda v: v['reviews'].append(copy.deepcopy(v['reviews'][-1])))

    def test_unrelated_inventory_scope_edit_is_refused(self):
        def change(v):
            next(r for r in v if r['id'] == 'susie')['global_CLI_dispatch_available'] = False
        self.altered(m.META[1], change)

    def test_unrelated_observation_removal_is_refused(self):
        def change(v):
            paper = next(p for p in v if any(t['tool_id'] != 'edger' for t in p['tools']))
            paper['tools'].remove(next(t for t in paper['tools'] if t['tool_id'] != 'edger'))
        self.altered(m.META[0], change)

    def test_extra_p088_observation_is_refused(self):
        def change(v):
            paper = next(p for p in v if p['paper_id'] == 'P088')
            paper['tools'].append(copy.deepcopy(next(t for t in paper['tools'] if t['tool_id'] == 'edger')))
        self.altered(m.META[0], change)

    def test_invented_p088_operation_is_refused(self):
        def change(v):
            use = next(t for p in v if p['paper_id'] == 'P088' for t in p['tools'] if t['tool_id'] == 'edger')
            use['source_role_qualification']['operation_unresolved'] = False
            use['source_role_qualification']['native_matrix_counterparts'] = ['TMM']
        self.altered(m.META[0], change)

    def test_misplaced_gt_ex_edition_is_refused(self):
        def change(v):
            use = next(t for p in v if p['paper_id'] == 'P108' for t in p['tools'] if t['tool_id'] == 'edger')
            use['version_reported_near_name'] = '3.40.2'
        self.altered(m.META[0], change)

    def test_native_double_family_credit_is_refused(self):
        def change(v):
            next(r for r in v if r['id'] == 'edger')['native_family_credit_added'] = 1
        self.altered(m.META[1], change)

    def test_changed_frozen_doi_is_refused(self):
        self.altered(m.META[0], lambda v: v[0].update(doi='10.invalid/new-cohort'))

    def test_wrong_summary_counts_are_refused(self):
        self.altered(m.META[2], lambda v: v.update(paper_tool_observations=2704))

    def test_double_apply_is_refused(self):
        with self.assertRaises(ValueError):
            m.transition(self.after, self.primary, self.q)

    def test_weak_global_model_qualification_is_refused(self):
        q = dict(self.q, exact_complete_object_comparisons=337)
        with self.assertRaises(ValueError):
            m.transition(self.before, self.primary, q)

    def test_weak_source_role_controls_are_refused(self):
        q = dict(self.q, source_role_controls_per_host=14)
        with self.assertRaises(ValueError):
            m.transition(self.before, self.primary, q)


if __name__ == '__main__':
    unittest.main()
